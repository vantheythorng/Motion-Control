package com.kwan.motioncontrol.motion

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.kwan.motioncontrol.MainActivity
import com.kwan.motioncontrol.R
import com.kwan.motioncontrol.accessibility.ActionDispatcher
import com.kwan.motioncontrol.accessibility.AirScrollAccessibilityService
import com.kwan.motioncontrol.accessibility.ForegroundAppTracker
import com.kwan.motioncontrol.data.AppRoute
import com.kwan.motioncontrol.data.DefaultProfiles
import com.kwan.motioncontrol.data.DeviceAction
import com.kwan.motioncontrol.data.Motion
import com.kwan.motioncontrol.data.Profile
import com.kwan.motioncontrol.data.ProfileRepository
import com.kwan.motioncontrol.data.ProfileResolver
import com.kwan.motioncontrol.data.SettingsRepository
import com.kwan.motioncontrol.data.cameraOffText
import com.kwan.motioncontrol.system.InstalledApps
import com.kwan.motioncontrol.system.ScreenState
import com.kwan.motioncontrol.motion.camera.HandTracker
import org.koin.android.ext.android.inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

/**
 * Foreground service (type camera) that keeps hand tracking running while the user is in other
 * apps. Must be started while our UI is visible: Android 14+ forbids starting a camera FGS from
 * the background. It only ever starts from the user tapping Start: it is never restarted by the
 * system (START_NOT_STICKY) or on boot.
 */
class MotionService : LifecycleService() {

    private val store: MotionStateStore by inject()
    private val dispatcher: ActionDispatcher by inject()
    private val profiles: ProfileRepository by inject()
    private val installedApps: InstalledApps by inject()
    private val settings: SettingsRepository by inject()

    /** Inputs of the single camera decision (see [cameraWanted]). */
    private val running = MutableStateFlow(false)
    private val screenUsable = MutableStateFlow(false)
    private var appliedProfile: Profile? = null
    private var notificationText: String? = null
    /** The notification offers "Show bubble" only while the bubble is off. */
    private var bubbleEnabled = true
    private var notificationBubbleEnabled: Boolean? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var engine: GestureEngine
    private lateinit var tracker: HandTracker
    private var inForeground = false
    @Volatile private var destroyed = false
    private var lastFrameAt = 0L
    private var fps = 0f
    private var lastStatsLogAt = 0L
    private var statFrames = 0
    private var statHandFrames = 0
    private var statPeakDx = 0f
    private var statPeakDy = 0f

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Usable only once the lock screen is gone, otherwise the camera would run (and
            // gestures fire) on the keyguard.
            screenUsable.value = intent.action != Intent.ACTION_SCREEN_OFF && ScreenState.isUsable(context)
        }
    }

    override fun onCreate() {
        super.onCreate()
        engine = GestureEngine(
            bindings = DefaultProfiles.shortVideo.bindings,
            config = ProfileTuning.configFor(DefaultProfiles.shortVideo),
            isPaused = { store.state.value.paused },
            onIgnored = { motion, reason -> Log.d(TAG, "$motion ignored: $reason") },
            onAction = { motion, action -> mainHandler.post { perform(motion, action) } },
        )
        tracker = HandTracker(
            context = this,
            onFrame = ::onFrame,
            onError = { e ->
                Log.e(TAG, "Hand tracking error", e)
                store.update { it.copy(error = e.message ?: e.javaClass.simpleName) }
            },
            onDelegateChosen = { d -> store.update { it.copy(delegate = d.name) } },
            onBound = { store.update { it.copy(cameraActive = true, error = null) } },
        )
        createChannel()
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        screenUsable.value = ScreenState.isUsable(this)

        // The profile follows the open app; edits apply live, without restarting the service.
        val routes = combine(
            ForegroundAppTracker.current,
            AirScrollAccessibilityService.instance.map { it != null },
            profiles.routing,
        ) { app, accessibilityOn, routing -> ProfileResolver.resolve(app, accessibilityOn, routing) }
            .distinctUntilChanged()
            .stateIn(lifecycleScope, SharingStarted.Eagerly, null)
        lifecycleScope.launch { routes.filterNotNull().collect(::onRoute) }
        lifecycleScope.launch {
            settings.bubbleEnabled.collect { enabled ->
                bubbleEnabled = enabled
                if (inForeground) refreshNotification()
            }
        }

        // The one place that turns the camera on and off.
        lifecycleScope.launch {
            cameraWanted(
                running = running,
                appAllowed = routes.map { it is AppRoute.Use },
                testModeActive = store.testModeActive,
                screenUsable = screenUsable,
            ).collect { wanted -> if (wanted) startCamera() else stopCamera() }
        }
    }

    private fun onRoute(route: AppRoute) {
        val label = route.app?.let(installedApps::label)
        if (route is AppRoute.Use && route.profile != appliedProfile) {
            appliedProfile = route.profile
            engine.applyProfile(route.profile.bindings, ProfileTuning.configFor(route.profile))
            Log.d(TAG, "Profile applied: ${route.profile.name} for ${route.app} ${route.profile.bindings}")
        }
        if (route is AppRoute.Off) Log.d(TAG, "Camera off for ${route.app}: ${route.reason}")
        store.update { it.copy(route = route, appLabel = label) }
        if (inForeground) refreshNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                stopMotion()
                return START_NOT_STICKY
            }
            ACTION_PAUSE, ACTION_RESUME -> {
                if (inForeground) setPaused(intent.action == ACTION_PAUSE) else stopSelf()
            }
            ACTION_SHOW_BUBBLE -> {
                if (inForeground) lifecycleScope.launch { settings.setBubbleEnabled(true) } else stopSelf()
            }
            // ACTION_START. Every startForegroundService() call must be answered with
            // startForeground(), even when already running.
            else -> startMotion()
        }
        // Never restarted by the system after being killed: motion control only runs when the
        // user starts it.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        destroyed = true
        unregisterReceiver(screenReceiver)
        tracker.close()
        mainHandler.removeCallbacksAndMessages(null)
        running.value = false
        store.update { MotionState(error = it.error) }
        store.publishDebug(null)
        super.onDestroy()
    }

    private fun startMotion() {
        val hasCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        try {
            check(hasCamera) { "Camera permission not granted" }
            // The camera type exists from Android 11; Android 10 doesn't require a type for camera use.
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            } else 0
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, buildNotification(store.state.value.paused && inForeground), type,
            )
        } catch (e: Exception) {
            if (inForeground) return
            // E.g. the camera permission was revoked. Don't retry: the user starts it again.
            Log.w(TAG, "Can't start camera foreground service", e)
            store.update { MotionState(error = "Couldn't start: ${e.message ?: e.javaClass.simpleName}") }
            stopSelf()
            return
        }
        if (inForeground) return
        inForeground = true
        store.update {
            MotionState(running = true, delegate = it.delegate, route = it.route, appLabel = it.appLabel)
        }
        running.value = true
    }

    private fun stopMotion() {
        running.value = false
        stopCamera()
        store.update { MotionState() }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        inForeground = false
        stopSelf()
    }

    /** Only called by the camera decision. [MotionState.cameraActive] is set once it's bound. */
    private fun startCamera() {
        if (tracker.isRunning) return
        tracker.start(this)
    }

    private fun stopCamera() {
        tracker.stop()
        // A pose held before the camera stopped must not fire when it restarts.
        engine.reset()
        store.update { it.copy(cameraActive = false) }
        store.publishDebug(null)
    }

    /** Called on MediaPipe's result thread. */
    private fun onFrame(frame: HandFrame) {
        if (destroyed) return
        val snapshot = engine.onFrame(frame)
        val dt = frame.timestampMs - lastFrameAt
        if (lastFrameAt > 0 && dt > 0) fps = fps * 0.8f + (1000f / dt) * 0.2f
        lastFrameAt = frame.timestampMs
        store.publishDebug(DebugFrame(frame.landmarks, snapshot, fps))
        logStats(frame, snapshot)
    }

    /**
     * Once a second while a hand was visible: how many frames had a hand (tracking dropouts) and
     * the largest swipe displacement reached, so thresholds can be tuned from logcat while another
     * app is in front.
     */
    private fun logStats(frame: HandFrame, snapshot: GestureEngine.Snapshot) {
        statFrames++
        if (frame.landmarks != null) statHandFrames++
        if (abs(snapshot.dx) > abs(statPeakDx)) statPeakDx = snapshot.dx
        if (abs(snapshot.dy) > abs(statPeakDy)) statPeakDy = snapshot.dy
        if (frame.timestampMs - lastStatsLogAt < 1000) return
        if (statHandFrames > 0) {
            Log.d(
                TAG, String.format(
                    Locale.US,
                    "fps=%.1f hand=%d/%d dist=%s state=%s paused=%b pose=%s(%.2f) active=%s peakDx=%+.2f peakDy=%+.2f",
                    fps, statHandFrames, statFrames,
                    snapshot.distanceCm?.let { "%.0fcm%s".format(Locale.US, it, if (snapshot.inRange) "" else "(out)") },
                    snapshot.state, store.state.value.paused,
                    snapshot.pose, snapshot.poseScore, snapshot.activePose, statPeakDx, statPeakDy,
                )
            )
        }
        lastStatsLogAt = frame.timestampMs
        statFrames = 0
        statHandFrames = 0
        statPeakDx = 0f
        statPeakDy = 0f
    }

    private fun perform(motion: Motion, action: DeviceAction) {
        if (destroyed) return
        Log.d(TAG, "$motion -> $action")
        store.notifyFired(motion)
        when (action) {
            DeviceAction.TogglePause -> setPaused(!store.state.value.paused)
            else -> if (!dispatcher.dispatch(action)) {
                Log.w(TAG, "Couldn't perform $action: accessibility service not enabled")
            }
        }
    }

    private fun setPaused(paused: Boolean) {
        store.update { it.copy(paused = paused) }
        // Visible feedback while another app is in front; otherwise a pause is easy to miss.
        Toast.makeText(
            this,
            if (paused) "Motion control paused" else "Motion control resumed",
            Toast.LENGTH_SHORT,
        ).show()
        refreshNotification()
    }

    /** Re-posts the notification only when what it shows changes. */
    private fun refreshNotification() {
        val paused = store.state.value.paused
        val text = notificationText(paused)
        if (text == notificationText && bubbleEnabled == notificationBubbleEnabled) return
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(paused))
    }

    private fun notificationText(paused: Boolean): String {
        if (paused) return "Gestures are ignored. Tap Resume to turn them back on."
        val state = store.state.value
        return when (val route = state.route) {
            is AppRoute.Use -> buildString {
                append(route.profile.name)
                if (route.viaDefault) append(" (default)")
                state.appLabel?.let { append(" · ").append(it) }
                append(". Keep your hand ${ProfileTuning.distanceLabel(route.profile)} from the camera.")
            }
            is AppRoute.Off -> cameraOffText(route.reason, state.appLabel)
            null -> "Starting…"
        }
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Motion control", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun buildNotification(paused: Boolean): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_motion)
            .setContentTitle(if (paused) "Motion control paused" else "Motion control active")
            .setContentText(notificationText(paused).also { notificationText = it })
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent())
            .addAction(
                0, if (paused) "Resume" else "Pause",
                servicePendingIntent(if (paused) ACTION_RESUME else ACTION_PAUSE),
            )
            .addAction(0, "Stop", servicePendingIntent(ACTION_STOP))
            .apply {
                notificationBubbleEnabled = bubbleEnabled
                if (!bubbleEnabled) addAction(0, "Show bubble", servicePendingIntent(ACTION_SHOW_BUBBLE))
            }
            .build()

    private fun servicePendingIntent(action: String): PendingIntent = PendingIntent.getService(
        this, action.hashCode(),
        Intent(this, MotionService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Only opens the app; it never starts motion control. */
    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        private const val TAG = "MotionControl"
        private const val CHANNEL_ID = "motion"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_START = "com.kwan.motioncontrol.action.START"
        private const val ACTION_STOP = "com.kwan.motioncontrol.action.STOP"
        private const val ACTION_PAUSE = "com.kwan.motioncontrol.action.PAUSE"
        private const val ACTION_RESUME = "com.kwan.motioncontrol.action.RESUME"
        private const val ACTION_SHOW_BUBBLE = "com.kwan.motioncontrol.action.SHOW_BUBBLE"

        /** Call only while our UI is visible. */
        fun start(context: Context) = ContextCompat.startForegroundService(
            context, Intent(context, MotionService::class.java).setAction(ACTION_START)
        )

        fun stop(context: Context) {
            context.startService(Intent(context, MotionService::class.java).setAction(ACTION_STOP))
        }

        fun pause(context: Context) {
            context.startService(Intent(context, MotionService::class.java).setAction(ACTION_PAUSE))
        }

        fun resume(context: Context) {
            context.startService(Intent(context, MotionService::class.java).setAction(ACTION_RESUME))
        }
    }
}

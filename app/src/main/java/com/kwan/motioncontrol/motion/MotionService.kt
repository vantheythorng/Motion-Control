package com.kwan.motioncontrol.motion

import android.Manifest
import android.app.KeyguardManager
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
import android.os.PowerManager
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
import com.kwan.motioncontrol.data.DefaultProfiles
import com.kwan.motioncontrol.data.DeviceAction
import com.kwan.motioncontrol.data.Motion
import com.kwan.motioncontrol.data.ProfileRepository
import com.kwan.motioncontrol.motion.camera.HandTracker
import org.koin.android.ext.android.inject
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
    private var profileName = DefaultProfiles.shortVideo.name
    private var profileDistance = ProfileTuning.distanceLabel(DefaultProfiles.shortVideo)

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
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> stopCamera()
                // Resume only once the lock screen is gone, otherwise the camera would run (and
                // gestures fire) on the keyguard.
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> startCameraIfUsable()
            }
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
        // Profile edits apply live, without restarting the service.
        lifecycleScope.launch {
            profiles.activeProfile.collect { profile ->
                engine.applyProfile(profile.bindings, ProfileTuning.configFor(profile))
                Log.d(TAG, "Profile applied: ${profile.name} ${profile.bindings}, sensitivity ${profile.sensitivity}")
                profileName = profile.name
                profileDistance = ProfileTuning.distanceLabel(profile)
                if (inForeground) refreshNotification()
            }
        }
        // Test mode borrows the front camera; hand it back when test mode ends.
        lifecycleScope.launch {
            store.testModeActive.collect { active ->
                if (active) stopCamera() else startCameraIfUsable()
            }
        }
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
        store.update { MotionState(running = true, delegate = it.delegate) }
        startCameraIfUsable()
    }

    private fun stopMotion() {
        stopCamera()
        store.update { MotionState() }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        inForeground = false
        stopSelf()
    }

    private fun startCameraIfUsable() {
        if (!inForeground || tracker.isRunning || store.testModeActive.value) return
        val interactive = getSystemService(PowerManager::class.java).isInteractive
        val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
        if (!interactive || locked) return
        tracker.start(this)
        store.update { it.copy(cameraActive = true, error = null) }
    }

    private fun stopCamera() {
        tracker.stop()
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

    private fun refreshNotification() {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(store.state.value.paused))
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
            .setContentText(
                if (paused) "Gestures are ignored. Tap Resume to turn them back on."
                else "Profile: $profileName. Keep your hand $profileDistance from the camera."
            )
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent())
            .addAction(
                0, if (paused) "Resume" else "Pause",
                servicePendingIntent(if (paused) ACTION_RESUME else ACTION_PAUSE),
            )
            .addAction(0, "Stop", servicePendingIntent(ACTION_STOP))
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

        /** Call only while our UI is visible. */
        fun start(context: Context) = ContextCompat.startForegroundService(
            context, Intent(context, MotionService::class.java).setAction(ACTION_START)
        )

        fun stop(context: Context) {
            context.startService(Intent(context, MotionService::class.java).setAction(ACTION_STOP))
        }
    }
}

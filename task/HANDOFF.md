# Handoff: Hands-Free Motion Control App (Android)

Working name: **AirScroll** (rename freely). Package placeholder: `com.example.airscroll`.

This document is the full spec for a native Android app that lets users control other apps (TikTok, Reels, Shorts, etc.) with camera-detected hand gestures, without touching the phone. It is written for a developer or an AI coding agent picking up the project.

---

## 1. Requirements

| # | Requirement | Priority |
|---|---|---|
| R1 | User opens the app, starts motion control, closes the app, and control keeps running while they use other apps | Must |
| R2 | Front camera tracks the user's hand motion | Must |
| R3 | Floating bubble ("head") the user can tap to pause, switch/edit profile, see status | Optional (Phase 5) |
| R4 | User chooses which motion triggers which device action (preferences) | Must |
| R5 | Motion control can be limited to specific apps, with per-app profiles | Optional (Phase 4) |
| R6 | Setting to toggle the floating bubble on/off | Must (with R3) |

Non-goals for v1: iOS, cloud processing, tilt/gyro gestures (require holding the phone).

---

## 2. Tech stack

- Kotlin, Jetpack Compose (Material 3), Coroutines + Flow
- CameraX: `camera-core`, `camera-camera2`, `camera-lifecycle` (`ImageAnalysis` use case)
- MediaPipe Tasks Vision: `com.google.mediapipe:tasks-vision` with `gesture_recognizer.task` in `assets/`
- DataStore Preferences + kotlinx.serialization (profiles stored as JSON)
- `androidx.lifecycle:lifecycle-service` (LifecycleService for CameraX binding in a service)
- DI: Koin or Hilt (developer's choice)

All of these are free and open source (Apache 2.0). All processing is on-device; no network calls, no API keys. Use latest stable versions.

---

## 3. Architecture

```
MainActivity (onboarding, settings)
      │ starts
      ▼
MotionService (foreground, type=camera) ──► GestureEngine ──► ActionDispatcher
      ▲                                         │                    │
      │ re-arm via ArmCameraActivity            │ reads              ▼
FloatingHeadService (overlay bubble)       ProfileRepository    AirScrollAccessibilityService
                                           SettingsRepository    (swipes, taps, global actions,
                                           (DataStore)            foreground-app tracking)
                                                ▲                     │
                                                └── current package ◄─┘
```

### Components

| Component | Responsibility |
|---|---|
| `AirScrollAccessibilityService` | Performs gestures via `dispatchGesture()` and global actions via `performGlobalAction()`. Tracks foreground app from `TYPE_WINDOW_STATE_CHANGED`. Detects when `MotionService` is dead and shows re-arm prompt. |
| `MotionService` | Foreground `LifecycleService` (type `camera`). Binds CameraX `ImageAnalysis`, runs MediaPipe Gesture Recognizer, feeds results to `GestureEngine`. Owns the persistent notification. Returns `START_STICKY`. |
| `GestureEngine` | Converts raw detections into `Motion` events: confidence threshold, hold durations, cooldown, air-swipe detection, state machine. Looks up the active profile and emits `DeviceAction`. |
| `ActionDispatcher` | Executes `DeviceAction` through the accessibility service (or `AudioManager` for volume). |
| `FloatingHeadService` | Draggable overlay bubble with status colors, quick panel, drag-to-× hide. See open decision D1. |
| `ArmCameraActivity` | Translucent, no-UI activity launched from bubble/notification tap. Starts `MotionService` while the app is briefly foreground (satisfies Android 14+ camera FGS rules), then `finish()`. |
| `BubbleController` | Observes `bubbleEnabled` setting and starts/stops `FloatingHeadService` accordingly. Single source of truth. |
| `ProfileRepository` / `SettingsRepository` | DataStore-backed persistence. |
| `BootReceiver` | On `BOOT_COMPLETED`, posts a "Tap to enable motion control" notification. Must NOT start the camera service (disallowed on Android 14+). |

### Suggested package layout

```
com.example.airscroll
├── app/            Application, DI setup
├── ui/             onboarding, home, profiles, profileeditor, testmode, troubleshooting
├── motion/         MotionService, ArmCameraActivity, GestureEngine, AirSwipeDetector, camera/
├── accessibility/  AirScrollAccessibilityService, ActionDispatcher, ForegroundAppTracker
├── overlay/        FloatingHeadService, BubbleController, QuickPanel
├── data/           Profile, Motion, DeviceAction, ProfileRepository, SettingsRepository, DefaultProfiles
└── system/         BootReceiver, BatteryOptimizationHelper, OemGuides
```

---

## 4. Data model

```kotlin
enum class Motion {
    AIR_SWIPE_UP, AIR_SWIPE_DOWN, AIR_SWIPE_LEFT, AIR_SWIPE_RIGHT,
    OPEN_PALM_HOLD, THUMBS_UP, THUMBS_DOWN, FIST_HOLD, VICTORY, POINTING_UP,
}

@Serializable
sealed interface DeviceAction {
    @Serializable data class Swipe(val direction: Direction) : DeviceAction
    @Serializable data object TapCenter : DeviceAction
    @Serializable data object DoubleTapCenter : DeviceAction        // "like" in TikTok/Reels
    @Serializable data class Global(val action: Int) : DeviceAction // BACK, HOME, RECENTS, SCREENSHOT
    @Serializable data object VolumeUp : DeviceAction
    @Serializable data object VolumeDown : DeviceAction
    @Serializable data object TogglePause : DeviceAction            // pauses motion control itself
    @Serializable data object None : DeviceAction
}

enum class Direction { UP, DOWN, LEFT, RIGHT }

@Serializable
data class Profile(
    val id: String,
    val name: String,
    val packages: Set<String>,                  // empty = global/default profile
    val bindings: Map<Motion, DeviceAction>,
    val sensitivity: Float = 0.5f,              // 0..1, maps to swipe distance/confidence thresholds
    val cooldownMs: Long = 800,
)
```

Settings (DataStore Preferences):

| Key | Type | Default |
|---|---|---|
| `bubble_enabled` | Boolean | `true` |
| `motion_enabled` | Boolean | `false` |
| `only_in_selected_apps` | Boolean | `true` |
| `onboarding_done` | Boolean | `false` |

### Gesture source mapping

MediaPipe Gesture Recognizer canned categories: `Closed_Fist`, `Open_Palm`, `Pointing_Up`, `Thumb_Down`, `Thumb_Up`, `Victory`, `ILoveYou`, `None`. Air swipes are NOT a canned category; detect them from hand landmarks (returned by the same recognizer) using `AirSwipeDetector` below.

`OPEN_PALM_HOLD` and `FIST_HOLD` require ~1 s continuous detection to avoid accidental triggers.

### Default profiles (ship these)

| Profile | Packages | Bindings |
|---|---|---|
| Short video | `com.zhiliaoapp.musically`, `com.ss.android.ugc.trill`, `com.instagram.android`, `com.google.android.youtube`, `com.facebook.katana` | AIR_SWIPE_UP → Swipe(UP) (next), AIR_SWIPE_DOWN → Swipe(DOWN) (previous), OPEN_PALM_HOLD → TapCenter (pause/play), THUMBS_UP → DoubleTapCenter (like), FIST_HOLD → TogglePause |
| Global (fallback, disabled when `only_in_selected_apps`) | empty | AIR_SWIPE_UP → Swipe(UP), AIR_SWIPE_DOWN → Swipe(DOWN), FIST_HOLD → TogglePause |

Note: `Swipe(UP)` means the finger moves up the screen (content scrolls to next item).

---

## 5. Key implementation details

### 5.1 Gesture injection

```kotlin
class AirScrollAccessibilityService : AccessibilityService() {

    fun swipe(direction: Direction, durationMs: Long = 250) {
        val dm = resources.displayMetrics
        val w = dm.widthPixels.toFloat(); val h = dm.heightPixels.toFloat()
        val path = Path().apply {
            when (direction) {
                Direction.UP    -> { moveTo(w / 2, h * 0.75f); lineTo(w / 2, h * 0.25f) }
                Direction.DOWN  -> { moveTo(w / 2, h * 0.25f); lineTo(w / 2, h * 0.75f) }
                Direction.LEFT  -> { moveTo(w * 0.8f, h / 2); lineTo(w * 0.2f, h / 2) }
                Direction.RIGHT -> { moveTo(w * 0.2f, h / 2); lineTo(w * 0.8f, h / 2) }
            }
        }
        dispatch(GestureDescription.StrokeDescription(path, 0, durationMs))
    }

    fun tapCenter(times: Int = 1) {
        val dm = resources.displayMetrics
        val builder = GestureDescription.Builder()
        repeat(times) { i ->
            val p = Path().apply { moveTo(dm.widthPixels / 2f, dm.heightPixels / 2f) }
            builder.addStroke(GestureDescription.StrokeDescription(p, i * 120L, 40))
        }
        dispatchGesture(builder.build(), null, null)
    }

    private fun dispatch(stroke: GestureDescription.StrokeDescription) =
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            e.packageName?.toString()?.let(ForegroundAppTracker::update)
        }
    }

    override fun onInterrupt() {}
}
```

`res/xml/accessibility_service_config.xml`:

```xml
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowStateChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:canPerformGestures="true"
    android:canRetrieveWindowContent="false"
    android:notificationTimeout="100"
    android:description="@string/accessibility_description" />
```

Ignore window events from your own package and from the system UI / keyboard so the bubble or IME doesn't change the active profile.

### 5.2 Air-swipe detection

Feed wrist (landmark 0) or index fingertip (landmark 8) normalized coordinates every frame.

```kotlin
class AirSwipeDetector(
    var minDy: Float = 0.25f,     // fraction of frame height; tie to profile.sensitivity
    var maxDx: Float = 0.15f,
    private val windowMs: Long = 450,
    var cooldownMs: Long = 800,
) {
    private data class Sample(val x: Float, val y: Float, val t: Long)
    private val buf = ArrayDeque<Sample>()
    private var lastFire = 0L

    fun onFrame(x: Float, y: Float, now: Long): Motion? {
        buf.addLast(Sample(x, y, now))
        while (buf.isNotEmpty() && now - buf.first().t > windowMs) buf.removeFirst()
        if (now - lastFire < cooldownMs || buf.size < 4) return null

        val dy = buf.last().y - buf.first().y
        val dx = buf.last().x - buf.first().x
        val result = when {
            kotlin.math.abs(dx) <= maxDx && dy < -minDy -> Motion.AIR_SWIPE_UP
            kotlin.math.abs(dx) <= maxDx && dy >  minDy -> Motion.AIR_SWIPE_DOWN
            // Left/right: front camera image is mirrored, so invert dx if needed
            else -> null
        }
        if (result != null) { lastFire = now; buf.clear() }
        return result
    }

    fun reset() = buf.clear()
}
```

### 5.3 GestureEngine rules

- State machine: `IDLE → ARMED → TRIGGERED → COOLDOWN → IDLE`.
- Require a hand present for ≥ 3 consecutive frames before entering `ARMED` (filters people walking past).
- Discard detections below confidence threshold (start at 0.6; scale by sensitivity).
- Hold gestures fire once per hold; user must drop the pose before it can fire again.
- After any action: cooldown from active profile (`cooldownMs`).
- When paused (TogglePause), only listen for the TogglePause motion.

### 5.4 MotionService

- Extend `LifecycleService`; bind CameraX to `this`.
- `ImageAnalysis`: front camera, target resolution ~320×240, `STRATEGY_KEEP_ONLY_LATEST`, output RGBA_8888.
- MediaPipe Gesture Recognizer in `LIVE_STREAM` mode, GPU delegate with CPU fallback.
- Must be started while app UI is visible (MainActivity or ArmCameraActivity).
- Persistent notification with actions: **Pause/Resume** always; **Show bubble** when `bubble_enabled == false`.
- Battery rules:
  - Stop analysis when screen is off (`ACTION_SCREEN_OFF` / `ACTION_SCREEN_ON`).
  - Stop analysis when current foreground package has no profile and `only_in_selected_apps` is true; release the camera entirely, not just skip frames.
  - Throttle to ~3 fps after 5 s with no hand detected; return to full rate when a hand appears.

```kotlin
private fun buildNotification(paused: Boolean, bubbleOn: Boolean): Notification =
    NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_motion)
        .setContentTitle(if (paused) "Motion control paused" else "Motion control active")
        .setOngoing(true)
        .addAction(0, if (paused) "Resume" else "Pause",
            servicePendingIntent(if (paused) ACTION_RESUME else ACTION_PAUSE))
        .apply {
            if (!bubbleOn) addAction(0, "Show bubble", servicePendingIntent(ACTION_SHOW_BUBBLE))
        }
        .build()
```

`ACTION_SHOW_BUBBLE` sets `bubble_enabled = true`; if the overlay permission was revoked, open the app's settings screen instead.

### 5.5 Floating bubble + toggle setting

Behavior:
- Status colors: grey = paused or camera off, green = hand detected, brief flash = gesture fired.
- Tap: if camera not running → launch `ArmCameraActivity`. Otherwise → open quick panel (pause/resume, active profile + edit shortcut, sensitivity slider).
- Drag to edge snaps; drag onto "×" target sets `bubble_enabled = false` and shows a one-time toast "Bubble hidden. Turn it back on in Settings."
- The settings switch, the × target, and the notification action all write the same `bubble_enabled` key. `BubbleController` is the only thing that starts/stops the service.

```kotlin
class SettingsRepository(private val dataStore: DataStore<Preferences>) {
    val bubbleEnabled: Flow<Boolean> = dataStore.data.map { it[BUBBLE_ENABLED] ?: true }
    suspend fun setBubbleEnabled(enabled: Boolean) {
        dataStore.edit { it[BUBBLE_ENABLED] = enabled }
    }
    companion object { val BUBBLE_ENABLED = booleanPreferencesKey("bubble_enabled") }
}

class BubbleController(
    private val context: Context,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch {
            settings.bubbleEnabled.distinctUntilChanged().collect { enabled ->
                val intent = Intent(context, FloatingHeadService::class.java)
                if (enabled && Settings.canDrawOverlays(context)) context.startService(intent)
                else context.stopService(intent)
            }
        }
    }
}
```

Settings row (Compose):

```kotlin
@Composable
fun BubbleSettingRow(settings: SettingsRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val enabled by settings.bubbleEnabled.collectAsState(initial = true)

    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { scope.launch { settings.setBubbleEnabled(Settings.canDrawOverlays(context)) } }

    ListItem(
        headlineContent = { Text("Show floating bubble") },
        supportingContent = { Text("Quick access to pause, profiles, and status") },
        trailingContent = {
            Switch(
                checked = enabled && Settings.canDrawOverlays(context),
                onCheckedChange = { checked ->
                    if (checked && !Settings.canDrawOverlays(context)) {
                        overlayLauncher.launch(Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        ))
                    } else scope.launch { settings.setBubbleEnabled(checked) }
                }
            )
        }
    )
}
```

### 5.6 Per-app profiles

- On foreground package change: find profile whose `packages` contains it → else global profile (if `only_in_selected_apps == false`) → else release camera.
- App picker lists launchable apps via `<queries>` (below). Do NOT use `QUERY_ALL_PACKAGES`.

---

## 6. Manifest

```xml
<uses-feature android:name="android.hardware.camera.front" android:required="true" />

<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CAMERA" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

<queries>
    <intent>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent>
</queries>

<application ...>
    <service
        android:name=".motion.MotionService"
        android:foregroundServiceType="camera"
        android:exported="false" />

    <service
        android:name=".overlay.FloatingHeadService"
        android:exported="false" />

    <service
        android:name=".accessibility.AirScrollAccessibilityService"
        android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
        android:exported="false">
        <intent-filter>
            <action android:name="android.accessibilityservice.AccessibilityService" />
        </intent-filter>
        <meta-data
            android:name="android.accessibilityservice"
            android:resource="@xml/accessibility_service_config" />
    </service>

    <activity
        android:name=".motion.ArmCameraActivity"
        android:theme="@android:style/Theme.Translucent.NoTitleBar"
        android:excludeFromRecents="true"
        android:exported="false" />

    <receiver android:name=".system.BootReceiver" android:exported="false">
        <intent-filter>
            <action android:name="android.intent.action.BOOT_COMPLETED" />
        </intent-filter>
    </receiver>
</application>
```

---

## 7. Screens

| Screen | Purpose |
|---|---|
| Onboarding | Explain app; request permissions one at a time with a reason each: camera → notifications → accessibility (with prominent disclosure) → overlay (only if bubble on) → battery optimization |
| Home | Start/stop toggle, live status, active profile, bubble toggle |
| Profiles | List; create/delete; assign apps |
| Profile editor | Motion → action dropdowns, sensitivity, cooldown, conflict warnings |
| Test mode | Camera preview with landmark overlay, recognized gesture + confidence, live dx/dy; no actions fired |
| Troubleshooting | Permission status re-check, brand-specific battery/auto-start guides (Oppo, Vivo, Xiaomi, Realme, Samsung; see dontkillmyapp.com) |

---

## 8. Android constraints and gotchas

- **Android 14+ camera FGS:** must start while app is visible; cannot start from `BOOT_COMPLETED`. Use `ArmCameraActivity` for re-arm from bubble/notification.
- **Camera privacy indicator** (green dot) is always visible while running. Tell users up front.
- **OEM killers** (very common devices in Cambodia: Oppo, Vivo, Xiaomi, Realme, Samsung) will kill services unless the user changes settings. Troubleshooting screen is required, not optional.
- **Service recovery:** `START_STICKY`; accessibility service watches for `MotionService` death and shows re-arm notification/bubble.
- **Front camera mirroring** affects left/right swipes.
- **Accessibility gesture coordinates** are in screen pixels; handle rotation and display cutouts.

## 9. Play Store policy

- AccessibilityService use requires the Play Console Permissions Declaration and a prominent in-app disclosure before sending users to accessibility settings.
- Frame and build the app around hands-free use (motor impairments, hands busy). Only set `isAccessibilityTool` if accessibility is genuinely the primary purpose.
- Privacy policy must state camera frames are processed on-device and never stored or uploaded. Keep it true: no network permission needed for core features.
- For personal use / beta, sideloading the APK avoids review until ready.

---

## 10. Build phases and acceptance criteria

| Phase | Deliverable | Done when |
|---|---|---|
| 1 | Accessibility service + test buttons in MainActivity | Tapping "Next" in the app, then switching to TikTok within 3 s (via delayed trigger), scrolls TikTok |
| 2 | `MotionService` with CameraX + Gesture Recognizer + `AirSwipeDetector`; hard-coded swipe up → next; Pause/Resume notification actions | Air swipe scrolls TikTok after the app is swiped out of recents |
| 3 | `Profile` model, DataStore, profile editor, test mode | User remaps a motion and the new mapping works without restarting the service |
| 4 | Foreground-app detection, per-app profiles, default profiles, camera released for unprofiled apps | Camera dot disappears when leaving TikTok for an unprofiled app and returns on re-entry |
| 5 | Floating bubble, quick panel, `ArmCameraActivity` re-arm, bubble toggle setting | Switch, × target, and notification action all stay in sync; bubble tap re-arms camera after reboot |
| 6 | Boot receiver, OEM guides, battery tuning, Play disclosures | 30 min continuous use without false triggers, service death, or overheating on at least one Oppo/Vivo/Xiaomi device |

Build a debug overlay (landmarks + live dx/dy + state machine state) early in Phase 2; threshold tuning is the biggest time sink.

---

## 11. Open decisions

- **D1 — Where to host the bubble.** A plain started `FloatingHeadService` may be stopped by Android's background service limits on some devices. Alternatives: (a) host the bubble view inside `AirScrollAccessibilityService` using `TYPE_ACCESSIBILITY_OVERLAY`, which needs no overlay permission and lives as long as the accessibility service (recommended to evaluate first; the bubble toggle then skips the overlay-permission flow); (b) host it inside `MotionService` (but then no bubble when the camera is off, so no re-arm via bubble). Decide in Phase 5 after testing on target devices.
- **D2 — Proximity-sensor wave** as a no-camera fallback mode (cheap, battery-friendly, 1–2 commands). Not in v1 scope; easy add later.
- **D3 — Head/face gestures** (MediaPipe Face Landmarker: nod, long blink) as an alternate input mode. Post-v1.
- **D4 — DI framework:** Koin vs Hilt.

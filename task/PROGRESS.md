# Progress

Status of the build phases in [SPEC.md](SPEC.md) §10. Update this file when a phase starts, finishes or changes plan.

| Phase | What | Status | Commit |
|---|---|---|---|
| 1 | Accessibility service + test buttons | Done | `c276c5a` |
| 2 | `MotionService`, camera, air swipes, Pause/Resume notification | Done | `c276c5a` |
| 3 | Profiles, profile editor, Test mode | Done | `c149211` |
| 4 | Per-app profiles, camera released in other apps | Done | `03051e7` |
| 5 | Floating bubble, quick panel, bubble toggle | Done | `fdcfbbb` |
| 6 | OEM guides, battery tuning | Built; 30-min test pending | `d8e6d8f` |
| 7 | Custom poses | Not started (spec only) | – |
| 8 | Quick Settings tile | Done | `1a51faf` |

Phases 1–4 were built before this file existed; their device checks weren't recorded.

## Phase 1: Accessibility service

Done 2026-10-22 (`c276c5a`, with Phase 2).

- `AirScrollAccessibilityService` performs swipes, taps and back/home through `ActionDispatcher`.
- Home shows a prominent disclosure before sending the user to accessibility settings.
- Done when (SPEC §10): tapping "Next" in the app, then switching to TikTok within 3 s, scrolls TikTok.

## Phase 2: Motion service and air swipes

Done 2026-10-22 (`c276c5a`).

- `MotionService`: camera foreground service with CameraX and the MediaPipe gesture recognizer (GPU with CPU fallback), `AirSwipeDetector` and `GestureEngine`.
- Notification with Pause/Resume and Stop.
- Camera off while the screen is off or locked.
- Release builds use R8, with keep rules for MediaPipe and Flogger (`c34ef80`, `479eaca`).
- Decision (§12): motion control never starts on its own: `START_NOT_STICKY`, no boot receiver.
- Done when (SPEC §10): an air swipe scrolls TikTok after the app is swiped out of recents.

## Phase 3: Profiles, editor and Test mode

Done 2026-10-22 (`c149211`).

- `Profile` stored as JSON in DataStore; profile list and editor (bindings, sensitivity, cooldown, conflict warnings).
- Test mode: live hand view and readout, no actions fired; shows only the hand view, no camera preview (`e9fc034`).
- Hand distance range adjustable per profile (`b6b21da`).
- Decisions (§12): FIST_HOLD → play/pause by default, not TogglePause; working range 30 cm – 1 m by default.
- Done when (SPEC §10): the user remaps a motion and the new mapping works without restarting the service.

## Phase 4: Per-app profiles

Done 2026-10-22 (`03051e7`).

- The open app is tracked from accessibility events (`ForegroundAppTracker`), and the matching profile applies live.
- App picker; "Use in all apps" with a default profile; the camera is released in apps without a profile.
- Done when (SPEC §10): the camera dot disappears when leaving TikTok for an app without a profile and returns on re-entry.

## Phase 5: Floating bubble

Done 2026-10-30 (`fdcfbbb`). Unit tests, build and lint pass; checked on device.

- D1 is decided (§12): the bubble lives in `AirScrollAccessibilityService` as a `TYPE_ACCESSIBILITY_OVERLAY` window, so there's no `FloatingHeadService`, no `SYSTEM_ALERT_WINDOW` and no overlay-permission flow.
- Tapping the bubble while motion control is off starts it through `ArmCameraActivity`, built in Phase 8.
- The quick panel is an overlay window, not an activity, so TikTok keeps playing while it's open (user decision, 2026-10-30).
- What was built is described in SPEC.md §5.5 ("As built").

Checklist:
- [x] `bubble_enabled` setting (default on) in `SettingsRepository`
- [x] Bubble overlay: status colors, drag, snap to edge, hidden on the lock screen
- [x] "×" drop target that turns the bubble off, with a one-time toast
- [x] Quick panel: pause/resume, stop, active profile with an edit shortcut, sensitivity slider
- [x] "Floating bubble" switch on Home
- [x] "Show bubble" notification action while the bubble is off
- [x] Unit tests for the pure parts (status colors, snapping, drop target, settings)
- [x] Device check: switch, × target and notification action stay in sync; bubble tap starts motion control after a reboot
- [x] Device check: colors over TikTok, quick panel (Back, tap outside, slider, Edit), rotation, lock screen

## Phase 6: Reliability and battery

Built 2026-10-30: Troubleshooting, battery exemption and onboarding. Unit tests, build and lint pass; checked on device. Only the 30-minute test below is left.

**Not publishing on the Play Store** (user decision, 2026-10-30): the app is installed directly (APK). Play-only work (privacy policy, Play Console Permissions Declaration, SPEC §9) is out of scope. Revisit only if that changes.

Already in place from earlier phases:
- [x] ~3 fps when no hand has been seen for a while, ~15 fps with a hand (`HandTracker`)
- [x] Camera released while the screen is off or locked, and in apps without a profile
- [x] Prominent accessibility disclosure before opening accessibility settings
- [x] No network permission (`INTERNET` and `ACCESS_NETWORK_STATE` removed in the manifest)
- [x] ~~Boot receiver~~: dropped, motion control never starts on its own (§12)

To do:
- [x] Troubleshooting screen (§7, from Home): camera, notifications, accessibility (incl. "switched on but not running"), battery optimization, each re-checked on resume with a fix button
- [x] Battery / auto-start guide per brand (`OemGuides`: Xiaomi/Redmi/POCO, Oppo/Realme/OnePlus, Vivo/iQOO, Samsung, other), with a button that tries the brand's auto-start screen, falling back to app info
- [x] Ask to be excluded from battery optimization (`BatteryOptimization`); Home warns while the app is restricted
- [x] Device check: Troubleshooting statuses update after fixing each one in Settings; "Open auto-start settings" opens the right page (or app info) on the test phone
- [x] Onboarding (§7): welcome, then camera → notifications → accessibility (doubles as the disclosure) → battery, one at a time with a reason; set-up steps are skipped, every step can be skipped (`onboarding_done`)
- [x] Android 13+ "restricted settings" hint (accessibility switch greyed out for apps installed from a file) in onboarding and Troubleshooting
- [x] Device check: onboarding on a fresh install (clear data), including the restricted-settings path
- [ ] Done when (SPEC §10): 30 min of continuous use without false triggers, the service being killed, or overheating, on at least one Oppo, Vivo or Xiaomi phone

## Phase 7: Custom poses

Not started; the design is in SPEC.md §5.7.

Decide first (SPEC §11):
- [ ] D5: shared pose library (recommended) or poses owned by one profile
- [ ] D6: image landmarks or world landmarks; record the same poses both ways on a device to choose
- [ ] D7: quick stable pose (~0.3 s) or 1 s hold, possibly per pose

To do:
- [ ] `CustomPose` model and storage in DataStore
- [ ] Profile bindings keyed by string (`"AIR_SWIPE_UP"`, `"custom:<id>"`); existing profiles load unchanged
- [ ] Landmark normalization and nearest-match recognition, fed into `GestureEngine`
- [ ] "Custom poses" screen: record (2 × ~2 s), live match %, rename, re-record, delete; limit ~10
- [ ] Warning when a new pose is too close to a built-in or another custom pose
- [ ] Custom poses in the profile editor and Test mode
- [ ] Deleting a pose removes its bindings from every profile
- [ ] Done when (SPEC §10): a new pose (e.g. 🤟) is recorded in under 30 s, bound to an action, and fires in TikTok without restarting motion control; in 2 minutes of normal hand movement and built-in poses it never fires by mistake

## Phase 8: Quick Settings tile

Done 2026-10-30 (`1a51faf`).

- The tile starts motion control through the invisible `ArmCameraActivity`, because Android 14+ only allows a camera foreground service to start while one of the app's activities is visible.
- Decision D9 (§12): the tile starts and stops motion control; pause stays in the notification and the bubble.
- Checked on device: the invisible `ArmCameraActivity` is unnoticeable when starting from the tile over another app.
- Not yet checked on device: stopping from the tile, "Paused" on the tile, starting again after the app is swiped away, "Set up in app" with accessibility off, long-press, re-adding after removing the tile.

## Other open items

- Open decisions D2, D3, D5–D8 (SPEC.md §11) are still open; D1, D4 and D9 are decided (§12).
- `.idea/.name` is untracked and deliberately not committed (IDE file).

# Progress

Status of the build phases in [SPEC.md](SPEC.md) §10. Update this file when a phase starts, finishes or changes plan.

| Phase | What | Status | Commit |
|---|---|---|---|
| 1 | Accessibility service + test buttons | Done | `c276c5a` |
| 2 | `MotionService`, camera, air swipes, Pause/Resume notification | Done | `c276c5a` |
| 3 | Profiles, profile editor, Test mode | Done | `c149211` |
| 4 | Per-app profiles, camera released in other apps | Done | `03051e7` |
| 5 | Floating bubble, quick panel, bubble toggle | **In progress** | – |
| 6 | OEM guides, battery tuning, Play disclosures | Not started | – |
| 7 | Custom poses | Not started (spec only) | – |
| 8 | Quick Settings tile | Done | `1a51faf` |

## Phase 8: Quick Settings tile

- Done 2026-10-30.
- Checked on device: the invisible `ArmCameraActivity` is unnoticeable when starting from the tile over another app.
- Not yet checked on device: stopping from the tile, "Paused" on the tile, starting again after the app is swiped away, "Set up in app" with accessibility off, long-press, re-adding after removing the tile.

## Phase 5: Floating bubble (in progress)

Started 2026-10-30. No code yet.

- D1 is decided (§12): the bubble lives in `AirScrollAccessibilityService` as a `TYPE_ACCESSIBILITY_OVERLAY` window, so there's no `FloatingHeadService`, no `SYSTEM_ALERT_WINDOW` and no overlay-permission flow.
- Tapping the bubble while motion control is off starts it through `ArmCameraActivity`, built in Phase 8.

To do:
- [ ] `bubble_enabled` setting (default on) in `SettingsRepository`
- [ ] Bubble overlay: status colors (grey off/paused, green hand detected, flash on a gesture), drag, snap to edge
- [ ] "×" drop target that turns the bubble off, with a one-time toast
- [ ] Quick panel: pause/resume, active profile with an edit shortcut, sensitivity slider
- [ ] "Show floating bubble" switch on Home
- [ ] "Show bubble" notification action while the bubble is off
- [ ] Unit tests for the pure parts (status colors, snapping, drop target)
- [ ] Device check: switch, × target and notification action stay in sync; bubble tap starts motion control after a reboot

## Other open items

- Open decisions D2, D3, D5–D8 (SPEC.md §11) are still open; D1, D4 and D9 are decided (§12).

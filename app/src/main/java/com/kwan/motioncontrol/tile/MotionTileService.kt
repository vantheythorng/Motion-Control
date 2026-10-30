package com.kwan.motioncontrol.tile

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.core.content.ContextCompat
import com.kwan.motioncontrol.MainActivity
import com.kwan.motioncontrol.accessibility.AirScrollAccessibilityService
import com.kwan.motioncontrol.motion.ArmCameraActivity
import com.kwan.motioncontrol.motion.MotionService
import com.kwan.motioncontrol.motion.MotionStateStore
import com.kwan.motioncontrol.system.AccessibilityStatus
import com.kwan.motioncontrol.system.TileStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * "Motion Control" tile in Quick Settings: starts or stops motion control (D9). Not an active
 * tile: the system binds it whenever the shade opens, so a fresh process correctly shows Off.
 * It never starts anything on its own; only a tap does.
 */
class MotionTileService : TileService() {

    private val store: MotionStateStore by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var listening: Job? = null

    override fun onTileAdded() {
        TileStatus.setAdded(this, true)
    }

    override fun onTileRemoved() {
        TileStatus.setAdded(this, false)
    }

    override fun onStartListening() {
        // The tile is in the user's tiles if it's listening; repairs the flag after "clear data".
        TileStatus.setAdded(this, true)
        // Show the real state at once, so the default (active) look never flashes.
        render()
        listening?.cancel()
        listening = scope.launch {
            // The bound accessibility service is only a trigger; the setting decides (see render).
            combine(store.state, AirScrollAccessibilityService.instance) { _, _ -> }.collect { render() }
        }
    }

    override fun onStopListening() {
        listening?.cancel()
        listening = null
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        if (isLocked) unlockAndRun(::toggle) else toggle()
    }

    private fun toggle() {
        if (store.state.value.running) {
            try {
                MotionService.stop(this)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Couldn't stop motion control from the tile", e)
            }
            return
        }
        val hasCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        // Setup happens in the app's normal task; otherwise start without leaving the open app.
        val target = if (hasCamera && AccessibilityStatus.isEnabled(this)) ArmCameraActivity::class.java
        else MainActivity::class.java
        launch(Intent(this, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun launch(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // From Android 14 only the PendingIntent overload is allowed (the Intent one throws).
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun render() {
        val tile = qsTile ?: return
        val content = TileContent.from(store.state.value, AccessibilityStatus.isEnabled(this))
        tile.state = if (content.active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = content.subtitle
        tile.updateTile()
    }

    private companion object {
        const val TAG = "MotionControl"
    }
}

package com.kwan.motioncontrol.motion

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.kwan.motioncontrol.MainActivity
import com.kwan.motioncontrol.system.AccessibilityStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Invisible activity that starts motion control from outside the app (the Quick Settings tile,
 * later the bubble). Android 14+ only allows starting a camera foreground service while one of
 * our activities is visible, so this one stays up until the service reports it's running, then
 * finishes and the user is back in the app they were using.
 */
class ArmCameraActivity : ComponentActivity() {

    private val store: MotionStateStore by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return finishQuietly()

        val hasCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        val setupOk = hasCamera && AccessibilityStatus.isEnabled(this)
        if (setupOk) {
            // A leftover error from an earlier failed start must not end this attempt early.
            store.update { it.copy(error = null) }
            MotionService.start(this)
        }
        val startedAt = SystemClock.uptimeMillis()
        lifecycleScope.launch {
            while (true) {
                when (ArmCameraDecision.next(setupOk, store.state.value, SystemClock.uptimeMillis() - startedAt)) {
                    ArmStep.WAIT -> delay(50)
                    ArmStep.FINISH -> return@launch finishQuietly()
                    ArmStep.OPEN_APP -> {
                        // The app shows what's missing or the start error.
                        startActivity(Intent(this@ArmCameraActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        return@launch finishQuietly()
                    }
                }
            }
        }
    }

    private fun finishQuietly() {
        finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}

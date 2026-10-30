package com.kwan.motioncontrol.ui.onboarding

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kwan.motioncontrol.R
import com.kwan.motioncontrol.system.AccessibilityStatus
import com.kwan.motioncontrol.system.BatteryOptimization
import com.kwan.motioncontrol.system.OemGuides
import com.kwan.motioncontrol.ui.common.RestrictedSettingsHint
import com.kwan.motioncontrol.ui.common.rememberOnResume

/**
 * First run: what the app does, then each permission with its reason, one at a time (SPEC §7).
 * Steps already set up are skipped, every step can be skipped, and a step moves on by itself
 * once the user has granted it (re-checked whenever the screen resumes).
 */
// POST_NOTIFICATIONS: the notifications step is only reached on Android 13+; below that,
// setupStatus() counts notifications as set up and the step is skipped.
@SuppressLint("InlinedApi")
@Composable
fun OnboardingScreen(onFinish: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val status = rememberOnResume { context.setupStatus() }
    var step by rememberSaveable { mutableStateOf(OnboardingStep.WELCOME) }
    fun next() {
        step = Onboarding.next(step, context.setupStatus())
    }
    LaunchedEffect(step, status) {
        if (Onboarding.isSatisfied(step, status)) next()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (step) {
            OnboardingStep.WELCOME -> Welcome(onNext = ::next)
            OnboardingStep.CAMERA -> PermissionStep(
                title = "Camera",
                reason = "Motion control watches your hand with the front camera. " +
                    "Frames are processed on this phone and never stored or uploaded.",
                permission = Manifest.permission.CAMERA,
                allowLabel = "Allow camera",
                onSkip = ::next,
            )
            OnboardingStep.NOTIFICATIONS -> PermissionStep(
                title = "Notifications",
                reason = "While motion control runs, its notification shows what it's doing and has Pause and Stop.",
                permission = Manifest.permission.POST_NOTIFICATIONS,
                allowLabel = "Allow notifications",
                onSkip = ::next,
            )
            OnboardingStep.ACCESSIBILITY -> AccessibilityStep(onSkip = ::next)
            OnboardingStep.BATTERY -> BatteryStep(onSkip = ::next)
            OnboardingStep.DONE -> Finished(onFinish)
        }
    }
}

@Composable
private fun Welcome(onNext: () -> Unit) {
    Title("Motion Control")
    Body("Control apps like TikTok, Reels and Shorts with hand gestures in front of the camera, without touching the phone.")
    Body("• Camera frames are processed on this phone and never stored or uploaded.")
    Body("• The camera indicator stays on while motion control runs.")
    Body("• It only runs after you start it, and never starts on its own.")
    Body("Next, Motion Control asks for a few permissions, each with the reason.")
    Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Get started") }
}

@Composable
private fun PermissionStep(title: String, reason: String, permission: String, allowLabel: String, onSkip: () -> Unit) {
    val context = LocalContext.current
    var refused by remember { mutableStateOf(false) }
    // Granting resumes the screen, and the onboarding moves on from there.
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> refused = !ok }
    Title(title)
    Body(reason)
    if (refused) {
        Body("Not allowed. You can allow it later in App info, or from Troubleshooting on the Home screen.")
        OutlinedButton(onClick = { OemGuides.openAppInfo(context) }, modifier = Modifier.fillMaxWidth()) {
            Text("Open app info")
        }
    } else {
        Button(onClick = { launcher.launch(permission) }, modifier = Modifier.fillMaxWidth()) { Text(allowLabel) }
    }
    Skip(onSkip)
}

/** The accessibility step doubles as the prominent disclosure (SPEC §9). */
@Composable
private fun AccessibilityStep(onSkip: () -> Unit) {
    val context = LocalContext.current
    Title("Accessibility service")
    Body(stringResource(R.string.accessibility_description))
    Body("In the list, open Motion Control (under Installed or Downloaded apps) and turn it on.")
    Button(
        onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Agree and open settings") }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RestrictedSettingsHint()
    Skip(onSkip)
}

@Composable
private fun BatteryStep(onSkip: () -> Unit) {
    val context = LocalContext.current
    val guide = remember { OemGuides.forThisDevice() }
    Title("Keep it running")
    Body(
        "Android may stop motion control in the background to save battery. " +
            "Allowing it to run keeps gestures working while you use other apps."
    )
    if (guide != OemGuides.other) {
        Body("${guide.brand} phones need a few more settings; Troubleshooting on the Home screen lists them.")
    }
    Button(onClick = { BatteryOptimization.requestExemption(context) }, modifier = Modifier.fillMaxWidth()) {
        Text("Allow")
    }
    Skip(onSkip)
}

@Composable
private fun Finished(onFinish: () -> Unit) {
    Title("You're set")
    Body("Tap Start motion control on the Home screen. You can also start it from the Quick Settings tile.")
    Body("For quick controls over other apps, turn on the floating bubble on the Home screen.")
    Body("Troubleshooting on the Home screen checks all of this again, any time.")
    Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) { Text("Done") }
}

@Composable
private fun Title(text: String) = Text(text, style = MaterialTheme.typography.headlineSmall)

@Composable
private fun Body(text: String) = Text(text, style = MaterialTheme.typography.bodyMedium)

@Composable
private fun Skip(onSkip: () -> Unit) {
    TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) { Text("Not now") }
}

private fun Context.setupStatus() = SetupStatus(
    camera = has(Manifest.permission.CAMERA),
    notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || has(Manifest.permission.POST_NOTIFICATIONS),
    accessibility = AccessibilityStatus.isEnabled(this),
    batteryExempt = BatteryOptimization.isExempt(this),
)

private fun Context.has(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

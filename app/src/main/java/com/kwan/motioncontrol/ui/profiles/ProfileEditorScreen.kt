package com.kwan.motioncontrol.ui.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kwan.motioncontrol.data.DeviceAction
import com.kwan.motioncontrol.data.Motion
import com.kwan.motioncontrol.data.Profile
import com.kwan.motioncontrol.data.label
import com.kwan.motioncontrol.motion.ProfileTuning
import com.kwan.motioncontrol.ui.common.ChoiceDropdown
import com.kwan.motioncontrol.ui.common.ScreenHeader
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Every change is saved immediately through [onChange], so it applies live to motion control. */
@Composable
fun ProfileEditorScreen(
    profile: Profile?,
    isActive: Boolean,
    onBack: () -> Unit,
    onChange: (Profile) -> Unit,
    onMakeActive: () -> Unit,
    onTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ScreenHeader(profile?.name ?: "Profile", onBack)
        if (profile == null) {
            Text("This profile no longer exists.")
            return@Column
        }

        if (!isActive) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Not the active profile, so edits don't affect motion control yet.", Modifier.weight(1f))
                    TextButton(onClick = onMakeActive) { Text("Make active") }
                }
            }
        }
        ProfileConflicts.check(profile).forEach { warning ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(warning, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }

        // Local state for the text field so typing isn't disturbed by the save round trip.
        var name by rememberSaveable(profile.id) { mutableStateOf(profile.name) }
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                if (it.isNotBlank()) onChange(profile.copy(name = it.trim()))
            },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Gestures", style = MaterialTheme.typography.titleMedium)
        Motion.entries.forEach { motion ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(motion.label, Modifier.weight(1f))
                ChoiceDropdown(
                    selected = profile.bindings[motion] ?: DeviceAction.None,
                    options = DeviceAction.choices,
                    optionLabel = { it.label },
                    onSelect = { onChange(profile.withBinding(motion, it)) },
                    modifier = Modifier.weight(1.3f),
                )
            }
        }
        HorizontalDivider()

        // Sliders keep local state while dragging and save when released.
        var sensitivity by rememberSaveable(profile.id) { mutableFloatStateOf(profile.sensitivity) }
        Text(
            "Sensitivity: ${(sensitivity * 100).roundToInt()} %",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "Higher accepts smaller swipes, less certain poses and shorter holds. 50 % is the tuned default.",
            style = MaterialTheme.typography.bodySmall,
        )
        Slider(
            value = sensitivity,
            onValueChange = { sensitivity = (it * 20).roundToInt() / 20f }, // 0.05 steps
            onValueChangeFinished = { onChange(profile.copy(sensitivity = sensitivity)) },
            valueRange = 0f..1f,
            steps = 19,
        )

        var cooldown by rememberSaveable(profile.id) { mutableFloatStateOf(profile.cooldownMs.toFloat()) }
        Text(
            "Pause between actions: ${String.format(Locale.US, "%.1f", cooldown / 1000)} s",
            style = MaterialTheme.typography.titleMedium,
        )
        Slider(
            value = cooldown,
            onValueChange = { cooldown = (it / 100).roundToInt() * 100f },
            onValueChangeFinished = { onChange(profile.copy(cooldownMs = cooldown.roundToLong())) },
            valueRange = 300f..2000f,
            steps = 16,
        )

        val (savedMin, savedMax) = ProfileTuning.distanceRange(profile)
        var minCm by rememberSaveable(profile.id) { mutableFloatStateOf(savedMin) }
        var maxCm by rememberSaveable(profile.id) { mutableFloatStateOf(savedMax) }
        Text(
            "Hand distance: ${ProfileTuning.distanceLabel(profile.copy(minDistanceCm = minCm, maxDistanceCm = maxCm))}",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "Hands closer or farther than this are ignored, e.g. people behind you. The distance is " +
                "estimated from the size of your hand, so it can be off by about 20 %; check it with " +
                "Test this profile.",
            style = MaterialTheme.typography.bodySmall,
        )
        RangeSlider(
            value = minCm..maxCm,
            onValueChange = { range ->
                // 5 cm steps, and never narrower than the minimum span.
                val lo = (range.start / 5).roundToInt() * 5f
                val hi = (range.endInclusive / 5).roundToInt() * 5f
                if (hi - lo >= Profile.MIN_DISTANCE_SPAN_CM) {
                    minCm = lo
                    maxCm = hi
                }
            },
            onValueChangeFinished = {
                onChange(profile.copy(minDistanceCm = minCm, maxDistanceCm = maxCm))
            },
            valueRange = Profile.DISTANCE_LIMIT_MIN_CM..Profile.DISTANCE_LIMIT_MAX_CM,
            steps = ((Profile.DISTANCE_LIMIT_MAX_CM - Profile.DISTANCE_LIMIT_MIN_CM) / 5).toInt() - 1,
        )

        OutlinedButton(onClick = onTest, modifier = Modifier.fillMaxWidth()) {
            Text("Test this profile")
        }
    }
}

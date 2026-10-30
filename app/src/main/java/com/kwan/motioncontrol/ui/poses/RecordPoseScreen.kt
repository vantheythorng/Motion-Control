package com.kwan.motioncontrol.ui.poses

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kwan.motioncontrol.data.CustomPose
import com.kwan.motioncontrol.data.PoseSpace
import com.kwan.motioncontrol.data.PoseTrigger
import com.kwan.motioncontrol.data.label
import com.kwan.motioncontrol.motion.HandFrame
import com.kwan.motioncontrol.motion.MotionStateStore
import com.kwan.motioncontrol.motion.PoseFingerprint
import com.kwan.motioncontrol.motion.PoseMatcher
import com.kwan.motioncontrol.motion.PoseRecorder
import com.kwan.motioncontrol.motion.camera.HandTracker
import com.kwan.motioncontrol.ui.common.ChoiceDropdown
import com.kwan.motioncontrol.ui.common.LandmarkView
import com.kwan.motioncontrol.ui.common.ScreenHeader
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

private enum class Stage { SETUP, COUNTDOWN, CAPTURING, REVIEW }

private const val COUNTDOWN_SECONDS = 3

/**
 * Records a new custom pose, or re-records [existing] (SPEC §5.7): two rounds of steady frames,
 * then a live preview of how well the new pose matches before it's saved. Like Test mode, this
 * screen owns the front camera while it's started.
 */
@Composable
fun RecordPoseScreen(
    existing: CustomPose?,
    /** All other poses, to warn when the new one is too much like one of them. */
    otherPoses: List<CustomPose>,
    poseSpace: PoseSpace,
    store: MotionStateStore,
    onSave: (CustomPose) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCamera by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCamera = it }

    // The recorder is fed on MediaPipe's result thread and read here; every use is synchronized.
    val recorder = remember { PoseRecorder() }
    val latest = remember { MutableStateFlow<HandFrame?>(null) }
    val progress = remember { MutableStateFlow(0) }
    val roundsDone = remember { MutableStateFlow(0) }

    val tracker = remember {
        HandTracker(
            context = context.applicationContext,
            onFrame = { frame ->
                latest.value = frame
                synchronized(recorder) {
                    recorder.onFrame(frame)
                    progress.value = recorder.roundProgress
                    roundsDone.value = recorder.roundsDone
                }
            },
            onError = {},
            onDelegateChosen = {},
        )
    }
    DisposableEffect(Unit) { onDispose { tracker.close() } }
    LifecycleStartEffect(hasCamera) {
        store.setTestModeActive(true)
        if (hasCamera) tracker.start(lifecycleOwner)
        onStopOrDispose {
            tracker.stop()
            latest.value = null
            store.setTestModeActive(false)
        }
    }

    // Not saveable: a rotation drops the recorder's samples, so recording starts over.
    var stage by remember { mutableStateOf(Stage.SETUP) }
    /** Counts recordings, so "Record again" never shows the previous recording's preview. */
    var session by remember { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "Pose ${otherPoses.size + 1}") }
    var trigger by rememberSaveable { mutableStateOf(existing?.trigger ?: PoseTrigger.HOLD) }
    var countdown by remember { mutableIntStateOf(COUNTDOWN_SECONDS) }
    val frame by latest.collectAsStateWithLifecycle()
    val captured by progress.collectAsStateWithLifecycle()
    val rounds by roundsDone.collectAsStateWithLifecycle()

    LaunchedEffect(stage) {
        if (stage != Stage.COUNTDOWN) return@LaunchedEffect
        for (s in COUNTDOWN_SECONDS downTo 1) {
            countdown = s
            delay(1000)
        }
        synchronized(recorder) { recorder.startRound() }
        stage = Stage.CAPTURING
    }
    LaunchedEffect(rounds) {
        if (stage != Stage.CAPTURING || rounds == 0) return@LaunchedEffect
        stage = if (synchronized(recorder) { recorder.done }) Stage.REVIEW else Stage.COUNTDOWN
    }
    var stalled by remember { mutableStateOf(false) }
    LaunchedEffect(stage, captured) {
        stalled = false
        if (stage == Stage.CAPTURING) {
            delay(3000)
            stalled = true
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ScreenHeader(if (existing == null) "New pose" else "Re-record ${existing.name}", onBack)
        if (!hasCamera) {
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Only the tracked hand is drawn, never the camera image.
            LandmarkView(frame?.landmarks, Modifier.width(160.dp))
            Text(
                if (frame?.landmarks == null) "No hand in view" else "Hand tracked",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        when (stage) {
            Stage.SETUP -> {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Fires", Modifier.weight(1f))
                    ChoiceDropdown(trigger, PoseTrigger.entries, { it.label }, { trigger = it }, Modifier.weight(1.5f))
                }
                Text(
                    "Hold the pose in front of the camera for about 2 seconds, twice: the second time " +
                        "at a slightly different angle or distance.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    onClick = {
                        synchronized(recorder) { recorder.clear() }
                        session++
                        stage = Stage.COUNTDOWN
                    },
                    enabled = hasCamera && name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Start recording") }
            }
            Stage.COUNTDOWN -> {
                Text(
                    if (rounds == 0) "Get ready: make the pose." else "Again, at a slightly different angle or distance.",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text("Recording in $countdown…", style = MaterialTheme.typography.headlineSmall)
            }
            Stage.CAPTURING -> {
                Text("Round ${rounds + 1} of ${recorder.rounds}: hold still", style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(progress = { captured / recorder.perRound.toFloat() }, modifier = Modifier.fillMaxWidth())
                if (stalled) Text("Keep your hand still and fully in view.", color = MaterialTheme.colorScheme.error)
            }
            Stage.REVIEW -> {
                val pose = remember(session) {
                    synchronized(recorder) {
                        CustomPose(
                            id = existing?.id ?: UUID.randomUUID().toString(),
                            name = name.trim(),
                            trigger = trigger,
                            imageSamples = recorder.imageSamples.toList(),
                            worldSamples = recorder.worldSamples.toList(),
                            resembles = recorder.mostlyBuiltIn()?.name,
                        )
                    }
                }
                Review(
                    pose = pose,
                    frame = frame,
                    otherPoses = otherPoses,
                    poseSpace = poseSpace,
                    builtIn = pose.resemblesMotion?.label,
                )
                Button(onClick = { onSave(pose) }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Text("Save")
                }
                OutlinedButton(
                    onClick = {
                        synchronized(recorder) { recorder.clear() }
                        session++
                        stage = Stage.COUNTDOWN
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Record again") }
                TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            }
        }
    }
}

/** Live match % of the new pose in both landmark spaces, the closest other pose, and warnings. */
@Composable
private fun Review(
    pose: CustomPose,
    frame: HandFrame?,
    otherPoses: List<CustomPose>,
    poseSpace: PoseSpace,
    builtIn: String?,
) {
    val similar = remember(pose, otherPoses, poseSpace) {
        PoseRecorder.similarPoses(pose.samples(poseSpace), otherPoses, poseSpace)
    }
    val warnings = buildList {
        similar.forEach { add("Too much like \"${it.name}\": the two may be mistaken for each other.") }
        builtIn?.let { add("Looks like the built-in $it. In profiles that use this pose, it replaces $it.") }
    }
    warnings.forEach { warning ->
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Text(warning, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }

    val image = remember(pose) { PoseMatcher(listOf(pose), PoseSpace.IMAGE) }
    val world = remember(pose) { PoseMatcher(listOf(pose), PoseSpace.WORLD) }
    val others = remember(otherPoses, poseSpace) { PoseMatcher(otherPoses, poseSpace) }
    fun score(matcher: PoseMatcher): String {
        val fingerprint = frame?.let { PoseFingerprint.of(it, matcher.space) } ?: return "-"
        val match = matcher.rank(fingerprint).firstOrNull() ?: return "-"
        return "${(match.score * 100).roundToInt()} %"
    }

    Text("Try it", style = MaterialTheme.typography.titleMedium)
    Text(
        "Make the pose, then other hand shapes. 50 % and up counts as a match.",
        style = MaterialTheme.typography.bodySmall,
    )
    Text("Match: 2D ${score(image)} · 3D ${score(world)}", style = MaterialTheme.typography.titleMedium)
    val fingerprint = frame?.let { PoseFingerprint.of(it, poseSpace) }
    val closest = fingerprint?.let { others.rank(it).firstOrNull() }
    if (closest != null) {
        Text(
            "Closest other pose: ${closest.pose.name} ${(closest.score * 100).roundToInt()} %",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    val classified = frame?.takeIf { it.gesture != null && it.gesture != "None" }
    Text(
        "Built-in classifier: " +
            (classified?.let { String.format(Locale.US, "%s (%.2f)", it.gesture, it.gestureScore) } ?: "-"),
        style = MaterialTheme.typography.bodySmall,
    )
}

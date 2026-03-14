@file:OptIn(ExperimentalMaterial3Api::class)

package com.serkka.tracker

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class WorkoutActivityType(
    val label: String,
    val stravaType: String,
    val icon: ImageVector
)

val workoutActivityTypes = listOf(
    WorkoutActivityType("Weight Training", "WeightTraining", Icons.Default.FitnessCenter),
    WorkoutActivityType("Other",   "Workout", Icons.Default.MoreHoriz),
)

fun formatElapsed(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
           else       String.format("%02d:%02d", m, s)
}

@Composable
fun WorkoutTimerScreen(
    timerViewModel:  WorkoutTimerViewModel,
    stravaViewModel: StravaViewModel,
    bottomPadding:   Dp = 0.dp,
) {
    val context = LocalContext.current

    val elapsedSeconds by timerViewModel.elapsedSeconds.collectAsState()
    val currentLapSeconds by timerViewModel.currentLapSeconds.collectAsState()
    val isRunning by timerViewModel.isRunning.collectAsState()
    val hasStarted by timerViewModel.hasStarted.collectAsState()
    val selectedType by timerViewModel.selectedType.collectAsState()
    val showUploadDialog by timerViewModel.showUploadDialog.collectAsState()
    val activityName by timerViewModel.activityName.collectAsState()
    val distanceKm by timerViewModel.distanceKm.collectAsState()
    val startDateTime by timerViewModel.startDateTime.collectAsState()
    val uploadState by stravaViewModel.uploadState.collectAsState()
    val savedToken by stravaViewModel.savedToken.collectAsState()


    val timeString = formatElapsed(elapsedSeconds)
    val lapTimeString = formatElapsed(currentLapSeconds)

    val rawProgress = (elapsedSeconds % 60) / 60f
    val animatedProgress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = tween(durationMillis = 900, easing = LinearEasing),
        label = "timerRing"
    )
    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    // ── React to Strava upload result ─────────────────────────────────────────
    LaunchedEffect(uploadState) {
        when (uploadState) {
            is UploadState.Success -> {
                Toast.makeText(context, "✓ Uploaded to Strava!", Toast.LENGTH_SHORT).show()
                timerViewModel.reset()
                stravaViewModel.clearUploadState()
                stravaViewModel.checkAndFetchActivities()
            }

            is UploadState.Error -> {
                Toast.makeText(
                    context,
                    (uploadState as UploadState.Error).message,
                    Toast.LENGTH_LONG
                ).show()
                stravaViewModel.clearUploadState()
            }

            else -> Unit
        }
    }

    // ── Layout ────────────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 42.dp, bottom = 8.dp + bottomPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {

        Text(
            text = if (hasStarted) "$lapTimeString" else "",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold,
            fontSize = if (hasStarted) 50.sp else 50.sp,
            letterSpacing = if (hasStarted) 2.sp else 0.sp,
            color = if (hasStarted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
        )


        Spacer(modifier = Modifier.weight(1f))

        // ── Ring + time display ───────────────────────────────────────────────
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(320.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false, radius = 160.dp),
                    onClick = { timerViewModel.lap() }
                )
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 14.dp.toPx()
                val inset = stroke / 2f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(inset, inset)

                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )

                if (hasStarted && animatedProgress > 0f) {
                    drawArc(
                        color = primaryColor,
                        startAngle = -90f,
                        sweepAngle = animatedProgress * 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = timeString,
                    fontSize = if (elapsedSeconds >= 3600) 60.sp else 70.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }
        }

        Spacer(modifier = Modifier.weight(0.6f))

        // ── Controls ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Stop button — only when paused (so user can discard/upload)
            AnimatedVisibility(
                visible = hasStarted && !isRunning,
                enter = fadeIn() + slideInHorizontally { -it },
                exit = fadeOut() + slideOutHorizontally { -it }
            ) {
                FilledTonalIconButton(
                    onClick = { timerViewModel.requestStop() },
                    modifier = Modifier.size(80.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Icon(
                        Icons.Default.Stop,
                        contentDescription = "Stop",
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            // Start / Pause — always visible once hasStarted, or as the initial start button
            FloatingActionButton(
                onClick = { timerViewModel.toggleRunning() },
                modifier = Modifier.size(80.dp),
                containerColor = if (isRunning) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.primary,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isRunning) "Pause" else "Start",
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.surface

                )
            }
        }

        Spacer(modifier = Modifier.height(80.dp))

        // ── Upload dialog ─────────────────────────────────────────────────────────
        if (showUploadDialog) {
            UploadWorkoutDialog(
                activityName = activityName,
                onNameChange = { timerViewModel.setActivityName(it) },
                elapsedSeconds = elapsedSeconds,
                isUploading = uploadState == UploadState.Loading,
                isStravaLinked = savedToken.isNotBlank(),
                onUpload = {
                    val dt = startDateTime
                        ?: LocalDateTime.now().minusSeconds(elapsedSeconds)
                    val iso = dt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    val dist = distanceKm.replace(',', '.').toFloatOrNull()?.let { it * 1000f }
                    stravaViewModel.uploadWorkout(
                        name = activityName,
                        sportType = selectedType.stravaType,
                        startDateLocal = iso,
                        elapsedSeconds = elapsedSeconds.toInt(),
                        distanceMeters = dist
                    )
                },
                onDismiss = { timerViewModel.dismissUploadDialog() },
                onDiscard = { timerViewModel.discard() }
            )
        }

    }
}

@Composable
private fun UploadWorkoutDialog(
    activityName: String,
    onNameChange: (String) -> Unit,
    elapsedSeconds: Long,
    isUploading: Boolean,
    isStravaLinked: Boolean,
    onUpload: () -> Unit,
    onDismiss: () -> Unit,
    onDiscard: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isUploading) onDismiss() },
        title = { Text("Upload Workout", fontWeight = FontWeight.Bold) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Timer,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Duration: ${formatElapsed(elapsedSeconds)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                OutlinedTextField(
                    value = activityName,
                    onValueChange = onNameChange,
                    label = { Text("Activity Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (!isStravaLinked) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Strava not linked – connect in Settings to upload",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDiscard,
                    enabled = !isUploading,
                    modifier = Modifier.weight(1.2f)
                ) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
                TextButton(
                    onClick = onDismiss,
                    enabled = !isUploading,
                    modifier = Modifier.weight(1.2f)
                ) {
                    Text("Cancel")
                }
                Spacer(Modifier.width(4.dp))
                Button(
                    onClick = onUpload,
                    enabled = activityName.isNotBlank() && !isUploading && isStravaLinked,
                    modifier = Modifier.weight(1.6f)
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(
                            Icons.Default.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.surface,
                        )
                        Spacer(Modifier.width(2.dp))
                        Text("Upload",color = MaterialTheme.colorScheme.surface)
                    }
                }
            }
        },
        dismissButton = {}
    )
}

@file:OptIn(ExperimentalMaterial3Api::class)

package com.serkka.tracker

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Houseboat
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.math.roundToInt

// ── Navigation ───────────────────────────────────────────────────────────────

enum class Screen(val title: String) {
    Summary("Weekly Summary"),
    Workouts("Workouts"),
    StravaCalendar("Strava Calendar"),
    WeightTracking("Weight Tracking"),
    WorkoutStats("Workout Stats"),
    Notes("Notes"),
    Settings("Settings"),
    WorkoutTimer("Workout Timer")
}

// ── Strava ────────────────────────────────────────────────────────────────────

internal const val STRAVA_CLIENT_ID = "206279"

// ── Number formatting ─────────────────────────────────────────────────────────

internal fun formatWeight(weight: Float): String {
    val rounded = (weight * 1000f).roundToInt() / 1000f
    return if (rounded % 1 == 0f) rounded.toInt().toString() else rounded.toString()
}

/** Accepts both '.' and ',' as decimal separators. */
internal fun String.toLeadFloat(): Float? = this.replace(',', '.').toFloatOrNull()

// ── Activity icons ────────────────────────────────────────────────────────────

internal fun getIconForActivity(type: String): ImageVector = when (type) {
    "WeightTraining" -> Icons.Default.FitnessCenter
    "Run"            -> Icons.AutoMirrored.Filled.DirectionsRun
    "Ride"           -> Icons.AutoMirrored.Filled.DirectionsBike
    "Swim"           -> Icons.Default.Waves
    "Walk"           -> Icons.AutoMirrored.Filled.DirectionsWalk
    "Yoga"           -> Icons.Default.SelfImprovement
    "Hike"           -> Icons.Default.Terrain
    else             -> Icons.Default.Star
}

// ── Animations ────────────────────────────────────────────────────────────────

fun Modifier.bounceClick(interactionSource: InteractionSource) = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "bounceScale"
    )
    this.graphicsLayer(scaleX = scale, scaleY = scale)
}

// ── Shared dialogs ────────────────────────────────────────────────────────────

@Composable
internal fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val confirmInteractionSource = remember { MutableInteractionSource() }
    val dismissInteractionSource = remember { MutableInteractionSource() }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text(title) },
        text    = { Text(message) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                interactionSource = confirmInteractionSource,
                modifier = Modifier.bounceClick(confirmInteractionSource),
                colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Delete") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                interactionSource = dismissInteractionSource,
                modifier = Modifier.bounceClick(dismissInteractionSource)
            ) { Text("Cancel") }
        }
    )
}

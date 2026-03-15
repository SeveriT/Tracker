@file:OptIn(ExperimentalMaterial3Api::class)

package com.serkka.tracker

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FitnessCenter
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
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
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

internal val STRAVA_CLIENT_ID = BuildConfig.STRAVA_CLIENT_ID
internal val STRAVA_CLIENT_SECRET = BuildConfig.STRAVA_CLIENT_SECRET

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

/**
 * Replaced the old spring bounce effect with a clean Material 3 ripple.
 * This ensures consistency across buttons and interactive elements.
 * Supports combinedClickable for long press actions.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.bounceClick(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
): Modifier = this.then(
    if (onClick != null || onLongClick != null) {
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                interactionSource = interactionSource as MutableInteractionSource,
                indication = ripple(),
                enabled = enabled,
                onLongClick = onLongClick,
                onClick = onClick ?: {}
            )
    } else {
        // If used for visual feedback on an existing clickable, we clip to ensure the ripple is rounded
        Modifier.clip(RoundedCornerShape(12.dp))
    }
)

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

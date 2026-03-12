package com.serkka.tracker

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun WorkoutStatsPage(workouts: List<Workout>, primaryColor: Color) {
    val workoutStats = remember(workouts) {
        workouts.filter { it.weightUnit == "kg" }
            .groupBy { it.exerciseName }
            .mapValues { entry ->
                entry.value.sumOf {
                    val s = if (it.sets > 0) it.sets.toLong() else 1L
                    val r = if (it.reps > 0) it.reps.toLong() else 1L
                    s * r * it.weight.toDouble()
                }
            }
            .toList()
            .sortedByDescending { it.second }
    }

    val totalWeightLifted = workoutStats.sumOf { it.second }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth().animateContentSize().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(0.8f)
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 8.dp, pressedElevation = 4.dp, hoveredElevation = 10.dp
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Total Volume Lifted",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val animatedWeight by animateFloatAsState(
                        targetValue = totalWeightLifted.toFloat(),
                        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
                        label = "TotalWeightAnimation"
                    )
                    Text(
                        "${String.format(Locale.getDefault(), "%,.0f", animatedWeight)} kg",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor
                    )
                }
            }
        }

        item {
            Text(
                "Breakdown by Exercise",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        items(workoutStats) { (exercise, weight) ->
            ElevatedCard(
                modifier = Modifier.fillMaxWidth().animateContentSize().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(0.8f)
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 8.dp, pressedElevation = 4.dp, hoveredElevation = 10.dp
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            exercise,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "${String.format(Locale.getDefault(), "%,.0f", weight)} kg",
                            fontWeight = FontWeight.Bold,
                            color = primaryColor,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    val progress = if (totalWeightLifted > 0) (weight / totalWeightLifted).toFloat() else 0f
                    val animatedProgress by animateFloatAsState(
                        targetValue = progress,
                        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
                        label = "ProgressAnimation"
                    )
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                        color = primaryColor,
                        trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(75.dp)) }
    }
}

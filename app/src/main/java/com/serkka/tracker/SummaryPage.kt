@file:OptIn(ExperimentalMaterial3Api::class)

package com.serkka.tracker

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.*

@Composable
fun SummaryPage(
    workouts: List<Workout>,
    bodyWeights: List<BodyWeight>,
    stravaViewModel: StravaViewModel,
    primaryColor: Color,
    onWorkoutEdit: (Workout) -> Unit,
    onWorkoutDelete: (Workout) -> Unit,
    onWorkoutCopy: (Workout) -> Unit,
    onNavigateToWeightTracking: () -> Unit,
    listState: LazyListState = rememberLazyListState()
) {
    val activities by stravaViewModel.activities.collectAsState()
    val isLoading by stravaViewModel.isLoading.collectAsState()
    var refreshTrigger by remember { mutableStateOf(false) }

    val isRefreshing = refreshTrigger && isLoading

    LaunchedEffect(isLoading) {
        if (!isLoading && refreshTrigger) refreshTrigger = false
    }

    val lastWeight = remember(bodyWeights) { bodyWeights.maxByOrNull { it.date } }
    val activityData = remember(activities) { stravaViewModel.getActivityData() }

    val today = LocalDate.now()

    val todaysWorkouts = remember(workouts) {
        workouts.filter {
            Instant.ofEpochMilli(it.date).atZone(ZoneId.systemDefault()).toLocalDate().isEqual(today)
        }
    }

    val weeklyStreak = remember(activityData, today) {
        val startDate = today.minusDays(6)
        (0..6).map { i ->
            val date = startDate.plusDays(i.toLong())
            val dateString = String.format(Locale.getDefault(), "%04d-%02d-%02d", date.year, date.monthValue, date.dayOfMonth)
            date to activityData.containsKey(dateString)
        }
    }

    val recentActivities = remember(activities) {
        val twoDaysAgo = today.minusDays(6)
        activities.filter { activity ->
            val activityDate = LocalDate.parse(activity.startDate.substringBefore("T"))
            !activityDate.isBefore(twoDaysAgo) && !activityDate.isAfter(today)
        }
    }

    LaunchedEffect(Unit) {
        stravaViewModel.checkAndFetchActivities()
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            refreshTrigger = true
            stravaViewModel.checkAndFetchActivities()
        }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Weekly streak dots ────────────────────────────────────────────
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(0.8f)
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 8.dp, pressedElevation = 4.dp, hoveredElevation = 10.dp
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            weeklyStreak.forEach { (date, active) ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = date.dayOfWeek.getDisplayName(
                                            java.time.format.TextStyle.SHORT, Locale.getDefault()
                                        ).take(1),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(
                                                color = if (active) primaryColor
                                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                                shape = CircleShape
                                            )
                                            .border(
                                                width = 2.dp,
                                                color = if (date == today) Color.White else Color.Transparent,
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (active) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = Color.Black
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Latest weight card ────────────────────────────────────────────
            if (lastWeight != null) {
                item {
                    Text(
                        "Latest Weight",
                        style = MaterialTheme.typography.titleMedium,
                        color = primaryColor,
                        fontWeight = FontWeight.Bold
                    )

                    val weekWeights = remember(bodyWeights) {
                        val twoDaysAgo = today.minusDays(13)
                        bodyWeights.filter {
                            val d = Instant.ofEpochMilli(it.date).atZone(ZoneId.systemDefault()).toLocalDate()
                            !d.isBefore(twoDaysAgo) && !d.isAfter(today)
                        }.sortedBy { it.date }
                    }

                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize()
                            .padding(top = 8.dp)
                            .clickable { onNavigateToWeightTracking() },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(0.8f)
                        ),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = 10.dp, pressedElevation = 6.dp, hoveredElevation = 12.dp
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${formatWeight(lastWeight.weight)} kg",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = SimpleDateFormat("EEEE d.M.yyyy", Locale.getDefault())
                                        .format(Date(lastWeight.date)),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (weekWeights.size >= 2) {
                                val surfaceColor = MaterialTheme.colorScheme.surface
                                val trend = weekWeights.last().weight - weekWeights.first().weight

                                Box(
                                    modifier = Modifier
                                        .width(150.dp)
                                        .height(85.dp)
                                        .background(
                                            color = primaryColor.copy(alpha = 0.05f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .padding(8.dp)
                                ) {
                                    val animationProgress = remember { Animatable(0f) }
                                    LaunchedEffect(Unit) {
                                        animationProgress.animateTo(
                                            1f,
                                            animationSpec = tween(500, easing = FastOutSlowInEasing)
                                        )
                                    }
                                    androidx.compose.foundation.Canvas(
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        val weights = weekWeights.map { it.weight }
                                        val minWeight = weights.minOrNull() ?: 0f
                                        val maxWeight = weights.maxOrNull() ?: 100f
                                        val range = (maxWeight - minWeight).coerceAtLeast(1f)
                                        val graphWidth = size.width
                                        val graphHeight = size.height
                                        val spacing = graphWidth / (weights.size - 1).coerceAtLeast(1)

                                        val fillPath = Path()
                                        weights.forEachIndexed { index, weight ->
                                            val x = index * spacing
                                            val y = graphHeight - ((weight - minWeight) / range * graphHeight)
                                            if (index == 0) { fillPath.moveTo(x, graphHeight); fillPath.lineTo(x, y) }
                                            else fillPath.lineTo(x, y)
                                        }
                                        fillPath.lineTo(graphWidth, graphHeight)
                                        fillPath.close()
                                        drawPath(
                                            path = fillPath,
                                            brush = Brush.verticalGradient(
                                                listOf(primaryColor.copy(alpha = 0.2f * animationProgress.value), Color.Transparent)
                                            )
                                        )

                                        val linePath = Path()
                                        weights.forEachIndexed { index, weight ->
                                            val x = index * spacing
                                            val y = graphHeight - ((weight - minWeight) / range * graphHeight)
                                            if (index == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
                                        }
                                        drawPath(linePath, color = primaryColor, style = Stroke(width = 2.5.dp.toPx()), alpha = animationProgress.value)

                                        weights.forEachIndexed { index, weight ->
                                            val x = index * spacing
                                            val y = graphHeight - ((weight - minWeight) / range * graphHeight)
                                            drawCircle(surfaceColor, radius = 4.dp.toPx() * animationProgress.value, center = Offset(x, y))
                                            drawCircle(primaryColor, radius = 3.dp.toPx() * animationProgress.value, center = Offset(x, y))
                                        }
                                    }

                                    Row(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .background(
                                                color = when {
                                                    trend > 0.1f  -> Color(0xFFDE4A4A).copy(alpha = 0.8f)
                                                    trend < -0.1f -> Color(0xFF46CE46).copy(alpha = 0.8f)
                                                    else          -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                                },
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            .padding(horizontal = 4.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = when {
                                                trend > 0.1f  -> Icons.Default.TrendingUp
                                                trend < -0.1f -> Icons.Default.TrendingDown
                                                else          -> Icons.Default.TrendingFlat
                                            },
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = "${if (trend > 0) "+" else ""}${formatWeight(trend)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Strava section ────────────────────────────────────────────────
            item {
                Text(
                    "Strava Activities (Last 7 Days)",
                    style = MaterialTheme.typography.titleMedium,
                    color = primaryColor,
                    fontWeight = FontWeight.Bold
                )
            }

            if (isLoading && activities.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(color = primaryColor) }
                }
            } else if (recentActivities.isEmpty()) {
                item {
                    Text(
                        "No Strava activities in the past 7 days. Link your account in the Strava Calendar page.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(recentActivities.chunked(2)) { activityPair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        activityPair.forEach { activity ->
                            ElevatedCard(
                                modifier = Modifier.weight(1f).animateContentSize().height(100.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(0.8f)
                                ),
                                elevation = CardDefaults.cardElevation(
                                    defaultElevation = 8.dp, pressedElevation = 4.dp, hoveredElevation = 10.dp
                                )
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(12.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(primaryColor.copy(alpha = 0.1f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                getIconForActivity(activity.type),
                                                contentDescription = null,
                                                tint = primaryColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = activity.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = activity.type,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        if (activity.distance != 0f || activity.calories != 0f) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = if (activity.distance == 0f) Icons.Default.LocalFireDepartment
                                                                  else Icons.Default.Straighten,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                )
                                                Text(
                                                    text = if (activity.distance == 0f)
                                                               "${activity.calories.toInt()} kcal"
                                                           else
                                                               "${String.format(Locale.getDefault(), "%.1f", activity.distance / 1000f)} km",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Schedule,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                            Text(
                                                text = run {
                                                    val totalMinutes = activity.movingTime / 60
                                                    when {
                                                        totalMinutes < 60      -> "$totalMinutes min"
                                                        totalMinutes % 60 == 0 -> "${totalMinutes / 60} h"
                                                        else                   -> "${totalMinutes / 60} h ${totalMinutes % 60} min"
                                                    }
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (activityPair.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            // ── Today's exercises ─────────────────────────────────────────────
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Today's Exercises",
                    style = MaterialTheme.typography.titleMedium,
                    color = primaryColor,
                    fontWeight = FontWeight.Bold
                )
            }

            if (todaysWorkouts.isEmpty()) {
                item {
                    Text(
                        "No exercises recorded for today yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                item {
                    Column {
                        todaysWorkouts.chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                pair.forEach { workout ->
                                    WorkoutCard(
                                        modifier = Modifier.weight(1f).fillMaxHeight(),
                                        workout = workout,
                                        primaryColor = primaryColor,
                                        onEdit = { onWorkoutEdit(workout) },
                                        onDelete = { onWorkoutDelete(workout) },
                                        onCopy = { onWorkoutCopy(workout) }
                                    )
                                }
                                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(65.dp)) }
        }
    }
}

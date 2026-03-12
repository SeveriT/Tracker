@file:OptIn(ExperimentalMaterial3Api::class)

package com.serkka.tracker

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*

// ── Weight tracking page ──────────────────────────────────────────────────────

@Composable
fun WeightTrackingPage(
    bodyWeights: List<BodyWeight>,
    primaryColor: Color,
    onWeightClick: (BodyWeight) -> Unit,
    onWeightDelete: (BodyWeight) -> Unit,
    listState: LazyListState = rememberLazyListState()
) {
    val sortedWeights = remember(bodyWeights) { bodyWeights.sortedBy { it.date } }

    val prediction = remember(sortedWeights) {
        if (sortedWeights.size < 2) null
        else {
            val last = sortedWeights.last()
            val first = sortedWeights.first()
            val daysDiff = (last.date - first.date) / (1000 * 60 * 60 * 24).toDouble()
            if (daysDiff < 1) null
            else {
                val ratePerDay = (last.weight - first.weight) / daysDiff
                Pair(last.weight + (ratePerDay * 30), ratePerDay * 7)
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        if (sortedWeights.isNotEmpty()) {
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().animateContentSize().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 8.dp, pressedElevation = 4.dp, hoveredElevation = 10.dp
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(
                                    "Current Weight",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "${formatWeight(sortedWeights.last().weight)} kg",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = primaryColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            prediction?.let { (_, rate) ->
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        "Trend",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val sign = if (rate >= 0) "+" else ""
                                    Text(
                                        "$sign${String.format(Locale.getDefault(), "%.2f", rate)} kg/week",
                                        color = if (rate <= 0) Color(0xFF46CE46).copy(alpha = 0.8f) else Color(0xFFEE3E3E).copy(alpha = 0.8f),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        prediction?.let { (pred, _) ->
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "30-Day Prediction: ${String.format(Locale.getDefault(), "%.1f", pred)} kg",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Box(
                            modifier = Modifier.fillMaxWidth().height(200.dp).padding(vertical = 8.dp)
                        ) {
                            WeightChart(weights = sortedWeights, color = primaryColor)
                        }
                    }
                }
            }

            item {
                Text(
                    "History",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            items(sortedWeights.reversed(), key = { it.id }) { weightEntry ->
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .animateContentSize()
                        .animateItem()
                        .clickable { onWeightClick(weightEntry) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 8.dp, pressedElevation = 4.dp, hoveredElevation = 10.dp
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f).padding(start = 8.dp)
                        ) {
                            Text(
                                SimpleDateFormat("EEEE d.M.yyyy", Locale.getDefault()).format(Date(weightEntry.date)),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                "${formatWeight(weightEntry.weight)} kg",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                            if (weightEntry.notes.isNotEmpty()) {
                                Text(
                                    weightEntry.notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(onClick = { onWeightDelete(weightEntry) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        } else {
            item {
                Text(
                    "Add your first weight entry to see progress!",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

// ── Weight chart ──────────────────────────────────────────────────────────────

@Composable
fun WeightChart(weights: List<BodyWeight>, color: Color) {
    if (weights.isEmpty()) return

    val minWeight = weights.minOf { it.weight } - 1f
    val maxWeight = weights.maxOf { it.weight } + 1f
    val weightRange = maxOf(1f, maxWeight - minWeight)
    val minDate = weights.first().date
    val maxDate = weights.last().date
    val dateRange = maxOf(1L, maxDate - minDate)

    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(weights) {
        animationProgress.animateTo(1f, animationSpec = tween(500, easing = FastOutSlowInEasing))
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        val points = weights.map { weight ->
            val x = ((weight.date - minDate).toFloat() / dateRange.toFloat()) * width
            val y = height - ((weight.weight - minWeight) / weightRange) * height
            Offset(x, y)
        }

        if (points.size > 1) {
            val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
            }
            drawPath(path, color = color, style = Stroke(width = 3.dp.toPx()), alpha = animationProgress.value)

            val fillPath = Path().apply {
                addPath(path)
                lineTo(width, height)
                lineTo(0f, height)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    listOf(color.copy(alpha = 0.2f * animationProgress.value), Color.Transparent)
                )
            )
        }

        points.forEach { point ->
            drawCircle(color = color, radius = 4.dp.toPx() * animationProgress.value, center = point)
        }
    }
}

// ── Body weight dialog ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyWeightDialog(
    bodyWeight: BodyWeight? = null,
    initialWeight: String = "",
    onDismiss: () -> Unit,
    onConfirm: (Float, Long, String) -> Unit
) {
    var weight by remember(bodyWeight, initialWeight) {
        mutableStateOf(bodyWeight?.weight?.let { formatWeight(it) } ?: initialWeight)
    }
    var notes by remember { mutableStateOf(bodyWeight?.notes ?: "") }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = bodyWeight?.date ?: System.currentTimeMillis()
    )
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = { TextButton(onClick = { showDatePicker = false }) { Text("OK") } }
        ) { DatePicker(state = datePickerState) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (bodyWeight == null) "Add Weight" else "Edit Weight") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NumericInput(
                    value = weight,
                    onValueChange = { weight = it },
                    label = "Weight (kg)",
                    modifier = Modifier.fillMaxWidth(),
                    step = 0.1f
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )
                OutlinedTextField(
                    value = SimpleDateFormat("EEEE d.M.yyyy", Locale.getDefault())
                        .format(Date(datePickerState.selectedDateMillis ?: System.currentTimeMillis())),
                    onValueChange = {},
                    label = { Text("Date") },
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.DateRange, null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val w = weight.toLeadFloat() ?: 0f
                if (w > 0) onConfirm(w, datePickerState.selectedDateMillis ?: System.currentTimeMillis(), notes)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

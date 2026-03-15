@file:OptIn(ExperimentalMaterial3Api::class)

package com.serkka.tracker

import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    // ── Height preference for BMI ─────────────────────────────────────────────
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("tracker_prefs", Context.MODE_PRIVATE) }
    var heightCm by remember { mutableStateOf(prefs.getFloat("height_cm", 0f)) }

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
                            // ── Left: weight + BMI ───────────────────────────
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
                                Spacer(modifier = Modifier.height(4.dp))
                                if (heightCm > 0f) {
                                    val heightM = heightCm / 100f
                                    val bmi = sortedWeights.last().weight / (heightM * heightM)
                                    val bmiCategory = when {
                                        bmi < 18.5f -> "Underweight"
                                        bmi < 25f   -> "Normal"
                                        bmi < 30f   -> "Overweight"
                                        else        -> "Obese"
                                    }
                                    val bmiColor = when {
                                        bmi < 18.5f -> Color(0xFF6693EB)
                                        bmi < 25f   -> Color(0xFF4AC067)
                                        bmi < 30f   -> Color(0xFFECFE72)
                                        else        -> Color(0xFFFF7043)
                                    }
                                    Text(
                                        "BMI ${String.format(Locale.getDefault(), "%.1f", bmi)} · $bmiCategory",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = bmiColor,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                } else {
                                    val bmiInteractionSource = remember { MutableInteractionSource() }
                                    TextButton(
                                        onClick = { /* handled below via heightCm dialog */ },
                                        interactionSource = bmiInteractionSource,
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier.height(24.dp).bounceClick(bmiInteractionSource)
                                    ) {
                                        Text(
                                            "Set height for BMI",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // ── Right: trend + 30-day prediction ────────────
                            prediction?.let { (pred, rate) ->
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
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "30-Day Prediction",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "${String.format(Locale.getDefault(), "%.1f", pred)} kg",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (pred > sortedWeights.last().weight) Color(0xFFEE3E3E).copy(alpha = 0.8f) else Color(0xFF46CE46).copy(alpha = 0.8f),
                                        fontWeight = FontWeight.Bold
                                    )
                                } //color = if (pred <= 0) Color(0xFF46CE46).copy(alpha = 0.8f) else Color(0xFFEE3E3E).copy(alpha = 0.8f),
                            }
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
                        val deleteInteractionSource = remember { MutableInteractionSource() }
                        IconButton(
                            onClick = { onWeightDelete(weightEntry) },
                            interactionSource = deleteInteractionSource,
                            modifier = Modifier.bounceClick(deleteInteractionSource)
                        ) {
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

        item { Spacer(modifier = Modifier.height(150.dp)) }
    }
}

// ── Weight chart ──────────────────────────────────────────────────────────────

@Composable
fun WeightChart(weights: List<BodyWeight>, color: Color) {
    if (weights.isEmpty()) return

    val gridLineColor  = Color(0xFF424349)
    val labelColor     = Color(0xFF9E9EA8)
    val gridLineStroke = 1.dp
    val yLabelCount    = 4   // number of horizontal grid lines
    val xLabelCount    = minOf(weights.size, 5)  // up to 5 date labels

    val rawMin = weights.minOf { it.weight }
    val rawMax = weights.maxOf { it.weight }
    val padding = maxOf(1f, (rawMax - rawMin) * 0.15f)
    val minWeight  = rawMin - padding
    val maxWeight  = rawMax + padding
    val weightRange = maxOf(1f, maxWeight - minWeight)

    val minDate  = weights.first().date
    val maxDate  = weights.last().date
    val dateRange = maxOf(1L, maxDate - minDate)

    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(weights) {
        animationProgress.animateTo(1f, animationSpec = tween(600, easing = FastOutSlowInEasing))
    }

    // Pre-format x-axis date labels
    val dateFmt = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }
    val xLabels: List<Pair<Float, String>> = remember(weights) {
        if (weights.size < 2) return@remember emptyList()
        val step = (weights.size - 1).toFloat() / (xLabelCount - 1).coerceAtLeast(1)
        (0 until xLabelCount).map { i ->
            val idx = (i * step).toInt().coerceIn(0, weights.size - 1)
            val ratio = (weights[idx].date - minDate).toFloat() / dateRange.toFloat()
            Pair(ratio, dateFmt.format(Date(weights[idx].date)))
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val yLabelWidthPx = 46.dp.toPx()
        val xLabelHeightPx = 20.dp.toPx()
        val chartLeft   = yLabelWidthPx
        val chartBottom = size.height - xLabelHeightPx
        val chartWidth  = size.width - chartLeft
        val chartHeight = chartBottom

        val textPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            textSize    = 10.sp.toPx()
            setColor(labelColor.toArgb())
        }

        // ── Horizontal grid lines + Y labels ─────────────────────────────────
        for (i in 0..yLabelCount) {
            val fraction = i.toFloat() / yLabelCount
            val yVal     = minWeight + fraction * weightRange
            val yPx      = chartBottom - fraction * chartHeight

            // Grid line
            drawLine(
                color       = gridLineColor,
                start       = Offset(chartLeft, yPx),
                end         = Offset(size.width, yPx),
                strokeWidth = gridLineStroke.toPx()
            )

            // Y-axis label
            val label = if (yVal % 1 == 0f) yVal.toInt().toString()
            else String.format("%.1f", yVal)
            drawContext.canvas.nativeCanvas.drawText(
                label,
                0f,
                yPx + textPaint.textSize / 3f,
                textPaint.apply { textAlign = android.graphics.Paint.Align.LEFT }
            )
        }

        // ── Map data to canvas points ─────────────────────────────────────────
        val points = weights.map { w ->
            val x = chartLeft + ((w.date - minDate).toFloat() / dateRange.toFloat()) * chartWidth
            val y = chartBottom - ((w.weight - minWeight) / weightRange) * chartHeight
            Offset(x, y)
        }

        // ── Smooth cubic bezier helper ────────────────────────────────────────
        fun Path.smoothCurveTo(pts: List<Offset>) {
            if (pts.size < 2) return
            moveTo(pts.first().x, pts.first().y)
            if (pts.size == 2) { lineTo(pts[1].x, pts[1].y); return }
            for (i in 0 until pts.size - 1) {
                val cur  = pts[i]
                val next = pts[i + 1]
                val cp1x = cur.x  + (next.x - (if (i > 0) pts[i - 1].x else cur.x)) / 6f
                val cp1y = cur.y  + (next.y - (if (i > 0) pts[i - 1].y else cur.y)) / 6f
                val cp2x = next.x - ((if (i < pts.size - 2) pts[i + 2].x else next.x) - cur.x) / 6f
                val cp2y = next.y - ((if (i < pts.size - 2) pts[i + 2].y else next.y) - cur.y) / 6f
                cubicTo(cp1x, cp1y, cp2x, cp2y, next.x, next.y)
            }
        }

        // ── Fill gradient ─────────────────────────────────────────────────────
        if (points.size > 1) {
            val fillPath = Path().apply {
                smoothCurveTo(points)
                lineTo(points.last().x, chartBottom)
                lineTo(points.first().x, chartBottom)
                close()
            }
            drawPath(
                path  = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(color.copy(alpha = 0.25f * animationProgress.value), Color.Transparent),
                    startY = 0f,
                    endY   = chartBottom
                )
            )

            // ── Line ─────────────────────────────────────────────────────────
            val linePath = Path().apply { smoothCurveTo(points) }
            drawPath(
                path  = linePath,
                color = color,
                style = Stroke(width = 2.5.dp.toPx()),
                alpha = animationProgress.value
            )
        }

        // ── Data point dots ───────────────────────────────────────────────────
        points.forEach { pt ->
            drawCircle(color = color,       radius = 4.dp.toPx() * animationProgress.value, center = pt)
            drawCircle(color = Color(0xFF24252B), radius = 2.dp.toPx() * animationProgress.value, center = pt)
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
            confirmButton = {
                val okInteractionSource = remember { MutableInteractionSource() }
                TextButton(
                    onClick = { showDatePicker = false },
                    interactionSource = okInteractionSource,
                    modifier = Modifier.bounceClick(okInteractionSource)
                ) { Text("OK") }
            }
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
                        val dateInteractionSource = remember { MutableInteractionSource() }
                        IconButton(
                            onClick = { showDatePicker = true },
                            interactionSource = dateInteractionSource,
                            modifier = Modifier.bounceClick(dateInteractionSource)
                        ) {
                            Icon(Icons.Default.DateRange, null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            val cancelInteractionSource = remember { MutableInteractionSource() }
            val saveInteractionSource = remember { MutableInteractionSource() }
            Row {
                TextButton(
                    onClick = onDismiss,
                    interactionSource = cancelInteractionSource,
                    modifier = Modifier.bounceClick(cancelInteractionSource)
                ) { Text("Cancel") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val w = weight.toLeadFloat() ?: 0f
                        if (w > 0) onConfirm(w, datePickerState.selectedDateMillis ?: System.currentTimeMillis(), notes)
                    },
                    interactionSource = saveInteractionSource,
                    modifier = Modifier.bounceClick(saveInteractionSource)
                ) { Text("Save") }
            }
        },
        dismissButton = null
    )
}

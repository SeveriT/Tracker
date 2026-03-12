@file:OptIn(ExperimentalMaterial3Api::class)

package com.serkka.tracker

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.*

// ── Strava calendar page ──────────────────────────────────────────────────────

@Composable
fun StravaCalendarPage(stravaViewModel: StravaViewModel, primaryColor: Color) {
    val context = LocalContext.current
    val activities by stravaViewModel.activities.collectAsState()
    val isLoading by stravaViewModel.isLoading.collectAsState()
    val error by stravaViewModel.error.collectAsState()
    val profilePicUrl by stravaViewModel.profilePicUrl.collectAsState()
    var refreshTrigger by remember { mutableStateOf(false) }

    val isRefreshing = refreshTrigger && isLoading

    LaunchedEffect(isLoading) {
        if (!isLoading && refreshTrigger) refreshTrigger = false
    }

    val activityData = remember(activities) { stravaViewModel.getActivityData() }
    val streak = remember(activities) { stravaViewModel.getWeeklyStreak() }
    val totalStreakActivities = remember(activities) { stravaViewModel.getTotalStreakActivities() }

    LaunchedEffect(error) {
        error?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            refreshTrigger = true
            stravaViewModel.checkAndFetchActivities()
        }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (activities.isEmpty() && !isLoading) {
                item {
                    Text("Link Strava to see your progress", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val authUri = "https://www.strava.com/oauth/mobile/authorize".toUri()
                                .buildUpon()
                                .appendQueryParameter("client_id", STRAVA_CLIENT_ID)
                                .appendQueryParameter("redirect_uri", "tracker-app://localhost")
                                .appendQueryParameter("response_type", "code")
                                .appendQueryParameter("approval_prompt", "force")
                                .appendQueryParameter("scope", "activity:read_all,activity:write,profile:read_all")
                                .build()
                            context.startActivity(Intent(Intent.ACTION_VIEW, authUri))
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryColor, contentColor = Color.Black
                        )
                    ) {
                        Text("Login with Strava", fontSize = 18.sp)
                    }
                }
            } else {
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
                        Row(
                            modifier = Modifier.padding(14.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                Column(horizontalAlignment = Alignment.Start) {
                                    Text("Streak", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                    Text("$streak Weeks", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Column(horizontalAlignment = Alignment.Start) {
                                    Text("Total activities", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                    Text("$totalStreakActivities", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            if (profilePicUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = profilePicUrl,
                                    contentDescription = "Profile Picture",
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                val months = (0..2).map { YearMonth.now().minusMonths(it.toLong()) }
                items(months) { month ->
                    StravaCalendar(month, activityData, primaryColor)
                    Spacer(modifier = Modifier.height(32.dp))
                }

                item { Spacer(modifier = Modifier.height(70.dp)) }
            }
        }
    }
}

// ── Strava calendar grid ──────────────────────────────────────────────────────

@Composable
fun StravaCalendar(month: YearMonth, activityData: Map<String, List<String>>, primaryColor: Color) {
    val daysInMonth = month.lengthOfMonth()
    val firstDayOfMonth = month.atDay(1).dayOfWeek.value - 1
    val year = month.year
    val monthValue = month.monthValue
    val today = LocalDate.now()
    val isActualCurrentMonth = month == YearMonth.now()

    Column {
        Text(
            text = "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} $year",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Row(modifier = Modifier.fillMaxWidth().padding(end = 40.dp)) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(modifier = Modifier.fillMaxWidth()) {
            val totalSlots = firstDayOfMonth + daysInMonth
            val rows = (totalSlots + 6) / 7

            Column(modifier = Modifier.fillMaxWidth().padding(end = 40.dp)) {
                var currentDayIndex = 0
                for (row in 0 until rows) {
                    Row(modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        for (col in 0 until 7) {
                            val slotIndex = row * 7 + col
                            if (slotIndex < firstDayOfMonth || currentDayIndex >= daysInMonth) {
                                Box(
                                    modifier = Modifier.weight(1f).aspectRatio(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val dayNum = if (slotIndex < firstDayOfMonth) {
                                        month.minusMonths(1).lengthOfMonth() - (firstDayOfMonth - slotIndex - 1)
                                    } else {
                                        slotIndex - (firstDayOfMonth + daysInMonth) + 1
                                    }
                                    Text(
                                        text = dayNum.toString(),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                        fontSize = 14.sp
                                    )
                                }
                            } else {
                                currentDayIndex++
                                val day = currentDayIndex
                                val dateString = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, monthValue, day)
                                val activitiesOnDay = activityData[dateString] ?: emptyList()
                                val isToday = today.year == year && today.monthValue == monthValue && today.dayOfMonth == day

                                Box(
                                    modifier = Modifier.weight(1f).aspectRatio(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (activitiesOnDay.isNotEmpty()) {
                                        val bgColor = if (isToday) primaryColor else MaterialTheme.colorScheme.onSurface
                                        val iconTint = if (isToday) Color.Black else MaterialTheme.colorScheme.surface
                                        Box(
                                            modifier = Modifier.size(40.dp).background(bgColor, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                getIconForActivity(activitiesOnDay.first()),
                                                null,
                                                tint = iconTint,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    } else if (isToday) {
                                        Box(
                                            modifier = Modifier.size(40.dp).border(2.dp, primaryColor, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(day.toString(), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        }
                                    } else {
                                        Text(day.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Week streak indicators ────────────────────────────────────────
            Box(
                modifier = Modifier.align(Alignment.TopEnd).width(36.dp).height((rows * 56).dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    repeat(rows) { rowIndex ->
                        Box(
                            modifier = Modifier.height(55.dp).width(36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val weekStartDay = if (rowIndex == 0) 1 else (rowIndex * 7 - firstDayOfMonth + 1)
                            val weekEndDay = minOf(daysInMonth, (rowIndex + 1) * 7 - firstDayOfMonth)
                            val isCurrentWeek = isActualCurrentMonth && today.dayOfMonth in weekStartDay..weekEndDay

                            if (isCurrentWeek) {
                                val lastMonday = LocalDate.now()
                                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                                    .minusWeeks(1)
                                val hasActivityLastWeek = (0 until 7).any { i ->
                                    val d = lastMonday.plusDays(i.toLong())
                                    val dateStr = String.format(Locale.getDefault(), "%04d-%02d-%02d", d.year, d.monthValue, d.dayOfMonth)
                                    activityData.containsKey(dateStr)
                                }

                                if (hasActivityLastWeek) {
                                    Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Bolt, null, tint = primaryColor, modifier = Modifier.size(36.dp))
                                    }
                                } else {
                                    val hasActivityThisWeek = (weekStartDay..weekEndDay).any { d ->
                                        activityData.containsKey(String.format(Locale.getDefault(), "%04d-%02d-%02d", year, monthValue, d))
                                    }
                                    if (hasActivityThisWeek) {
                                        Box(
                                            modifier = Modifier.size(24.dp).background(primaryColor, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) { Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(16.dp)) }
                                    }
                                }
                            } else {
                                val hasActivity = (weekStartDay..weekEndDay).any { d ->
                                    activityData.containsKey(String.format(Locale.getDefault(), "%04d-%02d-%02d", year, monthValue, d))
                                }
                                if (hasActivity) {
                                    Box(
                                        modifier = Modifier.size(24.dp).background(primaryColor, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) { Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(16.dp)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

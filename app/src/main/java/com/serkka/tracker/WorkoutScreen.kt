@file:OptIn(ExperimentalMaterial3Api::class)

package com.serkka.tracker

import android.app.Activity
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.serkka.tracker.ui.theme.PersonalBestGold
import com.serkka.tracker.ui.theme.PBGlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.*
import kotlin.math.roundToInt

enum class Screen {
    Workouts, StravaCalendar, WeightTracking, WorkoutStats, Notes, Summary, Settings
}

private fun formatWeight(weight: Float): String {
    // Round to 3 decimal places to handle floating-point precision errors (e.g., 72.000001 -> 72.0)
    val rounded = (weight * 1000f).roundToInt() / 1000f
    return if (rounded % 1 == 0f) rounded.toInt().toString() else rounded.toString()
}

private fun String.LeadFloatOrNull(): Float? = this.replace(',', '.').toFloatOrNull()

@Composable
fun NumericInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    step: Float = 1f
) {
    val isInteger = step % 1 == 0f

    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            if (!isInteger || !newValue.contains(".") && !newValue.contains(",")) {
                onValueChange(newValue)
            }
        },
        label = { Text(text = label, style = MaterialTheme.typography.titleMedium, maxLines = 1) },
        modifier = modifier,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isInteger) KeyboardType.Number else KeyboardType.Decimal
        ),
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontSize = 14.sp),
        leadingIcon = {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                IconButton(
                    onClick = {
                        val current = value.LeadFloatOrNull() ?: 0f
                        if (current >= step) {
                            val next = if (isInteger) (current.toInt() - step.toInt()).toFloat() else current - step
                            onValueChange(formatWeight(next))
                        }
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Remove", modifier = Modifier.size(20.dp))
                }
            }
        },
        trailingIcon = {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                IconButton(
                    onClick = {
                        val current = value.LeadFloatOrNull() ?: 0f
                        val next = if (isInteger) (current.toInt() + step.toInt()).toFloat() else current + step
                        onValueChange(formatWeight(next))
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(20.dp))
                }
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
    viewModel: WorkoutViewModel,
    stravaViewModel: StravaViewModel = viewModel(),
    themeViewModel: ThemeViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val backupManager = remember { BackupManager(context) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var currentScreen by remember { mutableStateOf(Screen.Summary) }

    val workouts by viewModel.allWorkouts.collectAsState()
    val bodyWeights by viewModel.allBodyWeights.collectAsState()
    val notesList by viewModel.allNotes.collectAsState()

    val workoutHistory = remember(workouts) {
        workouts.asSequence()
            .sortedWith(compareByDescending<Workout> { it.date }.thenByDescending { it.id })
            .distinctBy { it.exerciseName }
            .toList()
    }

    var showAddWorkoutDialog by remember { mutableStateOf(false) }
    var showAddWeightDialog by remember { mutableStateOf(false) }
    var showAddNoteDialog by remember { mutableStateOf(false) }
    var editingWorkout by remember { mutableStateOf<Workout?>(null) }
    var copyingWorkout by remember { mutableStateOf<Workout?>(null) }
    var editingWeight by remember { mutableStateOf<BodyWeight?>(null) }
    var editingNote by remember { mutableStateOf<Note?>(null) }
    var workoutToDelete by remember { mutableStateOf<Workout?>(null) }
    var weightToDelete by remember { mutableStateOf<BodyWeight?>(null) }
    var noteToDelete by remember { mutableStateOf<Note?>(null) }
    val currentSong by MediaRepository.getInstance().currentSong.collectAsState()

    val primaryColor by themeViewModel.primaryColor.collectAsState()

    // Google Sign-In Setup
    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                task.getResult(ApiException::class.java)
                Toast.makeText(context, "Google Drive linked!", Toast.LENGTH_SHORT).show()
            } catch (e: ApiException) {
                Log.e("WorkoutScreen", "Sign-in failed: ${e.statusCode}", e)
                Toast.makeText(context, "Sign-in failed: ${e.statusCode}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Manual Drive Backup Trigger
    val performDriveBackup: () -> Unit = {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account == null) {
            googleSignInLauncher.launch(googleSignInClient.signInIntent)
        } else {
            Toast.makeText(context, "Uploading to Drive...", Toast.LENGTH_SHORT).show()
            coroutineScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val credential = GoogleAccountCredential.usingOAuth2(
                            context, Collections.singleton(DriveScopes.DRIVE_FILE)
                        ).apply { selectedAccount = account.account }

                        val driveService = Drive.Builder(
                            NetHttpTransport(), GsonFactory.getDefaultInstance(), credential
                        ).setApplicationName("Tracker").build()

                        val driveHelper = GoogleDriveHelper(driveService)

                        val db = WorkoutDatabase.getDatabase(context)
                        db.query(androidx.sqlite.db.SimpleSQLiteQuery("PRAGMA wal_checkpoint(FULL)")).use { cursor ->
                            cursor.moveToFirst()
                        }

                        val dbFile = context.getDatabasePath("workout_db")
                        val fileId = driveHelper.uploadFile(dbFile, "application/x-sqlite3", "workout_backup_auto.db")

                        withContext(Dispatchers.Main) {
                            if (fileId != null) {
                                Toast.makeText(context, "Drive backup successful!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Drive upload failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("WorkoutScreen", "Manual backup failed", e)
                    coroutineScope.launch(Dispatchers.Main) {
                        Toast.makeText(context, "Backup error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // Launcher for Saving the Local Backup
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let {
            coroutineScope.launch {
                if (backupManager.backupDatabase(it)) {
                    Toast.makeText(context, "Local backup successful", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Launcher for Restoring the Backup
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch {
                if (backupManager.restoreDatabase(uris)) {
                    Toast.makeText(context, "Restore successful! Restarting...", Toast.LENGTH_LONG).show()
                    val packageManager = context.packageManager
                    val intent = packageManager.getLaunchIntentForPackage(context.packageName)
                    context.startActivity(Intent.makeRestartActivityTask(intent?.component))
                    Runtime.getRuntime().exit(0)
                }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerContentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                NavigationDrawerItem(
                    label = { Text("Summary", modifier = Modifier.padding(start = 8.dp)) },
                    icon = { Icon(Icons.Default.Dashboard, null) },
                    selected = currentScreen == Screen.Summary,
                    onClick = {
                        currentScreen = Screen.Summary
                        coroutineScope.launch { drawerState.close() }
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = primaryColor.copy(alpha = 0.1f),
                        unselectedContainerColor = Color.Transparent,
                        selectedIconColor = primaryColor,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedTextColor = primaryColor,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .padding(NavigationDrawerItemDefaults.ItemPadding)
                        .padding(vertical = 6.dp)
                )
                NavigationDrawerItem(
                    label = { Text("Workouts", modifier = Modifier.padding(start = 8.dp)) },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                    selected = currentScreen == Screen.Workouts,
                    onClick = {
                        currentScreen = Screen.Workouts
                        coroutineScope.launch { drawerState.close() }
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = primaryColor.copy(alpha = 0.1f),
                        unselectedContainerColor = Color.Transparent,
                        selectedIconColor = primaryColor,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedTextColor = primaryColor,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .padding(NavigationDrawerItemDefaults.ItemPadding)
                        .padding(vertical = 6.dp)
                )
                NavigationDrawerItem(
                    label = { Text("Strava Calendar", modifier = Modifier.padding(start = 8.dp)) },
                    icon = { Icon(Icons.Default.DateRange, null) },
                    selected = currentScreen == Screen.StravaCalendar,
                    onClick = {
                        currentScreen = Screen.StravaCalendar
                        coroutineScope.launch { drawerState.close() }
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = primaryColor.copy(alpha = 0.1f),
                        unselectedContainerColor = Color.Transparent,
                        selectedIconColor = primaryColor,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedTextColor = primaryColor,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .padding(NavigationDrawerItemDefaults.ItemPadding)
                        .padding(vertical = 6.dp)
                )
                NavigationDrawerItem(
                    label = { Text("Workout Stats", modifier = Modifier.padding(start = 8.dp)) },
                    icon = { Icon(Icons.Default.BarChart, null) },
                    selected = currentScreen == Screen.WorkoutStats,
                    onClick = {
                        currentScreen = Screen.WorkoutStats
                        coroutineScope.launch { drawerState.close() }
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = primaryColor.copy(alpha = 0.1f),
                        unselectedContainerColor = Color.Transparent,
                        selectedIconColor = primaryColor,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedTextColor = primaryColor,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .padding(NavigationDrawerItemDefaults.ItemPadding)
                        .padding(vertical = 6.dp)
                )
                NavigationDrawerItem(
                    label = { Text("Weight Tracking", modifier = Modifier.padding(start = 8.dp)) },
                    icon = { Icon(Icons.Default.MonitorWeight, null) },
                    selected = currentScreen == Screen.WeightTracking,
                    onClick = {
                        currentScreen = Screen.WeightTracking
                        coroutineScope.launch { drawerState.close() }
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = primaryColor.copy(alpha = 0.1f),
                        unselectedContainerColor = Color.Transparent,
                        selectedIconColor = primaryColor,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedTextColor = primaryColor,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .padding(NavigationDrawerItemDefaults.ItemPadding)
                        .padding(vertical = 6.dp)
                )
                NavigationDrawerItem(
                    label = { Text("Notes", modifier = Modifier.padding(start = 8.dp)) },
                    icon = { Icon(Icons.AutoMirrored.Filled.Notes, null) },
                    selected = currentScreen == Screen.Notes,
                    onClick = {
                        currentScreen = Screen.Notes
                        coroutineScope.launch { drawerState.close() }
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = primaryColor.copy(alpha = 0.1f),
                        unselectedContainerColor = Color.Transparent,
                        selectedIconColor = primaryColor,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedTextColor = primaryColor,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .padding(NavigationDrawerItemDefaults.ItemPadding)
                        .padding(vertical = 6.dp)
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Spacer(modifier = Modifier.weight(1f))

                NavigationDrawerItem(
                    label = { Text("Settings", modifier = Modifier.padding(start = 8.dp)) },
                    icon = { Icon(Icons.Default.Settings, null) },
                    selected = currentScreen == Screen.Settings,
                    onClick = {
                        currentScreen = Screen.Settings
                        coroutineScope.launch { drawerState.close() }
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = primaryColor.copy(alpha = 0.1f),
                        unselectedContainerColor = Color.Transparent,
                        selectedIconColor = primaryColor,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedTextColor = primaryColor,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .padding(NavigationDrawerItemDefaults.ItemPadding)
                        .padding(vertical = 6.dp)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = when (currentScreen) {
                                Screen.Workouts -> "Workouts"
                                Screen.StravaCalendar -> "Strava Calendar"
                                Screen.WeightTracking -> "Weight Tracking"
                                Screen.WorkoutStats -> "Workout Stats"
                                Screen.Notes -> "Notes"
                                Screen.Summary -> "Weekly Summary"
                                Screen.Settings -> "Settings"
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.background,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            floatingActionButton = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if (currentScreen == Screen.Workouts || currentScreen == Screen.WeightTracking || currentScreen == Screen.Notes || currentScreen == Screen.Summary) 20.dp else 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentSong.title != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.large,
                            tonalElevation = 2.dp,
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .height(56.dp)
                                .weight(1f)
                                .padding(end = if (currentScreen == Screen.Workouts || currentScreen == Screen.WeightTracking || currentScreen == Screen.Notes || currentScreen == Screen.Summary) 16.dp else 0.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(0.dp)
                            ) {
                                IconButton(onClick = { MediaRepository.getInstance().togglePlayPause() }) {
                                    Icon(
                                        imageVector = if (currentSong.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Play/Pause",
                                        tint = if (currentSong.isPlaying) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 8.dp)
                                        .clickable { MediaRepository.getInstance().openApp() }
                                ) {
                                    Text(
                                        text = currentSong.title ?: "",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Text(
                                        text = currentSong.artist ?: "",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .combinedClickable(
                                            onClick = { MediaRepository.getInstance().nextTrack() },
                                            onLongClick = {
                                                MediaRepository.getInstance().previousTrack()
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipNext,
                                        contentDescription = "Next (Long press for Previous)",
                                        tint = primaryColor
                                    )
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    if (currentScreen == Screen.Workouts || currentScreen == Screen.Summary) {
                        FloatingActionButton(
                            onClick = { showAddWorkoutDialog = true },
                            containerColor = primaryColor,
                            contentColor = Color.Black,
                            elevation = FloatingActionButtonDefaults.elevation(4.dp),
                        ) {
                            Icon(Icons.Default.Add, "Add Workout", modifier = Modifier.size(32.dp))
                        }
                    } else if (currentScreen == Screen.WeightTracking) {
                        FloatingActionButton(
                            onClick = {
                                viewModel.prepareNewEntry()
                                showAddWeightDialog = true
                            },
                            containerColor = primaryColor,
                            contentColor = Color.Black,
                            elevation = FloatingActionButtonDefaults.elevation(4.dp),
                        ) {
                            Icon(Icons.Default.MonitorWeight, "Add Weight")
                        }
                    } else if (currentScreen == Screen.Notes) {
                        FloatingActionButton(
                            onClick = { showAddNoteDialog = true },
                            containerColor = primaryColor,
                            contentColor = Color.Black,
                            elevation = FloatingActionButtonDefaults.elevation(4.dp),
                        ) {
                            Icon(Icons.Default.Add, "Add Note", modifier = Modifier.size(32.dp))
                        }
                    }
                }
            },
            floatingActionButtonPosition = FabPosition.Center
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) togetherWith
                        fadeOut(animationSpec = tween(300))
                    },
                    label = "ScreenTransition"
                ) { targetScreen ->
                    when (targetScreen) {
                        Screen.Workouts -> {
                            WorkoutListContent(
                                workouts = workouts,
                                primaryColor = primaryColor,
                                onDelete = { workoutToDelete = it },
                                onEdit = { editingWorkout = it },
                                onCopy = { copyingWorkout = it }
                            )
                        }
                        Screen.StravaCalendar -> {
                            StravaCalendarPage(stravaViewModel, primaryColor)
                        }
                        Screen.WeightTracking -> {
                            WeightTrackingPage(
                                bodyWeights = bodyWeights,
                                primaryColor = primaryColor,
                                onWeightClick = { editingWeight = it },
                                onWeightDelete = { weightToDelete = it }
                            )
                        }
                        Screen.WorkoutStats -> {
                            WorkoutStatsPage(workouts, primaryColor)
                        }
                        Screen.Notes -> {
                            NotesPage(
                                notes = notesList,
                                primaryColor = primaryColor,
                                onNoteClick = { editingNote = it },
                                onNoteDelete = { noteToDelete = it }
                            )
                        }
                        Screen.Summary -> {
                            SummaryPage(
                                workouts = workouts,
                                bodyWeights = bodyWeights,
                                stravaViewModel = stravaViewModel,
                                primaryColor = primaryColor,
                                onWorkoutEdit = { editingWorkout = it },
                                onWorkoutDelete = { workoutToDelete = it },
                                onWorkoutCopy = { copyingWorkout = it },
                                onNavigateToWeightTracking = { currentScreen = Screen.WeightTracking }
                            )
                        }
                        Screen.Settings -> {
                            SettingsPage(
                                primaryColor = primaryColor,
                                themeViewModel = themeViewModel,
                                stravaViewModel = stravaViewModel,
                                viewModel = viewModel,
                                performDriveBackup = performDriveBackup,
                                backupLauncher = backupLauncher,
                                restoreLauncher = restoreLauncher,
                                googleSignInClient = googleSignInClient,
                                context = context
                            )
                        }
                    }
                }
            }

            if (showAddWorkoutDialog) {
                WorkoutDialog(
                    history = workoutHistory,
                    onDismiss = { showAddWorkoutDialog = false },
                    onConfirm = { exercise, sets, reps, weight, dateMillis, isPB, weightUnit, notes ->
                        viewModel.addWorkout(exercise, sets, reps, weight, dateMillis, isPB, weightUnit, notes)
                        showAddWorkoutDialog = false
                    }
                )
            }

            if (showAddWeightDialog) {
                BodyWeightDialog(
                    initialWeight = viewModel.weightInput,
                    onDismiss = { showAddWeightDialog = false },
                    onConfirm = { weight, dateMillis, notes ->
                        viewModel.addBodyWeight(weight, dateMillis, notes)
                        showAddWeightDialog = false
                    }
                )
            }

            if (showAddNoteDialog) {
                NoteDialog(
                    onDismiss = { showAddNoteDialog = false },
                    onConfirm = { title, content, dateMillis ->
                        viewModel.addNote(title, content, dateMillis)
                        showAddNoteDialog = false
                    }
                )
            }

            editingWorkout?.let { workout ->
                WorkoutDialog(
                    workout = workout,
                    history = workoutHistory,
                    onDismiss = { editingWorkout = null },
                    onConfirm = { exercise, sets, reps, weight, dateMillis, isPB, weightUnit, notes ->
                        viewModel.updateWorkout(workout.copy(
                            exerciseName = exercise,
                            sets = sets,
                            reps = reps,
                            weight = weight,
                            date = dateMillis,
                            isPersonalBest = isPB,
                            weightUnit = weightUnit,
                            notes = notes
                        ))
                        editingWorkout = null
                    },
                    onDelete = {
                        workoutToDelete = workout
                        editingWorkout = null
                    },
                    onCopy = {
                        copyingWorkout = workout
                        editingWorkout = null
                    }
                )
            }

            copyingWorkout?.let { workout ->
                WorkoutDialog(
                    workout = workout.copy(id = 0, date = System.currentTimeMillis()),
                    history = workoutHistory,
                    onDismiss = { copyingWorkout = null },
                    onConfirm = { exercise, sets, reps, weight, dateMillis, isPB, weightUnit, notes ->
                        viewModel.addWorkout(exercise, sets, reps, weight, dateMillis, isPB, weightUnit, notes)
                        copyingWorkout = null
                    }
                )
            }

            editingWeight?.let { bodyWeight ->
                BodyWeightDialog(
                    bodyWeight = bodyWeight,
                    onDismiss = { editingWeight = null },
                    onConfirm = { weight, dateMillis, notes ->
                        viewModel.updateBodyWeight(bodyWeight.copy(weight = weight, date = dateMillis, notes = notes))
                        editingWeight = null
                    }
                )
            }

            editingNote?.let { note ->
                NoteDialog(
                    note = note,
                    onDismiss = { editingNote = null },
                    onConfirm = { title, content, dateMillis ->
                        viewModel.updateNote(note.copy(title = title, content = content, date = dateMillis))
                        editingNote = null
                    },
                    onDelete = {
                        noteToDelete = note
                        editingNote = null
                    }
                )
            }

            workoutToDelete?.let { workout ->
                AlertDialog(
                    onDismissRequest = { workoutToDelete = null },
                    title = { Text("Delete Workout") },
                    text = { Text("Are you sure you want to delete this ${workout.exerciseName} workout?") },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.deleteWorkout(workout)
                                workoutToDelete = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { workoutToDelete = null }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            weightToDelete?.let { bodyWeight ->
                AlertDialog(
                    onDismissRequest = { weightToDelete = null },
                    title = { Text("Delete Weight Entry") },
                    text = { Text("Are you sure you want to delete this weight entry?") },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.deleteBodyWeight(bodyWeight)
                                weightToDelete = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { weightToDelete = null }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            noteToDelete?.let { note ->
                AlertDialog(
                    onDismissRequest = { noteToDelete = null },
                    properties = DialogProperties(usePlatformDefaultWidth = false),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                    title = { Text("Delete Note") },
                    text = { Text("Are you sure you want to delete this note?") },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.deleteNote(note)
                                noteToDelete = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { noteToDelete = null }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun SummaryPage(
    workouts: List<Workout>,
    bodyWeights: List<BodyWeight>,
    stravaViewModel: StravaViewModel,
    primaryColor: Color,
    onWorkoutEdit: (Workout) -> Unit,
    onWorkoutDelete: (Workout) -> Unit,
    onWorkoutCopy: (Workout) -> Unit,
    onNavigateToWeightTracking: () -> Unit
) {
    val activities by stravaViewModel.activities.collectAsState()
    val isLoading by stravaViewModel.isLoading.collectAsState()
    var refreshTrigger by remember { mutableStateOf(false) }
    
    // Sync refresh state with ViewModel loading state
    val isRefreshing = refreshTrigger && isLoading
    
    LaunchedEffect(isLoading) {
        if (!isLoading && refreshTrigger) {
            refreshTrigger = false
        }
    }

    val lastActivity = remember(activities) { activities.firstOrNull() }
    val lastWeight = remember(bodyWeights) { bodyWeights.maxByOrNull { it.date } }

    val activityData = remember(activities) { stravaViewModel.getActivityData() }

    val today = LocalDate.now()
    val todaysWorkouts = remember(workouts) {
        workouts.filter {
            val date = Instant.ofEpochMilli(it.date).atZone(ZoneId.systemDefault()).toLocalDate()
            date.isEqual(today)
        }
    }

    val weeklyStreak = remember(activityData, today) {
        val startDate = today.minusDays(6)
        (0..6).map { i ->
            val date = startDate.plusDays(i.toLong())
            val dateString = String.format(Locale.getDefault(), "%04d-%02d-%02d", date.year, date.monthValue, date.dayOfMonth)
            val hasStrava = activityData.containsKey(dateString)
            date to hasStrava
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
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        item {
            ElevatedCard(
                modifier = Modifier.
                fillMaxWidth()
                .animateContentSize(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                                    text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(1),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(
                                            color = if (active && date == today) primaryColor
                                            else if (active) primaryColor
                                            else if (date == today) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                            shape = CircleShape
                                        )
                                        .border(
                                            width = 2.dp,
                                            color = if (date == today) Color.White
                                            else Color.Transparent,
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

        if (lastWeight != null) {
            item {
                Text(
                    "Latest Weight",
                    style = MaterialTheme.typography.titleMedium,
                    color = primaryColor,
                    fontWeight = FontWeight.Bold
                )
                
                // Get last 7 days of weight data
                val weekWeights = remember(bodyWeights) {
                    val today = LocalDate.now()
                    val twoDaysAgo = today.minusDays(13)
                    
                    bodyWeights
                        .filter { 
                            val weightDate = Instant.ofEpochMilli(it.date)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                            !weightDate.isBefore(twoDaysAgo) && !weightDate.isAfter(today)
                        }
                        .sortedBy { it.date }
                }

                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize()
                        .padding(top = 8.dp)
                        .clickable { onNavigateToWeightTracking() },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left side - Weight info and trend
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${formatWeight(lastWeight.weight)} kg",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = primaryColor
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = SimpleDateFormat("EEEE d.M.yyyy", Locale.getDefault()).format(Date(lastWeight.date)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        // Right side - Mini graph with background
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

                                // Animation for the path drawing
                                val animationProgress = remember { Animatable(0f) }
                                LaunchedEffect(Unit) {
                                    animationProgress.animateTo(1f, animationSpec = tween(500, easing = FastOutSlowInEasing))
                                }
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val weights = weekWeights.map { it.weight }
                                    val minWeight = weights.minOrNull() ?: 0f
                                    val maxWeight = weights.maxOrNull() ?: 100f
                                    val range = (maxWeight - minWeight).coerceAtLeast(1f)
                                    
                                    val graphWidth = size.width
                                    val graphHeight = size.height
                                    val spacing = graphWidth / (weights.size - 1).coerceAtLeast(1)
                                    
                                    // Draw gradient fill under the line
                                    val fillPath = Path()
                                    weights.forEachIndexed { index, weight ->
                                        val x = index * spacing
                                        val y = graphHeight - ((weight - minWeight) / range * graphHeight)
                                        
                                        if (index == 0) {
                                            fillPath.moveTo(x, graphHeight)
                                            fillPath.lineTo(x, y)
                                        } else {
                                            fillPath.lineTo(x, y)
                                        }
                                    }
                                    fillPath.lineTo(graphWidth, graphHeight)
                                    fillPath.close()
                                    
                                    drawPath(
                                        path = fillPath,
                                        brush = Brush.verticalGradient(
                                            colors = listOf(primaryColor.copy(alpha = 0.2f * animationProgress.value), Color.Transparent)
                                        )
                                    )
                                    
                                    // Draw line path
                                    val linePath = Path()
                                    weights.forEachIndexed { index, weight ->
                                        val x = index * spacing
                                        val y = graphHeight - ((weight - minWeight) / range * graphHeight)
                                        
                                        if (index == 0) {
                                            linePath.moveTo(x, y)
                                        } else {
                                            linePath.lineTo(x, y)
                                        }
                                    }
                                    
                                    // Draw the line
                                    drawPath(
                                        path = linePath,
                                        color = primaryColor,
                                        style = Stroke(width = 2.5.dp.toPx()),
                                        alpha = animationProgress.value
                                    )

                                    // Draw points with animation
                                    weights.forEachIndexed { index, weight ->
                                        val x = index * spacing
                                        val y = graphHeight - ((weight - minWeight) / range * graphHeight)
                                        
                                        // Outer circle (white background) - animated
                                        drawCircle(
                                            color = surfaceColor,
                                            radius = 4.dp.toPx() * animationProgress.value,
                                            center = Offset(x, y)
                                        )
                                        // Inner circle (primary color) - animated
                                        drawCircle(
                                            color = primaryColor,
                                            radius = 3.dp.toPx() * animationProgress.value,
                                            center = Offset(x, y)
                                        )
                                    }
                                }
                                
                                // Trend indicator overlay at top left
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .background(
                                            color = when {
                                                trend > 0.1f -> Color(0xFFDE4A4A).copy(alpha = 0.8f)
                                                trend < -0.1f -> Color(0xFF46CE46).copy(alpha = 0.8f)
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                            },
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = when {
                                            trend > 0.1f -> Icons.Default.TrendingUp
                                            trend < -0.1f -> Icons.Default.TrendingDown
                                            else -> Icons.Default.TrendingFlat
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
                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = primaryColor)
                }
            }
        } else {
            if (recentActivities.isEmpty()) {
                item {
                    Text(
                        "No Strava activities in the past 7 days. Link your account in the Strava Calendar page.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Display activities in 2-column grid
                items(recentActivities.chunked(2)) { activityPair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        activityPair.forEach { activity ->
                            ElevatedCard(
                                modifier = Modifier
                                    .weight(1f)
                                    .animateContentSize()
                                    .height(100.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp),
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
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Straighten,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                            Text(
                                                text = "${String.format(Locale.getDefault(), "%.2f", activity.distance / 1000f)} km",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
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
                                                        totalMinutes < 60 -> "$totalMinutes min"
                                                        totalMinutes % 60 == 0 -> "${totalMinutes / 60} h"
                                                        else -> "${totalMinutes / 60} h ${totalMinutes % 60} min"
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
                        
                        // If odd number of activities, add spacer for alignment
                        if (activityPair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

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
            items(todaysWorkouts) { workout ->
                WorkoutCard(
                    workout = workout,
                    primaryColor = primaryColor,
                    onEdit = { onWorkoutEdit(workout) },
                    onDelete = { onWorkoutDelete(workout) },
                    onCopy = { onWorkoutCopy(workout) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(70.dp))
            }
        }
    }
}

@Composable
fun WorkoutCard(
    workout: Workout,
    primaryColor: Color,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = if (workout.isPersonalBest) PersonalBestGold else primaryColor

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .padding(vertical = 4.dp)
            .clickable { onEdit() },
        colors = CardDefaults.cardColors(containerColor =  MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
    ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp)
            ) {
                Text(
                    text = workout.exerciseName,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (workout.isPersonalBest) PersonalBestGold else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                val details = buildString {
                    if (workout.sets > 0) append("${workout.sets} sets ")
                    if (workout.reps > 0) {
                    if (workout.sets > 0) append("x ")
                    append("${workout.reps} reps ")
                    }
                    if (workout.weight > 0)append("@ ${formatWeight(workout.weight)}${workout.weightUnit}")
                }
                Text(
                    text = details,
                    color = if (workout.isPersonalBest) PersonalBestGold else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)

                if (workout.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = workout.notes,
                        color = if (workout.isPersonalBest) PersonalBestGold else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDialog(
    workout: Workout? = null,
    history: List<Workout> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (String, Int, Int, Float, Long, Boolean, String, String) -> Unit,
    onDelete: (() -> Unit)? = null,
    onCopy: (() -> Unit)? = null
) {
    var exercise by remember { mutableStateOf(workout?.exerciseName ?: "") }
    var expanded by remember { mutableStateOf(false) }

    var sets by remember { mutableStateOf(workout?.sets?.toString() ?: "") }
    var reps by remember { mutableStateOf(workout?.reps?.toString() ?: "") }
    var weight by remember { mutableStateOf(workout?.weight?.let { formatWeight(it) } ?: "") }
    val weightUnit = "kg"
    var notes by remember { mutableStateOf(workout?.notes ?: "") }
    var isPB by remember { mutableStateOf(workout?.isPersonalBest ?: false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = workout?.date ?: System.currentTimeMillis())
    var showDatePicker by remember { mutableStateOf(false) }

    val lastPerformance = remember(exercise, history) {
        history.find { it.exerciseName.equals(exercise, ignoreCase = true) }
    }

    val suggestions = remember(exercise, history) {
        if (exercise.isEmpty()) {
            history.asSequence().map { it.exerciseName }.distinct().take(8).toList()
        } else {
            history.asSequence()
                .filter { it.exerciseName.contains(exercise, ignoreCase = true) }
                .map { it.exerciseName }
                .distinct()
                .filter { it.lowercase() != exercise.lowercase() }
                .take(10)
                .toList()
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = { TextButton(onClick = { showDatePicker = false }) { Text("OK") } }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.Center)
            .padding(24.dp)
            .fillMaxWidth(),
        title = { Text(if (workout == null) "Add Workout" else "Edit Workout") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (exercise.isEmpty() && history.isNotEmpty()) {
                    Text("Recent exercises:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        history.take(8).forEach { recent ->
                            AssistChip(
                                onClick = {
                                    exercise = recent.exerciseName
                                    sets = recent.sets.toString()
                                    reps = recent.reps.toString()
                                    weight = formatWeight(recent.weight)
                                },
                                label = { Text(recent.exerciseName) }
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = expanded && suggestions.isNotEmpty(),
                    onExpandedChange = { expanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = exercise,
                        onValueChange = {
                            exercise = it
                            expanded = true
                        },
                        label = { Text("Exercise") },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryEditable)
                            .fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                    )

                    ExposedDropdownMenu(
                        expanded = expanded && suggestions.isNotEmpty(),
                        onDismissRequest = { expanded = false }
                    ) {
                        suggestions.forEach { suggestion ->
                            DropdownMenuItem(
                                text = { Text(suggestion) },
                                onClick = {
                                    exercise = suggestion
                                    history.find { it.exerciseName == suggestion }?.let { recent ->
                                        sets = recent.sets.toString()
                                        reps = recent.reps.toString()
                                        weight = formatWeight(recent.weight)
                                    }
                                    expanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                        }
                    }
                }

                lastPerformance?.let { last ->
                    Text(
                        text = "Last time: ${last.sets}x${last.reps} @ ${formatWeight(last.weight)}${last.weightUnit}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(bottom = 4.dp)
                            .clickable {
                                sets = last.sets.toString()
                                reps = last.reps.toString()
                                weight = formatWeight(last.weight)
                            }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumericInput(value = sets, onValueChange = { sets = it }, label = "Sets", modifier = Modifier.weight(1f))
                    NumericInput(value = reps, onValueChange = { reps = it }, label = "Reps", modifier = Modifier.weight(1f))
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NumericInput(
                        value = weight,
                        onValueChange = { weight = it },
                        label = "Weight (kg)",
                        modifier = Modifier.weight(0.5f),
                        step = 2.5f
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier =
                            Modifier.padding(top = 4.dp, end = 16.dp)
                                    .weight(0.5f)
                    ) {
                        Checkbox(
                            checked = isPB,
                            onCheckedChange = { isPB = it },
                        )
                        Text(
                            "Personal Best",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )

                OutlinedTextField(
                    value = SimpleDateFormat("EEEE d.M.yy", Locale.getDefault()).format(Date(datePickerState.selectedDateMillis ?: System.currentTimeMillis())),
                    onValueChange = {},
                    label = { Text("Date") },
                    readOnly = true,
                    trailingIcon = { IconButton(onClick = { showDatePicker = true }) { Icon(Icons.Default.DateRange, null) } },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Delete and Copy buttons (only when editing)
                if (onDelete != null || onCopy != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        onCopy?.let {
                            IconButton(onClick = it) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Copy",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        onDelete?.let {
                            IconButton(onClick = it) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = {
                        onConfirm(
                            exercise,
                            sets.toIntOrNull() ?: 0,
                            reps.toIntOrNull() ?: 0,
                            weight.LeadFloatOrNull() ?: 0f,
                            datePickerState.selectedDateMillis ?: System.currentTimeMillis(),
                            isPB,
                            weightUnit,
                            notes
                        )
                    }) { Text("Save") }
                }
            }
        },
        dismissButton = null
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorkoutListContent(
    workouts: List<Workout>,
    primaryColor: Color,
    onDelete: (Workout) -> Unit,
    onEdit: (Workout) -> Unit,
    onCopy: (Workout) -> Unit
) {
    val groupedWorkouts = workouts.groupBy {
        SimpleDateFormat("EEEE d.M.yyyy", Locale.getDefault()).format(Date(it.date))
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 85.dp)
    ) {
        groupedWorkouts.forEach { (date, workoutsInDay) ->
            stickyHeader {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
                ) {
                    Text(
                        text = date,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 24.dp),
                        color = primaryColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // Display workouts in 2-column grid
            items(workoutsInDay.chunked(2), key = { pair -> pair.first().id }) { workoutPair ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    workoutPair.forEach { workout ->
                        WorkoutCard(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .animateContentSize()
                                .animateItem(),
                            workout = workout,
                            primaryColor = primaryColor,
                            onDelete = { onDelete(workout) },
                            onEdit = { onEdit(workout) },
                            onCopy = { onCopy(workout) }
                        )
                    }
                    
                    // If odd number of workouts, add spacer for alignment
//                    if (workoutPair.size == 1) {
//                       Spacer(modifier = Modifier.weight(1f))
//                    }
                }
            }
        }
    }
}

private fun getIconForActivity(type: String): ImageVector {
    return when (type) {
        "WeightTraining" -> Icons.Default.FitnessCenter
        "Run" -> Icons.AutoMirrored.Filled.DirectionsRun
        "Ride" -> Icons.AutoMirrored.Filled.DirectionsBike
        "Swim" -> Icons.Default.Waves
        "Walk" -> Icons.AutoMirrored.Filled.DirectionsWalk
        "Yoga" -> Icons.Default.SelfImprovement
        "Hike" -> Icons.Default.Terrain
        else -> Icons.Default.Star
    }
}

@Composable
fun StravaCalendarPage(stravaViewModel: StravaViewModel, primaryColor: Color) {
    val context = LocalContext.current
    val activities by stravaViewModel.activities.collectAsState()
    val isLoading by stravaViewModel.isLoading.collectAsState()
    val error by stravaViewModel.error.collectAsState()
    val profilePicUrl by stravaViewModel.profilePicUrl.collectAsState()
    var refreshTrigger by remember { mutableStateOf(false) }
    
    // Sync refresh state with ViewModel loading state
    val isRefreshing = refreshTrigger && isLoading
    
    LaunchedEffect(isLoading) {
        if (!isLoading && refreshTrigger) {
            refreshTrigger = false
        }
    }

    val activityData = remember(activities) { stravaViewModel.getActivityData() }
    val streak = remember(activities) { stravaViewModel.getWeeklyStreak() }
    val totalStreakActivities = remember(activities) { stravaViewModel.getTotalStreakActivities() }


    LaunchedEffect(error) {
        error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
        }
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
                            .appendQueryParameter("client_id", "206279")
                            .appendQueryParameter("redirect_uri", "tracker-app://localhost")
                            .appendQueryParameter("response_type", "code")
                            .appendQueryParameter("approval_prompt", "force")
                            .appendQueryParameter("scope", "activity:read_all,profile:read_all")
                            .build()

                        context.startActivity(Intent(Intent.ACTION_VIEW, authUri))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor, contentColor = Color.Black)
                ) {
                    Text("Login with Strava", fontSize = 18.sp)
                }
            }
        } else {
            item {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(14.dp)
                            .fillMaxWidth(),
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

                        Row(verticalAlignment = Alignment.CenterVertically)
                        { if (profilePicUrl.isNotEmpty()) {
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
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            val months = (0..2).map { YearMonth.now().minusMonths(it.toLong()) }
            items(months) { month ->
                StravaCalendar(month, activityData, primaryColor)
                Spacer(modifier = Modifier.height(32.dp))
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
    }
}

@Composable
fun StravaCalendar(month: YearMonth, activityData: Map<String, List<String>>, primaryColor: Color) {
    val daysInMonth = month.lengthOfMonth()
    val firstDayOfMonth = (month.atDay(1).dayOfWeek.value - 1)
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 40.dp)
        ) {
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 40.dp)
            ) {
                var currentDayIndex = 0
                val totalSlots = firstDayOfMonth + daysInMonth
                val rows = (totalSlots + 6) / 7

                for (row in 0 until rows) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        for (col in 0 until 7) {
                            val slotIndex = row * 7 + col
                            if (slotIndex < firstDayOfMonth || currentDayIndex >= daysInMonth) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val dayNum = if (slotIndex < firstDayOfMonth) {
                                        val prevMonth = month.minusMonths(1)
                                        prevMonth.lengthOfMonth() - (firstDayOfMonth - slotIndex - 1)
                                    } else {
                                        slotIndex - (firstDayOfMonth + daysInMonth) + 1
                                    }
                                    Text(text = dayNum.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), fontSize = 14.sp)
                                }
                            } else {
                                currentDayIndex++
                                val day = currentDayIndex
                                val dateString = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, monthValue, day)
                                val activitiesOnDay = activityData[dateString] ?: emptyList()
                                val isToday = today.year == year && today.monthValue == monthValue && today.dayOfMonth == day

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (activitiesOnDay.isNotEmpty()) {
                                        if (isToday) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .background(primaryColor, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    getIconForActivity(activitiesOnDay.first()),
                                                    null,
                                                    tint = Color.Black,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }    else  {
                                                Box(
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .background(MaterialTheme.colorScheme.onSurface, CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        getIconForActivity(activitiesOnDay.first()),
                                                        null,
                                                                tint = MaterialTheme.colorScheme.surface,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                            }
                                        }
                                    } else if (isToday) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .border(2.dp, primaryColor, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(text = day.toString(), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        }
                                    } else {
                                        Text(text = day.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val totalSlots = firstDayOfMonth + daysInMonth
            val rows = (totalSlots + 6) / 7

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .width(36.dp)
                    .height((rows * 56).dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    repeat(rows) { rowIndex ->
                        Box(
                            modifier = Modifier
                                .height(55.dp)
                                .width(36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val weekStartDay = if (rowIndex == 0) 1 else (rowIndex * 7 - firstDayOfMonth + 1)
                            val weekEndDay = minOf(daysInMonth, (rowIndex + 1) * 7 - firstDayOfMonth)

                            val isCurrentWeek = isActualCurrentMonth && today.dayOfMonth in weekStartDay..weekEndDay

                            if (isCurrentWeek) {
                                val lastMonday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1)
                                var hasActivityLastWeek = false
                                for (i in 0 until 7) {
                                    val d = lastMonday.plusDays(i.toLong())
                                    val dateStr = String.format(Locale.getDefault(), "%04d-%02d-%02d", d.year, d.monthValue, d.dayOfMonth)
                                    if (activityData.containsKey(dateStr)) {
                                        hasActivityLastWeek = true
                                        break
                                    }
                                }

                                if (hasActivityLastWeek) {
                                    Box(
                                        modifier = Modifier.size(36.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Bolt,
                                            contentDescription = null,
                                            tint = primaryColor,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                } else {
                                    var hasActivityThisWeek = false
                                    for (d in weekStartDay..weekEndDay) {
                                        val dateStr = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, monthValue, d)
                                        if (activityData.containsKey(dateStr)) {
                                            hasActivityThisWeek = true
                                            break
                                        }
                                    }
                                    if (hasActivityThisWeek) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .background(primaryColor, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            } else {
                                var hasActivity = false
                                for (d in weekStartDay..weekEndDay) {
                                    val dateStr = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, monthValue, d)
                                    if (activityData.containsKey(dateStr)) {
                                        hasActivity = true
                                        break
                                    }
                                }

                                if (hasActivity) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .background(primaryColor, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

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
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Total Volume Lifted", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = primaryColor,
                        trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(70.dp))
        }
    }
}

@Composable
fun WeightTrackingPage(
    bodyWeights: List<BodyWeight>,
    primaryColor: Color,
    onWeightClick: (BodyWeight) -> Unit,
    onWeightDelete: (BodyWeight) -> Unit
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
                val weightDiff = last.weight - first.weight
                val ratePerDay = weightDiff / daysDiff
                val predictedWeight = last.weight + (ratePerDay * 30)
                Pair(predictedWeight, ratePerDay * 7) // (Weight in 30 days, Change per week)
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        if (sortedWeights.isNotEmpty()) {
            item {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Current Weight", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("${formatWeight(sortedWeights.last().weight)} kg", style = MaterialTheme.typography.headlineMedium, color = primaryColor, fontWeight = FontWeight.Bold)
                            }
                            prediction?.let { (_, rate) ->
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Trend", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val sign = if (rate >= 0) "+" else ""
                                    Text("$sign${String.format(Locale.getDefault(), "%.2f", rate)} kg/week", color = if (rate <= 0) Color.Green else Color.Red, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        prediction?.let { (pred, _) ->
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("30-Day Prediction: ${String.format(Locale.getDefault(), "%.1f", pred)} kg", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Box(modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .padding(vertical = 8.dp)) {
                            WeightChart(weights = sortedWeights, color = primaryColor)
                        }
                    }
                }
            }

            item {
                Text("History", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
            }

            items(sortedWeights.reversed(), key = { it.id }) { weightEntry ->
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .animateContentSize()
                        .animateItem()
                        .clickable { onWeightClick(weightEntry) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp)
                        ) {
                            Text(SimpleDateFormat("EEEE d.M.yyyy", Locale.getDefault()).format(Date(weightEntry.date)), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                            Text("${formatWeight(weightEntry.weight)} kg",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(top = 6.dp))
                            if (weightEntry.notes.isNotEmpty()) {
                                Text(
                                    weightEntry.notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        IconButton(onClick = { onWeightDelete(weightEntry) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        } else {
            item {
                Text("Add your first weight entry to see progress!", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 24.dp))
            }
        }

        item {
            Spacer(modifier = Modifier.height(70.dp))
        }
    }
}

@Composable
fun WeightChart(weights: List<BodyWeight>, color: Color) {
    if (weights.isEmpty()) return

    val minWeight = weights.minOf { it.weight } - 1f
    val maxWeight = weights.maxOf { it.weight } + 1f
    val weightRange = maxOf(1f, maxWeight - minWeight)

    val minDate = weights.first().date
    val maxDate = weights.last().date
    val dateRange = maxOf(1L, maxDate - minDate)

    // Animation for the path drawing
    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(weights) {
        animationProgress.animateTo(1f, animationSpec = tween(500, easing = FastOutSlowInEasing))
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        val points = weights.map { weight ->
            val x = if (dateRange == 0L) width / 2 else ((weight.date - minDate).toFloat() / dateRange.toFloat()) * width
            val y = height - ((weight.weight - minWeight) / weightRange) * height
            Offset(x, y)
        }

        if (points.size > 1) {
            val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    lineTo(points[i].x, points[i].y)
                }
            }

            // Drawing the path with animation
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 3.dp.toPx()),
                alpha = animationProgress.value
            )

            val fillPath = Path().apply {
                addPath(path)
                lineTo(width, height)
                lineTo(0f, height)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(color.copy(alpha = 0.2f * animationProgress.value), Color.Transparent)
                )
            )
        }

        points.forEach { point ->
            drawCircle(color = color, radius = 4.dp.toPx() * animationProgress.value, center = point)
        }
    }
}

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
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = bodyWeight?.date ?: System.currentTimeMillis())
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = { TextButton(onClick = { showDatePicker = false }) { Text("OK") } }
        ) {
            DatePicker(state = datePickerState)
        }
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
                    value = SimpleDateFormat("EEEE d.M.yyyy", Locale.getDefault()).format(Date(datePickerState.selectedDateMillis ?: System.currentTimeMillis())),
                    onValueChange = {},
                    label = { Text("Date") },
                    readOnly = true,
                    trailingIcon = { IconButton(onClick = { showDatePicker = true }) { Icon(Icons.Default.DateRange, null) } },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val w = weight.LeadFloatOrNull() ?: 0f
                if (w > 0) {
                    onConfirm(w, datePickerState.selectedDateMillis ?: System.currentTimeMillis(), notes)
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun NotesPage(
    notes: List<Note>,
    primaryColor: Color,
    onNoteClick: (Note) -> Unit,
    onNoteDelete: (Note) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        if (notes.isNotEmpty()) {
            items(notes, key = { it.id }) { note ->
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .animateContentSize()
                        .animateItem()
                        .clickable { onNoteClick(note) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = SimpleDateFormat("EEEE d.M.yyyy", Locale.getDefault()).format(Date(note.date)),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            if (note.title.isNotEmpty()) {
                                Text(
                                    text = note.title,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                            Text(
                                text = note.content,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 25,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }
        } else {
            item {
                Text(
                    text = "Start taking notes to keep track of your progress!",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }
        }
        item {
            Spacer(modifier = Modifier.height(70.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDialog(
    note: Note? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Long) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var title by remember { mutableStateOf(note?.title ?: "") }
    var content by remember { mutableStateOf(note?.content ?: "") }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = note?.date ?: System.currentTimeMillis())
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = { TextButton(onClick = { showDatePicker = false }) { Text("OK") } }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        title = { Text(if (note == null) "Add Note" else "Edit Note") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Content") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 25,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )

                OutlinedTextField(
                    value = SimpleDateFormat("EEEE d.M.yyyy", Locale.getDefault()).format(Date(datePickerState.selectedDateMillis ?: System.currentTimeMillis())),
                    onValueChange = {},
                    label = { Text("Date") },
                    readOnly = true,
                    trailingIcon = { IconButton(onClick = { showDatePicker = true }) { Icon(Icons.Default.DateRange, null) } },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (note != null && onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = {
                    if (content.isNotEmpty()) {
                        onConfirm(title, content, datePickerState.selectedDateMillis ?: System.currentTimeMillis())
                    }
                }) { Text("Save") }
            }
        },
        dismissButton = null
    )
}


@Composable
fun SettingsPage(
    primaryColor: Color,
    themeViewModel: ThemeViewModel,
    stravaViewModel: StravaViewModel,
    viewModel: WorkoutViewModel,
    performDriveBackup: () -> Unit,
    backupLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    restoreLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    googleSignInClient: com.google.android.gms.auth.api.signin.GoogleSignInClient,
    context: android.content.Context
) {
    val workouts by viewModel.allWorkouts.collectAsState()
    val bodyWeights by viewModel.allBodyWeights.collectAsState()
    val notesList by viewModel.allNotes.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "Appearance",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
                .animateContentSize(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Accent Color (RGB)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Column(verticalArrangement = Arrangement.spacedBy((-8).dp)) {
                        Slider(
                            value = primaryColor.red,
                            onValueChange = {
                                themeViewModel.updatePrimaryColor(
                                    primaryColor.copy(
                                        red = it
                                    )
                                )
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = primaryColor,
                                activeTrackColor = primaryColor
                            ),
                            thumb = {
                                SliderDefaults.Thumb(
                                    interactionSource = remember { MutableInteractionSource() },
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            track = { sliderState ->
                                SliderDefaults.Track(
                                    sliderState = sliderState,
                                    modifier = Modifier.height(4.dp),
                                    thumbTrackGapSize = 4.dp
                                )
                            }
                        )

                        Slider(
                            value = primaryColor.green,
                            onValueChange = {
                                themeViewModel.updatePrimaryColor(
                                    primaryColor.copy(
                                        green = it
                                    )
                                )
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = primaryColor,
                                activeTrackColor = primaryColor
                            ),
                            thumb = {
                                SliderDefaults.Thumb(
                                    interactionSource = remember { MutableInteractionSource() },
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            track = { sliderState ->
                                SliderDefaults.Track(
                                    sliderState = sliderState,
                                    modifier = Modifier.height(4.dp),
                                    thumbTrackGapSize = 4.dp
                                )
                            }
                        )

                        Slider(
                            value = primaryColor.blue,
                            onValueChange = {
                                themeViewModel.updatePrimaryColor(
                                    primaryColor.copy(
                                        blue = it
                                    )
                                )
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = primaryColor,
                                activeTrackColor = primaryColor
                            ),
                            thumb = {
                                SliderDefaults.Thumb(
                                    interactionSource = remember { MutableInteractionSource() },
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            track = { sliderState ->
                                SliderDefaults.Track(
                                    sliderState = sliderState,
                                    modifier = Modifier.height(4.dp),
                                    thumbTrackGapSize = 4.dp
                                )
                            }
                        )
                    }
                }
            }
        }


        item {
            ElevatedCard(
                modifier = Modifier.
                fillMaxWidth()
                .animateContentSize(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Data Backup & Restore",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // First Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = performDriveBackup,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = primaryColor,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudUpload,
                                    contentDescription = "Drive Backup",
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    "Drive Backup",
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Left
                                )
                            }
                        }

                        Button(
                            onClick = { backupLauncher.launch("workout_backup.db") },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = primaryColor,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = "Local Backup",
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    "Local Backup",
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Left
                                )
                            }
                        }
                    }

                    // Second Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { restoreLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = primaryColor,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SettingsBackupRestore,
                                    contentDescription = "Restore",
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    "Restore Data",
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Left
                                )
                            }
                        }

                        Button(
                            onClick = {
                                googleSignInClient.signOut().addOnCompleteListener {
                                    Toast.makeText(
                                        context,
                                        "Google signed out",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = "Google Logout",
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    "Google Sign Out",
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Left
                                )
                            }
                        }
                    }

                    // Third Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                stravaViewModel.logout()
                                Toast.makeText(
                                    context,
                                    "Strava signed out",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DirectionsRun,
                                    contentDescription = "Strava Logout",
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    "Strava Sign Out",
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Left
                                )
                            }
                        }

                        Button(
                            onClick = {
                                showDeleteConfirmDialog = true
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = Color.Red
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteForever,
                                    contentDescription = "Clear All",
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    "Clear All Data",
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Left
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    "Delete All Data?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "This will permanently delete:\n\n" +
                            "• All workouts\n" +
                            "• All body weight entries\n" +
                            "• All notes\n\n" +
                            "This action cannot be undone!",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            try {
                                // Delete all data
                                workouts.forEach { viewModel.deleteWorkout(it) }
                                bodyWeights.forEach { viewModel.deleteBodyWeight(it) }
                                notesList.forEach { viewModel.deleteNote(it) }

                                showDeleteConfirmDialog = false
                                Toast.makeText(
                                    context,
                                    "All data deleted successfully",
                                    Toast.LENGTH_LONG
                                ).show()
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    "Error deleting data: ${e.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete All", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
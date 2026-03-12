@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.serkka.tracker

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.serkka.tracker.ui.theme.DarkSurfaceColor

@Composable
fun WorkoutScreen(
    viewModel: WorkoutViewModel,
    stravaViewModel: StravaViewModel = viewModel(),
    themeViewModel: ThemeViewModel = viewModel(),
    timerViewModel: WorkoutTimerViewModel = viewModel()
) {
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route ?: Screen.Summary.name

    // ── List states (preserved across nav) ───────────────────────────────────
    val workoutsListState = rememberLazyListState()
    val summaryListState  = rememberLazyListState()
    val weightListState   = rememberLazyListState()
    val notesListState    = rememberLazyListState()

    val isFabVisible by remember {
        derivedStateOf {
            val state = when (currentRoute) {
                Screen.Workouts.name       -> workoutsListState
                Screen.Summary.name        -> summaryListState
                Screen.WeightTracking.name -> weightListState
                Screen.Notes.name          -> notesListState
                else                       -> null
            }
            (state?.firstVisibleItemIndex ?: 0) <= 2
        }
    }

    val workouts    by viewModel.allWorkouts.collectAsState()
    val bodyWeights by viewModel.allBodyWeights.collectAsState()
    val notesList   by viewModel.allNotes.collectAsState()

    val workoutHistory = remember(workouts) {
        workouts.asSequence()
            .sortedWith(compareByDescending<Workout> { it.date }.thenByDescending { it.id })
            .distinctBy { it.exerciseName }
            .toList()
    }

    var showAddWorkoutDialog by remember { mutableStateOf(false) }
    var showAddWeightDialog  by remember { mutableStateOf(false) }
    var showAddNoteDialog    by remember { mutableStateOf(false) }
    var editingWorkout       by remember { mutableStateOf<Workout?>(null) }
    var copyingWorkout       by remember { mutableStateOf<Workout?>(null) }
    var editingWeight        by remember { mutableStateOf<BodyWeight?>(null) }
    var editingNote          by remember { mutableStateOf<Note?>(null) }
    var workoutToDelete      by remember { mutableStateOf<Workout?>(null) }
    var weightToDelete       by remember { mutableStateOf<BodyWeight?>(null) }
    var noteToDelete         by remember { mutableStateOf<Note?>(null) }

    val currentSong    by MediaRepository.getInstance().currentSong.collectAsState()
    val timerIsRunning by timerViewModel.isRunning.collectAsState()
    val timerElapsed   by timerViewModel.elapsedSeconds.collectAsState()
    val primaryColor   by themeViewModel.primaryColor.collectAsState()

    /** Navigate with single-top + state restore — no back stack buildup on repeated taps. */
    fun navigate(route: String) = navController.navigate(route) {
        popUpTo(Screen.Summary.name) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    val navBarColors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        indicatorColor = primaryColor.copy(alpha = 0.1f),
    )

    val drawerItemColors: @Composable () -> NavigationDrawerItemColors = {
        NavigationDrawerItemDefaults.colors(
            selectedContainerColor = primaryColor.copy(alpha = 0.1f),
            unselectedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
            selectedIconColor = primaryColor,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedTextColor = primaryColor,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
                    label    = { Text("Workout Stats", modifier = Modifier.padding(start = 8.dp)) },
                    icon     = { Icon(Icons.Default.BarChart, null) },
                    selected = currentRoute == Screen.WorkoutStats.name,
                    onClick  = {
                        navigate(Screen.WorkoutStats.name)
                        coroutineScope.launch { drawerState.close() }
                    },
                    colors   = drawerItemColors(),
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding).padding(vertical = 6.dp)
                )

                NavigationDrawerItem(
                    label    = { Text("Notes", modifier = Modifier.padding(start = 8.dp)) },
                    icon     = { Icon(Icons.AutoMirrored.Filled.Notes, null) },
                    selected = currentRoute == Screen.Notes.name,
                    onClick  = {
                        navigate(Screen.Notes.name)
                        coroutineScope.launch { drawerState.close() }
                    },
                    colors   = drawerItemColors(),
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding).padding(vertical = 6.dp)
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Spacer(modifier = Modifier.weight(1f))

                NavigationDrawerItem(
                    label    = { Text("Settings", modifier = Modifier.padding(start = 8.dp)) },
                    icon     = { Icon(Icons.Default.Settings, null) },
                    selected = currentRoute == Screen.Settings.name,
                    onClick  = {
                        navigate(Screen.Settings.name)
                        coroutineScope.launch { drawerState.close() }
                    },
                    colors   = drawerItemColors(),
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding).padding(vertical = 6.dp)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            modifier = Modifier.padding(start = 8.dp),
                            text = Screen.entries.find { it.name == currentRoute }?.title ?: ""
                        )
                    },
                    actions = {
                        // Live timer pill — visible on all screens except the timer itself
                        AnimatedVisibility(
                            visible = timerIsRunning && currentRoute != Screen.WorkoutTimer.name,
                            enter   = fadeIn() + slideInHorizontally { it },
                            exit    = fadeOut() + slideOutHorizontally { it }
                        ) {
                            AssistChip(
                                onClick = { navigate(Screen.WorkoutTimer.name) },
                                label = {
                                    Text(
                                        text = formatElapsed(timerElapsed),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Timer, contentDescription = "Timer running", modifier = Modifier.size(16.dp))
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    leadingIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                        IconButton(onClick = { navigate(Screen.Notes.name) }) {
                            Icon(Icons.Default.Notes, contentDescription = "Notes", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(onClick = { navigate(Screen.Settings.name) }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurface)
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

            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Spacer(modifier = Modifier.width(4.dp))
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.CalendarMonth, null) },
                        label = { Text("Strava") },
                        selected = currentRoute == Screen.StravaCalendar.name,
                        onClick  = { navigate(Screen.StravaCalendar.name) },
                        colors = navBarColors
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.FitnessCenter, null) },
                        label = { Text("Workouts") },
                        selected = currentRoute == Screen.Workouts.name,
                        onClick  = { navigate(Screen.Workouts.name) },
                        colors = navBarColors
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Dashboard, null) },
                        label = { Text("Summary") },
                        selected = currentRoute == Screen.Summary.name,
                        onClick  = { navigate(Screen.Summary.name) },
                        colors = navBarColors
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.MonitorWeight, null) },
                        label = { Text("Weight") },
                        selected = currentRoute == Screen.WeightTracking.name,
                        onClick  = { navigate(Screen.WeightTracking.name) },
                        colors = navBarColors
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Timer, null) },
                        label = { Text("Timer") },
                        selected = currentRoute == Screen.WorkoutTimer.name,
                        onClick  = { navigate(Screen.WorkoutTimer.name) },
                        colors = navBarColors
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
            },

            floatingActionButton = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val hasMusicWidget = currentSong.title != null && currentSong.packageName == "com.spotify.music"
                    val fabScreens = setOf(Screen.Workouts.name, Screen.WeightTracking.name, Screen.Notes.name)

                    if (hasMusicWidget) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.large,
                            tonalElevation = 2.dp,
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = if (isFabVisible && currentRoute in fabScreens) 16.dp else 0.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                                ) {
                                    val haptic = LocalHapticFeedback.current
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .combinedClickable(
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    MediaRepository.getInstance().togglePlayPause()
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (currentSong.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = "Play/Pause",
                                            tint = if (currentSong.isPlaying) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(28.dp)
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
                                            color = MaterialTheme.colorScheme.onSurface,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
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
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    MediaRepository.getInstance().nextTrack()
                                                },
                                                onLongClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    MediaRepository.getInstance().previousTrack()
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SkipNext,
                                            contentDescription = "Next (Long press for Previous)",
                                            tint = primaryColor,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                                    LinearProgressIndicator(
                                        progress = {
                                            val position = currentSong.position?.toFloat() ?: 0f
                                            val duration = currentSong.duration?.toFloat() ?: 1f
                                            if (duration > 0) position / duration else 0f
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 10.dp)
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp)),
                                        color = primaryColor,
                                        trackColor = DarkSurfaceColor,
                                    )
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    AnimatedVisibility(
                        visible = isFabVisible,
                        enter   = fadeIn() + scaleIn(),
                        exit    = fadeOut() + scaleOut()
                    ) {
                        when (currentRoute) {
                            Screen.Workouts.name -> FloatingActionButton(
                                onClick = { showAddWorkoutDialog = true },
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = primaryColor,
                                elevation = FloatingActionButtonDefaults.elevation(4.dp),
                                modifier = Modifier.size(70.dp)
                            ) { Icon(Icons.Default.Add, "Add Workout", modifier = Modifier.size(32.dp)) }

                            Screen.WeightTracking.name -> FloatingActionButton(
                                onClick = { viewModel.prepareNewEntry(); showAddWeightDialog = true },
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = primaryColor,
                                elevation = FloatingActionButtonDefaults.elevation(4.dp),
                                modifier = Modifier.size(70.dp)
                            ) { Icon(Icons.Default.MonitorWeight, "Add Weight") }

                            Screen.Notes.name -> FloatingActionButton(
                                onClick = { showAddNoteDialog = true },
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = primaryColor,
                                elevation = FloatingActionButtonDefaults.elevation(4.dp),
                                modifier = Modifier.size(70.dp)
                            ) { Icon(Icons.Default.Add, "Add Note", modifier = Modifier.size(32.dp)) }
                        }
                    }
                }
            },
            floatingActionButtonPosition = FabPosition.Center

        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Summary.name,
                modifier = Modifier.padding(innerPadding),
                enterTransition = { fadeIn(animationSpec = tween(300)) },
                exitTransition  = { fadeOut(animationSpec = tween(300)) }
            ) {
                composable(Screen.Summary.name) {
                    SummaryPage(
                        workouts = workouts,
                        bodyWeights = bodyWeights,
                        stravaViewModel = stravaViewModel,
                        primaryColor = primaryColor,
                        onWorkoutEdit   = { editingWorkout = it },
                        onWorkoutDelete = { workoutToDelete = it },
                        onWorkoutCopy   = { copyingWorkout = it },
                        onNavigateToWeightTracking = { navigate(Screen.WeightTracking.name) },
                        listState = summaryListState
                    )
                }
                composable(Screen.Workouts.name) {
                    WorkoutListContent(
                        workouts = workouts,
                        primaryColor = primaryColor,
                        onDelete  = { workoutToDelete = it },
                        onEdit    = { editingWorkout = it },
                        onCopy    = { copyingWorkout = it },
                        listState = workoutsListState
                    )
                }
                composable(Screen.StravaCalendar.name) {
                    StravaCalendarPage(stravaViewModel, primaryColor)
                }
                composable(Screen.WeightTracking.name) {
                    WeightTrackingPage(
                        bodyWeights = bodyWeights,
                        primaryColor = primaryColor,
                        onWeightClick  = { editingWeight = it },
                        onWeightDelete = { weightToDelete = it },
                        listState = weightListState
                    )
                }
                composable(Screen.WorkoutStats.name) {
                    WorkoutStatsPage(workouts, primaryColor)
                }
                composable(Screen.Notes.name) {
                    NotesPage(
                        notes = notesList,
                        primaryColor = primaryColor,
                        onNoteClick  = { editingNote = it },
                        onNoteDelete = { noteToDelete = it },
                        listState = notesListState
                    )
                }
                composable(Screen.Settings.name) {
                    SettingsPage(
                        primaryColor = primaryColor,
                        themeViewModel = themeViewModel,
                        stravaViewModel = stravaViewModel,
                        viewModel = viewModel
                    )
                }
                composable(Screen.WorkoutTimer.name) {
                    val musicVisible = currentSong.title != null &&
                        currentSong.packageName == "com.spotify.music"
                    WorkoutTimerScreen(
                        timerViewModel  = timerViewModel,
                        stravaViewModel = stravaViewModel,
                        bottomPadding   = if (musicVisible) 88.dp else 0.dp
                    )
                }
            }

            // ── Dialogs ───────────────────────────────────────────────────────

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
                            exerciseName = exercise, sets = sets, reps = reps, weight = weight,
                            date = dateMillis, isPersonalBest = isPB, weightUnit = weightUnit, notes = notes
                        ))
                        editingWorkout = null
                    },
                    onDelete = { workoutToDelete = workout; editingWorkout = null },
                    onCopy   = { copyingWorkout  = workout; editingWorkout = null }
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
                    onDismiss  = { editingWeight = null },
                    onConfirm  = { weight, dateMillis, notes ->
                        viewModel.updateBodyWeight(bodyWeight.copy(weight = weight, date = dateMillis, notes = notes))
                        editingWeight = null
                    }
                )
            }

            editingNote?.let { note ->
                NoteDialog(
                    note      = note,
                    onDismiss = { editingNote = null },
                    onConfirm = { title, content, dateMillis ->
                        viewModel.updateNote(note.copy(title = title, content = content, date = dateMillis))
                        editingNote = null
                    },
                    onDelete = { noteToDelete = note; editingNote = null }
                )
            }

            workoutToDelete?.let { workout ->
                ConfirmDeleteDialog(
                    title   = "Delete Workout",
                    message = "Are you sure you want to delete this ${workout.exerciseName} workout?",
                    onConfirm = { viewModel.deleteWorkout(workout); workoutToDelete = null },
                    onDismiss = { workoutToDelete = null }
                )
            }

            weightToDelete?.let { bodyWeight ->
                ConfirmDeleteDialog(
                    title   = "Delete Weight Entry",
                    message = "Are you sure you want to delete this weight entry?",
                    onConfirm = { viewModel.deleteBodyWeight(bodyWeight); weightToDelete = null },
                    onDismiss = { weightToDelete = null }
                )
            }

            noteToDelete?.let { note ->
                ConfirmDeleteDialog(
                    title   = "Delete Note",
                    message = "Are you sure you want to delete this note?",
                    onConfirm = { viewModel.deleteNote(note); noteToDelete = null },
                    onDismiss = { noteToDelete = null }
                )
            }
        }
    }
}

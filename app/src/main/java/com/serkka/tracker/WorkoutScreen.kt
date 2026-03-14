@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.serkka.tracker

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.serkka.tracker.ui.theme.DarkSurfaceColor
import kotlinx.coroutines.launch
import kotlin.math.sin


@Composable
fun WavyProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    isPlaying: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "offset"
    )

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 1000, easing = LinearOutSlowInEasing),
        label = "progress"
    )

    Box(modifier = modifier.height(12.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerY = size.height / 2
            drawLine(
                color = trackColor,
                start = androidx.compose.ui.geometry.Offset(0f, centerY),
                end = androidx.compose.ui.geometry.Offset(size.width, centerY),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width * animatedProgress.coerceIn(0f, 1f)
            if (width <= 0f) return@Canvas

            val centerY = size.height / 2
            val amplitude = if (isPlaying) 1.dp.toPx() else 0f
            val wavelength = 100.dp.toPx()

            val path = Path().apply {
                val startY = if (isPlaying) centerY + amplitude * sin(waveOffset) else centerY
                moveTo(0f, startY)
                if (isPlaying && amplitude > 0f) {
                    var x = 0f
                    while (x < width) {
                        val y = centerY + amplitude * sin(x * (2 * Math.PI.toFloat() / wavelength) + waveOffset)
                        lineTo(x, y)
                        x += 2f
                    }
                } else {
                    lineTo(width, centerY)
                }
            }

            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
private fun ElasticColumnWrapper(
    listState: LazyListState,
    content: @Composable () -> Unit
) {
    // Column scaling removed as it was reported as clunky
    Box(modifier = Modifier.fillMaxSize()) {
        content()
    }
}


@Composable
fun WorkoutScreen(
    viewModel: WorkoutViewModel,
    stravaViewModel: StravaViewModel = viewModel(),
    themeViewModel: ThemeViewModel = viewModel(),
    timerViewModel: WorkoutTimerViewModel = viewModel()

) {
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        stravaViewModel.checkAndFetchActivities()
    }
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route ?: Screen.Summary.name

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

    fun navigate(route: String) = navController.navigate(route) {
        popUpTo(Screen.Summary.name) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    val navBarColors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        indicatorColor = Color.Transparent,
    )

    val drawerItemColors: @Composable () -> NavigationDrawerItemColors = {
        NavigationDrawerItemDefaults.colors(
            selectedContainerColor = primaryColor.copy(alpha = 0.1f),
            unselectedContainerColor = Color.Transparent,
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
                    icon     = { Icon(Icons.Default.Create, null) },
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
                        AnimatedVisibility(
                            visible = timerIsRunning && currentRoute != Screen.WorkoutTimer.name,
                            enter   = fadeIn() + slideInHorizontally { it },
                            exit    = fadeOut() + slideOutHorizontally { it }
                        ) {
                            val infinitePulse = rememberInfiniteTransition(label = "pulse")
                            val pulseScale by infinitePulse.animateFloat(
                                initialValue = 1f,
                                targetValue = 1.03f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1200, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "scale"
                            )

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
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    labelColor = MaterialTheme.colorScheme.surface,
                                    leadingIconContentColor = MaterialTheme.colorScheme.surface
                                ),
                                modifier = Modifier.padding(end = 4.dp).graphicsLayer(scaleX = pulseScale, scaleY = pulseScale)
                            )
                        }
                        IconButton(onClick = { navigate(Screen.Notes.name) }) {
                            Icon(Icons.Default.Create, contentDescription = "Notes", tint = MaterialTheme.colorScheme.onSurface)
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
            bottomBar = {},
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
            val swipeScreens = listOf(
                Screen.WorkoutTimer.name,
                Screen.Workouts.name,
                Screen.Summary.name,
                Screen.WeightTracking.name,
                Screen.StravaCalendar.name
            )

            NavHost(
                navController = navController,
                startDestination = Screen.Summary.name,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding())
                    .pointerInput(currentRoute) {
                        var dragTotal = 0f
                        detectHorizontalDragGestures(
                            onDragStart  = { dragTotal = 0f },
                            onDragEnd    = {
                                val idx = swipeScreens.indexOf(currentRoute)
                                if (idx >= 0) {
                                    val target = when {
                                        dragTotal < -80f && idx < swipeScreens.lastIndex ->
                                            swipeScreens[idx + 1]
                                        dragTotal >  80f && idx > 0 ->
                                            swipeScreens[idx - 1]
                                        else -> null
                                    }
                                    target?.let {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        navigate(it)
                                    }
                                }
                            },
                            onHorizontalDrag = { _, delta -> dragTotal += delta }
                        )
                    },
                enterTransition = { fadeIn(animationSpec = tween(300)) },
                exitTransition  = { fadeOut(animationSpec = tween(300)) }
            ) {
                composable(Screen.Summary.name) {
                    ElasticColumnWrapper(summaryListState) {
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
                }
                composable(Screen.Workouts.name) {
                    ElasticColumnWrapper(workoutsListState) {
                        WorkoutListContent(
                            workouts = workouts,
                            primaryColor = primaryColor,
                            onDelete  = { workoutToDelete = it },
                            onEdit    = { editingWorkout = it },
                            onCopy    = { copyingWorkout = it },
                            listState = workoutsListState
                        )
                    }
                }
                composable(Screen.StravaCalendar.name) {
                    StravaCalendarPage(stravaViewModel, primaryColor)
                }
                composable(Screen.WeightTracking.name) {
                    ElasticColumnWrapper(weightListState) {
                        WeightTrackingPage(
                            bodyWeights = bodyWeights,
                            primaryColor = primaryColor,
                            onWeightClick  = { editingWeight = it },
                            onWeightDelete = { weightToDelete = it },
                            listState = weightListState
                        )
                    }
                }
                composable(Screen.WorkoutStats.name) {
                    WorkoutStatsPage(workouts, primaryColor)
                }
                composable(Screen.Notes.name) {
                    ElasticColumnWrapper(notesListState) {
                        NotesPage(
                            notes = notesList,
                            primaryColor = primaryColor,
                            onNoteClick  = { editingNote = it },
                            onNoteDelete = { noteToDelete = it },
                            listState = notesListState
                        )
                    }
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

            // ── Music widget + FAB ────────────────────────────────────────────
            val fabScreens = setOf(Screen.Workouts.name, Screen.WeightTracking.name, Screen.Notes.name)
            val hasMusicWidget = currentSong.title != null && currentSong.packageName == "com.spotify.music"

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 80.dp)
            ) {
                if (hasMusicWidget) {
                    val musicInteractionSource = remember { MutableInteractionSource() }
                    val musicPressed by musicInteractionSource.collectIsPressedAsState()
                    val musicScale by animateFloatAsState(
                        targetValue = if (musicPressed) 0.985f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = if (musicPressed) Spring.StiffnessLow else Spring.StiffnessMedium
                        ),
                        label = "scale"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer(scaleX = musicScale, scaleY = musicScale)
                            .padding(top = if (isFabVisible && currentRoute in fabScreens) 80.dp else 0.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(MaterialTheme.shapes.large)
                                .background(Color.Black.copy(alpha = 0.95f))
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    currentSong.albumArt?.let { bitmap ->
                                        androidx.compose.foundation.Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = "Album art",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                        Spacer(Modifier.width(4.dp))
                                    }

                                    Column(
                                        modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
                                            .clickable(
                                                interactionSource = musicInteractionSource,
                                                indication = null
                                            ) { MediaRepository.getInstance().openApp() }
                                    ) {
                                        Text(
                                            text = currentSong.title ?: "",
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = currentSong.artist ?: "",
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Box(
                                        modifier = Modifier.size(52.dp).clip(CircleShape)
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
                                            contentDescription = "Next",
                                            tint = primaryColor,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier.size(52.dp).clip(CircleShape)
                                            .combinedClickable(onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                MediaRepository.getInstance().togglePlayPause()
                                            }),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (currentSong.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = "Play/Pause",
                                            tint = if (currentSong.isPlaying) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                                WavyProgressIndicator(
                                    progress = {
                                        val position = currentSong.position?.toFloat() ?: 0f
                                        val duration = currentSong.duration?.toFloat() ?: 1f
                                        if (duration > 0) position / duration else 0f
                                    }(),
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .padding(bottom = 10.dp),
                                    color = primaryColor,
                                    trackColor = DarkSurfaceColor,
                                    isPlaying = currentSong.isPlaying
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = isFabVisible && currentRoute in fabScreens,
                    enter = fadeIn() + scaleIn(),
                    exit  = fadeOut() + scaleOut(),
                    modifier = Modifier.align(if (hasMusicWidget) Alignment.TopEnd else Alignment.BottomEnd)
                        .padding(bottom = if (hasMusicWidget) 0.dp else 25.dp)
                ) {
                    val fabInteractionSource = remember { MutableInteractionSource() }
                    val fabPressed by fabInteractionSource.collectIsPressedAsState()
                    val fabScale by animateFloatAsState(
                        targetValue = if (fabPressed) 0.96f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = if (fabPressed) Spring.StiffnessLow else Spring.StiffnessMedium
                        ),
                        label = "scale"
                    )

                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .graphicsLayer(scaleX = fabScale, scaleY = fabScale)
                            .clip(MaterialTheme.shapes.large)
                            .background(Color.Black.copy(alpha = 0.95f))
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize().combinedClickable(
                                    interactionSource = fabInteractionSource,
                                    indication = ripple(),
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        when (currentRoute) {
                                            Screen.Workouts.name -> showAddWorkoutDialog = true
                                            Screen.WeightTracking.name -> { viewModel.prepareNewEntry(); showAddWeightDialog = true }
                                            Screen.Notes.name -> showAddNoteDialog = true
                                        }
                                    }
                                ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (currentRoute == Screen.WeightTracking.name)
                                        Icons.Default.MonitorWeight else Icons.Default.Add,
                                    contentDescription = "Add",
                                    tint = primaryColor,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                    }
                }
            }

            val bgColor = MaterialTheme.colorScheme.background
            NavigationBar(
                containerColor = Color.Transparent,
                tonalElevation = 0.dp,
                contentColor = primaryColor,
                windowInsets = WindowInsets(0),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                0.25f to bgColor.copy(alpha = 0.5f),
                                0.5f to bgColor.copy(alpha = 0.8f),
                                0.8f to bgColor,
                                1.0f to bgColor
                            )
                        )
                    )
            ) {
                Spacer(modifier = Modifier.width(4.dp))
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Timer, null) },
                    label = { Text("Timer") },
                    selected = currentRoute == Screen.WorkoutTimer.name,
                    onClick  = { navigate(Screen.WorkoutTimer.name) },
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
                    icon = { Icon(Icons.Default.CalendarMonth, null) },
                    label = { Text("Strava") },
                    selected = currentRoute == Screen.StravaCalendar.name,
                    onClick  = { navigate(Screen.StravaCalendar.name) },
                    colors = navBarColors
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            }
        }
    }
}

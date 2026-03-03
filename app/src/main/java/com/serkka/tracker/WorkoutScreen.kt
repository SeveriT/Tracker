package com.serkka.tracker

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.serkka.tracker.ui.theme.DarkBackground
import com.serkka.tracker.ui.theme.PersonalBestGold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.*

enum class Screen {
    Workouts, StravaCalendar, WeightStats
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
    var currentScreen by remember { mutableStateOf(Screen.Workouts) }
    
    val workouts by viewModel.allWorkouts.collectAsState()
    val workoutHistory = remember(workouts) {
        // Keeps the latest workout for each unique exercise name
        workouts.distinctBy { it.exerciseName }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingWorkout by remember { mutableStateOf<Workout?>(null) }
    var workoutToDelete by remember { mutableStateOf<Workout?>(null) }
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
                drawerContainerColor = DarkBackground,
                drawerContentColor = Color.White
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                NavigationDrawerItem(
                    label = { Text("Workouts", modifier = Modifier.padding(start = 8.dp)) },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                    selected = currentScreen == Screen.Workouts,
                    onClick = {
                        currentScreen = Screen.Workouts
                        coroutineScope.launch { drawerState.close() }
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = primaryColor.copy(alpha = 0.2f),
                        unselectedContainerColor = Color.Transparent,
                        selectedIconColor = primaryColor,
                        unselectedIconColor = Color.Gray,
                        selectedTextColor = primaryColor,
                        unselectedTextColor = Color.Gray
                    ),
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding).padding(vertical = 8.dp)
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
                        selectedContainerColor = primaryColor.copy(alpha = 0.2f),
                        unselectedContainerColor = Color.Transparent,
                        selectedIconColor = primaryColor,
                        unselectedIconColor = Color.Gray,
                        selectedTextColor = primaryColor,
                        unselectedTextColor = Color.Gray
                    ),
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding).padding(vertical = 8.dp)
                )
                NavigationDrawerItem(
                    label = { Text("Weight Stats", modifier = Modifier.padding(start = 8.dp)) },
                    icon = { Icon(Icons.Default.FitnessCenter, null) },
                    selected = currentScreen == Screen.WeightStats,
                    onClick = {
                        currentScreen = Screen.WeightStats
                        coroutineScope.launch { drawerState.close() }
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = primaryColor.copy(alpha = 0.2f),
                        unselectedContainerColor = Color.Transparent,
                        selectedIconColor = primaryColor,
                        unselectedIconColor = Color.Gray,
                        selectedTextColor = primaryColor,
                        unselectedTextColor = Color.Gray
                    ),
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding).padding(vertical = 8.dp)
                )
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.Gray.copy(alpha = 0.3f))
                
                Text(
                    "Theme Color",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp)
                )
                
                val colors = listOf(
                    Color(0xFFEE6517), // Original Orange
                    Color(0xFFE0882F),
                    Color(0xFFE0DD2F),
                    Color(0xFFB1E02F),
                    Color(0xFF5EE02F),
                    Color(0xFF2FE093),
                    Color(0xFF2FE0CB),
                    Color(0xFF2FA8E0),
                    Color(0xFF2F70E0),
                    Color(0xFF552FE0),
                    Color(0xFF8D2FE0),
                    Color(0xFFC02FE0),
                    Color(0xFFE02FBA),
                    Color(0xFFCB3179),
                    Color(0xFFCB3131),
                    Color(0xFFFFFFFF),
                    Color(0xFFADADAD),
                    Color(0xFF7A7A7A)
                )
                
                // Displaying colors in a grid-like manner (6 per row)
                val itemsPerRow = 5
                val rows = colors.chunked(itemsPerRow)
                
                Column(modifier = Modifier.padding(horizontal = 28.dp)) {
                    rows.forEach { rowColors ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowColors.forEach { color ->
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (primaryColor.toArgb() == color.toArgb()) 2.dp else 0.dp,
                                            color = Color.White,
                                            shape = CircleShape
                                        )
                                        .clickable { themeViewModel.updatePrimaryColor(color) }
                                )
                            }
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { 
                        Text(when(currentScreen) {
                            Screen.Workouts -> "Workouts"
                            Screen.StravaCalendar -> "Strava Training"
                            Screen.WeightStats -> "Weight Stats"
                        })
                    },
                    navigationIcon = {
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    },
                    actions = {
                        IconButton(onClick = performDriveBackup) {
                            Icon(Icons.Default.CloudUpload, "Drive Backup", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(onClick = { backupLauncher.launch("workout_backup.db") }) {
                            Icon(Icons.Default.Save, "Local Backup", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(onClick = { restoreLauncher.launch(arrayOf("*/*")) }) {
                            Icon(Icons.Default.SettingsBackupRestore, "Restore", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(onClick = {
                            googleSignInClient.signOut().addOnCompleteListener {
                                Toast.makeText(context, "Google signed out", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.Logout, "Google Logout", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                )
            },
            floatingActionButton = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if (currentScreen == Screen.Workouts) 28.dp else 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentSong.title != null) {
                        Surface(
                            color = DarkBackground,
                            shape =  MaterialTheme.shapes.large,
                            tonalElevation = 1.dp,
                            modifier = Modifier
                                .height(56.dp)
                                .weight(1f) // Fill available space
                                .padding(end = if (currentScreen == Screen.Workouts) 16.dp else 0.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 4.dp), // Reduced from 12.dp
                                horizontalArrangement = Arrangement.spacedBy(0.dp)
                            ) {
                                IconButton(onClick = { MediaRepository.getInstance().togglePlayPause() }) {
                                    Icon(
                                        imageVector = if (currentSong.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Play/Pause",
                                        tint = if (currentSong.isPlaying) primaryColor else Color.Gray
                                    )
                                }
                                Column(modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                                    .clickable { MediaRepository.getInstance().openApp() }
                                ) { // Added internal padding to keep text safe
                                    Text(currentSong.title ?: "",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = Color.White)

                                    Text(currentSong.artist ?: "",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.Gray)
                                }
                                
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .combinedClickable(
                                            onClick = { MediaRepository.getInstance().nextTrack() },
                                            onLongClick = { MediaRepository.getInstance().previousTrack() }
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
                    
                    if (currentScreen == Screen.Workouts) {
                        FloatingActionButton(
                            onClick = { showAddDialog = true },
                            containerColor = DarkBackground,
                            contentColor = primaryColor,
                            elevation = FloatingActionButtonDefaults.elevation(1.dp)
                        ) {
                            Text("+", style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                }
            },
            floatingActionButtonPosition = FabPosition.Center
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                when (currentScreen) {
                    Screen.Workouts -> {
                        WorkoutListContent(
                            workouts = workouts,
                            onDelete = { workoutToDelete = it },
                            onEdit = { editingWorkout = it }
                        )
                    }
                    Screen.StravaCalendar -> {
                        StravaCalendarPage(stravaViewModel, primaryColor)
                    }
                    Screen.WeightStats -> {
                        WeightStatsPage(workouts, primaryColor)
                    }
                }
            }

            if (showAddDialog) {
                WorkoutDialog(
                    history = workoutHistory,
                    onDismiss = { showAddDialog = false },
                    onConfirm = { exercise, sets, reps, weight, dateMillis, isPB, weightUnit, notes ->
                        viewModel.addWorkout(exercise, sets, reps, weight, dateMillis, isPB, weightUnit, notes)
                        showAddDialog = false
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
        }
    }
}

@Composable
fun WeightStatsPage(workouts: List<Workout>, primaryColor: Color) {
    val stats = remember(workouts) {
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
    
    val totalWeight = stats.sumOf { it.second }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = primaryColor.copy(alpha = 0.1f))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Total Weight Lifted", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "${String.format(Locale.getDefault(), "%,.0f", totalWeight)} kg",
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
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        items(stats) { (exercise, weight) ->
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        exercise,
                        modifier = Modifier.weight(1f),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "${String.format(Locale.getDefault(), "%,.0f", weight)} kg",
                        fontWeight = FontWeight.Bold,
                        color = primaryColor,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { if (totalWeight > 0) (weight / totalWeight).toFloat() else 0f },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                    color = primaryColor,
                    trackColor = Color.Gray.copy(alpha = 0.2f),
                )
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorkoutListContent(
    workouts: List<Workout>,
    onDelete: (Workout) -> Unit,
    onEdit: (Workout) -> Unit
) {
    val groupedWorkouts = workouts.groupBy { 
        SimpleDateFormat("EEEE d.M.yyyy", Locale.getDefault()).format(Date(it.date))
    }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 85.dp)
    ) {
        groupedWorkouts.forEach { (date, workoutsInDay) ->
            stickyHeader {
                Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background.copy(alpha = 0.9f)) {
                    Text(date, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(8.dp))
                }
            }
            items(workoutsInDay) { workout -> 
                WorkoutCard(
                    workout = workout, 
                    onDelete = { onDelete(workout) },
                    onEdit = { onEdit(workout) }
                ) 
            }
        }
    }
}

@Composable
fun StravaCalendarPage(stravaViewModel: StravaViewModel, primaryColor: Color) {
    val context = LocalContext.current
    val activities by stravaViewModel.activities.collectAsState()
    val isLoading by stravaViewModel.isLoading.collectAsState()
    val error by stravaViewModel.error.collectAsState()
    val profilePicUrl by stravaViewModel.profilePicUrl.collectAsState()
    
    val activityData = remember(activities) { stravaViewModel.getActivityData() }
    val streak = remember(activities) { stravaViewModel.getWeeklyStreak() }
    val totalStreakActivities = remember(activities) { stravaViewModel.getTotalStreakActivities() }

    LaunchedEffect(Unit) {
        stravaViewModel.checkAndFetchActivities()
    }

    LaunchedEffect(error) {
        error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
        }
    }

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
                        val authUri = Uri.parse("https://www.strava.com/oauth/mobile/authorize")
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
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor, contentColor = Color.White)
                ) {
                    Text("Login with Strava")
                }
            }
        } else {
            item {
                // Header Section with Stats
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Stats on the left
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(horizontalAlignment = Alignment.Start) {
                            Text("Streak", color = Color.Gray, fontSize = 10.sp)
                            Text("$streak Weeks" , fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                        }
                        Column(horizontalAlignment = Alignment.Start) {
                            Text("Total activities", color = Color.Gray, fontSize = 10.sp)
                            Text("$totalStreakActivities", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                        }
                    }

                    // Actions on the right
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { stravaViewModel.checkAndFetchActivities() }) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = primaryColor, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, "Refresh", tint = Color.Gray)
                            }
                        }
                        if (profilePicUrl.isNotEmpty()) {
                            AsyncImage(
                                model = profilePicUrl,
                                contentDescription = "Profile Picture",
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .border(1.dp, Color.Gray, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        IconButton(onClick = { stravaViewModel.logout() }) {
                            Icon(Icons.AutoMirrored.Filled.Logout, "Logout", tint = Color.Gray)
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
            
            // Padding at the bottom to ensure the last month isn't obscured by FAB/Song bar
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
fun StravaCalendar(month: YearMonth, activityData: Map<String, List<String>>, primaryColor: Color) {
    val daysInMonth = month.lengthOfMonth()
    
    // Monday = 0, Sunday = 6
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
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Day Headers
        Row(modifier = Modifier.fillMaxWidth().padding(end = 40.dp)) { // Reduced padding for streak column
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        Box(modifier = Modifier.fillMaxWidth()) {
            // Calendar Grid
            Column(modifier = Modifier.fillMaxWidth().padding(end = 40.dp)) {
                var currentDayIndex = 0
                val totalSlots = firstDayOfMonth + daysInMonth
                val rows = (totalSlots + 6) / 7

                for (row in 0 until rows) {
                    Row(modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        for (col in 0 until 7) {
                            val slotIndex = row * 7 + col
                            if (slotIndex < firstDayOfMonth || currentDayIndex >= daysInMonth) {
                                // Previous/Next month days
                                Box(modifier = Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                                    val dayNum = if (slotIndex < firstDayOfMonth) {
                                        val prevMonth = month.minusMonths(1)
                                        prevMonth.lengthOfMonth() - (firstDayOfMonth - slotIndex - 1)
                                    } else {
                                        slotIndex - (firstDayOfMonth + daysInMonth) + 1
                                    }
                                    Text(text = dayNum.toString(), color = Color.DarkGray, fontSize = 14.sp)
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
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(Color.White, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = getIconForActivity(activitiesOnDay.first()),
                                                contentDescription = null,
                                                tint = Color.Black,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    } else if (isToday) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .border(2.dp, Color.White, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(text = day.toString(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        }
                                    } else {
                                        Text(text = day.toString(), color = Color.Gray, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Streak Column on the right
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
                            modifier = Modifier.height(56.dp).width(36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val weekStartDay = if (rowIndex == 0) 1 else (rowIndex * 7 - firstDayOfMonth + 1)
                            val weekEndDay = minOf(daysInMonth, (rowIndex + 1) * 7 - firstDayOfMonth)
                            
                            val isCurrentWeek = isActualCurrentMonth && today.dayOfMonth in weekStartDay..weekEndDay
                            
                            if (isCurrentWeek) {
                                // Only show bolt if there were activities in the PREVIOUS week
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
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(primaryColor.copy(alpha = 0.2f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Bolt,
                                            contentDescription = null, 
                                            tint = primaryColor, 
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                } else {
                                    // Fallback to regular activity check for the current week if no bolt
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
                                            modifier = Modifier.size(24.dp).background(primaryColor, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            } else {
                                // Check for activity in this week
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
                                        modifier = Modifier.size(24.dp).background(primaryColor, CircleShape),
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
fun WorkoutCard(workout: Workout, onDelete: () -> Unit, onEdit: () -> Unit) {
    val backgroundColor = if (workout.isPersonalBest) PersonalBestGold else MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable { onEdit() },
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    workout.exerciseName,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val details = buildString {
                    if (workout.sets > 0) append("${workout.sets} sets ")
                    if (workout.reps > 0) {
                        if (workout.sets > 0) append("x ")
                        append("${workout.reps} reps ")
                    }
                    if (workout.weight > 0) append("@ ${workout.weight}${workout.weightUnit}")
                }
                Text(details, color = Color.Black, style = MaterialTheme.typography.bodyLarge)
                if (workout.notes.isNotBlank()) {
                    Text(
                        workout.notes,
                        color = Color.Black,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Black)
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
    onConfirm: (String, Int, Int, Float, Long, Boolean, String, String) -> Unit
) {
    var exercise by remember { mutableStateOf(workout?.exerciseName ?: "") }
    var expanded by remember { mutableStateOf(false) }
    
    var sets by remember { mutableStateOf(workout?.sets?.toString() ?: "") }
    var reps by remember { mutableStateOf(workout?.reps?.toString() ?: "") }
    var weight by remember { mutableStateOf(workout?.weight?.toString() ?: "") }
    var weightUnit by remember { mutableStateOf(workout?.weightUnit ?: "kg") }
    var notes by remember { mutableStateOf(workout?.notes ?: "") }
    var isPB by remember { mutableStateOf(workout?.isPersonalBest ?: false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = workout?.date ?: System.currentTimeMillis())
    var showDatePicker by remember { mutableStateOf(false) }

    val suggestions = remember(exercise, history) {
        if (exercise.isEmpty()) {
            history.map { it.exerciseName }.distinct().take(10)
        } else {
            history.filter { it.exerciseName.contains(exercise, ignoreCase = true) }
                .map { it.exerciseName }
                .distinct()
                .filter { it.lowercase() != exercise.lowercase() }
                .take(10)
        }
    }

    if (showDatePicker) {
        DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = { TextButton(onClick = { showDatePicker = false }) { Text("OK") } }) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (workout == null) "Add Workout" else "Edit Workout") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Quick Selection Chips
                if (exercise.isEmpty() && history.isNotEmpty()) {
                    Text("Recent exercises:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        history.take(8).forEach { recent ->
                            AssistChip(
                                onClick = {
                                    exercise = recent.exerciseName
                                    if (sets.isEmpty() || sets == "0") sets = recent.sets.toString()
                                    if (reps.isEmpty() || reps == "0") reps = recent.reps.toString()
                                    if (weight.isEmpty() || weight == "0") weight = recent.weight.toString()
                                    weightUnit = recent.weightUnit
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
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth(),
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
                                    // Auto-fill from history
                                    history.find { it.exerciseName == suggestion }?.let { recent ->
                                        if (sets.isEmpty() || sets == "0") sets = recent.sets.toString()
                                        if (reps.isEmpty() || reps == "0") reps = recent.reps.toString()
                                        if (weight.isEmpty() || weight == "0") weight = recent.weight.toString()
                                        weightUnit = recent.weightUnit
                                    }
                                    expanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = sets, onValueChange = { sets = it }, label = { Text("Sets") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = reps, onValueChange = { reps = it }, label = { Text("Reps") }, modifier = Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = weight, 
                        onValueChange = { weight = it }, 
                        label = { Text("Weight") },
                        modifier = Modifier.weight(1f)
                    )
                    
                    // Simple Unit Selector
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = weightUnit == "kg", onClick = { weightUnit = "kg" })
                        Text("kg")
                        RadioButton(selected = weightUnit == "cm", onClick = { weightUnit = "cm" })
                        Text("cm")
                    }
                }
                
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )

                OutlinedTextField(
                    value = SimpleDateFormat("EEEE d.M.yy", Locale.getDefault()).format(Date(datePickerState.selectedDateMillis ?: System.currentTimeMillis())),
                    onValueChange = {},
                    label = { Text("Date") },
                    readOnly = true,
                    trailingIcon = { IconButton(onClick = { showDatePicker = true }) { Icon(Icons.Default.DateRange, null) } }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isPB, onCheckedChange = { isPB = it })
                    Text("Personal Best")
                }
            }
        },
        confirmButton = {
            Button(onClick = { 
                onConfirm(exercise, sets.toIntOrNull() ?: 0, reps.toIntOrNull() ?: 0, weight.toFloatOrNull() ?: 0f, datePickerState.selectedDateMillis ?: System.currentTimeMillis(), isPB, weightUnit, notes)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

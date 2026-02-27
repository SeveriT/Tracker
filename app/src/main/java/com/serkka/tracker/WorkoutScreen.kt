package com.serkka.tracker

import android.app.Activity
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
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
import com.serkka.tracker.ui.theme.OrangePrimary
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
    Workouts, StravaCalendar
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(viewModel: WorkoutViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val backupManager = remember { BackupManager(context) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var currentScreen by remember { mutableStateOf(Screen.Workouts) }
    
    val workouts by viewModel.allWorkouts.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    val currentSong by MediaRepository.getInstance().currentSong.collectAsState()

    // Strava ViewModel
    val stravaViewModel: StravaViewModel = viewModel()

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
                    context.startActivity(android.content.Intent.makeRestartActivityTask(intent?.component))
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
                    label = { Text("Workouts") },
                    icon = { Icon(Icons.Default.List, null) },
                    selected = currentScreen == Screen.Workouts,
                    onClick = {
                        currentScreen = Screen.Workouts
                        coroutineScope.launch { drawerState.close() }
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = OrangePrimary.copy(alpha = 0.2f),
                        unselectedContainerColor = Color.Transparent,
                        selectedIconColor = OrangePrimary,
                        unselectedIconColor = Color.Gray,
                        selectedTextColor = OrangePrimary,
                        unselectedTextColor = Color.Gray
                    ),
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    label = { Text("Strava Calendar") },
                    icon = { Icon(Icons.Default.DateRange, null) },
                    selected = currentScreen == Screen.StravaCalendar,
                    onClick = {
                        currentScreen = Screen.StravaCalendar
                        coroutineScope.launch { drawerState.close() }
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = OrangePrimary.copy(alpha = 0.2f),
                        unselectedContainerColor = Color.Transparent,
                        selectedIconColor = OrangePrimary,
                        unselectedIconColor = Color.Gray,
                        selectedTextColor = OrangePrimary,
                        unselectedTextColor = Color.Gray
                    ),
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (currentScreen == Screen.Workouts) "Tracker" else "Strava Training") },
                    navigationIcon = {
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    },
                    actions = {
                        IconButton(onClick = performDriveBackup) {
                            Icon(Icons.Default.CloudUpload, "Drive Backup", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        TextButton(onClick = { backupLauncher.launch("workout_backup.db") }) {
                            Text("Backup", color = MaterialTheme.colorScheme.onSurface)
                        }
                        TextButton(onClick = { restoreLauncher.launch(arrayOf("*/*")) }) {
                            Text("Restore", color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                )
            },
            floatingActionButton = {
                if (currentScreen == Screen.Workouts) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentSong.title != null) {
                            Surface(
                                color = DarkBackground,
                                shape = MaterialTheme.shapes.large,
                                tonalElevation = 4.dp,
                                modifier = Modifier.height(56.dp).widthIn(max = 240.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    IconButton(onClick = { MediaRepository.getInstance().togglePlayPause() }) {
                                        Icon(
                                            imageVector = if (currentSong.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = "Play/Pause",
                                            tint = if (currentSong.isPlaying) OrangePrimary else Color.Gray
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(currentSong.title ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White)
                                        Text(currentSong.artist ?: "", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    }
                                    IconButton(onClick = { MediaRepository.getInstance().nextTrack() }) {
                                        Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        FloatingActionButton(onClick = { showDialog = true }, containerColor = DarkBackground, contentColor = OrangePrimary) {
                            Text("+", style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                }
            },
            floatingActionButtonPosition = FabPosition.Center
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                when (currentScreen) {
                    Screen.Workouts -> {
                        WorkoutListContent(workouts, viewModel)
                    }
                    Screen.StravaCalendar -> {
                        StravaCalendarPage(stravaViewModel)
                    }
                }
            }

            if (showDialog) {
                AddWorkoutDialog(onDismiss = { showDialog = false }, onAdd = { exercise, sets, reps, weight, dateMillis, isPB ->
                    viewModel.addWorkout(exercise, sets, reps, weight, dateMillis, isPB)
                    showDialog = false
                })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorkoutListContent(workouts: List<Workout>, viewModel: WorkoutViewModel) {
    val groupedWorkouts = workouts.groupBy { 
        SimpleDateFormat("d.M.yy", Locale.getDefault()).format(Date(it.date)) 
    }
    
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        groupedWorkouts.forEach { (date, workoutsInDay) ->
            stickyHeader {
                Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background.copy(alpha = 0.9f)) {
                    Text(date, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(8.dp))
                }
            }
            items(workoutsInDay) { workout -> 
                WorkoutCard(workout = workout, onDelete = { viewModel.deleteWorkout(workout) }) 
            }
        }
    }
}

@Composable
fun StravaCalendarPage(stravaViewModel: StravaViewModel) {
    val context = LocalContext.current
    val activities by stravaViewModel.activities.collectAsState()
    val isLoading by stravaViewModel.isLoading.collectAsState()
    val error by stravaViewModel.error.collectAsState()
    val savedToken by stravaViewModel.savedToken.collectAsState()
    var token by remember(savedToken) { mutableStateOf(savedToken) }
    
    val activityData = remember(activities) { stravaViewModel.getActivityData() }
    val streak = remember(activities) { stravaViewModel.getWeeklyStreak() }
    val totalStreakActivities = remember(activities) { stravaViewModel.getTotalStreakActivities() }

    LaunchedEffect(error) {
        error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (activities.isEmpty() && !isLoading) {
            Text("Link Strava to see your progress", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = token,
                onValueChange = { token = it; stravaViewModel.clearError() },
                label = { Text("Enter Strava Access Token") },
                modifier = Modifier.fillMaxWidth(),
                isError = error != null
            )
            if (error != null) {
                Text(
                    text = error ?: "Unknown error",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Button(
                onClick = { stravaViewModel.fetchActivities(token) },
                enabled = token.isNotBlank() && !isLoading,
                modifier = Modifier.padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(contentColor = Color.White)
            ) {
                Text("Fetch Activities")
            }
        } else {
            val currentMonth = YearMonth.now()
            val monthName = currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
            val year = currentMonth.year

            // Header Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$monthName $year",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Row {
                    IconButton(onClick = { stravaViewModel.logout() }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, "Logout", tint = Color.Gray)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Stats Section
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Column {
                    Text("Your Streak", color = Color.Gray, fontSize = 12.sp)
                    Text("$streak Weeks", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                }
                Column {
                    Text("Streak Activities", color = Color.Gray, fontSize = 12.sp)
                    Text("$totalStreakActivities", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            StravaCalendar(activityData, streak)
            
            Spacer(modifier = Modifier.weight(1f))
            
            Button(
                onClick = { stravaViewModel.fetchActivities(token) }, 
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.DarkGray,
                    contentColor = Color.White
                )
            ) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                else Text("Refresh Data")
            }
        }
    }
}

@Composable
fun StravaCalendar(activityData: Map<String, List<String>>, streak: Int) {
    val currentMonth = YearMonth.now()
    val daysInMonth = currentMonth.lengthOfMonth()
    
    // Monday = 0, Sunday = 6
    val firstDayOfMonth = (currentMonth.atDay(1).dayOfWeek.value - 1)
    
    val year = currentMonth.year
    val monthValue = currentMonth.monthValue
    val today = LocalDate.now()

    Column {
        // Day Headers
        Row(modifier = Modifier.fillMaxWidth().padding(end = 48.dp)) { // Padding for streak column
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
            Column(modifier = Modifier.fillMaxWidth().padding(end = 48.dp)) {
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
                                        val prevMonth = currentMonth.minusMonths(1)
                                        prevMonth.lengthOfMonth() - (firstDayOfMonth - slotIndex - 1)
                                    } else {
                                        slotIndex - (firstDayOfMonth + daysInMonth) + 1
                                    }
                                    Text(text = dayNum.toString(), color = Color.DarkGray, fontSize = 14.sp)
                                }
                            } else {
                                currentDayIndex++
                                val day = currentDayIndex
                                val dateString = String.format("%04d-%02d-%02d", year, monthValue, day)
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
                    .height((rows * 56).dp)
                    .background(Color(0xFF2A1500), RoundedCornerShape(18.dp)) // Dark orange background
                    .padding(vertical = 0.dp),
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
                            if (rowIndex < rows - 1) {
                                // Check for activity in this week
                                val weekStartDay = if (rowIndex == 0) 1 else (rowIndex * 7 - firstDayOfMonth + 1)
                                val weekEndDay = minOf(daysInMonth, (rowIndex + 1) * 7 - firstDayOfMonth)
                                var hasActivity = false
                                for (d in weekStartDay..weekEndDay) {
                                    val dateStr = String.format("%04d-%02d-%02d", year, monthValue, d)
                                    if (activityData.containsKey(dateStr)) {
                                        hasActivity = true
                                        break
                                    }
                                }

                                if (hasActivity) {
                                    Box(
                                        modifier = Modifier.size(24.dp).background(Color(0xFFE65100), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                    }
                                }
                            } else {
                                // Current week indicator: Lightning Bolt with Streak count on top
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Bolt, 
                                        contentDescription = null, 
                                        tint = Color(0xFFE65100), 
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Text(
                                        text = streak.toString(), 
                                        fontWeight = FontWeight.ExtraBold, 
                                        color = Color.White, 
                                        fontSize = 14.sp,
                                        modifier = Modifier.offset(y = 2.dp)
                                    )
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
fun WorkoutCard(workout: Workout, onDelete: () -> Unit) {
    val backgroundColor = if (workout.isPersonalBest) PersonalBestGold else OrangePrimary
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
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
                    if (workout.reps > 0) append("x ${workout.reps} reps ")
                    if (workout.weight > 0) append("@ ${workout.weight}kg")
                }
                Text(details, color = Color.Black)
            }
            if (workout.isPersonalBest) {
                Text(
                    "PB!",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Black)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWorkoutDialog(onDismiss: () -> Unit, onAdd: (String, Int, Int, Float, Long, Boolean) -> Unit) {
    var exercise by remember { mutableStateOf("") }
    var sets by remember { mutableStateOf("") }
    var reps by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var isPB by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = { TextButton(onClick = { showDatePicker = false }) { Text("OK") } }) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Workout") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = exercise, onValueChange = { exercise = it }, label = { Text("Exercise") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = sets, onValueChange = { sets = it }, label = { Text("Sets") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = reps, onValueChange = { reps = it }, label = { Text("Reps") }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = weight, onValueChange = { weight = it }, label = { Text("Weight (kg)") })
                OutlinedTextField(
                    value = SimpleDateFormat("d.M.yy", Locale.getDefault()).format(Date(datePickerState.selectedDateMillis ?: System.currentTimeMillis())),
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
                onAdd(exercise, sets.toIntOrNull() ?: 0, reps.toIntOrNull() ?: 0, weight.toFloatOrNull() ?: 0f, datePickerState.selectedDateMillis ?: System.currentTimeMillis(), isPB)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

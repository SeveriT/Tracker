package com.serkka.tracker

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorkoutScreen(viewModel: WorkoutViewModel) {
    val workouts by viewModel.allWorkouts.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    // Group workouts by date
    val groupedWorkouts = workouts.groupBy { it.date }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) {
                Text("+")
            }
        }
    ) { padding ->
        LazyColumn(
            contentPadding = padding,
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
        ) {
            groupedWorkouts.forEach { (date, workoutsInDay) ->
                stickyHeader {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Text(
                            text = date,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .padding(vertical = 8.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(8.dp)
                                .fillMaxWidth()
                        )
                    }
                }

                items(workoutsInDay) { workout ->
                    WorkoutCard(
                        workout = workout,
                        onDelete = { viewModel.deleteWorkout(workout) }
                    )
                }
            }
        }

        if (showDialog) {
            AddWorkoutDialog(
                onDismiss = { showDialog = false },
                onAdd = { exercise, sets, reps, weight, date ->
                    viewModel.addWorkout(exercise, sets, reps, weight, date)
                    showDialog = false
                }
            )
        }
    }
}

@Composable
fun WorkoutCard(workout: Workout, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = workout.exerciseName, style = MaterialTheme.typography.titleLarge)
                Text(text = "${workout.sets} sets x ${workout.reps} reps @ ${workout.weight}kg")
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Workout"
                )
            }
        }
    }
}

@Composable
fun AddWorkoutDialog(onDismiss: () -> Unit, onAdd: (String, Int, Int, Float, String) -> Unit) {
    var exercise by remember { mutableStateOf("") }
    var sets by remember { mutableStateOf("") }
    var reps by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }

    // Hardcoded date for simplicity, you'd usually use a DatePicker here
    val date = "2026-02-26"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Workout") },
        text = {
            Column {
                OutlinedTextField(value = exercise, onValueChange = { exercise = it }, label = { Text("Exercise") })
                OutlinedTextField(value = sets, onValueChange = { sets = it }, label = { Text("Sets") })
                OutlinedTextField(value = reps, onValueChange = { reps = it }, label = { Text("Reps") })
                OutlinedTextField(value = weight, onValueChange = { weight = it }, label = { Text("Weight (kg)") })
            }
        },
        confirmButton = {
            Button(onClick = {
                onAdd(
                    exercise,
                    sets.toIntOrNull() ?: 0,
                    reps.toIntOrNull() ?: 0,
                    weight.toFloatOrNull() ?: 0f,
                    date
                )
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

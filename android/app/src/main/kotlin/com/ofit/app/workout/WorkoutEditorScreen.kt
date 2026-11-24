package com.ofit.app.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.ofit.app.data.SavedWorkout
import com.ofit.protocol.ProgramValidation
import com.ofit.protocol.SafetyLimits
import com.ofit.protocol.WorkoutProgram
import com.ofit.protocol.WorkoutStep

/**
 * Write a workout.
 *
 * A workout is a name and an ordered list of steps. Each step says how fast to run, how steep,
 * and for how long. The ceilings are checked here as the user types, so an impossible workout is
 * never saved and never sent to the treadmill.
 */
@Composable
fun WorkoutEditorScreen(
    saved: SavedWorkout?,
    limits: SafetyLimits,
    onSave: (WorkoutProgram) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf(saved?.program?.name ?: "My workout") }
    val steps = remember {
        mutableStateListOf<WorkoutStep>().apply {
            addAll(saved?.program?.steps ?: defaultSteps())
        }
    }

    val program = WorkoutProgram(name.trim(), steps.toList())
    val validation = program.validate(limits)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        HeaderBar(title = if (saved == null) "New workout" else "Edit workout", onBack = onCancel)

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "${steps.size} steps, ${formatClock(program.totalDurationSeconds)} in total",
            style = MaterialTheme.typography.bodySmall,
        )

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(steps) { index, step ->
                StepRow(
                    step = step,
                    index = index,
                    canMoveUp = index > 0,
                    canMoveDown = index < steps.lastIndex,
                    onChange = { steps[index] = it },
                    onRemove = { steps.removeAt(index) },
                    onMoveUp = {
                        if (index > 0) {
                            val moved = steps.removeAt(index)
                            steps.add(index - 1, moved)
                        }
                    },
                    onMoveDown = {
                        if (index < steps.lastIndex) {
                            val moved = steps.removeAt(index)
                            steps.add(index + 1, moved)
                        }
                    },
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = {
                steps.add(WorkoutStep(speedKph = 4.0, inclinePercent = 0.0, durationSeconds = 60))
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text("Add a step")
        }

        Spacer(Modifier.height(8.dp))

        val problems = validation.allProblems
        if (problems.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "This workout cannot run yet:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    for (problem in problems) {
                        val where = problem.stepIndex?.let { "Step ${it + 1}" } ?: "Workout"
                        Text(text = "$where: ${problem.message}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = { onSave(program) },
            enabled = validation is ProgramValidation.Valid,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save workout")
        }
    }
}

/**
 * One editable step.
 */
@Composable
private fun StepRow(
    step: WorkoutStep,
    index: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onChange: (WorkoutStep) -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val units = com.ofit.app.settings.LocalUnits.current
    var speed by remember(units) {
        mutableStateOf("%.1f".format(java.util.Locale.ROOT, units.speedFromKph(step.speedKph)))
    }
    var incline by remember { mutableStateOf(step.inclinePercent.toString()) }
    var duration by remember { mutableStateOf(step.durationSeconds.toString()) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Step ${index + 1}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove step ${index + 1}")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = speed,
                    onValueChange = {
                        speed = it
                        it.toDoubleOrNull()?.let { value -> onChange(step.copy(speedKph = units.speedToKph(value))) }
                    },
                    label = { Text(units.speedUnit) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = incline,
                    onValueChange = {
                        incline = it
                        it.toDoubleOrNull()?.let { value -> onChange(step.copy(inclinePercent = value)) }
                    },
                    label = { Text("incline %") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = duration,
                    onValueChange = {
                        duration = it
                        it.toIntOrNull()?.let { value -> onChange(step.copy(durationSeconds = value)) }
                    },
                    label = { Text("seconds") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** The steps a new workout starts with. */
private fun defaultSteps(): List<WorkoutStep> = listOf(
    WorkoutStep(speedKph = 3.0, inclinePercent = 0.0, durationSeconds = 120),
    WorkoutStep(speedKph = 6.0, inclinePercent = 0.0, durationSeconds = 300),
)

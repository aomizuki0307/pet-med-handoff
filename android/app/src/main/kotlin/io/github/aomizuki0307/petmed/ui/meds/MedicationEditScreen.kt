package io.github.aomizuki0307.petmed.ui.meds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aomizuki0307.petmed.R
import io.github.aomizuki0307.petmed.domain.model.Medication
import io.github.aomizuki0307.petmed.domain.model.ScheduleSlot
import io.github.aomizuki0307.petmed.ui.AppViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationEditScreen(
    viewModel: AppViewModel,
    petId: String,
    medId: String?,
    onSaved: () -> Unit,
    onBack: () -> Unit,
) {
    val existing = medId?.let { id -> viewModel.uiState.value.household?.medications?.find { it.id == id } }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var dosage by remember { mutableStateOf(existing?.dosageText ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    val slots = remember {
        (existing?.slots ?: listOf(ScheduleSlot(UUID.randomUUID().toString(), LocalTime.of(8, 0), "朝")))
            .toMutableStateList()
    }
    val days = remember { mutableStateOf<Set<Int>>(existing?.daysOfWeek ?: (1..7).toSet()) }
    var nameError by remember { mutableStateOf(false) }
    var slotError by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val dayLabels = listOf("月", "火", "水", "木", "金", "土", "日")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (existing == null) R.string.med_edit_title_new else R.string.med_edit_title_edit))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; nameError = false },
                label = { Text(stringResource(R.string.med_name_label)) },
                isError = nameError,
                supportingText = if (nameError) {
                    { Text(stringResource(R.string.med_name_required)) }
                } else {
                    null
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = dosage,
                onValueChange = { dosage = it },
                label = { Text(stringResource(R.string.med_dosage_label)) },
                placeholder = { Text(stringResource(R.string.med_dosage_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )

            Text(stringResource(R.string.med_slots_label), style = MaterialTheme.typography.titleSmall)
            if (slotError) {
                Text(
                    stringResource(R.string.med_slot_required),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                slots.forEach { slot ->
                    InputChip(
                        selected = true,
                        onClick = {},
                        label = { Text("${slot.label} ${slot.time}") },
                        trailingIcon = {
                            IconButton(onClick = { slots.remove(slot) }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_delete))
                            }
                        },
                    )
                }
            }
            AssistChip(
                onClick = { showTimePicker = true; slotError = false },
                label = { Text(stringResource(R.string.med_slot_add)) },
            )

            Text(stringResource(R.string.med_days_label), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                dayLabels.forEachIndexed { index, label ->
                    val day = index + 1
                    FilterChip(
                        selected = day in days.value,
                        onClick = {
                            days.value = if (day in days.value) days.value - day else days.value + day
                        },
                        label = { Text(label) },
                    )
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.med_note_label)) },
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = {
                    if (name.isBlank()) { nameError = true; return@Button }
                    if (slots.isEmpty()) { slotError = true; return@Button }
                    val med = Medication(
                        id = existing?.id ?: "",
                        petId = petId,
                        name = name.trim(),
                        dosageText = dosage.trim(),
                        slots = slots.toList(),
                        daysOfWeek = days.value.toSet(),
                        startDate = existing?.startDate ?: LocalDate.now(),
                        note = note.trim(),
                    )
                    viewModel.saveMedication(med, isNew = existing == null, onDone = onSaved)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.common_save)) }
        }
    }

    if (showTimePicker) {
        val timeState = rememberTimePickerState(initialHour = 8, initialMinute = 0, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val time = LocalTime.of(timeState.hour, timeState.minute)
                    val label = when {
                        time.hour < 11 -> "朝"
                        time.hour < 16 -> "昼"
                        else -> "夜"
                    }
                    slots.add(ScheduleSlot(UUID.randomUUID().toString(), time, label))
                    showTimePicker = false
                }) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
            text = { TimePicker(state = timeState) },
        )
    }
}

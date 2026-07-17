package io.github.aomizuki0307.petmed.ui.pets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.aomizuki0307.petmed.R
import io.github.aomizuki0307.petmed.domain.model.Species
import io.github.aomizuki0307.petmed.ui.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetEditScreen(
    viewModel: AppViewModel,
    petId: String?,
    onSaved: (petId: String) -> Unit,
    onBack: () -> Unit,
) {
    val existing = petId?.let { id -> viewModel.uiState.value.household?.pets?.find { it.id == id } }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var species by remember { mutableStateOf(existing?.species ?: Species.CAT) }
    var birthYear by remember { mutableStateOf(existing?.birthYear?.toString() ?: "") }
    var nameError by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (existing == null) R.string.pet_edit_title_new else R.string.pet_edit_title_edit))
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
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; nameError = false },
                label = { Text(stringResource(R.string.pet_name_label)) },
                isError = nameError,
                supportingText = if (nameError) {
                    { Text(stringResource(R.string.pet_name_required)) }
                } else {
                    null
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = species == Species.CAT,
                    onClick = { species = Species.CAT },
                    label = { Text(stringResource(R.string.pet_species_cat)) },
                )
                FilterChip(
                    selected = species == Species.DOG,
                    onClick = { species = Species.DOG },
                    label = { Text(stringResource(R.string.pet_species_dog)) },
                )
            }
            OutlinedTextField(
                value = birthYear,
                onValueChange = { birthYear = it.filter(Char::isDigit).take(4) },
                label = { Text(stringResource(R.string.pet_birth_year_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    if (name.isBlank()) {
                        nameError = true
                        return@Button
                    }
                    val year = birthYear.toIntOrNull()
                    if (existing == null) {
                        viewModel.addPet(name.trim(), species, year) { newId -> onSaved(newId) }
                    } else {
                        viewModel.updatePet(existing.copy(name = name.trim(), species = species, birthYear = year)) {
                            onSaved(existing.id)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.common_save)) }
        }
    }
}

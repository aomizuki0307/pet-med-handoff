package io.github.aomizuki0307.petmed.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aomizuki0307.petmed.R
import io.github.aomizuki0307.petmed.ui.AppViewModel

private const val PRIVACY_URL = "https://aomizuki0307.github.io/pet-med-lp/privacy.html"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onOpenInvite: () -> Unit,
    onOpenPaywall: () -> Unit,
    onDeleted: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val household = state.household

    var name by remember(household?.myDisplayName) { mutableStateOf(household?.myDisplayName ?: "") }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }

    val isOwner = household?.members?.any { it.uid == household.myUid && it.isOwner } == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
            if (!state.isBackendConfigured) {
                Text(
                    stringResource(R.string.settings_backend_not_configured),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.settings_display_name)) },
                singleLine = true,
                trailingIcon = {
                    TextButton(onClick = { if (name.isNotBlank()) viewModel.updateDisplayName(name.trim()) }) {
                        Text(stringResource(R.string.common_save))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedButton(onClick = onOpenPaywall, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_plan))
            }
            OutlinedButton(onClick = onOpenInvite, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_invite))
            }
            OutlinedButton(
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL)))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.settings_privacy)) }

            HorizontalDivider()

            TextButton(
                onClick = { confirmLeave = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(R.string.settings_delete_leave),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (isOwner) {
                TextButton(
                    onClick = { confirmDeleteAll = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.settings_delete_household),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.settings_delete_confirm_title)) },
            text = { Text(stringResource(R.string.settings_delete_leave_body)) },
            confirmButton = {
                Button(onClick = {
                    confirmLeave = false
                    viewModel.deleteMyAccount(onDeleted)
                }) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text(stringResource(R.string.settings_delete_confirm_title)) },
            text = { Text(stringResource(R.string.settings_delete_household_body)) },
            confirmButton = {
                Button(onClick = {
                    confirmDeleteAll = false
                    viewModel.deleteHousehold(onDeleted)
                }) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

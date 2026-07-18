package io.github.aomizuki0307.petmed.ui.today

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import io.github.aomizuki0307.petmed.R
import io.github.aomizuki0307.petmed.domain.model.DoseRecord
import io.github.aomizuki0307.petmed.ui.AppViewModel
import io.github.aomizuki0307.petmed.ui.TodaySlotUi
import io.github.aomizuki0307.petmed.ui.components.DoseSlotCard
import io.github.aomizuki0307.petmed.ui.components.PermissionStatusCard
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    viewModel: AppViewModel,
    onOpenHistory: () -> Unit,
    onOpenHandoff: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenInvite: () -> Unit,
    onAddPet: () -> Unit,
    onAddMed: (petId: String) -> Unit,
    onEditMed: (petId: String, medId: String) -> Unit,
    onOpenPaywall: (trigger: String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val message by viewModel.message.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    var pendingSlot by remember { mutableStateOf<TodaySlotUi?>(null) }
    var warnAgainst by remember { mutableStateOf<DoseRecord?>(null) }

    // 通知権限（初回のみ薬設定後に文脈付きで要求）
    var notifGranted by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    var exactGranted by remember { mutableStateOf(viewModel.alarmScheduler.canScheduleExact()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notifGranted = granted
        viewModel.analytics.log("notification_permission", mapOf("granted" to granted))
    }

    // 設定アプリから戻ったときに権限状態を再取得し、正確なアラームの許諾変化を実測ログする
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                notifGranted = NotificationManagerCompat.from(context).areNotificationsEnabled()
                val nowExact = viewModel.alarmScheduler.canScheduleExact()
                if (nowExact != exactGranted) {
                    exactGranted = nowExact
                    viewModel.analytics.log("exact_alarm_permission", mapOf("granted" to nowExact))
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(state.household?.medications?.isNotEmpty()) {
        if (state.household?.medications?.isNotEmpty() == true &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !notifGranted
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val recordedText = stringResource(R.string.today_recorded_snackbar)
    LaunchedEffect(message) {
        if (message == "recorded") {
            snackbar.showSnackbar(recordedText)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.today_title)) },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Default.History, contentDescription = stringResource(R.string.history_title))
                    }
                    IconButton(onClick = onOpenInvite) {
                        Icon(Icons.Default.PersonAdd, contentDescription = stringResource(R.string.invite_title))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            val household = state.household
            val pets = household?.pets ?: emptyList()
            FloatingActionButton(onClick = {
                when {
                    pets.isEmpty() -> onAddPet()
                    household != null &&
                        !io.github.aomizuki0307.petmed.domain.FreeTierPolicy.canAddMedication(
                            household.household,
                            household.medications.count { it.active },
                            java.time.Instant.now(),
                        ) -> {
                        // 無料枠(薬2件)超過 → 価格画面へ（docs/01 F13 / paywall_viewed trigger=med_limit）
                        viewModel.logPaywallViewed("med_limit")
                        onOpenPaywall("med_limit")
                    }
                    else -> onAddMed(pets.first().id)
                }
            }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.med_edit_title_new))
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                PermissionStatusCard(
                    notifGranted = notifGranted,
                    exactAlarmGranted = exactGranted,
                    onFixNotification = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notifGranted) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            )
                        }
                    },
                    onFixExactAlarm = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            // 実際の許諾結果はON_RESUMEで実測ログする（ここではログしない）
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        }
                    },
                )
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.handoff_card_title), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.handoff_card_body),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        TextButton(onClick = onOpenHandoff) {
                            Text(stringResource(R.string.handoff_card_cta))
                        }
                    }
                }
            }

            if (!state.isBackendConfigured) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Text(
                            stringResource(R.string.settings_backend_not_configured),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }

            val slotsByPet = state.todaySlots.groupBy { it.slot.petName }
            if (state.household?.medications.isNullOrEmpty()) {
                item { EmptyMeds(onAddPet, onAddMed, viewModel) }
            } else if (state.todaySlots.isEmpty()) {
                item { Text(stringResource(R.string.today_empty)) }
            }
            slotsByPet.forEach { (petName, slots) ->
                item {
                    Text(
                        "🐾 $petName",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(slots, key = { it.slot.slotKey }) { slotUi ->
                    DoseSlotCard(
                        slotUi = slotUi,
                        onRecordGiven = {
                            val existing = viewModel.existingGivenFor(slotUi.slot)
                            if (existing != null) {
                                pendingSlot = slotUi
                                warnAgainst = existing
                            } else {
                                pendingSlot = slotUi
                            }
                        },
                        onUndo = { record -> viewModel.cancelRecord(record) },
                        onEditMed = { onEditMed(slotUi.slot.petId, slotUi.slot.medId) },
                    )
                }
            }
        }
    }

    // 記録シート（投薬済み/見送り）
    pendingSlot?.let { slotUi ->
        if (warnAgainst == null) {
            RecordDoseDialog(
                slotUi = slotUi,
                onGiven = {
                    viewModel.recordDose(slotUi.slot, given = true)
                    pendingSlot = null
                },
                onSkipped = {
                    viewModel.recordDose(slotUi.slot, given = false)
                    pendingSlot = null
                },
                onDismiss = { pendingSlot = null },
            )
        }
    }

    // 二重投薬警告
    val warn = warnAgainst
    val warnSlot = pendingSlot
    if (warn != null && warnSlot != null) {
        val timeFmt = remember { DateTimeFormatter.ofPattern("H:mm") }
        val recordedTime = java.time.LocalDateTime.ofInstant(warn.recordedAt, java.time.ZoneId.systemDefault())
        AlertDialog(
            onDismissRequest = { warnAgainst = null; pendingSlot = null },
            title = { Text(stringResource(R.string.dup_warn_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.dup_warn_body,
                        warn.recordedByName,
                        recordedTime.format(timeFmt),
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.recordDose(warnSlot.slot, given = true, proceededAfterWarning = true)
                    warnAgainst = null
                    pendingSlot = null
                }) { Text(stringResource(R.string.dup_warn_proceed)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.analytics.log("double_dose_warned", mapOf("proceeded" to false))
                    warnAgainst = null
                    pendingSlot = null
                }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

@Composable
private fun EmptyMeds(
    onAddPet: () -> Unit,
    onAddMed: (String) -> Unit,
    viewModel: AppViewModel,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.today_no_meds), style = MaterialTheme.typography.bodyMedium)
        val pets = viewModel.uiState.value.household?.pets ?: emptyList()
        if (pets.isEmpty()) {
            Button(onClick = onAddPet) { Text(stringResource(R.string.pet_edit_title_new)) }
        } else {
            Button(onClick = { onAddMed(pets.first().id) }) { Text(stringResource(R.string.med_edit_title_new)) }
        }
    }
}

@Composable
private fun RecordDoseDialog(
    slotUi: TodaySlotUi,
    onGiven: () -> Unit,
    onSkipped: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.record_sheet_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${slotUi.slot.petName} — ${slotUi.slot.medName}（${slotUi.slot.slotLabel} ${slotUi.slot.time}）")
                Text(
                    stringResource(R.string.record_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onSkipped) { Text(stringResource(R.string.record_skipped)) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onGiven) { Text(stringResource(R.string.record_given)) }
            }
        },
    )
}

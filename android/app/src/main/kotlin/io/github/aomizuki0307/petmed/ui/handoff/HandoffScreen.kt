package io.github.aomizuki0307.petmed.ui.handoff

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aomizuki0307.petmed.R
import io.github.aomizuki0307.petmed.domain.CareHandoffSummary
import io.github.aomizuki0307.petmed.domain.HandoffActivity
import io.github.aomizuki0307.petmed.domain.model.DoseStatus
import io.github.aomizuki0307.petmed.ui.AppViewModel
import io.github.aomizuki0307.petmed.ui.theme.WarnBg
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandoffScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val summary = state.handoffSummary
    val context = LocalContext.current
    val shareChooserTitle = stringResource(R.string.handoff_share_chooser)

    LaunchedEffect(Unit) {
        viewModel.analytics.log("handoff_viewed")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.handoff_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    if (summary != null) {
                        IconButton(onClick = {
                            viewModel.analytics.log("handoff_shared")
                            shareHandoff(context, shareChooserTitle, summary)
                        }) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.handoff_share))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (summary == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
            ) {
                Text(stringResource(R.string.handoff_empty))
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { OverviewCard(summary) }

            if (summary.duplicateWarnings.isNotEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = WarnBg)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                stringResource(R.string.handoff_duplicate_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            summary.duplicateWarnings.forEach { warning ->
                                Text(
                                    stringResource(
                                        R.string.handoff_duplicate_line,
                                        warning.slot.petName,
                                        warning.slot.medName,
                                        warning.records.size,
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }

            summary.nextDose?.let { next ->
                item {
                    Text(stringResource(R.string.handoff_next_title), style = MaterialTheme.typography.titleMedium)
                    Card {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                "${next.slot.petName} — ${next.slot.medName}",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                stringResource(
                                    if (next.overdue) R.string.handoff_overdue else R.string.handoff_scheduled,
                                    next.slot.slotLabel,
                                    next.slot.time.toString(),
                                ),
                                color = if (next.overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            if (summary.remainingDoses.size > 1) {
                item { Text(stringResource(R.string.handoff_remaining_title), style = MaterialTheme.typography.titleMedium) }
                items(summary.remainingDoses.drop(1), key = { it.slot.slotKey }) { dose ->
                    Card {
                        Text(
                            "${dose.slot.time}  ${dose.slot.petName} / ${dose.slot.medName}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                        )
                    }
                }
            }

            item { Text(stringResource(R.string.handoff_activity_title), style = MaterialTheme.typography.titleMedium) }
            if (summary.recentActivity.isEmpty()) {
                item { Text(stringResource(R.string.handoff_activity_empty)) }
            } else {
                items(summary.recentActivity, key = { it.record.id }) { activity ->
                    ActivityCard(activity)
                }
            }

            item {
                Button(
                    onClick = {
                        viewModel.analytics.log("handoff_shared")
                        shareHandoff(context, shareChooserTitle, summary)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Text(stringResource(R.string.handoff_share), modifier = Modifier.padding(start = 8.dp))
                }
            }

            item {
                Text(
                    stringResource(R.string.handoff_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun OverviewCard(summary: CareHandoffSummary) {
    val complete = summary.totalDoses > 0 && summary.resolvedDoses == summary.totalDoses
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (complete) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                stringResource(
                    if (complete) R.string.handoff_status_complete else R.string.handoff_status_in_progress
                ),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                stringResource(
                    R.string.handoff_progress,
                    summary.resolvedDoses,
                    summary.totalDoses,
                    summary.completedDoses,
                    summary.skippedDoses,
                )
            )
        }
    }
}

@Composable
private fun ActivityCard(activity: HandoffActivity) {
    val time = LocalDateTime.ofInstant(activity.record.recordedAt, ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("M/d H:mm"))
    val status = when (activity.record.status) {
        DoseStatus.GIVEN -> stringResource(R.string.today_status_given)
        DoseStatus.GIVEN_LATE -> stringResource(R.string.today_status_late)
        DoseStatus.SKIPPED -> stringResource(R.string.today_status_skipped)
        DoseStatus.CANCELLED -> ""
    }
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text("$time  ${activity.petName} / ${activity.medicationName}")
            Text(
                "$status — ${activity.record.recordedByName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun shareText(summary: CareHandoffSummary): String = buildString {
    appendLine("おくすり当番 引き継ぎ（${summary.date}）")
    appendLine("本日の確認済み: ${summary.resolvedDoses}/${summary.totalDoses}回")
    appendLine("投薬済み ${summary.completedDoses}回 / 見送り ${summary.skippedDoses}回")
    summary.nextDose?.let { next ->
        appendLine("次の確認: ${next.slot.time} ${next.slot.petName} / ${next.slot.medName}")
    }
    if (summary.duplicateWarnings.isNotEmpty()) {
        appendLine("注意: 重複記録 ${summary.duplicateWarnings.size}件")
    }
    append("※記録の共有用です。診断・投薬判断は獣医師の指示に従ってください。")
}

private fun shareHandoff(
    context: Context,
    chooserTitle: String,
    summary: CareHandoffSummary,
) {
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText(summary))
            },
            chooserTitle,
        )
    )
}

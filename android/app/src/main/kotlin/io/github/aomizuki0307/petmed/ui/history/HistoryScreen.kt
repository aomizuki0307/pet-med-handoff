package io.github.aomizuki0307.petmed.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aomizuki0307.petmed.R
import io.github.aomizuki0307.petmed.domain.DoubleDoseDetector
import io.github.aomizuki0307.petmed.domain.model.DoseStatus
import io.github.aomizuki0307.petmed.ui.AppViewModel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onOpenPaywall: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val fmt = DateTimeFormatter.ofPattern("M/d H:mm")

    val medNames = state.household?.medications?.associate { it.id to it.name } ?: emptyMap()
    val petNames = state.household?.pets?.associate { it.id to it.name } ?: emptyMap()

    val limitDays = state.historyLimitDays
    // 「履歴7日」= 今日を含む7日分（today-6 〜 today）
    val cutoff = limitDays?.let { LocalDate.now().minusDays(it - 1) }
    val visible = state.records
        .filter { it.status != DoseStatus.CANCELLED }
        .sortedByDescending { it.recordedAt }
    val effectiveIds = DoubleDoseDetector.effectiveRecords(state.records).map { it.id }.toSet()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        if (visible.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
            ) { Text(stringResource(R.string.history_empty)) }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val withinLimit = if (cutoff == null) visible else visible.filter { !it.slotDate.isBefore(cutoff) }
            val lockedCount = visible.size - withinLimit.size

            items(withinLimit, key = { it.id }) { r ->
                val statusLabel = when (r.status) {
                    DoseStatus.GIVEN -> stringResource(R.string.today_status_given)
                    DoseStatus.GIVEN_LATE -> stringResource(R.string.today_status_late)
                    DoseStatus.SKIPPED -> stringResource(R.string.today_status_skipped)
                    DoseStatus.CANCELLED -> ""
                }
                val isCancelled = r.id !in effectiveIds
                Card {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            "${LocalDateTime.ofInstant(r.recordedAt, ZoneId.systemDefault()).format(fmt)}  " +
                                "${petNames[r.petId] ?: "?"} / ${medNames[r.medId] ?: "?"}" +
                                if (isCancelled) " " + stringResource(R.string.history_cancelled) else "",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            "$statusLabel — ${r.recordedByName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (lockedCount > 0) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.history_locked),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = {
                                viewModel.logPaywallViewed("history_lock")
                                onOpenPaywall()
                            }) { Text(stringResource(R.string.history_locked_cta)) }
                        }
                    }
                }
            }
        }
    }
}

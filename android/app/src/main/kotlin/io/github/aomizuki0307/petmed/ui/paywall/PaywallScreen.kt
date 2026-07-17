package io.github.aomizuki0307.petmed.ui.paywall

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aomizuki0307.petmed.R
import io.github.aomizuki0307.petmed.ui.AppViewModel

/**
 * 価格表示 + 購入意向計測のみ（Play Billing 非実装 — docs/01 F13）。
 * 「購入に進む」→ purchase_intent イベント → 「準備中」ダイアログ。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(
    viewModel: AppViewModel,
    trigger: String,
    onBack: () -> Unit,
) {
    var showNotReady by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.logPaywallViewed(trigger)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.paywall_title)) },
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
            Text(stringResource(R.string.paywall_lead))

            Card {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(stringResource(R.string.paywall_free_name), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.paywall_free_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.paywall_monthly_name), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.paywall_paid_desc),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(
                        onClick = {
                            viewModel.purchaseIntent("monthly")
                            showNotReady = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.paywall_cta)) }
                }
            }

            Card {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.paywall_yearly_name), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.paywall_paid_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = {
                            viewModel.purchaseIntent("yearly")
                            showNotReady = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.paywall_cta)) }
                }
            }
        }
    }

    if (showNotReady) {
        AlertDialog(
            onDismissRequest = { showNotReady = false },
            title = { Text(stringResource(R.string.paywall_not_ready_title)) },
            text = { Text(stringResource(R.string.paywall_not_ready_body)) },
            confirmButton = {
                TextButton(onClick = { showNotReady = false }) {
                    Text(stringResource(R.string.common_close))
                }
            },
        )
    }
}

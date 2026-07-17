package io.github.aomizuki0307.petmed.ui.invite

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aomizuki0307.petmed.R
import io.github.aomizuki0307.petmed.domain.FreeTierPolicy
import io.github.aomizuki0307.petmed.ui.AppViewModel
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onOpenPaywall: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val code by viewModel.inviteCode.collectAsState()
    val context = LocalContext.current

    val household = state.household
    val canInvite = household != null &&
        FreeTierPolicy.canAddMember(household.household, household.members.size, Instant.now())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.invite_title)) },
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
            Text(stringResource(R.string.invite_body))

            if (!canInvite) {
                Card {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.paywall_free_desc),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(onClick = {
                            viewModel.logPaywallViewed("invite")
                            onOpenPaywall()
                        }) { Text(stringResource(R.string.history_locked_cta)) }
                    }
                }
            } else if (code == null) {
                Button(onClick = { viewModel.createInvite() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.invite_create))
                }
            } else {
                Card {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(code!!, style = MaterialTheme.typography.displayMedium)
                        Text(
                            stringResource(R.string.invite_expires, "72時間"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                OutlinedButton(
                    onClick = {
                        val share = Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "「おくすり当番」の招待コード: $code\nアプリで「招待コードで参加する」から入力してください（72時間有効）",
                                )
                            },
                            null,
                        )
                        context.startActivity(share)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.invite_share)) }
            }

            Text(stringResource(R.string.invite_members_title), style = MaterialTheme.typography.titleSmall)
            household?.members?.forEach { member ->
                Text(
                    "・${member.displayName}" + if (member.isOwner) "（オーナー）" else "",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

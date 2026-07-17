package io.github.aomizuki0307.petmed.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aomizuki0307.petmed.R
import io.github.aomizuki0307.petmed.domain.model.DoseRecord
import io.github.aomizuki0307.petmed.ui.TodaySlotUi
import io.github.aomizuki0307.petmed.ui.theme.Pine
import io.github.aomizuki0307.petmed.ui.theme.WarnBg
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 通知/正確なアラームの権限状態を常時可視化（両方OKなら何も出さない） */
@Composable
fun PermissionStatusCard(
    notifGranted: Boolean,
    exactAlarmGranted: Boolean,
    onFixNotification: () -> Unit,
    onFixExactAlarm: () -> Unit,
) {
    if (notifGranted && exactAlarmGranted) return
    Card(colors = CardDefaults.cardColors(containerColor = WarnBg)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (!notifGranted) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.perm_notification_off),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onFixNotification) { Text(stringResource(R.string.perm_fix)) }
                }
            }
            if (!exactAlarmGranted) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.perm_exact_alarm_off),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onFixExactAlarm) { Text(stringResource(R.string.perm_fix)) }
                }
            }
        }
    }
}

private val timeFmt = DateTimeFormatter.ofPattern("H:mm")

/** 今日の1スロットのカード。状態: 未記録 / 済 / 遅れ / 見送り / 重複。左側タップで薬の編集へ */
@Composable
fun DoseSlotCard(
    slotUi: TodaySlotUi,
    onRecordGiven: () -> Unit,
    onUndo: (DoseRecord) -> Unit,
    onEditMed: () -> Unit,
) {
    val containerColor = when {
        slotUi.isDuplicate -> WarnBg
        slotUi.isDone -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    Card(colors = CardDefaults.cardColors(containerColor = containerColor)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onEditMed),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    "${slotUi.slot.slotLabel} ${slotUi.slot.time.format(timeFmt)}  ${slotUi.slot.medName}",
                    style = MaterialTheme.typography.titleSmall,
                )
                when {
                    slotUi.isDuplicate -> {
                        Text(
                            stringResource(R.string.today_duplicate_badge),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        slotUi.given.forEach { r ->
                            Text(
                                recordLine(r),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    slotUi.isDone -> {
                        val r = slotUi.given.first()
                        Text(
                            "✓ " + recordLine(r),
                            style = MaterialTheme.typography.bodySmall,
                            color = Pine,
                        )
                    }
                    slotUi.isSkipped -> {
                        Text(
                            stringResource(R.string.today_skipped_by, slotUi.skipped.first().recordedByName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (slotUi.isDone || slotUi.isSkipped) {
                TextButton(onClick = {
                    val record = slotUi.given.firstOrNull() ?: slotUi.skipped.first()
                    onUndo(record)
                }) { Text(stringResource(R.string.common_undo)) }
            } else {
                Button(onClick = onRecordGiven) { Text(stringResource(R.string.today_record_given)) }
            }
        }
    }
}

@Composable
private fun recordLine(r: DoseRecord): String {
    val time = LocalDateTime.ofInstant(r.recordedAt, ZoneId.systemDefault()).format(timeFmt)
    return stringResource(R.string.today_recorded_by, r.recordedByName, time)
}

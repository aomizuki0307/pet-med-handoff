package io.github.aomizuki0307.petmed.domain

import io.github.aomizuki0307.petmed.domain.model.DoseRecord
import io.github.aomizuki0307.petmed.domain.model.DoseStatus
import java.time.Duration
import java.time.Instant

/**
 * 二重投薬の判定と遅延判定（純Kotlin・副作用なし）。
 * doseRecords は append-only。訂正は CANCELLED レコード（cancelsRecordId）で相殺する。
 */
object DoubleDoseDetector {

    val LATE_THRESHOLD: Duration = Duration.ofMinutes(60)

    /** CANCELLED による相殺を適用した「有効な」レコード一覧 */
    fun effectiveRecords(records: List<DoseRecord>): List<DoseRecord> {
        val cancelledIds = records
            .filter { it.status == DoseStatus.CANCELLED }
            .mapNotNull { it.cancelsRecordId }
            .toSet()
        return records.filter { it.status != DoseStatus.CANCELLED && it.id !in cancelledIds }
    }

    /** 同一スロットの有効な「投与済み」レコード（GIVEN / GIVEN_LATE） */
    fun givenRecordsForSlot(records: List<DoseRecord>, slotKey: String): List<DoseRecord> =
        effectiveRecords(records)
            .filter { slotKeyOf(it) == slotKey }
            .filter { it.status == DoseStatus.GIVEN || it.status == DoseStatus.GIVEN_LATE }
            .sortedBy { it.recordedAt }

    /** 記録直前の警告判定: 既に投与済み記録があれば最初の1件を返す */
    fun existingGiven(records: List<DoseRecord>, slotKey: String): DoseRecord? =
        givenRecordsForSlot(records, slotKey).firstOrNull()

    /** 同期後の重複検知: 有効な投与済みが2件以上あるスロット */
    fun duplicateSlots(records: List<DoseRecord>): Map<String, List<DoseRecord>> =
        effectiveRecords(records)
            .filter { it.status == DoseStatus.GIVEN || it.status == DoseStatus.GIVEN_LATE }
            .groupBy { slotKeyOf(it) }
            .filterValues { it.size >= 2 }

    /** 投与記録のステータス決定: 予定から60分超過なら GIVEN_LATE */
    fun statusForGiven(scheduledAt: Instant, recordedAt: Instant): DoseStatus =
        if (Duration.between(scheduledAt, recordedAt) > LATE_THRESHOLD) {
            DoseStatus.GIVEN_LATE
        } else {
            DoseStatus.GIVEN
        }

    fun slotKeyOf(r: DoseRecord): String =
        DoseSlotCalculator.slotKey(r.petId, r.medId, r.slotId, r.slotDate)
}

package io.github.aomizuki0307.petmed.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.aomizuki0307.petmed.domain.model.DoseSlotInstance
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

private val Context.scheduleDataStore by preferencesDataStore(name = "schedule_cache")

/**
 * 直近48時間のスロット表を端末内に保持する（DataStore）。
 * アラーム発火・再起動後の復元は Firestore/リポジトリに依存せず、
 * 必ずこのキャッシュだけを読む（docs/05 の同期方針）。
 */
class ScheduleCache(private val context: Context) {

    private val key = stringPreferencesKey("upcoming_slots_json")

    data class CachedSlot(
        val slot: DoseSlotInstance,
        val at: LocalDateTime,
    )

    suspend fun write(slots: List<Pair<DoseSlotInstance, LocalDateTime>>) {
        val arr = JSONArray()
        slots.forEach { (s, at) ->
            arr.put(
                JSONObject()
                    .put("petId", s.petId)
                    .put("petName", s.petName)
                    .put("medId", s.medId)
                    .put("medName", s.medName)
                    .put("slotId", s.slotId)
                    .put("slotLabel", s.slotLabel)
                    .put("slotDate", s.slotDate.toString())
                    .put("time", s.time.toString())
                    .put("at", at.toString())
            )
        }
        context.scheduleDataStore.edit { it[key] = arr.toString() }
    }

    suspend fun read(): List<CachedSlot> {
        val json = context.scheduleDataStore.data.first()[key] ?: return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                CachedSlot(
                    slot = DoseSlotInstance(
                        petId = o.getString("petId"),
                        petName = o.getString("petName"),
                        medId = o.getString("medId"),
                        medName = o.getString("medName"),
                        slotId = o.getString("slotId"),
                        slotLabel = o.getString("slotLabel"),
                        slotDate = LocalDate.parse(o.getString("slotDate")),
                        time = LocalTime.parse(o.getString("time")),
                    ),
                    at = LocalDateTime.parse(o.getString("at")),
                )
            }
        }.getOrDefault(emptyList())
    }

    /**
     * 次のアラーム対象スロット。
     * strict=false: now以降（>=） / strict=true: nowより厳密に後（>）
     * （発火後の再スケジュールはstrict=trueで呼び、早発火時の同一スロット再登録ループを防ぐ）
     */
    suspend fun nextAfter(now: LocalDateTime, strict: Boolean = false): CachedSlot? =
        read().filter { if (strict) it.at.isAfter(now) else !it.at.isBefore(now) }
            .minByOrNull { it.at }

    /** 指定時刻ちょうどの全スロット（同時刻に複数の薬がある場合をまとめて通知するため） */
    suspend fun slotsAt(at: LocalDateTime): List<CachedSlot> = read().filter { it.at == at }
}

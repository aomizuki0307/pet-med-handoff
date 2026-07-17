package io.github.aomizuki0307.petmed.data

import io.github.aomizuki0307.petmed.domain.model.DoseRecord
import io.github.aomizuki0307.petmed.domain.model.DoseStatus
import io.github.aomizuki0307.petmed.domain.model.HouseholdState
import io.github.aomizuki0307.petmed.domain.model.Medication
import io.github.aomizuki0307.petmed.domain.model.Member
import io.github.aomizuki0307.petmed.domain.model.Pet
import io.github.aomizuki0307.petmed.domain.model.RecordSource
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

/**
 * データ層の抽象境界。
 * mockフレーバー = FakeInMemoryPetCareRepository（認証情報なしで全フロー動作）
 * prodフレーバー = FirestorePetCareRepository（オフラインキャッシュ+リアルタイム同期）
 */
interface PetCareRepository {

    /** バックエンドが実際に構成されているか（prodでgoogle-services.json未配置ならfalse） */
    val isBackendConfigured: Boolean

    /** 現在参加している世帯の状態。未参加ならnullを流す */
    val householdState: Flow<HouseholdState?>

    /** 直近の投薬記録（append-only、CANCELLED含む生データ） */
    val doseRecords: Flow<List<DoseRecord>>

    suspend fun createHousehold(displayName: String, householdName: String)

    /** 招待コードで参加。失敗時は JoinException */
    suspend fun joinHousehold(code: String, displayName: String)

    suspend fun addPet(name: String, species: io.github.aomizuki0307.petmed.domain.model.Species, birthYear: Int?)
    suspend fun updatePet(pet: Pet)

    suspend fun addMedication(medication: Medication)
    suspend fun updateMedication(medication: Medication)

    /**
     * 投薬記録を追加（append-only）。
     * status=CANCELLED の場合は cancelsRecordId 必須。
     */
    suspend fun recordDose(
        petId: String,
        medId: String,
        slotId: String,
        slotDate: LocalDate,
        status: DoseStatus,
        scheduledAt: Instant,
        source: RecordSource,
        cancelsRecordId: String? = null,
        clientRecordId: String = java.util.UUID.randomUUID().toString(),
    )

    /** 招待コードを発行して返す（72h有効） */
    suspend fun createInvite(): String

    suspend fun updateDisplayName(name: String)

    /** 有料意向を登録（purchase_intent。課金はしない） */
    suspend fun markPurchaseIntent(plan: String)

    /** 自分のアカウントを削除（世帯から退出）。オーナーで他メンバーがいない場合は世帯ごと削除 */
    suspend fun deleteMyAccount()

    /** 世帯の全データを削除（オーナーのみ） */
    suspend fun deleteHousehold()

    class JoinException(message: String) : Exception(message)
}

/** 分析イベント送信（docs/07 が正典。PII・医療入力値の送信禁止） */
interface AnalyticsLogger {
    fun log(name: String, params: Map<String, Any?> = emptyMap())
}

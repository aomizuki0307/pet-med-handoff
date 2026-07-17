# 05. データモデルと同期方針

## バックエンド選定（比較表）

| 観点 | **Firebase Spark（採用）** | Supabase Cloud (free) | Supabase self-host（既存PC） | 独自バックエンド | ローカルのみ+手動共有 |
|---|---|---|---|---|---|
| 認証 | 匿名認証組込み、後からemail昇格可 | 要設定 | 同左+運用 | 全自作 | 不要 |
| 家族招待 | ドキュメント+rulesで成立（Functions不要） | RLS+テーブル | 同左 | 自作 | **不可** |
| リアルタイム同期 | snapshot listener組込み | Realtimeあり | PC稼働時のみ | 自作 | なし |
| オフライン対応 | **永続キャッシュ+書込キュー組込み** | なし（自前実装） | なし | 自作 | 完全 |
| データ削除 | ドキュメント削除+Auth削除API | SQL | 同左 | 自作 | 端末内 |
| バックアップ | クラウド保存が兼ねる+Firestore export | pg_dump | 自前 | 自前 | なし |
| 月間費用 | ¥0（30世帯はSpark枠内） | ¥0（無アクセス1週間で停止） | 電気代+PC常時稼働 | VPS費 | ¥0 |
| ロックイン | 中（Repository抽象で緩和） | 低 | 低 | なし | なし |
| セキュリティ | rulesで宣言的・実績豊富 | RLS | 公開露出リスク自己責任 | 自己責任 | N/A |
| 運営工数/月 | **≈0h** | ≈1h | 4h+PC uptime | 8h+ | 0h |
| 将来の英語対応 | 問題なし | 問題なし | 問題なし | — | — |

**決定打**: (a) オフライン永続+書込キューが組込みの唯一の無料選択肢、(b) 「家族招待≥30%」ゲートはローカルのみでは検証不能、(c) self-host はPC電源断=全世帯同期停止で検証データが汚染される。

**Roomは使わない**: クエリは「今日のスロット」「直近7日」「スロット別既存記録」のみで、Firestoreキャッシュで十分。
ただし**アラームはFirestoreに依存させない** — スケジュール変更のたびに直近48時間のスロット表を DataStore（`ScheduleCache`）へ書き出し、AlarmScheduler / BootReceiver / 通知アクションはDataStoreのみを読む。

## Firestore ドキュメント構成

```
households/{hid}
  name, createdByUid, createdAt, planTier: "free" | "trial" | "paid_intent"

households/{hid}/members/{uid}
  displayName, role: "owner" | "member", joinedAt

households/{hid}/pets/{petId}
  name, species: "dog" | "cat", birthYear?, note?, archived: bool

households/{hid}/medications/{medId}   # petIdフィールドでペットに紐付け
                                       # （petsのサブコレクションにしない: リスナー1本で全薬を監視するため）
  petId,
  name, dosageText,            # 獣医指示を自由文のまま（量の解釈・推奨はしない）
  active: bool,
  slots: [ { slotId, time: "08:00", label: "朝" } ],
  daysOfWeek: [1..7],          # ISO (1=月)
  startDate: "2026-07-17", endDate?: "...", note?

households/{hid}/doseRecords/{recordId}       # append-only 監査ログ
  petId, medId, slotId, slotDate: "2026-07-17",
  status: "GIVEN" | "SKIPPED" | "GIVEN_LATE" | "CANCELLED",
  cancelsRecordId?,            # 訂正は取消レコード追加で表現（update/delete禁止）
  scheduledAt: Timestamp, recordedAt: serverTimestamp,
  recordedByUid, recordedByName, source: "app" | "notification",
  clientRecordId               # UUID 冪等キー（通知アクション二重発火対策）

invites/{code}                 # 6桁英数字
  hid, createdByUid, createdAt, expiresAt (+72h), revoked: bool

events/{autoId}                # 分析イベント（07参照。PII・医療入力値の禁止）
  name, params: map, ts, hidHash, appVersion
```

将来拡張（v1.1、実装しない）: `households/{hid}/careRecords/{id}`（type: toilet|meal|symptom）。

## 二重投薬警告ロジック

- **slotKey = `{petId}_{medId}_{slotId}_{slotDate}`**（`DoseSlotCalculator.slotKey()`、純関数・単体テスト対象）
- 記録直前: 同一slotKeyの GIVEN / GIVEN_LATE（CANCELLED相殺後）が存在 → 警告ダイアログ
  「⚠️ この回は◯◯さんが HH:mm に投与済みです」→ 強行記録は可能（事実の記録が正）、Todayに「記録2件」バッジ
- GIVEN_LATE 判定: recordedAt が scheduledAt + 60分 超過
- 深夜スロットの日跨ぎ: slotDate はスロットの属する日付で固定（単体テストでカバー）

## オフラインと同期（MVPとして受容する制限）

- Firestore永続キャッシュON。書込はオフラインキュー→再接続で自動送信
- **オフライン同時記録の競合**: 2人が同一slotKeyへ記録 → 両レコードとも残す（append-only監査として両方が事実）。
  同期後にTodayへ「⚠️ 二重投薬の可能性: 母 08:02 / 父 08:05」バナー表示。自動マージしない
- 受容する制限（明記）:
  1. オンライン同士でも数秒窓の同時タップは事前警告できない（事後バナーで検知）
  2. 匿名認証のため、アンインストール=そのメンバーのアカウント消失（再招待で復帰。記録は世帯に残る）
  3. サインアウトUIは出さない（キャッシュ消失防止）
  4. 暗号化エクスポート/PDF/CSVは有料機能として**表示のみ**（purchase_intent計測対象）

## セキュリティルール（firestore.rules 全文は android/firestore.rules）

要点（ECCレビュー2026-07-17反映）:
- `households/**` は members に uid が存在する場合のみ read/write。household更新は name/planTier のみ許可
- `doseRecords` は **create のみ許可、update/delete 拒否**（append-only強制）。`recordedByUid == auth.uid` を強制（他人名義の記録禁止）
- member の create は「世帯作成者本人=owner固定」または「有効invite保持=member固定」。**roleの自己昇格は update ルールで禁止**（displayNameのみ変更可）
- `invites` は有効なもの（revoked=false かつ期限内）だけ get 可・list 禁止。**expiresAt≤72h をサーバ側で強制**。失効(revoked=true)はオーナーのみ
- オーナーの個別退出は不可（role移譲を許さないため）→ UIは「世帯全削除」のみ提示
- `events` は create のみ + キー・サイズの軽い検証
- 公開前に Firebase エミュレータで手動チェックリスト（08参照）必須 — rulesバグ=他世帯侵入事故
- 補強推奨: Firebase App Check（Play Integrity）でスクリプト経由の総当たり・フラッディングを抑止（docs/10）

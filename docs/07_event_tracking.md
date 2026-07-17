# 07. イベント計測設計

## 原則（PII・医療情報の禁止）

- **個人情報・医療に関係する入力値をイベントに載せない**: ペット名・薬名・用量・メモ・症状・email・表示名は送信禁止
- パラメータは列挙値・件数・真偽値のみ。自由文フィールドは存在させない
- ユーザー識別は `hidHash`（householdId の SHA-256 先頭8文字）。uid生値・招待コードは送らない
- 保存先: アプリ = Firestore `events` コレクション / LP = GAS → Google Sheets
- 30世帯規模のため GA4/Firebase Analytics は使わず、生イベントを直接目視・ピボット集計する

## アプリ内イベント辞書

| イベント名 | パラメータ | 対応仮説 |
|---|---|---|
| `first_open` | — | 到達計測 |
| `onboarding_done` | mode: create\|join | 到達計測 |
| `pet_registered` | species: dog\|cat, petCount | 前提 |
| `med_registered` | slotCount, daysPerWeek, medCount | 前提 |
| `dose_recorded` | status: given\|skipped\|given_late, source: app\|notification, isSecondCaregiver: bool, slotDate: "YYYY-MM-DD"（H2のdistinct日数集計に必須） | H1, H2 |
| `double_dose_warned` | proceeded: bool, source?: notification（通知経由の抑止時のみ付与） | H1 |
| `duplicate_detected_after_sync` | — | H1 |
| `invite_created` | — | H3 |
| `invite_accepted` | memberCount | H3 |
| `notification_opened` | — | H2 |
| `notification_permission` | granted: bool | リスク計測 |
| `exact_alarm_permission` | granted: bool（設定画面から戻ったON_RESUMEで許諾状態が変化した時のみ実測記録） | リスク計測 |
| `alarm_fired` | delayMinutes: int（±15分KPI自己計測） | H2 |
| `retention_ping` | daysSinceFirstOpen: 1\|7\|14 | W2継続 |
| `paywall_viewed` | trigger: history_lock\|pet_limit\|med_limit\|invite\|menu | H4 |
| `purchase_intent` | plan: monthly\|yearly | H4 |
| `data_deleted` | scope: member\|household | 運用 |
| `sync_error` | code: parse_medication\|parse_doseRecord\|record_denied（種別のみ・内容は送らない） | 品質 |
| `app_error` | screen（画面名のみ、メッセージ不可） | 品質 |

共通フィールド: `ts`, `hidHash`, `appVersion`, `name`, `params`。

## LPイベント辞書（beacon.js → GAS → Sheets `Events` タブ）

| イベント名 | パラメータ | 用途 |
|---|---|---|
| `page_view` | referrer, utm_source, utm_medium, utm_campaign | 流入元・分母 |
| `pricing_view` | — （料金セクション50%可視、IntersectionObserver） | 価格到達率 |
| `cta_click` | plan: free\|monthly\|yearly\|header\|hero（CTA率の分子は monthly\|yearly のみ） | **価格CTA率（ゲート≥6%の分子）** |
| `price_choice` | plan | 想定価格の選択 |
| `preregister` | hasEmail: bool, plan_interest | 事前登録率 |
| `leave` | maxScrollPct（visibilitychange時） | 離脱 |

共通フィールド: `vid`（localStorageの匿名UUID）, `ts`, `ua_coarse`（モバイル/デスクトップのみ）。
emailは `Preregister` タブにのみ保存し、`Events` タブには載せない。

## 集計方法

- LP: Sheets上でユニーク`vid`ベースのピボット。CTA率 = cta_click(monthly|yearly)のユニークvid ÷ page_viewのユニークvid
- アプリ: Firestoreコンソール or エクスポートで `events` を期間絞り込み。継続率・週記録日数は hidHash × slotDate のdistinctで算出
- サポート時間は手動記録（12_go_no_go_template.md の表に分単位で記入）

## 送信の実装規約

- アプリ: `AnalyticsLogger` インターフェース経由のみ（mock=Logcat、prod=Firestore）。UI層から直接Firestoreを叩かない
- LP: `navigator.sendBeacon` を優先、page_view のみ `fetch(keepalive:true)` フォールバック（分母欠損はCTA率判定を無効化するため）
- 送信失敗は無視（計測のためにUXを止めない）

# 04. 画面一覧とユーザーフロー

## 画面一覧

| # | route | 画面 | 主目的 |
|---|---|---|---|
| 1 | `onboarding` | オンボーディング | 価値説明+免責、開始/参加の分岐 |
| 2 | `join` | 招待コードで参加 | 6桁コード+表示名 |
| 3 | `pet_edit` | ペット登録/編集 | 名前・犬猫・年齢 |
| 4 | `med_edit` | 薬登録/編集 | 薬名・用量自由文・時刻スロット・曜日 |
| 5 | `today` | **きょうのおくすり（メイン）** | 今日のスロット一覧、1タップ記録、権限カード |
| 6 | (dialog) | 記録ダイアログ/二重警告 | 投薬済み/見送り、二重投薬の警告 |
| 7 | `history` | 投薬履歴（監査ログ） | 誰が・いつ・何を。無料は7日でロック |
| 8 | `invite` | 家族を招待 | コード発行・共有Intent・メンバー一覧 |
| 9 | `paywall` | プラン | 価格表示+purchase_intent計測（課金なし） |
| 10 | `settings` | 設定 | 表示名・権限・プライバシー・削除 |

## メインフロー

```mermaid
flowchart TD
    A[onboarding<br>免責+表示名] -->|新しく始める| B[pet_edit<br>ペット登録]
    A -->|招待コードで参加| J[join<br>6桁コード]
    B --> C[med_edit<br>薬+時刻+曜日]
    C --> P{通知権限<br>リクエスト}
    P --> T[today きょうのおくすり]
    J --> T
    T -->|投薬済みにする| R{既に投与済み?}
    R -->|いいえ| S[記録ダイアログ<br>投薬済み/見送り]
    R -->|はい| W[⚠️二重投薬警告<br>それでも記録/やめる]
    S --> T
    W --> T
    T --> H[history 履歴]
    T --> I[invite 招待]
    T --> G[settings 設定]
    H -->|7日ロック| PW[paywall]
    I -->|無料1人制限| PW
    G --> PW
    G -->|削除| A
```

## 通知フロー

```mermaid
flowchart LR
    AL[AlarmManager発火] --> N[通知表示]
    N -->|「投薬済み」アクション| WK[WorkManager<br>RecordDoseWorker] --> REC[記録+同期]
    N -->|本文タップ| T[today画面<br>notification_openedイベント]
    AL --> NEXT[次のアラームを再セット]
```

## 家族共有フロー（検証仮説H3の主経路）

1. 世帯Aのオーナーが invite でコード発行 → LINE等で共有（共有Intent）
2. 家族Bがアプリをインストール → onboarding →「招待コードで参加」
3. B の記録が A の today にリアルタイム反映（`invite_accepted` → `dose_recorded(isSecondCaregiver=true)`）

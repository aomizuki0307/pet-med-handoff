# 14. Play Console提出・クローズドテスト・募集運用

> 2026-07-18時点の実装とPlay Console画面に合わせた実施手順。
> Data Safetyと健康アプリ申告は、実装やSDKを変更したら再確認する。

## 0. 現在の状態

- Play Consoleデベロッパー: `tanzaku.inc`（個人用アカウント）
- アプリ名: `おくすり当番（ペット投薬共有）`
- パッケージ名: `io.github.aomizuki0307.petmed`（Play Consoleで利用可能確認済み）
- デフォルト言語: 日本語 / 種別: アプリ / 価格: 無料
- Firebase本番環境、匿名認証、Firestore rules、E2Eは確認済み
- releaseビルド（R8・lintVital・AAB生成）は成功済み
- 未完了: Play Consoleの法的確認、アップロード署名鍵、署名済みAAB

## 1. Play Consoleでアプリを作成

1. Play Console → `tanzaku.inc` → 「アプリを作成」。
2. 上記のアプリ名・パッケージ名・日本語・アプリ・無料を指定する。
3. アカウント所有者が次の2項目を確認してチェックする。
   - デベロッパープログラムポリシーを遵守している
   - 米国輸出法を遵守し、アプリの輸出が認可されている
4. 「アプリを作成」を押す。

この2項目は法的な申告なので、アカウント所有者の確認なしに代理チェックしない。

## 2. アップロード署名鍵とAAB

リポジトリ直下でアップロード鍵を作成する。パスワードはパスワードマネージャーにも保存する。

```powershell
keytool -genkeypair -v -keystore petmed-upload.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload
Copy-Item android\keystore.properties.example android\keystore.properties
```

`android/keystore.properties` の2つのパスワードを置換後:

```powershell
cd android
.\gradlew.bat bundleProdRelease
jarsigner -verify -verbose -certs app\build\outputs\bundle\prodRelease\app-prod-release.aab
```

`jar verified.` を確認する。次の2ファイルはGit管理外だが、紛失に備えて安全な場所へバックアップする。

- `petmed-upload.jks`
- `android/keystore.properties`

## 3. まず内部テスト

1. Play Console → テストとリリース → テスト → 内部テスト。
2. 「新しいリリースを作成」→ Play App Signingを有効化。
3. `app-prod-release.aab` をアップロード。
4. リリース名は自動値、リリースノートは次を使用。

> 初回クローズドテスト準備版。家族間の投薬記録共有、通知、招待、オフライン同期、データ削除を検証します。

5. 自分のGoogleアカウントを内部テスターに登録して公開。
6. Playストア経由でインストールし、Firebase接続と通知を1回確認。

内部テストだけならData Safety表示は免除されるが、クローズドテスト開始前には以下をすべて完了する。

## 4. アプリのコンテンツ

### プライバシーポリシー・削除URL

- プライバシーポリシー:
  `https://aomizuki0307.github.io/pet-med-lp/privacy.html`
- アカウント/データ削除:
  `https://aomizuki0307.github.io/pet-med-lp/privacy.html#data-deletion`

アプリ内にも設定画面から退出・世帯全削除の導線がある。

### 広告・アプリアクセス・対象年齢

- 広告: なし
- アプリアクセス: 全機能を利用可能。ログイン情報の提供不要（匿名認証）
- 対象年齢: 成人のペット飼育者向けとして `18歳以上` を推奨
- 子どもを対象としたアプリ: いいえ

全年齢のUIであっても子ども向けサービスではないため、対象年齢で子どもの区分を選ぶとFamilies要件が増える点に注意する。

### 健康アプリ申告

- 健康機能あり
- `Medication and Treatment Management（薬・治療管理）`
- 対象はペットの投薬記録・リマインダー・家族共有
- 医療機器: いいえ
- 診断、治療判断、投薬量計算、飲み合わせ判定: なし
- 獣医師の指示を自由文で記録するだけ
- プライバシーポリシーとアプリ内に免責表示あり

参考:
- https://support.google.com/googleplay/android-developer/answer/14738291
- https://support.google.com/googleplay/android-developer/answer/16679511

### Data Safety

共通回答:

- ユーザーデータを収集する: はい
- 第三者へ共有する: いいえ（Firebaseは委託先サービスプロバイダ）
- 転送中に暗号化: はい（Firebase SDKのHTTPS/TLS）
- データ削除リクエスト手段: はい
- アカウント作成: あり（Firebase匿名アカウント）

| Playのデータ種別 | 収集 | 必須/任意 | 目的 |
|---|---:|---|---|
| Personal info → Name | はい | 必須 | アプリ機能（家族内の表示名） |
| Personal info → User IDs | はい | 必須 | アプリ機能、アカウント管理 |
| App activity → App interactions | はい | 必須 | 分析 |
| App activity → Other user-generated content | はい | 必須 | アプリ機能（ペット・薬・用量・メモ・投薬記録） |

いずれも「共有なし」「一時的処理ではない」。位置情報、メール、電話番号、連絡先、写真、広告ID、クラッシュログは現行アプリでは収集しない。

Firebase SDKのデータ開示も更新時に再確認する:
https://firebase.google.com/docs/android/play-data-disclosure

## 5. ストア掲載情報

最低限、次を準備する。

- アプリアイコン（512×512 PNG）
- フィーチャーグラフィック（1024×500）
- スマートフォンのスクリーンショット2枚以上
- 短い説明（80文字以内）
- 詳細説明

短い説明案:

> ペットの投薬記録を家族でリアルタイム共有。飲ませ忘れや重複記録を確認できます。

掲載文では「二重投薬を防ぐ」と断定せず、「記録を共有」「重複を確認」と表現する。

## 6. クローズドテスト

1. テストとリリース → テスト → クローズドテスト → トラックを作成。
2. トラック名: `family-validation-01`
3. Googleアカウントのメールリストを作成し、テスターを登録。
4. 署名済みAABを使ってリリースを作成。
5. 必須タスクと申告をすべて完了して審査へ送信。
6. 承認後、オプトインURLをテスターへ送る。

個人用デベロッパーアカウントが対象の場合、製品版アクセスには最低12人が14日間連続でオプトインしたクローズドテストが必要。30世帯募集とは別に、少なくとも12個のGoogleアカウントが途中離脱しないよう管理する。

参考:
- https://support.google.com/googleplay/android-developer/answer/14151465
- https://support.google.com/googleplay/android-developer/answer/9845334

## 7. 30世帯募集とLP計測

### Day 1-3

- X/InstagramプロフィールからLPへ誘導
- UTM例:
  - `?utm_source=x&utm_medium=social&utm_campaign=validation_01`
  - `?utm_source=instagram&utm_medium=social&utm_campaign=validation_01`
  - `?utm_source=family&utm_medium=referral&utm_campaign=validation_01`
- 投稿先の宣伝規約を先に確認
- 医療効果を断定せず、無料の記録・共有ツールとして案内

### Day 4-7

- Preregisterシートから家族2人以上の世帯を優先して第1陣15世帯へ招待
- テスターのGoogleアカウントをメールリストへ追加
- オプトインURLとdocs/08の案内文を送付

### Day 8-10

- 第2陣を招待し計30世帯へ
- 12人以上が連続オプトインしているかPlay Consoleで確認

### 日次

- LP: page_view、pricing_view、cta_click、preregister
- Firebase: sync_error、app_error、alarm_fired
- サポート時間と問い合わせ件数
- `setup_test` は集計対象外

応募者へのメール送信やSNS投稿は外部への発信になるため、宛先・本文・投稿先を確認してから実行する。

## 8. 正式公開前

- docs/08の攻撃者視点rulesチェック
- App Check（Play Integrity）
- `data_deleted` が認証削除後に失敗する既知事象の再評価
- 12人×14日要件を満たした後、製品版アクセスを申請

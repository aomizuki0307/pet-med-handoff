# 10. Firebase セットアップ手順書（ユーザー作業）

> 所要約20分。完了すると prod フレーバーが実際に同期する。
> **このリポジトリには認証情報を含めない**（google-services.json は .gitignore 済み）。

## 1. Firebaseプロジェクト作成

1. https://console.firebase.google.com → 「プロジェクトを追加」
2. 名前: `pet-med-handoff`（任意）。Googleアナリティクスは**無効**でよい（独自events使用のため）
3. プラン: Spark（無料）のまま

## 2. Androidアプリを登録

1. プロジェクト概要 → Androidアイコン
2. パッケージ名: `io.github.aomizuki0307.petmed`
   - ※mockフレーバーは `.mock` サフィックス付きだがFirebaseを使わないので登録不要
3. `google-services.json` をダウンロード → `android/app/google-services.json` に配置
4. 配置後、`.\gradlew.bat assembleProdDebug` でビルドされることを確認

## 3. Authentication

1. 構築 → Authentication → 始める
2. ログイン方法 → **匿名** を有効化（それ以外は不要）

## 4. Firestore

1. 構築 → Firestore Database → データベースを作成
2. ロケーション: `asia-northeast1`（東京）
3. **本番モード**で開始
4. 「ルール」タブに `android/firestore.rules` の内容を全文貼り付け → 公開
5. インデックス: 現状の単一フィールドクエリでは複合インデックス不要。
   アプリ実行時にエラーログへインデックス作成URLが出た場合はそれに従う

## 5. 動作確認（エミュレータ2台）

```powershell
cd android
.\gradlew.bat assembleProdDebug
# AVD 2台起動（Pixel_7 / Medium_Phone）して両方にインストール
adb -s <emu1> install app\build\outputs\apk\prod\debug\app-prod-debug.apk
adb -s <emu2> install app\build\outputs\apk\prod\debug\app-prod-debug.apk
```

1. 端末1: 新しく始める → ペット・薬登録 → 招待コード発行
2. 端末2: 招待コードで参加 → 端末1の薬が見えること
3. 端末2で投薬記録 → 端末1に数秒で反映されること
4. 端末2を機内モード → 記録 → 解除 → 同期されること
5. Firestoreコンソールで `events` にイベントが溜まっていること

## 6. LP計測（GAS）のセットアップ

1. https://sheets.new で空のスプレッドシート作成 → URLの `/d/` と `/edit` の間のIDを控える
2. 拡張機能 → Apps Script → `lp/gas/Code.gs` の内容を貼り付け
3. プロジェクトの設定 → スクリプトプロパティ:
   - `SPREADSHEET_ID` = 1で控えたID
   - `BEACON_TOKEN` = ランダム32文字以上（PowerShell: `-join ((48..57)+(97..122) | Get-Random -Count 40 | % {[char]$_})`）
4. デプロイ → 新しいデプロイ → 種類「ウェブアプリ」
   - 実行ユーザー: 自分 / アクセス: **全員**
5. デプロイURLを控え、`lp/js/beacon.js` の `GAS_URL` と `GAS_TOKEN` を書き換え
6. LPをブラウザで開き、Sheetsの `Events` タブに page_view が入ることを確認

## 7. LP公開（GitHub Pages）

```powershell
cd lp
git init -b main && git add -A && git commit -m "lp: initial"
gh repo create pet-med-lp --public --source . --push
gh api repos/aomizuki0307/pet-med-lp/pages -X POST -f "source[branch]=main" -f "source[path]=/"
# 数分後 https://aomizuki0307.github.io/pet-med-lp/ で公開
```

※ beacon.js のGAS URL書き換え後にpushすること。

## 8. 推奨: Firebase App Check（Play Integrity）

匿名認証のみのため、スクリプトからの招待コード総当たり・eventsフラッディングの抑止として
App Check の有効化を推奨（Firebaseコンソール → App Check → Play Integrity。クローズドテスト前でなくても可、正式公開前には必須扱い）。

## 完了チェック

- [ ] google-services.json 配置 & assembleProdDebug 成功
- [ ] 匿名認証ON / Firestore作成 / rules公開
- [ ] エミュレータ2台で共有・同期・オフライン復帰を確認
- [ ] GAS デプロイ & Events タブに page_view
- [ ] **lp/privacy.html の連絡先メール（REPLACE_CONTACT_EMAIL）を実アドレスに書き換え**
- [ ] GitHub Pages で LP 公開（privacy.html のURLをPlay Consoleに登録）
- [ ] （推奨）App Check 有効化

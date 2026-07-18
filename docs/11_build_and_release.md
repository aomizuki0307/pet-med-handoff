# 11. ビルド・テスト・セットアップ手順

## 開発環境（このマシンで確認済み）

- Android SDK: `C:\Users\wandt\AppData\Local\Android\Sdk`（platforms 35/36, build-tools 35/36）
- JDK 21（PATH上、JetBrains Runtime）/ Gradle は wrapper（8.14.3）
- AVD: `Pixel_7`, `Medium_Phone_API_36.0`
- スタック: AGP 8.13.0 / Kotlin 2.2.20 / Compose BOM 2026.06.00 / minSdk 26 / targetSdk 36

## 日常コマンド（`android/` ディレクトリで実行）

```powershell
.\gradlew.bat assembleMockDebug        # 認証情報なしビルド（常時グリーン必須）
.\gradlew.bat testMockDebugUnitTest    # 単体テスト
.\gradlew.bat assembleProdDebug        # Firebase接続ビルド（google-services.json必要）
.\gradlew.bat lintMockDebug            # Lint
```

## エミュレータでの手動E2E（mockフレーバー）

```powershell
# AVD起動
& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd Pixel_7 &
# インストール
adb install app\build\outputs\apk\mock\debug\app-mock-debug.apk
```

確認フロー: オンボーディング→ペット登録→薬登録（時刻2つ）→Today表示→投薬記録→
「招待コードで参加」（別インストールで code≠000000）→デモ世帯で二重警告→履歴→プラン→削除

## 署名とAAB（クローズドテスト用）

```powershell
# 署名鍵の作成（初回のみ。keystoreは絶対にコミットしない）
keytool -genkeypair -v -keystore petmed-upload.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload
```

`android/keystore.properties`（gitignore済み）を作成:

```properties
storeFile=..\\..\\petmed-upload.jks
storePassword=＜控えたパスワード＞
keyAlias=upload
keyPassword=＜控えたパスワード＞
```

`app/build.gradle.kts` は `android/keystore.properties` が存在する場合のみ
release署名を適用する設定済み。鍵とパスワードはGit管理外のため、別途安全にバックアップする。

```powershell
.\gradlew.bat bundleProdRelease   # → app\build\outputs\bundle\prodRelease\app-prod-release.aab
```

## テスト方針（書く/書かないの線引き — docs/01非機能要件と対応）

- 書く: domain純関数（スロット展開・slotKey・二重判定・遅延境界・無料枠境界）
- 書かない（検証MVPでは省略と明記): Compose UIテスト / rulesの自動テスト（docs/08の手動チェックで代替）/ E2E自動化
- 通知・省電力・再起動は docs/08 の端末手動テスト表で担保

## 既知の制約

- mockフレーバーの「招待コードで参加」はデモ世帯に接続する擬似動作（コード 000000 のみ失敗）
- prodはgoogle-services.json未配置でも起動するがFakeで動作し、設定画面に注意書きが出る
- 履歴ロック等の14日トライアル判定は household.createdAt 基準（端末時刻に依存しない）

# ペットのおくすり当番（仮称） — pet-med-handoff

高齢・持病ペット（犬猫）の投薬を家族で共有し、**飲ませ忘れと二重投薬を防ぐ** Android アプリの
**開発前検証プロジェクト**。一般公開用の完成アプリではなく、需要・継続・支払い意欲の検証が目的。

根拠レポート:
- 第1段階: `AI_coding/docs/research/compass_artifact_wf-c99369ea-*.md`
- デューデリジェンス: `AI_coding/docs/research/android_top5_due_diligence_2026-07-17.md`（F節が本MVPの正典）

## 構成

| パス | 内容 |
|---|---|
| `android/` | Kotlin + Jetpack Compose の検証用MVP（mock/prod 2フレーバー） |
| `lp/` | 検証用日本語ランディングページ（GitHub Pages公開用）+ GAS計測エンドポイント |
| `docs/` | 要件・仮説・データモデル・検証手順・判定テンプレ等 01〜13 |

## クイックスタート

```powershell
# Androidビルド（Firebase認証情報なしで動くmockフレーバー）
cd android
.\gradlew.bat assembleMockDebug
.\gradlew.bat testMockDebugUnitTest

# LPをローカル確認
cd ..\lp
python -m http.server 8080   # → http://localhost:8080
```

Firebase接続（prodフレーバー）は `docs/10_setup_firebase.md` の手順でユーザーが
`google-services.json` を配置した後のみ有効。認証情報は捏造しない。

## 検証ゲート（docs/02 参照）

LP価格CTA≥6% / W2継続≥70% / 週4日記録≥50% / 有料意向≥40% / 家族招待≥30%。
中止条件: CTA<3% または W2<50% またはサポート>12h/月 → 2位案（釣果記録）へ移行。
判定は `docs/12_go_no_go_template.md` に記入する。

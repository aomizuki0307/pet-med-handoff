# OpenAI Build Week 2026 — Development Record

This file distinguishes work completed during the official submission period
and records how Codex and GPT-5.6 contributed.

## Eligibility timeline

- Official submission period: July 13–21, 2026 (Pacific Time).
- First project commit: July 17, 2026 JST.
- All repository code was created after the submission period opened.
- Build Week completion pass: July 18, 2026 JST.
- Codex configuration during the completion pass: `gpt-5.6-sol`, high reasoning.

The Git history is the timestamped source of truth for individual changes.

## Work built in the main Codex session

1. Reproduced the judge-build failure with a local production
   `google-services.json` present.
2. Isolated Google Services processing from the mock flavor while preserving
   the user's conditional production signing configuration.
3. Added a pure Kotlin caregiver-handoff summary that derives progress, overdue
   doses, duplicate warnings, and recent activity from append-only records.
4. Added the Compose handoff screen, Android share sheet, Today-screen entry
   point, navigation route, and deterministic judge data.
5. Added regression tests and verified both unit tests and mock APK generation.
6. Found an emulator-only startup crash caused by WorkManager initialization
   ordering and switched the application to Android's documented on-demand
   `Configuration.Provider` initialization.
7. Installed the APK on an Android emulator and verified cold start, Today,
   handoff navigation, and the Android text share sheet.
8. Produced the English README, Devpost copy, judge instructions, and video script.
9. Generated a reproducible 94.8-second, 1080p H.264/AAC demo with English
   narration and verified its duration and streams with FFprobe.

## Human decisions

- The app must coordinate records but never make a medical decision.
- A duplicate database record is reported as a duplicate *record*, not proof of
  a physical double dose.
- Shared text includes schedule identity and status but omits dosage instructions.
- The append-only correction model remains unchanged.
- Judges receive an offline mock APK so Firebase availability cannot block review.

## Verification commands

```powershell
cd android
.\gradlew.bat testMockDebugUnitTest assembleMockDebug --console=plain
```

Expected artifact:
`android/app/build/outputs/apk/mock/debug/app-mock-debug.apk`.

Verified completion state on July 18:

- 22 unit tests, 0 failures
- Android lint: pass
- APK Signature Scheme v2: verified
- Cold start and share flow: verified on emulator

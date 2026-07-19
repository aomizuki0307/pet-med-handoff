# Devpost Submission Draft — OpenAI Build Week 2026

## Submission fields

- **Project name:** Okusuri Toban — Shared Pet Medication Handoff
- **Category:** Apps for Your Life
- **Tagline:** Know who gave the pet's medicine before the next caregiver acts.
- **Repository URL:** `https://github.com/aomizuki0307/pet-med-handoff`
- **Judge APK:** `https://github.com/aomizuki0307/pet-med-handoff/releases/tag/build-week-2026`
- **Demo video URL:** `TODO_AFTER_YOUTUBE_PUBLICATION`
- **Prepared 95-second MP4:** `https://github.com/aomizuki0307/pet-med-handoff/releases/download/build-week-2026/build-week-demo.mp4`
- **Main Codex /feedback session ID:** `TODO_IF_EVER_SUBMITTED` (removed 2026-07-20; the
  previously recorded ID belonged to the Visual Contract main thread and must not be
  reused for this project)

## Project description

Families caring for an aging or chronically ill pet often coordinate medication
through memory and chat messages. When a reply is delayed, the next caregiver
cannot quickly tell whether a scheduled dose has already been handled.

Okusuri Toban is an Android coordination app with an append-only household
record. It shows today's schedule, records who handled each dose and when, warns
before a second given record is written, and synchronizes the production
prototype through Firebase. Its new caregiver-handoff view compresses today's
progress, the next unresolved schedule, duplicate-record warnings, and recent
activity into one shareable screen.

The project deliberately does not diagnose, recommend medication, interpret
dosage, or claim that a database record proves physical administration. It makes
caregiver state visible and leaves treatment decisions to people and veterinarians.

Codex with GPT-5.6 was used throughout the completion pass: diagnosing a
flavor-specific Gradle failure, implementing the pure Kotlin handoff model and
Compose experience, writing regression tests, and preparing the judge workflow.
Human decisions set the medical-safety boundary, append-only correction model,
duplicate-warning semantics, and the decision to omit dosage text from shares.

## Judge instructions

1. Download the attached `app-mock-debug.apk` release asset on an Android 8+
   device or emulator. No account, key, or Firebase project is required.
2. Open the app. A deterministic household with one pet and two medicines is loaded.
3. Tap **見る** on the handoff card.
4. Review today's progress, next unresolved schedule, and recent record.
5. Use **引き継ぎを共有** to open the Android share sheet.
6. Return to Today and try recording the already-completed morning medicine to
   see the double-record warning.

To build instead:

```powershell
cd android
.\gradlew.bat testMockDebugUnitTest assembleMockDebug
```

## Final submission checklist

- [ ] Public GitHub repository URL inserted above
- [ ] MIT license visible at repository root
- [ ] Judge APK attached to a GitHub Release
- [ ] Public YouTube video under three minutes inserted above
- [ ] English audio explains both Codex and GPT-5.6 use
- [ ] `/feedback` session ID accepted by the Devpost form
- [ ] Repository secret scan clean
- [ ] APK install tested on a clean emulator/device
- [ ] Submission saved at least several hours before the deadline

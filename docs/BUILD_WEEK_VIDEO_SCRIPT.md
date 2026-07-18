# OpenAI Build Week Demo Script (2:35 target)

Public YouTube video, English narration, no copyrighted music. Keep the Android
screen large and readable. Do not show real pet, household, or credential data.

| Time | Visual | Narration |
|---|---|---|
| 0:00–0:15 | Title, then a delayed family chat message | "When several people care for an aging pet, one question can be surprisingly hard: has someone already given this medicine? A delayed reply can leave the next caregiver guessing." |
| 0:15–0:35 | Open Today screen with deterministic demo household | "Okusuri Toban gives the household one shared, append-only medication record. This judge build needs no account or API key. It opens with safe sample data." |
| 0:35–0:55 | Point to the completed morning card and caregiver/time | "The Today view shows the scheduled medicine, who recorded it, and when. Corrections append a cancellation event instead of rewriting history." |
| 0:55–1:20 | Open the new handoff screen | "For Build Week I added this caregiver handoff. It derives today's progress, the next unresolved schedule, overdue items, duplicate-record warnings, and recent activity from the same effective record timeline." |
| 1:20–1:35 | Tap Share and show Android share sheet | "A caregiver can send a bounded summary through the Android share sheet. Dosage instructions are deliberately excluded, because this product coordinates records and never makes a medical decision." |
| 1:35–1:55 | Return and try recording the already-completed dose; warning appears | "If someone tries to record a dose that already has a given record, the app names the caregiver and time before allowing another entry. It reports record state without claiming what physically happened." |
| 1:55–2:20 | Show source tree, handoff unit tests, successful Gradle output | "Codex with GPT-5.6 helped me trace a production Firebase configuration leaking into the offline mock build, implement the pure Kotlin handoff model and Compose flow, and add regression tests. I made the product and safety decisions: append-only history, no medical advice, and an account-free judge path." |
| 2:20–2:35 | Architecture diagram from README and closing title | "The result is a runnable Android product for a specific family workflow, not only a technical proof of concept. The repository, setup instructions, test build, and development record are linked below." |

## Required capture evidence

- Handoff progress and next-dose card
- Android share sheet
- Existing-dose warning with sample caregiver and time
- `BUILD SUCCESSFUL` for test plus mock APK assembly
- README section “How Codex and GPT-5.6 were used”
- Final medical-safety disclaimer on screen

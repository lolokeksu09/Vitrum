# VITRUM 1.1 — Experience & Reliability

> **Historical plan.** This document records the agreed scope for the Vitrum 1.1 stage. It is retained as project history, not as the current backlog or proof that every item was implemented. Current primary release: [Vitrum 2.0.0](releases/v2.0.0.md); current repository state: [HANDOFF](HANDOFF.md).

Approved scope: UI Foundation → Home/Servers → Routing/Shield → Settings/Diagnostics → verification/clone release. This document tracks code scope and verification boundaries, not device-level success.

## Invariants
- Preserve `app.rayclient` and `app.rayclient.clone`, Xray, routing semantics, kill switch, Vault, and signing identity.
- No implicit network requests, telemetry, root usage, or permission prompts.
- Check actual Android 16 screen geometries separately from unit tests.
- Each PR runs `testDebugUnitTest assembleDebug assembleClone`; release only for Clone after green CI.

## Stage 1 — UI Foundation
- Native touch targets of at least 48 dp.
- Segmented and navigation text follows system font scale, with adaptive height for larger fonts.
- Keep the established AMOLED/Manrope and Material 3 palette.

## Stage 2 — Home + Servers
- Responsive home scrolling and actionable VPN state.
- Clear empty-search and subscription/server states.
- Preserve favorites, selection, and sorting semantics.

## Stage 3 — Routing + Shield
- Explain routing outcomes and distinguish direct paths from tunnel traffic.
- Distinguish unknown Shield checks from verified evidence.

## Stage 4 — Settings + Diagnostics
- Search settings without silently enabling controls.
- Display diagnostics with actionable states, consistent copy, and redaction.

## Stage 5 — Verification + Clone
- Unit tests; SDK/build identity verification and redacted build artifacts.
- Android 16 phone acceptance: 360/390/430 dp widths where available, font scales 1.0/1.3/1.5, navigation buttons, TalkBack, error/empty/loading states, and traffic behavior.
- Do not publish an official release without manual VPN tests.

## Implementation evidence
- Stage 1 PR #11: merged after GitHub `testDebugUnitTest assembleDebug assembleClone`.
- Stage 2 PR #12: merged after fixing missing Compose scroll imports and rerunning CI.
- Stage 3 PR #13: merged after fixing a missing text-alignment import and rerunning CI.
- Stage 4 PR #14: merged with `SettingsSearchTest` and clean CI.
- Stage 5 PR #15: integration/translation/Clone pipeline; requires its own green CI before merge.
- A green build does not confirm real Android VPN behavior; the manual matrix remains separate.

Integration finding: routing explanation must follow `AppState.defaultAction` values 0/1/2, not assume unmatched traffic always enters the VPN. All three routes are now displayed correctly; Xray behavior is unchanged.

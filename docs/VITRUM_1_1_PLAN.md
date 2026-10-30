# VITRUM 1.1 — Experience & Reliability

Approved scope: UI Foundation → Home/Servers → Routing/Shield → Settings/Diagnostics → verification/clone release. This document tracks implementation boundaries and does not declare features verified.

## Invariants
- Preserve `app.rayclient` and `app.rayclient.clone`, Xray, routing semantics, kill switch, Vault and signature.
- No implicit network requests, telemetry, root usage or permission prompts.
- Check actual Android 16 screen geometries separately from unit tests.
- Every PR runs `testDebugUnitTest assembleDebug assembleClone`, with release only for the clone after green CI.

## Stage 1 — UI Foundation
- Native touch targets minimum 48 dp.
- Segmented and navigation text follow system font scale, with height adapting to large fonts.
- Keep the established AMOLED/Manrope and Material 3 palette.

## Stage 2 — Home + Servers
- Responsive home scrolling and actionable VPN state.
- Clear empty search and subscription/server states.
- Preserve favorites, selection and sorting semantics.

## Stage 3 — Routing + Shield
- Explain routing outcomes and distinguish direct paths from tunnel traffic.
- Distinguish unknown shield checks and verified evidence.

## Stage 4 — Settings + Diagnostics
- Search settings without silently enabling controls.
- Diagnoses displayed with actionable states, consistent copy and redaction.

## Stage 5 — Verification + clone
- Unit tests; SDK/build identity verification and redacted build artifacts.
- Android 16 phone acceptance: 360/390/430 dp widths where available, font 1.0/1.3/1.5, navigation buttons, TalkBack, error/empty/loading, traffic behavior.
- Do not publish official release without manual VPN tests.

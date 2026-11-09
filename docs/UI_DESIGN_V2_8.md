# VITRUM — Native UI clarity (v2.8 candidate)

> **Архивный документ.** Это кандидатное UI-направление для линии `v2.8`, составленное по состоянию на 2026-11-05. Оно не является действующим дизайн-контрактом Vitrum 2.0.0. Актуальные публичные функции и ограничения описаны в [заметках v2.0.0](releases/v2.0.0.md) и [HANDOFF](HANDOFF.md).

## Scope and comparison (source reviewed 2026-11-05)

- **Hiddify**: `lib/features/home/view/home_page.dart` places current profile beside a single connection action; `widgets/connection_button.dart` reflects explicit connection state and disables switching. Keep the fast profile/action relationship, but do not copy its large glowing circle.
- **Proton VPN**: `app/src/main/java/com/protonvpn/android/redesign/home_screen/ui/Home.kt` separates VPN status state from home presentation and accounts for screen size and active UI transitions. Preserve state fidelity and layout hierarchy; do not transplant its map, upsells, or identity.
- **v2rayNG**: `ui/main/MainServerPager.kt` uses LazyColumn or grid and stable per-server models; keep practical server selection, latency and favorites.
- **NekoBox**: user flow remains server selection → connect → VPN permission; preserve that sequence. No source/assets copied.

**VITRUM current source findings:** `HomeScreen.kt` uses a 300-dp animated glowing orb with multiple infinite transitions and a 320-dp mandatory height. `Ui.kt` uses gradient 'glass' panels and a custom floating pill navbar. `MainActivity.Root` draws animated AuroraBackground behind every screen. `ServersScreen.ServerRow` uses gradients/borders across all rows. The three systems combined hide meaningful state and compete with server list readability.

## Design direction — restrained Android utility

A VPN is a binary operation with consequential statuses, not a neon sculpture. The identity comes from **VITRUM's existing Manrope typography and violet accent**, not glass or generic cybersecurity shields.

- Dark: background `#000000` (AMOLED), main raised surface `#111218`, elevated secondary `#1B1C24`, foreground `#F3F1F9`, secondary text `#A9A5B8`, violet interactive `#A78BFA`, green connected `#3DDBB4`.
- Light: existing neutral scheme, plain opaque surfaces and semantic color roles.
- Hierarchy: **connection state → connect/disconnect → selected server → HTTP evidence → traffic**. Extra features remain accessible but visually secondary.
- Navigation: normal Material3 bottom bar, not glass, glow, drifting selection or bounce animations; preserve exact Home/Settings routes and system Back handling.
- Surfaces: low radius, one background and subtle divider, no gradient frames, no repeated icon tiles or nested cards. Same typed labels for status in both themes.
- Motion: only state/interaction feedback, no continuous idle waves; connection's busy state can use a small Material progress indicator. Reduced Motion supported by the absence of decorative loops.
- Touch: actions >=48dp, one main connection action >=60dp, status description for TalkBack, multiline labels at 1.3 font scale, maintain insets.
- Servers: selected item distinguished by one quiet surface highlight and a check; stable existing click, long press, favorite, and verified switching policy unchanged.
- Settings: text-first plain list; 48dp tap areas, grouped by purpose, no colorful fake tiles. No search/feature loss.
- No changes to VPN/Xray, server storage, DNS routing, flags, kill switch semantics or APK certificate.

## Acceptance / limitations

- GitHub PR includes a minimal visual regression test around UI state labels (connected, disconnected, connecting, blocked, error).
- `testDebugUnitTest`, debug/clone compile and Android 16 UI smoke must pass before merge.
- Get screenshots from actual emulator workflow if available, otherwise state that source/build was checked but screenshots not visually reviewed. Do not pretend that an online concept image proves Android runtime.
- Dark/light + 1.3x fonts + narrow screen should be manually checked on real device (Lenovo Legion Y70) before stable release. Existing official signed v2.7.0 remains until a separate verified release.

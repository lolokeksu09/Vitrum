# VITRUM — Native UI Clarity (v2.8 Candidate)

> **Archived document.** This candidate UI direction was written for the `v2.8` line as of 2026-11-05. It is not the active design contract for Vitrum 2.0.0. Current public features and limitations are documented in [v2.0.0 release notes](releases/v2.0.0.md) and [HANDOFF](HANDOFF.md).

## Scope and comparison (sources reviewed 2026-11-05)

- **Hiddify**: `lib/features/home/view/home_page.dart` places the current profile beside a single connection action; `widgets/connection_button.dart` reflects explicit connection state and disables switching. Keep the fast profile/action relationship, but do not copy its large glowing circle.
- **Proton VPN**: `app/src/main/java/com/protonvpn/android/redesign/home_screen/ui/Home.kt` separates VPN status from home presentation and accounts for screen size and active UI transitions. Preserve state fidelity and layout hierarchy; do not transplant its map, upsells, or identity.
- **v2rayNG**: `ui/main/MainServerPager.kt` uses LazyColumn or grid and stable per-server models; keep practical server selection, latency, and favorites.
- **NekoBox**: preserve the user flow server selection → connect → VPN permission. Do not copy source or assets.

**VITRUM source findings at the time:** `HomeScreen.kt` used a 300-dp animated glowing orb with multiple infinite transitions and a 320-dp mandatory height. `Ui.kt` used gradient glass panels and a custom floating pill navbar. `MainActivity.Root` drew animated AuroraBackground behind every screen. `ServersScreen.ServerRow` used gradients/borders across all rows. Together, these systems obscured meaningful state and reduced server-list readability.

## Design direction — restrained Android utility

A VPN is a binary operation with consequential states, not a neon sculpture. The identity comes from **VITRUM's existing Manrope typography and violet accent**, not glass or generic cybersecurity shields.

- Dark: background `#000000` (AMOLED), main raised surface `#111218`, elevated secondary `#1B1C24`, foreground `#F3F1F9`, secondary text `#A9A5B8`, violet interactive `#A78BFA`, green connected `#3DDBB4`.
- Light: existing neutral scheme, plain opaque surfaces, and semantic color roles.
- Hierarchy: **connection state → connect/disconnect → selected server → HTTP evidence → traffic**. Additional features remain accessible but visually secondary.
- Navigation: standard Material 3 bottom bar, not glass, glow, drifting selection, or bounce animations; preserve exact Home/Settings routes and system Back handling.
- Surfaces: low radius, one background and subtle divider; no gradient frames, repeated icon tiles, or nested cards. Use consistent status labels in both themes.
- Motion: state/interaction feedback only; no continuous idle waves. Connection busy state may use a small Material progress indicator. Avoid decorative loops.
- Touch: actions >=48dp, primary connection action >=60dp, status descriptions for TalkBack, multiline labels at 1.3 font scale, correct insets.
- Servers: distinguish the selected item with one quiet surface highlight and a check; preserve existing click, long-press, favorite, and verified switching behavior.
- Settings: text-first plain list; 48dp touch targets, grouped by purpose, no decorative colored tiles. No search or feature loss.
- Do not change VPN/Xray, server storage, DNS routing, flags, kill-switch semantics, or APK certificate.

## Acceptance and limitations
- PRs should include a minimal visual regression test for UI state labels (connected, disconnected, connecting, blocked, error).
- `testDebugUnitTest`, Debug/Clone compilation, and Android 16 UI smoke must pass before merge.
- Use screenshots from the actual emulator workflow when available. Otherwise state that source/build was checked but screenshots were not visually reviewed. An online concept image is not proof of Android runtime behavior.
- Dark/light themes, 1.3x fonts, and narrow screens require manual verification on a real device (Lenovo Legion Y70) before a stable release. The existing official signed v2.7.0 remains until a separately verified release.

# Vitrum TV — project foundation

**Status:** Stage 1 Android TV shell implemented on the TV feature branch, 2026-11-13. This is a developer-only interface **without** a VPN engine; GitHub Actions verified a developer-only APK build and Android TV 11 emulator D-pad tests in [run 43363278312](https://github.com/lolokeksu09/Vitrum/actions/runs/43363278312). No official TV release exists. Stage 1.1 UI hardening and device-read-only diagnostics were verified by [CI run 43376055711](https://github.com/lolokeksu09/Vitrum/actions/runs/43376055711) (APK build + four Android TV 11 emulator tests). This new APK has **not** been validated on the physical BOXY; older `0.1.0-dev` was.

**Development branch:** `feature/vitrum-tv-foundation` in `lolokeksu09/Vitrum`. The release branch and published app versions are unaffected.

Vitrum TV is a **new, dedicated Android TV VPN application** designed for D-pad remotes, 10-foot viewing, low-memory streaming boxes and an independent Android application lifecycle. It is not a TV mode of the phone client and does not derive from the tablet-oriented edition. Its code, package identity, encrypted profile storage and VPN service must be separate.

## Documents

1. [PRODUCT.md](PRODUCT.md) — product goals, audience, MVP, non-goals and acceptance.
2. [ARCHITECTURE.md](ARCHITECTURE.md) — proposed standalone `:tv` architecture, 32-bit runtime, connection state and security invariants.
3. [DESIGN.md](DESIGN.md) — TV-first interaction model, focus navigation, screen structure, visual system and bilingual copy.
4. [PAIRING_SECURITY.md](PAIRING_SECURITY.md) — phone-to-TV import threat model, alternatives and decision gate.
5. [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) — sequenced milestones, real file targets, decision gates and deliverables.
7. [BOXY_UI_AUDIT_2026-11-13.md](BOXY_UI_AUDIT_2026-11-13.md) — real ARM32 Android TV box audit: all five screens, screenshots, D-pad navigation and measured memory.
6. [TESTING.md](TESTING.md) — emulators, BOXY hardware matrix, release and security evidence requirements.

## Evidence baseline

Repository facts on 2026-11-12:
- Existing main Android application is `:app`, `app.rayclient`, Android 8+, ARM64.
- The repository already has a root Gradle project and separate product modules. The independent **`:tv` module is now implemented on this branch**; it is not present in published official releases.
- The branch started from commit `7b244bb81fadceb99eaf1f4d03190e3a027376da` (Vitrum 2.1.2 sources); Android TV shell is implemented here; real VPN transport is not available. Basic physical BOXY UI tests passed on 2026-11-13, with physical remote acceptance and VPN/network tests still outstanding.
- The existing repo security rules in `AGENTS.md` remain binding for any future source changes.

Target hardware reported by the maintainer:
- Android TV 11 (API 30), 2 GB RAM, 32-bit app ABI `armeabi-v7a`, active output 1920×1080.
- Hardware platform identified as HP44H / YYC, likely BOXY / Skyworth with Amlogic S905X4-family SoC.
- 16 GB storage and exact retail SKU are based partly on model identification; verify through device reporting before binding release requirements.
- Wider device compatibility and actual VPN performance **have not been established**.

## Decisions already made

- Product and code lineage are **independent**. No dependence on tablet-specific code, storage or published APKs.
- D-pad and Back are primary input; pointer/touch are optional.
- A running VPN process is never presented as proof of working internet or protection from leaks.
- Phone-assisted import is a user goal, but transfer protocol and trust model are gated on the pairing security review.
- No advertising, analytics, device fingerprinting, trackers, root requirement or credential-bearing logs.
- A developer-only `:tv:assembleDebug` APK is built by `.github/workflows/tv.yml` on a TV PR; no TV release tags or official signed APK are published.

## Official platform references

- [Android TV navigation](https://developer.android.com/training/tv/get-started/navigation)
- [Building TV apps and manifest requirements](https://developer.android.com/training/tv/get-started/create)
- [Adaptive apps for TV](https://developer.android.com/develop/adaptive-apps/guides/tv/build-adaptive-apps-for-tv)
- [Android VpnService](https://developer.android.com/develop/connectivity/vpn)

When implementing, verify latest platform guidance again and record precise SDK/API and device evidence.

## Stage 1 developer shell (source)

- Module: `:tv` (Java Android Views, separate codebase; no dependency on `:app` or `:lite`).
- Application ID: `app.rayclient.tv`; version `0.1.1-dev` (2); minSdk 26, targetSdk 35, ARM32 filter.
- Launcher: Android TV Leanback and a 320x180 branded banner. This build requests **no Internet or VPN permissions**, contains no `VpnService`, and cannot protect traffic.
- UI screens: Home, Servers, Apps, Connection, Settings. The offline-only server/connection states are explicit. The Apps page shows a read-only list of apps visible in the TV launcher, and Settings shows real local device facts and available memory. No VPN privileges, credential input, remote listener or arbitrary package discovery are added.
- Tests: `:tv:testDebugUnitTest` and `:tv:connectedDebugAndroidTest` (Android TV 11 emulator) are configured in [tv.yml](../../.github/workflows/tv.yml); the [2026-11-13 workflow](https://github.com/lolokeksu09/Vitrum/actions/runs/43363278312) passed both jobs. The x86-only emulator test does not establish physical ARM32 compatibility.
- The choice of Java Android Views is documented in [ADR-0001](ADR-0001-TV-UI.md), specific to 2 GB TV hardware. This is not a code or product dependency on Lite.

### Building locally

From the repository root with JDK 21 and SDK platform 37.0:

```bash
./gradlew :tv:testDebugUnitTest :tv:assembleDebug :tv:assembleDebugAndroidTest
```

Developer APK (debug-signed): `tv/build/outputs/apk/debug/tv-debug.apk`. Install only for UI evaluation. It must never be advertised as a working VPN client.

### Verified emulator screenshot

[Android TV 11 UI evidence (GitHub Actions artifact)](https://github.com/lolokeksu09/Vitrum/actions/runs/43363278312/artifacts/14009246654) — real 1920×1080 screenshot obtained **after** reinstalling the debug APK following instrumentation. Test result: 2 Android UI tests, zero failures. The subsequent [BOXY physical UI audit](BOXY_UI_AUDIT_2026-11-13.md) verified all five screens and ADB-injected D-pad on the real Android 11 ARM32 box. Physical remote hardware and VPN functionality remain untested.

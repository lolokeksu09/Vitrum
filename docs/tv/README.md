# Vitrum TV — project foundation

**Status:** design and technical planning only, 2026-11-12. This branch does **not** contain a TV application or a testable TV APK.

**Development branch:** `feature/vitrum-tv-foundation` in `lolokeksu09/Vitrum`. The release branch and published app versions are unaffected.

Vitrum TV is a **new, dedicated Android TV VPN application** designed for D-pad remotes, 10-foot viewing, low-memory streaming boxes and an independent Android application lifecycle. It is not a TV mode of the phone client and does not derive from the tablet-oriented edition. Its code, package identity, encrypted profile storage and VPN service must be separate.

## Documents

1. [PRODUCT.md](PRODUCT.md) — product goals, audience, MVP, non-goals and acceptance.
2. [ARCHITECTURE.md](ARCHITECTURE.md) — proposed standalone `:tv` architecture, 32-bit runtime, connection state and security invariants.
3. [DESIGN.md](DESIGN.md) — TV-first interaction model, focus navigation, screen structure, visual system and bilingual copy.
4. [PAIRING_SECURITY.md](PAIRING_SECURITY.md) — phone-to-TV import threat model, alternatives and decision gate.
5. [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) — sequenced milestones, real file targets, decision gates and deliverables.
6. [TESTING.md](TESTING.md) — emulators, BOXY hardware matrix, release and security evidence requirements.

## Evidence baseline

Repository facts on 2026-11-12:
- Existing main Android application is `:app`, `app.rayclient`, Android 8+, ARM64.
- The repository already has a root Gradle project and separate product modules. **No `:tv` module exists yet.**
- This branch starts from commit `7b244bb81fadceb99eaf1f4d03190e3a027376da` (Vitrum 2.1.2 sources). Product design documents are proposals, not working implementation.
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
- No builds, tags, release or APK publication as part of this foundation.

## Official platform references

- [Android TV navigation](https://developer.android.com/training/tv/get-started/navigation)
- [Building TV apps and manifest requirements](https://developer.android.com/training/tv/get-started/create)
- [Adaptive apps for TV](https://developer.android.com/develop/adaptive-apps/guides/tv/build-adaptive-apps-for-tv)
- [Android VpnService](https://developer.android.com/develop/connectivity/vpn)

When implementing, verify latest platform guidance again and record precise SDK/API and device evidence.

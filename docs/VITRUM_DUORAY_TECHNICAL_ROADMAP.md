# Vitrum — Technical Roadmap Review Against DUORAY-Mobile

> **Archived technical snapshot (2026-11-07).** PR statuses, branch references, and source SHAs below belonged to the historical audit. This is not a live roadmap. As of 2026-11-09, the repository uses `main` as its only branch and the current public primary release is Vitrum 2.0.0. See [HANDOFF](HANDOFF.md) and [v2.0.0 release notes](releases/v2.0.0.md).

## Historical status at the time of review
- PR #80 added an Xray runtime launch probe and Clone instrumentation/build wiring.
- PR #81 added partial Clone UI polish.
- PR #82 added network/Wi-Fi SSID server-selection policy.
- Clone CI passed compilation/test/signature gates for the recorded commit, but this did not prove full ARM64 runtime operation.
- Physical ARM64 Xray launch, production linker selection, end-to-end VPN traffic, and network-policy transitions were not confirmed by those PRs.

## Original objective

Develop Vitrum as an independent Android VPN client, using DUORAY-Mobile as a technical reference for Xray version management, complex JSON subscription compatibility, and network-aware server selection—**without rewriting the app in Rust/Slint or replacing its existing architecture**.

The engineering principle was to adopt verifiable technical ideas, not blindly transplant another app's implementation. Contracts and constraints should be documented first, followed by isolated changes and regression tests.

## Reviewed project baseline

The historical source snapshot recorded:
- Vitrum: Kotlin + Jetpack Compose, package `app.rayclient`, minSdk 26, ARM64.
- Vitrum Xray: version pinned in `Brand.kt` and `tools/FETCH_BINARIES.sh`, with SHA-256 verification.
- DUORAY-Mobile: Kotlin Android shell plus Rust/Slint; shared `duoray-core` used by desktop/mobile code.
- Xray-core and DUORAY version references were specific to the audit date and must not be treated as current dependency recommendations.

Key Vitrum files reviewed:
- `Brand.kt`: Xray version.
- `tools/FETCH_BINARIES.sh`: runtime binary and Geo data retrieval/checksum.
- `Native.kt` and `spawn.c`: native Xray launch.
- `RayVpnService.kt`: Android VpnService, TUN, Xray lifecycle, reconnect, failover, kill switch.
- `ConfigBuilder.kt`: Xray config generation and routing rules.
- `AutoSub.kt` and `AutoStore.kt`: JSON subscription mode and protected config storage.
- `Scenarios.kt`: Wi-Fi/mobile/roaming/open-Wi-Fi classification and server policy.
- `AppState.kt`: app state and settings.

## Architectural decisions and constraints

1. Retain the existing Android architecture: Kotlin, Jetpack Compose, RayVpnService, JNI, and the current Xray runtime. Rust/Slint migration and replacement of `app.rayclient` were out of scope.
2. Do not add `duoray-core` as a default dependency. Vitrum's implementation is independent; direct adoption would change the stack, data model, and compatibility boundaries.
3. Do not copy DUORAY source or text. Implement ideas independently and preserve the repository's policy.
4. Do not change package ID, signing key, storage format, or user data without a separate migration plan. Normal updates must remain installable over the official APK.
5. Do not weaken network protection for compatibility. Unknown or unsupported JSON must not silently bypass the VPN.
6. Keep network-core work separate from UI redesign. Do not change routing behavior as a side effect of visual work.

## Historical workstreams

- Xray runtime lifecycle and launch validation.
- JSON subscription parsing and compatibility.
- Server selection based on network type and Wi-Fi SSID.
- Clone instrumentation and UI build coverage.
- Explicit separation between CI evidence and physical-device VPN verification.

This document preserves the audit rationale and architectural constraints. It does not authorize new network behavior changes. Re-check current source, tests, and [HANDOFF](HANDOFF.md) before any implementation.

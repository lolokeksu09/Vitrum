# Vitrum TV — staged implementation plan

**Scope:** create a **new** standalone Android TV VPN app in a new module. Stage 1 TV UI code has been implemented in this feature branch. No VPN code or official TV release has been produced; debug APK compilation and Android TV 11 x86 emulator D-pad instrumentation passed in [run 43361654744](https://github.com/lolokeksu09/Vitrum/actions/runs/43361654744). Physical BOXY acceptance is still UNKNOWN.

**Repository base:** `main` at `7b244bb81fadceb99eaf1f4d03190e3a027376da` when the foundation branch was created. Refresh against upstream `main` before each implementation PR; do not rewrite unrelated product code or force-push.

## Stage 0 — documentation and architecture (this branch)

Deliver:
- `docs/tv/README.md`
- `docs/tv/PRODUCT.md`
- `docs/tv/ARCHITECTURE.md`
- `docs/tv/DESIGN.md`
- `docs/tv/PAIRING_SECURITY.md`
- `docs/tv/IMPLEMENTATION_PLAN.md`
- `docs/tv/TESTING.md`

Acceptance: all cross-links resolve, hardware evidence has source/uncertainty labels, only branch documentation changed, no claim that TV app or tests already exist.

## Stage 1 — independent Android TV shell (implemented; CI passed, BOXY acceptance pending)

Implemented source files (Java Views selected in ADR-0001):
- `settings.gradle.kts`: register `:tv`.
- `tv/build.gradle.kts`: independent package, Android SDK and ABI constraints, minimal dependencies.
- `tv/src/main/AndroidManifest.xml`: Leanback launcher, no touchscreen requirement, TV banner/icon, no network listener.
- `tv/src/main/java/app/rayclient/tv/TvActivity.java`: five native TV screens, explicit D-pad focus and truthful offline status; `TvNavigationModel.java` separates UI navigation.
- Russian/English TV strings, focused-control styling, icon and banner under `tv/src/main/res/`.
- `TvNavigationModelTest.java` and `TvActivityRemoteTest.java` under `tv/src/test` and `tv/src/androidTest`.

Deliverable: source of a **developer-only** TV UI shell with remote navigation and no false VPN status. `tv.yml` is configured to produce a debug APK and run Android TV 11 emulator D-pad checks. **BOXY install and actual remote screenshot remain outstanding.** CI run 43361654744 passed JVM tests, Debug APK packaging, TV manifest/no-network-permission checks and 2 emulator instrumentation tests (0 failures).

Gate: D-pad/Back, manifest, package identity and memory budget baseline work on API 30 / ARM32 device. Java Views has been selected and documented in ADR-0001; measure RAM/focus on BOXY and revisit only with a separate ADR.

## Stage 2 — real ARM32 VPN engine and state contract

Suggested paths:
- `tv/src/main/java/app/rayclient/tv/TvVpnService.kt`
- `tv/src/main/java/app/rayclient/tv/TvRuntimeManager.kt`
- `tv/src/main/java/app/rayclient/tv/TvConnectionCoordinator.kt`
- `tv/src/main/java/app/rayclient/tv/TvXrayConfig.kt`
- `tv/src/main/java/app/rayclient/tv/TvConnectionEvidence.kt`
- toolchain script under `tools/` with pinned Xray sources, SHA-256 and ABI/ELF checks, after build design review.

Deliverable: Xray executable for `armeabi-v7a`, Android `VpnService` integration, correct foreground notification, START/STOP/permission rejection/revocation states. Validate config via actual binary and physical device; no direct fallback route when protection was requested.

Gate: native process cleanup, repeated stop/start, system permission, physical traffic verification with IPv4 and DNS; UNKNOWN recorded for untested routing/IPv6 cases.

## Stage 3 — profile storage and server UX

Suggested paths:
- `TvProfileStore.kt`, `TvSubscriptionRepository.kt`, `TvSplitPolicy.kt`, TV server/settings screens and bounded parser tests.
- Create a real encrypted storage boundary using Android Keystore; no schema reuse from other app packages.

Deliverable: profile entry and preservation, HTTPS subscription refresh, server switching, safe full-list focus and app split routing with reconnect messaging.

Gate: 1/10/256 server records, failed-import rollback, input fuzz/negative cases, saved profiles survive app process death and Android reboot, no sensitive logs.

## Stage 4 — phone pairing

First publish a separate security ADR in `docs/tv/` resolving the choices from `PAIRING_SECURITY.md`. Only then implement transport, authorization screen, expiration, replay prevention and encrypted persistence.

Gate: two-device authenticated transfer, adversarial pairing tests and a real phone + BOXY usability run. No cloud service or new listening port without explicitly reviewed authorization.

## Stage 5 — background reliability and release readiness

- Assess platform **Always-on VPN** settings support on actual BOXY; never misrepresent compatibility.
- Test network changes, Wi-Fi/Ethernet, TV sleep/awake, app switching, revoked permission, Xray crash, empty app selection, large subscriptions and data retention.
- Maintain independent `.github/workflows/tv.yml`, introduced for Stage 1: unit tests, APK/manifest checks and emulator D-pad instrumentation. Extend with ABI/ELF/runtime checks once Xray exists. Do not attach TV workflows to phone/Lite official tags.
- Audit licensing, signing key provenance, update path, banner localization and release checklist. Pick a separate TV version/tag namespace after review; **none exists yet**.

## Dependency graph

```text
Stage 0 docs
  |
  v
Stage 1 TV shell + real remote focus
  |
  v
Stage 2 independently verified ARM32 VPN runtime
  |
  +----> Stage 3 encrypted storage + subscription + server flows
  |                 |
  |                 v
  |           Stage 4 secure phone pairing ADR + implementation
  |                 |
  +-----------------+
          |
          v
Stage 5 background + security + performance + release
```

UI prototyping of server states can start in parallel with Stage 2 **only** when explicitly labeled simulated and prevented from being shown as working VPN evidence.

## Tasks deliberately not authorized by this plan

- Modifying `:app` or `:lite`, their release tags, keystores, packages or real routing semantics.
- Changing root module structure outside registering a future standalone `:tv`.
- Publishing TV releases, selecting a signing identity, installing on user hardware or opening a companion-network port before implementation authorization and review.
- Replacing the chosen security model solely for convenience.

## Completion reporting

For each stage include: exact commit/PR, affected paths, tested artifact and hash, executed commands or CI run IDs, PASS/FAIL/UNKNOWN test matrix, physical-device model/firmware and material limitations. Never report “TV VPN works” after only Gradle build or emulator launch.

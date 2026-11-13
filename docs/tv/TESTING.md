# Vitrum TV — verification and device acceptance

**Status:** [GitHub Actions run 43363278312](https://github.com/lolokeksu09/Vitrum/actions/runs/43363278312) succeeded on the Stage 1 source commit `94ea5a758926c5fdfd1251453bb1e677fbec261c`. The unit tests, debug APK/TV manifest check and two Android TV 11 x86 emulator D-pad tests passed (2 tests, 0 failures, 0 errors). Physical BOXY **UI** checks now have partial real-device evidence: all five page screenshots and ADB-injected D-pad navigation passed on Android 11 ARM32; **physical remote input remains UNKNOWN**. All VPN/networking test cases remain **UNKNOWN / NOT IMPLEMENTED**. Details: [BOXY physical UI audit (2026-11-13)](BOXY_UI_AUDIT_2026-11-13.md). The x86 emulator's ABI filter was intentionally changed inside the CI job only; the distributable debug APK was built with an ARM32 filter.


## Physical BOXY UI evidence (2026-11-13)

- Android 11 / API 30, `armeabi-v7a`, 1920×1080 @ 320 density, model property `4K Android TV Box`.
- Installed debug package `app.rayclient.tv`, version `0.1.0-dev`, running on physical device.
- Five **actual device** screenshots: Home, Servers, Apps, Setup, Settings. All screens rendered and responded to **ADB-injected** D-pad actions as intended.
- Hardware remote input, 720p/4K/overscan, cold TV launcher entry, accessibility, thermal, and sleep/wake **remain UNKNOWN**.
- ADB Back returned from a secondary screen to Home; a prior ADB OK from Home opened Setup. These do **not** verify Android TV remote hardware.
- One `dumpsys meminfo` sample: TOTAL PSS 31,438 KiB, TOTAL RSS 120,192 KiB, TOTAL SWAP PSS 156 KiB (not a soak result).
- Foreground-application check is missing in the **Termux bridge**, so one blind navigation command acted on Google Play instead of Vitrum TV; this should not be misreported as a Vitrum UI failure.
- See [full real-device UI audit and private screenshot links](BOXY_UI_AUDIT_2026-11-13.md). No VPN engine, Xray runtime or TUN existed in this test APK.

## 1. Test hardware and evidence

**Primary device reported by maintainer:** HP44H / YYC Android TV box, believed BOXY / Skyworth; Android TV 11, API 30, 2 GB RAM, `armeabi-v7a`, display output currently 1920×1080, firmware C3.0.1 dated 2023-08-03. Model mapping and storage SKU require ADB confirmation if they are used for compatibility claims.

Required evidence for every physical run:
- `adb shell getprop ro.product.model`, `ro.build.version.sdk`, `ro.product.cpu.abilist` and `ro.build.fingerprint` **with sensitive IDs/redactions reviewed**.
- Source commit, installed APK package/version/signature/hash, actual output resolution, remote hardware type, time and network type.
- Step-by-step observed output, PASS / FAIL / UNKNOWN and redacted logs/screenshots.
- Do not publish serial numbers, tokens, server links, subscription endpoints, private IP addresses unnecessarily or raw Xray config.

Secondary device coverage and Android version support are to be decided after BOXY acceptance; do not claim all Android TV boxes are compatible.

## 2. CI vs hardware

| Evidence | CI/emulator | Real Android TV box |
|---|---|---|
| Kotlin and resource compilation | Yes | No substitute for install |
| Unit tests for parsers, policy, state | Yes | Optional regression confirmation |
| TV manifest / Leanback launcher metadata | Static check + emulator | Launcher visibility required |
| D-pad focus semantics | Emulator input events | Physical remote acceptance required |
| ARM32 native ELF compatibility | ELF/ABI inspection | Native runtime start required |
| Native process/TUN and routing | Not proven by x86 emulator | VPN connection and packet route required |
| DNS/IPv4/IPv6 leak/lockdown | Cannot infer from build | Real tests with test endpoint and packet evidence |
| Sleep, power, RAM and thermal | CI insufficient | Measure device |
| Update data retention | CI may inspect metadata | Install over signed prior TV test build |

Use a verified Xray test server owned by the maintainer. Keep secrets out of logs and screen captures.

## 3. Remote-only interaction matrix

| ID | Test | Pass condition |
|---|---|---|
| TV-UI-01 | Cold launch with no profile | UI opens from TV launcher, initial focus is Add profile, no keyboard |
| TV-UI-02 | D-pad traversal | Every actionable control reached with D-pad, OK activates once, no mouse |
| TV-UI-03 | Back semantics | Dialog -> screen -> launcher with VPN state unchanged |
| TV-UI-04 | Permission dialog | Real TV remote can grant/deny; denial restores focus and yields clear action |
| TV-UI-05 | Servers (1, 10, 256 items) | Focus stays visible, list scrolls to row; selection distinct from connected |
| TV-UI-06 | Lists and dialogs | No focus trap on app selection, prompts, errors, long localized labels |
| TV-UI-07 | 1080p + 4K (if offered) | No overscan clipping, critical text accessible; document unavailable 4K |
| TV-UI-08 | Large font / screen-reader options | Main status and action readable, focused semantics exposed |
| TV-UI-09 | Navigate away to media app | UI task leaving does not intentionally terminate active VPN |

## 4. VPN and privacy matrix

| ID | Scenario | Pass condition |
|---|---|---|
| TV-VPN-01 | First-run, permission granted | TUN + Xray start correctly; app reports only evidenced state |
| TV-VPN-02 | Permission denied/revoked | No false connected label; native process and FD released |
| TV-VPN-03 | Rapid START/STOP/START | Exactly one intended session, no stale TUN, orphan child or status |
| TV-VPN-04 | Core fails after TUN establishment | State transitions to error/recovery; direct bypass not silently introduced |
| TV-VPN-05 | Switch server during connection | Active/selected labels truthful, rollback on failure |
| TV-VPN-06 | IPv4 | Test app and real endpoint produce route/IP evidence through VPN |
| TV-VPN-07 | IPv6 | Test when firmware/network provides IPv6; otherwise UNKNOWN, never PASS |
| TV-VPN-08 | DNS incl. Android Private DNS | Domain queries, permitted DNS modes, DoH and direct bypass inspected |
| TV-VPN-09 | ALL / ONLY / EXCLUDE | Selected and excluded apps take expected path; invalid ONLY fails closed |
| TV-VPN-10 | Wi-Fi/Ethernet transitions | Bounded recovery or explicit loss/failure state, no fabricated guarantee |
| TV-VPN-11 | Sleep/awake and reboot | System startup/recovery behavior recorded; distinguish Always-on settings |
| TV-VPN-12 | 20-minute operation | RAM, CPU and temperature measurements recorded (no preset pass claim) |
| TV-VPN-13 | Connection lost | Home/notification do not label data protected without evidence |
| TV-VPN-14 | Upgrade after first signed TV build | Encrypted profiles persist with matching signing identity |

**Bypass note:** ONLY/EXCLUDE routing modes intentionally permit some applications to use direct networking. Do not classify this expected behavior as a leak; clearly label the selected policy and test the apps it promises to route.

## 5. Provisioning and threat tests

| ID | Test | Pass condition |
|---|---|---|
| TV-SEC-01 | Invalid share link / subscription | Cannot crash, cannot overwrite valid encrypted profile |
| TV-SEC-02 | HTTPS downgrade / invalid certificate | Rejected; hostname validation never disabled |
| TV-SEC-03 | Oversized/fuzzed payload | Bounded memory/parse time; safe error |
| TV-SEC-04 | Lost Keystore key | Preserve undecryptable bytes; do not silently erase valid old state |
| TV-SEC-05 | Log and diagnostics scan | No raw server credentials, URLs, subscription tokens or keys |
| TV-SEC-06 | Expired/replayed pairing QR | Pairing fails, no reused token |
| TV-SEC-07 | Rogue LAN device / conflicting phone | Only TV-authorized, correctly authenticated sender can commit |
| TV-SEC-08 | Pairing UI close/restart | Listener/transfer ends; secrets and session are invalidated |
| TV-SEC-09 | Split policy empty ONLY | No silent expansion from a subset to all apps |

Pairing test cases are blocked until the protocol ADR is approved; do not mark them skipped as passed.

## 6. Build/release gates

- `./gradlew :tv:testDebugUnitTest :tv:assembleDebug :tv:assembleDebugAndroidTest` — now configured for the independent `:tv` module. GitHub Actions exercises it on the feature branch's PR.
- ABI, `minSdk`, `targetSdk`, package ID, TV manifest metadata and release certificate are checked with real build tools.
- An ARM32 ELF check must establish an Android-compatible executable, but **does not** establish functional TUN/real traffic.
- Build pipeline reports uploaded artifact SHA-256, verified source/ref, test results and exact signing identity.
- Release signing keys never appear in code, CI logs, issues or docs.
- A **debug-signed developer-only TV shell APK** may be produced for UI tests before physical acceptance. No **official TV release APK** or release tag before independent physical BOXY acceptance and review.

## 7. Decision on release readiness

A release may be discussed as a candidate when CI and basic launcher/focus tests pass. A **stable** TV VPN release requires explicit PASS evidence for permission, real tunneled traffic, DNS, server switch, STOP/START and background behavior. Untestable IPv6/Always-on conditions must be disclosed as UNKNOWN rather than claimed working.

See [Android TV navigation](https://developer.android.com/training/tv/get-started/navigation), [TV manifest requirements](https://developer.android.com/training/tv/get-started/create), [VpnService](https://developer.android.com/develop/connectivity/vpn) for platform contracts.

## Stage 1 CI evidence (2026-11-13)

- [Run 43363278312](https://github.com/lolokeksu09/Vitrum/actions/runs/43363278312): both TV build and Android 11 x86 TV emulator jobs PASS.
- The final x86 emulator report: 2 tests / 0 failures / 0 errors, including D-pad OK/Back and truthful offline status.
- The CI APK build is independently configured with `armeabi-v7a`; the emulator's `x86` override is temporary to its job only. The APK has no Xray/native runtime in this stage.
- [Actual UI screenshot](https://github.com/lolokeksu09/Vitrum/actions/runs/43363278312/artifacts/14009246654) is captured after a post-test APK reinstall, avoiding an earlier launcher-only screenshot. Physical BOXY remains UNKNOWN.

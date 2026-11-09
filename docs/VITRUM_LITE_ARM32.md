# Vitrum Lite ARM32 — Samsung Galaxy Tab S 10.5 LTE

> **Current status (2026-11-09):** `lite-v1.0.0` is published as a normal GitHub Release (not a pre-release). Lite remains a separate package, `app.rayclient.lite`, and does not update the main app. `.github/workflows/lite.yml` builds the ARM32 APK and uploads it as an Actions artifact; it does not publish or overwrite GitHub Releases. `.github/workflows/lite-ui-emulator.yml` runs UI smoke tests on an x86 emulator only; it is not an ARM32/Xray runtime test. Historical sections record Lite development. Manual matrices are checklists, not passed tests, unless actual results are recorded.

## Purpose

A third independent Android package, `app.rayclient.lite`, for SM-T805 (Exynos 5420, ARMv7, 3 GB RAM) on Android 6.0.1 (API 23). It is **not** a reduced visual mode of the main APK; it is built separately from source. The main app and Clone are not modified by the Lite build.

## Retained functionality
- Add single VLESS, VMess, Trojan, Shadowsocks, and Hysteria2 links; supported transport parameters depend on the format and Xray version.
- HTTPS subscriptions: plain-text or Base64 lists of supported protocols, select one server before connecting, response limit 1 MiB and 256 records.
- Paste from clipboard; clear error and permission-denial messages.
- System VPN permission prompt, TUN, separate VPN service, and notification.
- Connect/Disconnect controls and visible errors.
- Loop protection: the app's own UID is excluded from the VPN using `VpnService.Builder.addDisallowedApplication`.

## Removed from Lite
- Jetpack Compose/Material3, animations, 300dp button, visual effects, and complex tabs.
- Geo databases, regional bypass, game telemetry, and continuous long-running measurements.
- Background subscription refresh, QR scanner, root tools, and complex routing rules.
- Background subscription auto-loading and persistent full server catalog (only one selected server and the subscription URL are retained in Keystore).

## Architecture
- Java and standard Android Views; no UI framework dependencies.
- Separate `:lite` Gradle module: API 23, ARMv7, package `app.rayclient.lite`.
- Xray v26.3.27 cross-compiled from the upstream tag using `GOOS=android GOARCH=arm GOARM=7` and NDK r27c. No Linux executable is repackaged as an Android binary.
- `libspawn.so` is cross-compiled for the same 32-bit architecture; execution uses the app-private native library location.

## Acceptance criteria

CI checks the Go build, JNI, ELF32 ARM DYN/PIE, minSdk 23, single ABI, APK signature, Gradle compilation, and JUnit tests for START/STOP cancellation, subscription parsing, and HTTPS restrictions. This does **not** prove that Xray works on Android 6.

Physical SM-T805 testing must cover:
1. APK installation, launch, and screen rotation.
2. Single-link and HTTPS-subscription imports for VLESS/VMess/Trojan/Shadowsocks/Hysteria2 (plain/Base64); clear rejection of unsupported formats and parameters.
3. Rapid START → STOP → START sequences, disconnect during DNS lookup, TUN creation, and Xray startup; no child process remains after stop.
4. System VPN dialog, Xray startup, real IPv4/IPv6 access, DNS, and leak behavior.
5. Disconnect, revoked VPN permission, Xray crash, and clean shutdown.
6. Temperature, RAM, and load over 15–20 minutes.
7. Signature update behavior: one-off debug keys are incompatible with an existing installation.

**Limitation:** Android 6.0.1 has old security patches; a successful VPN session does not fix OS vulnerabilities.

## Build the APK

On Linux with Android SDK 35, NDK 27.2.12479018, Go 1.26, and JDK 21:

```bash
bash tools/build-lite-arm32.sh
./gradlew :lite:testDebugUnitTest :lite:assembleDebug
bash tools/verify-lite-apk.sh lite/build/outputs/apk/debug/lite-debug.apk
```

GitHub Actions uploads the result as an artifact. The current `lite.yml` does not create a pre-release or modify the published `lite-v1.0.0`; download the official APK from GitHub Releases.

## Format and privacy boundaries
- Subscriptions are downloaded **only over HTTPS**; HTTP downgrade is prohibited and timeouts are bounded. A subscription URL may contain an access token—never publish it in Issues or logs.
- Supported formats: plain/Base64 lists of VLESS, VMess, Trojan, Shadowsocks, and Hysteria2. Clash YAML and arbitrary JSON panels are not parsed. Shadowsocks plugins are unsupported.
- After saving, the subscription URL is hidden from the home screen. The source URL and selected server are stored only in encrypted form through Android Keystore. The full server list is not cached across launches. While connected, Xray receives a temporary private config file that is removed during normal shutdown.
- Android 6 is obsolete; test builds are not recommended for sensitive traffic until DNS and IPv4/IPv6 protection have been verified on a real device.

## Android 6 HTTPS subscriptions and certificates (Lite 0.2.1)

The client uses standard hostname verification and full X.509 chain validation. On Android 6, **only** the official ISRG Root X1 certificate is added alongside system roots; its SHA-256 is pinned and checked at startup. Source: [Let's Encrypt official certificate](https://github.com/letsencrypt/website/blob/main/static/certs/isrgrootx1.pem). TLS and `HostnameVerifier` checks are never disabled. Other certificate authorities still require a trusted chain and a compatible TLS protocol.

Errors are distinguished as DNS, timeout, connection refusal, certificate validation, TLS protocol, and HTTP status. Subscription URLs and tokens are not shown in error messages. If HTTPS fails, check tablet date/time and Wi-Fi, then the certificate and server TLS 1.2 support.

This build does not prove compatibility with a particular provider. The owner must test a real HTTPS subscription on the SM-T805 without publishing its URL or token.

## Protocol support expansion (test build)

Lite accepts single servers and plain/Base64 HTTPS subscriptions containing VLESS, VMess (Base64 JSON), Trojan, Shadowsocks (SIP002 or legacy Base64 without plugins), and Hysteria2. TLS/Reality certificate checks and required keys remain enforced. VLESS/VMess/Trojan may use TCP, WS, gRPC, HTTPUpgrade, and XHTTP when supported by the specific Xray version. Unknown transports are rejected rather than silently replaced with TCP. Actual server connectivity must be tested on the SM-T805; successful config validation does not mean a server is reachable.

## Per-app split tunneling

Android 6 supports `VpnService.Builder.addAllowedApplication` and `addDisallowedApplication`. Lite provides three modes: all app traffic through VPN; selected apps only (all others go **direct**, outside VPN); or all apps except selected ones (selected apps go **direct**). Installed apps are selected from the system list and the choice is stored locally; server links are not stored there. A change applies on the **next** TUN establishment, so stop and reconnect VPN after changing it. Lite excludes its own app from routing to prevent recursive TUN. "Selected only" with an empty list or removed apps is rejected; Lite must not silently route the entire device through VPN. These settings do not replace system lockdown/always-on. Real Android 6 traffic and leak behavior require physical-device verification.

## Android 6 manual matrix for 0.3.0

- [ ] VMess, Shadowsocks, and Hysteria2: at least one valid server for each protocol, traffic through TUN, no Xray crash.
- [ ] TLS, Reality, WS, gRPC, HTTPUpgrade, and XHTTP: applicability depends on server parameters; verify the variants actually used.
- [ ] ALL: browser and DNS use VPN, not direct routing.
- [ ] ONLY: selected browser uses VPN; a non-selected app goes direct; an empty/removed list must not start VPN for the entire device.
- [ ] EXCLUDE: selected app goes direct; all others use VPN.
- [ ] Changing modes while VPN is active requires reconnect; the UI explains this.
- [ ] STOP/START, revoked VPN permission, IPv4/IPv6 and DNS checks, and no traffic leak when the core crashes.
- [ ] RAM, battery, and temperature on Samsung Exynos 5420 over 15–20 minutes.

This is a test checklist, not a claim that the checks passed. ONLY/EXCLUDE intentionally allow selected apps to connect directly. Never publish subscription URLs or passwords in test reports.

## Lite 0.4.0 — connection state and server latency

- When VPN is active, the home screen shows "VPN connected", selected server, and connection duration. The notification also shows state, but **a running Xray process and existing TUN do not prove internet or DNS reachability**.
- After an HTTPS subscription is loaded, the first four servers appear on the home screen. The full list opens separately; choosing a server does not rewrite the subscription URL.
- TCP servers support asynchronous measurement of **TCP connection establishment time**: three background workers, 1.2 s timeout, first 24 servers automatically, remaining servers only on explicit request. This is **not ICMP ping**, TLS/Reality verification, or VPN speed measurement.
- Hysteria2/UDP does not show TCP latency; reporting "unreachable" would be misleading. DNS/port failures are shown as "no response", without subscription URLs or keys.
- Measurements run only when VPN is inactive; pre-connection TCP requests go directly and expose server-address lookups to the local network. Previously measured values may be viewed while VPN is active.
- Subscription lists and source URLs are not persisted across launches. While VPN is running, Xray config (including server credentials) is temporarily stored in the app-private directory and removed on shutdown. Server labels use short names only.
- SM-T805 test priorities: visual state after connect/STOP/crash, server switching, notification state, long names, TCP checks and UDP labels, rotation, and performance with a 256-server subscription.

All new features require physical Android 6 verification; CI confirms only builds and unit tests.

## Lite 0.5.0 — saved profile and reliable shutdown

- Android Keystore (API 23+, AES-256-GCM) encrypts the original HTTPS subscription or single server and the selected server from a subscription. SharedPreferences contains only the encrypted blob. Android Auto Backup is disabled. Reinstalling/changing signing identity loses this data; keep the original link separately.
- After saving, the secret URL is removed from the home screen. Only a short server name and actions remain: Connect, Replace link, Delete profile, and Refresh server list.
- After restart, the last selected server can connect without downloading the subscription again; the list can be refreshed manually. The in-memory list does not survive Activity closure, but the encrypted selected profile does.
- Disconnect remains available even if UI state is restored incorrectly. STOP is sent through an explicit Service Intent, cancels the previous START generation, waits for SIGTERM/SIGKILL, and releases TUN. `stopSelfResult` associates shutdown with a specific startId so it does not kill a newer START.
- Server selection does not automatically open over the keyboard. The first four servers are visible on Home; remaining servers are in a separate list. Connection controls sit directly below status.
- Real DNS/IPv4/IPv6, shutdown with a hung Xray process, and performance require testing on Samsung SM-T805. Unit tests/builds do not prove VPN traffic works.

## Lite 1.0.0 — published release

- Version 1.0.0 applies only to the separate `app.rayclient.lite` package, not the main Vitrum app.
- The main card displays the installed version from PackageManager; operation errors/notifications do not disappear behind an active VPN status.
- When another server is selected during an active VPN session, the new server is saved but is not presented as already connected. Disconnect the current session and tap Connect.
- The published release is listed as `lite-v1.0.0`; use GitHub Releases for the official APK.

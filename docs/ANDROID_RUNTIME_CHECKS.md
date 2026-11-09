# VITRUM — Android Runtime Regression Matrix

This is a manual verification checklist, **not evidence that tests passed**. Do not mark an item as passed without device evidence.

Target: Android 14, 15, and 16; ARM64; record the verified APK hash and source commit.

| Scenario | Action | Expected result |
|---|---|---|
| Foreground-service rejection | Restrict service launch using device-supported testing tools | No Xray after rejection; visible error and stopped service |
| Background startup | Connect from tile, widget, schedule, and boot where supported | VPN starts or clearly refuses; no crash |
| Network changes | Switch Wi-Fi ↔ mobile while traffic is active | Reconnect; kill switch prevents direct traffic |
| Xray crash | Terminate Xray child in a controlled test | Recover or keep traffic blocked when kill switch is enabled |
| VPN permission revoked | Revoke VPN in system Settings | Cleanup, UI, and notification remain consistent |
| Always-on and lockdown | Enable Android Always-on and block traffic without VPN; restart | System policy is honored; excluded apps are checked separately |
| AutoStore storage failure | Fail storage/Keystore write in instrumentation | Previous list/selected server remains intact; error shown |
| Corrupt JSON cache | Corrupt test-only `autosub.dat` and restart | File is not overwritten; no silent data loss |
| Backup over 8 MB | Select oversized `.vitrumbak` | Rejected with bounded reading |
| Maintainer-signed update | Check available official release | Only maintainer-signed APK is offered |
| Debug update | Check from ephemeral debug-fallback APK | No incompatible in-place update is offered |
| Accessibility | Home, Servers, Routing, Shield, Settings at font scales 1.0/1.3/1.5, TalkBack, dark theme | Controls identifiable and not clipped |
| Short home height | Rotate/use a short window while connected and disconnected | Connect, status, server card, and controls remain reachable by scrolling |
| Scaled UI text | Set font scale 1.3 and 1.5 | Group help, segmented controls, floating tabs, and all controls visible; touch targets >=48 dp |
| Empty server results | Add only a test server, search for nonsense, activate favorites | Clear empty-state explanation and reset; selection/favorites unchanged |
| Routing bypass warning | Enable selected-only/excluded apps and/or an explicit direct rule | UI warns about possible bypass; no unexpected route changes |
| Unknown Shield evidence | Run with and without network self-check | Unknown is labeled; never presented as verified protection |
| Settings search | Search DNS, Protection, Root, and nonexistent words | Accurate filtering, useful empty state, reset, and Back navigation |
| Diagnostics privacy | Run diagnostics, navigate away, reopen result | Result remains visible and warns before copy |
| Two-app isolation | Install official and Clone on one phone | Both icons available, distinct settings, at most one active VPN |
| Clone replacement | Install new ephemeral-signed Clone over older Clone | Android rejects update if signature differs; back up Clone before uninstall |

Record Android version, device, APK checksum, commit, reproducible steps, expected/actual behavior, and redacted logcat. Never include credentials or subscription URLs.

## Issue #40 — Manual DNS / IPv4 / IPv6 verification on ARM64

This is a **test plan**, not a test result. An HTTP check through the internal Xray service socket in Shield does not prove that a browser or other app uses Android TUN. The VITRUM app itself is excluded from TUN to prevent a VPN loop.

1. Install the test Clone APK. Record Android/firmware, commit SHA, APK SHA-256, date, and network type (Wi-Fi or mobile). Do not record keys, server links, or raw queried domains.
2. Select a profile with "Apps → All" and "Other traffic → Via VPN". **Explicitly verify** that no Direct rules, Russia/LAN bypass, or test-browser exclusions are active; document any required exclusions.
3. Connect VPN. In Privacy Shield, manually tap "Check IPv4 / IPv6". Record states and response times only. A successful IPv4/IPv6 check means an **HTTP response through the Xray service socket**, not absence of leaks. Failure or timeout means UNKNOWN, not "IPv6 blocked".
4. In a **different browser included in Android VPN**, open a public DNS leak test and IPv4/IPv6 connectivity test. Record supported address families and visible network/resolvers without full IP addresses or personal DNS names. Compare resolvers with expected provider and configured DNS/DoH settings. This is manual observation, not an automated VITRUM assertion.
5. Repeat with "Do not use IPv6" enabled and disabled. IPv6 may be unavailable when enabled, but this alone does not prove that traffic cannot bypass TUN. Repeat on Wi-Fi and mobile, including transitions.
6. Repeat with the browser excluded from VPN and with system Always-on/lockdown. Direct traffic from excluded apps is **expected** if Android permits it; do not label it a leak without accounting for the mode. Test lockdown separately.
7. STOP/reconnect/network loss: stale Shield results must disappear. With system blocking enabled, verify in an external browser that direct access is unavailable during stop or tunnel loss.

For every scenario, report PASS/FAIL/UNKNOWN, observed browser route, app/rule settings, and exactly what was tested. A successful Unix-socket response cannot justify "DNS/IPv4/IPv6 leak-free". No automatic telemetry or background requests.

## Manual server switch A → B without disconnecting VPN (PR #56)

Plan for ARM64 Android 16; do not mark PASS until the test is actually performed.

| Scenario | Action | Expected result |
|---|---|---|
| Normal switch | Connect to A, tap B in Servers | TUN remains open; state shows Switching; B becomes selected only after HTTP response through its Xray Unix socket |
| Switch back | Switch from B to A | One active Xray PID; displayed name matches the verified route |
| Unresponsive server | While on A, select a known-unresponsive test B | B is not saved as selected; A is restored; B is never shown as Connected |
| Rollback failure | Simulate preparation/start failure for both A and B | VPN TUN remains open, Xray stopped, `blocked=true`; no leak-free claim without separate test |
| STOP during switch | Tap STOP before DNS/HTTP check of the new target completes | Old watcher does not restart Xray or change selectedId after STOP |
| Rapid taps | Tap B, C, D quickly | At most one switch at a time; selected ID matches the actually verified target |
| Network and IPv6 | Repeat Wi-Fi↔LTE, toggle IPv6, and test an external browser under TUN | Record each address family separately; DNS/IPv6 remain UNKNOWN without observations |
| Lockdown | Enable system blocking without VPN | No external traffic during Xray stop window; verify with a browser included in VPN |

Record APK SHA, Android version, scenario, observation, and PASS/FAIL/UNKNOWN. Do not attach raw server links, keys, or personal DNS names. Emulator CI does not prove real-device routing safety.

## v2.3.0 — automatic recovery (not device-tested)

- Enable automatic switching. When A fails, provide an unresponsive B and working C; C is selected only after a response. Also test a candidate whose Xray process fails to start.
- If all candidates fail but A becomes reachable again, A is restored and a rollback event is recorded; successful-switch count does not increase.
- If A and all candidates are unavailable, TUN remains and traffic is blocked. Repeat with the app kill switch enabled and disabled. Observe external IPv4/IPv6/DNS, not only UI state.
- STOP and STOP→START during candidate preparation, HTTP check, and rollback must prevent old watchers from changing selection or stopping a new Xray process. Manual switching during auto-failover is rejected.
- Repeat with Wi-Fi↔LTE, brief network loss, and Always-on/lockdown. After successful rollback, test a subsequent auto-failover and manual server switch.

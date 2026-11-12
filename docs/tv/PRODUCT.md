# Vitrum TV — product specification

**Status:** proposed baseline, design-only. **Target:** Android TV 11 / API 30 on 32-bit ARM devices with 2 GB RAM. Broader support is a future validation task.

## Product thesis

A VPN client for a television should let a viewer **connect, see whether it is actually working, change server and return to content using only a D-pad remote**. Setup should minimize typing, never require an air mouse and not lose VPN state merely because the user leaves the app.

## Audience and primary use

- A person using a television from several meters away, mostly with Left/Right/Up/Down, OK and Back.
- A home streaming box with 2 GB RAM and no touchscreen, occasionally using Wi-Fi or Ethernet.
- A person with their own compatible VPN server/share link or subscription. Vitrum TV does not sell or supply VPN servers.
- Locale: Russian first, English complete; no user-facing raw exception messages.

### Jobs to be done

1. Open Vitrum TV, know the **actual** connection state and connect in a small number of D-pad actions.
2. Add a profile without typing a long credential-bearing URL with the remote.
3. Select or switch servers safely, recover from a failed connection, and explicitly disconnect.
4. Decide which installed apps use the VPN and understand that intentionally excluded apps use direct networking.
5. Optionally enable **system-managed Always-on VPN** if the firmware offers the setting, with a clear explanation of what it does and does not guarantee.

## First-release scope (MVP)

| Area | Required behavior | Acceptance evidence |
|---|---|---|
| TV entry | Separate launcher icon and TV banner, no touchscreen requirement, usable landscape layout | Visible on target TV launcher and launchable via OK |
| Navigation | Every visible interactive item accessible by D-pad; initial focus, visible focus and Back predictable | Recorded remote-only path for every screen/state |
| Connection | Import or select a valid server; obtain system VPN permission; connect/disconnect via VpnService | Physical-device traffic and lifecycle checks |
| Profile security | Secrets encrypted at rest; no tokens in normal labels, QR contents or logs | Negative tests and source review |
| Server selection | Stable focus for long lists, selected server distinguished from live connected server | 1, 10 and 256-item navigation exercises |
| Status | Separate service/TUN state, real connection diagnostics and unverified network behavior | Status-to-evidence table and fake-success negative tests |
| Background | A normal switch to streaming apps must not intentionally tear down VPN | BOXY reproduction across app switching and sleep |
| App split | Clear semantics for ALL / ONLY / EXCLUDE with safe handling of missing or empty selections | Positive and negative per-app traffic tests |
| Errors | DNS, TLS, permission, subscription and service failures have actionable text | Failure injection and D-pad recovery tests |

**Import UX goal:** phone-assisted secure transfer using a TV-presented QR/code, plus an accessible remote-only fallback. The wire protocol is **not approved** until [PAIRING_SECURITY.md](PAIRING_SECURITY.md) has a reviewed design and prototype. An implementation must never put raw server credentials in the on-screen QR.

## Non-goals for the initial release

- Reusing the phone UI by scaling it to a TV; providing full phone settings as-is.
- Using the tablet edition as a base, depending on its code, installing over its package or sharing its encrypted preferences.
- A VPN service hosted by Vitrum, account registration, ads, telemetry or external tracking.
- Root-only features, firmware modification, unsupported Android platform bypasses.
- Marketing claims of absolute DNS/IPv4/IPv6 leak freedom, or claiming transport health from a launched Xray process alone.
- Animation-heavy backgrounds, continuous speed tests, 3D graphics or an always-active pairing web server.
- Automatic stable release before acceptance on real ARM32 Android TV hardware.

## Behavior priorities

**Critical:** connection correctness, absence of deceptive status, predictable input focus, safe credential lifecycle, explicit errors.

**Next:** secure phone-assisted provisioning, per-app routing UX, system-managed Always-on, recovery through network transitions.

**Later:** multi-profile favorites, shortcuts and convenience telemetry confined to local device, only after memory/performance testing.

## State vocabulary (no invented success)

- **No profile:** no usable saved connection; action Add profile.
- **Disconnected:** known profile but service inactive.
- **Permission required:** Android system consent needed; preserve context after dialog.
- **Connecting:** TUN/Xray operations in progress, not proof of traffic.
- **Connected (service):** service reports established TUN and runtime; additional network validation is separate.
- **Checking connection:** explicitly running a bounded diagnostic.
- **Reachability verified (checked path):** positive result limited to the tested route/target/time, **not** universal leak protection.
- **Reconnecting:** recovery in progress; surface whether direct traffic is blocked or still possible.
- **Error:** meaningful summary, retry and details without secrets.
- **Stopping:** temporary state, never allow overlapping start/stop to misrepresent current server.

## Independence contract

A dedicated module (provisionally named `:tv`) and a unique applicationId (candidate `app.rayclient.tv`) must be reviewed before implementation. The target is a **new TV product** rather than a renamed APK. No existing Android package or signing identity may be repurposed or silently replaced.

The initial product is not ready for a release until [TESTING.md](TESTING.md) requirements are recorded as PASS/FAIL/UNKNOWN with evidence.

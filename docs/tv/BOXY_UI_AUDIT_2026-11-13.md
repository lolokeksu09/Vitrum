# Vitrum TV — physical BOXY UI audit (2026-11-13)

**Scope:** Stage 1 `app.rayclient.tv` `0.1.0-dev` on a physical `4K Android TV Box` via a private, locally operated GitHub-Issues → Termux → ADB diagnostic relay. This is a hardware **UI audit**, **not** a VPN acceptance test.

**Source branch:** `feature/vitrum-tv-foundation`. **Production modules `:app` and `:lite` were not modified.**

## Device evidence

| Item | Evidence |
|---|---|
| Reported Android model | `4K Android TV Box` |
| OS | Android 11 / API 30 |
| ABI | `armeabi-v7a,armeabi` (32-bit) |
| Display | Physical size: 1920×1080; density: 320 |
| Security patch | 2023-07-05 |
| App | `app.rayclient.tv` / `0.1.0-dev` / versionCode 1 |
| Process | Running during the inspection |
| One `dumpsys meminfo` sample | TOTAL PSS 31,438 KiB (~30.7 MiB); TOTAL RSS 120,192 KiB (~117.4 MiB); TOTAL SWAP PSS 156 KiB |
| Physical remote | **NOT tested**: D-pad events were injected via ADB |
| VPN/network routing | **NOT implemented; NOT tested** |

Device metadata: private bridge issues [#1](https://github.com/lolokeksu09/vitrum-tv-bridge/issues/1), [#5](https://github.com/lolokeksu09/vitrum-tv-bridge/issues/5), [#6](https://github.com/lolokeksu09/vitrum-tv-bridge/issues/6), [#38](https://github.com/lolokeksu09/vitrum-tv-bridge/issues/38). Private issue evidence requires access to the bridge repository.

## Five real screenshots

| Page | Evidence | Visual outcome |
|---|---|---|
| Home / Главная | [Physical screenshot #20](https://github.com/lolokeksu09/vitrum-tv-bridge/blob/main/captures/issue-20.png) | Visible `VPN не подключён` and clear disclaimer; large focused primary action |
| Servers / Серверы | [Physical screenshot #24](https://github.com/lolokeksu09/vitrum-tv-bridge/blob/main/captures/issue-24.png) | Page and nav selection correct; placeholder, no server profiles |
| Apps / Приложения | [Physical screenshot #28](https://github.com/lolokeksu09/vitrum-tv-bridge/blob/main/captures/issue-28.png) | Page and nav selection correct; split tunneling not implemented |
| Setup / Настройка | [Physical screenshot #32](https://github.com/lolokeksu09/vitrum-tv-bridge/blob/main/captures/issue-32.png) | Page and nav selection correct; pairing/import not implemented |
| Settings / Параметры | [Physical screenshot #36](https://github.com/lolokeksu09/vitrum-tv-bridge/blob/main/captures/issue-36.png) | Page and nav selection correct; version shown, no actionable settings |

Screenshots are **real screenshots from the physical box**, not generated mockups or emulator images, and may persist in the private bridge repository's Git history.

### Test execution

The relay sent the hard-coded D-pad sequence LEFT → DOWN → OK after a confirmed Vitrum TV Home screenshot. The same sequence was repeated successively for each of the four additional pages; a PNG was retrieved and visually inspected after each step. ADB BACK subsequently returned to Home ([private bridge request #37](https://github.com/lolokeksu09/vitrum-tv-bridge/issues/37)). Earlier on this physical box, an OK action from Home opened Setup and BACK returned to Home, confirmed by physical screenshots [#12](https://github.com/lolokeksu09/vitrum-tv-bridge/blob/main/captures/issue-12.png) and [#14](https://github.com/lolokeksu09/vitrum-tv-bridge/blob/main/captures/issue-14.png).

An initial unguarded remote-key sequence produced a screenshot of **Google Play**, not Vitrum TV ([capture #18](https://github.com/lolokeksu09/vitrum-tv-bridge/blob/main/captures/issue-18.png)). This was **a bridge precondition failure**: it did not verify the foreground application. Re-launching Vitrum TV before navigation restored valid, reproducible screenshots. Do not classify that image as a Vitrum TV screen or as a proven application crash.

## Findings / prioritized action list

| Priority | Finding | Evidence | Corrective direction |
|---|---|---|---|
| P0 — release blocker | All five screens are Stage 1 **UI-only**. There is no working VPN engine, Android VpnService/TUN verification, server import, per-app policy or secure phone pairing. | Honest copy in all five screenshots; independent TV source and manifest | Complete Stage 2/3/4 prerequisites and pass real packet, DNS, IPv4 and permission/denial tests before any VPN release claim |
| P1 — UX | Four secondary pages use essentially the **same empty-state layout**. Descriptions are duplicated both under the heading and within the large central card. | Server #24, Apps #28, Setup #32, Settings #36 | Use concise, distinct, actionable empty states; avoid verbatim duplicate copy |
| P1 — IA | `Настройка` (provisioning/pairing) and `Параметры` (application settings) can be confused. Settings currently explains module independence rather than showing settings. | #32 and #36 | Consider explicit `Подключение` or `Добавить профиль` naming for provisioning; reserve `Параметры` for actual settings |
| P1 — navigation/CTA | Several primary actions only return to Home even though a persistent five-item left rail already provides navigation; pages lack useful contextual actions. | Apps #28, Setup #32, Settings #36 | Introduce relevant actions when actual functionality exists; avoid dead-end controls |
| P1 — bridge reliability | ADB key events can act on an unrelated foreground app (Google Play) unless launch/focus is verified first. | Bridge #18 versus successful launch/capture #20 | Add explicit foreground-app verification before automation; abort on mismatch; do not attribute other-app screenshots to Vitrum |
| P2 — density | Home uses an oversized two-line hero title and large status/CTA; secondary screens use generous whitespace. It is readable at 1080p but occupies significant area and pushes additional Home panels below the initial viewport. | Home #20; Stage 1 `TvActivity.java` layout | Validate 720p, overscan, font scaling and lower-resolution TV layouts; reconsider title density |
| P2 — focus | Violet outlines distinguish the selected navigation item and white outlines distinguish the primary focused button; visually good, but keyboard focus vs current-page selection deserves explicit QA. | All five screenshots | Automated + physical remote focus-path checks at rail/action boundaries, Back and lifecycle restoration |
| P2 — diagnostics | One sample shows low **PSS** (~30.7 MiB), but it is not a stress/soak metric; thermal/CPU, sleep/wake and sustained memory remain unknown. | Private bridge #38 | Collect 20-minute and wake/resume measurements and OOM/low-memory evidence |

## Acceptance matrix after this run

| Gate | Result | Basis |
|---|---|---|
| Installed ARM32 debug shell on Android 11 BOXY | **PASS** | Package/version/process from actual box |
| All five section views render in 1080p | **PASS** | Five captured and inspected PNGs |
| ADB-injected D-pad left/down/OK navigation | **PASS** | Screenshot-confirmed page transitions |
| ADB-injected OK from Home and BACK to Home | **PASS** | Screenshot #12/#14; Back #37 |
| UI truthfulness (no false connected VPN label) | **PASS** | Visible offline notices |
| Physical handheld remote hardware | **UNKNOWN** | Only ADB key injection tested |
| Foreground-app guard before remote navigation | **FAIL / bridge weakness** | Unrelated Google Play screenshot #18 |
| 720p, 4K, overscan and accessible font size | **UNKNOWN** | 1080p only |
| VPN permission, TUN, DNS, IPv4/IPv6, routing | **NOT IMPLEMENTED** | Stage 1 shell only |
| Stable release readiness | **NOT READY** | Functional/security gates outstanding |

## Next sequence

1. Preserve this physical evidence and fix bridge foreground guard before unattended UI sweeps.
2. Improve the secondary page content architecture and naming, without pretending real features exist.
3. Implement independently verifiable Android 11 ARM32 VPN runtime in `:tv`; retain complete isolation from the original `:app` and tablet-only `:lite`.
4. Test real D-pad remote, denial/cancel states, sleep/resume, remote input on 720p/1080p and continuous memory before a release candidate.

**Summary:** The current BOXY prototype is a visually coherent **working TV UI shell** with proven ADB navigation on actual hardware. It is **not a working VPN product** and is not ready for a stable release.

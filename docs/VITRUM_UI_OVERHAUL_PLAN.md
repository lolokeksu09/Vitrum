# VITRUM — Historical UI Overhaul Plan and Implementation Record

> **Archived project record.** This document summarizes the former UI overhaul effort on the historical `clone-integration` branch and the intermediate `v2.8.x` line. As of 2026-11-09, only `main` remains in the repository; `clone-integration` is not an active branch. PRs and commit SHAs from that period are historical references, not current open tasks. This file does not describe the current Vitrum 2.0.0 UI specification. See [HANDOFF](HANDOFF.md) and [v2.0.0 release notes](releases/v2.0.0.md).

## Historical implementation summary

The former overhaul covered:
- Shared UI foundations and dialog/screen wrappers.
- Home screen and server catalog.
- Routing screen and Settings Hub/search.
- Journal and Privacy Shield confirmation/accessibility states.
- Onboarding and Add Server dialog behavior.
- Game and Settings action touch targets, targeting at least 48 dp.
- Clone UI smoke coverage and emulator screenshot matrix planning.

Historical PRs included #87–#107. Their merge records and exact commit SHAs remain available in Git history. They must not be interpreted as evidence that every manual accessibility or device-acceptance gate passed.

## Architecture observed during the historical effort

The UI was not a five-tab architecture. `MainActivity.Root` and `FloatingNav` exposed two bottom tabs: `HomeTab` and `SettingsTab`. `ServersSheet` opened `ServersTab` over Home; `GameDialog`, `JournalDialog`, and `ShieldDialog` were full-screen tools. `RoutingTab` was reached through `SettingsTab` and `RoutesPage`.

The intended navigation contract was to preserve these routes and not add tabs merely for visual novelty.

## Historical visual principles

- Premium, restrained utility interface; AMOLED `#000000` in dark theme, VITRUM violet accent, Manrope typography.
- State before decoration. Semantic colors only; avoid glass panels, meaningless glow, continuous background animation, duplicated status labels, and visual clutter.
- Consistent typography, spacing (4/8/12/16/24/32 dp guidance), surfaces, and corner radii.
- Primary actions visually distinct; statuses not communicated by color alone.
- Touch targets at least 48 dp; text wraps at font scales 1.3 and 1.5; no horizontal scrolling for required actions.
- Minimal functional motion and correct edge-to-edge/system insets.

## Required UX invariants recorded by the plan

1. Do not confuse off, connecting, connected, switching, blocked, error, and unknown states.
2. "Xray running" is not equivalent to "traffic verified" or "no leaks".
3. Preserve the two-tab Home/Settings navigation and system Back behavior.
4. Preserve package ID, settings/subscription formats, TUN/Xray, kill switch, DNS/routing priority, encryption, and signing certificate.
5. Do not add permissions, SDK dependencies, root features, telemetry, or network requests for visual design.
6. Keep accessibility: contrast, >=48 dp touch targets, TalkBack labels/roles/states, font scales 1.0/1.3/1.5, and no clipped critical actions.
7. Route all new visible strings through the existing localization mechanism.
8. UI rendering must not mutate server, DNS, routing rules, or subscriptions.
9. Visual PRs require current screenshots and manual review; a successful build is not visual acceptance.
10. Official APKs require verified CI and maintainer signing; CI does not replace ARM64 device tests.

## Verification boundaries

Clone builds and Android emulator UI smoke use synthetic/demo data. They do not prove real VPN/Xray traffic, TUN behavior, TalkBack acceptance, physical-device insets, or ARM64 runtime. Physical Lenovo Legion Y70 acceptance was a separate manual gate.

This archive is a historical design/implementation summary, not a current backlog. Consult `main`, [HANDOFF](HANDOFF.md), and the current release notes before planning further work.

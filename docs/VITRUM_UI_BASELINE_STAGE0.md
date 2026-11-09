# VITRUM — UI Overhaul Baseline (Stage 0)

> **Archived baseline record.** This document records the baseline for the historical UI overhaul work. It is not the current product specification or an open backlog. Current public product status is documented in [HANDOFF](HANDOFF.md) and [Vitrum 2.0.0 release notes](releases/v2.0.0.md).

## Purpose

Capture reproducible UI states before redesign, using synthetic demo data only. Demo fixtures must never be represented as real VPN traffic or connectivity.

## Baseline dimensions
- Dark and light themes.
- Russian and English localization.
- Compact window heights and target widths of approximately 360/390/430 dp.
- Font scales 1.0, 1.3, and 1.5.
- Home, server sheet, Routing, all Settings sections, Game, Journal, Privacy Shield, onboarding, and dialogs.
- Loading, empty, error, connected, disconnected, switching, blocked, and unknown states.

## Required evidence
- Screenshot set tied to an exact source commit and APK/build identity.
- Navigation map and list of states actually covered.
- Separate list of visual defects for follow-up changes.
- Explicit record of states not covered by emulator automation.

## Verification limits

Android emulator screenshots with synthetic demo data verify UI layout only. They do not verify Xray startup, Android TUN, VPN traffic, DNS, IPv4/IPv6 routing, kill switch, TalkBack behavior on physical devices, or ARM64 runtime. Accessibility, system insets, and real-device behavior require separate manual acceptance.

## Historical status

The baseline and initial UI smoke work were integrated during the Clone UI overhaul. Historical PRs and Actions runs are retained in repository history. The current project is not based on the old `clone-integration` branch; use `main` and [HANDOFF](HANDOFF.md) for current state.

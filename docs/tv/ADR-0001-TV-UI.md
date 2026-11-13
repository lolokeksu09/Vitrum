# ADR-0001 — Java Android Views for the initial Vitrum TV shell

**Status:** Accepted for Stage 1 implementation on the feature branch, 2026-11-13. Revisit only with comparative evidence.

## Context

Vitrum TV is a new independent app with the first verification target an Android TV 11 / API 30 box reporting 2 GB RAM and a 32-bit application ABI. Its core input is a physical D-pad remote, not touch. The Stage 1 objective is a small, installable UI that demonstrates launcher integration, visible focus, deterministic navigation and Back handling. It cannot use the existing phone or tablet modules as a product foundation.

## Decision

Use **Java with standard Android Views** for this first shell, in module `:tv` and package `app.rayclient.tv`. The choice is based on minimal UI dependencies and direct control of View focus for initial ARM32 testing. It is **not** based on or copied from `:lite`; source code and private data remain separate.

Use a TV navigation rail, five destinations, one primary action on each screen, Russian and English strings, large 10-foot typography, explicit `nextFocusLeft/Right/Up/Down` and stable View IDs. Do not declare `VpnService` or INTERNET permission until a real independent VPN backend is implemented.

## Alternatives

- **Compose for TV:** strong declarative components and state handling; introduces additional dependencies and requires measured focus/memory behavior on the target hardware. A valid later candidate, not rejected permanently.
- **Phone UI reused on TV:** rejected. Touch and compact layout assumptions do not satisfy the D-pad requirement, and would mix independent product release lifecycles.

## Consequences

- Native Views require deliberate state and focus handling; instrumented D-pad tests are mandatory.
- No migration path or source compatibility exists with other Vitrum apps by default.
- The `0.1.0-dev` APK is a **developer-only preview**: it cannot connect VPN or protect traffic.
- Before a final UI architecture decision, measure cold launch, RAM, scrolling and real remote-only navigation on BOXY and record actual screenshots.
- Any switch to Compose requires a new ADR with side-by-side memory, cold-launch, focus and APK size evidence, not stylistic preference alone.

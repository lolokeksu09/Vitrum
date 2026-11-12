# Vitrum — Current Project Status

Last synchronized: 2026-11-12.  
Primary branch: `main`. Maintenance changes use short-lived PR branches.  
Current stable public release: **Vitrum 2.1.1** (versionCode 286). A separate official, author-signed **Vitrum 2.1.2 pre-release** (versionCode 287) was published on 2026-11-12 from source commit `729f27e26dc0773e358969cc53a219aee1a0a8d8`. It includes subscription error redaction merged after 2.1.1. The certificate matched the existing maintainer certificate in CI; full physical-device VPN, network/leak and data-preservation acceptance is not verified for 2.1.2.

## Public releases

Four stable releases and their tags are retained:

- [Vitrum 2.1.1](https://github.com/lolokeksu09/Vitrum/releases/tag/v2.1.1) — current primary stable release, Android 8+, ARM64, WorkManager startup fix.
- [Vitrum 2.1.2 signed pre-release](https://github.com/lolokeksu09/Vitrum/releases/tag/v2.1.2) — published 2026-11-12; privacy maintenance, Android 8+, ARM64. See [notes](releases/v2.1.2.md).
- [Vitrum 2.0.0](https://github.com/lolokeksu09/Vitrum/releases/tag/v2.0.0) — previous primary stable release, Android 8+, ARM64.
- [Vitrum 1.0.0](https://github.com/lolokeksu09/Vitrum/releases/tag/v1.0.0) — first primary release.
- [Vitrum Lite 1.0.0](https://github.com/lolokeksu09/Vitrum/releases/tag/lite-v1.0.0) — separate reduced ARM32 build, Android 6+.

Do not move or delete release tags. Tag `v2.0.0` points to the source commit used for the published release; later `main` changes are repository/documentation maintenance, not APK changes.

Consolidated changelog: [docs/releases/v2.0.0.md](releases/v2.0.0.md). Public history: [CHANGELOG.md](../CHANGELOG.md). Historical notes `v2.2.0–v2.8.3`, the archived v2.1.0 test candidate and older documents are retained as historical archives, not as currently published releases.

## Structure and builds

- Main application: Android, package ID `app.rayclient`, based on Xray-core.
- Separate Lite build: package ID `app.rayclient.lite`, ARMv7/Android 6+.
- The main Gradle project retains an internal Clone build variant for testing. It is not a separate public product or release.
- Build versions and dependencies are defined in Gradle configuration. Version 2.1.2 changes only `app/build.gradle.kts` versionCode/versionName; dependencies, minSdk/targetSdk and Lite remain unchanged.

### Remaining GitHub Actions

| Workflow | Purpose |
|---|---|
| `.github/workflows/build.yml` | Main Debug/Clone/Release build and maintainer-signature verification |
| `.github/workflows/lite.yml` | Lite ARM32 build; APK uploaded as an Actions artifact |
| `.github/workflows/lite-ui-emulator.yml` | Lite UI smoke test on an Android 6 x86 emulator |
| `.github/workflows/pr-check.yml` | JVM tests, R8 constructor guard, Debug/Clone/release compilation and package checks |
| `.github/workflows/main-app-startup-emulator.yml` | Android 13 debug and optimized-release UI launch/relaunch smoke (x86_64 only) |
| `.github/workflows/release-artifact-smoke.yml` | PR check of cross-job artifact upload/download integrity |
| `.github/workflows/showcase-emulator.yml` | Manual-only Android 13 screenshot capture with synthetic data |

The Lite workflow must not automatically create or overwrite the already published `lite-v1.0.0`. Completed one-shot 2.1.0/2.1.1/2.1.2 release-request workflows and older publisher/retry/probe workflows have been removed. Do not restore them without a specific new release requirement.

## Repository infrastructure

- Community rules and processes: `CODE_OF_CONDUCT.md`, `SUPPORT.md`, `GOVERNANCE.md`, `CONTRIBUTING.md`, and `SECURITY.md`.
- Engineering documentation: `docs/ARCHITECTURE.md`, `docs/TESTING.md`, and `docs/RELEASING.md`; index: `docs/README.md`.
- `.github/CODEOWNERS` assigns `@lolokeksu09` as code owner for all paths. This routes review requests; it does not enforce branch protection.
- `.github/dependabot.yml` configures weekly Gradle and GitHub Actions version updates. This does not confirm that Dependabot security alerts are enabled.
- GitHub Discussions are disabled. Repository About has an English description, but Topics are not configured.
- `main` reports `protected=false` and the repository rulesets list is empty as of 2026-11-12. Required checks and release/tag controls need separate GitHub settings; release immutability was not independently verified.

## Open repository tasks

- No open GitHub Issues or Pull Requests existed when checked on 2026-11-12, before this maintenance PR was created. Re-check when planning subsequent work.
- `main` is not protected (`protected=false`); required status checks and tag protection are not configured. These are GitHub settings, not source-code properties.
- Vitrum 2.1.1 remains the current stable author-signed APK. Vitrum 2.1.2 is published as an author-signed pre-release with `versionCode 287`; CI verified its official certificate, but physical-device VPN/TUN, DNS and IPv4/IPv6 acceptance remains unverified. The published 2.1.1 tag and APK are unchanged.
- Historical plans and intermediate release notes are retained. Their historical status is stated in the documents; deletion is not required.
- Subscription error messages must not include untrusted response body snippets, URLs or raw exception messages. Regression checks live in `SubscriptionErrorTextTest.kt`; this change is part of the 2.1.2 release candidate.

## Verification limits

This handoff describes the GitHub repository and its configuration. It does not prove physical VPN operation, DNS/IPv4/IPv6 leak resistance, network transitions, Xray runtime behavior on a specific device, or server availability. Such claims require the corresponding runtime tests.

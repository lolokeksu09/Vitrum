# Vitrum — Current Project Status

Last synchronized: 2026-11-11.  
Primary branch: `main` (the repository currently has one branch).  
Current stable public release: **Vitrum 2.0.0**. Signed **2.1.0** was published as a pre-release but crashes at startup on Android 13 due to R8 removal of `WorkDatabase_Impl.<init>()`. **2.1.1** is an in-progress targeted hotfix; it is not a published/stable release until signed workflow publication and physical ARM64 device acceptance. The previous maintainer device confirmation applies only to 2.0.0.

## Public releases

Three stable releases and their tags are retained:

- [Vitrum 2.0.0](https://github.com/lolokeksu09/Vitrum/releases/tag/v2.0.0) — current primary release, Android 8+, ARM64.
- [Vitrum 1.0.0](https://github.com/lolokeksu09/Vitrum/releases/tag/v1.0.0) — first primary release.
- [Vitrum Lite 1.0.0](https://github.com/lolokeksu09/Vitrum/releases/tag/lite-v1.0.0) — separate reduced ARM32 build, Android 6+.

Do not move or delete release tags. Tag `v2.0.0` points to the source commit used for the published release; later `main` changes are repository/documentation maintenance, not APK changes.

Consolidated changelog: [docs/releases/v2.0.0.md](releases/v2.0.0.md). Public history: [CHANGELOG.md](../CHANGELOG.md). Historical notes `v2.2.0–v2.8.3`, the archived v2.1.0 test candidate and older documents are retained as historical archives, not as currently published releases.

## Structure and builds

- Main application: Android, package ID `app.rayclient`, based on Xray-core.
- Separate Lite build: package ID `app.rayclient.lite`, ARMv7/Android 6+.
- The main Gradle project retains an internal Clone build variant for testing. It is not a separate public product or release.
- Build versions and dependencies are defined in Gradle configuration. Do not update them as part of documentation/repository cleanup without a separate task.

### Remaining GitHub Actions

| Workflow | Purpose |
|---|---|
| `.github/workflows/build.yml` | Main Debug/Clone/Release build and maintainer-signature verification |
| `.github/workflows/lite.yml` | Lite ARM32 build; APK uploaded as an Actions artifact |
| `.github/workflows/lite-ui-emulator.yml` | Lite UI smoke test on an Android 6 x86 emulator |
| `.github/workflows/pr-check.yml` | PR checks, including the internal Clone build variant |

The Lite workflow must not automatically create or overwrite the already published `lite-v1.0.0`. One-off publisher/retry/probe workflows were removed. Do not restore old `publish-v2-*`, `retry-v2`, `xray-runtime-probe`, or Clone workflows without a specific need.

## Repository infrastructure

- Community rules and processes: `CODE_OF_CONDUCT.md`, `SUPPORT.md`, `GOVERNANCE.md`, `CONTRIBUTING.md`, and `SECURITY.md`.
- Engineering documentation: `docs/ARCHITECTURE.md`, `docs/TESTING.md`, and `docs/RELEASING.md`; index: `docs/README.md`.
- `.github/CODEOWNERS` assigns `@lolokeksu09` as code owner for all paths. This routes review requests; it does not enforce branch protection.
- `.github/dependabot.yml` configures weekly Gradle and GitHub Actions version updates. This does not confirm that Dependabot security alerts are enabled.
- GitHub Discussions are disabled. Repository About has no description or Topics yet.
- `main` protection, rulesets, and immutable releases are not enabled. These require GitHub repository settings, not file changes.

## Open repository tasks

- No open GitHub Issues were found on 2026-11-09 (the GitHub API returned an empty list). Re-check current Issues before planning future work.
- `main` is not protected (`protected=false`); required status checks and tag protection are not configured. These are GitHub settings, not source-code properties.
- Version 2.1.0 migrates Gradle/AGP/Kotlin/Compose to a coordinated Android SDK 37.0 toolchain. It must pass CI and receive physical-device acceptance before stable publication.
- Historical plans and intermediate release notes are retained. Their historical status is stated in the documents; deletion is not required.

## Verification limits

This handoff describes the GitHub repository and its configuration. It does not prove physical VPN operation, DNS/IPv4/IPv6 leak resistance, network transitions, Xray runtime behavior on a specific device, or server availability. Such claims require the corresponding runtime tests.

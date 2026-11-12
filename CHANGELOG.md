# Vitrum Changelog

## Unreleased — maintenance after Vitrum 2.1.1

These source-repository changes do **not** modify the already published, author-signed `Vitrum-2.1.1.apk` or its Git tag.

### Fixed
- Subscription error messages no longer show response-body snippets, raw exception messages or user-supplied server-link parse details. Error categories remain actionable, with JVM regression coverage.

### Maintenance
- Remove two completed one-shot pre-release request workflows for 2.1.0 and 2.1.1, leaving the official signed-release workflow intact.
- Synchronize project status, CI workflow inventory and security-reporting documentation.

The public release history is consolidated around the primary releases. Intermediate patches are represented by the consolidated Vitrum 2.0.0 release.

## Vitrum 2.1.1 — WorkManager startup hotfix (stable release)

The signed v2.1.0 pre-release crashed on launch on Android 13 due to a missing `WorkDatabase_Impl` zero-argument constructor after R8 shrinking. Full details: [v2.1.1](docs/releases/v2.1.1.md).

### Fixed
- Preserve the WorkManager/Room generated database constructor used during AndroidX Startup, before MainActivity.
- Add a DEX-level R8 regression check required for both PR and official release CI.
- Add Android 13 startup smoke test coverage for Debug and optimized Release builds.

### Known limitations
- Maintainer confirmed successful installation and launch of the corrected v2.1.1 APK. Complete VPN/TUN and leak-protection behavior across devices is not independently verified.
- The affected v2.1.0 pre-release is superseded by the author-signed v2.1.1 stable release. Keep data intact when updating.

## Vitrum 2.1.0 — toolchain maintenance candidate

This is a maintenance update to the Android build stack, Compose UI dependency set, and asynchronous Kotlin runtime. See [2.1.0 release notes](docs/releases/v2.1.0.md).

### Improved
- Kotlin/Compose compiler 2.4.21 with compatible compilerOptions configuration.
- Coroutines Android 1.11.0; AGP 9.5.1; Gradle 9.6.0; Compose BOM 2026.09.00; Android compile SDK 37.0.
- Material3 Text wrapper for the updated Compose API without changing the application's localization behavior.
- CI dependency checks, JVM unit test coverage, release artifact checks, and test/Clone build compatibility.

### Known limitations
- CI proves building and tested JVM logic, not real-device VPN networking or leak prevention.
- Stable promotion requires physical-device acceptance and verification of the original author signing certificate.

## Vitrum 2.0.0 — consolidated primary release

Full change list: [Vitrum 2.0.0 release notes](docs/releases/v2.0.0.md).

### Added
- Updated onboarding, settings search, server filters, and sorting.
- VPN event history and diagnostic reports.
- Explanations of expected routes for apps, domains, IP addresses, ports, and protocols.
- Manual IPv4/IPv6 checks, DNS port 53 and DoT/DoQ 853 audits, and Android Private DNS visibility.
- Strict DNS address validation and Russian/English localization.
- More convenient routing-profile and server selection from game mode.

### Improved
- Home screen, Material 3 navigation, server lists, and settings.
- Server switching and automatic selection with connection verification and recovery.
- DNS route handling, diagnostics, and privacy-status presentation.
- Atomic auto-server persistence and application update checks.

### Fixed
- STOP/START races, stale results, and false active-server changes.
- Connection recovery and server iteration errors.
- Active-route resets during subscription operations.
- Corrupt GeoData handling, invalid DNS, and Pinger shutdown.
- UI state errors during connection, filtering, and server selection.

### Redesigned
- The home screen and main sections now use a consistent, minimal AMOLED/Material 3 interface.
- Privacy Shield, event history, and route explanations distinguish configuration analysis from observed diagnostic results.
- Subscription, profile, and persistence workflows are more predictable.

## Vitrum 1.0.0 — first primary release
- Initial Android VPN client based on Xray, with server, subscription, and routing support.
- Core connection, server, settings, and diagnostics screens.
- Split tunneling, game exclusions, DNS settings, and backup support.
- GNU GPL v3.0 licensing and third-party component notices.

## Vitrum Lite 1.0.0 — separate ARM32 build
- Separate `app.rayclient.lite` application for Android 6.0+ and ARMv7.
- Connection using a single link or HTTPS subscription, server selection, and encrypted profile persistence.
- Simplified interface for resource-constrained devices.
- Lite installs separately and does not replace the main application.

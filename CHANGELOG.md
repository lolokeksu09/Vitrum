# Vitrum Changelog

The public release history is consolidated around the primary releases. Intermediate patches are represented by the consolidated Vitrum 2.0.0 release.

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

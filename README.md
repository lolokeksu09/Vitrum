<p align="center"><img src="assets/vitrum-banner.svg" alt="Vitrum — Android VPN client" width="960"></p>
<p align="center"><a href="README.ru.md">Russian version</a> · <a href="https://github.com/lolokeksu09/Vitrum/releases">Releases</a> · <a href="CONTRIBUTING.md">Contributing</a> · <a href="SUPPORT.md">Support</a> · <a href="GOVERNANCE.md">Governance</a> · <a href="SECURITY.md">Security</a></p>
<p align="center"><img src="https://img.shields.io/github/v/release/lolokeksu09/Vitrum?display_name=tag&style=flat-square&color=8F79F6" alt="Latest release"> <img src="https://img.shields.io/github/license/lolokeksu09/Vitrum?style=flat-square&color=48DEC4" alt="License"> <img src="https://img.shields.io/github/actions/workflow/status/lolokeksu09/Vitrum/build.yml?branch=main&style=flat-square&label=build" alt="Build status"></p>

**Vitrum** is a free, open-source Android VPN client powered by [Xray-core](https://github.com/XTLS/Xray-core). Import your own connection profiles, switch servers, control which apps use the tunnel, and inspect routing and network diagnostics. No ads, analytics or tracking SDKs.

**[Download Vitrum 2.1.1 (signed APK)](https://github.com/lolokeksu09/Vitrum/releases/download/v2.1.1/Vitrum-2.1.1.apk)** · [Release notes](https://github.com/lolokeksu09/Vitrum/releases/tag/v2.1.1) · [Russian README](README.ru.md)

| Connect | Control | Diagnose |
|---|---|---|
| VLESS, VMess, Trojan, Shadowsocks, Hysteria2; link, QR and subscription imports | Per-app split tunneling, domain/IP rules, DNS options and server switching | Route previews, IPv4/IPv6 checks, DNS analysis and VPN event history |

**Requirements:** Android 8.0+ · ARM64 (`arm64-v8a`) · your own compatible server configuration or subscription.

**Current stable:** `v2.1.1` (`versionCode 286`). It fixes the WorkManager startup crash in the superseded `v2.1.0` pre-release. Install the author-signed APK over an existing version without clearing app data. [Fix and verification details](docs/releases/v2.1.1.md).

## Quick start

1. **Download** the official `Vitrum-2.1.1.apk` above and install it on a supported Android device. For the release checksum and author certificate, see [GitHub Releases](https://github.com/lolokeksu09/Vitrum/releases/tag/v2.1.1) and [SECURITY.md](SECURITY.md).
2. **Import** a compatible profile using a share link, QR code or subscription. Vitrum is a VPN client; it does not provide a VPN server or subscription.
3. **Connect** after reviewing Android's VPN permission prompt. Configure split tunneling or DNS options only as needed.

**Security scope:** diagnostics report what can be verified and what remains unknown. No VPN client can claim universal leak-free behavior from build tests or configuration analysis alone.

## Features

**VPN and servers**
- VLESS, VMess, Trojan, Shadowsocks and Hysteria2.
- Xray transports and security modes supported by imported configurations, including Reality, TLS, XHTTP, gRPC and WebSocket.
- Import from share links and QR codes; HTTPS and JSON subscriptions.
- Server search, country filters, sorting, favourites and latency checks.
- Server switching with connection confirmation and rollback handling.
- Automatic server selection and network-change recovery.

**Traffic routing**
- Per-app split tunneling and routing profiles.
- Global, allow-list and "Everything except Russia" modes.
- Custom domain, IP/CIDR and port rules.
- DNS, DoH, FakeDNS, IPv6 and local-network settings.
- Route preview for apps, domains, IP addresses, ports and protocols.

**Diagnostics**
- Privacy Shield separates confirmed results, warnings and checks that could not be verified.
- Manual IPv4/IPv6 checks and DNS route analysis.
- Android Private DNS status and expected DoT/DoQ routing.
- VPN event history and diagnostic reports.
- Diagnostics distinguish configuration analysis from observed results; they are not a universal guarantee that traffic can never bypass the VPN.

**Everyday use**
- Game mode and app exclusions for selected game platforms.
- Traffic statistics, quick-settings tile and connection shortcut.
- Encrypted .vitrumbak backups, app lock and widget.
- Russian and English interface; large-font and TalkBack support.

See the current [2.1.1 release notes](docs/releases/v2.1.1.md) and [changelog](CHANGELOG.md) for the latest changes; [2.0.0 notes](docs/releases/v2.0.0.md) describe the previous feature release.

## Downloads

Get APKs only from [GitHub Releases](https://github.com/lolokeksu09/Vitrum/releases).

| Release | Package ID | Android | Architecture |
|---|---|---:|---|
| **[Vitrum 2.1.1 — Stable](https://github.com/lolokeksu09/Vitrum/releases/tag/v2.1.1)** | **app.rayclient** | **8.0+** | **ARM64** |
| [Vitrum 2.0.0](https://github.com/lolokeksu09/Vitrum/releases/tag/v2.0.0) | app.rayclient | 8.0+ | ARM64 |
| [Vitrum 1.0.0](https://github.com/lolokeksu09/Vitrum/releases/tag/v1.0.0) | app.rayclient | 8.0+ | ARM64 |
| [Vitrum Lite 1.0.0](https://github.com/lolokeksu09/Vitrum/releases/tag/lite-v1.0.0) | app.rayclient.lite | 6.0+ | ARMv7 |

Vitrum Lite is a separate reduced build. It does not replace or update the main application.

## Compatibility

| Component | Current baseline |
|---|---|
| Main application | Android 8.0 (API 26)+ |
| Main ABI | arm64-v8a |
| Main application ID | app.rayclient |
| Lite application ID | app.rayclient.lite |
| Android compile SDK | 37.0 |
| Kotlin/JVM target | 17 |
| Build JDK | 21 |
| License | GNU GPL v3.0 |

## Build from source

Requirements: Linux or WSL, JDK 21, Android SDK Platform 37.0, Build Tools 37.0.0 and internet access for verified Xray runtime assets.

From the repository root:

~~~bash
tools/SETUP_ENV.sh
tools/FETCH_BINARIES.sh --linux
tools/build.sh debug
~~~

The build helper runs in the background and writes logs to /tmp/vitrum-build/. Check progress with:

~~~bash
tail -n 30 /tmp/vitrum-build/debug.log
cat /tmp/vitrum-build/done
~~~

Run the configured unit and Android compilation checks with:

~~~bash
tools/build.sh tests
~~~

A signed release build requires the maintainer's release keystore at keystore/vitrum-release.jks and the VITRUM_KS_PASS environment variable. The keystore and password are not part of this repository. Do not create a replacement signing key and present its APK as an official update.

## Project structure

| Path | Purpose |
|---|---|
| app/ | Main Android application |
| lite/ | Reduced ARMv7 application |
| tools/ | Environment setup, runtime asset retrieval and build helpers |
| .github/workflows/ | Build, PR and Lite automation |
| docs/ | Project handoff and technical documentation |
| docs/releases/ | Release notes and historical archive |
| AGENTS.md | Repository-specific development and safety rules |
| SECURITY.md | Vulnerability reporting and release-signature guidance |
| CODE_OF_CONDUCT.md | Community behaviour standards |
| SUPPORT.md | Support scope and troubleshooting information |
| GOVERNANCE.md | Maintainer roles and decision process |
| docs/ARCHITECTURE.md | System boundaries and component responsibilities |
| docs/TESTING.md | CI coverage and manual verification limits |
| docs/RELEASING.md | Versioning, signing and publication procedure |

The main Gradle project also contains an internal Clone build variant used by CI. It is not a separate public product or release.

## Privacy and security

- No advertising, analytics or tracking SDKs are included.
- Server credentials and subscription data use the application's encrypted storage mechanisms.
- Updates and subscriptions use HTTPS.
- Diagnostic reports are designed to exclude server keys, subscription URLs and raw Xray configuration.
- Route previews and privacy checks have explicit limits. They must not be interpreted as proof of leak-free operation across every Android device, app, network and routing configuration.

Read [SECURITY.md](SECURITY.md) before reporting a vulnerability. Do not publish exploitable details in public Issues.

## Project governance

Vitrum documents its community rules, support scope, maintainer responsibilities and engineering processes:

- [Code of Conduct](CODE_OF_CONDUCT.md)
- [Support policy](SUPPORT.md)
- [Governance](GOVERNANCE.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Testing](docs/TESTING.md)
- [Release process](docs/RELEASING.md)

## Contributing

Contributions are welcome: focused bug fixes, documentation improvements, accessibility fixes and translation corrections.

1. Search existing Issues before opening a new one.
2. For substantial changes, open an Issue first to agree on scope.
3. Read [CONTRIBUTING.md](CONTRIBUTING.md) and [AGENTS.md](AGENTS.md).
4. Keep pull requests focused and explain user-visible impact.
5. Never include signing keys, passwords, subscription URLs, real server links or personal data.

Pull requests are reviewed by the maintainer before merge. Passing CI does not replace review or physical-device acceptance for VPN and network behaviour.

## License

Vitrum is distributed under the [GNU General Public License v3.0](LICENSE). Third-party components are listed in [THIRD-PARTY-NOTICES.txt](THIRD-PARTY-NOTICES.txt).

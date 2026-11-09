<p align="center"><img src="assets/vitrum-banner.svg?v=0577de7" alt="Vitrum — Android VPN client" width="100%"></p>
<p align="center"><a href="README.ru.md">Русская версия</a> · <a href="https://github.com/lolokeksu09/Vitrum/releases">Releases</a> · <a href="CONTRIBUTING.md">Contributing</a> · <a href="SECURITY.md">Security</a></p>
<p align="center"><img src="https://img.shields.io/github/v/release/lolokeksu09/Vitrum?display_name=tag&style=flat-square&color=8F79F6" alt="Latest release"> <img src="https://img.shields.io/github/license/lolokeksu09/Vitrum?style=flat-square&color=48DEC4" alt="License"> <img src="https://img.shields.io/github/actions/workflow/status/lolokeksu09/Vitrum/build.yml?branch=main&style=flat-square&label=build" alt="Build status"></p>

**Vitrum** is an open-source Android VPN client built on [Xray-core](https://github.com/XTLS/Xray-core). It combines server management, subscription imports, flexible traffic routing and connection diagnostics in one application.

The project is designed around explicit connection state, local control and privacy-conscious diagnostics. It does not include advertising, analytics or tracking SDKs.

> **Current stable release:** [Vitrum 2.0.0](https://github.com/lolokeksu09/Vitrum/releases/tag/v2.0.0) · Android 8.0+ · ARM64

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
- Global, allow-list and «Everything except Russia» modes.
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

See the [2.0.0 release notes](docs/releases/v2.0.0.md) and [changelog](CHANGELOG.md) for the complete change list.

## Downloads

Get APKs only from [GitHub Releases](https://github.com/lolokeksu09/Vitrum/releases).

| Release | Package ID | Android | Architecture |
|---|---|---:|---|
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
| Android compile SDK | 35 |
| Kotlin/JVM target | 17 |
| Build JDK | 21 |
| License | GNU GPL v3.0 |

## Build from source

Requirements: Linux or WSL, JDK 21, Android SDK Platform 35, Build Tools 35.0.0 and internet access for verified Xray runtime assets.

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

The main Gradle project also contains an internal clone build variant used by CI. It is not a separate public product or release.

## Privacy and security

- No advertising, analytics or tracking SDKs are included.
- Server credentials and subscription data use the application's encrypted storage mechanisms.
- Updates and subscriptions use HTTPS.
- Diagnostic reports are designed to exclude server keys, subscription URLs and raw Xray configuration.
- Route previews and privacy checks have explicit limits. They must not be interpreted as proof of leak-free operation across every Android device, app, network and routing configuration.

Read [SECURITY.md](SECURITY.md) before reporting a vulnerability. Do not publish exploitable details in public Issues.

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

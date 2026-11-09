# Support

This document explains where to ask for help and what information is useful. Vitrum is maintained on a best-effort basis; individual response times are not guaranteed.

## Supported releases

Check [GitHub Releases](https://github.com/lolokeksu09/Vitrum/releases) for the latest stable main application release. Vitrum Lite is a separate Android ARM32 package and has its own compatibility and limitations.

Older releases and historical development notes are not automatically supported. A bug should be reproduced on the latest applicable release before it is reported, unless the update itself is impossible or unsafe.

## Where to ask

- Reproducible defects: use the [Bug report issue template](https://github.com/lolokeksu09/Vitrum/issues/new?template=bug_report.yml).
- Proposed functionality: use the [Feature request issue template](https://github.com/lolokeksu09/Vitrum/issues/new?template=feature_request.yml).
- Pull requests and implementation discussion: use the repository's Pull Request workflow and [CONTRIBUTING.md](CONTRIBUTING.md).
- Security vulnerabilities: follow [SECURITY.md](SECURITY.md). Do not report them in public Issues.

GitHub Discussions are not currently configured for this repository. Do not open a bug Issue solely to request general troubleshooting or personal configuration work.

## Useful bug report information

Include:
- Vitrum version and build type (main ARM64, Lite ARMv7, debug or locally built);
- Android version and device model;
- whether the issue is reproducible and the exact steps;
- expected and actual behaviour;
- relevant sanitised logs, with timestamps where useful.

For VPN/network behaviour, also describe the connection type (Wi-Fi or mobile), relevant routing mode and whether the affected application is excluded from VPN. Do not post raw public IP addresses when they are not needed.

## Never publish

Do not attach:
- server links, UUIDs, passwords, private keys or subscription URLs (they may contain access tokens);
- unredacted Xray logs, private IPs, DNS names or diagnostic exports;
- signing keystores, passwords, access tokens or personal data.

A successful CI run or a working connection on one device does not prove that DNS, IPv4, IPv6 or routing is leak-free on all devices and networks.

## Scope

Vitrum is not a managed VPN service and does not provide server hosting, subscription provisioning or guaranteed network support. The project can review reproducible application defects and focused contributions; it cannot guarantee that a third-party server, carrier, Android firmware or network policy will work with every configuration.

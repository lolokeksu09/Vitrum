# Governance

## Project ownership

Vitrum is currently maintained by one project maintainer: [@lolokeksu09](https://github.com/lolokeksu09). The maintainer is responsible for project direction, repository administration, accepting or declining contributions, release approval and security coordination.

There is no steering committee, voting body or delegated release team at this time. The project does not claim a multi-maintainer review process.

## Contributions and decisions

1. Small, focused fixes and documentation improvements may be proposed directly as Pull Requests.
2. New features, architecture changes and behaviour changes should first be discussed in an Issue so scope, compatibility and security impact are understood.
3. The maintainer reviews changes for correctness, scope, maintainability, licensing and compatibility with the project's safety constraints.
4. The maintainer may request changes, defer a proposal, close it or decline it with an explanation.
5. A successful CI run is evidence for the checks that ran; it is not automatic approval and does not replace device acceptance for VPN/network behaviour.

Contributors retain copyright in their own contributions and submit them under the repository's [GPL-3.0-only license](LICENSE), subject to applicable third-party rights. No separate Contributor License Agreement is currently required.

## Release authority

Only the maintainer approves official releases and controls the author signing key. Official main-app APKs must use the established author certificate. A debug-signed or differently signed APK must never be represented as an official update.

See [docs/RELEASING.md](docs/RELEASING.md) for the release procedure and [SECURITY.md](SECURITY.md) for security reports.

## Repository controls

This document describes project policy; it does not itself enforce GitHub permissions or branch rules.

As checked on 2026-11-09, the `main` branch was not protected and no repository rulesets were configured. Until those settings are enabled, GitHub does not technically require a Pull Request or passing status checks before a direct push. Contributors should still use Pull Requests; the maintainer should configure branch and tag protection in GitHub repository settings.

The repository uses [CODEOWNERS](.github/CODEOWNERS) to identify the maintainer as the default code owner. CODEOWNERS requests review routing; it does not replace branch protection.

## Adding maintainers

Additional maintainers may be appointed by the current maintainer after reviewing sustained, high-quality contributions and demonstrated understanding of the project's security and release constraints. Any change to signing-key custody or release authority must be explicitly documented and handled separately.

## Decision history

Technical proposals and their outcomes should be recorded in the relevant Issue or Pull Request. User-visible changes should be reflected in [CHANGELOG.md](CHANGELOG.md) and the corresponding release notes.

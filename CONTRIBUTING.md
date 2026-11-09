# Contributing to Vitrum

Thank you for considering a contribution. This document describes the expected workflow for changes to the Vitrum repository.

## Before you start
1. Search existing Issues and pull requests for related work.
2. For a substantial feature, architectural change or behaviour change, open an Issue first and agree on scope with the maintainer.
3. For small documentation, translation or isolated bug fixes, a focused pull request is welcome.
4. Read [AGENTS.md](AGENTS.md) and [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md). Their security and community rules apply to every contribution.
5. Check [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) before changing cross-component behaviour and [docs/TESTING.md](docs/TESTING.md) to select proportionate checks.

Do not bundle unrelated refactoring, formatting changes or dependency upgrades with a functional fix.

## Development principles
- Preserve existing architecture and naming unless the change requires otherwise.
- Keep changes small, reviewable and limited to the agreed scope.
- Do not change VPN, TUN, Xray, DNS, routing, persistence or failover semantics without a concrete defect and an agreed implementation plan.
- Do not add analytics, advertising, tracking, device identifiers or telemetry.
- Do not add root-dependent behaviour.
- Do not weaken certificate validation, HTTPS requirements, encrypted storage or kill-switch behaviour.
- Treat diagnostic uncertainty as unknown; do not present configuration analysis as proof of real network behaviour.
- Keep user-facing strings in the existing localization system.

## Build and checks

The project uses JDK 21, Android SDK Platform 35 and Build Tools 35.0.0. On Linux or WSL:

~~~bash
tools/SETUP_ENV.sh
tools/FETCH_BINARIES.sh --linux
tools/build.sh debug
~~~

Run the configured repository checks when relevant:

~~~bash
tools/build.sh tests
~~~

The helper writes logs to /tmp/vitrum-build/. See [README.md](README.md#build-from-source) and [docs/TESTING.md](docs/TESTING.md) for the current check matrix.

For documentation-only changes, Android compilation is normally unnecessary. For UI-only changes, explain what changed and which focused checks were run. VPN and network changes require evidence appropriate to the behaviour being changed; CI compilation alone is not runtime validation.

Do not claim physical-device tests unless they were actually performed. Device acceptance for VPN behaviour remains a separate step.

## Pull request checklist
- [ ] The PR has one clear purpose and matches the agreed scope.
- [ ] User-visible behaviour and compatibility impact are described.
- [ ] Relevant checks were run, or the reason they were not needed is stated.
- [ ] Documentation and translations were updated where needed.
- [ ] No secrets, real server links, subscription URLs, personal data or generated local files are included.
- [ ] No unrelated files or dependency updates are included.

## Support and project decisions

Read [SUPPORT.md](SUPPORT.md) before opening a request for help. Feature proposals and architectural changes should be discussed before implementation. The maintainer's decision process is documented in [GOVERNANCE.md](GOVERNANCE.md).

## Security-sensitive contributions

Never commit signing keys, passwords, access tokens, real server URLs, subscription links, private user data, device identifiers or diagnostic dumps containing sensitive data.

If you discover a security vulnerability, do not create a public Issue. Follow [SECURITY.md](SECURITY.md).

## Review and merging

The maintainer reviews scope, correctness, security implications and CI results. A passing workflow does not automatically approve or merge a contribution. The maintainer may request changes, narrow the scope or close a contribution that conflicts with the project's security constraints or direction.

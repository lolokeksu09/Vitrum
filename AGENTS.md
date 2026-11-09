# AI Agent Instructions (Claude Code, Codex, and Others)

Project status: `docs/HANDOFF.md`. Public release history: `CHANGELOG.md`. Read both before making changes.
Architecture, verification limits, and release procedure: `docs/ARCHITECTURE.md`, `docs/TESTING.md`, and `docs/RELEASING.md`. Documentation index: `docs/README.md`. Do not treat historical plans in `docs/` as the current backlog without checking `HANDOFF.md`.

## Working with the maintainer
1. Respond in Russian, concisely and directly.
2. Stay within the agreed scope; do not perform adjacent work without approval.
3. Distinguish source-code facts, CI results, external information, and assumptions.
4. The maintainer tests the app on a physical device. CI is not device QA.
5. Keep changes minimal. Explain consequences before breaking changes, migrations, or network-behavior changes.
6. Preserve exact project, file, variable, and technical names.
7. Never invent commands, URLs, branches, remotes, or verification results.

## Secrets
- Never print or commit the signing key `keystore/vitrum-release.jks`, its password, tokens, subscription URLs, real server links, or panel responses.
- Pass the password only through `VITRUM_KS_PASS`; prevent it from appearing in logs.
- Keep secrets and real user data outside the repository.
- Inspect changes for accidental secret exposure before committing.

## Security invariants
- Do not open service ports on 127.0.0.1. Xray ingress uses Unix sockets under `filesDir`; the TUN FD is passed through `XRAY_TUN_FD`.
- Server links and auto-server configurations on disk must remain encrypted (`Vault`, Keystore). Temporary configs containing keys must be deleted after use.
- Keep `allowBackup=false`; `debuggable` is allowed only in debug builds; unencrypted HTTP is prohibited; updates and subscriptions must use HTTPS.
- Do not present a warning as proof of a leak. Unknown results must not increase a protection score.
- If auto-server selection fails, traffic must not bypass the VPN through `direct`.
- External links and subscription URL changes require user confirmation.
- Do not add analytics, advertising, or trackers; do not send HWID or device data to a panel.

## Development constraints
- Do not implement root-required features without a separate maintainer request.
- Do not change VPN/TUN/DNS/routing semantics without a specific, confirmed defect.
- Do not change the signing key: updates must remain compatible with the existing certificate.
- Do not copy DUORAY code or text; Vitrum is licensed under GNU GPL v3.0.

## Build and verification
- Main Android build: `.github/workflows/build.yml`. It runs for tags matching `v*` (subject to workflow exclusions) or manually. It includes unit tests, Debug/Clone compilation, release APK validation, and maintainer-certificate verification.
- Vitrum Lite ARM32: `.github/workflows/lite.yml`. It runs for Lite-path changes in PRs or pushes to `main`, and manually. The APK is uploaded as an Actions artifact; this workflow does not create or overwrite a GitHub Release.
- Lite UI smoke: `.github/workflows/lite-ui-emulator.yml`. PR/manual, Android 6 x86 emulator; this is a UI check, not an ARM32/Xray runtime test.
- PR checks: `.github/workflows/pr-check.yml`. Runs for PRs and through `workflow_call`; uses the existing internal Clone build variant. Clone is not a separately published release.
- One-off `publish-v2-*`, `retry-v2`, and `xray-runtime-probe` workflows were removed. Do not restore them for future releases.
- GitHub Actions secrets (`VITRUM_KS_BASE64`, `VITRUM_KS_PASS`) are configured by the maintainer. Agents must not read or print them. Official APKs must use the existing maintainer certificate.
- Local setup: `tools/SETUP_ENV.sh`, `tools/FETCH_BINARIES.sh --linux`, `tools/build.sh tests|debug|release`. A local release requires the maintainer keystore and `VITRUM_KS_PASS`.
- Verify test results using XML reports (`app/build/test-results/testDebugUnitTest/*.xml`), not only the final Actions status line.
- Validate Xray configs with the actual Xray binary (`xray run -test`); inspect live Xray routes through the Unix socket.
- On Windows, use WSL or Git Bash; `tools/*.sh` scripts require bash.

## Release procedure
1. Check `versionCode` and `versionName` in `app/build.gradle.kts`; check the Xray version in `Brand.kt` and `tools/FETCH_BINARIES.sh`.
2. Add new Russian strings to `app/src/main/res/raw/i18n_en.tsv`; run `python3 tools/i18n/dict_cov.py`.
3. Run tests and builds relevant to the changes.
4. Verify the APK signature with `apksigner verify --print-certs`. Current certificate SHA-256: `8cb8b675f97239948f0b93ac4208464e856397d6141268798129c00337205c61`.
5. Update `CHANGELOG.md`, `README.md`, `docs/HANDOFF.md`, and the new release notes with SHA-256.
6. Never publish a debug APK as official or put the key/password in an APK or repository.

## Code map
Under `app/src/main/java/app/rayclient/`: `Links` parses links; `ConfigBuilder` builds Xray configs; `RayVpnService` is the VPN service; `Failover` and `Scenarios` implement switching logic; `Shield` handles diagnostics; `AppState`, `Subscriptions`, `Profiles`, `Dns`, and `Updates` manage state; `Vault` handles encryption; `I18n` and `Texts` handle translations; `Integrity` verifies signatures; `*Screen` files implement screens. See `docs/HANDOFF.md` for current context.

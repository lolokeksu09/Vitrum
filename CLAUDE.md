# Vitrum: Claude Code Instructions

Full rules: @AGENTS.md  
Current project status: `docs/HANDOFF.md`.  
Public release history: `CHANGELOG.md` and GitHub Releases.

- Respond in Russian and be concise; preserve exact file, variable, and technology names.
- Distinguish source-code facts, CI results, and physical-device verification.
- Do not claim device testing; the maintainer performs it.
- Never print or commit the signing key, password, tokens, subscription URLs, or real server data.
- Follow the security invariants in `AGENTS.md`.
- Do not change VPN/TUN/DNS/routing semantics without a specific defect and separate approval.
- Main build: `.github/workflows/build.yml`; Lite ARM32: `.github/workflows/lite.yml`; PR checks: `.github/workflows/pr-check.yml`.
- Obsolete publisher/retry/probe workflows have been removed. Do not use historical release notes as a description of the current state.
- Build commands: `tools/SETUP_ENV.sh`, `tools/FETCH_BINARIES.sh --linux`, `tools/build.sh tests|debug|release`.

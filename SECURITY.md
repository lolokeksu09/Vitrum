# Security Policy

Only the latest release is supported (see GitHub Releases).

## Reporting a vulnerability

Use the repository's **Security → Advisories → Report a vulnerability** flow if GitHub Private Vulnerability Reporting is enabled. If the private-reporting option is unavailable, open a public Issue requesting a private contact method **without** disclosing exploit details, credentials, subscription URLs or proof-of-concept code. The maintainer can then arrange a private reporting channel. Do not disclose vulnerability details in a public Issue.

## Secrets

Never commit secrets. The repository must not contain the signing key (`*.jks`), its password, subscription addresses or tokens, or real server links. If a secret enters repository history, treat it as compromised: rotate it and remove it from history.

## Verifying official builds

Vitrum's release certificate SHA-256 fingerprint is:

`8cb8b675f97239948f0b93ac4208464e856397d6141268798129c00337205c61`

The fingerprint is shown in the app (Settings → About) and can be checked with:

`apksigner verify --print-certs Vitrum-X.Y.Z.apk`

An APK signed with a different fingerprint is not an official maintainer release.

# Security Policy

Only the latest release is supported (see GitHub Releases).

## Reporting a vulnerability

Contact the maintainer through GitHub private messages or use the repository's private vulnerability reporting feature under the Security tab, if enabled. Do not publish vulnerability details in Issues before a fix is available.

## Secrets

Never commit secrets. The repository must not contain the signing key (`*.jks`), its password, subscription addresses or tokens, or real server links. If a secret enters repository history, treat it as compromised: rotate it and remove it from history.

## Verifying official builds

Vitrum's release certificate SHA-256 fingerprint is:

`8cb8b675f97239948f0b93ac4208464e856397d6141268798129c00337205c61`

The fingerprint is shown in the app (Settings → About) and can be checked with:

`apksigner verify --print-certs Vitrum-X.Y.Z.apk`

An APK signed with a different fingerprint is not an official maintainer release.

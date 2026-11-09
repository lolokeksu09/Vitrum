# Release process

Official releases are controlled by the project maintainer. Do not publish an APK as an official Vitrum update unless its package identity and signing certificate have been verified.

## 1. Prepare the version

For the main application:

1. Update `versionName` and monotonically increasing `versionCode` in `app/build.gradle.kts`.
2. Verify the Xray version is consistent between `Brand.kt`, `tools/FETCH_BINARIES.sh` and `tools/binaries.sha256`.
3. Update `docs/releases/vX.Y.Z.md` with the scope, compatibility, known limitations and release checks.
4. Update `CHANGELOG.md` and both README files where the current version, downloads or supported behaviour changes.
5. Check that the release notes do not claim device acceptance or leak resistance without recorded evidence.

For Lite, use `lite/build.gradle.kts` and the independent Lite documentation. Lite has its own package ID, ABI, minimum Android version and signing implications.

## 2. Validate the candidate

Run the checks relevant to the change. The main release workflow includes unit tests, Debug/Clone compilation, Clone instrumentation compilation, Xray runtime asset checksum verification and official APK signing verification.

Physical-device acceptance is a separate step. For VPN/network-sensitive changes, use the relevant manual matrix in [TESTING.md](TESTING.md) and record the tested APK hash and environment.

## 3. Sign the official main APK

The official main APK must be signed with the existing author key. GitHub Actions reads the repository secrets `VITRUM_KS_BASE64` and `VITRUM_KS_PASS`; the keystore is materialized only during the job and removed afterwards.

The expected certificate SHA-256 is:

```
8cb8b675f97239948f0b93ac4208464e856397d6141268798129c00337205c61
```

Never commit the keystore or password. Never replace the key as a shortcut. A differently signed APK is not an in-place update for users of the official build and must not be labelled as an official release.

## 4. Publish the main release

The current `.github/workflows/build.yml` supports eligible version-tag pushes and manual dispatch.

For a manual release dispatch:
- `tag` is the intended tag such as `v2.1.0`;
- for a **new tag**, provide `source_ref` as the exact branch or, preferably, immutable commit SHA to build; the workflow validates that `versionName` matches the tag before creating the release tag;
- if `source_ref` is left empty, the workflow checks out the tag supplied in `tag` and requires that tag to already exist and match the checked-out source;
- `stable` defaults to `false`, which keeps a newly created release as a pre-release.

The workflow verifies that `versionName` matches the tag when a source ref is supplied, verifies the author signing certificate, uploads the author-signed APK and a separately signed debug fallback, and records SHA-256 hashes in the release notes.

Set `stable=true` only after the maintainer has accepted the candidate on the intended physical device(s). A debug fallback is for diagnosis only and must never be presented as an official update.

Existing stable releases are guarded by the workflow: they must not be silently converted or overwritten through an ordinary pre-release dispatch. Do not move or force-update an existing version tag. Publish a corrected build under a new patch version and explain the change.

## 5. Lite releases

The current `.github/workflows/lite.yml` builds Lite and uploads an Actions artifact; it does not create or overwrite a GitHub Release. The emulator workflow is UI-only. Do not assume that a green ARM32 build proves physical Android 6 networking.

The published `lite-v1.0.0` is a separate package and signing lineage from the main application. Any future Lite publication must explicitly state its signing identity and whether it can update the preceding Lite APK.

## 6. After publication

- Verify the tag points to the intended source commit.
- Verify the official APK's package ID, version, ABI and author certificate.
- Compare uploaded assets against the SHA-256 values in release notes.
- Verify the Release title, notes and pre-release/stable status.
- Update [HANDOFF.md](HANDOFF.md) and [CHANGELOG.md](../CHANGELOG.md) if the current repository or public release state changed.
- Do not edit release assets in place after users have downloaded them; publish a new patch release when a correction is needed.

## Release integrity

Git tags and release assets are part of the software supply chain. On 2026-11-09, the public Releases API reported `immutable=false` for the current releases. Until repository release immutability is enabled, the maintainer must treat tag protection, asset hash verification and controlled release permissions as required safeguards.

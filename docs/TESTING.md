# Testing and verification

This document separates automated checks from claims that require a real Android device. CI results apply only to the commit and environment shown by the corresponding workflow run.

## Pull Request checks

`.github/workflows/pr-check.yml` runs the current focused PR pipeline:

1. focused Clone JVM tests for route explanation and rule validation;
2. full Clone JVM unit tests and JUnit XML reports;
3. retrieval and checksum verification of Xray runtime assets;
4. translation coverage validation;
5. shell syntax validation for the Android 16 Clone UI matrix script;
6. Debug and Clone APK compilation, unsigned release build, and Clone instrumentation compilation;
7. Clone package identity/metadata verification and artifact upload.

This is deliberately a focused pipeline. It is not a complete test suite for every app feature.

## Main release workflow

`.github/workflows/build.yml` runs for eligible version tags or manual dispatch. The main build job runs unit tests, Debug/Clone compilation and Clone instrumentation compilation, then builds the release APK only with the configured author signing key and verifies the expected certificate SHA-256.

The workflow may publish or update a GitHub Release after the build succeeds. A green workflow is not proof of physical-device VPN behaviour. The release must not be promoted to stable until device acceptance has been performed by the maintainer.

## Vitrum Lite

- `.github/workflows/lite.yml` builds the Android ARM32 Xray/JNI runtime, runs Lite JVM tests, assembles the Lite APK, checks its package/minimum SDK/ABI/signature metadata and uploads an Actions artifact.
- `.github/workflows/lite-ui-emulator.yml` runs a UI smoke test using an Android 6 x86 emulator build. This is a UI test only; it is not an ARM32 device test and does not execute the production ARM32 Xray binary on hardware.

## Local build commands

From the repository root on Linux or WSL with the required JDK/Android SDK:

```bash
tools/SETUP_ENV.sh
tools/FETCH_BINARIES.sh --linux
tools/build.sh debug
```

The build helper writes logs under `/tmp/vitrum-build/`. For the configured unit and Clone compilation checks:

```bash
tools/build.sh tests
```

For a Lite ARM32 build, use the pinned Go/NDK requirements documented in [VITRUM_LITE_ARM32.md](VITRUM_LITE_ARM32.md) and run:

```bash
bash tools/build-lite-arm32.sh
./gradlew :lite:testDebugUnitTest :lite:assembleDebug
bash tools/verify-lite-apk.sh lite/build/outputs/apk/debug/lite-debug.apk
```

## Manual and device verification

Use [ANDROID_RUNTIME_CHECKS.md](ANDROID_RUNTIME_CHECKS.md) for the main-app manual runtime matrix and [VITRUM_LITE_ARM32.md](VITRUM_LITE_ARM32.md) for Lite-specific hardware checks.

Record at least the following when reporting device acceptance:
- exact APK filename and SHA-256;
- repository commit or release tag;
- Android version, device model and relevant firmware;
- network type and routing configuration;
- action sequence and observed result;
- PASS, FAIL or UNKNOWN for each scenario.

Do not mark a scenario as passed based only on compilation, emulator UI output, a successful Xray config parse or an HTTP response from an internal diagnostic socket.

## What CI does not prove

CI does not prove:
- absence of DNS, IPv4 or IPv6 leaks on all networks;
- correct routing for every installed application and user exclusion;
- stable operation through every Wi-Fi/mobile transition or device sleep cycle;
- compatibility with every third-party server configuration;
- thermal, memory or battery performance on every supported device;
- physical ARM32 Xray runtime behaviour on the Lite target tablet.

Use UNKNOWN where evidence is unavailable. Do not convert an untested scenario into a PASS.

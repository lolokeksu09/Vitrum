#!/usr/bin/env bash
# Refuse releases if tag, checked-out source and Android version disagree.
set -euo pipefail
TAG="${1:?Release tag is required}"
[[ "$TAG" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "Invalid release tag: $TAG" >&2; exit 1; }
[ "$(git rev-parse HEAD)" = "$(git rev-parse "refs/tags/$TAG^{commit}")" ] || { echo "Checkout does not match release tag $TAG" >&2; exit 1; }
ACTUAL="$(sed -nE 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"([^"]+)".*$/\1/p' app/build.gradle.kts)"
[ "$ACTUAL" = "${TAG#v}" ] || { echo "versionName (${ACTUAL:-missing}) does not match $TAG" >&2; exit 1; }
echo "Release source and version match $TAG"

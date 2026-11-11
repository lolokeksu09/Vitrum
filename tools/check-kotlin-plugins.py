#!/usr/bin/env python3
"""Check that the Kotlin Android and Compose compiler Gradle plugins match."""
from pathlib import Path
import re
import sys

source = Path(__file__).resolve().parents[1] / "build.gradle.kts"
text = source.read_text(encoding="utf-8")
pattern = re.compile(
    r'^\s*id\("(?P<name>org\.jetbrains\.kotlin\.(?:android|plugin\.compose))"\)'
    r'\s+version\s+"(?P<version>[^"]+)"\s+apply\s+false\s*$',
    re.MULTILINE,
)
found = {}
for match in pattern.finditer(text):
    name = match.group("name")
    if name in found:
        sys.exit(f"Duplicate Kotlin Gradle plugin declaration: {name}")
    found[name] = match.group("version")
expected = ("org.jetbrains.kotlin.android", "org.jetbrains.kotlin.plugin.compose")
missing = [name for name in expected if name not in found]
if missing:
    sys.exit(f"Missing Kotlin Gradle plugin declaration(s): {', '.join(missing)}")
if found[expected[0]] != found[expected[1]]:
    sys.exit(
        "Kotlin Android and Compose compiler plugin versions differ: "
        f"{found[expected[0]]} vs {found[expected[1]]}"
    )
print(f"Kotlin plugins aligned: {found[expected[0]]}")

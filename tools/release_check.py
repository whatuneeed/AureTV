#!/usr/bin/env python3
"""Release artifact gate for AURE TV.

Checks the distributable APK, not the source tree:
  * versionCode/versionName/applicationId from output-metadata.json
    match app/build.gradle.kts;
  * APK signature is v2 and the signing certificate is the expected one;
  * minSdk/targetSdk are the supported values;
  * EN and RU resource names are in parity;
  * prints size and SHA-256 for the evidence file.

Run from the Kotlin project root after `assembleRelease`:

    python tools/release_check.py
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

EXPECTED_CERT = "327a50453093b783e84b1f2645b03319477d89966ea6a653dab53d5a567d7b10"
EXPECTED_APPLICATION_ID = "app.aure.tv"
EXPECTED_MIN_SDK = 28
EXPECTED_TARGET_SDK = 35


def fail(message: str) -> None:
    print(f"FAIL  {message}")
    global failed
    failed = True


def ok(message: str) -> None:
    print(f"ok    {message}")


def read_gradle(path: Path) -> dict[str, object]:
    text = path.read_text(encoding="utf-8")
    values: dict[str, object] = {}
    for key in ("versionCode", "versionName", "minSdk", "targetSdk"):
        match = re.search(rf"\b{key}\s*=\s*(\d+|\"[^\"]*\")", text)
        if not match:
            raise SystemExit(f"cannot read {key} from {path}")
        raw = match.group(1)
        values[key] = raw.strip('"') if raw.startswith('"') else int(raw)
    return values


def resource_names(path: Path) -> set[str]:
    root = ET.parse(path).getroot()
    names: set[str] = set()
    for child in root:
        if not child.tag.split("}")[-1] in {"string", "plurals", "string-array"}:
            continue
        name = child.get("name")
        if name:
            names.add(name)
    return names


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--apk",
        default="app/build/outputs/apk/release/app-release.apk",
        help="release APK path",
    )
    parser.add_argument(
        "--apksigner",
        default=os.environ.get("APKSIGNER", "apksigner"),
        help="apksigner executable",
    )
    parser.add_argument(
        "--skip-locales",
        action="store_true",
        help="skip the RU/EN resource parity check",
    )
    args = parser.parse_args()

    root = Path.cwd()
    apk = (root / args.apk).resolve()
    gradle = read_gradle(root / "app/build.gradle.kts")
    metadata_path = apk.parent / "output-metadata.json"

    if not apk.is_file():
        fail(f"APK not found: {apk}")
        return 1
    if not metadata_path.is_file():
        fail(f"output-metadata.json not found: {metadata_path}")
        return 1

    metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    element = metadata["elements"][0]
    checked_version_code = int(element["versionCode"])
    checked_version_name = element["versionName"]
    checked_app_id = metadata["applicationId"]
    size = apk.stat().st_size
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()

    print(f"APK   {apk}")
    print(f"size  {size} bytes")
    print(f"sha256 {digest}")

    if checked_version_code == gradle["versionCode"]:
        ok(f"versionCode {checked_version_code}")
    else:
        fail(f"versionCode {checked_version_code} != gradle {gradle['versionCode']}")
    if checked_version_name == gradle["versionName"]:
        ok(f"versionName {checked_version_name}")
    else:
        fail(f"versionName {checked_version_name} != gradle {gradle['versionName']}")
    if checked_app_id == EXPECTED_APPLICATION_ID:
        ok(f"applicationId {checked_app_id}")
    else:
        fail(f"applicationId {checked_app_id} != {EXPECTED_APPLICATION_ID}")

    if gradle["minSdk"] == EXPECTED_MIN_SDK:
        ok(f"minSdk {EXPECTED_MIN_SDK}")
    else:
        fail(f"minSdk {gradle['minSdk']} != {EXPECTED_MIN_SDK}")
    if gradle["targetSdk"] == EXPECTED_TARGET_SDK:
        ok(f"targetSdk {EXPECTED_TARGET_SDK}")
    else:
        fail(f"targetSdk {gradle['targetSdk']} != {EXPECTED_TARGET_SDK}")

    command = [args.apksigner, "verify", "--print-certs", "--verbose", str(apk)]
    if os.name == "nt":
        command = ["cmd", "/c"] + command
    try:
        result = subprocess.run(command, capture_output=True, text=True, check=False)
    except FileNotFoundError:
        fail(f"apksigner not found: {args.apksigner}")
        return 1

    output = result.stdout + result.stderr
    if result.returncode != 0:
        fail("apksigner verify failed:\n" + output)
    else:
        ok("apksigner verify")
    if re.search(r"Verified using v2 scheme .*: true", output):
        ok("APK Signature Scheme v2")
    else:
        fail("APK is not v2-signed")
    if EXPECTED_CERT in output:
        ok("signing certificate")
    else:
        fail(f"certificate does not match {EXPECTED_CERT}")

    if not args.skip_locales:
        english = resource_names(root / "app/src/main/res/values/strings.xml")
        russian = resource_names(root / "app/src/main/res/values-ru/strings.xml")
        if english == russian:
            ok(f"RU/EN resource names ({len(english)} entries)")
        else:
            fail(
                "RU/EN resource names differ: only EN "
                f"{sorted(english - russian)}, only RU {sorted(russian - english)}",
            )

    print("\nEvidence:")
    print(f"  sha256={digest}")
    print(f"  certificate={EXPECTED_CERT}")
    return 1 if failed else 0


failed = False

if __name__ == "__main__":
    sys.exit(main())

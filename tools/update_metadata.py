#!/usr/bin/env python3
"""Generate the in-app update metadata from a signed AURE TV APK.

The launcher never trusts the latest GitHub release blindly. This script turns
one published APK into the small, strict JSON document the launcher already
validates: exact versionCode/versionName, a bounded HTTPS asset URL, its size
and SHA-256. The release workflow runs it after an APK is attached to a GitHub
release and commits docs/update.json, which GitHub Pages serves.

Example:
    python tools/update_metadata.py \
      --apk GITHUB_RELEASE/AURE-TV-0.6.0.apk \
      --url https://github.com/whatuneeed/AureTV/releases/download/v0.6.0/AURE-TV-0.6.0.apk \
      --aapt C:/Android/sdk/build-tools/35.0.0/aapt.exe
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path

EXPECTED_PACKAGE = "app.aure.tv"
MAX_NOTES = 20
MAX_NOTE_CHARS = 200


def fail(message: str) -> None:
    print(f"FAIL  {message}", file=sys.stderr)
    raise SystemExit(1)


def find_aapt(explicit: str | None) -> str:
    candidates = [
        explicit,
        os.environ.get("AAPT"),
        shutil.which("aapt"),
        shutil.which("aapt2"),
    ]
    for candidate in candidates:
        if candidate and Path(candidate).is_file():
            return str(candidate)
    fail("aapt not found; pass --aapt or set AAPT")


def read_badging(apk: Path, aapt: str) -> tuple[str, int, str]:
    command = [aapt, "dump", "badging", str(apk)]
    result = subprocess.run(command, capture_output=True, text=True, check=False)
    if result.returncode != 0:
        fail(f"aapt failed:\n{result.stdout}{result.stderr}")
    first = result.stdout.splitlines()[0] if result.stdout else ""
    match = re.search(
        r"package: name='([^']+)' versionCode='(\d+)'(?: versionName='([^']*)')?",
        first,
    )
    if not match:
        fail(f"cannot read package metadata from {apk}: {first}")
    package, version_code, version_name = match.groups()
    if package != EXPECTED_PACKAGE:
        fail(f"package is {package}, expected {EXPECTED_PACKAGE}")
    if int(version_code) <= 0:
        fail(f"invalid versionCode: {version_code}")
    return package, int(version_code), version_name or ""


def one_line(value: str) -> str:
    line = " ".join(value.split()).strip()
    if not line or len(line) > MAX_NOTE_CHARS:
        fail(f"release note must be 1..{MAX_NOTE_CHARS} characters")
    return line


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True, help="signed release APK")
    parser.add_argument("--url", required=True, help="HTTPS APK asset URL")
    parser.add_argument("--output", default="docs/update.json")
    parser.add_argument("--aapt", default=None)
    parser.add_argument("--channel", default="stable", choices=["stable", "beta"])
    parser.add_argument("--minimum-android-api", type=int, default=28)
    parser.add_argument("--minimum-supported-version-code", type=int, default=0)
    parser.add_argument("--release-date", default="")
    parser.add_argument(
        "--previous-metadata",
        default=None,
        help="existing channel JSON; refuse to publish a non-increasing versionCode",
    )
    parser.add_argument("--expires-days", type=int, default=3650)
    parser.add_argument("--notes-ru", action="append", default=[])
    parser.add_argument("--notes-en", action="append", default=[])
    args = parser.parse_args()

    apk = Path(args.apk).resolve()
    if not apk.is_file():
        fail(f"APK not found: {apk}")
    if not args.url.lower().startswith("https://"):
        fail("asset URL must use HTTPS")
    if not 1 <= args.minimum_android_api <= 100:
        fail("invalid minimum Android API")

    aapt = find_aapt(args.aapt)
    _, version_code, version_name = read_badging(apk, aapt)
    if not version_name:
        fail("APK has no versionName")

    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    size = apk.stat().st_size

    if args.previous_metadata:
        previous_path = Path(args.previous_metadata)
        if previous_path.is_file():
            try:
                previous = json.loads(previous_path.read_text(encoding="utf-8"))
                previous_release = max(
                    previous["releases"],
                    key=lambda release: int(release["versionCode"]),
                )
                previous_code = int(previous_release["versionCode"])
            except (KeyError, TypeError, ValueError, json.JSONDecodeError) as error:
                fail(f"cannot read previous metadata {previous_path}: {error}")
            if version_code < previous_code:
                fail(
                    f"versionCode {version_code} is older than published "
                    f"versionCode {previous_code} in {previous_path}",
                )
            if version_code == previous_code:
                # A retried workflow may regenerate identical metadata, but it
                # must never replace the same version with a different APK.
                previous_assets = previous_release.get("assets") or []
                same_asset = len(previous_assets) == 1 and (
                    previous_assets[0].get("sha256") == digest
                    and int(previous_assets[0].get("sizeBytes", -1)) == size
                )
                if not same_asset:
                    fail(
                        f"versionCode {version_code} already exists with a different APK "
                        f"in {previous_path}",
                    )

    notes_ru = [one_line(n) for n in (args.notes_ru or [
        f"Версия {version_name}. Подробности — на странице релиза GitHub.",
    ])]
    notes_en = [one_line(n) for n in (args.notes_en or [
        f"Version {version_name}. See the GitHub release page for details.",
    ])]
    if not 1 <= len(notes_ru) <= MAX_NOTES or not 1 <= len(notes_en) <= MAX_NOTES:
        fail("invalid number of release-note lines")

    now = datetime.now(timezone.utc).replace(microsecond=0)
    release_date = args.release_date or now.isoformat().replace("+00:00", "Z")
    if not release_date.endswith("Z"):
        release_date = release_date.replace("+00:00", "Z")
    expires = (now + timedelta(days=args.expires_days)).isoformat().replace("+00:00", "Z")

    document = {
        "schema": 1,
        "channel": args.channel,
        "generatedAt": now.isoformat().replace("+00:00", "Z"),
        "expiresAt": expires,
        "releases": [
            {
                "versionCode": version_code,
                "versionName": version_name,
                "channel": args.channel,
                "releaseDate": release_date,
                "minimumAndroidApi": args.minimum_android_api,
                "minimumSupportedVersionCode": max(0, args.minimum_supported_version_code),
                "mandatory": False,
                "notes": {"ru": notes_ru, "en": notes_en},
                "assets": [
                    {
                        "abi": "universal",
                        "url": args.url,
                        "sha256": digest,
                        "sizeBytes": size,
                    }
                ],
            }
        ],
    }

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {output}")
    print(f"version {version_name} ({version_code})")
    print(f"size    {size} bytes")
    print(f"sha256  {digest}")
    print(f"asset   {args.url}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

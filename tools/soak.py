#!/usr/bin/env python3
"""Short soak for OVisionTV_Kotlin on a connected emulator/device.

Cycles Home, Settings, Control Center and Search for N minutes, samples the
process PSS and fails when the crash buffer gains an app entry. It does not
assert visual correctness; the gate for that is the connected test suite and
the manual smoke.

    python tools/soak.py --minutes 5
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
import time

PACKAGE = "com.whatuneed.ovisiontv.kotlin"
ACTIVITY = f"{PACKAGE}/.MainActivity"
SETTINGS = f"{PACKAGE}/.SettingsActivity"


def run(adb: str, *args: str) -> str:
    result = subprocess.run(
        [adb, "-s", "emulator-5554", *args],
        capture_output=True,
        text=True,
        check=False,
    )
    return result.stdout + result.stderr


def key(adb: str, code: str) -> None:
    run(adb, "shell", "input", "keyevent", code)


def pss(adb: str) -> str:
    output = run(adb, "shell", "dumpsys", "meminfo", PACKAGE)
    match = re.search(r"TOTAL PSS:\s*(\d+)", output)
    return match.group(1) if match else "?"


def crash_lines(adb: str) -> list[str]:
    output = run(adb, "shell", "logcat", "-b", "crash", "-d")
    return [line for line in output.splitlines() if line.strip()]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--minutes", type=float, default=5.0)
    parser.add_argument("--cycle-seconds", type=float, default=20.0)
    args = parser.parse_args()

    run(args.adb, "shell", "am", "start", "-n", ACTIVITY)
    time.sleep(3)
    start_crashes = crash_lines(args.adb)
    start_pss = pss(args.adb)
    started = time.time()
    cycles = 0
    print(f"soak start: pss={start_pss} crash_lines={len(start_crashes)}")

    while time.time() - started < args.minutes * 60:
        run(args.adb, "shell", "am", "start", "-n", ACTIVITY)
        time.sleep(1)
        key(args.adb, "KEYCODE_DPAD_UP")
        key(args.adb, "KEYCODE_DPAD_CENTER")  # Control Center
        time.sleep(2)
        key(args.adb, "KEYCODE_BACK")
        time.sleep(1)
        run(args.adb, "shell", "am", "start", "-n", SETTINGS)
        time.sleep(2)
        key(args.adb, "KEYCODE_BACK")
        time.sleep(1)
        key(args.adb, "KEYCODE_SEARCH")
        time.sleep(1)
        key(args.adb, "KEYCODE_BACK")
        time.sleep(1)
        cycles += 1
        if cycles % 3 == 0:
            print(
                f"  {cycles} cycles, elapsed={int(time.time() - started)}s, "
                f"pss={pss(args.adb)}",
            )
        time.sleep(args.cycle_seconds)

    end_crashes = crash_lines(args.adb)
    end_pss = pss(args.adb)
    new_crashes = [line for line in end_crashes if line not in start_crashes]
    app_crashes = [line for line in new_crashes if PACKAGE in line]
    print(f"soak end: cycles={cycles} pss={start_pss}->{end_pss}")
    print(f"crash lines: start={len(start_crashes)} end={len(end_crashes)} new={len(new_crashes)}")
    if app_crashes:
        print("APP CRASHES:")
        print("\n".join(app_crashes))
        return 1
    print("ok: no new app crash entries")
    return 0


if __name__ == "__main__":
    sys.exit(main())

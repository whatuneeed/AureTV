# AURE TV 0.6.3

A hotfix for the in-app update permission flow.

## What changed

- **Returning from the unknown-sources screen now refreshes immediately.**
  The update screen re-reads `canRequestPackageInstalls()` on `ON_RESUME`
  instead of waiting for a state change that never came. After permission is
  granted, the install action appears without leaving and reopening
  **Settings → Device → Software Update**.
- **The stale permission prompt is removed once access is granted.** The page
  switches from “Allow installs” / “Open install settings” to
  “Ready to install” and moves focus to **Install update**.
- **Installation still requires the Android confirmation dialog.** AURE TV
  never installs silently.
- Version **0.6.3** (`versionCode 157`), signed with the same release key.

## Verification

- JVM unit tests: **588 / 588**, 0 failures, 0 errors, 0 skipped
- Android lint: **0 errors, 16 warnings**
- Release APK: `app.aure.tv`, **0.6.3** (`versionCode 157`), APK Signature
  Scheme v2, same signing certificate
- Release gate: package, SDK levels, v2 signature, certificate and RU/EN
  resource names pass
- Emulator flow: release-signed test build **0.6.1 (155)** downloaded published
  **0.6.2**, was sent to unknown-sources settings, and after returning showed
  **Install update** immediately. The system dialog then installed
  **0.6.2 (156)** successfully.
- Final **0.6.3 (157)** APK installed over 0.6.2 and booted; the update screen
  reports 0.6.3 (157).
- In-app update **from 0.6.2 to 0.6.3** is **not tested yet** because 0.6.3 is
  not published at the time of these notes.

Physical Android TV hardware, real remotes, 4K/HDR and TalkBack remain
unverified. The emulator result is not a substitute for a living-room TV test.

## Install

Requirements: Android TV 9+ (API 28). No Google account is required for the
launcher itself.

```bash
adb install AURE-TV-0.6.3.apk
```

Builds from 0.6.1 onward can update from
**Settings → Device → Software Update** after this release is published.

## Checksum

```text
762fec93a9313c3f76abcbeb2ae57cd7e2f5171cf645d92d5ecdaa3ca43d57d6  AURE-TV-0.6.3.apk
```

Size: 6,961,042 bytes.

AURE TV is licensed under Apache-2.0. Aerial clips come from Apple's public CDN;
AURE TV is not affiliated with Apple. Third-party product names belong to their
owners.

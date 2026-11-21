# AURE TV 0.6.2

A maintenance and stability release. It adds no new launcher features.

## What changed

- **Backup cancellation no longer becomes a fake failure.** Leaving the Backup
  screen now rethrows `CancellationException` instead of updating a disposed
  screen. A cancelled restore still rolls back any partially applied data, then
  preserves the cancellation.
- **Home-button lookup no longer holds a lock during a PackageManager query.**
  The Accessibility service now queries Home activities outside the publish
  lock and atomically replaces a cached snapshot. The event thread never waits
  behind a slow package scan.
- **Stricter backup encryption headers.** A restored archive must carry a
  32-byte salt and a 12-byte nonce; oversized Base64 parameters are rejected
  before decoding or PBKDF2.
- **Update channel cannot silently move backwards.** The metadata generator
  refuses an older `versionCode`, or a same-version retry carrying a different
  APK, and the release workflow requires exactly one APK asset.
- **CI now builds and lints the release variant** in addition to debug lint and
  JVM tests.
- **Update documentation corrected.** The channel uses HTTPS, a strict host
  allowlist, SHA-256 and an APK signer match; the metadata JSON itself is not
  claimed to be cryptographically signed.
- Version **0.6.2** (`versionCode 156`), signed with the same release key.

## Verification

- JVM unit tests: **588 / 588**, 0 failures, 0 errors, 0 skipped
- Android lint: **0 errors, 17 warnings**
- Release APK: `app.aure.tv`, **0.6.2** (`versionCode 156`), APK Signature
  Scheme v2, same signing certificate
- Release gate: package, SDK levels, v2 signature, certificate and RU/EN
  resource names pass
- Connected instrumentation tests: **305 / 305** on the `hume-tv` Android TV
  emulator (API 34)
- Full download-and-install of a newer published APK: **not tested**

Physical Android TV hardware, real remotes, 4K/HDR and TalkBack remain
unverified. The emulator result is not a substitute for a living-room TV test.

## Install

Requirements: Android TV 9+ (API 28). No Google account is required for the
launcher itself.

```bash
adb install AURE-TV-0.6.2.apk
```

Builds from 0.6.1 can update from **Settings → Device → Software Update** after
this release is published. The metadata is not written until the release is
live.

## Checksum

```text
fb27b0902ed3bc8a04740d50916074b43e10785508e991a655e7bce031cde4e7  AURE-TV-0.6.2.apk
```

Size: 6,961,038 bytes.

AURE TV is licensed under Apache-2.0. Aerial clips come from Apple's public CDN;
AURE TV is not affiliated with Apple. Third-party product names belong to their
owners.

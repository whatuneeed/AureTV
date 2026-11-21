# AURE TV 0.6.1

A maintenance release that turns on the in-app update channel.

## What changed

- **In-app updates are now configured.** Builds from 0.6.1 check the signed
  release metadata at `https://whatuneeed.github.io/AureTV/update.json`.
- The metadata is generated from the APK attached to a published GitHub release.
  No release asset is trusted without a matching size and SHA-256.
- The downloader uses HTTPS only, stays on the documented GitHub hosts, and
  delegates the final confirmation to Android's PackageInstaller. Updates are
  never silent.
- **Privacy policy version 12** names the GitHub Pages metadata host and every
  GitHub download host used by the updater.
- Version **0.6.1** (`versionCode 155`), signed with the same release key.

## Important

The published 0.6.0 build predates this channel, so it cannot update itself.
Install 0.6.1 manually once; later versions can be installed from
**Settings → Device → Software Update**.

## Verification

- JVM unit tests: **583 / 583**
- Release APK: `app.aure.tv`, **0.6.1** (`versionCode 155`), signed with
  APK Signature Scheme v2
- Update screen checked on the `hume-tv` Android TV emulator (API 34, x86,
  1920×1080): source configured, manual check succeeds
- Full download-and-install of a newer published APK: **not tested yet**

Physical Android TV hardware, real remotes, 4K/HDR and TalkBack remain
unverified.

## Install

Requirements: Android TV 9+ (API 28). No Google account is required for the
launcher itself.

```bash
adb install AURE-TV-0.6.1.apk
```

## Checksum

```text
5f7ae0cc1d3aebb3942b0dfe8946612cf80320965f0806192c6a9d46948f74df  AURE-TV-0.6.1.apk
```

Size: 6,961,038 bytes.

AURE TV is licensed under Apache-2.0. Aerial clips come from Apple's public CDN;
AURE TV is not affiliated with Apple. Third-party product names belong to their
owners.

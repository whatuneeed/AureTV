# AURE TV branding assets

Source files for the launcher identity. The Android package and
`applicationId` remain `com.whatuneed.ovisiontv.kotlin`; only the user-facing
name and artwork changed.

## Files

- `aure_icon.svg` — textless launcher icon, 1024 master (rounded squircle)
- `aure_icon_1024.png` / `aure_icon_512.png` — store and reference exports
- `aure_icon_full_1024.png` — full-bleed artwork for adaptive masks
- `background_432.png`, `foreground_432.png` — Android adaptive icon layers
- `aure_mono_192.png` — monochrome/themed icon
- `aure_banner_1280x720.png`, `aure_banner_640x360.png`, `aure_banner_320x180.png` — TV banner masters
- `aure_icon_circle_512.png` — circular reference export

The launcher icon intentionally has **no text**: Android TV already draws the
app name under the tile. The wordmark appears in the banner and store listing.
Do not change the package or signing key during a rename; updates and existing
backups depend on them.

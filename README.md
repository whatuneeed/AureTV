# OVisionTV Launcher

Android TV launcher with a tvOS-inspired Home, app folders, profiles, weather,
Aerial ambience, a local backup/restore stack and an optional in-app updater.

- **Package:** `com.whatuneed.ovisiontv.kotlin`
- **Min SDK:** 28
- **Compile/target SDK:** 35
- **Language/UI:** Kotlin, Jetpack Compose for TV
- **License:** Apache 2.0; third-party notices are in [`NOTICE`](NOTICE)

## Build

The project uses the Gradle wrapper and JDK 17.

```bash
./gradlew :app:assembleDebug
```

On this workstation the Gradle cache lives in `C:/gradle-home`:

```bash
GRADLE_USER_HOME=C:/gradle-home ./gradlew :app:assembleDebug
```

Release signing is intentionally **not** stored in the repository. Configure
`OVISIONTV_SIGNING_PROPERTIES` (or `~/.ovisiontv/keystore.properties`) before
building a distributable APK. Without it the release build is unsigned.

## Tests

Fast checks:

```bash
./gradlew :app:lintDebug :app:testDebugUnitTest
```

Instrumented checks need a running emulator or TV:

```bash
./gradlew :app:connectedDebugAndroidTest
```

Prefer targeted runs while iterating:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.whatuneed.ovisiontv.kotlin.ui.HomeScreenTest
```

The launcher UI supports RU and EN. UI tests should resolve strings through
`R.string.*`, not hard-coded English text, and should avoid locale-dependent
text selectors.

## Release check

```bash
python tools/release_check.py \
  --apksigner 'C:/Users/5518~1/AppData/Local/Android/Sdk/build-tools/36.0.0/apksigner.bat'
```

The script checks `versionCode`/`versionName`, `applicationId`, SDK levels, the
v2 signature, the signing certificate and RU/EN resource-name parity. Release
notes and known regressions live in [`FIXES.md`](FIXES.md); the audit reports
are in the topic directories (`SECURITY_AUDIT`, `RELEASE_READINESS`, etc.).

## Architecture

- `data/` — preferences, profiles, app repository, weather, Watch Next,
  Aerial catalogue and device/adaptive policy.
- `discovery/` — deterministic, rule-based recommendation builder. It has no
  Android dependencies and is covered by JVM tests.
- `online/` — optional online metadata/preview clients. Online features are
  off by default, HTTPS-only and never used in Kids Mode.
- `backup/` — archive codec, AES-256-GCM encryption, validator, restore engine,
  journal/rollback, scheduler and storage gate.
- `update/` — optional GitHub-release metadata/downloader/installer. The
  metadata URL is supplied at build time; the updater is disabled when empty.
- `ui/` — Compose screens and shared TV components; `ui/components/` holds the
  focus surfaces, shelf, overlays and ambience.

Home is a focus-driven Compose surface. Integer cursor indices are the source of
truth; Compose focus mirrors them. Back/OK/D-pad handling is centralized so a
remote and TalkBack use the same actions.

## Privacy

No account, analytics or advertising SDK is included. Online chart/preview
features are opt-in; the privacy policy text is bundled in `res/raw`. The only
cleartext-network exception is the Aerial CDN host documented in
`res/xml/network_security_config.xml`. Do not add API keys or secrets to the APK.

## CI

`.github/workflows/android-verify.yml` runs lint and JVM unit tests on pushes and
pull requests. Connected tests are intentionally not run on every push because
they need a device/emulator; run them locally or in a dedicated device job.

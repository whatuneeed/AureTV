# OVisionTV Launcher — Security & Privacy Audit (Stage 1)

**Project:** `C:/OpenCode Project/OVisionTV/OVisionTV_Kotlin`
**Package:** `com.whatuneed.ovisiontv.kotlin`
**Snapshot:** working tree at `versionCode 73`, `versionName 0.5.0`, `compileSdk/targetSdk 35`, `minSdk 28`
**Audit type:** read-only source, manifest, build, and emulator-state review
**Frameworks used:** OWASP MASVS, OWASP Mobile Top 10, Android security guidance, Privacy by Design
**Status:** **Stage 1 complete; Stage 2 prioritised below. After the owner asked to start, the P0/P1 items, P2-1, P2-4 (dependency verification metadata) and P3-1 (policy version/date) were implemented and tested. P2-2, P2-3 and the remaining P3 items are still open. Stage 5/6 adds a local photo grant and an optional third-party Reddit feed (SEC-09); the original audit findings below are retained as the historical baseline; §17 records what changed and what is still unverified on physical hardware.**

> **Owner decision, 2026-11-16 (0.5.9):** SEC-02 is deliberately reopened. Aerial stills and video must use `http://sylvan.apple.com/...` because some TV trust stores reject Apple's certificate chain; the previous HTTPS-only behaviour made the screen saver preview and playback empty. The cleartext exception is scoped to that single public host in `network_security_config.xml` and `AerialCatalog`; all other media remains HTTPS-only. The catalogue refresh and redirect checks remain HTTPS. The privacy policy (version 6) and both language variants now disclose the exception. This is an **accepted risk**, not a claim that the earlier finding was wrong: cleartext permits network injection into the screen-saver surface. `FIXES.md` entry #48 records the symptom and the constraint. Reverting to HTTPS requires evidence from the affected TV models, not only an AVD.

---

## 0. Scope, method, and limitations

### 0.1 In scope

- Manifest, components, permissions, exported surface, backup and network configuration.
- Local storage: `SharedPreferences`, files, cache, diagnostics, profiles, Kids Mode.
- Network: hosts, schemes, redirects, TLS, authentication material, third-party data flows.
- Secrets and signing configuration.
- Logging, crash handling, and diagnostic retention.
- Third-party dependencies and transitive SDKs.
- Privacy by Design, privacy disclosures, data minimisation, retention, and user control.
- Android TV–specific risks: Home role, accessibility service, TV provider rows, screen saver, device admin, shared family device.
- Profile isolation and Kids Mode boundaries.
- Threat model and security/performance trade-offs.

### 0.2 Method

- Source-level review of the Kotlin application, resources, manifest, Gradle files, and generated merged manifest.
- Pattern searches for WebView, analytics SDKs, hardcoded secrets, TLS overrides, world-readable storage, exported components, unsafe intents, and unbounded provider input.
- Artifact checks:
  - `aapt2 dump permissions` on the release APK.
  - `apksigner verify --print-certs` on the release APK.
  - `adb shell dumpsys package com.whatuneed.ovisiontv.kotlin` on AVD `hume-tv` (Android 14 / API 34, x86).
- No dynamic network capture, TLS interception, root extraction, runtime exploit, physical-TV test, or SCA scan was performed. Such results are explicitly marked **not measured**.

### 0.3 What this audit does **not** prove

- It does not prove the app is free of exploitable vulnerabilities.
- It does not prove Play Store / TV certification compliance.
- It does not measure runtime performance, ANR rate, memory, 4K rasterisation, or battery impact.
- It does not test physical remotes, ARM32/ARM64 devices, API 28–33/35, 1 GB RAM devices, or TalkBack voice output.
- It does not verify Apple/weather host behaviour, certificate rotation, or CDN availability.
- It does not replace a dependency CVE scan, penetration test, or privacy legal review.

---

## 1. Executive summary

OVisionTV has a **fundamentally sound security shape for a minimal TV launcher**:

- No analytics, advertising, crash-upload, or attribution SDKs were found.
- No hardcoded API keys, tokens, passwords, or signing material were found in source or in the APK.
- Release build is minified, resource-shrunk, signed, and not debuggable by default.
- Backups are disabled (`allowBackup=false`) with both legacy and Android 12+ extraction rules.
- App data is stored in app-private storage; writes use atomic replacement; corrupt preferences are read safely.
- External deep links from TV provider rows are validated against the publishing package.
- Accessibility service reads only window package/class, not screen content; it is system-bound and user-enabled.
- Device admin requests only `force-lock`.
- Dynamic receivers are registered non-exported where the platform supports it.
- Release signing credentials live outside the repository.

The audit nevertheless found **release-blocking privacy and network issues**:

1. **The shipped privacy policy is materially inaccurate.** It says the only external host is Apple, that the app does not request location, and lists crash reports under "Not collected" while saying it sends none. The code contacts multiple weather/geolocation hosts, declares and requests location for the Wi-Fi name, and writes a local crash report.
2. **The screen saver media path deliberately uses cleartext HTTP** for `sylvan.apple.com`, and the app downgrades HTTPS URLs to HTTP. This permits network injection of images/video into a trusted visual surface and increases decoder attack surface.
3. **Weather/IP geolocation is enabled by default** and the wizard preselects "Automatic"; no explicit opt-in or host disclosure is shown.
4. **Location permission is broader than needed** for reading the Wi-Fi SSID: both fine and coarse location are declared and requested, with no `NEARBY_WIFI_DEVICES` path for modern Android.
5. **Untrusted TV provider strings are not size-bounded**, creating a potential memory/UI denial-of-service through a malicious or compromised TV app.
6. **The Kids Mode shelf launch path lacks the allowlist check** used by app-tile launches; a stale shelf card can theoretically launch an app removed from the allowlist.
7. **Local crash diagnostics are not deletable from the UI** and are not covered by "Clear cache."
8. **Sensitive launcher data is stored unencrypted** (`SharedPreferences`); this is acceptable for low-sensitivity data under the current threat model but becomes a concern if profiles, viewing affinity, and location are considered private.

**Overall verdict:** the architecture is not in need of a rewrite. The main problems are **truthfulness of privacy disclosures, a deliberate cleartext media exception, over-broad location permission, and missing data/provider bounds**. These can be fixed in small, testable steps.

**Security scorecard (pre-remediation baseline): 3.3 / 5.**
**Release readiness from a security/privacy perspective: conditional — fix P0/P1 before shipping a release that claims the current privacy text.**

> **Post-remediation note:** SEC-01, SEC-02, SEC-03, SEC-04, SEC-05, SEC-06 and SEC-07 are fixed in code and covered by tests; SEC-08 remains an accepted open design decision. See §17 for the exact changes, test results, and what is still unverified on physical hardware. The findings below are the Stage 1 baseline, not a claim that the code is still in that state.

---

## 2. Security scorecard (0–5)

| Area | Score | Rationale |
|---|:---:|---|
| Manifest & permissions | **4 / 5** | Small permission set, no `QUERY_ALL_PACKAGES`, no storage/camera/mic/contacts. Location is over-broad for SSID, and the policy does not describe it. |
| Data storage protection | **4 / 5** | App-private storage, backups off, atomic writes, safe preference reads, PBKDF2 PIN. No encryption at rest; diagnostics retention is not user-controlled. |
| Network security | **2 / 5** | TLS for weather/catalogue; **cleartext HTTP for Apple media/previews**; multiple third-party hosts; no pinning (acceptable) but no redirect allow-list for weather/media. |
| Secrets management | **5 / 5** | No hardcoded keys/tokens; signing material outside repo; no auth headers; no `BuildConfig` secrets. |
| Logging & crash reporting | **4 / 5** | Local-only, bounded, no upload, no intentional PII. Policy says "no crash reports"; diagnostics are not user-deletable. |
| Third-party dependencies | **4 / 5** | No analytics/ad/WebView SDK; 95 transitive artifacts; no SCA/SBOM/dependency verification was performed. Media3 parses untrusted remote media. |
| Privacy by Design | **2 / 5** | Inaccurate policy; weather on by default; no host disclosure; no clear path for weather/crash data; otherwise good data minimisation in launch paths. |
| Android TV–specific security | **3 / 5** | Minimal accessibility service, validated deep links, force-lock-only admin. Exported Settings/Home components and Kids Mode caveats require care; TV provider is untrusted input. |
| Profile data isolation | **4 / 5** | Layout, discovery, favorites, hidden cards, PIN and allowlist are per profile; deletion cleans them. Weather/saver/content/language/startup remain global; 4-digit PIN is a convenience gate, not a security boundary. |
| **Overall** | **3.3 / 5** | Good minimal attack surface; release-blocking transparency/network issues. |

Score meaning: 5 = strong, no material issue found; 4 = good with hardening items; 3 = acceptable with material gaps; 2 = significant gaps; 1 = unsafe; 0 = absent/broken.

---

## 3. Findings

Severity is qualitative and based on the stated threat model, not on a CVSS calculation. "Confirmed" means the code/configuration was directly observed. "Potential" means the code path exists but the impact was not reproduced dynamically.

### SEC-01 — Privacy policy contradicts the shipped behaviour

**Severity:** High (transparency / legal / store-policy risk)
**Status:** Confirmed

**Evidence**

- `app/src/main/res/raw/privacy_en.txt:15`:
  > "The only external host is Apple's public server…"
- `app/src/main/res/raw/privacy_en.txt:19`:
  > "The launcher does not request location, contacts, microphone or user storage access."
- `app/src/main/res/raw/privacy_en.txt:9-11` lists crash reports under "Not collected" and says the launcher sends none.
- The same claims exist in `privacy_ru.txt:11,15,19`.
- Actual outbound hosts/data:
  - Open-Meteo forecast: `data/Weather.kt:354`.
  - MET Norway: `data/Weather.kt:416`.
  - wttr.in: `data/Weather.kt:484`.
  - IP geolocation: `ipapi.co`, `ipwho.is`, `get.geojs.io` at `data/Weather.kt:645-647`.
  - Nominatim reverse geocoding: `data/Weather.kt:704`.
  - Open-Meteo city search: `data/CitySearch.kt:30`.
- Location permissions are declared at `app/src/main/AndroidManifest.xml:43-44` and runtime-requested from the Control Center Wi-Fi tile at `ui/components/ControlCenter.kt:585-591` and Settings at `ui/SettingsScreen.kt:2041-2042`.
- A local crash report is written at `data/CrashReport.kt:35-41` to `cacheDir/diagnostics/last-crash.txt`.
- The policy is surfaced in Settings at `ui/SettingsScreen.kt:3966-3967`.

**Why it matters**

Users cannot make an informed decision about weather location, IP geolocation, TV listing access, or local diagnostics. This is a Privacy by Design / transparency failure and a potential store-policy and consumer-protection issue, even though no data is uploaded to the developer's own server.

**Reproduction (static)**

1. Open Settings → Privacy.
2. Compare the text with `aapt2 dump permissions` / the manifest and with the hosts in `Weather.kt` / `CitySearch.kt`.
3. Enable weather or press the Wi-Fi tile; the code path requests location/contacts third-party hosts.

**Recommended fix (no architecture change)**

Rewrite `privacy_en.txt` and `privacy_ru.txt` to include, at minimum:

- Every external host and purpose (Apple media/catalogue; Open-Meteo; MET Norway; wttr.in; ipapi/ipwho/geojs; Nominatim; Open-Meteo geocoding).
- What is sent: IP address (implicitly or explicitly), approximate location/coordinates, selected city, language, app User-Agent, no account/cookie/auth.
- TV listings / Watch Next reading and why.
- Wi-Fi SSID and the location permission used for it.
- Installed-app list and launch history are read/stored locally and not uploaded.
- Local crash diagnostics location, purpose, retention, and deletion path.
- Profiles, weather location, screen-saver settings, and discovery history.
- A clear "no developer server, no analytics, no ads" statement.

### SEC-02 — Cleartext HTTP is the primary path for Apple screen-saver media

**Severity:** High (network integrity; potentially high if a media decoder bug is exploitable)
**Status:** Confirmed in code/configuration; impact not dynamically reproduced

**Evidence**

- `app/src/main/res/xml/network_security_config.xml:6-17` globally disables cleartext, then permits cleartext for `sylvan.apple.com`.
- `data/AerialCatalog.kt:29-42` rewrites every `https://sylvan.apple.com/...` URL to `http://sylvan.apple.com/...`.
- `data/AerialCatalog.kt:94-112` applies this rewrite to both video and preview image URLs.
- `ui/components/SaverScreen.kt:423-441` starts playback from the HTTP URL first and only tries HTTPS after a failure.
- `ui/components/SaverPreviewImage.kt:120-132` downloads previews from that URL, and `:155-180` follows redirects with `instanceFollowRedirects = true`.
- The catalogue metadata itself is fetched over TLS (`data/AerialRefresh.kt:71`), and redirects are restricted to `https://sylvan.apple.com` (`:185-196`). That control does **not** protect the media URLs.

**Why it matters**

A network attacker on the same LAN, ISP, or Wi-Fi can replace the screen-saver image/video content, cause playback failure, or target decoder/image-parser bugs. The screen saver is a trusted visual surface and often runs unattended. No user credentials are exposed, which is why this is not a data-confidentiality finding, but integrity is lost.

**Recommended fix**

- Make HTTPS the primary scheme for both video and previews.
- Remove the `sylvan.apple.com` cleartext exception if HTTPS works on target networks.
- If HTTP fallback is genuinely required for networks that block port 443, make it an explicit, visible fallback after HTTPS failure, not a silent default, and never downgrade a URL that is already HTTPS.
- Keep the existing strict catalogue redirect check and add a media-host allow-list if the catalogue is untrusted.
- Add a test that asserts no `http://sylvan.apple.com` URL is produced by `aerialStreamUrl` / `AerialCatalog.parse`.

### SEC-03 — Weather/IP geolocation is on by default and not explicitly disclosed

**Severity:** Medium (privacy / consent)
**Status:** Confirmed behaviour

**Evidence**

- `data/WeatherPreferences.kt:33-34`: `enabled()` defaults to `true`.
- `ui/SetupScreen.kt:510-543`: the weather page preselects "Automatic" (`checked = weatherEnabled && !weatherManual`) and "Continue" accepts it without an explicit opt-in.
- `ui/HomeScreen.kt:449-462`: Home refreshes weather whenever `foreground` is true and weather is enabled.
- `data/Weather.kt:642-663`: automatic location first sends the device's public IP to `ipapi.co`, then `ipwho.is`, then `get.geojs.io`, and may send the resulting coordinates/city to geocoding/Nominatim.
- `data/Weather.kt:354,416,484`: coordinates and language are sent to weather providers.
- `data/CitySearch.kt:30`: a user-typed city is sent to Open-Meteo geocoding.

**Why it matters**

The default is a network request to third parties based on the household's IP address. A user who simply presses Continue may not understand that "Automatic" means IP-based geolocation, not the TV's GPS. This is a Privacy by Design default issue.

**Recommended fix**

- Default weather to **off**, or require an explicit radio selection with no preselected network-location option.
- Show the host list and what will be sent before the first fetch.
- Prefer a manually chosen city; use IP geolocation only after explicit consent.
- Provide "Clear weather data" that removes place, coordinates, cache, and cached forecast.
- Consider one IP-geolocation provider instead of sequential fallbacks, or make fallback explicit.

### SEC-04 — Location permission is broader than the Wi-Fi-name use case

**Severity:** Medium (privacy / least privilege)
**Status:** Confirmed manifest/request; runtime exploit not relevant

**Evidence**

- `app/src/main/AndroidManifest.xml:43-44` declares both `ACCESS_COARSE_LOCATION` and `ACCESS_FINE_LOCATION`.
- `ui/components/ControlCenter.kt:585-591` requests both when the Wi-Fi tile is pressed without a grant.
- `ui/SettingsScreen.kt:2041-2042` requests both from the Permissions page.
- `data/DeviceFacts.kt:120-137` uses location only to read `WifiInfo.connectionInfo.ssid`.
- No `NEARBY_WIFI_DEVICES` permission or `neverForLocation` flag is declared.
- Emulator state (`adb shell dumpsys package …`): both location permissions are declared and currently denied; `READ_TV_LISTINGS` is also denied; no location request is made at first launch.

**Why it matters**

Fine location is precise and unnecessary for showing a Wi-Fi network name. On Android 13+ the platform provides `NEARBY_WIFI_DEVICES` for Wi-Fi APIs when the app does not derive physical location. The current model asks for a stronger permission than the feature needs and contradicts the privacy text.

**Recommended fix**

- Evaluate `android.permission.NEARBY_WIFI_DEVICES` with `android:usesPermissionFlags="neverForLocation"` on API 33+, with a manifest/version-specific fallback for API 28–32.
- Do not request `ACCESS_FINE_LOCATION` if SSID can be read with the narrower grant on target devices.
- Request only when the Wi-Fi tile is activated; keep "Connected" fallback otherwise.
- Update the Permissions UI and privacy text to explain the exact grant.
- Verify SSID availability on API 33/34/35 before removing the legacy path.

### SEC-05 — Untrusted TV provider strings are not size-bounded

**Severity:** Medium (denial of service / robustness)
**Status:** Confirmed missing bounds; impact potential

**Evidence**

- `data/WatchNext.kt:56-58`: limit is 500 rows per query, but no per-field or total-byte limit.
- `data/WatchNext.kt:161-186`: `Cursor.getString` values for title, subtitle, thumbnail, poster, and intent URI are copied without length checks.
- `discovery/DiscoveryEngine.kt:42-53`: rows are mapped into discovery items and ranked.
- `discovery/DiscoveryStore.kt:168-190`: full items, including `intentUri`, title, subtitle, and image URIs, are serialized into `SharedPreferences`; the total decode limit is 512 KiB but there is no per-field cap.
- `ui/components/TopShelf.kt` displays these strings and loads remote art.

**Why it matters**

Any installed app that can publish Watch Next / Preview Programs rows can supply very large strings. The launcher may allocate and lay out large text, store a large item, or repeatedly parse it. This is a local DoS vector, not a data exfiltration vector. The existing 500-row and 512 KiB caps reduce but do not eliminate the risk.

**Recommended fix**

- Add per-field limits (for example title/subtitle/package/URIs), drop or truncate over-limit rows, and enforce a total-byte budget before mapping.
- Validate URI schemes and authorities; reject `file://` and `content://` art outside an expected provider/type.
- Store only stable IDs/package names in favorites/hidden where possible; re-read provider data when needed, or cap and expire persisted intent URIs.
- Add JVM tests with oversized provider rows and malformed URIs.

### SEC-06 — Kids Mode shelf deep link bypasses the allowlist check

**Severity:** Medium (defence-in-depth; Kids Mode is explicitly not a security boundary)
**Status:** Confirmed code gap; race/stale-state exploit not reproduced

**Evidence**

- `data/KidsMode.kt:33-39`: `allowed()` checks the current profile's allowlist.
- `ui/LauncherRoot.kt:586-590`: the app-tile launch path calls `KidsMode.allowed` before launching.
- `ui/HomeScreen.kt:884-905`: `activateShelfCard` calls `discovery.contentIntent(card)` and `startActivity` directly, with no `KidsMode.allowed` check.
- `discovery/DiscoveryEngine.kt:42-53`: provider rows are filtered by `visiblePackages`, but the snapshot can be stale for a frame after a profile/allowlist change.
- `ui/HomeScreen.kt:475-497`: the refresh effect runs after composition; the old shelf can remain briefly.

**Why it matters**

In Kids Mode, a shelf card for an app removed from the allowlist can theoretically be activated before the refresh replaces the rail. The app-tile path is protected; the deep-link path is not. Since Kids Mode is documented as a UI convenience and not Android parental control, this is not a system-security bypass, but it is an avoidable gap in the feature's own promise.

**Recommended fix**

- In `activateShelfCard`, reject cards whose package is not `KidsMode.allowed(context, card.packageName)`; show the standard launch-failed message.
- Clear or re-key the shelf snapshot immediately when `kidsMode` or `kidsAllowlist` changes.
- Add a test that activates a stale/disallowed card and asserts no `startActivity`.

### SEC-07 — Local crash diagnostics are not user-deletable and are not disclosed

**Severity:** Low (privacy / data retention)
**Status:** Confirmed

**Evidence**

- `data/CrashReport.kt:35-41`: writes `cacheDir/diagnostics/last-crash.txt`, capped at 32 KiB.
- `OVisionApp.kt`: installs the handler globally.
- `ui/SettingsScreen.kt:1038-1046`: "Clear cache" removes aerial clip, preview, and poster caches only.
- `res/raw/privacy_en.txt:9-11` says no crash reports are collected.
- No UI row exposes or deletes `cache/diagnostics`.

**Why it matters**

A stack trace can contain file paths, class names, package names, and exception messages. It is local and never uploaded, but it survives "Clear cache" and is not disclosed. On a shared/returned TV, this is a small residual privacy issue.

**Recommended fix**

- Include `cache/diagnostics` in the Storage clear action, or provide a dedicated "Diagnostics" row with "View/Delete".
- Disclose in the privacy policy that a local crash trace is stored and never uploaded.
- Keep the 32 KiB cap and the no-upload design.

### SEC-08 — Sensitive launcher data is stored unencrypted

**Severity:** Low / design observation (conditional on threat model)
**Status:** Confirmed; accepted for low-sensitivity data, not for a high-security profile

**Evidence**

- `data/Profiles.kt:276-280` uses ordinary `SharedPreferences("profiles")`.
- `data/Profiles.kt:393-407` stores profile list/current and per-profile prefs names.
- `discovery/DiscoveryStore.kt:30-33` stores usage, favorites, hidden, shown, personalization in per-profile prefs.
- `data/WeatherPreferences.kt:30-31` stores manual city, coordinates, and forecast JSON.
- `data/KidsMode.kt:55-58` stores PIN hash/salt, recovery hint/hash, allowlist, and lockout in ordinary prefs.
- `data/KidsMode.kt:245-311` uses PBKDF2-HMAC-SHA256 with 120,000 iterations, a 16-byte `SecureRandom` salt, and a 32-byte hash.
- `AndroidManifest.xml:72` disables backups; the release build is not debuggable by default.
- No `EncryptedSharedPreferences`, Android Keystore, SQLCipher, or equivalent was found.

**Why it matters**

A rooted device, forensic image, or compromised app sandbox can read profile names, viewing affinity, favorites, weather location, and PIN hashes. A 4-digit PIN has only 10,000 possible values; 120k PBKDF2 makes offline guessing slower but not infeasible for a determined attacker. This is acceptable if Kids Mode is a convenience gate, not a parental-control security boundary. It is not acceptable if the product claims strong privacy or child safety.

**Recommended fix (decision required)**

- Either explicitly document the threat model ("app-private, non-encrypted, not a parental control") and keep the current design, or
- Encrypt only the sensitive preference groups (profiles, discovery, weather location, Kids credentials) with Android Keystore-backed keys; do not encrypt everything blindly.
- Do not store full intent URIs/tokens in favorites if they can contain credentials; prefer IDs and re-resolve.
- If strong child safety is a goal, use a longer PIN/passphrase and an OS-level parental-control integration, not a 4-digit code.

---

## 4. Manifest and component audit

### 4.1 Application-level settings

| Setting | Value | Assessment |
|---|---|---|
| `allowBackup` | `false` (`AndroidManifest.xml:72`) | Good. No cloud/device transfer of launcher data. |
| `dataExtractionRules` | `@xml/data_extraction_rules` | Good; excludes sharedpref/database/file for cloud and device transfer. |
| `fullBackupContent` | `@xml/backup_rules` | Good; legacy counterpart. |
| `networkSecurityConfig` | `@xml/network_security_config` | Base cleartext off; Apple exception (SEC-02). |
| `debuggable` | not set; release default false | Good. Release APK does not expose `run-as`. |
| `extractNativeLibs` | false (merged manifest) | Good; no native libraries to extract. |
| `allowClearUserData` | not set | Default true; user/system can clear app data. |
| `usesCleartextTraffic` | not set; network config controls | Acceptable, but Apple exception applies. |
| `requestLegacyExternalStorage` | not set | Good. |
| `preserveLegacyExternalStorage` | not set | Good. |
| `appComponentFactory` | AndroidX Core (merged) | Normal. |

### 4.2 Exported components

| Component | Exported | Protection | Assessment |
|---|---:|---|---|
| `MainActivity` | Yes | HOME + LEANBACK filters | Required for a launcher. Can be started by other apps, but exposes no privileged intent data. |
| `SettingsActivity` | Yes | LEANBACK filter; no intent data | Exported so the TV app row can open it. Kids gate is re-applied inside `SettingsScreen` (`ui/SettingsScreen.kt:3317-3347`) for a kids profile with a PIN. |
| `SetupActivity` | No | — | Good; only the app can open setup. |
| `OVisionAccessibilityService` | Yes | `BIND_ACCESSIBILITY_SERVICE` | System-bound; user must enable. Only window package/class is read; `canRetrieveWindowContent=false`. |
| `AerialDreamService` | Yes | `BIND_DREAM_SERVICE` | System-bound; user selects the screen saver. |
| `SleepReceiver` | No | — | Only the app's own immutable `PendingIntent` targets it. |
| `SleepReceiver$Admin` | Yes | `BIND_DEVICE_ADMIN` | System-bound device-admin receiver; XML requests only `force-lock`. |
| `androidx.startup.InitializationProvider` | No | — | Transitive AndroidX provider; not externally accessible. |
| `androidx.profileinstaller.ProfileInstallReceiver` | Yes | `android.permission.DUMP` | Transitive; only shell/root/DUMP holders can trigger. Not a normal-app attack surface. |

**Assessment:** the exported surface is small and justified. No exported component accepts a privileged action from arbitrary extras. `KidsEntryGrant` is an in-process one-shot token, not an intent extra (`SettingsActivity.kt:20-39`). Direct starts re-apply the kids gate.

### 4.3 Permissions audit

| Permission | Protection | Purpose in code | Request timing | Assessment |
|---|---|---|---|---|
| `INTERNET` | Normal | Aerial catalogue/media, weather, geocoding | Install | Required for network features. |
| `ACCESS_NETWORK_STATE` | Normal | Connection type/IP on Device/Network pages | Install | Appropriate. |
| `READ_TV_LISTINGS` | Runtime/dangerous | Watch Next / Preview Programs rows | Setup/Settings | Broad but purpose-bound to TV recommendations; user can deny. Reads public provider rows only. |
| `ACCESS_WIFI_STATE` | Normal | Wi-Fi adapter/SSID tile | Install | Appropriate; no runtime prompt. |
| `ACCESS_COARSE_LOCATION` | Runtime/dangerous | Wi-Fi SSID display | On Wi-Fi tile / Settings | Could be narrowed; see SEC-04. |
| `ACCESS_FINE_LOCATION` | Runtime/dangerous | Wi-Fi SSID display | On Wi-Fi tile / Settings | Over-broad for SSID; see SEC-04. |
| `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | Signature | AndroidX receiver protection | Install | Good; app-internal. |

**No `QUERY_ALL_PACKAGES`.** Package visibility is declared with narrow `<queries>` for LAUNCHER, LEANBACK_LAUNCHER, HOME, the AirPlay package, and the TV provider (`AndroidManifest.xml:46-68`).

**No** camera, microphone, contacts, calendar, SMS, call log, external storage, `POST_NOTIFICATIONS`, `SYSTEM_ALERT_WINDOW`, `REQUEST_INSTALL_PACKAGES`, or foreground-service permissions.

**Emulator verification (AVD `hume-tv`, Android 14/API 34):**

- Install-granted: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, app `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.
- Runtime-denied at first launch: `READ_TV_LISTINGS`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`.
- No runtime location prompt occurred on cold launch; the code requests it only from the Wi-Fi tile/Settings.

**Least-privilege verdict:** good overall; location is the one clear over-request. `READ_TV_LISTINGS` is broad but is the API contract for TV recommendations and is user-controlled.

---

## 5. Data storage and data inventory

### 5.1 Storage locations

| Location | Data | Protection | Backup |
|---|---|---|---|
| `SharedPreferences("profiles")` | Profile list, names, emoji, colour, kids flag, current profile, quarantine blob | App-private; plaintext | Excluded |
| `SharedPreferences("ovisiontv...")` (p1) and per-profile names | Tile order, folders, focus, shelf, language, saver, content toggles, discovery keys | App-private; plaintext | Excluded |
| `SharedPreferences("kids_mode")` | PIN/recovery salts and PBKDF2 hashes, recovery hint, allowlist, lockout | App-private; hashes; hint plaintext | Excluded |
| `SharedPreferences("weather")` | Enabled/manual flags, city, lat/lon, units, source, refresh, cached forecast JSON | App-private; plaintext | Excluded |
| `SharedPreferences("startup")`, `"controls"`, `"setup"`, `"profiles"` | Startup target/label, sleep timer, setup progress, profile metadata | App-private; plaintext | Excluded |
| `filesDir/aerial_catalog.json` | Refreshed Apple catalogue | App-private; plaintext | Excluded |
| `cacheDir/aerial_*` | Preview images, posters, clips | App-private; disposable | Excluded |
| `cacheDir/diagnostics/last-crash.txt` | Last uncaught stack trace, ≤32 KiB | App-private; plaintext | Excluded; not cleared by UI |
| Logcat | Operational warnings/errors, package names, URLs/hosts in messages | System log; not persisted by the app | N/A |
| Memory | Installed app list, labels, icons; Watch Next rows; discovery snapshot | Process memory | N/A |

### 5.2 Data inventory

| Data | Sensitivity | Stored? | Retention | Leaves device? | Notes |
|---|---|---|---|---|---|
| Profile names / avatars / kids flag | Medium (personal/family) | Yes, `profiles` prefs | Until profile deleted or app data cleared | No | Plaintext. |
| Tile order / folders / hidden apps | Low–medium | Yes, per-profile prefs | Until layout reset/profile delete | No | Package names + folder names. |
| Launch counts / last launch | Medium (behaviour) | Yes, per-profile discovery prefs | 64 entries / until history clear | No | `DiscoveryStore.usage`. |
| Favorites / hidden cards | Medium | Yes, per-profile discovery prefs | 100 items / until profile delete | No | Includes title, subtitle, image URIs, intent URI. |
| Shown cards (`SHOWN`) | Low–medium | Yes, per-profile discovery prefs | 128 keys | No | Used to avoid repeats. |
| Watch Next rows | Medium (viewing) | Transient memory; favorites may persist fields | Per refresh / until delete | No | Read from TV provider, not uploaded. |
| Weather city / lat / lon | Medium (location) | Yes, `weather` prefs and cache | Indefinite; no clear-data UI | **Yes** to weather/geocoding hosts | IP geolocation on by default; see SEC-03. |
| Weather forecast cache | Low | Yes, `weather` prefs | Until refresh/overwrite | Yes (request) | Contains place + forecast JSON. |
| Kids PIN / recovery hashes | High (auth secret) | Yes, `kids_mode` prefs | Until PIN cleared/profile delete | No | PBKDF2-SHA256, 120k, 16-byte salt. 4-digit PIN. |
| Kids allowlist | Medium | Yes, `kids_mode` prefs | Until profile delete | No | Package names. |
| Wi-Fi SSID | High (location-adjacent) | Not stored by launcher; displayed in Control Center | Transient | No (except normal Wi-Fi traffic) | Requires location grant. |
| IP address / network type | Medium | Not stored; displayed in Device/Network page | Transient | Implicitly to every network host | `DeviceFacts`. |
| Installed app list / labels | Medium | Memory/cache; not sent | Process/cache | No | PackageManager query. |
| Crash stack trace | Low–medium | Yes, `cache/diagnostics` | Until overwritten/cleared by system; UI does not delete | No | 32 KiB cap; not disclosed. |
| Aerial catalogue / media | Low | Files/cache | Until cache clear/refresh | Yes (Apple) | HTTP media (SEC-02). |
| Signing private key | Critical | Outside repo (`~/.ovisiontv/keystore.properties`) | Operator-managed | No | Never in APK. |

### 5.3 Storage controls that are working well

- `data/AtomicWrite.kt:22-54` writes through a temp file and an atomic/rename replace; failure preserves the previous file.
- `data/SafePreferences.kt:17-44` catches `ClassCastException`, returns a default, logs the key once, and leaves the corrupt value untouched for later repair.
- `data/HomeOrderStore.kt` and `data/Profiles.kt` keep raw quarantine copies under bounded keys rather than deleting user data.
- Profile deletion (`Profiles.kt:340-374`) clears layout, discovery data, PIN, recovery, and allowlist; it bumps the ID floor to prevent reuse.
- `KidsMode` PBKDF2 is executed off the lock and off the main thread; stored iteration count is capped to prevent a CPU DoS.
- Backups and device transfer are disabled.

### 5.4 Storage gaps

- No encryption at rest (SEC-08).
- Crash diagnostics have no user-facing delete (SEC-07).
- Weather location/cache has no "Clear weather data" path (SEC-03).
- Favorites/hidden persist full provider fields including `intentUri` (`DiscoveryStore.kt:168-190`); if a provider embeds tokens, they persist in plaintext.
- Profile IDs are accepted from persisted JSON without a strict pattern check (`Profiles.kt:178-241`, `homePrefsName` at `:406-407`). This is not remotely exploitable, but a corrupted/restored profile ID influences a `SharedPreferences` file name. Validate IDs with a strict `p[0-9]+` pattern and reject anything else.

---

## 6. Network security

### 6.1 Hosts and schemes observed

| Host | Purpose | Scheme | Data sent | Notes |
|---|---|---|---|---|
| `sylvan.apple.com` | Aerial catalogue, previews, video | HTTPS catalogue; **HTTP media** | IP, User-Agent/HTTP headers | Cleartext exception; SEC-02. |
| `api.open-meteo.com` | Forecast | HTTPS | lat/lon, language/units | No key. |
| `geocoding-api.open-meteo.com` | City search | HTTPS | typed city, language | No key. |
| `api.met.no` | Forecast | HTTPS | lat/lon | No key. |
| `wttr.in` | Forecast fallback | HTTPS | lat/lon | No key. |
| `ipapi.co`, `ipwho.is`, `get.geojs.io` | IP geolocation | HTTPS | device public IP | Sequential fallback. |
| `nominatim.openstreetmap.org` | Reverse geocoding | HTTPS | lat/lon, language | User-Agent set. |

### 6.2 Positive controls

- Base cleartext disabled; only the Apple host is excepted.
- Catalogue download uses TLS, rejects non-HTTPS redirects, restricts the host to `sylvan.apple.com`, caps at 8 MiB and 3 redirects (`AerialRefresh.kt:71,135-196`).
- Weather responses are capped at 2 MiB, geocoding at 512 KiB, with timeouts and cancellation checks.
- No cookies, `CookieManager`, `Authorization`, bearer tokens, or API keys in network calls.
- No WebView, OkHttp, Retrofit, or custom `TrustManager`/`HostnameVerifier`.
- No certificate pinning; this is acceptable for public, keyless endpoints but should be revisited if a sensitive API is added.

### 6.3 Network gaps

- **SEC-02**: cleartext Apple media and HTTPS→HTTP downgrade.
- **SEC-03**: automatic IP geolocation and multiple third-party hosts.
- Weather `get()` and `SaverPreviewImage.download()` follow redirects without a host allow-list. They are hardcoded/TLS for weather, and Apple-only for previews, so this is low risk, but a redirect allow-list would be more consistent with `AerialRefresh`.
- `TopShelf.readArtBytes` accepts `http`, `https`, `content`, and `file` URIs from TV provider rows (`ui/components/TopShelf.kt:957-984`). The base cleartext policy blocks non-Apple HTTP, but `content://`/`file://` reads are not type/authority constrained. The launcher only decodes the bytes as an image; no data is re-uploaded. Treat this as a robustness/SSRF-like concern, not a confirmed data leak.
- No network security config debug override, so user-installed CAs are not trusted in debug; good.

---

## 7. API keys and secrets

**Result: no hardcoded secrets found.**

- No API keys, tokens, passwords, client secrets, private keys, or `Authorization` headers in source.
- `ui/Tokens.kt` is a colour-token file, not credentials (false-positive name).
- Weather and geocoding services are keyless.
- Release signing reads `OVISIONTV_SIGNING_PROPERTIES` or `~/.ovisiontv/keystore.properties` (`app/build.gradle.kts:13-19`); the file is outside the workspace and `.gitignore` excludes `*.jks`, `*.keystore`, and `keystore.properties`.
- No `local.properties` or secrets in `gradle.properties`.
- The release APK is signed with certificate `CN=OVisionTV Launcher, O=OVisionTV, C=RU`, SHA-256 `327a50453093b783e84b1f2645b03319477d89966ea6a653dab53d5a567d7b10` (verified with `apksigner`). The private key is not in the APK.
- No `BuildConfig` secret fields; `buildConfig=true` is used only for generated defaults.
- Logs do not print preference values, PINs, hashes, or tokens; `SafePreferences` logs the key name only.

**Residual risk:** the signing keystore is operator-managed; losing it prevents updates, and leaking it allows malicious updates. This is an operational control, not an app-code finding.

---

## 8. Logging and crash reporting

### 8.1 What is logged

- `Log.w` / `Log.d` on network failures, app-catalogue failures, atomic-write failures, deep-link failures, saver player errors, and preference type errors.
- Package names appear in launch/deep-link warnings (`ui/HomeScreen.kt:888`, `:2299`); no user content.
- Weather errors may include host/URL context from exceptions; no account data.
- No analytics or remote logging SDK.

### 8.2 Crash handling

- `data/CrashReport.kt:20-52` installs a default uncaught-exception handler, writes one local file, caps at 32 KiB, then delegates to the previous handler.
- No upload, no restart, no attempt to suppress the system crash.
- `OVisionApp` installs it globally.
- `Settings > Storage > Clear cache` does not delete `cache/diagnostics` (SEC-07).

### 8.3 Assessment

Local-only crash handling is privacy-respecting and operationally useful. The main problems are disclosure and deletion, not collection. On a rooted device or a bugreport, the local trace may be readable; do not put secrets into exception messages.

---

## 9. Third-party libraries and SDKs

### 9.1 Direct dependencies (`app/build.gradle.kts:104-128`)

| Dependency | Version | Purpose | Security note |
|---|---|---|---|
| AndroidX Compose BOM | 2024.09.03 | UI | Normal; no network/analytics. |
| `activity-compose` | 1.9.2 | Activity/Compose integration | Normal. |
| `core-ktx` | 1.13.1 | AndroidX core | Normal. |
| `profileinstaller` | 1.4.1 | Baseline profiles | Adds exported `ProfileInstallReceiver` protected by `android.permission.DUMP`; no app data. |
| `kotlinx-coroutines-android` | 1.7.3 | Async | Normal. |
| `media3-exoplayer` / `media3-ui` | 1.4.1 | Screen-saver video | Parses remote media; keep updated. Cleartext Apple path increases exposure (SEC-02). |
| JUnit | 4.13.2 | Unit tests | Test-only. |
| `org.json` | 20240303 | JVM JSON tests | Test-only. |
| Compose UI test / AndroidX test | BOM / 1.2.1 / 1.6.x | Instrumented tests | Test-only. |

### 9.2 Transitive and SDK inventory

- 95 Maven artifacts in the release `sdkDependencies.txt`, all from AndroidX/Kotlin/Google Guava/JetBrains; no advertising, analytics, attribution, crash-upload, or social SDK.
- Notable transitive components: AndroidX Lifecycle, SavedState, Emoji2, Startup, ProfileInstaller, Guava, Media3 datasource/decoder.
- No WebView, OkHttp, Retrofit, Firebase, Crashlytics, AdMob, Facebook, Adjust, AppsFlyer, or equivalent.
- `androidx.profileinstaller.ProfileInstallReceiver` is exported with `android.permission.DUMP`; this is a platform-protected diagnostic surface, not a normal third-party app entry point.
- `androidx.startup.InitializationProvider` is non-exported.

### 9.3 Supply-chain gaps

- No dependency verification, Gradle lockfiles, SBOM, or SCA report was found.
- No CI fast security gate was found in this snapshot.
- No evidence of a known CVE in the listed versions was established; **do not claim "no CVEs"** without running an SCA tool.
- Recommendation: add Gradle dependency verification/locking and an OWASP Dependency-Check (or equivalent) job; keep Media3/ExoPlayer current because it parses network media.

---

## 10. Privacy by Design assessment

| Principle | Assessment | Evidence / gap |
|---|---|---|
| Lawfulness, fairness, transparency | **Weak** | Privacy text contradicts the code (SEC-01). |
| Purpose limitation | **Good** | TV listings used for recommendations; app list for launcher; no analytics. |
| Data minimisation | **Mixed** | No account, no ads, no identifiers; but automatic IP geolocation, precise location for SSID, persisted full intent URIs, crash traces. |
| Accuracy | **Weak** | Policy says "only Apple", "no location", "no crash reports"; all incomplete/false. |
| Storage limitation | **Mixed** | Bounded caps and profile deletion; no weather-data delete, no crash deletion, favorites persist until manual clear/profile delete. |
| Integrity and confidentiality | **Good baseline** | App-private storage, backup off, atomic writes, PBKDF2; no encryption at rest. |
| Accountability | **Weak** | Privacy page offers no contact/controller/date/version; only "open project". |
| Privacy by default | **Weak** | Weather/IP geolocation defaults on; location grant requested only from tile (good), but over-broad. |
| User control | **Mixed** | Weather off, recommendations/continue toggles, clear history, delete profiles; no clear weather/crash data, no consent screen. |
| Children's privacy | **Caveated** | Kids Mode is an app allowlist + PIN, explicitly not Android parental control; content inside allowed apps is not filtered. |

**Priority:** fix SEC-01 and SEC-03 before making any privacy claim. The rest is data-lifecycle hardening.

---

## 11. Android TV–specific risks

1. **Shared family device.** Profiles isolate layout and discovery, but weather location, saver preferences, language, content toggles, and startup settings are global. Profile names and favorites are visible to anyone who opens the profile picker or Settings.
2. **Kids Mode is not a security boundary.** It filters launcher tiles and gates Settings/profile exit with a 4-digit PIN, but system Settings, another HOME app, or a third-party launcher can leave it; content inside an allowed app is not filtered. This is documented in `KidsMode.kt:16-20`; keep that wording.
3. **TV provider is untrusted input.** Any installed app can publish Watch Next/Preview Programs rows. Deep links are validated to the publishing package (`DiscoveryIntents.kt:15-35`), but titles/URIs/art URLs are not size-bounded (SEC-05). Art URLs can cause the launcher to fetch arbitrary `http(s)`/`content`/`file` resources.
4. **Accessibility service is high privilege.** It is user-enabled, system-bound, reads only window package/class, and has `canRetrieveWindowContent=false`. It can start Home from the background and open a chosen app on boot/wake. The design is minimal and appropriate; keep the permission description accurate.
5. **Exported Settings/Home.** `SettingsActivity` is exported for the leanback app row and re-applies the Kids PIN gate internally. `MainActivity` is the HOME activity. No privileged extras are accepted.
6. **Screen saver is a public, unattended surface.** Cleartext media permits content injection (SEC-02); no user data is displayed except local weather/place if enabled.
7. **Remote/IR input.** No authentication is possible on a TV remote; the launcher must not treat key events as authorization. Kids gate and Settings gate are UI controls, not cryptographic authorization.
8. **Device admin.** Only `force-lock` is requested; the system confirmation explains it. No wipe, password, or policy-management capability.
9. **Network variability.** Some TV networks block 443; the current HTTP workaround trades integrity for availability. Prefer HTTPS-first with an explicit fallback, not silent downgrade.
10. **Physical access / ADB.** Release is not debuggable and backups are off; a debug build installed on a TV is a different threat model (debuggable, test manifest). Do not ship debug builds as release.

---

## 12. Profile data isolation

### 12.1 Scoped correctly

- `ProfileStore.homePrefsName(id)` gives each profile its own layout/discovery preference file; `p1` keeps the legacy file (`Profiles.kt:393-407`).
- `HomeOrderStore` and `DiscoveryStore` are instantiated with the current profile's prefs name (`ui/LauncherRoot.kt:127-134`).
- `DiscoveryEngine` receives `profileId` and the profile-scoped store; favorites, hidden cards, usage, and shown history do not cross profiles.
- `ProfileStore.delete` clears layout, discovery, PIN, recovery, and allowlist (`Profiles.kt:340-374`).
- Kids PIN, recovery, allowlist, and lockout are keyed by profile ID (`KidsMode.kt:55-68`).
- `HomeScreen` is keyed by `profile.id` in `LauncherRoot`, so a switch rebuilds the grid/shelf.

### 12.2 Global by design (not isolated)

| Data | Store | Why it matters |
|---|---|---|
| Weather enabled/city/lat/lon/cache | `weather` prefs | Shared household location and forecast. |
| Saver settings/clips/themes | main prefs | Not personal, but shared. |
| Language | main prefs | Shared UI preference. |
| Content recommendations/continue toggles | main prefs (`ContentPreferences`) | Global feature toggles. |
| Startup target/on-wake/home override | `startup` prefs | Shared device policy. |
| Sleep timer | `controls` prefs | Shared device state. |
| Display/sound/network settings | system | Shared device state. |

**Assessment:** isolation is appropriate for layout and viewing affinity, but not a confidentiality boundary. If profiles are meant to be private from each other, global weather location and discovery preferences need a product decision; do not silently change them in a security patch.

### 12.3 Kids Mode boundary

- `KidsMode.allowed` checks the current profile's allowlist (`KidsMode.kt:33-39`).
- `kidsFilter` removes disallowed apps from the grid and folders (`KidsMode.kt:318-335`).
- Cross-app "For You" and Recently Used rails are disabled in Kids Mode (`DiscoveryEngine.kt:95,125`); favorites are cleared (`:147-155`).
- App-tile launches are checked in `LauncherRoot.kt:586-590`.
- Shelf deep-link activation is **not** checked (SEC-06).
- Settings and system intents are gated (`LauncherRoot.kt:515-539`); Settings re-applies its own entry gate (`SettingsScreen.kt:3317-3347`).
- Leaving the kids profile requires a PIN if one exists; without a PIN it deliberately does not trap the child (`KidsMode.kt:337-343`).
- A direct start of `SettingsActivity` re-opens the gate rather than unlocking it; `KidsEntryGrant` is an in-process one-shot, not an intent extra.

**Verdict:** reasonable convenience boundary; fix SEC-06 and keep the "not parental control" disclaimer.

---

## 13. Threat model

| ID | Threat actor / scenario | Asset | Vector | Impact | Existing controls | Residual | Priority |
|---|---|---|---|---|---|---|---|
| T1 | Network attacker on LAN/ISP | Screen-saver media/previews | HTTP Apple host; HTTPS→HTTP downgrade | Content spoofing, playback DoS, decoder attack surface | TLS for catalogue/weather; strict catalogue redirect check; system trust | **Medium–High** | P1 |
| T2 | Malicious/compromised TV app | Shelf, launcher memory, user attention | Watch Next rows: huge strings, deep links, art URLs | Memory/UI DoS, launch own app, SSRF-like fetch | Publisher-package validation; 500-row cap; kids filter | **Medium** | P1 |
| T3 | Child in Kids Mode | Disallowed apps/settings | Stale shelf card; system Settings/other launcher | Access to removed app / settings | Allowlist filter, PIN, Settings gate, app-tile check | **Low–Medium** (not a security boundary) | P1 |
| T4 | Thief / offline device access | Profiles, viewing affinity, weather location, PIN hashes | Root/forensic image; app data | Privacy loss; offline PIN guessing | Android sandbox; `allowBackup=false`; PBKDF2 120k; release not debuggable | **Medium if rooted**; low otherwise | P2 |
| T5 | Other profile user | Cross-profile layout/discovery | Profile switch | Seeing another profile's tiles/history | Per-profile stores; profile deletion cleanup | **Low** | P2 |
| T6 | External app | Settings/Home surface | Explicit start of exported `SettingsActivity` | Change settings, UI phishing | No intent data; internal kids gate; user interaction required | **Low** | P3 |
| T7 | Supply chain | Build/APK | Compromised dependency or update | Code execution/data theft | Reputable repos; minify; no dynamic loading | **Medium** (no SCA/lock) | P2 |
| T8 | Weather/geolocation provider | IP/location/city | Automatic fetch | Profiling by third parties; policy mismatch | HTTPS; no account; user can disable weather | **Medium** | P1 |
| T9 | Accessibility abuse | Home/startup behaviour | User-enabled service; window events | Redirect/launch chosen app | System binding; package/class only; no content; user opt-in | **Low** | P3 |
| T10 | Backup/adb extraction | App data | `adb backup`, cloud backup | Data exposure | `allowBackup=false`; extraction rules; release not debuggable | **Low** | P3 |
| T11 | Local log/bugreport reader | Package names, errors | Logcat/bugreport | Low-sensitivity operational data | No values/PINs; no remote logging | **Low** | P3 |
| T12 | Physical remote / shoulder surfing | Settings/PIN | D-pad observation | PIN disclosure | 4-digit PIN is visible by nature; lockout | **Accepted** (convenience gate) | P3 |

---

## 14. Performance vs security trade-offs

| Decision | Security effect | Performance effect | Assessment |
|---|---|---|---|
| HTTP Apple media to survive port-443 blocking | Weakens integrity | Avoids failed playback on restrictive networks | **Unacceptable as a silent default**; use HTTPS-first with explicit fallback. |
| No certificate pinning | Allows system-trusted CA compromise | Avoids CDN rotation breakage | Acceptable for keyless public endpoints; revisit for sensitive APIs. |
| No `QUERY_ALL_PACKAGES` | Reduces installed-app visibility | Slightly more complex queries | Good. Keep the narrow `<queries>`. |
| PBKDF2 120k off main thread | Slows PIN guessing | ~off-UI; 4-digit PIN remains weak | Reasonable for a convenience gate; not strong parental control. |
| Unencrypted prefs | Readable on rooted/forensic device | Fast, simple, no key management | Acceptable only with a documented low-sensitivity threat model (SEC-08). |
| No analytics/crash-upload SDK | Minimal data collection and attack surface | No telemetry/ops visibility | Good for privacy; keep local diagnostics. |
| Bounded caches (`LruCache` byte budgets) | Limits memory DoS | Predictable memory on low-end TVs | Good. |
| Accessibility package query on IO | Avoids event-thread stall | Slight cache-refresh latency | Good. |
| Weather backoff | Avoids tight retry loops | Less network wakeups | Good. |
| Remote art fetch on IO with 8 MiB cap | Limits memory; SSRF-like fetch remains | No main-thread block | Good bounds; add URI/host validation. |
| No encryption at rest | Lower CPU/key-management complexity | Faster prefs/JSON | Trade-off acceptable only under the stated threat model. |

**Measurement caveat:** none of these trade-offs were benchmarked on physical TV hardware in this audit. Do not present the qualitative reasoning as a measured performance result.

---

## 15. Remediation roadmap (Stage 2 prioritisation)

P0-1…P0-3, P1-1…P1-4, P2-1, P2-4 (dependency verification only) and P3-1 (version/date only) were implemented after the owner asked to start. The tables below keep the original proposal, with the Stage 4 outcome recorded in §17. P2-2, P2-3 and the remaining P3 items are not implemented.

### P0 — Release-blocking transparency and consent

| ID | Action | Risk | Tests / verification |
|---|---|---|---|
| P0-1 | Rewrite `privacy_en.txt` / `privacy_ru.txt` with every host, data field, purpose, retention, and deletion path (SEC-01). | Low code risk | Text review against source; EN/RU parity check; Settings render test. |
| P0-2 | Make weather opt-in or default off; remove the preselected "Automatic" network-location option (SEC-03). | Low–medium product change | Unit tests for default; setup UI tests; no network fetch when off. |
| P0-3 | Disclose local crash diagnostics and add a delete path / include diagnostics in Storage clear (SEC-07). | Low | Instrumented Settings test; file-absence assertion. |

### P1 — Network, permissions, and Kids boundary

| ID | Action | Risk | Tests / verification |
|---|---|---|---|
| P1-1 | HTTPS-first Apple media; remove/limit cleartext exception; no silent HTTPS→HTTP downgrade (SEC-02). | Medium; may break blocked-network TVs | URL-shape unit tests; emulator playback smoke; manual restricted-network test. |
| P1-2 | Adopt `NEARBY_WIFI_DEVICES` with `neverForLocation` on API 33+, coarse fallback on older; stop requesting fine location if unnecessary (SEC-04). | Medium; OEM variation | Permission tests on API 28/33/34/35; SSID presence/fallback test. |
| P1-3 | Add per-field/URI/total-byte caps for TV provider rows and persisted discovery items (SEC-05). | Low | JVM tests with oversized rows; fuzz malformed URIs. |
| P1-4 | Add `KidsMode.allowed` check in `activateShelfCard`; clear shelf on kids/allowlist change (SEC-06). | Low | Compose/instrumented test with stale card; no `startActivity`. |

### P2 — Data lifecycle and hardening

| ID | Action | Risk | Tests / verification |
|---|---|---|---|
| P2-1 | Add "Clear weather data" (place, coordinates, cache) and retention description (SEC-03). | Low | Settings test; prefs empty after action. |
| P2-2 | Decide encryption-at-rest for profiles/discovery/weather/Kids credentials; if adopted, use Keystore-backed keys (SEC-08). | Medium–high architecture decision | Migration test; key-loss/recovery test; performance check. |
| P2-3 | Stop persisting full provider intent URIs/tokens in favorites; store IDs and re-resolve (SEC-05/SEC-08). | Medium feature change | Favorite launch test after provider refresh. |
| P2-4 | Add dependency verification/locking and an SCA job (Section 9.3). | Low | CI report; reproducible build check. |

### P3 — Long-term assurance

| ID | Action |
|---|---|
| P3-1 | Add a privacy contact/controller/version/date to the privacy page. |
| P3-2 | Add a security review checklist to release notes / CI. |
| P3-3 | Re-test on physical TVs, ARM32/ARM64, API 28–35, 1 GB RAM, 4K, real remotes, TalkBack. |
| P3-4 | Conduct a network MITM test and a decoder/media fuzz test before claiming strong media security. |
| P3-5 | Review Kids Mode positioning; if strong child safety is required, use OS-level parental controls and a longer secret. |

---

## 16. Verification plan for Stage 4–5

1. **Unit tests:** URL construction (no HTTP downgrade), weather default off, provider caps, Kids allowlist activation, privacy string/host parity, diagnostics deletion.
2. **Instrumented tests:** Settings privacy render, weather opt-in, Storage clear removes diagnostics, Kids shelf stale-card test, permission flows.
3. **Static checks:** `./gradlew lintDebug`, manifest merge diff, `aapt2 dump permissions`, `apksigner verify`, grep for `http://` in generated catalogue, grep for secrets.
4. **Emulator smoke:** AVD `hume-tv` (API 34) — permissions state, weather off/on, screen-saver playback over HTTPS, Settings gate, app launch.
5. **Physical-device follow-up:** API 28/33/35, ARM, restricted network (443 blocked), real remote, TalkBack; only then remove the "not tested" caveat.
6. **Stage 5 verification:** repeat the same checks after each small patch; do not claim a fix without a passing test or a reproducible manual result.

---

## 17. Stage 4 remediation performed

The owner asked to start after the Stage 1–2 report. The changes below were made in small, test-backed steps. They do **not** change the app's architecture, signing key, data model, or dependency list.

| Finding | Status | What changed | Verification | Residual risk |
|---|---|---|---|---|
| SEC-01 privacy text | **Fixed (code)** | `privacy_en.txt` / `privacy_ru.txt` rewritten: every host, weather off-by-default, the Wi-Fi grant, local crash trace, TV-listings access, Kids Mode scope, and user controls. `aerial_sources_*` now says the TLS chain is checked against the Android system trust store, not an Apple root. | `PrivacyPolicyTextTest` (hosts/HTTPS/crash-trace/weather wording in both languages); unit run. | Text is source-reviewed, not legal review. |
| SEC-02 cleartext Apple media | **Reopened by owner decision / accepted risk (0.5.9)** | `AerialCatalog.aerialStreamUrl()` now rewrites `https://sylvan.apple.com/` to `http://sylvan.apple.com/` and `isAerialMediaUrl()` accepts that host over either scheme; `SaverPreviewImage` uses the same predicate. `network_security_config.xml` keeps `cleartextTrafficPermitted="false"` globally and adds exactly one `domain-config` for `sylvan.apple.com` (`includeSubdomains="false"`). External hosts are not downgraded. | `AerialCatalogTest` (HTTP for Apple, HTTPS for other hosts), `AerialCatalogBuilderTest`, `SaverPreviewTest`, `NetworkSecurityConfigTest` (exactly one exception); AVD 34 preview and 100-clip grid load over HTTP (evidence 54/55). | Cleartext can be injected by a network attacker into the screen-saver surface; decoder attack surface is unchanged from the Stage 1 finding. Physical-TV playback and a hostile-network test remain **not measured**. Reverting to HTTPS needs physical evidence, not an AVD assumption. |
| SEC-03 weather/IP location default | **Fixed (code)** | `WeatherPreferences.enabled()` defaults to `false`; the wizard opens on “Off”; choosing Automatic/City is now an explicit action. P2-1 adds **Settings → Weather → Clear Weather Data**, which deletes the saved place, coordinates, forecast and cache timestamp and turns weather off. | `SetupScreenTest.theWeatherRowsWriteTheRealPreference`; `WeatherPreferencesTest`; `SettingsScreenTest.weather_clear_requires_confirmation_and_removes_saved_data` (all instrumented, pass). | A pre-existing install that had already enabled weather keeps that choice until the viewer clears it or turns it off. An in-flight fetch is invalidated by a data-generation counter so it cannot write the cleared answer back. |
| SEC-04 location breadth | **Fixed (code), partial device matrix** | API 33+ requests only `NEARBY_WIFI_DEVICES` with `neverForLocation`; API 31–32 also sends coarse because the platform requires it; API 28–30 uses fine only. Both legacy grants are capped at `maxSdkVersion=32`, so API 33+ never sees them. `DeviceFactsReader` and `PermissionRecovery` use the new helpers. | `WifiNamePermissionTest` (28/31/32/33/34/35), `ManifestPermissionTest`, release `aapt2 dump permissions` (fine/coarse `maxSdkVersion='32'`, nearby `neverForLocation`); AVD API 34 shows nearby denied and no location prompt. | No runtime test on API 28–32 physical/emulator images yet. The tile still falls back to “Connected” without the grant. |
| SEC-05 provider bounds | **Fixed (code)** | `WatchNextSource` caps package (255), title/subtitle (512), and drops URIs over 4096; `DiscoveryStore` caps id/package/title/subtitle/label/activity/URI before writing and on read. | `WatchNextTest` (truncate/drop), `DiscoveryStoreCodecTest` (oversized fields and identity); unit run. | Total JSON is still capped at 512 KiB; malformed deep links are dropped, not fuzzed. |
| SEC-06 Kids shelf launch | **Fixed (code)** | `HomeScreen.activateShelfCard` calls `KidsMode.allowed` before resolving/launching; the predicate was extracted to `allowedInKidsProfile` for testability. Existing `LaunchedEffect` keys already include `kidsMode` and `resumeTick`, so the rail refreshes on profile/allowlist changes. | `KidsModeTest.aKidsProfileAllowsOwnAndAllowlistedPackagesOnly`; full instrumented suite. | No dedicated stale-Compose-card test; the guard is defense-in-depth on top of the existing refresh. |
| SEC-07 crash diagnostics | **Fixed (code)** | `CrashReport.clear(context)` deletes only `cache/diagnostics/last-crash.txt`; Storage → Clear cache calls it; policy discloses it. | `CrashReportInstrumentedTest` (only the trace is removed; absent file is harmless) — both pass in the 221-test run. | The trace is still plaintext and local; it is not encrypted. |
| SEC-08 encryption at rest | **Open / accepted risk** | No change: profiles/discovery/weather/Kids data remain in app-private `SharedPreferences`; Kids PIN uses PBKDF2-SHA256 with per-profile salt. | Existing analysis only. | Root/forensic access can read data. P2-2 requires an explicit architecture decision; not silently changed. |
| P2-1 weather deletion | **Fixed (code)** | `WeatherPreferences.clearData()` turns weather off and removes manual place, coordinates, cached forecast, cache timestamp/key; units/source/refresh remain. `SettingsScreen` adds a confirmation page (Cancel focused by default). | `WeatherPreferencesTest` (prefs cleared; display choices kept); `SettingsScreenTest.weather_clear_requires_confirmation_and_removes_saved_data` (confirmation does not delete early; confirm deletes); `WeatherRequestTest` generation guard. | No physical-device run of this Settings path; the AVD Compose test covers it. |
| P2-4 dependency verification | **Partially implemented** | `gradle/verification-metadata.xml` pins SHA-256 for 400 dependency components and verifies metadata. No SCA/CVE scanner or new dependency was added. | `:app:compileDebugKotlin --rerun-tasks`, `:app:assembleRelease`, `:app:assembleDebugAndroidTest` and `:app:lintDebug` all pass with verification enabled. | Any dependency addition or version bump requires regenerating the metadata; known-CVE scanning is still missing. |
| P3-1 policy version/date | **Partially implemented** | EN/RU policy now carry “Policy version 4, updated 2026-11-16”, disclose Photos as a local system-picker grant (no upload, no backup), and disclose the Reddit feed/image hosts, User-Agent, lack of account/cookie/token, unmoderated content, Kids Mode exclusion and cache-only storage. | `PrivacyPolicyTextTest.thePolicyCarriesAVersionAndDate`, `.photosAreDisclosedAsALocalPickerGrantAndNotBackedUp`, `.redditIsDisclosedAsAnUnmoderatedThirdPartyFeed`. | No real privacy contact/controller was supplied, so that part remains open rather than invented. |
| SEC-09 third-party Reddit feed | **Fixed (code), unverified on real network** | Only `www.reddit.com` Atom feed is requested; images must be HTTPS on `i.redd.it`, `preview.redd.it` or `external-preview.redd.it`; every redirect is revalidated; DTD/external entities disabled; feed ≤4 MB/100 posts, image ≤12 MB/4 M decoded pixels; no account, cookie, token or client secret; feed/image caches live in `cacheDir` and are excluded from backup; Kids Mode never loads or displays Reddit; UI and policy warn that content is unmoderated and may be adult. The subreddit is deliberately not allowlisted at the owner’s request. | `RedditFeedTest` (host allowlist, HTTPS/no-userinfo/port, HTML/URL extraction, arbitrary subreddit names, 6/12/24 h intervals); `RedditCatalogOverlayTest`; `AmbientScreenTest` Kids/empty/loading fallbacks; privacy test; AVD real RSS smoke (100 posts, preview, full-screen, Back). | Real 403/429 behaviour, RSS stability, NSFW leakage, vendor TV/remote, port-443-blocked image loading and long soak are **not** verified. RSS is unofficial and has no SLA. |

### 17.1 Verification actually performed

| Check | Result |
|---|---|
| `:app:testDebugUnitTest` | **467 / 467 passed**, 0 failed, 0 errors (current cumulative run; includes the Reddit parser/URL/interval tests). |
| `:app:connectedDebugAndroidTest` on AVD `hume-tv` (Android 14 / API 34, x86) | **255 / 255 passed**, 0 failed, 0 errors, 0 skipped. Includes the Reddit catalog D-pad/back test and the Reddit ambient fallbacks. |
| `:app:lintDebug` | **0 errors, 18 warnings** (pre-existing GradleDependency/deprecation warnings; no security-lint error). |
| `:app:assembleRelease` | **Success**; APK 4,771,546 bytes (0.5.7/80). |
| `aapt2 dump permissions` (release) | INTERNET, ACCESS_NETWORK_STATE, READ_TV_LISTINGS, ACCESS_WIFI_STATE, `NEARBY_WIFI_DEVICES` (`neverForLocation`), `ACCESS_FINE_LOCATION` (`maxSdkVersion=32`), `ACCESS_COARSE_LOCATION` (`maxSdkVersion=32`), plus the AndroidX dynamic-receiver permission. No `QUERY_ALL_PACKAGES`, no background location. |
| `apksigner verify --print-certs` | OK; `CN=OVisionTV Launcher, O=OVisionTV, C=RU`; SHA-256 `327a5045…d7b10` unchanged. |
| Dependency verification | `gradle/verification-metadata.xml` generated with SHA-256 for 400 components; `verify-metadata=true`; a verification-enabled release/lint/test build passes. |
| Release install/launch on AVD | Installed and `MainActivity` started with a live process. This is a launch smoke test, **not** screen-saver playback or a privacy-flow test. |

### 17.2 Not verified / still open

- Physical Android TV, ARM32/ARM64, API 28–33/35, 1 GB RAM, 4K rasterisation, real remotes, TalkBack voice.
- HTTPS screen-saver playback on a network that blocks port 443; the still-only fallback is the intended behaviour, but it was not observed end-to-end.
- MITM capture, decoder/media fuzz, root/forensic extraction, dependency CVE/SCA scan.
- P2-2 encryption-at-rest; P2-3 re-resolve provider intents instead of persisting full URIs; SCA/known-CVE scanning (the P2-4 dependency-verification half is done); P3-1 contact/controller (version/date is present); P3-2 CI gate; P3-3…P3-5 device/fuzz work.

---

## 18. Final verdict

**Security architecture:** good, and unchanged by this remediation. The launcher has no analytics/ad SDK, no hardcoded secrets, no WebView, no broad package visibility, app-private storage, disabled backups, atomic writes, and validated provider deep links.

**Privacy architecture:** materially improved. The policy now matches the hosts, permissions, local diagnostics, and weather default; weather is off until chosen; the crash trace is disclosed and removable. It is still source-reviewed rather than legally reviewed, and encrypted-at-rest remains an open design question.

**Network architecture:** HTTPS for catalogue refresh, updates, weather, Reddit and every non-Apple host. A single, explicit cleartext exception exists for `http://sylvan.apple.com/` stills and video, reinstated by the owner on 2026-11-16 because several TV trust stores reject Apple's chain; the privacy policy discloses it. This is an accepted risk, not an HTTPS-only posture, and physical playback on restricted networks remains unverified.

**Kids/profile architecture:** reasonable convenience isolation, not an Android security boundary. The shelf launch gap is closed and the existing refresh keys still drive the rail.

**Overall:** the original **3.3 / 5** was a pre-remediation score. A defensible post-remediation code-level position is **4.0 / 5**, with the score held down by the unverified physical/device matrix, the accepted unencrypted-prefs risk (SEC-08), and the remaining P2/P3 items. This is **not** a claim that the app is vulnerability-free or release-ready on hardware that has not been tested.

**Release recommendation:** the P0/P1 code-level scope is complete and green on the AVD. Before a public release, run the §16 physical/restricted-network checks and decide SEC-08 explicitly. Do not present the AVD-only result as a physical-TV certification.

---

## Appendix A — Release security checklist (P3-2 draft)

This is a manual gate until a CI pipeline exists. Checked items were run locally for this remediation; unchecked items remain unverified and must not be presented as passing.

- [x] `./gradlew :app:testDebugUnitTest` — 426/426.
- [x] `./gradlew :app:connectedDebugAndroidTest` on the AVD — 223/223.
- [x] `./gradlew :app:lintDebug` — 0 errors.
- [x] `./gradlew :app:assembleRelease` with dependency verification enabled.
- [x] `gradle/verification-metadata.xml` covers build, release, lint and test artifacts; regenerate it deliberately when changing a dependency.
- [x] `aapt2 dump permissions` matches the documented allowlist (7 permissions; no background location, no `QUERY_ALL_PACKAGES`).
- [x] `apksigner verify --print-certs` shows the expected release certificate.
- [x] EN/RU privacy policy names every host, the weather default, the Wi-Fi grant, the local crash trace, and the data-deletion paths.
- [x] `network_security_config` disables cleartext; no feature depends on HTTP. Legacy `http://` literals are confined to Apple URL normalisation/builder input, whose output is HTTPS and is covered by tests; no TLS override, API key or analytics SDK is present.
- [ ] Physical Android TV / ARM / API 28–33/35 / 4K / 1 GB / real remote / TalkBack matrix.
- [ ] Port-443-blocked network playback and MITM capture.
- [ ] Known-CVE/SCA review of the pinned dependencies.
- [ ] Privacy contact/controller supplied by the project owner.

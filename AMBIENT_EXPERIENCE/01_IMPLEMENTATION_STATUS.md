# Ambient Experience — статус реализации Stage 2–4b

**Дата:** 2026-11-15
**База:** OVisionTV Launcher 0.5.4, `versionCode 77`
**Статус:** Stage 2–3 реализованы; Stage 4a — опциональные Dim After и Max Session; Stage 4b — opt-in погода на часах (только из кэша).
**Не реализовано и не обещается:** 4K/HDR, Photo Gallery, отдельный Weather-режим (погода — только строка на часах), Cinematic Photography, Abstract Motion, blackout, энергоизмерения, физические ТВ. Pixel shift/dim — не «OLED-safe» и не гарантия от burn-in.

## Что сделано

### 1. Ambient-слой

Новый пакет `app/src/main/java/com/whatuneed/ovisiontv/kotlin/ambient/`:

- `AmbientModels.kt` — `AmbientMode` (`aerial`, `clock`, `minimal`), `AmbientClockFormat` (`system`, `twelve`, `twenty_four`), `AmbientSettings`, `AmbientSettingsStore`, `AmbientScheduler`.
- `AmbientPixelShift.kt` — чистый детерминированный сдвиг часов ±9/±6 dp раз в минуту; возврат в центр при выключении.
- `AmbientPowerPolicy.kt` — чистая политика dim (80% → 60%) и max session; без Android-часов и догадок о панели.
- `AmbientPlaylist.kt` — единая точка сборки Aerial-плейлиста из `AmbientSettings`; Clock/Minimal не требуют клипов.
- `AmbientClock.kt` — чистое форматирование времени/даты; формат выбирает `AmbientClockFormat`.

Ключи:

- `ambient_mode` — новый, отсутствует → `aerial`;
- старые `saver_delay_minutes`, `saver_music`, `saver_refresh`, `saver_themes`, `saver_hidden_clips` читаются без миграции;
- сброс заставки сбрасывает и режим в `aerial`.

### 2. Один ambient surface

Новый `ui/components/AmbientScreen.kt`:

- Aerial делегирует в существующий `SaverScreen` — поведение, плееры, still fallback, watchdog и media session не изменены.
- Clock: локальные часы/дата, без декодера и сети; обновление раз в минуту, а не каждый кадр.
- Clock: системный/12-часовой/24-часовой формат; `Pixel Shift` двигает только текст часов раз в минуту (не «OLED-safe»).
- Clock: opt-in строка погоды под датой (`Show Weather`, по умолчанию Off). Если погода в системе выключена — строка ведёт в `Settings → Weather`; если включена, показывает cached `Weather.Data` (иконка, температура, условие) и `Outdated`, когда кэш устарел. Сеть из заставки не запрашивается.
- Clock/Minimal: опционально `Dim After` (10/20/30 мин) и `Max Session` (30/60/120 мин); по умолчанию `Never`.
- Dim: 80% через выбранное время, 60% ещё через 5 минут; экран никогда не становится чёрным.
- Minimal: локальный градиент без текста; медленное изменение фазы раз в 15 с, а не 60 fps.
- Любая клавиша закрывает; BACK и exit-fade работают как у Aerial.
- `keepScreenOn` только на время поверхности.
- Reduce Motion убирает scale и движение фона.
- Для Clock content description включает время и дату.

### 3. Home и DreamService

- `LauncherRoot.kt` читает `AmbientSettingsStore.load(...)` на resume; Aerial-плейлист строится через `AmbientPlaylist.scenes(...)`.
- Новый режим не требует клипов; таймер использует `AmbientScheduler.isDue(...)`.
- В Clock/Minimal пара `ExoPlayer` освобождается, warm-still и Aerial-refresh не запускаются.
- `AerialDreamService` показывает выбранный режим; Clock/Minimal не загружают каталог и не ходят в сеть.
- Системная подпись изменена на `OVisionTV Ambient` / `OVisionTV: заставка`.

### 4. Настройки

Новая IA:

```
Settings
  └── Personalization
        ├── Language
        └── Ambient Experience
              ├── Ambient Mode → Aerials / Clock / Minimal
              ├── Start After
              ├── Show During Music
              ├── Clock Format / Pixel Shift (только Clock)
              ├── Dim After / Max Session (Clock и Minimal)
              ├── Show Weather (только Clock; Off по умолчанию)
              ├── Aerial Clips / Themes / Download Frequency (только Aerials)
              ├── System screen saver
              └── Reset
```

- `PAGE_SAVER_MODE` — radio-страница режимов.
- `PAGE_SAVER_CLOCK_FORMAT` — System / 12-hour / 24-hour; в Clock виден `Pixel Shift` toggle.
- `PAGE_SAVER_DIM`, `PAGE_SAVER_SESSION` — radio-страницы; по умолчанию `Never`, поведение без явного выбора не меняется.
- В Clock/Minimal Aerial-строки скрыты, чтобы не предлагать темы, которые не рисуются.
- `Choose Screen Saver` переименован в `Aerial Clips`; тупиковый выбор с одним пунктом убран.
- Preview в Settings: Aerial — реальный still; Clock — локальные часы и, если включено, cached погода; Minimal — локальный градиент. Декодер не запускается.

### 5. Backup

- `SaverData.mode`, `clockFormat`, `pixelShift`, `dimAfter`, `maxSession`, `showWeather` — необязательные поля; старые архивы без них восстанавливаются как `aerial`, `system`, `true`, `never`, `never`, `false`.
- `BackupSource` читает `ambient_mode`, `ambient_clock_format`, `ambient_pixel_shift`, `ambient_dim_after`, `ambient_max_session`, `ambient_show_weather`.
- `BackupRestoreEngine` пишет все шесть полей в том же commit, что и остальные saver-ключи.
- Schema остаётся 1: поле обратно совместимо.

## Проверено

| Проверка | Результат |
|---|---|
| `:app:testDebugUnitTest` | **457/457** |
| `:app:connectedDebugAndroidTest` на AVD `hume-tv` API 34 | **242/242** |
| `:app:lintDebug` | **0 errors / 18 warnings / 5 info** |
| `:app:assembleRelease` | **Success**, APK **4 685 502 B**, подпись SHA-256 `327a5045…67d7b10` (не изменилась) |
| AVD, визуально | Home → Personalization → Ambient Experience; Aerials/Clock; Clock Format; Dim After/Max Session; Show Weather; preview с реальной погодой; full-screen clock; any key exits |
| Backup | режим, формат, pixel shift, dim, max session и showWeather входят в backup/restore; старый архив без полей → Aerial / System / On / Never / Off |
| Aerial | старый `SaverScreen` остаётся рендерером; поведение не менялось |

Новые тесты:

- `ambient/AmbientModelsTest` — режимы и `AmbientClockFormat` (system/12/24, fallback, стабильные ключи)
- `ambient/AmbientClockTest` — 12/24-форматирование и локализованная дата
- `ambient/AmbientPlaylistTest`
- `ambient/AmbientPixelShiftTest` — центр, пределы ±9/±6 dp, цикл
- `ambient/AmbientPowerPolicyTest` — пороги 10/20/30 мин, шаги 80/60%, session Never/30 мин
- `ui/components/AmbientScreenTest`
- `SettingsScreenTest.the_ambient_mode_page_switches_between_aerial_and_clock`
- `SettingsScreenTest.the_clock_page_sets_a_12_24_format_and_pixel_shift`
- `SettingsScreenTest.the_clock_page_sets_dim_and_max_session`
- `SettingsScreenTest.the_clock_page_offers_weather_when_the_weather_feature_is_on`
- `SettingsScreenTest.the_clock_page_points_to_weather_settings_when_weather_is_off`
- `BackupCodecTest` — mode/clockFormat/pixelShift/dim/maxSession/showWeather в roundtrip
- `BackupManagerInstrumentedTest` — Clock/24h/Off/Dim/Session/Weather сбрасываются к дефолтам при restore

## Не проверено

- физический Android TV / Google TV;
- `DreamService` в системном chooser на реальном ТВ;
- Clock/Minimal на 720p, 4K, ARM32/64, 1 GB RAM;
- 12/24-часовой режим на реальной прошивке (AVD: system 12h и ручной 24h проверены);
- TalkBack на часах;
- OLED burn-in, энергия, нагрев;
- фактический pixel shift/dim на реальной панели (только программная логика и AVD);
- реальное закрытие по Max Session через 30/60/120 мин (unit-логика; AVD-сессия не ждалась);
- 4K/HDR — отсутствует контент и pipeline;
- Photos/Cinematic/Abstract — не реализованы;
- погода на часах не проверена при stale-кэше на реальном устройстве; источник и атрибуция остаются в `Settings → Weather`;
- SAF picker;
- process death/reboot;
- 24 h soak.

## Следующие шаги

1. Blackout/расписание ночи — только после явного решения владельца; сейчас dim-ступени есть, blackout нет.
2. Stage 5 — Photo Gallery через SAF, per-profile.
3. Stage 6 — 4K/HDR только после лицензии и проверки декодера/панели.
4. Физическая матрица устройств и измерения.

**Это не сертификация.** Всё, что не проверено на физическом устройстве, остаётся непроверенным.

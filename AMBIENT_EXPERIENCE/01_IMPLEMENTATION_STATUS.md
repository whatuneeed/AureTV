# Ambient Experience — статус реализации Stage 2–3

**Дата:** 2026-11-15  
**База:** OVisionTV Launcher 0.5.1, `versionCode 74`  
**Статус:** Stage 2 (выделение ambient-слоя) и Stage 3 (IA + Aerial/Clock/Minimal) реализованы.  
**Не реализовано и не обещается:** 4K/HDR, Photo Gallery, Weather-режим, Cinematic Photography, Abstract Motion, OLED pixel shift/dim/session, энергоизмерения, физические ТВ.

## Что сделано

### 1. Ambient-слой

Новый пакет `app/src/main/java/com/whatuneed/ovisiontv/kotlin/ambient/`:

- `AmbientModels.kt` — `AmbientMode` (`aerial`, `clock`, `minimal`), `AmbientSettings`, `AmbientSettingsStore`, `AmbientScheduler`.
- `AmbientPlaylist.kt` — единая точка сборки Aerial-плейлиста из `AmbientSettings`; Clock/Minimal не требуют клипов.
- `AmbientClock.kt` — чистое форматирование времени/даты, 12/24-часовой режим.

Ключи:

- `ambient_mode` — новый, отсутствует → `aerial`;
- старые `saver_delay_minutes`, `saver_music`, `saver_refresh`, `saver_themes`, `saver_hidden_clips` читаются без миграции;
- сброс заставки сбрасывает и режим в `aerial`.

### 2. Один ambient surface

Новый `ui/components/AmbientScreen.kt`:

- Aerial делегирует в существующий `SaverScreen` — поведение, плееры, still fallback, watchdog и media session не изменены.
- Clock: локальные часы/дата, без декодера и сети; обновление раз в минуту, а не каждый кадр.
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
              ├── Aerial Clips / Themes / Download Frequency (только Aerials)
              ├── System screen saver
              └── Reset
```

- `PAGE_SAVER_MODE` — radio-страница режимов.
- В Clock/Minimal Aerial-строки скрыты, чтобы не предлагать темы, которые не рисуются.
- `Choose Screen Saver` переименован в `Aerial Clips`; тупиковый выбор с одним пунктом убран.
- Preview в Settings: Aerial — реальный still; Clock — локальные часы; Minimal — локальный градиент. Декодер не запускается.

### 5. Backup

- `SaverData.mode` — необязательное поле; старые архивы без него восстанавливаются как `aerial`.
- `BackupSource` читает `ambient_mode`.
- `BackupRestoreEngine` пишет режим в том же commit, что и остальные saver-ключи.
- Schema остаётся 1: поле обратно совместимо.

## Проверено

| Проверка | Результат |
|---|---|
| `:app:testDebugUnitTest` | **448/448** |
| `:app:connectedDebugAndroidTest` на AVD `hume-tv` API 34 | **238/238** |
| `:app:lintDebug` | **0 errors / 18 warnings / 5 info** |
| `:app:assembleRelease` | **Success**, APK **4 658 974 B**, подпись SHA-256 `327a5045…67d7b10` (не изменилась) |
| AVD, визуально | Home → Personalization → Ambient Experience; переключение Aerials/Clock; preview; full-screen clock; any key exits |
| Backup | режим входит в backup/restore; старый архив без mode → Aerial |
| Aerial | старый `SaverScreen` остаётся рендерером; поведение не менялось |

Новые тесты:

- `ambient/AmbientModelsTest`
- `ambient/AmbientClockTest`
- `ambient/AmbientPlaylistTest`
- `ui/components/AmbientScreenTest`
- `SettingsScreenTest.the_ambient_mode_page_switches_between_aerial_and_clock`
- `BackupCodecTest` — `mode = clock` в roundtrip
- `BackupManagerInstrumentedTest` — Clock сбрасывается в Aerial при restore

## Не проверено

- физический Android TV / Google TV;
- `DreamService` в системном chooser на реальном ТВ;
- Clock/Minimal на 720p, 4K, ARM32/64, 1 GB RAM;
- 12/24-часовой режим на реальной прошивке (AVD — 12h);
- TalkBack на часах;
- OLED burn-in, энергия, нагрев;
- 4K/HDR — отсутствует контент и pipeline;
- Photos/Cinematic/Weather/Abstract — не реализованы;
- SAF picker;
- process death/reboot;
- 24 h soak.

## Следующие шаги

1. Stage 4a — часы: 12/24, размер/позиция, safe area, pixel shift/OLED-политика (после решения владельца).
2. Stage 4b — Weather как opt-in режим (реальные данные, без новых permissions).
3. Stage 5 — Photo Gallery через SAF, per-profile.
4. Stage 6 — 4K/HDR только после лицензии и проверки декодера/панели.
5. Физическая матрица устройств и измерения.

**Это не сертификация.** Всё, что не проверено на физическом устройстве, остаётся непроверенным.

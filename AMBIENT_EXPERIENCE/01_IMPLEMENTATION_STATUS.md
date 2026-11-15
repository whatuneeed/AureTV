# Ambient Experience — статус реализации Stage 2–5

**Дата:** 2026-11-15
**База:** OVisionTV Launcher 0.5.6, `versionCode 79`
**Статус:** Stage 2–3 реализованы; Stage 4a — опциональные Dim After и Max Session; Stage 4b — opt-in погода на часах (только из кэша); Stage 5 — Photos через системный выбор (SAF/Photo Picker).
**Не реализовано и не обещается:** 4K/HDR, отдельный Weather-режим (погода — только строка на часах), Cinematic Photography, Abstract Motion, blackout, энергоизмерения, физические ТВ. Photos работают только из выбранной папки/снимков; системный photo picker на AOSP-образе требует Tab/касания для входа в сетку — D-pad-only выбор не подтверждён. Pixel shift/dim — не «OLED-safe» и не гарантия от burn-in.

## Что сделано

### 1. Ambient-слой

Новый пакет `app/src/main/java/com/whatuneed/ovisiontv/kotlin/ambient/`:

- `AmbientModels.kt` — `AmbientMode` (`aerial`, `clock`, `minimal`, `photos`), `AmbientClockFormat` (`system`, `twelve`, `twenty_four`), `AmbientSettings`, `AmbientSettingsStore`, `AmbientScheduler`.
- `AmbientPixelShift.kt` — чистый детерминированный сдвиг часов ±9/±6 dp раз в минуту; возврат в центр при выключении.
- `AmbientPowerPolicy.kt` — чистая политика dim (80% → 60%) и max session; без Android-часов и догадок о панели.
- `AmbientPlaylist.kt` — единая точка сборки Aerial-плейлиста из `AmbientSettings`; Clock/Minimal не требуют клипов.
- `AmbientClock.kt` — чистое форматирование времени/даты; формат выбирает `AmbientClockFormat`.
- `AmbientPhotoStore.kt` — persisted grant на дерево (`ambient_photo_tree_uri`) **или** до 50 отдельных снимков (`ambient_photo_selected_uris`); источники взаимоисключающие; `clear()` освобождает grant, но не трогает файлы.
- `AmbientPhotoLibrary.kt` — один уровень `DocumentsContract`-запроса, только `image/*`, лимит 300, сортировка по имени; без полного сканирования медиатеки.
- `AmbientPhotoDecoder.kt` — `ImageDecoder` с `ALLOCATOR_SOFTWARE` и target size ≤1920×1080; ошибка/OOM → `null`, без падения.
- `AmbientPhotoPlaylist.kt` — перемешивание при ≥2 снимках; один снимок показывается без повторов.

Ключи:

- `ambient_mode` — новый, отсутствует → `aerial`; значения `aerial`, `clock`, `minimal`, `photos`;
- `ambient_photo_tree_uri` / `ambient_photo_selected_uris` — device-local, **не** входят в backup;
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

Photos:

- Режим использует **только системный выбор**: папка через `OPEN_DOCUMENT_TREE` либо до 50 снимков через `PICK_IMAGES`; нет `READ_MEDIA_IMAGES` и нет широкого доступа к хранилищу.
- Если системный DocumentsUI на ТВ отсутствует (AOSP TV stub), строка предлагает photo picker; если нет и его — честное «Unavailable», без фальшивого выбора.
- Кадр декодируется off-main ≤1920×1080, держится 12 с, кроссфейд 1200 мс; ошибки пропускаются, после всех ошибок остаётся градиент.
- В Kids-профиле личные фото не читаются: показывается нейтральный Clock-fallback.
- Если источник не выбран или grant потерян — Clock-fallback, а не пустой градиент.
- AVD: 2 снимка выбраны через photo picker, preview и full-screen показывают реальные фото, ротация и BACK проверены; после force-stop выбор сохраняется. **D-pad в системном photo picker не подтверждён** (на AOSP-образе фокус входил в сетку через Tab/касание).

### 3. Home и DreamService

- `LauncherRoot.kt` читает `AmbientSettingsStore.load(...)` на resume; Aerial-плейлист строится через `AmbientPlaylist.scenes(...)`.
- Новый режим не требует клипов; таймер использует `AmbientScheduler.isDue(...)`.
- В Clock/Minimal пара `ExoPlayer` освобождается, warm-still и Aerial-refresh не запускаются.
- `AerialDreamService` показывает выбранный режим; Clock/Minimal/Photos не загружают Aerial-каталог и не ходят в сеть.
- Системная подпись изменена на `OVisionTV Ambient` / `OVisionTV: заставка`.

### 4. Настройки

Новая IA:

```
Settings
  └── Personalization
        ├── Language
        └── Ambient Experience
              ├── Ambient Mode → Aerials / Clock / Minimal / Photos
              ├── Start After
              ├── Show During Music
              ├── Clock Format / Pixel Shift (только Clock)
              ├── Dim After / Max Session (Clock и Minimal)
              ├── Show Weather (только Clock; Off по умолчанию)
              ├── Selected Photos / Photos Folder (только Photos)
              ├── Forget Photos / Forget Folder (только при выбранном источнике)
              ├── Aerial Clips / Themes / Catalogue Check (только Aerials)
              ├── System screen saver
              └── Reset
```

- `PAGE_SAVER_MODE` — radio-страница режимов.
- `PAGE_SAVER_CLOCK_FORMAT` — System / 12-hour / 24-hour; в Clock виден `Pixel Shift` toggle.
- `PAGE_SAVER_DIM`, `PAGE_SAVER_SESSION` — radio-страницы; по умолчанию `Never`, поведение без явного выбора не меняется.
- В Clock/Minimal/Photos Aerial-строки скрыты, чтобы не предлагать темы, которые не рисуются.
- `Download Frequency` переименована в `Catalogue Check`: публичный каталог Apple сегодня старее встроенного, поэтому проверка обычно оставляет встроенные ролики. Ключ `saver_refresh` не менялся.
- `Choose Screen Saver` переименован в `Aerial Clips`; тупиковый выбор с одним пунктом убран.
- Photos: строка источника показывает «Selected Photos → N photos» для photo picker или имя папки для дерева; «Not selected» / «Unavailable», если источника нет; «Forget» освобождает grant и возвращает режим в `Aerials`.
- `Reset Screen Saver` сбрасывает режим и **освобождает** photo grant: после сброса личные снимки больше не доступны приложению.
- Preview в Settings: Aerial — реальный still; Clock — локальные часы и, если включено, cached погода; Minimal — локальный градиент; Photos — первый выбранный снимок (off-main decode), при отсутствии источника — Clock-fallback. Декодер не запускается для Aerial/Clock/Minimal.

### 5. Backup

- `SaverData.mode`, `clockFormat`, `pixelShift`, `dimAfter`, `maxSession`, `showWeather` — необязательные поля; старые архивы без них восстанавливаются как `aerial`, `system`, `true`, `never`, `never`, `false`.
- `BackupSource` читает `ambient_mode`, `ambient_clock_format`, `ambient_pixel_shift`, `ambient_dim_after`, `ambient_max_session`, `ambient_show_weather`.
- `BackupRestoreEngine` пишет все шесть полей в том же commit, что и остальные saver-ключи.
- Schema остаётся 1: поле обратно совместимо.
- Photos-источник **не** входит в backup: URI-гранты принадлежат устройству. Восстановленный режим `photos` без источника показывает Clock-fallback и приглашает выбрать снимки заново.

## Проверено

| Проверка | Результат |
|---|---|
| `:app:testDebugUnitTest` | **461/461** |
| `:app:connectedDebugAndroidTest` на AVD `hume-tv` API 34 | **248/248** |
| `:app:lintDebug` | **0 errors / 18 warnings** |
| `:app:assembleRelease` | **Success**, APK **4 709 198 B**, подпись SHA-256 `327a5045…67d7b10` (не изменилась) |
| AVD, визуально | Home → Personalization → Ambient Experience; Aerials/Clock; Clock Format; Dim After/Max Session; Show Weather; preview с реальной погодой; full-screen clock; any key exits |
| AVD, Photos | photo picker → 2 снимка → «2 photos» → preview и full-screen реальные фото → ротация → BACK; выбор сохраняется после force-stop |
| Privacy policy | версия 3: Photos описаны как локальный grant системного выбора, без upload и без backup |
| Backup | режим, формат, pixel shift, dim, max session и showWeather входят в backup/restore; старый архив без полей → Aerial / System / On / Never / Off |
| Aerial | старый `SaverScreen` остаётся рендерером; поведение не менялось |

Новые тесты:

- `ambient/AmbientModelsTest` — режимы и `AmbientClockFormat` (system/12/24, fallback, стабильные ключи)
- `ambient/AmbientClockTest` — 12/24-форматирование и локализованная дата
- `ambient/AmbientPlaylistTest`
- `ambient/AmbientPixelShiftTest` — центр, пределы ±9/±6 dp, цикл
- `ambient/AmbientPowerPolicyTest` — пороги 10/20/30 мин, шаги 80/60%, session Never/30 мин
- `ambient/AmbientPhotoPlaylistTest` — один/несколько снимков, сохранение набора
- `ambient/AmbientPhotoDecoderTest` — большое изображение ограничивается 1920×1080, битый файл → null
- `ui/components/AmbientScreenTest` — Photos без источника → Clock, Kids → Clock, выбранное фото → surface
- `PrivacyPolicyTextTest.photosAreDisclosedAsALocalPickerGrantAndNotBackedUp`
- `SettingsScreenTest.the_ambient_mode_page_switches_between_aerial_and_clock`
- `SettingsScreenTest.the_clock_page_sets_a_12_24_format_and_pixel_shift`
- `SettingsScreenTest.the_clock_page_sets_dim_and_max_session`
- `SettingsScreenTest.the_clock_page_offers_weather_when_the_weather_feature_is_on`
- `SettingsScreenTest.the_clock_page_points_to_weather_settings_when_weather_is_off`
- `SettingsScreenTest.the_photos_mode_names_its_folder_and_never_pretends_it_is_chosen`
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
- Photos: **D-pad-only** выбор в системном photo picker (AOSP-образ требует Tab/касания); реальный SAF-folder picker на vendor TV; фото на физической панели;
- Cinematic/Abstract — не реализованы;
- погода на часах не проверена при stale-кэше на реальном устройстве; источник и атрибуция остаются в `Settings → Weather`;
- process death/reboot во время Photos;
- 24 h Photos soak;
- 24 h soak.

## Следующие шаги

1. Blackout/расписание ночи — только после явного решения владельца; сейчас dim-ступени есть, blackout нет.
2. Photos: проверить D-pad в photo picker на реальном Google TV; если он недоступен, решить, оставлять ли режим только для пультов с Tab/указателем.
3. Stage 6 — 4K/HDR только после лицензии и проверки декодера/панели.
4. Физическая матрица устройств и измерения.

**Это не сертификация.** Всё, что не проверено на физическом устройстве, остаётся непроверенным.

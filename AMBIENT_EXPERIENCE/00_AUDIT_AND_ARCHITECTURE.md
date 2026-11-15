# Ambient Experience / Screensaver — аудит и архитектура

**Проект:** OVisionTV Launcher (Kotlin, Android TV / Google TV)
**Версия:** 0.5.0 (`versionCode 73`) на момент аудита; реализация Stage 2–4b — 0.5.5 (`versionCode 78`), `minSdk 28`, `targetSdk 35`
**Дата аудита:** 2026-11-15
**Статус аудита:** Stage 1–3 выполнены: аудит, архитектура, UX-спецификация.
**Реализация:** Stage 2–4b проверены на AVD: `ambient`-слой, режимы Aerial/Clock/Minimal, Clock Format (System/12/24), Pixel Shift, optional Dim/Max Session, opt-in Weather на часах (только cached данные), IA `Personalization → Ambient Experience`, backup-совместимость. См. `01_IMPLEMENTATION_STATUS.md`.
**Аудит выполнялся read-only;** изменения кода — отдельный этап после него.
**Следующий шаг:** blackout/ночное расписание — только после решения владельца; затем Stage 5 — Photo Gallery. 4K/Cinematic — только после лицензионного решения.

---

## 0. Резюме для владельца

1. В проекте уже есть **рабочая, аккуратно сделанная однорежимная Aerial-заставка**, а не «Ambient Experience» в смысле tvOS.
2. Заставка имеет **две точки входа**: собственная Home-оверлей-заставка и системный `DreamService`. Обе используют один `SaverScreen`.
3. Контент — **100 реальных роликов Apple Aerial** в четырёх темах, с реальными still-кадрами и реальными названиями. Никаких фейковых изображений нет.
4. Качество — **1080p H.264 SDR**. 4K, HEVC, HDR, 10-bit и выбор display mode **не реализованы**; на 4K-панели это апскейл 1080p.
5. Настройка **Download Frequency** сейчас фактически **не даёт обновлений**: публичный tar Apple — старая плоская схема, а `AerialCatalogBuilder.supports()` принимает только category schema. Код честно возвращает `UNCHANGED` и сохраняет встроенный каталог, но пользователь видит настройку, которая ничего не меняет.
6. UX на момент аудита: путь `Settings → Interface → Screen Saver`; строка «Choose Screen Saver» — **тупиковый выбор с одним пунктом** «Aerials». Нет режимов, нет «Personalization → Ambient Experience», нет выбора «Clock», «Weather», «Photos», «Minimal», «Abstract», «Cinematic Photography». **Stage 3 исправил это для Aerial/Clock/Minimal, включая формат часов и Pixel Shift.**
7. OLED-защита отсутствует: статический нижний градиент, статичная подпись, нет pixel shift, нет dim/blackout, нет ограничения сессии. `keepScreenOn` держит экран включённым, пока оверлей виден.
8. Энергопотребление не измерялось. В активной заставке работают **два ExoPlayer-декодера** (один играет, второй префетчит), плюс `TextureView`-композиция и still-битмап.
9. Надёжность — сильная сторона: таймауты старта/стопа, 3 последовательных отказа → stills-only, backoff 15/30/60/120 с, отсутствие «чёрного экрана» при отсутствии картинки, отказ от HTTP-даунгрейда.
10. Тестами покрыта логика расписания, настроек, плейлиста, каталога, парсинга, still-кэша и поведения `SaverScreen`; **нет** теста `AerialDreamService`, реального воспроизведения, OLED/энергии, 4K, TalkBack и физического ТВ.

**Вердикт:** как Aerial screen saver — **8/10 и можно оставлять**. Как Ambient Experience — **3/10**, потому что это один режим с монолитным рендерером 922 строки. Рекомендуется **не переписывать**, а выделить `ambient`-движок из существующего кода, сохранив Aerial поведение и тесты.

---

## A. Аудит текущей реализации

### A.1. Инвентаризация

| Файл | Роль | Размер/факт |
|---|---|---|
| `AerialDreamService.kt` | системный `DreamService` | 136 строк |
| `ui/components/SaverScreen.kt` | полноэкранный Compose-оверлей, плееры, кроссфейд, still, media session | 922 строки |
| `data/SaverSchedule.kt` | чистое правило «пора начинать» | без Android-типов |
| `data/SaverPlaylist.kt` | сборка плейлиста и выбор preview-клипа | чистые функции |
| `data/SaverPreferences.kt` | delay, music, refresh, themes, hidden | `SharedPreferences` `LauncherPrefs.NAME` |
| `data/AerialCatalog.kt` | модель `AerialClip`, парсер, выбор saved/bundled | 100 клипов в asset |
| `data/AerialCatalogBuilder.kt` | сборка каталога из Apple `entries.json` | только `url-1080-H264` |
| `data/AerialRefresh.kt` | загрузка `resources.tar` с Apple, TLS allowlist | 197 строк |
| `data/AerialClipCache.kt` | удаление старого дискового кэша клипов | cleanup |
| `ui/components/AerialPosterStore.kt` | 1080p first-frame stills, LRU + JPEG | 16 файлов, max 1920×1080 |
| `ui/components/SaverPreview.kt` | статичный still в Settings | 800×450 |
| `ui/components/SaverPreviewImage.kt` | `AerialPreviewStore`, thumbs | 900×580 PNG, лимиты |
| `ui/components/SaverLabels.kt` | EN/RU подписи тем | 4 темы |
| `ui/SettingsScreen.kt` | страницы `PAGE_SAVER*`, chooser | 7 строк настроек + grid |
| `ui/LauncherRoot.kt` | idle-таймер, Home-оверлей, warm still, decoder pair | основная интеграция |
| `MainActivity.kt` | комментарий про tvOS idle; системные бары | `:80` |
| `AndroidManifest.xml` | `AerialDreamService` с `BIND_DREAM_SERVICE` | `:181–193` |
| `NOTICE` | Apple ©, AerialViews GPL-3.0 derived catalogue | `:12–16` |
| `res/raw/privacy_en.txt` | раскрытие Apple CDN | `:21` |

### A.2. Две точки входа и один рендерер

**1. Home-оверлей (`LauncherRoot.kt`)**

- Живёт только пока launcher — foreground Home. При уходе в другое приложение или Settings `saverVisible = false` (`LauncherRoot.kt:214–219`).
- Idle-время считается от `SystemClock.elapsedRealtime() - lastActivity`; вход, возврат из Settings, музыкальный callback и resume будят таймер (`:176–189`, `:321–356`).
- Таймер **спит до дедлайна**, а не просыпается каждую секунду. Это хорошее энергосберегающее решение.
- Музыка: `SaverSchedule.isDue(delay, idleMs, musicPlaying, showDuringMusic)`. При музыке и выключенном «Show During Music» ждёт 15 с и перепроверяет, без бесконечного опроса.
- Не открывается над `pickerVisible` или `pinGate` — это правильная защита от заставки поверх PIN-пада.
- Перед показом прогревает still первого клипа и заранее строит пару `ExoPlayer` (`:238–281`), чтобы первый кадр не ждал decoder start.
- Home под оверлеем уменьшается до `0.7` за 500 мс, затем получает `alpha = 0`, чтобы Compose не растеризовал сетку под видео (`:197–210`, `:557`).

**2. Системный `DreamService` (`AerialDreamService.kt`)**

- `isInteractive = false`, `isFullscreen = true` (`:45–46`).
- Загружает каталог и строит плейлист при attach (`:106–127`), использует те же `SaverPreferences.themes/hidden`, `SaverPlaylist.build`, `SaverScreen`.
- Не использует пользовательский delay — частота запуска принадлежит системным настройкам ТВ. Это корректно.
- Если плейлист пуст — завершает dream (`onFinished`), не показывает пустой экран.
- `publishMediaSession = true`, `handleBack = false`: системные wake-keys закрывают dream, `BackHandler` не нужен.
- Не имеет собственного `settingsActivity`; строка «System screen saver» открывает `Settings.ACTION_DREAM_SETTINGS` с fallback в общие настройки (`SettingsScreen.kt:4104–4119`).

**3. Preview в Settings**

- `SaverPreview` — статичный still 800×450 в правой колонке, без второго декодера.
- Долгое OK на клипе открывает **настоящий** `SaverScreen(items = listOf(clip), swallowOpeningPress = true)` (`SettingsScreen.kt:3213–3228`).
- Preview не публикует media session. Это правильно: Settings не должен перехватывать системный Now Playing.
- `SaverScreen` без переданных `players` создаёт **свою пару декодеров** внутри Settings; на слабом ТВ это может дать задержку открытия preview.

### A.3. Планировщик и состояния

| Состояние | Где | Поведение |
|---|---|---|
| delay | `SaverPreferences.KEY_DELAY_MINUTES` | 2/5/10/20/`NEVER`, default 5 |
| music | `SaverPreferences.KEY_MUSIC` | **default `true`** — заставка показывается во время музыки |
| themes | `SaverPreferences.KEY_THEMES` | 4 темы, нельзя выключить последнюю |
| hidden | `SaverPreferences.KEY_HIDDEN_CLIPS` | нельзя скрыть последний видимый клип |
| refresh | `SaverPreferences.KEY_REFRESH` | daily/weekly/monthly/never, default weekly |
| preview | Settings only | hold OK, не влияет на Home |
| Dream | system | системный idle, не пользовательский delay |

`SaverSchedule.isDue` — чистая функция, тестируется без Android (`SaverScheduleTest`). Это правильный слой, его нужно сохранить и расширить, а не дублировать.

### A.4. Плейлист и каталог

- В asset **100 клипов**: `landscapes` — 57, `earth` — 18, `underwater` — 14, `cities` — 11.
- У всех 100 есть `preview` 900×580; все видео и preview — `https://sylvan.apple.com/...`.
- `SaverPlaylist.build` фильтрует по темам и hidden, затем `shuffled(random)`. При `random == null` — порядок каталога (для тестов).
- Нет «bag without repeat»: на границе цикла клип может повториться; между сессиями нет recency. Для 100 клипов это не критично, но для «Ambient» лучше иметь no-repeat bag.
- `SaverPlaylist.previewClip` выбирает первый клип первой включённой темы, поэтому Settings-preview следует за темами.
- `AerialCatalog.load` берёт saved-каталог только если он не меньше bundled и содержит preview; иначе bundled. Это защищает от отката на старый неполный каталог.

### A.5. Рендеринг, тайминги, переходы

| Параметр | Значение | Комментарий |
|---|---:|---|
| `SAVER_STILL_MS` | 26 000 | still, если видео не играет |
| `SAVER_CLIP_MS` | 30 000 | excerpt, всегда с головы клипа |
| `SAVER_CROSSFADE_MS` | 1 200 | кроссфейд двух `TextureView` |
| `SAVER_STILL_FADE_MS` | 600 | скрытие still под видео |
| `SAVER_ENTRY_MS` | 500 | scale 1.3 → 1.0 |
| `SAVER_ENTRY_ALPHA_MS` | 250 | alpha 0 → 1 |
| `SAVER_EXIT_MS` | 330 | выход |
| `SAVER_REDUCED_MS` | 400 | Reduce Motion |
| `SAVER_ZOOM` | 1.06 за 26 с | slow push для still |
| `FIRST_FRAME_LEAD_IN_MS` | 1 500 | Apple masters открываются с чёрного |
| `CLIP_START_TIMEOUT_MS` | 20 000 | старт клипа |
| `CLIP_STALL_TIMEOUT_MS` | 12 000 | stall |
| `PREFETCH_TIMEOUT_MS` | 20 000 | префетч следующего |
| `NEXT_START_TIMEOUT_MS` | 8 000 | старт следующего |
| `MAX_CONSECUTIVE_FAILURES` | 3 | затем stills-only |
| `PLAYER_POLL_MS` | 200 | 5 Гц polling состояния |

**Что сделано хорошо:**

- `TextureView`, а не `SurfaceView`, потому что нужен alpha-кроссфейд (`saver_player.xml`).
- Slot alpha ведётся через `snapshotFlow`, а не через рекомпозицию всего экрана на каждом кадре кроссфейда (`SaverScreen.kt:365–390`).
- `INVISIBLE` намеренно не используется: TextureView должен продолжать получать кадры, иначе ExoPlayer не пришлёт `onRenderedFirstFrame` и заставка застрянет на still.
- Пока видео нет, still реальный; при отсутствии и still, и видео — watchdog закрывает заставку через 15 с, чтобы не держать пустой тёмный экран.
- `keepScreenOn = true` только на время оверлея (`SaverScreen.kt:744–745`), а не глобально.
- `volume = 0f`, `setAudioAttributes(..., false)` — заставка не забирает аудиофокус (`SaverScreen.kt:919–920`).
- MediaSession ставится только для Home overlay и Dream, не для Settings preview.

### A.6. Отказы и восстановление

- Ошибка плеера → лог, счётчик, после 3 отказов — stills-only.
- Stills-only проходит полный круг, затем backoff `15/30/60/120 с` и снова пробует видео (`saverOfflineRetryDelay`).
- При недоступном первом кадре — still; при отсутствии still — закрытие.
- HTTPS-only: `AerialCatalog` отклоняет `http://`; `AerialRefresh` не следует на другой хост/схему.
- Нет «тихого» бесконечного ретрая: есть потолок и backoff. Это соответствует stability-брифу.

**Слабое место:** при полностью офлайн-ТВ заставка сначала тратит время на сетевые попытки (до 20 с на старт), и только потом уходит в stills-only. Нет предварительной проверки `NetworkCapabilities`/`ACCESS_NETWORK_STATE`, хотя permission уже есть.

### A.7. Настройки: что видит пользователь

На момент аудита путь был: `Settings → Interface → Screen Saver`. После Stage 3: `Settings → Personalization → Ambient Experience`. Страница `PAGE_SAVER` содержала 7 строк (`SettingsScreen.kt:1218–1269`); теперь первая строка — `Ambient Mode`, а Aerial-строки показываются только в режиме Aerial.

1. Choose Screen Saver — значение «Aerials», открывает chooser с одним типом.
2. Start After — 2/5/10/20/Never.
3. Show During Music — On/Off.
4. Themes — 4 темы.
5. Download Frequency — Daily/Weekly/Monthly/Never.
6. System screen saver — открыть системные настройки.
7. Reset Screen Saver.

Chooser: grid 3 колонки, боковая панель категорий, счётчики, скрытие группы, hold OK → preview. Это хороший D-pad UI, но он относится только к Aerial.

`PAGE_SAVER` — не «Ambient Experience»: нет выбора режима, нет персонализации, нет preview-режимов, нет OLED/energy, нет расписания.

### A.8. Тесты

Есть:

- `SaverScheduleTest` — границы delay, `NEVER`, музыка.
- `SaverSettingsTest` — темы, последняя тема, hidden, группы, legacy `space`, кодирование.
- `SaverPlaylistTest` — сборка/фильтрация/preview.
- `SaverRefreshScheduleTest` — интервалы.
- `AerialCatalogTest` — HTTPS-only, legacy URL upgrade, preferred catalogue.
- `AerialCatalogBuilderTest` — сборка из entries/plist.
- `AerialRefreshUrlTest` — trusted host/redirect.
- `AerialTarTest` — безопасное извлечение.
- `SaverScreenTest` (9 кейсов, Compose) — заполнение экрана, любая клавиша закрывает, BACK, приоритет над grid, exit не раньше fade, отсутствие картинки → закрытие, прозрачный фон без картинки, still удерживает экран, клип с ошибкой не ломает saver.
- `SaverPreviewTest` (6 кейсов) — локализация, битые/огромные PNG, удаление битого файла, downsampling, обычный still/thumb.

Нет:

- теста `AerialDreamService` (attach/detach, пустой каталог, lifecycle);
- теста реального воспроизведения (только заведомо недоступный `https://127.0.0.1:1`);
- теста `AerialPosterStore` на 1080p capture/readback;
- теста media session;
- теста OLED/power;
- теста 4K/HEVC/HDR;
- теста TalkBack и физического пульта;
- теста системного chooser заставки на реальном ТВ.

### A.9. Что уже работает как продукт

- Заставка запускается на Home после выбранной задержки, не мешает PIN/профилям.
- Системная заставка доступна через `DreamService`.
- 100 реальных Apple Aerial, 4 темы, EN/RU названия.
- Реальные stills и first-frame poster cache.
- Кроссфейд без чёрного кадра, с прогревом.
- Stills-only fallback и backoff.
- D-pad chooser, скрытие клипов/категорий, preview.
- Настройки сохраняются и входят в backup (`BackupSource`/`BackupRestoreEngine`).
- Reduce Motion частично учитывается.
- Нет аудиофокуса и нет глобального `keepScreenOn`.

### A.10. Ограничения (findings)

Таблица фиксирует состояние **на момент аудита**. После Stage 3–4b частично закрыты AMB-01, AMB-02, AMB-04, AMB-05 и AMB-11 (Aerial/Clock/Minimal + IA + Pixel Shift + optional Dim/Max Session + Weather-строка на часах); 4K, Photos, отдельный Weather-режим, blackout и энергия остаются открытыми.

| ID | Severity | Finding | Evidence |
|---|---|---|---|
| AMB-01 | High (product) | Нет Ambient-режимов: только Aerial. «Choose Screen Saver» — выбор с одним пунктом. | `SettingsScreen.kt:1218–1225`, `SaverTheme` |
| AMB-02 | High (product) | Нет IA «Personalization → Ambient Experience»; заставка спрятана в `Interface`. | `SettingsScreen.kt:1316–1332` |
| AMB-03 | High (quality) | Только 1080p H.264 SDR; нет 4K/HEVC/HDR/10-bit/display-mode. На 4K-панели — upscale. | `AerialCatalogBuilder.kt:66`, `AerialPosterStore.kt:27–28` |
| AMB-04 | High (truthfulness) | Download Frequency не обновляет каталог в текущей публичной выдаче Apple: flat schema → `UNCHANGED`. **Частично закрыто:** строка переименована в `Catalogue Check`, на странице честно сказано, что публичный каталог Apple старее встроенного; поведение (проверка → `UNCHANGED`) не выдаётся за загрузку. | `AerialRefresh.kt:110–115`, `SettingsScreen.kt` |
| AMB-05 | High (OLED) | Нет pixel shift, dim, blackout, max session; статический градиент и подпись. | `SaverScreen.kt` bottom gradient/caption |
| AMB-06 | Medium (architecture) | `SaverScreen` — монолит: плееры, плейлист, still, media session, анимации, captions, watchdog в одном файле 922 строки. Новый режим нельзя добавить без усложнения. | `SaverScreen.kt` |
| AMB-07 | Medium (power) | Два декодера + два TextureView + still + readback; нет измерений и low-power режима. | `createSaverPlayers`, `capturePoster` |
| AMB-08 | Medium (offline) | Нет проверки сети перед стартом; офлайн тратит до 20 с на попытку. | `startFromHead` |
| AMB-09 | Medium (content) | Нет 4K-контента и лицензионного источника для «Cinematic Photography»; Apple CDN — не гарантия и не SLA. | `NOTICE:12–16` |
| AMB-10 | Medium (privacy/profile) | Настройки заставки глобальные, не per-profile; будущие фото/погода потребуют profile scoping. | `SaverPreferences.prefs` → `LauncherPrefs.NAME` |
| AMB-11 | Medium (UX) | Нет режимов Photos/Abstract и отдельного Weather; расписания (ночь/день) нет. Clock/Minimal и opt-in погода на часах реализованы. | modes matrix |
| AMB-12 | Medium (playback) | Каждый клип всегда с головы, всегда 30 с; нет recency/no-repeat, нет вариативных фрагментов. | `startFromHead` |
| AMB-13 | Low (accessibility) | Reduce Motion влияет на entrance и zoom, но не на 1.2 с кроссфейд и caption fade; нет отдельного caption toggle; TalkBack не проверен. | `SaverScreen.kt` |
| AMB-14 | Low (UX) | Нет подсказки «любая кнопка — выход» на экране; только accessibility description. | `saver_screen_description` |
| AMB-15 | Low (reliability) | `DreamService` не тестирован; при `onDreamingStopped` lifecycle переводится в CREATED, но освобождение плееров зависит от detach/Compose dispose. | `AerialDreamService.kt` |
| AMB-16 | Low (legal) | Нет юридического подтверждения права стримить Apple Aerial в стороннем лаунчере; каталог — GPL-3.0 derived data. | `NOTICE` |
| AMB-17 | Low (quality) | Stills 900×580 апскейлятся до 1920×1080 до первого проигрывания; после — 1080p poster. | `AerialPreviewStore`, `AerialPosterStore` |
| AMB-18 | Low (settings) | «Show During Music» default On; нужно подтвердить, что это ожидаемое поведение, а не наследие. | `SaverPreferences.music` |

### A.11. Чего аудит **не** проверял

- Реальное воспроизведение с Apple CDN на физическом ТВ.
- 4K/HEVC/HDR, 24p/30p cadence, display mode switching.
- Энергопотребление ваттметром, нагрев, throttling.
- OLED burn-in на реальной панели.
- TalkBack, IR/Bluetooth/vendor remotes.
- `DreamService` в системном chooser на вендорских прошивках.
- Сетевые блокировки порта 443, MITM, CDN-сбои.
- Процесс death/OOM/ANR во время заставки.
- 24-часовой soak.
- Юридическую допустимость Apple CDN.

Это не «пройдено». Это **не проверено**.

---

## B. Матрица Ambient-режимов

| Режим | Сейчас | Реальный источник | Что нужно | Риск | Приоритет |
|---|---|---|---|---|---|
| **Aerial & Landscape** | **Есть**: 4 темы, 100 клипов | Apple Aerial CDN, HTTPS | 4K/HDR-варианты, лицензия, no-repeat bag | средний | P0 — сохранить |
| **Cinematic Photography** | Нет | Нужны лицензированные stills или фото пользователя | 4K stills, Ken Burns/parallax, color grading, attribution | высокий (лицензия) | P2 |
| **Minimal Ambient** | Нет | Время + градиент/фон | низкая мощность, pixel shift, часы | низкий | P1 |
| **Photo Gallery** | Нет | Личные фото через SAF (без `READ_MEDIA_IMAGES`) | выбор папки, EXIF, per-profile scope, приватность | высокий (приватность) | P2 |
| **Weather** | Нет в заставке; погода есть на Home | `Weather.kt` (Open-Meteo/MET/wttr), off by default | реальные данные, units, attribution, opt-in | средний (приватность/сеть) | P1 |
| **Abstract Motion** | Нет | Процедурная графика/шейдер | GPU-бюджет, Reduce Motion, OLED-контроль | низкий/средний | P2 |
| **Clock & Info** | Нет | Системное время/дата; погода опционально | 12/24 ч, safe area, pixel shift, низкая мощность | низкий | P1 |
| **Aerial + Clock overlay** | Нет | Aerial + время | композиция, не перегружать экран | низкий | P1 |

### B.1. Что считать «Ambient Experience»

Не «набор обоев», а **режим экрана ожидания** с четырьмя осями:

1. **Content** — что показывается (видео, фото, процедурная графика, информация).
2. **Motion** — как (cut, crossfade, Ken Burns, none).
3. **Information** — часы/погода/дата, опционально.
4. **Protection** — OLED/energy/session limits.

Сейчас реализована только ось Content в одном варианте (Aerial), Motion — crossfade, Information — нет, Protection — нет.

### B.2. Рекомендуемый набор v1

Не пытаться сделать все 7 режимов сразу. Предлагается:

- **v1 (P0):** Aerial (как есть) + Clock & Info overlay (опционально) + Minimal (фон + часы).
- **v1.1 (P1):** Weather (реальные данные, off by default) + OLED protection.
- **v2 (P2):** Photo Gallery (SAF, per-profile) + Cinematic Photography (лицензированный контент) + Abstract Motion.

Это даёт видимую эволюцию без раздувания Home и без новых permissions.

---

## C. Архитектура

### C.1. Принципы

1. **Не строить параллельную систему.** `AerialCatalog`, `SaverPlaylist`, `SaverPreferences`, `AerialPosterStore`, `AerialPreviewStore`, `SaverScreen` player/fallback и `AerialDreamService` переиспользуются.
2. **Один Ambient Engine, два адаптера.** Home overlay и Dream — только платформенные оболочки.
3. **Настройки — данные, а не ветвления в UI.** Режим, контент, motion, info, protection — отдельные поля.
4. **Чистая логика отдельно от Android.** Планировщик, плейлист, выбор сцены, capability negotiation — unit-testable.
5. **Ни одного фейкового кадра.** Если сцена не готова — реальный still, затем закрытие/следующая сцена; не рисовать «заглушку».
6. **Никаких новых permissions без approval.** Photo Gallery — SAF, не `READ_MEDIA_IMAGES`. Weather — существующие opt-in настройки.
7. **Не ломать focus.** Settings — существующие integer cursors и `SettingsRow`; preview — существующий hold OK; saver — «любая клавиша».
8. **Не менять backup-контракт без версии.** Новые ключи — schema 2, старые `saver_*` читаются.

### C.2. Целевой пакет

```
ambient/
  AmbientMode.kt          // режимы и их возможности
  AmbientSettings.kt      // типизированные настройки + миграция saver_*
  AmbientScene.kt         // сцена: video/still/composed/procedural
  AmbientSource.kt        // интерфейс источника сцен
  AmbientPlaylist.kt      // no-repeat bag, recency, preview
  AmbientScheduler.kt     // idle/music/schedule/gates → isDue
  AmbientEngine.kt        // state machine: Idle/Entering/Playing/Fading/Exited
  AmbientPowerPolicy.kt   // профиль, OLED, max session, dim
  AmbientCapabilities.kt  // decoder/panel negotiation (1080/4K/HDR)
  source/
    AerialSource.kt       // обёртка над AerialCatalog/SaverPlaylist
    WeatherSource.kt      // обёртка над Weather.kt
    ClockSource.kt        // системное время
    PhotoSource.kt        // SAF/URI, без новых permissions
    ProceduralSource.kt   // градиенты/шейдер
  render/
    AmbientSurface.kt     // общий full-screen host, alpha/scale/exit
    AerialSceneRenderer.kt// текущий player/still/crossfade
    PhotoSceneRenderer.kt // sampled decode + Ken Burns
    ClockRenderer.kt
    WeatherRenderer.kt
    ProceduralRenderer.kt
  platform/
    HomeOverlayAdapter.kt // LauncherRoot timer + SaverScreen
    DreamAdapter.kt       // AerialDreamService
```

Это **не** второй launcher и не второй Focus Engine. Это извлечение уже существующих обязанностей `SaverScreen` в тестируемые слои.

### C.3. Модель данных

```kotlin
enum class AmbientModeId { AERIAL, MINIMAL, CLOCK, WEATHER, PHOTOS, CINEMATIC, ABSTRACT }

data class AmbientMode(
    val id: AmbientModeId,
    val titleRes: Int,
    val contentKind: ContentKind,
    val powerClass: PowerClass,       // VIDEO, STILL, INFO, PROCEDURAL
    val needsNetwork: Boolean,
    val needsLicense: Boolean,
    val supportsClockOverlay: Boolean,
)

sealed interface SceneContent {
    data class Video(val url: String, val mime: String) : SceneContent
    data class Still(val uri: String, val width: Int, val height: Int) : SceneContent
    data class Composed(val clock: Boolean, val weather: Boolean) : SceneContent
    data class Procedural(val preset: String) : SceneContent
}

data class AmbientScene(
    val id: String,
    val mode: AmbientModeId,
    val title: String,
    val subtitle: String?,
    val stillUri: String?,
    val content: SceneContent,
    val durationMs: Long,
    val attribution: String?,
)
```

Ключевое: **Aerial-сцена остаётся видео-сценой**, поэтому текущий `SaverScreen` можно постепенно превратить в `AerialSceneRenderer`, не меняя поведение.

### C.4. `AmbientSource` и capability negotiation

```kotlin
interface AmbientSource {
    suspend fun load(prefs: AmbientSettings): List<AmbientScene>
    fun preview(prefs: AmbientSettings): AmbientScene?
}
```

- `AerialSource` делегирует в `AerialCatalog.load` + `SaverPlaylist.build`.
- `PhotoSource` читает URI, выданные через SAF; не сканирует всю медиатеку.
- `WeatherSource` использует только уже сохранённое место/данные; если погода выключена — сцена не создаётся.
- `ClockSource` не делает сетевых вызовов.

`AmbientCapabilities`:

```kotlin
data class AmbientCapabilities(
    val maxWidth: Int,
    val maxHeight: Int,
    val hevc: Boolean,
    val hdr: Boolean,
    val frameRate: Int?,
)
```

Правило: выбрать **наибольший вариант, который декодер и панель реально поддерживают**, иначе 1080p SDR. Не показывать 4K на 1080p-панели и не включать HDR без подтверждения.

### C.5. `AmbientScheduler`

Текущий `SaverSchedule.isDue` расширяется, а не дублируется:

```kotlin
data class AmbientIdleState(
    val nowMs: Long,
    val lastActivityMs: Long,
    val musicPlaying: Boolean,
    val foreground: Boolean,
    val gateVisible: Boolean,
    val mode: AmbientModeId,
    val schedule: AmbientSchedule, // delay + quiet hours + duringMusic
)

object AmbientScheduler {
    fun isDue(state: AmbientIdleState): Boolean
    fun nextWakeMs(state: AmbientIdleState): Long? // null = ждать события
}
```

Home adapter использует `nextWakeMs` вместо собственного `while(true) delay(...)`, Dream adapter вызывает `onDreamingStarted()`.

### C.6. `AmbientEngine` state machine

```
Idle
  → Entering (fade/scale, still warm)
  → Playing (scene N)
  → Crossfading (scene N+1)
  → Playing
  → Fading (any key / timeout)
  → Exited
```

Ответственность:

- выбрать следующую сцену;
- держать не более двух декодеров;
- префетчить следующую сцену;
- отдавать `Scene` рендереру;
- при ошибке — следующая сцена или stills-only;
- при отсутствии картинки — закрыть;
- освобождать плееры при `Exited` и при `onPause`/`onDreamingStopped`.

`SaverScreen` становится **host + Aerial renderer**, а не владельцем всей логики. Это уменьшит 922 строки и позволит добавить Clock/Weather без копирования.

### C.7. Настройки и миграция

`AmbientSettings`:

```
mode: AERIAL (default)
aerial.themes: Set<SaverTheme>
aerial.hidden: Set<String>
aerial.delay: SaverDelay
aerial.duringMusic: Boolean
aerial.refresh: SaverRefresh
clock.enabled: Boolean
clock.format: SYSTEM/12/24
weather.enabled: Boolean
weather.units: AUTO/C/F
photo.uris: List<String> (SAF persisted)
oled.protection: AUTO/ON/OFF
oled.maxSessionMinutes: Int?
motion: CINEMATIC/CALM/REDUCED
```

Миграция:

- старые ключи `saver_delay_minutes`, `saver_music`, `saver_refresh`, `saver_themes`, `saver_hidden_clips` читаются как есть;
- новый `ambient_mode` отсутствует → `AERIAL`;
- backup: `BackupCategory.saver` расширяется, schema version 2; старые бэкапы schema 1 восстанавливаются в Aerial;
- никакие фото/погода не попадают в backup без явного выбора.

### C.8. Media session и фокус

- MediaSession только у Home overlay и Dream.
- В preview — не публиковать.
- Focus: Settings использует существующие `SettingsRow`, `selectableGroup`, integer cursor. Не добавлять новые `FocusRequester`-иерархии.
- Saver: любая клавиша закрывает; Dream: системные wake-keys; preview: hold OK + swallow opening press.
- BACK во время fade/exit не должен запускать второй exit.

---

## D. Image & video pipeline

### D.1. Текущий pipeline

```
asset aerial_catalog.json (100 clips)
  → AerialCatalog.parse
  → SaverPlaylist (theme/hidden/shuffle)
  → SaverScreen
      ├─ still: AerialPosterStore (1080p JPEG, 16 files)
      │         или AerialPreviewStore (900×580 PNG)
      ├─ video: 2× ExoPlayer 1.4.1 (HTTPS, no cache)
      │         → TextureView → crossfade 1200 ms
      └─ capturePoster: TextureView.bitmap → JPEG 88 → cache (IO)
```

### D.2. Ограничения текущего pipeline

1. **1080p SDR only.** `AerialCatalogBuilder` читает `url-1080-H264`, не `url-4K-H265`/HDR. `AerialPosterStore` ограничен 1920×1080. `SaverScreen` не выбирает display mode.
2. **Нет дискового кэша видео.** Каждый запуск — новый HTTPS-стрим; на 100 клипов и 30-секундных excerpt это повторный трафик.
3. **Два декодера постоянно.** Второй нужен для префетча, но на 1 GB TV это заметная нагрузка.
4. **`capturePoster` делает readback 1080p** (~8 MB) на IO-потоке сразу после старта; на слабом GPU это может стоить кадров.
5. **Stills 900×580** апскейлятся; после первого проигрывания — 1080p poster.
6. **Нет HDR/color management.** `TvInk`/Compose-градиент не связан с цветовым пространством видео.
7. **Нет frame pacing.** `TextureView` + Compose vsync; 24p/30p не матчится с панелью.

### D.3. Целевой pipeline

```
AmbientSource
  → AmbientScene(content = Video/Still/Composed/Procedural)
  → CapabilityNegotiator (panel + decoder + license)
  → ScenePreloader (2 slots, bounded)
  → Renderer
       Aerial: ExoPlayer → TextureView → crossfade
       Photo:  ImageDecoder/BitmapFactory sampled → Ken Burns
       Clock:  Compose text, 1 update/min
       Weather: Compose, cached data
  → MediaSession (only overlay/dream)
```

**Правила:**

- 4K/HDR вариант выбирается только если:
  - контент лицензирован для 4K;
  - `MediaCodecList` подтверждает decoder;
  - панель сообщает 4K;
  - пользователь не включил «экономить трафик».
- Иначе — 1080p SDR. Никакого «4K» в UI, если играет 1080p.
- Still для фото: downsample до панели, максимум 4K, EXIF orientation, без полного оригинала в памяти.
- Кэш: сначала память, затем ограниченный диск; видео-кэш — только opt-in и с бюджетом, иначе не вводить.
- Все медиа — HTTPS + allowlist хоста; без HTTP-даунгрейда.
- Attribution/лицензия — в `AmbientScene`, показывать в About/legal, не на каждом кадре.
- Никаких скачанных «обоев» из интернета без лицензии.

### D.4. Content sources & licensing

| Источник | Сейчас | Лицензия | Решение |
|---|---|---|---|
| Apple Aerial | 100 клипов, streaming | © Apple, public CDN; `NOTICE` | Юридическое подтверждение до расширения; не бандлить видео |
| AerialViews catalogue | derived JSON | GPL-3.0, без кода | Сохранить attribution; не копировать код |
| Пользовательские фото | нет | пользователь | SAF, без broad permissions, per-profile |
| Weather | Home, off by default | Open-Meteo/MET/wttr | opt-in, attribution, без геолокации по умолчанию |
| Procedural | нет | собственная | безопасно, но OLED/GPU |

**Вывод:** перед «Cinematic Photography» и 4K нужен **licensing decision**, а не код. Нельзя обещать 4K-качество на Apple-потоке, который отдаёт 1080p.

---

## E. OLED & Energy Strategy

### E.1. Текущее состояние

- `view.keepScreenOn = true` на время saver.
- Видео — полноэкранное, часто с движением; still — 26 с с медленным zoom 1.06.
- Постоянный нижний градиент `TvInk.saver.copy(alpha = 0.62f)` и подпись в одном месте.
- Нет pixel shift, нет dim, нет blackout, нет max session, нет panel-type detection.
- `volume = 0`, аудиофокус не берётся — это хорошо.
- Два декодера + GPU-композиция — не «low power ambient».

### E.2. Риски

| Риск | Условие | Последствие |
|---|---|---|
| Burn-in статического градиента | OLED, долгая сессия | неравномерный износ нижней зоны |
| Burn-in подписи | OLED, повторяющееся место | следы текста |
| Яркий still без движения | OLED, 26 с+ | локальный износ |
| Постоянный decoder | LCD/OLED, часы | нагрев, энергия |
| Ночная яркость | тёмная комната | дискомфорт, засветка |
| Долгая сессия | любой TV | преждевременный износ панели |

### E.3. Целевая политика

`AmbientPowerPolicy`:

- **Video mode:** decode only while visible; release on pause/stop; no audio focus; optional 30 fps cap if source supports.
- **Still mode:** slow Ken Burns or none; pixel shift; dim after N min; blackout after M min (configurable).
- **Info mode (clock/weather):** update once per minute, not per frame; pixel shift every 60 s; no full-screen recomposition.
- **Procedural mode:** cap resolution/fps; respect Reduce Motion; dim.
- **Session limit:** default 30 min? — **решение владельца**. tvOS-like behavior может не ограничивать; для OLED нужен explicit.
- **OLED protection:** `AUTO/ON/OFF`; `AUTO` не пытается угадать панель, а включает консервативный режим при выборе пользователя «OLED» или при наличии panel info (если доступно). Не выдавать эвристику за факт.
- **Pixel shift:** overscan 1.02–1.03, сдвиг ±0.5% за 1–2 мин; для видео сдвигать весь слой, чтобы не было чёрных краёв.
- **Dim:** 100% → 80% → 60% → black; шаги 5–10 мин; без резких скачков.
- **Blackout:** только по таймеру/расписанию, не внезапно; любая клавиша будит.
- **Night schedule:** опционально, по локальному времени; не требует новых permissions.

### E.4. Энергия: что измерять

- Потребление в ваттах: Home idle, Aerial video, stills-only, clock-only, blackout.
- Нагрев/CPU/GPU: `dumpsys thermalservice`, Perfetto.
- Декодеры: `dumpsys media.codec`, `dumpsys media.player`.
- Пропущенные кадры: `dumpsys gfxinfo`, `SurfaceFlinger` latency.
- Сеть: байты за 1 ч, повторные загрузки.
- Время до первого кадра и до выхода.
- OLED: только визуальный контроль на реальной панели; программно burn-in не измерить.

**Сейчас эти числа не измерены.** Любые «примерно X Вт» в отчёте были бы выдумкой.

---

## F. UI/UX Specification

### F.1. Информационная архитектура

Сейчас (после Stage 3):

```
Settings
  └── Personalization
        ├── Language
        └── Ambient Experience
              ├── Ambient Mode → Aerials / Clock / Minimal
              ├── Start After
              ├── Show During Music
              ├── Aerial Clips / Themes / Download Frequency  (только Aerials)
              ├── System screen saver
              └── Reset
```

Исходная схема на момент аудита:

```
Settings
  └── Interface
        ├── Language
        └── Screen Saver
              ├── Choose Screen Saver → Aerials (один пункт)
              ├── Start After
              ├── Show During Music
              ├── Themes
              ├── Download Frequency
              ├── System screen saver
              └── Reset
```

Целевая схема (без добавления седьмого root-раздела):

```
Settings
  └── Personalization          // бывший Interface; строки те же
        ├── Language
        └── Ambient Experience // бывший Screen Saver
              ├── Mode          → Aerial / Minimal / Clock / Weather / Photos / ...
              ├── Aerial        → Themes / Clips / Download Frequency
              ├── Clock & Info  → Off / Clock / Clock + Weather
              ├── Motion        → Cinematic / Calm / Reduce Motion
              ├── OLED Protection → Auto / On / Off
              ├── Start After
              ├── Show During Music
              ├── Schedule      → Always / Night only / Custom (future)
              ├── System screen saver
              └── Reset
```

Это сохраняет шесть root-строк и существующую навигацию; меняется только название раздела и содержимое страницы. `PAGE_SAVER` можно оставить как internal id, чтобы не ломать backup/history.

### F.2. Экран выбора режима

- Список-card: реальный preview 16:9, название, 1 строка описания, badges: `Network`, `4K`, `Low power`, `Photos`.
- Фокус — существующий list; `selectableGroup`; TalkBack: radio + state.
- Aerial — выбран по умолчанию; остальные не обещают того, чего нет.
- У режима «Photos» — объяснение, что доступ только к выбранной папке; без «сканировать всё».
- У «Weather» — «Off by default; uses saved city; no location by default».

### F.3. Preview

- В Settings — статичный still 800×450 (как сейчас), без декодера.
- Hold OK — полноэкранный реальный preview; выход любой клавишей; подсказка не должна висеть постоянно.
- В preview показывать **режим**, а не только название клипа: `Aerial · Cities`.
- Если сцена не готова — не показывать чёрный экран; ждать still или вернуться.

### F.4. Первый запуск

Не добавлять ещё один вопрос в Setup. Оставить `Start After` + `Themes`, default Aerial. «More modes» — в Settings, не на первом экране. Это соблюдает правило «не перегружать Home».

### F.5. Focus / D-pad / BACK

- Не строить второй Focus Engine.
- Settings: integer cursor + `requestWhenAttached`; mode list — `selectableGroup`.
- Preview: hold OK; opening press release не должен закрывать preview.
- Saver: любая клавиша; BACK — тот же путь.
- Dream: системные wake-keys; `handleBack=false`.
- Во время exit fade BACK не должен запускать повторный exit.

### F.6. Accessibility

- `contentDescription` для сцены: режим + название + «Press any button to exit» (уже есть).
- Caption toggle (Off / On) — для видео/фото; часы/погода не должны читаться как часть видео.
- Reduce Motion: не только entrance/zoom, но и crossfade (сократить до 200–300 мс) и caption fade.
- TalkBack: проверить, что preview не «залипает» и что выход доступен.
- Контраст подписи: проверить на светлых still; при необходимости — scrim/outline.
- Не использовать мигание >3 Гц.

### F.7. Строки

- EN/RU, ключи `ambient_*`; старые `saver_*` сохранить как fallback.
- Не обещать «4K» в названии режима, если контент 1080p.
- «Download Frequency» переименовать в «Catalogue Updates» и честно писать «Apple’s public feed may be older than the bundled catalogue».
- «System screen saver» оставить, но объяснить, что это выбор системного chooser.

---

## G. Device Compatibility

### G.1. Кодовая совместимость

| Класс | Код | Проверено |
|---|---|---|
| Android TV / Google TV, API 28–35 | `minSdk 28`, `targetSdk 35`; media3 1.4.1 | только AVD API 34 x86 |
| 720p | `TvScale`, full-screen saver | не проверено |
| 1080p | AVD 1920×1080, density 320 | проверено на AVD |
| 4K | нет 4K-контента; 1080p upscale | не проверено |
| LCD / QLED | нет panel-specific logic | не проверено |
| OLED | нет burn-in protection | не проверено, риск |
| 1 GB RAM | `AdaptiveProfile.LOW`; 2 decoders + caches | не проверено |
| ARM32 / ARM64 | media3/ExoPlayer | не проверено |
| HDR / 10-bit | нет | не поддерживается |
| 24p / 30p | нет display mode switch | не проверено |
| DreamService | manifest + `BIND_DREAM_SERVICE` | не проверено на ТВ |
| Real remote | any key exit | Compose-тест, не физический пульт |
| TalkBack | contentDescription | не проверено |
| Port 443 blocked | HTTPS-only → stills/backoff | не проверено |
| Offline | stills-only/backoff | логика есть, не измерено |
| Vendor TV без DocumentsUI | Photos/SAF | не проверено |

### G.2. Тестовая матрица (целевая)

| Устройство | API | RAM | Разрешение | Панель | Что проверить |
|---|---:|---:|---|---|---|
| AVD `hume-tv` | 34 | 2 GB+ | 1080p | virtual | baseline, уже есть |
| Google TV HD | 30–34 | 1.5 GB | 720p | LCD | decode, память, D-pad |
| Android TV box | 28–31 | 1 GB | 1080p | LCD | OOM, stills-only, startup |
| Google TV 4K | 31–34 | 2 GB | 4K | LCD/QLED | upscale, frame pacing |
| OLED TV | 30–34 | 2 GB+ | 4K | OLED | burn-in, dim, pixel shift |
| ARM32 TV | 28–30 | 1 GB | 720p/1080p | LCD | decoder, ABI |
| Vendor TV | 28–33 | 1–2 GB | 1080p | LCD | DreamService chooser, remote |
| No-network TV | any | any | any | any | offline start, backoff |

**Ни одна строка, кроме AVD, не является проверенной.**

---

## H. Performance Metrics

### H.1. Что известно

- Первый кадр: still прогревается на Home; decoder pair строится за 2 с после первого кадра Home.
- Polling 5 Гц на плеер; media session 1 Гц.
- Два декодера, два `TextureView`, still 1080p, кэш poster 16–32 MiB в памяти.
- Stills-only: 26 с на кадр, zoom 26 с.
- Exit: 330 мс; crossfade 1200 мс.
- Сетевой старт: 20 с таймаут; stall 12 с; prefetch 20 с.
- Память: LRU poster 16/24/32 MiB; preview 3/6 MiB; thumbs 8/12/16 MiB; плюс bitmap still ~8 MiB.
- Диск: 16 poster JPEG; 8 preview PNG; 120 thumbs, 32 MiB cap.

Это **не** измерения FPS/энергии/памяти на устройстве.

### H.2. Целевые SLO (не выдавать за факты)

| Метрика | Цель | Как мерить |
|---|---|---|
| Время до первого реального кадра, warm | ≤ 1.5 с | `SystemClock` + screenshot/видео |
| Время до первого кадра, cold | ≤ 3 с | то же |
| Чёрные кадры на входе/кроссфейде | 0 | 240 fps capture |
| Пропущенные кадры за 10 мин | < 1% | `dumpsys gfxinfo`, Perfetto |
| Память saver | ≤ профильного бюджета | `dumpsys meminfo` |
| OOM/ANR | 0 | 24 h soak |
| Сеть за 1 ч | измерить, не угадывать | `TrafficStats`/роутер |
| Энергия | измерить ваттметром | внешний meter |
| Выход | ≤ 400 мс | 240 fps capture |
| Still-only деградация | без чёрного экрана | визуально/скриншоты |

### H.3. Что нужно добавить в инструментарий

- `Perfetto` trace: `gfx`, `view`, `media`, `power`.
- `dumpsys gfxinfo <pkg> framestats`.
- `dumpsys meminfo <pkg>` до/во время/после.
- `dumpsys media.codec` / `media.player`.
- 240 fps camera или `screenrecord` для проверки чёрных кадров.
- Внешний wattmeter для OLED/LCD.
- 24 h soak с автоматическим скриншотом каждые 10 мин.
- Тест с заблокированным 443 и с отключённым Wi-Fi.
- Тест process death/force-stop во время saver.

---

## I. Implementation Roadmap

Без дат: оценка в stage/effort, чтобы не выдавать план за сроки.

### Stage 0 — Decisions & licensing (blocking)

- Юридическое подтверждение Apple CDN.
- Решение по 4K/HDR: нужен ли лицензированный источник.
- Решение по Photo Gallery: SAF, per-profile, без broad permissions.
- Решение по OLED: max session, dim, blackout defaults.
- Решение по IA: переименовать `Interface` → `Personalization` или оставить.
- **Approval gate. Код не меняется до этого.**

### Stage 1 — Baseline measurement

- Perfetto/gfxinfo/meminfo/energy на AVD и хотя бы одном физическом TV.
- Зафиксировать текущие SLO и проблемы.
- Acceptance: отчёт с реальными числами, не оценками.

### Stage 2 — Extract Ambient engine (no visible change)

- `AmbientMode`, `AmbientScene`, `AmbientSource`, `AmbientPlaylist`, `AmbientScheduler`, `AmbientEngine`.
- `SaverScreen` → `AerialSceneRenderer` + `AmbientSurface`.
- Home/Dream adapters.
- Acceptance: Aerial behavior идентичен; все текущие тесты зелёные; новых permissions нет.

### Stage 3 — Settings IA + mode selector

- `Personalization → Ambient Experience`.
- `Mode` page: Aerial / Minimal / Clock (v1).
- Clock overlay, 12/24, safe area, pixel shift.
- Acceptance: D-pad/TalkBack; Aerial unchanged; preview; no new permissions.

### Stage 4 — Weather + OLED protection

- Stage 4a: pixel shift, dim, max session — реализовано, по умолчанию off/on консервативно.
- Stage 4b: погода на часах только если уже включена в `Settings → Weather`; cached данные, без сети из заставки; off by default.
- Acceptance: no static bright UI; session limit; energy measured. Энергия и OLED на реальной панели **не измерялись**.

### Stage 5 — Photo Gallery (SAF)

- `OpenDocument`/`OpenDocumentTree`; persisted URI; per-profile.
- No `READ_MEDIA_IMAGES`; no broad media scan.
- Acceptance: vendor TV without DocumentsUI has a clean fallback; photos never leak into Kids profile.

### Stage 6 — 4K/HDR pipeline + content licensing

- Capability negotiation; 4K only with licensed content and supported decoder/panel.
- Acceptance: on 1080p panel never selects 4K; on 4K panel actual 4K playback verified; no false “4K” label.

### Stage 7 — Device matrix + soak

- API 28–35, 720p/1080p/4K, LCD/OLED, 1 GB, ARM32/64.
- 24 h soak, offline, 443-blocked, process death.
- Acceptance: honest compatibility table; no “certified” claim without evidence.

### Priority summary

| Priority | Item | Why |
|---|---|---|
| P0 | Aerial preserve + engine extraction | не сломать работающее |
| P0 | Settings IA + mode selector | убрать тупиковый «Choose» |
| P1 | Clock/Minimal + OLED protection | максимальный эффект при низком риске |
| P1 | Weather (opt-in) | использует уже существующие данные |
| P2 | Photo Gallery | приватность/SAF/профили |
| P2 | 4K/Cinematic | лицензия и железо |
| P2 | Abstract | процедурный, но OLED/GPU |

---

## J. Final Verdict

### J.1. Оценка

| Область | Оценка | Почему |
|---|---:|---|
| Aerial как screensaver | **8/10** | реальный контент, кроссфейд, fallback, тесты, аккуратная интеграция |
| Ambient как продукт | **3/10** | один режим, нет часов/погоды/фото/minimal, нет OLED |
| Архитектура | **5/10** | хорошие чистые слои есть, но `SaverScreen` — монолит 922 строки |
| Качество изображения | **6/10** | 100 реальных 1080p клипов; 4K/HDR нет |
| UX / discoverability | **5/10** | спрятано в Interface, «Choose» с одним пунктом |
| Надёжность | **8/10** | таймауты, backoff, stills-only, no black screen |
| OLED / энергия | **3/10** | нет защиты, нет измерений |
| Совместимость | **3/10** | проверен только AVD API 34 |
| Тесты | **7/10** | хорошие unit/Compose; нет Dream/E2E/4K/OLED |

**Итог:** это **хорошая Aerial-заставка**, а не Ambient Experience. Не переписывать. Выделить движок, сохранить Aerial, затем добавлять режимы по одному, с реальными источниками и без новых permissions.

### J.2. Что делать в первую очередь

1. **Не менять Aerial поведение.**
2. **Убрать тупиковый «Choose Screen Saver»**: либо сделать реальный выбор режима, либо переименовать строку в «Aerial Themes» и не обещать выбор.
3. **Честно назвать Download Frequency**: сейчас он не обновляет каталог; либо починить источник, либо переименовать/объяснить.
4. **Добавить Clock/Minimal + OLED protection** — самый большой UX/OLED-эффект при малом риске.
5. **Извлечь `AmbientEngine`** до добавления Photos/4K, иначе `SaverScreen` станет 2000+ строк.
6. **Решить лицензию/4K** до любых заявлений о 4K.

### J.3. Что нельзя обещать

- «4K» — пока нет 4K-контента и подтверждённого 4K pipeline.
- «Cinematic Photography» — пока нет лицензированного контента.
- «Photo Gallery» — без решения по приватности и per-profile.
- «OLED-safe» — без pixel shift/dim/session и проверки на панели.
- «Энергоэффективно» — без измерений.
- «Совместимо со всеми Android TV» — проверен только AVD.

### J.4. Approval checklist

- [ ] Юридически подтверждено использование Apple Aerial CDN.
- [ ] Решено, нужен ли 4K/HDR и есть ли лицензированный источник.
- [ ] Решено, как Photo Gallery получает доступ (SAF, per-profile).
- [ ] Решены defaults OLED: dim/blackout/max session.
- [ ] Согласовано переименование `Interface → Personalization`.
- [ ] Согласован список v1-режимов (Aerial + Clock + Minimal).
- [ ] Согласовано, что Stage 2 — рефакторинг без изменения поведения.
- [ ] Согласовано, что новые permissions не добавляются.

---

## Приложение 1. Карта доказательств

| Утверждение | Где смотреть |
|---|---|
| Два входа, один `SaverScreen` | `AerialDreamService.kt:45–127`, `LauncherRoot.kt:158–356`, `SettingsScreen.kt:3213–3228` |
| Idle-таймер спит до дедлайна | `LauncherRoot.kt:317–356` |
| Музыка/гейты | `LauncherRoot.kt:326–356`, `SaverSchedule.kt` |
| Тайминги | `SaverScreen.kt:120–148` |
| Два декодера | `SaverScreen.kt:862–920` |
| Still/poster | `AerialPosterStore.kt:22–31`, `SaverPreviewImage.kt:25–31` |
| 1080 only | `AerialCatalogBuilder.kt:66–70`, `AerialPosterStore.kt:27–28` |
| Refresh no-op | `AerialRefresh.kt:110–115` |
| HTTPS-only | `AerialCatalog.kt`, `AerialRefresh.kt`, `network_security_config.xml` |
| Manifest Dream | `AndroidManifest.xml:181–193` |
| Лицензия | `NOTICE:12–16` |
| Настройки | `SettingsScreen.kt:1218–1269`, `:1316–1332` |
| Тесты | `app/src/test/.../Saver*Test.kt`, `app/src/androidTest/.../Saver*Test.kt` |

## Приложение 2. Что осталось непроверенным

- физический Android TV / Google TV;
- 4K, HDR, 10-bit, 24p;
- ARM32/ARM64;
- 1 GB RAM / OOM;
- OLED burn-in;
- энергопотребление;
- TalkBack;
- реальный пульт;
- `DreamService` в системном chooser;
- SAF picker на TV;
- port 443 blocked;
- process death во время saver;
- 24 h soak;
- юридическая лицензия Apple CDN;
- PBKDF2/backup не относится к этому аудиту.

**Этот документ не является сертификацией. Он описывает код и известные ограничения.**

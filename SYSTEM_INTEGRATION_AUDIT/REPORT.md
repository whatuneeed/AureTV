# Android TV System Integration — аудит Kotlin-лаунчера

Дата: 14.11.2026. Объект: `OVisionTV_Kotlin`, **0.4.49 / vc69**, `com.whatuneed.ovisiontv.kotlin`. Аудит read-only: production-код, manifest, подпись, applicationId и визуальный дизайн не изменялись. Соседняя Java-версия не входит в объект.

Стенд: `hume-tv`, AOSP TV on x86, Android 14 (API 34), 1920×1080, эмулятор; реального ТВ нет. Команды и вывод — `SYSTEM_INTEGRATION_AUDIT/evidence/system-checks.txt`, `roundtrip.txt`. Предыдущие доказательства — `UI_UX_UNIFICATION/evidence-0.4.49/`.

---

## Статус реализации (0.5.0 / vc73)

Этап системной интеграции, шаги 1–3, Low hardening, SI-01, часть SI-06 и SI-02/SI-03 выполнены в 0.4.50–0.5.0. Код менялся только по перечисленным пунктам; applicationId, подпись и визуальный дизайн не менялись.

| Пункт | Статус 0.4.50 | Что сделано |
|---|---|---|
| SI-04 hidden PowerManager | **реализовано** | `SleepControls.powerOff` = документированный `sleepNow` (DeviceAdmin/accessibility); reflection `shutdown/goToSleep` удалён; плитка «Power Off» переименована в «Sleep/Режим ожидания» |
| SI-05 startup на SCREEN_ON | **реализовано** | `MainActivity` запускает wake-старт только когда Home resumed; accessibility service — только если последняя поверхность Home/stock launcher (`lastPackage`) |
| SI-08 accessibility content | **реализовано** | `canRetrieveWindowContent=false`; сервис больше не читает `rootInActiveWindow`, только package/class событий |
| SI-09 Control Center KeyUp | **реализовано** | KeyUp поглощается только для обработанных D-pad/OK/BACK; volume/media отдаются системе |
| SI-10 AccessibilityStatus | **реализовано** | точное сравнение component + проверка глобального `ACCESSIBILITY_ENABLED` |
| SI-11 disabled components | **реализовано** | `activityInfo.enabled`/`applicationInfo.enabled` фильтруются при чтении |
| SI-12 HOME settings fallback | **реализовано** | при отсутствии `ACTION_HOME_SETTINGS` открывается общий Settings и показывается подсказка «Настройки → Домой» |
| SI-01 leanback-маркер | **реализовано** | `TvApp.leanback`, фильтр `filterPhoneOnly`, переключатель «Приложения без ТВ-версии» в Settings (по умолчанию вкл.) |
| SI-02 MediaSession | **реализовано** | своя сессия заставки (metadata/play/pause/stop/seek), активна только пока видна; чужие сессии без notification listener недоступны |
| SI-03 DreamService | **реализовано, runtime NOT TESTED** | `AerialDreamService` + запись в системном выборе; на стенде dream нельзя запустить без root |
| SI-06 4K-арты | **частично** | `gridBannerBox`: 400×240 на 1080p, 500×300 при ширине панели ≥2560; иконка/постер/баннер лаунчера не менялись, 4K-растеризация не проверялась |
| SI-07/SI-13/SI-14 | **без изменений** | accepted/unsupported/PASS |

Проверка 0.5.0: unit **346/346** debug+release, connected **208/208**, lint **0 Error / 14 Warning**, release APK `337ca9d8…3ed8`, v2. Реальный ТВ/API 28/29/35/LOW/4K/TalkBack/HDMI-CEC — NOT TESTED.

---

## A. Текущее состояние интеграции

**Оценка:** интеграция с Android TV у лаунчера уже реальная, а не «обычное приложение на большом экране». HOME/LEANBACK объявлены, Package Visibility ограничена `<queries>`, `QUERY_ALL_PACKAGES` не используется, системные экраны открываются через документированные Intent, пульт обрабатывается D-pad-движком с защитой от повторов, фокус восстанавливается, а обновление/установка/удаление приложений перестраивают сетку без потери раскладки.

**Поддерживаемые API:** `HOME`/`LEANBACK_LAUNCHER`, `PackageManager.queryIntentActivities`, `<queries>`, `Intent.ACTION_MAIN`, `Settings.*`, `AccessibilityService`, `DeviceAdminReceiver` (force-lock), `READ_TV_LISTINGS` + `TvContract.WatchNextPrograms/PreviewPrograms`, `ACCESS_WIFI_STATE`/location для SSID, `ExoPlayer` для заставки, `WindowInsets.safeDrawing`, runtime-разрешения.

**Основные ограничения:**
- нет реального ТВ, API 28/29/35 и OEM-матрицы;
- нет интеграции с чужими `MediaSession` и нет собственной `MediaSession`;
- заставка — overlay внутри Home, а не системный `DreamService`;
- в `SleepControls` есть скрытые `PowerManager.shutdown/goToSleep` через reflection;
- 4K-панель недоступна, а часть артов ограничена 256/400×240/1280 px;
- TalkBack voice-pass, HDMI-CEC и OEM-специфичные пульты не проверялись.

**Обнаруженные ошибки/риски:** подтверждённых Critical/High нет. Есть 6 Medium (TV-compatibility приложений, MediaSession, DreamService, hidden API, startup на SCREEN_ON, 4K-арты) и несколько Low (экспорт Settings, широкие права accessibility, KeyUp в Control Center, точность AccessibilityStatus, disabled-компоненты, HOME-settings fallback).

---

## B. Android System Integration

### B1. HOME и LEANBACK

- `MainActivity` объявляет `MAIN` + `HOME` + `DEFAULT` и отдельно `MAIN` + `LEANBACK_LAUNCHER` (`AndroidManifest.xml:81–100`); `launchMode=singleTask`, `exported=true`, `excludeFromRecents=true`, `stateNotNeeded=true`.
- На стенде `cmd package resolve-activity -a MAIN -c HOME` возвращает `com.whatuneed.ovisiontv.kotlin.MainActivity`, `isDefault=true`.
- Проверено: HOME из Google Play возвращает Kotlin Home; BACK на Home ничего не делает; SettingsActivity открывается и BACK возвращает Home; `install -r` сохраняет данные; после `adb reboot` Kotlin Home снова стал верхним, default Home сохранился.
- Переключение HOME между установленными Java- и Kotlin-лаунчерами через `cmd package set-home-activity` работает.
- `SettingsActivity` тоже имеет `LEANBACK_LAUNCHER` и `exported=true`; это осознанно: она видна в системном ряду приложений. Вход в Kids-профиль защищён PIN-гейтом, intent-данные не читаются.
- `SetupActivity` `exported=false` — мастер нельзя открыть снаружи.

**Вывод:** сценарии 1–5 из ТЗ подтверждены на эмуляторе. Сценарии 6–7 (обновление/удаление/повторная установка) подтверждены частично: `install -r` и `package`-broadcast-перестройка; полный цикл uninstall → install с проверкой пустых prefs на реальном ТВ — Expected.

### B2. PackageManager и управление приложениями

- `<queries>` объявляет `LAUNCHER`, `LEANBACK_LAUNCHER`, `HOME`, пакет AirPlay и `provider android.media.tv` (`AndroidManifest.xml:41–70`). `QUERY_ALL_PACKAGES` **отсутствует** — это правильно.
- `AppRepository.collectActivities` запрашивает сначала `LEANBACK_LAUNCHER`, затем `LAUNCHER`, дедуплицирует по package (`AppRepository.kt:175–209`). На стенде: 43 leanback-активности, 31 launcher-активность; leanback-only `com.android.tv.settings/.MainSettings` находится.
- Запуск идёт явным `ComponentName` из `ActivityInfo` (`HomeScreen.kt:1930–1941`), а не повторным `getLaunchIntentForPackage`; ошибка запуска показывает toast. Для startup добавлен leanback-fallback (`StartupPreferences.kt:145–162`).
- Обновление списка: `PACKAGE_ADDED/REMOVED/CHANGED/REPLACED` receiver (`LauncherRoot.kt:724–746`); `PACKAGE_REMOVED` с `EXTRA_REPLACING` игнорируется, чтобы обновление не дёргало сетку дважды. Receivers зарегистрированы `RECEIVER_NOT_EXPORTED`.
- Арты: сетка 400×240 баннер / 256×256 иконка, полка — исходный баннер вплоть до 1920×1080, постеры до 1280 px, `RGB_565`, лимит 8 MiB и таймауты 10 с. Кэши — key-based LRU с trim/resize.

**Ограничение:** приложения без Leanback-интерфейса показываются через fallback `LAUNCHER` и визуально не помечены как «phone-only»; на ТВ они могут открыть сенсорный/неоптимизированный экран. Это Medium, не ошибка запуска.

### B3. Permissions

Запрошены только: `INTERNET`, `ACCESS_NETWORK_STATE`, `READ_TV_LISTINGS`, `ACCESS_WIFI_STATE`, `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION`. `READ_TV_LISTINGS` и location — dangerous, запрашиваются по действию пользователя; SSID без location показывает «Connected». `QUERY_ALL_PACKAGES` не добавлен. `allowBackup=false` и оба backup-правила пустые, что согласовано.

### B4. Intents и системные экраны

Используются документированные действия: `ACTION_HOME_SETTINGS`, `ACTION_SETTINGS`, `ACTION_APPLICATION_DETAILS_SETTINGS`, `ACTION_ACCESSIBILITY_SETTINGS`, `ACTION_DISPLAY_SETTINGS`/`ACTION_SOUND_SETTINGS`, `ACTION_WIFI_SETTINGS`, `ACTION_BLUETOOTH_SETTINGS`. Где экран может отсутствовать, есть `resolveActivity`/`runCatching` и fallback на общий Settings; при отказе — toast. Дочерние экраны (PIN, клавиатура, меню, профиль) — собственные Compose-поверхности, стандартных `AlertDialog` нет.

**Замечание:** `ACTION_HOME_SETTINGS` на части прошивок отсутствует; fallback ведёт в общий Settings без подсказки, куда идти. Low/UX.

### B5. MediaSession

В коде нет `MediaSession`, `MediaController`, `PlaybackState` и `MediaMetadata`; Control Center показывает только громкость и открывает системный звук. Заставка использует `ExoPlayer`, но не публикует сессию.

Обычное приложение **не может** надёжно читать чужие сессии: `MediaSessionManager.getActiveSessions` требует `MEDIA_CONTENT_CONTROL` (signature/privileged) или включённого notification listener; `MediaController` нужен token. Легальные варианты: (1) собственная `MediaSession` для заставки, (2) опциональный `NotificationListenerService` с явным согласием пользователя для чтения media-уведомлений, (3) `MediaButtonReceiver` для своих действий. Универсальное «управление любым плеером» без согласия — **Unsupported**. Это Medium-функция, не скрытый дефект.

### B6. Системные возможности

| Возможность | Статус | API/ограничение |
|---|---|---|
| Watch Next / Preview Programs | **Emulator tested** | `TvContract` + `READ_TV_LISTINGS`; запись каналов не выполняется |
| Content Recommendations (публикация) | **Unsupported/не реализовано** | нужен `TvProvider` write/EPG-доступ; лаунчер только читает |
| App Shortcuts | **Не реализовано** | `ShortcutManager` доступен, но не нужен для базового сценария |
| Deep Links | **Частично** | provider-ссылки проверяются на тот же package (`WatchNext.kt:100–110`); своего `ACTION_VIEW` нет |
| Screen Saver API | **Не реализовано как DreamService** | заставка — overlay Home; системный выбор screensaver не задействован |
| HDMI-CEC / внешние источники | **Unsupported** | `HdmiControlManager` — system API/privileged; обычному приложению недоступен |
| Системные настройки изображения/звука | **Emulator tested** | `ACTION_DISPLAY_SETTINGS` с fallback на Sound/Settings |
| Информация об устройстве/сети | **Emulator tested** | `Build`, `DisplayManager`, `ActivityManager`, `ConnectivityManager` |
| Уведомления | **Не реализовано** | TV-уведомления не являются частью сценария |
| Голосовой TalkBack | **Не tested** | semantic-тесты есть, voice-pass нет |

---

## C. Remote Control

- Все Activity наследуют `TvRemoteActivity`, который пропускает KeyEvent через `TvKeyBurstFilter` (`TvKeyBurst.kt`). Фильтр склеивает «пары» Down/Up от host-клавиатур/эмулятора в одно нажатие, но пропускает настоящий auto-repeat (`repeatCount > 0`).
- Обрабатываются D-pad, OK/Enter/NumpadEnter, BACK, MENU, SEARCH/ASSIST; long OK открывает контекстное меню/перетаскивание, удержание стрелок — собственный repeat. Volume/media keys не перехватываются в Home (`else -> false`), система управляет ими сама.
- BACK: Home потребляет; папки/меню/панели имеют свои `BackHandler`; `SettingsActivity` возвращает Home; из системных настроек возврат обрабатывается `resumeTick`.
- Фокус: `FocusRequester` на каждую плитку/строку, сохранённый `HomeFocus` (app или folder ID), восстановление после папки/меню/настроек; `TvCanvas` + `safeDrawing` не дают фокусу уехать за overscan.
- На эмуляторе проверены Home/Back/Settings/Play-return; 208 инструментальных тестов включают D-pad, фокус, меню, папки, TalkBack-semantics.
- Не проверены: реальные ИК-пульты Sony/TCL/Xiaomi/Philips/Hisense, repeat-rate разных OEM, кнопки Netflix/Google Assistant, voice search.

**Замечание Low:** `ControlCenter` в `onKeyEvent` возвращает `true` на **любой** KeyUp (`ControlCenter.kt:736–740`), включая volume/media, хотя KeyDown для них не обрабатывает. Обычно громкость меняется на KeyDown, но KeyUp стоит отдавать системе, если клавиша не обработана.

---

## D. Device Compatibility

| Область | Статус |
|---|---|
| API 34 AVD, AOSP TV x86 | **Emulator tested** |
| API 28 / 29 / 35 | **Not tested** (нет system images) |
| Google TV / Android TV | **Expected**; используются публичные API, без OEM-зависимостей |
| Sony/TCL/Xiaomi/Philips/Hisense | **Not tested**; OEM-различия в Settings/пультах/энергосбережении не проверены |
| Реальный ТВ/ИК-автоповтор | **Not tested** |
| LOW 1 ГБ / 4K-панель | **Not tested** |
| TalkBack voice / RTL / третий язык | **Not tested** (semantic-тесты есть; RTL-ресурсов нет) |
| HDMI-CEC | **Unsupported** для обычного приложения |

Архитектурно OEM-зависимости изолированы: системные экраны идут через Intent + fallback, Home takeover — через Accessibility, sleep — через DeviceAdmin/Accessibility. Но именно эти три места наиболее чувствительны к прошивкам.

---

## E. Display & Asset Quality

- Дизайн-канва 1920×1080, `TvCanvas` масштабирует по **меньшей** оси и центрирует, учитывает `safeDrawing` (`TvCanvas.kt`, `TvMetrics.kt:104–108`). 16:9, 4:3, 21:9 не растягиваются; 720p проверен, 4K-панель недоступна.
- Текст — `TvType`/`TvLeading`/`TvSpace` в TV-масштабе (body 26), не телефонные 16; Reduce Motion учитывается.
- Иконки лаунчера — vector 108dp; `tv_banner.png` 640×360; сторонние иконки 256×256, сеточные баннеры 400×240, постеры полки до 1280 px, `RGB_565`, `FilterQuality.Low` для фонового баннера.
- Память: adaptive budgets HIGH/BALANCED/LOW; LRU + trim/resize; decoded art не хранится в `TvApp` (ключи); загрузка асинхронная, лимиты 8 MiB/10 с.
- **4K-риск (Medium):** на 4K-панели с density 1.0 canvas factor 2, а иконка 256 px и сеточный баннер 400×240 могут апскейлиться; постер 1280 px на full-screen 3840 — 3×. Это не «native 4K», а ограничение бюджета. Нужно поднимать целевые размеры для HIGH и проверять память.
- **4K-факт:** `wm size 3840x2160` на образе не применяется; проверена формула/прокси-геометрия, не растеризация.

---

## F. Таблица проблем

| ID | Проблема | Компонент | Приоритет | Рекомендуемое решение |
|---|---|---|---|---|
| SI-01 | Приложения без Leanback показываются без пометки TV-совместимости | `AppRepository.collectActivities`, `TvApp` | **Medium** | Сохранять `leanback: Boolean` из категории, показывать метку/фильтр в Settings; fallback `LAUNCHER` оставить |
| SI-02 | Нет MediaSession/MediaController; чужие плееры не читаются и не управляются | весь проект | **Medium** | Своя `MediaSession` для заставки; для чужих — опциональный notification listener с согласием; не обещать универсальное управление |
| SI-03 | Заставка не является системным `DreamService`; работает только поверх Home | `LauncherRoot`, `SaverScreen` | **Medium** | Опциональный `DreamService` + `ACTION_DREAM_SETTINGS`; overlay сохранить |
| SI-04 | `SleepControls` использует скрытые `PowerManager.shutdown/goToSleep` через reflection | `SleepControls.kt:57–74` | **Medium** | Убрать hidden API; оставить `DevicePolicyManager.lockNow`/accessibility lock; «Power Off» честно называть standby |
| SI-05 | Startup по `SCREEN_ON` может открыть приложение поверх другого foreground-приложения | `OVisionAccessibilityService.kt:35`, `StartupPreferences.kt:109` | **Medium** | Проверять текущую поверхность (Home/stock launcher) перед `fire`; либо убрать service-wake путь |
| SI-06 | 4K-арты ограничены 256/400×240/1280 px, баннер лаунчера 640×360 | `AppRepository`, `AdaptivePolicy`, `TopShelf` | **Medium** | Для HIGH поднять иконки/баннеры/постеры, проверить PSS; не включать для LOW |
| SI-07 | `SettingsActivity` экспортирована и имеет LEANBACK-фильтр | `AndroidManifest.xml:108–122` | **Low (accepted)** | Оставить для системного ряда; гейт уже есть; рассмотреть custom permission нельзя без потери видимости |
| SI-08 | Accessibility service запрашивает `canRetrieveWindowContent=true`, хотя читает только package/class | `ovision_accessibility.xml` | **Low** | По возможности убрать root-чтение и выставить `false`; иначе документировать |
| SI-09 | Control Center поглощает все KeyUp, включая необработанные volume/media | `ControlCenter.kt:736–740` | **Low** | Возвращать `true` только для обработанных клавиш; остальные отдавать системе |
| SI-10 | `AccessibilityStatus` считает включённым любой сервис пакета и не проверяет глобальный `ACCESSIBILITY_ENABLED` | `AccessibilityStatus.kt:35–43` | **Low** | Точное сравнение component + глобальный флаг |
| SI-11 | Нет явной проверки `activityInfo.enabled`/`applicationInfo.enabled` после сканирования | `AppRepository.readApp` | **Low** | Фильтровать disabled и обновлять по `PACKAGE_CHANGED` |
| SI-12 | `ACTION_HOME_SETTINGS` может отсутствовать; fallback в общий Settings без подсказки | `SetupScreen.kt:605`, `SettingsScreen.kt:3781` | **Low** | Проверять resolver и показывать текст «Настройки → Домой» |
| SI-13 | Нет App Shortcuts, своего Deep Link, публикации Channels/Programs, HDMI-CEC | — | **Info/Unsupported** | Не добавлять без продуктивной необходимости; HDMI-CEC недоступен обычному приложению |
| SI-14 | `QUERY_ALL_PACKAGES` отсутствует, visibility ограничена `<queries>` | manifest | **Info (PASS)** | Не добавлять; текущего набора хватает |
| SI-15 | `ControlCenter` не управляет громкостью напрямую, только показывает и открывает Sound | `ControlCenter.kt:997–1003` | **Info** | Оставить, если ТВ-пульт сам управляет громкостью; иначе отдельное решение |

---

## G. План улучшения

1. **SI-04 — убрать hidden API (первый шаг).** `SleepControls`: удалить reflection `shutdown/goToSleep`, оставить `DevicePolicyManager.lockNow` и accessibility `GLOBAL_ACTION_LOCK_SCREEN`; переименовать «Выключить» в standby, если так и есть. Файлы: `SleepControls.kt`, `ControlCenter.kt`, строки. Риск: на части ТВ исчезнет «полное выключение», но оно и не было документированным. Проверка: unit + release на эмуляторе/ТВ, device-admin и accessibility ветки.
2. **SI-05 — foreground-политика startup.** В `OVisionAccessibilityService` перед `StartupPreferences.onScreenOn` проверять, что на экране Home/stock launcher, либо перенести wake-триггер в `MainActivity.onResume`. Файлы: `OVisionAccessibilityService.kt`, `StartupPreferences.kt`. Риск: пропуск законного wake; тесты на boot/wake раздельно.
3. **SI-01/SI-11 — TV-совместимость и enabled-фильтр.** `TvApp` получает `leanback`; `collectActivities` сохраняет категорию; Settings показывает метку/фильтр; `readApp` проверяет enabled. Файлы: `AppRepository.kt`, `TvApp.kt`, `HomeScreen.kt`/`SettingsScreen.kt`. Риск: скрыть нужное приложение — оставить fallback и настройку.
4. **SI-06 — 4K-арты.** HIGH-бюджет: icon/banner/постеры до 512/640×360/1920, `FilterQuality.Medium` для hero; замерить PSS на LOW/HIGH. Файлы: `AppRepository.kt`, `AdaptivePolicy.kt`, `TopShelf.kt`, `AppCard.kt`. Риск: память и первый кадр; порог фиксировать до изменения.
5. **SI-03 — DreamService (продуктовое решение).** Отдельный `DreamService`, манифест, выбор в системных настройках, общий `SaverPlaylist`/player. Риск: второй decoder/конкуренция с overlay; не включать по умолчанию.
6. **SI-02 — MediaSession (продуктовое решение).** Своя сессия для заставки; опциональный notification listener для чужих плееров. Риск: лишние разрешения/согласие; без него — только собственные действия.
7. **SI-09/SI-10/SI-08/SI-12 — Low hardening.** KeyUp только для обработанных клавиш; точный accessibility component + global flag; `canRetrieveWindowContent=false`, если root не нужен; подсказка для HOME settings.
8. **SI-13 — не добавлять.** Shortcuts/Deep Links/HDMI-CEC/уведомления — только при явной продуктовой задаче; HDMI-CEC обычному приложению недоступен.

Общий gate каждого шага: текущий baseline → RED/тест → минимальный diff → unit+connected+lint → release APK/подпись → evidence → запись в `FIXES.md`. Без массового рефакторинга и без смены applicationId/подписи.

---

## H. Результаты тестирования

**Проверено (Emulator tested, API 34, `hume-tv`, 1920×1080):**
- default Home: `cmd package resolve-activity` → `isDefault=true`, `MainActivity`;
- HOME из Google Play → Kotlin Home; BACK на Home — no-op; Settings → BACK → Home;
- `install -r` сохраняет данные, приложение остаётся Home;
- `adb reboot` → Kotlin Home, default Home сохраняется; crash-буфер пуст;
- переключение HOME между Java- и Kotlin-лаунчерами через `set-home-activity`;
- PackageManager: 43 leanback / 31 launcher; leanback-only `com.android.tv.settings` найден;
- permissions: 6 запрошенных, `QUERY_ALL_PACKAGES` нет; `READ_TV_LISTINGS`/location не granted на стенде;
- 720p override работает, 4K override образом отклоняется;
- unit **343/343 debug + 343/343 release**, connected **208/208**, lint **0 Error / 14 Warning** (0.4.49).

**Expected (не проверено фактически):**
- установка/удаление приложения и `PACKAGE_REPLACED` на реальном ТВ;
- обновление лаунчера поверх старой версии с сохранением prefs;
- повторная установка после удаления (wizard);
- поведение на Google TV/OEM;
- offline-старт и отсутствие сети;
- 4K-панель.

**Not tested:**
- реальный ТВ и ИК-пульты, repeat-rate OEM, кнопки Netflix/Assistant;
- API 28/29/35;
- LOW 1 ГБ, 4K-растеризация;
- TalkBack voice-pass, RTL/третий язык, масштаб шрифта;
- системный DreamService, MediaSession, HDMI-CEC;
- длительный soak и память на 4K.

**Unsupported (официально/технически для обычного приложения):**
- универсальное чтение/управление чужими MediaSession без notification listener/`MEDIA_CONTENT_CONTROL`;
- HDMI-CEC через `HdmiControlManager` (system/privileged);
- принудительная установка лаунчера по умолчанию (используется только системный picker);
- запись Channels/Programs в TvProvider без соответствующих прав.

---

## Итог

Системная интеграция **рабочая и в основном корректная**, но не «полный аналог системного лаунчера»: три Medium-направления — TV-совместимость приложений, системная заставка/MediaSession и удаление hidden Power API — требуют отдельного решения. Аппаратная и OEM-матрица остаётся **NOT TESTED**; текущий PASS относится к AVD API 34. Масштабную реализацию не начинал — по условию ТЗ сначала отчёт и согласование.

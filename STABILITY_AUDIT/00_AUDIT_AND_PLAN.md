# Аудит стабильности, обработки ошибок и восстановления

Проект: OVisionTV Kotlin (`com.whatuneed.ovisiontv.kotlin`, 0.5.0 / versionCode 73)
Дата аудита: 2026-02-12
Режим: **read-only**. Production-код, ресурсы, манифест, зависимости и разрешения
в этом аудите не изменялись. Создан только этот отчёт.

Основание: запрос владельца — сначала аудит и отчёт A–I с доказательствами и планом,
затем отдельное подтверждение на исправления. До подтверждения крупный рефакторинг
не начинается.

Обозначения доказательств:

- **S** — ветка/путь в коде подтверждены чтением; runtime-воспроизведение не выполнялось.
- **M** — число/поведение взято из ранее сохранённого измерения проекта; в этом аудите
  повторно не измерялось и не выдаётся за новый замер.
- **N/A** — механизма в проекте нет (например, собственной SQLite-БД).

---

## 0. Краткое резюме

1. **Базовая архитектура в целом разумна.** Тяжёлые чтения каталога, погоды, постеров,
   заставки и настроек устройства в основном уходят в `Dispatchers.IO`; `GlobalScope`,
   `runBlocking`, `Thread.sleep` и ручных пулов нет. JSON-хранилища уже умеют частично
   восстанавливаться, каталог Aerial пишется атомарно, а погода/заставка имеют честные
   fallback-состояния.
2. **Главный риск 24/7 — не один «большой баг», а отсутствие верхнего слоя отказоустойчивости.**
   Нет `Application`, нет обработчика необработанных исключений, нет локального
   диагностического журнала и нет единого состояния «главный экран не загрузился».
   Один сбой `PackageManager` или повреждённое значение `SharedPreferences` может
   оставить пользователя без HOME-экрана, а причина не попадёт ни в один отчёт.
3. **Самые опасные найденные пути:**
   - прямое чтение типизированных `SharedPreferences` без защиты от `ClassCastException`;
     `LocalePreference` читается уже в `attachBaseContext`, то есть до первого экрана;
   - `LauncherRoot.reload()` и загрузка списка в Settings не имеют внешнего `try/catch`;
     сбой одного пакета или гонка uninstall/scan даёт падение корутины/пустой экран;
   - кэш постеров полки ограничен **числом записей**, а не байтами, при hero-разрешении
     до 2560 px; это потенциально десятки мегабайт на слабом ТВ;
   - отмена корутин не доходит до сетевых чтений (`TopShelf`, `Weather`, `WatchNext`,
     `AerialRefresh`), поэтому уход с экрана/быстрое переключение оставляет работу в IO;
   - повреждённые данные профилей/layout перезаписываются «починенной» версией без
     резервной копии, что противоречит правилу «не удалять данные пользователя при ошибке».
4. **ANR на главном потоке в прочитанном коде не найден как постоянный путь.** Есть
   точечные main-thread чтения в Settings (документы, повторная загрузка каталога) и
   запросы `PackageManager` в accessibility-сервисе; они требуют переноса/кэша, но
   измеренного ANR нет. Основные «долгие» операции находятся в IO-потоках.
5. **Восстановление после process death/reboot/wake частично есть.** Профили, порядок,
   фокус и setup progress сохраняются; Settings-страница и фокус теряются из-за
   `stateNotNeeded="true"`. Бесконечного self-restart в коде нет, но и защиты от
   crash-loop тоже нет.
6. **Измерений в этом аудите нет.** `testDebugUnitTest` подтверждён (390 тестов, 0 падений),
   но `androidTest`, 24-часовой прогон, Perfetto, реальный ТВ и слабый бокс не запускались:
   `adb devices` пуст, эмулятор в окружении не поднимается.

**Итоговая оценка:** функционально зрелый лаунчер с хорошими локальными fallback-ами,
но **не готовый к обещанию «24/7 без деградации»** без слоя диагностики, защищённого
чтения настроек и ограничения памяти полки.

---

## A. Общая оценка стабильности

### A.1. Что уже сделано правильно и должно сохраниться

| Область | Подтверждение |
|---|---|
| Атомарная запись каталога | `AtomicWrite.writeFileAtomically`: temp-файл + `ATOMIC_MOVE`, fallback, false при ошибке; `AerialStore.save` под lock |
| Ремонт JSON | `parseProfiles` чинит одну запись, не весь список; `buildHomeOrderRepair` сохраняет/перестраивает порядок; `DiscoveryStore` и weather-cache переживают битый JSON |
| Честный tri-state каталога | `AerialRefreshResult.UPDATED/UNCHANGED/FAILED`; ошибка сети не выдаётся за успех |
| Fallback заставки | Bundled catalogue, stills-only режим, first-frame watchdog, TLS-fallback для клипа |
| Fallback погоды | 3 источника, bounded timeouts, byte cap, кэш последнего прогноза, расчётный retry-delay |
| Внешний TV provider | `WatchNextSource` ловит ошибки query/строк и не роняет Home |
| Ввод | Единый `TvRemoteActivity` + `TvKeyBurstFilter`; повторные press-события и потерянный KeyUp ограничены |
| Ресурсы | `onTrimMemory`/`onLowMemory` в `MainActivity`, `MemoryPressure` с уровнями, LRU-кэши по байтам у большинства bitmap-хранилищ |
| Завершение | `SaverScreen` снимает listeners, останавливает host-плееры; `LauncherRoot` снимает receiver/audio/thermal callbacks |
| Отсутствие «тяжёлых» антипаттернов | нет `GlobalScope`, `runBlocking`, `Thread.sleep`, ручных executor-пулов; нет собственной SQLite с миграциями |

### A.2. Где архитектура не даёт гарантий

- **Нет единого владельца ошибок.** Каждый экран сам решает, что делать с `runCatching`:
  иногда возвращает пустой список, иногда `null`, иногда бросает исключение наружу.
- **Нет верхнего error state у Home.** `visible == null` рисует только фон, `visible.isEmpty()`
  рисует «нет приложений»; отличить «пусто» от «скан не удался» нельзя.
- **Нет локальной диагностики.** `Log.w` есть у сети, заставки и части Aerial, но нет
  crash-лога, счётчика ошибок, последнего успешного снимка и экспорта для поддержки.
- **Нет гарантии, что необязательный слой не уронит весь Compose.** Большинство сетевых
  и файловых ошибок уже поймано, но исключение в одном composable/редьюсере всё равно
  убивает всю Activity: Compose не имеет встроенного error boundary.
- **Нет DB.** Это не дефект: данные лежат в prefs/JSON/файлах. Но значит «database
  integrity» здесь = целостность prefs/JSON, и именно там остались главные дыры.

### A.3. Профиль риска для 24/7

| Риск | Вероятность | Влияние | Комментарий |
|---|---|---|---|
| Повреждённый тип prefs → crash при старте | низкая/средняя | критическое | зависит от внешнего восстановления/старой схемы; код не защищён |
| Один сбой пакета при скане → пустой Home | средняя при install/uninstall-шторме | высокое | нет last-good и внешнего catch |
| Постепенный рост памяти полки | средняя на 4K/большом каталоге | высокое на слабом ТВ | LRU считает записи, не байты |
| Сетевые IO-потоки после ухода с экрана | средняя при плохой сети | среднее | cancellation не доходит до blocking read |
| Потеря layout/профиля при repair | низкая | высокое | repair сразу перезаписывает исходные данные |
| Потеря страницы Settings после process death | средняя | низкое | `stateNotNeeded=true` |
| Невидимый сбой записи настроек | низкая | среднее | часть записей возвращает Boolean, но Home/Discovery его игнорируют |

---

## B. Аудит падений и ANR

### B.1. Реестр путей падения

| ID | Причина | Где | Воспроизведение | Влияние | Безопасное исправление | Приоритет |
|---|---|---|---|---|---|---|
| ST-01 | Прямой typed-getter предполагает тип; `ClassCastException`; `LocalePreference` читается до UI | `data/LocalePreference.kt:19-21`, `MainActivity.kt:68-69`, `Profiles.kt:274`, `HomeOrderStore.kt:23`, `DiscoveryStore.kt:29` | записать `ui_language`/`profiles` другим типом или восстановить несовместимый prefs-файл; холодный старт | лаунчер не стартует, HOME недоступен, возможен crash-loop | единый безопасный reader с fallback, карантин исходного значения и лог; не стирать данные автоматически | **Critical** |
| ST-02 | Загрузка каталога приложений без внешнего catch; `readApp` ловит только `NameNotFoundException`, label не защищён | `ui/LauncherRoot.kt:368-379`, `data/AppRepository.kt:247-264`, `ui/SettingsScreen.kt:596-602` | удалить/сломать пакет между `queryIntentActivities` и `getPackageInfo`; бросить `SecurityException`/`Resources.NotFoundException`; нажать reload | исключение в корутине, пустой/пропавший Home, пустые Settings | per-app `runCatching`, `Result` наружу, last-good список, error state + retry, не показывать «нет приложений» при ошибке | **Critical** |
| ST-03 | Нет process-wide crash/ANR-диагностики и последнего успешного состояния | нет `Application`; только `Log` в сети/заставке | внедрить исключение в composition/startup; процесс падает, в crash-буфере нет контекста приложения | невозможно разобрать 24/7-инцидент; повторяемый crash-loop | `Application` + `Thread.setDefaultUncaughtExceptionHandler`, bounded ring-файл без PII, затем делегировать системному handler; не глотать исключение | **High** |
| ST-04 | Повреждённый PIN/recovery не даёт управляемого восстановления | `data/KidsMode.kt:86-100`, `KidsCrypto.matches:235-253`, `hasRecovery:179`; `PinPad.kt:249-280` | испортить `pin_hash_p*`/salt, оставить kids-профиль без recovery: любой PIN = WRONG, выхода нет | пользователь заперт в kids-профиле; поддержка не имеет штатного reset | явное состояние «credential damaged» и документированный reset родителем/device owner; не удалять PIN молча | **High** |
| ST-05 | Кэш постеров полки ограничен числом записей, не байтами; hero до 2560 px | `ui/components/TopShelf.kt:770-771`, `AdaptivePolicy.kt:63-95`, `loadInto:861-869` | листать много карточек/секций на 1080p/4K, снять `dumpsys meminfo` | OOM/убийство процесса, GC-давление, потеря плавности | `sizeOf = allocationByteCount`, байтовый бюджет полки, `shrinkTo/trim` как у остальных кэшей | **High** |
| ST-06 | Trim памяти подключён только к `MainActivity` | `MainActivity.kt:152-160`; в `SettingsActivity`, `SetupActivity`, `AerialDreamService` override отсутствует | уничтожить Home, оставить Settings/Dream, послать `send-trim-memory` | кэши не реагируют на давление в другом entry point | `Application.onTrimMemory`/`onLowMemory` (или общий базовый callback) для всех entry points | **Medium/High** |
| ST-07 | Main-thread файловое чтение в Settings | `ui/SettingsScreen.kt:2336-2337` (`loadRawText`), `2094-2096` (`AerialCatalog.load`) | открыть документ/обновить каталог на медленном storage, StrictMode/trace | задержка кадра/ввода, не доказанный ANR | читать документ/каталог в `withContext(IO)`, публиковать состояние после завершения | **Medium** |
| ST-08 | Отмена не прерывает blocking HTTP/чтение | `TopShelf.kt:922-946` нет `ensureActive`; `AerialPreviewStore:152-177`; `AerialRefresh:135-158`; `WatchNext:138-180`; `Weather.locate:627-635`, `runFetch:258-322`, `localize:647-682` | уйти с Home/сменить rail на медленной/мёртвой сети | IO-поток/сокет живёт до timeout, повторные запросы, батарея; возможна нехватка IO-потоков | не ловить `CancellationException`, `ensureActive()` в циклах чтения, `withTimeout`, закрывать stream/connection по отмене | **High** |
| ST-09 | Загрузка постеров rail последовательная, без negative cache | `TopShelf.kt:815-847`, `886-900` — `items.forEach`, по 10 s на URL/кандидат | provider row с мёртвым URL; открыть rail/переключить секцию | картинки висят десятки секунд, IO занят, повторные попытки | hero/видимые первыми, ограниченный параллелизм, negative cache с TTL, отмена | **Medium** |
| ST-10 | Погода при ошибке повторяется каждые 60 s без backoff/лимита | `Weather.kt:42-43`, `HomeScreen.kt:451-455` | отключить сеть на длительное время; наблюдать запросы | бесконечные попытки, расход батареи/трафика | экспоненциальный backoff с потолком, сброс после успеха, учёт connectivity; last forecast сохранять | **Medium** |
| ST-11 | Repair перезаписывает повреждённый исходник без копии | `Profiles.kt:273-277`, `HomeOrderStore.kt:42-45` | испортить JSON профиля/порядка; открыть Home | потеря layout/профилей при ошибке парсера или внешнем повреждении | сначала сохранить raw в `*.corrupt`/quarantine + лог, потом repair; не стирать без backup | **Medium** |
| ST-12 | Парсеры prefs не ограничивают число записей | `Profiles.kt:184-212`, `HomeOrderStore.kt:145-180`, `DiscoveryStore.kt:186-205` | подложить огромный массив в prefs; cold start/Settings | OOM/долгий main-thread parse | доменные лимиты (профили/порядок/discovery), отклонение или quarantine; тесты | **Medium** |
| ST-13 | Дисковый кэш превью ограничен 120 файлами, хранит исходный PNG | `SaverPreviewImage.kt:24-31`, `45-58`, `save:179-187` | открыть много клипов в picker, проверить cacheDir | рост диска на десятки МБ; нет byte budget | хранить уменьшенный/сжатый thumb или byte-LRU с trim; отчёт о размере | **Medium** |
| ST-14 | Accessibility-сервис делает `PackageManager`-запросы на main при window events | `OVisionAccessibilityService.kt:74-80`, `DeviceFacts.kt:146-155` | быстро переключать окна приложений при включённом Home override | jank/ANR на слабом ТВ при шторме событий | кэшировать `isDefaultHome`, throttle/debounce, проверять только candidate stock-home | **Medium** |
| ST-15 | Пустой каталог и ошибка скана выглядят одинаково | `LauncherRoot.kt:525-527`, `AppRepository.collectActivities:218-221` | сбой query/скан вернул пусто; Home показывает EmptyState | пользователь не понимает, что делать; нет retry | отдельный error state с retry и last-good; EmptyState только для реально пустого списка | **Medium** |
| ST-16 | Часть ошибок записи/кэша молча игнорируется | `AtomicWrite.kt:12-34` (false), `HomeOrderStore.saveOrder` не сообщает об ошибке, `DiscoveryStore` apply без результата, `Saver` save без проверки | заполнить диск/сделать каталог read-only, изменить порядок/избранное | пользователь думает, что сохранилось; после reboot данные старые | возвращать `Result`/Boolean, логировать один раз, показывать диагностику; не очищать данные | **Medium** |
| ST-17 | `stateNotNeeded=true` у всех Activity; Settings-страница/фокус не восстанавливаются после process death | `AndroidManifest.xml:82-132` | убить процесс на странице настроек, вернуться | потеря навигационного состояния; не падение | убрать флаг или сохранять page/focus в `rememberSaveable`+prefs; instrumentation | **Low** |
| ST-18 | Удаление профиля — несколько отдельных записей без транзакции | `Profiles.kt:325-342` | убить процесс между записью списка и очисткой layout/PIN | orphan layout/discovery/PIN, редко — неполное состояние | tombstone/очередь очистки, идемпотентный cleanup при старте; не менять ID | **Low** |
| ST-19 | Битый JPEG постера не удаляется и декодируется снова | `AerialPosterStore.kt:40-53` | повредить файл в `cacheDir/aerial_posters` | повторный IO/decode при каждом входе в заставку | удалять/переименовывать битый entry, negative cache | **Low** |
| ST-20 | Заставка при полном отказе клипов бесконечно повторяет раунд stills и сетевые попытки | `SaverScreen.kt:523-542`, `MAX_CONSECUTIVE_FAILURES` | отключить сеть на заставке | расход батареи/сети, но не ANR | backoff между раундами, ограничение попыток, явный offline-индикатор | **Low** |

### B.2. Разбор наиболее важных путей

#### ST-01 — повреждённые настройки убивают лаунчер до первого экрана

`LocalePreference.wrap()` вызывается в `attachBaseContext` каждого Activity. Внутри —
`SharedPreferences.getString("ui_language", ...)`. Если значение записано другим типом
(смена схемы, restore, ручное вмешательство, редкое повреждение файла), Android бросает
`ClassCastException` до `onCreate`; пользователь видит падение HOME, а не настройки.
Аналогичная схема есть в `ProfileStore.list`, `HomeOrderStore` и `DiscoveryStore`.

Это не гипотетическая «ошибка парсера JSON»: типобезопасность `SharedPreferences` не
проверяется, и в проекте десятки прямых prefs-getter-вызовов (grep по `getString`,
`getStringSet`, `getBoolean`, `getInt`, `getLong`, `getFloat` дал 128 совпадений, но
часть строк — `context.getString`/`Settings.Secure`). Отдельные JSON-парсеры уже
защищены, а слой prefs — нет. Исправление должно быть **одним** общим reader-ом, а не
отдельными `try/catch` в каждом вызове: иначе легко снова пропустить новый ключ.

#### ST-02 — один пакет может обнулить Home

`AppRepository.readApp` ловит только `NameNotFoundException` на `getPackageInfo`.
`getApplicationLabel` и `activity.loadIconSafe` — разные точки; label не защищён.
`LauncherRoot.reload()` запускает корутину без `try/catch`. Если один пакет исчез
между `queryIntentActivities` и `getPackageInfo` или его resources сломаны, исключение
не превращается в пропуск записи, а завершает загрузку. Следствие — либо пустой Home,
либо необработанное исключение в scope.

`SettingsScreen` повторяет тот же путь: `AppRepository(context).load(...)` внутри
`LaunchedEffect` без внешней защиты. Поэтому инцидент затрагивает и настройки.
Исправление должно сохранить **последний успешный снимок**: пустой список при ошибке
не должен затирать рабочий Home и saved order.

#### ST-05 — кэш полки может быть намного больше заявленного бюджета

`TopShelf.shelfArtCache` создан как `LruCache<String, ImageBitmap>(discoveryArtEntries)`.
По умолчанию `LruCache.sizeOf` возвращает `1`, значит ограничение — **16/14/12 записей**,
а не байты. При этом `loadInto` для hero использует `discoveryHeroPx = 2560/1920/1280`,
а декодирует в `RGB_565`. Один кадр 2560×1440 RGB_565 ≈ 7,0 МиБ (≈7,4 МБ decimal);
даже 1920×1080 ≈ 4,0 МиБ.
Шестнадцать hero-записей могут дать десятки мегабайт сверх остальных кэшей.

В `PERFORMANCE_OPTIMIZATION/07 §7.2` ранее было записано, что LRU полки использует
`sizeOf = byteCount`; в текущем коде это не так. Это **расхождение кода с прежним
инженерным выводом**, а не измеренный OOM. Проверять нужно `dumpsys meminfo` после
листания 20–30 карточек; до замера формулировка — «потенциальные десятки МБ», не
«происходит OOM».

#### ST-08 — отмена есть в Compose, но не в blocking IO

Compose отменяет `LaunchedEffect` при смене ключа, но `HttpURLConnection` и
`ContentResolver` не обязаны прерываться. В `TopShelf.readAtMost`, `AerialPreviewStore.download`,
`AerialRefresh.download` нет `ensureActive()` в цикле чтения; в `Weather.locate` и
`runFetch` `CancellationException` попадает в общий `catch (Exception)`/`runCatching`.
В результате после ухода с Home сеть может продолжать работать до 10–20 секунд на
каждый URL/кандидат. Это не main-thread ANR, но это ресурсный шум и риск исчерпания
IO-пула при быстром переключении секций.

**Правило исправления:** `CancellationException` никогда не должен попадать в
`runCatching`/`catch (Exception)`; циклы чтения обязаны проверять `ensureActive()`;
соединение и поток закрываются в `finally`/`invokeOnCancellation`.

### B.3. ANR / главный поток

| Операция | Поток | Оценка | Доказательство |
|---|---|---|---|
| Загрузка списка приложений | `Dispatchers.IO` | ✅ правильно | `AppRepository.load` → `withContext(IO)` |
| Декод баннеров/иконок | IO | ✅ | `readApp`, `drawableToImage` |
| Погода/геокодинг/локализация | IO | ✅ с оговоркой по отмене | `Weather`, `CitySearch` |
| Aerial refresh/download | IO | ✅, но до 4×20 s и без `ensureActive` | `AerialRefresh` |
| Постеры rail | IO | ⚠️ последовательно, 10 s на кандидат | `TopShelf.loadInto` |
| TV provider cursor | IO | ⚠️ нет timeout/cancellation | `WatchNext.query` |
| Документы Settings | **Main** | ⚠️ file read в composition | `SettingsScreen:2336-2337` |
| Повторная загрузка Aerial после кнопки | **Main** | ⚠️ `AerialCatalog.load` | `SettingsScreen:2094-2096` |
| `PackageManager` в accessibility | **Main** | ⚠️ IPC на каждый window event | `OVisionAccessibilityService:74-80` |
| `DiscoveryStore.markShown` | Main | ⚠️ JSON+prefs на каждый фокус карточки | `HomeScreen` эффект, `DiscoveryStore:80-87` |
| Запись порядка/фокуса | Main | ✅ малые prefs-записи, но без результата | `HomeOrderStore`, `HomeScreen` |

**Вердикт по ANR:** в коде нет очевидного постоянного блокирующего вызова на main,
который бы гарантировал ANR. Есть несколько коротких, но реальных main-thread
file/IPC-путей; на медленном ТВ и при шторме window-событий они требуют исправления.
Измеренного ANR/`am_anr` в этом аудите нет.

---

## C. Аудит памяти

### C.1. Текущие владельцы памяти

| Владелец | Бюджет/лимит | Тип | Комментарий |
|---|---:|---|---|
| `AppArtCache` | 12/8 МБ | `LruCache` c `sizeOf` по allocationBytes | иконки/баннеры из APK; epoch-инвалидация |
| `AerialPosterStore` | 32/24/16 МБ | `LruCache` по `byteCount` | full-HD stills заставки; диск 16 JPEG |
| `AerialPreviewStore.memory` | 6/6/3 МБ | `LruCache` по `byteCount` | превью |
| `AerialPreviewStore.thumbMemory` | 16/12/8 МБ | `LruCache` по `byteCount` | сетка picker |
| `TopShelf.shelfArtCache` | 16/14/12 **записей** | `LruCache` без `sizeOf` | главный риск; hero до 2560 px |
| `TopShelf.arts` | столько же записей | Compose map | вторая ссылка на те же bitmap |
| `warmStill` в `LauncherRoot` | один full-HD bitmap (~8 МБ ARGB) | Compose state | нужен для первого кадра заставки |
| `saverPlayers` | 2 ExoPlayer | host pool | освобождаются в фоне, но создаются заранее |
| Диск: previews | 8 полных PNG + 120 «thumb» PNG | файлы | «thumb» хранится как исходный PNG |
| Диск: posters | 16 JPEG | файлы | bounded по count |

### C.2. Утечки и долгоживущие ссылки: что проверено

| Проверка | Результат |
|---|---|
| `GlobalScope`, `runBlocking`, `Thread.sleep` | не найдены |
| `rememberCoroutineScope` | используется; scope отменяется с composition |
| `LaunchedEffect` loops | часы, погода, saver, setup — отменяются |
| BroadcastReceiver Home | `onDispose` unregister |
| `screenOnReceiver` | `onDestroy` unregister |
| `AudioPlaybackCallback` | `onDispose` unregister |
| Thermal listener | `onDispose` remove |
| Network callback ControlCenter | `onDispose` unregister |
| `SaverScreen` players | host останавливается, owned release |
| Settings preview | `ON_STOP` + dispose освобождают surface |
| Long-lived context | caches/store используют `applicationContext`; `AppRepository` живёт в composition |
| `AerialPosterStore`/`AerialPreviewStore` | статические, но с bounded LRU и app-context |
| `shelfArtCache` | статический, **ограничен count**, не bytes |
| Unfinished jobs | `reloadJob`, `holdJob`, `pending` отменяются/завершаются |

### C.3. Риски, которые нужно закрыть

1. **ST-05** — байтовый лимит кэша полки. Это первый приоритет памяти.
2. **ST-06** — trim не доходит до Settings/Dream, если Home уничтожен.
3. `warmStill` + два host-плеера + LRU — суммарный baseline на LOW-профиле требует
   `dumpsys meminfo` на реальном боксе; сейчас это оценка по аллокациям, не замер.
4. **ST-13** — диск превью; ограничение по файлам не равно ограничению по байтам.
5. **ST-19** — битый poster остаётся на диске и повторно декодируется.
6. **ST-12** — огромный prefs JSON может дать OOM при старте, хотя это не «обычный»
   сценарий, а последствие повреждения/внешнего restore.

### C.4. Поведение под `onTrimMemory`

`MemoryPressure.plan` различает `RUNNING_*`, `UI_HIDDEN`, `BACKGROUND` и `COMPLETE`;
это правильная идея. Но `TRIM_MEMORY_UI_HIDDEN` оставляет `shelf=true` (постеры полки
специально сохраняются для мгновенного возврата), а `TRIM_MEMORY_BACKGROUND` и ниже
уже их сбрасывают. После исправления ST-05 нужно проверить, что byte-budget полки
уменьшается на thermal/LOW и что `trimToProfile()` действительно вызывает
`shrinkTo` для нового byte-лимита.

---

## D. Обработка ошибок: текущая архитектура и целевые уровни

### D.1. Что есть сейчас

- `runCatching`/`try` в ~80 местах; часть возвращает `null`, часть — пустой список,
  часть — честный enum (`AerialRefreshResult`).
- `AppRepository` глотает ошибки `queryIntentActivities`, но не ошибки одного пакета.
- `Weather` сохраняет последний прогноз и логирует сбой источника.
- `AerialRefresh` логирует причину и возвращает `FAILED`, но ловит и `CancellationException`.
- `Profiles`/`HomeOrder` чинят JSON, но не дают diagnostic result и не сохраняют копию.
- `writeFileAtomically` возвращает `false`; не все вызывающие это проверяют.
- Нет `Application`, crash handler, ANR trace, локального отчёта.

### D.2. Целевые уровни (предложение, не реализовано)

| Уровень | Что относится | Поведение | Не делать |
|---|---|---|---|
| **Critical** | профили/layout/prefs/PIN, главный экран, целостность данных | сохранить last-good, показать явный error state с Retry, записать диагностику, не терять исходные данные | не стирать prefs/профиль, не уходить в пустой Home молча |
| **High** | Settings/navigation/restore, kids lock, каталог приложений | деградировать только слой; вернуть управляемый экран/сообщение; retry с backoff | не заменять рабочий Home пустым списком |
| **Medium** | отдельный источник погоды, provider rows, постер, каталог Aerial | placeholder/кэш/fallback, лог, ограниченный retry | не запускать бесконечный цикл, не блокировать UI |
| **Low** | необязательные метаданные, косметика | скрыть/подставить плейсхолдер, не шуметь в логах | не выдавать отсутствие за ошибку данных |

### D.3. Политика ошибок, которую нужно зафиксировать в коде

1. `CancellationException` никогда не перехватывается общим `Exception`/`runCatching`.
2. Все primary-surface операции возвращают `Result`/sealed state; UI не различает
   «пусто» и «ошибка» по одному `emptyList()`.
3. Last-good snapshot хранится в памяти (Home) и, при необходимости, в отдельном
   атомарном файле; fallback не перезаписывает last-good.
4. Repair данных сначала копирует исходник в `*.corrupt`, затем пытается восстановить.
5. Логи — локальные, с ограниченным ring buffer; никаких токенов/URL с query/персональных
   данных. Телеметрия не добавляется.
6. Retry имеет предел/backoff и уважает `foreground`; «нет сети» — штатное состояние.
7. Автоматический restart после падения не вводится без отдельного решения; сначала
   diagnostic + last-good.

---

## E. Система восстановления

| Сценарий | Текущее поведение | Оценка | Что добавить |
|---|---|---|---|
| Падение процесса | система перезапускает Activity; prefs/JSON остаются; crash-лог приложения отсутствует | ⚠️ частично | ST-03, last-good Home, диагностика |
| Process death | Home заново читает профиль/порядок/фокус; Settings теряет page/focus из-за `stateNotNeeded` | ⚠️ частично | ST-17; сохранять Settings state |
| Reboot | `SetupProgress` не повторяет мастер; startup policy на boot; accessibility service | ✅ по коду | проверить на устройстве |
| Wake/standby | `onResume` → foreground/resumeTick; saver скрывается; startup on screen | ✅ по коду | 24h soak + реальный ТВ |
| Нет БД | собственной БД нет; TV provider недоступен → пустые provider rails | ✅ N/A | не добавлять БД «на будущее» |
| Повреждённые настройки | JSON repair; prefs type → crash; repair перезаписывает исходник | ❌ риск | ST-01, ST-11, quarantine |
| Источник недоступен | погода: cache/stale; Aerial: bundled; provider: пусто; постер: placeholder | ✅ | backoff/cancellation ST-08/10 |
| Ошибка картинки | `AppArtCache`/rail → null; `SaverPreviewStore` → still; placeholder | ✅ | negative cache ST-09 |
| Возврат из другого приложения | `resumeTick`, foreground, отмена заставки, preview `ON_STOP` | ✅ | проверить на ТВ |
| Бесконечный restart | self-restart в коде нет | ✅ | не добавлять; диагностика вместо него |
| Повторяемый crash-loop | система может крутить падение; приложение не различает причину | ❌ | ST-03 + last-good; не глотать |

**Вывод:** восстановление уже продумано лучше, чем обработка ошибок. Главные пробелы —
повреждённые prefs, отсутствие last-good/диагностики и потеря Settings state.

---

## F. Производительность, батарея, 24/7

### F.1. Что работает в простое

- Погода спит до расчётного срока (`Weather.refreshDelay`), а не просыпается каждую минуту.
- Заставка спит до дедлайна; музыка приходит через `AudioPlaybackCallback` + страховочный
  опрос, а не через вечный секундный цикл.
- Часы обновляются к границе минуты.
- Aerial refresh проверяется только на foreground и только при `isDue`.
- Discovery обновляется на resume/package change/локальное действие, а не таймером.
- Accessibility `stockHomes` кэшируется на 60 с, redirect throttle 800 мс.

### F.2. Что стоит ресурсов

| Механизм | Когда | Стоимость | Комментарий |
|---|---|---|---|
| Saver player poll | только видимая заставка | 200 мс | нужен для first-frame/stall; не в простое |
| MediaSession poll | только видимая заставка | 1 с | обновление playback state |
| `SaverScreen` stills | при сбое клипов | каждые 26 с + сетевые раунды | ST-20 |
| Rail art | при появлении rail | до 10 с × 2 URL × N последовательно | ST-09, ST-08 |
| Weather retry | offline/stale | 60 с | ST-10 |
| Discovery `markShown` | каждый фокус карточки | JSON+`apply` | ST-21 |
| Setup greeting | только Setup | 2,4/2,2 с | отменяется при dispose |
| Clock/chips | Home видим | раз в минуту | принято |

### F.3. Сценарий 24-часового прогона (ещё не выполнен)

1. Чистая установка, мастер, 2 профиля, kids с PIN и recovery.
2. 24 ч на Home с периодическими 5–10-минутными сессиями: D-pad по сетке, папки,
   rail, поиск, Control Center, Settings, заставка, standby/wake.
3. 20 циклов install/uninstall/update тестового пакета во время Home и Settings.
4. Сеть: offline → DNS fail → timeout → восстановление; проверить отсутствие шторма
   запросов и рост очередей.
5. 10 process death (`am kill`) на Home/Settings/Setup; проверить page/focus/order.
6. `send-trim-memory` на RUNNING_LOW/CRITICAL и UI_HIDDEN; снять PSS до/после.
7. 4K/1080p: 30 heroes, 20 preview thumbnails, 100 rail cards; `dumpsys meminfo`,
   `gfxinfo framestats`, threads/FD count.
8. 24-часовой счётчик: crash/ANR, PSS, threads, FD, cacheDir size, battery/network.
9. Сравнить с baseline; без baseline не объявлять «улучшение».

### F.4. Startup

- MainActivity поднимает Compose сразу; `AppRepository.load` идёт в IO.
- Пока `apps == null`, Home показывает только фон (`LauncherRoot:525-527`); нет
  last-good/persisted snapshot и нет отличия «загрузка» от «ошибка».
- `AerialCatalog` читается после первого кадра в IO, погода — только на resume;
  необязательная сеть старт не блокирует. Это правильно.
- Ранее измеренный cold start (эмулятор) не перепроверялся; для текущего дерева
  абсолютных цифр в этом аудите нет.

---

## G. Сводная таблица проблем

| ID | Проблема | Приоритет | Доказательство | Решение |
|---|---|---|---|---|
| ST-01 | typed prefs getter + отсутствие safe fallback; crash в `attachBaseContext` | **Critical** | `LocalePreference.kt:19-21`, `MainActivity.kt:68-69`; десятки прямых prefs-getter-вызовов | единый safe reader, fallback, quarantine, тесты на неверный тип |
| ST-02 | сбой каталога приложений не пойман, нет last-good/error state | **Critical** | `LauncherRoot.kt:368-379`, `AppRepository.kt:247-264`, `SettingsScreen.kt:596-602` | per-app catch, `Result`, last-good, Retry, различать empty/error |
| ST-03 | нет process crash/ANR-диагностики | **High** | нет `Application`/handler; только `Log` в сети/заставке | handler + bounded local ring, не глотать; diagnostic screen/export |
| ST-04 | повреждённые PIN/recovery не дают reset | **High** | `KidsMode.kt:86-100`, `KidsCrypto.matches:235-253`, `hasRecovery:179` | явное damaged-состояние + документированный reset родителем |
| ST-05 | кэш полки ограничен count, не bytes | **High** | `TopShelf.kt:770-771`, `AdaptivePolicy.kt:63-95` | `sizeOf` по байтам + byte budget + shrink/trim; тест эвикции |
| ST-06 | trim только в MainActivity | **Medium/High** | `MainActivity.kt:152-160`; других override нет | `Application.onTrimMemory`/общий callback |
| ST-07 | main-thread file I/O в Settings | **Medium** | `SettingsScreen.kt:2336-2337`, `2094-2096` | `withContext(IO)` + state после завершения |
| ST-08 | cancellation не прекращает blocking IO | **High** | `TopShelf.kt:922-946`, `Weather.kt:258-322/627-635`, `WatchNext.kt:138-180`, `AerialRefresh.kt:135-158` | не ловить CancellationException, ensureActive, timeout, close |
| ST-09 | rail art последовательный, без negative cache | **Medium** | `TopShelf.kt:815-847`, `886-900` | hero/visible first, limited parallel, negative TTL |
| ST-10 | weather retry 60 s без backoff | **Medium** | `Weather.kt:42-43`, `HomeScreen.kt:451-455` | exponential backoff+cap, connectivity, reset on success |
| ST-11 | repair перезаписывает corrupt original | **Medium** | `Profiles.kt:273-277`, `HomeOrderStore.kt:42-45` | quarantine copy + log before repair |
| ST-12 | нет лимитов в JSON-парсерах prefs | **Medium** | `Profiles.kt:184-212`, `HomeOrderStore.kt:145-180`, `DiscoveryStore.kt:186-205` | доменные caps, reject/quarantine, тесты |
| ST-13 | disk preview cache: 120 full PNG без byte budget | **Medium** | `SaverPreviewImage.kt:24-31`, `45-58` | compressed thumb/byte LRU + trim |
| ST-14 | accessibility PackageManager на main | **Medium** | `OVisionAccessibilityService.kt:74-80`, `DeviceFacts.kt:146-155` | cache/throttle, только candidate home |
| ST-15 | пустой список = ошибка и реально пусто | **Medium** | `LauncherRoot.kt:525-527`; `collectActivities:218-221` | error state + Retry + last-good |
| ST-16 | ошибки записи/кэша молча теряются | **Medium** | `AtomicWrite.kt:12-34`, `HomeOrderStore.saveOrder`, `DiscoveryStore`; часть boolean-результатов не проверяется | Result/Boolean, лог, diagnostics; не стирать |
| ST-17 | Settings state теряется после process death | **Low** | `AndroidManifest.xml:82-132` | убрать `stateNotNeeded`/persist page+focus |
| ST-18 | удаление профиля не транзакционно | **Low** | `Profiles.kt:325-342` | tombstone/idempotent cleanup, не менять ID |
| ST-19 | битый poster не удаляется | **Low** | `AerialPosterStore.kt:40-53` | delete/quarantine битого файла |
| ST-20 | offline saver вечно повторяет сетевые раунды | **Low** | `SaverScreen.kt:523-542` | backoff, предел, offline-индикатор |
| ST-21 | `markShown` пишет prefs на каждый фокус | **Low** | `HomeScreen` эффект; `DiscoveryStore.kt:80-87` | debounce/batch, не терять последний |
| ST-22 | ошибки launch/системных экранов не попадают в диагностику | **Low** | `HomeScreen.launchApp:2279-2290`, `SettingsScreen` fallback-блоки | единый лог без PII; Toast оставить |

### G.1. Связь с предыдущими аудитами

- **ST-02/ST-15** — развитие AQ-08/AQ-13/AQ-14 из `ARCHITECTURE_AUDIT/REPORT.md`;
  per-owner reload исправлен, но внешний exception boundary и last-good всё ещё нет.
- **ST-06** — остаточная дыра после P-MEM-1: `onTrimMemory` добавлен, но только в
  `MainActivity`; другие entry points не покрыты.
- **ST-08** — AQ-16 исправлен для `CitySearch` (ensureActive/deadline), но не для
  `TopShelf`/`Weather`/`WatchNext`/`AerialRefresh`.
- **ST-07** — AQ-08 частично исправлен (device facts/каталог в IO), но `loadRawText`
  и повторная загрузка каталога после кнопки остались на main.
- **ST-05** — новое расхождение с `PERFORMANCE_OPTIMIZATION/07 §7.2`: текущий
  `TopShelf` LRU не задаёт `sizeOf` по байтам.
- **ST-17/ST-18** — известные ограничения state restore/AQ-10; не выдаются за новые
  security-находки, но остаются в реестре стабильности.

---

## H. План улучшений (до реализации нужно подтверждение)

### H.1. Принципы

- Не переписывать архитектуру и не вводить вторую БД/второй focus/второй error-слой.
- Сначала остановить источники падений/потери данных, затем память/сеть, затем UX.
- Каждый этап — отдельный diff с тестами; production не меняется до approval.
- Не добавлять зависимости/разрешения/телеметрию; не скрывать исключения.
- Не удалять пользовательские данные; repair только с quarantine/backup.
- Не выдавать code-level оценку за измерение; после этапов — стенд.

### H.2. Этапы

| Этап | Содержимое | Файлы | Риск | Критерий завершения |
|---|---|---|---|---|
| **0. Safe storage + Home boundary** | safe prefs reader; quarantine corrupt raw; per-app catch; `Result`/last-good/error-state для Home/Settings; empty vs error | `data/` (новый безопасный reader), `Profiles.kt`, `HomeOrderStore.kt`, `DiscoveryStore.kt`, `AppRepository.kt`, `LauncherRoot.kt`, `SettingsScreen.kt` | миграция ключей, неверный fallback может скрыть реальную ошибку | cold start с неверным типом prefs не падает; один сбойный пакет не убирает остальные; last-good сохраняется; unit/instrumentation tests |
| **1. Diagnostics + trim coverage** | crash handler с forwarding; `onTrimMemory` на уровне `Application` (покрывает Settings/Dream/Setup без дублирования в каждой Activity); логирование неуспешных записей | новый `OVisionApp.kt`, `data/CrashReport.kt`, `AndroidManifest.xml`, `MainActivity.kt`, `MemoryPressure.kt` | handler может случайно скрыть crash; объём лога | crash не проглатывается, но есть локальный отчёт; trim срабатывает во всех entry points; тест форматирования/forwarding |
| **2. Memory** | byte budget для `shelfArtCache`; shrink/trim; negative cache/limited parallel для rail; disk preview budget; poster corrupt cleanup | `TopShelf.kt`, `AdaptivePolicy.kt`, `MemoryPressure.kt`, `SaverPreviewImage.kt`, `AerialPosterStore.kt` | эвикция может ухудшить визуал; параллелизм — нагрузка | meminfo после 30 heroes не растёт без предела; cache size ≤ budget; нет регрессии Home; tests |
| **3. Cancellation/network** | общий cancellation-safe HTTP helper; `ensureActive`; no CancellationException catch; backoff; provider timeout/cancel | `Weather.kt`, `CitySearch.kt`, `AerialRefresh.kt`, `TopShelf.kt`, `WatchNext.kt`, `SaverPreviewImage.kt` | таймауты могут сделать редкие сети «неуспешными»; backoff — UX | уход с экрана закрывает запрос; нет накопления IO; offline не крутит бесконечно; tests with fake server |
| **4. Data recovery/state** | bounded parsers, quarantine, PIN damaged state/reset, Settings page restore, profile cleanup idempotency | `Profiles.kt`, `HomeOrderStore.kt`, `DiscoveryStore.kt`, `KidsMode.kt`, `SettingsScreen.kt`, манифест | reset PIN — security-sensitive; caps могут отклонить старые данные | corrupt prefs/JSON даёт управляемое состояние; profile/layout не теряются; page/focus восстанавливаются; tests |
| **5. Stress/measurement** | 24h soak, install/uninstall storm, network loss, process death, low memory, 4K; Perfetto/gfxinfo/meminfo; сравнение с baseline | только тесты/evidence, production — по результатам | нет стенда | отчёт с числами и списком NOT TESTED; без чисел этап не закрыт |

### H.3. Порядок

1. **ST-01 + ST-02** — без них любой 24/7-прогон может закончиться падением HOME.
2. **ST-05 + ST-06** — память; сначала byte budget, потом trim coverage.
3. **ST-03** — диагностика до стресс-тестов, иначе инциденты нечем разбирать.
4. **ST-08/ST-09/ST-10** — сеть/отмена, после стабилизации старта.
5. **ST-11/ST-12/ST-04** — данные/PIN, с осторожным security-review.
6. **ST-13/ST-17/ST-18/ST-19/ST-20/ST-21** — низкий приоритет/косметика.

### H.4. Что не делать

- Не вводить `WorkManager`/фоновый сервис «для retry»: лаунчер существует только как Home.
- Не добавлять Room/SQLite ради «транзакций», пока данные — prefs/JSON; сначала quarantine/atomic.
- Не добавлять автоматический self-restart после crash: это маскирует причину и может дать loop.
- Не чистить кэши агрессивно без замера; disk-кэш заставки — продуктовый fallback.
- Не превращать пустой provider/погоду в выдуманный контент.
- Не менять `stateNotNeeded` без проверки restore-сценария на устройстве.

---

## I. Результаты проверок

### I.1. Выполнено в этом аудите

| Проверка | Результат |
|---|---|
| Чтение production-кода и манифеста | выполнено |
| Поиск `GlobalScope`/`runBlocking`/`Thread.sleep`/ручных пулов | не найдены |
| Поиск `onTrimMemory`/`onLowMemory` | исходно только `MainActivity`; после этапа 1 — `OVisionApp` + `MainActivity` |
| `testDebugUnitTest` | после этапов 0–4: **BUILD SUCCESSFUL**; XML: **412 tests, 0 failures, 0 errors, 0 skipped** |
| `adb devices` | пусто |
| androidTest | **не запускались** (нет устройства/эмулятора) |
| 24h soak / Perfetto / meminfo / gfxinfo | **не выполнялись** |
| Реальный ТВ, слабый бокс, 4K, IR/Bluetooth | **NOT TESTED** |

Число 412 — фактическое содержимое `app/build/test-results/testDebugUnitTest` на текущем
дереве после добавления тестов этапов 0–4; runtime-проверки и этап 5 не выполнены.

### I.2. Что не проверено и почему

- **androidTest** (`HomeScreenTest`, `MainActivityInputTest`, `SettingsScreenTest`,
  `PinPadInputTest`, `TopShelfTest`) не запускался: `adb devices` пуст, AVD в окружении
  не поднимается (`Failed to load opengl`, зависание QEMU). Компиляция androidTest
  ранее успешна, но это не заменяет прогон.
- **Process-death/prefs corruption** на устройстве не воспроизводились; ST-01/ST-02
  подтверждены статическим путём кода (S), не runtime-падением.
- **OOM полки** не воспроизводился; ST-05 — оценка по типу/размеру LRU и target px,
  не измеренный пик.
- **ANR** не зафиксирован; main-thread пути оценены статически.
- **24h/soak** не выполнялся; выводы о росте памяти/очередей требуют прогона.

### I.3. Ограничения отчёта

- В проекте нет собственной БД, поэтому разделы «DB/migrations/transactions» относятся
  к prefs/JSON/файлам, а не к SQLite.
- Предыдущие документы `PERFORMANCE_OPTIMIZATION`/`ARCHITECTURE_AUDIT` содержат
  эмуляторные измерения и старые статусы; в этом отчёте они не пересчитывались.
- Никакие субъективные «ощущения скорости» не выдаются за замеры.
- Владелец подтвердил поэтапную работу; этапы 0–4 внесены отдельными небольшими изменениями с тестами.
  Этот документ — gate и журнал, а не разрешение на массовый рефакторинг.

### I.4. Этапы 0–4: что изменено

| Область | Изменение | Тест/проверка |
|---|---|---|
| Безопасное чтение prefs | `data/SafePreferences.kt`: типизированные чтения с fallback на `ClassCastException`, одно предупреждение на ключ; вызывающие не падают. Значение не удаляется и не перезаписывается — это не destructive quarantine. | `SafePreferencesTest` (3) |
| Prefs-вызовы | `LocalePreference`, `Profiles`, `HomeOrderStore`, `KidsMode`, `ContentPreferences`, `SaverPreferences`, `WeatherPreferences`, `SetupProgress`, `StartupPreferences`, `SleepControls`, `DiscoveryStore`, `DiscoverySettings`, `AppRepository`, `HomeScreen` переведены на safe reader. | compile + 394 unit |
| Ошибка каталога | `AppRepository.loadResult()` отличает `Loaded` от `Failed`; `CancellationException` пробрасывается; один сбойный пакет пропускается, а не обрывает весь каталог; ошибка `queryIntentActivities` больше не превращается в «приложений нет». | `AppRepositoryTest.a_successful_scan_is_reported_as_loaded` (androidTest, компиляция; запуск недоступен) |
| Home | `LauncherRoot`: last-good список сохраняется; ошибка первого сканирования показывает отдельный экран с кнопкой «Повторить», а не `EmptyState`; смена профиля очищает список до нового скана. | compile; runtime на устройстве **NOT TESTED** |
| Settings | `SettingsScreen`: тот же `loadResult`, строка ошибки/повтора на корне, `itemsOwner` не даёт писать layout другому профилю; ошибка не маскируется пустым скрытым списком. | compile; instrumentation **NOT RUN** |
| Diagnostics | `OVisionApp` ставит `CrashReport`: локальный `cache/diagnostics/last-crash.txt` (32 KiB cap) пишется best-effort, затем исходный handler получает crash; self-restart нет. | `CrashReportTest`; forwarding на устройстве **NOT TESTED** |
| Trim | `OVisionApp.onTrimMemory/onLowMemory` вызывает `MemoryPressure.trim` для всех entry points; `MainActivity` оставлен для совместимости. | существующие `MemoryPressureTest` (6) |
| Память полки (этап 2, частично) | `shelfArtCache` переведён с count на byte-budget; `sizeOf` считает ARGB по `width*height*4`; добавлены `discoveryArtBytes` (48/32/16 MiB) и `trimShelfArtCacheTo(bytes)`. | `AdaptivePolicyTest.every_profile_fits_the_hero_and_the_rail_in_its_byte_budget`; runtime meminfo **NOT MEASURED** |
| Диск превью (этап 2) | `SaverPreviewImage` thumb-кэш дополнительно ограничен 32 MiB; удаление старых файлов идёт по count + bytes. | `SaverPreviewBudgetTest` (3) |
| Negative cache artwork (этап 2) | `ArtFailureCache` не даёт повторно открывать мёртвый host на каждой рекомпозиции; TTL 60 с, cap 32, успешная загрузка сбрасывает запись. | `ArtFailureCacheTest` (3) |
| Повреждённый постер (этап 2) | `AerialPosterStore.load` удаляет нечитаемый JPEG, иначе он декодировался и отвергался при каждом входе в заставку. | `AerialPosterStoreTest` (androidTest, компиляция; запуск недоступен) |
| Сеть/отмена (этап 3) | `Weather`, `CitySearch`, `AerialRefresh`, `WatchNext` и `TopShelf` больше не превращают `CancellationException` в обычную ошибку; чтение проверяет `ensureActive`; Nominatim-ответ ограничен 512 KiB. | compile; сетевые сценарии на устройстве **NOT TESTED** |
| Повреждённый PIN (этап 4) | `KidsStore.pinDamaged`/`recoveryDamaged` + `KidsCrypto.credentialUsable`: вместо молчаливого «пустой ввод» PIN-пад показывает явное состояние повреждённых данных. | `KidsCredentialShapeTest` (3) |
| Идемпотентная очистка профиля (этап 4) | Повторный `ProfileStore.delete` удалённого id завершает очистку осиротевших layout/discovery/PIN-ключей. | `ProfileStoreTest.a_repeated_delete_finishes_cleanup_of_orphaned_profile_data` (androidTest, компиляция; запуск недоступен) |
| Main-thread I/O (этап 4) | Текст документов и `AerialCatalog.load` в Settings ушли с главного потока в `Dispatchers.IO`; UI получает результат через state/effect. | compile; без измерений latency |
| Лимиты JSON (этап 4) | `Profiles` (32 профиля / 256 KiB), `Discovery` (1 000 записей / 512 KiB) отбрасывают заведомо повреждённый/огромный blob до построения списка. | `ProfilesTest` (2), `DiscoveryStoreCodecTest` (2) |
| Quarantine профиля (этап 4) | Перед repair сохранён последний повреждённый raw в `list_quarantine`; это одна bounded запись, не растущий лог. | `ProfileStoreTest.a_repaired_profile_list_keeps_the_original_raw_value` (androidTest, компиляция) |
| Quarantine порядка Home (этап 4) | Повреждённый `home_order` сохраняется в `order_quarantine` до перестройки сетки. | `HomeOrderStoreTest.corrupt_order_falls_back_to_the_install_order` (androidTest, компиляция) |
| Offline-заставка (этап 4) | После полного круга stills повтор сетевых клипов идёт с backoff 15/30/60/120 с вместо вечного 26-секундного цикла. | `SaverOfflineBackoffTest` |
| Диагностика записей (этап 4) | `writeFileAtomically` логирует причину неудачи, сохраняя Boolean-контракт и прежний файл. | compile; `AtomicWriteTest` |
| Debounce показа (этап 4) | Hero считается «показанным» после 500 мс удержания, а не при каждом промежуточном фокусе. | compile; UI-тест на устройстве не запускался |
| Accessibility cache (этап 4) | Запрос Home-активностей вынесен в IO-worker; event thread читает только кэш, устаревший кэш не сбрасывается в пустой набор. | compile; сервис на устройстве **NOT TESTED** |
| Weather backoff (этап 4) | После неудачных запросов пауза растёт 1/2/4/8/15 мин вместо постоянных 60 с; успешный ответ сбрасывает счётчик. | `WeatherTest.updateDelayBacksOffAfterRepeatedFailures` |
| Лимиты порядка Home (этап 4) | Порядок ограничен 512 KiB, 1 000 записями и 1 000 приложениями в папке; гигантский blob не парсится и заменяется установочным порядком. | `HomeOrderTest` (2) |
| Диагностика запуска (этап 4) | Неудачи запуска приложения, deep link и системных экранов пишутся в Log без персональных данных; Toast остаётся. | compile; runtime не проверялся |

**Что сознательно не сделано**

- Не реализован собственный ANR-watchdog: это был бы новый фоновый механизм и риск ложных срабатываний; ANR-трейсы остаются системными. Локальный отчёт фиксирует только uncaught exception.
- Corrupt prefs не удаляются автоматически: destructive quarantine запрещён brief; safe reader возвращает default и оставляет исходное значение для будущего repair-этапа.
- `HomeLoadError`/`Settings` error row не проверены на реальном D-pad/ТВ: `adb devices` пуст; это остаётся в §I.2.
- Этапы 2–4 закрыты на code-level; замеры памяти, сетевые сценарии, PIN-сценарии и androidTest требуют устройства.
- Этап 5 (24h soak, Perfetto/gfxinfo/meminfo, 4K, слабый ТВ) не начинался: стенда нет.
- ST-11, ST-20, ST-21 и логирование записей закрыты на code-level; runtime-поведение quarantine/repair/offline не проверено на устройстве.
- ST-14 закрыт на code-level: PackageManager-запрос ушёл в IO-worker, а `isDefaultHome` в Settings/Setup читается через `Dispatchers.IO`; поведение Home override на реальном ТВ не проверено.
- ST-10, ST-12 и ST-22 закрыты на code-level; сетевые тайминги и системные экраны на ТВ не проверены.
- ST-17 (`stateNotNeeded`) сознательно не менялся: brief запрещает трогать флаг без проверки restore-сценария на устройстве.
- Этап 5 по-прежнему заблокирован отсутствием устройства/эмулятора.
- `SettingsScreen` восстановление page/focus уже было сделано в focus-этапе; в этом diff оно только проверено, не переписано.

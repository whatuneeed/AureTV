# Architecture & Code Quality Audit — Kotlin 0.4.44

Дата: 12.11.2026. Объект — **только OVisionTV_Kotlin**, рабочее дерево `0.4.44 / versionCode 64`, `com.whatuneed.ovisiontv.kotlin`. Соседняя Java-версия исключена из этого отчёта по указанию владельца. Аудит выполнен на 0.4.44; **этапы 1–5 реализованы в 0.4.45–0.4.48 (vc65–vc68)** — статус и доказательства ниже; аппаратная/API-матрица остаётся NOT TESTED.

Аудит read-only: production-код, интерфейс, applicationId, подпись, разрешения и зависимости не изменялись. Созданы только этот отчёт и его проверочные артефакты. Ключи подписи и tvOS не читались; сторонние приложения и контент для аудита не запускались.

Прочитаны все **77 production Kotlin-файлов**, 22 306 строк вместе с комментариями и пустыми строками. Проверки UI/state, хранения, сети/медиа и IPC распределены между четырьмя read-only субагентами; сборка, компоненты дизайна, тестовая инфраструктура и перепроверка находок выполнены основным агентом. Это покрытие чтения, **не показатель test coverage**.

Исходное состояние зафиксировано в [source-baseline.json](file:///C:/OpenCode%20Project/OVisionTV/OVisionTV_Kotlin/ARCHITECTURE_AUDIT/evidence/source-baseline.json): 168 текстовых файлов исходников/конфигурации Kotlin-проекта, SHA-256; повторная проверка не обнаружила изменений. Git-корень находится внутри Kotlin-проекта, ветка `master`; ранее внесённые изменения 0.4.44 не закоммичены. Не путать рабочее дерево с HEAD.

## Статус реализации (0.4.45–0.4.48)

Этап 1 («серьёзные ошибки и стабильность») реализован 12.11.2026. Production-код: `SettingsActivity.kt`, `SettingsScreen.kt`, `LauncherRoot.kt`, `ControlCenter.kt`, `SleepControls.kt`, `Profiles.kt`, `AerialRefresh.kt`, `BinaryPlist.kt`, `SaverPreviewImage.kt`, `KidsMode.kt`. Новые тесты: `KidsEntryGrantTest`, `ArtKeyTest`, `SampleForTest`, `KidsStoreTest`, `PinPadInputTest`; расширены `SettingsScreenTest`, `ControlCenterTest`, `SaverPreviewTest`, `BinaryPlistTest`, `ProfilesTest`, `KidsModeTest`, `KidsGateTest`.

| Пункт | Статус | Проверка |
|---|---|---|
| AQ-01 вход Settings без внешнего extra | реализовано: одноразовый in-process `KidsEntryGrant` на профиль | `KidsEntryGrantTest` 4/4, `SettingsScreenTest` |
| AQ-02 владелец exit PIN | реализовано: origin-профиль `kidsLeaveFrom` | `SettingsScreenTest.leave_gate_checks_the_current_kids_profile_not_the_entry_snapshot` |
| AQ-03 permission navigation | реализовано: Power/Timer через общий Kids-гейт | `ControlCenterTest` (2) |
| AQ-04 repair отключает Kids | реализовано: repair сохраняет `kids` | `ProfilesTest` |
| AQ-05 подлинность каталога | реализовано: только HTTPS, redirect только на Apple, без fallback | `AerialRefreshUrlTest` (4) |
| AQ-06 plist | реализовано: cycle detection + бюджет 100k узлов | `BinaryPlistTest` |
| AQ-07 decoded bitmap | реализовано: bounds-first, бюджет ~4 Мпикс, удаление битого файла | `SampleForTest`, `SaverPreviewTest` |
| AQ-11 malformed credentials | реализовано: fail-closed, проверка тега/итераций | `KidsModeTest` |

Прогон 0.4.45: **308/308 debug + 308/308 release** юнит, **205/205** инструментальных, lint **0 Error / 14 Warning / 5 Information**, `assembleRelease` BUILD SUCCESSFUL, source drift = 0. APK `fe5c8609…f0d1`, v2, сертификат `327a50…7b10`; установленный `base.apk` совпал по SHA-256. Доказательства — `UI_UX_UNIFICATION/evidence-0.4.45/`, запись — `FIXES.md` от 12.11.2026.

Этап 2 («ownership и архитектурные границы») реализован в 0.4.46. Production-код: `Weather.kt`, `Profiles.kt`, `LauncherRoot.kt`, `SettingsScreen.kt`, `AerialRefresh.kt`, `SaverPreferences.kt`, `SaverPreviewImage.kt`, новый `AtomicWrite.kt`. Новые тесты: `WeatherRequestTest`, `AtomicWriteTest`, `AerialRefreshUrlTest`, `ProfileStoreTest`; расширены `ProfilesTest`, `SaverSettingsTest`.

| Пункт | Статус | Проверка |
|---|---|---|
| AQ-09 weather commit | реализовано: снимок key/языка, commit только при совпадении, pending force | `WeatherRequestTest` (3) |
| AQ-10 identity/delete | реализовано: монотонный `next_id`, поднимается до записи списка | `ProfilesTest`, `ProfileStoreTest` (2) |
| AQ-13 package receiver | реализовано: `rememberUpdatedState` + generation/owner | полный UI-прогон; реальное package-событие — NOT TESTED |
| AQ-14 owner-scoped items | реализовано: `itemsOwner`, мутации только после загрузки владельца | полный UI-прогон; отдельный race-тест — NOT TESTED |
| AQ-17 atomic catalogue | реализовано: уникальный temp + проверяемый replace, `FAILED` при сбое | `AtomicWriteTest` (5) |
| AQ-12 пустой saver | реализовано: toggleTheme учитывает клипы/hidden | `SaverSettingsTest` (3) |
| AQ-31 границы | изменения ограничены операциями профиля/загрузки/записи | — |

Прогон 0.4.46: **324/324 debug + 324/324 release** юнит, **207/207** инструментальных, lint **0 Error / 14 Warning / 5 Information**, `assembleRelease` BUILD SUCCESSFUL. APK `9e8ea1dd…32cd`, v2, сертификат `327a50…7b10`; установленный `base.apk` совпал по SHA-256, crash-буфер пуст. Живой smoke: мастер RU, Главная, «О системе 0.4.46». Доказательства — `UI_UX_UNIFICATION/evidence-0.4.46/`, запись — `FIXES.md`.

Этап 3 («производительность и lifecycle») реализован в 0.4.47. Production-код: `LauncherRoot.kt`, `SettingsScreen.kt`, `HomeScreen.kt`, `AppRepository.kt`, `TopShelf.kt`, `SaverPreviewImage.kt`, `CitySearch.kt`, `Weather.kt`, `WatchNext.kt`, `StartupPreferences.kt`, новый `CacheBudget.kt`. Новые тесты: `CacheBudgetTest`; расширены `StartupPreferencesTest`, `WeatherRequestTest`.

| Пункт | Статус | Проверка |
|---|---|---|
| AQ-08 Main I/O | реализовано: каталог и очистка кэшей на IO, публикация на Main | полный прогон; trace/detector — NOT TESTED |
| AQ-15 capacity | реализовано: `resize` + 10% остаток после полного trim | `CacheBudgetTest` (3), `TopShelfTest` |
| AQ-16 HTTP cancellation | реализовано: suspend-чтение, `ensureActive`, deadline | `WeatherRequestTest`; slow-drip — NOT TESTED |
| AQ-18 bounded reader | реализовано: CitySearch 512 KiB, Weather 2 MiB + deadline | `CitySearchTest`, `WeatherRequestTest` |
| AQ-19 Cursor traversal | реализовано: package selection, `runCatching`, лимит 500 | `WatchNextTest`/`TopShelfTest`; provider fault — NOT TESTED |
| AQ-20 preview lifecycle | реализовано: `ON_STOP` закрывает preview | полный прогон; счётчики фона — NOT TESTED |
| AQ-21 weather loop | реализовано: ключ `resumeTick, foreground` | полный прогон; число refresh — NOT TESTED |
| AQ-27 startup policy | реализовано: reason + recheck onWake/Kids | `StartupPreferencesTest` (3) |
| AQ-32 leanback-only | реализовано: fallback на `LEANBACK_LAUNCHER` | полный прогон; реальное приложение — NOT TESTED |

Прогон 0.4.47: **330/330 debug + 330/330 release** юнит, **207/207** инструментальных, lint **0 Error / 14 Warning / 5 Information**, `assembleRelease` BUILD SUCCESSFUL. APK `7a26a32c…30e9`, v2, сертификат `327a50…7b10`; установленный `base.apk` совпал по SHA-256, crash-буфер пуст. Живой smoke: мастер RU, Главная, «О системе 0.4.47». Доказательства — `UI_UX_UNIFICATION/evidence-0.4.47/`, запись — `FIXES.md`.

Этап 4 («UX-контракты без редизайна») реализован в 0.4.48. Production-код: `SettingsScreen.kt`, `SetupScreen.kt`, `ControlCenter.kt`, `SettingsActivity.kt`, `HomeOrderStore.kt`, новые `PermissionRecovery.kt` и `CacheBudget.kt` (этап 3). Новые тесты: `PermissionRecoveryTest`; расширены `HomeOrderTest`, `HomeOrderStoreTest`.

| Пункт | Статус | Проверка |
|---|---|---|
| AQ-22 modal focus | реализовано: `modalOpen` в guard и ключах эффекта | компиляция; device — NOT TESTED |
| AQ-23 мастер и TalkBack | реализовано: `onActivate` в semantics кнопок | компиляция; device — NOT TESTED |
| AQ-24 владелец city-query | реализовано: `foundQuery` | компиляция; device — NOT TESTED |
| AQ-25 language resume | реализовано: `appliedLanguage` + `recreate()` | компиляция; device — NOT TESTED |
| AQ-26 Control Center | реализовано: `runTile` для пульта и semantics | компиляция; device — NOT TESTED |
| AQ-28 stable folder ID | реализовано: `buildHomeOrderRepair` + persist | `HomeOrderTest` (2); instrumented — NOT TESTED |
| AQ-29 finish cursor | реализовано: `moveUp` guard | компиляция; device — NOT TESTED |
| AQ-30 permanent denial | реализовано: App Info после отказа | `PermissionRecoveryTest` (4); device — NOT TESTED |

Прогон 0.4.48: **342/342 debug + 342/342 release** юнит, **208/208** инструментальных (7 мин 42 с), lint **0 Error / 14 Warning / 5 Information**, `assembleRelease` BUILD SUCCESSFUL, `install -r` и мастер RU — успешно. APK `4bf03882…ebdd`, v2, сертификат `327a50…7b10`. Доказательства — `UI_UX_UNIFICATION/evidence-0.4.48/`, запись — `FIXES.md`.

Этап 5 («release discipline и подготовка к расширению») выполнен в доступном объёме: добавлены `tools/release_check.py` (APK/подпись/сертификат/версия/RU-EN паритет), `tools/soak.py` (5 мин, 13 циклов, PSS 63 917→56 364 КиБ, 0 crash), `StorageContractTest` (6) и `ARCHITECTURE_AUDIT/RELEASE_DISCIPLINE.md` (gate, политика зависимостей, матрица, checklist). Код приложения не менялся, поэтому APK остаётся 0.4.48/vc68.

| Область | Статус |
|---|---|
| API 34 AVD, 1920×1080 | PASS (208/208 + smoke + soak) |
| API 28/29/35 | NOT TESTED (нет system images) |
| Реальный ТВ / ИК-автоповтор | NOT TESTED |
| LOW 1 ГБ / 4K-панель | NOT TESTED |
| Голосовой TalkBack / RTL / третий язык | NOT TESTED (semantic-тесты есть) |
| Длительный soak / GPU benchmark | NOT TESTED (5-минутный smoke пройден) |

Матрица и правила — `ARCHITECTURE_AUDIT/RELEASE_DISCIPLINE.md`.

### 0.4.49 — hotfix показа заставок

После 0.4.48 обнаружилось, что заставки не отображаются: HTTPS-only относился к метаданным, но у медиа/превью не было запаса по схеме. Android TV не доверяет цепочке Apple (`Trust anchor for certification path not found`), а сохранённый каталог старой сборки мог быть 2019-й схемой без preview. Исправление: HTTP остаётся основным путём для медиа, HTTPS — запасным; превью качаются с тем же запасом; `SaverScreen` один раз повторяет клип по запасному URL; `preferredCatalogue` заменяет пустой/короткий/без-preview сохранённый каталог встроенным. Метаданные каталога остаются HTTPS-only. Проверка: **343/343 debug + 343/343 release** юнит, **208/208** инструментальных (7 мин 8 с), lint **0 Error / 14 Warning**, `release_check.py` PASS, савер с пульта отображается на API 34. APK `2b96ab59…29a0`, v2. Граница: реальный ТВ и его trust store, а также принудительный HTTPS-fallback не проверялись; обновление каталога при недоступном TLS не применяется, остаётся встроенный. Доказательства — `UI_UX_UNIFICATION/evidence-0.4.49/`.

## Уровни доказательств

- **R — воспроизведено на JVM:** вызваны текущие скомпилированные Kotlin-функции с контролируемыми входами. Это не равнозначно воспроизведению Android crash, ANR или UI-сценария.
- **S — статический факт/достижимый путь:** проверены реализация и вызывающие места. Последствия, зависящие от гонки, состояния устройства или внешних данных, отдельно обозначены как риски.
- **M — фактическое измерение/наблюдение:** вывод команды, размер, число объектов или результат теста с источником.
- **NOT TESTED / UNKNOWN:** проверка не выполнена; не считается успехом.

Приоритет отражает влияние и условия возникновения, а не только размер файла. Статическая находка не объявляется измеренной утечкой, OOM или ANR.

# A. Общая оценка

**Вердикт:** проект пригоден для дальнейшей контролируемой проверки, но **безусловный production-ready статус не подтверждён**. Главные препятствия — доверие к входным данным, принадлежность авторизации/асинхронного состояния профилю и недостаточные ограничения декодирования; отсутствие ViewModel или DI не является причиной этого вердикта.

### Сильные стороны

1. Реальные функции уже реализованы: Home/App Library, папки, профили, локальный Kids Mode, рекомендации TvProvider, поиск приложений, быстрые настройки, погода, заставка и мастер настройки.
2. Значительная часть правил — чистые функции: порядок, профили, фильтрация, расписание, геометрия и навигация; они доступны для JVM-тестирования.
3. Есть совместимость форматов: строковые элементы старого порядка, отсутствие поля `kids`, прежний ключ темы `space`, версия шагов мастера.
4. Основные запросы, загрузка artwork и PBKDF2 вынесены с Main. PIN имеет соль, PBKDF2-SHA256/120000, проверку cancellation/revision и атомарный учёт lockout.
5. Artwork хранится ключами, предусмотрены LRU, инвалидация, обработка trim и адаптивные бюджеты. Медиа-плееры освобождаются при disposal.
6. Общая система токенов, размеров, фокуса и компонентов уже существует; её не нужно создавать заново.
7. Свежие host-тесты и lint зелёные; у текущего релиза есть также сохранённый полный инструментальный прогон. Новые контрпримеры показывают границы этого покрытия.

### Основные слабые места

- Публичная SettingsActivity принимает подделываемый признак успешной проверки PIN.
- Авторизация и загруженные данные не всегда привязаны к текущему профилю/поколению запроса.
- Некоторые операции чтения и удаления файлов выполняются непосредственно на Main.
- Отмена корутины не гарантирует отмену HTTP или запрет запоздавшей записи.
- Ограничение размера скачанных байтов не ограничивает раскрытие plist и декодированный bitmap.
- Несколько UI-контроллеров одновременно выполняют навигацию, бизнес-правила и побочные эффекты; это уже проявляется конкретными ошибками ownership.

В реестре — **32 пункта: Critical — 0, High — 7, Medium — 22, Low — 3**. Отсутствие подтверждённого Critical не является гарантией отсутствия критических дефектов. Kids Mode — защита собственных поверхностей лаунчера, а не Android parental control.

# B. Архитектурная карта

## B1. Модуль и стек

Единственный Gradle-модуль — `:app`: [settings.gradle.kts:18](file:///C:/OpenCode%20Project/OVisionTV/OVisionTV_Kotlin/settings.gradle.kts#L18). Отдельных domain/data/UI Gradle-модулей нет.

| Область | Обнаруженная реализация |
|---|---|
| Плагины | AGP 8.7.2, Kotlin и Compose plugin 2.0.21 — [build.gradle.kts:2](file:///C:/OpenCode%20Project/OVisionTV/OVisionTV_Kotlin/build.gradle.kts#L2) |
| Gradle | 8.9 — [gradle-wrapper.properties:3](file:///C:/OpenCode%20Project/OVisionTV/OVisionTV_Kotlin/gradle/wrapper/gradle-wrapper.properties#L3) |
| Android | minSdk 28, targetSdk 35, compileSdk 35; vc64/0.4.44 — [app/build.gradle.kts:23](file:///C:/OpenCode%20Project/OVisionTV/OVisionTV_Kotlin/app/build.gradle.kts#L23) |
| JVM | В проверенной среде Temurin 21.0.12.1; Java/Kotlin bytecode target 17 |
| UI | Обычный Jetpack Compose, Material3, собственный D-pad/focus engine; `androidx.tv` не используется |
| Основные зависимости | Compose BOM 2024.09.03, activity-compose 1.9.2, core-ktx 1.13.1, coroutines-android 1.7.3, Media3 ExoPlayer/UI 1.4.1, profileinstaller 1.4.1 — [app/build.gradle.kts:96](file:///C:/OpenCode%20Project/OVisionTV/OVisionTV_Kotlin/app/build.gradle.kts#L96) |
| Release | R8 и resource shrinking включены; параметры подписи берутся извне проекта |
| Тесты | JUnit4, org.json для JVM, Compose UI tests, AndroidX runner/rules |

В production-исходниках не обнаружены ViewModel, StateFlow, SharedFlow, LiveData, DI-фреймворк, Room, DataStore или собственная SQLite-база. Это описание стека, не отдельный дефект. `media3-ui` используется: PlayerView присутствует в коде заставки и XML, удалять зависимость как «неиспользуемую» нельзя.

## B2. Слои и потоки

```text
MainActivity → LauncherRoot → HomeScreen / локальные overlays
SettingsActivity → SettingsScreen
SetupActivity → SetupScreen
        ↓                       ↑
Android repositories/stores → snapshots, callbacks, resumeTick
        ↓
SharedPreferences / PackageManager / TvProvider / HTTP / files
        ↓
Artwork caches / Aerial catalogue / ExoPlayer / device services
```

- **Activity-границы:** Main — HOME/LEANBACK, `singleTask`; Settings — LEANBACK, `singleTop`; Setup приватная. Экспорт HOME/Settings нужен продукту, закрывать их вместо проверки доверия нельзя.
- **UI-state:** `remember`, `mutableStateOf`, mutable state collections, `rememberSaveable`, `LaunchedEffect` и `DisposableEffect`. Синхронизация отдельных Activity идёт через stores и `resumeTick`.
- **Навигация:** Root координирует профили и гейты; Home владеет своими overlays/редактированием; Settings использует page IDs, history и focus map. D-pad обрабатывается собственными key handlers.
- **Данные:** Android stores рядом с чистыми правилами в `data`. Часть Android-моделей ссылается на Activity-классы и Compose-типы; слой не полностью независим от presentation.
- **Побочные эффекты:** создаются локально в UI или process-wide объектах. Есть отдельные тестовые callbacks, но общего центра ownership/cancellation нет.

Цикл между Gradle-модулями отсутствует: модуль один. Есть двунаправленные зависимости пакетов `data ↔ Activity/UI`; это ограничение дальнейшего выделения чистого слоя, не ошибка компиляции.

## B3. Хранилища и внешние интеграции

| Область | Хранение / интеграция |
|---|---|
| Профили | JSON в `profiles`, текущий ID и настройки picker |
| Layout | `ovisiontv_launcher` для p1; отдельные layout-файлы остальных профилей; порядок, папки, dock/focus |
| Kids | `kids_mode`: хэши PIN/recovery, allowlist, счётчик и срок блокировки |
| Погода | Настройки и JSON-кэш; Open-Meteo, MET Norway, wttr.in; геокодирование/локализация |
| Мастер | Отдельный versioned progress в `setup` |
| Рекомендации | Android TvProvider / Watch Next и Preview Programs; не собственная контентная БД |
| Artwork | PackageManager/APK, URI провайдера, файлы и bitmap-LRU |
| Заставка | Bundled catalogue → обновлённый JSON в filesDir; HTTP tar/plist; streaming Media3, кэши кадров |
| Устройство | Framework services, accessibility service, device-admin receiver, AlarmManager |
| Backup | В Kotlin нет пользовательского экспорта/импорта настроек; framework backup выключен. Это граница функций, не повод копировать чужой restore без анализа |

## B4. Существующие функции и возможность расширения

| Компонент | Текущее состояние | Условие безопасного расширения |
|---|---|---|
| Home, приложения, док | Реализованы | Стабильные ключи, единая проверка запуска и owner-scoped reload |
| Папки | Реализованы чистые операции и сохранение | Persist структурного repair, тесты порядка/фокуса |
| Профили | Реализованы отдельные layouts | Привязка гейтов/данных к owner; безопасная identity при удалении |
| Рекомендации/поиск | TvProvider и локальный поиск приложений | Защищённый traversal, ограничение объёма, актуальность результатов |
| Быстрые настройки | Реализованы | Единый permission gate и одинаковая активация remote/semantics |
| Погода | Реализована | Request snapshot/revision, политика foreground, отменяемый bounded HTTP |
| Заставка | Реализована | Совместный invariant themes/hidden; lifecycle и budgets |
| Настройки | Реализованы | Выделять конкретные owner-инварианты, не переписывать экран целиком |
| Темы/персонализация | Общие токены и темы заставки; произвольной темы UI нет | Не добавлять отклонённые функции в техническом аудите |
| Backup/restore | Не реализован пользовательский Kotlin restore | Сначала схема/границы/atomic recovery, затем отдельное согласование функции |

Система дизайна — [Tokens.kt:88](file:///C:/OpenCode%20Project/OVisionTV/OVisionTV_Kotlin/app/src/main/java/com/whatuneed/ovisiontv/kotlin/ui/Tokens.kt#L88), [TvFocus.kt:19](file:///C:/OpenCode%20Project/OVisionTV/OVisionTV_Kotlin/app/src/main/java/com/whatuneed/ovisiontv/kotlin/ui/TvFocus.kt#L19), [TvCanvas.kt:27](file:///C:/OpenCode%20Project/OVisionTV/OVisionTV_Kotlin/app/src/main/java/com/whatuneed/ovisiontv/kotlin/ui/TvCanvas.kt#L27). Её сохранение — ограничение плана; визуальный редизайн не предлагается.

# C. Реестр проблем

Обозначения R/S/M — из методики выше. Для S-пунктов наличие конкретной ветки кода подтверждено, но связанные crash/ANR/OOM, гонки и потери фокуса не выдаются за выполненный runtime-тест. Подробные причины, влияние, риск исправления и проверка каждого ID — после таблицы.

| ID | Проблема | Расположение | Приоритет | Решение |
|---|---|---|---|---|
| AQ-01 | S: внешний extra обходит entry PIN | [SettingsActivity.kt:54][aq01] | High | Авторизация внутри Settings, без доверия к extra |
| AQ-02 | S: exit gate проверяет PIN первоначального, не текущего профиля | [SettingsScreen.kt:712][aq02] | High | Захват origin-ID и профильная авторизация |
| AQ-03 | S: Power/Timer открывают permission screens мимо Kids gate | [ControlCenter.kt:525][aq03] | High | Общий gate перед получением полномочий |
| AQ-04 | R: ремонт пустого имени сбрасывает kids | [Profiles.kt:195][aq04] | High | Сохранить security-поля при repair |
| AQ-05 | S: каталог обновляется без проверки подлинности | [AerialRefresh.kt:72][aq05] | High | TLS/подпись либо безопасный fallback |
| AQ-06 | R: экспоненциальное раскрытие plist | [BinaryPlist.kt:48][aq06] | High | Cycle detection и общий work budget/memoization |
| AQ-07 | S: compressed limit не ограничивает decoded bitmap | [SaverPreviewImage.kt:89][aq07] | High | Bounds-first и pixel budget до allocation |
| AQ-08 | S: файловое I/O на Main | [SettingsScreen.kt:793][aq08] | Medium | IO dispatcher, обновление UI после завершения |
| AQ-09 | S: weather-response записывается под новой конфигурацией | [Weather.kt:237][aq09] | Medium | Снимок запроса и revision-checked commit |
| AQ-10 | S: delete профиля не crash-atomic при reuse ID | [Profiles.kt:319][aq10] | Medium | Непереиспользуемая identity и orphan cleanup |
| AQ-11 | R: повреждённые credentials бросают исключение/принимают чужой tag | [KidsMode.kt:235][aq11] | Medium | Валидация формата, отказ без удаления PIN |
| AQ-12 | R: допустимые переключения создают пустой playlist | [SaverPreferences.kt:54][aq12] | Medium | Совместная проверка themes/hidden/catalogue |
| AQ-13 | S: receiver вызывает reload со старым orderStore | [LauncherRoot.kt:349][aq13] | Medium | Актуальный callback или owner-key эффекта |
| AQ-14 | S: старые items можно сохранить в новый профиль | [SettingsScreen.kt:448][aq14] | Medium | Owner-scoped loaded state и mutation guard |
| AQ-15 | S: thermal trim не уменьшает maxSize LRU | [AppRepository.kt:57][aq15] | Medium | Resize, а не только trimToSize |
| AQ-16 | S: coroutine cancellation не прекращает HTTP | [CitySearch.kt:82][aq16] | Medium | Disconnect по отмене и общий deadline |
| AQ-17 | S: общий temp/непроверенный replace допускают ложный UPDATED | [AerialRefresh.kt:51][aq17] | Medium | Сериализация и проверяемая atomic запись |
| AQ-18 | S: отдельный HTTP-reader без byte limit | [Weather.kt:626][aq18] | Medium | Переиспользовать bounded reader |
| AQ-19 | S: Cursor traversal не защищён, limit поздний | [WatchNext.kt:166][aq19] | Medium | Защитить traversal и ограничить материализацию |
| AQ-20 | S: Settings preview освобождается только при disposal | [SaverScreen.kt:535][aq20] | Medium | Lifecycle STOP/foreground ownership |
| AQ-21 | S: weather-loop не зависит от foreground | [HomeScreen.kt:373][aq21] | Medium | Явная политика фонового refresh |
| AQ-22 | S: focus-effect не учитывает модальные поверхности | [SettingsScreen.kt:2146][aq22] | Medium | Modal guard и явный возврат focus |
| AQ-23 | S: Setup buttons без semantics onClick | [SetupScreen.kt:1701][aq23] | Medium | Общий callback для remote/semantics |
| AQ-24 | S: выбираемы результаты предыдущего city-query | [SetupScreen.kt:1135][aq24] | Medium | Привязать результат к query |
| AQ-25 | S: язык Settings устаревает после повторного Setup | [SettingsScreen.kt:425][aq25] | Medium | Проверка языка на resume и обновление context |
| AQ-26 | S: semantics игнорирует closes=false | [ControlCenter.kt:820][aq26] | Medium | Единая activation-ветка |
| AQ-27 | S: отложенный startup не перепроверяет политику | [StartupPreferences.kt:96][aq27] | Medium | Event reason и актуальные условия перед запуском |
| AQ-28 | R: исправленный folder-ID не сохраняется | [HomeOrderStore.kt:36][aq28] | Low | Persist структурного repair |
| AQ-29 | S: Up на finish создаёт focus=-1 | [SetupScreen.kt:679][aq29] | Low | Не выходить из buttons при rows=0 |
| AQ-30 | S: нет восстановления после permanent denial | [SetupScreen.kt:374][aq30] | Low | Rationale/переход в App info |
| AQ-31 | S: ownership-инварианты распределены по большим UI-контроллерам | [SettingsScreen.kt:425][aq31] | Medium | Выделять конкретные state/operation boundaries |
| AQ-32 | S+M: startup не разрешает leanback-only выбор | [StartupPreferences.kt:126][aq32] | Medium | Leanback fallback/валидированный component |

[aq01]: file:///C:/OpenCode%20Project/OVisionTV/OVisionTV_Kotlin/app/src/main/java/com/whatuneed/ovisiontv/kotlin/SettingsActivity.kt#L54
[aq02]: file:///C:/OpenCode%20Project/OVisionTV/OVisionTV_Kotlin/app/src/main/java/com/whatuneed/ovisiontv/kotlin/ui/SettingsScreen.kt#L712
[aq03]: file:///C:/OpenCode%20Project/OVisionTV/OVisionTV_Kotlin/app/src/main/java/com/whatuneed/ovisiontv/kotlin/ui/components/ControlCenter.kt#L525
[aq04]: file:///C:/OpenCode%20Project/OVisionTV/OVisionTV_Kotlin/app/src/main/java/com/whatuneed/ovisiontv/kotlin/data/Profiles.kt#L195
[aq05]: file:///C:/OpenCode%20Project/OVisionTV/OVisionTV_Kotlin/app/src/main/java/com/whatuneed/ovisiontv/kotlin/data/AerialRefresh.kt#L72
[aq06]: file:///C:/OpenCode%20Project/OVisionTV/OVisionTV_Kotlin/app/src/main/java/com/whatuneed/ovisiontv/kotlin/data/BinaryPlist.kt#L48
[aq07]: file:///C:/OpenCode%20Project/OVisionTV/OVisionTV_Kotlin/app/src/main/java/com/whatuneed/ovisiontv/kotlin/ui/components/SaverPreviewImage.kt#L89
[aq08]: file:///C:/OpenCode%20Project/OVisionTV/OVisionTV_Kotlin/app/src/main/java/com/whatuneed/ovisiontv/kotlin/ui/SettingsScreen.kt#L793
[aq09]: file:///C:/OpenCode%20Project/OVisionTV/OVisionTV_Kotlin/app/src/main/java/com/whatuneed/ovisiontv/kotlin/data/Weather.kt#L237
[aq10]: file:///C:/OpenCode%20Project/OVisionTV/OVisionTV_Kotlin/app/src/main/java/com/whatuneed/ovisiontv/kotlin/data/Profiles.kt#L319
[aq11]: file:///C:/OpenCode%20Project/OVisionTV/OVisionTV_Kotlin/app/src/main/java/com/whatuneed/ovisiontv/kotlin/data/KidsMode.kt#L235
[aq12]: file:///C:/OpenCode%20Project/OVisionTV/OVisionTV_Kotlin/app/src/main/java/com/whatuneed/ovisiontv/kotlin/data/SaverPreferences.kt#L54
[aq13]: file:///C:/OpenCode%20Project/OVisionTV/OVisionTV_Kotlin/app/src/main/java/com/whatuneed/ovisiontv/kotlin/ui/LauncherRoot.kt#L349
[aq14]: file:///C:/OpenCode%20Project/OVisionTV/OVisionTV_Kotlin/app/src/main/java/com/whatuneed/ovisiontv/kotlin/ui/SettingsScreen.kt#L448
[aq15]: file:///C:/OpenCode%20Project/OVisionTV/OVisionTV_Kotlin/app/src/main/java/com/whatuneed/ovisiontv/kotlin/data/AppRepository.kt#L57
[aq16]: file:///C:/OpenCode%20Project/OVisionTV/OVisionTV_Kotlin/app/src/main/java/com/whatuneed/ovisiontv/kotlin/data/CitySearch.kt#L82

[aq17]: file:///C:/OpenCode%20Project/OVisionTV/OVisionTV_Kotlin/app/src/main/java/com/whatuneed/ovisiontv/kotlin/data/AerialRefresh.kt#L51
[aq18]: file:///C:/OpenCode%20Project/OVisionTV/OVisionTV_Kotlin/app/src/main/java/com/whatuneed/ovisiontv/kotlin/data/Weather.kt#L626
[aq19]: file:///C:/OpenCode%20Project/OVisionTV/OVisionTV_Kotlin/app/src/main/java/com/whatuneed/ovisiontv/kotlin/data/WatchNext.kt#L166
[aq20]: file:///C:/OpenCode%20Project/OVisionTV/OVisionTV_Kotlin/app/src/main/java/com/whatuneed/ovisiontv/kotlin/ui/components/SaverScreen.kt#L535
[aq21]: file:///C:/OpenCode%20Project/OVisionTV/OVisionTV_Kotlin/app/src/main/java/com/whatuneed/ovisiontv/kotlin/ui/HomeScreen.kt#L373
[aq22]: file:///C:/OpenCode%20Project/OVisionTV/OVisionTV_Kotlin/app/src/main/java/com/whatuneed/ovisiontv/kotlin/ui/SettingsScreen.kt#L2146
[aq23]: file:///C:/OpenCode%20Project/OVisionTV/OVisionTV_Kotlin/app/src/main/java/com/whatuneed/ovisiontv/kotlin/ui/SetupScreen.kt#L1701
[aq24]: file:///C:/OpenCode%20Project/OVisionTV/OVisionTV_Kotlin/app/src/main/java/com/whatuneed/ovisiontv/kotlin/ui/SetupScreen.kt#L1135
[aq25]: file:///C:/OpenCode%20Project/OVisionTV/OVisionTV_Kotlin/app/src/main/java/com/whatuneed/ovisiontv/kotlin/ui/SettingsScreen.kt#L425
[aq26]: file:///C:/OpenCode%20Project/OVisionTV/OVisionTV_Kotlin/app/src/main/java/com/whatuneed/ovisiontv/kotlin/ui/components/ControlCenter.kt#L820
[aq27]: file:///C:/OpenCode%20Project/OVisionTV/OVisionTV_Kotlin/app/src/main/java/com/whatuneed/ovisiontv/kotlin/data/StartupPreferences.kt#L96
[aq28]: file:///C:/OpenCode%20Project/OVisionTV/OVisionTV_Kotlin/app/src/main/java/com/whatuneed/ovisiontv/kotlin/data/HomeOrderStore.kt#L36
[aq29]: file:///C:/OpenCode%20Project/OVisionTV/OVisionTV_Kotlin/app/src/main/java/com/whatuneed/ovisiontv/kotlin/ui/SetupScreen.kt#L679
[aq30]: file:///C:/OpenCode%20Project/OVisionTV/OVisionTV_Kotlin/app/src/main/java/com/whatuneed/ovisiontv/kotlin/ui/SetupScreen.kt#L374
[aq31]: file:///C:/OpenCode%20Project/OVisionTV/OVisionTV_Kotlin/app/src/main/java/com/whatuneed/ovisiontv/kotlin/ui/SettingsScreen.kt#L425
[aq32]: file:///C:/OpenCode%20Project/OVisionTV/OVisionTV_Kotlin/app/src/main/java/com/whatuneed/ovisiontv/kotlin/data/StartupPreferences.kt#L126

## C1. High: границы доверия и ресурсов

### AQ-01 — Settings авторизуется внешним extra (S)
- **Где:** SettingsActivity:19–24,54; экспорт — [AndroidManifest.xml:108–121](file:///C:/OpenCode%20Project/OVisionTV/OVisionTV_Kotlin/app/src/main/AndroidManifest.xml#L108); SettingsScreen:559–565.
- **Факт/причина:** `kids_ok=true` передаётся в `kidsUnlocked`; обычное стороннее приложение может сформировать такой Intent. Extra не подтверждает успешную проверку PIN.
- **Влияние:** entry gate не включается; собственные настройки Kids становятся доступны без предусмотренной авторизации. Один Intent сам по себе не изменяет prefs.
- **Исправление:** проверять авторизацию внутри Settings; убрать доверие к extra. Для передачи уже полученного разрешения допустим только внутренний одноразовый grant, привязанный к профилю.
- **Риск:** двойной PIN, изменения шва Cancel/focus; экспорт Settings должен остаться для LEANBACK.
- **Проверка:** внешний cold/warm start без extra, с false/true; Kids+PIN остаётся защищён, законный внутренний вход не ломается.

### AQ-02 — неверный владелец exit PIN (S)
- **Где:** SettingsScreen:712–724,559,2916–2939.
- **Факт/причина:** `switchProfile` проверяет текущий профиль, но pad получает первоначальный `kidsGateProfile`. При A→Kids B→A проверяется PIN A вместо PIN B.
- **Влияние:** PIN другого профиля разрешает переход либо правильный PIN B не проходит; это не доказательство неисправности отдельного Home picker.
- **Исправление:** фиксировать origin-ID при открытии leave gate; разделить entry authorization и текущего владельца операции.
- **Риск:** нельзя внезапно заблокировать взрослую сессию после установки собственного PIN; требуется согласованная семантика сессии.
- **Проверка:** разные PIN A/B, A без PIN, смена профиля при незавершённой проверке; результат применим только к захваченному origin/target.

### AQ-03 — permission navigation вне Kids gate (S)
- **Где:** ControlCenter:525–526,586–588; SleepControls:123–138.
- **Факт/причина:** Power/Timer вызывают `requestPermission` напрямую, а не через существующий `openSystem`/гейт.
- **Влияние:** при отсутствии права собственная поверхность лаунчера открывает admin/accessibility Settings без предусмотренного PIN. Это не заявление об Android-wide parental control.
- **Исправление:** провести получение новых полномочий через общий gate; уже разрешённые power/timer действия сохранить.
- **Риск:** OEM fallback и продолжение после выдачи права; нельзя удалить полезные быстрые действия.
- **Проверка:** Kids+PIN, admin/accessibility отсутствуют; gate появляется до system screen. После законной выдачи timer/power работают.

### AQ-04 — repair имени отключает Kids (R)
- **Где:** Profiles:195–200,271–275.
- **Факт/причина:** пустое имя создаёт `Profile(id, defaultName, "", 0)` с default `kids=false`; `list()` сохраняет repair.
- **Влияние:** повреждение только имени снимает фильтрацию и гейты, хотя PIN может остаться. Обычная клавиатура пустое имя не сохраняет.
- **Исправление:** ремонтировать display-поля с сохранением security-полей; отсутствующее legacy-поле `kids` по-прежнему трактовать отдельно.
- **Риск:** нельзя менять здоровые имена или автоматически отключать защиту при повреждении данных.
- **Проверка:** blank/whitespace name + kids=true; повторное чтение сохранённого repair сохраняет kids=true. JVM-контрпример воспроизведён.

### AQ-05 — подлинность catalogue update не проверяется (S)
- **Где:** AerialRefresh:72,98–118.
- **Факт/причина:** HTTP tar определяет сохраняемый каталог и URL; tar checksum не является проверкой автора данных.
- **Влияние:** сетевой посредник может подменить метаданные/ресурсы; exploitation и реальные последствия не запускались.
- **Исправление:** принимать обновления через TLS или проверенную подпись; при недоступности доверенного обновления сохранять прежний/bundled catalogue.
- **Риск:** Apple TLS недоступен в части сетей; простое переключение схемы может сломать обновление. Нельзя вводить автоматический небезопасный fallback.
- **Проверка:** подмена, redirect на другую схему/host, ошибка подписи/TLS; старый catalogue и offline saver сохраняются.

### AQ-06 — plist раздувается экспоненциально (R)
- **Где:** BinaryPlist:48–107.
- **Факт/причина:** ограничены depth/object table, но не суммарное раскрытие повторных ссылок. Shared DAG копируется как дерево.
- **Влияние:** CPU/allocations растут экспоненциально; OOM/ANR на Android не воспроизводились. На JVM 98 байт/depth14 дали 16 383 array nodes; depth32 payload не исполнялся.
- **Исправление:** cycle detection плюс общий work budget или memoization; одного depth limit недостаточно.
- **Риск:** malformed таблицы начнут отклоняться; корректные общие ссылки должны сохранять семантику.
- **Проверка:** self-reference, shared DAG, лимит посещений/allocations и существующие Apple fixtures; тесты выполняются с безопасным бюджетом.

### AQ-07 — decoded bitmap не ограничен (S)
- **Где:** AerialPreviewStore в SaverPreviewImage:89–112, также thumbnail path:43–55.
- **Факт/причина:** compressed limit 8 MiB не ограничивает width/height; sample=1/2 задаётся без bounds-first. LRU проверяет уже созданный bitmap; файл сохраняется до decode.
- **Влияние:** возможны большие allocations/OOME/GC pressure и повторная декодировка плохого disk entry; величина пика и crash не измерялись.
- **Исправление:** проверить dimensions/pixels до allocation, выбрать sample по budget, убрать некорректный cache entry.
- **Риск:** корректные крупные изображения могут сильнее downsample; стандартные preview/still должны остаться качественными.
- **Проверка:** oversized dimensions при малом compressed size отклоняются до decode; повреждённый файл не используется бесконечно; обычные assets проходят.

## C2. Medium: корректность состояния и побочных эффектов

### AQ-08 — блокирующее файловое I/O на Main (S)
- **Где:** LauncherRoot:158 → AerialCatalog:46–50/AerialStore:45–48; SettingsScreen:532–534,793–803.
- **Факт/причина:** composition/remember читает файлы и разбирает JSON; Main input callback синхронно удаляет cache directories.
- **Влияние:** возможны задержки первого кадра/ввода на медленном storage. Blocking path доказан, длительность и ANR — нет.
- **Исправление:** существующий IO coroutine-паттерн; publishing результата на Main, busy/completion для очистки.
- **Риск:** loading interval; сохранить fallback и не показывать успешную очистку до completion.
- **Проверка:** Main-thread I/O detector/trace, холодный/повреждённый catalogue, populated cache на slow storage.

### AQ-09 — weather commit теряет конфигурацию запроса (S)
- **Где:** Weather:225–234,237–284; WeatherPreferences:108–114.
- **Факт/причина:** координаты/units читаются до сети, key — перед записью. Forced refresh при busy просто возвращается.
- **Влияние:** старый прогноз получает новый key и может заменить имя нового города; неправильный cache переживает restart.
- **Исправление:** единый request snapshot, revision-checked commit относительно setters, latest pending refresh.
- **Риск:** лишний запрос при переключении; сохранить locale/place и stale fallback.
- **Проверка:** задержать A, выбрать B/другие units, завершить A; B и его cache key не заменяются A.

### AQ-10 — delete профиля имеет durable crash-window (S)
- **Где:** Profiles:78–89,319–329.
- **Факт/причина:** список сохраняется отдельно от layout/credential cleanup; максимальный удалённый ID затем может использоваться снова.
- **Влияние:** при аварии между durable-записями новый профиль способен унаследовать старые layout/PIN/allowlist. Это не утверждение, что любой apply теряет данные.
- **Исправление:** не переиспользовать identity, обеспечить orphan/tombstone cleanup и согласованное восстановление.
- **Риск:** нельзя переименовывать существующие IDs или менять файл p1 без миграции.
- **Проверка:** восстановить каждое промежуточное durable-состояние delete, затем add; чужих данных/credentials нет.

### AQ-11 — malformed credentials не дают управляемого отказа (R)
- **Где:** KidsCrypto.matches/pbkdf2, KidsMode:235–255; PinPad submit использует finally без обработки этих ошибок.
- **Факт/причина:** invalid Base64 и iterations≤0 бросают исключение; algorithm tag не проверяется как поддерживаемый.
- **Влияние:** повреждённый credential выходит из UI coroutine как необработанная ошибка. JVM helper исключения и принятие неподдерживаемого tag воспроизведены; Android crash не запускался.
- **Исправление:** валидировать поддерживаемый format/salt/iterations/digest, fail closed с понятным состоянием ошибки.
- **Риск:** не ломать существующий PBKDF2/120000 и не удалять PIN автоматически.
- **Проверка:** malformed salt, zero/negative iterations, неизвестный algorithm, здоровый формат; нет допуска/необработанного exception.

### AQ-12 — совместные настройки создают пустой saver (R)
- **Где:** SaverSettings.toggleTheme/toggleGroup, SaverPreferences:54–79; SettingsScreen:1023–1027; SaverPlaylist:14–22.
- **Факт/причина:** запрет последней темы не учитывает hidden-clips. Скрыть все A при видимой B, затем отключить B — допустимо.
- **Влияние:** themes не пусты, но playlist пуст; delay включён, собственная заставка не запускается.
- **Исправление:** проверять итоговый playlist при изменении обеих настроек.
- **Риск:** не подменять отсутствующий catalogue выдуманным контентом; восстановление ранее скрытых клипов сохранить.
- **Проверка:** A скрыта, B последняя видимая; отключение B отклоняется. Текущие pure-функции воспроизвели пустой playlist на JVM.

### AQ-13 — package receiver удерживает старый reload (S)
- **Где:** LauncherRoot:349–376.
- **Факт/причина:** DisposableEffect(context, repository) не обновляется с orderStore, а reload closure его захватывает.
- **Влияние:** после switch callback работает с неверным владельцем и может отменить актуальную загрузку через общий reloadJob.
- **Исправление:** rememberUpdatedState для callback либо owner-key эффекта; generation/owner у результата.
- **Риск:** не создать gap/дубли receiver и повторные загрузки.
- **Проверка:** switch → package event, в том числе при pending reload. Protected broadcast delivery отдельно проверяется установкой/обновлением, не spoofed intent.

### AQ-14 — старые items сохраняются в новый профиль (S)
- **Где:** SettingsScreen:440–470,775–779.
- **Факт/причина:** items не keyed by owner; новый orderStore появляется до завершения нового load, restoreHidden пишет текущие старые items.
- **Влияние:** прежний layout способен попасть в новый профиль при действии в этом окне.
- **Исправление:** owner-scoped loaded state, mutation разрешена только при совпадении владельца результата.
- **Риск:** не считать loading пустым пользовательским layout и не сохранять его как reset.
- **Проверка:** задержанная загрузка после switch и попытка restoreHidden; старый layout не записан в новый store.

### AQ-15 — thermal trim не изменяет capacity (S)
- **Где:** AppRepository:57–60; AerialPosterStore:99–100; SaverPreviewImage:73–75; TopShelf:405–408.
- **Факт/причина:** trimToSize уменьшает текущий size, но оставляет прежний maxSize.
- **Влияние:** refill возвращается к старому бюджету, несмотря на LOW profile. Actual PSS этого сценария не измерялся.
- **Исправление:** уменьшать maxSize через resize с согласованной инвалидацией/профильной политикой.
- **Риск:** чаще reload; охлаждение не должно повышать capacity выше аппаратного лимита.
- **Проверка:** HIGH→LOW→refill, assert capacity/size; затем профильный memory/scroll прогон.

### AQ-16 — отмена Job не прекращает HTTP (S)
- **Где:** CitySearch.download:82–102; аналогичный контракт других URLConnection-readers.
- **Факт/причина:** socket не привязан к Job; read timeout ограничивает паузу между данными, не общее время ответа.
- **Влияние:** старые запросы занимают worker/сеть; отменённая загрузка способна продолжить file write. Реальное число запросов не измерено.
- **Исправление:** cancellation-driven disconnect, общий deadline и cancellation check перед commit.
- **Риск:** медленные, но корректные ответы могут прекращаться; failure/stale поведение сохранить.
- **Проверка:** controlled slow-drip сервер, серия отмен, отсутствие записи старого результата; Main не блокируется.

### AQ-17 — temp/replace не гарантируют сохранение catalogue (S)
- **Где:** AerialStore.save:51–58, AerialRefresh:88–118; AerialPreviewStore.save:141–153.
- **Факт/причина:** общий .tmp допускает столкновение writers; после неудачного rename target удаляется, последний rename не проверен.
- **Влияние:** актуальный файл может исчезнуть, а refresh вернуть UPDATED. На устройстве rename failure не моделировался.
- **Исправление:** сериализация update, проверяемый atomic replace/AtomicFile; уникальные temp для cache writes.
- **Риск:** прежний ложный успех станет FAILED; сохранить пригодный старый файл при сбое.
- **Проверка:** два writers, rename failure/ENOSPC/отмена; old file цел, статус соответствует durable результату.

### AQ-18 — Nominatim reader не ограничивает байты (S)
- **Где:** Weather.localize:626–638.
- **Факт/причина:** inputStream.readBytes обходит bounded reader основной сети.
- **Влияние:** большой/непрерывный ответ способен расходовать память до общей ошибки; OOM не воспроизведён.
- **Исправление:** существующий ограниченный reader и общий deadline.
- **Риск:** oversized ответ отклоняется, локализация должна оставить fallback имя.
- **Проверка:** oversized chunked response, timeout и нормальный ответ; память/время ограничены.

### AQ-19 — Cursor traversal не защищён (S)
- **Где:** WatchNext.query:166–205.
- **Факт/причина:** защищено создание Cursor, но не moveToNext/get*; весь dataset собирается до ranking/limit.
- **Влияние:** provider exception выходит из reader; большой dataset увеличивает allocations. Crash на устройстве не запускался.
- **Исправление:** защитить traversal с корректным close, ранний package filter и bounded ranking.
- **Риск:** fallback вместо частичного списка; не потерять ранжирование/continue semantics.
- **Проверка:** Cursor exception, отмена query, большой dataset, нормальные Watch Next/Preview rows.

### AQ-20 — Settings preview не lifecycle-scoped (S)
- **Где:** previewClip state SettingsScreen:544; SaverScreen cleanup:535–557.
- **Факт/причина:** stop/release привязаны к disposal; paused/STOPPED Activity может сохранить композицию и preview.
- **Влияние:** риск ненужного playback, сети и decoder ownership в фоне; реальный расход/звук не измерен.
- **Исправление:** явный lifecycle STOP/foreground контракт для preview, согласованный resume.
- **Риск:** холодная подготовка после возврата; удержать корректный still и clip selection.
- **Проверка:** preview → HOME/другая Activity → возврат; счётчики players/network/Surface и продолжение без потери фокуса.

### AQ-21 — weather-loop переживает pause (S)
- **Где:** HomeScreen:373–385.
- **Факт/причина:** ключ эффекта resumeTick, не foreground; coroutine существует до disposal.
- **Влияние:** риск фоновых refresh/offline retries и обновлений невидимого UI. Это не измеренный ANR или battery drain.
- **Исправление:** определить foreground/prefetch политику и явно привязать работу к ней.
- **Риск:** прогноз при возврате может временно быть stale; осознанный background refresh нельзя удалить молча.
- **Проверка:** pause и продвижение времени, число refresh; resume обновляет нужный прогноз без дублей.

### AQ-22 — modal state не входит в focus ownership (S)
- **Где:** SettingsScreen focus-effect:2146–2235; PIN/keyboard/editor surfaces ниже в композиции.
- **Факт/причина:** изменения rows/grid запускают requestFocus фоновой поверхности без modal guard.
- **Влияние:** риск перехвата focus при late load и непредсказуемого возврата после editor; runtime-срабатывание не установлено.
- **Исправление:** modal-state в guard/keys, явный requester возврата после закрытия.
- **Риск:** нельзя задержать законное восстановление focus или лишить доступности пустую страницу.
- **Проверка:** late catalogue при PIN/keyboard; Cancel/Done без изменения данных; один активный focus-owner.

### AQ-23 — wizard buttons не имеют accessibility action (S)
- **Где:** SetupButton:1701–1734.
- **Факт/причина:** Role.Button объявлен, semantics onClick отсутствует; действие есть только в key-handler.
- **Влияние:** semantic activation не продолжает/не завершает мастер. Полный голосовой TalkBack-проход не выполнялся.
- **Исправление:** тот же callback для remote и semantics, без изменения визуального компонента.
- **Риск:** не допустить двойной activation на key event.
- **Проверка:** semantic click Back/Continue/Finish, затем обычный D-pad и повтор OK.

### AQ-24 — результаты city-query не имеют владельца query (S)
- **Где:** SetupScreen:1135–1159, выбор places в result handler.
- **Факт/причина:** после ввода нового query старый found остаётся на debounce/network interval.
- **Влияние:** можно сохранить город предыдущего поиска, пока UI уже показывает новый запрос.
- **Исправление:** связать found с query или запретить выбор устаревших результатов.
- **Риск:** не убрать полезные presets/результаты после нормального успешного запроса.
- **Проверка:** A→B с задержанным B и попыткой OK; выбрать A как результат B нельзя.

### AQ-25 — Settings сохраняет старый language context (S)
- **Где:** SettingsScreen:425,1867–1871; SettingsActivity.onResume:61–66.
- **Факт/причина:** Setup меняет язык, но возвращающаяся Settings не проверяет изменение wrapped context; language remembered при первом входе.
- **Влияние:** подписи/выбранное radio-state могут не соответствовать актуальным prefs.
- **Исправление:** проверка языка на resume и корректное recreate/обновление состояния, как на других entry Activity.
- **Риск:** back stack и незавершённые editors; не потерять введённое имя/выбранный профиль.
- **Проверка:** Settings→Setup→смена RU/EN→возврат; текст, выбранная строка и сохранённая настройка согласованы.

### AQ-26 — semantic activation отличается от remote (S)
- **Где:** ControlCenter:695 и 820.
- **Факт/причина:** remote учитывает tile.closes, semantics всегда вызывает закрывающий activate.
- **Влияние:** Wi-Fi/timer panel закрывается вместо сохранения поверхности/статуса, в том числе при permission callback.
- **Исправление:** единая ветка activation с соблюдением closes.
- **Риск:** сохранить closeReturnsFocus и не изменить поведение Power/системных переходов.
- **Проверка:** semantic click и remote на Wi-Fi/timer/Power дают одинаковые side effects и open/closed state.

### AQ-27 — delayed startup не подтверждает актуальную политику (S)
- **Где:** StartupPreferences:96–113,126–129.
- **Факт/причина:** callback не хранит тип boot/wake события и не перепроверяет on_wake/текущий Kids allowlist перед launch.
- **Влияние:** принятый ранее wake способен запустить приложение после выключения настройки; глобальный target не учитывает профильную политику.
- **Исправление:** перед fire проверить актуальные условия для конкретного event reason и выбранного target.
- **Риск:** нельзя запретить законный boot только потому, что on_wake=false; policy startup относительно Kids должна быть явной.
- **Проверка:** отключить wake до callback, сменить target/profile/allowlist; boot и wake тестируются раздельно.

## C3. Low и поддерживаемость

### AQ-28 — repaired folder-ID не переживает новое чтение (R)
- **Где:** HomeOrderStore:36–39,83–105,145–150.
- **Факт/причина:** UUID исправляется, но covers-all-apps запрещает save; следующий parse создаёт другой UUID.
- **Влияние:** нестабильный Compose-key и persisted focus при повреждённом ID. Android focus-сценарий не запускался.
- **Исправление:** persist структурного repair отдельно от добавления пакетов.
- **Риск:** не переписывать здоровый order при обычном reload/локализации.
- **Проверка:** missing/duplicate ID, два load; identity стабильна. Pure-контрпример воспроизведён.

### AQ-29 — Up на finish переводит курсор в пустую область (S)
- **Где:** SetupScreen.moveUp/activate:679–697.
- **Факт/причина:** rows=0, но inButtons сбрасывается и focus становится −1.
- **Влияние:** следующий OK не завершает мастер; Down восстанавливает кнопки, это не постоянный crash/hang.
- **Исправление:** при пустых rows сохранять button focus.
- **Риск:** не изменить обычную навигацию страниц с rows.
- **Проверка:** finish→Up→OK, границы Back/Right/Down и нормальные страницы.

### AQ-30 — permanent denial не имеет специального recovery path (S)
- **Где:** SetupScreen:347–352,374–385; SettingsScreen:600–609 и permission rows.
- **Факт/причина:** callback перечитывает grant, но не различает rationale/permanent denial; строка повторно запрашивает отклонённое разрешение.
- **Влияние:** пользователь не получает понятного пути восстановления. Автоматический request-loop не найден.
- **Исправление:** объяснение в существующем UI и переход в App info после permanent denial.
- **Риск:** не открывать system screen автоматически и не запрашивать неиспользуемые права.
- **Проверка:** повторный denial, permanent denial, approximate-only и последующий revocation.

### AQ-31 — распределённые ownership-инварианты UI (S)
- **Где:** SettingsScreen (3926 строк), HomeScreen (1938), SetupScreen (1746), LauncherRoot (744); конкретные примеры — AQ-02/09/13/14/22.
- **Факт/причина:** navigation state, профиль, авторизация, загрузки и записи меняются в разных closures/эффектах без одного operation-owner контракта.
- **Влияние:** расширение очередного раздела требует согласования нескольких state/side-effect мест; ошибки ownership уже установлены. Сам размер файла не является High-дефектом.
- **Исправление:** выделять конкретные owner-scoped state/operations и pure rules по мере исправления; modules/ViewModel/DI не вводить автоматически.
- **Риск:** перенос remember/lifecycle может изменить scope, cancellation, focus и persistence.
- **Проверка:** invariant tests до extraction, ограниченный diff, прежние сценарии и runtime поведение без визуальных изменений.

### AQ-32 — startup не разрешает leanback-only приложение (S+M)
- **Где:** StartupPreferences:126–129; SettingsScreen:1807–1823.
- **Факт/причина:** picker использует TV catalogue, а launch только getLaunchIntentForPackage. На стенде system TV Settings не имеет MAIN+LAUNCHER/INFO, но имеет LEANBACK.
- **Влияние:** разрешённый выбор может не запуститься. Измерены категории, само выполнение startup не запускалось.
- **Исправление:** leanback fallback либо валидированный component из catalogue; policy gate проверить до запуска.
- **Риск:** не получить двойной launch и не открывать недоступный/неэкспортируемый component.
- **Проверка:** resolver classic=null, leanback=valid; classic app, disabled/missing app, запрещённый Kids target; без запуска реального контента в тестах.

# D. Производительность и стабильность

## D1. Что подтверждено

- **Main-thread blocking path (S):** setContent/composition → remember → AerialCatalog.load → file/asset read + JSON parse; key/row callback → clearCaches → deleteRecursively. Это реальные I/O вызовы без dispatcher, **не измеренный ANR**; длительность на slow storage неизвестна.
- **Раздувание plist (R/M):** 66/82/98 байт при depth 6/10/14 дали 63/1023/16383 array nodes. Измерено число материализованных узлов, не время freeze на TV.
- **Дефекты чистой логики (R):** kids=false после blank-name repair; исключения malformed KDF/принятие чужого tag; непостоянный repaired folder-ID; пустой playlist после допустимых переключений.
- **Fresh verification (M):** 287 host-тестов на debug и 287 на release без failures/errors/skipped, lint без Error. Подробнее G.
- **TV категории (M):** read-only PackageManager query обнаружил leanback-only system TV Settings. Контент и startup target не запускались.

## D2. Потенциальные риски, не выданные за измерения

1. У LRU есть отдельные бюджеты, но это не общий heap ceiling. Номинальная сумма poster/preview/thumb/app-art — HIGH 66 MiB, BALANCED 54 MiB, LOW 35 MiB; shelf entry-cache, wide banners, remembered stills, players и GPU стоят отдельно.
2. Refill после thermal downgrade, concurrency загрузок, декодирование oversized image и retained UI state могут увеличить peak memory. Утечка/PSS этого сценария не доказаны.
3. Coroutine cancellation без socket disconnect/deadline оставляет resource work; эффект на actual concurrency/CPU требует slow-network измерения.
4. STOPPED composition не обязательно disposed. Плееры, weather-loop и cleanup, зависящий только от Compose-state transition, надо проверить на реальном lifecycle, а не только сменой bool в активном test host.
5. Для 24/7: непрерывный Home не проверяет catalogue refresh по background schedule; проверка происходит на foreground/resume. Соответствие ожиданию Download Frequency требует явной политики, не автоматического нового scheduler.
6. Startup boot/wake dedup process-local; process death в boot-window, смена профиля и policy до delayed callback требуют отдельного теста.
7. Нет измерения recomposition/overdraw/decoder count/heap retention после длительного использования. Нельзя объявлять каждую lambda или большой Composable источником 60 fps recomposition.

Предыдущий release-обход 0.4.44 измерил RUNNING_CRITICAL PSS 71 264→66 888 КиБ и пустой crash-буфер. Это другой, сохранённый сценарий после мастера/смены языка; в данном аудите он не повторялся и не доказывает отсутствие memory-регрессии или leak.

## D3. Что нужно измерить

- Cold/warm start и frame percentiles на реальном TV; отдельно первый запуск с catalogue/cache.
- Profile switch и package event при задержанной загрузке; ownership и active jobs.
- LOW/thermal downgrade→refill, размеры bitmap и peak heap/PSS; не только size LRU.
- Settings preview→STOP→resume, players/codec/network/Surface count.
- Offline/slow-drip/ошибки provider и file replace; время отмены и сохранность старых данных.
- Soak с повторяемым сценарием, включая длительный Home/saver и process death/restart.
- Реальные API 28/29/35, 4K raster, fontScale, TV remote repeats и TalkBack.

## D4. Отклонённые ложные срабатывания

- delay, coroutine join/withContext — suspension, не Main blocking.
- Основные PBKDF2/image/network/Wallpaper/DeviceFacts paths выполняются на Default/IO; им не приписан Main ANR.
- Off-main TextureView.bitmap getter найден, но attached View mutation/invalidate/layout не доказаны; **Sync Barrier Leak ANR не объявлен**.
- SharedPreferences.apply — не автоматическая потеря данных; отдельный crash-window AQ-10 требует нескольких файлов и reuse ID.
- Missing DYNAMIC_RECEIVER permission — ложная находка: signature permission/uses-permission добавлены AndroidX merger для pre-33.
- SCREEN_ON/package events защищены платформой; отсутствие ручного sender extra не доказывает spoofing.
- Sleep PendingIntent явный/immutable, receiver приватный; accessibility/admin защищены BIND permissions. Пользовательского AIDL/Binder/Messenger интерфейса не найдено.
- Tar не распаковывает произвольные пути на filesystem; обычный tar path traversal не установлен.
- media3-ui используется PlayerView/XML; отсутствие ViewModel/Room/DI и один Gradle-модуль сами по себе не дефекты.
- Машинный SDK path в local.properties игнорируется Git: персональный путь не объявлен архитектурной ошибкой. Предупреждение aapt2 учитывается в G.

# E. Технический долг

## Необходимо исправить

- Все High: граница Settings/permission gate, правильный владелец PIN, безопасный repair, доверенное обновление и bounded parsers/bitmaps.
- Ownership запросов и результатов, корректные file commits и interruption recovery, error boundaries провайдера/credentials.
- Конкретное Main I/O и lifetime фоновых ресурсов; одинаковая remote/semantic activation.
- Пробелы в тестах именно на выявленные условия: happy path и количество зелёных тестов не заменяют их.

## Желательно улучшить

- Изолировать небольшие profile/auth/load state transitions при работе над соответствующими багами.
- Согласовать foreground/prefetch/startup/download policies и закрепить их тестами.
- Поддерживать release/R8, API matrix и resource-budget regression checks; оценивать dependency updates поштучно.
- Автоматизировать проверку подписи/версии/тестов на distributable artifact. Обновление зависимости из lint warning не объявляется устранением известной CVE без advisory.
- Небольшие dead/duplicate constants и comments корректировать только после проверки usages; не начинать массовую cosmetic cleanup.

## Лучше не трогать без отдельного обоснования

- applicationId/package/signing и существующие форматы пользовательских данных.
- Дизайн, геометрию, токены, шрифты и отлаженную калибровку D-pad/repeats.
- Декодерную пару/текстурный вход saver только ради уменьшения числа объектов: сначала реальные измерения и сохранение первого кадра.
- Переписывание проекта, обязательное внедрение Hilt/Room/Navigation/ViewModel или разбиение на множество модулей.
- Новые функции темы/персонализации/backup, если задача — исправить существующее поведение.

# F. Поэтапный план улучшений

**План. Этапы 1–5 реализованы в доступном объёме в 0.4.45–0.4.48 (см. «Статус реализации» выше); аппаратная/API-матрица — NOT TESTED.** Каждый следующий пункт — небольшой отдельный diff с тестом; крупный рефакторинг и новые зависимости не предполагаются.

Общий gate микроэтапа: зафиксирован исходный working tree/backup → воспроизведение/RED → минимальное изменение → GREEN → полная регрессия → проверенный подписанный APK и установка на изолированный стенд → журнал/артефакты. Не использовать reset/удаление пользовательских данных или отключение тестов для получения зелёного результата.

## Этап 1 — серьёзные ошибки и стабильность

- **1A. Авторизация:** AQ-01/02/03 — entry trust boundary, origin профиль, permission route.
- **1B. Защита данных:** AQ-04/11 — repair с сохранением kids, корректный отказ malformed credentials.
- **1C. Внешние данные:** AQ-05/06/07 — подлинность catalogue, bounded plist и bitmap.
- **Зависимости:** 1A требует выбора единственного Settings gate/внутреннего grant; 1C требует политики доверенного обновления при недоступном TLS. Подгруппы могут исследоваться независимо, но общие файлы не редактируются двумя агентами.
- **Готовность:** все семь High закрыты проверяемыми тестами; нет допуска по внешнему extra/чужому PIN; old credentials/valid catalogue/assets совместимы; malformed input не вызывает неограниченную работу.
- **Риски:** focus/Cancel seam, двойной PIN, доступность Apple update, слишком строгие limits.

## Этап 2 — ownership и архитектурные границы

- AQ-09/10/13/14/17: request snapshot/commit, identity/delete recovery, актуальный reload, owner-scoped items, atomic catalogue writes.
- AQ-31: выделять небольшие операции профиля/авторизации/загрузки только там, где исправляются эти инварианты; не дробить все UI-классы ради размера.
- **Зависимости:** после стабилизации trust/format из этапа 1; схему delete/identity согласовать до миграции. Existing IDs и p1 layout file сохраняются.
- **Готовность:** задержанный A не записывается как B; crash-window не приводит к reuse чужих данных; package event после switch обновляет нужный owner; replace failure не возвращает UPDATED.
- **Риски:** изменение scope/lifetime при extraction, потеря layout/навигации, дополнительные reloads.

## Этап 3 — производительность и lifecycle

- AQ-08/15/16/18/19/20/21: Main I/O, capacity LRU, HTTP deadline/cancellation, bounded readers/provider traversal, preview и weather lifetime.
- AQ-27/32: актуальная startup policy и TV launch resolution.
- Сначала снять baseline на одинаковом сценарии; измерить реальные bottlenecks, не оптимизировать по количеству lambdas.
- **Зависимости:** owner/revision и durable commit этапа 2; resource limits этапа 1.
- **Готовность:** file work не блокирует Main; capacity после downgrade остаётся ограниченной при refill; STOP не оставляет нежелательные resources; cancelled request не commit-ит; leanback-only/missing/disabled targets обрабатываются.
- **Риски:** cold reload/первый кадр, reconnect latency, устаревший прогноз при resume. Порог сравнения с baseline фиксируется до изменения с учётом шума стенда.

## Этап 4 — качество кода и UX-контракты без редизайна

- AQ-22–26,28–30: modal focus, semantics, query ownership, language resume, стабильный repair, finish cursor и permission recovery.
- Проверить мелкие unused/duplicate элементы по usages; сохранить общие компоненты и калибровку.
- **Зависимости:** новые operation owners/lifecycle из этапов 2–3; отдельные pure fixes можно делать раньше, не пересекая файлы.
- **Готовность:** remote и semantics одинаковы; старый city query не выбирается как новый; RU/EN и back/focus согласованы; healthy persistence не переписывается.
- **Риски:** двойной activation, потеря pending editor input, изменение скорости привычной навигации.

## Этап 5 — подготовка к расширению и release discipline

- Закрепить owner/schema/resource contracts в тестах; расширить release/R8, API28/29/35 и реальный TV/LOW/4K/TalkBack/soak coverage.
- Согласовать CI/release artifact checks, dependency update policy и сохранение доказательств; не создавать Git заново — Kotlin уже имеет Git-корень.
- Новые функции backup/темы/контентный поиск — отдельные продуктовые решения. Для backup сначала определить типизированную схему, transaction/rollback и границы данных.
- **Зависимости:** нет незакрытых High; результаты измерений и регрессии предшествующих этапов.
- **Готовность:** расширение не требует обхода trust boundary/профильной схемы; поддерживаемая API/device matrix имеет явные PASS/NOT TESTED, distributable APK проверен по version/certificate.
- **Риски:** слишком широкая модернизация SDK/dependencies или перенос функций без совместимости данных.

## Распределение между субагентами при реализации

1. **Auth/UI:** Settings entry/exit и системные переходы; эксклюзивные Activity/Settings/ControlCenter файлы.
2. **Data:** Profiles/Kids/storage, fixtures и migration/repair проверки.
3. **Network/resources:** catalogue/plist/images/HTTP; отдельные файлы и bounded fault tests.
4. **Validator:** read-only review, сравнение артефакта/подписи и тестов; не исправляет файлы параллельно авторам.

Главный агент задаёт понятный scope, проверяет реальные diff/callers, объединяет результат и выполняет gate. При пересечении одного файла работа последовательная; нельзя считать отчёт субагента доказательством реализованного исправления.

# G. Результаты проверок

## G1. Выполнено в этом аудите

### Host tests и lint

```bash
GRADLE_USER_HOME=C:/gradle-home ./gradlew :app:testDebugUnitTest :app:testReleaseUnitTest :app:lintDebug --console=plain --rerun-tasks
```

**BUILD SUCCESSFUL**, exit 0, **1 мин 39 с**, 59 задач выполнены. На каждом варианте 287 tests, 0 failures/errors/skipped, 44 JUnit XML. В дереве 43 JVM test-файла; XML count не является числом уникальных production-сценариев.

Lint: **0 Error / 14 Warning / 5 Information**. Типы: GradleDependency 9, DiscouragedApi 3, UseOfNonLambdaOffsetOverload 2, AutoboxingStateCreation 5. Предупреждения не скрывались и не объявляются доказанными runtime дефектами.

Артефакты: [gradle-unit-lint.log](file:///C:/OpenCode%20Project/OVisionTV/OVisionTV_Kotlin/ARCHITECTURE_AUDIT/evidence/gradle-unit-lint.log), [lint-results-debug.xml](file:///C:/OpenCode%20Project/OVisionTV/OVisionTV_Kotlin/ARCHITECTURE_AUDIT/evidence/lint-results-debug.xml), каталоги `evidence/unit-debug` и `evidence/unit-release`.

### Безопасные JVM-контрпримеры

[AuditProbe.java](file:///C:/OpenCode%20Project/OVisionTV/OVisionTV_Kotlin/ARCHITECTURE_AUDIT/evidence/AuditProbe.java) вызывает текущие compiled Kotlin classes. Это временный JVM-harness на Java, **не аудит отдельной Java-версии приложения**; новые библиотеки не добавлялись.

Запуск: Java с `-Xmx128m`, UTF-8, существующими debug classes/kotlin-stdlib/org.json/coroutines JAR. Точный массив выполненных аргументов — [host-probes-command.json](file:///C:/OpenCode%20Project/OVisionTV/OVisionTV_Kotlin/ARCHITECTURE_AUDIT/evidence/host-probes-command.json). Exit 0.

| Группа | Фактический результат |
|---|---|
| Profiles | healthy name сохраняет kids=true, blank-name repair даёт kids=false |
| Credentials | malformed salt и zero iterations бросают IllegalArgumentException; неподдерживаемый tag с рассчитанным digest принимается |
| Folder repair | covers=true при missing ID, два parse получают разные UUID |
| Saver | hidden A + выключение B дают nonempty themes и empty playable list |
| Plist | depth6/66 B→63 arrays; depth10/82 B→1023; depth14/98 B→16383 |

Лог — [host-probes.log](file:///C:/OpenCode%20Project/OVisionTV/OVisionTV_Kotlin/ARCHITECTURE_AUDIT/evidence/host-probes.log). Probe проверяет наличие контрпримеров на неисправленном коде, не является свидетельством готового исправления. Не выполнялись сеть, Android prefs writes, depth32/OOM payload или вмешательство в GUI.

### Read-only проверка TV launch categories

Выполнены команды через `C:/Android/sdk/platform-tools/adb.exe -s emulator-5554`:

```text
shell cmd package query-activities --components -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER
shell cmd package query-activities --components -a android.intent.action.MAIN -c android.intent.category.LAUNCHER
shell cmd package query-activities --components -a android.intent.action.MAIN -c android.intent.category.INFO
```

Наблюдение: `com.android.tv.settings` есть в leanback, отсутствует в launcher/info. Это доказывает категорию установленного приложения, не выполненный startup launch. [launch-category-query.txt](file:///C:/OpenCode%20Project/OVisionTV/OVisionTV_Kotlin/ARCHITECTURE_AUDIT/evidence/launch-category-query.txt).

### Статические и integrity проверки

- Полное чтение production Kotlin, проверка вызывающих мест/dispatchers и merged component boundaries.
- Проверены imports/использование технологий, зависимости, тестовые annotations и фактический Git-корень.
- @Ignore/@Disabled в production test scope не обнаружены; есть два assumption-gated UI сценария. Сохранённый последний UI-прогон имел 0 skipped — это не гарантия отсутствия пропусков на другом стенде.
- SHA-256 baseline: **168 Kotlin source/config text files unchanged**. Новый report/harness/evidence не входят в production-код.
- Машинный local.properties игнорируется Git. Fresh aapt2 предупреждает об отсутствующем android-35/android.jar по локальному пути; сборка успешно завершена. Это зарегистрированная особенность окружения, не доказанная архитектурная поломка.

## G2. Сохранённые проверки того же релиза, не перезапущенные в аудите

Предыдущий полный прогон 0.4.44: 287 debug + 287 release + **194 UI**, 0 failures/errors/skipped; assembleRelease, **BUILD SUCCESSFUL**, exit 0, 9 мин 8 с. Тестовый стенд — hume-tv API34, accessibility выключена.

Свежий аудит сравнил [175-file snapshot](file:///C:/OpenCode%20Project/OVisionTV/OVisionTV_Kotlin/UI_UX_UNIFICATION/evidence-0.4.44/verified-0.4.44-source-hashes.json) с текущим деревом: source drift=0. [Предыдущий полный лог](file:///C:/OpenCode%20Project/OVisionTV/OVisionTV_Kotlin/UI_UX_UNIFICATION/evidence-0.4.44/full-gradle.log) сохранён, но не называется новым UI-прогоном этого аудита.

Подписанный артефакт этого выпуска: **4 077 702 B**, SHA-256 `f8dbd5239859b3139540cf592292c38a268458d19c50236e54af30ab7e3796ba`, v2, сертификат `327a50453093b783e84b1f2645b03319477d89966ea6a653dab53d5a567d7b10`. ApplicationId и подпись не менялись; пересборки нового APK в этом аудите нет.

Функциональный PASS прежнего релиза не является security/production sign-off: новые условия из C не покрывались тем happy-path набором.

## G3. Что не выполнено и почему

- Внешний Intent/PIN bypass, profile-switch race и malformed credentials в реальном UI: не выполнялись изменения профилей/прав и не вмешивались в работающий GUI. Их доказательства — S либо bounded JVM R, не device exploitation/crash.
- Реальные ANR/OOM/heap leaks и sync-barrier leak: trace/heap evidence не получен; искусственное исчерпание ресурсов не запускалось.
- Новый полный instrumentation/release smoke, compiler recomposition metrics, GPU/performance baseline и soak: в данном read-only аудите не выполнялись.
- Реальные TV/Google TV/LOW/4K, API28/29/35 и TalkBack: нет соответствующей законченной проверки; API34 emulator не подменяет эту матрицу.
- Concurrent network/file faults и crash-recovery intermediate states: описаны как обязательные проверки будущих исправлений, не помечены PASS.

## Итог и следующий шаг

Аудит завершает исследование и планирование, **не исправление 32 пунктов**. Минимальный следующий шаг — согласовать этап 1, начиная с авторизации/owner PIN/repair и bounded external data; затем выполнять отдельные изменения с RED→GREEN и независимым gate.


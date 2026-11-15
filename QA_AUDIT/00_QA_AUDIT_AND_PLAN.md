# 00 — QA, Device Compatibility & Automation Audit

**Проект:** OVisionTV Launcher (Kotlin)  
**Версия в дереве:** `0.5.0`, versionCode `73`  
**Пакет:** `com.whatuneed.ovisiontv.kotlin`  
**Дата аудита:** 2026-11-15  
**Режим:** аудит и план; G0 выполнен на AVD, production-изменения — только точечные исправления, найденные прогоном. Массовая реализация G1–G6 не начиналась.

> **Обновление после Stage 4 security remediation:** текущий прогон — **426 unit / 223 instrumented**, оба зелёные; `lintDebug` — 0 errors. Историческая таблица §G0 ниже сохраняет результат 219/219 на момент QA-прогона; новые instrumented-тесты проверяют удаление crash-диагностики и очистку данных погоды из Settings.

---

## 0. Короткий вердикт

**Сборка и доменные тесты — в хорошем состоянии. Готовность к заявленной совместимости — не подтверждена.**

Что проверено сейчас:

| Проверка | Результат |
|---|---|
| `:app:testDebugUnitTest` | **412 tests, 0 failures, 0 errors, 0 skipped** |
| `:app:assembleDebug` | **BUILD SUCCESSFUL** |
| `:app:assembleRelease`, `:app:bundleRelease` | **BUILD SUCCESSFUL**, R8 + resource shrink |
| `:app:compileDebugAndroidTestKotlin` | **BUILD SUCCESSFUL** |
| `:app:lintDebug` | **0 errors, 16 warnings** |
| Release APK | **4 244 946 B**, подпись проверена `apksigner verify` |
| Release AAB | **5 087 646 B** |
| `adb devices` | `emulator-5554 device` — AVD `hume-tv`, Android 14 (API 34), x86, 1920×1080 / 320 dpi |
| `connectedDebugAndroidTest` | **219/219 passed, 0 failed, 0 errors, 0 skipped**; 4 последовательных зелёных прогона после исправлений |

**G0 выполнен на эмуляторе 2026-11-15.** Исходный прогон дал 11 падений; после разбора и точечных исправлений — 4 зелёных прогона подряд. Это подтверждает текущий debug-набор на одном AVD, но **не** заменяет проверку на физическом ТВ, 4K, реальном пульте и слабом железе.

**Release readiness: условный NO-GO.** APK/AAB собираются и подписаны, debug-instrumentation стабилен на AVD, но нельзя утверждать совместимость с Android TV, 4K, реальным пультом и слабым железом без выполнения §B–§E на устройстве.

---

# A. Состояние QA

## A.1. Инвентаризация

| Показатель | Значение |
|---|---:|
| Production Kotlin-файлов | 91 |
| Production Kotlin-строк | 26 337 |
| Unit-тестовые файлы | 60 |
| Unit `@Test` | **412** |
| Instrumented-тестовые файлы | 31 (28 файлов с `@Test` + 3 helper-файла) |
| Instrumented `@Test` | **219** |
| Ручные эталонные PNG в `screenshots/` | 478 (≈168 MB) |
| Файлы доказательств в `UI_UX_UNIFICATION/evidence-*` | 375 |
| CI/CD | **нет** |
| Git remote | **не настроен** в рабочей копии |

Распределение unit-тестов:

| Пакет | `@Test` |
|---|---:|
| `data` | 276 |
| `ui` (включая `ui/components`) | 80 |
| `discovery` | 45 |
| `input` | 7 |
| root (`KidsEntryGrantTest`) | 4 |
| **Итого** | **412** |

Распределение instrumented-тестов:

| Пакет | `@Test` |
|---|---:|
| `ui` (включая `ui/components`) | 189 |
| `data` | 25 |
| `discovery` | 5 |
| **Итого** | **219** |

## A.2. Что уже есть

**Unit-тесты (JVM, без устройства):**

- ranking/engine/discovery: `DiscoveryRankingTest`, `DiscoveryEngineTest`, `DiscoveryStoreCodecTest`;
- persistence и восстановление: `ProfilesTest`, `HomeOrderTest`, `HomeFocusTest`, `SafePreferencesTest`, `SetupProgressTest`, `StorageContractTest`;
- input/focus-математика: `TvKeyBurstFilterTest`, `HomeFocusTest`, `CcFocusNavigationTest`, `LaunchGeometryTest`, `MotionDurationTest`, `TvCanvasFactorTest`;
- заставка: `SaverSettingsTest`, `SaverPlaylistTest`, `SaverScheduleTest`, `SaverRefreshScheduleTest`, `SaverOfflineBackoffTest`, `SaverPreviewBudgetTest`;
- сеть/погода: `WeatherTest`, `WeatherRequestTest`, `CitySearchTest`, `AerialRefreshUrlTest`, `WatchNextTest`;
- файлы/кэши/память: `AtomicWriteTest`, `ArtFailureCacheTest`, `CacheBudgetTest`, `MemoryPressureTest`, `AdaptivePolicyTest`;
- локализация/сеть/лицензия: `LocalizationTest`, `NetworkSecurityConfigTest`, `PermissionRecoveryTest`, `PermissionSummaryTest`.

**Instrumented-тесты (Compose/AndroidX, требуют устройства):**

- реальный `MainActivity`: `MainActivityInputTest`, `MainActivityResumeTest`, `LanguageReturnTest`, `LauncherReloadTest`;
- Compose-экраны: `HomeScreenTest` (36), `SettingsScreenTest` (49), `SetupScreenTest` (20), `SearchTest` (12), `ControlCenterTest` (9), `KidsGateTest` (11);
- компоненты: `TopShelfTest` (8), `SaverScreenTest` (9), `SaverPreviewTest` (6), `PinPadInputTest` (3), `TvKeyRepeatTest` (5), `WeatherPanelTest` (1);
- реальные SharedPreferences/PackageManager: `HomeOrderStoreTest` (7), `ProfileStoreTest` (4), `KidsStoreTest` (5), `AppRepositoryTest` (3), `LocalePreferenceTest` (3), `WallpaperTest` (3), `DiscoveryStoreTest` (5), `AerialPosterStoreTest` (1);
- смоук лицензии/текста: `LicensingTest`, `TvCanvasTest`.

**Инструменты, которые реально объявлены в Gradle:**

- JUnit 4 (`junit:junit:4.13.2`);
- `androidx.test.ext:junit:1.2.1`, `androidx.test:runner:1.6.2`, `androidx.test:rules:1.6.1`;
- Compose BOM `2024.09.03`, `ui-test-junit4`, `ui-test-manifest`;
- `org.json:json:20240303` для JVM-тестов;
- `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"`.

**Чего в Gradle нет:**

- `testOptions` (`animationsDisabled`, orchestrator, managed devices);
- явного Espresso/UI Automator;
- `macrobenchmark`-модуля и benchmark-зависимостей;
- screenshot/golden-библиотеки (Paparazzi/Roborazzi и т.п.);
- JaCoCo/Kover и вообще измерения line/branch coverage;
- CI-workflow, test-orchestrator, test-sharding.

## A.3. Что проверяется автоматически, что — вручную, что — никак

| Область | Автоматически | Вручную | Не покрыто |
|---|---|---|---|
| Доменная логика, парсеры, ranking | Да, 412 unit | — | — |
| Compose-экраны, фокус, D-pad | Да, 219 instrumented **скомпилированы** | Частично старые прогоны | Текущий прогон 0.5.0 |
| Реальный запуск внешнего приложения | Нет (`onLaunch` — stub) | Да | Автоматический end-to-end |
| Возврат из внешнего приложения | Частично `MainActivityResumeTest` | Да | Реальное стороннее приложение |
| Системные диалоги разрешений | Частично `assume`/fake | Да | Grant/deny/permanent deny |
| 4K/overscan/fontScale/RTL | Только формула `TvCanvasFactorTest` | Старые кадры 720p/4:3 | Реальный 4K, font 1.15/1.3, RTL |
| Реальный ИК/Bluetooth-пульт | Нет (только `KeyEvent`) | Частично | Автоповтор, vendor-keys |
| TalkBack-голос | Нет (есть semantics) | Отложено | Полный голосовой проход |
| Скриншоты/визуальная регрессия | **Нет** | 478 PNG без diff | Golden/diff-система |
| Производительность | **Нет** | Старые AVD-замеры 0.4.41 | Macrobenchmark, Perfetto, 0.5.0 |
| Soak/стресс/process death | **Нет** | Ручные сценарии | 24h, OOM, ANR, reboot |
| Обновление со старой версии | **Нет** | — | Миграция данных |
| Release/R8 smoke | Только сборка | — | Instrumented на release |

---

# B. Device Compatibility Matrix

## B.1. Платформы, API и производители

`minSdk = 28` (Android 9), `targetSdk = 35`, `compileSdk = 35`.  
**Android < 9 — Unsupported.** Всё ниже — только представители семейств; конкретные модели не проверялись.

| Устройство / семейство | Производитель | Android | Разрешение | Статус |
|---|---|---|---|---:|---|
| `hume-tv` AOSP TV x86 | AOSP TV | 14 (API 34) | 1920×1080 | **Emulator tested** — исторически (0.4.x); **0.5.0 не прогнан** |
| Sony Bravia / Google TV | Sony | 9–12 | 4K / 1080p | **Expected** |
| TCL Android TV / Google TV | TCL | 10–14 | 4K / 1080p | **Expected** |
| Xiaomi TV / Mi Box | Xiaomi | 9–11 | 1080p / 4K | **Expected** |
| Philips Android TV | Philips | 9–12 | 4K / 1080p | **Expected** |
| Hisense Android TV / Google TV | Hisense | 9–13 | 4K / 1080p | **Expected** |
| Generic AOSP TV box (ARM64) | OEM | 9–15 | 1080p / 4K | **Expected** |
| Generic AOSP TV box (ARM32) | OEM | 9–13 | 1080p | **Expected** — APK содержит `armeabi-v7a`, запуск не проверялся |
| Android 15 / API 35 TV | любой | 15 | 4K | **Expected** — target 35, устройства нет |
| Android 9 / API 28 | любой | 9 | 1080p | **Expected** — минимальная поддерживаемая версия |
| Android 8 и ниже | любой | ≤ 8 | — | **Unsupported** — `minSdk 28` |
| Телефон/планшет | любой | 9–15 | — | **Unsupported как продукт**; APK устанавливается для разработки, но UI не адаптирован под touch |

**Почему Android TV / Google TV / AOSP различаются:** код не использует Leanback UI, а сам объявляет `LEANBACK_LAUNCHER` и `HOME`; системный слой (Home override, TV provider, DreamService, разрешение `READ_TV_LISTINGS`) у Google TV и OEM-прошивок может вести себя по-разному. Это надо проверять отдельно, а не считать «Android TV» одной платформой.

## B.2. Разрешения, экран и плотность

| Сценарий | Код | Статус |
|---|---|---|
| 1280×720 | `TvCanvas` + `tvCanvasFactor` | **Partially tested** — старые AVD-кадры 0.4.41/0.4.42; 0.5.0 не снят |
| 1920×1080 | `tvCanvasFactor = 1` | **Emulator tested** — исторически API 34 |
| 3840×2160 (4K) | `tvCanvasFactor = 2` | **Partially tested** — только формула `TvCanvasFactorTest`; реальная растеризация **NOT TESTED** |
| 1600×1200 (4:3) | канва по меньшей оси | **Partially tested** — исторически, не 0.5.0 |
| 21:9 / 4:3 / нестандарт | `minOf(width/1920, height/1080)` | **Partially tested** — юнит-геометрия |
| Safe Areas / overscan | `WindowInsets.safeDrawing` + `TvOs.SAFE_AREA=90` | **Expected** — на реальном ТВ не проверялось |
| Плотность 160/320 dpi | dp-геометрия от `TvCanvas`, `nodpi`-баннер | **Partially tested** — 320 dpi AVD; 160 — только прокси |
| Системный `fontScale` 1.15/1.3 | используется `scale.sp(...)`, `maxLines`/ellipsis | **Expected / NOT TESTED** |
| RTL | `supportsRtl=true`, но только RU/EN | **Expected / NOT TESTED**; RTL-языки не поставляются |
| HDR / 10-bit панель | нет отдельного кода | **Expected / NOT TESTED** |

Важное ограничение: `TvCanvas` — это дизайн-канва 1920×1080, а не «нативная 4K-растеризация». На 4K она масштабирует те же dp/sp-значения; это надо проверять на панели, а не выдавать `tvCanvasFactor=2` за проверенный 4K.

## B.3. Аппаратная совместимость

| Фактор | Что известно | Статус |
|---|---|---|
| ABI | APK содержит `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`; нативная библиотека только `libandroidx.graphics.path.so` | **Partially tested** — x86 AVD; ARM не запускался |
| RAM | профили `HIGH/BALANCED/LOW` и byte-бюджеты кэшей покрыты unit-тестами | **Partially tested** — логика; LOW на 1 GB TV **NOT TESTED** |
| GPU | Compose/OpenGL ES; Vulkan не используется; GPU-blur сознательно отсутствует | **Expected** — аппаратный GPU не проверялся |
| Декодеры | Media3 ExoPlayer, H.264 1080p Aerial | **Expected** — 4K/HEVC/аппаратный декодер **NOT TESTED** |
| Хранилище | дисковые лимиты превью и каталога, `writeFileAtomically` | **Partially tested** — unit; полный диск/ENOSPC **NOT TESTED** |
| Температура/троттлинг | `AdaptivePolicy` + `ThermalService` listener | **Partially tested** — старый AVD `override-status`; реальный 30+ мин **NOT TESTED** |
| Пульт | `TvKeyBurstFilter`, `TvKeyRepeat`, `KeyEvent`-тесты | **Partially tested** — синтетика; ИК/Bluetooth **NOT TESTED** |

Что нужно измерить на реальном устройстве: cold/warm start, input→visual, PSS/RSS, frame time/janky, threads/FD, cacheDir, battery/network, поведение при 1 GB RAM и при 4K-панели.

---

# C. Test Coverage

## C.1. Покрытие по компонентам

Это **поведенческое** покрытие, а не процент строк: JaCoCo/Kover не настроены, поэтому численный line/branch coverage сейчас **не измерен**.

| Компонент | Unit | Instrumented | Итог |
|---|---|---|---|
| Ввод/D-pad burst/repeat | 7 `TvKeyBurstFilterTest` + `TvKeyRepeat` unit-часть | 5 `TvKeyRepeatTest`, 4 `MainActivityInputTest`, `PinPadInputTest` | **Сильное**, но instrumented не прогнан |
| Фокус/сетка/док | `HomeFocusTest`, `LaunchGeometryTest`, `TvCanvasFactorTest`, `CcFocusNavigationTest` | 36 `HomeScreenTest`, `TopShelfTest` | **Сильное** на уровне компонентов |
| Папки/меню/перемещение | `FolderEditTest`, `FolderPreviewTest` | `HomeScreenTest` (много сценариев), `MainActivityInputTest` | **Сильное** |
| Профили/PIN/kids | `ProfilesTest` (24), `KidsModeTest` (17), `KidsCredentialShapeTest`, `PinPadLayoutTest` | `ProfileStoreTest`, `KidsStoreTest`, `KidsGateTest`, `PinPadInputTest` | **Сильное**; визуальный PIN не снят |
| Discovery/полка/Watch Next | `DiscoveryEngineTest` (18), `DiscoveryRankingTest` (16), `DiscoveryStoreCodecTest` (11), `WatchNextTest` (8) | `DiscoveryStoreTest`, `TopShelfTest` | **Сильное**, реальный TV provider не проверен |
| Погода/сеть | `WeatherTest` (12), `CitySearchTest`, `AerialRefreshUrlTest` | `WeatherPanelTest` | **Среднее**: парсеры и тайминги; offline/DNS/timeout на устройстве не автоматизированы |
| Заставка/Aerial | 18 `SaverSettingsTest`, 11 `SaverPlaylistTest`, 8 `SaverGridScrollTest`, 5+3 schedule, 3 preview budget | 9 `SaverScreenTest`, 6 `SaverPreviewTest`, `AerialPosterStoreTest` | **Среднее–сильное**; реальный видеодекодер и 4K не проверены |
| Настройки | `KidsSettingsTest`, `SaverSettingsTest` | 49 `SettingsScreenTest`, 20 `SetupScreenTest` | **Сильное** по семантике; system dialogs/4K — нет |
| Сохранение/миграции | `SafePreferencesTest`, `ProfilesTest`, `HomeOrderTest`, `StorageContractTest`, codec caps | `ProfileStoreTest`, `HomeOrderStoreTest`, `LocalePreferenceTest` | **Среднее**; upgrade со старой версии не тестируется |
| Release/R8 | — | — | **Нет**; проверяется только сборка |
| Визуальная регрессия | — | 3 pixel-assert в `HomeScreenTest`/`SaverScreenTest` | **Почти нет**; 478 PNG не автоматизированы |
| Производительность | — | — | **Нет** |
| Стресс/soak/process death | — | — | **Нет** |

## C.2. Пробелы, которые важнее процента покрытия

1. **Instrumented-прогон выполнен только на одном AVD.** 219/219 зелёные, но физический ТВ, 4K, реальный пульт и API 28/35 не проверены.
2. **Нет теста реального запуска приложения.** `HomeScreenTest` подменяет `onLaunch`; `MainActivityInputTest` не открывает сторонний app. Ошибка `launchApp`/Intent не проверяется end-to-end.
3. **Нет теста реального возврата из стороннего приложения.** `MainActivityResumeTest` использует stop/start самой Activity.
4. **Нет теста системных permission-диалогов.** `GrantPermissionRule` выдаёт разрешение до теста, `assume` пропускает часть сценариев; отказ/permanent denial не автоматизирован.
5. **Нет теста обновления поверх старой версии.** Все проверки — на чистых prefs; старые ключи и миграции проверяются точечно в unit.
6. **Нет визуальной регрессии.** `captureToImage` используется только для нескольких пиксельных утверждений, а не для сравнения экранов.
7. **Нет performance-гейта.** Старые замеры 0.4.41 на AVD не относятся к 0.5.0 и не являются целью.
8. **Нет 4K, fontScale, RTL, TalkBack, ИК-пульта.** Это заявлено как `NOT TESTED` в старых документах; для 0.5.0 статус не изменился.

---

# D. Performance Results

## D.1. Что измерено, а что нет

Последние доступные числа — **0.4.41 на AVD API 34, 1920×1080, 320 dpi, SwiftShader** (`UI_UX_UNIFICATION/15_PERFORMANCE.md`). Это **не текущая 0.5.0** и **не реальный ТВ**. Ниже они приведены только как историческая база, а не как результат новой сборки.

| Метрика | Историческая база (0.4.41, AVD) | Предварительная цель | 0.5.0 |
|---|---:|---:|---|
| Cold start, медиана | 283 мс (272–293) | ≤ 2 000 мс на целевом ТВ (черновая) | **NOT MEASURED** |
| Warm start, медиана | 81 мс (62–96) | ≤ 500 мс | **NOT MEASURED** |
| Time to Interactive | не измерено | ≤ 2 500 мс | **NOT MEASURED** |
| Input-to-visual, P95 | нет прямого замера; frame P95 53 мс | ≤ 50 мс | **NOT MEASURED** |
| Frame time P95, 60 Гц | 53 мс | ≤ 16,67 мс | **NOT MEASURED** |
| Janky frames, 20× D-pad | 27,9 % | ≤ 5 % (черновая) | **NOT MEASURED** |
| Idle PSS | 42 669–52 081 КБ | без регрессии, в бюджете профиля | **NOT MEASURED** |
| `RUNNING_LOW`, PSS | −1 116 КБ | интерфейс не теряет контент | **NOT MEASURED** |
| Crash / ANR | 0 / 0 в старой сессии | 0 / 0 | **NOT MEASURED** |
| Release APK | 4 061 318 B (0.4.42) | — | **4 244 946 B (измерено)** |
| Release AAB | — | — | **5 087 646 B (измерено)** |

**Текущая 0.5.0:** измеримые артефакты сборки есть, runtime-метрик нет. `macrobenchmark`-модуля в проекте нет; Perfetto/gfxinfo/meminfo снимались вручную на старом стенде.

## D.2. Как правильно заполнить таблицу

Для приёмки нужен один и тот же целевой ТВ/бокс, 5 прогонов на метрику, медиана, одинаковая температура и набор приложений. Обязательные команды:

```bash
adb shell am force-stop com.whatuneed.ovisiontv.kotlin
adb shell am start -W -n com.whatuneed.ovisiontv.kotlin/.MainActivity
adb shell dumpsys meminfo com.whatuneed.ovisiontv.kotlin
adb shell dumpsys gfxinfo com.whatuneed.ovisiontv.kotlin reset
# D-pad сценарий
adb shell dumpsys gfxinfo com.whatuneed.ovisiontv.kotlin framestats
adb shell atrace -t 10 -b 16384 gfx input view sched freq
```

Без этих прогонов числа 0.4.41 нельзя переносить на 0.5.0 и тем более на ТВ.

---

# E. Обнаруженные проблемы

| ID | Проблема | Приоритет | Воспроизведение / доказательство | Решение |
|---|---|---|---|---|
| **QA-01** | ~~Нет успешного instrumented-прогона текущего дерева 0.5.0~~ **Закрыто на AVD** | **P0** | Исходно 11 failed; после исправлений 219/219 в 4 прогонах подряд, 0 skipped | Осталось повторить на физическом устройстве; см. §G0 |
| **QA-02** | Нет CI/CD; регрессия зависит от ручного запуска | **P1** | Нет `.github/workflows`, Git remote отсутствует | Ввести быстрый локальный/CI-гейт: build + lint + unit + release; instrumented — на self-hosted AVD |
| **QA-03** | Нет физического ТВ/бокса; все заявления о совместимости — Expected | **P0** | В `adb` только AVD `hume-tv`; реальных устройств нет | Матрица §B, реальные прогоны на 1–2 целевых устройствах |
| **QA-04** | Нет автоматической визуальной регрессии; 478 PNG не сравниваются | **P1** | В `androidTest` нет golden/diff; только 3 pixel-assert | Golden-снимки ключевых экранов на одном AVD + маски динамики |
| **QA-05** | Нет performance-гейта; 0.5.0 не измерена | **P1** | Нет `macrobenchmark`-модуля; старые числа 0.4.41 | Добавить отдельный benchmark-модуль; числа только с целевого устройства |
| **QA-06** | Instrumented-тесты зависят от установленных приложений и реальных prefs | **P1** | `MainActivityInputTest.firstApp`, `MainActivityResumeTest.catalog`, `AppRepositoryTest` используют `PackageManager`; `PrefsSnapshot` пишет в реальный target prefs | Детерминированные фикстуры/тестовый пакет; изоляция данных; документировать как smoke |
| **QA-07** | Таймауты и длинный `Thread.sleep` делают прогон медленным; остаточный риск flakiness | **P1** | `KidsGateTest` — `Thread.sleep(125_000)`; `animationsDisabled` уже включён; 4 зелёных прогона по ~5,3 мин | Заменить реальное ожидание инъекцией idle-часов; при подтверждении — orchestrator; не считать 4 прогона доказательством для всех устройств |
| **QA-08** | Нет тестов обновления с предыдущей версии и миграции данных | **P1** | Нет старого APK в тестах; только чистые prefs | Хранить предыдущий release APK; `adb install -r` + проверка профилей/порядка/настроек |
| **QA-09** | Нет instrumented-прогона release/R8 | **P1** | `connectedDebugAndroidTest` только debug; release проверен только сборкой | `testBuildType = "release"` или отдельный smoke на release |
| **QA-10** | 4K, fontScale, overscan, RTL не проверены на панели | **P1** | `TvCanvasFactorTest` — только формула; старые отчёты `NOT TESTED` | 4K-панель/бокс, `wm size`, `wm density`, `font_scale`; визуальная приёмка |
| **QA-11** | Нет реального ИК/Bluetooth-пульта и TalkBack-голоса | **P1** | Все ключи — `KeyEvent`/Compose `performKeyInput`; TalkBack отключён в тестах | Ручная матрица пультов + отдельный голосовой проход |
| **QA-12** | Нет измерения покрытия | **P2** | Нет JaCoCo/Kover/testCoverageEnabled | Добавить coverage только после стабилизации 219 тестов; не путать с качеством |
| **QA-13** | `assume` может скрывать критичные сценарии | **P2** | `ControlCenterTest` (admin), `SettingsScreenTest` (default Home), `KidsGateTest` (клипы) | Явно считать skipped в отчёте; отдельный прогон с подготовленными условиями |
| **QA-14** | Нет автоматического offline/DNS/timeout сценария | **P2** | `WeatherTest` проверяет парсеры/тайминги, но не сеть | MockWebServer или управляемый прокси на instrumented-стенде |
| **QA-15** | Нет end-to-end запуска внешнего приложения | **P2** | `HomeScreenTest`/`SearchTest` подменяют `onLaunch` | Тестовый пакет с launcher-activity + проверка возврата |
| **QA-16** | Нет теста permission deny/permanent deny | **P2** | `GrantPermissionRule` и `assume` | UI Automator для системных диалогов; отдельные grant/deny прогоны |
| **QA-17** | `stateNotNeeded=true`; process death не проверен | **P2** | `AndroidManifest.xml`; в отчётах `NOT TESTED` | Не менять флаг без device-теста; добавить `am kill` сценарий |
| **QA-18** | Нет проверки APK/AAB integrity в автоматическом гейте | **P2** | `apksigner`/`aapt` выполнены вручную | CI-шаг: `apksigner verify`, `aapt dump badging`, checksum |
| **QA-19** | Нет soak/ANR/OOM/reboot стресс-тестов | **P1** | Нет скриптов/тестов; только старые ручные сессии | `tools/qa` сценарии на устройстве, 24h/10 process death/trim |

---

# F. Automation Architecture

## F.1. Инструменты: что оставить, что добавить позже

| Слой | Сейчас | Решение |
|---|---|---|
| Domain unit | JUnit4 + `org.json` | **Оставить**; это сильная и быстрая база |
| Compose UI | `ui-test-junit4`, `createComposeRule`, `createAndroidComposeRule` | **Оставить**; не дублировать Espresso |
| Activity/intents | AndroidX Test runner/rules, lifecycle monitor | **Оставить**; добавить UI Automator только для системных диалогов |
| Изоляция | нет | **Добавить** `animationsDisabled=true`; orchestrator — после подтверждения |
| Performance | нет | **Отдельный** `:benchmark` (Macrobenchmark), не в `:app` |
| Visual | нет | **Начать** с Compose `captureToImage` + golden PNG; библиотеку добавлять только если понадобится JVM-прогон |
| CI | нет | **Начать** с локального скрипта и GitHub Actions при появлении remote |
| Coverage | нет | **Опционально** JaCoCo; не блокирует релиз, пока нет стабильного прогона |

Не добавлять одновременно Espresso, UI Automator, Robolectric, Paparazzi, Roborazzi и Macrobenchmark: это создаст несколько конкурирующих инфраструктур.

## F.2. Структура тестов

```text
app/src/test/                  # JVM, доменная логика, без Android
app/src/androidTest/           # Compose/Activity, один AVD или устройство
  ui/                          # экраны и навигация
  components/                  # изолированные компоненты
  data/                        # реальные SharedPreferences/PackageManager
  discovery/                   # store/provider
  fixtures/                    # НОВОЕ: детерминированные данные, тестовый пакет
tools/qa/                      # НОВОЕ: adb-сценарии, сбор логов, матрица
benchmark/                     # НОВОЕ (позже): Macrobenchmark
.github/workflows/qa.yml       # НОВОЕ (если появится remote)
```

## F.3. CI/CD: три уровня, без обязательной невозможной инфраструктуры

1. **Fast gate (каждый коммит, без устройства):**
   `assembleDebug` → `testDebugUnitTest` → `lintDebug` → `assembleRelease`/`bundleRelease`.
   Это уже воспроизводимо локально; добавить в CI можно без эмулятора.
2. **Instrumented gate (self-hosted AVD, не GitHub-hosted):**
   `connectedDebugAndroidTest` на API 34 x86, 1080p; JUnit XML + logcat; 3 последовательных прогона для проверки flakiness. GitHub-hosted runner без KVM не подходит.
3. **Device lab (nightly/manual):**
   реальные TV/боксы по матрице §B; 4K, ARM, слабая RAM, ИК-пульт, soak. Это не блокирует каждый коммит, но блокирует релиз.

Отчёты: JUnit XML, lint HTML, `connected` HTML, logcat, `dumpsys meminfo/gfxinfo`, скриншоты. Пустой отчёт или skipped-тест не считается успехом.

## F.4. Воспроизводимые данные

Нужны фикстуры:

- 0, 1, 10, 50, 200 приложений;
- пустой/заполненный порядок, 3 профиля, 10 папок, hidden folder;
- 0/1/30 постеров, отсутствующие изображения, битые метаданные;
- недоступный TV provider и offline/timeout;
- старые prefs-схемы (versionCode-миграции).

Текущие `HomeScreenTest`/`SearchTest` уже используют фейковые `TvApp`; расширять эту модель, а не подменять production-код.

---

# G. План реализации (после подтверждения)

## G0. Зафиксировать baseline — **выполнено на AVD**

- [x] Поднят AVD API 34 (x86, 1080p, 320 dpi): `hume-tv`, emulator 37.2.13, AEHD.
- [x] Прогнан `:app:connectedDebugAndroidTest` на текущем дереве.
- [x] Сохранены XML/HTML/logcat; зафиксировано точное число passed/failed/skipped.

**Результат:**

| Прогон | Результат | Время |
|---|---|---|
| Исходный (до исправлений) | 219 tests, **11 failed** | 5m 24s |
| После исправлений, №1 | **219/219 passed**, 0 skipped | 5m 21s |
| После исправлений, №2 | **219/219 passed**, 0 skipped | 5m 26s |
| После исправлений, №3 | **219/219 passed**, 0 skipped | 5m 28s |
| После `animationsDisabled = true` | **219/219 passed**, 0 skipped | 5m 34s |

**Что было исправлено по результатам прогона:**

1. `ProfileStore.delete()`: проверка «последний профиль» стояла до поиска осиротевших ключей. Повторное удаление уже удалённого профиля при одном оставшемся профиле не чистило layout/PIN. Проверка переставлена после orphan-ветки; тест `a_repeated_delete_finishes_cleanup_of_orphaned_profile_data` теперь проходит.
2. `TopShelf`: первый кадр ленивого rail не получал фокус, если shelf стартовал сразу с `focusedCard = 0`: `LaunchedEffect` родителя успевал до composition элемента `LazyRow`. Карточка теперь сама запрашивает фокус в `LaunchedEffect(focused)`, когда реально существует. Это закрывает и начальный фокус, и карточку, показанную после scroll.
3. `SearchTest`: тест искал mode-key по старой подписи `абв`, тогда как TalkBack-имя теперь локализованное (`Cyrillic letters`/`Кириллица`). Тест переведён на актуальное accessibility-имя.
4. `TopShelfTest`: неверное ожидание «3» для невыбранной секции; у невыбранной секции есть только pill. Ожидание исправлено на 1.
5. `app/build.gradle.kts`: включён `testOptions { animationsDisabled = true }`; это убирает системные анимации из activity-level тестов и не меняет production-поведение.

**Остаётся в G0/G1:** `KidsGateTest` всё ещё делает `Thread.sleep(125_000)`, потому что idle-часы завязаны на реальный `SystemClock`; для удаления нужна инъекция часов, а не простое сокращение таймаута. Один AVD не доказывает API 28/35 и физические устройства.

## G1. Стабилизация тестов — **частично выполнено**

- [x] `app/build.gradle.kts`: `testOptions { animationsDisabled = true }`.
- [x] Критерий 3 последовательных прогона 219/219, 0 skipped: выполнено 4 прогона на одном AVD.
- [ ] `KidsGateTest`: убрать/разделить `Thread.sleep(125_000)` — нужна инъекция idle-часов, иначе тест перестанет проверять реальный таймер заставки.
- [ ] Orchestrator — только после отдельного решения: добавляет зависимость и меняет модель запуска.
- [ ] Заменить device-dependent fixtures там, где это возможно, на детерминированные; остальное явно пометить как `@Smoke`/`device-dependent`.
- [ ] Убрать лишние `assume` из критичных путей или добавить отдельный прогон с подготовленными условиями.
- **Критерий для закрытия G1:** 3 прогона 219/219 без `Thread.sleep`, 0 skipped в критичных сценариях; отчёт с временем.

## G2. D-pad/UI-матрица — 2–3 дня

- Добавить `RemoteNavigationMatrixTest` для Home, Settings, Setup, Search, Control Center, папки, PIN: UP/DOWN/LEFT/RIGHT/CENTER/BACK/MENU, long-press, быстрые пары.
- Использовать существующие `performKeyInput`, `sendKeyDownUpSync`, `TvKeyBurstFilter`; не создавать второй Focus Engine.
- **Файлы:** `app/src/androidTest/.../ui/RemoteNavigationMatrixTest.kt`, helper в `fixtures/`.
- **Критерий:** каждый экран после каждого ключа имеет фокус; BACK не оставляет «мёртвый» экран.

## G3. Визуальная регрессия — 2–4 дня

- Golden-снимки: Home (idle/focus/dock), Settings root, Setup welcome, Search, Control Center, Folder, Saver preview, PIN.
- Фиксировать один AVD 1080p/320 dpi; маскировать часы, погоду, сеть, реальные обои.
- Сравнение с допуском; хранить эталоны в `androidTest/assets/golden/`.
- **Зависимости:** сначала без новых; при необходимости JVM-эталонов — отдельное решение по Paparazzi/Roborazzi.
- **Критерий:** 3 стабильных прогона, ложных diff из-за времени/погоды нет.

## G4. Performance / Macrobenchmark — 3–5 дней + устройство

- Отдельный модуль `:benchmark`, `androidx.benchmark:benchmark-macro-junit4`, `uiautomator`.
- Сценарии: cold/warm start, input→visual, 30 D-pad, открытие Settings/папки, 50 приложений, scroll.
- **Критерий:** медиана 5 прогонов на целевом ТВ; эмулятор — только «до/после», не результат.
- **Без устройства не начинать:** иначе получим красивые, но недостоверные числа.

## G5. Stress / migration / release — 3–5 дней

- `tools/qa`: install/uninstall storm, offline/DNS, `am kill` ×10, reboot, `send-trim-memory`, low storage.
- Хранить предыдущий release APK; `adb install -r` + проверка prefs.
- Release variant smoke: R8 + `apksigner verify`.
- **Критерий:** 0 crash/ANR, данные не теряются, отчёт с логами.

## G6. CI — 1–2 дня, если появится remote

- `.github/workflows/qa.yml`: JDK 17, Gradle cache, fast gate.
- Instrumented job — self-hosted; не обещать GitHub-hosted AVD.
- **Критерий:** каждый push даёт отчёт; падение теста блокирует merge.

## Зависимости, которые могут понадобиться

| Зависимость | Когда | Зачем |
|---|---|---|
| `androidx.test:orchestrator` | G1 | изоляция тестов, меньше утечек prefs между классами |
| `androidx.test.uiautomator` | G2/G5 | системные диалоги, permission, Home |
| `androidx.benchmark:benchmark-macro-junit4` | G4 | startup/frame/input |
| JaCoCo/Kover | опционально | численный coverage; не раньше стабильного прогона |
| Screenshot-библиотека | опционально | только если `captureToImage`+golden недостаточно |

---

# H. Release Readiness

## H.1. Пройдено

- [x] Debug build.
- [x] Release APK + AAB, R8/resource shrink.
- [x] Release APK подписан; `apksigner verify` проходит.
- [x] `minSdk 28`, `targetSdk 35`; APK metadata корректны.
- [x] 412 unit-тестов зелёные.
- [x] Lint: 0 errors, 16 warnings.
- [x] Baseline profile-артефакты присутствуют в APK/AAB (библиотечный профиль; app-specific эффект не измерен).
- [x] 219 instrumented-тестов скомпилированы и **прогнаны на AVD**: 219/219, 0 skipped, 4 зелёных прогона подряд.
- [x] `testOptions { animationsDisabled = true }`.

## H.2. Не пройдено / не проверено

- [ ] 219 instrumented-тестов **не прогнаны на физическом Android TV**; только AVD `hume-tv` API 34 x86.
- [ ] Нет физического Android TV/бокса.
- [ ] Нет 4K-панели и 4K-растеризации.
- [ ] Нет реального ИК/Bluetooth-пульта.
- [ ] Нет TalkBack-голосовой приёмки.
- [ ] Нет fontScale 1.15/1.3, RTL, Android 9–13/15.
- [ ] Нет performance/soak/ANR/OOM/process death.
- [ ] Нет migration/upgrade с предыдущей версии.
- [ ] Нет visual regression.
- [ ] Нет CI-гейта.

## H.3. Непроверенные устройства

Sony, TCL, Xiaomi, Philips, Hisense, generic AOSP TV box (ARM32/ARM64), Android 15 TV, API 28–33, 4K-панели, 1 GB RAM, HDR, 120 Гц.

## H.4. Известные ограничения

- `stateNotNeeded="true"` — process death не восстанавливает Settings page/focus; менять без device-теста нельзя.
- Нет 4K-видео заставки: источник Aerial — 1080p; это осознанное ограничение, не «4K».
- UI-языки — RU/EN; прочие локали дают английский; RTL не тестировался.
- Нет touch/gesture-поддержки как продукта; только D-pad.
- Нет фоновых сервисов и агрессивных ретраев; сетевые сценарии требуют ручной проверки.

---

# I. Текущие доказательства и команды

```bash
# 1. Unit + lint (уже выполнено)
GRADLE_USER_HOME=C:/gradle-home ./gradlew \
  :app:testDebugUnitTest :app:lintDebug \
  -Pkotlin.incremental=false --console=plain

# 2. Debug + release
GRADLE_USER_HOME=C:/gradle-home ./gradlew \
  :app:assembleDebug :app:assembleRelease :app:bundleRelease \
  -Pkotlin.incremental=false --console=plain

# 3. AndroidTest: только компиляция (устройства нет)
GRADLE_USER_HOME=C:/gradle-home ./gradlew \
  :app:compileDebugAndroidTestKotlin \
  -Pkotlin.incremental=false --console=plain

# 4. Проверить устройство
"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" devices -l

# 5. Instrumented-прогон на AVD (выполнено)
export ANDROID_SERIAL=emulator-5554
export ANDROID_SDK_ROOT='C:\Android\Sdk'
GRADLE_USER_HOME=C:/gradle-home ./gradlew \
  :app:connectedDebugAndroidTest \
  -Pkotlin.incremental=false --console=plain
```

**Окружение прогона:** AVD `hume-tv`, Android 14 (API 34), x86, 1920×1080, density 320; emulator 37.2.13; AEHD 2.2. Рабочий бинарник эмулятора на этой машине — `C:\Android\Sdk\emulator\emulator.exe`; копия из `%LOCALAPPDATA%\Android\Sdk\emulator` зависала до QEMU loop. Для Gradle нужен `ANDROID_SDK_ROOT=C:\Android\Sdk`.

Артефакты:

- `app/build/outputs/apk/debug/app-debug.apk` — 34 103 949 B;
- `app/build/outputs/apk/release/app-release.apk` — 4 244 946 B;
- `app/build/outputs/bundle/release/app-release.aab` — 5 087 646 B;
- `app/build/reports/tests/testDebugUnitTest/` — JUnit HTML/XML;
- `app/build/reports/lint-results-debug.html` — lint.

---

# J. Итог

**Что можно утверждать сейчас:** доменная логика хорошо покрыта unit-тестами, Compose-тесты описывают реальные пользовательские сценарии, release собирается и подписывается, APK содержит нужные ABI и корректные SDK-метаданные. **На AVD `hume-tv` (API 34, x86, 1080p) 219/219 instrumented-тестов проходят четыре прогона подряд, 0 skipped.**

**Что нельзя утверждать:** что лаунчер работает на Sony/TCL/Xiaomi/Philips/Hisense, что 4K и реальный пульт проверены, что 0.5.0 не имеет performance/soak-регрессий, что обновление поверх старой версии безопасно, что API 28–33/35 ведут себя так же, как API 34 x86.

**Следующий шаг:** G1 — заменить `Thread.sleep(125_000)` инъекцией idle-часов; затем G2–G5 на физическом устройстве. Без реального ТВ дальнейшая автоматизация даёт инфраструктуру, но не доказательства совместимости.

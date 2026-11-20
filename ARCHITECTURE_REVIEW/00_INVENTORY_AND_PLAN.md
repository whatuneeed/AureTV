# OVISIONTV — CODEBASE REVIEW, INVENTORY & SAFE MODULARIZATION PLAN

Дата: 18.11.2026  
Версия на входе: **0.5.40 / vc113**, HEAD `f5466c0`  
Статус: **Phase 1–3 завершены, Phase 4 начат (safe import cleanup)**. Полная модуляризация не заявляется завершённой.

---

## 1. BASELINE (фактические измерения)

### Исходное состояние

| Метрика | Значение |
|---|---|
| Основные `.kt` | **175** |
| Строк в `app/src/main/java` | **40 799** |
| Unit-тесты | **80 suites / 552 теста** |
| Android/UI-тесты | **47 файлов / 302 теста** |
| Unit baseline | **552/552 passed** |
| Connected baseline | **302/302 passed**, AVD `hume-tv`, API 34, 1920×1080 |
| Lint baseline | **0 errors / 15 warnings / 0 information** |
| `runBlocking` в main | **0** |
| `!!` в main | **3** |
| `TODO/FIXME` в main | **0** |
| `HttpURLConnection` | **41** |
| APK 0.5.40 | 6 601 723 B, v2, прежний сертификат |

### Крупнейшие основные файлы

| Строк | Файл | Текущая ответственность |
|---:|---|---|
| 4 793 | `ui/SettingsScreen.kt` | все категории Settings, строки, диалоги, picker'ы, эффекты |
| 1 867 | `ui/SetupScreen.kt` | 6 страниц setup, профиль, язык, погода, финал |
| 1 247 | `ui/components/TopShelf.kt` | shelf, hero, section bar, карточки |
| 1 074 | `ui/components/ControlCenter.kt` | панель, часы, сеть, профиль |
| 947 | `ui/components/SaverScreen.kt` | screensaver playback, UI, media session |
| 944 | `ui/LauncherRoot.kt` | Activity-уровень, saver, kids gate, навигация |
| 841 | `ui/FolderOverlay.kt` | folder grid, drag, rename, menus |
| 824 | `ui/BackupScreen.kt` | backup/restore UI |
| 823 | `ui/components/AmbientScreen.kt` | ambient UI и настройки |
| 787 | `data/Weather.kt` | API, parse, cache, scheduling, formatting |
| 727 | `ui/components/PinPad.kt` | PIN UI и ввод |
| 723 | `ui/HomeScreen.kt` | координация Home (после серии split'ов) |

### Пакеты

| Строк | Пакет |
|---:|---|
| 14 715 | `ui` |
| 10 962 | `ui.components` |
| 5 717 | `data` |
| 3 010 | `backup` |
| 1 762 | `update` |
| 1 448 | `ambient` |
| 1 428 | `discovery` |
| 927 | корень (`MainActivity`, `SetupActivity`, `OVisionApp`, …) |
| 666 | `online` |
| 164 | `input` |

### Manifest

Permissions: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `NEARBY_WIFI_DEVICES`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `READ_TV_LISTINGS`, `REQUEST_INSTALL_PACKAGES`.

Компоненты: `MainActivity`, `SettingsActivity`, `SetupActivity`, `BackupActivity`, `SoftwareUpdateActivity`, `OVisionAccessibilityService`, `AerialDreamService`, `BackupAlarmReceiver`, `InstallResultReceiver`, `SleepReceiver`, `SleepReceiver$Admin`.

### Зависимости

Compose BOM 2024.09.03, activity-compose 1.9.2, core-ktx 1.13.1, profileinstaller 1.4.1, coroutines-android 1.7.3, media3-exoplayer/ui 1.4.1. Тестовые: JUnit 4.13.2, org.json, Compose UI test, androidx.test ext/runner/rules. Лишних или дублирующихся библиотек не подтверждено; обновление версий не входит в этот этап.

---

## 2. АРХИТЕКТУРНАЯ ДИАГНОСТИКА

### Подтверждённые проблемы

1. **`SettingsScreen.kt` — 4 793 строки.** Один файл содержит навигацию, все категории, строки, диалоги, выбор города, PIN, Kids, Aerial, update/backup-переходы. Это главный риск сопровождения.
2. **`SetupScreen.kt` — 1 867 строк.** Страницы и их состояние связаны сильнее, чем нужно.
3. **`TopShelf.kt` / `ControlCenter.kt` / `SaverScreen.kt`** совмещают UI, focus-механику и часть бизнес-логики.
4. **`ui` + `ui.components` = 25 677 строк.** Нет явной границы между design-system, feature-UI и экранами.
5. **41 `HttpURLConnection`.** Не все являются дублями, но сетевой слой размазан по weather, aerial, online, update, reddit, discovery.
6. **Повторяющиеся имена `clear/load/prefs/read/save/refresh`** — кандидаты на аудит, не доказанные дубли. Поведение может различаться.
7. **3 `!!`** — точечно проверить (`WatchNext.kt`, `OnlineModels.kt`, `WeatherText.kt`).
8. **Фокус/клавиатура/сайзер** имеют несколько точек входа; после split'ов Home логика стала чище, но `LauncherRoot` остаётся крупным координатором.

### Что уже сделано до этого этапа

- HomeScreen: **2 619 → 723 строки** (после import cleanup), вынесены состояния, эффекты, слои, меню, клавиатуры, key-handler.
- SettingsScreen ранее уменьшен с 5 250 до 4 793 строк (`SettingsMetrics`, `SettingsRowCard`, `SettingsGroupRows`).
- Recovery gate сделан неблокирующим.
- CI workflow и README добавлены, но workflow не запускался без GitHub remote.

### Ограничения анализа

- Нет detekt/ktlint/static-analysis плагина; «мёртвые» импорты проверены лексически и компиляцией, не IDE-инспекцией.
- Нет физического ТВ, поэтому 4K/HDR/HEVC/IR/BT/HDMI-CEC не проверялись.
- Полный connected baseline прогонялся один раз; после import cleanup — целевые Home/Settings (100 тестов, один teardown-flake, изолированный повтор 1/1).

---

## 3. ЦЕЛЕВАЯ СТРУКТУРА

Для проекта такого размера **не создавать отдельный Gradle-модуль на каждую фичу**: это увеличит build-сложность без пользы. Цель — логические пакеты внутри существующего модуля, с постепенным переносом и сохранением публичных точек входа.

```
com.whatuneed.ovisiontv.kotlin
├── app/                     Activity, Application, wiring
├── core/
│   ├── designsystem/        TvListRowSurface, TvMenu, tokens, surfaces
│   ├── input/               key repeat, focus helpers, remote input
│   ├── network/             единый HTTP-клиент и политика TLS
│   ├── storage/             prefs, atomic write, JSON codecs
│   └── logging/             crash/diagnostics
├── feature/
│   ├── home/                HomeScreen, state, layers, menю, effects
│   ├── apps/                AppRepository, discovery, labels, folders data
│   ├── weather/             Weather, cache, panel, preferences
│   ├── settings/            SettingsScreen + category pages
│   ├── setup/               SetupScreen + pages
│   ├── profiles/            Profiles, picker, PIN, kids gate
│   ├── recommendations/     discovery, ranking, online/Flux
│   ├── ambient/             saver, aerial, reddit, photos
│   ├── backup/              archive, restore, journal, UI
│   └── update/              metadata, download, install, UI
└── domain/                  только реально общие модели/правила
```

Правило: **не переносить всё сразу**. Сначала файлы внутри текущего пакета, затем — пакетные перемещения с обновлением импортов и полным прогоном тестов.

---

## 4. ПЛАН ПО ЭТАПАМ

### Stage 0 — Baseline (выполнено)
- Зафиксированы 552 unit / 302 connected / lint 0 errors.
- Рабочее дерево чистое на `f5466c0`.

### Stage 1 — Safe cleanup (выполнено частично)
- Удалены **20 подтверждённо неиспользуемых импортов** в `HomeScreen.kt` (11) и `SettingsScreen.kt` (9).
- Поведение не менялось; compile успешен, unit 552/552.
- Оставшиеся кандидаты (ещё ~88) не удалялись: часть импортов нужна Kotlin-делегатам (`getValue`/`setValue`) и не видна текстовому поиску.

### Stage 2 — Settings decomposition (следующий безопасный крупный шаг)
Порядок:
1. `SettingsModels` / `SettingsMetrics` / `SettingsRowCard` — уже есть, расширить.
2. Вынести независимые страницы: About, Device, Appearance, Language, Ambient, Aerial, Kids, Profiles.
3. Каждая страница получает свой файл и параметры-колбэки; persistence остаётся в `SafePreferences`.
4. `SettingsScreen` остаётся навигатором категорий.
5. После каждого выноса: compile + `SettingsScreenTest` + unit.

Риск: средний. Контракт — те же `testTag`, строки, порядок фокуса, те же prefs-ключи.

### Stage 3 — Setup decomposition
Вынести страницы welcome/language/profile/weather/ambient/finish в отдельные файлы; сохранить `SetupProgress` и порядок.

### Stage 4 — Large UI components
`TopShelf`, `ControlCenter`, `SaverScreen`, `LauncherRoot`, `FolderOverlay` — разделить на presentation + state/focus, не меняя API.

### Stage 5 — Network consolidation
Сначала инвентаризация 41 `HttpURLConnection` по контрактам (timeouts, TLS, headers, retry). Только после этого — общий helper. Нельзя объединять запросы с разным поведением.

### Stage 6 — Package moves
`ui.components` → `core.designsystem`; feature-пакеты. Только после стабилизации файлов; каждый перенос — отдельный коммит.

### Stage 7 — Dependency review
Проверить фактическое использование, удалить только подтверждённо лишнее. Обновления версий — отдельная ветка, не в этом рефакторинге.

### Stage 8 — Final verification
Clean build, unit, connected, lint, release APK, regression checklist, финальный отчёт.

---

## 5. REGRESSION GATES

После каждого этапа обязательно:

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
# затронутые UI-классы:
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=<affected tests>
```

Полный connected suite — на финальном этапе и после рискованных переносов.  
Для каждого этапа фиксировать: изменённые файлы, число тестов, lint, APK SHA-256.

---

## 8. STAGE 5 — PACKAGE MOVES

| Шаг | Файл | Из | В | Проверка |
|---|---|---|---|---|
| 5.1 | `SectionGlyph.kt` | `ui.components` | `core.designsystem` | compile, `ControlCenterTest` + `SetupScreenTest` 36/36 |
| 5.2 | `TvListRowSurface.kt` | `ui.components` | `core.designsystem` | compile, Settings/Backup/Update 65/65 (1 teardown flake, repeat 1/1) |
| 5.3 | `IconFocusLight.kt` | `ui.components` | `core.designsystem` | compile, `HomeScreenTest` 41/41, `FocusLightTest` 4/4 |
| 5.4 | `TvKeyRepeat.kt` | `ui.components` | `core.input` | compile, `TvKeyRepeatTest` + `MainActivityInputTest` 9/9 |

Правило: один файл за коммит, только `package` и импорты; поведение, `internal`-видимость и имена не меняются.

---

## 6. RISK REGISTER

| Риск | Уровень | Контроль |
|---|---|---|
| Смена prefs/JSON/БД | высокий | не менять ключи и схемы; backup tests |
| Смена focus/Back/OK | высокий | Home/Settings/Setup UI tests + ручная проверка |
| Потеря callbacks при split | средний | compile + targeted tests |
| Дубли сетевых клиентов с разным TLS/timeout | средний | сначала контрактная инвентаризация |
| Скрытая зависимость от порядка композиции | средний | screenshot/ручная проверка |
| Физические функции | высокий | **NOT TESTED** без ТВ |

---

## 7. СЛЕДУЮЩИЙ КОНКРЕТНЫЙ ШАГ

**Stage 2, первый вынос:** `SettingsScreen.kt` → страница About/Device как отдельный composable-файл, с сохранением всех `testTag`, строк и callback'ов.  
Критерий готовности: `SettingsScreenTest` 59/59, unit 552/552, lint без новых ошибок, diff без изменения поведения.

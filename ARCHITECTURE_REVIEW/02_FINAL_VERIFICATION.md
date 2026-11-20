# OVISIONTV — ФИНАЛЬНАЯ ПРОВЕРКА РЕФАКТОРИНГА (A–G)

Дата: 18.11.2026
Версия: **0.5.73 / versionCode 146**
HEAD на момент проверки: финальный коммит документации, тег `v0.5.73-verified`
Устройство: эмулятор `hume-tv`, API 34, x86, 1920×1080
Проверяющий: автоматизированный прогон + статический анализ

---

## A. Границы проверки

Проверялся только рефакторинг и его регрессии:

- декомпозиция крупных экранов и компонентов;
- сетевые контракты (redirect, host policy, caps, cancellation, User-Agent);
- переносы файлов в `core.*`;
- отсутствие изменений пользовательских данных, схем, строк и навигации.

Не проверялись и не заявляются как проверенные:

- физический Android TV;
- 4K/HDR-декодирование;
- 720p+ трейлеры;
- ARM32/ARM64, API 28–33/35, 1 ГБ RAM;
- RTL, TalkBack-голос, IR/BT/HDMI-CEC;
- hostile DNS, captive portal, MITM, IPv6-only;
- 24-часовой soak, Perfetto, storage-full;
- реальный GitHub update path (отложен владельцем).

---

## B. Что изменено

### B.1. Декомпозиция

| Файл | Было | Стало |
|---|---:|---:|
| `HomeScreen.kt` | 2619 | 723 |
| `SettingsScreen.kt` | 4793 | 4122 |
| `SetupScreen.kt` | 1867 | 977 |
| `TopShelf.kt` | 1247 | 483 |
| `ControlCenter.kt` | 1074 | 735 |
| `SaverScreen.kt` | 947 | 861 |
| `LauncherRoot.kt` | 944 | 809 |
| `FolderOverlay.kt` | 841 | 804 |
| `BackupScreen.kt` | 824 | 574 |
| `AmbientScreen.kt` | 823 | 395 |
| `SoftwareUpdateScreen.kt` | 641 | 395 |

Вынесены presentation-компоненты, состояния, focus/effects и страницы Setup/Settings. Persistence, SAF-лаунчеры, `BackupManager`, `UpdateManager` и навигация остались во владеющих экранах.

### B.2. Сетевые контракты

- **G1:** manual redirects с проверкой схемы/хоста во всех клиентах.
- **G2:** один `LauncherUserAgent` из `BuildConfig.VERSION_NAME`.
- **G3:** ранний `contentLengthLong` во всех `HttpURLConnection`.
- **G4:** единый `BoundedRead.readAtMost` с `ensureActive()` на каждом чанке.
- **G5:** единый `resolveRedirectUrl`; allowlist остаётся у клиента.

JustWatch POST не повторяется на 3xx: тело GraphQL-запроса не переигрывается на другом хопе.

### B.3. Package moves

| Файл | Новый пакет |
|---|---|
| `SectionGlyph.kt` | `core.designsystem` |
| `TvListRowSurface.kt` | `core.designsystem` |
| `IconFocusLight.kt` | `core.designsystem` |
| `TvKeyRepeat.kt` | `core.input` |
| `BoundedRead.kt`, `ImageRedirectPolicy.kt` | `core.network` |

---

## C. Проверка

### C.1. Clean build

```
./gradlew clean :app:assembleDebug :app:assembleRelease \
  :app:testDebugUnitTest :app:lintDebug -Pkotlin.incremental=false
```

Результат: **BUILD SUCCESSFUL**.

### C.2. Тесты

| Набор | Результат |
|---|---:|
| Unit | **569 / 569**, failures 0, errors 0, skipped 0 |
| Connected | **302 / 302**, failures 0, errors 0 |
| Lint | **0 errors / 15 warnings / 0 information** |

Полный connected suite выполнялся на эмуляторе; targeted-прогоны этапов не заменяют его.

### C.3. Установка release APK

- install: **Success**;
- первый cold start: **1512 ms**;
- второй cold start: **1099 ms**;
- процесс жив, `FATAL EXCEPTION` в logcat отсутствует.

Разброс cold start на эмуляторе высокий; это не измерение производительности ТВ.

---

## D. Статические метрики

| Метрика | Baseline | Финал |
|---|---:|---:|
| Kotlin-файлы `main` | 175 | **202** |
| Строки `main` | 40 799 | **41 981** |
| `runBlocking` | 0 | **0** |
| `TODO` | 0 | **0** |
| `!!` | 3 | **0** |
| `HttpURLConnection` | 41 | **41** |

Рост строк объясняется заголовками новых файлов, импортами, тестами и документирующими комментариями; логика не дублировалась. `!!` в `main` больше нет: три оставшихся случая заменены на безопасные проверки.

Крупнейшие файлы после рефакторинга:

1. `SettingsScreen.kt` — 4122;
2. `SetupScreen.kt` — 977;
3. `SaverScreen.kt` — 861;
4. `Weather.kt` — 833;
5. `LauncherRoot.kt` — 809.

---

## E. Риски и ограничения

| Риск | Статус |
|---|---|
| Смена prefs/JSON/БД | не менялись; backup-тесты зелёные |
| Смена focus/Back/OK | Home/Settings/Setup/ControlCenter зелёные |
| Потеря callbacks при split | compile + targeted + full connected |
| Сетевые таймауты/redirect | unit-политики; live CDN не проверялся |
| Физические функции ТВ | **NOT TESTED** |
| 4K/HDR, 720p trailers | **NOT TESTED / deferred** |
| GitHub update delivery | **deferred владельцем** |

Один прогон Settings дал известный teardown-flake `Activity never becomes DESTROYED`; после перезапуска AVD тот же тест прошёл 1/1, код между прогонами не менялся. В финальном полном прогоне flake не повторился.

---

## F. Артефакт

- APK: `app/build/outputs/apk/release/app-release.apk`
- Размер: **6 618 107 B**
- SHA-256 clean-сборки: **`fdac34ed74a9df6aa9e2faf1d26ce27386152a23b6c49d35c6f22508f41d75b7`**
- Подпись: APK Signature Scheme **v2**
- Сертификат: `327a50453093b783e84b1f2645b03319477d89966ea6a653dab53d5a567d7b10`
- `release_check.py`: **PASS**; RU/EN — **661** ресурсное имя
- Evidence: `RELEASE_READINESS/evidence/189-release-check-0.5.73-final.txt`, `190-connected-final-0.5.73.txt`

Для аудита авторитетен артефакт clean-сборки, указанный выше. Инкрементальные сборки того же кода могут дать другой SHA-256 при том же размере; воспроизводимость байт-в-байт не заявляется.

---

## G. Вердикт

**CONDITIONAL GO.**

Рефакторинг завершён без подтверждённых регрессий: clean build, 569 unit, 302 connected, lint без ошибок, release APK устанавливается и запускается.

Условия перед безусловным релизом не изменились:

1. проверка на физическом Android TV;
2. 4K/HDR и 720p+ trailer honesty;
3. P0-3 HOME role на Google TV;
4. отдельное разрешение владельца на публикацию;
5. отложенные P1–P3 (лицензирование RU-метаданных, updater hardening, soak, R8 mapping, SCA/CVE и др.).

Публикация APK без отдельного разрешения не выполнялась.

# Финальный аудит готовности к релизу OVisionTV 0.5.8 (versionCode 81)

**Дата:** 16.11.2026  
**Объект:** `C:/OpenCode Project/OVisionTV/OVisionTV_Kotlin`  
**Проверяемая сборка:** `app/build/outputs/apk/release/app-release.apk`  
**HEAD:** `1dcc024 Home: match tvOS top shelf and profile capsule`  
**Устройство для проверки:** AVD `hume-tv`, Android 14 / API 34, x86, 1920×1080, density 320  
**Аудитор:** независимая проверка исходников, сборки, тестов, APK и поведения на эмуляторе

## Решение

# NO-GO

Публикация 0.5.8 / versionCode 81 в текущем виде **не рекомендуется**. Это не оценка «проект плохой»: архитектура, фокус, сборка, подпись, резервное копирование и большая часть тестов действительно находятся в хорошем состоянии. Но у релиза есть три подтверждённых блокера уровня P0, а также незакрытые P1-риски, которые нельзя честно назвать «готовыми к публикации».

Главное:

1. **Home после завершения Setup пустой** — только часы, аватар и док; нет hero, нет подписей приложений, нет пустого состояния. Это подтверждено скриншотами первого запуска и запуска в роли HOME.
2. **Проверенный APK собран из грязного дерева.** В рабочем дереве остались незакоммиченные изменения `HomeScreen.kt` и `TopShelf.kt`; SHA-256 проверенного APK не соответствует обещанному артефакту 0.5.8; versionCode не повышен.
3. **На Google TV AVD приложение не становится фактическим HOME.** OVisionTV виден в системном выборе, роль HOME через shell переключается, но после перезагрузки и нажатия HOME система снова открывает штатный Google TV Launcher. Когда штатный лаунчер принудительно отключён, OVisionTV как HOME работает — значит, приложение в принципе способно быть HOME, но заявленную замену HOME на этом образе подтвердить нельзя.

Дополнительно: **превью Aerial на AVD остаётся пустым**; в logcat была ошибка доверия TLS к CDN Apple; полноэкранное воспроизведение заставки не проверялось; **Setup состоит из 9 шагов без Skip**; **нет README, CHANGELOG, CI и release notes**; **нет физического ТВ, 720p/4K, TalkBack и матрицы API 28–35**. Поэтому «зелёные» 481 unit + 267 instrumented тестов не означают готовность продукта.

---

## 0. Дополнение после исправлений 0.5.9 (16.11.2026)

Этот раздел добавлен после того, как владелец поручил начать исправления. Основной отчёт ниже сохраняет состояние аудита **0.5.8 / vc81** и не переписывается задним числом.

**Что изменилось в 0.5.9 / versionCode 82:**

- **P0-1 (пустой Home) — закрыт по коду и AVD.** `TopShelf` получил явное пустое состояние `EmptyHome` (заголовок и объяснение вместо чёрного поля; действие — плитка Settings в доке, отдельной кнопки нет), под иконками дока появились реальные названия приложений/папок; строки добавлены в RU и EN. `HomeScreenTest` и `TopShelfTest` покрывают пустой Home и подписи. Скриншоты: `evidence/52-fix-home-empty.png`, `evidence/53-fix-home-labels-focus.png`. Сценарии «нет permissions / permissions есть, но провайдеров нет / провайдеры есть» проверены на AVD; физический ТВ — **NOT TESTED**.
- **P0-2 (грязное дерево) — закрыт.** Изменения закоммичены в **`07bfef3`** (`Home: hide partial second row and keep a banner-only shelf honest`), versionCode поднят до **82**, versionName — **0.5.9**, release пересобран после остановки Gradle и удаления `app/build`. Создан локальный аннотированный тег **`v0.5.9`** (переставлен на финальный коммит после правки частичной второй строки). Новый артефакт: **4 881 010 B**, SHA-256 **`b12a267b245b7e1b77f07cb05deeb3d8264e56e8a0fd2a42ebb5a00aabaa7696`**.
- **P1-01 (пустое превью Aerial) — симптом устранён на AVD.** Медиа Apple снова загружается по HTTP с единственного хоста `sylvan.apple.com` (ограниченное исключение в `network_security_config.xml`), превью и сетка из 100 клипов открываются: `evidence/54-fix-aerial-preview-http.png`, `evidence/55-fix-aerial-thumbnails-http.png`. **Полноэкранное воспроизведение, физический ТВ и враждебная сеть — NOT TESTED**; честное состояние ошибки для случая «сеть недоступна» не добавлялось. Это осознанное решение владельца, а не возврат к HTTPS-only.
- **P0-3 (HOME на Google TV) — не закрыт.** На AVD роль HOME по-прежнему не приводит к OVisionTV без отключения штатного лаунчера. Нужны 2–3 физических ТВ; вывод «замена HOME подтверждена» делать нельзя.

**Проверки 0.5.9:** clean `:app:assembleRelease` — **BUILD SUCCESSFUL**; `tools/release_check.py` с явным `apksigner` — **EXIT=0**, v2, сертификат `327a5045…7b10`, RU/EN **639**; `zipalign -c -P 16` — **Verification successful**; unit **481 debug + 481 release, 0 fail/error/skip**; connected AVD **272 / 272, 0 fail, 6m20s** (полный прогон; `MainActivityInputTest` теперь изолирует prefs и не зависит от предыдущих классов); lint **0 Error / 19 Warning / 5 Information**; release APK установлен, запускается (Setup на французском приветствии — это анимированное `GREETINGS`, не смена языка).

**Что это меняет для решения:** P0-1 и P0-2 больше не являются блокерами. Вердикт **NO-GO** сохраняется, но теперь он держится на **P0-3** (HOME не подтверждён на целевом ТВ) и незакрытых P1 (документация/тег релиза на GitHub, физическая матрица, лицензии, реальный updater). Это уже не «пустой первый экран» и не «APK из грязного дерева».

## 0.1. Дополнение 0.5.10: мастер настройки сжат до шести шагов

- **P1-02 закрыт.** Вместо девяти экранов теперь шесть: `Welcome → Language & Profile → Home & Weather → Screen Saver → Home and Access → All Set`. Язык и профиль объединены; рекомендации, продолжение просмотра и погода — на одной странице; заставка получила режимы `Aerials / Clock / Minimal`, циклическую задержку и музыку; отдельная страница тем убрана, темы/Photos/Reddit остаются в `Settings → Personalization → Ambient Experience`.
- **Проверки.** `SetupScreenTest` — **25/25**; полный connected AVD — **277/277** (0 fail, 6m46s); unit — **481 debug + 481 release** (0 fail/error/skip); lint — **0 Error / 19 Warning / 5 Information**. Release: **4 877 894 B**, SHA-256 **`a30dde38c354d337049c16b2a881f15cfbafc219648a5b147316cde09c5d8286`**, vc83/0.5.10, коммит `4ae91bb`, тег `v0.5.10`; ручной проход RU/EN на 1920×1080 и 1280×720.
- **P0-3 не закрыт:** физический Google TV по-прежнему не проверен. Новый мастер не обещает автоматическую замену HOME: строка называет выбор домашнего экрана только как возможность, если ТВ её предлагает.

---

## 1. Как читать этот отчёт

Шкала статусов:

| Статус | Значение |
|---|---|
| **PASS** | Проверено на исходниках, сборке, тестах или воспроизводимом поведении; результат соответствует требованию |
| **PARTIAL** | Реализовано и/или частично проверено, но есть существенное непокрытое условие |
| **FAIL** | Подтверждённый дефект или отсутствие обязательного результата |
| **NOT TESTED** | Проверка не выполнялась; «ожидаемо работает» не считается доказательством |
| **BLOCKED** | Проверка невозможна без внешнего условия (физическое устройство, сеть, OEM, подпись) |
| **N/A** | Не применимо к Android TV или к данному релизу |

Правила отчёта:

- всё, что не проверено, помечено **NOT TESTED**, а не «должно работать»;
- выводы отделены от предположений: причина дефекта указывается только там, где она видна в коде или логе;
- скриншоты и логи лежат в `RELEASE_READINESS/evidence/`;
- «зелёный» тест не отменяет дефект, если тест не покрывает сценарий;
- ничего не публиковалось, не подписывалось заново и не отправлялось в store;
- секреты, пароли и ключи в отчёт не включались.

---

## 2. Метод и доказательства

### 2.1. Что реально проверялось

| Проверка | Как | Результат |
|---|---|---|
| Исходники | чтение `app/src/main`, `app/src/test`, `app/src/androidTest`, Gradle, manifest, res/xml, raw | выполнено |
| Чистая release-сборка | `:app:clean` + `:app:assembleRelease` | сборка успешна после остановки Gradle и удаления `app/build`; первый `clean` упал на заблокированных lint-cache jars — это среда, не код |
| Артефакт | `aapt2 badging`, `apksigner`, `zipalign -c -P 16`, `tools/release_check.py` | APK собран, подписан v2, выровнен, release-check EXIT=0 |
| Unit-тесты | `testDebugUnitTest` + `testReleaseUnitTest` | **481 / 481** и **481 / 481**, 0 failures, 0 errors, 0 skipped |
| Instrumented | `connectedDebugAndroidTest` на AVD API 34 | **267 / 267**, 0 failures, 0 errors, BUILD SUCCESSFUL за 6m06s |
| Lint | `lintDebug` | **0 Error / 19 Warning / 5 Information** |
| Установка | release APK после удаления debug-версии | установлен и запущен; `INSTALL_FAILED_UPDATE_INCOMPATIBLE` при попытке поставить release поверх debug — ожидаемая разница подписей |
| Первый запуск | полный проход 9 экранов Setup | пройден, скриншоты 09–19 |
| Home | первый Home после Setup, Home как HOME, возврат из shelf | Home пустой; shelf-код и тесты есть, но контента нет |
| Settings | root, Device, System, Software Update, Personalization, Ambient | открываются; Update честно сообщает об отсутствии источника; Backup вручную не открыт |
| HOME-роль | системный picker, `cmd role`, перезагрузка, HOME, отключение штатного лаунчера | роль не приводит к OVisionTV; при отключённом штатном лаунчере OVisionTV работает как HOME |
| Aerial | превью Ambient, ожидание, logcat | превью пустое; в logcat предыдущего прогона — `Trust anchor for certification path not found`; файл лога не сохранён, симптом подтверждён скриншотами 35 и 50 |
| Производительность | `am start -W` ×3, `dumpsys meminfo`, `gfxinfo` | только эмулятор; см. §9 |
| Секреты | grep по ключам/паролям/токенам | ключей и токенов нет; строки `password = ""` — UI-поля, тестовый пароль — тест |

### 2.2. Ключевые файлы доказательств

| Файл | Что доказывает |
|---|---|
| `evidence/02-assembleRelease.log` | release-сборка завершилась успешно |
| `evidence/03-release-check.txt` | 4 863 242 B, SHA-256, vc81/0.5.8, appId, min/targetSdk, v2, сертификат, 637 RU/EN |
| `evidence/04-badging.txt` | versionCode/versionName, permissions, application label |
| `evidence/05-permissions.txt` | фактический набор permissions в APK |
| `evidence/06-manifest-xmltree.txt` | manifest и компоненты |
| `evidence/07-release-install.txt` | установка release APK |
| `evidence/09–19` | 9 шагов Setup |
| `evidence/20-release-home.png` | **пустой Home после Setup** |
| `evidence/21-unit-lint.log` | unit-тесты и lint |
| `evidence/22-connected.log` | instrumented-прогон |
| `evidence/23-release-settings.png` | Settings root |
| `evidence/25-release-system.png` | System: `Default Home App: No`, Software Update, Backup |
| `evidence/26-release-update.png` | Software Update честно пишет, что источник не настроен |
| `evidence/27-release-backup.png` | **не Backup, а системный picker Default home app** — файл назван неверно; это ограничение доказательств, а не доказательство работы Backup |
| `evidence/28-zipalign-16k.txt` | `Verification successful` |
| `evidence/29-apksigner.txt` | v1=false, v2=true, v3/v3.1/v4=false; сертификат |
| `evidence/34-release-ambient.png`, `35-release-ambient-after-wait.png`, `50-ambient-tls-retry.png` | пустое превью Aerial (два независимых визита) |
| `evidence/36–44` | системный picker, роль HOME, перезагрузка, HOME |
| `evidence/45-home-with-stock-disabled.png` | OVisionTV как HOME при отключённом штатном лаунчере — **и всё равно пустой Home** |
| `evidence/46-startup-w.txt` | холодный старт 891/931/772 ms |
| `evidence/47-stock-launcher-home-priority.txt` | у штатного Google TV Launcher HOME-фильтр имеет `priority=2` |
| `evidence/49-home-role.log` | FallbackHome запускает штатный лаунчер после разблокировки |
| `evidence/51-role-state.txt` | роль HOME = OVisionTV, но resolver всё равно возвращает `com.google.android.tvlauncher/.MainActivity` |

### 2.3. Ограничения

- **Нет физического Android TV.** Всё поведение — только AVD x86 с software GPU (`swiftshader_indirect`).
- **Нет 720p/4K, HDR, 1 GB RAM, ARM32/ARM64, API 28–33/35.**
- **Нет TalkBack voice, реального ИК/Bluetooth-пульта, HDMI-CEC, Ethernet-only.**
- **Нет реального GitHub release, реального скачивания APK обновления, OEM-installer, SAF-провайдера, Doze/Alarm/reboot backup.**
- **Нет 24-часового soak, Perfetto, baseline profile, CI.**
- **Нет юридической экспертизы** по Apple Aerial, GPL-каталогу AerialViews, погодным данным и Reddit.
- **TLS-лог Aerial не архивирован:** ошибка `Trust anchor for certification path not found` наблюдалась в logcat, но после перезагрузки буфер очистился, а weekly-кэш не дал повторить запрос; пустое превью подтверждено скриншотами 35 и 50, причина — наблюдением в logcat, не сохранённым файлом.
- **Эмулятор — не доказательство совместимости с ТВ.** Все числа производительности в §9 — только для сравнения сборок между собой, не для обещаний пользователю.

---

## 3. Проверка 11 предыдущих аудитов

Предыдущие документы полезны как карта работ, но не являются доказательством. Ниже — что подтверждено независимо, а что осталось заявкой.

| № | Аудит | Что проверено сейчас | Статус | Что не закрыто |
|---|---|---|---|---|
| 1 | `ARCHITECTURE_AUDIT` | один модуль, package/appId, Compose, `TvCanvas`, `TvScale`, отсутствие второго Focus Engine | **PASS** | нет физического ТВ |
| 2 | `SYSTEM_INTEGRATION_AUDIT` | HOME + LEANBACK filters, banner, leanback `required=false`, настройки системы | **PARTIAL** | фактическая замена HOME на Google TV AVD не работает |
| 3 | `CONTENT_DISCOVERY` | `discovery/` — чистый `buildSnapshot`, правила, Kids/диверсити; Home shelf | **PARTIAL** | нет провайдеров на AVD; Home пустой; пустое состояние отсутствует |
| 4 | `INPUT_FOCUS_AUDIT` | integer cursor + `FocusRequester`, `TvKeyBurstFilter`, `TvKeyRepeat`, BACK, тесты | **PASS** | нет реального пульта/ИК |
| 5 | `STABILITY_AUDIT` | `SafePreferences`, `CrashReport`, ограниченные кэши, quarantine, backoff | **PARTIAL** | нет soak, storage-full, реального сбоя сети; P2-устойчивость не проверена |
| 6 | `QA_AUDIT` | 481 unit, 267 connected, lint, AVD | **PARTIAL** | один AVD; нет физического ТВ, 720/4K, API-матрицы, TalkBack |
| 7 | `SECURITY_AUDIT` | permissions, HTTPS-only media, network config, exported components, no secrets | **PASS / PARTIAL** | TLS-доверие к Apple CDN на AVD не работает; нет pinning; экспортированные системные сервисы требуют review |
| 8 | `BACKUP_RESTORE_MIGRATION` | AES-256-GCM, PBKDF2 210k, лимиты, journal, pre-restore snapshot, Kids PIN не экспортируется, тесты | **PARTIAL** | нет ручного Backup UI, реального SAF, vendor picker, Doze/reboot |
| 9 | `AMBIENT_EXPERIENCE` | `AmbientScreen`, playlist/clock/pixel shift, фото, Reddit, DreamService | **PARTIAL / FAIL на AVD** | превью и, вероятно, клипы пустые из-за TLS; DreamService вручную не проверен |
| 10 | `UPDATE_SYSTEM` | `UpdateManager`, metadata→compat→download→signature→install, `PackageInstaller`, v2, Kids block | **PARTIAL** | `UPDATE_METADATA_URL=""`; реального обновления нет; pre-update restore не проверен |
| 11 | `PRODUCT_EXPERIENCE` | 1231 строк, вердикт 2,6/5, P0/P1 | **FAIL (по выводам)** | P0 Home и P1 Setup/Settings не исправлены; 9 решений владельца не приняты |
| — | `PERFORMANCE_OPTIMIZATION` (14 файлов) | в основном шаблоны и прошлые замеры | **NOT TESTED** | нет актуального TV-замера; §12 честно помечен как template |
| — | `UI_UX_UNIFICATION` (16 файлов) | документы, не код | **NOT TESTED** | единый scaffold не внедрён как обязательный слой |

Вывод: **предыдущие аудиты не отменяют P0**. Они хорошо описывают архитектуру и отдельные подсистемы, но продуктовый слой и проверка на реальном железе остаются незакрытыми.

---

## 4. Матрица прослеживаемости требований

Ниже — сводка требований, которые следуют из аудитов, ТЗ и заявлений продукта. Это не «пожелания», а проверяемые обязательства.

| ID | Требование | Источник | Статус | Доказательство / комментарий |
|---|---|---|---|---|
| R-01 | Package/appId `com.whatuneed.ovisiontv.kotlin` | build | **PASS** | badging, release-check |
| R-02 | versionCode 81 / versionName 0.5.8 | build | **PASS** | badging; но для нового артефакта нужен vc82 |
| R-03 | minSdk 28, compile/target 35 | build | **PASS** | badging |
| R-04 | Запуск на API 28–35 | QA | **NOT TESTED** | только API 34 |
| R-05 | MAIN + LEANBACK_LAUNCHER | manifest | **PASS** | xmltree |
| R-06 | HOME intent filter | manifest | **PASS** | xmltree |
| R-07 | Фактическая замена HOME | TV integration | **FAIL на AVD** | §5.3; при отключённом штатном лаунчере работает |
| R-08 | D-pad/OK/BACK без второго Focus Engine | input | **PASS** | тесты `MainActivityInputTest`, `TopShelfTest` |
| R-09 | Стабильный фокус после resume/menu/search | input | **PASS** | `focusTick`, тесты |
| R-10 | Reduce Motion = системный флаг | motion | **PASS** | `LocalReduceMotion`, `motionDuration` |
| R-11 | Семантика для TalkBack | accessibility | **PARTIAL** | semantics-тесты; голосовой TalkBack не проверен |
| R-12 | Home = poster + title + dock | product | **PARTIAL** | код есть; при отсутствии контента — пусто |
| R-13 | Пустое состояние Home | product P0 | **FAIL** | скриншоты 20, 45 |
| R-14 | Подписи приложений в доке/сетке | product P0 | **FAIL** | на скриншотах только иконки |
| R-15 | Hero не из выдуманного ресурса | product | **PASS** | card-art fallback; не рисуется, если карточек нет |
| R-16 | Нет `APPLICATION` и белой iOS-пилюли | product | **PASS** | код/тесты `TopShelfTest`; визуально подтверждено |
| R-17 | Shelf/rail не рисуется под доком | product | **PASS** | тест `the_browse_home_does_not_draw_the_rail_under_the_dock` |
| R-18 | Аватар из реального `ProfileStore` | product | **PASS** | `activeProfile`, `ProfileCatalog.color` |
| R-19 | Рекомендации только из реальных источников | content | **PARTIAL** | движок чистый; на AVD провайдеров нет |
| R-20 | Kids-профиль скрывает запрещённое | kids | **PASS** | unit/instrumented; ручной сценарий — нет |
| R-21 | Watch Next / continue watching | discovery | **PARTIAL** | модель есть; на AVD нет данных |
| R-22 | Настройки: все разделы доступны | settings | **PASS** | 56 тестов; ручной обход |
| R-23 | Setup: понятный первый запуск | product | **PASS (0.5.10)** | 6 страниц, 25 тестов; ручной проход RU/EN; исторический FAIL 0.5.8 |
| R-24 | Settings IA: Personalization/Device/System | product | **PARTIAL** | работает, но Update/Backup визуально выпадают |
| R-25 | Поиск: BACK сохраняет запрос | product P1 | **PARTIAL** | `BackHandler` есть; продукт-аудит фиксирует потерю запроса; не перепроверено вручную |
| R-26 | Удаление папки: подтверждение | UX | **PASS (код)** | destructive confirm; ручной сценарий — нет |
| R-27 | Профили: создание/переключение | profiles | **PASS** | instrumented `ProfileStoreTest` |
| R-28 | Backup: AES-256-GCM + PBKDF2 | backup | **PASS** | `BackupEncryption`, тесты |
| R-29 | Backup: лимиты, journal, rollback | backup | **PARTIAL** | тесты; реальный SAF/Doze — нет |
| R-30 | Backup: Kids PIN не экспортируется | backup | **PASS** | код/тесты |
| R-31 | Ambient: единый экран, без второго движка | ambient | **PASS** | `AmbientScreen`, `SaverScreen` |
| R-32 | Ambient: 100 клипов, темы, Pixel Shift | ambient | **PASS** | каталог/тесты |
| R-33 | Ambient: превью показывает кадр | ambient | **FAIL на AVD** | пустое превью, TLS |
| R-34 | DreamService зарегистрирован | ambient | **PASS (manifest)** | `AerialDreamService` |
| R-35 | DreamService реально запускается системой | ambient | **NOT TESTED** | нет проверки «Спящий режим» на ТВ |
| R-36 | Photo/Reddit источники | ambient | **PARTIAL / NOT TESTED** | код есть; live-поведение не проверено |
| R-37 | Update: metadata→compat→download→signature→install | update | **PASS (код)** | `UpdateManager` |
| R-38 | Update: не обещать silent install | update | **PASS** | `PackageInstaller`, UI |
| R-39 | Update: SHA-256 не заменяет подпись | update | **PASS** | `UpdateSignatureVerifier` + system installer |
| R-40 | Update: Kids Mode блокирует | update | **PASS** | код/тесты |
| R-41 | Update: источник настроен | update | **FAIL** | `UPDATE_METADATA_URL=""`; UI честно говорит «not configured» |
| R-42 | HTTPS-only media, cleartext=false | security | **PASS** | `network_security_config`, `AerialCatalog` |
| R-43 | Нет `QUERY_ALL_PACKAGES` | security | **PASS** | permissions |
| R-44 | Нет лишних опасных permissions | security | **PASS** | 8 permissions, location maxSdk 32 |
| R-45 | Нет секретов/ключей в APK | security | **PASS** | grep, нет keystore в APK |
| R-46 | Экспортированные компоненты защищены | security | **PASS / review** | accessibility/dream/device-admin — system BIND; Main/Settings — intended |
| R-47 | Privacy policy соответствует коду | privacy | **PASS** | raw privacy RU/EN; permissions/endpoints совпадают |
| R-48 | Crash report локальный, без upload | stability | **PASS** | `CrashReport` |
| R-49 | RU/EN parity | localization | **PASS** | 639/639 (0.5.9) |
| R-50 | 16 KB page alignment | build | **PASS** | zipalign/native libs |
| R-51 | APK Signature Scheme v2 | build | **PASS** | apksigner |
| R-52 | Release artifact воспроизводим из HEAD | release | **PASS (0.5.9)** | `07bfef3`, vc82, чистый build; исторический FAIL 0.5.8 — §5.2 |
| R-53 | Git tag / release notes | release | **PARTIAL (0.5.9)** | локальный тег `v0.5.9`; README/CHANGELOG/release notes на GitHub ещё нет |
| R-54 | License/NOTICE | legal | **PARTIAL** | Apache + Aerial GPL/Apple; NOTICE не в APK |
| R-55 | Weather attribution | legal | **FAIL / review** | Open-Meteo/MET/Nominatim не имеют видимой ссылки/лицензии |
| R-56 | Физическое устройство/720/4K | QA | **NOT TESTED** | §8 |
| R-57 | TalkBack voice | QA | **NOT TESTED** | semantics only |
| R-58 | 24h soak / storage full | stability | **NOT TESTED** | нет |

---

## 5. P0 — блокеры релиза

### 5.1. P0-1. Home пустой, без подписей и без пустого состояния

**Статус:** подтверждён на 0.5.8; **закрыт в 0.5.9** (`EmptyHome`, подписи под плитками, тесты; см. §0 и `evidence/52`, `53`). Раздел сохранён как история аудита.

**Воспроизведение:**

1. Установить release APK на чистый AVD.
2. Пройти Setup (9 экранов; на шаге рекомендаций оставить доступ как есть / не выдавать провайдерский контент).
3. Нажать «Open Home».
4. Наблюдать: чёрный экран, часы/аватар вверху справа, док с иконками; больше ничего.

**Доказательства:**

- `evidence/20-release-home.png` — первый Home после Setup;
- `evidence/45-home-with-stock-disabled.png` — тот же пустой Home, когда OVisionTV принудительно сделан HOME;
- `evidence/19-setup-step9.png` — итог Setup прямо говорит: `Shelf: recommendations on, continue watching on`;
- `TopShelf.kt` возвращает пустоту, если нет banner и нет rails; `HomeScreen` не рисует пустое состояние; док не содержит текстовых подписей.

**Почему это P0, а не «косметика»:**

- это первый экран после установки; пользователь не понимает, работает ли лаунчер;
- приложения в доке нельзя опознать без подписей;
- нет hero, нет строки приложений, нет подсказки «разрешите рекомендации»;
- итог Setup противоречит фактическому экрану;
- продукт-аудит уже ставил это в P0; после commit `1dcc024` hero/rail появились только при наличии контента, но пустое состояние не добавлено.

**Причина (подтверждённая):** отсутствует ветка пустого состояния; `TopShelf` рассчитан на непустой `shelf`; подписи в доке не реализованы. Это не TLS и не ошибка рендера.

**Что нужно:**

1. Настоящее пустое состояние: заголовок, короткое объяснение, действие «Настроить рекомендации» / «Открыть приложения», без выдуманного контента.
2. Подписи под иконками дока (и, если сетка появится, под плитками).
3. Не возвращать бессмысленный `APPLICATION`; использовать название приложения/раздела.
4. Проверить три сценария: нет permissions; permissions есть, но провайдеров нет; провайдеры есть.
5. Скриншот-тест/Compose-тест на пустой Home, а не только на непустой shelf.

**Критерий закрытия:** на чистом AVD без рекомендаций Home содержит осмысленный заголовок, подписи приложений и действие; ни один элемент не выглядит «сломанным».

---

### 5.2. P0-2. Проверенный APK собран из грязного дерева и не соответствует HEAD

**Статус:** подтверждён на 0.5.8; **закрыт в 0.5.9**: коммит `07bfef3`, тег `v0.5.9`, vc82, чистый `assembleRelease`, новый SHA (см. §0, §11). Раздел сохранён как история аудита.

**Факты:**

- `HEAD = 1dcc024`.
- `git status` до сборки/после: `M app/src/main/java/.../ui/HomeScreen.kt`, `M app/src/main/java/.../ui/components/TopShelf.kt`.
- Дифф: `HomeScreen.kt` +6/−1, `TopShelf.kt` +4/−1 (weather pill / `headingTop`).
- Исходники изменены в 15:49, APK собран в 15:57 — то есть APK содержит незакоммиченные изменения.
- Проверенный APK: **4 863 242 B**, SHA-256 `a4059d96952e0410da5a972e023f6a12dee4318f3ea9efad2fc7c1c5e315e038`.
- Ранее заявленный финальный артефакт 0.5.8: **4 863 266 B**, SHA-256 `c084f81b7bb965307f6999a28d752cba5a8e600cd6c80e7fad475d9a04be8aba`; в этом аудите он **не пересобран и не проверен**.
- versionCode остался **81**, хотя UI-изменение уже внесено.

**Почему это P0:**

- аудит и скриншоты относятся к сборке, которой нет в коммите;
- нельзя доказать, что опубликованный APK соответствует исходникам;
- обновление с тем же versionCode не увидит разницу;
- SHA-256 артефакта не совпадает с обещанным SHA финального релиза;
- это классический release-integrity дефект, даже если сам код хороший.

**Что нужно:**

1. Решить судьбу незакоммиченных `HomeScreen.kt`/`TopShelf.kt`: закоммитить или откатить.
2. Поднять versionCode до **82** (и, при необходимости, versionName).
3. Собрать release из чистого дерева, сохранить git tag.
4. Заново прогнать `release_check.py`, unit, connected, lint, установку.
5. Записать в отчёт новый SHA и сертификат; не переиспользовать `c084…`/`a405…` как «финальный» без пересборки.

**Критерий закрытия:** `git status --short` пуст (кроме намеренно untracked docs), тег указывает на коммит, из которого собран APK; SHA и vc в отчёте совпадают.

---

### 5.3. P0-3. На Google TV AVD приложение не становится фактическим HOME

**Статус:** подтверждён на AVD; причина не доказана как дефект приложения, поэтому это блокер проверки, а не обязательно код-баг.

**Что наблюдалось:**

1. В системном `Default home app` OVisionTV **виден** и выбирается радиокнопкой (`evidence/36`, `40`).
2. `cmd role get-role-holders android.app.role.HOME` после shell-назначения показывает `com.whatuneed.ovisiontv.kotlin`; при этом resolver всё равно возвращает штатный launcher (`evidence/51`).
3. После перезагрузки `FallbackHome` пишет `User unlocked and real home found; let's go!`, но запускает **`com.google.android.tvlauncher/.MainActivity`** (`evidence/49`).
4. Нажатие HOME из OVisionTV возвращает штатный Google TV Launcher (`evidence/42`, `44`).
5. Если **отключить** штатный лаунчер (`pm disable-user com.google.android.tvlauncher`), OVisionTV действительно становится HOME (`evidence/45`), но его Home всё равно пустой (P0-1).
6. У штатного лаунчера HOME-фильтр имеет `android:priority="2"` (`evidence/47`); у OVisionTV приоритет не задан.
7. В Settings → System строка `Default Home App` показывает **No** (`evidence/25`), то есть приложение само признаёт, что не является домашним.

**Интерпретация:**

- приложение **квалифицировано** как HOME (иначе его не было бы в picker и оно не работало бы при отключённом штатном лаунчере);
- на этом Google TV образе штатный launcher/`FallbackHome` удерживает HOME, несмотря на роль;
- это может быть особенность AVD/Google TV Launcher, а может быть реальной проблемой на части ТВ;
- **нельзя заявлять «работает как замена HOME на Android TV»**, пока это не проверено на физическом устройстве целевой модели.

**Почему P0:** лаунчер, который не становится HOME, не выполняет свою основную функцию. Даже если причина вендорская, релизный claim должен быть снят или подтверждён на железе.

**Что нужно:**

1. Повторить на 2–3 физических ТВ (Google TV, Android TV Sony/Philips/TCL, бюджетный 1 GB).
2. Проверить `directBootAware`, роль HOME, `FallbackHome`, OEM-политику.
3. Если проблема только на AVD — задокументировать и не считать дефектом, но добавить в support «как выбрать Home».
4. Если проблема воспроизводится на ТВ — исследовать `priority`/role qualification и исправить.
5. Не обещать автоматическую замену HOME в Setup, пока не подтверждено.

**Критерий закрытия:** на целевом ТВ после выбора OVisionTV и перезагрузки HOME открывает OVisionTV; либо официально подтверждено, что модель не позволяет сторонний HOME, и UI честно это объясняет.

---

## 6. P1/P2/P3 — оставшиеся риски

### 6.1. P1

| ID | Находка | Доказательство | Влияние | Что делать |
|---|---|---|---|---|
| P1-01 | Aerial-превью пустое; TLS к CDN Apple не проходит на AVD | `evidence/35`, `50`; в logcat `AerialRefresh: Trust anchor... not found` (лог не архивирован). **В 0.5.9 симптом устранён:** HTTP-исключение для `sylvan.apple.com`, `evidence/54`, `55` | на 0.5.8 заставка/превью выглядели сломанными. Остаётся риск cleartext-инъекции и непроверенное полноэкранное воспроизведение | не расширять HTTP на другие хосты; проверить на физическом ТВ; добавить честное состояние ошибки и локальный fallback без выдуманного контента |
| P1-02 | **Закрыто в 0.5.10.** Setup — 6 страниц; язык+профиль и Home+погода объединены; темы убраны в Settings; режим заставки/музыка добавлены | `SetupScreenTest` 25/25, полный 277/277; `evidence/58–60` | на 0.5.8 первый запуск был слишком длинным | оставить шесть страниц; не возвращать отдельную страницу тем |
| P1-03 | Нет физического ТВ, 720p/4K, API 28–35, TalkBack, ARM, 1 GB | §8 | нельзя утверждать совместимость | пройти матрицу до публикации; strict statuses |
| P1-04 | Нет README, CHANGELOG, release notes, CI, git tag, support | repo | пользователь не знает, как ставить/обновлять/восстанавливать; нет трассируемости | README + CHANGELOG + tag + release notes + issue/support |
| P1-05 | Updater не работает: `UPDATE_METADATA_URL=""` | `BuildConfig`, UI 26, `UpdateAutoCheck` | нет пути обновления; релиз останется ручным | опубликовать HTTPS metadata/APK, проверить реальный flow, pre-update backup, откат |
| P1-06 | Визуальный разрыв Settings / Software Update / Backup; `Recommendations on` при пустом Home; `Device: AOSP TV on x86` | 23, 25, 26; продукт-аудит | ощущение «набора экранов», а не системы; ложный статус | единый `TvPageScaffold`, честные статусы, единые отступы/типографика |
| P1-07 | TalkBack voice не проверен | semantics-тесты | доступность не доказана | пройти TalkBack на ТВ/эмуляторе, проверить порядок и озвучку |
| P1-08 | Нет видимой атрибуции погоды Open-Meteo/MET Norway/Nominatim | privacy 31, `weather_source_*` | лицензионный риск при публикации | добавить «Weather data by Open-Meteo.com» + ссылку на CC BY 4.0; MET/OSM attribution |
| P1-09 | Юридический статус AerialViews GPL-3.0 каталога и Apple-клипов не подтверждён | `NOTICE`, `open_source_licenses.txt` | возможен блок публикации/жалоба правообладателя | юридическая проверка; при необходимости — собственный каталог/локальные ассеты с правом использования |
| P1-10 | `NOTICE` с Hume/Petrobot не входит в APK; в in-app licenses его нет | `NOTICE` в корне, `open_source_licenses.txt` | Apache-2.0 §4(d) риск | включить NOTICE в APK/лицензии или подтвердить отсутствие обязательства |

### 6.2. P2

| ID | Находка | Влияние | Приоритет |
|---|---|---|---|
| P2-01 | Только v2-подпись; v1/v3/v3.1/v4 отсутствуют | нет key rotation; старые API (<24) не нужны, но v3 желателен | P2 |
| P2-02 | R8 mapping не сохранён/не опубликован | нельзя разобрать краш-стек | P2 |
| P2-03 | Нет baseline profile, хотя `profileinstaller` подключён | холодный старт можно улучшить | P2 |
| P2-04 | Нет certificate pinning для Apple/погоды | MITM-риск снижен системным TLS, но pinning нет | P2 |
| P2-05 | `AerialDreamService` exported с `BIND_DREAM_SERVICE` | системный вызов expected; проверить, что стороннее приложение не может вызвать | P2 |
| P2-06 | `OVisionAccessibilityService` exported, очень мощный | требует явного включения; высокий риск | P2 / security review |
| P2-07 | Нет runtime resize без restart | смена 720/1080 на лету может ломать `TvCanvas` | P2 |
| P2-08 | Нет SCA/CVE-сканирования зависимостей | unknown vuln | P2 |
| P2-09 | Нет 24h soak, storage-full, fuzz | устойчивость не доказана | P2 |
| P2-10 | Нет RTL, реального ИК/BT-пульта, HDMI-CEC | совместимость не доказана | P2 |
| P2-11 | Weather/Nominatim/Reddit live не проверены | юридические/функциональные сюрпризы | P2 |
| P2-12 | `Product Experience` и `RELEASE_READINESS` не в git | аудит не воспроизводим из репозитория | P2 |

### 6.3. P3

- Косметика/метаданные: `Device: AOSP TV on x86` на реальном ТВ может выглядеть странно.
- Нет скриншотов в README/Store.
- Нет шаблона issue/PR.
- Нет changelog для 0.5.8.
- Нет пояснения, что Aerial — внешний CDN и может быть недоступен.
- Нет пользовательской инструкции по Backup/restore.

---

## 7. Функциональный regression checklist

Проверка не «по документам», а по фактическому поведению и тестам. Где ручного прогона не было — так и написано.

| Сценарий | Ожидание | Факт | Статус |
|---|---|---|---|
| Чистая установка release | APK ставится, Setup открывается | ставится после удаления debug; Setup 9 экранов | **PASS** |
| Первый запуск | понятный onboarding, можно пропустить лишнее | 9 шагов, Skip нет | **FAIL (P1)** |
| Setup: язык/профиль/погода/заставка | значения сохраняются | сохраняются; итог 19 | **PASS** |
| Первый Home | hero + подписи + dock | пусто: часы/аватар/dock | **FAIL (P0)** |
| Home как HOME | лаунчер открывается по HOME | на AVD открывается штатный; при отключённом — OVisionTV, но пустой | **FAIL / PARTIAL (P0)** |
| Dock: запуск приложения | приложение открывается | тесты/ручной запуск YouTube до этого работали; в текущем аудите не перепроверялся | **PARTIAL** |
| Подписи приложений | пользователь видит названия | отсутствуют | **FAIL (P0)** |
| Up → shelf | появляется shelf с title/rail | код/тесты есть; при пустом контенте — нечего показывать | **PARTIAL** |
| Shelf: BACK | возврат в browse-Home | тесты есть; вручную не перепроверено | **PARTIAL** |
| Shelf: rail не под доком | нет наложения | тест `TopShelfTest` 9/9 | **PASS** |
| Dock: фокус | увеличение, ровный D-pad | тесты/скриншоты | **PASS** |
| Settings root | 5 строк, переходы | открывается (23) | **PASS** |
| Settings: 56 тестов | все строки/страницы | 56/56 | **PASS** |
| Settings → System | Update/Default Home/Backup | открывается (25) | **PASS** |
| Software Update | честно «not configured» | 26 | **PASS (честность) / FAIL (функция)** |
| Backup UI | создать/восстановить | вручную не открыт; `27` — не Backup | **NOT TESTED** |
| Backup crypto | AES/PBKDF2/journal | тесты | **PASS (код)** |
| Профили | создать/переключить | instrumented 4 + Setup | **PASS** |
| Kids | PIN, lockout, скрытие | 8 instrumented | **PASS (тесты)** |
| Поиск | открыть/ввести/BACK | 12 тестов; потерю запроса не перепроверял | **PARTIAL** |
| Папки | создать/переименовать/удалить | тесты есть; destructive confirm | **PARTIAL** |
| Погода | включить, город, прогноз | код/тесты; live не проверен | **NOT TESTED** |
| Control Center | часы/сеть/аватар | 9 тестов; визуально | **PASS** |
| Ambient | превью/клипы | пусто на AVD | **FAIL (P1)** |
| DreamService | системная заставка | не проверялся | **NOT TESTED** |
| Photos/Reddit | источник/плейлист | не проверялся live | **NOT TESTED** |
| Пульт: UP/DOWN/LEFT/RIGHT/OK/BACK | без залипаний | тесты, 400/100/1200 ms | **PASS (тесты)** |
| Реальный ИК/BT-пульт | тот же результат | не проверялся | **NOT TESTED** |
| HOME key | возврат в лаунчер | штатный на AVD | **FAIL на AVD** |
| Offline | понятная ошибка, без краша | не проверялся | **NOT TESTED** |
| Storage full | без потери данных | не проверялся | **NOT TESTED** |
| Corrupt prefs | quarantine, без wipe | unit-тесты `SafePreferences` | **PASS (тесты)** |
| Resume/relaunch | состояние/focus | тесты | **PASS** |
| Смена языка | RU/EN parity | 637/637; тесты | **PASS** |
| 720p | читаемость | не проверялся в release | **NOT TESTED** |
| 4K | raster/память | не проверялся | **NOT TESTED** |
| TalkBack | озвучка/порядок | не проверялся | **NOT TESTED** |
| RTL | layout | не проверялся | **NOT TESTED** |

---

## 8. Матрица совместимости устройств

Строгие категории: **VERIFIED** — только там, где был реальный запуск; **NOT TESTED** — не «должно работать»; **N/A** — не применимо.

| Платформа / условие | Статус | Что именно проверено / почему нет |
|---|---|---|
| AVD `hume-tv`, Android 14 / API 34, x86, 1080p | **VERIFIED (partial)** | install, launch, Setup, Home, Settings, Update, Ambient; но P0 Home, TLS Aerial, HOME-роль |
| Физический Android TV (Sony/Philips/TCL/Hisense) | **NOT TESTED** | нет устройства |
| Google TV / Chromecast | **NOT TESTED** | AVD использует Google TV Launcher; поведение HOME не подтверждено |
| Amazon Fire TV | **NOT TESTED** | нет устройства; Google-сервисы могут отсутствовать, но лаунчер не проверялся |
| NVIDIA Shield | **NOT TESTED** | нет устройства |
| API 28 (Android 9) | **NOT TESTED** | minSdk 28, но запуск не проверен |
| API 29–33 | **NOT TESTED** | нет прогонов |
| API 35 | **NOT TESTED** | targetSdk 35, но нет устройства/эмулятора API 35 |
| ARM32 | **NOT TESTED** | APK содержит libs; запуск не проверен |
| ARM64 | **NOT TESTED** | то же |
| 1 GB RAM | **NOT TESTED** | нет устройства; memory pressure не проверен |
| 720p | **NOT TESTED** | в release не проверялся; старые debug-скриншоты не считаются |
| 1080p | **VERIFIED (partial)** | AVD 1920×1080 |
| 4K / HDR / 10-bit | **NOT TESTED** | нет панели/контента; raster не измерен |
| Overscan / safe area | **NOT TESTED** | нет реального ТВ |
| RTL | **NOT TESTED** | нет прогона |
| TalkBack | **NOT TESTED** | только semantics-тесты |
| ИК-пульт | **NOT TESTED** | только adb keyevent |
| Bluetooth-пульт | **NOT TESTED** | нет |
| HDMI-CEC | **NOT TESTED** | нет |
| Ethernet-only | **NOT TESTED** | AVD сеть эмулируется |
| SAF-провайдер (USB/SD/облако) | **NOT TESTED** | Backup UI вручную не открыт |
| Doze / Alarm / reboot backup | **NOT TESTED** | нет длительного прогона |
| 16 KB page size | **VERIFIED (build)** | zipalign/native libs; runtime не проверялся |
| Play Store / Google TV policy | **N/A** | публикация не в Play; лицензионные требования всё равно действуют |

---

## 9. Производительность

### 9.1. Измерено

На AVD x86, release APK, software GPU:

| Метрика | Значение | Комментарий |
|---|---|---|
| Cold start, `am start -W` | **891 ms**, **931 ms**, **772 ms** | 3 запуска; эмулятор, не ТВ |
| WaitTime | 898 / 938 / 779 ms | то же |
| PSS после старта | **51 114 KB** | `dumpsys meminfo` |
| RSS | **174 296 KB** | то же |
| Java Heap | 3 632 KB | то же |
| Native Heap | 26 472 KB | то же |
| gfxinfo | 12 frames, **83.33% janky**, 90th 450 ms, 95th 1150 ms | software GPU; **не product metric** |
| APK size | **4 863 242 B** | ~4.64 MiB |
| Native libs | только `libandroidx.graphics.path.so`, 4 ABI | нет media/ffmpeg |

### 9.2. Чего нет

- нет замера на физическом ТВ;
- нет 720p/1080p/4K rasterization;
- нет измерения памяти при 100 постерах/клипах/фото;
- нет 24h soak, memory leak, thermal throttling;
- нет baseline profile и startup tracing;
- нет сравнения debug/release на одном устройстве;
- нет измерения сети/загрузки Aerial/погоды/Reddit;
- нет Perfetto/ systrace.

**Вывод:** числа 772–931 ms и 51 MB — **только эмулятор**. Нельзя писать «быстрый старт» или «экономная память» в release notes. Для релиза нужен замер на целевом ТВ; текущий gfxinfo с 83% janky вообще нельзя использовать как отрицательный вывод, потому что GPU программный.

---

## 10. Безопасность, приватность и распространение

### 10.1. Permissions (фактические, из APK)

| Permission | Зачем | Оценка |
|---|---|---|
| `INTERNET` | Aerial, погода, Reddit, обновления | нужно |
| `ACCESS_NETWORK_STATE` | Control Center/сеть | нужно |
| `READ_TV_LISTINGS` | рекомендации/каналы | опционально; можно отозвать |
| `ACCESS_WIFI_STATE` | имя Wi-Fi | нужно для tile |
| `NEARBY_WIFI_DEVICES` (`neverForLocation`) | имя Wi-Fi на Android 13+ | узкое, ок |
| `ACCESS_FINE_LOCATION` (`maxSdk=32`) | имя Wi-Fi на старых ТВ | ограничено; ок |
| `ACCESS_COARSE_LOCATION` (`maxSdk=32`) | то же | ограничено; ок |
| `REQUEST_INSTALL_PACKAGES` | in-app update | опасно, но нужно; Kids block |

Нет `QUERY_ALL_PACKAGES`, нет storage/camera/mic/contacts. Это **PASS** по least privilege.

### 10.2. Сеть и TLS

- `network_security_config.xml`: `cleartextTrafficPermitted="false"`, system anchors.
- `AerialCatalog` переводит `http://sylvan.apple.com/` в HTTPS и отбрасывает cleartext.
- **Aerial на AVD не работает:** `Trust anchor for certification path not found`; превью пустое. Это не «разрешить HTTP», а проблема trust store/цепочки. Ослаблять HTTPS нельзя; нужно честное состояние ошибки и, при необходимости, собственный каталог с легальными локальными кадрами.
- Нет certificate pinning; для публичного релиза — P2.

### 10.3. Экспортированные компоненты

| Компонент | Защита | Оценка |
|---|---|---|
| `MainActivity` | exported, HOME/LEANBACK | intended |
| `SettingsActivity` | exported, LEANBACK | intended; проверяет Kids grant |
| `SetupActivity` | exported=false | ок |
| `SoftwareUpdateActivity` | exported=false | ок |
| `OVisionAccessibilityService` | `BIND_ACCESSIBILITY_SERVICE` | system-only; высокий риск, но user-enabled |
| `AerialDreamService` | `BIND_DREAM_SERVICE` | system-only |
| `SleepReceiver$Admin` | `BIND_DEVICE_ADMIN` | system-only |
| `BackupAlarmReceiver` | exported=false | ок |
| `InstallResultReceiver` | exported=false | ок |
| `ProfileInstallReceiver` | `DUMP` | standard AndroidX; проверить, не лишний ли |

Нет unprotected exported receiver/provider. **PASS** с P2-review для accessibility/dream/device-admin.

### 10.4. Backup

- AES-256-GCM + PBKDF2-HMAC-SHA256 210 000;
- лимиты: 32 MB archive, 64 MB uncompressed, 256 entries, ratio 100;
- pre-restore snapshot + `journal.json`;
- Kids PIN не экспортируется;
- `StorageGate` read/write;
- тесты `BackupCodecTest`, `BackupManagerInstrumentedTest`, `BackupScreenTest`.

**PARTIAL:** нет реального SAF/OEM picker/Doze/reboot; Backup UI вручную не открыт; `27-release-backup.png` — не Backup.

### 10.5. Обновления

- `UpdateManager` оркестрирует metadata→compat→download→signature→install;
- `UpdateSignatureVerifier` проверяет size/SHA-256/package/versionCode/versionName/cert; финальная криптография — system installer;
- `PackageInstaller.Session`, `PendingIntent.FLAG_MUTABLE`, `exported=false`;
- Kids Mode блокирует;
- `UPDATE_METADATA_URL=""` → `NOT_CONFIGURED`; UI честно говорит, что источник не настроен.

**PARTIAL:** функция не работает по назначению, пока владелец не опубликует HTTPS metadata; реальный GitHub flow не проверен; pre-update restore не проверен.

### 10.6. Секреты и приватность

- grep не нашёл API-ключей/токенов/private key;
- `password = ""` — поля UI; тестовый пароль — только тест;
- keystore вне APK; `keystore.properties` в `~/.ovisiontv`;
- privacy RU/EN называет Aerial/Open-Meteo/MET/wttr/ipapi/ipwho/geojs/Nominatim/Reddit/Photos;
- нет аналитики/трекеров/аккаунта;
- crash report локальный.

**PASS** по секретам; **PARTIAL** по внешним сервисам: их условия/атрибуция не проверены.

### 10.7. Распространение

- прямое APK, не Play;
- подпись v2, сертификат `327a5045…7b10`, RSA 4096;
- нет CI/tag/README/release notes;
- GPL/Apple/погодные лицензии не закрыты;
- `REQUEST_INSTALL_PACKAGES` требует объяснения пользователю.

**PARTIAL / FAIL** для публичного релиза.

---

## 11. Сборка и артефакт

### 11.1. Что получилось

| Параметр | Значение |
|---|---|
| Команда | `:app:assembleRelease` после `gradlew --stop` и удаления `app/build` |
| Результат | **BUILD SUCCESSFUL** |
| APK | `app/build/outputs/apk/release/app-release.apk` |
| Размер | **4 881 010 B** |
| SHA-256 | `b12a267b245b7e1b77f07cb05deeb3d8264e56e8a0fd2a42ebb5a00aabaa7696` |
| versionCode / Name | **82 / 0.5.9** |
| appId | `com.whatuneed.ovisiontv.kotlin` |
| min / target | 28 / 35 |
| Подпись | v1 **false**, v2 **true**, v3/v3.1/v4 **false** |
| Cert SHA-256 | `327a50453093b783e84b1f2645b03319477d89966ea6a653dab53d5a567d7b10` |
| Cert DN | `CN=OVisionTV Launcher, O=OVisionTV, C=RU` |
| Key | RSA 4096 |
| zipalign 16KB | `Verification successful` |
| Release check | EXIT=0, RU/EN 639 |
| Lint | 0 Error / 19 Warning / 5 Info |
| Unit | **481 debug + 481 release**, 0 fail/skip |
| Connected | **272 / 272**, 0 fail, 6m20s |

### 11.2. Артефакт соответствует HEAD после 0.5.9 (историческая проблема 0.5.8)

Для 0.5.8 release provenance был **FAIL**, см. §5.2: HEAD `1dcc024` не содержал незакоммиченные `HomeScreen.kt`/`TopShelf.kt`, APK собран после их изменения, SHA `a405…` ≠ `c084…`, versionCode не повышен, тега не было.

Исправлено в 0.5.9: изменения закоммичены (`07bfef3`), создан локальный тег `v0.5.9`, versionCode 82, release собран из чистого дерева; новый SHA `b12a267b…7696`. **Для 0.5.9 provenance — PASS** при условии, что тег не перезаписывается, а APK публикуется именно из `07bfef3`.

### 11.3. Чего нет

- нет AAB (для direct APK не обязательно);
- нет R8 mapping в артефактах;
- нет baseline profile;
- нет CI;
- нет reproducible-build настроек;
- нет SCA/CVE;
- нет tag/release notes.

---

## 12. Лицензии и атрибуция

### 12.1. Что есть

- `LICENSE` — Apache 2.0;
- `NOTICE` в корне: based on Hume Launcher by Petrobot; AerialViews GPL-3.0-derived catalogue; Apple videos streamed, not bundled;
- `res/raw/open_source_licenses.txt` — Apache + AerialViews/Apple;
- `res/raw/aerial_sources_ru/en.txt` — источник Aerial;
- privacy/terms в приложении.

### 12.2. Риски

1. **AerialViews GPL-3.0.** Каталог (`assets/aerial_catalog.json`) заявлен как derived from GPL-3.0 project; в APK нет исходников AerialViews. Это может быть допустимо для данных, а может требоваться GPL-совместимая лицензия/исходники. **Нужна юридическая проверка.** Это P1 для публичного релиза.
2. **Apple Aerial videos.** Клипы © Apple, стримятся с CDN, не входят в APK. Условия использования CDN/контента не проверены; публичный релиз может получить претензию. **P1.**
3. **Hume NOTICE.** `NOTICE` не входит в APK; in-app licenses не содержит Hume/Petrobot. Apache-2.0 §4(d) требует сохранять NOTICE. **P1/P2.**
4. **Погода.** Open-Meteo (CC BY 4.0) требует attribution + ссылку; MET Norway — credit; Nominatim — © OpenStreetMap contributors/ODbL. В UI есть только названия источников, без ссылок/лицензий. **P1/P2.**
5. **Reddit.** RSS/контент и условия использования не проверены; нужен User-Agent/атрибуция. **P2.**
6. **Шрифты/арт.** Заявлено, что сторонних шрифтов/арта нет; проверено по `assets`/`res` выборочно. **PASS с оговоркой.**

**Вывод:** техническая лицензия Apache оформлена, но **публичный релиз с Aerial/погодой юридически не закрыт**. Это самостоятельный NO-GO-фактор для публикации, даже если P0 UI исправить.

---

## 13. Документация и поддержка

| Документ | Статус |
|---|---|
| README (установка, требования, управление, ограничения) | **нет** |
| CHANGELOG / release notes 0.5.8 | **нет** |
| Git tag | **нет** |
| CI (build/test/lint/release) | **нет** |
| Инструкция по Backup/Restore | **нет** (только in-app) |
| Инструкция по обновлению / GitHub metadata | **нет** |
| Support/issue tracker/контакт | **нет** |
| Известные ограничения (Aerial CDN, HOME, permissions) | **нет** |
| Скриншоты для пользователя | **нет** |
| In-app privacy/terms/licenses | **есть** |
| FIXES.md | **есть**, внутренний |

Для проекта, который распространяется APK-файлом, отсутствие README/CHANGELOG/тега — не «мелочь»: пользователь не может проверить версию, не знает, как откатиться, и не понимает, почему Aerial/погода могут не работать. **P1.**

---

## 14. UX final quality gate и scorecard

### 14.1. Проверка parity-направления Home (что уже хорошо)

| Проверка | Статус |
|---|---|
| Dock как на tvOS, без второго Focus Engine | **PASS** |
| Профиль-капсула с реальным аватаром | **PASS** |
| Нет белой iOS-пилюли | **PASS** |
| Нет заголовка `APPLICATION` | **PASS** |
| Rail не рисуется под доком | **PASS** |
| Hero не из выдуманного ресурса; card-art fallback | **PASS (код)** |
| Заголовок отражает выбранный контент | **PASS (код)** |
| Пустой Home | **FAIL** |
| Подписи приложений | **FAIL** |

То есть направление «минимум отличий от tvOS» выбрано верно, но **browse-Home без контента не доведён**. Не нужно копировать tvOS буквально; нужен честный пустой экран и названия приложений.

### 14.2. Scorecard 0–5

| № | Область | Оценка | Обоснование |
|---|---|---|---|
| 1 | Архитектура | **4.0** | чистое разделение, один Focus Engine, понятные слои |
| 2 | TV-интеграция | **2.5** | filters/banner есть; HOME на AVD не работает |
| 3 | Контент и Home | **1.0** | пустой Home, нет подписей, нет empty state |
| 4 | Пульт и фокус | **4.0** | тесты, integer cursor, key repeat |
| 5 | Стабильность | **3.5** | SafePreferences/caches/quarantine; нет soak |
| 6 | QA и тесты | **3.0** | 481+267 зелёные; один AVD, нет железа |
| 7 | Security/privacy | **3.5** | least privilege, HTTPS, нет секретов; TLS/правовые оговорки |
| 8 | Backup | **3.0** | крипто/журнал/тесты; нет SAF/Doze/manual |
| 9 | Ambient | **2.0** | движок есть; превью пустое, DreamService не проверен |
| 10 | Updates | **2.0** | код честный; источник пуст, flow не проверен |
| 11 | Product/UX | **1.5** | Setup 9 шагов, пустой Home, разрыв экранов |
| 12 | Performance | **1.5** | только эмулятор; нет TV/720/4K |
| 13 | Accessibility | **2.0** | semantics есть; TalkBack voice нет |
| 14 | Distribution/licensing | **1.5** | нет tag/docs/CI; GPL/Apple/погода не закрыты |
| 15 | Documentation/support | **0.5** | README/CHANGELOG/support отсутствуют |

**Средневзвешенная оценка: 2.4 / 5.** Это согласуется с продуктовым аудитом (2,6/5), но финальная готовность ниже из-за provenance, HOME и Aerial.

### 14.3. UX gate

- Hero/типографика/док — направление верное, но **неполное**.
- Empty/error/loading — **FAIL**: пустой Home и пустое Aerial-превью.
- 720p — **NOT TESTED**.
- Focus — **PASS** по тестам.
- TalkBack — **NOT TESTED**.
- Setup — **FAIL** по продукту (9 шагов, нет Skip).
- Consistency — **FAIL**: Settings / Update / Backup / Setup выглядят как разные продукты.
- Никаких новых фич в аудите не добавлялось; рекомендации — только исправление существующего.

---

## 15. План исправлений и критерии выхода

### Этап A — до любой публикации (P0)

1. **A-1. Зафиксировать исходники.**
   - Решить: закоммитить weather/title fix или откатить.
   - Поднять `versionCode` до 82.
   - Собрать release из чистого дерева; поставить tag.
   - Пересобрать и заново получить SHA/сертификат.
   - Прогнать release_check, unit, connected, lint, установку.
   - **Выход:** SHA в отчёте = SHA APK; `git status` чист; tag указывает на коммит сборки.

2. **A-2. Пустой Home.**
   - Empty state + подписи приложений + действие «настроить рекомендации».
   - Проверить 3 сценария: нет permissions / нет провайдеров / есть провайдеры.
   - **Выход:** на чистом AVD Home не пустой и не выглядит сломанным; скриншот-тест.

3. **A-3. HOME-роль.**
   - Проверить на 2–3 физических ТВ.
   - Если AVD-специфично — задокументировать; если воспроизводится — исправить/снять claim.
   - **Выход:** HOME открывает OVisionTV на целевом ТВ либо официально подтверждено ограничение с честным UI.

### Этап B — P1 до публичного релиза

4. **Aerial fallback.** Не ослаблять HTTPS; добавить состояние ошибки/диагностику/локальный легальный fallback; проверить на ТВ.
5. **Setup.** Сократить до 3–4 обязательных шагов, остальное — Skip; не обещать контент, которого нет.
6. **Attribution/legal.** Open-Meteo/MET/Nominatim; Hume NOTICE; GPL/Apple review; Reddit terms.
7. **Docs/CI/tag.** README, CHANGELOG, release notes, инструкция Backup/Update, issue/support, CI.
8. **Device matrix.** Физический ТВ, 720p, 4K, API 28–35, ARM, 1 GB, TalkBack, ИК/BT, SAF, Doze.
9. **Update flow.** Реальный HTTPS metadata, download, signature, install, pre-update backup, rollback.
10. **Visual consistency.** `TvPageScaffold`/`TvListRow` для Settings/Update/Backup; честные статусы (`Recommendations on` только при наличии контента).

### Этап C — P2/P3

- v3-подпись/key rotation;
- R8 mapping;
- baseline profile;
- certificate pinning;
- SCA/CVE;
- 24h soak/fuzz/storage-full;
- RTL;
- docs/screenshots.

### Критерии повторного аудита

Релиз можно перевести в **CONDITIONAL GO** только если одновременно:

- ни одного P0;
- все P1 закрыты или явно приняты владельцем с записью в release notes;
- APK собран из чистого тега, SHA совпадает;
- HOME проверен на целевом ТВ;
- Home не пустой без рекомендаций;
- Aerial имеет честное состояние ошибки;
- есть README/CHANGELOG/release notes/tag;
- пройдены физический ТВ, 720p и хотя бы один 4K-сценарий;
- TalkBack и реальный пульт проверены;
- лицензии/атрибуция закрыты.

До этого:

# Итоговое решение: NO-GO

Публикация 0.5.8 / vc81 не должна выполняться. После 0.5.10 / vc83 (**`4ae91bb`**, SHA `a30dde38…8286`) закрыты пустой Home, подписи плиток, release provenance, симптом пустого Aerial и девятишаговый Setup; поэтому вердикт **NO-GO** теперь держится на **P0-3** (HOME не подтверждён на физическом Google TV) и оставшихся P1: отсутствие README/CHANGELOG/CI/release notes, физическая матрица, лицензии/атрибуция и нерабочий updater. Проект можно и нужно доводить: фундамент (архитектура, фокус, безопасность, backup, сборка) сильный.

# Система обновлений и управление релизами — аудит и архитектура

**Проект:** OVisionTV Launcher (Kotlin), `com.whatuneed.ovisiontv.kotlin`  
**База аудита:** `0.5.7`, `versionCode 80`, HEAD `abd01fd`  
**Дата:** 16.11.2026  
**Статус этапа:** Stage 1 (audit) — выполнен, Stage 2 (architecture) — выполнен, Stage 3 (security review) — выполнен, Stage 4 (approval) — **получен в виде запроса владельца на поэтапную реализацию**, Stage 5 (implementation) — выполнен. Актуальный статус, схема метаданных и результаты проверок: `UPDATE_SYSTEM/01_IMPLEMENTATION_STATUS.md`.

> Главный вывод на момент аудита: **встроенной системы обновлений в приложении не было**. Сегодня обновление — это вручную полученный APK и системный установщик. Существующая страница «Обновления» обновляет **каталог заставок**, а не приложение. Поэтому аудит фиксировал не дефекты несуществующего UpdateManager, а отсутствие всего контура обновления и те сильные стороны релизной дисциплины, на которые можно опереться. После Stage 4 контур реализован; см. `01_IMPLEMENTATION_STATUS.md`.

---

## 0. Метод, границы и честность результатов

### 0.1 Что проверено

Прочитаны и сопоставлены:

- `app/build.gradle.kts`, `build.gradle.kts`, `gradle/wrapper/gradle-wrapper.properties`;
- `app/src/main/AndroidManifest.xml`;
- `app/src/main/java/com/whatuneed/ovisiontv/kotlin/ui/SettingsScreen.kt` (страницы, строки, навигация);
- `app/src/main/java/com/whatuneed/ovisiontv/kotlin/data/AerialRefresh.kt` (существующий сетевой код);
- `app/src/main/java/com/whatuneed/ovisiontv/kotlin/backup/BackupMigration.kt`, `BackupModels.kt`, `BackupManager.kt`, `BackupRecovery.kt`, `BackupScheduler.kt`;
- `app/src/main/java/com/whatuneed/ovisiontv/kotlin/OVisionApp.kt`;
- `tools/release_check.py`, `ARCHITECTURE_AUDIT/RELEASE_DISCIPLINE.md`;
- `FIXES.md` (исторические доказательства обновлений и отката);
- `app/src/main/res/raw/privacy_ru.txt`, `privacy_en.txt`;
- `app/src/main/res/xml/network_security_config.xml`;
- `gradle/verification-metadata.xml` (факт наличия).

Выполнены команды:

```text
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
aapt2 dump badging app/build/outputs/apk/release/app-release.apk
APKSIGNER=... python tools/release_check.py
git remote -v
git tag --list
git status --short
```

Фактический результат `release_check.py` на текущем артефакте:

```text
size   4 771 546 bytes
sha256 7c590d80ab38074c9225aabeef59ba066f30ff502d96d1ab889b8cba35ab312e
versionCode 80
versionName 0.5.7
applicationId com.whatuneed.ovisiontv.kotlin
minSdk 28
targetSdk 35
apksigner verify: PASS
APK Signature Scheme v2: true
certificate: 327a50453093b783e84b1f2645b03319477d89966ea6a653dab53d5a567d7b10
RU/EN resource names: 584 / 584
```

`apksigner` дополнительно показал: **v1 = false, v2 = true, v3 = false, v3.1 = false, v4 = false**, один подписант, RSA 4096, DN `CN=OVisionTV Launcher, O=OVisionTV, C=RU`.

### 0.2 Что НЕ проверялось

- Полный прогон `testDebugUnitTest`, `testReleaseUnitTest`, `connectedDebugAndroidTest` и `lint` в этом аудите **не перезапускался**. Числа 467/467 и 255/255 взяты из `AMBIENT_EXPERIENCE/01_IMPLEMENTATION_STATUS.md` как исторические, а не как результат этого прохода.
- Нет физического ТВ, Google TV-бокса, ARM32/ARM64-устройства, ТВ с 1 ГБ RAM, пульта с ИК-автоповтором.
- Нет реального update-сервера и валидного release-манифеста, поэтому весь путь «проверка → скачивание → установка» в этом аудите не мог быть проверен вживую.
- Нет CI: ни `.github/workflows`, ни иного pipeline-файла; `git remote -v` пуст, тегов нет.
- Установка APK на реальном телевизоре, поведение OEM-установщика и Google Play Protect не проверялись.

Всё, что не проверено, ниже помечено **NOT TESTED**. Исторические PASS из `FIXES.md` помечены отдельно и не выдаются за текущий прогон.

---

## A. Текущая архитектура обновлений

### A.1 Идентичность и сборка

| Параметр | Значение | Источник |
|---|---|---|
| `applicationId` | `com.whatuneed.ovisiontv.kotlin` | `app/build.gradle.kts:26` |
| `versionCode` | `80` | `app/build.gradle.kts:31` |
| `versionName` | `0.5.7` | `app/build.gradle.kts:32` |
| `minSdk` / `targetSdk` | `28` / `35` | `app/build.gradle.kts:27-28` |
| `compileSdk` | `35` | `app/build.gradle.kts:23` |
| Release | R8/minify + shrink resources | `app/build.gradle.kts:69-80` |
| Подпись release | `signingConfig`, если найден внешний `keystore.properties` | `app/build.gradle.kts:36-45`, `75` |
| Если ключа нет | release собирается **без подписи**, но gate `release_check.py` это ловит | `app/build.gradle.kts:36-44`; `tools/release_check.py:134-155` |
| APK | Универсальный, содержит native-библиотеки 4 ABI (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`) | `unzip -l app-release.apk` |
| R8 mapping | Генерируется, ~53,5 МБ, в APK не входит | `app/build/outputs/mapping/release/mapping.txt` |

Текущий APK подписан **только APK Signature Scheme v2**. Для `minSdk 28` этого достаточно для установки, но v3 отсутствует, а значит нет штатной линии ротации ключа. Это не текущий блокер, но важное ограничение на будущее.

### A.2 Что реально есть

1. **Страница «Обновления»** — `PAGE_UPDATES = 12` (`SettingsScreen.kt:318`). Она содержит только:
   - количество клипов Aerial (`settings_aerial_catalog`);
   - кнопку «Обновить каталог» (`settings_aerial_update`), которая вызывает `AerialRefresh.update(context)` (`SettingsScreen.kt:2741-2767`, `4561-4580`).
2. **Строка «Обновления» в разделе «О системе»** — показывает `BuildConfig.VERSION_NAME` и открывает `PAGE_UPDATES` (`SettingsScreen.kt:4534-4540`). Это не проверка версии приложения.
3. **Сведения о системе** — версия, build, канал (`Debug`/`Stable`), пакет (`SettingsScreen.kt:4582-4610`). Канал — статическая надпись на основе `BuildConfig.DEBUG`, а не выбираемый канал обновлений.
4. **Ручная релизная дисциплина** — документ `ARCHITECTURE_AUDIT/RELEASE_DISCIPLINE.md:7-23` и скрипт `tools/release_check.py`. Это сильная сторона: артефакт проверяется на версию, `applicationId`, SDK, подпись, сертификат и паритет RU/EN.
5. **Локальные резервные копии и миграционный gate** — `BackupManager`, `MigrationManager`, `BackupScheduler`, `BackupRecovery`; запуск в `OVisionApp.onCreate()` (`OVisionApp.kt:23-25`).
6. **HTTPS-only сеть** — `network_security_config.xml`: `cleartextTrafficPermitted="false"`, только системные trust anchors, без domain-config.
7. **Dependency verification metadata** — файл `gradle/verification-metadata.xml` присутствует.

### A.3 Чего нет

Поиск по `app/src` не нашёл ни одного из перечисленных механизмов:

| Механизм | Наличие | Комментарий |
|---|---|---|
| `UpdateManager` / `UpdateRepository` | Нет | нет ни класса, ни экрана проверки версии |
| Release metadata endpoint/JSON | Нет | нет URL, парсера, кэша, каналов |
| `PackageInstaller` | Нет | ни одного вхождения |
| `ACTION_INSTALL_PACKAGE` / `ACTION_VIEW` для APK | Нет | установка только вручную |
| `FileProvider` | Нет | нет `<provider>` и `update_paths.xml` |
| `REQUEST_INSTALL_PACKAGES` | Нет | в `aapt2 dump permissions` отсутствует |
| `DownloadManager` | Нет | загрузок APK нет |
| `WorkManager` | Нет | осознанно не вводился (см. `BackupScheduler`) |
| `UpdateStateStore` / `last_check` | Нет | нет ни времени проверки, ни pending install |
| `InstallResultReceiver` | Нет | нет отслеживания результата установки |
| Каналы Stable/Beta/Nightly | Только надпись `Debug/Stable` | не влияет на обновление |
| CI/CD | Нет | нет workflow-файлов; remote и теги отсутствуют |
| Release notes как артефакт | Нет | есть только `FIXES.md` — журнал, не машинно-читаемые notes |
| Уведомления об обновлении | Нет | и `POST_NOTIFICATIONS` не запрашивается |

### A.4 Фактический путь обновления сегодня

Сегодня существует только внешний, ручной путь:

1. Владелец/пользователь получает новый APK вне приложения (файл, ADB, файловый менеджер, сторонний источник).
2. Устанавливает его системным установщиком или через `adb install -r`.
3. Android проверяет:
   - совпадение `applicationId`;
   - совпадение подписи с уже установленным приложением;
   - `versionCode` не ниже установленного;
   - `minSdk`/ABI.
4. При успешном обновлении данные `/data/data/com.whatuneed.ovisiontv.kotlin` сохраняются, потому что это замена пакета, а не удаление.
5. При несовпадении подписи или понижении версии установка отклоняется.

Приложение в этом пути **не участвует**: оно не знает о новой версии, не проверяет её, не скачивает и не открывает установщик.

Исторические доказательства (не текущий прогон):

- `FIXES.md:2133` — release `vc2 → vc3`: `adb install -r` сохранил порядок и фокус; кадры до/после совпали побайтно; откат `vc2` отклонён `INSTALL_FAILED_VERSION_DOWNGRADE`.
- `FIXES.md:1774-1782` — release `vc12 → vc13`: сохранены язык English, скрытый список, порядок плиток; кадры Главной до/после совпали.
- `FIXES.md:1786` — отдельно подтверждено, что `vc11 → vc12` сохранил 12 плиток и папки; изменение подписи скрытой папки было ожидаемым исправлением языка, не потерей данных.

Эти записи доказывают платформенное поведение и отсутствие потери данных в проверенных сценариях, но **не доказывают работу будущего UpdateManager**: его ещё нет.

### A.5 Ответы на 10 вопросов аудита

1. **Как приложение узнаёт о новой версии?**  
   Никак. Нет проверки, нет release-манифеста, нет сетевого запроса. Страница «Обновления» обновляет только каталог Aerial (`SettingsScreen.kt:2741-2767`).

2. **Проверяются ли целостность и подпись?**  
   В приложении — нет. При ручной установке подпись и целостность проверяет системный PackageManager. SHA-256 APK фиксируется только в `release_check.py`/`FIXES.md`, но не используется на устройстве для проверки скачанного файла.

3. **Как запускается установка?**  
   Вручную: системный установщик / `adb install -r`. Приложение не вызывает `PackageInstaller`, не открывает `ACTION_VIEW` и не имеет `FileProvider`.

4. **Что происходит при отмене?**  
   В приложении нечего отменять. Пользователь отменяет системный диалог установки; приложение об этом не узнаёт и не хранит состояние.

5. **Поведение при нехватке памяти?**  
   Встроенного загрузчика нет, поэтому нет и его поведения. Системный установщик может отказать при нехватке места; приложение это не проверяет и не объясняет.

6. **Что с несовместимой версией?**  
   Система отклонит APK с меньшим `versionCode`, несовпадающей подписью, слишком высоким `minSdk` или неподходящим ABI. Приложение не делает preflight и не показывает понятную причину.

7. **Как уведомляется пользователь?**  
   Никак. Есть только строка версии в «О системе»; нет badge, диалога, уведомления или статуса «доступно обновление».

8. **Где хранится версия?**  
   `BuildConfig.VERSION_CODE/VERSION_NAME` из `app/build.gradle.kts:31-32`. Отдельно `MigrationManager` хранит `last_version_code` в `backup_state` (`BackupMigration.kt:30`, `50`, `63`), но это версия для миграционного gate, а не состояние обновления.

9. **Блокирует ли проверка запуск Launcher?**  
   Сейчас проверки нет, поэтому не блокирует. В будущей реализации это должно остаться правилом: сеть — только после первого кадра и только в IO.

10. **Что при офлайне/ошибке сети?**  
    Сейчас — ничего, потому что нет проверки. У `AerialRefresh` есть отдельная обработка ошибок (`AerialRefresh.kt:88-100`, `135-180`), но она относится к каталогу заставок, а не к обновлению приложения.

### A.6 Релизная дисциплина: что уже правильно

- Версия и `applicationId` централизованы в Gradle (`app/build.gradle.kts:26-32`).
- Есть обязательный gate и артефактная проверка (`RELEASE_DISCIPLINE.md:12-23`).
- Сертификат зафиксирован: SHA-256 `327a5045…7b10` (`RELEASE_DISCIPLINE.md:9`; `tools/release_check.py:29`).
- Ключ и `keystore.properties` не в репозитории; release остаётся unsigned, если ключа нет, но gate это обнаруживает (`app/build.gradle.kts:36-45`; `release_check.py:134-155`).
- R8/minify и shrinkResources включены (`app/build.gradle.kts:69-80`).
- Есть dependency verification metadata.
- Есть исторические проверки обновления с сохранением данных и отказа от downgrade (`FIXES.md:2133`, `1774-1786`).

### A.7 Данные и миграции: что уже есть и чего не хватает

`MigrationManager.ensureCurrent()` (`BackupMigration.kt:32-75`) делает важные вещи:

- отказывается работать, если сохранённая схема **новее** приложения (`current > DATA_SCHEMA`);
- при `lastVersion != 0 && lastVersion < BuildConfig.VERSION_CODE` создаёт локальную копию с `BackupTrigger.PRE_UPDATE` (`BackupMigration.kt:42-53`);
- при `current < DATA_SCHEMA` создаёт `PRE_MIGRATION` копию и записывает новую схему (`BackupMigration.kt:55-66`);
- запускается в фоне из `OVisionApp.onCreate()` (`OVisionApp.kt:25`), ошибки только логируются (`BackupMigration.kt:86-98`).

Ограничения, важные для обновлений:

1. **Схема пока `DATA_SCHEMA = 1`**, а `0 → 1` — намеренный no-op (`BackupModels.kt:7-10`; комментарий `BackupMigration.kt:12-16`). Реальных миграционных шагов нет.
2. **PRE_UPDATE-копия создаётся уже новой версией при первом запуске**, а не старой версией перед установкой. Если новая версия падает до `BackupMaintenance.start`, pre-update копии ещё нет.
3. **`BackupMaintenance.start()` асинхронный**, а экраны читают `SharedPreferences` сразу. Для текущей no-op схемы это безопасно; для будущей реальной миграции это гонка. Перед реальными миграциями нужен явный gate или отдельный порядок «миграция → чтение экранов».
4. **Ошибка миграции не видна пользователю**: только `Log.w` (`BackupMigration.kt:92-96`). Данные не удаляются — это правильно, — но пользователь не узнает, что обновление данных не завершилось.

### A.8 Реестр находок

Приоритеты: **P0** — блокер коммерческого контура обновлений, **P1** — существенный пробел, **P2** — риск/долг, **P3** — качество/документация. Это результаты чтения кода; динамические атаки не проводились.

| ID | Приоритет | Находка | Доказательство | Влияние |
|---|---|---|---|---|
| UP-01 | P0 | Нет встроенного механизма обновления приложения | нет `UpdateManager`, metadata, downloader, installer; `SettingsScreen.kt:318`, `2741` | пользователь не узнаёт о версиях; обновление полностью ручное |
| UP-02 | P0 | Нет проверки подлинности скачанного APK на устройстве | нет `PackageInstaller`/`REQUEST_INSTALL_PACKAGES`/проверки cert; только внешний installer | нельзя гарантировать, что пользователь ставит именно ваш APK, кроме подписи системы |
| UP-03 | P1 | Release можно собрать без подписи | `app/build.gradle.kts:36-45`; gate ловит, но сборка не падает | риск случайно распространить unsigned APK, если gate пропущен |
| UP-04 | P1 | Нет CI/CD, remote и тегов | `git remote -v` пуст; workflow-файлов нет; тегов 0 | релизы воспроизводятся вручную, нет автоматической трассируемости |
| UP-05 | P1 | Нет машинно-читаемого release-манифеста | только `FIXES.md` и `release_check.py` | невозможно построить проверку версий, каналы и release notes без ручного труда |
| UP-06 | P1 | Нет release notes как артефакта и локализованных notes | `FIXES.md` — журнал; UI не имеет страницы notes | пользователь не видит, что изменилось; есть соблазн придумать текст |
| UP-07 | P1 | Страница «Обновления» семантически занята каталогом Aerial | `SettingsScreen.kt:2741-2767`, `4561-4580`; `settings_updates_hint` (`strings.xml:257`) | пользователь ищет обновление приложения, а получает обновление заставок |
| UP-08 | P1 | Нет разрешения и UX для установки неизвестного APK | в manifest нет `REQUEST_INSTALL_PACKAGES`; `AndroidManifest.xml:24-55` | даже при готовом APK приложение не сможет корректно провести установку |
| UP-09 | P1 | Нет реестра миграций; gate только фиксирует схему | `BackupMigration.kt:32-75`; `DATA_SCHEMA = 1` (`BackupModels.kt:10`) | будущая смена схемы может пройти без преобразования данных |
| UP-10 | P2 | PRE_UPDATE-копия создаётся после установки, новой версией | `BackupMigration.kt:42-53`; `OVisionApp.kt:25` | при падении новой версии до запуска backup нет |
| UP-11 | P2 | Нет состояния установки и восстановления после process death | нет `InstallResultReceiver`/state store | после установки нельзя отличить success от неизвестного результата |
| UP-12 | P2 | Нет проверки свободного места, отмены, resume и очистки частичных файлов | нет downloader | на 1 ГБ ТВ и при обрыве сети установка будет непредсказуемой |
| UP-13 | P2 | Политика конфиденциальности утверждает, что сервера разработчика нет | `privacy_ru.txt:5` | автоматическая проверка через новый хост потребует версии политики и раскрытия |
| UP-14 | P2 | Только v2-подпись; нет v3 и политики ротации ключа | `apksigner`; `RELEASE_DISCIPLINE.md:9` | ротация ключа и lineage не поддержаны; потеря ключа = потеря пути обновления |
| UP-15 | P2 | Нет проверки обновления на физическом ТВ/API 28–35 | `RELEASE_DISCIPLINE.md:29-38` | нельзя выдавать AVD-результат за сертификацию |
| UP-16 | P3 | `RELEASE_DISCIPLINE.md` не обновлён до 0.5.7 | документ содержит 0.5.0 в §8 | расхождение документа и текущего артефакта |
| UP-17 | P3 | Нет уведомлений/канала уведомлений; `POST_NOTIFICATIONS` не запрашивается | manifest; UI | in-app уведомление — единственный безопасный вариант без нового разрешения |
| UP-18 | P3 | Debug и release используют один `applicationId`, но разные ключи | `app/build.gradle.kts:26`, `36-45`; исторические заметки `FIXES.md` | debug нельзя ставить поверх release; это ожидаемо, но должно быть явно в инструкции |

### A.9 Вывод по разделу A

**Текущая система обновлений: 0/10 как продуктовая функция.** Это не значит, что проект плохой: релизная дисциплина и ручная проверка артефакта сделаны лучше, чем во многих лаунчерах. Но контура «узнать → скачать → проверить → установить → сохранить данные → восстановиться» нет ни в одном звене.

---

## B. Аудит безопасности обновлений (Stage 3)

### B.1 Активы и доверие

| Актив | Почему важен | Текущая защита |
|---|---|---|
| Signing key | кто владеет ключом, тот выпускает «легитимные» обновления | ключ вне репозитория, `keystore.properties`; содержимое не читалось |
| APK | код, который попадёт на ТВ | v2-подпись, сертификат `327a50…7b10` |
| Release metadata | версия, URL, SHA-256, mandatory, notes | отсутствует |
| Пользовательские данные | профили, плитки, папки, PIN, настройки | локальные prefs + backup layer |
| Update temp files | APK/частичные файлы | отсутствуют |

Доверие не должно строиться на одном канале. Даже при HTTPS и SHA-256 в metadata **подпись APK** остаётся единственным доказательством того, что бинарник выпущен владельцем ключа. SHA-256 доказывает только «файл не изменился с момента, когда его хэш записали», но не «хэш записал владелец».

### B.2 Модель угроз

| Угроза | Вектор | Последствие | Обязательная мера |
|---|---|---|---|
| Подмена APK на хосте | compromised host/CDN | вредоносный APK | cert нового APK = cert установленного; system installer; SHA-256 только как целостность |
| MITM | подмена TLS/HTTP | подмена metadata/APK | HTTPS-only, host allowlist, redirect revalidation, system trust |
| Downgrade/replay | старый metadata/APK | откат на уязвимую версию | `versionCode` строго больше; metadata expiry/sequence; не принимать старый |
| Подмена release notes | metadata | социальная инженерия | notes только из доверенного metadata; без HTML; локализация; лимиты |
| Hijack installer intent | implicit intent | чужой установщик | только explicit `PackageInstaller`/`PendingIntent`; receiver not exported; immutable PendingIntent |
| Path traversal/temp | имя файла из metadata | запись вне каталога | имя только из `versionCode`, не из URL; app-private `cacheDir` |
| Disk exhaustion | огромный APK/частичный файл | отказ установки/сбои | max size, free-space check, лимит одного APK, cleanup |
| Key compromise | утечка keystore | злоумышленник выпускает обновления | offline custody, backup, v3 lineage, Play App Signing при Play; без ключа в APK/репозитории |
| Debug/release confusion | debug APK поверх release | установка отклонена или потеря данных при uninstall | разные ключи; не предлагать debug как обновление |
| Play/sideload conflict | in-app APK поверх Play-версии | Play Protect/подмена канала | определить installer source; на Play — только deep link в Play |
| Fake update UI | фишинг | пользователь ставит чужой APK | все установки только через системный installer; никаких «тихих» установок |

### B.3 SHA-256, подпись APK и подпись metadata — не одно и то же

- **SHA-256 APK** — целостность: байты не изменились относительно значения в metadata. Если metadata подменена вместе с APK, SHA-256 не спасёт.
- **Подпись APK** — авторство и целостность на уровне Android PackageManager. Именно она не даст установить APK, подписанный чужим ключом, поверх текущего приложения. Системный installer — финальный арбитр.
- **Подпись metadata** — защита от подмены URL/версии/notes на хосте и от replay. Она **не заменяет** подпись APK. Для v1 её можно не вводить: APK-подпись уже блокирует вредоносный бинарник, а monotonic `versionCode` блокирует downgrade. Если хост/CDN не полностью доверенный или нужен staged rollout, metadata-подпись становится оправданной.

**Рекомендация:** в v1 не вводить новый криптоключ для metadata; сделать обязательными HTTPS + host allowlist + SHA-256 + cert-match + `versionCode` strict. Вопрос metadata-подписи вынести в Stage 5 как отдельное решение, если владелец выбирает сторонний хост или несколько каналов.

Отдельно важно не переоценить `PackageManager.getPackageArchiveInfo(..., GET_SIGNING_CERTIFICATES)`: это preflight-разбор архива и сверка сертификата, а **не** полноценная криптографическая проверка APK. Финальную проверку подписи выполняет системный PackageInstaller. Если нужна независимая проверка до установки, это отдельное решение о добавлении `apksig` (новая зависимость), а не «бесплатная» гарантия.

### B.4 Текущая подпись

| Свойство | Значение | Оценка |
|---|---|---|
| Схема | v2 | работает на API 28+; v1/v3/v4 отсутствуют |
| Алгоритм | RSA 4096 | сильный, но крупный; совместим |
| Сертификат | `327a5045…7b10` | зафиксирован в gate |
| Ключ | вне репозитория, `keystore.properties` | правильная гигиена; содержимое не читалось |
| Ротация | нет | при смене ключа обновление невозможно без uninstall → потеря данных |
| Play App Signing | нет сведений | если Play — ключом управляет Google; sideload-обновление несовместимо |

**Вывод:** подпись текущего артефакта корректна; контур управления ключом описан недостаточно. Для «коммерческого» уровня нужны: офлайн-хранение, вторая копия, процедура восстановления, запрет генерации релиза без ключа, план v3-lineage на случай ротации.

### B.5 Что нужно добавить в манифест и код

- `<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />` — только если владелец одобрит in-app установку.
- `<receiver android:name=".update.InstallResultReceiver" android:exported="false" />` — результат `PackageInstaller`.
- Никаких `QUERY_ALL_PACKAGES`, `WRITE_EXTERNAL_STORAGE`, `REQUEST_DELETE_PACKAGES`.
- `FileProvider` не нужен для `PackageInstaller`; добавлять только если появится fallback через `ACTION_VIEW`.
- Явные intents, `FLAG_IMMUTABLE`, `FLAG_UPDATE_CURRENT`; без implicit broadcast.
- Все сетевые URL — только `https`, allowlist host + port 443, без userinfo, без downgrade.
- Никаких секретов/токенов/приватных ключей в APK, BuildConfig, ресурсах, логах и репозитории.

### B.6 Оценка безопасности текущего состояния

| Область | Оценка | Обоснование |
|---|---|---|
| Подпись релиза | 8/10 | v2, RSA 4096, сертификат проверяется gate; нет v3/ротации |
| Целостность распространения | 3/10 | SHA-256 только в ручных evidence; на устройстве не проверяется |
| Защита от подмены обновления | 1/10 | механизма обновления нет; защита только системного installer |
| Секреты | 7/10 | ключ вне repo; политика custody неполная |
| Приватность | 6/10 | HTTPS-only; политика прямо запрещает сервер разработчика; autocheck потребует изменений |
| Тестирование безопасности | 2/10 | нет MITM/fuzz/SCA; это не значит, что уязвимости есть — значит, они не проверены |

---

## C. Предлагаемая архитектура

### C.1 Принципы

1. **Один `UpdateManager`** — единственная точка входа; никакого второго механизма в Settings/Activity.
2. **Никаких новых тяжёлых зависимостей** в v1: `HttpURLConnection`, `org.json`, `PackageManager`, `PackageInstaller`, `MessageDigest`, `SharedPreferences`, `AlarmManager`, Compose. Без OkHttp, WorkManager, Room, Hilt, DownloadManager.
3. **Сеть — никогда на main thread**; проверка — после первого кадра, не в `Application.onCreate`.
4. **Установка — только системный PackageInstaller**; никакого root, hidden API, silent install.
5. **Состояние — персистентное и восстановимое** после process death.
6. **Данные не удаляются**; обновление — замена пакета, миграция — отдельный gate.
7. **Один APK на канал** (universal), без ABI-split для sideload.
8. **UI не раздувает `SettingsScreen`**: состояние обновления выносится в отдельный composable/held state, чтобы не повторить K2-проблему `SettingsScreen` (AQ-30).

### C.2 Компоненты

| Компонент | Ответственность | Предлагаемое размещение |
|---|---|---|
| `UpdateManager` | оркестрация: check → download → verify → install; единая точка для UI | `update/UpdateManager.kt` |
| `UpdateRepository` | выбор канала, кэш metadata, ETag, TTL | `update/UpdateRepository.kt` |
| `ReleaseMetadataProvider` | HTTP GET metadata, redirects, таймауты, лимиты | `update/ReleaseMetadataProvider.kt` |
| `ReleaseMetadataParser` | строгая валидация JSON, schema, notes | `update/ReleaseMetadata.kt` |
| `VersionComparator` | `versionCode` как единственный критерий установки; semver только display | `update/VersionComparator.kt` |
| `CompatibilityChecker` | API, ABI, `minSupported`, размер, free space, installer source | `update/CompatibilityChecker.kt` |
| `UpdateDownloader` | потоковая загрузка, progress, cancel, resume, hash | `update/UpdateDownloader.kt` |
| `IntegrityVerifier` | SHA-256 + размер; сверка с metadata | `update/IntegrityVerifier.kt` |
| `SignatureVerifier` | preflight cert match с установленным приложением | `update/SignatureVerifier.kt` |
| `InstallationManager` | `PackageInstaller` session, commit, статус, reconcile | `update/InstallationManager.kt` |
| `InstallResultReceiver` | приём статуса установки | `update/InstallResultReceiver.kt` |
| `UpdateStateStore` | `SafePreferences`/JSON: phase, lastCheck, etag, pending | `update/UpdateStateStore.kt` |
| `UpdateScheduler` | on-launch/daily/weekly/manual; `AlarmManager` inexact | `update/UpdateScheduler.kt` |
| `SoftwareUpdateSection` | UI-строки и состояния без фокуса | `ui/components/SoftwareUpdateSection.kt` |
| `UpdateNotesOverlay` | прокручиваемые release notes, D-pad | `ui/components/UpdateNotesOverlay.kt` |
| `InstallResultReceiver` (manifest) | `exported=false` | `AndroidManifest.xml` |

Это ориентир, а не требование «класс на файл». Малые `VersionComparator`, `CompatibilityChecker`, `IntegrityVerifier`, `SignatureVerifier` можно объединить в 2–3 файла, чтобы не плодить сущности.

`UpdateNotificationController` как отдельный класс в v1 не нужен: системные уведомления не добавляются (это потребовало бы `POST_NOTIFICATIONS` и отдельный канал), а состояние показывается in-app на странице обновления. Если позже понадобится фоновое уведомление, оно добавляется отдельным решением, а не «на всякий случай».

### C.3 Состояния

```text
IDLE
  └─ CHECKING
       ├─ UP_TO_DATE
       ├─ AVAILABLE
       ├─ INCOMPATIBLE
       ├─ FAILED_NETWORK
       └─ FAILED_METADATA

AVAILABLE
  └─ DOWNLOADING
       ├─ VERIFYING
       │    ├─ READY
       │    └─ FAILED_INTEGRITY
       ├─ CANCELLED
       └─ FAILED_DOWNLOAD

READY
  └─ INSTALL_PENDING
       ├─ INSTALLED
       ├─ INSTALL_FAILED
       └─ INSTALL_UNKNOWN

Любое состояние → IDLE по явному Reset/после успешной установки.
```

Правила:

- `CHECKING` не блокирует UI; строки показывают «Проверка…».
- `DOWNLOADING` — реальный прогресс; если `Content-Length` неизвестен, показывать indeterminate и байты, не выдумывать процент.
- `VERIFYING` — SHA-256 + размер + `PackageManager` preflight; без UI-блокировки.
- `READY` — файл лежит в app-private `cacheDir`, готов к установке; не удаляется до результата/отмены.
- `INSTALL_PENDING` — записан **до** `commit()`; после перезапуска reconcile по `versionCode`.
- `INSTALL_UNKNOWN` — процесс умер, результат не пришёл; при следующем запуске: если `versionCode` вырос — success; если нет — failed/unknown с понятным текстом.

### C.4 Поток данных

```text
Settings/Update UI
      │ check()
      ▼
UpdateManager ──► UpdateScheduler (due? manual?)
      │
      ▼
UpdateRepository ──► ReleaseMetadataProvider ──► HTTPS host (allowlist)
      │                       │
      │                       └─ 304/cache/ETag
      ▼
CompatibilityChecker ──► AVAILABLE / INCOMPATIBLE / EOL
      │
      ▼
UpdateDownloader ──► cacheDir/updates/<versionCode>.apk.part
      │                    │ progress (bytes)
      ▼                    ▼
IntegrityVerifier ◄──── SHA-256 + size
      │
      ▼
SignatureVerifier ◄──── PackageManager.getPackageArchiveInfo + installed cert
      │
      ▼
InstallationManager ──► PackageInstaller.Session ──► system UI
      │
      ▼
InstallResultReceiver / reconcile ──► UpdateStateStore
```

### C.5 Потоки и производительность

- `UpdateManager.check()` — `suspend`, `withContext(Dispatchers.IO)`.
- Загрузка — `InputStream` 64 КБ, без `readBytes()` на весь APK; память O(64 КБ), не O(APK).
- Metadata — лимит 256 КБ, парсинг после проверки размера; notes — лимит на количество/длину.
- UI получает `StateFlow`/`State` из `UpdateManager`; progress throttle 100–200 мс, чтобы не recompose каждый чанк.
- Проверка на старте — только после того, как Home отрисовал первый кадр; не в `Application.onCreate`.
- Отмена — coroutine `cancel()` + `disconnect()` + удаление `.part`; не оставлять «вечную» загрузку.
- `onTrimMemory` не должен убивать установку; загрузка вне UI, при уходе в фон — пауза/отмена по политике.
- Никаких `Thread.sleep`, busy-wait или фиктивных задержек.

### C.6 Персистентность

`UpdateStateStore` (отдельный `SharedPreferences`, например `update_state`):

| Ключ | Тип | Назначение |
|---|---|---|
| `phase` | string | текущее состояние |
| `channel` | string | stable/beta (выбор пользователя) |
| `auto_mode` | string | manual/daily/weekly |
| `last_check_ms` | long | последняя попытка |
| `last_success_ms` | long | последняя успешная проверка |
| `etag` | string? | условный GET |
| `available_version_code` | int | версия из metadata |
| `available_version_name` | string | для UI |
| `available_sha256` | string | ожидаемый хэш |
| `available_size` | long | ожидаемый размер |
| `pending_session_id` | int | сессия PackageInstaller |
| `pending_version_code` | int | что устанавливаем |
| `last_error` | string? | код ошибки для диагностики |

Требования:

- запись — через `SafePreferences`/`commit()` для критичных фаз;
- JSON metadata — атомарно через существующий `writeFileAtomically` (`data/AtomicWrite.kt`);
- APK — только app-private `cacheDir/updates`; не `externalCacheDir`, не `filesDir` для временного APK;
- лимит: 1 APK + 1 `.part`; старые `.part` старше 24 ч удаляются при открытии экрана/в IO, не на старте;
- при `INSTALLED` удалить APK; при отмене/ошибке — удалить `.part`; готовый APK можно сохранить до ручного retry, но не дольше 7 дней.

### C.7 Планировщик

| Режим | Поведение |
|---|---|
| Manual | только по нажатию; cooldown 60 с от повторного нажатия |
| On launch | один раз за 24 ч, после первого кадра; не в `onCreate` |
| Daily | `AlarmManager.setInexactRepeating`, интервал 24 ч; без `WAKE_LOCK` |
| Weekly | тот же механизм, 7 дней |
| Auto off | никаких сетевых запросов, только manual |

- Автоматическая проверка — минимум 12 ч между попытками; по умолчанию рекомендуется **Manual до решения владельца о хосте**, затем **Daily для Stable**.
- `AlarmManager` не гарантирует точное время; приложение не обещает «ровно в 03:00».
- Будильник не переживает перезагрузку; `ensureScheduled()` вызывается при старте Launcher (как `BackupScheduler`), без `RECEIVE_BOOT_COMPLETED`.
- В Kids-профиле автопроверка и установка не запускаются; обновление доступно из обычного профиля.
- Офлайн: не крутить retry-loop; показать «Нет сети» + последнюю успешную проверку.

### C.8 Кэш и rate limiting

- Metadata: 1 файл на канал, максимум 256 КБ; TTL 6 ч для UI, но `last_check` показывается всегда.
- `If-None-Match`/`If-Modified-Since`; `304` не считается ошибкой.
- `429`/`403`: не повторять автоматически; показать «Сервер ограничил проверку», уважать `Retry-After` (с верхним пределом, например 24 ч).
- `5xx`: backoff 15 мин → 30 → 60 → 120 → 240 → 480 → 900 с (максимум 6 неудач), затем ручная проверка.
- Ручная проверка игнорирует backoff, но не чаще 60 с; сервер всё равно может ограничить.
- Никаких частых фоновых проверок «на всякий случай».

### C.9 Выбор механизма установки

| Механизм | Плюсы | Минусы | Решение |
|---|---|---|---|
| `PackageInstaller.Session` | явный API, результат через `PendingIntent`, system UI, контроль сессии | нужен `REQUEST_INSTALL_PACKAGES`; пользователь всё равно подтверждает | **основной** |
| `ACTION_INSTALL_PACKAGE` | просто вызвать | deprecated с API 29, нет надёжного результата, легко получить чужой обработчик | не использовать |
| `ACTION_VIEW` + MIME `application/vnd.android.package-archive` | открывает системный установщик | нужен `FileProvider` (file:// запрещён), нет результата, возможен выбор чужого приложения | только аварийный fallback, не v1 |
| `FileProvider` | даёт read-only URI для `ACTION_VIEW` | лишний provider/XML; для `PackageInstaller` не нужен | не добавлять без fallback-решения |
| `REQUEST_INSTALL_PACKAGES` | открывает путь к установке | special app access; пользователь должен разрешить; на части TV пункт спрятан | добавлять только с approval |
| Системный установщик | финальная проверка подписи/совместимости; доверие пользователя | нельзя обойти; OEM-поведение различается | **единственный путь установки** |

`PackageInstaller`-сессия:

1. `SessionParams` с `setAppPackageName`, `setSize`, `MODE_FULL_INSTALL`, `setInstallReason(INSTALL_REASON_USER)`.
2. `PendingIntent.getBroadcast(..., FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT)` на **явный** `InstallResultReceiver` (`exported=false`).
3. `commit()`; при `STATUS_PENDING_USER_ACTION` взять `EXTRA_INTENT` и открыть системное подтверждение.
4. `STATUS_SUCCESS` → `INSTALLED`; `STATUS_FAILURE_*` → `INSTALL_FAILED` с кодом; `STATUS_FAILURE_ABORTED` → отмена.
5. Процесс может умереть до доставки статуса: после старта сверить `versionCode` с `pending_version_code`; не показывать ложный success.

`ACTION_INSTALL_PACKAGE` не даёт нормального result-tracking, а `ACTION_VIEW` без `FileProvider` на API 24+ упадёт с `FileUriExposedException`. Поэтому «два механизма на всякий случай» не нужны: один основной и один честно описанный fallback.

### C.10 Протокол загрузки

- `HttpURLConnection`, `connectTimeout = 15_000`, `readTimeout = 30_000`, `instanceFollowRedirects = false`; редирект проверяется по allowlist, максимум 3; никакого HTTP.
- `Accept: application/vnd.android.package-archive`, `Accept-Encoding: identity`, стабильный `User-Agent`; без cookies, auth, аккаунта.
- Размер: `sizeBytes` из metadata обязателен; проверить `Content-Length`, если он есть; жёсткий максимум 100 МиБ; при расхождении — отказ.
- Запись: `<versionCode>.apk.part` в app-private `cacheDir/updates`; поток 64 КБ; SHA-256 считается по ходу; после завершения — `fsync`/закрытие, проверка размера и хэша, атомарное переименование в `.apk`.
- **Resume только при поддержке сервера:** `Range: bytes=N-` + сохранённый `ETag`/`Last-Modified`; принимать только `206` с корректным `Content-Range` и тем же валидатором. Если сервер вернул `200` или валидатор изменился — удалить `.part` и начать заново. Без валидатора resume не обещать.
- Обрыв/таймаут: сохранить `.part` только если есть валидатор; иначе удалить. Автоматический повтор — не более одного; дальше ручная кнопка.
- Отмена: `Job.cancel()` + `disconnect()` + удаление `.part`; UI не показывает «отменено», пока файл не удалён.
- Очистка: не более одного `.apk` и одного `.part`; `.part` старше 24 ч удаляется в IO при открытии экрана; готовый `.apk` — после установки/7 дней.
- SHA-256 не считается «подписью»: после хэша обязательна cert-сверка, затем system installer.

---

## D. Спецификация release metadata

### D.1 Формат

Один JSON-документ на канал, `schema = 1`, UTF-8, не более 256 КБ. Пример (URL и версия — **иллюстрация формата**, не реальный релиз):

```json
{
  "schema": 1,
  "channel": "stable",
  "generatedAt": "2026-11-16T12:00:00Z",
  "expiresAt": "2026-12-16T12:00:00Z",
  "releases": [
    {
      "versionCode": 81,
      "versionName": "0.5.8",
      "channel": "stable",
      "releaseDate": "2026-11-20T00:00:00Z",
      "minimumAndroidApi": 28,
      "minimumSupportedVersionCode": 60,
      "mandatory": false,
      "notes": {
        "en": [
          "Fixed focus restoration after returning from an app.",
          "Reduced memory use in the wallpaper catalogue."
        ],
        "ru": [
          "Исправлено восстановление фокуса после возврата из приложения.",
          "Снижено потребление памяти в каталоге заставок."
        ]
      },
      "assets": [
        {
          "abi": "universal",
          "url": "https://updates.example.com/ovisiontv/0.5.8/OVisionTV-0.5.8-81-stable.apk",
          "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
          "sizeBytes": 4771546
        }
      ]
    }
  ]
}
```

### D.2 Поля

| Поле | Обяз. | Правило |
|---|---|---|
| `schema` | да | ровно `1`; неизвестная схема → отказ |
| `channel` | да | `stable` или `beta`; должен совпасть с выбранным каналом |
| `generatedAt` | да | ISO-8601 UTC; не в будущем более чем на 24 ч |
| `expiresAt` | да | ISO-8601 UTC; после истечения metadata не используется для установки (только для диагностики) |
| `releases` | да | непустой массив; один элемент на версию; максимум 20 |
| `versionCode` | да | целое > 0; строго > установленного для установки |
| `versionName` | да | строка 1..64; должна совпасть с APK после скачивания |
| `channel` (релиз) | да | совпадает с channel документа |
| `releaseDate` | да | ISO-8601; не будущее > 24 ч |
| `minimumAndroidApi` | да | целое 28..35; больше текущего API → INCOMPATIBLE |
| `minimumSupportedVersionCode` | да | целое ≥ 1; текущий ниже → EOL-предупреждение |
| `mandatory` | да | bool; только для security/data-loss фиксов; не отменяет подтверждение пользователя |
| `notes.en` / `notes.ru` | да | массив строк 1..20, каждая ≤ 200 символов; без HTML |
| `assets` | да | ровно один элемент в v1; universal APK |
| `assets[].url` | да | HTTPS, allowlist host, port 443, без userinfo, ≤ 2048 символов |
| `assets[].sha256` | да | 64 lowercase hex |
| `assets[].sizeBytes` | да | 1..100 МиБ; сверять с реальным размером |
| `assets[].abi` | нет | `universal` в v1; иначе сверять с `Build.SUPPORTED_ABIS` |

### D.3 Что валидируется, а не принимается на веру

- `versionCode` из metadata **не** используется для установки без сверки с APK: после скачивания `PackageManager.getPackageArchiveInfo` должен вернуть тот же код и имя.
- `sha256` из metadata не считается доказательством авторства; он проверяется вместе с cert-match.
- `sizeBytes` не используется для прогресса, если реальный `Content-Length` отличается; расхождение → отказ.
- URL не строится из пользовательских данных; только из проверенного metadata.
- `notes` не содержат HTML/ссылок; UI рисует обычный текст.
- Неизвестные поля игнорируются, но не влияют на безопасность; неизвестная схема — отказ.
- Никаких «последняя версия = максимальный semver»: сравнение только по `versionCode`.

### D.4 Каналы и указатели

- `stable.json` — только stable-релизы.
- `beta.json` — beta; beta `versionCode` всегда выше текущего stable на момент публикации.
- Nightly **не рекомендуется** для ТВ: шум, нестабильность, поддержка; если появится — тот же app id/key и `versionCode` выше beta.
- Переключение канала:
  - не устанавливает APK автоматически;
  - очищает `available_*`, скачанный APK и pending session;
  - перечитывает metadata нового канала;
  - если установленный `versionCode` **выше** последнего в новом канале, показывает «Установленная сборка новее выбранного канала; downgrade невозможен без удаления приложения» — это не ошибка.
- Никаких разных `applicationId` для beta/stable: это сломало бы обновление и данные.

### D.5 Подпись metadata (опционально, не v1)

Если владелец выбирает сторонний хост/CDN или требует защиту от replay:

- отдельный документ `update.json.sig` — base64url ECDSA P-256 SHA-256 над точными байтами JSON;
- в приложении — публичный ключ и `keyId`; приватный ключ только офлайн/в CI-secret, не в APK;
- проверка до парсинга; `expiresAt` + monotonic `versionCode` дают replay-защиту;
- ротация ключа metadata — через `keyId` и overlap, иначе старые клиенты перестанут проверять.

Почему не обязательно: подпись APK уже не даст установить чужой бинарник; metadata-подпись защищает от подмены notes/URL/канала, но добавляет второй ключ и процедуру. Это решение Stage 5, а не «по умолчанию».

---

## E. Спецификация UX

### E.1 Информационная архитектура

Сейчас: `Настройки → О системе → Обновления` (`SettingsScreen.kt:4534-4540`), а страница занята каталогом Aerial (`2741-2767`).

Предлагается:

```text
Настройки → Система → Обновление ПО
  ├─ Текущая версия: 0.5.7 (80)
  ├─ Канал: Stable / Beta
  ├─ Последняя проверка: …
  ├─ Состояние: …
  ├─ [Проверить обновления]
  ├─ [Скачать] / [Отмена] / [Установить]
  ├─ Размер, дата, SHA-256 (в «Подробнее»)
  └─ Что нового → прокручиваемые notes
```

- Строку «Обновления» из «О системе» перенести в «Система»; в «О системе» оставить версию/build/package.
- Каталог Aerial из страницы обновлений убрать; его проверка уже есть в `Настройки → Персонализация → Заставка → Проверка каталога` (`PAGE_SAVER_REFRESH`, `SettingsScreen.kt:1876-1885`). Если владелец не хочет менять IA, допустимо оставить каталог, но **не называть страницу «Обновления»** без пояснения; это хуже.
- Не добавлять второй экран-дубль: `SoftwareUpdateSection` — часть существующей страницы Settings.

### E.2 Состояния и сообщения

| Состояние | EN | RU | Действия |
|---|---|---|---|
| Up to date | You're up to date | У вас последняя версия | Проверить снова |
| Update available | Update available | Доступно обновление | Скачать |
| Downloading | Downloading… 42% | Загрузка… 42% | Отмена |
| Downloading (unknown size) | Downloading… 1.2 MB | Загрузка… 1,2 МБ | Отмена |
| Verifying | Checking the file… | Проверка файла… | Отмена |
| Ready | Ready to install | Готово к установке | Установить |
| Installation pending | Waiting for the system installer | Ожидание системного установщика | Открыть установщик |
| Installed | Update installed | Обновление установлено | Закрыть |
| Installation failed | Couldn't install the update | Не удалось установить обновление | Подробнее / Повторить |
| Network unavailable | No internet connection | Нет подключения к интернету | Повторить |
| Server unavailable | Update server is unavailable | Сервер обновлений недоступен | Повторить позже |
| Incompatible | This update doesn't support this TV | Обновление не поддерживает этот телевизор | Подробнее |
| Permission required | Allow installs for OVisionTV | Разрешите установку для OVisionTV | Открыть настройки |
| Storage insufficient | Not enough free space | Недостаточно свободного места | Управление хранилищем |
| Channel newer installed | Installed build is newer than this channel | Установленная сборка новее канала | OK |
| EOL | This version is no longer supported | Эта версия больше не поддерживается | Обновить |
| No release notes | No release notes provided | Примечания к выпуску не указаны | OK |

Правила текста:

- Не писать «обновление установлено», пока `PackageManager` не подтвердил новый `versionCode`.
- Не писать «скачано 100%», пока SHA-256 и размер не проверены.
- Не показывать проценты, если `Content-Length` неизвестен; только байты.
- Не придумывать release notes; если metadata не содержит локали — показать EN fallback и пометку.
- Не обещать «тихую установку», «без перезапуска» или «сохранит всё» без проверки.
- В «Подробнее»: текущая/новая версия, дата, размер, SHA-256, код ошибки, но **без** stack trace и без секретов.

### E.3 Прогресс, отмена и установка

- Прогресс — реальные байты; скорость/ETA не показывать, чтобы не выдавать оценку за измерение.
- «Отмена» во время загрузки: остановить поток, удалить `.part`, вернуться в `AVAILABLE`.
- «Отмена» во время проверки: прекратить, удалить tmp; не оставлять недопроверенный APK.
- «Отмена» после `commit`: `Session.abandon()` best-effort; системный диалог может остаться; честно написать «установка отменена/system installer».
- Перед установкой: системный диалог Android; объяснить, что приложение перезапустится и данные сохраняются; не обещать, что установка не потребует подтверждения.
- Во время установки: не убивать процесс, не чистить `cacheDir`; ТВ не выключать.
- После установки: приложение перезапускается; первый запуск проверяет `versionCode` и миграцию.

### E.4 Фокус и доступность

- Все действия — focusable-строки существующего `SettingsRow`; курсор `focusIndex` остаётся источником истины.
- Не использовать отдельные `FocusRequester` на каждой строке; следовать правилу `UI_UX_UNIFICATION`.
- BACK: из notes → на страницу обновления; со страницы → в «Система»; во время загрузки BACK не отменяет молча — сначала подтверждение.
- Прогресс-строка не должна перехватывать фокус; обновление состояния не должно сбрасывать `focusIndex`.
- TalkBack: `stateDescription` для состояния, `Role.Button` только у действий; notes читаются как текст, не как кнопки.
- Длинные notes — прокручиваемая поверхность, не обрезанный `SettingsRow`.
- Не добавлять `POST_NOTIFICATIONS`: уведомление только in-app; это осознанно, чтобы не расширять разрешения.

### E.5 Что видит пользователь в разных сценариях

1. **Проверка вручную, сети нет:** «Нет подключения», последняя успешная проверка, кнопка «Повторить»; ничего не скачивается.
2. **Доступно обновление:** версия, дата, размер, notes; кнопка «Скачать».
3. **Загрузка:** реальные байты, «Отмена».
4. **Файл повреждён:** «Файл повреждён, скачайте заново»; `.part`/APK удалены; автоматический повтор — не более одного.
5. **Подпись не совпала:** «Обновление не от этого разработчика»; установка запрещена; файл удалён; подробности без секретов.
6. **Нет разрешения:** объяснение, кнопка в системные настройки; после возврата — повторная проверка разрешения.
7. **Системный установщик:** пользователь подтверждает; после успеха — «Обновление установлено», версия обновилась.
8. **Пользователь отменил установщик:** «Установка отменена»; APK можно скачать снова; никаких авто-повторов.
9. **Новая версия несовместима:** «Не поддерживает этот телевизор»; кнопка «Скачать» disabled; причина — API/ABI/`minSupported`.
10. **Канал Beta → Stable, stable ниже:** «Установленная сборка новее»; установка запрещена; данные не трогаются.

### E.6 Полный сценарий установки (10 шагов)

1. Пользователь открывает `Настройки → Система → Обновление ПО`.
2. Нажимает «Проверить обновления»; проверка уходит в IO, UI остаётся отзывчивым.
3. Приложение получает metadata, проверяет схему/канал/URL/размер и показывает версию, дату, размер и notes.
4. Пользователь нажимает «Скачать»; приложение проверяет свободное место и начинает потоковую загрузку.
5. UI показывает реальные байты/процент; «Отмена» удаляет `.part` и возвращает `AVAILABLE`.
6. После загрузки проверяются размер и SHA-256; при ошибке файл удаляется, установка не начинается.
7. Выполняется preflight-сверка сертификата с установленным приложением; при несовпадении — отказ.
8. Пользователь нажимает «Установить»; `InstallationManager` открывает системный `PackageInstaller`; пользователь подтверждает установку.
9. Система заменяет пакет, сохраняя данные; приложение перезапускается; `UpdateStateStore` фиксирует `INSTALL_PENDING` до commit и сверяет `versionCode` после старта.
10. Первый запуск завершает миграционный gate, показывает «Обновление установлено», удаляет APK и оставляет диагностику без секретов.

---

## F. Подпись и стратегия распространения

### F.1 Текущее состояние

| Вопрос | Ответ |
|---|---|
| Кто подписывает | внешний keystore, `~/.ovisiontv/keystore.properties` или `OVISIONTV_SIGNING_PROPERTIES` |
| Схема | v2; v1/v3/v4 отсутствуют |
| Сертификат | `327a5045…7b10`, RSA 4096 |
| Ключ в repo? | нет; содержимое не читалось |
| Release без ключа? | да, unsigned; gate `release_check.py` падает |
| Play App Signing? | нет сведений |
| Remote/теги? | нет remote, нет тегов |

### F.2 Незыблемые правила

1. **`applicationId` не меняется** без отдельного решения владельца и плана миграции. Смена = новый пакет = потеря данных при переходе.
2. **Signing key не меняется** без lineage/Play App Signing. Смена = невозможно обновить существующую установку; только uninstall → потеря данных.
3. **Beta и Stable — один `applicationId` и один signing key.** Иначе beta нельзя обновить до stable без потери данных.
4. **Debug — отдельный ключ**, не предлагается как обновление.
5. **Ключ не попадает в APK, репозиторий, CI-логи, backup.**
6. **Без ключа релиз не публикуется.** `release_check.py` должен быть обязательным, а CI — падать.

### F.3 Варианты сборки

| Вариант | applicationId | Ключ | Канал | versionName | versionCode |
|---|---|---|---|---|---|
| debug | тот же | debug | `debug` | `0.5.7`/суффикс | не релиз |
| beta | тот же | release | `beta` | `0.6.0-beta.1` | выше stable |
| stable | тот же | release | `stable` | `0.6.0` | выше beta/предыдущего |

- Не использовать `applicationIdSuffix` для beta/stable: это ломает обновление.
- `versionName` — только отображение; установка решается `versionCode`.
- Beta `versionCode` всегда выше текущего stable; stable, выпущенный после beta, должен быть выше beta, иначе beta-пользователи не смогут перейти на stable.

### F.4 Матрица распространения

| Канал | Плюсы | Ограничения на Android TV | Автоматизация | Безопасность | Вердикт |
|---|---|---|---|---|---|
| Google Play | системные обновления, доверие, staged rollout, Play Protect | Play есть не на всех TV/boxes; TV-ревью; аккаунт; Google управляет signing key | высокая | высокая | лучший для массового потребителя, **если** Play доступен |
| GitHub Releases | нет своего сервера, прозрачные артефакты, подходит open-source | нужен sideload/unknown sources; GitHub могут блокировать; нет staged rollout; rate limits API | средняя (CI + assets) | зависит от подписи APK | лучший для текущего sideload-проекта |
| Свой сервер | полный контроль, staged rollout, метрики без аналитики | инфраструктура, TLS, CDN, стоимость, безопасность | высокая | зависит от эксплуатации | не строить без необходимости |
| Ручной APK | просто, уже работает | нет уведомлений, нет проверки, высокий человеческий фактор | низкая | только подпись | временный/аварийный |

**Рекомендация:** для текущего проекта без remote (наличие Play-аккаунта не подтверждено) — **GitHub Releases как канонический sideload-канал + встроенный updater**. Play добавить позже, если владелец готов к аккаунту/ревью и если целевые устройства имеют Play. Свой сервер не строить, пока нет требований к staged rollout/аналитике.

### F.5 Определение источника установки

На API 30+:

```text
PackageManager.getInstallSourceInfo(packageName).installingPackageName
```

На API 28–29 — `getInstallerPackageName(packageName)` (deprecated, но доступен).

| Installer | Поведение updater |
|---|---|
| `com.android.vending` | in-app APK **не** предлагать; показать «Обновления через Google Play» и открыть Play |
| сам пакет / неизвестный / GitHub-загрузка | разрешить in-app обновление с явным предупреждением |
| OEM-магазин | показать предупреждение; не ломать чужой канал; предложить ручную установку |
| неизвестно (API 28) | считать sideload; предупредить |

Это предотвращает конфликт Play и sideload и не создаёт «второй системы обновления» на Play-устройстве.

### F.6 URL и хостинг

- URL metadata задаётся **build-time** (`UPDATE_BASE_URL`) и не хардкодится в коде как секрет.
- Пока URL нет, updater должен быть **выключен**: страница честно говорит «Обновления вручную», без фиктивной кнопки.
- Хост — allowlist; редиректы только на allowlist-хосты, HTTPS, порт 443, максимум 3.
- Для GitHub: разрешить только реальные хосты релиза/ассетов, без wildcard; не использовать GitHub API без необходимости (rate limit).
- `Cache-Control` metadata: короткий TTL; APK — immutable; `ETag` для metadata.
- Никакого HTTP-fallback: при блокировке 443 показывать ошибку, а не скачивать по HTTP.

---

## G. CI/CD pipeline

### G.1 Текущее состояние

| Элемент | Состояние |
|---|---|
| CI | нет |
| Remote | нет (`git remote -v` пуст) |
| Tags | 0 |
| Release gate | ручной `RELEASE_DISCIPLINE.md:12-23` |
| Artifact gate | `release_check.py`, PASS на текущем APK |
| Unit/instrumented | исторические 467/255; в этом аудите не перезапускались |
| Lint | исторически 0 errors / 18 warnings; не перезапускался |
| Static analysis | только Android Lint; detekt/ktlint нет |
| Dependency verification | файл есть; strict-режим в CI не зафиксирован |
| Mapping | генерируется; в артефакты релиза не упакован |
| Release notes | нет машинного артефакта |

### G.2 Предлагаемый pipeline

```text
1. Checkout + JDK 17 + Android SDK 35 + Gradle 8.9
2. Dependency verification: --dependency-verification=strict
3. Static:
   ./gradlew lintDebug lintRelease
4. Tests:
   ./gradlew testDebugUnitTest testReleaseUnitTest
5. Instrumentation (nightly / release candidate):
   API 28, 34, 35 TV images; один AVD на прогон
6. Build:
   ./gradlew assembleRelease (signed only)
7. Verify artifact:
   apksigner verify --verbose --print-certs
   aapt2 dump badging
   zipalign -c -v 4
   sha256sum
   python tools/release_check.py
8. Retain:
   APK, SHA-256, certificate, mapping.txt, lint report,
   test XML, release-notes JSON, metadata JSON
9. Publish (manual approval):
   GitHub Release assets / Play track
10. Post-publish:
    verify download URL, SHA-256, metadata schema, cert
```

Обязательные условия:

- `assembleRelease` **падает**, если signing properties отсутствуют или сертификат не совпал; unsigned release не должен доходить до публикации.
- `release_check.py` обновить под v3, если v3 включён; сейчас он требует v2.
- `mapping.txt` — артефакт релиза, не в APK; хранить минимум столько же, сколько APK.
- Release notes — ревьюируемый JSON, не автогенерация из commit messages: иначе легко получить выдуманный/нелокализованный текст.
- Metadata генерируется из **того же** APK: `versionCode`, `versionName`, `sizeBytes`, `sha256`; не вводить руками.
- Публикация — только после ручного approval; PR из fork не получают signing secrets.
- Никаких `--no-verify`, отключения lint или тестов ради зелёного pipeline.

### G.3 Variants и подпись в CI

| Вопрос | Решение |
|---|---|
| Где ключ | secret store (CI secret / protected environment), не repo |
| Формат | `OVISIONTV_SIGNING_PROPERTIES` указывает на файл вне workspace; в CI — temp file с правами 600 |
| Пароли | только из secret; не печатать; не писать в лог |
| Кто может собрать release | protected branch/tag + approval |
| Кто может публиковать | отдельный manual job |
| Ротация | только с v3 lineage/Play App Signing; иначе потеря данных |
| Потеря ключа | путь обновления потерян; остаётся uninstall + restore backup |

### G.4 Воспроизводимость

Полная побитовая воспроизводимость APK не гарантируется: подпись, timestamps и пути сборки могут менять байты. Корректная формулировка — **повторяемая сборка**: одинаковые JDK/SDK/Gradle/зависимости/исходники дают функционально тот же артефакт, а SHA-256 фиксируется для конкретного запуска. Для аудита важнее:

- зафиксированные версии Gradle/AGP/Kotlin/Compose/media3;
- dependency verification;
- `--rerun-tasks` для тестов;
- сохранённый mapping;
- явный diff исходников до/после релиза;
- проверка, что APK не изменился после gate.

### G.5 Что добавить позже, но не сейчас

- detekt/ktlint — только отдельным решением, с полным gate; не совмещать с продуктовыми правками.
- SCA/CVE-сканер — отдельный этап; dependency verification не является CVE-сканом.
- Play internal track — после решения о Play.
- Staged rollout — только при наличии сервера/Play; в GitHub Releases его нет.

---

## H. Rollback и восстановление

### H.1 Что Android не позволяет

| Ожидание | Реальность |
|---|---|
| «Тихий откат на предыдущую версию» | Нет. PackageInstaller не устанавливает APK с меньшим `versionCode`; downgrade блокируется |
| «Откат без потери данных» | Обычно нет. uninstall удаляет данные; downgrade через ADB возможен, но схема данных может быть несовместима |
| «Подмена ключа при откате» | Нельзя: подпись нового APK должна совпадать с установленной |
| «Автоматический rollback при краше» | Нет встроенного механизма; custom watchdog запрещён правилами стабильности |
| «Установка без подтверждения» | Нет для обычного приложения; только device owner/privileged, чего у нас нет |

### H.2 Предлагаемая стратегия

1. **Перед установкой** (старая версия, если updater уже есть):
   - создать локальную копию `PRE_UPDATE` через `BackupManager.createLocal`;
   - сохранить `backupId`, `versionCode`, `sha256` в `UpdateStateStore`;
   - если копия не удалась — показать предупреждение, но не блокировать критический фикс без явного согласия.
2. **После установки, первый запуск** (новая версия):
   - `MigrationManager.ensureCurrent()` создаёт `PRE_UPDATE`/`PRE_MIGRATION` копию (уже есть, `BackupMigration.kt:42-66`);
   - до реальных миграций — явный gate, чтобы экраны не читали данные до завершения; текущая no-op схема не требует блокировки;
   - сверить `versionCode` с `pending_version_code`; записать `INSTALLED` или `INSTALL_UNKNOWN`.
3. **Диагностика**:
   - если приложение запускается — показать «Обновление установлено» и, при ошибке, «Подробнее»;
   - не удалять данные и не делать auto-restore: восстановление перезапишет более новые изменения.
4. **Ручное восстановление**:
   - если новая версия не запускается: ADB downgrade `adb install -r -d old.apk` (если прошивка разрешает), затем restore из pre-update backup; данные, созданные после backup, будут потеряны;
   - если downgrade невозможен: uninstall → install old → restore backup; это потеря локальных данных, поэтому только с явным предупреждением;
   - хранить старые подписанные APK и их SHA-256 в release-архиве.
5. **Критический фикс**:
   - только forward-fix; не отзывать версию через «удалённый kill switch»;
   - `mandatory=true` — максимум для security/data-loss, с подтверждением пользователя;
   - не блокировать Launcher на старых версиях без необходимости.

### H.3 Гарантии, которые можно и нельзя давать

**Можно:**

- данные сохраняются при штатной замене пакета с тем же `applicationId`, той же подписью и большим `versionCode`;
- при ошибке установки старая версия и её данные остаются;
- локальная pre-update копия создаётся до миграции (при успешном запуске новой версии);
- приложение не удаляет пользовательские данные при ошибке обновления.

**Нельзя:**

- гарантировать, что новая версия не содержит ошибок;
- гарантировать автоматический откат;
- гарантировать совместимость данных при ручном downgrade;
- гарантировать сохранность данных при смене ключа/пакета или uninstall;
- гарантировать, что OEM-установщик не удалит данные (это вне контроля приложения).

---

## I. Матрица совместимости и QA

### I.1 Совместимость версий

| Проверка | Правило | Если не выполнено |
|---|---|---|
| `versionCode` | новый > установленного | не предлагать установку; показать «новее/старее» |
| `minSdk` / `minimumAndroidApi` | ≤ API устройства | INCOMPATIBLE, без скачивания |
| ABI | universal или пересечение с `Build.SUPPORTED_ABIS` | INCOMPATIBLE |
| `applicationId` | совпадает | не устанавливать |
| Подпись | cert нового = cert установленного | отказать, удалить файл |
| Свободное место | `size * 2 + 32 МиБ` (оценка, не гарантия) | предупредить/отказать |
| Канал | metadata channel = выбранный | не принимать metadata |
| `minimumSupportedVersionCode` | текущий ≥ min | EOL-предупреждение, не блокировка Launcher |
| Installer source | Play → deep link; sideload → updater | не смешивать каналы |

### I.2 Устройства и статусы

| Платформа/условие | Статус | Комментарий |
|---|---|---|
| AVD `hume-tv`, API 34, x86, 1920×1080 | **PASS для текущего приложения** | не для updater; updater не реализован |
| API 28 | **NOT TESTED** | нет system image в этой среде |
| API 29 | **NOT TESTED** | нет image |
| API 35 | **NOT TESTED** | нет image |
| ARM32 | **NOT TESTED** | нет устройства |
| ARM64 | **NOT TESTED** | нет устройства |
| 1 ГБ RAM / LOW | **NOT TESTED** | логика `AdaptivePolicy` покрыта тестами, но не updater |
| 720p / 1080p / 4K | **720p/1080p — исторические проверки; 4K NOT TESTED** | не переносить на updater |
| Физический Android TV | **NOT TESTED** | нет устройства |
| Google TV | **NOT TESTED** | нет устройства |
| AOSP/операторский TV | **NOT TESTED** | установщик может быть ограничен |
| Play-установка | **NOT TESTED** | Play-аккаунта/листинга нет |
| Sideload/unknown sources | **NOT TESTED** | нет реального сценария |
| ИК/Bluetooth-пульт | **NOT TESTED** | AVD-клавиатура ≠ реальный пульт |
| TalkBack | **NOT TESTED** | есть semantics-тесты, но не голосовой проход |
| 24 h soak | **NOT TESTED** | нет прогона |

### I.3 QA-матрица обновлений

| ID | Сценарий | Метод | Статус |
|---|---|---|---|
| UPD-QA-01 | Текущая версия/канал отображаются верно | UI + `BuildConfig` | NOT TESTED (новая UI) |
| UPD-QA-02 | Manual check без сети | эмулятор, network off | NOT TESTED |
| UPD-QA-03 | Metadata: валидный JSON | unit | NOT TESTED |
| UPD-QA-04 | Metadata: неизвестная схема, битый JSON, oversized | unit | NOT TESTED |
| UPD-QA-05 | Metadata: неверный channel/URL/hash/size | unit | NOT TESTED |
| UPD-QA-06 | `versionCode` equal/lower/higher | unit | NOT TESTED |
| UPD-QA-07 | SHA-256 mismatch → удаление, без установки | unit/instrumented | NOT TESTED |
| UPD-QA-08 | Подпись APK не совпадает → отказ | instrumented | NOT TESTED |
| UPD-QA-09 | Скачивание: прогресс, cancel, resume | instrumented | NOT TESTED |
| UPD-QA-10 | Обрыв сети/таймаут/429/5xx | instrumented + fake server | NOT TESTED |
| UPD-QA-11 | Нет свободного места | instrumented/fake | NOT TESTED |
| UPD-QA-12 | Install permission denied → объяснение и настройки | AVD | NOT TESTED |
| UPD-QA-13 | PackageInstaller success | AVD, тестовый APK vc81 | NOT TESTED |
| UPD-QA-14 | Cancel системного установщика | AVD | NOT TESTED |
| UPD-QA-15 | Process death во время install → reconcile | AVD | NOT TESTED |
| UPD-QA-16 | Данные после update (плитки, папки, профили, PIN, заставка) | AVD | исторически PASS для vc2→3/vc12→13; для updater NOT TESTED |
| UPD-QA-17 | Downgrade blocked | AVD/ADB | исторически PASS (`INSTALL_FAILED_VERSION_DOWNGRADE`); для updater NOT TESTED |
| UPD-QA-18 | Beta → Stable при меньшем stable | unit/UI | NOT TESTED |
| UPD-QA-19 | Play installer → deep link, без sideload | fake installer/reading | NOT TESTED |
| UPD-QA-20 | TalkBack/D-pad/BACK на странице | AVD + физический | NOT TESTED |
| UPD-QA-21 | Первый запуск после update: миграция/ошибка | AVD | NOT TESTED |
| UPD-QA-22 | API 28, 34, 35 | matrix | только API 34 AVD для текущего app; updater NOT TESTED |
| UPD-QA-23 | Физический TV/Google TV | ручной | NOT TESTED |
| UPD-QA-24 | `release_check.py` на релизе | gate | **PASS** для текущего APK |
| UPD-QA-25 | CI pipeline | CI | NOT TESTED (CI нет) |

**Правило:** ни одна строка NOT TESTED не становится PASS по чтению кода. Для коммерческого релиза обязательны AVD + физический ТВ + failure-инъекции.

### I.4 EOL, критичные исправления и beta

- `minimumSupportedVersionCode` — единственный механизм EOL-предупреждения; он **не блокирует** Launcher и не удаляет данные.
- Если текущая версия ниже `minimumSupportedVersionCode`, показать «Эта версия больше не поддерживается» и доступное обновление; если совместимого обновления нет — предложить ручную установку, не «кирпичить» приложение.
- `mandatory=true` — только для security/data-loss; всё равно требует системного подтверждения. Никакого silent install.
- Beta — best-effort канал без гарантированного срока поддержки; Stable — основной. Численные окна поддержки не выдумывать: они фиксируются владельцем отдельно.
- Критический фикс выпускается только вперёд; удалённого kill switch и автоматического downgrade нет.

### I.5 Android TV и Google TV: различия для установки

| Аспект | Android TV (AOSP/оператор) | Google TV |
|---|---|---|
| Системный установщик | PackageInstaller UI зависит от OEM | PackageInstaller + Google Play Protect |
| Unknown sources | может быть скрыт/запрещён оператором | обычно есть, но с предупреждениями Play Protect |
| Play | может отсутствовать | обычно есть |
| Обновления | часто только sideload/магазин оператора | Play + sideload |
| Пульт | ИК/Bluetooth, разное поведение BACK/OK | Google-пульт, голос |
| Ограничения | OEM может блокировать установку APK | политики Play/Protect |

Вывод: **нельзя обещать одинаковый install-flow на всех ТВ**. Приложение должно проверять `PackageInstaller`, разрешение и наличие обработчика, а при отказе — объяснять и не пытаться обойти систему.

---

## J. Дорожная карта внедрения

### J.1 Решения, нужные до кода (Stage 4)

1. **Источник metadata:** GitHub Releases / собственный хостинг / только manual. Без URL updater выключен.
2. **Канал по умолчанию:** Stable; beta — отдельно.
3. **Автопроверка:** Manual / Daily / Weekly; рекомендуемый старт — Manual до появления хоста, затем Daily.
4. **Установка:** разрешать ли `REQUEST_INSTALL_PACKAGES` и in-app PackageInstaller; или только deep link/ручная установка.
5. **Play:** будет ли Play-канал; если да — in-app updater не должен конкурировать.
6. **Metadata-подпись:** нужна ли отдельная (влияет на второй ключ и CI).
7. **IA:** переносить ли «Обновления» в «Система» и убирать ли каталог Aerial со страницы обновлений.
8. **Ключ:** политика custody/backup; включать ли v3.
9. **Privacy:** новая версия политики при появлении сетевого хоста.
10. **Kids:** запрет auto/install в детском профиле (рекомендуется).

### J.2 Этапы

| Этап | Содержание | Результат | Риск |
|---|---|---|---|
| 0 | Решения J.1, фиксация URL/канала/политики | утверждённый scope | без решений код не начинать |
| 1 | Модели, `VersionComparator`, `ReleaseMetadataParser`, `UpdateStateStore`, unit-тесты | проверяемая metadata/состояние без сети | низкий |
| 2 | `ReleaseMetadataProvider`, `UpdateRepository`, кэш, ETag, лимиты, fake-server тесты | проверка версии без установки | средний |
| 3 | `UpdateDownloader`, `IntegrityVerifier`, free space, cancel/resume, cleanup | скачивание и проверка | средний |
| 4 | `SignatureVerifier`, `InstallationManager`, `InstallResultReceiver`, permission UX | установка через системный installer | высокий |
| 5 | `SoftwareUpdateSection`, notes overlay, строки EN/RU, состояния, D-pad/TalkBack | полноценный UX | средний |
| 6 | PRE_UPDATE backup из старой версии, migration registry/gate, first-launch reconcile | сохранность данных | высокий |
| 7 | CI/CD, v3, release notes/metadata generation, artifact retention | автоматизированный релиз | средний |
| 8 | QA-матрица: AVD API 28/34/35, failure injection, физический TV, 1 ГБ, 24 h | доказательства | высокий |

### J.3 Критерии готовности (Definition of Done)

- Metadata/скачивание/установка работают end-to-end на AVD API 34 и физическом ТВ.
- SHA-256 mismatch, wrong cert, downgrade, cancel, offline, 429, disk full — все дают понятное состояние и не устанавливают APK.
- Данные после update сохранены: профили, плитки, папки, favorites, hidden, saver/theme, language, kids PIN.
- Process death во время скачивания/установки восстанавливается без потери данных.
- `release_check.py` + CI + mapping + notes/metadata — зелёные.
- Нет новых permissions, кроме явно одобренного `REQUEST_INSTALL_PACKAGES`.
- Нет секретов в APK/repo/логах.
- Документация: privacy v5 (если хост), release process, rollback instructions.
- Все NOT TESTED закрыты или явно приняты владельцем.

### J.4 Что не делать

- Не добавлять WorkManager/OkHttp/DownloadManager «для удобства» без отдельного решения.
- Не делать silent install/root/device-owner.
- Не менять `applicationId`/ключ без migration plan.
- Не вводить второй updater в Activity/Settings помимо `UpdateManager`.
- Не генерировать release notes из commit messages.
- Не показывать фейковый прогресс/ETA.
- Не удалять данные при ошибке/миграции.
- Не обещать автоматический rollback.
- Не публиковать APK без отдельного разрешения владельца.

---

## K. Итоговый вердикт

### K.1 Ответы на 7 вопросов

1. **Надёжна ли текущая система обновлений?**  
   Как продуктовая функция — **нет, 0/10**: её нет. Как ручной процесс — **ограниченно надёжна**: подпись и gate есть, данные исторически сохраняются, downgrade блокируется. Но пользователь не узнаёт об обновлении и не защищён от подмены источника.

2. **Что блокирует релиз коммерческого уровня?**  
   - отсутствие metadata/хоста и каналов;  
   - отсутствие downloader/verifier/installer;  
   - отсутствие `REQUEST_INSTALL_PACKAGES`/PackageInstaller;  
   - отсутствие CI, remote, тегов и release notes;  
   - unsigned release возможен;  
   - только v2, нет политики ротации;  
   - нет физического ТВ/API 28–35;  
   - нет pre-update backup со стороны старой версии;  
   - политика конфиденциальности запрещает сервер разработчика.

3. **Можно ли гарантировать подлинность APK?**  
   После внедрения: **да, в пределах Android-модели** — SHA-256 + cert-match + системный installer + монотонный `versionCode`, при условии, что signing key не скомпрометирован. Сейчас приложение не проверяет APK; гарантию даёт только PackageManager при ручной установке. SHA-256 сам по себе подлинность **не** гарантирует.

4. **Какие ограничения установки на Android TV?**  
   Нет silent install; нужен системный диалог и часто «unknown sources»; OEM может ограничить; downgrade запрещён; подпись должна совпадать; нужен `versionCode` выше; при Play-версии нельзя смешивать sideload; нехватка места/слабый ТВ могут прервать установку.

5. **Как не потерять данные?**  
   Не менять `applicationId`/ключ; всегда увеличивать `versionCode`; не делать uninstall/clear data; делать pre-update backup; миграционный gate; проверять данные после update. При смене ключа/пакета или uninstall данные неизбежно теряются; при ручном downgrade совместимость схемы не гарантируется.

6. **Лучший способ распространения?**  
   Для текущего sideload-проекта: **GitHub Releases + встроенный updater**, с проверкой подписи и явным unknown-sources UX. Для массового потребителя с Play — **Google Play**, но только если целевые ТВ имеют Play; тогда in-app updater отключается. Свой сервер — только при доказанной необходимости.

7. **Готов ли проект к внедрению?**  
   **К проектированию — да; к коду — после Stage 4 approval.** Текущий ручной релиз можно продолжать, но автоматическое обновление без решений J.1 и без тестов выпускать нельзя. Оценка готовности: **архитектура 8/10, реализация 0/10, доказательства 2/10**.

### K.2 Сводная таблица

| Область | Статус | Оценка |
|---|---|---|
| Текущий ручной релиз | работает, исторические PASS | 7/10 |
| Подпись артефакта | v2, cert, gate PASS | 8/10 |
| In-app update | отсутствует | 0/10 |
| Release metadata | отсутствует | 0/10 |
| Download/verify/install | отсутствует | 0/10 |
| Data preservation | backup layer есть; update-путь не автоматизирован | 5/10 |
| CI/CD | отсутствует | 1/10 |
| Security design | требует реализации; текущая поверхность мала | 4/10 |
| QA обновлений | не начат | 0/10 |
| Документация | аудит/архитектура готовы; release docs частично устарели | 6/10 |

### K.3 Финальная остановка

Stage 1–3 выполнены, Stage 4 получен как запрос владельца на реализацию, Stage 5 выполнен. Публикация, изменение ключа, `applicationId` или версии release-канала — только с отдельным разрешением владельца. Детали реализации и ограничения проверок: `UPDATE_SYSTEM/01_IMPLEMENTATION_STATUS.md`.

---

## Приложение. Быстрые ссылки на доказательства

- Версия/сборка: `app/build.gradle.kts:26-32`, `69-80`.
- Текущая «Обновления» = Aerial: `ui/SettingsScreen.kt:318`, `1097`, `2741-2767`, `4534-4540`, `4561-4580`.
- Надпись «вручную»: `res/values/strings.xml:257`, `res/values-ru/strings.xml:267`.
- HTTP-паттерны: `data/AerialRefresh.kt:68-183`.
- Backup/migration: `backup/BackupMigration.kt:32-98`, `backup/BackupModels.kt:7-38`, `OVisionApp.kt:23-25`.
- Release gate: `tools/release_check.py:29-32`, `134-155`; `ARCHITECTURE_AUDIT/RELEASE_DISCIPLINE.md:7-23`, `29-38`.
- Историческое обновление без потери данных: `FIXES.md:2133`, `1774-1786`.
- Приватность: `res/raw/privacy_ru.txt:5`.
- HTTPS-only: `res/xml/network_security_config.xml`.
- Манифест на момент аудита без install-разрешений: `AndroidManifest.xml:24-55`. После Stage 5 добавлены `REQUEST_INSTALL_PACKAGES` и non-exported `InstallResultReceiver`; см. `01_IMPLEMENTATION_STATUS.md`.
- APK на момент аудита: `app/build/outputs/apk/release/app-release.apk`, 4 771 546 B, SHA-256 `7c590d…312e`, v2, cert `327a50…7b10`. Артефакт Stage 5: 4 863 122 B, SHA-256 `4fae4b…1170`, см. `01_IMPLEMENTATION_STATUS.md`.

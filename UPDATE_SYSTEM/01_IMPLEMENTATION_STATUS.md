# Система обновлений — статус реализации

**Проект:** OVisionTV Launcher (Kotlin), `com.whatuneed.ovisiontv.kotlin`  
**Версия:** `0.5.8`, `versionCode 81`  
**Дата:** 16.11.2026  
**Основание:** Stage 1–3 из `00_AUDIT_AND_ARCHITECTURE.md`; Stage 4 получен как запрос владельца на поэтапную реализацию; Stage 5 (implementation) выполнен.  
**Публикация:** **не выполнялась**. Это локальная сборка; GitHub-страница, теги и релизы не создавались.

---

## 1. Что реализовано

Добавлен пакет `com.whatuneed.ovisiontv.kotlin.update`:

| Файл | Назначение |
|---|---|
| `UpdateModels.kt` | каналы, режимы проверки, фазы, ошибки, модель релиза/ассета/состояния |
| `UpdateMetadata.kt` | строгий парсер JSON schema 1 и политика HTTPS/host/path |
| `UpdateNetwork.kt` | `HttpURLConnection`-клиент метаданных, ETag/304, редиректы, лимиты; `UpdateRepository` |
| `UpdateStore.kt` | `update_state` SharedPreferences + ограниченный кэш метаданных в `cacheDir/update` |
| `UpdatePolicy.kt` | сравнение только по `versionCode`, проверка API/ABI/EOL, требуемое место |
| `UpdateDownloader.kt` | потоковая загрузка в `cacheDir/updates`, `.part`, Range только с валидатором, SHA-256, атомарная замена |
| `UpdateInstall.kt` | предпроверка APK/подписи, `PackageInstaller`, `InstallResultReceiver` |
| `UpdateManager.kt` | единая оркестрация, `StateFlow`, восстановление после смерти процесса, pre-update backup |
| `UpdateAutoCheck` (в `UpdateManager.kt`) | ежедневная/еженедельная проверка из Home; пропускает Kids Mode |

UI:

- `ui/SoftwareUpdateScreen.kt` — отдельный TV-экран, D-pad-фокус, прогресс, отмена, release notes overlay;
- `SoftwareUpdateActivity.kt` — internal, `exported=false`, landscape, `excludeFromRecents`;
- `Settings → Device → System → Software Update` — единственная точка входа;
- `Settings → Device → System → Updates` по-прежнему отвечает за **каталог заставок**, не за APK. Подпись страницы уточнена: обновления приложения находятся в Software Update.

Манифест:

- `REQUEST_INSTALL_PACKAGES`;
- non-exported `InstallResultReceiver` с action `com.whatuneed.ovisiontv.kotlin.action.UPDATE_RESULT`;
- non-exported `SoftwareUpdateActivity`.

Приватность: `privacy_ru.txt` / `privacy_en.txt` обновлены до версии 5. Раскрыты GitHub-хосты, загрузка метаданных и APK, IP/User-Agent, отсутствие аккаунта/cookie/токена, отключение автопроверок, запрет в Kids Mode, SHA-256/сертификат и системный установщик.

Новых библиотек нет: только `HttpURLConnection`, `org.json`, `PackageInstaller`, `AlarmManager`-независимый ручной/фоновый запуск через `LaunchedEffect`. `WorkManager`, `DownloadManager`, `FileProvider`, `OkHttp`, Room, Hilt, Play Core и foreground service не добавлены.

---

## 2. Поведение

### 2.1 Источник не настроен

`UPDATE_METADATA_URL` — **compile-time** `buildConfigField`, берётся из env `OVISIONTV_UPDATE_URL`. Если env пуст:

- экран показывает «Update source is not configured yet»;
- приложение **не ходит в сеть**;
- авто-проверка ничего не делает;
- это штатное состояние до появления GitHub-страницы.

Текущий release-артефакт собран с пустым URL: `UPDATE_METADATA_URL = ""`.

### 2.2 Проверка

- каналы: `stable` / `beta`;
- режимы: `manual`, `daily` (по умолчанию), `weekly`;
- авто-проверка запускается после первого кадра Home, не в `Application.onCreate`, и не в Kids Mode;
- ручная проверка обходит cadence, но не блокирует сеть при уже идущей операции;
- сравнение только по `versionCode`; `versionName` — отображение;
- проверяются `minimumAndroidApi`, ABI ассета (если указан), `minimumSupportedVersionCode` (EOL), свободное место;
- 304 сохраняет кэш; 429/5xx показывают ошибку без бесконечного retry;
- метаданные ограничены 256 КБ, 20 релизами, URL ≤ 2048 символов;
- разрешены только HTTPS, порт 443, без userInfo/fragment; GitHub-хосты и хост самого metadata URL; каждый редирект перепроверяется (≤ 3).

### 2.3 Загрузка

- потоково, буфер 64 КБ, в `cacheDir/updates`;
- проверяется объявленный размер и SHA-256;
- Range/If-Range используется только если сервер дал `ETag`/`Last-Modified`; иначе файл перекачивается;
- `.part` и `.validator` очищаются; валидный `.part` не перекачивается;
- прогресс показывается в байтах и процентах; отмена по BACK/D-pad останавливает job и возвращает `AVAILABLE`;
- старые `.part` старше 24 ч удаляются.

### 2.4 Установка

- перед установкой выполняется предпроверка: размер, SHA-256, packageName, `versionCode`, `versionName`, совпадение signer-дайджестов с установленным приложением;
- это **не** полноценная криптографическая проверка APK: финальную проверку делает системный `PackageInstaller`; предпроверка лишь отсекает явно чужой/битый файл;
- используется `PackageInstaller.Session` (`MODE_FULL_INSTALL`) и **mutable explicit PendingIntent**; system installer показывает подтверждение;
- `REQUEST_INSTALL_PACKAGES` проверяется через `canRequestPackageInstalls()`; если запрещено, UI предлагает открыть настройки и после возврата позволяет повторить установку;
- silent install, root, device-owner, `ACTION_INSTALL_PACKAGE` и `FileProvider` не используются;
- `InstallResultReceiver` не exported; результат сохраняется в `update_state`, а `UpdateManager.restore()` сверяет `versionCode` после смерти процесса;
- перед установкой создаётся локальный pre-update backup (`BackupTrigger.PRE_UPDATE`); сбой backup не блокирует обновление;
- данные сохраняются за счёт того же `applicationId`, той же подписи и монотонного `versionCode`; uninstall/clear data не выполняются;
- downgrade не поддерживается; сообщение об этом честное.

---

## 3. Метаданные для GitHub-страницы

Формат: JSON, `schema = 1`. Пример:

```json
{
  "schema": 1,
  "channel": "stable",
  "generatedAt": "2026-11-16T00:00:00Z",
  "expiresAt": "2027-11-16T00:00:00Z",
  "releases": [
    {
      "versionCode": 81,
      "versionName": "0.5.8",
      "channel": "stable",
      "releaseDate": "2026-11-16T00:00:00Z",
      "minimumAndroidApi": 28,
      "minimumSupportedVersionCode": 1,
      "mandatory": false,
      "notes": {
        "en": ["Software Update screen", "Signed in-app update"],
        "ru": ["Экран Software Update", "Подписанное встроенное обновление"]
      },
      "assets": [
        {
          "url": "https://github.com/<owner>/<repo>/releases/download/v0.5.8/ovisiontv-0.5.8.apk",
          "sha256": "<64 lowercase hex characters>",
          "sizeBytes": 4862594
        }
      ]
    }
  ]
}
```

Обязательные поля:

- `schema` — ровно `1`;
- `channel` — `stable` или `beta`;
- `generatedAt`, `expiresAt` — ISO-8601 UTC; после `expiresAt` метаданные **не используются для установки**, остаются только для диагностики;
- `releases[]` — от новых к старым; дубликаты `versionCode` отклоняются;
- `versionName` — до 64 символов, допустимы буквы/цифры/`.`, `_`, `-`, `+`;
- `minimumAndroidApi` — целое ≥ 1;
- `minimumSupportedVersionCode` — целое ≥ 0 (`0` = нет EOL);
- `notes.en` и `notes.ru` — непустые массивы строк (до 20 строк, каждая ≤ 200 символов);
- ровно один `asset`: `url`, `sha256` (64 hex), `sizeBytes` (1…100 МБ);
- `abi` необязателен; если указан — только `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`.

Если `notes` на одном языке нет, используется `en`; полностью пустые notes отклоняются.

### 3.1 Где разместить

Два допустимых варианта:

1. **GitHub Release asset** (рекомендуется):
   - metadata: `https://github.com/<owner>/<repo>/releases/latest/download/updates-stable.json`
   - APK: `https://github.com/<owner>/<repo>/releases/download/v0.5.8/ovisiontv-0.5.8.apk`
2. **GitHub Pages**:
   - metadata: `https://<owner>.github.io/<repo>/updates/stable.json`
   - APK можно положить рядом; хост метаданных автоматически разрешён и для ассета.

Хосты `github.com`, `objects.githubusercontent.com`, `release-assets.githubusercontent.com`, `github-releases.githubusercontent.com` разрешены для ассетов. Сторонние CDN не разрешены.

### 3.2 Сборка с URL

Windows/Git Bash:

```bash
OVISIONTV_UPDATE_URL='https://github.com/<owner>/<repo>/releases/latest/download/updates-stable.json' \
GRADLE_USER_HOME=C:/gradle-home \
./gradlew :app:assembleRelease -Pkotlin.incremental=false --console=plain
```

Проверка URL в артефакте:

```bash
grep UPDATE_METADATA_URL \
  app/build/generated/source/buildConfig/release/com/whatuneed/ovisiontv/kotlin/BuildConfig.java
```

Перед публикацией:

```bash
APKSIGNER='C:/Users/5518~1/AppData/Local/Android/Sdk/build-tools/36.0.0/apksigner.bat' \
PYTHONIOENCODING=utf-8 python tools/release_check.py
```

`release_check.py` должен показать `v2`, ожидаемый сертификат, versionCode/versionName, minSdk 28, targetSdk 35, паритет RU/EN.

> URL — публичный, не секрет. Менять его можно только новой сборкой. `applicationId` и signing key менять нельзя; это отдельное решение владельца с миграцией данных.

---

## 4. Проверки

### 4.1 Выполнено

| Проверка | Результат |
|---|---|
| `testDebugUnitTest` | **481 / 481**, 0 failures |
| `connectedDebugAndroidTest` на AVD `hume-tv` (API 34, x86) | **266 / 266**, 0 failures |
| `lintDebug` | **0 errors**, 19 warnings (включая общие `GradleDependency`, `DiscouragedApi`) |
| `assembleRelease` | PASS |
| `apksigner verify` | v1 false, **v2 true**, v3 false, v4 false |
| `release_check.py` | PASS |
| версия | `0.5.8 (81)` |
| размер release APK | **4 863 122 B** |
| SHA-256 release APK | `4fae4bb9ea6a5aaaf5298e923cb2de1b815b14bea7819eb2dbea7a6d5bc41170` |
| сертификат | `327a50453093b783e84b1f2645b03319477d89966ea6a653dab53d5a567d7b10` |
| RU/EN resources | 637 / 637 |

### 4.2 Проверено на AVD вручную

- пустой `UPDATE_METADATA_URL` → экран «Update source is not configured yet», сеть не вызывается;
- `Settings → Device → System → Software Update` открывается и работает D-pad;
- фокус на строках, переходы вниз/вверх, BACK возвращает в System;
- состояние `READY` с подготовленными локально метаданными и APK;
- нажатие «Install update» открывает **системный экран подтверждения** Android;
- подтверждение устанавливает APK, приложение перезапускается, `versionCode` меняется;
- `InstallResultReceiver` получает extras и сохраняет результат;
- **важный найденный дефект:** исходный `PendingIntent` был `FLAG_IMMUTABLE`; `PackageInstaller` не мог заполнить `EXTRA_STATUS`/`EXTRA_INTENT`, callback приходил с `Integer.MIN_VALUE`. Исправлено на `FLAG_MUTABLE`; после этого системный prompt и установка работают;
- повторная установка после выдачи `REQUEST_INSTALL_PACKAGES` проверена;
- **Kids Mode:** `SoftwareUpdateScreen` не делает restore/check и показывает только текущую версию и сообщение о недоступности; строка Software Update скрыта в System. Проверено Compose-тестом.
- **Downloader:** `UpdateDownloaderTest` проверяет успешную запись, несовпадение SHA-256, запрет чужого хоста и повторное использование уже проверенного файла через fake `HttpURLConnection` (без сети).
- исправлена ветка retry: если APK уже готов, «Try again» устанавливает его, а не скачивает заново; в состоянии `PERMISSION` после выдачи разрешения появляется «Install update»;
- проверка SHA-256 добавлена и в preflight установки, не только в downloader.

Тестовые APK для этого прогона были подготовлены локально (`81→81`, `82→83`) и подложены в `cacheDir`; **реальный GitHub download не использовался**.

### 4.3 НЕ проверено

- реальный GitHub Releases/Pages URL, редиректы GitHub CDN, ETag/304 на реальном сервере;
- реальная загрузка APK по сети и Range/resume на GitHub; тест downloader использует fake connection, не сеть;
- реальный 403/429/5xx от GitHub;
- физический Android TV / Google TV, ARM32/ARM64, API 28–33/35, 1 ГБ RAM;
- OEM-установщик, Play Protect, Google TV против Android TV;
- TalkBack-озвучка новых строк;
- 24-часовой soak, Doze, reboot во время ожидания установки;
- Play-канал, metadata signature, v3/lineage, CI;
- 4K/HDR и слайд-шоу во время обновления.

Эти пункты остаются `NOT TESTED`; AVD-результат не является сертификацией физического ТВ.

---

## 5. Ограничения и сознательные решения

1. **Нет silent install.** Всегда системный prompt; на части TV может потребоваться включить unknown sources.
2. **Нет обещания автоматического rollback.** Android не даёт приложению откатить чужую установку. Данные защищаются backup + миграционным gate, но downgrade вручную может быть destructive.
3. **SHA-256 не заменяет подпись.** Integrity проверяется downloader, авторство — сравнением signer + финальной проверкой `PackageInstaller`.
4. **Нет metadata signature в v1.** Метаданные защищены HTTPS и allowlist; подпись метаданных — отдельное решение (см. J.1 аудита).
5. **Нет v3.** Оставлено v2, как требует release gate и `minSdk 28`; ротация ключа — отдельное решение владельца. Включение v3 без проверки схемы release gate не делалось.
6. **Нет CI.** Сборка и `release_check.py` выполняются локально; pipeline не создан.
7. **Нет Play Core.** Если появится Play-версия, встроенный updater должен уступать Play, иначе конфликт источников.
8. **Нет фонового частого опроса.** Только daily/weekly/manual; проверка не на старте `Application`.
9. **Kids Mode:** авто-проверка пропускается; установка в Kids не инициируется. Окончательная блокировка установки в Kids — решение владельца.
10. **Кэш обновления — не backup.** APK в `cacheDir` не входит в резервную копию; `update_state` не содержит URL/токенов.

---

## 6. Следующие шаги владельца

1. Создать GitHub-репозиторий/страницу релизов, когда лаунчер будет готов.
2. Для каждого релиза:
   - поднять `versionCode` (следующий — `82`);
   - собрать release с `OVISIONTV_UPDATE_URL`;
   - прогнать `release_check.py`;
   - загрузить APK и `updates-stable.json` (или Pages JSON);
   - проверить, что `sha256`/`sizeBytes` в метаданных совпадают с APK.
3. Решить открытые вопросы J.1: beta-канал, metadata signature, `REQUEST_INSTALL_PACKAGES` policy, Play, Kids install ban, key custody/v3, privacy bump.
4. Проверить на физическом ТВ: системный prompt, unknown sources, D-pad, установка, сохранение данных, повторный запуск.
5. Только после этого — публикация. Публикация и теги требуют отдельного разрешения.

---

## 7. Команды проверки

```bash
GRADLE_USER_HOME=C:/gradle-home ./gradlew :app:testDebugUnitTest \
  -Pkotlin.incremental=false --console=plain

export ANDROID_SERIAL=emulator-5554
export ANDROID_SDK_ROOT='C:\Android\Sdk'
GRADLE_USER_HOME=C:/gradle-home ./gradlew :app:connectedDebugAndroidTest \
  -Pkotlin.incremental=false --console=plain

GRADLE_USER_HOME=C:/gradle-home ./gradlew :app:lintDebug \
  -Pkotlin.incremental=false --console=plain

APKSIGNER='C:/Users/5518~1/AppData/Local/Android/Sdk/build-tools/36.0.0/apksigner.bat' \
PYTHONIOENCODING=utf-8 python tools/release_check.py
```

---

## 8. Итог

Встроенный updater реализован и проверен на AVD вплоть до системного подтверждения и фактической установки. Он не публикует релизы, не обходит Android, не устанавливает молча и не обещает откат. До появления GitHub-страницы он честно находится в состоянии `NOT_CONFIGURED` и не ходит в сеть.

**Готовность:** реализация — 8/10; доказательства на AVD — 7/10; физический ТВ и реальный GitHub — 0/10 (не проверялись). Публикация не выполнялась.

# Backup / Restore — состояние реализации

Дата фиксации: **2026-11-15 07:28 RTZ**  
Ветка: `master`  
Статус: **реализация начата и остановлена на проверенной контрольной точке; UI и restore ещё не прошли end-to-end проверку на AVD.**

---

## 1. Что уже сделано

### 1.1. Формат и модели

Каталог: `app/src/main/java/com/whatuneed/ovisiontv/kotlin/backup/`

- `BackupModels.kt` — контракт формата:
  - kind `ovisiontv-backup`, `FORMAT_VERSION = 1`, `DATA_SCHEMA = 1`;
  - расширение `.ovbk`;
  - имена `manifest.json`, `payload.enc`, `components/`;
  - лимиты: архив 32 MiB, распакованный 64 MiB, запись 8 MiB, 256 записей, JSON 8 MiB, ratio 100:1;
  - `MAX_PROFILES = 3`, `LOCAL_RETENTION = 5`, `PASSWORD_MIN_CHARS = 8`, `KDF_ITERATIONS = 210_000`;
  - категории: profiles, home, discovery, kids, weather, saver, language, content, startup;
  - модели профилей, home/discovery/kids/weather/saver/language/content/startup;
  - manifest, component entries, encryption params, index entry, preview, warnings, stage, exception, UI state.
- `BackupJson.kt` — типизированный JSON-кодек всех компонентов. Сырой XML SharedPreferences не экспортируется.
- `BackupEncryption.kt` — AES-256-GCM + PBKDF2-HMAC-SHA256, свежие salt/nonce на каждый экспорт.
- `BackupArchive.kt` — ZIP-архив с ограничениями, безопасными именами, SHA-256, отдельным внешним manifest для зашифрованного варианта.
- `BackupValidator.kt` — проверка manifest/schema, целостности, политики «discovery/manual weather только с паролем».
- `BackupSource.kt` — чтение существующих stores в `BackupData`.
- `BackupRepository.kt` — локальные копии в `filesDir/backups`, `index.json`, retention, временные файлы, атомарная запись.
- `BackupRestoreEngine.kt` — применение данных; один commit на prefs-файл; профили применяются последними; погодный кэш очищается и generation увеличивается; `SetupProgress.markDone`.
- `BackupManager.kt` — публичный API: `list`, `delete`, `createLocal`, `export`, `previewLocal`, `previewUri`, `restore`, `rollbackTo`, `recoverIfNeeded`; один `Mutex`, IO на `Dispatchers.IO`; обязательный pre-restore snapshot + журнал + rollback.
- `StorageGate.kt` — process-wide `ReentrantReadWriteLock` для будущей интеграции с записями Home/Discovery. Сейчас создан, но ещё не подключён ко всем писателям.
- `RestoreJournal.kt` — атомарный `filesDir/backups/journal.json`, состояния prepared/applying/applied/rolled_back/failed.
- `BackupScheduler.kt` — ежедневный неточный `AlarmManager.setInexactRepeating`, prefs `backup_state`, request code 7301.
- `BackupAlarmReceiver.kt` — receiver для автоматической локальной копии.
- `BackupRecovery.kt` — восстановление после незавершённого restore, вызывается из `OVisionApp.onCreate`.

### 1.2. UI и навигация

- `ui/BackupScreen.kt` — экран Backup & Restore:
  - HOME / CREATE / SOURCE / PREVIEW / PROGRESS / SETTINGS / PASSWORD;
  - выбор категорий;
  - локальное создание копии;
  - экспорт через SAF `CreateDocument`;
  - импорт через SAF `OpenDocument`;
  - предпросмотр и предупреждения;
  - восстановление;
  - пароль для зашифрованного экспорта/импорта;
  - автоматический режим on/off;
  - retention/статус.
- `BackupActivity.kt` — отдельная activity, `exported=false`, без intent-фильтра, внутри `TvRemoteActivity`, с `LocalePreference.wrap`.
- `SettingsScreen.kt` — строка `Backup & Restore` добавлена в `PAGE_SYSTEM`.
- `AndroidManifest.xml` — добавлены `BackupActivity` и `BackupAlarmReceiver` (`exported=false`).
- Строки EN/RU: 60 ключей `backup_*` в обоих `strings.xml`.

### 1.3. Модель отсутствующих приложений

- `TvApp` получил `available: Boolean = true`.
- `HomeOrderStore.missingApp(packageName)` сохраняет позицию отсутствующего пакета, а не выбрасывает его.
- `AppSearch` исключает недоступные приложения из home/search.
- `FolderEdit` не возвращает недоступное приложение.
- `KidsMode.KidsFilter` исключает недоступные.
- `HomeScreen`: запуск недоступного приложения блокируется и показывает `app_unavailable`; в контекстном меню недоступного приложения скрыты Open/App info/Uninstall, остаются Move/Move to folder.
- `FolderOverlay`: те же правила; Open/App info/Uninstall скрыты для недоступного.
- `AppCard`: затемнение и подпись «Unavailable on this TV».
- `HomeOrderTest` обновлён под новую семантику.

### 1.4. Тесты

- `app/src/test/java/com/whatuneed/ovisiontv/kotlin/backup/BackupCodecTest.kt`:
  - round-trip всех компонентов;
  - encrypted round-trip;
  - неверный пароль;
  - повреждённый ciphertext;
  - home с папками и отсутствующими пакетами;
  - запрет plain-export при discovery;
  - отказ при более новой schema;
  - отказ при >3 профилях;
  - zip-slip;
  - свежие salt/nonce.

---

## 2. Проверка на момент фиксации

| Проверка | Результат |
|---|---|
| `:app:compileDebugKotlin` | **BUILD SUCCESSFUL**, 2026-11-15 07:27 RTZ |
| `:app:testDebugUnitTest` | **435/435**, failures 0, errors 0, skipped 0 |
| Instrumented tests после появления backup | **не запускались** |
| `lintDebug` после появления backup | **не запускался** |
| `assembleRelease` после появления backup | **не запускался** |
| AVD/ручная проверка BackupScreen | **не выполнялась** |
| SAF export/import на реальном файловом провайдере | **не проверялось** |
| Восстановление после process death | **не проверялось** |
| Rollback после сбоя restore | **не проверялся** |

Последнее известное значение instrumented-тестов **223/223** относится к состоянию **до** backup-кода и не подтверждает новый код.

---

## 3. Что важно знать о текущей реализации

- Локальные автоматические копии **не шифруются at rest**; они лежат в app-private `filesDir/backups`. Это осознанное решение v1.
- Шифрование применяется к экспортируемому `.ovbk`, если задан пароль. Для discovery и manual weather plain-export запрещён.
- Kids PIN/recovery **никогда не экспортируются**. Переносится только allowlist; PIN нужно задать заново. Если в backup `kids=true`, но на текущем устройстве PIN нет, профиль восстанавливается как non-kids с предупреждением.
- Профили объединяются по ID: backup wins, отсутствующие в backup текущие профили не удаляются; >3 профилей — abort.
- Restore заменяет выбранные ключи, а не мержит отдельные поля. Перед restore создаётся обязательная локальная копия с `pinned=true`.
- `p1` глобальные ключи и layout пишутся одним `commit()`, потому что делят `ovisiontv_launcher.xml`.
- Автоматизация сейчас на `AlarmManager`, не WorkManager. Триггеры `PRE_UPDATE` и `PRE_MIGRATION` есть в enum, но пока нигде не вызываются.
- `MigrationManager` ещё не реализован: `FORMAT_VERSION`/`DATA_SCHEMA` уже разделены, baseline `0→1` не написан.
- `StorageGate` создан, но все существующие писатели Home/Discovery ещё не переведены на него.
- В UI восстановление применяет все категории из preview; отдельный выбор категорий именно на restore пока не реализован.
- В `BackupScreen` кнопка BACK заблокирована во время PROGRESS, чтобы не скрыть результат restore.
- Пароль хранится в памяти как `String`; после экспорта/импорта очищается неявно только сменой состояния. Для TV-пульта это приемлемо, но не идеально.

---

## 4. Известные незакрытые места

1. **Нет instrumented-теста BackupManager**: round-trip create → mutate → restore → verify, rollback, journal recovery, pre-restore snapshot.
2. **Нет UI-теста BackupScreen**: D-pad-фокус, переходы страниц, пароль, SAF-запуск, поведение BACK.
3. **SAF не проверен на AVD/реальном TV**: некоторые TV-прошивки могут не иметь DocumentsUI.
4. **Нет проверки restore после настоящего uninstall** и после process death в середине `commit()`.
5. **Auto backup не проверен**: `AlarmManager` на TV/Doze, фактическое время срабатывания, поведение при перезагрузке.
6. **PBKDF2 210k на 1 GB TV** не измерялся.
7. **Нет SCA/CVE-проверки** для backup-кода (новых зависимостей не добавляли, но и аудита нет).
8. **`MissingApp`** реализован в Home/Folder/Search, но нужны отдельные тесты на меню и на сохранение позиции после реального uninstall.
9. **`StorageGate`** не подключён к `HomeOrderStore`, `DiscoveryStore`, `KidsStore` и другим писателям; конкурентная запись во время restore пока теоретически возможна.
10. **Нет UI удаления локальных копий** и отображения последнего auto-run, хотя retention уже работает.
11. **Нет `PRE_UPDATE`/`PRE_MIGRATION` вызовов** и нет MigrationManager.
12. **Нет финального `lintDebug`/`assembleRelease`** после backup-кода.

---

## 5. Следующие шаги (в порядке продолжения)

1. Добавить `BackupManagerInstrumentedTest`:
   - создать копию;
   - изменить prefs;
   - preview + restore;
   - проверить восстановленные значения;
   - проверить наличие pre-restore копии и удаление journal;
   - отдельный тест rollback при искусственной ошибке apply;
   - отдельный тест `recoverIfNeeded` с journal `applying`.
2. Прогнать `:app:connectedDebugAndroidTest` на AVD `hume-tv`; не считать старые 223/223 подтверждением нового кода.
3. Проверить BackupScreen вручную на AVD:
   - D-pad по всем страницам;
   - пароль (неверный, короткий, несовпадающий);
   - экспорт в Downloads;
   - импорт обратно;
   - restore и автоматический relaunch MainActivity;
   - BACK во всех состояниях.
4. Добавить UI-тесты/тесты навигации для `Settings → System → Backup & Restore`.
5. Подключить `StorageGate` к писателям Home/Discovery/Kids или явно задокументировать, почему restore не конкурирует с ними.
6. Решить: оставить `AlarmManager` или перейти на WorkManager; добавить `PRE_UPDATE`/`PRE_MIGRATION` вызовы.
7. Реализовать `MigrationManager` и baseline `0→1` (без изменения текущей schema).
8. Финальная проверка: `testDebugUnitTest`, `connectedDebugAndroidTest`, `lintDebug`, `assembleRelease`; обновить `00_AUDIT_AND_ARCHITECTURE.md` и этот файл фактическими результатами.

---

## 6. Команды для продолжения

```powershell
$env:GRADLE_USER_HOME = 'C:/gradle-home'

# compile
.\gradlew.bat :app:compileDebugKotlin -Pkotlin.incremental=false --console=plain

# unit
.\gradlew.bat :app:testDebugUnitTest -Pkotlin.incremental=false --console=plain

# AVD
$env:ANDROID_SDK_ROOT = 'C:\Android\Sdk'
$env:ANDROID_SERIAL = 'emulator-5554'
.\gradlew.bat :app:connectedDebugAndroidTest -Pkotlin.incremental=false --console=plain

# lint / release
.\gradlew.bat :app:lintDebug -Pkotlin.incremental=false --console=plain
.\gradlew.bat :app:assembleRelease -Pkotlin.incremental=false --console=plain
```

Результаты unit-тестов: `app/build/test-results/testDebugUnitTest/`.

---

## 7. Границы честности

Этот файл фиксирует только то, что реально было сделано и проверено. Он не утверждает, что backup/restore готов к релизу: UI, SAF, rollback, восстановление после process death, auto backup и физические TV ещё не проверены. Все незакрытые пункты выше остаются открытыми до соответствующих прогонов.

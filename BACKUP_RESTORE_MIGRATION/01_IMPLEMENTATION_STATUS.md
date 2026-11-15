# Backup / Restore — состояние реализации

Дата обновления: **2026-11-15 13:52 RTZ**  
Ветка: `master`  
**2026-11-15, 0.5.4:** добавлен `showWeather`; ранее в 0.5.3 — `AmbientMode`, `AmbientClockFormat`, `pixelShift`, `dimAfter`, `maxSession` (все — необязательные поля `SaverData`, schema 1 остаётся обратно совместимой); см. `AMBIENT_EXPERIENCE/01_IMPLEMENTATION_STATUS.md`.

**2026-11-15, 0.5.6:** режим `photos` входит в `SaverData.mode`, но сам источник фото (SAF tree / выбранные URI) **не** входит в backup: URI-гранты device-local. Восстановление на другом устройстве даёт `photos` без источника → Clock-fallback; это ожидаемое поведение, а не ошибка.
Статус: **Stage 0–8 реализованы; Stage 9 (устройства/физические TV) частично. Основной AVD-сценарий зелёный, release собирается.**

---

## 1. Что реализовано

### 1.1. Формат и модели

Каталог: `app/src/main/java/com/whatuneed/ovisiontv/kotlin/backup/`

- `BackupModels.kt` — контракт `.ovbk`:
  - kind `ovisiontv-backup`, `FORMAT_VERSION = 1`, `DATA_SCHEMA = 1`;
  - `manifest.json`, `payload.enc`, `components/`;
  - лимиты: 32 MiB архив, 64 MiB распакованный, 8 MiB запись, 256 записей, JSON 8 MiB, ratio 100:1;
  - `MAX_PROFILES = 3`, `LOCAL_RETENTION = 5`, пароль от 8 символов, PBKDF2 210 000;
  - категории profiles/home/discovery/kids/weather/saver/language/content/startup.
- `BackupJson.kt` — типизированный JSON; сырой XML SharedPreferences не экспортируется.
- `BackupEncryption.kt` — AES-256-GCM + PBKDF2-HMAC-SHA256, свежие salt/nonce.
- `BackupArchive.kt` — bounded ZIP, безопасные имена, SHA-256, отдельный внешний manifest для encrypted.
- `BackupValidator.kt` — manifest/schema, целостность, plain-export policy, проверка «выбранная категория имеет компонент».
- `BackupSource.kt` — чтение stores в `BackupData`.
- `BackupRepository.kt` — `filesDir/backups`, `index.json`, retention, pinning, атомарная запись.
- `BackupRestoreEngine.kt` — применение; один commit на prefs-файл; профили последними; weather cache очищается + generation; `SetupProgress.markDone`.
- `BackupManager.kt` — `list`, `delete`, `createLocal`, `export`, `previewLocal`, `previewUri`, `restore`, `rollbackTo`, `recoverIfNeeded`; mutex + IO; pre-restore snapshot + journal + rollback.
- `RestoreJournal.kt` — атомарный журнал prepared/applying/applied/rolled_back/failed.
- `BackupScheduler.kt` / `BackupAlarmReceiver.kt` — ежедневный inexact `AlarmManager`, receiver, auto copy.
- `BackupRecovery.kt` — recovery при старте процесса.
- `BackupMigration.kt` — live-schema gate:
  - `0 → 1` baseline;
  - pre-migration pinned copy;
  - pre-update pinned copy при смене `versionCode`;
  - `BackupMaintenance` запускает gate в IO при старте.
- `StorageGate.kt` — process-wide RW-lock для restore/snapshot; пока не подключён ко всем писателям.

### 1.2. UI

- `ui/BackupScreen.kt`:
  - HOME / CREATE / SOURCE / PREVIEW / PROGRESS / SETTINGS / PASSWORD;
  - категории, локальная копия, SAF export/import, preview, restore, пароль;
  - auto on/off, retention/status;
  - D-pad-фокус: информационные строки тоже focusable, initial focus ставится после layout;
  - BACK заблокирован во время PROGRESS;
  - пароль очищается после успеха/отмены/ошибки;
  - поле пароля получает фокус после первого layout, а не конкурирует со строкой Continue.
- `BackupActivity.kt` — отдельная `exported=false` activity.
- `SettingsScreen.kt` — `Settings → System → Backup & Restore`.
- `AndroidManifest.xml` — activity и receiver (`exported=false`).
- EN/RU строки `backup_*`.

### 1.3. Missing apps

- `TvApp.available`.
- `HomeOrderStore.missingApp` сохраняет позицию отсутствующего пакета.
- `AppSearch` / `KidsMode.KidsFilter` / `FolderEdit` исключают unavailable.
- `HomeScreen` / `FolderOverlay`: Open/App info/Uninstall скрыты, Move/Move to folder доступны; запуск блокируется.
- `AppCard` показывает unavailable.
- `HomeOrderStoreTest` проверяет позицию и флаг.

---

## 2. Проверено

| Проверка | Результат |
|---|---|
| `:app:compileDebugKotlin` | **BUILD SUCCESSFUL** |
| `:app:testDebugUnitTest` | **436/436**, 0 failures, 0 errors, 0 skipped |
| `:app:connectedDebugAndroidTest` | **234/234**, AVD `hume-tv` API 34 |
| `:app:lintDebug` | **0 errors, 18 warnings, 5 info** |
| `:app:assembleRelease` | **BUILD SUCCESSFUL**, APK **4 638 994 B** |
| Release signature | SHA-256 `327a50453093b783e84b1f2645b03319477d89966ea6a653dab53d5a567d7b10` (не изменилась) |
| Manual AVD flow | Settings → System → Backup & Restore; create local; preview; restore; auto-relaunch MainActivity |
| Release smoke on AVD | Release APK установлен, `Settings → System → Backup & Restore` открылся, локальная копия создана (R8/manifest/receiver не сломали экран) |

Новые instrumented-тесты:
- `BackupManagerInstrumentedTest` — 5 сценариев:
  - create → mutate → preview → restore → verify всех категорий;
  - pinned pre-restore copy + journal очищен;
  - kids=true без PIN → non-kids;
  - alarm receiver создаёт AUTO copy;
  - encrypted export через `ContentResolver` + wrong password;
  - interrupted restore recovery из journal.
- `MigrationManagerInstrumentedTest` — fresh install baseline + pre-update copy.
- `BackupScreenTest` — D-pad focus, нижние действия, password page + short-password validation.
- `BackupCodecTest` — плюс отказ при выбранной категории без компонента.
- `HomeOrderStoreTest` — missing package сохраняет позицию и `available=false`.

---

## 3. Что осталось непроверенным

1. **SAF picker на реальном TV**: `CreateDocument`/`OpenDocument` не выбирались вручную; `ContentResolver` export проверен тестом. Некоторые прошивки могут не иметь DocumentsUI.
2. **Полный UI-export/import**: пароль вводился только для проверки short-password; полный экспорт через файловый picker и импорт обратно не прогонялись.
3. **Process death** во время `commit()`: journal recovery проверен симуляцией, не реальным убийством процесса.
4. **Auto backup по расписанию**: receiver проверен прямым broadcast; реальное срабатывание `AlarmManager`, Doze, reboot — нет.
5. **PBKDF2 210k на 1 GB TV** не измерялся.
6. **Physical TV / matrix**: ARM32/ARM64, API 28–33/35, 1 GB RAM, 4K, реальные пульты, TalkBack — не проверялись.
7. **StorageGate** не подключён к `HomeOrderStore`, `DiscoveryStore`, `KidsStore`; restore не является ACID между файлами.
8. **MigrationManager** реализует только baseline `0→1`; реестра будущих схем `1→2` пока нет.
9. **Нет UI удаления локальных копий** (retention работает, но ручного delete/confirm нет).
10. **Нет SCA/CVE-сканирования** backup-кода.
11. `BackupRecovery` при старте всё ещё делает `runBlocking` на main; это осознанный компромисс, чтобы Home не прочитал половину restore, но потенциально влияет на ANR при повреждённом journal.
12. Локальные копии plain at rest; шифрование только для exported `.ovbk`.

---

## 4. Следующие шаги

1. Проверить SAF export/import на AVD с реальным DocumentsUI и на физических TV.
2. Прогнать process-death во время restore (adb kill/force-stop между коммитами) и убедиться, что `BackupRecovery` возвращает pre-restore.
3. Проверить auto backup после reboot/Doze и решить, нужен ли WorkManager.
4. Подключить `StorageGate` к писателям Home/Discovery/Kids или явно ограничить restore моментом, когда Home не пишет.
5. Добавить UI удаления локальных копий и отображение последнего auto-run.
6. Добавить реестр будущих `dataSchema` миграций и тест `1→2`.
7. Физическая матрица: API 28–33/35, ARM, 1 GB, 4K, TalkBack, реальный пульт.
8. SCA/CVE-проверка зависимостей.

---

## 5. Границы честности

Всё выше — результаты AVD `hume-tv` (API 34, x86) и JVM-тестов. Это не сертификация физических Android TV/Google TV. SAF picker, process death, auto alarm, Doze, 1 GB RAM, 4K и реальные пульты остаются непроверенными; пункты раздела 3 нельзя считать закрытыми.

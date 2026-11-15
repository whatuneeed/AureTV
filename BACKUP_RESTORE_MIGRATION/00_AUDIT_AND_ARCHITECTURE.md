# Backup, Restore & Migration — аудит и архитектура

**Проект:** OVisionTV Kotlin Launcher (`com.whatuneed.ovisiontv.kotlin`)  
**Версия на момент аудита:** `versionCode = 73`, `versionName = "0.5.0"`, `minSdk = 28`, `targetSdk = 35`  
**Дата:** 2026-11-15  
**Статус:** **Stage 1–8 реализованы; Stage 9 (устройства/физические TV) частично. AVD-прогон зелёный, release собирается.**  
**Текущая граница:** формат, локальные копии, encryption, restore/journal/rollback, SAF-UI, missing-app model, auto receiver и baseline migration gate уже в коде; SAF picker на реальных TV, process death, Doze/reboot, физическая матрица и future-schema registry ещё не проверены.  
**Источник актуального состояния:** `BACKUP_RESTORE_MIGRATION/01_IMPLEMENTATION_STATUS.md`.

---

## Краткий вывод

1. **Сейчас у лаунчера нет пользовательского backup/restore.** Android-бэкап и device-transfer **намеренно полностью отключены**: `android:allowBackup="false"`, а `backup_rules.xml` и `data_extraction_rules.xml` исключают `sharedpref`, `database` и `file` для cloud-backup и device-transfer. Это не ошибка: это осознанная защита приватности, но она означает, что **после настоящего uninstall все настройки, профили, раскладка, избранное и персонализация исчезают безвозвратно**. `adb install -r` данные сохраняет, обычное удаление приложения — нет.
2. **Базы данных нет.** В проекте не обнаружены Room, SQLite, `SQLiteOpenHelper`, `DataStore` или `openOrCreateDatabase`. Всё пользовательское состояние — это **SharedPreferences (XML) + один внутренний JSON-файл каталога заставки**. Поэтому «миграция БД» здесь — это миграция **наборов ключей SharedPreferences и JSON-форматов**, а не SQL-схем.
3. **Централизованной схемы данных и migration manager нет.** Версионирование распределено: `SetupProgress.STEP_VERSION` (только шаги мастер-настройки), `"version": 1` в каталоге Aerial (не пользовательские данные), защитные чтения `SafePreferences`, ремонт/карантин профилей и порядка Home, терпимый разбор Discovery. Это работает как «самовосстановление», но **не даёт предсказуемой миграции, backup перед обновлением и rollback**.
4. **Есть один критичный архитектурный конфликт:** файл `ovisiontv_launcher.xml` одновременно хранит **глобальные** настройки (язык, заставка, контент, показ телефонных приложений) и **раскладку профиля `p1`**. Профили `p2`/`p3` имеют отдельные файлы `ovisiontv_launcher_p2.xml` / `p3.xml`. Backup/restore обязан это учитывать, иначе настройки одного профиля могут «переехать» в глобальные.
5. **Есть потеря данных при переносе на другое устройство:** `buildHomeOrderRepair()` **выбрасывает** пакеты, которых нет в текущем `PackageManager` (`byPackage[packageName] ?: continue`). Сейчас нет типа `HomeItem` для «недоступного приложения», поэтому требование «сохранить запись, но пометить недоступной» **не может быть выполнено без изменения модели Home**.
6. **Kids PIN нельзя переносить в открытом виде.** PIN хранится как PBKDF2-хеш (120 000 итераций), но 4-значный PIN — это 10 000 комбинаций; офлайн-перебор хеша из экспортированного архива тривиален. Рекомендация: **никогда не экспортировать `pin_hash_*`, `pin_salt_*`, `rec_hash_*`, `rec_salt_*`, `rec_hint_*`**; переносить allowlist, а PIN задавать заново. Иначе профиль `kids=true` без PIN после restore может оказаться без реальной защиты: `kidsLeaveNeedsPin()` требует `hasPin == true`.
7. **Предлагаемое решение — не включать Android auto-backup, а построить собственный слой**: версионированный ZIP-контейнер `.ovbk`, типизированные компоненты, SHA-256, строгая валидация, staging, pre-restore snapshot, журнал восстановления, опциональное AES-GCM-шифрование, SAF export/import, WorkManager для локальных автоматических копий и D-pad-UI в `Settings → System → Backup & Restore`.
8. **Автоматический backup не может гарантировать «перед самым обновлением» или «перед удалением»**: приложение в эти моменты не работает. Честная схема: backup на первом запуске после смены `versionCode` **до миграции**, pre-migration backup, периодический локальный backup и явный экспорт перед uninstall. Локальные копии в `filesDir` **исчезают вместе с приложением**, поэтому пережить переустановку может только экспортированный пользователем файл.

---

# A. Аудит текущего состояния

## A.1. Что проверено

Только чтение исходников; изменения не вносились.

- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/xml/backup_rules.xml`
- `app/src/main/res/xml/data_extraction_rules.xml`
- `app/src/main/java/com/whatuneed/ovisiontv/kotlin/data/*`
- `app/src/main/java/com/whatuneed/ovisiontv/kotlin/discovery/*`
- `app/src/main/java/com/whatuneed/ovisiontv/kotlin/ui/SettingsScreen.kt`
- `app/src/main/java/com/whatuneed/ovisiontv/kotlin/ui/HomeScreen.kt`
- `app/src/main/java/com/whatuneed/ovisiontv/kotlin/ui/LauncherRoot.kt`
- `app/src/main/java/com/whatuneed/ovisiontv/kotlin/ui/components/*`
- `app/build.gradle.kts`, `build.gradle.kts`

Поиск по `Room`, `SQLiteOpenHelper`, `SQLiteDatabase`, `DataStore`, `openOrCreateDatabase`, `WorkManager`, `JobScheduler`, `ACTION_CREATE_DOCUMENT`, `ACTION_OPEN_DOCUMENT`, `FileProvider`, `DocumentFile` не дал пользовательских хранилищ/механизмов backup. Это важно для дизайна: **никакого скрытого второго хранилища нет**, и создавать параллельную БД не нужно.

## A.2. Карта хранилищ

Все пути ниже — относительно sandbox приложения (`/data/user/0/com.whatuneed.ovisiontv.kotlin/...`). Все SharedPreferences открываются в `MODE_PRIVATE`.

| № | Данные | Где лежат | Ключи / формат | Кто пишет | Пользовательские? | Комментарий |
|---|---|---|---|---|---|---|
| 1 | Глобальные настройки лаунчера и раскладка `p1` | `shared_prefs/ovisiontv_launcher.xml` | `focused_package`, `shelf_package`, `home_order`, `show_phone_apps` | `HomeScreen`, `HomeOrderStore`, `AppRepository` | Да | `LauncherPrefs.NAME = "ovisiontv_launcher"` |
| 2 | Язык интерфейса | тот же `ovisiontv_launcher.xml` | `ui_language` | `LocalePreference` | Да | Глобально, не на профиль |
| 3 | Настройки заставки | тот же файл | `saver_delay_minutes`, `saver_music`, `saver_refresh`, `saver_themes`, `saver_hidden_clips` | `SaverPreferences` | Да | `saver_refresh_attempt_ms` — служебное время, не переносить |
| 4 | Контент-переключатели `p1` | тот же файл | `content_recommendations`, `content_continue_watching` | `ContentPreferences`, `DiscoverySettings` | Да | Дублируются в `DiscoveryKeys` для совместимости; для `p1` это те же ключи |
| 5 | Показ телефонных приложений | тот же файл | `show_phone_apps` | `AppRepository` | Да | Глобально |
| 6 | Профили | `shared_prefs/profiles.xml` | `list` (JSON-массив), `current`, `next_id`, `ask_on_boot`, `ask_on_wake`, `list_quarantine` | `ProfileStore` | Да | `ProfileCatalog.MAX = 3`, `DEFAULT_ID = "p1"`; JSON ограничен 256 KiB / 32 записи |
| 7 | Раскладка, фокус, полка профиля | `ovisiontv_launcher.xml` для `p1`; `ovisiontv_launcher_p2.xml` / `_p3.xml` для остальных | `home_order`, `focused_package`, `shelf_package`, `order_quarantine` | `HomeOrderStore` | Да | `ProfileStore.homePrefsName(id)` |
| 8 | Discovery-настройки и сигналы | тот же per-profile prefs-файл | `content_recommendations`, `content_continue_watching`, `discovery_personalization`, `discovery_usage`, `discovery_favorites`, `discovery_hidden`, `discovery_shown` | `DiscoverySettings`, `DiscoveryStore` | Да | `DiscoveryStore` ограничивает JSON 512 KiB, элементы 1 000, usage 512 ключей |
| 9 | Kids allowlist | `shared_prefs/kids_mode.xml` | `allow_<profileId>` | `KidsStore` | Да | Пакеты, которые разрешены в детском профиле |
| 10 | Kids PIN / recovery | `shared_prefs/kids_mode.xml` | `pin_salt_*`, `pin_hash_*`, `rec_salt_*`, `rec_hash_*`, `rec_hint_*`, `fail_*`, `lock_until_*` | `KidsStore` | Да, **секрет** | PBKDF2-HMAC-SHA256, 120 000 итераций, 4-значный PIN. Не экспортировать |
| 11 | Прогресс первичной настройки | `shared_prefs/setup.xml` | `setup_done`, `setup_step`, `setup_step_ver` | `SetupProgress` | Служебное | `STEP_VERSION = 5`; не переносить как пользовательские данные |
| 12 | Запуск при boot/wake | `shared_prefs/startup.xml` | `target`, `label`, `on_wake`, `home_override` | `StartupPreferences` | Частично | `home_override` — попытка системного разрешения, не переносить; `target/label/on_wake` — можно, но проверять наличие пакета |
| 13 | Таймер сна | `shared_prefs/sleep_controls.xml` (имя `PREFS`) | абсолютное время `sleep_at` | `SleepControls` | Транзиентное | Не переносить: время истекло/не имеет смысла на другом устройстве |
| 14 | Погода | `shared_prefs/weather.xml` | `enabled`, `manual`, `place`, `lat`, `lon`, `units`, `source`, `refresh_min`, `cache2`, `cache_at`, `cache_key`, `data_generation` | `WeatherPreferences`, `Weather` | Частично | Настройки — да; `cache2/cache_at/cache_key/data_generation` — нет, это кэш и поколение |
| 15 | Каталог Apple Aerials | `files/aerial_catalog.json` | JSON, `"version": 1` | `AerialRefresh` через `AtomicWrite` | Нет | Регенерируется из сети/ассетов; не переносить |
| 16 | Старый кэш клипов | `cache/aerial_media/` | файлы | `AerialClipCache` | Нет | Удаляется при запуске; не переносить |
| 17 | Постеры/превью | `cache/aerial_posters/`, `cache/aerial_previews/`, `cache/aerial_previews/thumbs/` | JPEG | `AerialPosterStore`, `SaverPreviewImage` | Нет | Кэш; не переносить |
| 18 | Crash trace | `cache/diagnostics/last-crash.txt` | текст, ≤ 32 KiB | `CrashReport` | Диагностика | Не переносить |
| 19 | Карантин повреждённых данных | `profiles.xml` → `list_quarantine`; per-profile → `order_quarantine` | сырой JSON | `ProfileStore`, `HomeOrderStore` | Диагностика | Может содержать пользовательские строки; не экспортировать |
| 20 | Список установленных приложений | Не хранится | — | `PackageManager` при каждом запуске | Нет | Восстанавливается на новом устройстве; раскладка ссылается на package names |
| 21 | Поиск/история поиска | Постоянного хранилища не найдено | — | `AppSearch` / `CitySearch` | Нет | `AppSearch` — in-memory; `CitySearch` — сетевой поиск города |
| 22 | Арт-кэш приложений | Только память | — | `AppArtCache` | Нет | Ключи — package/version/kind; на диск не пишется |
| 23 | ArtFailureCache | Только память | — | `ArtFailureCache` | Нет | TTL 60 с, не переносить |

### A.2.1. Подтверждённые ссылки

- `LauncherPrefs` — `app/src/main/java/com/whatuneed/ovisiontv/kotlin/data/LauncherPrefs.kt:3-9`.
- Профили и per-profile prefs — `data/Profiles.kt:393-407`.
- Kids keys — `data/KidsMode.kt:243-253`; PBKDF2 — `data/KidsMode.kt:257-262`.
- Setup — `data/SetupProgress.kt:9-26`.
- Startup — `data/StartupPreferences.kt:29-37`.
- Weather keys — `data/WeatherPreferences.kt:12-24`.
- Discovery keys — `discovery/DiscoveryStore.kt:26-37`; лимиты — `:9-24`.
- Home order — `data/HomeOrderStore.kt:80-94`; лимиты — `:102-107`.
- Aerial file — `data/AerialRefresh.kt:46,60`; атомарная запись — `data/AtomicWrite.kt`.
- Crash trace — `data/CrashReport.kt:38-48`.
- Caches — `data/AerialClipCache.kt:12-16`, `ui/components/AerialPosterStore.kt:21,90-97`, `ui/components/SaverPreviewImage.kt:23-24,70-78`.
- `SafePreferences` — `data/SafePreferences.kt:1-40`.

## A.3. Что сейчас делает Android backup

### A.3.1. Манифест

`app/src/main/AndroidManifest.xml:83-86`:

```xml
android:allowBackup="false"
android:dataExtractionRules="@xml/data_extraction_rules"
android:fullBackupContent="@xml/backup_rules"
```

### A.3.2. Правила

`app/src/main/res/xml/backup_rules.xml`:

```xml
<exclude domain="sharedpref" path="." />
<exclude domain="database" path="." />
<exclude domain="file" path="." />
```

`app/src/main/res/xml/data_extraction_rules.xml`:

```xml
<cloud-backup>
    <exclude domain="sharedpref" path="." />
    <exclude domain="database" path="." />
    <exclude domain="file" path="." />
</cloud-backup>
<device-transfer>
    <exclude domain="sharedpref" path="." />
    <exclude domain="database" path="." />
    <exclude domain="file" path="." />
</device-transfer>
```

### A.3.3. Следствия

| Сценарий | Что происходит сейчас |
|---|---|
| Обновление APK / `adb install -r` | Данные сохраняются; код читает старые ключи через `SafePreferences` / ремонт. Централизованной миграции и pre-update копии нет |
| Обычное удаление (uninstall) | **Все данные приложения удаляются**, включая `shared_prefs`, `files`, `cache`. Локальные backup-файлы, если их хранить в `filesDir`, тоже исчезнут |
| Переустановка после uninstall | Чистое состояние, мастер настройки заново; Android backup не помогает |
| Новое устройство / device transfer | **Ничего не переносится**: `device-transfer` исключает все домены, `allowBackup=false` |
| Google cloud backup | Отключён |
| `adb backup` | Для `allowBackup=false` не даст пользовательские данные |
| Смена профиля | Данные профилей остаются в per-profile prefs; `ProfileStore.delete()` чистит layout/discovery/kids для удалённого id (`Profiles.kt:340-374`) |
| Сброс раскладки | Удаляются `home_order`, `focused_package`, `shelf_package` (`HomeOrderStore.kt:72-78`) |

**Вывод:** текущее поведение — «данные живут только внутри одного установленного экземпляра». Это соответствует privacy-by-default, но не требованию «не терять настройки при переустановке и смене устройства». Новый слой должен быть **launcher-owned** и не зависеть от Android auto-backup; включать `allowBackup` обратно не нужно и не рекомендуется без отдельного решения.

## A.4. Версионирование и миграции: что есть сейчас

| Механизм | Где | Что делает | Чего не делает |
|---|---|---|---|
| `SetupProgress.STEP_VERSION` | `SetupProgress.kt` | Сбрасывает номер шага мастера при изменении карты страниц | Не версионирует пользовательские данные; не мигрирует layout/профили |
| `SafePreferences` | `SafePreferences.kt` | При неверном типе возвращает default, логирует ключ, **не удаляет** значение | Не знает схем; не исправляет данные; не создаёт backup |
| `parseProfiles()` | `Profiles.kt:166-243` | Ремонтирует id/emoji/color/name, ограничивает 256 KiB и 32 записи | Не сохраняет неизвестные поля; repair может изменить данные; нет версии |
| `buildHomeOrderRepair()` | `HomeOrderStore.kt:154-222` | Читает строки и папки, чинит id, отбрасывает неизвестные пакеты, добавляет новые | **Теряет отсутствующие приложения**; не различает «удалено» и «ещё не установлено»; нет версии |
| `decodeItems()` / `decodeUsage()` | `DiscoveryStore.kt:155-242` | Терпимый разбор, лимиты, отбрасывание плохих записей | Не сохраняет неизвестные поля; нет версии; id содержит `intentUri` |
| `Weather.Data.fromJson()` | `data/Weather.kt:126-165` | Читает кэш; исключения при отсутствии обязательных полей | Не миграция; кэш можно просто потерять |
| Aerial `"version": 1` | `data/AerialRefresh.kt` | Поле в JSON каталога | Не пользовательская схема; парсер не выполняет цепочку миграций |
| `AtomicWrite` | `data/AtomicWrite.kt` | Атомарная замена одного файла | Не транзакция across prefs-файлов; не journal |
| `Profiles.delete()` | `Profiles.kt:340-374` | Сначала профиль, потом очистка layout/PIN; есть повторная очистка | Не journal; при process death возможны orphans, хотя код пытается это исправить |

**Главный вывод:** существующие механизмы — это **defensive parsing / repair**, а не migration framework. Для backup/restore нужен отдельный слой версий, который не ломает текущие защиты и не переписывает stores без необходимости.

## A.5. Чувствительные данные

| Данные | Риск | Решение для backup |
|---|---|---|
| Kids PIN salt/hash, recovery salt/hash/hint | Офлайн-перебор 4-значного PIN; hint может содержать личные данные | **Никогда не экспортировать**, даже в зашифрованном архиве по умолчанию. PIN задаётся заново |
| Kids allowlist | Раскрывает набор детских приложений | Переносить; в ручном экспорте — только с шифрованием или с предупреждением |
| `discovery_favorites` / `discovery_hidden` / `discovery_usage` / `discovery_shown` | История просмотра, названия, ссылки; `DiscoveryItem.id` содержит `intentUri`, а `intentUri` может содержать токены | Локально — можно; в ручной экспорт — только с шифрованием, иначе категория недоступна |
| `weather.place/lat/lon` | Местоположение | Переносить только с шифрованием или с явным предупреждением; кэш погоды не переносить |
| `profiles.list` (имена, emoji) | Личные имена | Локально — можно; ручной экспорт — предупреждение/шифрование |
| `list_quarantine`, `order_quarantine` | Сырые пользовательские строки | Не экспортировать |
| `cache/diagnostics/last-crash.txt` | Может содержать package names/пути | Не экспортировать |
| `startup.target` | Название приложения | Переносить; проверять установку |
| `show_phone_apps`, `ui_language`, saver | Не секрет | Переносить |

## A.6. Дефекты и риски текущего состояния

| ID | Серьёзность | Находка | Последствие |
|---|---|---|---|
| BKP-01 | Critical | Нет пользовательского backup/restore; Android backup/device-transfer отключены | Потеря всех данных при uninstall/смене устройства; нет пути восстановления |
| BKP-02 | High | Нет централизованной data schema и migration manager | Обновления могут менять/терять unknown fields; нет pre-migration копии и rollback |
| BKP-03 | High | Нет SAF export/import | Нельзя вынести данные на USB/в файл и вернуть на другом TV |
| BKP-04 | High | Нет шифрования экспортируемых данных; Kids PIN-хеши лежат в prefs | Утечка личных данных/истории при передаче файла; риск перебора PIN |
| BKP-05 | High | `ovisiontv_launcher.xml` смешивает глобальные настройки и layout `p1` | Неверный restore может записать глобальные ключи в `p2`/`p3` или наоборот |
| BKP-06 | High | `buildHomeOrderRepair()` отбрасывает отсутствующие пакеты; нет `MissingApp`/placeholder | Нельзя сохранить позицию приложения, которого нет на новом TV |
| BKP-07 | Medium | Нет общего storage lock; prefs-файлы независимы | Backup/restore может видеть несогласованный снимок across files; restore не атомарен across files |
| BKP-08 | Medium | Нет retention/versioned backup index | Нет управления копиями, размерами, pre-restore копиями |
| BKP-09 | Medium | Ремонт профилей/порядка может отбрасывать неизвестные поля | Future schema compatibility ограничена |
| BKP-10 | Medium | В prefs/cache есть диагностика и сырой карантин | Их нельзя включать в backup; иначе утечка/раздувание |
| BKP-11 | Medium | Нет ограничений/валидации архивов | Zip-slip, zip-bomb, огромный JSON, malicious package/intent URI при импорте |
| BKP-12 | Low | Нет автоматического расписания | Пользователь забывает делать копии |
| BKP-13 | Low | Нет UI/статуса последней копии | Непонятно, защищены ли данные |
| BKP-14 | Low | SAF/file picker на Android TV может отсутствовать | Нужен явный fallback и сообщение, а не падение |
| BKP-15 | Low | Privacy policy не описывает backup/export (нужно обновить после реализации) | Неполное раскрытие обработки данных |

## A.7. Ограничения текущей архитектуры, которые нельзя обойти «маленькой правкой»

1. **Нет БД.** Нельзя использовать SQL-транзакции; «атомарность» restore — только per-file `SharedPreferences.commit()` + journal + rollback.
2. **Нет общей модели Home для отсутствующих приложений.** Для требования «сохранить позицию, но пометить недоступным» нужно расширить `HomeItem`/`TvApp` и UI, а не только backup-формат.
3. **`p1` — особый профиль.** Глобальные настройки и layout `p1` физически в одном файле; restore обязан писать их одним `Editor`/`commit()` для этого файла.
4. **Store-классы читают prefs напрямую.** После restore текущие `SharedPreferences`-объекты в памяти и Compose-состояния могут быть stale; нужен контролируемый restart activity/process, а не «горячее» применение.
5. **Нет фонового планировщика.** WorkManager/JobScheduler отсутствуют; для автоматизации нужно добавить зависимость или писать свой JobScheduler-слой (что хуже).
6. **Нет `FileProvider`.** Для SAF export/import он не нужен; для share-интента — возможно, но можно обойтись `CreateDocument`.
7. **Приложение — HOME/launcher.** Фоновые задачи и WorkManager на TV могут откладываться системой/вендором; нельзя обещать точное время.

---

# B. Архитектура Backup, Restore & Migration

## B.1. Принципы

1. **Один владелец данных — существующие store-классы.** Backup не создаёт вторую БД и не дублирует модели; он читает те же `ProfileStore`, `HomeOrderStore`, `DiscoveryStore`, `KidsStore`, `WeatherPreferences`, `SaverPreferences`, `LocalePreference`, `StartupPreferences`.
2. **Формат — typed JSON-компоненты в ZIP.** Не дампить XML SharedPreferences: XML нестабилен, содержит служебные/чувствительные ключи и не даёт семантической валидации.
3. **Никаких destructive изменений до полной валидации.** Сначала staging + manifest + SHA-256 + schema validation + pre-restore snapshot; только потом запись.
4. **Каждый restore имеет точку отката.** Pre-restore backup + journal; при сбое — best-effort rollback. Честно документировать, что cross-file restore не является истинной ACID-транзакцией.
5. **Секреты не экспортируются.** Kids PIN/recovery — никогда; Discovery/координаты — только в зашифрованном экспорте; диагностика/карантин — никогда.
6. **Формат версионирован и forward-only.** Backup schema; app с более старой схемой отказывается читать новый backup; старые backup мигрируются вверх.
7. **UI — D-pad-first, без фейкового прогресса.** Реальные стадии, indeterminate progress, отмена только до apply, подтверждение с Cancel по умолчанию.
8. **Никаких новых permissions для backup.** SAF не требует storage permission; `allowBackup` не включаем; cloud/network не используем.

## B.2. Компоненты

```
┌─────────────────────────────────────────────────────────────────────┐
│ SettingsScreen / SettingsActivity (D-pad UI)                        │
│  Settings → System → Backup & Restore                               │
└───────────────┬─────────────────────────────────────────────────────┘
                │ intents/state, no storage work on main
┌───────────────▼─────────────────────────────────────────────────────┐
│ BackupUiController / BackupViewModel                                │
│  state machine: Idle → Preparing → Exporting → … → Completed/Failed │
└───────────────┬─────────────────────────────────────────────────────┘
                │
┌───────────────▼─────────────────────────────────────────────────────┐
│ BackupManager (orchestrator, Dispatchers.IO)                        │
│  create / validate / restore / migrate / retention                  │
└───┬───────────────┬───────────────────┬───────────────────┬─────────┘
    │               │                   │                   │
┌───▼──────────┐ ┌──▼──────────────┐ ┌──▼──────────────┐ ┌──▼──────────────┐
│ BackupSource │ │ BackupCodec     │ │ BackupValidator │ │ RestoreEngine   │
│ (read stores)│ │ (zip/json)      │ │ (schema/caps)   │ │ (stage/apply)   │
└───┬──────────┘ └──┬──────────────┘ └──┬──────────────┘ └──┬──────────────┘
    │               │                   │                   │
┌───▼───────────────▼───────────────────▼───────────────────▼─────────┐
│ Existing stores: Profiles, HomeOrder, Discovery, Kids, Weather,     │
│ Saver, Locale, Startup + SafePreferences/AtomicWrite                │
└─────────────────────────────────────────────────────────────────────┘

Supporting:
  BackupRepository   filesDir/backups + index (atomic)
  EncryptionManager  AES-256-GCM + PBKDF2-HMAC-SHA256 (export only)
  MigrationManager   BackupFormat + DataSchema migrations
  RestoreJournal     filesDir/backups/journal.json (AtomicWrite)
  StorageGate        cooperative read/write lock for snapshot consistency
  BackupScheduler    WorkManager (if approved) or no auto
  SafGateway         ACTION_CREATE_DOCUMENT / ACTION_OPEN_DOCUMENT
```

## B.3. Предлагаемая структура пакетов

```text
backup/
  BackupContract.kt          # ids, versions, caps, constants
  BackupModels.kt            # manifest, component envelope, selection
  BackupSource.kt            # typed reads from existing stores
  BackupCodec.kt             # JSON <-> models, archive read/write
  BackupValidator.kt         # size/path/schema/cross-component checks
  BackupRepository.kt        # local files, index, retention
  BackupManager.kt           # orchestrator + state machine
  EncryptionManager.kt       # password envelope, no custom crypto
  RestoreEngine.kt           # staging, apply per prefs file, verify
  RestoreJournal.kt          # crash recovery
  MigrationManager.kt        # backup format + data schema migrations
  StorageGate.kt             # read/write coordination
  BackupScheduler.kt         # WorkManager integration
  SafGateway.kt              # SAF import/export

ui/settings/backup/          # composables only; no storage logic
  BackupHomePage.kt
  BackupCreatePage.kt
  BackupRestorePage.kt
  BackupProgressPage.kt
  BackupSettingsPage.kt
```

Не создавать `BackupDatabase`/Room: это противоречит принципу «не дублировать хранилище» и усложняет миграцию.

## B.4. Поток export

1. Пользователь открывает `Settings → System → Backup & Restore` → «Создать копию».
2. UI собирает `BackupSelection` (категории, шифрование, назначение).
3. `BackupManager`:
   - проверяет свободное место (оценка + запас);
   - `StorageGate.withRead { ... }` — не даёт писать stores во время снимка;
   - читает компоненты через `BackupSource` (только whitelisted keys);
   - строит `manifest` + payload;
   - пишет во временный файл `filesDir/backups/tmp/<uuid>.ovbk.part`;
   - считает SHA-256 компонентов/архива;
   - проверяет собственным `BackupValidator` (self-check);
   - атомарно переименовывает в `filesDir/backups/<id>.ovbk`;
   - обновляет `index.json` атомарно;
   - применяет retention (не удаляя pinned/pre-restore/pre-migration);
   - при export в SAF копирует готовый валидный файл в `contentResolver.openOutputStream(uri)`.
4. UI показывает реальную стадию; по завершении — дату/размер/имя файла, не фейковый процент.

## B.5. Поток restore

1. Источник: локальная копия или SAF-файл.
2. `BackupManager` копирует источник в `cacheDir/restore/<uuid>/source.ovbk` (stream, с лимитом), чтобы не читать URI повторно.
3. `BackupValidator`:
   - формат/манифест/версии;
   - zip safety/caps;
   - SHA-256;
   - schema компонентов;
   - cross-component constraints;
   - compatibility.
4. UI показывает **preview**: дата, app version, schema, профили, количество элементов, какие категории будут заменены, предупреждения (missing apps, Kids PIN reset, permissions not restored, encrypted).
5. Пользователь выбирает категории и подтверждает. `Cancel` — фокус по умолчанию.
6. `RestoreEngine`:
   - создаёт **pre-restore backup** всех затрагиваемых данных (локально, pinned);
   - пишет journal `prepared`;
   - применяет по одному prefs-файлу, одним `Editor` на файл, `commit()`, проверяет `true`;
   - после каждого файла — verify read-back;
   - journal `applied`;
   - post-restore bookkeeping (setup done, clear weather cache if needed, bump schema);
   - возвращает `RestartRequired`; UI перезапускает `MainActivity`.
7. При любой ошибке — `rollback` из pre-restore; journal `rolled_back`; сообщение; исходный backup не изменяется.

## B.6. Состояния UI

```kotlin
sealed interface BackupState {
    data object Idle
    data class Preparing(val reason: String)
    data class Exporting(val destination: Destination)
    data class Validating
    data class Restoring
    data class Migrating(val from: Int, val to: Int)
    data class Verifying
    data class Completed(val backupId: String, val bytes: Long)
    data class Failed(val stage: Stage, val message: String, val canRetry: Boolean)
    data class RestartRequired
}
```

Правила:

- `Preparing/Exporting/Validating/Restoring/Migrating/Verifying` — **indeterminate** progress, без выдуманных процентов.
- Если известны байты, можно показывать «обработано X из Y», но только из реально прочитанных байтов.
- `Cancel` доступен до начала apply; во время apply — нет (или только между файлами с rollback, но лучше не обещать).
- Ошибка содержит стадию и действие; не «что-то пошло не так».
- Долгая операция не блокирует main; Compose получает state через `StateFlow`.

## B.7. StorageGate (обязательное условие согласованности)

Проблема: `HomeOrderStore`, `DiscoveryStore`, `ProfileStore` и др. пишут независимо; backup, читающий файлы последовательно, может получить снимок, где профиль уже удалён, а layout ещё нет.

Решение:

- Один process-wide `StorageGate` на `ReentrantReadWriteLock`.
- Все **мутирующие** операции stores выполняются в `StorageGate.withWrite { ... }`.
- Backup snapshot — `StorageGate.withRead { ... }`.
- Restore — `StorageGate.withWrite { ... }` на всё время apply.
- UI-мутации, не обёрнутые gate, считаются дефектом; gate нужно добавить в writer-методы (не в каждый `prefs.edit()` вручную, а в фасады stores).
- `apply()` обновляет in-memory map синхронно; lock удерживается до возврата из writer, поэтому snapshot видит согласованное in-memory состояние. Disk flush не обязателен для чтения.
- Если полностью обернуть все writers в рамках первой итерации слишком дорого, **нельзя обещать cross-file consistency**; тогда backup запускается только из Settings при простое Home, а отчёт должен это явно указывать. Рекомендуется всё же сделать gate.

Ограничение: `SharedPreferences` не даёт публичного snapshot API; gate координирует только код приложения. Внешние изменения (adb/root) не учитываются.

## B.8. Почему не Room и не DataStore

- Нет текущей БД; вводить Room только ради backup — это второй источник истины, двойная миграция и риск рассинхронизации.
- DataStore тоже потребовал бы переписать все stores.
- Backup-слой должен быть **адаптером** над существующими stores. Если в будущем появится Room, backup-компоненты можно будет перевести на SQL-транзакции, не меняя формат.

---

# C. Автоматизация

## C.1. Что автоматизировать можно честно

| Триггер | Реализуемость | Предлагаемое поведение |
|---|---|---|
| Периодический локальный backup | Да, но с задержками | WorkManager periodic, daily (по умолчанию), constraints battery/storage not low |
| Backup на первом запуске после смены `versionCode` | Да | Один раз, **до** любых миграций; локальная копия `pre_update` |
| Backup перед миграцией схемы | Да | Всегда, pinned; если не удалось — не выполнять destructive migration |
| Backup перед restore | Да | Всегда, pinned; без него restore не начинается |
| Backup «перед обновлением из магазина» | Нет | Приложение не работает в момент установки APK; можно только after-update/pre-migration |
| Backup «перед uninstall» | Нет | Нельзя перехватить обычное удаление; нужен явный экспорт и текст в UI |
| Автоматический внешний backup | Ограниченно | Только с persisted SAF tree, opt-in; не гарантируется на всех TV |
| Облако | Не делать без отдельного approval | Нет сети/аккаунта/токенов |

## C.2. WorkManager или JobScheduler

**Рекомендация:** `androidx.work:work-runtime-ktx` (pinned version), потому что:

- переживает reboot без `RECEIVE_BOOT_COMPLETED`;
- даёт constraints, unique work, backoff, test helpers;
- не требует новых permissions;
- стандартный механизм для deferrable local work.

Минусы:

- новая зависимость → нужно обновить `gradle/verification-metadata.xml` и release checklist (P2-4);
- на TV/Doze задача может быть отложена; **не обещать точное время**;
- periodic minimum 15 минут; daily — нормально.

Если owner против новой зависимости:

- альтернатива — `JobScheduler` + `JobService` + `setPeriodic` (без WorkManager), но это больше кода, свои reboot/backoff/constraints, и всё равно нужен receiver;
- либо **без автоматизации**: только manual + pre-migration/pre-restore. Это хуже для требования «автоматическое создание».

## C.3. Политика планировщика

- Default: **daily**, retention 5 локальных копий, только локально.
- Constraints: `setRequiresBatteryNotLow(true)`, `setRequiresStorageNotLow(true)`. Не требовать charging (TV включён в сеть, но не гарантируется).
- Unique work: `"ovisiontv-backup-periodic"`, `ExistingPeriodicWorkPolicy.KEEP`.
- Pre-update/pre-migration/pre-restore: one-time `"ovisiontv-backup-<reason>"`, unique, `APPEND_OR_REPLACE`/`KEEP` по смыслу.
- Worker:
  - проверяет `BackupGate`/`StorageGate`; если занято — `Result.retry()` с backoff;
  - не пишет UI; ошибки в `index.json`/status;
  - не делает бесконечный retry: WorkManager backoff + максимум попыток на запуск (например, 3), затем `Result.failure()`;
  - не удаляет старые копии, пока новая не валидирована.
- Пропуск, если последняя копия моложе интервала и хэш данных не изменился (dedupe).
- Никаких уведомлений с `POST_NOTIFICATIONS`; статус — в Settings. На TV это уместнее и не добавляет permission.

## C.4. Pre-update / pre-migration

**Точный «до обновления» невозможен.** Реализуемая последовательность:

1. Пользователь/магазин устанавливает новую версию; данные старой версии ещё на диске.
2. Первый запуск нового кода: `DataBootstrap` сравнивает `BuildConfig.VERSION_CODE` с `backup_state.last_seen_version_code`.
3. Если версия изменилась — создаётся локальная копия `pre_update` (или `pre_migration`, если `dataSchema` тоже меняется) **до** чтения/миграции stores.
4. Только после успешной копии запускается `MigrationManager`.
5. Если копия не удалась:
   - для additive/no-op миграции — можно продолжить, но записать `backup_missing` и показать предупреждение;
   - для destructive миграции — остановить миграцию, показать ошибку, не удалять данные;
   - никогда не притворяться, что копия есть.

Нужен единый `DataBootstrap`/`DataOpenGate`, через который проходят `MainActivity`, `SettingsActivity`, `SetupActivity`; иначе один из входов может прочитать данные до backup/миграции. Это отдельная архитектурная работа, не «одна кнопка».

## C.5. Retention

- `index.json` — атомарный; хранит `id`, дата, размер, schema, trigger, pinned, sha256.
- Локальные копии: default max 5; диапазон 3–10.
- Pinned: `pre_update`, `pre_migration`, `pre_restore` — не удаляются автоматическим retention, пока не истёк их срок/пользователь не удалит.
- Удалять старейшую **только после** успешной валидации новой и обновления индекса.
- Если индекс повреждён — восстановить сканированием `.ovbk` заголовков; повреждённые — в `backups/quarantine`, не удалять молча.
- Общий лимит: 32 MiB на архив, 128 MiB на локальный каталог (настраиваемо/жёстко); при нехватке места — ошибка до записи, без удаления старых.

## C.6. Внешний автоматический backup

Не default. Если owner захочет:

- `ACTION_OPEN_DOCUMENT_TREE` + `takePersistableUriPermission(READ|WRITE)`.
- Хранить `treeUri` в `backup_state`; проверять доступ при каждом запуске.
- Писать `DocumentFile`/`DocumentsContract` (без новой зависимости, если возможно).
- При отзыве permission/недоступном provider — отключить auto-external, сохранить локальные копии, показать статус.
- Не обещать, что файл переживёт uninstall: URI permission может быть отозвана; сам файл останется, но пользователь должен снова выбрать его при импорте.
- На TV file picker может отсутствовать; `ActivityNotFoundException` → сообщение, не crash.

---

# D. Формат backup и манифест

## D.1. Контейнер

- Расширение: `.ovbk`.
- Физически: ZIP (deflate), UTF-8 JSON.
- Два режима:
  - **plain**: `manifest.json` + `components/...`;
  - **encrypted**: `manifest.json` (только метаданные/параметры) + `payload.enc` (AES-256-GCM над внутренним ZIP с компонентами).
- Никаких Java serialization, никаких XML prefs, никаких absolute paths.

### Plain

```text
ovisiontv-backup.ovbk
├── manifest.json
└── components/
    ├── profiles.json
    ├── profiles/p1/home.json
    ├── profiles/p1/discovery.json
    ├── profiles/p1/kids.json
    ├── profiles/p2/home.json
    ├── profiles/p2/discovery.json
    ├── global/language.json
    ├── global/content.json
    ├── global/saver.json
    ├── weather.json
    └── startup.json
```

### Encrypted

```text
ovisiontv-backup.ovbk
├── manifest.json          # format, encryption params, ciphertext hash/bytes
└── payload.enc            # AES-GCM(inner ZIP with manifest/components)
```

`manifest.json` в encrypted-режиме **не содержит** имён профилей, заголовков, package names, количества избранного. Только `backupId`, `createdAt`, `formatVersion`, `dataSchema`, `app` (version code/name), `encryption` и `payload` hash/bytes.

## D.2. Манифест: поля

```json
{
  "kind": "ovisiontv-backup",
  "formatVersion": 1,
  "dataSchema": 1,
  "backupId": "0f4c1e2a-...",
  "createdAt": "2026-11-15T07:02:00Z",
  "app": {
    "versionCode": 73,
    "versionName": "0.5.0"
  },
  "source": {
    "sdk": 34,
    "model": "Android TV",
    "locale": "ru-RU"
  },
  "selection": [
    "profiles",
    "home",
    "discovery",
    "weather",
    "saver",
    "language",
    "startup"
  ],
  "components": [
    {
      "id": "profiles",
      "schema": 1,
      "path": "components/profiles.json",
      "bytes": 1234,
      "sha256": "…",
      "count": 3
    }
  ],
  "encryption": null
}
```

Для encrypted:

```json
{
  "kind": "ovisiontv-backup",
  "formatVersion": 1,
  "dataSchema": 1,
  "backupId": "…",
  "createdAt": "…",
  "app": { "versionCode": 73, "versionName": "0.5.0" },
  "encryption": {
    "cipher": "AES-256-GCM",
    "kdf": "PBKDF2-HMAC-SHA256",
    "iterations": 210000,
    "salt": "base64…",
    "nonce": "base64…",
    "tagBits": 128
  },
  "payload": {
    "path": "payload.enc",
    "bytes": 12345,
    "sha256": "…"
  }
}
```

Правила:

- `formatVersion` — структура контейнера; `dataSchema` — семантика данных.
- `source` — **не** device serial, не Android ID, не IP; только модель/SDK/локаль (необязательно).
- `selection` — что реально сохранено.
- `components` — для plain; для encrypted — внутри зашифрованного payload.
- `sha256` — hex lowercase; хэш считается по точным байтам файла.
- `bytes` — размер; validator сверяет.
- `count` — для UI/preview, не для доверия.
- `createdAt` — UTC ISO-8601; не локализовать в файле.

## D.3. Envelope компонента

```json
{
  "component": "home",
  "schema": 1,
  "profileId": "p1",
  "data": {
    "order": [
      "com.example.app",
      {
        "id": "folder-uuid",
        "name": "Кино",
        "apps": ["com.example.one", "com.example.two"]
      }
    ],
    "labels": {
      "com.example.app": "Example"
    }
  }
}
```

- `component` — whitelist id.
- `schema` — версия компонента.
- `profileId` — только для per-profile.
- `data` — типизированная структура; unknown fields игнорируются, но не ломают decode.
- Все строки проходят существующие caps (`MAX_TEXT_CHARS`, `MAX_ITEM_*`, `MAX_PROFILES`).

## D.4. Компоненты и ID

| Component ID | Содержимое | Per-profile | Обязательный |
|---|---|---|---|
| `profiles` | `list`, `current`, `next_id`, `ask_on_boot`, `ask_on_wake` | Нет | Да, если выбраны профили |
| `home` | `home_order` (пакеты/папки), `labels` | Да | Нет |
| `discovery` | settings, favorites, hidden, usage, shown | Да | Нет |
| `kids` | allowlist only | Да | Нет |
| `weather` | settings only; no cache | Нет | Нет |
| `saver` | delay/music/refresh/themes/hidden clips | Нет (глобально) | Нет |
| `language` | `ui_language` | Нет | Нет |
| `content` | `content_recommendations`, `content_continue_watching` (для `p1` и/или глобально) | Зависит | Нет |
| `startup` | `target`, `label`, `on_wake` | Нет | Нет |

`focused_package`/`shelf_package` не включаются: это эфемерное состояние. После restore Home выбирает первый элемент/сохранённый порядок.

## D.5. Целостность и лимиты

- SHA-256 каждого компонента; для encrypted — AEAD tag + SHA-256 ciphertext.
- ZIP CRC как вторичная проверка; duplicate entry names — reject.
- Лимиты:
  - архив: 32 MiB (можно 64 MiB, но 32 достаточно для текущих данных);
  - распакованный суммарный размер: 64 MiB;
  - один entry: 8 MiB;
  - entries: 256;
  - JSON: 8 MiB;
  - compression ratio: 100:1 (zip-bomb);
  - profiles: 3 (приложение), максимум 32 в decoder для диагностики, но restore >3 отклоняется;
  - discovery items: существующие `ITEM_LIMIT = 100`, `SHOWN_LIMIT = 128`, `USAGE_LIMIT = 64`;
  - home: `MAX_ORDER_ENTRIES = 1_000`, `MAX_FOLDER_APPS = 1_000`;
  - строки: существующие caps + backup caps.
- Проверять `entry.name`:
  - не пустой, не absolute, без `..`, без `\`, без `:`/NUL;
  - нормализованный путь начинается с `components/` (или `manifest.json`);
  - не писать entry напрямую в файловую систему по имени из архива; staging-имена генерируются по component id.
- ZIP bomb: читать поток с счётчиком, а не доверять `entry.size`.

## D.6. Версии

- `formatVersion = 1`, `dataSchema = 1` — первая версия.
- Backward: приложение читает `dataSchema <= CURRENT`, мигрирует вверх.
- Forward: `dataSchema > CURRENT` или `formatVersion > CURRENT` → отказ с сообщением «копия создана более новой версией».
- Unknown component ID в известной schema: предупредить и пропустить; не валить весь restore, если core-манифест валиден.
- Unknown fields: сохранять в архиве; при restore текущая версия применяет известные поля. Старая версия не должна притворяться, что понимает новые поля.

---

# E. Категории данных, выбор и совместимость

## E.1. Пользовательские категории

| Категория UI | Внутренние компоненты | По умолчанию | Cross-device | Что не переносится |
|---|---|---|---|---|
| **Профили и раскладка** | `profiles`, `home` для каждого профиля | Да | Да, по package names | `focused_package`, `shelf_package`, порядок отсутствующих приложений теряется без новой модели |
| **Избранное и рекомендации** | `discovery` (favorites/hidden/usage/shown/settings) | Да | Да, но провайдеры/приложения могут отсутствовать | `intentUri`/art URL могут быть неактуальны; id содержит URI |
| **Kids** | `kids` allowlist только | Да | Да, allowlist по package names | PIN/recovery **никогда**; PIN задаётся заново |
| **Погода** | `weather` settings | Да | Да | Кэш/поколение; координаты требуют шифрования при экспорте |
| **Заставка и оформление** | `saver` | Да | Да | `saver_refresh_attempt_ms`; hidden clip IDs, которых нет в новом каталоге, фильтруются |
| **Язык и контент** | `language`, `content` | Да | Да | — |
| **Запуск при boot/wake** | `startup` target/label/on_wake | Да | Условно | `home_override`, системные permissions |
| **Диагностика** | `list_quarantine`, `order_quarantine`, crash | Нет | Нет | Сырые/чувствительные данные; не экспортировать |
| **Кэши** | aerial posters/previews/media, weather cache | Нет | Нет | Восстанавливаются/регенерируются |
| **Каталог Aerial** | `aerial_catalog.json` | Нет | Нет | Сетевой/ассетный кэш |
| **Setup / sleep** | setup progress, sleep timer | Нет | Нет | Локальное состояние; после restore setup помечается done |

## E.2. Что можно/нельзя в ручной экспорт без пароля

Рекомендуемая политика:

| Категория | Plain export | Encrypted export |
|---|---|---|
| Профили (без PIN) | Разрешено с предупреждением | Да |
| Раскладка/папки | Да | Да |
| Kids allowlist | С предупреждением | Да |
| Kids PIN/recovery | **Никогда** | **Никогда** |
| Discovery (favorites/history) | **Запрещено** (id/intent URI) | Да |
| Weather settings без координат | Да | Да |
| Weather с `place/lat/lon` | **Запрещено** | Да |
| Saver/language/content | Да | Да |
| Startup | Да | Да |
| Диагностика/карантин | Никогда | Никогда |
| Кэши | Никогда | Никогда |

Если пользователь выбирает запрещённую категорию без пароля — UI требует включить шифрование или снимает выбор. Это лучше, чем «тихо вырезать» данные.

Локальные автоматические backup-файлы в `filesDir` — plain, но app-private (sandbox). Это не «шифрование at rest»; на rooted/forensic устройстве они читаемы, как и сами prefs. Не называть их зашифрованными.

## E.3. Семантика restore

1. **Replace, не merge, для выбранных ключей.** Если выбрана погода — настройки погоды заменяются; если не выбрана — не трогаются.
2. **Профили — merge by ID** (рекомендация):
   - профиль из backup с существующим id обновляется (backup wins);
   - отсутствующий id добавляется;
   - текущие профили, которых нет в backup, **не удаляются**;
   - если итог > `ProfileCatalog.MAX` — restore отклоняется **до** записи;
   - `next_id = max(current, backup, maxId+1)` — не переиспользовать удалённые id.
   - Альтернатива «полная замена каталога» требует архивации удаляемых профилей; в v1 не рекомендуется.
3. **Layout/discovery** применяются только для профилей, которые есть в backup; если профиль не выбран/не существует — компонент пропускается с предупреждением.
4. **Kids:** allowlist заменяется; `kids` flag включается только если у профиля есть PIN **или** пользователь тут же задаёт новый. Иначе профиль восстанавливается как обычный, с предупреждением «Kids Mode требует нового PIN». Это предотвращает `kids=true` без защиты.
5. **Weather:** настройки заменяются; `cache2/cache_at/cache_key` очищаются; `data_generation` увеличивается; при `enabled=true` — запланировать refresh.
6. **Saver:** hidden clip IDs фильтруются по текущему каталогу; themes — по enum; неизвестные значения отбрасываются с предупреждением.
7. **Startup:** target сохраняется; если пакет отсутствует, показывается «приложение не установлено», запуск не выполняется; `home_override` не восстанавливается.
8. **Setup:** после успешного restore (любых пользовательских данных) `setup_done=true`, `setup_step/version` очищаются; иначе мастер может внезапно появиться.
9. **Permissions/system:** никогда не восстанавливаются; UI показывает, что нужно выдать заново.

## E.4. Что нельзя перенести в принципе

- Разрешения (`READ_TV_LISTINGS`, Wi-Fi/location, notifications).
- Accessibility service enablement, device admin, default Home role, `home_override`.
- Установленные приложения и их данные.
- System locale (мы храним только `ui_language` лаунчера).
- TV-specific capabilities (TLS, decoder, HDMI/4K, remote).
- Точные ID устройств/серийники (и не нужно).
- Kids PIN (по политике).
- Кэши/диагностику.

---

# F. Безопасный restore и валидация

## F.1. Валидация до записи

Обязательные проверки:

1. **Контейнер**: ZIP читается, нет duplicate entries, нет path traversal, соблюдены caps.
2. **Манифест**: `kind`, `formatVersion`, `dataSchema`, `backupId`, `createdAt`, `components`; неизвестные поля допустимы.
3. **Совместимость**: `formatVersion <= CURRENT_FORMAT`, `dataSchema <= CURRENT_SCHEMA`; иначе отказ.
4. **Целостность**: SHA-256 каждого компонента; для encrypted — GCM tag; размеры.
5. **Схема**: типы, диапазоны, enum, caps; отсутствующие обязательные поля → ошибка; лишние → игнор.
6. **Cross-component**:
   - profile IDs уникальны, ≤ 3;
   - `home`/`discovery`/`kids` ссылаются на профиль из `profiles` или на существующий;
   - folder IDs уникальны; package names валидны;
   - `current` существует;
   - weather lat/lon диапазоны; source/units/refresh допустимы;
   - startup target формата `""`/`app:<pkg>`;
   - language допустим;
   - discovery item package/activity/URI caps.
7. **Права/система**: backup не пытается включать permissions; если данные содержат `home_override`, они игнорируются.
8. **Preview**: пользователь видит, что будет заменено, до apply.

Ошибка валидации → **ни одной записи** в live prefs. Источник не изменяется. Временные файлы удаляются.

## F.2. Staging

- Источник копируется в `cacheDir/restore/<uuid>/` потоком с лимитом.
- Payload извлекается только в сгенерированные файлы (`0001.bin`, ...), не по именам из ZIP.
- JSON парсится после проверки размера/hash.
- Staging удаляется после успеха/rollback; при сбое — сохраняется для диагностики (без секретов? если encrypted — да; если plain — под app-private, ограниченно).

## F.3. Pre-restore snapshot

- Перед apply: `BackupManager.createLocalBackup(trigger = PRE_RESTORE, selection = ALL_AFFECTED)`.
- Копия pinned; не участвует в auto-retention, пока restore не завершён.
- Если snapshot не создан — restore не начинается.
- Snapshot включает все категории, которые будут затронуты, плюс `profiles` для консистентности.

## F.4. Применение

- Один `SharedPreferences.Editor` на файл; `commit()`; проверить `true`.
- Порядок:
  1. `profiles` (метаданные/current) — **в конце**, чтобы active profile не переключился на недописанные данные;
  2. per-profile `home`/`discovery`/`kids` — для целевых профилей;
  3. глобальные `language`/`content`/`saver`;
  4. `weather`;
  5. `startup`;
  6. bookkeeping (`setup_done`, schema, cache invalidation).
- Для `p1` глобальные и layout-ключи пишутся **одним** editor/commit, потому что это один файл.
- Для `p2`/`p3` — отдельный editor/commit на файл.
- После каждого commit — read-back проверка ключей.
- `StorageGate.withWrite` удерживается на всё время apply; UI не мутирует данные.
- Никаких bitmap/UI-операций в IO.

## F.5. Journal и crash recovery

`filesDir/backups/journal.json`:

```json
{
  "state": "prepared|applying|applied|rolled_back|failed",
  "backupId": "…",
  "preRestoreId": "…",
  "startedAt": "…",
  "components": [
    { "id": "profiles", "status": "pending|written|verified|failed" }
  ]
}
```

- `AtomicWrite` для journal.
- На старте `DataBootstrap`/`RestoreRecovery`:
  - `applying`/`failed` → автоматический rollback из `preRestoreId`; journal `rolled_back`; показать статус;
  - `applied` → данные применены, нужен restart; journal `applied`;
  - `prepared` → apply не начинался; удалить staging/journal;
  - rollback failed → **не удалять** данные, показать «требуется восстановление», сохранить journal, не делать бесконечный retry.
- Rollback использует тот же apply-механизм, но с флагом `isRollback` (не создаёт новый pre-restore, не рекурсия).

## F.6. Ограничения атомарности (честно)

- `SharedPreferences.commit()` атомарен для **одного файла**, не для набора файлов.
- Между commit разных файлов возможен process death; journal + pre-restore закрывают это, но **не делают restore ACID**.
- Если приложение убито во время `commit()`, Android/SharedPreferences обычно оставляет либо старый, либо новый файл; backup FS не гарантирует, но journal rollback восстановит.
- Не заявлять «транзакционный restore» без оговорки.
- Восстановление не должно удалять исходный backup или pre-restore до успешной проверки.

## F.7. Ошибки и поведение

| Ситуация | Поведение |
|---|---|
| Нет места | Ошибка до записи; старые копии не удаляются |
| Битый ZIP/JSON | Отказ; live data не тронуты |
| Hash mismatch | Отказ; источник помечен damaged; не применять |
| Encrypted, неверный пароль | Отказ; без подсказок, без перебора |
| Schema новее | Отказ с версией |
| Профилей > 3 после merge | Отказ до записи; предложить удалить профиль/выбрать другое |
| Пакет отсутствует | Сохранить запись (после новой модели) как unavailable; не запускать |
| Kids без PIN | Восстановить non-kids + предупреждение; allowlist сохранить |
| Permission отсутствует | Не включать; показать, что нужно выдать |
| Apply failed | Rollback; сообщение; journal |
| Process death | Recovery при следующем старте; rollback/roll-forward по journal |
| Отмена пользователем до apply | Удалить staging; live data не тронуты |
| Отмена во время apply | Не поддерживать; wait/rollback |

---

# G. Cross-device, SAF и шифрование

## G.1. Cross-device модель

- Раскладка хранится как **package names + folder ids/names**, без координат/плотности/разрешения. Это уже хорошо переносится между 1080p/4K: `TvOs` масштабирует канвас.
- Папки: id/name/package list; id генерируется; при restore сохранять id, если уникален, иначе новый.
- Профили: id/name/emoji/color/kids; id сохранять; emoji uniqueness проверить.
- Discovery: package/activity/intent; provider IDs могут не существовать; favorite остаётся, но launch может быть недоступен.
- Weather: координаты/единицы/источник; TV не требует location permission; после restore — refresh.
- Saver: themes enum + clip IDs; фильтровать по текущему каталогу.
- Startup: package может отсутствовать; не запускать, показать.
- Language: `ui_language`; проверить допустимые значения.
- `show_phone_apps`: переносится; на TV phone apps может не быть — список просто пуст.

### G.1.1. Отсутствующие приложения — обязательная доработка

Сейчас `buildHomeOrderRepair()`:

```kotlin
val app = byPackage[packageName] ?: continue
```

(`data/HomeOrderStore.kt:182,190`) — запись исчезает. Чтобы выполнить требование «сохранить запись, но пометить недоступной», нужно:

- добавить в `HomeItem` вариант `MissingApp(packageName, label)` **или** в `TvApp` поле `available: Boolean` + синтетический `TvApp` без activity;
- `HomeOrderStore.saveOrder()` должен сохранять missing entries (package + label) в `home_order` или отдельном `home_missing`;
- `buildHomeOrderRepair()` не должен `continue` для отсутствующего пакета, а создавать placeholder в той же позиции/папке;
- Home/Grid/Folder UI: disabled tile, явная метка «Недоступно», без запуска; D-pad фокус не должен застревать;
- `FolderEdit`, `AppSearch`, `KidsFilter`, `HomeFocus` — обработать новый вариант;
- при установке приложения placeholder заменяется реальным `TvApp` (сопоставление по package);
- backup layout хранит `labels` для missing, иначе на новом устройстве будет только package name.

Без этого пункта backup может сохранить JSON, но текущий Home при первом чтении снова выбросит missing. Это **не backup-баг**, а ограничение модели Home; его нужно чинить отдельной фазой.

## G.2. SAF export

- `ActivityResultContracts.CreateDocument("application/zip")` (или `application/octet-stream`).
- `contentResolver.openOutputStream(uri, "w")`; писать потоком; не загружать весь архив в память.
- Имя: `ovisiontv-backup-YYYYMMDD-HHmm.ovbk`; без имени профиля/устройства.
- Если file picker отсутствует: `ActivityNotFoundException` → диалог «На этом TV нет файлового менеджера; используйте USB/другой способ»; не crash.
- Никаких `WRITE_EXTERNAL_STORAGE`/`MANAGE_EXTERNAL_STORAGE`.
- После записи — перечитать/проверить размер/hash (если provider позволяет) и показать успех.
- Если пользователь отменил — состояние `Idle`, без ошибки.

## G.3. SAF import

- `ActivityResultContracts.OpenDocument(arrayOf("application/zip", "application/octet-stream"))`.
- Скопировать в staging с лимитом; не доверять имени.
- Валидировать до показа preview.
- Если файл зашифрован — запросить пароль; KDF/params из manifest; не логировать пароль.
- Не сохранять URI постоянно, если не нужен auto-external; для одного импорта — достаточно.
- Для auto-external — `ACTION_OPEN_DOCUMENT_TREE` + persisted permission; проверять доступ.

## G.4. Шифрование

### Алгоритм

- AES-256-GCM (JCE, Android API 28+).
- PBKDF2WithHmacSHA256, random 16/32-byte salt, **210 000** итераций (компромисс для TV; OWASP рекомендует больше, но 4K TV CPU/1 GB RAM; измерить перед фиксацией).
- Random 12-byte nonce, **один nonce на шифрование**; никогда не переиспользовать.
- Tag 128 bit.
- Пароль не хранится; в manifest только KDF/cipher/salt/iterations/nonce.
- Пароль минимум 8 символов (лучше passphrase); 4-значный PIN **не разрешать** для шифрования.
- Ошибка аутентификации → «неверный пароль или повреждённый файл», без деталей.
- Никакого custom crypto/obfuscation.

### Что шифруется

- Внутренний ZIP со всеми компонентами + внутренний manifest (если нужен).
- Внешний manifest — только несекретные метаданные/параметры.
- Имена файлов/профилей не утекают.

### Локальные auto-backup

- Plain в app-private `filesDir/backups`; не называть «encrypted at rest».
- Если owner хочет шифровать и локальные: нельзя использовать случайный ключ, потерянный при uninstall (тогда backup не переживёт reinstall). Варианты:
  - пароль при каждом auto backup — невозможно без UI;
  - Android Keystore-ключ — теряется при uninstall/сбросе, backup бесполезен для reinstall;
  - оставить plain app-private — практичный выбор.
- Вывод: локальные auto-backup — для rollback/migration, не для переноса после uninstall. Для переноса — только encrypted export.

## G.5. Угрозы

| Угроза | Мера |
|---|---|
| Чужой файл backup с zip-slip | Нормализация, whitelist путей, staging-имена |
| Zip bomb / огромный JSON | Caps, streaming, ratio limit |
| Подмена payload | SHA-256 + AEAD tag |
| Чтение экспортированного файла | Пароль/AES-GCM; запрет plain для sensitive |
| Офлайн-перебор Kids PIN | Не экспортировать PIN hash; PIN заново |
| Вредоносный `intentUri` | Валидация URI/пакета/activity; launch уже проверяет resolve package (`DiscoveryIntents.kt`) |
| Вредоносный package name | Caps/валидность; только установленные пакеты запускаются |
| Утечка через manifest | Encrypted manifest не содержит пользовательских строк |
| Root/forensic | App-private prefs не являются защитой; encryption только для export; честно документировать |
| Случайное удаление данных | Pre-restore snapshot, journal, no delete-on-error |

## G.6. Что не дает backup

- Не восстанавливает разрешения/роль Home/accessibility/device admin.
- Не устанавливает приложения.
- Не переносит данные приложений.
- Не гарантирует, что deep link/art URL провайдера живы.
- Не переживает uninstall без экспорта.
- Не является cloud sync.

---

# H. Миграции и rollback

## H.1. Две независимые версии

| Версия | Что означает | Где |
|---|---|---|
| `formatVersion` | структура ZIP/manifest/encryption | backup layer |
| `dataSchema` | семантика компонентов | backup + live data |
| `component.schema` | версия конкретного компонента | envelope |

`SetupProgress.STEP_VERSION` — **не** data schema; не путать.

## H.2. Live data schema

Нужен маленький `backup_state` (SharedPreferences или JSON в `filesDir/backups`):

```json
{
  "dataSchema": 1,
  "lastSeenVersionCode": 73,
  "lastMigrationFrom": 0,
  "lastMigrationTo": 1,
  "lastMigrationStatus": "ok|failed|skipped",
  "lastBackupAt": 0,
  "lastBackupId": "",
  "lastAutoBackupAt": 0
}
```

- `dataSchema` — то, что реально записано в stores.
- `lastSeenVersionCode` — для pre-update backup.
- `lastBackupAt` — для UI/дедупликации.
- Не включать этот state в backup как пользовательские данные; после restore выставить `dataSchema = backup.dataSchema` (или current после миграции).

## H.3. MigrationManager

```kotlin
interface DataMigration {
    val from: Int
    val to: Int
    fun migrate(input: BackupData): BackupData
}
```

- Registry `0 -> 1`, `1 -> 2`, …; применять последовательно; нет пропусков.
- Для live data: `LiveMigration` читает/пишет stores; перед запуском — pre-migration backup.
- Для restore: мигрировать **в памяти** `BackupData` до apply; live stores не трогать.
- Если миграция не удалась — отказ; live data не менять; pre-migration backup остаётся.
- Unknown fields: сохранять через `JSONObject` extras, где возможно; не обещать perfect round-trip для полей, которых текущая версия не знает.
- First version: `dataSchema = 1`; для существующих установок `0 -> 1` — baseline, который:
  - делает pre-migration backup;
  - не переписывает stores, если не нужно;
  - фиксирует schema=1.
- `SetupProgress` и Aerial version не включать в эту цепочку.

## H.4. Backup format migrations

- `FormatMigration` отдельно от `DataMigration`.
- v1 → v2: преобразование контейнера/манифеста; читать старый, писать новый.
- Не мутировать исходный файл; создавать новый.
- Если backup encrypted — миграция после расшифровки (в памяти/temp), затем повторное шифрование с тем же паролем? Пароль недоступен для auto; только при restore пользователь вводит. Для format migration при импорте — можно, но сложно; v1 не нужен.

## H.5. Rollback

| Сценарий | Действие |
|---|---|
| Restore failed до apply | Ничего не менять; staging удалить |
| Restore failed во время apply | Rollback из pre-restore; journal |
| Process death во время apply | Recovery при старте: rollback из pre-restore |
| Restore succeeded, но UI stale | Restart activity; данные уже применены |
| Migration failed | pre-migration backup остаётся; live data не менять; показать ошибку |
| App downgrade | Не поддерживать; backup schema > current → отказ. Android downgrade обычно блокирует; sideload — на свой риск |
| Backup file corrupted | Не применять; сохранить повреждённый файл в quarantine для диагностики (если пользователь хочет) |

## H.6. Существующие implicit migrations

Их не надо переписывать в v1, но нужно **обернуть** и знать:

- `parseProfiles()` repair: может менять/терять неизвестные поля; backup schema должна сохранять исходный JSON? Для restore лучше использовать типизированную модель, а не raw; неизвестные поля — в extras.
- `buildHomeOrderRepair()`: чинит id и отбрасывает missing; после добавления `MissingApp` поведение изменится; нужны тесты.
- `DiscoveryStore.decode`: отбрасывает плохие элементы; backup decoder должен отклонять/помечать, а не тихо терять, если это влияет на данные.
- `SafePreferences`: не удаляет повреждённые значения; backup не должен «лечить» их молча.
- `WeatherPreferences.clearData()` generation: сохранить механизм; restore weather должен bump generation и очистить cache.

---

# I. UI, тесты, план и решения

## I.1. Размещение в Settings

Сейчас root-секции: `INTERFACE`, `HOME_SCREEN`, `PROFILES`, `WEATHER`, `DEVICE`, `ABOUT` (`SettingsScreen.kt:362-367`), шесть строк. Добавлять седьмую root-секцию нельзя без переработки.

Рекомендация: **`Settings → System → Backup & Restore`**:

- В `PAGE_SYSTEM` (`SettingsScreen.kt:1933-2041`) добавить `SettingsRow` с `chevron = true`, значением «последняя копия»/«никогда».
- Новые страницы: `PAGE_BACKUP = 43`, `PAGE_BACKUP_CREATE = 44`, `PAGE_BACKUP_LIST = 45`, `PAGE_BACKUP_RESTORE = 46`, `PAGE_BACKUP_PREVIEW = 47`, `PAGE_BACKUP_PROGRESS = 48`, `PAGE_BACKUP_SETTINGS = 49`, `PAGE_BACKUP_DELETE = 50`, `PAGE_BACKUP_PASSWORD = 51`.
- Обновить `parentOf()` (`:889-905`), `sectionOf()` (`:3972-3990`), title/hint maps (`:2252-2370`), `pageOf`/section logic.
- Не добавлять новую root-секцию; не ломать 6-строчный layout.
- Строки EN/RU в `res/values/strings.xml` и `res/values-ru/strings.xml`; никаких хардкодов.

### Страницы

1. **Backup & Restore (home)**
   - Last backup: date/size/status; auto on/off.
   - `Create backup` → `PAGE_BACKUP_CREATE`.
   - `Restore from backup` → `PAGE_BACKUP_RESTORE`.
   - `Backup settings` → `PAGE_BACKUP_SETTINGS`.
   - `Delete local backup` → list.
2. **Create**
   - Category toggles (E.1), estimated size («оценка по лимитам», не фейк).
   - Destination: `Local` / `Export file` (SAF).
   - Encryption toggle; password page; warning about loss.
   - Summary/confirm; Cancel focused by default.
3. **Restore source**
   - Local list + `Import file`.
   - For each: date, app version, schema, components, encrypted.
   - `Continue` → preview.
4. **Preview**
   - Profiles/counts, categories, conflicts, missing apps, Kids PIN reset, permissions not restored.
   - `Restore` destructive, `Cancel` default.
5. **Progress**
   - Real stages; no fake percent; no Back during apply.
6. **Settings**
   - Auto backup on/off; daily/weekly/off; retention 3–10; external tree opt-in; last status; diagnostics.
7. **Delete**
   - List with size/date; confirm; cannot delete pinned pre-restore while journal active.

### D-pad / accessibility

- Использовать существующий `SettingsRow`/focus cursor; не создавать второй focus engine.
- `open(page, focus = index)` для начального фокуса.
- Подтверждение: Cancel по умолчанию; destructive row с явной меткой.
- Progress: focus на кнопке «Назад» только после завершения; во время apply — disabled.
- Content descriptions для status/icons; не полагаться только на цвет.
- Пароль: `BasicTextField` с `PasswordVisualTransformation`; показать/скрыть; D-pad фокус; TV IME; без 4-значного PIN.
- Длинные строки — `maxLines`, ellipsis; EN/RU.
- Пустые состояния: «Копий пока нет», «Файл не выбран», «Нет файлового менеджера».

## I.2. Тестовая матрица

### Unit (JVM)

| Группа | Проверки |
|---|---|
| Manifest/codec | round-trip plain/encrypted; обязательные поля; unknown fields; неверные типы; caps |
| Archive | zip-slip (`../`, absolute, `\`), duplicate entries, zip-bomb ratio, truncated, CRC, hash mismatch, too many entries, too big |
| Schema | profiles/home/discovery/kids/weather/startup; диапазоны; enum; cross-component; >3 profiles; missing profile; duplicate folder id |
| Selection | plain vs encrypted policy; Discovery/weather coords blocked plain; Kids PIN never; diagnostics/cache never |
| Encryption | correct/wrong password; tamper → fail; unique salt/nonce; KDF params; no plaintext; password min length; iteration cost |
| Retention | prune only after validated; pinned preserved; max count/size; corrupt index rebuild/quarantine |
| Restore | replace semantics; profile merge; p1 global+layout; p2/p3 isolation; missing apps; kids no PIN; weather cache clear; startup missing |
| Journal | prepared/applying/applied/rolled_back; simulated failure per file; recovery on start; no infinite retry |
| Migration | 0→1 baseline; 1→2; unknown/newer; failure; unknown fields; format migration |
| Scheduler | interval, dedupe, constraints, retry/backoff, unique work (fake clock / WorkManager test helper) |
| Privacy | manifest no PIN/token/title in encrypted; logs no secrets; no path leakage |

### Instrumented

| Группа | Проверки |
|---|---|
| SharedPreferences restore | p1 vs p2/p3; global keys not copied; current profile; `apply` vs `commit` verification |
| SAF round-trip | CreateDocument/OpenDocument; content resolver; cancel; picker absent; no storage permission |
| Restore transaction | process death between files → recovery; pre-restore rollback; source untouched |
| Kids | allowlist restored; PIN absent; kids flag safe; gate behavior |
| Home missing app | placeholder position/folder; disabled launch; focus; install replaces |
| Weather | settings restored; cache cleared; generation bump; refresh scheduled |
| Startup | missing package warning; no launch; `home_override` untouched |
| UI | D-pad traversal each page; default Cancel; progress states; focus restore; EN/RU; TalkBack labels; long text |
| Encryption UI | password entry; wrong password; cancel; no secret in saved state |
| Auto backup | WorkManager test; constraints; unique; boot; storage low; failure status |
| Large archive | streaming, memory cap, low-end emulator (1 GB if available) |

### Матрица устройств

| Платформа | Статус |
|---|---|
| AVD API 34 x86 (`hume-tv`) | Есть; можно запускать instrumented |
| Physical Android TV / Google TV | **Не проверено** |
| API 28–33, 35 | **Не проверено** (minSdk 28) |
| ARM32/ARM64 | **Не проверено** |
| 1 GB RAM | **Не проверено** |
| 4K / разные density | **Не проверено** |
| IR/Bluetooth/vendor remote | **Не проверено** |
| TalkBack voice | **Не проверено** |
| SAF provider отсутствует/облачный | **Не проверено** |
| Storage full / quota | **Не проверено** |

Существующий baseline из QA-отчёта: 426 unit / 223 instrumented, зелёные на AVD; это **не** проверка backup (его ещё нет). После реализации счётчики должны быть обновлены фактическим прогоном.

## I.3. Поэтапный план (после approval)

| Этап | Содержание | Риск | Выход |
|---|---|---|---|
| 0 | Решения owner (ниже), фиксация политики | Низкий | Утверждённый scope |
| 1 | `BackupContract`, models, `BackupSource`, codec, validator, unit-тесты; без UI и без изменения поведения | Низкий | Читаемый/валидируемый формат |
| 2 | `BackupRepository`, retention, local create/list/delete; `StorageGate` для writers (или явная оговорка) | Средний | Локальные копии |
| 3 | SAF export/import; encryption; password UI | Средний | Перенос на другое устройство |
| 4 | `RestoreEngine`, pre-restore, journal, recovery, restart; instrumented | Высокий | Безопасный restore |
| 5 | Settings UI, D-pad, EN/RU, status | Средний | Пользовательский сценарий |
| 6 | WorkManager auto + pre-update/pre-migration hooks; dependency metadata | Средний | Автоматизация |
| 7 | `MigrationManager` 0→1, затем будущие; live migration gate | Высокий | Предсказуемые обновления |
| 8 | `MissingApp`/placeholder в Home + UI + tests | Средний | Требование «недоступное приложение» |
| 9 | Проверка на AVD, затем physical TV; обновление QA/Security/Privacy docs | Средний | Verified |

**Не начинать с UI и не начинать с WorkManager.** Сначала формат/валидатор/тесты; иначе UI закрепит незрелый контракт.

## I.4. Решения, которые нужны от owner

| № | Вопрос | Рекомендация |
|---|---|---|
| 1 | Добавлять `androidx.work:work-runtime-ktx`? | Да, для daily auto; иначе только manual + pre-migration/pre-restore |
| 2 | Автоматический backup включён по умолчанию? | Да, daily, 5 локальных копий, только local; external — opt-in |
| 3 | Внешний auto backup через SAF tree? | Не в v1; manual export достаточно; добавить позже |
| 4 | Шифрование ручного экспорта | Обязательно для Discovery/координат; рекомендуется для всех; локальные auto — plain app-private |
| 5 | Kids PIN | **Никогда** не экспортировать; allowlist переносить; PIN заново; `kids` без PIN не включать |
| 6 | Профили при restore | Merge by ID, backup wins, current extras keep; >3 — отказ. Полная замена — отдельно, с архивом |
| 7 | Missing apps | Добавить `MissingApp`/placeholder в Home (этап 8); без этого требование не выполнено |
| 8 | `intentUri`/art URL в plain export | Запретить plain для Discovery; только encrypted |
| 9 | UI location | `Settings → System → Backup & Restore`; не добавлять root-секцию |
| 10 | Restore semantics | Replace selected keys; профили merge by ID; не удалять unselected |
| 11 | Pre-migration backup при нехватке места | Для destructive migration — остановиться; для additive — продолжить с предупреждением |
| 12 | Privacy policy | Обновить после реализации: backup/export, encryption, no cloud, retention |

## I.5. Acceptance criteria

- [ ] Пользователь может создать локальную копию и увидеть её дату/размер.
- [ ] Пользователь может экспортировать `.ovbk` через SAF и импортировать на другом устройстве.
- [ ] Restore валидирует формат/хэши/схему до записи; при ошибке live data не меняются.
- [ ] Перед restore создаётся pre-restore копия; при сбое выполняется rollback; journal переживает process death.
- [ ] Восстанавливаются: профили, layout/папки, discovery favorites/history (с шифрованием), kids allowlist, weather settings, saver, language/content, startup.
- [ ] Не восстанавливаются: permissions, Home role, accessibility, device admin, Kids PIN, caches, diagnostics, sleep timer, setup step.
- [ ] `p1` глобальные ключи и layout не смешиваются с `p2`/`p3`.
- [ ] Отсутствующие приложения сохраняют позицию и помечаются недоступными (после этапа 8).
- [ ] Kids-профиль без PIN не включается как kids; PIN можно задать заново.
- [ ] Нет новых permissions; `allowBackup=false` остаётся.
- [ ] Нет фейкового прогресса; D-pad и Cancel-по-умолчанию; EN/RU.
- [ ] Unit + instrumented тесты; `lintDebug` без новых errors; dependency verification обновлена (если WorkManager).
- [ ] Проверено на AVD; physical TV отмечен отдельно, без ложных заявлений.

## I.6. Что не проверено в этом аудита

- Реальный размер пользовательских prefs на устройстве (не измерялся).
- Поведение SAF picker на конкретных TV/вендорах.
- WorkManager на TV/Doze/вендорных прошивках.
- Производительность PBKDF2 210k на 1 GB TV.
- Поведение Android при process death во время `SharedPreferences.commit()`.
- Восстановление после настоящего uninstall (данных нет по определению).
- Физические TV, ARM, API 28–33/35, 4K, TalkBack.
- SCA/CVE для WorkManager (если добавляем) — потребуется dependency verification/CVE check.

---

## Приложение A. Быстрая карта «что сохранять»

| Сохранять | Не сохранять |
|---|---|
| `profiles.xml`: list/current/next_id/ask_on_boot/ask_on_wake | `list_quarantine` |
| `home_order` всех профилей + labels | `focused_package`, `shelf_package`, `order_quarantine` |
| Discovery settings/favorites/hidden/usage/shown (encrypted для export) | Кэш/сырые битые JSON |
| Kids allowlist | PIN/recovery/fails/lock/hint |
| Weather settings | `cache2`, `cache_at`, `cache_key`, `data_generation` |
| Saver delay/music/refresh/themes/hidden | `saver_refresh_attempt_ms`, media/poster/preview cache |
| `ui_language`, `content_*`, `show_phone_apps` | `setup_*`, sleep timer |
| Startup target/label/on_wake | `home_override` |
| — | Aerial catalog, crash trace, `ArtFailureCache` |

## Приложение B. Источники (ключевые)

- `app/src/main/AndroidManifest.xml:83-86`
- `app/src/main/res/xml/backup_rules.xml`
- `app/src/main/res/xml/data_extraction_rules.xml`
- `app/src/main/java/com/whatuneed/ovisiontv/kotlin/data/LauncherPrefs.kt`
- `app/src/main/java/com/whatuneed/ovisiontv/kotlin/data/Profiles.kt`
- `app/src/main/java/com/whatuneed/ovisiontv/kotlin/data/HomeOrderStore.kt`
- `app/src/main/java/com/whatuneed/ovisiontv/kotlin/data/KidsMode.kt`
- `app/src/main/java/com/whatuneed/ovisiontv/kotlin/data/WeatherPreferences.kt`
- `app/src/main/java/com/whatuneed/ovisiontv/kotlin/data/SaverPreferences.kt`
- `app/src/main/java/com/whatuneed/ovisiontv/kotlin/data/StartupPreferences.kt`
- `app/src/main/java/com/whatuneed/ovisiontv/kotlin/data/SafePreferences.kt`
- `app/src/main/java/com/whatuneed/ovisiontv/kotlin/data/AtomicWrite.kt`
- `app/src/main/java/com/whatuneed/ovisiontv/kotlin/discovery/DiscoveryStore.kt`
- `app/src/main/java/com/whatuneed/ovisiontv/kotlin/discovery/DiscoverySettings.kt`
- `app/src/main/java/com/whatuneed/ovisiontv/kotlin/discovery/DiscoveryIntents.kt`
- `app/src/main/java/com/whatuneed/ovisiontv/kotlin/ui/SettingsScreen.kt`
- `app/src/main/java/com/whatuneed/ovisiontv/kotlin/ui/HomeScreen.kt`
- `app/src/main/java/com/whatuneed/ovisiontv/kotlin/ui/LauncherRoot.kt`
- `app/src/main/java/com/whatuneed/ovisiontv/kotlin/data/AerialRefresh.kt`
- `app/src/main/java/com/whatuneed/ovisiontv/kotlin/data/CrashReport.kt`

---

**Итог:** архитектура backup/restore/migration для этого проекта должна быть **слоем над SharedPreferences + JSON**, а не новой БД. Главные обязательные вещи: версионированный формат, строгая валидация, pre-restore snapshot + journal, SAF, шифрование sensitive-категорий, запрет экспорта Kids PIN, корректная работа с `p1`-файлом и отдельная доработка Home для отсутствующих приложений. Реализацию предлагается начинать только после утверждения пунктов I.4.

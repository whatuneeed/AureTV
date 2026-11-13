# Release discipline — OVisionTV_Kotlin

Дата: 13.11.2026. Документ фиксирует правила выпуска после этапов 1–4 аудита. Он не заменяет доказательства прогонов: PASS в таблице ставится только по артефакту.

## 1. Версии и подпись

- `versionCode` растёт на каждом APK, который покидает машину; `versionName` называет пакет изменений. Оба читаются из `app/build.gradle.kts`. После любого изменения версии обязателен новый `assembleRelease`.
- `applicationId` — `com.whatuneed.ovisiontv.kotlin`. Смена applicationId или signing key — отдельное решение владельца с миграцией данных, не «техническая» правка.
- Ожидаемый сертификат: SHA-256 `327a50453093b783e84b1f2645b03319477d89966ea6a653dab53d5a567d7b10`, подпись APK Scheme v2. Ключи и `keystore.properties` не читаются и не коммитятся.
- Каталог `temp/` и `connected.log` — локальный мусор, в Git не входят. `screenshots/` исключён отдельно.

## 2. Обязательный gate перед выпуском

```bash
GRADLE_USER_HOME=C:/gradle-home ./gradlew \
  :app:testDebugUnitTest :app:testReleaseUnitTest :app:lintDebug \
  :app:connectedDebugAndroidTest :app:assembleRelease --console=plain --rerun-tasks
python tools/release_check.py
```

`tools/release_check.py` проверяет: APK существует, versionCode/versionName/applicationId совпадают с Gradle, minSdk 28 / targetSdk 35, подпись v2 и сертификат, паритет RU/EN ресурсов, печатает размер и SHA-256 для evidence.

Дополнительно на стенде: `adb install -r`, `set-home-activity`, мастер с нуля, Настройки RU/EN, смена языка и возврат, Control Center, поиск, BACK/D-pad, `logcat -b crash` пуст.

## 3. Матрица проверок

| Область | Статус | Чем подтверждено |
|---|---|---|
| API 34, AVD `hume-tv`, 1920×1080 | PASS | полный connected-прогон и обходы 0.4.44–0.4.48 |
| API 28 / 29 / 35 | NOT TESTED | нет установленных system images, кроме android-34 |
| Реальный ТВ, ИК-автоповтор | NOT TESTED | нет аппарата |
| Профиль LOW (1 ГБ, heap ≤160 МБ) | NOT TESTED | нет слабого бокса; логика покрыта `AdaptivePolicyTest` |
| 4K 3840×2160 | NOT TESTED | образ отклоняет `wm size 3840x2160`; формула покрыта тестом |
| TalkBack voice-pass | NOT TESTED | есть semantic-тесты, но не голосовой проход |
| RTL и локали кроме RU/EN | NOT TESTED | `supportsRtl=true`, переводов нет |
| Масштаб шрифта 1,15/1,3 | NOT TESTED | вёрстка держит maxLines, живьём не снималось |
| Soak / долгий idle | NOT TESTED | отдельного прогона нет |
| Реальный benchmark GPU | NOT TESTED | только emulator/SwiftShader |

## 4. Политика зависимостей и API

- Обновление Compose BOM, media3, core-ktx, AGP/Kotlin — отдельный diff с полным gate и проверкой `minSdk 28`; не совмещать с продуктовыми правками.
- Lint `NewApi` должен оставаться 0 Error. Рост Warning фиксируется и объясняется; скрывать предупреждения нельзя.
- R8/minify остаётся включённым для release. После изменения reflection/сериализации обязателен релизный smoke, а не только debug.
- Новые библиотеки не добавляются «на будущее»; Hilt/Room/Navigation/ViewModel не вводятся автоматически (решение аудита).

## 5. Данные и совместимость

- Ключи и файлы `StorageContractTest` — контракт: `ovisiontv_launcher`, `profiles`, `kids_mode`, `weather`, `startup`, ключи `home_order`, `focused_package`, `shelf_package`, `place`, `cache2` и т.д. Переименование — только с миграцией.
- Существующие профили `p1…`, `p1`-layout и формат PIN/PBKDF2 не меняются без отдельного согласования.
- Repair данных не должен отключать Kids или переписывать здоровый layout; `repairedIds` сохраняется.
- Backup/restore, темы и контентный поиск — отдельные продуктовые решения. Для backup сначала схема, transaction/rollback и границы данных.

## 6. Доказательства и сохранение

- Каждый выпуск: `UI_UX_UNIFICATION/evidence-0.4.xx/` (подпись/хэш, логи, XML, кадры), запись в `FIXES.md`, обновление `ARCHITECTURE_AUDIT/REPORT.md`.
- Старые evidence не перезаписываются; старые APK/бэкапы не трогаются. Source drift фиксируется хэшами до и после прогона.
- `connected`-прогон, упавший только на установке из-за смены debug/release подписи, не считается PASS: нужен повторный прогон после `pm uninstall` и отдельная запись об этом.
- Временный APK для установки можно держать в `temp/`, но в evidence — только хэш/подпись, не сам APK.

## 7. Checklist выпуска

1. Изменения и версия зафиксированы, `git status` понятен.
2. `testDebugUnitTest` + `testReleaseUnitTest` зелёные.
3. `lintDebug` без Error; предупреждения не выросли без объяснения.
4. `assembleRelease`, `tools/release_check.py` — PASS.
5. `connectedDebugAndroidTest` — PASS или явно NOT TESTED с причиной.
6. `install -r`, мастер RU, Настройки RU/EN, язык/BACK, Control Center, поиск, crash-буфер пуст.
7. Evidence и `FIXES.md` обновлены; старые артефакты не перезаписаны.
8. Коммит с версией и кратким перечнем исправлений.

## 8. Текущее состояние

- 0.4.49 (vc69): hotfix показа заставок (HTTP основной путь + HTTPS-запас, preview fallback, безопасный выбор встроенного каталога). `connectedDebugAndroidTest` **208/208**, юнит **343/343**, lint 0/14, `release_check.py` PASS, савер с пульта отображается на API 34.
- Этап 5 выполнен в доступном объёме: release gate, контрактные тесты и soak добавлены; 0.4.49 — saver-хотфикс после регрессии HTTPS-схемы. Аппаратная/API-матрица из §3 остаётся **NOT TESTED** и не выдаётся за PASS.
- API 28/29/35, реальный ТВ, LOW, 4K, голосовой TalkBack и длительный soak требуют отдельного стенда; для них нет ни образа, ни железа.

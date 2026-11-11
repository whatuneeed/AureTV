# 11 — REGRESSION TESTS

Цель: перед/после любой правки производительности подтверждать, что **не сломано поведение**. Проект уже имеет сильную тестовую базу — её нужно использовать как «забор», а не писать заново.

## 11.1 Что уже есть (baseline тестов)

| Набор | Файлы `.kt` | `@Test` |
|---|---:|---:|
| Unit (`app/src/test`) | 39 | 267 |
| Instrumented (`app/src/androidTest`) | 23 | 178 |
| **Всего** | **62** | **445** |
| Lint | 0 ошибок, 15 предупреждений (0.4.34) | — |

> Числа — прогон 0.4.34 (10.11.2026). Исторический baseline STAGE 1–2: 34/222 и 22/166 (388). Рост дали тесты `AdaptivePolicy`, `MemoryPressure`, `TvOs` и локализационных сценариев.

Ключевые instrumented-наборы, которые **обязаны** остаться зелёными после любых правок:

| Файл | Что защищает | Связь с perf-правками |
|---|---|---|
| `HomeScreenTest.kt` | грид, раскладка, фокус | P-GPU-1/2/4 |
| `HomeOrderStoreTest.kt` | порядок/папки | P-FOCUS-3 |
| `MainActivityInputTest.kt` | обработка пульта | P-INPUT-1 |
| `MainActivityResumeTest.kt` | возврат из приложения | P-START-3/4 |
| `TvKeyRepeatTest.kt` | повтор клавиш | §4 input |
| `TopShelfTest.kt` | полка | P-GPU-2, P-IMG-2 |
| `SaverScreenTest.kt` | заставка | P-MEM-2 |
| `SaverPreviewTest.kt` | превью Aerial | P-MEM-3 |
| `ControlCenterTest.kt` | пункт управления | P-GPU overdraw |
| `SettingsScreenTest.kt` | настройки (в т.ч. устройство) | P-ADAPT-1 |
| `LauncherReloadTest.kt` | перезагрузка списка | P-IMG-1 |
| `PackageChangeFilterTest.kt` | broadcast установки | P-IMG-1, §9 |
| `LicensingTest.kt` | лицензия | защита от «оставили неиспользуемое» |
| `KidsGateTest.kt` | детский режим | защита функциональности |
| `LanguageReturnTest.kt` | язык/возврат | configChanges |

Ключевые unit-наборы, напрямую релевантные:

| Файл | Что защищает |
|---|---|
| `MotionDurationTest.kt` | `motionDuration` / Reduce Motion |
| `LaunchGeometryTest.kt` | геометрия запуска/фокуса |
| `HomeFocusTest.kt` | модель фокуса по индексам |
| `FocusLightTest.kt` | focus-light слой |
| `AerialCatalog*Test.kt` | каталог/расписание заставки |
| `SaverPlaylistTest.kt`, `SaverScheduleTest.kt` | плейлист заставки |
| `WatchNextTest.kt` | Watch Next |
| `WeatherTest.kt` | погода (§ CPU) |
| `StartupPreferencesTest.kt` | флаги старта |
| `PackageChangeTest.kt` | фильтр broadcast |
| `DeviceFormatTest.kt` | форматирование фактов устройства |
| `NetworkSecurityConfigTest.kt` | сеть/безопасность |

## 11.2 Базовые команды

```bash
# Полный прогон
GRADLE_USER_HOME="C:/gradle-home" ./gradlew :app:lintDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest

# Только то, что чаще ломают perf-правки
./gradlew :app:testDebugUnitTest --tests "*MotionDuration*" --tests "*HomeFocus*" --tests "*LaunchGeometry*" --tests "*FocusLight*"
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.whatuneed.ovisiontv.kotlin.HomeScreenTest
```

**Правило калитки:** перед правкой — прогнать; после правки — прогнать. Любой упавший тест = откат правки, не переписывание теста (кроме случая, когда тест проверял именно устраняемое поведение — тогда отдельный анализ).

## 11.3 Ручной чек-лист на ТВ (обязателен к автотестам)

Автотесты не покрывают GPU/RAM. После каждой perf-правки — ручной обход:

### Запуск и возврат
- [ ] Cold start: иконки появляются без «белой вспышки» и без скачка раскладки.
- [ ] Возврат из стороннего приложения: фокус на том же элементе, скролл не сброшен, нет двойного зума.

### Фокус и пульт
- [ ] Быстрые 30 нажатий вправо подряд — нет потерь/двойных шагов, нет отставания выделения.
- [ ] Вход в панель и мгновенный выход + стрелка — отклик фокуса < 100 мс (проверка `P-INPUT-1`).
- [ ] Удержание OK 350 мс → режим редактирования; удержание на соседе 750 мс → папка.
- [ ] Reduce Motion (системный): анимации телепортом, состояние/запуск не теряются.

### Экраны (overdraw/GPU)
- [ ] Главная: нет двух фоновых градиентов (визуально фон остаётся идентичным — §5).
- [ ] Полка: открытие/листание плавное, градиенты не «мигают», постер не размывается.
- [ ] Плитки: тень визуально не грубее после снижения `SHADOW_STEPS`.
- [ ] Control Center / меню / поиск / папка — открытие/закрытие плавное.

### Память
- [ ] Заставка → выход: bitmap-кэши освободились (проверка `dumpsys meminfo` до/после).
- [ ] `adb shell am send-trim-memory <pkg> RUNNING_LOW` — лаунчер не мигает, кэши сокращаются, приложение живо.
- [ ] Цикл «главная → заставка → настройки → назад» ×20: PSS не растёт монотонно.

### Изображения
- [ ] Иконки/баннеры не мутнее после смены `FilterQuality`; арт не «замылен».
- [ ] После установки/обновления приложения список обновляется (broadcast), старые иконки не «залипают».

### Адаптив (если вводится профиль LOW)
- [ ] На LOW функции все на месте: полка, заставка, поиск, папки, настройки.
- [ ] На LOW постеры не «рассыпаются» визуально.

## 11.4 Матрица «правка → что проверить»

| Правка (STAGE) | Автотесты | Ручной чек |
|---|---|---|
| Убрать двойной фон (P-GPU-1) | `HomeScreenTest` | фон/пустой экран, переключение профилей |
| Слить градиенты полки (P-GPU-2) | `TopShelfTest` | листание полки |
| Уменьшить `SHADOW_STEPS` (P-GPU-3) | — | тени всех плиток/капсул |
| `onTrimMemory` (P-MEM-1) | `SaverScreenTest` | send-trim-memory, цикл заставки |
| Ленивее ExoPlayer (P-MEM-2) | `SaverScreenTest`, `SaverPreviewTest` | вход в заставку ≤ 1 с |
| Кэш иконок (P-IMG-1) | `AppRepositoryTest`, `PackageChangeFilterTest` | установка/обновление/удаление |
| `shelfArtCache` (P-IMG-2) | `TopShelfTest` | листание 12 постеров вперёд/назад |
| Baseline Profile (P-START-1) | все на `connectedDebugAndroidTest` | cold start ×5 |
| R8 (P-START-2) | **все 445** + ручной обход | рефлексия media3/Compose |
| `AdaptivePolicy` (P-ADAPT) | `SettingsScreenTest`, `DeviceFormatTest` | LOW/MANUAL/HIGH |
| Убрать `delay(350)` (P-INPUT-1) | `MainActivityInputTest`, `HomeScreenTest` | выход из панели + стрелка |

## 11.5 Вердикт

- Тестовая база **достаточна** как регресс-забор: 445 тестов + lint 0.
- Не хватает **макробенчмарков** (cold start / frame timing) — это отдельный инструмент (`macrobenchmark`), а не unit/instrumented. Кандидат в STAGE 8.
- Любая правка GPU/RAM **обязана** сопровождаться ручным чек-листом §11.3 — автотесты её не видят.
- Финальный прогон 0.4.34 поймал кросс-стадийную регрессию, которую не видели юниты: после P-MEM-4 собственный ярлык «Настройки» и порядок плиток читались с `applicationContext` и игнорировали язык лаунчера. `LanguageReturnTest` (2/2) падал до фикса и проходит после. Вывод: instrumented-прогон перед релизом обязателен, а не «по желанию».

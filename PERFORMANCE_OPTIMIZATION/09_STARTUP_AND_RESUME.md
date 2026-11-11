# 09 — STARTUP & RESUME

**Baseline (эмулятор):** Cold **1046 мс**, Warm **646 мс**. В logcat при cold: `Skipped 36 frames`.
**Оценка: архитектурно продумано, но два крупных ускорителя не подключены (Baseline Profile и R8).**

## 9.1 Путь холодного старта

```
Application.onCreate (нет кастомного App — системы)
  └─ MainActivity.onCreate
       ├─ LocalePreference.get / wrap
       ├─ WindowCompat.setDecorFitsSystemWindows(false) + hide systemBars
       ├─ onBackPressedDispatcher callback (не выходить из Home)
       ├─ if (!SetupProgress.done) → SetupActivity          // только первый запуск
       ├─ StartupPreferences.justBooted → onBoot
       ├─ registerReceiver(SCREEN_ON)
       └─ setContent { OVisionTheme { LauncherRoot(...) } }
            └─ LauncherRoot composition
                 ├─ remember AppRepository / ProfileStore
                 ├─ LaunchedEffect(orderStore) { reload() }      // ← AppRepository.load()
                 ├─ LaunchedEffect(foreground, saverItems, saverDelay) { warmStill }   // IO
                 ├─ LaunchedEffect(foreground, saverDelay) { delay(2000); saverPlayers } // 2×ExoPlayer
                 └─ HomeScreen composition
                      ├─ LaunchedEffect ... focus request (delay 60)
                      ├─ LaunchedEffect(shelfApp…) { WatchNextSource.load }  // IO
                      └─ grid: for all items → AppCard  (полный список)
```

### Что происходит на главном потоке при старте
- `reload()` идёт в `scope.launch` (rememberCoroutineScope) → `repository.load()` внутри `withContext(IO)` ✅. Но **результат применяется в state** → первая композиция грида ждёт.
- Иконки/баннеры декодируются в IO, но их **применение** (`apps = …`) и последующая композиция всех плиток — на UI.
- `Skipped 36 frames` — именно затраты первой композиции + декод.

## 9.2 Тёплый старт (из фона)

- `onResume` → `foreground=true`, `resumeTick++`.
- `HomeScreen` `LaunchedEffect(resumeTick)` — обратный зум запуска (T2), перечитывание.
- Полка/погода перечитываются по `resumeTick`.
- 646 мс warm — это уже в основном перекомпоновка + декод изменившегося.

## 9.3 Что сделано правильно (важно сохранить)

1. **Главный экран становится интерактивным до завершения фоновых задач.** `saverPlayers` создаются с `delay(2_000)` — «let Home finish its first frame before the decoders take the thread». Это ровно §11 «не заставляй ждать необязательных операций».
2. **Заставка прогревается, а не ждёт.** `warmStill` (still bitmap) + `saverPlayers` заранее.
3. **Возврат из приложения мгновенный.** T2: `launchProgress.snapTo(1f)` затем `animateTo(0f)` — грид уже на конечном offset, нет двойного хода.
4. **Не перезагружает список без необходимости.** `LaunchedEffect(resumeTick)` в `LauncherRoot`: `if (apps == null || orderStore.savedOrder() == savedOrder) return` — возврат не пересобирает грид, если настройки не менялись ✅.
5. **Package-broadcast de-dup.** `reloadsHomeFor` игнорирует `PACKAGE_REMOVED` с `EXTRA_REPLACING` (обновление = один reload, не два) ✅.
6. **`configChanges` покрывает** uiMode/density/screenSize → смена конфигурации не пересоздаёт Activity.

## 9.4 Проблемы / ускорители

### P-START-1 — нет Baseline Profile (Высокий)

- В `app/build.gradle.kts` **нет** `androidx.profileinstaller`; в `src` нет `baseline-prof.txt`; в sdk — только скомпилированный `baselineProfiles` в build-выходе (следствие defaults, но без источника).
- Logcat: `ProfileInstaller: Skipping profile installation for com.whatuneed.ovisiontv.kotlin`.
- **Влияние:** первые запуски JIT-компилируют Compose/media3 на ходу → «Skipped 36 frames» и холодный старт 1046 мс частично из-за этого.

**Кандидат фикса (STAGE 8):**
1. Добавить `androidx.profileinstaller:profileinstaller`.
2. Сгенерировать Baseline Profile (ручной `baseline-prof.txt` покрывающий старт Home + переход в Control Center + заставку) или через `macrobenchmark`.
3. Проверить, что `Skipped frames` на старте падает.

### P-START-2 — R8/шринк выключен (Средний)

```kotlin
release { isMinifyEnabled = false }
```
- `minifyEnabled=false` → нет удаления неиспользуемого кода, нет оптимизации, APK 24 МБ, классы грузятся «как есть».
- **Риск включения:** Compose/media3 требуют аккуратных `proguard-rules`. Включение R8 может сломать рефлексию (media3, Compose). Поэтому — **только после тестов, с `proguard-rules.pro` и полным регрессом**. Не «в один шаг».

**Кандидат:** оценить `isMinifyEnabled=true` + `shrinkResources=true` на **dev-ветке**, прогнать 388 тестов + ручной обход. Если зелёно — ускорение холодного старта и -APK.

### P-START-3 — грид композитит все плитки на первом кадре (Средний)

- Следствие `P-GPU-4` (неленивый грид). Первый кадр Home строит **все** плитки.
- Кандидат: skeleton/placeholder и перенос декода арта «по мере видимости»; либо ленивый грид (осторожно — фокус).

### P-START-4 — `executePendingBindings`/первый layout (Низкий)

- Первый фокус-запрос через `delay(60)`; второй через `delay(350)`. На старте это скрыто, но добавляет «слепое окно».

## 9.5 App Resume (возврат из стороннего приложения)

| Требование §11 | Статус |
|---|---|
| Обновление изменившихся данных | ✅ `resumeTick` → перечитать профиль/порядок/погоду/полку |
| Сохранение позиции прокрутки | ✅ `scroll` Animatable сохраняется; `rememberSaveable` |
| Сохранение выбранного элемента | ✅ `lastFocusKey` |
| Мгновенный возврат | ✅ T2 reverse без двойного хода |

## 9.6 Вердикт

| # | Проблема | Приоритет | Правка |
|---|---|---|---|
| P-START-1 | нет Baseline Profile | **Высокий** | profileinstaller + baseline-prof |
| P-START-2 | R8 выключен | Средний | включить с тестами (dev-ветка) |
| P-START-3 | первый кадр строит все плитки | Средний | skeleton / ленивость |
| P-START-4 | слепое окно фокуса | Низкий | реактивный фокус |

Целевые ориентиры §16 (cold start) снимать **только на реальном ТВ**, по 5 прогонов.

## 9.7 Итог STAGE 8 (startup, первый кадр, R8) — 09–10.11.2026

Методика: headless-эмулятор `hume-tv` (API 34, user build, SwiftShader
`ro.hardware.egl=emulation`, 1920×1080), release-сборка vc53. Холодный старт:
лаунчер временно перестаёт быть домашним (`cmd package set-home-activity
com.android.tv.settings/.system.FallbackHome`) → `am force-stop` → `am start -W`
+ строка `ActivityTaskManager: Displayed`. После замеров домашний экран возвращён
на `com.whatuneed.ovisiontv.kotlin/.MainActivity` (проверено `resolve-activity`).
Тёплый — `HOME` (FallbackHome) на живом процессе → `am start -W`. Всё ниже —
эмулятор; реальный ТВ **UNKNOWN**.

### P-START-1 — Baseline Profile: установка подтверждена, выигрыш не измерен

Сделано:

- `app/build.gradle.kts`: `implementation("androidx.profileinstaller:profileinstaller:1.4.1")`
  — без установщика слитый AGP-профиль библиотек не доезжает до устройства.
- APK содержит `assets/dexopt/baseline.prof` и `baseline.profm` (проверено
  распаковкой); logcat первого запуска: `ProfileInstaller: Installing profile`.
- `cmd dexopt` после `bg-dexopt-job`: `[status=speed-profile] [reason=bg-dexopt]`
  (до установщика профиля установка оставалась в `verify`).
- Слитый профиль = **2532 строки правил библиотек** (Compose, media3, AndroidX).
  Правил самого приложения нет: отдельного `baseline-prof.txt` и модуля
  `macrobenchmark` в проекте нет, а эмулятор — `user` build (снять профиль
  на устройстве нельзя).

Замеры холодного старта (5 прогонов, медиана):

| Состояние сборки | Прогоны, мс | Медиана |
|---|---|---|
| до профиля/R8 (baseline §9.1) | 352/405/344/346/370 | 352 |
| только профиль библиотек | 405/360/405/420/422 | 405 |
| профиль + R8 (+после dexopt) | 447/425/419/424/446, 437/350/346/345/407 | 419–425 |

Вывод: **выигрыша по холодному старту на эмуляторе не зафиксировано** —
разброс перекрывает разницу (345–447 против 344–405), разница медиан 40–70 мс
на границе шума. Что действительно изменилось и проверено: профиль ставится,
`dexopt` доходит до `speed-profile`. Эффект профиля проявится на реальном
устройстве, где JIT-разогрев дороже; на ТВ — **UNKNOWN**.

### P-START-2 — R8 + shrink включены

```kotlin
release {
    isMinifyEnabled = true
    isShrinkResources = true
    signingConfig = signingConfigs.findByName("release")
}
```

- APK: **25,48 МБ → 3,94 МБ** (3 944 042 байта, −84,6 %); те же минифай и
  ресурсы проходят `assembleRelease` + `testDebugUnitTest` (BUILD SUCCESSFUL).
- Смоук на установленной R8-сборке (скриншоты `screenshots/stage8_r8_*.png`):
  Главная с погодой и доком; «Настройки OVisionTV» (корень и страницы
  «Главный экран», «Сеть», «Заставка» — превью-кадр, «5 мин»); Control Center
  (Wi-Fi/Ethernet, экран 1920×1080, звук, таймер сна, ряд иконок); поиск с
  результатами по запросу «google» (Google Play / Google Play Games).
- Плитка-шестерёнка в доке — **системные** «Настройки» (`com.android.tv.settings`)
  — это содержимое дока, а не регрессия: своя плитка подписана
  «Настройки OVisionTV» (`settings_tile_name`). Запуск нашей
  `SettingsActivity` напрямую — корректный.
- Буфер падений `logcat -b crash` пуст за весь смоук.
- Дополнительный плюс помимо размера: R8 убирает мёртвый код из dex, что
  снижает страницы, читаемые при загрузке классов. Прямой замер старта этого
  не показал (см. таблицу выше) — заявляем только размер и отсутствие регрессий.

### P-START-3 — первый кадр ограничен рендер-потоком (SwiftShader), не данными

`dumpsys gfxinfo com.whatuneed.ovisiontv.kotlin framestats` (файл
`screenshots/stage8_framestats.txt`):

- первый кадр: main draw ≈116 мс, GPU ≈114 мс — сам по себе не предел;
- далее кадр, где между `DrawStart` и `IssueDrawCommandsStart` ≈**1040 мс**:
  рендер-поток собирает/растеризует display list всей сетки (24 плитки, арт),
  суммарно ≈1,2 с;
- загрузка данных уже в `Dispatchers.IO` (`AppRepository.load`), первый кадр
  не ждёт диска.

После R8 на холодном запуске: 8 кадров, 6 «janky», 90-й перцентиль 1200 мс,
`Number Slow issue draw commands: 5` — та же картина, что до R8. Причина —
программный GLES эмулятора. На реальном ТВ с аппаратным GPU стоимость
растеризации других порядков; сколько именно — **UNKNOWN**, замер на ТВ нужен.

Безопасной правки в рамках STAGE 8 нет: неленивый грид — `P-GPU-4` (FRAGILE,
не трогать без полного прогона фокуса), skeleton отложен (§9.4 P-START-3).
Решение фиксирую в STAGE 10 (адаптивная политика/ленивость — только с
проверкой фокуса).

### Числа после R8 (эмулятор, MEASURED)

| Метрика | Значение |
|---|---|
| Холодный старт, 10 прогонов | 345–447 мс, медиана ≈420 мс |
| Тёплый возврат, 3 прогона | 93 / 108 / 75 мс |
| Первые кадры после холодного запуска | 8 кадров, 6 janky, p90 1200 мс (SwiftShader) |
| APK | 3 944 042 байта |
| dexopt | `speed-profile` (bg-dexopt) x86 |
| Падения | нет (`-b crash` пуст) |

# 07 — MEMORY OPTIMIZATION

**Baseline (эмулятор):** TOTAL PSS ≈ **86,5 МБ**, RSS ≈ 187 МБ, Native Heap ≈ 33 МБ, GPU ≈ 31,8 МБ.
**Оценка: структура разумная, но нет управления под давлением и есть «тихое» пере-удержание bitmap.**

## 7.1 Где живёт память

| Источник | Оценка | Доказательство |
|---|---:|---|
| Bitmap-кэши (posters+previews) | до ~54 МБ | `AerialPosterStore 32` + `SaverPreviewStore 6+16` |
| «Прогретая» стопка заставки | 2× ExoPlayer | `LauncherRoot` `saverPlayers` создаются заранее |
| Иконки/баннеры приложений | зависит от числа приложений | `AppRepository` ARGB_8888, без кэша |
| Постеры полки | до 12 × RGB_565 | `TopShelf` `arts` |
| Compose runtime + media3 | базово | APK 24 МБ |

## 7.2 Что сделано правильно

1. **Освобождение постера заставки.** `SaverPosterStore` / `TopShelf` используют `LruCache` с `sizeOf = byteCount` — эвикция по реальным байтам, не по числу.
2. **Постельное «гашение» композиции.** `homeBehindSaver`: под заставкой Home-слой переводится в `alpha=0`, чтобы Compose не растеризовал грид каждый кадр видео. Состояние сохраняется → возврат мгновенный.
3. **Ограничение живых постеров полки.** `arts.size > 12` → удаление неактуальных.
4. **Отказ от тяжёлого.** Карусель-герой Java (печёные 1920×1080 ARGB ≈ 8 МБ/кадр, 4K ≈ 33 МБ) **не перенесён** — это самое крупное улучшение RAM относительно Java.
5. **Заставка — дисковый LRU + memory LRU**, а не безлимит.

## 7.3 Проблемы

### P-MEM-1 — нет реакции на давление памяти (Высокий)

Поиск по проекту: **`onTrimMemory` / `onLowMemory` не вызываются нигде.**
```bash
grep -r "onTrimMemory\|onLowMemory\|ComponentCallbacks2" → 0 совпадений
```
- При нехватке RAM система шлёт `onTrimMemory(TRIM_MEMORY_*)`, но лаунчер никак не освобождает кэши.
- `AerialPosterStore 32 МБ` + `SaverPreviewStore 22 МБ` остаются занятыми даже когда Home — не заставка.

**Риск:** на 1 ГБ боксе лаунчер держит bitmap-пул, который система может убить целиком → перезапуск процесса → потеря плавности.

**Кандидат фикса (STAGE 3/7):**
```kotlin
// MainActivity: ComponentCallbacks2 либо override onTrimMemory
override fun onTrimMemory(level: Int) {
    if (level >= TRIM_MEMORY_UI_HIDDEN) {
        AerialPosterStore.trimMemory(...)     // soft
        SaverPreviewStore.trimMemory(...)
        // при TRIM_MEMORY_RUNNING_LOW/CRITICAL — evictAll
    }
}
```
Разделить «лаунчер в фоне» (можно вычистить много) и «лаунчер на экране» (мягко).

### P-MEM-2 — 2 декодера ExoPlayer «греются» в простое (Средний)

`LauncherRoot`:
```kotlin
LaunchedEffect(foreground, saverDelay) {
    if (!foreground || saverDelay == SaverDelay.NEVER || saverPlayers != null) return
    delay(2_000)
    saverPlayers = createSaverPlayers(context)   // 2× ExoPlayer
}
```
- Два ExoPlayer создаются через 2 с после входа в Home и живут весь сеанс (освобождаются только в `onDispose` процесса).
- Это ускоряет вход в заставку, но занимает память и CPU **всё время**, даже если заставка выключена в настройках. Хотя гейт `saverDelay == NEVER` есть — если заставка «никогда», декодеры не создаются. Но при `SaverDelay` ≠ NEVER они висят.

**Кандидат фикса:** создавать пул **ленивее** (например, по первому простою > 60 с, или по приближению таймера), или освобождать при `TRIM_MEMORY_*` и пересоздавать к моменту показа.

### P-MEM-3 — bitmap-кэши не согласованы и без лимита по RAM устройства (Средний)

Четыре независимых LRU (см. `06` §6.4) без единой политики. `ActivityManager.isLowRamDevice` читается в `DeviceFacts` (для отображения), но **не влияет** на бюджеты кэшей.

**Кандидат фикса:** фабрика бюджетов: при `isLowRamDevice` (или `memoryClass` мал) — уменьшить `AerialPosterStore` до 12–16 МБ, preview — вдвое.

### P-MEM-4 — удержание Activity/Context (проверено)

- `AppRepository(context)` создаётся в `LaunchedEffect`/`remember` и держит `Context`; в `AppRepository` обращения идут к `packageManager`, `resources` — если это Activity-context и он переживает Activity, будет утечка. **Проверить:** `remember(context) { AppRepository(context) }` живёт, пока живёт композиция Activity → при `recreate()` (смена языка!) пересоздаётся. Класс «утечки» — риск низкий, но `applicationContext` был бы безопаснее в долгоживущих кэшах.
- `AerialCache`, `SaverPreviewStore`, `AerialPosterStore` — `object` (singleton), используют `context.applicationContext` ✅.
- Найденная потенциальная утечка: `shelfArtCache` (companion `object` внутри файла) держит `ImageBitmap` бессрочно — если кэш не чистится, а приложение работает недели (ТВ не перезагружается), это медленный рост. Ограничен 8 записями → приемлемо.

## 7.4 Вердикт

| # | Проблема | Приоритет | Правка |
|---|---|---|---|
| P-MEM-1 | нет `onTrimMemory` | **Высокий** | добавить эвикцию по давлению |
| P-MEM-2 | 2 декодера в простое | Средний | ленивее создавать/освобождать |
| P-MEM-3 | кэши без лимита по RAM | Средний | бюджеты по `isLowRamDevice` |
| P-MEM-4 | context в репозитории | Низкий | использовать `applicationContext` |

**Оговорка:** PSS 86,5 МБ — на эмуляторе. На реальном ТВ снять `dumpsys meminfo` и `meminfo` под нагрузкой (`gfxinfo`+`procstats`), особенно при переключении на заставку и обратно.

## 7.5 Итог STAGE 7

### P-MEM-2 — декодеры заставки отпускаются в фоне (ИСПРАВЛЕНО, ПРОВЕРЕНО)

Правка в `LauncherRoot`: эффект завязан на `foreground, saverVisible, saverDelay`.

```kotlin
LaunchedEffect(foreground, saverVisible, saverDelay) {
    if (!foreground) {
        if (!saverVisible) {
            saverPlayers?.forEach(ExoPlayer::release)
            saverPlayers = null
        }
        return@LaunchedEffect
    }
    if (saverDelay == SaverDelay.NEVER || saverPlayers != null) return@LaunchedEffect
    delay(2_000) // Home рисует первый кадр без борьбы за поток
    saverPlayers = createSaverPlayers(context)
}
```

Видимая заставка — единственный случай, когда плееры остаются в фоне: их нельзя освобождать под работающим видео. Возврат в Home создаёт пару заново через 2 с; если заставка успела понадобиться раньше, `SaverScreen` строит свой пул (`ownedPlayers`) — путь уже существовал.

**Доказательство (эмулятор hume-tv, API 34, release):** `/proc/<pid>/task/*/comm`

| Состояние | `ExoPlayer:Playback` | `ExoPlayer:Frame` |
|---|---:|---:|
| Home на экране | 2 | 1 |
| Settings на экране, +10 с | 0 | 1 |
| Home, +1 с после возврата | 0 | 1 |
| Home, +5 с после возврата | 2 | 1 |

`ExoPlayer:Frame` — общий поток обработки кадров media3 1.4.1; он создаётся один раз на процесс и после `release()` не убирается (MEASURED: остаётся при нуле `Playback`). Это стоимость библиотеки, а не утечка: один поток без работы на простое ничего не декодирует.

### P-MEM-4 — repository на application context (ИСПРАВЛЕНО)

```kotlin
val repository = remember(context) { AppRepository(context.applicationContext) }
```

Репозиторий живёт дольше одной Activity (и переживает `recreate()` при смене языка), поэтому Activity-context в нём недопустим. Остальные долгоживущие кэши (`AerialPosterStore`, `SaverPreviewStore`, `AerialCache`) уже были `object` на `applicationContext`.

**Оговорка (регрессия 0.4.34, исправлена):** язык лаунчера — отдельная настройка, которая применяется обёрткой Activity (`LocalePreference.wrap`), а `applicationContext` остаётся на системной локали. После перехода на `applicationContext` собственный ярлык «Настройки» (`settings_tile_name`) и `Collator` для порядка плиток читались из системной локали. Лечение — внутри конструктора репозитория:

```kotlin
private val context: Context = LocalePreference.wrap(context)
```

`applicationContext` при этом сохраняется как основа (утечки нет), но ресурсы и `locales[0]` берутся уже с языком лаунчера. Проверка: `LanguageReturnTest` падал до правки (RU-ярлык после переключения на EN), проходит 2/2 после; полный instrumented 178/178.

### P-MEM-3 — единые бюджеты кэшей (ОТЛОЖЕНО в STAGE 9)

Осознанно: бюджеты должны зависеть от профиля устройства (`isLowRamDevice`, `memoryClass`), а профили — предмет STAGE 9 (`P-ADAPT-1/2`). Делать отдельную фабрику бюджетов сейчас — значит потом переписывать её под `AdaptivePolicy`. До STAGE 9 действует реактивная защита STAGE 3: `onTrimMemory` режет кэши по уровню давления.

### Замеры памяти

| Момент | TOTAL PSS |
|---|---:|
| Home до правок (STAGE 3 baseline) | 100 093 КБ |
| Home после правок | 75 838 КБ |
| Home в простое +10 с | 73 852 КБ |

**Оговорка:** падение включает эффект STAGE 3 (`TRIM_MEMORY_UI_HIDDEN` вычищает битмап-кэши при уходе в фон), поэтому цифру нельзя приписывать только освобождению плееров. Вклад плееров доказан не PSS, а потоками: `Playback`-потоков в фоне ноль. Абсолютные значения — эмулятор; реальный ТВ UNKNOWN (Q1).

### Вердикт STAGE 7

| # | Проблема | Статус |
|---|---|---|
| P-MEM-1 | нет `onTrimMemory` | исправлен (STAGE 3) |
| P-MEM-2 | 2 декодера в простое | **исправлен, проверен** |
| P-MEM-3 | кэши без лимита по RAM | отложено в STAGE 9 (поглощено `P-ADAPT-1/2`) |
| P-MEM-4 | context в репозитории | **исправлен** |

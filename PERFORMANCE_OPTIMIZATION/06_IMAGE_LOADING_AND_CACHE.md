# 06 — IMAGE LOADING & CACHE

**Оценка: хорошая база, есть несогласованности.** Загрузка асинхронная, сжатие постеров грамотное, но кэши разрознены и иконки приложений не кэшируются.

## 6.1 Асинхронность загрузки

| Контент | Место | Дисpatcher |
|---|---|---|
| Иконки/баннеры приложений | `AppRepository.load()` → `withContext(Dispatchers.IO)` | ✅ IO |
| Постеры полки | `TopShelf.rememberShelfArts` → `withContext(Dispatchers.IO)` | ✅ IO |
| Превью Aerial | `SettingsScreen` → `withContext(Dispatchers.IO)` | ✅ IO |
| Постеры заставки | `AerialPosterStore.save` → `withContext(Dispatchers.IO)` | ✅ IO |
| Кадр заставки (bitmap) | `SaverScreen.capturePoster` → `withContext(Dispatchers.IO)` | ✅ IO |

**Ни одного декода на главном потоке не найдено.** Это соответствует §8.

## 6.2 Сжатие и конфиги (правильно)

### Полка
`TopShelf.loadShelfArt`:
```kotlin
const val ART_MAX_PX = 1280
// 1) inJustDecodeBounds → расчёт inSampleSize
// 2) inPreferredConfig = RGB_565     ← в 2 раза меньше, чем ARGB_8888
BitmapFactory.decodeStream(again, null, options)
```
- Декод **один раз** в ~экранном размере, `RGB_565`, не масштабируется на render-thread.
- Комментарий прямо обосновывает: *«art is decoded once at screen size, which is what the Java launcher did with its baked hero frames»*.

### Заставка
`AerialPosterStore`:
```kotlin
private const val JPEG_QUALITY = 88
private const val MAX_WIDTH = 1920
private const val MAX_HEIGHT = 1080
private val memory = object : LruCache<String, Bitmap>(32 * 1024 * 1024) { sizeOf = byteCount }
```
- Дисковый LRU (16 файлов) + memory LRU 32 МБ, время-штамп prune, SHA-256 имя файла, `isBlack` отсекает чёрные лид-ины.

### Превью Aerial
`SaverPreviewStore` (`SaverPreviewImage.kt`):
```kotlin
private const val MEMORY_BYTES = 6 * 1024 * 1024      // полные превью
private const val THUMB_MEMORY_BYTES = 16 * 1024 * 1024 // миниатюры
```

## 6.3 Проблемы

### P-IMG-1 — иконки/баннеры декодируются в ARGB_8888 без переиспользования (Средний, RAM)

`AppRepository.drawableToImage`:
```kotlin
val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
```
- **ARGB_8888** для иконок 256×256 (≈ 256 КБ каждая) и баннеров 400×240 (≈ 384 КБ).
- Нет общего `LruCache<packageName, ImageBitmap>`: при каждом package-broadcast (`PACKAGE_ADDED/CHANGED/REPLACED`) `AppRepository.load()` **заново декодирует все** иконки/баннеры.
- Приложение с 40 приложениями → каждый reload = 40 декодов Drawable→Bitmap→Canvas.

**Влияние:** всплески allocation на каждый reload; часть Native/GPU-памяти.

**Кандидат фикса (STAGE 6/7):**
1. Кэш `LruCache<String, ImageBitmap>` по `packageName + versionCode`.
2. Иконки — `RGB_565` там, где нет альфы (или `HARDWARE`-bitmap на API ≥26, что отдаёт память GPU, но ускоряет draw).
3. Инвалидация только изменённого package, не всех.

### P-IMG-2 — `shelfArtCache` (8) < живых `arts` (12) (Средний, UX)

`TopShelf.kt`:
```kotlin
private val shelfArtCache = object : LruCache<String, ImageBitmap>(8) {}
...
if (arts.size > 12) { arts.keys.filterNot { it in current }… }
```
- В `arts` держится до **12** постеров, а LruCache хранит **8**.
- При листании карусели 9-й постер вытесняет 1-й из LruCache, но `arts` всё ещё ссылается → следующий шаг назад снова декодирует.

**Кандидат фикса:** согласовать размеры (LruCache ≥ 12, или ограничить `arts` до 8).

### P-IMG-3 — placeholder без фиксированных размеров? (проверено — ок)

Плитка — фиксированный `Box(ICON_WIDTH×ICON_HEIGHT)`; «скачков размеров» нет: арт всегда фитится внутрь. §8 «Placeholder без скачков» — соблюдено.

### P-IMG-4 — `FilterQuality` на плитках неконсистентен (Низкий)

`AppCard`: иконка `FilterQuality.High`, баннер `Medium`; `SaverScreen` — `High`. `High` включает бикубическую фильтрацию → дороже для GPU. Для уменьшенного арта на ТВ достаточно `Low/Medium`. Низкий приоритет, но при GPU-борьбе — кандидат.

## 6.4 Сводка кэшей (разрознены)

| Кэш | Размер | Тип |
|---|---:|---|
| `AerialCache` (disk) | 512 МБ | media3 SimpleCache |
| `AerialPosterStore.memory` | 32 МБ | Bitmap LRU |
| `SaverPreviewStore.memory` | 6 МБ | Bitmap LRU |
| `SaverPreviewStore.thumbMemory` | 16 МБ | Bitmap LRU |
| `shelfArtCache` | 8 записей | ImageBitmap LRU |
| Иконки/баннеры | 0 | ❌ нет |

**Общий верхний предел bitmap-кэшей: 54 МБ + полка + (нет) иконок.** Промт §9/§10 требует «разумной политики кэша с учётом объёма RAM». Сейчас — четыре независимых LRU **без единого менеджера и без реакции на давление памяти**.

## 6.5 Вердикт

| # | Проблема | Приоритет | Правка |
|---|---|---|---|
| P-IMG-1 | декод ARGB_8888 без кэша, полный ре-декод | Средний | кэш по package+version, RGB_565/HARDWARE |
| P-IMG-2 | shelfArtCache 8 < arts 12 | Средний | согласовать |
| P-IMG-4 | FilterQuality.High где не нужно | Низкий | Low/Medium |
| — | placeholder-скачки | ок | — |
| — | декод на UI-потоке | ок | — |

См. также `07_MEMORY_OPTIMIZATION.md` — там предложен единый менеджер.

## 6.6 Итог STAGE 6

| # | Что сделано | Доказательство |
|---|---|---|
| P-IMG-1 | Добавлен `AppArtCache` — `LruCache<String, ImageBitmap>` на 12 МБ, `sizeOf` по `allocationByteCount` | Ключ `${pkg}|${lastUpdateTime}|banner|400x240` или `…|icon|256x256`: обновление приложения меняет `lastUpdateTime` и арт перечитывается, остальные попадают в кэш. `PackageInfo` заменил `getApplicationInfo` в `readApp` — тот же один IPC, но с версией пакета. Сброс — `AppArtCache.trim()` из `MemoryPressure` при уходе в фон. Повторный broadcast/возврат из настроек больше не декодирует все 40 иконок |
| — | `RGB_565` / `HARDWARE` для иконок **отклонены** | Баннеры и иконки приложений часто с прозрачностью и градиентами (RGB_565 даёт грязь на альфе), а HARDWARE-bitmap нельзя рисовать в software `Canvas` и повторно использовать при пересборке — бюджет 12 МБ это переживает |
| P-IMG-2 | `shelfArtCache` 8 → 12 записей (`SHELF_ART_CACHE_ENTRIES`) | Совпадает с лимитом `arts.size > 12`; листание длинного ряда больше не выкидывает постер, который ещё на экране |
| P-IMG-4 | `AppCard` и `SearchOverlay`: иконка `FilterQuality.High` → `Medium` | Иконка уже растеризована в ≈256 px и рисуется около 1:1; `SaverScreen` оставлен на `High` — там полноэкранный кадр, качество важнее |

**Проверка на эмуляторе:** после установки релиза иконки дока и сетки, баннеры и постеры рисуются как прежде; после `am send-trim-memory … RUNNING_CRITICAL` и `UI_HIDDEN` Home перерисовывается без пустых плиток (кэш иконок восстанавливается из APK, скрим — из градиентов).

**Оговорка.** Число попаданий в кэш на эмуляторе не замерялось (нет счётчиков); эффект доказан структурно — при повторной загрузке `drawableToImage` не вызывается. Потребление кэша (12 МБ верхняя граница) складывается с остальными кэшами; единый бюджет — STAGE 7 (`P-MEM-3`).

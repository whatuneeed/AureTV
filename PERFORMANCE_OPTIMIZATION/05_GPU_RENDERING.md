# 05 — GPU & RENDERING

**Оценка: смешанная.** Крупные эффекты (blur) осознанно убраны, но остались **систематические overdraw** и дорогая тень плитки.

## 5.1 Что уже оптимизировано правильно (не трогать)

| Решение | Доказательство в коде |
|---|---|
| **Нет GPU-blur / RenderEffect** | `TopShelf.kt:98`: *«RenderEffect on a full-screen surface measured ~20 fps on the TV image»*; `HomeScreen.kt:1677` — библиотечный фон без blur |
| **Нет `Modifier.blur`** | `CardShell.kt:63`: *«Modifier.blur needs API 31 and a GPU layer per tile»* → тень рисуется вручную (minSdk 28) |
| **Декод постера не в render-thread** | `TopShelf.kt:73`: *«must never be decoded, blurred or scaled on the render thread»* |
| **Единственный `Modifier.shadow`** | только `ShelfDots` (`TopShelf.kt:360`) — 6 малых точек |

## 5.2 Overdraw — главная GPU-проблема

### P-GPU-1 — двойной полноэкранный фон (Высокий)

Два full-screen `verticalGradient` рисуются один поверх другого:

1. `LauncherRoot.kt` (~356): внешний `Box.fillMaxSize().background(verticalGradient(backgroundTop…bottom))`
2. `HomeScreen.kt` (~910): внутренний `Box.fillMaxSize().background(verticalGradient(backgroundTop…bottom))` — **тот же градиент**

Плюс если `visible == null` (до загрузки) / `isEmpty()` — рисуется `EmptyState` поверх того же.

**Влияние:** один лишний полноэкранный проход на кадр постоянно. На 1 ГБ боксе с software-композитингом это прямой минус к fill-rate.

**Кандидат фикса (STAGE 6):** оставить фон в одном месте (в `LauncherRoot`), у `HomeScreen` убрать дубль.

### P-GPU-2 — три полноэкранных градиента полки каждый кадр (Высокий при открытой полке)

`TopShelf.kt` (~156) при активной полке рисует **3 full-screen `drawRect`** в `drawBehind`:
```kotlin
drawRect(brush = horizontalGradient(...))   // скрим слева
drawRect(brush = radialGradient(...))       // виньетка в углу
drawRect(brush = verticalGradient(...))     // затемнение снизу
```
Плюс `Image` с баннером-подложкой и `Image` с постером.

**Влияние:** до 5 полноэкранных слоёв на кадр при открытой полке. На ТВ среднего класса — риск падения fill-rate.

**Кандидат фикса:** слить три градиента в один `drawBehind`-проход/один Brush, либо запечь в один предкомпозированный градиент.

### P-GPU-3 — тень плитки: 16 стекущихся `drawRoundRect` (Средний)

`CardShell.kt`:
```kotlin
private const val SHADOW_LAYER_ALPHA = 0.05f
private const val SHADOW_STEPS = 16
private fun DrawScope.drawSoftShadow(feather: Float) {
    for (step in 1 until SHADOW_STEPS) {   // 15 итераций
        drawRoundRect(color = Black.copy(alpha = 0.05f), ..., cornerRadius = ...)
    }
}
```
- На каждую плитку → **15 draw-call** с alpha-blending (полупрозрачное наложение = fill-rate + blend).
- При ~24 плитках → **~360 draw-call только на тени**.

**Влияние:** это самый «дорогой» постоянный элемент плитки. Оправдание в комментарии: blur недоступен на minSdk 28 и стоил бы GPU-слоя на плитку. Компромисс разумный, но 16 слоёв — много.

**Кандидат фикса:** уменьшить `SHADOW_STEPS` (10–12) при сохранении визуальной плотности; либо один предзапечённый 9-patch/растровый ассет тени, повторно используемый.

### P-GPU-4 — грид не ленивый (Средний)

`HomeScreen.kt` (~1333):
```kotlin
for ((index, item) in items.withIndex()) {
    if (pickupActive && index == pickupOrigin) continue
    key(item.key) { … AppCard … }
}
```
Композитятся **все** плитки списка, независимо от видимости. При 40–60 установленных приложениях — 40–60 плиток × (тень 15 слоёв + градиент + иконка).

**Влияние:** линейный рост стоимости кадра с числом приложений; on mid-TC для больших списков.

**Оговорка:** полная замена на `LazyVerticalGrid` рискует уронить кастомную модель фокуса/переупорядочивания (FRAGILE-зона!). Нужны замеры: если на реальном ТВ с ≤30 приложениями кадры держатся — не трогать. Это правка «при доказанной необходимости».

### P-GPU-5 — полка: баннер + постер (Низкий)

`TopShelf.kt`: `bannerUnderlay` + `Image(art)` — два полноэкранных `Image`. Уже частично оптимизировано (`bannerUnderlay` не рисуется, если есть загруженный постер). Остаточный риск — кратковременно оба.

## 5.3 Клиппинг / слои / view-hierarchy

- Attached Views на Home: **87** (при idle) — для Compose это «внешние» AndroidView (PlayerView заставки) + host; сам Compose-контент в одном `ViewRootImpl`. Render nodes: 90 KB.
- `graphicsLayer` используется массово (63 вызова), но **по назначению**: dock shift, shelf hide, light alpha, launch overlay — то есть «анимация без recompose».

## 5.4 GPU-память (из gfxinfo)

| Ресурс | Размер |
|---|---:|
| Texture (Image) | 10,89 МБ |
| Scratch RenderTarget | 10,66 МБ |
| Scratch Texture | 9,99 МБ |
| **Всего GPU** | **31,77 МБ** |

Обсервация: ~11 МБ image-texture — это постеры/иконки; scratch ~20 МБ — промежуточные таргеты (типично для полноэкранных градиентов). Сокращение overdraw (§5.2) снизит и scratch.

## 5.5 Вердикт

| # | Проблема | Приоритет | Правка |
|---|---|---|---|
| P-GPU-1 | Двойной фон | **Высокий** | убрать дубль в HomeScreen |
| P-GPU-2 | 3 градиента полки/кадр | **Высокий** | слить в один проход |
| P-GPU-3 | 16 слоёв тени/плитку | Средний | уменьшить или ассет |
| P-GPU-4 | неленивый грид | Средний | только по замерам |
| P-GPU-5 | 2 Image полки | Низкий | уже частично решено |

**Все правки — только после замеров на ТВ.** Ни один пункт не меняет визуальное качество при аккуратной реализации.

## 5.6 Итог STAGE 6

| # | Что сделано | Доказательство |
|---|---|---|
| P-GPU-1 | Дубль полноэкранного градиента удалён из `HomeScreen`; фон остался один — в `LauncherRoot` | Оба градиента непрозрачные (`0xFF101014 → 0xFF0E0E11`), поэтому кадр не изменился; минус один full-screen проход |
| P-GPU-2 | Три `drawRect` со shader'ами заменены одним запечённым изображением полки | `TopShelf.shelfScrim`: один ARGB_8888 960×540 (≈2,0 МБ) на размер поверхности, кэш по `width×height`, сброс через `dropShelfScrim()` по `onTrimMemory`. 3 full-screen shader-прохода → 1 `drawImage` |
| P-GPU-3 | Тень плитки: 16 → 12 шагов | `SHADOW_STEPS=12`, alpha `0.0675`: 11 композитных слоёв дают те же 0,537 под центром, что прежние 15 × 0,05; 24 плитки — 264 `drawRoundRect` вместо 360 |
| P-GPU-4 | Не менялся | `LazyVerticalGrid` задел бы кастомный фокус/перестановку — статус FRAGILE, нужны замеры на ТВ |
| P-GPU-5 | Проверено — уже решено | `bannerUnderlay` не рисуется, когда готов постер; обе картинки одновременно только на время загрузки |

**Проверка на эмуляторе (hume-tv, API 34, release 0.4.33 + правки):**

- Визуально полка совпадает с прежней: нижняя строка кадра = ровно `#101012`, верхний левый угол уходит в тёмный композит; заголовок и текст читаются (скриншоты `screenshots/v48_shelf_0.4.34_stage6.png`, `v49_banner_0.4.34_stage6.png`).
- После `am send-trim-memory … RUNNING_CRITICAL` запечённый скрим сброшен и перестроен на следующем кадре, полка и иконки на месте — эвикция кэшей не оставляет пустых плиток.
- `dumpsys gfxinfo`: PSS 82,2 МБ (полка открыта) против baseline 86,5 МБ (idle 0.4.24) — **разные состояния**, только ориентир. GPU 37,03 МБ (Texture 18,75 / Scratch RT 16,01 / Scratch Tex 2,00) против 31,77 МБ baseline; +≈2 МБ из них — сам запечённый скрим, Scratch суммарно −2,6 МБ.
- Навигация D-pad по доку: 136 кадров, p50 34 мс, p90 53 мс, janky 36,8 %. В baseline этот сценарий дал 0 UI-кадров, поэтому «до/после» по кадрам для него нет.

**Оговорка.** Абсолютные числа — эмулятор (host GL), не ТВ. Выигрыш fill-rate от 3→1 прохода и 15→11 слоёв тени на реальном GPU **не измерен (UNKNOWN)**; на ТВ нужен повтор по `01 §7`.


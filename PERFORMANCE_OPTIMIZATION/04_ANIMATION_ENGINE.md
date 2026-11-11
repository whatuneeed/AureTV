# 04 — ANIMATION ENGINE

**Оценка: хорошая.** Длительности почти централизованы; физика согласована.

## 4.1 Единый источник параметров движения

`ui/TvMetrics.kt` — канон:

```kotlin
object TvOs {
    const val SCROLL_DURATION_MS = 300
    const val FOCUS_MS = 180            // M1; «tvOS UNKNOWN, project value»
    const val SURFACE_ENTER_MS = 240
    const val SURFACE_SLIDE = 26f
}
val SurfaceEasing = CubicBezierEasing(0.22f, 0f, 0.36f, 1f)

internal fun motionDuration(ms: Int, reduceMotion: Boolean): Int = if (reduceMotion) 0 else ms
```

Функция `motionDuration(ms, reduceMotion)` — единственная точка, где Reduce Motion обнуляет длительность. Это ровно то, что просит промт: «согласованная физика и временные характеристики».

## 4.2 Разделение анимаций по назначению (промт §6)

| Категория | Реализация | Параметры |
|---|---|---|
| **Micro** (нажатие, выбор) | `animateFloatAsState` в `CcTileView`, `CardShell` | `TvOs.FOCUS_MS=180` tween |
| **Focus** (получение/потеря, перемещение) | `iconFocusScale` + `IconEdgeLight`/`IconSpecular` (alpha слоя) | 180 мс, `FOCUS_SCALE = 306/250` |
| **Navigation** (экраны, папки, запуск) | `hideShift` 340pt, `libraryShift`, `LaunchSpec` | `FOLDER_ZOOM_MS=500`, `LAUNCH_MS=500`, `LAUNCH_START_DELAY_MS=380` |
| **Content** (постеры, полка) | `Crossfade` полки, `stillAlpha`, `zoom` | `ART_CROSSFADE_MS=350`, `SAVER_CROSSFADE_MS=1200` |
| **System** (CC, диалоги, меню, папки, поиск, заставка) | `ControlCenter` enter/exit, `TvMenu`, `FolderOverlay`, `PinPad`, `ProfilePicker` | `CC_ENTER_MS=240`, `CC_EXIT_MS=200`, `SURFACE_ENTER_MS=240` |

Разные типы → разные длительности: **требование «не одна длительность на всё» соблюдено.**

## 4.3 Что сделано уже правильно (не переусердствовать при правках)

1. **Градиенты записаны один раз, анимируется alpha слоя.**
   `IconFocusLight.kt`: *«The gradients are recorded once at full strength; the animation only changes this layer’s alpha»*. Это устраняет перерисовку записи градиента на кадр.
2. **`graphicsLayer`-чтение состояния вместо recompose.**
   `CardShell`, фокус-свет, dock band, полка — всё читает анимируемое значение **внутри** `graphicsLayer { }`, не в композиции.
3. **Джиггл плиток только в edit mode.**
   `CardShell`: `jigglePhase` включается лишь при `editing || jiggleAmount>0`, комментарий R7: «always-running transition would invalidate every tile’s layer at 60 fps while the grid is idle». Это важно — иначе 30+ бесконечных анимаций в простое.
4. **Launch zoom читается из `Animatable`, не из state Home.**
   `LaunchTransition`: *«reading it in the caller recomposed the whole grid once per frame, which is what made the zoom look stepped»*. То есть класс `progress: () -> Float` — осознанный perf-приём.
5. **Заставка гасит композицию под собой.**
   `homeBehindSaver` (alpha 0) + `snapshotFlow` для alpha слотов (не recompose на 60 fps).

## 4.4 Бесконечные анимации (риск)

Найдено 8 `rememberInfiniteTransition`/`infiniteRepeatable`. Все — **условные**:

| Место | Условие |
|---|---|
| `CardShell` jiggle | только `editing` (или settle) |
| `SaverScreen` zoom | только `stillOnScreen && !reduceMotion` |
| `PinPad`, `ProfilePicker`, `WeatherPanel`, `SearchOverlay` | по контексту появления |

**Обсервация:** ни одна бесконечная анимация не работает в простое на главном экране. Это хорошо.

## 4.5 Reduce Motion (§14)

- Глобально: `reduceMotion = !ValueAnimator.areAnimatorsEnabled()` — читается системный переключатель анимаций.
- `motionDuration(ms, true) = 0` → анимации «телепортом», но **состояние и hand-off сохраняются** (`LaunchTransition` в reduce-motion стартует приложение на первом кадре).
- Заставка: alpha-only путь (`SAVER_REDUCED_MS`).

Соответствует §14: «предусмотри упрощённый режим», «не делай анимации условием понимания состояния».

## 4.6 Риски

| # | Риск | Приоритет | Итог STAGE 5 |
|---|---|---|---|
| P-ANIM-1 | Числа `Reordering=200`, `EDIT_HOLD=350` и др. разнесены по файлам, а не в `TvOs` — при «унификации» легко рассинхронизировать | Низкий | **исправлен частично:** пять одинаковых констант входа поверхности (`CC_ENTER_MS`, `PICKER_ENTER_MS`, `SEARCH_ENTER_MS`, `WEATHER_ENTER_MS`, `PAGE_MS`/`CITY_ENTER_MS` мастера) удалены и заменены на `TvOs.SURFACE_ENTER_MS`; `SearchOverlay` и `WeatherCitySearch` теперь получают `reduceMotion` и гасят вход, как остальные поверхности. Локальными остались длительности, принадлежащие одному элементу (выходы, promote, навигация, контент) — это разные категории движения по §4.2, их слияние было бы ошибкой |
| P-ANIM-2 | `WeatherCapsule`/`StatusCapsule` используют `animateFloatAsState` даже когда скрыты (нет `if visible`) — проверить, не висит ли слой | Низкий | **проверен, не воспроизводится:** `WeatherCapsule` создаётся только при `weatherVisible`, `StatusCapsule` — только когда не открыты панель/меню/поиск/запуск; у обеих `animateFloatAsState` идут лишь при смене цели и останавливаются в settled-состоянии. Бесконечных анимаций в простое на Главной нет |

## 4.7 Вердикт

Хорошая, дисциплинированная система. Основной риск — **не архитектура, а число источников констант**; в STAGE 5 все общие входы поверхности сведены к `TvOs.SURFACE_ENTER_MS`, а специфичные для элемента длительности осознанно оставлены рядом с элементом. Открыто: «180 мс фокуса ощущаются мгновенно» — ощущение проверяется только на реальном ТВ (на эмуляторе подтверждается длительность, не восприятие).

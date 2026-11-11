# 03 — FOCUS ENGINE

**Оценка: сильная.** Единая индексная модель; независимых реализаций фокуса по экранам нет.

## 3.1 Архитектура фокуса

Главный экран — **одна модель на индексах**, а не набор конфликтующих систем:

- `focusIndex: Int` — текущая плитка грида (док = первая строка, `focusIndex < COLUMNS`).
- `shelfFocus: Int` — фокус полки (−1 = нет).
- `capsuleFocused`, `weatherFocused` — фокус на капсулах верхних углов.
- `TvOs.COLUMNS = 6`, шаг `ICON_COLUMN_PITCH = ICON_WIDTH + ICON_GAP = 298`.

Навигация `targetFor(dx, dy)` — чистая арифметика по индексам; `moveFocus` — единая точка.

### Отражение в Android-фокус (для TalkBack)

Каждая плитка получает `FocusRequester`:
```kotlin
val tileRequesters = remember(items) { mutableMapOf<String, FocusRequester>() }
...
key(item.key) { val tileRequester = remember(item.key) { FocusRequester() } ... }
```
Геометрией владеет «движок», а Android-фокус лишь **зеркалит** его:
```kotlin
val requester = items.getOrNull(focusIndex)?.let { tileRequesters[it.key] }
if (requester != null) requester.requestFocus() else focusRequester.requestFocus()
```
Это правильный паттерн: своя модель + реальные target’ы только для доступности.

## 3.2 Требования промта §5 — соответствие

| Требование | Статус | Обоснование в коде |
|---|---|---|
| Фокус не теряется при обновлении данных | ✅ | `LaunchedEffect(initialItems)` перестраивает, но `lastFocusKey` + `indexOfHomeFocus` восстанавливают позицию по package |
| Фокус не прыгает при появлении изображений | ✅ | Плитка — `Box` фиксированного размера `ICON_WIDTH×ICON_HEIGHT`; картинка внутри, скачков нет |
| Быстрый D-pad не запаздывает | ⚠️ | См. `02` §P-INPUT-1 (delay(350) на возврате) |
| Повторное нажатие не конфликтует анимаций | ✅ | `animate*AsState` идемпотентны; `holdJob` отменяется перед новым |
| Анимация не мешает следующему перемещению | ✅ | Фокус-анимации — 180 мс tween; движение индекса не ждёт анимации |
| Возврат из приложения восстанавливает состояние | ✅ | `resumeTick` → `LaunchedEffect(resumeTick)`; `focusTick`; `rememberSaveable(initialItems)` |

## 3.3 Сохранение и восстановление

- `focusIndex` — `rememberSaveable(initialItems)` + ключ package из prefs (`lastFocusKey`).
- `shelfIndex` — `rememberSaveable` + ключ package полки.
- `LaunchedEffect(focusIndex, …)` с дебаунсом `FOCUS_SAVE_MS=400` — **только покоящийся** курсор пишется в prefs (не каждый шаг серии).
- Восстановление в `indexOfHomeFocus` (по package), устойчиво к «сдвигу» удалённого приложения.

Правило «позиция не сохраняется, пока курсор движется» — прямо противостоит «скачкам интерфейса при загрузке».

## 3.4 Скролл и полка

- Скролл — `Animatable(scrollTargetFor(focusIndex))`, `SCROLL_DURATION_MS=300` tween `FastOutSlowInEasing`.
- Док/библиотека: `libraryMode = items.size > COLUMNS && focusIndex >= COLUMNS`; анимации `LIBRARY_SHIFT=674` и fade — две параллельные корутины в одном `coroutineScope`.
- `shelfHeld` — держит полку, если фокус на полке/капсуле «из полки», а не в библиотеке.

## 3.5 Риски

| # | Риск | Приоритет |
|---|---|---|
| P-FOCUS-1 | `delay(60)+delay(350)` при возврате фокуса (совместно с input) | Средний |
| P-FOCUS-2 | `targetFor` при неполном последнем ряду прыгает на `items.lastIndex` — на ТВ может ощущаться как «прыжок в конец строки» | Низкий (соответствует tvOS по комментарию) |
| P-FOCUS-3 | `moveFocus` при `pickupActive` мутирует `items` (reorder) → возможна лишняя перекомпоновка при драге | Низкий |

## 3.6 Вердикт

Фокус-движок соответствует архитектурным требованиям промта: **единый, индексный, с реальным Android-фокусом только для доступности**. Основной кандидат на правку — восстановление после панели (`P-INPUT-1`/`P-FOCUS-1`).

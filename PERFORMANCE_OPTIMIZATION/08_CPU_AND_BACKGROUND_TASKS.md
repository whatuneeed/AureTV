# 08 — CPU & BACKGROUND TASKS

**Оценка: аккуратная.** Все тяжёлые операции — в `Dispatchers.IO`; активность в простое низкая.

## 8.1 Диспетчеры и потоки

| Проверка | Результат |
|---|---|
| `withContext(Dispatchers.IO)` | 16 мест |
| `Dispatchers.Default` / `Dispatchers.Main` | 0 явных |
| `GlobalScope` | 0 ✅ |
| `runBlocking` | 0 ✅ |
| `Thread.sleep` | 0 ✅ |
| `Executors` / ручные пулы | 0 ✅ |
| `CoroutineScope(…)` вручную | нет; только `rememberCoroutineScope()` ✅ |

**Вывод:** единый подход к конкурентности через структурированный корутин-скоуп. Это ровно то, что просит §10.

## 8.2 Периодические задачи

| Задача | Период | Место | Оценка |
|---|---:|---|---|
| Idle-проверка заставки | **1000 мс** (до STAGE 7) | `LauncherRoot` `delay(SAVER_POLL_MS)` | заменено на дедлайн-таймер, §8.8 |
| Обновление погоды | **60 000 мс** (до STAGE 7) | `HomeScreen` `while(true){ …; delay(60_000) }` | заменено на расчётный срок, §8.8 |
| Часы капсулы | до ближайшей минуты | `ControlCenter.rememberClockText` | ✅ идеально (пробуждение на границе минуты, не секундный поллинг) |
| Смена постера полки | 10 000 мс | `HomeScreen` `SHELF_ADVANCE_MS` | ✅ tvOS-парадигма |
| Загрузка каталога Aerial | на resume, если due | `LauncherRoot` | ✅ не по расписанию в фоне |
| `stockHomes` кэш | 60 000 мс | `OVisionAccessibilityService` | ✅ |

Заставка: poll 1000 мс был осознанным (комментарий: *«The idle check is a cheap read of the elapsed clock and the music state»*), но в STAGE 7 заменён на ожидание до дедлайна — §8.8.

## 8.3 Сетевые запросы

| Задача | Поток | Оценка |
|---|---|---|
| Погода (`Weather.refresh`) | `Dispatchers.IO` | ✅ |
| Геокодинг города | `Dispatchers.IO` | ✅ |
| Обновление каталога Aerial | `Dispatchers.IO` | ✅ |
| Постеры полки | `Dispatchers.IO` | ✅ |

Все HTTP — на IO-потоке. `Weather` защищён `AtomicBoolean busy` (`compareAndSet`) — **нет параллельных одинаковых запросов**. Это буквально требование §10 «не запускай несколько одинаковых фоновых задач одновременно» ✅.

`Weather` также имеет timeouts (`CONNECT_MS=8000`, `READ_MS=10000`) и лимит ответа (`MAX_BYTES=2 МБ`) ✅.

## 8.4 Отмена задач

- `reloadJob?.cancel()` перед новым reload ✅ (устаревший каталог не перезапишет свежий).
- `holdJob?.cancel()` перед новыми таймерами hold ✅.
- `Weather.busy` — не отмена, но дедупликация ✅.
- `LaunchedEffect` — Compose отменяет при смене ключа ✅.

## 8.5 Проблемы

### P-CPU-1 — погода будит корутину каждые 60 с даже когда не нужна (Низкий)

`HomeScreen`:
```kotlin
LaunchedEffect(resumeTick) {
    while (true) {
        weatherEnabled = WeatherPreferences.enabled(context)
        if (weatherEnabled) { Weather.refresh(context, false); weatherData = … }
        else { weatherData = null }
        delay(60_000L)
    }
}
```
- В простое — просыпание раз в минуту и чтение SharedPreferences. `Weather.refresh` внутри сам гейтит по `stale()`, так что сеть дергается редко ✅.
- Само пробуждение 1/мин — дёшево, но не идеально: можно перейти на «запланировать одно срабатывание на следующий срок stale» вместо вечного цикла.

### P-CPU-2 — опрос заставки 1000 мс (Низкий)

Каждую секунду — `SystemClock.elapsedRealtime()` + `audioManager.isMusicActive`. На ТВ это сотни вызовов в час. Приемлемо (дешёво), но идеальнее — подписаться на `AudioManager`/`ACTION_SCREEN_*` вместо опроса. Низкий приоритет, т.к. реальный расход энергии мал.

### P-CPU-3 — `AerialRefresh.update` на каждом `foreground` (Низкий)

`LauncherRoot`:
```kotlin
LaunchedEffect(foreground, context) {
    if (!foreground || !AerialRefresh.isDue(context)) return
    if (AerialRefresh.update(context) == UPDATED) catalogTick++
}
```
- Гейт `isDue` есть → сеть не дергается зря ✅.
- Но при частых переключениях home↔app возможны повторные проверки `isDue`. Низкий риск.

## 8.6 Фоновые процессы вне приложения

- `OVisionAccessibilityService` — принимает `TYPE_WINDOW_STATE_CHANGED`; фильтрует по package/class, кэш `stockHomes` 60 с, throttle redirect 800 мс. Дёшево ✅.
- `SleepReceiver`, `screenOnReceiver` — только события ✅.

## 8.7 Вердикт

| # | Проблема | Приоритет | Правка |
|---|---|---|---|
| P-CPU-1 | вечный цикл погоды 60 с | Низкий | одноразовый таймер по stale |
| P-CPU-2 | опрос заставки 1 с | Низкий | событийная подписка |
| P-CPU-3 | AerialRefresh на resume | Низкий | ок (гейт есть) |

**CPU-профиль в простое — здоровый.** Крупных «лишних фоновых процессов» нет. Правки — оптимизация, не устранение багов.

## 8.8 Итог STAGE 7

Принцип обоих фиксов один: **спать до ближайшего срока, а не просыпаться по фиксированному шагу**. Событие (ввод, возврат из приложения, музыка, настройки) перезапускает ожидание, поэтому пробуждение происходит ровно тогда, когда что-то действительно может измениться.

### P-CPU-1 — погода планирует одно пробуждение (ИСПРАВЛЕНО)

`HomeScreen` больше не крутит `while(true) … delay(60_000)`:

```kotlin
while (true) {
    Weather.refresh(context, false)
    weatherData = Weather.cached(context)
    weatherStale = Weather.stale(context)
    delay(Weather.refreshDelay(context))
}
```

`Weather.updateDelay` (чистая функция, юнит-тесты без Android):

- `stale` (последняя загрузка не удалась) → 60 с (`RETRY_MS`), чтобы не крутиться на ошибке;
- иначе `refreshMinutes − age`, но не меньше 60 с;
- и не позже **начала следующего локального часа**: `present()` фильтрует часы от `localHourStart` и дни от сегодня, поэтому на границе часа прогноз обязан перечитаться.

При настройке 15 мин это 4 пробуждения в час вместо 60; на границе часа — ещё одно, необходимое для корректного прогноза.

**Доказательство:** три новых теста `WeatherTest` (`updateDelayFollowsTheRefreshSchedule`, `updateDelayWakesAtTheHourBoundary`, `updateDelayRetriesAStaleCacheAfterAMinute`) — `:app:testDebugUnitTest` BUILD SUCCESSFUL. На устройстве экран «Погода» при интервале 15 мин показывал «Последнее обновление 9 мин назад» — расписание соблюдается.

### P-CPU-2 — таймер заставки спит до дедлайна (ИСПРАВЛЕНО, ПРОВЕРЕНО)

`SAVER_POLL_MS = 1000` удалён. `LauncherRoot`:

- `lastActivity` + `markActivity()` — одна точка отметки активности; её вызывают ввод, возврат из Settings/приложения (`resumeTick`), закрытие заставки/пикера/PIN;
- `AudioPlaybackCallback` ставит `audioTick` на смену конфигурации воспроизведения (музыка началась/закончилась) — без чтения `isMusicActive` раз в секунду;
- эффект вычисляет `waitMs` до дедлайна `saverDelay.minutes` и спит; под музыкой без «показывать во время музыки» — ждёт события, а раз в 15 с (`SAVER_MUSIC_CHECK_MS`) перечитывает `isMusicActive` как страховку от пропущенного колбэка.

Пробуждений в простое больше нет: одно на дедлайн вместо 300 за 5 минут.

**Доказательство:** при установленных 2 мин заставка включилась после 145 с без ввода (скриншот `screenshots/stage7_saver_2min.png`, кадр «Гонконг»); настройка возвращена на 5 мин. В фоне (`SettingsActivity`) `Playback`-потоков нет (см. `07 §7.5`).

**CPU в простое (MEASURED, эмулятор):** 14 тиков `/proc/<pid>/stat` (поля 14+15) за 60 с при `CLK_TCK=100` → 0,14 с CPU ≈ **0,23 % одного ядра**. Абсолютные значения на ТВ UNKNOWN.

### P-CPU-3 — без изменений

`AerialRefresh.update` остаётся на `isDue`-гейте: сеть не дёргается, повторная проверка при частых переключениях — доли миллисекунды.

### Вердикт STAGE 7

| # | Проблема | Статус |
|---|---|---|
| P-CPU-1 | вечный цикл погоды 60 с | **исправлен** (расчётный дедлайн + граница часа) |
| P-CPU-2 | опрос заставки 1 с | **исправлен, проверен** (дедлайн + колбэк музыки) |
| P-CPU-3 | AerialRefresh на resume | принято (гейт есть) |

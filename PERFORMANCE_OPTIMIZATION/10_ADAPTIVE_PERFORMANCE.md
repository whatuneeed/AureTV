# 10 — ADAPTIVE PERFORMANCE

**Оценка (исходная, до STAGE 9): механизм есть частично.** Reduce Motion поддержан и централизован. Адаптации под возможности железа (RAM / GPU / слабый CPU) — **нет**: факты о железе читаются, но ни на что не влияют. Итог реализации — §10.6.

## 10.1 Что уже адаптивно

| Механизм | Реализация | Статус |
|---|---|---|
| Reduce Motion (§14) | `reduceMotion = !ValueAnimator.areAnimatorsEnabled()`, `motionDuration(ms, true) = 0` | ✅ глобально, 26 точек применения |
| `configChanges` | манифест покрывает `uiMode/density/screenSize/smallestScreenSize/keyboard` | ✅ не пересоздаёт Activity |
| Плотность/размер | Compose dp-модель, `screenOrientation=landscape` | ✅ |
| Язык/Locale | `LocalePreference` + per-Activity wrap | ✅ |
| Частота обновления | `refreshHz` читается из `Display.mode` | ⚠️ только для отображения в Settings |

**Вывод:** пользователь может вручную отключить анимации на уровне системы, и лаунчер это уважает. Это правильно и по промту (§14), и по tvOS-парадигме.

## 10.2 Что НЕ адаптивно

### P-ADAPT-1 — `isLowRamDevice` читается и не используется (Средний)

`DeviceFacts.kt:100`:
```kotlin
lowRam = activityManager?.isLowRamDevice == true,
```
Единственное применение `lowRam` — показать строку в настройках:
`SettingsScreen.kt:3584: if (facts.lowRam) { … }`.

**Влияние:** на устройстве с `isLowRamDevice = true` (типичный 1 ГБ ТВ-бокс) лаунчер ведёт себя ровно как на флагмане:
- те же 32 МБ на bitmap-кэш постеров заставки,
- те же 6 + 16 МБ на превью,
- те же 2 «прогретых» ExoPlayer,
- тот же `SHADOW_STEPS = 16` на плитку,
- тот же неленивый грид.

**Это главный адаптивный пробел.** Промт §15 прямо требует «адаптации к слабому железу».

### P-ADAPT-2 — нет профилей производительности (Средний)

Отсутствует понятие «режима»:
- нет `PerformanceProfile { HIGH, BALANCED, LOW }`;
- нет влияния `ramTotal` / `memoryClass` / `Build.HARDWARE` на бюджеты;
- нет ручного переключателя в Settings («Плавность / Качество / Авто»).

### P-ADAPT-3 — нет реакции на тепловой троттлинг (Низкий)

На ТВ длительный троттлинг — типичен. Сейчас нет ни `PowerManager.getCurrentThermalStatus()`, ни снижения качества при нагреве.

## 10.3 Предлагаемая модель адаптивности (для STAGE 9)

Единая точка `AdaptivePolicy`, читающая факты **один раз** при старте:

```kotlin
enum class PerformanceProfile { HIGH, BALANCED, LOW }

object AdaptivePolicy {
    fun resolve(context: Context, manual: PerformanceProfile?): PerformanceProfile {
        if (manual != null) return manual
        val am = context.getSystemService(ActivityManager::class.java)
        return when {
            am?.isLowRamDevice == true -> PerformanceProfile.LOW
            am != null && am.memoryClass <= 128 -> PerformanceProfile.LOW
            am != null && am.memoryClass <= 256 -> PerformanceProfile.BALANCED
            else -> PerformanceProfile.HIGH
        }
    }
}
```

Бюджеты по профилю:

| Параметр | HIGH | BALANCED | LOW |
|---|---:|---:|---:|
| `AerialPosterStore.memory` | 32 МБ | 20 МБ | 10 МБ |
| `SaverPreviewStore.memory` | 6 МБ | 4 МБ | 2 МБ |
| `SaverPreviewStore.thumbMemory` | 16 МБ | 10 МБ | 6 МБ |
| `shelfArtCache` | 12 | 10 | 8 |
| «Прогретые» ExoPlayer | 2 | 1 | 0 (по требованию) |
| `SHADOW_STEPS` (плитка) | 16 | 12 | 8 |
| Постер полки `ART_MAX_PX` | 1280 | 1280 | 960 |
| `FilterQuality` арта | Medium | Low | Low |
| Бесконечные анимации (zoom заставки) | вкл | вкл | вкл (дёшево, alpha/scale слоя) |

**Важно по промту:**
- Никакой профиль не должен **отключать функции** (§3 «не удалять функции/данные»).
- `LOW` снижает качество бюджетов, но не убирает кадры/панели/заставку.
- Ручной выбор обязателен: пользователь должен уметь вернуть HIGH на слабом боксе и наоборот.

## 10.4 Как проверить адаптивность

| Проверка | Как |
|---|---|
| Профиль LOW на 1 ГБ | `adb shell setprop` не поможет — `isLowRamDevice` зашит в сборку. Тестировать эмулятором с `hw.ramSize=1024` + `hw.gpu` software |
| Профиль через настройку | ручной переключатель → проверить, что бюджеты применились (лог/строка в Settings) |
| Поведение при троттлинге | `adb shell cmd thermalservice override-status`, реальный ТВ — под нагрузкой |
| Регресс качества | визуальный обход: тени, постеры, полка не «рассыпаются» на LOW |

## 10.5 Вердикт

| # | Проблема | Приоритет | Действие (STAGE 9) |
|---|---|---|---|
| P-ADAPT-1 | `isLowRamDevice` не влияет на бюджеты | Средний | `AdaptivePolicy` + параметризация кэшей/теней |
| P-ADAPT-2 | нет профилей и ручного режима | Средний | переключатель в Settings |
| P-ADAPT-3 | нет реакции на троттлинг | Низкий | `getCurrentThermalStatus()` → мягкое снижение |

**Оговорка:** адаптивность имеет смысл **после** того, как сняты безусловные потери (двойной фон, `onTrimMemory`, лишний ExoPlayer) — их надо убрать на всех профилях, а не прятать за профилем.

## 10.6 Итог STAGE 9 (адаптивные бюджеты и тепловой кап) — 10.11.2026

### Что сделано

| Файл | Изменение |
|---|---|
| `data/AdaptivePolicy.kt` (новый) | `PerformanceProfile { HIGH, BALANCED, LOW }`, `AdaptiveBudgets`, `AdaptivePolicy.profileFor/thermalCap/budgets`, объект `Adaptive` (`install`, `onThermalStatus`, `budgets`) |
| `MainActivity.kt`, `SettingsActivity.kt` | `Adaptive.install(this)` до первой композиции; в Settings ещё и в `onResume` (строка профиля честна после системных экранов) |
| `AerialPosterStore`, `SaverPreviewImage` (`AerialPreviewStore`), `AppRepository` (`AppArtCache`), `TopShelf` (`shelfArtCache`, `ART_MAX_PX`) | лимиты берутся из `Adaptive.budgets` при создании; добавлены `shrinkTo`/`trimShelfArtCacheTo` для понижения профиля на лету |
| `CardShell.kt` | `SHADOW_STEPS` заменён на `Adaptive.budgets.shadowSteps`, читается в каждом draw |
| `MemoryPressure.kt` | `trimToProfile()`: применяет новые бюджеты к кэшам без выброса полки |
| `LauncherRoot.kt` | `DisposableEffect` с `PowerManager.addThermalStatusListener` (API 29+): `Adaptive.onThermalStatus` + `MemoryPressure.trimToProfile` |
| `SettingsScreen.kt` (Устройство) | read-only строка «Производительность / Performance»: Высокая — Сбалансированная — Экономная |
| `AdaptivePolicyTest` (новый) | 6 тестов: low-RAM флаг, границы heap 128/160/161/192/320/321/512, heap=0, тепловые статусы 0–6, монотонность бюджетов, ≥10 постеров полки в любом профиле |

### Фактические бюджеты (отличаются от проекта §10.3)

| Параметр | HIGH | BALANCED | LOW |
|---|---:|---:|---:|
| Кэш постеров заставки | 32 МБ | 24 МБ | 16 МБ |
| Превью | 6 МБ | 6 МБ | 3 МБ |
| Превью-миниатюры | 16 МБ | 12 МБ | 8 МБ |
| `AppArtCache` | 12 МБ | 12 МБ | 8 МБ |
| Постеры полки, записей | 12 | 12 | 10 |
| Постер полки, px | 1280 | 1280 | 960 |
| Слоёв тени плитки | 12 | 10 | 8 |

Почему не как в §10.3:

- **Ручного переключателя нет.** Решение владельца проекта — автоопределение
  по реальным признакам, без новых галочек (`feedback-prefer-generic-automation`).
  Профиль выводится из `isLowRamDevice` + `memoryClass`; смотреть его можно, но
  не менять — на ТВ владелец не знает объём heap, и неверный выбор только вредит.
- **`shelfArtEntries` LOW = 10, а не 8.** На экране одновременно 12 постеров;
  кэш на 8 переспрашивал диск на каждом шаге карусели — это уже чинилось в STAGE 6
  (`P-IMG-2`), откатывать нельзя.
- **`grid` и полка остаются целыми до LOW.** Их владелец видит каждый день;
  экономятся в первую очередь кадры заставки и превью настроек.
- **ExoPlayer не сокращается.** STAGE 7 уже освобождает оба плеера, пока Home не
  на экране; один плеер убрал бы кроссфейд заставки — это удаление функции.
- **`FilterQuality` не переключается.** Арт уже сэмплируется под профиль
  (`ART_MAX_PX`), а качество фильтра задаётся в точке вызова; менять его по
  профилю — рекомпозиция ради невидимой на 1080p разницы.

### Проверка на hume-tv (эмулятор, MEASURED)

| Шаг | Результат |
|---|---|
| Профиль на AVD (heap limit 192 МБ, `isLowRamDevice=false`) | «Сбалансированная» — `stage9_device_page.png` |
| `cmd thermalservice override-status 3` → профиль | «Экономная» — `stage9_device_low.png` |
| `cmd thermalservice reset` + возврат в Settings (`onResume`) | «Сбалансированная» — `stage9_device_balanced_again.png` |
| Главная под LOW | тени/постеры без деградации, крашей нет — `stage9_home_low.png` |
| EN-подписи | «Performance — Balanced» — `stage9_device_en.png` |
| Память в простое (BALANCED, Главная) | TOTAL PSS 63 316 КБ, Java heap 10 196 КБ |
| Падения | нет (`logcat -b crash` пуст) |

Юнит-тесты: 267, падений 0 (`:app:testDebugUnitTest`), из них 6 новых.

### Остаточный риск

- **LOW на реальном 1 ГБ боксе не запускался.** `isLowRamDevice` зашит в образ;
  логика профиля покрыта юнит-тестами, но фактическая работа LOW на слабом
  железе — **UNKNOWN**.
- **Тепловой слушатель живёт только в `LauncherRoot`.** Если Settings открыт
  поверх Главной и кап меняется, строка обновится при следующем resume Settings
  (там `install`), а кэши ужмёт слушатель Главной, как только он получит статус.
- **Возврат к базе при повторном `install`.** После снятия капа профиль
  возвращается к аппаратному (это ожидаемо), но уже ужатые кэши не отрастают до
  перезапуска процесса — только переиспользуются.
- Замеры теплового пути — на эмуляторе; сколько реальный ТВ троттлит под
  заставкой — **UNKNOWN**.

### Вердикт §10.5

| # | Проблема | Статус |
|---|---|---|
| P-ADAPT-1 | `isLowRamDevice` не влиял на бюджеты | **исправлен** — профиль LOW и его бюджеты |
| P-ADAPT-2 | нет профилей (ручной режим отклонён осознанно) | **исправлен** — профили + read-only строка |
| P-ADAPT-3 | нет реакции на троттлинг | **исправлен** — кап MODERATE→BALANCED, SEVERE+→LOW |
| P-MEM-3 | 4 несогласованных кэша без лимита по RAM | **исправлен** — единая точка бюджетов + `trimToProfile` |

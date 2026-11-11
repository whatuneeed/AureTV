# 12 — FINAL BENCHMARK (шаблон)

> ⚠️ **Этот файл — шаблон, а не результат.** Заполняется **только** на реальном целевом ТВ/боксе. Эмулятор `hume-tv` доказательством не считается (software/host GPU, накладные PSS). Числа эмулятора см. в `01_BASELINE_METRICS.md` — они служат лишь точкой сравнения «до/после» на том же стенде.

## 12.0 Карточка стенда (заполнить перед замерами)

| Поле | Значение |
|---|---|
| Модель / производитель | ____ |
| Android / API | ____ |
| RAM всего | ____ |
| `isLowRamDevice` | ____ |
| `ActivityManager.memoryClass` | ____ |
| GPU | ____ |
| Разрешение / плотность | ____ |
| Частота обновления | ____ |
| Сборка лаунчера (versionName/versionCode) | ____ |
| Профиль производительности (HIGH/BALANCED/LOW) | ____ |
| Температура на старте | ____ |
| Фон (что установлено, что запущено) | ____ |

**Методика:** каждый показатель — **5 прогонов**, берётся **медиана**; между прогонами — пауза до стабилизации температуры; один и тот же сценарий; один и тот же набор приложений.

## 12.E Эмулятор `hume-tv`: срез 0.4.34 (НЕ результат для ТВ)

Снято 10.11.2026 на headless-эмуляторе после чистой установки release 0.4.34 (vc54) и пройденного мастера. Стенд: AOSP TV on x86, Android 14 (API 34), 1920×1080 · 320 dpi · 60 Гц, heap limit 192 МБ, `isLowRamDevice=false` → профиль **BALANCED**, системный язык ru-RU, APK 3 962 290 B, SHA-256 `c40a57e8…a39a3`. Числа годятся только для сравнения «до/после» на этом же стенде.

| Показатель | 0.4.24 baseline (`01`) | 0.4.34 | Комментарий |
|---|---:|---:|---|
| Cold start TotalTime, мс (медиана 5) | 1046 | **359** (335–370) | метод FallbackHome + `force-stop`, как в STAGE 8 |
| Warm start TotalTime, мс (медиана 5) | 646 | **97** (81–108) | HOME → `am start -W` |
| Idle Home TOTAL PSS, КБ | 86 493 | **61 829** | после 30 с покоя, полка с постером загружена; сразу после warm — 52 297 |
| Idle Home TOTAL RSS, КБ | 187 408 | **160 500** | |
| GPU memory | 33 308 204 B (31,77 МБ) | **42 895 596 B (40,91 МБ)**, 32,18 МБ purgeable | условия различаются: в 0.4.34 полка реально держит арт; прямой разницы не выводить |
| 20× D-pad Right: кадры | 35 кадров, 97,14 % janky, p50 150 мс (idle) | **59 кадров, 41 janky (69,49 %), p50 34 мс, p90 73, p95 109, p99 300**, Missed Vsync 9, Slow UI 14 | GPU-перцентили эмулятора недостоверны |
| Реакция на `RUNNING_LOW` | не проверялось | PSS 64 628 → **59 076 КБ** (−5,4 МБ), Native Heap 34 356 → 32 316 КБ | эвикция кэшей срабатывает |
| Термотроттлинг | не проверялось | `override-status 3` → строка «Производительность» = **Экономная**; `reset` → **Сбалансированная** | сквозная проверка STAGE 9 |
| Стабильность | — | crash-буфер пуст, `am_anr` = 0, потоков 31 | сессия ~40 мин с прогонами |

Тестовые ворота 0.4.34: lint 0 ошибок / 15 предупреждений, unit 267/267, instrumented 178/178.

## 12.1 Startup

| Сценарий | Прогон 1 | 2 | 3 | 4 | 5 | Медиана | Цель §16 |
|---|---:|---:|---:|---:|---:|---:|---:|
| Cold (`am start -W` TotalTime, мс) | | | | | | | минимально |
| Warm TotalTime, мс | | | | | | | минимально |
| First-frame (`reportFullyDrawn`/Perfetto), мс | | | | | | | |
| Пропущено кадров на старте (logcat) | | | | | | | 0 |

```bash
adb shell am force-stop com.whatuneed.ovisiontv.kotlin
adb shell am start -W -n com.whatuneed.ovisiontv.kotlin/.MainActivity
adb logcat -d | grep -E "Skipped [0-9]+ frames"
```

## 12.2 Кадры (навигация D-pad)

Сценарий: 30 шагов D-pad по гриду → открыть полку → полистать → открыть Control Center → закрыть.

| Метрика | До | После | Цель §16 |
|---|---:|---:|---:|
| Total frames | | | |
| Janky frames % | | | ↓ |
| 50th percentile (мс) | | | ≤ 16,67 (60 Гц) |
| 90th percentile (мс) | | | ≤ 16,67 |
| 95th percentile (мс) | | | ≤ 16,67 |
| 99th percentile (мс) | | | минимально |
| Missed Vsync | | | ↓ |
| Slow UI thread | | | ↓ |
| Slow issue draw commands | | | ↓ |

```bash
adb shell dumpsys gfxinfo com.whatuneed.ovisiontv.kotlin reset
# ... сценарий D-pad ...
adb shell dumpsys gfxinfo com.whatuneed.ovisiontv.kotlin framestats
```

## 12.3 Input-to-Visual Response

| Сценарий | Прогон 1 | 2 | 3 | 4 | 5 | Медиана P95 | Цель §16 |
|---|---:|---:|---:|---:|---:|---:|---:|
| D-pad → движение фокуса (мс) | | | | | | | ≤ 50 |
| OK → отклик плитки (мс) | | | | | | | ≤ 50 |
| Выход из панели → отклик стрелки (мс) | | | | | | | ≤ 50 (проверка `P-INPUT-1`) |
| Back → возврат (мс) | | | | | | | ≤ 50 |

```bash
adb shell atrace -t 10 -b 16384 gfx input view sched freq --async_start
# ... сценарий ...
adb shell atrace --async_stop -z -c -o /data/local/tmp/trace
```

## 12.4 Память

| Метрика | Idle Home | С открытой полкой | Заставка | Настройки | Цель |
|---|---:|---:|---:|---:|---|
| TOTAL PSS (КБ) | | | | | ↓ |
| TOTAL RSS (КБ) | | | | | ↓ |
| Native Heap (КБ) | | | | | ↓ |
| Graphics / GPU (МБ) | | | | | ↓ |
| Bitmap-кэши суммарно (МБ) | | | | | по профилю |

```bash
adb shell dumpsys meminfo com.whatuneed.ovisiontv.kotlin | grep -E "TOTAL|Native Heap|Graphics"
```

Проверка реакции на давление:
```bash
adb shell am send-trim-memory com.whatuneed.ovisiontv.kotlin RUNNING_LOW
adb shell dumpsys meminfo com.whatuneed.ovisiontv.kotlin | grep TOTAL
```

## 12.5 GPU / Overdraw

| Метрика | До | После | Цель §16 |
|---|---:|---:|---:|
| GPU memory (МБ) | | | ↓ |
| Texture (Image) (МБ) | | | ↓ |
| Scratch RenderTarget (МБ) | | | ↓ |
| Full-screen проходов на кадр (расчёт/розовая сетка) | | | минимум |

```bash
adb shell dumpsys gfxinfo com.whatuneed.ovisiontv.kotlin | grep -E "GPU|Texture|Scratch|Max"
# визуально: настройки разработчика → Показать overdraw
```

## 12.6 Живучесть и стабильность

| Проверка | Результат | Цель |
|---|---|---|
| Crash за 30 мин сессии | | 0 |
| ANR за 30 мин сессии | | 0 |
| Цикл «Home → заставка → настройки → назад» ×50 | PSS не растёт | стабильно |
| Утечка Activity (`recreate()` сменой языка ×10) | нет роста | 0 |
| Реакция на троттлинг (30 мин) | плавность сохраняется | без деградации до «слайд-шоу» |

## 12.7 Сводная таблица «до → после» (заполняется по итогам STAGE 3–9)

| Показатель | Baseline (ТВ) | После | Изменение | Достигнут §16 |
|---|---:|---:|---:|---|
| Cold start, мс | | | | |
| Warm start, мс | | | | |
| P95 frame, мс | | | | |
| P95 input→visual, мс | | | | |
| TOTAL PSS idle, КБ | | | | |
| GPU mem, МБ | | | | |
| Janky %, idle/nav | | | | |

## 12.8 Итоговая оценка

Заполнить **после** всех замеров на целевом железе, честно:

| Область | Оценка (0–10) | Обоснование (числа, а не мнение) |
|---|---:|---|
| Startup | | |
| Input/Focus | | |
| Animation | | |
| GPU/Rendering | | |
| Memory | | |
| CPU/Background | | |
| Images/Cache | | |
| Adaptive | | |

> Правило отчёта: **без заполненного `12` на реальном ТВ финальная оценка не ставится.** Эмулятор даёт только «до/после на одном стенде», не абсолютный результат.

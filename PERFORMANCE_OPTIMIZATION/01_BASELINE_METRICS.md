# 01 — BASELINE METRICS

**Устройство:** эмулятор `hume-tv` (Google Android TV Emulator)
**Android:** 14 (API 34) · **Разрешение:** 1920×1080 · **Density:** 320 dpi · **Refresh:** 60.000004 Гц
**Сборка:** release 0.4.24 (vc44), `isMinifyEnabled=false`, подпись dev-ключом
**Дата:** 09.11.2026

> ⚠️ **Это эмулятор, а не телевизор.** GPU-перцентили `gfxinfo` на хосте недостоверны; PSS включает накладные процесса эмулятора. Абсолютные числа приводить как «результат на ТВ» нельзя. Они нужны как *точка отсчёта для сравнения до/после* на том же стенде.

---

## 1. Установка и идентичность

```
pm list packages | ovisiontv:
  com.whatuneed.ovisiontv          (Java 1.1.17, vc30)
  com.whatuneed.ovisiontv.kotlin   (Kotlin 0.4.24, vc44)

default HOME: com.whatuneed.ovisiontv/.MainActivity (Java — сейчас держит HOME)
```

- Оба лаунчера установлены одновременно на **разных** `applicationId`; HOME-кандидатов два.

## 2. Память (idle Home, `dumpsys meminfo`)

| Метрика | Значение |
|---|---:|
| **TOTAL PSS** | **86 493 KB (≈ 86,5 МБ)** |
| TOTAL RSS | 187 408 KB (≈ 187,4 МБ) |
| TOTAL SWAP PSS | 10 227 KB |
| Native Heap (PSS) | 33 213 KB |
| Native Heap (RSS) | 34 020 KB |
| Dalvik Heap (PSS) | 8 313 KB |
| Graphics | 0 KB (в отчёте meminfo рантайма) |

**Производные (GPU, из `gfxinfo`):**
| Метрика | Значение |
|---|---:|
| GPU memory total | 33 308 204 B (≈ 31,77 МБ) |
| Texture (Image) | 10,89 МБ (10 entries) |
| Scratch RenderTarget | 10,66 МБ (16 entries) |
| Scratch Texture | 9,99 МБ (15 entries) |
| Max surface area | 2 073 600 (1920×1080) |
| Max resource usage | 99,53 МБ |

**Обсервация:** Native Heap (33 МБ) + GPU texture (≈ 21 МБ образ+scratch) — это в основном **bitmap-постеры и декод сжатых картинок** (Aerial posters, Aerial previews, shelf art, app icons). Именно здесь лежит бюджет оптимизации RAM.

## 3. Размер сборки

| Метрика | Значение |
|---|---:|
| base.apk на устройстве | 25 415 013 B (≈ 24,2 МБ) |
| native libs (`lib/`) | 16 KB (практически нет нативных) |
| Т.е. вес — это DEX Compose/media3 + ресурсы | — |

Для сравнения: Java-версия без зависимостей существенно легче по APK; Kotlin несёт Compose + media3. `isMinifyEnabled=false` **не уменьшает** этот размер и не ускоряет старт.

## 4. Startup

| Сценарий | `am start -W` TotalTime | WaitTime |
|---|---:|---:|
| **Cold** (после `force-stop`) | **1046 мс** | 1062 мс |
| **Warm** (из фона) | **646 мс** | 651 мс |

Связанное из logcat при старте:
```
Choreographer: Skipped 36 frames! The application may be doing too much work on its main thread.
ProfileInstaller: Skipping profile installation for com.whatuneed.ovisiontv.kotlin
```
- Нет профиля → `ProfileInstaller` ничего не делает (естественно, baseline-profile отсутствует).
- «Skipped 36 frames» — первый кадр Home упирается в декод иконок/баннеров (`AppRepository.load()`), запускаемый в `LauncherRoot` после первого кадра, но конкурирующий за CPU.

## 5. Кадры (idle + 20× D-pad Right)

`dumpsys gfxinfo` (framestats и накопительный):

| Метрика | Idle Home | После 20 нажатий D-pad |
|---|---:|---:|
| Total frames (интервал) | 35 | 0/эмулятор не зафиксировал UI-кадры |
| Janky frames | 34 (97,14 %) | — |
| 50th percentile | 150 мс | 4950 мс (= нет данных) |
| 90th percentile | 250 мс | 4950 мс |
| 95th percentile | 250 мс | 4950 мс |
| Number Missed Vsync | 33 | 0 |
| Number Slow UI thread | — | 0 |
| Render nodes | 90,01 KB / 114,70 KB | — |
| Attached Views | 87 | 7 (после перезахода) |

**Важно:** GPU-перцентиль `4950 мс` — это маркер эмулятора «GPU-метрик нет», **не** реальная задержка. Перцентили 150–250 мс отражают накладные эмулятора, а не ТВ.

## 6. Тепловая/энергетическая (не снято)

Не снималось: на эмуляторе показательно. На реальном ТВ снять `dumpsys thermalservice` и `batterystats` при цикле «Главная → док → заставка → Настройки».

---

## 7. Как повторить на реальном ТВ (обязательно перед STAGE 3)

```bash
# 1. Baseline памяти
adb shell dumpsys meminfo com.whatuneed.ovisiontv.kotlin | grep -E "TOTAL|Native Heap|Graphics"

# 2. Cold / warm start (по 5 прогонов, взять медиану)
adb shell am force-stop com.whatuneed.ovisiontv.kotlin
adb shell am start -W -n com.whatuneed.ovisiontv.kotlin/.MainActivity

# 3. Кадры на навигации: сбросить, пройти D-pad 30 шагов, снять
adb shell dumpsys gfxinfo com.whatuneed.ovisiontv.kotlin reset
# ... D-pad ...
adb shell dumpsys gfxinfo com.whatuneed.ovisiontv.kotlin framestats

# 4. Трейс (Perfetto/system trace)
adb shell atrace -t 10 -b 16384 gfx input view sched freq --async_start
# ... D-pad + открытие панели ...
adb shell atrace --async_stop -z -c -o /data/local/tmp/trace

# 5. JankStats / FrameMetrics (в приложении) — если решим встроить
```

**Критерий готовности стенда:** 3 набора чисел «до» на одном и том же ТВ, в одном сценарии, с одинаковой температурой/фоном. Только после этого STAGE 3 даёт сравнимые «после».

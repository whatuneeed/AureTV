# 00 — MASTER PERFORMANCE REPORT

**Проект:** OVisionTV Launcher (Kotlin) · `com.whatuneed.ovisiontv.kotlin`
**Версия:** 0.4.34 / versionCode 54 (на момент финальной верификации)
**Дата аудита:** 09.11.2026 · **финальная верификация:** 10.11.2026
**Автор:** Senior Android Performance / Graphics / Motion audit
**Промт:** MASTER TASK — ANDROID TV LAUNCHER PERFORMANCE & MOTION OPTIMIZATION

---

## 0. Статус работы

| Этап | Название | Состояние |
|---|---|---|
| STAGE 1 | Audit (без изменений кода) | ✅ выполнен — код прочитан целиком |
| STAGE 2 | Measurement (baseline) | ✅ выполнен на эмуляторе `hume-tv` |
| STAGE 3 | Critical fixes (`onTrimMemory`, кэши) | ✅ выполнен, проверен |
| STAGE 4 | Input & Focus | ✅ выполнен, проверен |
| STAGE 5 | Animation | ✅ выполнен |
| STAGE 6 | Graphics & Images | ✅ выполнен, проверен |
| STAGE 7 | Memory & CPU | ✅ выполнен, проверен |
| STAGE 8 | Startup & Resume | ✅ выполнен; профиль ставится, выигрыш на эмуляторе не измерен |
| STAGE 9 | Adaptive Performance | ✅ выполнен, проверен (термо-кап вживую) |
| STAGE 10 | Final verification | ✅ выполнен на эмуляторе; **на реальном ТВ — UNKNOWN** |

Финальные ворота 0.4.34: lint 0 ошибок / 15 предупреждений · unit 267/267 · instrumented 178/178 · release 3 962 290 B, SHA-256 `c40a57e8…a39a3` · crash-буфер пуст, ANR 0. Эмуляторный срез «до/после» — `12_FINAL_BENCHMARK.md` §12.E.

**Правило отчёта:** ни одна цифра эмулятора не выдаётся за телевизионную; §§12.1–12.8 ждут реального ТВ.

---

## 1. Что за приложение (фактура, STAGE 1)

- **UI-фреймворк:** Jetpack Compose (BOM 2024.09.03) + `material3`. Не Leanback, не Views.
- **Язык:** Kotlin, `minSdk 28` / `targetSdk 35`.
- **Объём главного кода:** 18 578 строк `.kt` в `app/src/main`.
- **Зависимости рантайма (крупные):** `androidx.media3:media3-exoplayer:1.4.1` + `media3-ui:1.4.1` (ExoPlayer — заставка/Aerial), `activity-compose`, `core-ktx`, `kotlinx-coroutines-android`.
- **`isMinifyEnabled = false`** — R8-шринк выключен.
- **Baseline Profile:** отсутствует (нет зависимости `androidx.profileinstaller`, нет `baseline-prof.txt`).
- **Активности:** `MainActivity` (HOME), `SettingsActivity`, `SetupActivity` — все `singleTask`-подобные, `configChanges` покрывает uiMode/density/screenSize, `screenOrientation=landscape`.
- **Сервис:** `OVisionAccessibilityService` (перехват Home + запуск из фона, событийные вызовы).
- **Анимации/движение:** централизованы в `TvOs` (`FOCUS_MS=180`, `SCROLL_DURATION_MS=300`) + `motionDuration(ms, reduceMotion)`.
- **Reduce Motion:** поддерживается (`ValueAnimator.areAnimatorsEnabled()`), пути alpha-only.

### Карта горячих файлов

| Файл | Строк | Роль |
|---|---:|---|
| `ui/SettingsScreen.kt` | 3544 | Настройки (16 `LaunchedEffect`, 86 `remember`, `LazyColumn` только в списке клипов) |
| `ui/HomeScreen.kt` | 1656 | Главный экран: грид/док/полка/фокус/меню/звук |
| `ui/SetupScreen.kt` | 1474 | Мастер первого запуска |
| `ui/components/ControlCenter.kt` | 875 | Пункт управления, капсула |
| `ui/components/SaverScreen.kt` | 773 | Заставка (2× ExoPlayer) |
| `ui/FolderOverlay.kt` | 699 | Панель папки |
| `ui/components/TopShelf.kt` | 416 | Полка/карусель (главный GPU-потребитель) |
| `ui/components/CardShell.kt` | 227 | Плитка: тень (16 слоёв), фокус, джиггл |
| `data/AppRepository.kt` | 179 | PackageManager + декод иконок/баннеров |

---

## 2. Итог аудита (кратко)

**Сильная сторона:** архитектура в целом грамотная для perf-задач. Фокус, анимации, Reduce Motion, blur-отказы, сжатие постеров — уже спроектированы осознанно, с комментариями-обоснованиями. Это редкость.

**Основные риски производительности (по приоритету):**

| # | Проблема | Область | Приоритет |
|---|---|---|---|
| P-GPU-1 | Двойной полноэкранный градиент (LauncherRoot + HomeScreen) + 3 полноэкранных градиента полки каждый кадр | GPU / Overdraw | **Высокий** |
| P-GPU-2 | Тень плитки рисуется 16 стекущимися `drawRoundRect` на каждой плитке | GPU | Средний |
| P-MEM-1 | Несколько несогласованных LruCache (32+6+16 МБ bitmap) + 2 «прогретых» ExoPlayer в простое + нет реакции на `onTrimMemory` | Memory | **Высокий** |
| P-START-1 | Нет Baseline Profile и выключен R8 (`isMinifyEnabled=false`) при cold start ~1046 мс | Startup | **Высокий** |
| P-IMG-1 | Иконки/баннеры декодируются в `ARGB_8888`, кэша bitmap по package нет — полный ре-декод на каждом package-broadcast | Images/Memory | Средний |
| P-IMG-2 | `shelfArtCache` (8) меньше, чем живёт в `arts` (12) — риск ре-декода при листании | Images | Средний |
| P-GPU-3 | Грид не ленивый (`for … items.withIndex()` композит все плитки) — при большом числе приложений растёт линейно | GPU/Layout | Средний |
| P-ADAPT-1 | `reduceMotion` есть, но адаптивных профилей устройств нет; `isLowRamDevice` читается и не используется | Adaptive | Средний |
| P-GPU-4 | Полка: баннер-подложка + постер — два полноэкранных `Image` в кадре | GPU | Низкий |
| P-CPU-1 | `Weather.refresh` будит корутину каждые 60 с; `AerialRefresh` на каждом resume | CPU | Низкий |

**Что уже хорошо (не трогать):**
- Отказ от GPU-blur/RenderEffect (задокументировано: ~20 fps на ТВ).
- Полка декодирует постер один раз в `RGB_565` при `ART_MAX_PX=1280`, не масштабирует в render-thread.
- `LightVisible`/`IconEdgeLight` — градиенты записаны один раз, анимируется только alpha слоя.
- `TvKeyRepeat` — собственный key-repeat вместо системного (стабильность input).
- `SaverScreen` реально снимает нагрузку с Home (`alpha=0` слой под заставкой, snapshotFlow вместо recompose на 60 fps).
- Один канал фокуса (индексная модель), а не независимые реализации по экранам.

---

## 3. Оговорка о доказательствах (важно)

Все цифры в `01_BASELINE_METRICS.md` сняты на **эмуляторе `hume-tv` (Android TV 14 / API 34, 1920×1080, 60 Гц, host GPU)**. Это **не реальный телевизор**:

- GPU-конвейер эмулятора software/OpenGL-host → показатели `dumpsys gfxinfo` GPU-перцентилей **недостоверны** (4950 мс = «нет данных», а не «очень медленно»).
- PSS эмулятора включает накладные host-процесса.
- Absolut-числа cold start на эмуляторе **нельзя выдавать за результат на ТВ**.

Поэтому: **до STAGE 3 нужны замеры на целевом железе** (см. `01` §«Как снять на реальном ТВ»). Ни один пункт ниже не помечен как «достигнуто измеримо» — только как «подтверждено кодом» либо «подтверждено эмулятором».

---

## 4. Целевые ориентиры (из промта §16) — как цели, не как факт

| Показатель | Ориентир | Текущий статус |
|---|---:|---|
| Input-to-Visual Response | P95 ≤ 50 мс | эмулятор p95 109 мс на D-pad (недостоверно); ТВ — не измерено |
| Frame time 60 Гц | ≤ 16,67 мс | эмулятор p50 34 мс после правок; ТВ — не измерено |
| Frame time 120 Гц | ≤ 8,33 мс | железо не заявлено |
| UI-Thread blocking | 0 длительных | стартовый кадр ≈1,04 с на SwiftShader (P-START-3, закрыт решением); на ТВ — не измерено |
| Утечки | 0 | не выявлено; trim-реакция проверена (−5,4 МБ PSS) |
| Crash/ANR | 0 | 0 в финальной сессии 0.4.34 |

---

## 5. Рекомендованный порядок STAGE 3–9

1. **STAGE 3 (Critical):** `onTrimMemory`/`onLowMemory` → эвикция кэшей; убрать двойной фон; снизить bitmap-бюджет. — **выполнено** (P-MEM-1).
2. **STAGE 4 (Input/Focus):** верификация на ТВ, убрать/сжать второй `delay(350)` в восстановлении фокуса. — **выполнено** (P-INPUT-1, P-START-4).
3. **STAGE 5 (Animation):** сверка длительностей с одним источником (`TvOs`) — почти готово. — **выполнено** (P-ANIM-1; P-ANIM-2 не воспроизводится).
4. **STAGE 6 (GPU/Images):** убрать overdraw полки и фона; RGB_565 для иконок; согласовать кэши. — **выполнено** (P-GPU-1/2/3, P-IMG-1/2/4; RGB_565/HARDWARE отклонены, см. `06 §6.6`; неленивый грид — FRAGILE).
5. **STAGE 7 (Memory/CPU):** не держать 2 декодера в простое; таймеры заставки и погоды — по сроку, а не по шагу; `applicationContext`; единые бюджеты кэшей. — **выполнено** (P-MEM-2/4, P-CPU-1/2; P-MEM-3 перенесён в STAGE 9 к `P-ADAPT-1/2`).
6. **STAGE 8 (Startup):** Baseline Profile + `profileinstaller`; решить по R8; skeleton вместо ожидания декода. — **выполнено** (P-START-1: установщик + профиль библиотек, dexopt `speed-profile`; P-START-2: R8+shrink, APK 25,5→3,9 МБ; P-START-3 диагностирован — рендер-поток/SwiftShader, решение в STAGE 10; ускорения старта на эмуляторе нет, ТВ UNKNOWN — `09 §9.7`).
7. **STAGE 9 (Adaptive):** профиль по `isLowRamDevice` + ручной режим. — **выполнено** (P-ADAPT-1/2/3, P-MEM-3: профиль HIGH/BALANCED/LOW автоопределением, бюджеты кэшей и теней, тепловой кап; ручной переключатель отклонён — read-only строка в «Устройстве»; LOW на реальном слабом боксе UNKNOWN — `10 §10.6`).
8. **STAGE 10 (Final verification):** замеры на реальном ТВ, регрессия, финальный релиз — `12_FINAL_BENCHMARK.md`. — **выполнено на эмуляторе** (0.4.34; §12.E): cold 359 мс, warm 97 мс, idle PSS 61 829 КБ, trim −5,4 МБ, термо-кап проверен, 0 падений; реальный ТВ — UNKNOWN.

Полный список задач — `13_REMAINING_ISSUES.md`.

---

## 6. Итоговый вердикт STAGE 10 (10.11.2026)

**Что закрыто и доказано на эмуляторе:** кэши освобождаются под давлением и по профилю; двойной фон и три градиента полки убраны; тень 12 слоёв вместо 16; кэш иконок и согласованный `shelfArtCache`; ExoPlayer не висит в простое; таймеры погоды/заставки по сроку; Baseline Profile устанавливается, R8 даёт APK 3,96 МБ; профили HIGH/BALANCED/LOW выводятся из железа, тепловой кап срабатывает end-to-end; навигация D-pad не теряет шагов; полный набор тестов и lint зелёные.

**Что осталось UNKNOWN (только реальный ТВ):**
- абсолютные cold/warm start, p95 кадра и input→visual при аппаратном GPU;
- профиль LOW на боксе с 1 ГБ RAM и heap ≤160 МБ (вживую не включался);
- поведение при настоящем троттлинге 30+ минут;
- первый кадр после cold start с реальным GPU (P-START-3);
- 4K-панель (UI ограничен 1920 px по `config_maxUiWidth`).

**Регрессия, поймана финальным прогоном:** после P-MEM-4 собственный ярлык «Настройки» и сортировка плиток читались с `applicationContext` и не следовали языку лаунчера. Исправлено в `AppRepository` (обёртка `LocalePreference.wrap`), подтверждено `LanguageReturnTest` 2/2 и полным прогоном 178/178.

---

## 7. Срез 0.4.41 после UI/UX-этапов 3–8 (11.11.2026)

Контрольный прогон на том же эмуляторе против 0.4.34: холодный старт 283 против 278 мс (разбросы перекрываются), тёплый 81 против 74 мс, idle PSS 42 669–52 081 против 52 325 КБ, janky 27,9 % против 40,9 %, Slow UI 3 против 6, на 80 перемещениях фокуса роста памяти нет, crash/ANR — 0/0. Регрессии после унификации не найдено; 4K и реальный ТВ по-прежнему `NOT TESTED`. Метод, все цифры и оговорки — `UI_UX_UNIFICATION/15_PERFORMANCE.md`.

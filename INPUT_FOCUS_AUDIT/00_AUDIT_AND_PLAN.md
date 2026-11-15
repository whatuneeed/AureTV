# Аудит системы пульта, фокуса и навигации

Проект: OVisionTV Kotlin (`com.whatuneed.ovisiontv.kotlin`)
Дата аудита: 2026-02-12
Статус: **аудит завершён; по подтверждению владельца выполнены Phase 0, Phase 3,
точечные Phase 1–2 и Phase 4–5; FOCUS-05/19/21/22 закрыты. Оставшиеся пункты — в §I.**
Документ ниже сохраняет исходные выводы аудита; отметки о фактической реализации собраны
в §I, чтобы не выдавать план за уже сделанное.

Основание: запрос владельца — сначала отчёт A–H и план, затем подтверждение.

---

## I. Ход реализации после подтверждения

Реализация велась без второго Focus Engine: существующие целочисленные курсоры остаются
источником истины, Compose-фокус теперь зеркалит их на всех затронутых поверхностях.
Новых зависимостей и разрешений не добавлено.

| Пункт | Что сделано | Файлы |
|---|---|---|
| FOCUS-01 | У вкладок полки и карточек rail появились реальные `FocusRequester`/`focusable()`; фокус запрашивается до и после прокрутки, повторно — по кадрам, а не по `delay`. | `ui/components/TopShelf.kt` |
| FOCUS-02 | Автолистание героя остановлено, пока `shelfFocus >= 0`, открыто меню/папка/поиск/CC/погода. | `ui/HomeScreen.kt` |
| FOCUS-03 | Возврат из Поиска в Control Center больше не теряет слой: `onClose(true)` возвращает фокус капсуле/панели, а не плитке сетки. | `ui/components/ControlCenter.kt`, `ui/HomeScreen.kt` |
| FOCUS-04 | PIN-пад: курсор зеркалится в реальный Android-фокус через `LaunchedEffect(row, column, forgotFocused, mode, onForgot)`; слепой `delay(400)` заменён на `requestWhenAttached()`. | `ui/components/PinPad.kt` |
| FOCUS-05 | **Закрыт политикой no-op:** лаунчер — HOME-поверхность; BACK на корне не уводит из него. `MainActivity` no-op + `HomeScreen` поглощает `Key.Back`; документ `06_NAVIGATION_FOCUS` приведён к коду, добавлен real-key тест. | `MainActivity.kt`, `HomeScreen.kt`, `UI_UX_UNIFICATION/06_NAVIGATION_FOCUS.md` |
| FOCUS-06 | Память папки хранится по package key, а не по индексу, и переживает process restore (`rememberSaveable` + `mapSaver`). | `ui/HomeScreen.kt`, `ui/FolderOverlay.kt` |
| FOCUS-07 | BACK из бокового списка Settings сначала возвращает в сетку/строки, затем — по истории страниц. | `ui/SettingsScreen.kt` |
| FOCUS-08 | `cancelPendingHold()` вызывается при `open()` и `back()`, поэтому удержание не срабатывает после ухода со страницы. | `ui/SettingsScreen.kt` |
| FOCUS-09 | Убраны слепые `delay(60)` в Settings/Folder/TvMenu и `delay(400)` в PinPad; вместо них — `withFrameNanos`-retry. `delay(32)` оставлен только как кадр ожидания анимации списка; Home accessibility-retry пока остаётся. | `SettingsScreen.kt`, `FolderOverlay.kt`, `TvMenu.kt`, `PinPad.kt` |
| FOCUS-10 | `LocalReduceMotion` из системного `ValueAnimator.areAnimatorsEnabled()`; focus-анимации `CardShell`, `TvKeyCap`, `ProfileDisc`, `AvatarDisc`, `WeatherCapsule`, `TvResultRow`, `SettingsRow`, `ChooserCategoryRow`, `SaverTile`, `TvMenu`, `SectionBar`, `ControlCenter` учитывают его. Пользовательских настроек силы/скорости/альтернативного индикатора пока нет. | `ui/Theme.kt` и перечисленные компоненты |
| FOCUS-11 | `pageFocus` и история страниц — `rememberSaveable`. | `ui/SettingsScreen.kt` |
| FOCUS-12 | Settings больше не делает `snapTo(0)` при каждой смене страницы: сохранённая строка открывается без прыжка вверх и обратной прокрутки. | `ui/SettingsScreen.kt` |
| FOCUS-13 | Меню полки якорится к реальной карточке (`shelfCardAnchor()`), а не к фиксированному `Offset(960f, 660f)`. | `ui/components/TopShelf.kt`, `ui/HomeScreen.kt` |
| FOCUS-14 | Удаление папки требует второго подтверждения в `TvMenu`; отмена оставляет папку. | `ui/HomeScreen.kt` |
| FOCUS-15 | Прокрутка rail — минимальное открытие карточки (`animateScrollBy`), а не `animateScrollToItem`; карточка не прыгает под курсором. | `ui/components/TopShelf.kt` |
| FOCUS-17 | `WeatherPanel` и `ProfilePicker` больше не поглощают KeyUp и необработанные KeyDown; системные volume/media/vendor-клавиши проходят дальше. | `ui/components/WeatherPanel.kt`, `ui/components/ProfilePicker.kt` |
| FOCUS-18 | Ветки капсулы и погоды больше не возвращают `true` на любое необработанное событие; Menu/Search/Assist обрабатываются локально, а не проваливаются в сетку. Вкладки полки больше не отдают Menu/Search в dock-ветку. | `ui/HomeScreen.kt` |
| FOCUS-16 | Закрытие Search/ASSIST с полки возвращает фокус карточке полки: Home не перехватывает его сеткой, `focusTick` доходит до `TopShelf`. | `ui/HomeScreen.kt`, `ui/components/TopShelf.kt` |
| FOCUS-20 | `TvMenu` учитывает Reduce Motion для появления/исчезновения и подсветки строк. | `ui/components/TvMenu.kt` |
| FOCUS-21 | Если свободных аватаров нет, показывается явное сообщение, оно становится реальным focus target, стрелки/OK не двигают занятые диски, Back закрывает выбор. | `ui/components/ProfilePicker.kt` |
| FOCUS-19 | Setup-wizard использует `requestWhenAttached` вместо прямого `requestFocus` для строк, кнопок, root и результатов поиска города. | `ui/SetupScreen.kt` |
| FOCUS-22 | Down на последней строке страницы папки открывает следующую страницу в той же колонке; на последней странице остаётся на месте. Тест `HomeScreenTest.folder_down_at_the_page_end_opens_the_next_page`. | `ui/FolderOverlay.kt`, `HomeScreenTest.kt` |

### I.1. Проверки

- `compileDebugKotlin` + `compileDebugAndroidTestKotlin` — **BUILD SUCCESSFUL**.
- `testDebugUnitTest` — **390 тестов, 0 падений, 0 ошибок, 0 пропусков**.
- Новые/обновлённые тесты: `PinPadInputTest.arrow_moves_the_real_focus_to_the_next_pin_key`,
  `HomeScreenTest` (подтверждение удаления папки, Down на краю страницы папки),
  `MainActivityInputTest.back_at_the_root_keeps_home_resumed`,
  `SettingsScreenTest.back_from_the_category_rail_returns_to_the_grid_before_the_page`,
  `TopShelfTest.the_focused_card_is_the_real_android_focus_target`,
  `TopShelfTest.the_selected_section_pill_is_the_real_android_focus_target`.
- **androidTest не запускались**: устройства/эмулятора нет. Компиляция androidTest успешна,
  но это не заменяет прогон.
- Визуальная проверка на ТВ, input-to-visual latency, Perfetto, 4K, IR/Bluetooth и TalkBack —
  по-прежнему **NOT MEASURED** (см. §H).

### I.2. Что осталось до полного закрытия плана

1. FOCUS-05 закрыт: на корне выбран no-op, документ синхронизирован.
2. Phase 1: вынести `FocusIdentity`/`FocusMemory` в общий слой, чтобы память фокуса не
   дублировалась в каждом экране.
3. Phase 2: единый `BackChain` вместо локальных `BackHandler`/`Key.Back`-веток.
4. Phase 4: пользовательские `Reduce Motion` / сила подсветки / альтернативный индикатор
   и `FocusMotion` (сейчас есть только системный Reduce Motion).
5. Полный прогон androidTest на устройстве (FOCUS-19/21/22 закрыты в коде, но UI-тесты
   не выполнены из-за отсутствия эмулятора).

---

## 0. Краткое резюме

1. **Единого Focus Engine на все экраны нет.** На уровне Activity есть один общий input-конвейер
   (`TvRemoteActivity` + `TvKeyBurstFilter`), но дальше почти каждая поверхность ведёт собственный
   курсор: `HomeScreen`, `FolderOverlay`, `SettingsScreen`, `ControlCenter`, `SearchOverlay`,
   `PinPad`, `ProfilePicker`, `SetupScreen`, `TvMenu`, `SaverScreen`, `WeatherPanel`.
   Внутри Главной модель действительно единая и аккуратная; назвать её общим движком всего
   лаунчера нельзя. Это главный архитектурный вывод аудита.

2. **Главная сделана правильно по схеме «курсор + один реальный focus target на ячейку».**
   `focusIndex` — источник истины, Compose-фокус только зеркалит его для TalkBack. Этот паттерн
   надо расширять, а не заменять вторым движком.

3. **Самые серьёзные дефекты находятся на стыках слоёв, а не в стрелках внутри сетки:**
   - полка рекомендаций и её вкладки не имеют реальных focus target, поэтому TalkBack остаётся
     на сетке, пока визуально фокус ушёл на карточку;
   - герой автоматически листается, даже когда пользователь уже стоит на карточке;
   - Поиск из Control Center закрывает панель и после возврата ставит фокус на плитку сетки, а не на капсулу/панель, откуда пришли;
   - в PIN-клавиатуре курсор двигается, а Android-фокус остаётся на первой клавише;
   - BACK на корне Главной и в паре состояний молча поглощается; документ `06_NAVIGATION_FOCUS`
     при этом обещает выход в систему;
   - фокус папки и память папки хранят индекс, а не идентичность приложения.

4. **Тайминги фокуса частично построены на «слепых» задержках** (`delay(60)`, `delay(32)`,
   `delay(350)`, `delay(400)`). Это не измеренная необходимость, а обход гонок layout/accessibility.
   Их нужно заменить на ожидание прикрепления узла и отменяемый retry, сохранив только
   действительно нужный accessibility-retry.

5. **Reduce Motion не применяется к большинству focus-анимаций** (на момент аудита).
   `motionDuration()` использовался в крупных переходах, но `CardShell`, `ControlCenter`,
   `SettingsRow`, `TvKeyCap`, `WeatherCapsule`, `TvResultRow`, `SectionBar` анимировали фокус
   безусловно. Частично исправлено в §I: системный Reduce Motion теперь учитывают основные
   focus-компоненты; пользовательских настроек скорости, силы подсветки и альтернативного
   индикатора фокуса пока нет.

6. **Измерений input-to-visual, Perfetto и Macrobenchmark в этом аудите нет.** Эмулятор в текущем
   окружении не поднимается (см. §H), устройства нет. Никакие субъективные оценки не выдаются
   за измерения. Unit-тесты выполнимы и проверены; androidTest требуют устройства.

---

## A. Текущее состояние системы управления

### A.1. Слои

```
Android KeyEvent
    ↓
TvRemoteActivity.dispatchKeyEvent
    ↓
TvKeyBurstFilter (склеивает Down/Up-«взрывы» удержания)
    ↓
Window / Compose focus node
    ↓
onPreviewKeyEvent (Home: idle timer, release bookkeeping)
    ↓
onKeyEvent конкретной поверхности
    ↓
собственный курсор поверхности (Int / flag / index)
    ↓
LaunchedEffect → FocusRequester.requestFocus() / requestWhenAttached()
    ↓
Android input focus (TalkBack читает этот узел)
```

### A.2. Общий input-конвейер

**`input/TvKeyBurst.kt`** — `TvKeyBurstFilter`:
- склеивает пары Down/Up, которые некоторые пульты и эмулятор присылают на каждое повторение;
- отдаёт первый KeyDown, глотает пары, последний KeyUp отдаёт после `SETTLE_MS = 60`;
- реальный auto-repeat (`repeatCount > 0`) пропускает;
- `LOST_UP_MS = 1_000` закрывает потерянный release;
- фильтрует только DPAD, ENTER, NUMPAD_ENTER, BACK, MENU, SEARCH, ASSIST;
- устанавливается в `TvRemoteActivity`, от которой наследуются `MainActivity`,
  `SettingsActivity`, `SetupActivity`.

**`ui/components/TvKeyRepeat.kt`**:
- `firstPress()` отличает первое нажатие от повтора;
- `startRepeat()` даёт паузу `400 мс`, затем шаг `100 мс`;
- `isOneShot()` перечисляет стрелки/OK/BACK/MENU/SEARCH/ASSIST;
- текстовые клавиши сохраняют системный repeat.

**Сильные стороны:** двойная обработка удержания закрыта системно, а не в каждом экране;
release после закрытия слоя не «залипает» благодаря `onPreviewKeyEvent` + `release()`.

**Ограничения:**
- это нормализация событий, а не Focus Engine;
- фильтр не различает «быстрое второе нажатие» и «повтор» в пределах 60 мс;
- фильтр не обрабатывает `ACTION_MULTIPLE` и IR-последовательности, отличные от пары Down/Up;
- он не знает о слоях, диалогах и приоритете BACK.

### A.3. Главная: лучшая часть системы

`ui/HomeScreen.kt` (≈2186 строк) использует не Compose-автофокус, а ручной курсор:

- `focusIndex: Int` (`rememberSaveable(initialItems)`) — текущая плитка;
- `targetFor(dx, dy)` — чистая арифметика по `TvOs.COLUMNS = 6`;
- `moveFocus()` — единственная точка изменения курсора;
- `tileRequesters: Map<item.key, FocusRequester>` — по одному реальному target на ячейку;
- `requestWhenAttached()` (private, строки 159–166) — три попытки с `withFrameNanos`, без
  «слепого» `delay`;
- сохранение позиции: `lastFocusKey` (живой ключ) + `KEY_FOCUS_PACKAGE` с дебаунсом
  `FOCUS_SAVE_MS = 400`; восстановление `indexOfHomeFocus()` по package, а не по индексу;
- папки: `folderFocus: MutableMap<String, Int>` — помнит ячейку папки между открытиями;
- `shelfFocus`, `shelfSelected`, `sectionFocused`, `capsuleFocused`, `weatherFocused` —
  отдельные состояния для полки, вкладок и верхних капсул;
- BACK: `BackHandler` для edit/library + собственный `Key.Back` в `onKeyEvent`;
- long press: `holdJob`, `longPressHandled`, `MENU_HOLD_MS = 350`,
  `FOLDER_HOLD_MS = 750`, `FOLDER_CONFIRM_MS = 300`.

Внутри Главной это цельная модель. Проблемы начинаются там, где к ней добавляются полка,
верхние капсулы и внешние слои.

### A.4. Курсоры по поверхностям

| Файл | Модель | Реальный focus target |
|---|---|---|
| `HomeScreen.kt` | `focusIndex` + `shelfFocus` + `sectionFocused` + capsule/weather flags | плитки/капсулы/погода/pickup; **полка и вкладки — нет** |
| `FolderOverlay.kt` | `focusIndex`, `titleFocused`, `page`, edit/menu | плитки папки, title, pickup |
| `SettingsScreen.kt` | `page`, `focusIndex`, `pageFocus`, `history`, `inSidebar`, `sidebarIndex`, grid/list/doc | строки, sidebar, плитки, абзацы документов |
| `ControlCenter.kt` | `focusId` + геометрический `nextCcFocusId` | плитки и круги |
| `SearchOverlay.kt` | `zone`, `KeyboardState.row/column`, `resultIndex` | клавиши клавиатуры и строки результатов |
| `TvKeyboard.kt` | `KeyboardState` | клавиши `TvKeyCap` |
| `PinPad.kt` | `row`, `column`, `forgotFocused`, digits | клавиши есть, но **курсор не зеркалится** |
| `ProfilePicker.kt` | `focusIndex` (диски), `focusIndex` (аватары) | диски/аватары |
| `SetupScreen.kt` | `page`, `focus`, `inButtons`, `buttonIndex` | строки, Back, Primary |
| `TvMenu.kt` | `focusIndex` | строки меню |
| `SaverScreen.kt` | корневой focus node | только корень |
| `WeatherPanel.kt` | корневой focus node | только корень |

Вывод: **реализаций фокуса много, но они не конкурируют за один и тот же экран**. Это не
«второй движок», а набор локальных движков. Задача — свести их к общему контракту, не ломая
существующие курсоры.

### A.5. Зеркалирование в Android-фокус

Правильный паттерн уже есть:

```kotlin
LaunchedEffect(focusIndex) {
    tileRequesters[focusIndex]?.requestFocus() ?: focusRequester.requestFocus()
}
```

Он работает в Home (плитки), Folder (плитки/title), Settings (строки/грид/sidebar),
Control Center, Search, ProfilePicker, AvatarPicker, Setup, TvMenu.

Где паттерн нарушен:
- **полка и вкладки** (`TopShelf`): есть `semantics { onClick }`, но нет `focusable()` и
  `FocusRequester`; Home не запрашивает карточку/вкладку;
- **PinPad**: реальные target есть, но запрос выполняется только при открытии (дважды, с
  `delay(400)`), а не при каждом `row/column`;
- **WeatherPanel/Saver**: только корень, что для read-only поверхности приемлемо, но поведение
  клавиш надо ограничить;
- **Setup**: запросы без `requestWhenAttached`/layout gate.

### A.6. Что уже сделано хорошо

- BACK не «проваливается» из панели в сетку: панель потребляет его первой.
- Меню, открытое удержанием OK, ждёт release (`suppressOkUntilRelease`, `OK_ARM_QUIET_MS`).
- Удаление приложения/пакета закрывает меню, у которого больше нет плитки.
- Папка помнит ячейку и зеркалит её наружу на каждом шаге, а не только при закрытии.
- Control Center использует геометрический FocusFinder-подобный выбор, а не порядок массива.
- Возврат из внешнего приложения обрабатывается `resumeTick`/`focusTick`.
- `TvFocus` свёл кольца капсул/дисков/чипов в один контракт.
- Есть содержательные тесты: `HomeScreenTest`, `SettingsScreenTest`, `SearchTest`,
  `KidsGateTest`, `CcFocusNavigationTest`, `PinPadInputTest`, `TvKeyBurstFilterTest`,
  `TvKeyRepeatTest`, `WeatherPanelTest`, `TopShelfTest`.

### A.7. Где система ломается

Критические и высокие риски перечислены в §F. Кратко:

1. полка/вкладки не имеют accessibility-фокуса;
2. авто-листание героя меняет выбор под пользователем;
3. Поиск поверх Control Center теряет фокус при закрытии;
4. PIN-клавиатура не зеркалит курсор;
5. BACK на корне Главной — no-op, хотя документация обещает system home;
6. память папки/фокуса — индекс, а не package/id;
7. «слепые» задержки фокуса;
8. Reduce Motion и пользовательские настройки движения неполны;
9. процесс-восстановление Settings теряет историю и память страниц;
10. удаление папки без подтверждения.

---

## B. Focus Engine: что предлагается вместо второго движка

### B.1. Принцип

Не создавать второй движок перемещения. Существующие курсоры (`focusIndex`, `shelfFocus`,
`focusId`, `page` и т. д.) остаются источником истины. Добавляется **тонкий слой
оркестрации**: кто владеет фокусом, как его вернуть, какой слой первым получает BACK,
какой target зеркалит курсор.

Новый слой не должен содержать `targetFor()`/геометрию Главной. Геометрия остаётся там, где
она уже есть.

### B.2. Компоненты

`ui/focus/FocusIdentity.kt`
- стабильные идентификаторы вместо «сырых» индексов:
  `HomeTile(package)`, `HomeFolder(folderId)`, `ShelfCard(section, itemId)`,
  `Section(section)`, `FolderTile(folderId, package)`, `SettingsRow(page, rowKey)`,
  `SettingsGrid(page, clipId)`, `MenuEntry(menuId, entryId)`, `PinCell(row, column)`,
  `Profile(id)`;
- fallback на индекс, если идентификатор исчез.

`ui/focus/FocusHost.kt`
- composition-local `FocusHost`/`FocusCoordinator` в `LauncherRoot` и в каждой Activity;
- `register(surfaceId, zOrder, onBack, canTakeFocus)`;
- стек слоёв: root grid → shelf → folder → menu → picker → PIN → saver;
- `currentOwner`, `lastOwner`, `restoreTarget`;
- `requestFocus(target)` с `requestWhenAttached` и отменой по первому реальному input-событию.

`ui/focus/FocusTarget.kt`
- `Modifier.tvFocusTarget(identity, requester)`:
  `focusRequester(...).focusable()` + семантика + единая регистрация;
- для карточек полки: `RailCard`/`SectionPill` получают `focusRequester` и `focusable()`,
  а Home запрашивает их, когда `shelfFocus >= 0` или `sectionFocused`.

`ui/focus/BackChain.kt`
- один приоритет: context menu → dialog/popup → folder → picker/PIN → library/shelf →
  previous screen → root policy;
- существующие `BackHandler` не удаляются, а регистрируются в цепочке с явным `id`;
- ровно один обработчик получает событие; повторное нажатие во время закрытия игнорируется.

`ui/focus/FocusMotion.kt`
- `motionDuration` уже есть; добавить `FocusStyle` (scale intensity, ring strength, motion
  speed, alternative indicator) и persisted prefs;
- все focus-анимации (`CardShell`, `ControlCenter`, `SettingsRow`, `TvKeyCap`,
  `WeatherCapsule`, `TvResultRow`, `SectionBar`, `TvMenu`) обязаны читать его.

`ui/focus/FocusRestore.kt`
- `saveState`/`restoreState` по identity, а не по `Int`;
- для Settings: `pageFocus` и `history` через `rememberSaveable`/`mapSaver`/`listSaver`;
- для папок: `folderId + package` вместо `Int`;
- для полки: `section + itemId` вместо `shelfSelected`.

### B.3. Состояние, которое должен знать движок

| Состояние | Сейчас | Предлагаемое место |
|---|---|---|
| текущий экран | локальные `page`/`openFolder` | `FocusHost.currentSurface` |
| активный контейнер | boolean-флаги на экране | `FocusLayer.container` |
| выбранный элемент | `focusIndex`/`focusId` | identity + index fallback |
| предыдущая позиция | `pageFocus`, `folderFocus` | `FocusMemory` |
| направление | аргумент `move()` | остаётся локальным |
| доступность | проверки в каждом move | `isTargetAvailable(identity)` |
| диалог/меню | локальные nullable | `FocusLayer` стек |
| анимация | локальные `Animatable` | слой не блокирует ввод, только закрытие |
| переход экрана | `launching`/`closing` | `FocusLayer.state` |

### B.4. BACK-политика

Текущая фактическая цепочка:

```
меню → панель/диалог → папка → библиотека → док → корень (no-op)
```

Для Главной на корне `Key.Back` всегда возвращает `true`, а `MainActivity` устанавливает
`OnBackPressedCallback`, который ничего не делает. Поэтому «home → system» из документа
`06_NAVIGATION_FOCUS` не выполняется.

Нужно принять одно решение и зафиксировать его тестом:

- **вариант A (рекомендуется для launcher-as-HOME):** BACK на корне — no-op, потому что
  системный home и есть этот launcher; документ исправить;
- **вариант B (если нужен выход):** на корне вызвать `moveTaskToBack(true)` или старт
  системного HOME; тогда `Key.Back` не должен возвращать `true` в этом состоянии.

Нельзя оставлять расхождение «документ обещает system, код молча глотает».

### B.5. Порядок внедрения

1. Добавить `FocusIdentity` и `FocusMemory`, не меняя поведение.
2. Перевести Home shelf/section на реальные target’ы и identity.
3. Ввести `FocusHost` и `BackChain`, зарегистрировать существующие слои.
4. Перевести Folder, Settings, Control Center, Search, PinPad, Setup.
5. Ввести `FocusMotion`/`FocusStyle` и настройки.
6. Только после этого — убирать «слепые» задержки и оптимизировать скролл.

---

## C. Совместимость с пультами

### C.1. Что уже поддержано

- стандартный D-pad: UP/DOWN/LEFT/RIGHT/CENTER;
- ENTER/NUMPAD_ENTER как альтернатива CENTER;
- BACK, MENU, SEARCH, ASSIST;
- удержание стрелок через `TvKeyRepeat`;
- пары Down/Up от эмулятора/некоторых пультов через `TvKeyBurstFilter`;
- реальный auto-repeat (`repeatCount > 0`);
- текстовый ввод через `unicodeChar` (Search, CitySearch, PIN digits);
- BACKSPACE/DELETE в текстовых слоях;
- никаких vendor-specific `KeyCode` и привязок к бренду.

### C.2. Риски

1. **IR-пульты** могут присылать не только пары Down/Up, но и `ACTION_MULTIPLE`/серии с
   нестандартным `repeatCount`. Фильтр это не покрывает; нужен ручной прогон на IR.
2. **Кнопки приложений** (Netflix/YouTube и т. п.) обычно приходят как обычные keycode или
   intent; лаунчер не должен их перехватывать. Сейчас он их не перехватывает, но модальные
   поверхности `WeatherPanel` (все клавиши), `ProfilePicker`/`ProfileAvatarPicker` (все
   KeyUp) и `SaverScreen` (все события, осознанно) могут съесть системную/media-клавишу.
3. **Volume/MEDIA keys** не должны блокироваться, кроме заставки, где это осознанное правило
   «любая клавиша будит». Для Weather/Profile/PIN это надо ограничить.
4. **Быстрые двойные нажатия** в пределах 60 мс склеиваются фильтром. Для игровых приложений
   это неважно, для лаунчера — почти неважно, но тест на два быстрых BACK/OK отсутствует.
5. **Клавиатура/мышь** (TV-боксы) проходят как обычные KeyEvent; специальной поддержки нет,
   но и поломки нет.
6. **Не проверено:** реальный ТВ, Bluetooth-пульт, IR-пульт, пульт с колесом, vendor-кнопки.
   Эмулятор в этом окружении не запустился.

---

## D. Движение и анимация

### D.1. Существующие параметры

| Параметр | Где | Значение |
|---|---|---|
| `TvOs.FOCUS_MS` | `TvMetrics.kt` | 180 мс |
| `TvOs.SCROLL_DURATION_MS` | `TvMetrics.kt` | 300 мс |
| `SURFACE_ENTER_MS` | `TvMetrics.kt` | 240 мс |
| `motionDuration(ms, reduceMotion)` | `TvMetrics.kt` | 0 при Reduce Motion |
| `FOLDER_ZOOM_MS`, `LAUNCH_START_DELAY_MS`, `LIBRARY_MOTION_MS` | `HomeScreen` | локальные |
| `MENU_APPEAR_MS`, `MENU_EXIT_MS`, `OK_ARM_QUIET_MS` | `TvMenu` | локальные |
| `EDIT_HOLD_MS`, `FOLDER_HOLD_MS`, `FOLDER_CONFIRM_MS` | `HomeScreen` | 350 / 750 / 300 |
| `PREVIEW_HOLD_MS` | `SettingsScreen` | 600 |

Единая кривая фокуса — `tween(FOCUS_MS)`; пружин нет; это предсказуемо и соответствует
«без искусственных задержек».

### D.2. Где Reduce Motion не применяется

- `CardShell`: `iconScale`, `confirmScale`, `jiggle` (`tween(TvOs.FOCUS_MS)`).
- `ControlCenter`: fill/grow/capsule/ring (`tween(TvOs.FOCUS_MS)`).
- `SettingsRow`, `SaverTile`, chooser category: `tween(TvOs.FOCUS_MS)`.
- `TvKeyCap`: `tween(TvOs.FOCUS_MS)`.
- `WeatherCapsule`: grow/ring.
- `TvResultRow`: focus amount.
- `ProfileDisc`/`AvatarDisc` в `ProfilePicker`: focus scale/ring.
- `TopShelf.SectionBar`: pill alpha.
- `TvMenu`: appear/exit, нет параметра `reduceMotion`.

Это нарушает §10 и §5: настройка Reduce Motion должна убирать не только page-переходы,
но и focus-масштаб/переходы, если пользователь этого хочет. Сейчас она влияет только на
крупные переходы и частично на `TopShelf`/`Folder`/`Home`.

### D.3. Клиппинг и z-order

- `CardShell` задаёт `zIndex(2f)` фокусу и отдельный слой тени — это правильно.
- `Settings` grid/list используют `clipToBounds()` с `GRID_HALO`/`LIST` padding, чтобы
  растущая плитка не обрезалась; это осознанный компромисс, требует визуальной проверки.
- `FolderOverlay` панель имеет `clip(RoundedCornerShape(...))`; фокус-масштаб плитки у края
  панели может обрезаться тенью/кантом. Не подтверждено без устройства.
- `ControlCenter` панель без `clipToBounds`; фокус-плитка может выйти за панель — визуально
  надо проверить.
- `TopShelf` `LazyRow` + `graphicsLayer` карточки: тень может обрезаться краем rail/скролла.

### D.4. Пользовательские настройки

Сейчас нет:
- скорости анимации (кроме системного animator scale);
- силы масштаба фокуса;
- силы/толщины подсветки;
- отключения эффектов;
- альтернативного индикатора (рамка вместо заливки/масштаба).

Нужен persisted `FocusPreferences` (без новых permissions/зависимостей) и строки в Settings.

---

## E. Таблица аудита навигации

| Экран | Навигация | Восстановление фокуса | Проблемы |
|---|---|---|---|
| **Главная: док + сетка** | `focusIndex` + `targetFor`; Up из верхнего ряда → полка/капсула; Down из дока → библиотека; Left/Right с клампами | package через `KEY_FOCUS_PACKAGE`, живой `lastFocusKey`, `indexOfHomeFocus`; `folderFocus`; `focusTick` после внешнего приложения | BACK на корне no-op (FOCUS-05); после shelf/search фокус уходит на плитку (FOCUS-16); при гонке debounce возможен откат к lastFocusIndex/0 (P3, не выделен отдельным ID) |
| **Полка рекомендаций** | `shelfFocus`, `shelfSelected`, Left/Right, Up → вкладки, Down/Back → сетка, OK/MENU → меню | нет реального target; после закрытия меню/поиска/запуска запрашивается плитка, а не карточка | **FOCUS-01** (TalkBack), **FOCUS-02** (авто-листание под фокусом), **FOCUS-13** (якорь меню), **FOCUS-15** (скролл), **FOCUS-16** |
| **Вкладки полки** | `sectionFocused`, Left/Right с зацикливанием, Down/Back → карточки, Up → капсула | нет реального target | **FOCUS-01**; необработанные MENU/Search проваливаются в ветку сетки и действуют на плитку дока (FOCUS-18) |
| **Папка** | `focusIndex`, страницы, title, edit/pickup, меню | `initialFocusIndex` из `folderFocus[id]`; наружу на каждом шаге | **FOCUS-06** (индекс, не package), **FOCUS-14** (удаление без подтверждения), **FOCUS-22** (Down не перелистывает страницу), клиппинг (не подтверждён) |
| **Меню (Home/Folder/Shelf)** | `TvMenu.focusIndex`, Up/Down, OK, BACK/MENU | при закрытии parent-эффект; shelf-меню ставит `shelfFocus=shelfSelected` | **FOCUS-09** (`delay(60)`), **FOCUS-13** (якорь), **FOCUS-14**; строка «Uninstall» защищена системным подтверждением |
| **Control Center** | `focusId` + `nextCcFocusId` (геометрия), Left/Right/Up/Down, OK, Back | `requestClose(true)` → Home `capsuleFocused`; `returnFocus=false` при уходе в другой экран | **FOCUS-03** (Search закрывает панель через `onClose(false)`, возврат идёт на плитку сетки); `focusId` не сохраняется между открытиями |
| **Поиск** | `zone` KEYS/RESULTS, клавиатура, `resultIndex`, результаты | `LaunchedEffect(zone, resultIndex)` request; закрытие → Home grid | **FOCUS-03** (из CC возврат на плитку сетки), **FOCUS-16** (из полки); при закрытии нет явного `onFocusReturn` |
| **Настройки: корень/сайдбар/список/грид/документы** | `page`, `focusIndex`, `inSidebar`, `sidebarIndex`, grid/list/doc | `pageFocus` + `history` (только в памяти), `focusTick`; mirror через `requestFocus` после 60 мс | **FOCUS-07** (Back из сайдбара), **FOCUS-08** (hold не отменяется), **FOCUS-09** (60/32), **FOCUS-11** (process restore), **FOCUS-12** (сброс скролла) |
| **Настройки: заставка/chooser** | grid + sidebar, hold OK → preview | preview закрывается `focusTick++`, эффект возвращает строку/плитку | **FOCUS-08** (hold через Back), **FOCUS-09** |
| **PIN-клавиатура** | `row/column`, forgot, цифры, BACKSPACE | только стартовый `requesters[0,0]` (+ повтор через 400 мс) | **FOCUS-04** (курсор не зеркалится), **FOCUS-09** (400 мс) |
| **Выбор профиля** | `focusIndex`, Left/Right, OK, Back | `LaunchedEffect(focusIndex, focusTick)`; `pickerFocusTick` после PIN | в целом хорошо; все KeyUp потребляются (FOCUS-17) |
| **Аватары** | `focusIndex`, сетка, move через `ProfileCatalog.avatarMove` | `LaunchedEffect(focusIndex)` | **FOCUS-21** (если все эмодзи заняты, курсор стоит на занятом диске без подсветки), KeyUp consumption |
| **Setup wizard** | `page`, `focus`, `inButtons`, `buttonIndex` | `LaunchedEffect(page, focus, ...)` с `requestFocus`, без layout gate | **FOCUS-19** (холодный старт/переход), Up с первого ряда — намеренный dead-end |
| **Weather panel** | только корень; OK/Back закрывают | после `onClose` Home ставит `weatherFocused=true` | **FOCUS-17** (поглощает все клавиши) |
| **Saver** | корень; первое нажатие «взводит», второе закрывает | Home `focusTick++` после `onDismissed` | consume-all осознан; **FOCUS-17** для системных клавиш |
| **CitySearch** | keyboard/result zone, как Search | `LaunchedEffect(zone, resultIndex)` + `KeyboardKeys` mirror; после закрытия Settings `modalOpen=false` | в целом хорошо; **FOCUS-09** (60 мс в Settings), KeyUp consumption |
| **Документы/лицензии** | `docFocus` по абзацам, Up/Down, back | `docFocus` + `bringIntoView` + `delay(32)` | **FOCUS-09**, сброс `docFocus=0` при входе (намеренно) |
| **Диалоги/подтверждения** | строки/кнопки своих страниц | `pageFocus`/`open` | подтверждения есть у reset/delete profile/PIN, **нет у delete folder** (FOCUS-14) |

---

## F. Таблица проблем

Приоритеты: **P0** — фокус/пульт реально ломается; **P1** — доступность или заметное
поведение; **P2** — риск/качество; **P3** — низкий.

| ID | Проблема | Приоритет | Решение |
|---|---|---|---|
| FOCUS-01 | Полка и вкладки не имеют реального focus target: `RailCard`/`SectionBar` только `semantics { onClick }`, без `focusable()` и requester; Home при `shelfFocus>=0`/`sectionFocused` всё равно запрашивает плитку/капсулу. TalkBack читает не тот элемент; визуальный фокус и input focus расходятся. | **P0** | Передать `FocusRequester` в `RailCard`/`SectionBar`, добавить `.focusable()`, в Home запрашивать карточку/вкладку по identity; Compose-тест на `isFocused()` и TalkBack-описание. |
| FOCUS-02 | Авто-смена героя (`SHELF_ADVANCE_MS`) не исключает `shelfFocus >= 0`: пользователь стоит на карточке, а через 8 с выбор и герой меняются под ним. | **P1** | Добавить `shelfFocus >= 0` (и, при необходимости, `sectionFocused`) в guard; тест «фокус на карточке → время идёт → selected не меняется». |
| FOCUS-03 | Поиск из Control Center: `onOpenSearch` вызывает `activate()`, который делает `onClose(false)` и закрывает панель; после Back из Search Home ставит фокус на плитку сетки, а не на капсулу/панель, откуда пользователь пришёл. | **P2** | Определить политику возврата: для Search (не Activity) восстанавливать капсулу/CC через `FocusHost.restore(lastOwner)`; для внешних Settings/Saver — не возвращать панель; тест. |
| FOCUS-04 | PIN-клавиатура двигает `row/column`, но не вызывает `requestFocus` у новой клавиши; запрос есть только при открытии (и повтор через 400 мс). Android-фокус остаётся на `(0,0)`, TalkBack читает неверную цифру. | **P1** | `LaunchedEffect(row, column, forgotFocused)` → `requesters[row to column]?.requestFocus()`; убрать слепой повтор после layout gate. |
| FOCUS-05 | BACK на корне Главной и в состояниях `openFolder != null || menuOpen` возвращает `true` без действия; `MainActivity` ставит no-op `OnBackPressedCallback`. Документ обещает выход в system home — код этого не делает. | **P1** | Принять политику: либо HOME-root no-op (исправить документ), либо `moveTaskToBack`/system HOME на корне; тест на оба состояния. |
| FOCUS-06 | Фокус папки и `folderFocus` хранят `Int` (и сама карта — `remember`, не saveable); при удалении/переустановке приложения курсор может оказаться на другом приложении, а после process death память папок теряется. Home хранит package, папка — нет. | **P1** | `FocusIdentity(folderId, package)` + fallback nearest; `folderFocus` хранит package/id и сохраняется через `rememberSaveable`. |
| FOCUS-07 | В Настройках BACK из сайдбара вызывает `back()` и уходит на предыдущую страницу, а не возвращает в список. Сайдбар — часть страницы, не отдельный экран. | **P2** | В `Key.Back`/`BackHandler`: если `inSidebar`, сначала `inSidebar=false` и восстановить `focusIndex`; только второй BACK — `back()`. |
| FOCUS-08 | `back()`/`open()` не отменяют `holdJob`: удержание OK на плитке заставки может открыть preview уже после ухода со страницы; аналогично в других переходах. | **P2** | `cancelHold()` в `back()`, `open()`, `showCategory()`, `openPreview()`-переходах; тест «hold → Back → preview не появился». |
| FOCUS-09 | «Слепые» задержки фокуса: Settings `delay(60)`/`delay(32)`, Folder `delay(60)`, TvMenu `delay(60)`, PinPad `delay(400)`, Home accessibility `delay(350)`. Часть — обход layout/accessibility, часть — реальная задержка отклика. | **P2** | Общий `requestWhenAttached` + layout gate; accessibility-retry только при `isTouchExplorationEnabled`, отменять по первому input; убрать 60/32/400 где не нужны; измерить до/после. |
| FOCUS-10 | Reduce Motion не применяется к focus-анимациям; нет пользовательских настроек скорости/силы/альтернативного индикатора. | **P2** | `FocusMotion`/`FocusStyle` + persisted prefs; `motionDuration` во всех `tween`; строки Settings; тест «reduce → 0 мс / без масштаба». |
| FOCUS-11 | В Settings `pageFocus` и `history` — `remember`, не `rememberSaveable`: после process death страница сохраняется, а история/память строк нет; BACK идёт по `parentOf`, фокус применяется к чужой странице. | **P2** | `mapSaver`/`listSaver` или `FocusMemory` по identity; тест `StateRestorationTester`. |
| FOCUS-12 | `listScroll.snapTo(0)` на каждом `page` сбрасывает скролл, даже когда `pageFocus` возвращает строку; пользователь видит прыжок вверх и анимацию вниз. | **P2** | Восстанавливать scroll из сохранённой identity/строки; `snapTo` только при реально новом переходе. |
| FOCUS-13 | Меню карточки полки имеет фиксированный `anchor = Offset(960f, 660f)`, а не позицию карточки; требование «меню рядом с элементом» не выполнено. | **P2** | Передавать позицию выбранной карточки/пилюли; клампить к экрану; тест геометрии. |
| FOCUS-14 | «Удалить папку» сразу удаляет папку и раскладывает приложения; подтверждения нет, хотя пункт помечен `destructive`. | **P2** | Добавить confirm-страницу/диалог с безопасным default Cancel (как в Settings reset/delete profile); либо Undo. |
| FOCUS-15 | `ShelfRail.LaunchedEffect` вызывает `animateScrollToItem(target)` на каждый шаг фокуса, выравнивая карточку к началу; это не минимальный reveal и может дёргать rail. | **P2** | Минимальный scroll по `BringIntoView`/`scrollBy`; помнить позицию на секцию; тест кадров/скролла. |
| FOCUS-16 | Search/ASSSIST с полки оставляет `shelfFocus>=0`; при закрытии Home-эффект запрашивает плитку, а полка сохраняет визуальный фокус. | **P2** | Возвращать фокус на карточку/вкладку через identity; закрывать `shelfFocus` только при явном переходе в сетку. |
| FOCUS-17 | Модальные поверхности глушат лишние клавиши: WeatherPanel возвращает `true` на все; ProfilePicker/Avatar — на все KeyUp; Saver — осознанно на всё. Volume/media/MENU могут не дойти до системы. | **P3** | Ограничить `when` только навигацией/OK/Back; системные/media пропускать (кроме Saver); тест. |
| FOCUS-18 | Несогласованная обработка клавиш на верхнем chrome: капсулы/погода возвращают `true` на все клавиши; вкладки полки необработанные MENU/Search проваливают в ветку сетки, и они действуют на плитку дока. | **P3** | Явно определить глобальные клавиши для каждого слоя; вкладки не должны открывать меню плитки под собой; капсулы не должны глотать media/system. |
| FOCUS-19 | SetupScreen делает `requestFocus()` без `requestWhenAttached`; при холодном старте/переходе страницы узел может быть ещё не прикреплён, фокус остаётся на root. | **P3** | Тот же layout-gate/retry, что в Home; тест первого кадра. |
| FOCUS-20 | `TvMenu` не принимает `reduceMotion` и всегда анимирует appear/exit; закрытие может размонтировать узел до завершения корутины. | **P3** | Параметр `reduceMotion`; `onDismiss`/action в `finally`/`NonCancellable` либо явный lifecycle; тест. |
| FOCUS-21 | `ProfileAvatarPicker`: если все эмодзи заняты, `avatarStartIndex` даёт 0, но диск занят (`focused=false`, OK ничего не делает); стрелки не находят свободный диск, остаётся только Back. | **P3** | Empty-state/понятное сообщение, либо разрешить повторное использование с подтверждением; тест на все занятые эмодзи. |
| FOCUS-22 | В папке Down на последней строке страницы не перелистывает на следующую страницу; переход только Right в конце страницы. Поведение предсказуемо, но неочевидно. | **P3** | Либо документировать, либо добавить Down→следующая страница с тестом. |

---

## G. План улучшений

### G.1. Архитектурные изменения

1. **FocusHost + FocusLayer + BackChain** — оркестрация, не второй курсор.
2. **FocusIdentity/FocusMemory** — package/id вместо индексов.
3. **FocusTarget modifier** — единое зеркалирование для всех поверхностей.
4. **FocusMotion/FocusStyle** — persisted настройки движения и доступности.
5. **FocusRestore** — сохранение/восстановление между диалогами, страницами, process death.

### G.2. Фазы

#### Фаза 0. Безопасные точечные исправления (без смены архитектуры)

- FOCUS-02: guard `shelfFocus >= 0` в авто-листании.
- FOCUS-04: `LaunchedEffect(row, column, forgotFocused)` в PinPad.
- FOCUS-03: политика возврата Search→Control Center/капсула (или явно задокументировать возврат на сетку).
- FOCUS-08: `cancelHold()` на Back/переходах Settings.
- FOCUS-14: подтверждение удаления папки.
- FOCUS-05: зафиксировать политику BACK на корне (документ + тест).
- FOCUS-07: Back из сайдбара в список.

**Файлы:** `HomeScreen.kt`, `PinPad.kt`, `ControlCenter.kt`, `SearchOverlay.kt`,
`SettingsScreen.kt`, `FolderOverlay.kt`, `TopShelf.kt`.
**Критерий:** все новые тесты зелёные; визуально ничего не меняется, кроме исправленного
поведения.

#### Фаза 1. FocusIdentity + FocusMemory

- добавление `ui/focus/FocusIdentity.kt`, `ui/focus/FocusMemory.kt`;
- Home: `folderFocus`/shelf/section → identity, fallback index;
- Folder: package identity;
- Settings: `pageFocus`/`history` saveable;
- без изменения навигации.

**Файлы:** новые `ui/focus/*`, `HomeScreen.kt`, `FolderOverlay.kt`, `SettingsScreen.kt`.
**Риск:** сериализация `rememberSaveable`; закрывается `mapSaver`/`listSaver` и unit-тестами.

#### Фаза 2. FocusHost + BackChain

- composition-local host в `LauncherRoot`/Activity;
- регистрация слоёв: Home, shelf, folder, menu, picker, PIN, saver, search, weather, CC;
- один приоритет BACK, закрытие ровно одного слоя;
- возврат `lastOwner` после закрытия.

**Файлы:** `ui/focus/FocusHost.kt`, `BackChain.kt`, `LauncherRoot.kt`, `HomeScreen.kt`,
`FolderOverlay.kt`, `ControlCenter.kt`, `SearchOverlay.kt`, `PinPad.kt`,
`ProfilePicker.kt`, `SettingsScreen.kt`, `WeatherPanel.kt`, `SaverScreen.kt`.
**Риск:** конфликт с существующими `BackHandler`; внедрять по одному слою, сохраняя
текущие тесты BACK.

#### Фаза 3. Focus targets для полки/вкладок/меню

- `RailCard`/`SectionBar` получают requester/focusable;
- Home запрашивает shelf/section, а не плитку;
- shelf-меню якорится к карточке;
- `ShelfRail` — минимальный scroll, память позиции.

**Файлы:** `TopShelf.kt`, `HomeScreen.kt`, `TvMenu.kt`.
**Критерий:** TalkBack и `isFocused()` совпадают на каждой карточке/вкладке.

#### Фаза 4. Motion & accessibility

- `FocusMotion`/`FocusStyle`, persisted prefs;
- все focus-анимации через `motionDuration`;
- альтернативный индикатор (рамка/заливка) и сила подсветки;
- строки в Settings с локализацией RU/EN.

**Файлы:** `TvMetrics.kt`, `Theme.kt`/`Tokens.kt`, `SettingsScreen.kt`,
`CardShell.kt`, `ControlCenter.kt`, `TvKeyCap.kt`, `WeatherCapsule.kt`,
`TvResultRow.kt`, `TopShelf.kt`, `TvMenu.kt`, `data/SettingsPreferences` (если есть/новый).
**Риск:** не менять дефолтный вид; настройки только добавляют варианты.

#### Фаза 5. Убрать «слепые» задержки и улучшить скролл

- `requestWhenAttached` вынести в `ui/focus`;
- Settings/Folder/TvMenu/PinPad — layout gate вместо 60/32/400;
- Home 350 мс — только при TalkBack, отменять по вводу;
- минимальный scroll в rail, сохранение scroll на страницу/секцию.

**Файлы:** `HomeScreen.kt`, `SettingsScreen.kt`, `FolderOverlay.kt`, `TvMenu.kt`,
`PinPad.kt`, `TopShelf.kt`, `ui/focus/FocusRequest.kt`.
**Критерий:** нет задержки визуального фокуса; input-событие не ждёт таймера.

#### Фаза 6. Проверка

- unit/Compose-тесты на все исправления;
- ручной прогон на устройстве/эмуляторе: все экраны, быстрые серии, удержания,
  закрытие/возврат, удаление сфокусированного, длинные списки, 4K;
- Perfetto/Macrobenchmark — только после появления устройства; результаты отдельно,
  без подмены субъективных оценок.

### G.3. Файлы, которые затрагиваются

**Новые (предположительно):**
- `app/src/main/java/com/whatuneed/ovisiontv/kotlin/ui/focus/FocusIdentity.kt`
- `app/src/main/java/com/whatuneed/ovisiontv/kotlin/ui/focus/FocusHost.kt`
- `app/src/main/java/com/whatuneed/ovisiontv/kotlin/ui/focus/FocusTarget.kt`
- `app/src/main/java/com/whatuneed/ovisiontv/kotlin/ui/focus/BackChain.kt`
- `app/src/main/java/com/whatuneed/ovisiontv/kotlin/ui/focus/FocusMotion.kt`
- `app/src/main/java/com/whatuneed/ovisiontv/kotlin/ui/focus/FocusMemory.kt`
- тесты рядом с существующими в `app/src/test` и `app/src/androidTest`.

**Изменяемые:** `HomeScreen.kt`, `FolderOverlay.kt`, `SettingsScreen.kt`,
`ControlCenter.kt`, `SearchOverlay.kt`, `PinPad.kt`, `ProfilePicker.kt`,
`SetupScreen.kt`, `TvMenu.kt`, `TopShelf.kt`, `SaverScreen.kt`, `WeatherPanel.kt`,
`TvKeyCap.kt`, `CardShell.kt`, `WeatherCapsule.kt`, `TvResultRow.kt`,
`TvMetrics.kt`, `LauncherRoot.kt`, `MainActivity.kt`, strings RU/EN.

### G.4. Риски и меры

| Риск | Мера |
|---|---|
| Регрессии в ручных курсорах | менять по одной поверхности; сохранять существующие тесты; не менять геометрию без нужды |
| Двойной BACK/двойное закрытие | `FocusHost` с состояниями `closing`; тесты на повторное нажатие |
| `FocusRequester` на удалённом узле | `requestWhenAttached`, try/catch, fallback на root |
| Потеря состояния при process death | `rememberSaveable` + identity, `StateRestorationTester` |
| Reduce Motion меняет привычный вид | дефолт = текущий; настройки опциональны |
| Нет устройства/эмулятора | не заявлять измерения; тесты на Compose + ручной чек-лист после появления устройства |
| Новые зависимости/разрешения | не добавлять; всё на существующих Compose/Android API |

### G.5. Критерии завершения

1. У каждой интерактивной поверхности есть ровно один реальный focus target,
   соответствующий её курсору; TalkBack и `isFocused()` совпадают.
2. BACK закрывает ровно один слой по документированной цепочке; повторное нажатие
   во время анимации ничего не ломает.
3. Удаление/переустановка/фильтрация не оставляет фокус на несуществующем элементе;
   восстановление идёт по identity, fallback — сосед.
4. `pageFocus`, `history`, позиция папки и полки переживают process recreation.
5. Reduce Motion и пользовательские настройки движения применяются ко всем focus-анимациям.
6. Нет «слепых» задержек, влияющих на визуальный отклик; accessibility-retry отменяется вводом.
7. Unit-тесты ≥ текущих 389 и зелёные; androidTest на API 34/1920×1080 зелёные; ручной
   чек-лист полного обхода подписан.
8. Измерения input-to-visual/Perfetto/4K/IR выполнены на реальном устройстве; до этого
   в отчёте стоит `NOT MEASURED`.

---

## H. Результаты тестов и что не проверено

### H.1. Выполнено в этом аудите

- `testDebugUnitTest` — **390 тестов, 0 падений, 0 ошибок, 0 пропусков** (текущий набор,
  результат прочитан из `app/build/test-results/testDebugUnitTest/*.xml`).
- Компиляция unit-тестов прошла; сборка не менялась.
- Статический аудит: прочитаны ключевые файлы, перечисленные в §A–F; размеры:
  `SettingsScreen.kt` 4218 строк, `HomeScreen.kt` 2186, `ControlCenter.kt` 1026,
  `TopShelf.kt` 964, `SaverScreen.kt` 928, `PinPad.kt` 688, `ProfilePicker.kt` 592,
  `SearchOverlay.kt` 450, `WeatherPanel.kt` 419, `TvMenu.kt` 354,
  `TvKeyBurst.kt` 164, `TvKeyRepeat.kt` 96.

### H.2. Существующие автоматические сценарии (не исчерпывающе)

- Home: фокус по стрелкам, папка открывается/закрывается, память ячейки папки,
  возврат из приложения, drag, удержание OK, меню, удаление папки, скрытие приложения,
  kids read-only, TalkBack-клик, неполный последний ряд.
- Settings: корень, переходы, Back, профили, PIN, лицензии, скролл, chooser заставки,
  hold-превью, hidden list, reset, языки, device/system pages.
- Input: `TvKeyBurstFilterTest` (7 сценариев), `TvKeyRepeatTest`,
  `PinPadInputTest`, `MainActivityInputTest` (реальные KeyEvent через Activity).
- Control Center: `CcFocusNavigationTest`, `ControlCenterTest`.
- Search/Weather/Saver/TopShelf: соответствующие Compose-тесты.
- Восстановление домашней позиции: `HomeFocusTest` — по package/folder id.

### H.3. Не выполнено и почему

- **androidTest / Compose UI на устройстве** — нет доступного эмулятора/устройства.
  В документе `UI_UX_UNIFICATION/06_NAVIGATION_FOCUS.md` зафиксирован прошлый прогон
  **185/185** на релизе 0.4.39; в этом аудите он не перезапускался.
- **Эмулятор**: AVD `hume-tv`/`hume-tv-aq`/`hume-tv-test` (API 34, x86) не регистрируются
  в ADB; `qemu-system-x86_64` живёт без загрузки, в логах
  `Critical: Failed to load opengl`, `Failed to load opengl32sw`, иногда
  `detected a hanging thread 'QEMU2 CPU0 thread'`. Пробованы GPU-режимы
  `auto`, `host`, `swiftshader`, `swiftshader_indirect`, `angle_indirect`, `guest`,
  `lavapipe`, `off`, а также отключение Vulkan/GLDirectMem. Устройство не появилось.
- **Input-to-visual latency**: не измерена. План: `adb shell atrace input gfx` /
  `dumpsys gfxinfo framestats`, сопоставление `eventTime` KeyDown с кадром фокус-масштаба.
- **Perfetto / Macrobenchmark**: в проекте нет benchmark-модуля; не добавлялся.
- **4K**: не проверялось; геометрия масштабируется через `TvScale`, но реальная панель
  не тестировалась.
- **IR/Bluetooth/вендорские пульты**: не проверялись; эмулятор не поднялся.
- **TalkBack голосовой проход**: не выполнялся; семантика проверялась только тестами.
- **Низкопроизводительный ТВ**: не проверялся.

### H.4. Непроверенные устройства

- реальный Android TV (любой вендор);
- 4K-панель;
- IR-пульт;
- Bluetooth-пульт с колесом/дополнительными кнопками;
- пульт с кнопками приложений (Netflix/YouTube и т. п.);
- ТВ с низкой производительностью (1–1.5 ГБ RAM, слабый GPU).

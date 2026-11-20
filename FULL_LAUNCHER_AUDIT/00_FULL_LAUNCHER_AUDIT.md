# Полный аудит лаунчера OVisionTV

- **Объект:** `OVisionTV_Kotlin`, пакет `com.whatuneed.ovisiontv.kotlin`.
- **Версия на момент отчёта:** 0.5.75 / versionCode 148.
- **Дата:** 20.11.2026.
- **Платформа аудита:** Android TV AVD `hume-tv`, API 34, x86, 1920×1080, пульт эмулируется key-событиями.
- **Режим:** полный обход по исходникам + Compose/instrumentation-тесты + ручная проверка экранов на эмуляторе.

Отчёт намеренно отделяет **проверенное** от **непроверенного**. Всё, что требует физического ТВ, реального пульта, 4K/HDR-декодера или опубликованного источника обновлений, помечено **NOT TESTED** / **NOT VERIFIED** и не выдаётся за результат.

---

## A. Общая оценка

Лаунчер — зрелый продукт с устойчивой архитектурой: экранные состояния отделены от меню и клавиатур, сетевые клиенты имеют общий контракт редиректов и лимитов чтения, Backup/Restore и Update разделены на движок и представление, а Remote-first взаимодействие покрыто тестами. В ходе аудита не найдено падений, мёртвых кнопок, поддельных успехов или блокирующих P0/P1-дефектов.

Сильные стороны:

- единая визуальная система `core.designsystem` и `LauncherColors`/`Tokens`;
- предсказуемый D-pad: строки, сетки, док, панели и оверлеи имеют явные точки входа и возврата;
- честные пустые/ошибочные состояния: нет фальшивого hero, нет «успеха» без результата;
- RU-локализация доведена до профессионального уровня, без кальки и машинного текста;
- Backup не показывает пользователю сырые английские исключения;
- Update не обещает GitHub-источник, пока источник реально не настроен;
- сетевые клиенты не следуют редиректам вслепую.

Что исправлено именно этим аудитом: 18 пользовательских дефектов и 56 русских строк (28 английских строк также уточнены). Подробности — в разделах E–H и в `FIXES.md`, запись «0.5.75: полный аудит лаунчера».

Ограничения оценки:

- эмулятор не заменяет физический Android TV: фокус, HDMI-CEC, ИК/BT-пульт, реальный декодер и 4K/HDR не проверены;
- публикация APK и реальный канал обновлений не входят в этот аудит;
- 12 предупреждений lint остаются, ошибок lint нет;
- `SettingsScreen.kt` остаётся крупным файлом (около 4 100 строк) — это долг сопровождаемости, не пользовательский дефект.

---

## B. Инвентаризация экранов

Список охватывает все пользовательские поверхности, найденные в `app/src/main/java/**` и ресурсах. «Проверено» означает: исходник прочитан, состояние и переходы сопоставлены с вызовами; где указан тест — поведение дополнительно исполнялось.

### B.1. Setup

| № | Экран / поверхность | Вход | Назначение | Состояния | Проверка |
|---|---|---|---|---|---|
| 1 | Welcome | первый запуск, `SetupActivity` | приветствие и старт | обычное | `SetupScreenTest` |
| 2 | Profile / avatar | Setup, смена профиля | имя, эмодзи, цвет | клавиатура, отмена | `SetupScreenTest` |
| 3 | Home apps | Setup | выбор приложений на Главную | список, пусто, «показать все» | `SetupScreenTest` |
| 4 | Weather | Setup | включение погоды, город, единицы | поиск, «Поиск…», ошибка сети, офлайн | `SetupScreenTest`, `CitySearchTest` |
| 5 | Screen saver | Setup | выбор Aerial/Clock/Minimal | выбранный режим, текущий Photos/Reddit | `SetupScreenTest` |
| 6 | Access / permissions | Setup | разрешения и системные переходы | разрешено, отказ, «больше не спрашивать» | `SetupScreenTest` |
| 7 | Finish | Setup | сводка и завершение | сводка, запуск Home | `SetupScreenTest` |
| 8 | City search | Weather в Setup | поиск города | загрузка, результаты, пусто, ошибка | `CitySearchTest` |
| 9 | Name keyboard | Profile | ввод имени | пусто, лимит, OK/BACK | `SetupScreenTest` |

### B.2. Home и его слои

| № | Экран / поверхность | Вход | Назначение | Состояния | Проверка |
|---|---|---|---|---|---|
| 10 | Home grid | запуск лаунчера | плитки приложений | обычное, пусто, скрытые, drag | `HomeScreenTest` |
| 11 | Dock | Home | избранные приложения | обычное, переполнение | `HomeScreenTest` |
| 12 | Folder overlay | плитка-папка | содержимое папки | пусто, переименование, удаление | `HomeScreenTest` |
| 13 | Hidden apps / folder | меню Home | скрытые приложения | список, восстановление | `HomeScreenTest` |
| 14 | App context menu | долгий OK / MENU | открыть, скрыть, переименовать, в папку | обычное, destructive-подтверждение | `HomeScreenTest` |
| 15 | Folder context menu | папка | открыть, переименовать, удалить | обычное, подтверждение | `HomeScreenTest` |
| 16 | Move-to-folder menu | меню приложения | выбор папки | список папок, «Новая папка…» | `HomeScreenTest` |
| 17 | New-folder keyboard | меню | имя новой папки | пусто, дубликат, OK/BACK | `HomeScreenTest` |
| 18 | Rename keyboard | меню | новое имя приложения | пусто, лимит, OK/BACK | `HomeScreenTest` |
| 19 | Search overlay | кнопка поиска / голос | поиск приложений | ввод, результаты, пусто | `SearchOverlayTest` |
| 20 | Control Center | верхняя панель | Wi-Fi, Bluetooth, питание, настройки | открыто, закрыто, permission denied | `ControlCenterTest` |
| 21 | Weather capsule | Home | текущая погода | есть данные, нет данных, выключено | `WeatherTest`, `HomeScreenTest` |
| 22 | Weather panel | OK на капсуле | прогноз по дням | загрузка, ошибка, 5 дней | `WeatherTest` |
| 23 | Shelf rail | Home | рекомендации приложений | пусто, загрузка, ошибка, «Для вас» | `TopShelfTest` |
| 24 | Shelf card | rail | запуск/трейлер/детали | обычное, disabled, focus | `TopShelfTest` |
| 25 | Online info overlay | карточка онлайн-полки | описание, трейлер, Flux | загрузка, ошибка, без сети | `OnlineInfoOverlayTest` |
| 26 | Trailer overlay | карточка | трейлер в диалоге | загрузка, play/pause, ошибка, BACK | `TopShelfTest` |
| 27 | Profile picker | аватар Home | смена профиля | список, Kids, PIN | `ProfilePicker` + `HomeScreenTest` |
| 28 | PIN pad | Kids/профиль | ввод PIN | ввод, ошибка, сброс | `KidsGateTest` |
| 29 | Empty Home | нет приложений | нейтральное приветствие | только EmptyHome | `HomeScreenTest` |
| 30 | Launch transition | запуск приложения | анимация перехода | обычное, отмена | `LaunchTransition` |
| 31 | Saver / Ambient | бездействие | заставка | Aerial, Clock, Photos, Reddit, fallback | `AmbientScreenTest`, `SaverPreviewTest` |

### B.3. Settings

| № | Экран / страница | Вход | Назначение | Проверка |
|---|---|---|---|---|
| 32 | Root | Settings | разделы и выход | `SettingsScreenTest` |
| 33 | Personalization | root | профили, тема, язык, погода | `SettingsScreenTest` |
| 34 | Profiles group | Personalization | список профилей | `SettingsScreenTest` |
| 35 | Profile edit | Profiles | имя, эмодзи, цвет | `SettingsScreenTest` |
| 36 | Profile delete | Profiles | подтверждение | `SettingsScreenTest` |
| 37 | Device group | root | дисплей, звук, питание, пульт | `SettingsScreenTest` |
| 38 | System | Device | Home-кнопка, startup, AirPlay | `SettingsScreenTest` |
| 39 | Startup | System | запуск приложения при старте | `SettingsScreenTest` |
| 40 | Startup when | Startup | условие запуска | `SettingsScreenTest` |
| 41 | Apps | root | список приложений, скрытие | `SettingsScreenTest` |
| 42 | Permissions | root/Device | состояние разрешений | `SettingsScreenTest` |
| 43 | Aerial catalogue | Device | сетка клипов, темы, фильтр | `SettingsScreenTest` |
| 44 | Saver main | root | текущий режим и параметры | `SettingsScreenTest` |
| 45 | Saver mode | Saver | Aerial/Photos/Reddit/Clock | `SettingsScreenTest` |
| 46 | Aerial themes | Saver | темы клипов | `SettingsScreenTest` |
| 47 | Aerial refresh | Saver | обновление каталога | `SettingsScreenTest` |
| 48 | Saver delay | Saver | задержка старта | `SettingsScreenTest` |
| 49 | Saver dim | Saver | затемнение | `SettingsScreenTest` |
| 50 | Saver session | Saver | длительность сеанса | `SettingsScreenTest` |
| 51 | Clock settings | Saver | формат, 24 часа, стиль | `SettingsScreenTest` |
| 52 | Photos settings | Saver | выбор фото, интервал | `SettingsScreenTest` |
| 53 | Reddit settings | Saver | сабреддиты, refresh now | `SettingsScreenTest` |
| 54 | Weather pages | root | город, единицы, включение | `SettingsScreenTest`, `WeatherTest` |
| 55 | Discovery pages | root | источники, онлайн-полка | `SettingsScreenTest` |
| 56 | About group | root | версия, обновления, документы | `SettingsScreenTest` |
| 57 | Updates | About | состояние и ручная проверка | `SoftwareUpdateScreenTest` |
| 58 | Legal | About | Terms/Privacy/Aerial/Licenses | `SettingsScreenTest` |
| 59 | Terms document | Legal | raw `terms_*` | `SettingsScreenTest` |
| 60 | Privacy document | Legal | raw `privacy_*` | `SettingsScreenTest` |
| 61 | Aerial sources | Legal | raw `aerial_sources_*` | `SettingsScreenTest` |
| 62 | Open-source licenses | Legal | raw licenses | `SettingsScreenTest` |

### B.4. Backup, Update, системные поверхности

| № | Экран / поверхность | Вход | Назначение | Проверка |
|---|---|---|---|---|
| 63 | Backup home | Settings | статус, создать/восстановить | `BackupScreenTest` |
| 64 | Backup create | Backup | категории и имя файла | `BackupScreenTest` |
| 65 | Backup source | Backup | SAF-выбор архива | `BackupScreenTest` |
| 66 | Backup preview | Backup | состав архива | `BackupScreenTest` |
| 67 | Backup progress | Backup | экспорт/импорт | `BackupScreenTest`, `BackupManagerInstrumentedTest` |
| 68 | Backup settings | Backup | пароль, хранение | `BackupScreenTest` |
| 69 | Backup password | Backup | ввод/подтверждение | `BackupScreenTest` |
| 70 | Software update | Settings/About | проверка, загрузка, установка | `SoftwareUpdateScreenTest` |
| 71 | Android file picker | Backup | внешний SAF | системный, **NOT TESTED** на всех OEM |
| 72 | Android permission dialogs | Setup/CC | внешние | системный, **NOT TESTED** на всех OEM |
| 73 | Android Home settings | Setup/System | выбор лаунчера | системный, **NOT TESTED** на всех OEM |

---

## C. Инвентаризация интерактивных элементов

Ниже перечислены все классы управляющих элементов и их фактические действия. Одинаковые по контракту строки сгруппированы; ни одна группа не является «декоративной» — у каждой есть обработчик, результат и обратная связь.

| Зона | Элементы | Действие → результат → обратная связь | Проверка |
|---|---|---|---|
| Setup: строки выбора | radio/check-строки Home apps, Aerial/Clock/Minimal, погода, разрешения | OK меняет выбор, сразу обновляет видимый статус; BACK возвращает на страницу | `SetupScreenTest` |
| Setup: главные кнопки | «Продолжить», «Назад», «Готово», «Настроить», «Разрешить» | навигация/запуск системного экрана; при отказе — Toast `settings_system_unavailable` | `SetupScreenTest` |
| Setup: город | поле, «Найти», результат, «Поиск…», «Повторить» | запрос → список → выбор; ошибка остаётся на странице | `CitySearchTest` |
| Setup: имя/аватар | клавиатура, эмодзи, цвет, OK/BACK | ввод → сохранение профиля → возврат | `SetupScreenTest` |
| Home: плитка | OK, долгий OK/MENU | запуск приложения; меню: открыть, скрыть, переименовать, в папку | `HomeScreenTest` |
| Home: папка | OK, MENU, удаление | открыть; переименовать/удалить с подтверждением | `HomeScreenTest` |
| Home: док | LEFT/RIGHT/OK | фокус и запуск избранного | `HomeScreenTest` |
| Home: поиск | ввод, стрелки, OK, BACK | фильтрация и запуск; BACK закрывает оверлей | `SearchOverlayTest` |
| Control Center | Wi-Fi, Bluetooth, питание, шестерёнка | Wi-Fi: один запрос имени, затем системные настройки; BT/питание — системный экран; шестерёнка — Settings | `ControlCenterTest`, `WifiNameRequestTest` |
| Погода | капсула, панель | OK открывает прогноз; ошибка не подменяется старым кэшем | `WeatherTest` |
| Полка | карточки, стрелки, OK, MENU | фокус по rail; OK — запуск/детали; MENU — контекст | `TopShelfTest` |
| Онлайн-карточка | OK, «Трейлер», «Flux» | описание → трейлер → Flux deep link; без Flux кнопка не обещает переход | `OnlineInfoOverlayTest` |
| Трейлер | play/pause, seek, BACK | управление ExoPlayer; BACK закрывает диалог | `TopShelfTest` |
| Профиль | аватар, список, PIN | смена профиля; Kids требует PIN | `HomeScreenTest`, `KidsGateTest` |
| Settings root | 8 разделов, BACK | переход в раздел; BACK в Home/Settings | `SettingsScreenTest` |
| Settings: строки | toggle, radio, value, action | toggle меняет prefs сразу; action открывает страницу/диалог; disabled-строки не фокусируются | `SettingsScreenTest` |
| Settings: сетка Aerial | категории, клипы, OK | фокус в sidebar меняет фильтр; OK включает/выключает клип; последний клип защищён | `SettingsScreenTest` |
| Settings: документы | абзацы, скролл, BACK | каждый абзац — отдельная фокус-цель; BACK возвращает в Legal | `SettingsScreenTest` |
| Backup | «Создать», «Восстановить», категории, «Далее», «Назад» | создание/выбор архива; пустой выбор — Toast, не мёртвая кнопка | `BackupScreenTest` |
| Backup: предупреждения | info/warning-строки | только чтение, не перехватывают OK и не крадут фокус | `BackupScreenTest` |
| Update | «Проверить», «Скачать», «Установить», «Отмена» | честные состояния; установка — системный installer | `SoftwareUpdateScreenTest` |
| Saver | OK/MENU/BACK, стрелки | выход/меню; BACK не оставляет чёрный экран | `AmbientScreenTest` |
| Все диалоги | OK, BACK, пункты меню | закрытие/подтверждение; destructive-пункт отделён | `HomeScreenTest` |

Отдельно проверено, что нет кнопок с пустым `onClick`, кроме намеренно read-only info-строк (они больше не кликабельны), и что disabled-строки не получают фокус.

---

## D. Локализация и тексты

### D.1. Целостность ресурсов

| Показатель | Результат |
|---|---|
| `<string>` в EN | 662 |
| `<plurals>` | 7 |
| `<string-array>` | 1 |
| Ключи EN / RU | 0 отсутствующих, 0 лишних |
| Несовпадения placeholder'ов | 0 |
| Неиспользуемые ключи | 0 |
| RU-плюралы | `one/few/many/other` у всех 7 |
| Изменено RU-строк | 56 |
| Изменено EN-строк | 28 |
| Добавлено ключей | 10 |
| Удалено ключей | 1 (`backup_auto_hint`, заменён точным `backup_retention_hint`) |

Единственный двойной пробел — намеренный `weather_high_low` (`%1$s: %2$d°  %3$s: %4$d°`), чтобы «H: 12°  L: 4°» не слипались. Это не дефект.

### D.2. Что исправлено в текстах

- Убраны кальки и неверные термины: «Минимализм» → «Минимальные», «5-ДНЕВНЫЙ ПРОГНОЗ» → «ПРОГНОЗ ПО ДНЯМ», «Загрузка версии» → проверка каталога, «во Flux» → «в приложении Flux».
- Уточнены системные пути: «Настройки → Главная», «Настройки → Персонализация → Заставка», «Настройки → Устройство → Каталог Aerial».
- Backup: ошибки больше не показывают английские `BackupException.message`; стадии маппятся в `backup_password_wrong`, `backup_error_integrity`, `backup_error_io`, `backup_error_restore`, `backup_error_format`.
- Update: текст больше не обещает GitHub, пока `OVISIONTV_UPDATE_URL` пуст; «Источник обновлений появится после публикации страницы релизов».
- Setup: «Разрешить» и «Настроить» без лишнего `›`; подсказка погоды говорит «до пяти дней», что совпадает с `days.take(5)`.
- Backup: «KB» и «MB» вынесены в `unit_kb`/`unit_mb`; «Скрыть» больше не выглядит удалением.
- Юридические документы: privacy/terms/aerial sources версии 11 от 18.11.2026; GitHub-обновления описаны как необязательные и только при настроенном источнике; путь к каталогу Aerial исправлен; убрано жёсткое «100 клипов» (в каталоге 117 записей).
- Kids: `backup_warning_kids` честно говорит, что Kids останется выключенным до нового PIN, — это совпадает с движком восстановления.

### D.3. Глоссарий

| EN | RU | Не использовать |
|---|---|---|
| Launcher / home screen | лаунчер / Главная | «домашний экран», «хоумскрин» |
| Screen saver / Ambient | заставка / Ambient Experience | «скринсейвер» в UI |
| Aerial | Aerial (не переводить) | «воздушная заставка» |
| Shelf | полка | «шельф» |
| Tile | плитка | «иконка» для плитки сетки |
| Focus | фокус | «выделение» |
| D-pad / remote | пульт / D-pad | «джойстик» |
| Backup / Restore | резервная копия / восстановление | «бэкап» в UI |
| Profile | профиль | «аккаунт» |
| Kids Mode | детский режим | «Kids» в русском тексте |
| Update | обновление | «апдейт» |
| Trailer | трейлер | «превью» для видео |
| Provider | приложение-источник | «провайдер» без пояснения |
| Permission | разрешение | «пермишен» |
| Settings | Настройки | «сеттинги» |

---

## E. Функциональные дефекты

Все перечисленные дефекты исправлены в 0.5.75; для каждого указано, почему это было ошибкой и чем проверено.

| ID | Где | Было | Ожидаемо / почему | Что изменено | Проверка |
|---|---|---|---|---|---|
| F-01 | `ui/components/ControlCenter.kt`, Wi-Fi tile | после отказа в разрешении плитка навсегда открывала только системные настройки, хотя имя сети можно было запросить | один запрос при первом использовании, затем честный переход в настройки | `shouldAskForWifiName(network, granted, asked)`, `wifiAsked`; запрос только WIFI и только если ещё не спрашивали | `WifiNameRequestTest` 4/4 |
| F-02 | `ui/BackupScreen.kt`, `fail()` | пользователь видел английский technical message исключения | локализованная причина по стадии | `backupErrorRes(stage)`, разворачивание `BackupException.stage` | `BackupErrorTextTest` 5/5 |
| F-03 | `ui/BackupScreen.kt`, создание | пустой выбор категорий оставлял кнопку, которая ничего не делала | действие либо создаёт архив, либо объясняет отказ | Toast `backup_selection_required`; нельзя снять последнюю категорию | `BackupScreenTest` |
| F-04 | `ui/BackupScreen.kt` | жёстко зашитое «KB» | единица локализуется | `unit_kb` | `BackupScreenTest` |
| F-05 | `ui/BackupScreen.kt` | строка хранения использовала подсказку про автопроверку | подсказка должна объяснять хранение | новый `backup_retention_hint` | `BackupScreenTest` |
| F-06 | `ui/BackupScreenParts.kt` | info/warning-строки были кликабельны и focusable без действия — ловушка фокуса | read-only строки не должны перехватывать OK | `clickable` только при `onClick != null`; focus на первую `action`-строку | `BackupScreenTest` |
| F-07 | `ui/BackupScreen.kt`, warnings | два разных предупреждения показывали один текст | каждое состояние — свой текст | `backup_warning_unknown_component`, `backup_warning_source_unreadable` | unit + runtime |
| F-08 | `ui/SoftwareUpdateParts.kt` | «MB» не локализовано | единица зависит от локали | `formatSize` — `@Composable`, `unit_mb` | `SoftwareUpdateScreenTest` |
| F-09 | `ui/SoftwareUpdateParts.kt` / strings | текст обещал GitHub-источник, хотя URL по умолчанию пуст | нельзя обещать несуществующий канал | честные `update_subtitle`, `_hint`, `_rate_limited` | `SoftwareUpdateScreenTest` |
| F-10 | `ui/SetupScreen.kt`, `openSystem()` | если ни прямой, ни fallback Intent не открывались, не было никакой реакции | пользователь должен знать, что переход не удался | Toast `settings_system_unavailable`; то же для Home settings | `SetupScreenTest` |
| F-11 | `ui/LauncherRootParts.kt`, `startSystemIntent` | молчаливый отказ системного Intent | честная обратная связь | Toast `settings_system_unavailable` (кроме DeviceAdmin OEM-fallback) | статическая проверка + `HomeScreenTest` |
| F-12 | `ui/LauncherRoot.kt` | `orderStore`/`discoveryStore` пересоздавались на локализованном контексте и могли очистить сетку при смене языка | смена языка не должна менять данные | ключ — `applicationContext` | `LanguageReturnTest` 2/2 |
| F-13 | `ui/HomeMenus.kt` | «Скрыть» было destructive, хотя действие обратимо | destructive только для необратимого | `destructive = false` | `HomeScreenTest` |
| F-14 | `ui/SettingsSaverMainSections.kt` | у «Обновить Reddit сейчас» был шеврон, но действие выполняется на месте | шеврон означает переход | шеврон убран | `SettingsScreenTest` |
| F-15 | `ui/SettingsScreen.kt`, Startup Home | OK на уже выбранном Home не закрывал страницу | OK должен подтверждать и возвращать | `onSelectHome` всегда `back()` | `SettingsScreenTest` |
| F-16 | `ui/HomeMenus.kt` / strings | «Переименовать» и «Новая папка» без многоточия, хотя открывают диалог | многоточие = будет диалог | `menu_rename_app`, `new_folder_action` | `HomeScreenTest` |
| F-17 | strings | `›` внутри текста кнопок «Настроить»/«Разрешить» | шеврон — отдельная иконка, не часть подписи | текст очищен | `SetupScreenTest` |
| F-18 | `ui/SetupPages.kt` | повторный Setup при текущем Photos/Reddit показывал три режима без выбранного | пользователь должен видеть текущий режим | `setup_saver_unsupported_hint` с названием режима | `SetupScreenTest` 27/27 |

Дополнительно исправлен тестовый дефект: raw-документы были перезаписаны с CRLF, из-за чего `split("\n\n")` не делил абзацы и `SettingsScreenTest#legal_page_opens_the_terms_document` не находил фокус. Файлы возвращены к LF, добавлен ключ `docParagraphs.size` в `LaunchedEffect`, чтобы фокус ставился после загрузки документа. Это не пользовательский дефект релиза, но важный регресс-урок: raw-файлы для `split` должны оставаться LF.

---

## F. Визуальные и usability-дефекты

| ID | Где | Проблема | Решение | Статус |
|---|---|---|---|---|
| V-01 | Backup | info-строки визуально выглядели как action-строки и ловили фокус | focusable/clickable только у action | исправлено |
| V-02 | Home menu | «Скрыть» была красной, как удаление | обычный цвет | исправлено |
| V-03 | Settings/Reddit | шеврон у действия «на месте» | шеврон убран | исправлено |
| V-04 | Setup | `›` в тексте кнопки | текст без шеврона | исправлено |
| V-05 | Setup saver | нет выбранного режима при Photos/Reddit | подпись с текущим режимом | исправлено |
| V-06 | Update | текст намекал на GitHub | честный источник/подсказка | исправлено |
| V-07 | Backup | «KB» латиницей в русском UI | `unit_kb` | исправлено |
| V-08 | Settings documents | один огромный focus-node, если документ без LF-разделителей | LF + `docParagraphs.size` как ключ фокуса | исправлено |

Остающиеся визуальные ограничения:

- на 720p длинные подписи по-прежнему требуют `maxLines`/ellipsis; это дизайн-компромисс, а не скрытый дефект;
- анимации перехода не измерялись на слабом железе;
- 4K/HDR-бейджи не проверялись физически и не должны считаться подтверждёнными.

---

## G. Доступность и читаемость

- Все action-строки имеют текстовую подпись; icon-only элементы (шестерёнка, стрелки) имеют `contentDescription`.
- Info-строки Backup больше не попадают в фокус-порядок и не читаются как кнопки.
- Каждый абзац юридического документа — отдельная focus-цель, чтобы TalkBack читал текст, а не пустой scroll-view.
- Disabled-строки не фокусируются; это проверено в `SettingsScreenTest`.
- Контраст: основной текст и вторичный текст используют `LauncherColors`; фокусная строка — белая поверхность с тёмным текстом.
- **NOT TESTED:** реальный TalkBack-голос, скорость речи, RTL, масштаб шрифта 130% на физическом ТВ, ИК/BT-пульт, HDMI-CEC.

---

## H. Тестирование

### H.1. Что запускалось

```text
GRADLE_USER_HOME=C:/gradle-home ./gradlew clean :app:assembleDebug :app:assembleRelease \
  :app:testDebugUnitTest :app:lintDebug -Pkotlin.incremental=false --console=plain

GRADLE_USER_HOME=C:/gradle-home ./gradlew :app:connectedDebugAndroidTest \
  -Pkotlin.incremental=false --console=plain

GRADLE_USER_HOME=C:/gradle-home ./gradlew :app:connectedDebugAndroidTest \
  -Pkotlin.incremental=false \
  -Pandroid.testInstrumentationRunnerArguments.class=com.whatuneed.ovisiontv.kotlin.ui.SetupScreenTest,\
com.whatuneed.ovisiontv.kotlin.ui.SettingsScreenTest#legal_page_opens_the_terms_document
```

### H.2. Результаты

| Прогон | Результат |
|---|---|
| Unit (`testDebugUnitTest`) | **578/578**, 0 failures, 0 errors |
| Connected (`connectedDebugAndroidTest`) | **304/304**, 0 failures, 0 skipped |
| Targeted Setup + Terms doc | **28/28** (27 Setup + 1 Terms) |
| Lint | **0 errors / 12 warnings / 0 information** |
| Compile debug + androidTest | BUILD SUCCESSFUL |
| Release build | BUILD SUCCESSFUL |
| Release check | PASS: versionCode 148, versionName 0.5.75, v2 signature, прежний сертификат; APK **6 623 683 B**, SHA-256 **`808fb6080963f4867b914d0f7b057f2306549cadc7747bc9fab17a45f87193bc`** |

Один изолированный flake: первый полный прогон упал в `MainActivityResumeTest` с `CalledFromWrongThreadException` внутри test-Recomposer. Класс прошёл **2/2** отдельно, полный suite — **304/304** на повторе; падения приложения не было. Это гонка тестового Compose-харнесса, но она зафиксирована здесь, а не скрыта.

Новые unit-тесты аудита:

- `WifiNameRequestTest` — 4/4: спрашивает один раз, не спрашивает после отказа, не спрашивает при выданном разрешении, не вмешивается в другие сети.
- `BackupErrorTextTest` — 5/5: формат, IO, integrity, crypto→password, restore.

Instrumentation-тесты аудита не добавлялись: поведение покрыто существующими `SetupScreenTest`, `SettingsScreenTest`, `BackupScreenTest`, `SoftwareUpdateScreenTest`, `ControlCenterTest`, `HomeScreenTest`.

### H.3. Чего тесты не доказывают

- Эмулятор API 34 не равен реальному Android TV API 28–35.
- HEVC-декодер эмулятора сломан: 4K/HDR и часть трейлеров **NOT TESTED**.
- Реальный GitHub-канал обновлений **NOT TESTED**: URL по умолчанию пуст.
- Системные SAF/permission/Home-picker диалоги зависят от OEM и **NOT TESTED** на матрице устройств.
- 24-часовой soak, storage-full, hostile DNS, captive portal, MITM, IPv6-only **NOT TESTED**.

---

## I. Внесённые изменения

Код:

- `ui/components/ControlCenter.kt` — Wi-Fi запрос один раз, затем системные настройки.
- `ui/BackupScreen.kt` — локализованные стадии ошибок, пустой выбор, `unit_kb`, `backup_retention_hint`, предупреждения.
- `ui/BackupScreenParts.kt` — read-only строки без фокуса/клика.
- `ui/SoftwareUpdateParts.kt` — локализованный размер, честный текст источника.
- `ui/SetupScreen.kt` — Toast при неудачном системном переходе.
- `ui/SetupPages.kt` — подпись текущего режима Photos/Reddit.
- `ui/LauncherRoot.kt` — стабильные store-ключи при смене языка.
- `ui/LauncherRootParts.kt` — Toast при неудачном системном Intent.
- `ui/HomeMenus.kt` — «Скрыть» не destructive; многоточия в rename/new folder.
- `ui/SettingsSaverMainSections.kt` — убран ложный шеврон.
- `ui/SettingsScreen.kt` — OK на выбранном Home; ключ фокуса документов.
- `res/values/strings.xml`, `res/values-ru/strings.xml` — 10 новых ключей, 56 RU/28 EN правок.
- `res/raw/privacy_*`, `terms_*`, `aerial_sources_*` — версия 11, дата 18.11.2026, точные формулировки.
- `app/src/test/.../WifiNameRequestTest.kt`, `BackupErrorTextTest.kt` — новые тесты.
- `KidsGateTest`, `SettingsEntryGrantTest`, `AppRepositoryTest` — строки читаются через `LocalePreference.wrap`, поэтому сохранённый русский язык приложения не ломает прогон.
- `FIXES.md` — запись 0.5.75 и 10 новых строк в таблице симптомов.

Документы аудита: этот файл.

---

## J. Оставшиеся вопросы и вердикт

### Классификация

| Уровень | Найдено | Статус |
|---|---|---|
| P0 — блокер, потеря данных/невозможность пользоваться | 0 | — |
| P1 — высокий, основной сценарий сломан | 0 | — |
| P2 — средний, ограничение или неудобство | 4 | перечислены ниже; не блокируют выпуск |
| P3 — низкий, косметика/долг | 4 | перечислены ниже |

### P2 — ограничения, не блокирующие выпуск

1. **Setup не переключает на Photos/Reddit.** Это осознанно: у Setup нет поверхности выбора фото/сабреддитов. Повторный проход теперь показывает текущий режим и путь в Settings, но выбрать Photos/Reddit можно только в Settings. Это ограничение сценария, не мёртвая кнопка.
2. **Физический ТВ не проверен.** Фокус, ИК/BT, HDMI-CEC, реальный HEVC, 4K/HDR, слабое железо и API 28–33/35 остаются **NOT TESTED**.
3. **Источник обновлений не опубликован.** Update UI честно это отражает; реальная загрузка/установка из сети **NOT TESTED**.
4. **`SettingsScreen.kt` ~4 100 строк.** Это долг сопровождаемости; дальнейшее дробление возможно, но не требуется для релиза и не должно делаться «заодно» с аудитом.

### P3 — известные мелочи

- 12 предупреждений lint (не ошибок).
- Холодный старт на эмуляторе нестабилен (примерно 1.1–1.5 с); это не измерение продукта.
- Длинные юридические документы требуют скролла; это ожидаемо.
- Часть сторонних превью зависит от доступности Reddit/Apple; предусмотрен локальный fallback.

### Незакрытые проверки

**NOT TESTED:** физический Android TV, 4K/HDR-декод, 720p+ трейлеры, RTL, TalkBack-голос, ИК/BT/HDMI-CEC, ARM32/64, 1 ГБ RAM, API 28–33/35, Perfetto, 24-часовой soak, storage-full, реальный GitHub update path, hostile DNS, captive portal, MITM-прокси, живые CDN-редиректы, IPv6-only.

### Вердикт

**CONDITIONALLY READY** — код, тексты, фокус и критические сценарии прошли unit 578/578, connected 304/304, lint 0 errors; P0/P1 не найдено; исправленные дефекты покрыты тестами. Условия: отдельное разрешение владельца на публикацию APK, проверка на физическом Android TV и подтверждение 4K/HDR/трейлеров на реальном декодере; до этого соответствующие пункты остаются **NOT TESTED**, а не «работает».

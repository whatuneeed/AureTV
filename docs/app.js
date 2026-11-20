(() => {
  "use strict";

  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
  const langStore = "aure-tv-preview-lang";

  const STR = {
    ru: {
      preview: "Интерактивное превью",
      weatherCity: "Москва",
      weatherState: "Облачно",
      watch: "Смотреть",
      info: "Подробнее",
      shelfKicker: "Новое в приложении",
      shelfHeading: "Для вас",
      hint: "Стрелки — выбор · Enter — открыть · Esc — назад",
      search: "Поиск",
      searchPlaceholder: "Найти приложение или фильм",
      apps: "Приложения",
      titles: "Фильмы и сериалы",
      nothing: "Ничего не нашлось",
      control: "Control Center",
      sleep: "Сон",
      wifi: "Wi-Fi",
      ethernet: "Ethernet",
      display: "Экран",
      sound: "Звук",
      volume: "Громкость 20%",
      sleepTimer: "Таймер сна",
      off: "Выкл",
      on: "Вкл",
      close: "Закрыть",
      settings: "Настройки",
      ambient: "Заставка",
      library: "Библиотека",
      demo: "Демо",
      weather: "Погода",
      weatherFeels: "Ощущается как 2° · Ветер 4 км/ч · Влажность 96%",
      hourly: "Почасовой прогноз",
      daily: "Прогноз по дням",
      now: "Сейчас",
      today: "Сегодня",
      high: "Макс.",
      low: "Мин.",
      demoData: "Демо-данные · Open-Meteo",
      details: "Описание",
      openApp: "Открыть приложение",
      demoLaunch: "Демо: запуск «{name}»",
      demoAction: "Демо: {name}",
      back: "Назад",
      settingsTitle: "Настройки",
      personalization: "Персонализация",
      home: "Главная",
      profiles: "Профили",
      device: "Устройство",
      about: "О системе",
      language: "Язык интерфейса",
      theme: "Тема",
      dark: "Тёмная",
      ambientRow: "Заставка",
      aerial: "Aerial",
      reduceMotion: "Уменьшить движение",
      recommendations: "Рекомендации на полке",
      continueWatching: "Продолжить просмотр",
      weatherOnHome: "Погода на Главной",
      chooseCity: "Город",
      units: "Единицы",
      showWeather: "Показать погоду",
      profileMe: "Я",
      profileKids: "Детский",
      profileNote: "Профили хранят свою раскладку и папки",
      displayRow: "Дисплей",
      resolution: "1920 × 1080",
      soundRow: "Звук",
      volumeShort: "20%",
      backup: "Резервное копирование",
      backupValue: "Открыть",
      update: "Обновление ПО",
      updateValue: "Вручную",
      version: "Версия",
      webPreview: "Интерактивное веб-превью",
      webPreviewNote: "Это не APK и не заменяет приложение на телевизоре.",
      download: "Скачать APK",
      ambientTitle: "Закат над городом",
      ambientHint: "Нажмите любую клавишу, чтобы выйти",
      ambientExit: "Выйти",
      sep: " · ",
    },
    en: {
      preview: "Interactive preview",
      weatherCity: "Moscow",
      weatherState: "Cloudy",
      watch: "Watch",
      info: "More info",
      shelfKicker: "New in the app",
      shelfHeading: "For you",
      hint: "Arrows to move · Enter to open · Esc to go back",
      search: "Search",
      searchPlaceholder: "Search apps or titles",
      apps: "Apps",
      titles: "Films & series",
      nothing: "Nothing found",
      control: "Control Center",
      sleep: "Sleep",
      wifi: "Wi-Fi",
      ethernet: "Ethernet",
      display: "Display",
      sound: "Sound",
      volume: "Volume 20%",
      sleepTimer: "Sleep timer",
      off: "Off",
      on: "On",
      close: "Close",
      settings: "Settings",
      ambient: "Ambient",
      library: "Library",
      demo: "Demo",
      weather: "Weather",
      weatherFeels: "Feels like 2° · Wind 4 km/h · Humidity 96%",
      hourly: "Hourly forecast",
      daily: "Daily forecast",
      now: "Now",
      today: "Today",
      high: "High",
      low: "Low",
      demoData: "Demo data · Open-Meteo",
      details: "Description",
      openApp: "Open app",
      demoLaunch: "Demo: launching “{name}”",
      demoAction: "Demo: {name}",
      back: "Back",
      settingsTitle: "Settings",
      personalization: "Personalization",
      home: "Home",
      profiles: "Profiles",
      device: "Device",
      about: "About",
      language: "Interface language",
      theme: "Theme",
      dark: "Dark",
      ambientRow: "Ambient",
      aerial: "Aerial",
      reduceMotion: "Reduce motion",
      recommendations: "Recommendations on shelf",
      continueWatching: "Continue watching",
      weatherOnHome: "Weather on Home",
      chooseCity: "City",
      units: "Units",
      showWeather: "Show weather",
      profileMe: "Me",
      profileKids: "Kids",
      profileNote: "Profiles keep their own layout and folders",
      displayRow: "Display",
      resolution: "1920 × 1080",
      soundRow: "Sound",
      volumeShort: "20%",
      backup: "Backup & restore",
      backupValue: "Open",
      update: "Software update",
      updateValue: "Manual",
      version: "Version",
      webPreview: "Interactive web preview",
      webPreviewNote: "This is not the APK and does not replace the TV app.",
      download: "Download APK",
      ambientTitle: "City sunset",
      ambientHint: "Press any key to exit",
      ambientExit: "Exit",
      sep: " · ",
    },
  };

  const CARDS = [
    {
      id: "midnight",
      title: { ru: "Полночный горизонт", en: "Midnight Horizon" },
      kind: { ru: "Сериал", en: "Series" },
      episodes: { ru: "8 эпизодов", en: "8 episodes" },
      year: "2026",
      description: {
        ru: "Тихий город на краю света просыпается после странного заката. Всё, что казалось знакомым, начинает меняться.",
        en: "A quiet town at the edge of the world wakes after a strange sunset. Everything familiar begins to change.",
      },
      c1: "#0c3152",
      c2: "#c85b55",
      tint: "rgba(255, 112, 81, 0.48)",
      mark: "AURE / 01",
      letter: "M",
    },
    {
      id: "north",
      title: { ru: "Северный свет", en: "Northern Light" },
      kind: { ru: "Фильм", en: "Film" },
      episodes: { ru: "1 ч 48 мин", en: "1h 48m" },
      year: "2025",
      description: {
        ru: "Полярная станция, потерявшая связь с миром, получает сигнал, которого не должно существовать.",
        en: "A polar station cut off from the world receives a signal that should not exist.",
      },
      c1: "#0e4c55",
      c2: "#73c79b",
      tint: "rgba(113, 226, 177, 0.35)",
      mark: "AURE / 02",
      letter: "N",
    },
    {
      id: "ocean",
      title: { ru: "Тихий океан", en: "Quiet Ocean" },
      kind: { ru: "Документальный", en: "Documentary" },
      episodes: { ru: "6 эпизодов", en: "6 episodes" },
      year: "2026",
      description: {
        ru: "История воды, света и жизни на глубине, куда ещё не доходил человек.",
        en: "A story of water, light and life in depths no human has reached.",
      },
      c1: "#153b67",
      c2: "#3fb9c5",
      tint: "rgba(75, 203, 220, 0.4)",
      mark: "AURE / 03",
      letter: "O",
    },
    {
      id: "glass",
      title: { ru: "Город стекла", en: "Glass City" },
      kind: { ru: "Сериал", en: "Series" },
      episodes: { ru: "10 эпизодов", en: "10 episodes" },
      year: "2024",
      description: {
        ru: "Архитектор строит дом, который помнит всех, кто в нём когда-либо жил.",
        en: "An architect builds a house that remembers everyone who ever lived in it.",
      },
      c1: "#3a2357",
      c2: "#c16698",
      tint: "rgba(209, 116, 164, 0.38)",
      mark: "AURE / 04",
      letter: "G",
    },
    {
      id: "rain",
      title: { ru: "После дождя", en: "After the Rain" },
      kind: { ru: "Фильм", en: "Film" },
      episodes: { ru: "1 ч 32 мин", en: "1h 32m" },
      year: "2025",
      description: {
        ru: "Одна ночь, один город и разговор, который меняет две жизни.",
        en: "One night, one city and a conversation that changes two lives.",
      },
      c1: "#27254c",
      c2: "#9876d7",
      tint: "rgba(152, 118, 215, 0.36)",
      mark: "AURE / 05",
      letter: "R",
    },
    {
      id: "hill",
      title: { ru: "Дом на холме", en: "House on the Hill" },
      kind: { ru: "Сериал", en: "Series" },
      episodes: { ru: "6 эпизодов", en: "6 episodes" },
      year: "2023",
      description: {
        ru: "Семья возвращается в старый дом, где каждый закат выглядит одинаково — и это только начало.",
        en: "A family returns to an old house where every sunset looks the same — and that is only the beginning.",
      },
      c1: "#5a3626",
      c2: "#e4a45e",
      tint: "rgba(255, 191, 112, 0.38)",
      mark: "AURE / 06",
      letter: "H",
    },
  ];

  const APPS = [
    {
      id: "settings",
      label: { ru: "Настройки", en: "Settings" },
      hint: { ru: "Система", en: "System" },
      icon: "gear",
      action: "settings",
    },
    {
      id: "aure-settings",
      label: { ru: "Настройки AURE TV", en: "AURE TV Settings" },
      hint: { ru: "Лаунчер", en: "Launcher" },
      icon: "sliders",
      action: "settings",
    },
    {
      id: "media",
      label: { ru: "Медиа", en: "Media" },
      hint: { ru: "Фильмы", en: "Films" },
      icon: "play",
      action: "toast",
    },
    {
      id: "games",
      label: { ru: "Игры", en: "Games" },
      hint: { ru: "Игровая полка", en: "Game shelf" },
      icon: "game",
      action: "toast",
    },
    {
      id: "tv",
      label: { ru: "ТВ", en: "TV" },
      hint: { ru: "Прямой эфир", en: "Live" },
      icon: "tv",
      action: "toast",
    },
    {
      id: "music",
      label: { ru: "Музыка", en: "Music" },
      hint: { ru: "Плейлисты", en: "Playlists" },
      icon: "music",
      action: "toast",
    },
  ];

  const SETTINGS = [
    { id: "personalization", icon: "sliders" },
    { id: "home", icon: "grid" },
    { id: "profiles", icon: "user" },
    { id: "weather", icon: "weather" },
    { id: "device", icon: "tv" },
    { id: "about", icon: "info" },
  ];

  const ICONS = {
    gear: '<span class="mini" aria-hidden="true">⚙</span>',
    sliders:
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" aria-hidden="true"><path d="M4 7h7M15 7h5M4 17h5M13 17h7"/><circle cx="13" cy="7" r="2"/><circle cx="11" cy="17" r="2"/></svg>',
    play:
      '<svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M8 5.5v13l11-6.5z"/></svg>',
    game:
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" aria-hidden="true"><path d="M7.5 8h9a4.5 4.5 0 0 1 4.3 5.8l-.8 2.2a2.2 2.2 0 0 1-3.8.8L14.5 15h-5l-1.7 1.8a2.2 2.2 0 0 1-3.8-.8l-.8-2.2A4.5 4.5 0 0 1 7.5 8Z"/><path d="M8 11v3M6.5 12.5h3M16.5 11.5h.01M18 13h.01"/></svg>',
    tv:
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" aria-hidden="true"><rect x="3" y="6" width="18" height="12" rx="2"/><path d="M8 21h8M9 6l3-3 3 3"/></svg>',
    music:
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M9 18V6l10-2v12"/><circle cx="6.5" cy="18" r="2.5"/><circle cx="16.5" cy="16" r="2.5"/></svg>',
    search:
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" aria-hidden="true"><circle cx="10.5" cy="10.5" r="6.2"/><path d="m15.3 15.3 5.2 5.2"/></svg>',
    grid:
      '<svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><rect x="4" y="4" width="6" height="6" rx="1"/><rect x="14" y="4" width="6" height="6" rx="1"/><rect x="4" y="14" width="6" height="6" rx="1"/><rect x="14" y="14" width="6" height="6" rx="1"/></svg>',
    user:
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" aria-hidden="true"><circle cx="12" cy="8" r="3.5"/><path d="M5 21c.7-4 3-6 7-6s6.3 2 7 6"/></svg>',
    weather:
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" aria-hidden="true"><path d="M6 16h10a4 4 0 0 0 .6-8 6 6 0 0 0-11.4 1.8A3.3 3.3 0 0 0 6 16Z"/></svg>',
    info:
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" aria-hidden="true"><circle cx="12" cy="12" r="8"/><path d="M12 10v6M12 7h.01"/></svg>',
    wifi:
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" aria-hidden="true"><path d="M3 9a14 14 0 0 1 18 0M6 13a10 10 0 0 1 12 0M9.5 16.5a4.5 4.5 0 0 1 5 0"/><circle cx="12" cy="20" r=".8" fill="currentColor" stroke="none"/></svg>',
    screen:
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" aria-hidden="true"><rect x="3" y="5" width="18" height="12" rx="2"/><path d="M9 21h6"/></svg>',
    volume:
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 10v4h4l4 3V7L8 10H4Z"/><path d="M15 9a4 4 0 0 1 0 6M17.5 6.5a8 8 0 0 1 0 11"/></svg>',
    clock:
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" aria-hidden="true"><circle cx="12" cy="12" r="8"/><path d="M12 7v5l3 2"/></svg>',
    power:
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" aria-hidden="true"><path d="M12 3v8"/><path d="M6.2 6.3a8 8 0 1 0 11.6 0"/></svg>',
    image:
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" aria-hidden="true"><rect x="3" y="4" width="18" height="16" rx="2"/><circle cx="8.5" cy="9" r="1.5"/><path d="m4 17 5-5 3 3 2-2 6 6"/></svg>',
  };

  const state = {
    lang: "ru",
    mode: "home",
    cardIndex: 0,
    settingsPage: "personalization",
    previousFocus: null,
    flags: {
      recommendations: true,
      continueWatching: true,
      weather: true,
      motion: false,
    },
    units: "C",
  };

  const els = {
    home: $("#homeView"),
    hero: $("#hero"),
    rail: $("#rail"),
    dock: $("#dock"),
    weatherPill: $(".weather-pill"),
    weatherCity: $("#weatherCity"),
    weatherState: $("#weatherState"),
    previewLabel: $("#previewLabel"),
    heroKicker: $("#heroKicker"),
    heroTitle: $("#heroTitle"),
    heroMeta: $("#heroMeta"),
    heroDescription: $("#heroDescription"),
    watchButton: $("#watchButton"),
    infoButton: $("#infoButton"),
    heroCorner: $("#heroCorner"),
    shelf: $(".shelf"),
    shelfKicker: $("#shelfKicker"),
    shelfHeading: $("#shelfHeading"),
    shelfCount: $("#shelfCount"),
    homeHint: $("#homeHint"),
    clock: $("#clock"),
    settings: $("#settingsView"),
    settingsBack: $("#settingsBack"),
    settingsNav: $("#settingsNav"),
    settingsPanel: $("#settingsPanel"),
    settingsTitle: $("#settingsTitle"),
    overlay: $("#overlayRoot"),
    overlayCard: $("#overlayCard"),
    ambient: $("#ambient"),
    ambientClock: $("#ambientClock"),
    ambientTitle: $("#ambientTitle"),
    ambientHint: $("#ambientHint"),
    toast: $("#toast"),
  };

  const t = (key) => (STR[state.lang] && STR[state.lang][key]) || key;
  const tr = (value) => (value && value[state.lang]) || value || "";
  const icon = (name) => ICONS[name] || ICONS.info;

  function loadLanguage() {
    try {
      const saved = localStorage.getItem(langStore);
      if (saved === "ru" || saved === "en") state.lang = saved;
    } catch (_) {
      // Private mode can block storage; the preview still works.
    }
  }

  function saveLanguage() {
    try {
      localStorage.setItem(langStore, state.lang);
    } catch (_) {
      // Ignore.
    }
  }

  function updateClock() {
    const value = new Intl.DateTimeFormat("ru-RU", {
      hour: "2-digit",
      minute: "2-digit",
      hour12: false,
    }).format(new Date());
    if (els.clock) els.clock.textContent = value;
    if (els.ambientClock) els.ambientClock.textContent = value;
  }

  function applyStaticText() {
    document.documentElement.lang = state.lang;
    document.title = state.lang === "ru" ? "AURE TV — интерактивное превью" : "AURE TV — interactive preview";
    els.previewLabel.textContent = t("preview");
    els.weatherCity.textContent = t("weatherCity");
    els.weatherState.textContent = t("weatherState");
    els.watchButton.textContent = t("watch");
    els.infoButton.textContent = t("info");
    els.shelfKicker.textContent = t("shelfKicker");
    els.shelfHeading.textContent = t("shelfHeading");
    els.shelfCount.textContent = String(CARDS.length);
    els.homeHint.textContent = t("hint");
    els.settingsTitle.textContent = t("settingsTitle");
    els.ambientTitle.textContent = t("ambientTitle");
    els.ambientHint.textContent = t("ambientHint");
    els.weatherPill.setAttribute("aria-label", t("weather"));
    els.weatherPill.hidden = !state.flags.weather;
  }

  function selectCard(index, { focus = false } = {}) {
    const card = CARDS[index];
    if (!card) return;
    state.cardIndex = index;
    els.heroTitle.textContent = tr(card.title);
    els.heroMeta.textContent = `${card.year}${t("sep")}${tr(card.kind)}${t("sep")}${tr(card.episodes)}`;
    els.heroDescription.textContent = tr(card.description);
    els.heroKicker.textContent = `${t("demo").toUpperCase()}${t("sep")}AURE ORIGINAL`;
    els.heroCorner.textContent = `${String(index + 1).padStart(2, "0")} / ${String(CARDS.length).padStart(2, "0")}`;
    els.hero.style.setProperty("--tint", card.tint);
    els.hero.style.setProperty("--accent", card.c2);
    $$(".card", els.rail).forEach((el, i) => {
      el.classList.toggle("is-current", i === index);
    });
    if (focus) {
      const target = $$(".card", els.rail)[index];
      if (target) focusElement(target);
    }
  }

  function renderHome() {
    els.rail.innerHTML = CARDS.map(
      (card, index) => `
        <button class="card focusable" data-focusable data-card="${index}" role="listitem" aria-label="${tr(card.title)}" style="--c1:${card.c1};--c2:${card.c2}">
          <span class="card-art">
            <span class="card-mark">${card.mark}</span>
            <span class="card-letter" aria-hidden="true">${card.letter}</span>
          </span>
          <span class="card-title">${tr(card.title)}</span>
        </button>`
    ).join("");

    els.dock.innerHTML = APPS.map(
      (app) => `
        <button class="dock-item focusable" data-focusable data-app="${app.id}" aria-label="${tr(app.label)}">
          <span class="dock-icon">${icon(app.icon)}</span>
          <span class="dock-label">${tr(app.label)}</span>
        </button>`
    ).join("");

    $$(".card", els.rail).forEach((el) => {
      const index = Number(el.dataset.card);
      el.addEventListener("focus", () => selectCard(index));
      el.addEventListener("pointerenter", () => selectCard(index));
      el.addEventListener("click", () => openDetails(index));
    });
    $$(".dock-item", els.dock).forEach((el) => {
      el.addEventListener("click", () => launchApp(el.dataset.app));
    });

    els.shelf.hidden = !state.flags.recommendations;
    selectCard(state.cardIndex);
  }

  function focusElement(el) {
    if (!el) return;
    $$(".is-focused").forEach((node) => node.classList.remove("is-focused"));
    el.classList.add("is-focused");
    try {
      el.focus({ preventScroll: true });
    } catch (_) {
      el.focus();
    }
  }

  function activeScope() {
    if (state.mode === "overlay" || state.mode === "search") return els.overlayCard;
    if (state.mode === "settings") return els.settings;
    if (state.mode === "ambient") return els.ambient;
    return document;
  }

  function visibleFocusables() {
    return $$("[data-focusable]", activeScope()).filter((el) => {
      if (el.hidden || el.disabled) return false;
      if (el.closest("[hidden]")) return false;
      return el.getClientRects().length > 0;
    });
  }

  function moveFocus(dx, dy) {
    const list = visibleFocusables();
    if (!list.length) return;
    const current = document.activeElement;
    if (!current || !list.includes(current)) {
      focusElement(list[0]);
      return;
    }
    const base = current.getBoundingClientRect();
    const cx = base.left + base.width / 2;
    const cy = base.top + base.height / 2;
    let best = null;
    let bestScore = Infinity;
    for (const el of list) {
      if (el === current) continue;
      const r = el.getBoundingClientRect();
      const x = r.left + r.width / 2;
      const y = r.top + r.height / 2;
      const deltaX = x - cx;
      const deltaY = y - cy;
      if (dx > 0 && deltaX < 5) continue;
      if (dx < 0 && deltaX > -5) continue;
      if (dy > 0 && deltaY < 5) continue;
      if (dy < 0 && deltaY > -5) continue;
      const primary = dx ? Math.abs(deltaX) : Math.abs(deltaY);
      const secondary = dx ? Math.abs(deltaY) : Math.abs(deltaX);
      const score = primary + secondary * 2.25;
      if (score < bestScore) {
        bestScore = score;
        best = el;
      }
    }
    if (best) focusElement(best);
  }

  function focusFirst(scope = document) {
    const el = $("[data-focusable]", scope) || visibleFocusables()[0];
    if (el) focusElement(el);
  }

  let toastTimer = 0;
  function showToast(message) {
    els.toast.textContent = message;
    els.toast.classList.add("show");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => els.toast.classList.remove("show"), 2400);
  }

  function launchApp(id) {
    const app = APPS.find((item) => item.id === id);
    if (!app) return;
    if (app.action === "settings") {
      openSettings();
      return;
    }
    showToast(t("demoLaunch").replace("{name}", tr(app.label)));
  }

  function overlayShell(title, subtitle, content) {
    return `
      <div class="overlay-head">
        <div><h2>${title}</h2>${subtitle ? `<p>${subtitle}</p>` : ""}</div>
        <button class="close-button" data-focusable data-close="overlay" aria-label="${t("close")}">×</button>
      </div>
      ${content}`;
  }

  function openOverlay(html, { search = false, focus = null, focusSelector = null } = {}) {
    state.previousFocus = document.activeElement;
    state.mode = search ? "search" : "overlay";
    els.overlay.hidden = false;
    els.overlayCard.innerHTML = html;
    const target = focus || (focusSelector ? $(focusSelector, els.overlayCard) : null) || $("[data-focusable]", els.overlayCard);
    requestAnimationFrame(() => {
      if (target) {
        focusElement(target);
      }
    });
  }

  function closeOverlay() {
    if (els.overlay.hidden) return;
    els.overlay.hidden = true;
    els.overlayCard.innerHTML = "";
    state.mode = "home";
    const target = state.previousFocus;
    state.previousFocus = null;
    if (target && target.isConnected && target.getClientRects().length) focusElement(target);
    else focusFirst(els.rail);
  }

  function openControlCenter() {
    const html = overlayShell(
      t("control"),
      `${t("weatherCity")} · ${state.lang === "ru" ? "демо" : "demo"}`,
      `<div class="cc-grid">
        <button class="cc-power focusable" data-focusable data-cc="sleep">
          <span class="power-icon">${icon("power")}</span><span>${t("sleep")}</span>
        </button>
        <button class="cc-item focusable" data-focusable data-cc="wifi">
          <span class="cc-icon">${icon("wifi")}</span>
          <span class="cc-copy"><strong>${t("wifi")}</strong><small>${t("ethernet")}</small></span>
        </button>
        <button class="cc-item focusable" data-focusable data-cc="display">
          <span class="cc-icon">${icon("screen")}</span>
          <span class="cc-copy"><strong>${t("display")}</strong><small>1920 × 1080</small></span>
        </button>
        <button class="cc-item focusable" data-focusable data-cc="sound">
          <span class="cc-icon">${icon("volume")}</span>
          <span class="cc-copy"><strong>${t("sound")}</strong><small>${t("volume")}</small></span>
        </button>
        <button class="cc-item focusable" data-focusable data-cc="timer">
          <span class="cc-icon">${icon("clock")}</span>
          <span class="cc-copy"><strong>${t("sleepTimer")}</strong><small>${t("off")}</small></span>
        </button>
      </div>
      <div class="cc-quick">
        <button class="icon-button focusable" data-focusable data-cc="search" aria-label="${t("search")}" title="${t("search")}">${icon("search")}</button>
        <button class="icon-button focusable" data-focusable data-cc="settings" aria-label="${t("settings")}" title="${t("settings")}">${icon("gear")}</button>
        <button class="icon-button focusable" data-focusable data-cc="ambient" aria-label="${t("ambient")}" title="${t("ambient")}">${icon("image")}</button>
        <button class="icon-button focusable" data-focusable data-cc="library" aria-label="${t("library")}" title="${t("library")}">${icon("grid")}</button>
      </div>`
    );
    openOverlay(html, { focusSelector: ".cc-power" });
    $$("[data-cc]", els.overlayCard).forEach((button) => {
      button.addEventListener("click", () => {
        const action = button.dataset.cc;
        if (action === "settings") {
          closeOverlay();
          openSettings();
          return;
        }
        if (action === "search") {
          closeOverlay();
          openSearch();
          return;
        }
        if (action === "ambient") {
          closeOverlay();
          startAmbient();
          return;
        }
        if (action === "library") {
          showToast(t("demoAction").replace("{name}", t("library")));
          return;
        }
        if (action === "sleep") {
          showToast(t("demoAction").replace("{name}", t("sleep")));
          return;
        }
        showToast(t("demoAction").replace("{name}", button.querySelector("strong, .cc-copy strong")?.textContent || t("demo")));
      });
    });
  }

  function openWeather() {
    const temp = (celsius) => {
      const value = state.units === "F" ? Math.round((celsius * 9) / 5 + 32) : celsius;
      return `${value}°`;
    };
    const hours = [
      [t("now"), 4],
      ["0", 4],
      ["1", 4],
      ["2", 4],
      ["3", 4],
      ["4", 4],
    ];
    const forecast = [
      [t("today"), "☂", 0, 5],
      [state.lang === "ru" ? "Вс" : "Sun", "☁", 0, 2],
      [state.lang === "ru" ? "Пн" : "Mon", "☁", 0, 1],
      [state.lang === "ru" ? "Вт" : "Tue", "☁", -1, 0],
      [state.lang === "ru" ? "Ср" : "Wed", "☁", -4, -1],
    ];
    const html = overlayShell(
      t("weather"),
      `${t("weatherCity")} · ${t("demoData")}`,
      `<div class="weather-card">
        <div class="weather-main">
          <div class="weather-big">${temp(4)}</div>
          <div class="weather-main-copy"><strong>${t("weatherState")}</strong><span>${t("weatherCity")}</span></div>
          <span class="weather-glyph" aria-hidden="true">☁</span>
        </div>
        <div class="weather-detail">${t("weatherFeels").replace("2°", temp(2))}</div>
        <div class="hourly">${hours.map(([label, value]) => `<div class="hour"><span class="h-icon">☁</span><span>${label}</span><strong>${temp(value)}</strong></div>`).join("")}</div>
        <div class="forecast"><div class="forecast-row"><span class="day"></span><span></span><span class="temp">${t("low")}</span><span class="temp">${t("high")}</span></div>
          ${forecast.map(([day, glyph, low, high]) => `<div class="forecast-row"><span class="day">${day}</span><span>${glyph}</span><span class="temp">${temp(low)}</span><span class="temp high">${temp(high)}</span></div>`).join("")}
        </div>
        <p class="weather-note">${t("demoData")}${t("sep")}${state.units === "F" ? "°F" : "°C"}</p>
      </div>`
    );
    openOverlay(html);
  }

  function openDetails(index) {
    const card = CARDS[index];
    if (!card) return;
    const html = overlayShell(
      t("details"),
      `${card.year}${t("sep")}${tr(card.kind)}${t("sep")}${tr(card.episodes)}`,
      `<div class="detail-card">
        <div class="detail-layout">
          <div class="detail-poster" style="--c1:${card.c1};--c2:${card.c2}"></div>
          <div>
            <h3 class="detail-title">${tr(card.title)}</h3>
            <p class="detail-meta">${t("demo")} · AURE ORIGINAL</p>
            <p class="detail-description">${tr(card.description)}</p>
            <div class="detail-actions">
              <button class="button primary focusable" data-focusable data-detail="open">${t("openApp")}</button>
              <button class="button ghost focusable" data-focusable data-close="overlay">${t("close")}</button>
            </div>
          </div>
        </div>
        <p class="detail-note">${t("webPreviewNote")}</p>
      </div>`
    );
    openOverlay(html, { focusSelector: "[data-detail]" });
    $$("[data-detail]", els.overlayCard).forEach((button) => {
      button.addEventListener("click", () => {
        showToast(t("demoLaunch").replace("{name}", tr(card.title)));
        closeOverlay();
      });
    });
  }

  function openSearch() {
    const html = overlayShell(
      t("search"),
      state.lang === "ru" ? "Найдите приложение или демо-тайтл" : "Find an app or a demo title",
      `<div class="search-field"><span class="icon">${icon("search")}</span><input id="searchInput" type="search" autocomplete="off" spellcheck="false" placeholder="${t("searchPlaceholder")}" aria-label="${t("search")}"></div>
       <p class="search-caption" id="searchCaption">${t("apps")}</p>
       <div id="searchResults" class="result-list"></div>`
    );
    openOverlay(html, { search: true, focusSelector: "#searchInput" });
    const input = $("#searchInput", els.overlayCard);
    const results = $("#searchResults", els.overlayCard);
    const caption = $("#searchCaption", els.overlayCard);
    const renderResults = () => {
      const query = input.value.trim().toLowerCase();
      const has = (value) => [value.ru, value.en].filter(Boolean).some((text) => text.toLowerCase().includes(query));
      const apps = APPS.filter((app) => has(app.label));
      const titles = CARDS.map((card, index) => ({ card, index })).filter(({ card }) => has(card.title));
      const rows = [];
      if (apps.length) {
        rows.push(`<p class="search-caption">${t("apps")}</p>`);
        rows.push(...apps.map((app) => `<button class="result-row focusable" data-focusable data-result-app="${app.id}"><span class="result-icon">${icon(app.icon)}</span><span class="result-copy"><strong>${tr(app.label)}</strong><small>${tr(app.hint)}</small></span></button>`));
      }
      if (titles.length) {
        rows.push(`<p class="search-caption">${t("titles")}</p>`);
        rows.push(...titles.map(({ card, index }) => `<button class="result-row focusable" data-focusable data-result-card="${index}"><span class="result-icon">${icon("play")}</span><span class="result-copy"><strong>${tr(card.title)}</strong><small>${card.year}${t("sep")}${tr(card.kind)}</small></span></button>`));
      }
      results.innerHTML = rows.length ? rows.join("") : `<div class="empty">${t("nothing")}</div>`;
      caption.hidden = true;
      $$(".result-row", results).forEach((row) => {
        row.addEventListener("click", () => {
          if (row.dataset.resultApp) {
            const app = APPS.find((item) => item.id === row.dataset.resultApp);
            closeOverlay();
            if (app.action === "settings") openSettings();
            else showToast(t("demoLaunch").replace("{name}", tr(app.label)));
          } else {
            const index = Number(row.dataset.resultCard);
            closeOverlay();
            openDetails(index);
          }
        });
      });
    };
    input.addEventListener("input", renderResults);
    input.addEventListener("keydown", (event) => {
      if (event.key === "Enter") {
        const first = $(".result-row", results);
        if (first) {
          event.preventDefault();
          first.click();
        }
      } else if (event.key === "ArrowDown") {
        const first = $(".result-row", results);
        if (first) {
          event.preventDefault();
          focusElement(first);
        }
      }
    });
    renderResults();
    requestAnimationFrame(() => input.focus());
  }

  function openSettings() {
    state.previousFocus = document.activeElement;
    els.overlay.hidden = true;
    els.overlayCard.innerHTML = "";
    state.mode = "settings";
    els.home.hidden = true;
    els.settings.hidden = false;
    renderSettings();
    requestAnimationFrame(() => focusFirst(els.settingsNav));
  }

  function closeSettings() {
    els.settings.hidden = true;
    els.home.hidden = false;
    state.mode = "home";
    renderHome();
    focusFirst(els.rail);
    state.previousFocus = null;
  }

  function settingsRow({ label, hint, value, action, toggle, toggleOn, extra = "" }) {
    const valueHtml = value ? `<span class="row-value ${toggleOn ? "on" : ""}">${value}</span>` : "";
    return `<button class="settings-row focusable" data-focusable data-settings-action="${action}" ${toggle ? `data-toggle="${toggle}"` : ""}>
      <span class="row-copy"><span>${label}</span>${hint ? `<small>${hint}</small>` : ""}</span>
      ${valueHtml}${extra}
    </button>`;
  }

  function renderSettings() {
    els.settingsNav.innerHTML = SETTINGS.map((item) => `<button class="focusable ${state.settingsPage === item.id ? "active" : ""}" data-focusable data-settings-page="${item.id}"><span><span class="cc-icon">${icon(item.icon)}</span> ${t(item.id)}</span><span class="nav-chevron">›</span></button>`).join("");
    const page = state.settingsPage;
    const value = (on) => (on ? t("on") : t("off"));
    let html = "";

    if (page === "personalization") {
      html = `<div class="panel-intro"><h2>${t("personalization")}</h2><p>${t("webPreviewNote")}</p></div>
        <div class="settings-group"><p class="settings-group-label">${t("personalization")}</p>
          ${settingsRow({ label: t("language"), hint: state.lang === "ru" ? "Русский" : "English", value: state.lang === "ru" ? "RU" : "EN", action: "language" })}
          ${settingsRow({ label: t("theme"), value: t("dark"), action: "theme" })}
          ${settingsRow({ label: t("ambientRow"), value: t("aerial"), action: "ambient" })}
          ${settingsRow({ label: t("reduceMotion"), value: value(state.flags.motion), action: "motion", toggle: "motion", toggleOn: state.flags.motion })}
        </div>`;
    } else if (page === "home") {
      html = `<div class="panel-intro"><h2>${t("home")}</h2><p>${state.lang === "ru" ? "Настройки Главной, полки и погоды." : "Home, shelf and weather settings."}</p></div>
        <div class="settings-group"><p class="settings-group-label">${t("home")}</p>
          ${settingsRow({ label: t("recommendations"), value: value(state.flags.recommendations), action: "home-recs", toggle: "recommendations", toggleOn: state.flags.recommendations })}
          ${settingsRow({ label: t("continueWatching"), value: value(state.flags.continueWatching), action: "home-continue", toggle: "continue", toggleOn: state.flags.continueWatching })}
          ${settingsRow({ label: t("weatherOnHome"), value: value(state.flags.weather), action: "home-weather", toggle: "weather", toggleOn: state.flags.weather })}
        </div>`;
    } else if (page === "profiles") {
      html = `<div class="panel-intro"><h2>${t("profiles")}</h2><p>${t("profileNote")}</p></div>
        <div class="settings-group"><p class="settings-group-label">${t("profiles")}</p>
          <button class="settings-row focusable" data-focusable data-settings-action="toast-profile"><span class="row-copy"><span class="profile-line"><span class="profile-badge">☺</span>${t("profileMe")}</span><small>${state.lang === "ru" ? "Основной профиль" : "Main profile"}</small></span><span class="row-value">›</span></button>
          <button class="settings-row focusable" data-focusable data-settings-action="toast-kids"><span class="row-copy"><span class="profile-line"><span class="profile-badge kids">✦</span>${t("profileKids")}</span><small>${state.lang === "ru" ? "Ограниченный доступ" : "Limited access"}</small></span><span class="row-value">›</span></button>
        </div>`;
    } else if (page === "weather") {
      html = `<div class="panel-intro"><h2>${t("weather")}</h2><p>${t("demoData")}</p></div>
        <div class="settings-group"><p class="settings-group-label">${t("weather")}</p>
          ${settingsRow({ label: t("chooseCity"), value: t("weatherCity"), action: "weather-city" })}
          ${settingsRow({ label: t("units"), value: state.units === "C" ? "°C" : "°F", action: "units" })}
          ${settingsRow({ label: t("showWeather"), value: t("openApp"), action: "show-weather" })}
        </div>`;
    } else if (page === "device") {
      html = `<div class="panel-intro"><h2>${t("device")}</h2><p>${state.lang === "ru" ? "Демонстрационные настройки устройства." : "Demo device settings."}</p></div>
        <div class="settings-group"><p class="settings-group-label">${t("device")}</p>
          ${settingsRow({ label: t("displayRow"), hint: t("resolution"), value: "›", action: "toast-display" })}
          ${settingsRow({ label: t("soundRow"), value: t("volumeShort"), action: "toast-sound" })}
          ${settingsRow({ label: t("backup"), value: t("backupValue"), action: "toast-backup" })}
          ${settingsRow({ label: t("update"), value: t("updateValue"), action: "toast-update" })}
        </div>`;
    } else {
      html = `<div class="panel-intro"><h2>${t("about")}</h2><p>${t("webPreviewNote")}</p></div>
        <div class="settings-group"><p class="settings-group-label">AURE TV</p>
          ${settingsRow({ label: t("version"), value: "0.6.0", action: "toast-version" })}
          <button class="settings-row focusable" data-focusable data-settings-action="download"><span class="row-copy"><span>${t("download")}</span><small>GitHub Releases</small></span><span class="row-value">↗</span></button>
        </div>`;
    }
    els.settingsPanel.innerHTML = html;
    $$("[data-settings-page]", els.settingsNav).forEach((button) => {
      button.addEventListener("click", () => {
        state.settingsPage = button.dataset.settingsPage;
        renderSettings();
        requestAnimationFrame(() => {
          const active = $(`[data-settings-page="${state.settingsPage}"]`, els.settingsNav);
          if (active) focusElement(active);
        });
      });
    });
    $$("[data-settings-action]", els.settingsPanel).forEach((button) => {
      button.addEventListener("click", () => handleSettingsAction(button.dataset.settingsAction, button));
    });
  }

  function handleSettingsAction(action, button) {
    if (action === "language") {
      state.lang = state.lang === "ru" ? "en" : "ru";
      saveLanguage();
      applyStaticText();
      renderHome();
      renderSettings();
      requestAnimationFrame(() => {
        const row = $('[data-settings-action="language"]', els.settingsPanel);
        if (row) focusElement(row);
      });
      return;
    }
    if (action === "motion") {
      state.flags.motion = !state.flags.motion;
      document.body.classList.toggle("reduce-motion", state.flags.motion);
      renderSettings();
      requestAnimationFrame(() => {
        const row = document.querySelector('[data-settings-action="motion"]');
        if (row) focusElement(row);
      });
      return;
    }
    if (action === "home-recs") state.flags.recommendations = !state.flags.recommendations;
    else if (action === "home-continue") state.flags.continueWatching = !state.flags.continueWatching;
    else if (action === "home-weather") state.flags.weather = !state.flags.weather;
    if (action.startsWith("home-")) {
      applyStaticText();
      renderHome();
      renderSettings();
      requestAnimationFrame(() => {
        const row = document.querySelector(`[data-settings-action="${action}"]`);
        if (row) focusElement(row);
      });
      return;
    }
    if (action === "ambient") {
      closeSettings();
      startAmbient();
      return;
    }
    if (action === "show-weather") {
      closeSettings();
      openWeather();
      return;
    }
    if (action === "units") {
      state.units = state.units === "C" ? "F" : "C";
      renderSettings();
      requestAnimationFrame(() => {
        const row = document.querySelector('[data-settings-action="units"]');
        if (row) focusElement(row);
      });
      return;
    }
    if (action === "download") {
      window.open("https://github.com/whatuneeed/AureTV/releases/latest", "_blank", "noopener");
      return;
    }
    const demoName = {
      theme: t("theme"),
      "weather-city": t("chooseCity"),
      "toast-profile": t("profileMe"),
      "toast-kids": t("profileKids"),
      "toast-display": t("displayRow"),
      "toast-sound": t("soundRow"),
      "toast-backup": t("backup"),
      "toast-update": t("update"),
      "toast-version": t("version"),
    }[action] || t("demo");
    showToast(t("demoAction").replace("{name}", demoName));
  }

  function startAmbient() {
    state.previousFocus = document.activeElement;
    state.mode = "ambient";
    els.overlay.hidden = true;
    els.ambient.hidden = false;
    applyStaticText();
    updateClock();
    requestAnimationFrame(() => focusElement($("#ambientExit")));
  }

  function stopAmbient() {
    els.ambient.hidden = true;
    state.mode = "home";
    const target = state.previousFocus;
    state.previousFocus = null;
    if (target && target.isConnected && target.getClientRects().length) focusElement(target);
    else focusFirst(els.rail);
  }

  function handleBack() {
    if (state.mode === "ambient") {
      stopAmbient();
      return;
    }
    if (state.mode === "overlay" || state.mode === "search") {
      closeOverlay();
      return;
    }
    if (state.mode === "settings") {
      closeSettings();
    }
  }

  document.addEventListener("keydown", (event) => {
    if (state.mode === "ambient") {
      event.preventDefault();
      stopAmbient();
      return;
    }
    const targetIsInput = event.target && event.target.tagName === "INPUT";
    if (event.key === "Escape" || event.key === "BrowserBack") {
      if (state.mode !== "home") {
        event.preventDefault();
        handleBack();
      }
      return;
    }
    if (event.key === "Enter" && targetIsInput) {
      return;
    }
    if (event.key === "ArrowUp" || event.key === "ArrowDown" || event.key === "ArrowLeft" || event.key === "ArrowRight") {
      if (targetIsInput) return;
      event.preventDefault();
      const dx = event.key === "ArrowRight" ? 1 : event.key === "ArrowLeft" ? -1 : 0;
      const dy = event.key === "ArrowDown" ? 1 : event.key === "ArrowUp" ? -1 : 0;
      moveFocus(dx, dy);
      return;
    }
    if (state.mode === "home" && !targetIsInput) {
      if (event.key === "/" || event.key.toLowerCase() === "s") {
        event.preventDefault();
        openSearch();
      } else if (event.key.toLowerCase() === "c") {
        event.preventDefault();
        openControlCenter();
      } else if (event.key.toLowerCase() === "a") {
        event.preventDefault();
        startAmbient();
      }
    }
  });

  document.addEventListener("click", (event) => {
    const target = event.target;
    if (target.closest("[data-close='overlay']")) {
      closeOverlay();
    }
    if (target.closest("[data-action='weather']")) openWeather();
    if (target.closest("[data-action='control']")) openControlCenter();
    if (target.closest("[data-action='search']")) openSearch();
    if (target.closest("[data-action='details']")) openDetails(state.cardIndex);
    if (target.closest("[data-action='settings-back']")) closeSettings();
    if (target.closest("[data-action='ambient-exit']")) stopAmbient();
  });

  els.ambient.addEventListener("click", (event) => {
    if (!event.target.closest("[data-action='ambient-exit']")) stopAmbient();
  });

  document.addEventListener("focusin", (event) => {
    const el = event.target.closest("[data-focusable]");
    if (!el) return;
    $$(".is-focused").forEach((node) => {
      if (node !== el) node.classList.remove("is-focused");
    });
    el.classList.add("is-focused");
  });

  loadLanguage();
  applyStaticText();
  renderHome();
  updateClock();
  setInterval(updateClock, 30000);
  requestAnimationFrame(() => focusFirst(els.rail));
})();

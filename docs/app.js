(() => {
  "use strict";

  const $ = (selector, root = document) => root.querySelector(selector);

  function syncDocumentLanguage() {
    document.documentElement.lang = state.lang;
    document.title = state.lang === "ru" ? "AURE TV — интерактивное превью" : "AURE TV — interactive preview";
    document.querySelectorAll("[data-ru][data-en]").forEach((node) => {
      node.textContent = node.dataset[state.lang] || node.dataset.ru;
    });
  }
  const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
  const stage = $("#stage");
  const tvFrame = $("#tvFrame");

  const STR = {
    ru: {
      weather: "Погода", moscow: "Москва", cloudy: "Облачно",
      control: "Control Center", sleep: "Сон",
      wifi: "Wi-Fi", wifiDetail: "Ethernet", display: "Экран", resolution: "1920 × 1080",
      sound: "Звук", volume: "Громкость 20%", timer: "Таймер сна", off: "Выкл",
      shelfReason: "Новое в приложении", shelfHeading: "Для вас",
      search: "Поиск", apps: "Приложения", nothing: "Ничего не найдено",
      detailAbout: "О фильме", detailInfo: "Информация", detailMeta: "2025 · ДЕМО",
      detailNoSynopsis: "Описание для этой демонстрационной карточки недоступно.",
      liveTv: "Live TV", accessibility: "Специальные возможности",
      settings: "Настройки", personalization: "Персонализация",
      home: "Главная", profiles: "Профили", weatherRow: "Погода", device: "Устройство", about: "О системе",
      system: "Системный", recommendationsOn: "Рекомендации вкл", me: "Я",
      deviceValue: "AOSP TV on x86", version: "0.6.2",
      language: "Язык интерфейса", theme: "Тема", dark: "Тёмная",
      ambient: "Заставка", aerial: "Aerial", reduceMotion: "Уменьшить движение",
      enabled: "Вкл", disabled: "Выкл",
      continueWatching: "Продолжить просмотр", weatherHome: "Погода на Главной",
      city: "Город", units: "Единицы", showWeather: "Показать погоду",
      displayRow: "Дисплей", soundRow: "Звук", backup: "Резервное копирование",
      update: "Обновление ПО", open: "Открыть",
      ambientTitle: "Вид сверху на фьорд в Исландии", ambientSubtitle: "Пейзажи",
      demoApp: "Демо: {name}", demoCard: "Демо: {name}",
      add: "Ввести", back: "Назад",
      hourly: "ПОЧАСОВОЙ ПРОГНОЗ", daily: "ПРОГНОЗ ПО ДНЯМ",
      today: "Сегодня", sun: "Вс", mon: "Пн", tue: "Вт", wed: "Ср", thu: "Чт",
      now: "Сейчас", low: "Мин.", high: "Макс.",
      feels: "Ощущается как 2° · Ветер 4 км/ч · Влажность 96%",
      source: "Open-Meteo · 23:01",
      searchHint: "Найти приложение",
      details: "Описание",
      keyboardSpace: "Пробел", keyboardClear: "Стереть", keyboardDone: "Готово",
      keyboardShift: "Сдвиг", keyboardNumbers: "123", keyboardLetters: "abc",
      searchNoResults: "Ничего не найдено",
      demoTag: "AURE",
    },
    en: {
      weather: "Weather", moscow: "Moscow", cloudy: "Cloudy",
      control: "Control Center", sleep: "Sleep",
      wifi: "Wi-Fi", wifiDetail: "Ethernet", display: "Display", resolution: "1920 × 1080",
      sound: "Sound", volume: "Volume 20%", timer: "Sleep timer", off: "Off",
      shelfReason: "New in the app", shelfHeading: "For You",
      search: "Search", apps: "Apps", nothing: "Nothing found",
      settings: "Settings", personalization: "Personalization",
      home: "Home", profiles: "Profiles", weatherRow: "Weather", device: "Device", about: "About",
      system: "System", recommendationsOn: "Recommendations on", me: "Me",
      deviceValue: "AOSP TV on x86", version: "0.6.2",
      language: "Interface language", theme: "Theme", dark: "Dark",
      ambient: "Ambient", aerial: "Aerial", reduceMotion: "Reduce motion",
      enabled: "On", disabled: "Off",
      continueWatching: "Continue watching", weatherHome: "Weather on Home",
      city: "City", units: "Units", showWeather: "Show weather",
      displayRow: "Display", soundRow: "Sound", backup: "Backup & restore",
      update: "Software update", open: "Open",
      ambientTitle: "Aerial view of an Icelandic fjord", ambientSubtitle: "Landscapes",
      demoApp: "Demo: {name}", demoCard: "Demo: {name}",
      add: "Enter", back: "Back",
      hourly: "HOURLY FORECAST", daily: "DAILY FORECAST",
      today: "Today", sun: "Sun", mon: "Mon", tue: "Tue", wed: "Wed", thu: "Thu",
      now: "Now", low: "Low", high: "High",
      feels: "Feels like 2° · Wind 4 km/h · Humidity 96%",
      source: "Open-Meteo · 23:01",
      searchHint: "Find an app",
      details: "Details",
      keyboardSpace: "Space", keyboardClear: "Clear", keyboardDone: "Done",
      keyboardShift: "Shift", keyboardNumbers: "123", keyboardLetters: "abc",
      searchNoResults: "Nothing found",
      demoTag: "AURE",
    },
  };

  const APPS = [
    { id: "settings", image: "assets/app-settings.jpg", label: { ru: "Настройки", en: "Settings" }, action: "settings" },
    { id: "aure", image: "assets/app-aure-settings.jpg", label: { ru: "Настройки AURE TV", en: "AURE TV Settings" }, action: "aure" },
    { id: "store", image: "assets/app-store.jpg", label: { ru: "Google Play", en: "Google Play" }, action: "toast" },
    { id: "games", image: "assets/app-games.jpg", label: { ru: "Google Play Games", en: "Google Play Games" }, action: "toast" },
    { id: "live", image: "assets/app-live.jpg", label: { ru: "Live TV", en: "Live TV" }, action: "toast" },
    { id: "video", image: "assets/app-video.jpg", label: { ru: "YouTube", en: "YouTube" }, action: "toast" },
  ];

  const CARDS = [
    {
      id: "midnight", image: "assets/shelf-1.jpg", hero: "assets/hero-castle.jpg",
      title: { ru: "Полночный горизонт", en: "Midnight Horizon" },
      synopsis: { ru: "Ночной город, одинокий маяк и обещание, которое нельзя нарушить.", en: "A night city, a lonely lighthouse and a promise that cannot be broken." },
    },
    {
      id: "north", image: "assets/shelf-0.jpg", hero: "assets/hero-castle.jpg",
      title: { ru: "Танец в темноте", en: "Dance in the Dark" },
      synopsis: { ru: "Труппа готовит спектакль, который изменит всё.", en: "A company prepares a performance that will change everything." },
    },
    {
      id: "quiet", image: "assets/shelf-2.jpg", hero: "assets/hero-castle.jpg",
      title: { ru: "Город героев", en: "City of Heroes" },
      synopsis: { ru: "Двое друзей находят город, которого нет на картах.", en: "Two friends find a city that is not on any map." },
    },
    {
      id: "empress", image: "assets/shelf-3.jpg", hero: "assets/hero-castle.jpg",
      title: { ru: "Второй брак императрицы", en: "The Empress's Second Marriage" },
      synopsis: { ru: "После внезапной смерти императора молодая вдова оказывается в центре борьбы за трон.", en: "After the emperor's sudden death, a young widow is caught in the struggle for the throne." },
    },
    {
      id: "iron", image: "assets/shelf-4.jpg", hero: "assets/hero-castle.jpg",
      title: { ru: "Семейные каникулы", en: "Family Holiday" },
      synopsis: { ru: "Большая семья отправляется в путешествие, которое пойдёт не по плану.", en: "A big family sets out on a trip that will not go to plan." },
    },
    {
      id: "hill", image: "assets/shelf-5.jpg", hero: "assets/hero-iron.jpg",
      title: { ru: "Стальной рыцарь", en: "Steel Knight" },
      synopsis: { ru: "История о человеке, который превращает сталь в броню, а страх — в силу.", en: "A story about a man who turns steel into armor and fear into strength." },
    },
  ];

  const state = {
    lang: "ru",
    mode: "home",
    dockIndex: 0,
    shelfIndex: 3,
    detailIndex: 3,
    previousMode: "home",
    settingsPage: null,
    settingsFocus: 0,
    searchQuery: "",
    searchLatin: false,
    units: "C",
    flags: { recs: true, continue: true, weather: true, motion: false },
    clock: "",
    help: false,
  };

  const home = $("#home");
  const heroArt = $("#heroArt");
  const title = $("#heroTitle");
  const weatherCapsule = $("#weatherCapsule");
  const statusCapsule = $("#statusCapsule");
  const dockTiles = $("#dockTiles");
  const shelfLayer = $("#shelfLayer");
  const shelfRail = $("#shelfRail");
  const shelfReason = $("#shelfReason");
  const shelfHeading = $("#shelfHeading");
  const controlScreen = $("#controlScreen");
  const weatherScreen = $("#weatherScreen");
  const searchScreen = $("#searchScreen");
  const settingsScreen = $("#settingsScreen");
  const ambientScreen = $("#ambientScreen");
  const detailScreen = $("#detailScreen");
  const detailArt = $("#detailArt");
  const detailTitle = $("#detailTitle");
  const detailMeta = $("#detailMeta");
  const detailSynopsis = $("#detailSynopsis");
  const detailAbout = $("#detailAbout");
  const detailSource = $("#detailSource");
  const toast = $("#toast");

  const t = (key) => (STR[state.lang] && STR[state.lang][key]) || key;
  const tr = (value) => (value && value[state.lang]) || value || "";
  const pad = (n) => String(n).padStart(2, "0");

  function loadState() {
    try {
      const lang = localStorage.getItem("aure-tv-lang");
      if (lang === "ru" || lang === "en") state.lang = lang;
    } catch (_) { /* storage may be unavailable */ }
  }

  function saveState() {
    try { localStorage.setItem("aure-tv-lang", state.lang); } catch (_) { /* ignore */ }
  }

  function clockText() {
    const now = new Date();
    if (state.lang === "en") {
      const h = now.getHours() % 12 || 12;
      return `${h}:${pad(now.getMinutes())} ${now.getHours() >= 12 ? "PM" : "AM"}`;
    }
    return `${pad(now.getHours())}:${pad(now.getMinutes())}`;
  }

  function updateClock() {
    state.clock = clockText();
    const a = $("#clock"), b = $("#ccClock");
    if (a) a.textContent = state.clock;
    if (b) b.textContent = state.clock;
  }

  function applyScale() {
    const frame = tvFrame || stage.parentElement;
    const width = frame.clientWidth || innerWidth;
    const height = frame.clientHeight || innerHeight;
    const scale = Math.max(0.1, Math.min(width / 1920, height / 1080));
    stage.style.setProperty("--scale", String(scale));
    stage.dataset.scale = String(scale);
  }

  function showToast(message) {
    toast.textContent = message;
    toast.classList.add("is-visible");
    clearTimeout(showToast.timer);
    showToast.timer = setTimeout(() => toast.classList.remove("is-visible"), 2300);
  }

  function setFocused(el) {
    $$(".is-focused").forEach((node) => node.classList.remove("is-focused"));
    if (!el) return;
    el.classList.add("is-focused");
    try { el.focus({ preventScroll: true }); } catch (_) { el.focus(); }
  }

  function clearFocused() {
    $$(".is-focused").forEach((node) => node.classList.remove("is-focused"));
  }

  function currentCard() {
    return CARDS[state.shelfIndex] || CARDS[0];
  }

  function selectedApp() {
    return APPS[state.dockIndex] || APPS[0];
  }

  function homeShelfOpen() {
    return state.mode === "shelf";
  }

  function renderHome() {
    syncDocumentLanguage();
    const card = currentCard();
    heroArt.style.backgroundImage = `url("${card.hero}")`;
    title.textContent = tr(card.title);
    home.classList.toggle("has-weather", state.flags.weather && state.mode === "home");
    home.classList.toggle("no-weather", !state.flags.weather || state.mode !== "home");
    home.classList.toggle("shelf-open", state.mode === "shelf");
    home.classList.toggle("overlay-open", state.mode === "control" || state.mode === "weather" || state.mode === "detail");
    home.classList.remove("is-gone");

    weatherCapsule.hidden = !state.flags.weather;
    weatherCapsule.querySelector(".weather-place").textContent = t("moscow");
    weatherCapsule.querySelector(".weather-condition").textContent = t("cloudy");
    weatherCapsule.setAttribute("aria-label", t("weather"));
    statusCapsule.setAttribute("aria-label", t("control"));

    renderDock();
    renderShelf();
    updateClock();
  }

  function renderDock() {
    dockTiles.innerHTML = "";
    APPS.forEach((app, i) => {
      const button = document.createElement("button");
      button.className = "dock-tile focusable";
      button.dataset.app = app.id;
      button.style.left = `${90 + i * 298}px`;
      button.setAttribute("aria-label", tr(app.label));
      button.innerHTML = `<img src="${app.image}" alt="" draggable="false">`;
      button.addEventListener("click", () => {
        state.dockIndex = i;
        focusDock(i);
        launchApp(app);
      });
      button.addEventListener("focus", () => {
        state.dockIndex = i;
        updateDockFocus();
      });
      dockTiles.appendChild(button);

      const label = document.createElement("span");
      label.className = "dock-label";
      label.style.left = `${90 + i * 298}px`;
      label.textContent = tr(app.label);
      dockTiles.appendChild(label);
    });
    updateDockFocus();
  }

  function updateDockFocus() {
    const tiles = $$(".dock-tile", dockTiles);
    tiles.forEach((el, i) => el.classList.toggle("is-focused", i === state.dockIndex));
    $$(".dock-label", dockTiles).forEach((el, i) => {
      el.style.color = i === state.dockIndex ? "#fff" : "rgba(255,255,255,.86)";
    });
  }

  function focusDock(index) {
    state.dockIndex = Math.max(0, Math.min(APPS.length - 1, index));
    updateDockFocus();
    const tile = $$(".dock-tile", dockTiles)[state.dockIndex];
    if (tile) setFocused(tile);
  }

  function renderShelf() {
    shelfReason.textContent = t("shelfReason");
    shelfHeading.textContent = t("shelfHeading");
    shelfRail.innerHTML = "";
    CARDS.forEach((card, i) => {
      const button = document.createElement("button");
      button.className = "shelf-card focusable";
      button.dataset.card = card.id;
      button.setAttribute("aria-label", tr(card.title));
      button.innerHTML = `<img src="${card.image}" alt="" draggable="false"><span class="shelf-mask" aria-hidden="true"></span><span class="shelf-source">${t("demoTag")}</span>`;
      button.addEventListener("click", () => {
        if (state.shelfIndex === i) {
          openDetails(i);
        } else {
          state.shelfIndex = i;
          focusShelf(i);
        }
      });
      button.addEventListener("focus", () => {
        if (state.shelfIndex !== i) {
          state.shelfIndex = i;
          applySelectedCard();
        }
        highlightShelf(i);
      });
      shelfRail.appendChild(button);
    });
    highlightShelf(state.shelfIndex);
  }

  function highlightShelf(index) {
    $$(".shelf-card", shelfRail).forEach((el, i) => el.classList.toggle("is-focused", i === index));
  }

  function applySelectedCard() {
    const card = currentCard();
    title.textContent = tr(card.title);
    heroArt.style.backgroundImage = `url("${card.hero}")`;
  }

  function focusShelf(index) {
    state.shelfIndex = Math.max(0, Math.min(CARDS.length - 1, index));
    applySelectedCard();
    highlightShelf(state.shelfIndex);
    const el = $$(".shelf-card", shelfRail)[state.shelfIndex];
    if (el) setFocused(el);
  }

  function openDetails(index) {
    const card = CARDS[index];
    if (!card) return;
    state.mode = "detail";
    state.detailIndex = index;
    detailArt.style.backgroundImage = `url("${card.hero}")`;
    detailAbout.textContent = t("detailAbout");
    detailTitle.textContent = tr(card.title);
    detailMeta.textContent = t("detailMeta");
    detailSynopsis.textContent = tr(card.synopsis) || t("detailNoSynopsis");
    detailSource.textContent = t("detailInfo");
    detailScreen.hidden = false;
    home.classList.add("overlay-open");
  }

  function closeDetails() {
    if (state.mode !== "detail") return;
    detailScreen.hidden = true;
    state.mode = "shelf";
    renderHome();
    focusShelf(state.detailIndex ?? state.shelfIndex);
  }

  function openShelf() {
    if (state.mode !== "home") return;
    state.mode = "shelf";
    renderHome();
    shelfLayer.hidden = false;
    focusShelf(state.shelfIndex);
  }

  function closeShelf() {
    if (state.mode !== "shelf") return;
    state.mode = "home";
    shelfLayer.hidden = true;
    renderHome();
    focusDock(state.dockIndex);
  }

  function launchApp(app) {
    if (app.action === "settings" || app.action === "aure") {
      openSettings();
      return;
    }
    showToast(t("demoApp").replace("{name}", tr(app.label)));
  }

  function glyph(name) {
    const icons = {
      wifi: '<svg viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><path d="M4 13.5a22 22 0 0 1 28 0M9.5 20a14 14 0 0 1 17 0M14.2 26a7 7 0 0 1 7.6 0"/><circle cx="18" cy="31" r="1.1" fill="currentColor" stroke="none"/></svg>',
      screen: '<svg viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><rect x="4" y="7" width="28" height="19" rx="3"/><path d="M13 32h10"/></svg>',
      volume: '<svg viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M5 15v6h6l6 5V10l-6 5H5Z"/><path d="M23 14a6 6 0 0 1 0 8M27 10a11 11 0 0 1 0 16"/></svg>',
      clock: '<svg viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><circle cx="18" cy="18" r="12"/><path d="M18 10v9l5 3"/></svg>',
      image: '<svg viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><rect x="4" y="6" width="28" height="24" rx="3"/><circle cx="13" cy="14" r="2.5"/><path d="m5 26 8-8 5 5 3-3 9 9"/></svg>',
      grid: '<svg viewBox="0 0 36 36" fill="currentColor"><rect x="5" y="5" width="10" height="10" rx="2"/><rect x="21" y="5" width="10" height="10" rx="2"/><rect x="5" y="21" width="10" height="10" rx="2"/><rect x="21" y="21" width="10" height="10" rx="2"/></svg>',
      search: '<svg viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><circle cx="15" cy="15" r="9"/><path d="m22 22 8 8"/></svg>',
      gear: '<svg viewBox="0 0 36 36" fill="currentColor"><path d="M18 4l2.1 2.4 3.1-.7 1.2 3 3 1.2-.7 3.1L29 18l-2.3 2.1.7 3.1-3 1.2-1.2 3-3.1-.7L18 29l-2.1-2.3-3.1.7-1.2-3-3-1.2.7-3.1L7 18l2.3-2.1-.7-3.1 3-1.2 1.2-3 3.1.7L18 4Zm0 4.8a6.2 6.2 0 1 0 0 12.4 6.2 6.2 0 0 0 0-12.4Z"/></svg>',
      tv: '<svg viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><rect x="5" y="9" width="26" height="18" rx="3"/><path d="M12 32h12M12 9l6-5 6 5"/></svg>',
      access: '<svg viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="18" cy="6.5" r="2.5"/><path d="M18 11v9M10 15h16M18 20l-5 11M18 20l5 11"/></svg>',
      power: '<svg viewBox="0 0 48 48" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round"><path d="M24 7v17"/><path d="M13.2 13.2a17 17 0 1 0 21.6 0"/></svg>',
    };
    return icons[name] || icons.search;
  }

  function weatherGlyph() {
    return '<svg viewBox="0 0 44 44" fill="none"><path d="M13 32.5h18.5a7.5 7.5 0 0 0 .2-15 11 11 0 0 0-20.8 2.3A6.4 6.4 0 0 0 13 32.5Z" fill="#E8EDF5"/><path d="M13.8 18.2a11 11 0 0 1 18.5-2.2 7.5 7.5 0 0 1 4.7 6.2H27.2a6.4 6.4 0 0 0-5.1-8.4 8.8 8.8 0 0 0-8.3 4.4Z" fill="#AAB4C4" opacity=".9"/></svg>';
  }

  function renderControl() {
    $("#ccClock").textContent = state.clock;
    const right = $("#ccRight");
    const rows = [
      { id: "wifi", title: t("wifi"), detail: t("wifiDetail"), icon: "wifi" },
      { id: "display", title: t("display"), detail: t("resolution"), icon: "screen" },
      { id: "timer", title: t("timer"), detail: t("off"), icon: "clock" },
    ];
    right.innerHTML = rows.map((row) => `
      <button class="cc-tile focusable" data-cc="${row.id}">
        <span class="cc-icon">${glyph(row.icon)}</span>
        <span class="cc-copy"><span class="cc-title">${row.title}</span><span class="cc-detail">${row.detail}</span></span>
      </button>`).join("");

    const sound = $("#ccSound");
    if (sound) {
      sound.innerHTML = `<span class="cc-icon">${glyph("volume")}</span><span class="cc-copy"><span class="cc-title">${t("sound")}</span><span class="cc-detail">${t("volume")}</span></span>`;
      sound.addEventListener("click", () => handleControl("sound"));
    }

    const circles = [
      { id: "ambient", icon: "image", label: t("ambient") },
      { id: "apps", icon: "grid", label: t("apps") },
      { id: "search", icon: "search", label: t("search") },
      { id: "settings", icon: "gear", label: t("settings") },
      { id: "live", icon: "screen", label: t("liveTv") },
      { id: "access", icon: "access", label: t("accessibility") },
    ];
    $("#ccCircles").innerHTML = circles.map((item) => `
      <button class="cc-circle focusable" data-circle="${item.id}" aria-label="${item.label}">
        ${glyph(item.icon)}
      </button>`).join("");

    $$("#controlScreen [data-cc]").forEach((button) => {
      button.addEventListener("click", () => handleControl(button.dataset.cc));
    });
    $$("#controlScreen [data-circle]").forEach((button) => {
      button.addEventListener("click", () => handleCircle(button.dataset.circle));
    });
    $("#ccStatus").addEventListener("click", closeControl);
  }

  function openControl() {
    if (state.mode === "ambient" || state.mode === "search" || state.mode === "settings") return;
    state.mode = "control";
    home.classList.add("overlay-open");
    controlScreen.hidden = false;
    weatherScreen.hidden = true;
    renderHome();
    renderControl();
    setFocused($("#ccPower"));
  }

  function closeControl() {
    controlScreen.hidden = true;
    state.mode = "home";
    renderHome();
    focusDock(state.dockIndex);
  }

  function handleControl(id) {
    if (id === "sleep") {
      showToast(t("sleep"));
      return;
    }
    const row = {
      wifi: t("wifi"), display: t("display"), sound: t("sound"), timer: t("timer"),
    }[id] || t("control");
    showToast(row);
  }

  function handleCircle(id) {
    if (id === "apps") {
      showToast(t("apps"));
      return;
    }
    if (id === "ambient") {
      closeControl();
      openAmbient();
    } else if (id === "settings" || id === "system") {
      closeControl();
      openSettings();
    } else if (id === "search") {
      closeControl();
      openSearch();
    } else {
      showToast(id === "live" ? "Live TV" : "Accessibility");
    }
  }

  function renderWeatherPanel() {
    const c = (value) => state.units === "F" ? Math.round(value * 9 / 5 + 32) : value;
    const deg = (value) => `${c(value)}°`;
    const hours = [
      [t("now"), 4], ["0", 4], ["1", 4], ["2", 4], ["3", 4], ["4", 4],
    ];
    const days = [
      [t("today"), "☂", 0, 5],
      [t("sun"), "☁", 0, 2],
      [t("mon"), "☁", 0, 1],
      [t("tue"), "☁", -1, 0],
      [t("wed"), "☁", -4, -1],
    ];
    $("#weatherPanel").innerHTML = `
      <div class="wp-place">${t("moscow")}</div>
      <div class="wp-temp">${deg(4)}</div>
      <div class="wp-icon">${weatherGlyph()}</div>
      <div class="wp-condition">${t("cloudy")}</div>
      <div class="wp-highlow">${t("high")}: ${deg(5)} ${t("low")}: ${deg(0)}</div>
      <div class="wp-detail">${t("feels")}</div>
      <div class="wp-rule" style="top:204px"></div>
      <div class="wp-section" style="top:208px">${t("hourly")}</div>
      <div class="wp-hours">${hours.map(([label, value]) => `<div class="wp-hour"><span>${label}</span><span class="wp-hicon">☁</span><b>${deg(value)}</b></div>`).join("")}</div>
      <div class="wp-rule" style="top:342px"></div>
      <div class="wp-section" style="top:346px">${t("daily")}</div>
      <span class="wp-head-lo">${t("low")}</span><span class="wp-head-hi">${t("high")}</span>
      <div class="wp-days">${days.map(([day, icon, low, high]) => `<div class="wp-day"><span class="day-name">${day}</span><span class="day-icon">${icon}</span><span class="day-lo">${deg(low)}</span><span class="day-hi">${deg(high)}</span></div>`).join("")}</div>
      <div class="wp-footer">${t("source")}${state.units === "F" ? " · °F" : " · °C"}</div>`;
  }

  function openWeather() {
    if (state.mode === "search" || state.mode === "settings" || state.mode === "ambient") return;
    state.mode = "weather";
    weatherScreen.hidden = false;
    home.classList.add("overlay-open");
    renderHome();
    renderWeatherPanel();
    clearFocused();
  }

  function closeWeather() {
    weatherScreen.hidden = true;
    state.mode = "home";
    renderHome();
    focusDock(state.dockIndex);
  }

  function openSearch() {
    state.previousMode = state.mode === "search" ? state.previousMode : state.mode;
    state.mode = "search";
    home.classList.add("is-gone");
    searchScreen.hidden = false;
    renderSearch();
    renderSearchKeyboard();
    renderSearchResults();
    const first = $(".key", $("#keyboard"));
    if (first) setFocused(first);
  }

  function closeSearch() {
    searchScreen.hidden = true;
    state.mode = "home";
    state.searchQuery = "";
    renderHome();
    focusDock(state.dockIndex);
  }

  function renderSearch() {
    $("#searchTitle").textContent = t("search");
    $("#searchQuery").textContent = state.searchQuery;
    $("#searchField").setAttribute("aria-label", `${t("search")}: ${state.searchQuery}`);
  }

  function renderSearchKeyboard() {
    const rows = state.searchLatin
      ? ["qwertyuiop", "asdfghjkl", "zxcvbnm"]
      : ["йцукенгшщзхъ", "фывапролджэ", "ячсмитьбюё"];
    const keys = rows.map((row) => `<div class="kb-row keys-${row.length}">${[...row].map((ch) => `<button class="key focusable" data-key="${ch}">${state.searchLatin ? ch.toUpperCase() : ch}</button>`).join("")}</div>`).join("");
    $("#keyboard").innerHTML = `${keys}
      <div class="kb-row">
        <button class="key small focusable" data-key="shift">⇧</button>
        <button class="key space focusable" data-key="space">${t("keyboardSpace")}</button>
        <button class="key small focusable" data-key="backspace">⌫</button>
        <button class="key wide focusable" data-key="numbers">${t("keyboardNumbers")}</button>
        <button class="key wide focusable" data-key="letters">${t("keyboardLetters")}</button>
        <button class="key wide focusable" data-key="clear">${t("keyboardClear")}</button>
        <button class="key wide focusable" data-key="done">${t("keyboardDone")}</button>
      </div>`;
    $$(".key", $("#keyboard")).forEach((button) => {
      button.addEventListener("click", () => handleKey(button.dataset.key));
    });
  }

  function handleKey(key) {
    if (key === "space") state.searchQuery += " ";
    else if (key === "backspace") state.searchQuery = state.searchQuery.slice(0, -1);
    else if (key === "clear") state.searchQuery = "";
    else if (key === "done") {
      const first = $(".result-row", $("#searchResults"));
      if (first) first.click();
      return;
    } else if (key === "letters") state.searchLatin = !state.searchLatin;
    else if (key === "numbers") state.searchLatin = false;
    else if (key === "shift") {
      state.searchLatin = !state.searchLatin;
    } else if (key.length === 1) {
      state.searchQuery += keyshift(key);
    }
    renderSearch();
    renderSearchKeyboard();
    renderSearchResults();
    const focusKey = $(`.key[data-key="${CSS.escape(key)}"]`, $("#keyboard"));
    if (focusKey) setFocused(focusKey);
  }

  function keyshift(ch) {
    return state.searchLatin ? ch.toUpperCase() : ch;
  }

  function renderSearchResults() {
    const q = state.searchQuery.trim().toLowerCase();
    const matches = APPS.filter((app) => {
      const label = tr(app.label).toLowerCase();
      return !q || label.includes(q) || app.id.includes(q);
    });
    const results = $("#searchResults");
    if (!matches.length) {
      results.innerHTML = `<p class="results-caption">${t("apps")}</p><div class="result-empty">${t("searchNoResults")}</div>`;
      return;
    }
    results.innerHTML = `<p class="results-caption">${t("apps")}</p>${matches.map((app, index) => `
      <button class="result-row focusable ${index === 0 ? "" : ""}" data-result="${app.id}">
        <span class="result-art"><img src="${app.image}" alt="" draggable="false"></span>
        <span class="result-name">${tr(app.label)}</span>
      </button>`).join("")}`;
    $$(".result-row", results).forEach((row) => {
      row.addEventListener("click", () => {
        const app = APPS.find((a) => a.id === row.dataset.result);
        closeSearch();
        launchApp(app);
      });
    });
  }

  // ---------- SETTINGS ----------
  const SETTINGS_ROWS = [
    { id: "personalization", label: "personalization", value: "system" },
    { id: "home", label: "home", value: "recommendations" },
    { id: "profiles", label: "profiles", value: "me" },
    { id: "weather", label: "weatherRow", value: "moscow" },
    { id: "device", label: "device", value: "device" },
    { id: "about", label: "about", value: "version" },
  ];

  function rowValue(key) {
    return {
      system: t("system"), recommendations: t("recommendationsOn"), me: t("me"),
      moscow: t("moscow"), device: t("deviceValue"), version: t("version"),
    }[key] || "";
  }

  function openSettings() {
    state.mode = "settings";
    state.settingsPage = null;
    state.settingsFocus = 0;
    home.classList.add("is-gone");
    settingsScreen.hidden = false;
    renderSettings();
    const first = $(".settings-row", $("#settingsList"));
    if (first) setFocused(first);
  }

  function closeSettings() {
    settingsScreen.hidden = true;
    state.mode = "home";
    state.settingsPage = null;
    state.settingsFocus = 0;
    renderHome();
    focusDock(state.dockIndex);
  }

  function renderSettings() {
    const list = $("#settingsList");
    const page = $("#settingsPage");
    if (state.settingsPage) {
      page.hidden = false;
      list.hidden = true;
      renderSettingsPage();
      return;
    }
    page.hidden = true;
    list.hidden = false;
    $("#settingsTitle").textContent = t("settings");
    list.innerHTML = SETTINGS_ROWS.map((row, index) => `
      <button class="settings-row focusable" data-settings="${row.id}">
        <span class="row-label">${t(row.label)}</span>
        <span class="row-value">${rowValue(row.value)}</span>
        <span class="row-chevron">›</span>
      </button>`).join("");
    $$(".settings-row", list).forEach((row, index) => {
      row.addEventListener("click", () => openSettingsPage(row.dataset.settings));
      row.addEventListener("focus", () => {
        state.settingsFocus = index;
        highlightSettings(index);
      });
    });
    highlightSettings(state.settingsFocus);
  }

  function highlightSettings(index) {
    $$(".settings-row", $("#settingsList")).forEach((row, i) => row.classList.toggle("is-focused", i === index));
  }

  function openSettingsPage(id) {
    state.settingsPage = id;
    state.settingsFocus = 0;
    renderSettings();
    const first = $(".settings-row", $("#settingsPageList"));
    if (first) setFocused(first);
  }

  function settingsPageData(page) {
    if (page === "personalization") {
      return [
        { label: t("language"), value: state.lang.toUpperCase(), action: "language" },
        { label: t("theme"), value: t("dark"), action: "toast" },
        { label: t("ambient"), value: t("aerial"), action: "ambient" },
        { label: t("reduceMotion"), value: state.flags.motion ? t("enabled") : t("disabled"), action: "motion" },
      ];
    }
    if (page === "home") {
      return [
        { label: t("recommendationsOn").replace(/\s.*$/, "") || t("home"), value: state.flags.recs ? t("enabled") : t("disabled"), action: "recs" },
        { label: t("continueWatching"), value: state.flags.continue ? t("enabled") : t("disabled"), action: "continue" },
        { label: t("weatherHome"), value: state.flags.weather ? t("enabled") : t("disabled"), action: "weatherHome" },
      ];
    }
    if (page === "weather") {
      return [
        { label: t("city"), value: t("moscow"), action: "city" },
        { label: t("units"), value: state.units === "F" ? "°F" : "°C", action: "units" },
        { label: t("showWeather"), value: "›", action: "openWeather" },
      ];
    }
    if (page === "profiles") {
      return [
        { label: t("me"), value: "›", action: "toast" },
        { label: t("profiles"), value: "›", action: "toast" },
      ];
    }
    if (page === "device") {
      return [
        { label: t("displayRow"), value: t("resolution"), action: "toast" },
        { label: t("soundRow"), value: "20%", action: "toast" },
        { label: t("backup"), value: "›", action: "toast" },
        { label: t("update"), value: "›", action: "toast" },
      ];
    }
    return [
      { label: t("version"), value: t("version"), action: "toast" },
      { label: t("ambient"), value: t("aerial"), action: "ambient" },
    ];
  }

  function renderSettingsPage() {
    const page = state.settingsPage;
    $("#settingsTitle").textContent = t(page);
    $("#settingsPageTitle").textContent = t(page);
    const rows = settingsPageData(page);
    $("#settingsPageList").innerHTML = rows.map((row, index) => `
      <button class="settings-row focusable" data-settings-action="${row.action}" data-index="${index}">
        <span class="row-label">${row.label}</span>
        <span class="row-value">${row.value}</span>
        <span class="row-chevron">›</span>
      </button>`).join("");
    $$(".settings-row", $("#settingsPageList")).forEach((row, index) => {
      row.addEventListener("click", () => handleSettingsAction(row.dataset.settingsAction));
      row.addEventListener("focus", () => {
        state.settingsFocus = index;
        $$(".settings-row", $("#settingsPageList")).forEach((el, i) => el.classList.toggle("is-focused", i === index));
      });
    });
    $$(".settings-row", $("#settingsPageList")).forEach((el, i) => el.classList.toggle("is-focused", i === state.settingsFocus));
  }

  function handleSettingsAction(action) {
    if (action === "language") {
      state.lang = state.lang === "ru" ? "en" : "ru";
      syncDocumentLanguage();
      saveState();
      renderHome();
      renderSettings();
      setFocused($(".settings-row", $("#settingsPageList")));
      return;
    }
    if (action === "motion") {
      state.flags.motion = !state.flags.motion;
      document.documentElement.classList.toggle("reduce-motion", state.flags.motion);
      renderSettings();
      return;
    }
    if (action === "recs") state.flags.recs = !state.flags.recs;
    else if (action === "continue") state.flags.continue = !state.flags.continue;
    else if (action === "weatherHome") {
      state.flags.weather = !state.flags.weather;
      renderHome();
    } else if (action === "units") state.units = state.units === "C" ? "F" : "C";
    else if (action === "ambient") {
      closeSettings();
      openAmbient();
      return;
    } else if (action === "openWeather") {
      closeSettings();
      openWeather();
      return;
    } else if (action === "city") showToast(t("moscow"));
    else showToast(t("details"));
    renderSettings();
    setFocused($(".settings-row", $("#settingsPageList")));
  }

  function closeSettingsPage() {
    if (!state.settingsPage) {
      closeSettings();
      return;
    }
    state.settingsPage = null;
    state.settingsFocus = 0;
    renderSettings();
    setFocused($(".settings-row", $("#settingsList")));
  }

  // ---------- AMBIENT ----------
  function openAmbient() {
    state.previousMode = state.mode;
    state.mode = "ambient";
    home.classList.add("is-gone");
    ambientScreen.hidden = false;
    $("#ambientTitle").textContent = t("ambientTitle");
    $("#ambientSubtitle").textContent = t("ambientSubtitle");
    clearFocused();
  }

  function closeAmbient() {
    ambientScreen.hidden = true;
    state.mode = "home";
    renderHome();
    focusDock(state.dockIndex);
  }

  // ---------- MODE / FOCUS ----------
  function openFromHome() {
    if (state.mode === "shelf") {
      openControl();
    }
  }

  function moveSpatial(dx, dy, scope) {
    const list = $$("[data-focusable], button, .settings-row, .cc-circle, .cc-tile, .cc-power, .key, .result-row, .shelf-card, .dock-tile", scope).filter((el) => {
      if (el.disabled || el.hidden) return false;
      if (el.closest("[hidden]")) return false;
      return el.getClientRects().length > 0;
    });
    const current = document.activeElement;
    if (!current || !list.includes(current)) return;
    const a = current.getBoundingClientRect();
    const cx = a.left + a.width / 2;
    const cy = a.top + a.height / 2;
    let best = null;
    let bestScore = Infinity;
    for (const el of list) {
      if (el === current) continue;
      const r = el.getBoundingClientRect();
      const x = r.left + r.width / 2;
      const y = r.top + r.height / 2;
      const ax = x - cx;
      const ay = y - cy;
      if (dx && (dx > 0 ? ax < 10 : ax > -10)) continue;
      if (dy && (dy > 0 ? ay < 10 : ay > -10)) continue;
      const primary = dx ? Math.abs(ax) : Math.abs(ay);
      const secondary = dx ? Math.abs(ay) : Math.abs(ax);
      const score = primary + secondary * 2.2;
      if (score < bestScore) {
        best = el;
        bestScore = score;
      }
    }
    if (best) setFocused(best);
  }

  function settingsMove(delta) {
    if (state.settingsPage) {
      const rows = $$(".settings-row", $("#settingsPageList"));
      if (!rows.length) return;
      state.settingsFocus = (state.settingsFocus + delta + rows.length) % rows.length;
      setFocused(rows[state.settingsFocus]);
      return;
    }
    const rows = $$(".settings-row", $("#settingsList"));
    if (!rows.length) return;
    state.settingsFocus = (state.settingsFocus + delta + rows.length) % rows.length;
    setFocused(rows[state.settingsFocus]);
  }

  function handleBack() {
    if (state.mode === "shelf") closeShelf();
    else if (state.mode === "control") closeControl();
    else if (state.mode === "weather") closeWeather();
    else if (state.mode === "search") closeSearch();
    else if (state.mode === "settings") closeSettingsPage();
    else if (state.mode === "ambient") closeAmbient();
    else if (state.mode === "detail") closeDetails();
  }

  function handleKeyDown(event) {
    if (event.defaultPrevented) return;
    if (event.key === "Escape" || event.key === "BrowserBack") {
      event.preventDefault();
      handleBack();
      return;
    }
    if (state.mode === "detail") {
      if (event.key === "Enter" || event.key === "Backspace") {
        event.preventDefault();
        closeDetails();
      }
      return;
    }
    if (state.mode === "ambient") {
      event.preventDefault();
      closeAmbient();
      return;
    }
    if (state.mode === "search") {
      if (event.key === "Backspace") {
        event.preventDefault();
        state.searchQuery = state.searchQuery.slice(0, -1);
        renderSearch(); renderSearchResults();
        return;
      }
      if (event.key === "Enter") {
        event.preventDefault();
        const first = $(".result-row", $("#searchResults"));
        if (first) first.click();
        return;
      }
      if (event.key === "ArrowUp" || event.key === "ArrowDown" || event.key === "ArrowLeft" || event.key === "ArrowRight") {
        event.preventDefault();
        const dx = event.key === "ArrowRight" ? 1 : event.key === "ArrowLeft" ? -1 : 0;
        const dy = event.key === "ArrowDown" ? 1 : event.key === "ArrowUp" ? -1 : 0;
        moveSpatial(dx, dy, $("#searchScreen"));
        return;
      }
      if (event.key.length === 1 && !event.ctrlKey && !event.metaKey && !event.altKey) {
        event.preventDefault();
        state.searchQuery += event.key;
        renderSearch(); renderSearchResults();
      }
      return;
    }
    if (state.mode === "settings") {
      if (event.key === "ArrowDown") { event.preventDefault(); settingsMove(1); return; }
      if (event.key === "ArrowUp") { event.preventDefault(); settingsMove(-1); return; }
      if (event.key === "Enter") return;
      return;
    }
    if (state.mode === "control") {
      if (event.key === "ArrowUp" || event.key === "ArrowDown" || event.key === "ArrowLeft" || event.key === "ArrowRight") {
        event.preventDefault();
        const dx = event.key === "ArrowRight" ? 1 : event.key === "ArrowLeft" ? -1 : 0;
        const dy = event.key === "ArrowDown" ? 1 : event.key === "ArrowUp" ? -1 : 0;
        moveSpatial(dx, dy, $("#controlScreen"));
      }
      return;
    }
    if (state.mode === "weather") return;
    if (state.mode === "shelf" || state.mode === "home") {
      if (event.key === "s" || event.key === "S") {
        event.preventDefault(); openSearch(); return;
      }
      if (event.key === "c" || event.key === "C") {
        event.preventDefault(); openControl(); return;
      }
      if (event.key === "a" || event.key === "A") {
        event.preventDefault(); openAmbient(); return;
      }
      if (event.key === "l" || event.key === "L") {
        event.preventDefault(); state.lang = state.lang === "ru" ? "en" : "ru"; saveState(); renderHome(); updateClock(); return;
      }
    }
    if (state.mode === "home") {
      if (event.key === "ArrowLeft") { event.preventDefault(); focusDock(state.dockIndex - 1); return; }
      if (event.key === "ArrowRight") { event.preventDefault(); focusDock(state.dockIndex + 1); return; }
      if (event.key === "ArrowUp") { event.preventDefault(); openShelf(); return; }
      if (event.key === "ArrowDown") { event.preventDefault(); return; }
    }
    if (state.mode === "shelf") {
      if (event.key === "Enter") { event.preventDefault(); openDetails(state.shelfIndex); return; }
      if (event.key === "ArrowLeft") { event.preventDefault(); focusShelf(state.shelfIndex - 1); return; }
      if (event.key === "ArrowRight") { event.preventDefault(); focusShelf(state.shelfIndex + 1); return; }
      if (event.key === "ArrowDown") { event.preventDefault(); closeShelf(); return; }
      return;
    }
    if (event.key === "s" || event.key === "S") {
      event.preventDefault();
      openSearch();
    } else if (event.key === "c" || event.key === "C") {
      event.preventDefault();
      openControl();
    } else if (event.key === "a" || event.key === "A") {
      event.preventDefault();
      openAmbient();
    } else if (event.key === "l" || event.key === "L") {
      event.preventDefault();
      state.lang = state.lang === "ru" ? "en" : "ru";
      saveState(); renderHome(); updateClock();
    } else if (event.key === "?" || event.key === "h" || event.key === "H") {
      const help = $("#previewHelp");
      state.help = !state.help;
      help.classList.toggle("is-visible", state.help);
    }
  }

  function remoteHome() {
    if (state.mode === "detail") {
      closeDetails();
      closeShelf();
    } else if (state.mode === "shelf") {
      closeShelf();
    } else if (state.mode === "control") {
      closeControl();
    } else if (state.mode === "weather") {
      closeWeather();
    } else if (state.mode === "search") {
      closeSearch();
    } else if (state.mode === "settings") {
      closeSettings();
    } else if (state.mode === "ambient") {
      closeAmbient();
    } else {
      focusDock(state.dockIndex);
    }
  }

  function remoteAction(action) {
    if (action === "home") {
      remoteHome();
    } else if (action === "power") {
      showToast(state.lang === "ru" ? "Питание — только в демо" : "Power is demo-only");
    } else if (action === "volume-up" || action === "volume-down") {
      showToast(t("volume"));
    }
  }

  function bindRemote() {
    $$("[data-remote-key]").forEach((button) => {
      button.addEventListener("click", () => {
        const key = button.dataset.remoteKey;
        button.blur();
        document.dispatchEvent(new KeyboardEvent("keydown", { key, bubbles: true, cancelable: true }));
      });
    });
    $$("[data-remote-action]").forEach((button) => {
      button.addEventListener("click", () => {
        const action = button.dataset.remoteAction;
        button.blur();
        remoteAction(action);
      });
    });
  }

  function bind() {
    document.addEventListener("keydown", handleKeyDown);
    bindRemote();
    $("#previewHelp").querySelector("#langToggle").addEventListener("click", () => {
      state.lang = state.lang === "ru" ? "en" : "ru";
      saveState(); renderHome(); updateClock();
      $("#langToggle").textContent = state.lang === "ru" ? "EN" : "RU";
    });
    weatherCapsule.addEventListener("click", openWeather);
    statusCapsule.addEventListener("click", openControl);
    $("#weatherScreen").querySelector(".dim-layer").addEventListener("click", closeWeather);
    $("#controlScreen").querySelector(".dim-layer").addEventListener("click", closeControl);
    $("#settingsIcon").addEventListener("click", () => {
      if (state.settingsPage) closeSettingsPage();
    });
    ambientScreen.addEventListener("click", closeAmbient);
    addEventListener("resize", applyScale);
    document.addEventListener("visibilitychange", () => { if (!document.hidden) updateClock(); });
  }

  loadState();
  applyScale();
  bind();
  document.documentElement.lang = state.lang;
  $("#langToggle").textContent = state.lang === "ru" ? "EN" : "RU";
  renderHome();
  renderSearchKeyboard();
  updateClock();
  setInterval(updateClock, 30000);
  // The launcher starts on the dock with the first app focused.
  requestAnimationFrame(() => focusDock(0));
})();

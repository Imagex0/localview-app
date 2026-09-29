# LocalView — Feature Set (vs Solipsism)

Goal: lightweight + beautiful localhost webview. Minimal fresh app reusing
Solipsism patterns, not a fork. Scope: dev + essentials (max 5 tabs, 24h history).

## KEEP

### 1. Core engine (tuned)
- `WebViewFactory.kt` pattern — JS, DOM storage, ServiceWorker, media, HMR websocket forced ON. `usesCleartextTraffic=true` stays.
- `UrlHandler.kt` + `UrlUtils.kt` + `normalizeLocalInput()` — `5173` / `:3000` / `localhost:8000` / `127.0.0.1:9000` resolve to `http://localhost:PORT/`.
- `TabModel.kt` + `TabsRepository.kt` (trimmed) — max 5 tabs, no freeze/restore, no `saveState` session.
- `TabWebViewClient.kt` / `TabWebChromeClient.kt` (slimmed) — progress, title, console passthrough only.

### 2. Dev workflow (the product — priority)
- DevTools panel (F12) — bottom-docked HTML / JS / LOG inspector bound to the active `:PORT`. Console passthrough via `TabWebChromeClient`, source view via `TabWebViewClient`. Toggled from the bottom devbar.
- Hard reload (long-press reload, cache bypass) + `Clear site data for this port`.
- Port pills + live probe (`HEAD :PORT`, 800ms timeout, `[OK]/[OFF]`).
- `ProjectsRepo {name, url, port}` (DataStore) — dashboard cards.
- Pin-shortcut per project (`Utils.kt` `requestPinShortcut` pattern).
- Desktop-UA toggle (`DefaultUserAgent.kt`, single switch).

### 3. Essentials (light)
- 24h ring-buffer history (no full history page, no Decoy Mode).
- Bookmarks = projects only (no folders / import / favicon sync).
- Downloads via `DownloadManager` only (no custom manager, no JPEG-convert).
- Minimal hardening: `intent:// file:// content:// data: javascript:` guards + SSL dialog (`ssl/`).

### 4. Look (one theme)
- Single Matrix-brutalist dark theme. No `AppTheme LIGHT/DARK/AMOLED`, no `AccentPalette`, no wallpapers / motto / custom fonts.
- Text-size control only.

## DROP (heavy / off-mission)
- **Antares engine** — `browser/engine/*`, AIDL, `BrowserCoreChooserActivity`, coordinate bridge.
- **Adblock** — `adblock/`, `BLOCK_ADS`, `COSMETIC_FILTERS`, element picker.
- **Malware + VirusTotal** — `malware/`, `virustotal/`, `VIRUS_TOTAL_*`, definitions updater.
- **Userscripts** — `userscript/` + editor + `USER_SCRIPTS`.
- **QR** — `qr/` + CameraX + `CAMERA` permission.
- **Screenshot Studio / Vault / Audio EQ / Haptics** — `screenshot/`, `vault/`, `audio/`, `haptics/`.
- **Rail & Menu Studio + 4 chrome modes** — rail delegates, `RailMenuStudioActivity`. One top chrome only.
- **Homepage studio** — `BUILT_IN/STATIC_HTML/DOMAIN`, wallpapers, motto, opacity grids. Replaced by project dashboard.
- **Incognito separate process** — `IncognitoBrowserActivity`, `:incognito`.
- **Search + suggestions** — `search/`, `searchChoice`. Omnibox is ports-only.
- **Cookie Manager advanced / Decoy Mode / TTS / Release-notes updater / 20 locales / donations**.

Net: ~16 files reused / ~400+ left behind. Mockup preview: `mockup/features.html` + live panel in `mockup/browser.html` (DEVTOOLS [F12]).

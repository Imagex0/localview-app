# LocalView — lightweight localhost WebView

Stop pasting `http://localhost:5173` into heavy Chrome. LocalView is a tiny,
dev-focused browser: it remembers your local servers, live-checks which ones
are up, and opens them in a tuned WebView with an F12 DevTools panel, desktop
view, and fullscreen page mode — all in a Matrix-brutalist UI.

Spec: [`./features.md`](./features.md) · clickable mockup: [`./mockup/`](./mockup/).

## Install (no build needed)

1. Open **Releases** → https://github.com/Imagex0/localview-app/releases
2. Download **`app-debug.apk`** straight from the latest release (no zip to extract).
3. Install it (allow *install unknown apps* once). Uninstall any older copy
   first **only** if install stalls — all builds since v0.1 share one debug
   signature, so updates install over each other.

Requirements: Android 8+ (minSdk 26). Permissions: `INTERNET` +
`ACCESS_NETWORK_STATE` only — no camera, location, or storage access.

## Use it

1. Start a dev server (`npm run dev`, `flask run`, …).
2. Dashboard → **+ ADD** → type `5173` (bare ports work: `5173`, `:3000`,
   `localhost:8000`, `127.0.0.1:9000`).
3. Tap **Open**. Cards show `[LIVE]`/`[OFF]` from an 800ms `HEAD :PORT` probe.
4. In-page tools (bottom dock): **DEVTOOLS [F12]** (HTML/JS/LOG + COPY),
   **maximize** (borderless page, back/minimize restores), **desktop view**
   (desktop UA spoof), **☰ hub** (every tool + live ON/OFF state).
5. Top chrome: back / forward / reload (long-press = hard reload), full-width
   URL bar with `:port` pill, **Home** key back to the dashboard, tab strip
   with **×** close keys (max 5 tabs).
6. Menu (⋮): hard reload, per-port site-data clear, **Storage** manager
   (live stats + clear history/cache/site-data/reset), pin to Home, dashboard.

Everything persists on-device: projects, tabs + active port + last URL per
port (session restores after process death), 20-entry history ring, DevTools
tab, desktop preference. All synchronous `SharedPreferences` — a failed write
is reported, never silently lost.

## Build (CI)

Push to `main` → **Actions → build-apk** → `localview-debug` artifact.
Manual/debug APKs: **Actions → release-apk → Run workflow** (or push a `v*`
tag) → APK attached straight to the GitHub Release.
No Gradle wrapper in repo; CI provisions Gradle 8.10 via
`gradle/actions/setup-gradle` and uses the runner's preinstalled Android SDK
(`setup-android` is broken on current images — deliberately not used).

## Build (local, Android Studio)

Open this folder in Android Studio (JDK 17, SDK 35) and Run. Debug builds use
the committed `app/debug.keystore` (fixed debug key, public convention
password) so every build shares one signature.

## What's inside

| File | Job |
|---|---|
| `LocalActivity.kt` | Dashboard + browser + tabs (max 5, × to close) + session restore + storage UI |
| `LocalWebView.kt` | Tuned settings (JS/DOM/SW/HMR forced ON), console capture, hard reload, per-port site-data clear, desktop UA spoof, maximize-safe |
| `LocalPort.kt` | Bare-port parser (`5173` → `http://localhost:5173/`), loopback helpers |
| `ProjectsRepo.kt` | `{name, url, port}` in synchronous SharedPreferences, write-verify + retry |
| `LocalStore.kt` | Session (tabs/active/last-URL), history ring, prefs, cache accounting, reset-all |
| `Probe.kt` | 800ms `HEAD :PORT` liveness on `Dispatchers.IO` |
| `DevToolsSheet.kt` | Legacy F12 bottom sheet (kept; inline panel is default) |
| `MenuSheet.kt` / `ToolsSheet.kt` | Mockup-styled bottom sheets: overflow menu + dev-tools hub |
| `res/drawable/lv_*.xml` | Ghost buttons, cards + hard shadows, chips, nav keys, URL fields + focus glow |
| `res/drawable/ic_*.xml` | 17 stroke icons traced from the mockup SVGs |
| `mockup/` | Clickable fake UI (the design source of truth) — serve with `python3 -m http.server` |

## Design language

Matrix-brutalist, mono everywhere, ghost (transparent) buttons, 2dp borders,
4px/3px offset hard shadows, 7dp/4dp radii, desaturated phosphor accents
(`#4ED58A` + cyan/amber/clay/slate/moss per-project coding). No emoji, SVG
stroke icons only. Dark-only by design (`MODE_NIGHT_YES` pinned).

## Roadmap

- NET request inspector tab (method/URL/status/ms)
- Port scanner (`3000–9999` sweep → suggested cards)
- Per-project pin-to-Home launcher shortcuts
- Console error ticker above the tools dock

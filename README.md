# LocalView (real app)

Minimal localhost WebView. Spec: [`./features.md`](./features.md) · clickable mockup: [`./mockup/`](./mockup/).

## Build (CI)

Push to `main` → **Actions → build-apk** → download `localview-debug` artifact.
No wrapper in repo; CI provisions Gradle via `gradle/actions/setup-gradle`.

## Build (local, Android Studio)

Open `localview-app/` in Android Studio (JDK 17, SDK 35) and Run.

## What's inside

- `LocalActivity.kt` — dashboard + single WebView, max 5 tabs, 20-entry port history
- `LocalWebView.kt` — tuned settings (JS/DOM/SW forced ON), console capture, hard reload, per-port site-data clear
- `LocalPort.kt` — bare-port parser (`5173` → `http://localhost:5173/`)
- `ProjectsRepo.kt` — `{name, url, port}` in DataStore
- `Probe.kt` — 800ms `HEAD :PORT` liveness
- `DevToolsSheet.kt` — F12 bottom sheet: HTML / JS / LOG for the active port

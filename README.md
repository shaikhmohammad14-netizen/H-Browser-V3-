# H Browser Next — Premium Liquid Glass Android Browser

A Kotlin + Jetpack Compose Android browser starter with real Android WebView browsing and a frosted-glass inspired interface.

## Included
- Android edge-to-edge layout with status-bar and navigation-bar insets (helps avoid the screenshot's status-bar overlap)
- Responsive Compose layout and rounded glass browser surfaces
- Real WebView navigation, back/forward/reload/home
- Search or URL entry with Google, Bing, DuckDuckGo and Brave search selection
- Bookmarks and browsing history stored locally
- Download handoff to Android DownloadManager
- Desktop-site user-agent option
- JavaScript, cookies and pop-up settings
- Dark/light appearance and glass-surface toggle
- Starter host-based ad/tracker blocking list
- Jelly-like press response on quick-access and bottom navigation controls
- GitHub Actions workflow to build a debug APK

## Build on GitHub from a phone
1. Download and extract this ZIP.
2. Create a new GitHub repository.
3. Upload the *contents* of the extracted folder to the repository root. Ensure `.github/workflows/android.yml` is included.
4. Open **Actions** → **Build H Browser** → **Run workflow** (or push to `main`).
5. Open the completed workflow run and download the `H-Browser-Next-debug` artifact.
6. Extract the artifact and install `app-debug.apk` on your Android device.

## Honest feature status
This is a functional WebView browser foundation, not a Chromium fork. The tab control currently starts a fresh home page and counts tabs; it does **not** yet keep independent simultaneous WebView instances or restore each tab's scroll/session state. A true isolated incognito profile, password manager, cross-device sync, extension support, complete download manager UI, and full EasyList/AdGuard filter engine are not implemented. The ad blocker is a starter domain denylist and will not block every ad. Website playback, logins, and compatibility depend on Android System WebView and each website.

## Vivo / OriginOS display note
The activity uses AndroidX `enableEdgeToEdge()` and Compose `statusBarsPadding()` / `navigationBarsPadding()` so browser chrome is laid out inside system safe areas. Final appearance can still vary with OriginOS display scaling, font size, gesture navigation, and Android System WebView version; test on the target phone.

## Local build
Use Android Studio with JDK 17 and Android SDK 35. Open this project and run the `app` configuration, or run `gradle assembleDebug` with Gradle 8.9.

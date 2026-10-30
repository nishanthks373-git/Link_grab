# LinkGrab

Android app (Kotlin + Jetpack Compose) that downloads video or audio from links.

## Features
- Paste a single link, or a whole list (one per line, or mixed with other text), into the input box
- **Import .txt**: pick a text file with links and they're all queued
- **Share to app**: share a link from your browser straight into LinkGrab
- **Preview before downloading**: every link shows its thumbnail, title and duration first. Tick the ones you want
- **Quality dropdown per item** (e.g. 1080p / 720p / 480p / 360p / Audio only MP3) with the approximate size in MB, a "Set all to" shortcut, and a running total for everything selected
- Size estimates come from the site's reported file sizes (or bitrate x duration), so they are approximate
- Direct file links (.mp4, .mp3, ...) are fetched directly; page links (YouTube and many other sites) use the bundled yt-dlp engine, with ffmpeg for merging and audio extraction
- 2 downloads in parallel, progress bars, cancel / retry, foreground notification so downloads continue in the background
- Files are saved to `Downloads/LinkGrab` (no storage permission needed)

## Build
1. Install Android Studio (current stable) and open the `LinkGrab` folder.
2. Let Gradle sync. If prompted, accept the suggested Gradle version.
3. Run on a device or emulator (Android 10 / API 29 or newer). Use **Build > Build APK(s)** for a shareable APK.

## Private downloads
Tick **Download privately** in the bar at the bottom before you tap Download. Private files are saved inside the app's own storage instead of Downloads:
- Other apps, file managers and the gallery can't see or open them. Use the **Private** tab to play or delete them.
- They are deleted automatically when the app is uninstalled. App backup is turned off, so they can't be restored from a backup either.

## Build the APK on GitHub (no Android Studio needed)
The "Build APK" workflow runs on every push (or manually from the Actions tab).
- On success, the APK is published to the **apk-build** branch as `LinkGrab.apk` and also attached to the run as the **LinkGrab-apk** artifact. Download it from the apk-build branch on GitHub (open `LinkGrab.apk`, then Download raw file), copy it to your phone and install it (allow installs from unknown sources when asked).
- On failure, the last lines of the build log are published to the **build-log** branch (`build.log`).
- Builds use a fixed debug key (`app/link.keystore`), so a new build installs over the previous one without uninstalling.

## Notes
- The yt-dlp library version is set in `app/build.gradle.kts` (`ytdlp`). Check Maven Central for `io.github.junkfood02.youtubedl-android` and bump it if a newer release exists.
- Sites change often. Tap **Update engine** in the app now and then to pull the latest yt-dlp.
- The first yt-dlp download is slower because the engine unpacks itself.
- Only download content you have the right to download. Many sites' terms of service prohibit downloading.
- Release builds: `isMinifyEnabled` is off on purpose; enabling R8 needs keep rules for the yt-dlp library.

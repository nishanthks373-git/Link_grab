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

## Build the APK on GitHub (no Android Studio needed)
1. Create a new GitHub repository and upload the contents of this folder to it (keep the `.github` folder).
2. Open the **Actions** tab. The "Build APK" workflow runs on every push, or run it manually with **Run workflow**.
3. When it finishes (about 5 to 10 minutes), open the run and download **LinkGrab-debug-apk** from the Artifacts section.
4. Unzip it, copy `app-debug.apk` to your phone and install it (allow installs from unknown sources when asked).
If the build fails, open the failed step and send me the error text.

## Notes
- The yt-dlp library version is set in `app/build.gradle.kts` (`ytdlp`). Check Maven Central for `io.github.junkfood02.youtubedl-android` and bump it if a newer release exists.
- Sites change often. Tap **Update engine** in the app now and then to pull the latest yt-dlp.
- The first yt-dlp download is slower because the engine unpacks itself.
- Only download content you have the right to download. Many sites' terms of service prohibit downloading.
- Release builds: `isMinifyEnabled` is off on purpose; enabling R8 needs keep rules for the yt-dlp library.

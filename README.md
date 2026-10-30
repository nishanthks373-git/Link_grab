# LinkGrab

Android app (Kotlin + Jetpack Compose) that downloads video or audio from links.

## Features
- Paste a single link, or a whole list (one per line, or mixed with other text), into the input box
- **Import .txt**: pick a text file with links and they're all queued
- **Share to app**: share a link from your browser straight into LinkGrab
- **Preview before downloading**: every link shows its thumbnail, title and duration first. Tick the ones you want
- **Download one video on its own**: every preview card has a round download button that downloads just that video at the quality shown. It then moves to the Downloads list like any other
- **Duplicate warning**: if a file with the same name is already saved (or already downloading), the app asks whether to download it again as a copy with `_1` added to the name (`_2`, `_3`... if needed). With several files you can also skip the duplicates
- **Quality dropdown per item** (e.g. 1080p / 720p / 480p / 360p / Audio only MP3) with the approximate size in MB, a "Set all to" shortcut, and a running total for everything selected
- Size estimates come from the site's reported file sizes (or bitrate x duration), so they are approximate
- Direct file links (.mp4, .mp3, ...) are fetched directly; page links (YouTube and many other sites) use the bundled yt-dlp engine, with ffmpeg for merging and audio extraction
- Up to 5 downloads in parallel, with live speed and time left for each download and a total speed, plus cancel / retry and a notification so downloads continue in the background
- Files are saved to `Downloads/LinkGrab` (no storage permission needed)

## Build
1. Install Android Studio (current stable) and open the `LinkGrab` folder.
2. Let Gradle sync. If prompted, accept the suggested Gradle version.
3. Run on a device or emulator (Android 10 / API 29 or newer). Use **Build > Build APK(s)** for a shareable APK.

## Downloads and the Downloaded tab
Tap the small round download button on a video to pick its resolution (and whether to keep it inside the app only) for that one download. The Download button in the bottom bar downloads everything ticked normally. Finished downloads leave the main list and appear in the **Downloaded** tab, which can play or delete them.

Files kept inside the app are saved in the app's own storage, so other apps, file managers and the gallery can't see them, and uninstalling the app deletes them. They are reached by pulling down twice at the top of the Downloaded list, then confirming with your fingerprint or screen lock. They lock again whenever you leave the app.

## Player
Private videos open full screen (wide videos turn the phone to landscape). Tap to show or hide the controls, double tap the right side to jump 10 seconds forward and the left side to jump 10 seconds back (keep double tapping to add more), drag the seek bar, and tap the speed button to change playback speed.

## Updating the app
Install each new APK over the old one, without uninstalling first. Private files stay on the phone through updates because every build uses the same package name and the same signing key (`app/link.keystore`), and each build has a higher version number. Uninstalling the app, or clearing its storage in Android settings, deletes the private files.

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

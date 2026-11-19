# YouTube Car

Kotlin Android app: browse/search YouTube, open channels, play video on the phone (portrait or
landscape full-screen), and show the video on the **Android Auto** screen.

* No ads, analytics, tracking SDKs, Google Play Services, or API keys.
* Permissions (plus ACCESS_NETWORK_STATE, added by Media3): INTERNET, WAKE_LOCK, FOREGROUND_SERVICE(+MEDIA_PLAYBACK), POST_NOTIFICATIONS (media controls),
  and the three Android Auto permissions (templates, surface, car speed). Nothing else.
* No root.

## Important: what Android Auto allows

Android Auto has **no public video API**. This app uses the one legitimate door that exposes a raw
drawing surface: the Car App Library *navigation* category (`NavigationTemplate` + `SurfaceCallback`).
The surface is mirrored from a virtual display hosting a letterboxing ExoPlayer view.

**Video is shown only while the car reports speed 0 for 3 s.** When moving (or if the car reports no
speed) the picture is removed and playback continues as **audio only**. This is deliberate and has no
override: video in front of a moving driver is dangerous and illegal in many places. Audio keeps
playing, and you can also use Android Auto's normal media UI (app "YouTube Car" → Trending / voice search).
Limitation: speed 0 cannot be told apart from a red light; treat it as "parked" yourself.

## Build

Requirements: JDK 17, Android SDK (platform 35), internet for Gradle. Gradle verifies every
downloaded dependency against `gradle/verification-metadata.xml` (SHA-256) and the Gradle
distribution against `distributionSha256Sum`.

    ./gradlew assembleRelease
    # -> app/build/outputs/apk/release/app-release-unsigned.apk   (NOT installable until signed)

## Signing (no key is, or ever should be, in this repository)

Gradle deliberately never sees your key. You sign the finished APK with `apksigner`.

**1. Create your own key once** (any computer or Termux on the phone; keep the file private, back it up):

    keytool -genkeypair -v -keystore ~/ytcar-release.jks -alias ytcar -keyalg RSA -keysize 4096 -validity 10000

Choose a strong password when asked.

**2a. Sign locally:**

    apksigner sign --ks ~/ytcar-release.jks --out YouTubeCar.apk app-release-unsigned.apk
    apksigner verify --print-certs YouTubeCar.apk

**2b. Or let GitHub Actions sign** (repo, Settings, Secrets and variables, Actions, New repository secret):

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | output of `base64 -w0 ~/ytcar-release.jks` |
| `ANDROID_KEYSTORE_PASSWORD` | keystore password |
| `ANDROID_KEY_ALIAS` | `ytcar` |
| `ANDROID_KEY_PASSWORD` | key password |

Then run the workflow with **publish = true** (Actions, Build APK, Run workflow). Without the secrets the
workflow only produces an `UNSIGNED` artifact, and publishing is refused. Compare the certificate SHA-256
printed in the job log with `keytool -list -v -keystore ~/ytcar-release.jks`. Only push / manual-dispatch
triggers are used (no pull_request), so secrets are never exposed to forks.

The build job has read-only repository permissions; only the manual publish job can write releases.
All GitHub Actions are pinned to commit SHAs.

## Install and enable in Android Auto

1. Install the APK (allow "install unknown apps").
2. Android Auto settings → tap **Version** 10 times → ⋮ menu → **Developer settings** →
   enable **Unknown sources**. Reconnect to the car.
3. Open the Android Auto app launcher: **YouTube Car** appears (video app) and in the media list (audio).

## Security design

* **Network allow-list** (`net/Net.kt`): one HTTP client for everything; only `youtube.com`, `googlevideo.com`,
  `ytimg.com`, `youtubei.googleapis.com` and a few YouTube image hosts. HTTPS only; others are refused before DNS.
* **Exported services**: `YtCarService` accepts only the genuine Android Auto host (package + signing-cert digest);
  `PlaybackService` accepts only Android Auto (same check), the platform, and this app. Caller-supplied URIs and
  metadata are discarded; only a YouTube video id is accepted, and media loading is HTTP(S)-only.
* No login, cookies or accounts. The only data kept is a local watch history and your chosen quality, in the app's
  private storage on the phone (not backed up, never uploaded). History > Clear wipes it.

## Security design

* **Network allow-list** (`net/Net.kt`): one HTTP client for everything; only `youtube.com`, `googlevideo.com`,
  `ytimg.com`, `youtubei.googleapis.com` and a few YouTube image hosts. HTTPS only; others are refused before DNS.
* **Exported services**: `YtCarService` accepts only the genuine Android Auto host (package + signing-cert digest);
  `PlaybackService` accepts only Android Auto (same check), the platform, and this app. Caller-supplied URIs and
  metadata are discarded; only a YouTube video id is accepted, and media loading is HTTP(S)-only.
* No login, cookies, accounts or local storage of user data.

## Dependencies (all open source)

| Dependency | Purpose | License |
|---|---|---|
| NewPipeExtractor v0.27.1 (JitPack, built from source by JitPack) | YouTube data & stream URLs, no API key | GPL-3.0 |
| AndroidX Media3 1.5.1 | playback + media session | Apache-2.0 |
| AndroidX Car App Library 1.4.0 | Android Auto | Apache-2.0 |
| Jetpack Compose / Material3, Coil, OkHttp, kotlinx-coroutines | UI, images, HTTP | Apache-2.0 |

JitPack is restricted in `settings.gradle.kts` to NewPipeExtractor's own groups, and every artifact is checksum-verified. Because NewPipeExtractor
is GPL-3.0, this app is distributed under GPL-3.0.

## Known limits

YouTube changes often; if playback breaks, bump the NewPipeExtractor version. Phone and car share one
player (the last screen to attach shows the picture). Video quality is selectable (360/480/720/1080p, default 720p); the best available stream at or below the choice is used.
Car lists have no thumbnails and are capped by the host's row limit.

## Regenerating dependency checksums

After changing a dependency version: `./gradlew --write-verification-metadata sha256 assembleRelease`,
then review the diff of `gradle/verification-metadata.xml` before committing. (Checksums are
trust-on-first-use; compare unfamiliar entries with the artifact's official release.)

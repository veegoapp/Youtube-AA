# YouTube Car

Kotlin Android app: browse/search YouTube, open channels, play video on the phone (portrait or
landscape full-screen), and show the video on the **Android Auto** screen.

* No ads, analytics, tracking SDKs, Google Play Services, or API keys.
* Permissions: INTERNET, WAKE_LOCK, FOREGROUND_SERVICE(+MEDIA_PLAYBACK), POST_NOTIFICATIONS (media controls),
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

Requirements: JDK 17, Android SDK (platform 35), internet for Gradle.

    ./gradlew assembleRelease
    # -> app/build/outputs/apk/release/app-release.apk

Or push to GitHub: the **Build APK** workflow builds on GitHub's runners and attaches
`YouTubeCar.apk` to a release (Releases page → latest `build-N`), downloadable on your phone.

The APK is signed with the committed, public key `keystore/sideload.keystore` (password `android`).
That is fine for personal sideloading and keeps updates installable; use your own key otherwise.

## Install and enable in Android Auto

1. Install the APK (allow "install unknown apps").
2. Android Auto settings → tap **Version** 10 times → ⋮ menu → **Developer settings** →
   enable **Unknown sources**. Reconnect to the car.
3. Open the Android Auto app launcher: **YouTube Car** appears (video app) and in the media list (audio).

## Dependencies (all open source)

| Dependency | Purpose | License |
|---|---|---|
| NewPipeExtractor v0.27.1 (JitPack, built from source by JitPack) | YouTube data & stream URLs, no API key | GPL-3.0 |
| AndroidX Media3 1.5.1 | playback + media session | Apache-2.0 |
| AndroidX Car App Library 1.4.0 | Android Auto | Apache-2.0 |
| Jetpack Compose / Material3, Coil, OkHttp, kotlinx-coroutines | UI, images, HTTP | Apache-2.0 |

JitPack is restricted in `settings.gradle.kts` to NewPipeExtractor's own groups. Because NewPipeExtractor
is GPL-3.0, this app is distributed under GPL-3.0.

## Known limits

YouTube changes often; if playback breaks, bump the NewPipeExtractor version. Phone and car share one
player (the last screen to attach shows the picture). Max video height is 720p (`PlayerHub.MAX_HEIGHT`).
Car lists have no thumbnails and are capped by the host's row limit.

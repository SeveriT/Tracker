<p align="center">
  <img src="https://github.com/SeveriT/Overclock/blob/master/app/src/main/res/mipmap-hdpi/app_logo_foreground.png?raw=true" width="96" />
</p>
<h1 align="center">Overclock</h1>
<p align="center">A personal fitness companion for Android</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white" />
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" />
  <img src="https://img.shields.io/badge/API-Strava-FC4C02?style=flat-square&logo=strava&logoColor=white" />
  <img src="https://img.shields.io/badge/API-Spotify-1DB954?style=flat-square&logo=spotify&logoColor=white" />
</p>

---

Overclock is an Android app built for athletes who want full control over their training data — without subscriptions, ads, or bloat. Log workouts, track body weight, monitor Strava activities, and time your sessions, all from a single fast interface.

## Features

- **Workout logging** — log sets, reps and weight with auto-fill from history and personal best tracking
- **Weight tracking** — smooth trend chart with BMI, height profile, and historical data management
- **Strava integration** — view your activity calendar, sync profile data, and upload workouts directly from the timer
- **Workout timer** — animated ring timer with lap tracking, persistent status bar notification, and Strava upload on finish
- **Music widget** — integrated Spotify controller with wavy progress bar, album art, and gesture-based playback controls
- **Volume stats** — total volume lifted broken down per exercise with animated progress bars and volume tracking
- **Notes** — dedicated training journal for freeform notes with date-based organization
- **Weekly summary** — overview of recent workouts, weight trends, and Strava activity streaks
- **Customization** — fully dynamic Material 3 theme with user-adjustable RGB accent colors
- **Cloud sync & Backup** — automated Google Drive backups via WorkManager and manual local backup/restore options

## Built with

- Kotlin + Jetpack Compose
- Room for local persistence
- ViewModel + StateFlow
- Navigation Compose with horizontal swipe gestures
- Strava OAuth2 + REST API
- Spotify MediaSession integration
- Google Drive API + WorkManager
- Material 3 with dynamic color customization

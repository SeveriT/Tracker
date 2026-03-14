<p align="center">
  <img src="https://github.com/SeveriT/Overclock/blob/master/app/src/main/res/mipmap-hdpi/app_logo_foreground.webp?raw=true" width="96" />
</p>

<h1 align="center">Overclock</h1>
<p align="center">A personal fitness companion for Android</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white" />
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" />
  <img src="https://img.shields.io/badge/API-Strava-FC4C02?style=flat-square&logo=strava&logoColor=white" />
</p>

---

Overclock is Android app built for athletes who want full control over their training data — without subscriptions, ads, or bloat. Log workouts, track body weight, monitor Strava activities, and time your sessions, all from a single fast interface.

## Features

- **Workout logging** — log sets, reps and weight with auto-fill from history and personal best tracking
- **Weight tracking** — smooth trend chart with BMI, 30-day prediction and weekly rate
- **Strava integration** — view your activity calendar and upload workouts directly from the timer
- **Workout timer** — animated ring timer with pause, resume and Strava upload on finish
- **Volume stats** — total volume lifted broken down per exercise with animated progress bars
- **Notes** — freeform training notes with title, content and date
- **Weekly summary** — overview of recent workouts, weight trend and Strava activity streak

## Built with

- Kotlin + Jetpack Compose
- Room for local persistence
- ViewModel + StateFlow
- Navigation Compose with swipe gestures
- Strava OAuth2 + REST API
- Google Drive backup via WorkManager
- Material 3 with custom dark theme

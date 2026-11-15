# Smart Track

An **offline-first** Outdoor Workout & Wellness **Digital Twin** built with Kotlin, Jetpack Compose (Material 3), and modern Android architecture patterns. Track outdoor Cycling, Walking, and Jogging sessions while combining phone sensors, microclimate context, and subjective readiness signals into real-time guidance.

## 📥 Download APK

You can download the ready-to-install `.apk` directly from:
- **[GitHub Releases](https://github.com/aggelosflampouris-byte/Bicycle-App/releases)** (Recommended — download the latest release APK).
- **GitHub Actions Tab** — Go to **Actions** -> click the latest workflow run -> download the **SmartTrack-APK** artifact.
- **In-App Share** — Open Settings inside the app to share a QR code link for direct download.

## ✨ Key Features

- 🧬 **Physiological Digital Twin (`MainTab.TWIN`)** — Closed-loop tracking of **Body Battery** (0–100), **Readiness Score**, **ATL (7d) / CTL (28d) / ACWR** load balance, optimal training zones, hydration rate (`L/h`), and an interactive **What-If Workout Simulator**.
- 📱 **Phone-Only Sensor Telemetry (`WorkoutSensorCollector`)** — Hardware step detector (`ACTIVITY_RECOGNITION`) + accelerometer cadence fallback, rolling 60s gait/form stability score (`0–100`), barometer altitude fusion (`TYPE_PRESSURE`), and low-light dusk detection (`TYPE_LIGHT`).
- 🌤️ **Keyless Outdoor Microclimate (`Open-Meteo`)** — Live temperature, feels-like temperature, wind speed, humidity, UV index, precipitation probability, environmental stress modifier, and best workout window with offline DataStore TTL caching.
- 🎯 **Adaptive In-Workout & Post-Workout Loop** — Rate-limited smart Twin nudges (heat hydration, dusk visibility, form fade) via TTS/HUD banners, 1-tap in-workout effort (RPE) tags, and post-workout check-ins (`Sleep / Energy / Soreness / Stress / Hydration`).
- 🏃 **Multi-Activity Outdoor Tracking** — Dedicated Cycling, Walking, and Jogging physics (Mifflin-St Jeor BMR, MET calories, Watts/kg), dynamic GPS polling with auto-pause, GPX route import, climb detection, off-route alerts, and Ghost Pacer.
- 🤖 **Digital Twin AI Coach** — Context-aware coaching powered by `Qwen/Qwen2.5-32B-Instruct` via the Hugging Face OpenAI-compatible router, enriched with live Digital Twin readiness, weather context, wellness check-ins, workout metrics, weekly training plans, and tactical debriefs.
- 🛡️ **Crash Impact Detection & SOS** — Accelerometer crash impact monitoring with emergency SMS distress beacon.
- 💾 **GPX & Encrypted JSON Backup** — Export standard `.gpx` routes or password-encrypted JSON backups (including wellness check-ins and Twin snapshots).

## 🛠 Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + Coroutines + Flows |
| Database | Room v14 (offline-first, explicit non-destructive migrations) |
| Sensors & Location | `SensorManager` (Step/Accel/Baro/Light), `FusedLocationProviderClient` |
| Maps | osmdroid (OpenStreetMap) |
| Environment API | Open-Meteo (keyless forecast API) |
| Background | WorkManager (Hilt on-demand `Configuration.Provider`), ForegroundService (`location`) |
| AI | OpenAI-compatible Chat Completions (`Qwen/Qwen2.5-32B-Instruct`) |
| Networking | Retrofit2 + OkHttp + Gson |
| DI | Hilt |

## 🏗 Architecture

```text
app/
├── data/
│   ├── local/          # Room DB v14, Entities (User, WorkoutSession, WellnessCheckIn, TwinSnapshot, Routine, PR, TrainingPlan), DAOs, SettingsRepository
│   └── remote/         # AiCoachApiService, AiCoachRepository, OpenMeteoApiService, EnvironmentContextRepository, AuthRepository, FirestoreRepository
├── engine/             # DigitalTwinEngine, PhysicsEngine, PersonalRecordEngine, RecoveryEngine
├── sensor/             # WorkoutSensorCollector (cadence, gait stability, barometer, ambient lux)
├── safety/             # CrashDetectionEngine
├── navigation/         # GpxParser, ClimbEngine, OffRouteDetector
├── gamification/       # GhostPacerEngine
├── service/            # OutdoorTrackingService (ForegroundService), NotificationHelper, RoutineScheduler
├── di/                 # Hilt modules (AppModule)
├── ui/
│   ├── twin/           # Digital Twin Hub (Body Battery, ACWR, weather window, check-ins, What-If simulator)
│   ├── dashboard/      # Dashboard + DigitalTwinDashboardCard & modular components
│   ├── tracking/       # Live tracking screen + Map rendering + Twin nudges & RPE bar
│   ├── summary/        # Post-workout summary + Twin strain & check-in feedback
│   ├── progress/       # Analytics charts & period comparisons (Distance, Speed, Calories, Strain)
│   ├── chat/           # Personal AI Coach chat + Multi-Session Select
│   ├── onboarding/     # Biometrics setup
│   ├── settings/       # Settings (Voice Nudges, SOS, Backup, QR Code share)
│   └── theme/          # Material 3 outdoor dark/light themes
└── Navigation.kt       # AppNavGraph
```

## 🔒 Permissions

- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` — GPS workout tracking and local weather context
- `ACCESS_BACKGROUND_LOCATION` — Workout tracking when the screen is off
- `POST_NOTIFICATIONS` — Persistent tracking notification & goal reminders
- `FOREGROUND_SERVICE_LOCATION` — Foreground location tracking service
- `ACTIVITY_RECOGNITION` — Optional (API 29+) hardware step detector cadence (falls back to accelerometer if declined)
- `RECORD_AUDIO` — Optional voice coaching queries
- `SEND_SMS` — Optional emergency SOS distress beacon (`android.hardware.telephony` marked `required="false"`)

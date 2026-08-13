# Smart Track

A **100% free-to-operate** Smart Fitness Tracker app built with Kotlin, Jetpack Compose (Material 3), and modern Android architecture patterns. Track your Rides, Walks, and Jogs with an AI-powered coach.

## 📥 Download APK

You can download the ready-to-install `.apk` directly from:
- **[GitHub Releases](https://github.com/aggelosflampouris-byte/Bicycle-App/releases)** (Recommended — download `app-debug.apk` under the latest release).
- **GitHub Actions Tab** — Go to **Actions** -> click the latest workflow run -> download the **SmartTrack-Debug-APK** artifact.
- **In-App Share** — Open Settings inside the app to share a QR code for direct download!

## ✨ Key Features

- 🏃 **Multi-Activity Tracking** — Dedicated modes for Cycling, Walking, and Jogging with specific UI colors and physics calculations.
- 🎯 **Workout Routines** — Set daily, weekly, or monthly goals for distance or calories. The app will notify you when you are falling behind and auto-improve your goals when you crush them!
- 🔋 **Battery-Optimized GPS** — Dynamic LocationRequest polling with a 3s base interval and 2m movement threshold to drastically save battery.
- ⏸️ **Auto-Pause Engine** — Automatically pauses when stationary for 5+ seconds to preserve your stats and battery.
- 🗺️ **Incremental OpenStreetMap** — O(1) seamless map rendering that prevents lag even on massive 100km+ rides.
- 🔥 **Physics Engine** — Mifflin-St Jeor BMR, MET-based calories, and Watts/kg estimation.
- 🤖 **Personal Coach AI** — Google Gemini 1.5 Flash with RAG (injects your real workout data into the prompt). View and send multiple past workouts for deep analysis.
- 📊 **Streaming Chat** — Real-time typing effect as Gemini responds.
- 🏔️ **Elevation Tracking** — Smartly filters GPS altitude noise to accurately calculate total elevation gain over a session.
- 📈 **Speed Profile Charts** — Smooth, downsampled, animated charts displaying your pacing strategy for any workout.
- 💾 **GPX Export** — Generate standard `.gpx` files and export them directly to your device to upload to Strava, Garmin Connect, and more.
- 🌙 **High-Contrast Dark UI** — Outdoor-readable display optimized for sunlight.

## 🛠 Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + Coroutines + Flows |
| Database | Room (offline-first, complex migrations) |
| Maps | osmdroid (OpenStreetMap) |
| Location | FusedLocationProviderClient |
| Background | AlarmManager (Routines), ForegroundService (GPS) |
| AI | Google Gemini 1.5 Flash (streaming) |
| Networking | Retrofit2 + OkHttp |
| DI | Hilt |

## 🏗 Architecture

```
app/
├── data/
│   ├── local/          # Room DB, Entities (User, WorkoutSession, Routine), DAOs, Repository
│   └── remote/         # GeminiApiService (Retrofit), GeminiRepository (SSE streaming)
├── engine/             # PhysicsEngine — BMR, calories, Watts/kg, Haversine
├── service/            # CyclingTrackingService (ForegroundService), RoutineScheduler
├── di/                 # Hilt modules (AppModule)
├── ui/
│   ├── dashboard/      # Dashboard + RoutineProgressCard
│   ├── tracking/       # Live tracking screen + Map rendering
│   ├── summary/        # Post-workout summary
│   ├── chat/           # Personal Coach AI chat + Multi-Session Select
│   ├── onboarding/     # Biometrics setup
│   ├── settings/       # Settings + QR Code share
│   └── theme/          # Material 3 dynamic themes based on activity
└── Navigation.kt       # NavHost graph
```

## 📍 GPS Tracking Details

- **Battery Saver**: Base interval is 3 seconds, min distance 2m. 
- **Accuracy filter**: Discards GPS points with accuracy > 35m.
- **Jump filter**: Discards impossible jumps (> 120 km/h) unless validating a tunnel recovery.
- **Batch writes**: Buffers GPS points and flushes to Room DB in an IO coroutine to prevent lockups.

## 🤖 AI Coach (RAG)

The Personal Coach system prompt is dynamically built with real user and session data. Users can attach one or more specific workouts to the context before sending a message:

```
Act as a professional fitness coach, acting as my "Personal Coach".
[USER DATA] Gender: male, Age: 32, Height: 178cm, Weight: 73kg.
[RECENT SESSION] Type: WALKING, Distance: 5.5km, Avg Speed: 6.3km/h, Calories: 342.
Analyze this strictly for progress. Keep it short, encouraging, and do not give medical advice.
```

## 🔒 Permissions Required

- `ACCESS_FINE_LOCATION` — GPS tracking
- `ACCESS_BACKGROUND_LOCATION` — Tracking with screen off
- `POST_NOTIFICATIONS` — Persistent tracking notification & Routine reminders
- `FOREGROUND_SERVICE_LOCATION` — Foreground service type

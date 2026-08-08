# Smart Cycling Tracker — VeloTrack

A **100% free-to-operate** Smart Cycling Tracker app built with Kotlin, Jetpack Compose (Material 3), and modern Android architecture patterns.

## Features

- 🚴 **Live GPS Tracking** — Foreground service with real-time speed, distance, and timer
- ⏸️ **Auto-Pause Engine** — Automatically pauses when stationary for 5+ seconds (< 2m displacement)
- 🗺️ **OpenStreetMap Maps** — Free osmdroid maps (no Google Maps billing)
- 🔥 **Physics Engine** — Mifflin-St Jeor BMR, MET-based calories, Watts/kg estimation
- 🤖 **VeloCoach AI** — Google Gemini 1.5 Flash with RAG (injects your real workout data into the prompt)
- 📊 **Streaming Chat** — Real-time typing effect as Gemini responds
- 🌙 **High-Contrast Dark UI** — Outdoor-readable display optimized for sunlight

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + Coroutines + Flows |
| Database | Room (offline-first) |
| Maps | osmdroid (OpenStreetMap) |
| Location | FusedLocationProviderClient |
| AI | Google Gemini 1.5 Flash (streaming) |
| Networking | Retrofit2 + OkHttp |
| DI | Hilt |


## Architecture

```
app/
├── data/
│   ├── local/          # Room DB, Entities (User, WorkoutSession), DAOs
│   └── remote/         # GeminiApiService (Retrofit), GeminiRepository (SSE streaming)
├── engine/             # PhysicsEngine — BMR, calories, Watts/kg, Haversine
├── service/            # CyclingTrackingService (ForegroundService) + NotificationHelper
├── di/                 # Hilt modules (AppModule)
├── ui/
│   ├── dashboard/      # Dashboard screen + ViewModel
│   ├── tracking/       # Live tracking screen + ViewModel
│   ├── summary/        # Post-workout summary screen + ViewModel
│   ├── chat/           # VeloCoach AI chat + ViewModel
│   ├── onboarding/     # Biometrics setup + ViewModel
│   └── theme/          # Material 3 dark theme (electric green / deep navy)
└── Navigation.kt       # NavHost graph
```

## GPS Tracking Details

- **Accuracy filter**: Discards GPS points with accuracy > 20m
- **Speed filter**: Discards points with instantaneous speed > 100 km/h
- **Auto-pause**: Pauses timer when displacement < 2m for 5 consecutive seconds
- **Batch writes**: Buffers GPS points and writes every 50 to Room DB (IO coroutine)

## AI Coach (RAG)

The VeloCoach system prompt is dynamically built with real user and session data:

```
Act as "VeloCoach", a professional cycling coach.
[USER DATA] Gender: male, Age: 32, Height: 178cm, Weight: 73kg.
[RECENT SESSION] Distance: 24.5km, Avg Speed: 26.3km/h, Performance: 3.21 W/kg, Calories: 742.
Analyze this strictly for cycling progress. Keep it short, encouraging, and do not give medical advice.
```

## Permissions

- `ACCESS_FINE_LOCATION` — GPS tracking
- `ACCESS_BACKGROUND_LOCATION` — Tracking with screen off
- `POST_NOTIFICATIONS` — Persistent tracking notification (Android 13+)
- `FOREGROUND_SERVICE_LOCATION` — Foreground service type

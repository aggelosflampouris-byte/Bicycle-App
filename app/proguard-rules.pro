# ProGuard / R8 rules for Smart Track

# Kotlin
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.**

# Hilt & WorkManager
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-dontwarn dagger.hilt.**
-keep class * extends androidx.work.ListenableWorker { *; }

# Retrofit + OkHttp
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-keep interface com.fitnessapp.tracker.data.remote.api.** { *; }
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# Gson + anonymous TypeToken generic signature preservation
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# App models, Room entities, enums, and DTOs (Gson, Room, and Firestore POJOs)
-keep class com.fitnessapp.tracker.data.local.entity.** { *; }
-keep class com.fitnessapp.tracker.data.remote.model.** { *; }
-keep class com.fitnessapp.tracker.data.remote.EnvironmentSnapshot { *; }
-keep class com.fitnessapp.tracker.service.RoutePoint { *; }
-keep class com.fitnessapp.tracker.util.BackupData { *; }
-keep enum com.fitnessapp.tracker.engine.** { *; }
-keep enum com.fitnessapp.tracker.data.local.** { *; }
-keep enum com.fitnessapp.tracker.data.local.entity.** { *; }

# osmdroid
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# Play Services Location
-keep class com.google.android.gms.location.** { *; }
-dontwarn com.google.android.gms.**

package com.fitnessapp.tracker.di

import android.content.Context
import androidx.room.Room
import com.fitnessapp.tracker.BuildConfig
import com.fitnessapp.tracker.data.local.AppDatabase
import com.fitnessapp.tracker.data.local.dao.RoutineDao
import com.fitnessapp.tracker.data.local.dao.PersonalRecordDao
import com.fitnessapp.tracker.data.local.dao.TwinSnapshotDao
import com.fitnessapp.tracker.data.local.dao.UserDao
import com.fitnessapp.tracker.data.local.dao.WellnessCheckInDao
import com.fitnessapp.tracker.data.local.dao.WorkoutSessionDao
import com.fitnessapp.tracker.data.remote.api.OpenMeteoApiService
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ── Room Database ────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
        .addMigrations(
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4,
            AppDatabase.MIGRATION_4_5,
            AppDatabase.MIGRATION_5_6,
            AppDatabase.MIGRATION_6_7,
            AppDatabase.MIGRATION_7_8,
            AppDatabase.MIGRATION_8_9,
            AppDatabase.MIGRATION_9_10,
            AppDatabase.MIGRATION_10_11,
            AppDatabase.MIGRATION_11_12,
            AppDatabase.MIGRATION_12_13,
            AppDatabase.MIGRATION_13_14,
            AppDatabase.MIGRATION_14_15,
            AppDatabase.MIGRATION_15_16
        )
        .build()

    @Provides
    @Singleton
    fun provideUserDao(db: AppDatabase): UserDao = db.userDao()

    @Provides
    @Singleton
    fun provideWorkoutSessionDao(db: AppDatabase): WorkoutSessionDao = db.workoutSessionDao()

    @Provides
    @Singleton
    fun provideRoutineDao(db: AppDatabase): RoutineDao = db.routineDao()

    @Provides
    @Singleton
    fun providePersonalRecordDao(db: AppDatabase): PersonalRecordDao = db.personalRecordDao()

    @Provides
    @Singleton
    fun provideWellnessCheckInDao(db: AppDatabase): WellnessCheckInDao = db.wellnessCheckInDao()

    @Provides
    @Singleton
    fun provideTwinSnapshotDao(db: AppDatabase): TwinSnapshotDao = db.twinSnapshotDao()

    @Provides
    @Singleton
    fun provideDataBackupManager(@ApplicationContext context: Context, db: AppDatabase): com.fitnessapp.tracker.util.DataBackupManager {
        return com.fitnessapp.tracker.util.DataBackupManager(context, db)
    }

    // ── Networking ───────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().create()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                    else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideOpenMeteoApiService(okHttpClient: OkHttpClient, gson: Gson): OpenMeteoApiService =
        Retrofit.Builder()
            .baseUrl(OpenMeteoApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(OpenMeteoApiService::class.java)
}

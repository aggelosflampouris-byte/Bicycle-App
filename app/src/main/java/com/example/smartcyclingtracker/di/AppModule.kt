package com.example.smartcyclingtracker.di

import android.content.Context
import androidx.room.Room
import com.example.smartcyclingtracker.data.local.AppDatabase
import com.example.smartcyclingtracker.data.local.dao.ChatMessageDao
import com.example.smartcyclingtracker.data.local.dao.ChatSessionDao
import com.example.smartcyclingtracker.data.local.dao.RoutineDao
import com.example.smartcyclingtracker.data.local.dao.UserDao
import com.example.smartcyclingtracker.data.local.dao.WorkoutSessionDao
import com.example.smartcyclingtracker.data.remote.api.HfApiService
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
        .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6)
        .build()

    @Provides
    @Singleton
    fun provideUserDao(db: AppDatabase): UserDao = db.userDao()

    @Provides
    @Singleton
    fun provideWorkoutSessionDao(db: AppDatabase): WorkoutSessionDao = db.workoutSessionDao()

    @Provides
    @Singleton
    fun provideChatMessageDao(db: AppDatabase): ChatMessageDao = db.chatMessageDao()

    @Provides
    @Singleton
    fun provideChatSessionDao(db: AppDatabase): ChatSessionDao = db.chatSessionDao()

    @Provides
    @Singleton
    fun provideRoutineDao(db: AppDatabase): RoutineDao = db.routineDao()

    // ── Networking ───────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().create()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)  // Long timeout for streaming
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit =
        Retrofit.Builder()
            .baseUrl(HfApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

    @Provides
    @Singleton
    fun provideHfApiService(retrofit: Retrofit): HfApiService =
        retrofit.create(HfApiService::class.java)
}

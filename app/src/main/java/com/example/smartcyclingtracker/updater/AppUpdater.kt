package com.example.smartcyclingtracker.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.example.smartcyclingtracker.BuildConfig
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdater @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    companion object {
        private const val TAG = "AppUpdater"
        private const val GITHUB_REPO_API = "https://api.github.com/repos/aggelosflampouris-byte/Bicycle-App/releases/latest"
    }

    suspend fun checkForUpdates(): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(GITHUB_REPO_API)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "VeloTrack-Android-App")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("GitHub API returned HTTP ${response.code}: ${response.message}")
                )
            }

            val bodyString = response.body?.string() ?: ""
            val release = gson.fromJson(bodyString, GitHubRelease::class.java)

            val latestTag = release.tagName.trim().removePrefix("v")
            val currentVersion = BuildConfig.VERSION_NAME.trim().removePrefix("v")

            val isNewer = isVersionNewer(latestTag, currentVersion)

            // Find APK asset from release assets or fallback to direct download URL
            val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            val downloadUrl = apkAsset?.browserDownloadUrl
                ?: if (release.tagName.isNotBlank()) "https://github.com/aggelosflampouris-byte/Bicycle-App/releases/download/${release.tagName}/app-debug.apk" else null

            val sizeMb = apkAsset?.let { it.sizeBytes / (1024.0 * 1024.0) }

            val updateInfo = UpdateInfo(
                isUpdateAvailable = isNewer && downloadUrl != null,
                latestVersion = release.tagName.ifBlank { "v$latestTag" },
                currentVersion = "v$currentVersion",
                releaseNotes = release.body.ifBlank { "Performance improvements and bug fixes." },
                downloadUrl = downloadUrl,
                apkSizeMb = sizeMb,
                publishedAt = release.publishedAt
            )

            Log.d(TAG, "Update check result: current=$currentVersion, latest=$latestTag, available=${updateInfo.isUpdateAvailable}")
            Result.success(updateInfo)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check for updates: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun downloadAndInstallApk(
        context: Context,
        downloadUrl: String,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "VeloTrack-Android-App")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("Failed to download APK: HTTP ${response.code}")
                )
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Empty download response body"))
            val totalBytes = body.contentLength()

            val destinationDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
            if (!destinationDir.exists()) destinationDir.mkdirs()

            val apkFile = File(destinationDir, "VeloTrack-update.apk")
            if (apkFile.exists()) apkFile.delete()

            body.byteStream().use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (totalBytes > 0) {
                            val progress = totalRead.toFloat() / totalBytes.toFloat()
                            withContext(Dispatchers.Main) {
                                onProgress(progress.coerceIn(0f, 1f))
                            }
                        }
                    }
                    output.flush()
                }
            }

            withContext(Dispatchers.Main) {
                onProgress(1.0f)
                installApk(context, apkFile)
            }

            Result.success(apkFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading/installing APK: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun installApk(context: Context, apkFile: File) {
        try {
            // Check unknown sources permission on Android 8.0+ (Oreo)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                    return
                }
            }

            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
            Log.d(TAG, "Launched APK installer with URI: $apkUri")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer: ${e.message}", e)
        }
    }

    /**
     * Compares semver strings, stripping any pre-release suffix (e.g. "-fix", "-beta").
     * Examples: "1.0.5-fix" → [1,0,5], "1.0.4" → [1,0,4]
     */
    private fun isVersionNewer(latest: String, current: String): Boolean {
        if (latest.isBlank() || current.isBlank()) return false
        // Strip any suffix after a hyphen: "1.0.5-fix" → "1.0.5"
        val latestClean = latest.substringBefore("-")
        val currentClean = current.substringBefore("-")
        val latestParts = latestClean.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = currentClean.split(".").mapNotNull { it.toIntOrNull() }

        val length = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until length) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}

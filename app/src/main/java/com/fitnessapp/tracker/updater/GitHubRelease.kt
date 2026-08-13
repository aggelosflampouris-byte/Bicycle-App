package com.fitnessapp.tracker.updater

import com.google.gson.annotations.SerializedName

data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("body") val body: String = "",
    @SerializedName("html_url") val htmlUrl: String = "",
    @SerializedName("published_at") val publishedAt: String = "",
    @SerializedName("assets") val assets: List<GitHubAsset> = emptyList()
)

data class GitHubAsset(
    @SerializedName("name") val name: String = "",
    @SerializedName("browser_download_url") val browserDownloadUrl: String = "",
    @SerializedName("size") val sizeBytes: Long = 0L,
    @SerializedName("content_type") val contentType: String = ""
)

data class UpdateInfo(
    val isUpdateAvailable: Boolean = false,
    val latestVersion: String = "",
    val currentVersion: String = "",
    val releaseNotes: String = "",
    val downloadUrl: String? = null,
    val apkSizeMb: Double? = null,
    val publishedAt: String = ""
)

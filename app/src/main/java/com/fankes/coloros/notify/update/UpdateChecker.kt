package com.fankes.coloros.notify.update

import android.content.Context
import com.fankes.coloros.notify.BuildConfig
import com.fankes.coloros.notify.R
import com.fankes.coloros.notify.core.ModuleInfo
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object UpdateChecker {

    data class Result(
        val hasUpdate: Boolean,
        val message: String,
        val releaseUrl: String = ModuleInfo.RELEASES_PAGE,
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(18, TimeUnit.SECONDS)
        .build()

    fun check(context: Context? = null): Result = try {
        val request = Request.Builder()
            .url(ModuleInfo.RELEASES_API)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "Glyph/${BuildConfig.VERSION_NAME}")
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            val body = response.body.string().trim()
            when (response.code) {
                404 -> Result(
                    hasUpdate = false,
                    message = context?.getString(R.string.update_no_release)
                        ?: "No releases published yet. You can visit the GitHub repository.",
                    releaseUrl = ModuleInfo.PROJECT_URL,
                )
                in 200..299 -> parseRelease(body, context)
                else -> Result(
                    hasUpdate = false,
                    message = context?.getString(R.string.update_check_failed_http, response.code)
                        ?: "Check failed (HTTP ${response.code})",
                )
            }
        }
    } catch (exception: Exception) {
        val detail = exception.localizedMessage ?: exception.javaClass.simpleName
        Result(
            hasUpdate = false,
            message = context?.getString(R.string.update_check_failed, detail)
                ?: "Update check failed: $detail",
        )
    }

    private fun parseRelease(body: String, context: Context? = null): Result {
        val json = JSONObject(body)
        val tag = json.optString("tag_name").orEmpty()
        val htmlUrl = json.optString("html_url").ifBlank { ModuleInfo.RELEASES_PAGE }
        val notes = json.optString("body").orEmpty().trim().take(800)
        val latest = normalizeVersion(tag)
        val current = normalizeVersion(BuildConfig.VERSION_NAME)
        if (latest.isEmpty()) {
            return Result(
                hasUpdate = false,
                message = context?.getString(R.string.update_cannot_parse_version)
                    ?: "Unable to parse latest version",
                releaseUrl = htmlUrl,
            )
        }
        return if (compareVersion(latest, current) > 0) {
            Result(
                hasUpdate = true,
                message = buildString {
                    val header = context?.getString(
                        R.string.update_found_new_version,
                        tag,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE,
                    ) ?: "New version found: $tag\nCurrent version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
                    append(header)
                    if (notes.isNotEmpty()) {
                        append("\n\n")
                        append(notes)
                    }
                },
                releaseUrl = htmlUrl,
            )
        } else {
            Result(
                hasUpdate = false,
                message = context?.getString(R.string.update_is_latest, BuildConfig.VERSION_NAME, tag)
                    ?: "Already up to date (${BuildConfig.VERSION_NAME})\nLatest Release: $tag",
                releaseUrl = htmlUrl,
            )
        }
    }

    private fun normalizeVersion(raw: String): String =
        raw.trim().removePrefix("v").removePrefix("V").substringBefore("-").trim()

    private fun compareVersion(left: String, right: String): Int {
        val leftParts = left.split('.').map { it.toIntOrNull() ?: 0 }
        val rightParts = right.split('.').map { it.toIntOrNull() ?: 0 }
        val size = maxOf(leftParts.size, rightParts.size)
        for (index in 0 until size) {
            val delta = leftParts.getOrElse(index) { 0 } - rightParts.getOrElse(index) { 0 }
            if (delta != 0) return delta
        }
        return 0
    }
}

package io.github.ruiquanqiao.alarmsets.core.update

import io.github.ruiquanqiao.alarmsets.core.domain.UpdateChecker
import io.github.ruiquanqiao.alarmsets.core.domain.UpdateStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

@Serializable
private data class GitHubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val name: String = "",
    val body: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("published_at") val publishedAt: String = "",
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
private data class GitHubAsset(
    val name: String = "",
    val size: Long = 0L,
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    @SerialName("content_type") val contentType: String = "",
)

/**
 * Checks the repository's GitHub Releases for a newer APK.
 *
 * ## A deliberate limitation
 *
 * This is for builds distributed directly - from GitHub, Obtainium, or a
 * sideloaded APK. **Google Play forbids an app from downloading and installing
 * its own updates**; a Play build has to use the Play In-App Update API
 * instead. That is why updating sits behind the
 * [io.github.ruiquanqiao.alarmsets.core.domain.UpdateChecker] port: swapping in
 * a Play-compliant implementation is a one-line change in the app's dependency
 * graph, with nothing else to rewrite.
 */
class GitHubReleaseUpdateChecker(
    private val owner: String,
    private val repo: String,
    private val currentVersionName: String,
    private val allowPreRelease: Boolean = false,
    private val client: OkHttpClient = defaultClient(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : UpdateChecker {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun check(): Result<UpdateStatus> = withContext(io) {
        runCatching {
            val release = fetchLatest()
                ?: return@runCatching UpdateStatus.UpToDate

            val remote = SemanticVersion.parseOrNull(release.tagName)
                ?: throw IOException("release tag is not a version: ${release.tagName}")
            val local = SemanticVersion.parseOrNull(currentVersionName)
                ?: throw IOException("installed version is not parseable: $currentVersionName")

            if (remote <= local) return@runCatching UpdateStatus.UpToDate

            val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                ?: throw IOException("release ${release.tagName} has no APK asset")

            UpdateStatus.Available(
                versionName = remote.toString(),
                // GitHub releases carry no version code, so the comparable
                // ordering comes from the tag. Kept in the model for a future
                // Play implementation that does have one.
                versionCode = 0L,
                releaseNotes = release.body.trim(),
                downloadUrl = apk.browserDownloadUrl,
                sizeBytes = apk.size,
                publishedAt = release.publishedAt,
            )
        }
    }

    private fun fetchLatest(): GitHubRelease? {
        val url = if (allowPreRelease) {
            "https://api.github.com/repos/$owner/$repo/releases?per_page=10"
        } else {
            "https://api.github.com/repos/$owner/$repo/releases/latest"
        }

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 404) return null // no releases published yet
            if (!response.isSuccessful) {
                throw IOException("GitHub returned ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            return if (allowPreRelease) {
                json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(GitHubRelease.serializer()), body)
                    .firstOrNull { !it.draft }
            } else {
                json.decodeFromString(GitHubRelease.serializer(), body)
                    .takeUnless { it.draft }
            }
        }
    }

    private companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}

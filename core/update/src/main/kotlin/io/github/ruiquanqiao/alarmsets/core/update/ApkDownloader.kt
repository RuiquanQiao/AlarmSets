package io.github.ruiquanqiao.alarmsets.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import io.github.ruiquanqiao.alarmsets.core.domain.UpdateStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext

/**
 * Downloads a release APK and hands it to the system package installer.
 *
 * The app never installs anything itself - it can only ask the system to, and
 * the user confirms in the platform's own dialog. Installing also requires the
 * `REQUEST_INSTALL_PACKAGES` permission and, from Android 8, the user granting
 * this app "install unknown apps".
 */
class ApkDownloader(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    private val directory: File
        get() = File(context.cacheDir, "updates").apply { mkdirs() }

    suspend fun download(
        update: UpdateStatus.Available,
        onProgress: (bytesRead: Long, total: Long) -> Unit = { _, _ -> },
    ): Result<File> = withContext(io) {
        runCatching {
            // Clear stale downloads so a failed attempt is never installed later.
            directory.listFiles()?.forEach { it.delete() }

            val target = File(directory, "alarmsets-${update.versionName}.apk")
            val request = Request.Builder().url(update.downloadUrl).build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("download failed: ${response.code}")
                val body = response.body ?: throw IOException("empty response body")
                val total = if (body.contentLength() > 0) body.contentLength() else update.sizeBytes

                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var read: Int
                        var written = 0L
                        while (input.read(buffer).also { read = it } >= 0) {
                            coroutineContext.ensureActive()
                            output.write(buffer, 0, read)
                            written += read
                            onProgress(written, total)
                        }
                    }
                }
            }

            if (target.length() <= 0L) throw IOException("downloaded file is empty")
            target
        }
    }

    /** True when the user has allowed this app to install packages. */
    fun canRequestInstall(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    fun manageUnknownSourcesIntent(): Intent =
        Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(Uri.parse("package:${context.packageName}"))

    fun installIntent(apk: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.updates",
            apk,
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}

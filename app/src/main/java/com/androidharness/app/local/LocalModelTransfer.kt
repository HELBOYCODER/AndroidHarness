package com.androidharness.app.local

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

/** A server/network failure that WorkManager may retry without discarding verified partial bytes. */
internal class RetryableModelDownloadException(message: String) : IOException(message)

internal fun localDownloadPercent(received: Long, total: Long): Int? =
    if (total <= 0) null else ((received.coerceIn(0, total).toDouble() / total) * 100).toInt().coerceIn(0, 100)

/** Byte offsets always refer to the identity representation, never transparent gzip bytes. */
internal class LocalModelTransfer(private val client: OkHttpClient = downloadClient()) {
    fun transfer(url: String, total: Long, existing: Long,
        onCall: (Call) -> Unit = {}, onFinished: (Call) -> Unit = {},
        consume: (InputStream, Long) -> Unit) {
        require(total > 0 && existing in 0 until total) { "Invalid model download size or offset." }
        var offset = existing
        while (true) {
            val call = client.newCall(Request.Builder().url(url).header("Accept-Encoding", "identity")
                .apply { if (offset > 0) header("Range", "bytes=$offset-") }.build())
            try {
                onCall(call)
                val restart = call.execute().use { response ->
                    val range = parseContentRange(response.header("Content-Range"))
                    val resumed = offset > 0 && response.code == 206 && range != null &&
                        range.first == offset && range.last == total - 1 && range.total == total
                    // Retry once without Range; a malformed full response must fail, not loop or append.
                    if (offset > 0 && (response.code == 416 || response.code == 206 && !resumed)) return@use true
                    if (response.code == 408 || response.code == 429 || response.code in 500..599) {
                        throw RetryableModelDownloadException("Model server returned HTTP ${response.code}. Try again later.")
                    }
                    check(response.code == 200 || resumed) { when (response.code) {
                        401, 403 -> "This model is private or gated. Use a public model download link."
                        404 -> "Model file not found. Check the link."
                        206 -> "Model server returned an unexpected byte range."
                        else -> "Model server returned HTTP ${response.code}."
                    } }
                    check(response.header("Content-Encoding").let { it == null || it.equals("identity", true) }) {
                        "Model server returned an encoded file. Byte-range downloads require identity encoding."
                    }
                    val body = checkNotNull(response.body) { "Empty model download." }
                    val start = if (resumed) offset else 0L
                    check(body.contentLength() < 0 || body.contentLength() == total - start) { "Unexpected model size. Paste the link again." }
                    body.byteStream().use { consume(it, start) }
                    false
                }
                if (!restart) return
                offset = 0
            } finally { onFinished(call) }
        }
    }

    companion object {
        fun downloadClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
            // ponytail: no whole-call deadline for multi-GB models — upgrade when downloads have a measured minimum throughput policy.
            .callTimeout(0, TimeUnit.SECONDS).followSslRedirects(false).build()
    }
}

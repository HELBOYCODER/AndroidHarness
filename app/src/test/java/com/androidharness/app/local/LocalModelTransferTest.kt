package com.androidharness.app.local

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class LocalModelTransferTest {
    private fun client(reply: (okhttp3.Request) -> Response): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { reply(it.request()) }.build()
    private fun reply(request: okhttp3.Request, code: Int, bytes: String, range: String? = null,
        encoding: String? = null): Response = Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
        .code(code).message("Fixture").apply {
            range?.let { header("Content-Range", it) }; encoding?.let { header("Content-Encoding", it) }
        }.body(bytes.toResponseBody()).build()
    private val url = "https://example.org/model.gguf"

    @Test fun `resume accepts only a complete matching suffix with identity encoding`() {
        var calls = 0
        LocalModelTransfer(client { request ->
            calls++
            assertEquals("identity", request.header("Accept-Encoding"))
            assertEquals("bytes=4-", request.header("Range"))
            reply(request, 206, "567890", "bytes 4-9/10")
        }).transfer(url, 10, 4) { source, offset ->
            assertEquals(4L, offset); assertEquals("567890", source.readBytes().toString(Charsets.UTF_8))
        }
        assertEquals(1, calls)
    }

    @Test fun `ignored range restarts with zero offset rather than appending a full file`() {
        LocalModelTransfer(client { reply(it, 200, "0123456789") }).transfer(url, 10, 4) { source, offset ->
            assertEquals(0L, offset); assertEquals(10, source.readBytes().size)
        }
    }

    @Test fun `invalid resumed range or 416 gets exactly one clean full request`() {
        for ((code, range) in listOf(416 to null, 206 to "bytes 3-9/10", 206 to "bytes 4-8/10",
            206 to "bytes 4-9/11", 206 to "bytes 4-9/*", 206 to "bytes 4-10/10")) {
            var calls = 0
            LocalModelTransfer(client { request ->
                calls++
                if (calls == 1) reply(request, code, "567890", range)
                else {
                    assertNull(request.header("Range")); reply(request, 200, "0123456789")
                }
            }).transfer(url, 10, 4) { source, offset -> assertEquals(0L, offset); source.readBytes() }
            assertEquals(2, calls)
        }
    }

    @Test fun `malformed partial full response fails without infinite retry or consuming bytes`() {
        var calls = 0
        var consumed = false
        assertThrows(IllegalStateException::class.java) {
            LocalModelTransfer(client { calls++; reply(it, 206, "567890", "bytes 3-9/10") })
                .transfer(url, 10, 4) { _, _ -> consumed = true }
        }
        assertEquals(2, calls); assertFalse(consumed)
    }

    @Test fun `length mismatch encoded responses and unexpected 2xx are rejected before writing`() {
        for ((code, range, body, encoding) in listOf(
            listOf(206, "bytes 4-9/10", "short", null),
            listOf(200, null, "0123456789", "gzip"),
            listOf(204, null, "", null),
            listOf(200, null, "short", null))) {
            var consumed = false
            assertThrows(IllegalStateException::class.java) {
                LocalModelTransfer(client { reply(it, code as Int, body as String, range as String?, encoding as String?) })
                    .transfer(url, 10, 4) { _, _ -> consumed = true }
            }
            assertFalse(consumed)
        }
    }

    @Test fun `transient HTTP statuses are retryable and permanent errors are not`() {
        for (code in listOf(408, 429, 500, 502, 503, 504)) {
            assertThrows(RetryableModelDownloadException::class.java) {
                LocalModelTransfer(client { reply(it, code, "") }).transfer(url, 10, 0) { _, _ -> fail() }
            }
        }
        for (code in listOf(400, 401, 403, 404, 416)) {
            assertThrows(IllegalStateException::class.java) {
                LocalModelTransfer(client { reply(it, code, "") }).transfer(url, 10, 0) { _, _ -> fail() }
            }
        }
    }

    @Test fun `active call is cleaned up on consumer failure`() {
        var registered: okhttp3.Call? = null
        var finished: okhttp3.Call? = null
        assertThrows(IOException::class.java) {
            LocalModelTransfer(client { reply(it, 200, "0123456789") }).transfer(url, 10, 0,
                onCall = { registered = it }, onFinished = { finished = it }) { _, _ -> throw IOException("disk fixture") }
        }
        assertNotNull(registered); assertSame(registered, finished)
    }

    @Test fun `download client has bounded idle waits without short whole file deadline`() {
        val client = LocalModelTransfer.downloadClient()
        assertEquals(30_000, client.connectTimeoutMillis)
        assertEquals(60_000, client.readTimeoutMillis)
        assertEquals(0, client.callTimeoutMillis)
        assertFalse(client.followSslRedirects)
    }

    @Test fun `progress handles zero negative and huge totals without arithmetic overflow`() {
        assertNull(localDownloadPercent(1, 0)); assertNull(localDownloadPercent(1, -1))
        assertEquals(0, localDownloadPercent(-1, 100)); assertEquals(100, localDownloadPercent(200, 100))
        assertEquals(100, localDownloadPercent(Long.MAX_VALUE, Long.MAX_VALUE))
        assertEquals(50, localDownloadPercent(Long.MAX_VALUE / 2, Long.MAX_VALUE))
    }

    @Test fun `cancelling registered call unblocks a stalled network body promptly`() {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            val started = CountDownLatch(1)
            val release = CountDownLatch(1)
            val call = AtomicReference<okhttp3.Call>()
            val failure = AtomicReference<Throwable>()
            val fixture = kotlin.concurrent.thread(isDaemon = true) {
                server.accept().use { socket ->
                    socket.soTimeout = 5000
                    val reader = socket.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) { /* consume request headers */ }
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Length: 10\r\nConnection: close\r\n\r\nx".toByteArray())
                        flush()
                    }
                    started.countDown()
                    release.await(5, TimeUnit.SECONDS)
                }
            }
            val transfer = kotlin.concurrent.thread(isDaemon = true) {
                try {
                    LocalModelTransfer().transfer("http://127.0.0.1:${server.localPort}/model.gguf", 10, 0,
                        onCall = { call.set(it) }) { source, _ -> source.readBytes() }
                } catch (error: Throwable) { failure.set(error) }
            }
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS))
                call.get().cancel()
                transfer.join(1500)
                assertFalse("Cancelled transfer must not wait for the read timeout", transfer.isAlive)
                assertTrue(failure.get() is IOException)
            } finally { release.countDown(); fixture.join(1000); transfer.join(1000) }
        }
    }
}

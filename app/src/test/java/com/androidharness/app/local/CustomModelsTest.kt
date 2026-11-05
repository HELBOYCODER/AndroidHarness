package com.androidharness.app.local

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class CustomModelsTest {
    @Test fun `links accept repo blob resolve tree direct and encoded names`() {
        assertEquals("org/model", CustomModelResolver.parseLink(" https://huggingface.co/org/model ").repository)
        assertEquals("folder/model q4.gguf", CustomModelResolver.parseLink("https://huggingface.co/org/model/blob/main/folder/model%20q4.gguf?download=true").file)
        assertEquals("dev", CustomModelResolver.parseLink("https://huggingface.co/org/model/tree/dev").revision)
        assertEquals("https://example.org/model.gguf?download=1", CustomModelResolver.parseLink("https://example.org/model.gguf?download=1#file").url)
        listOf("http://example.org/a.gguf", "https://user:pass@example.org/a.gguf", "https://example.org/model.safetensors",
            "https://huggingface.co/org/model/blob/main/model.safetensors", "https://example.org/model-00001-of-00002.gguf",
            "https://example.org/mmproj-model.gguf", "https://huggingface.co/models").forEach {
            assertThrows(Exception::class.java) { CustomModelResolver.parseLink(it) }
        }
    }

    @Test fun `HF file links canonicalize blob resolve host query and nested encoded filenames`() {
        val blob = CustomModelResolver.parseLink("https://www.huggingface.co/org/model/blob/main/folder/chat%20Q4.GGUF?download=true#file")
        val resolve = CustomModelResolver.parseLink("https://huggingface.co/org/model/resolve/main/folder/chat%20Q4.GGUF")
        assertEquals(resolve, blob)
        assertEquals("https://huggingface.co/org/model/resolve/main/folder/chat%20Q4.GGUF", blob.url)
        assertEquals("folder/chat Q4.GGUF", blob.file)
        val projector = CustomModelResolver.parseLink("https://huggingface.co/org/model/blob/main/mmproj%20Q4.gguf", projector = true)
        assertEquals("https://huggingface.co/org/model/resolve/main/mmproj%20Q4.gguf", projector.url)
        listOf("https://huggingface.co:8443/org/model/resolve/main/chat.gguf",
            "https://huggingface.co/org/model/tree/main/folder",
            "https://huggingface.co/org/model/resolve/main/folder%2Fchat.gguf",
            "https://huggingface.co/org/model/resolve/main/a%5Cb.gguf",
            "https://example.org/a%00.gguf",
            "https://huggingface.co/org/model/resolve/main//chat.gguf").forEach { link ->
            assertThrows(Exception::class.java) { CustomModelResolver.parseLink(link) }
        }
    }

    @Test fun `probe rejects wrong offsets contradictory totals encoding and non file success`() {
        val bytes = header()
        for ((code, range, encoding) in listOf(Triple(206, "bytes 1-${bytes.size}/2048", null),
            Triple(206, "bytes 0-${bytes.size - 1}/1", null), Triple(206, "bytes 0-${bytes.size - 1}/*", null),
            Triple(200, null, "gzip"), Triple(204, null, null))) {
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("Fixture")
                    .apply { range?.let { header("Content-Range", it) }; encoding?.let { header("Content-Encoding", it) } }
                    .body(bytes.toResponseBody()).build()
            }.build()
            assertThrows(IllegalArgumentException::class.java) { CustomModelResolver(client).resolve("https://example.org/model.gguf") }
        }
    }

    @Test fun `HF file lookup pins revision hash and nested file and excludes unsupported files`() {
        val resolver = CustomModelResolver()
        val page = CustomModelResolver.parseLink("https://huggingface.co/org/model")
        val data = repositoryJson()
        val model = resolver.parseRepository(page, Json.parseToJsonElement(data).let { it as kotlinx.serialization.json.JsonObject }).single()
        assertEquals("https://huggingface.co/org/model/resolve/${"a".repeat(40)}/folder/chat%20q4.gguf", model.url)
        assertEquals("b".repeat(64), model.sha256)
        assertEquals(2048L, model.bytes)
        assertTrue(model.custom)
        assertEquals("mit", model.license)
        assertEquals(model, resolver.parseRepository(page, Json.parseToJsonElement(data) as kotlinx.serialization.json.JsonObject).single())
        assertThrows(IllegalArgumentException::class.java) {
            resolver.parseRepository(page.copy(file = "absent.gguf"), Json.parseToJsonElement(data) as kotlinx.serialization.json.JsonObject)
        }
    }

    @Test fun `header computes grouped attention KV cache and training context`() {
        val result = GgufMetadata.inspect(header())
        assertEquals(32768L, result.kvBytesPerToken)
        assertEquals(1024, result.maxContext)
        assertThrows(IllegalArgumentException::class.java) { GgufMetadata.inspect("<html>not a model</html>".toByteArray()) }
        assertThrows(IllegalArgumentException::class.java) { GgufMetadata.inspect(header(split = true)) }
        assertEquals(256, GgufMetadata.inspect(header(context = 256)).maxContext)
        assertEquals(131072, GgufMetadata.inspect(header(context = 131072)).maxContext)
        val truncated = header().copyOf(24)
        assertEquals(256 * 1024L, GgufMetadata.inspect(truncated).kvBytesPerToken)
    }

    @Test fun `direct probe handles range total and refuses HTML or changing size`() {
        val bytes = header()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            assertEquals("bytes=0-${GgufMetadata.PROBE_BYTES - 1}", chain.request().header("Range"))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(206).message("Partial")
                .header("Content-Range", "bytes 0-${bytes.size - 1}/2048").body(bytes.toResponseBody()).build()
        }.build()
        val resolver = CustomModelResolver(client)
        val model = resolver.resolve("https://example.org/model.gguf").single()
        assertEquals(2048L, model.bytes)
        assertEquals(32768L, model.kvBytesPerToken)
        assertEquals(1024, model.defaultContext)
        assertThrows(IllegalArgumentException::class.java) { resolver.inspect(model.copy(bytes = 3000)) }
        val htmlClient = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("<html>this is a file page</html>".toResponseBody()).build()
        }.build()
        assertThrows(IllegalArgumentException::class.java) { CustomModelResolver(htmlClient).resolve("https://example.org/model.gguf") }
    }

    @Test fun `HF blob import resolves pinned asset and publishes only verified installed bytes`() = temporary { root ->
        val bytes = header()
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val sha = "a".repeat(40)
        val requests = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests += request.url.toString()
            val body = if (request.url.encodedPath.startsWith("/api/models/")) {
                """{"sha":"$sha","siblings":[{"rfilename":"folder/chat q4.gguf","size":${bytes.size},"lfs":{"sha256":"$hash"}}]}""".toByteArray()
            } else bytes
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody()).build()
        }.build()
        val resolver = CustomModelResolver(client)
        val model = resolver.inspect(resolver.resolve("https://www.huggingface.co/org/model/blob/main/folder/chat%20q4.gguf?download=true").single())
        assertEquals("org/model", model.repository)
        assertEquals(sha, model.revision)
        assertEquals(hash, model.sha256)
        assertEquals(bytes.size.toLong(), model.bytes)
        val registry = CustomModelRegistry(root)
        registry.add(model)
        val store = LocalModelStore(root) { registry.models.firstOrNull { model -> model.id == it } }
        assertFalse(store.installed(model))
        LocalModelTransfer(client).transfer(model.url, model.bytes, 0) { source, offset ->
            store.install(model, source, { false }, {}, offset)
        }
        assertTrue(store.installed(model))
        val reloaded = CustomModelRegistry(root).models.single()
        assertEquals(model, reloaded)
        assertTrue(store.installed(reloaded))
        assertArrayEquals(bytes, store.file(model.id).readBytes())
        assertTrue(requests.first().startsWith("https://huggingface.co/api/models/org/model/revision/main?"))
        assertTrue(requests.drop(1).all { it == "https://huggingface.co/org/model/resolve/$sha/folder/chat%20q4.gguf" })
    }

    @Test fun `unsupported saved formats preserve GGUF entries and source files`() = temporary { root ->
        val model = model(header())
        val encoded = Json.encodeToString(LocalModelSpec.serializer(), model)
        val removed = encoded.dropLast(1) + ",\"format\":\"RETIRED_FORMAT\"}"
        File(root, "custom-models.json").writeText("[$encoded,$removed]")
        val weights = File(root, "old-model.bin").apply { writeText("preserve") }
        val registry = CustomModelRegistry(root)
        assertNull(registry.loadError)
        assertEquals(listOf(model), registry.models)
        assertEquals("preserve", weights.readText())
    }

    @Test fun `custom registry reload installs checks checksum and deletes model plus limits`() = temporary { root ->
        val bytes = header()
        val model = model(bytes)
        val registry = CustomModelRegistry(root)
        registry.add(model)
        registry.add(model)
        assertEquals(listOf(model), CustomModelRegistry(root).models)
        val store = LocalModelStore(root) { registry.models.firstOrNull { model -> model.id == it } }
        store.install(model, bytes.inputStream(), { false }, {})
        assertTrue(store.installed(model))
        File(root, "${model.id}.json").writeText("settings")
        store.remove(model.id)
        assertFalse(store.file(model.id).exists())
        assertFalse(File(root, "${model.id}.json").exists())
        assertThrows(IllegalStateException::class.java) { store.install(model.copy(sha256 = "0".repeat(64)), bytes.inputStream(), { false }, {}) }
        assertFalse(store.partial(model.id).exists())
        store.install(model.copy(sha256 = ""), bytes.inputStream(), { false }, {})
        assertTrue(store.installed(model))
        assertThrows(IllegalArgumentException::class.java) { registry.add(model.copy(id = "../escape")) }
        assertThrows(IllegalArgumentException::class.java) { store.file("../escape") }
        registry.remove(model.id)
        assertTrue(CustomModelRegistry(root).models.isEmpty())
    }

    @Test fun `corrupt custom catalog preserves model files and reports recovery instead of crashing`() = temporary { root ->
        File(root, "custom-models.json").writeText("broken")
        val saved = File(root, "custom-${"a".repeat(32)}.gguf").apply { writeText("saved") }
        val registry = CustomModelRegistry(root)
        assertTrue(registry.models.isEmpty())
        assertNotNull(registry.loadError)
        assertTrue(saved.exists())
    }

    private fun repositoryJson() = """{"sha":"${"a".repeat(40)}","cardData":{"license":"mit"},"siblings":[
        {"rfilename":"folder/chat q4.gguf","size":2048,"lfs":{"sha256":"${"b".repeat(64)}"}},
        {"rfilename":"model.safetensors","size":100},
        {"rfilename":"mmproj-model.gguf","size":100},
        {"rfilename":"model-00001-of-00002.gguf","size":100}]}"""

    private fun model(bytes: ByteArray) = LocalModelCatalog.models.first().copy(id = "custom-${"c".repeat(32)}",
        bytes = bytes.size.toLong(), sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },
        custom = true, downloadUrl = "https://example.org/model.gguf")

    private fun header(split: Boolean = false, context: Int = 1024): ByteArray {
        val out = ByteArrayOutputStream()
        fun int(value: Int) { out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()) }
        fun long(value: Long) { out.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array()) }
        fun string(value: String) { val bytes = value.toByteArray(); long(bytes.size.toLong()); out.write(bytes) }
        out.write("GGUF".toByteArray()); int(3); long(1); long(if (split) 7 else 6)
        string("general.architecture"); int(8); string("llama")
        listOf("llama.block_count" to 16, "llama.attention.head_count" to 8, "llama.attention.head_count_kv" to 4,
            "llama.embedding_length" to 1024, "llama.context_length" to context).forEach { (key, value) -> string(key); int(4); int(value) }
        if (split) { string("split.count"); int(4); int(2) }
        return out.toByteArray()
    }
    private fun temporary(block: (File) -> Unit) {
        val root = Files.createTempDirectory("custom-model-test").toFile()
        try { block(root) } finally { root.deleteRecursively() }
    }
}

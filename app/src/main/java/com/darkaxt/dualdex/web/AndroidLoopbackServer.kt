package com.darkaxt.dualdex.web

import com.enrpau.dualscreendex.parser.catalog.MapLighting
import com.enrpau.dualscreendex.parser.catalog.MapTimeOfDay
import com.enrpau.dualscreendex.parser.sprite.PngEncoder
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

interface MapperHttpHandler {
    fun state(): Any
    fun action(type: String, values: Map<String, String?>): Any
    fun exportRaw(): ByteArray
}

data class LoopbackCapacitySnapshot(
    val workerThreads: Int,
    val activeWorkers: Int,
    val queuedConnections: Int,
    val activeConnections: Int,
)

/** Small HTTP/1.1 host for the bundled WebView. It never binds outside 127.0.0.1. */
class AndroidLoopbackServer(
    private val runtime: ProductionCompanionRuntime,
    private val requestedPort: Int = 0,
    private val requestBodySpoolFactory: () -> Path = {
        val directory = File(System.getProperty("java.io.tmpdir"), "dualdex-request-bodies")
        require(directory.isDirectory || directory.mkdirs()) { "request body directory could not be created" }
        File.createTempFile("request-", ".body", directory).toPath()
    },
    private val assetLoader: (String) -> ByteArray?,
) : AutoCloseable {
    private val gson: Gson = GsonBuilder().serializeNulls().create()
    private val acceptor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "dualdex-loopback-acceptor").apply { isDaemon = true }
    }
    private val clients = ThreadPoolExecutor(
        CLIENT_WORKERS,
        CLIENT_WORKERS,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(CLIENT_QUEUE_CAPACITY),
        { runnable -> Thread(runnable, "dualdex-loopback-client").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy(),
    )
    private val activeSockets = Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<Socket, Boolean>())
    @Volatile private var socket: ServerSocket? = null
    @Volatile private var nativeActionHandler: ((String, Map<String, String?>) -> Boolean)? = null
    @Volatile private var mapperHandler: MapperHttpHandler? = null

    val address: InetSocketAddress
        get() = socket?.localSocketAddress as? InetSocketAddress
            ?: error("loopback server has not started")

    @Synchronized
    fun start() {
        check(socket == null) { "loopback server is already started" }
        val loopback = InetAddress.getByName(LOOPBACK_HOST)
        val bound = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(loopback, requestedPort))
        }
        socket = bound
        acceptor.execute { acceptConnections(bound) }
    }

    fun ballSpritePng(id: Int): ByteArray? = runtime.ballSprite(id)?.let(PngEncoder::encode)

    fun updateDisplayMode(mode: String) {
        runtime.action("SETTINGS", mapOf("displayMode" to mode))
    }

    fun updateOverlayScale(scale: Double) {
        runtime.updateOverlayScale(scale)
    }

    fun setNativeActionHandler(handler: (String, Map<String, String?>) -> Boolean) {
        nativeActionHandler = handler
    }

    fun setMapperHandler(handler: MapperHttpHandler) {
        mapperHandler = handler
    }

    fun capacitySnapshot() = LoopbackCapacitySnapshot(
        workerThreads = clients.poolSize,
        activeWorkers = clients.activeCount,
        queuedConnections = clients.queue.size,
        activeConnections = activeSockets.size,
    )

    override fun close() {
        val bound = synchronized(this) {
            val current = socket
            socket = null
            current
        }
        bound?.close()
        activeSockets.toList().forEach(Socket::close)
        activeSockets.clear()
        acceptor.shutdown()
        clients.shutdownNow()
        runtime.close()
    }

    private fun acceptConnections(server: ServerSocket) {
        while (!server.isClosed) {
            try {
                val client = server.accept()
                activeSockets += client
                try {
                    clients.execute { handle(client) }
                } catch (_: RejectedExecutionException) {
                    rejectBusy(client)
                }
            } catch (failure: Exception) {
                if (!server.isClosed) throw failure
            }
        }
    }

    private fun rejectBusy(client: Socket) {
        client.use { connection ->
            try {
                writeResponse(
                    BufferedOutputStream(connection.getOutputStream()),
                    textResponse("loopback server is busy", 503),
                )
            } finally {
                activeSockets -= connection
            }
        }
    }

    private fun handle(client: Socket) {
        client.use { connection ->
            try {
                readRequest(BufferedInputStream(connection.getInputStream())).use { request ->
                    writeResponse(BufferedOutputStream(connection.getOutputStream()), route(request))
                }
            } catch (failure: Exception) {
                runCatching {
                    writeResponse(
                        BufferedOutputStream(connection.getOutputStream()),
                        jsonResponse(mapOf("error" to (failure.message ?: failure.javaClass.simpleName)), 400),
                    )
                }
            } finally {
                activeSockets -= connection
            }
        }
    }

    private fun route(request: Request): Response = when {
        request.method == "GET" && request.path == "/api/health" -> jsonResponse(mapOf("ok" to true))
        request.method == "GET" && request.path == "/api/bootstrap" -> jsonResponse(runtime.bootstrap())
        request.method == "GET" && request.path == "/api/state" -> stateResponse(request)
        request.method == "POST" && request.path == "/api/actions" -> handleAction(request)
        request.method == "GET" && request.path == "/api/mapper/state" -> jsonResponse(requireNotNull(mapperHandler) { "mapper is unavailable" }.state())
        request.method == "POST" && request.path == "/api/mapper/actions" -> handleMapperAction(request)
        request.method == "POST" && request.path == "/api/mapper/export" -> handleMapperExport(request)
        request.method == "POST" && request.path == "/api/load" -> handleLoad(request)
        request.method == "GET" && request.path == "/api/diagnostics" -> jsonResponse(
            runtime.diagnostics(request.query["speciesId"]?.toIntOrNull(), request.query["moveId"]?.toIntOrNull()),
        )
        request.method == "GET" && request.path.startsWith("/api/sprites/species/") -> spriteResponse(request.path, true)
        request.method == "GET" && request.path.startsWith("/api/sprites/balls/") -> spriteResponse(request.path, false)
        request.method == "GET" && request.path.startsWith("/api/maps/") -> mapResponse(request)
        request.method == "GET" && request.path.startsWith("/api/trainer-assets/") -> trainerAssetResponse(request.path)
        request.path.startsWith("/api/") -> if (request.method in setOf("GET", "POST")) {
            textResponse("not found", 404)
        } else {
            textResponse("method not allowed", 405)
        }
        request.method == "GET" -> staticResponse(request.path)
        else -> textResponse("method not allowed", 405)
    }

    private fun handleAction(request: Request): Response {
        val (type, values) = parseAction(request)
        if (nativeActionHandler?.invoke(type, values) == true) return jsonResponse(runtime.stateView())
        return jsonResponse(runtime.action(type, values))
    }

    private fun stateResponse(request: Request): Response {
        val view = runtime.stateView()
        val sinceVersion = request.query["sinceVersion"]?.toLongOrNull()
        require(sinceVersion == null || sinceVersion >= 0) { "state version is invalid" }
        return if (sinceVersion != null && view.version <= sinceVersion) {
            emptyResponse(204)
        } else {
            jsonResponse(view)
        }
    }

    private fun handleMapperAction(request: Request): Response {
        val (type, values) = parseAction(request)
        return jsonResponse(requireNotNull(mapperHandler) { "mapper is unavailable" }.action(type, values))
    }

    private fun handleMapperExport(request: Request): Response {
        val bytes = requireNotNull(mapperHandler) { "mapper is unavailable" }.exportRaw()
        return Response(
            200, "application/json; charset=utf-8", bytes,
            mapOf("Cache-Control" to "no-store", "Content-Disposition" to "attachment; filename=dualdex-memory-session.json"),
        )
    }

    private fun parseAction(request: Request): Pair<String, Map<String, String?>> {
        val payload = request.body.open().bufferedReader(Charsets.UTF_8).use { reader ->
            gson.fromJson(reader, JsonObject::class.java)
        }
        val type = payload.get("type")?.asString ?: error("action type is required")
        val values = payload.entrySet().filter { it.key != "type" }.associate { entry ->
            entry.key to if (entry.value.isJsonNull) null else entry.value.asString
        }
        return type to values
    }

    private fun handleLoad(request: Request): Response {
        val name = request.query["name"] ?: error("upload name is required")
        val path = requireNotNull(request.body.path) { "upload body is required" }
        return jsonResponse(runtime.load(com.enrpau.dualscreendex.parser.io.RomSourceLoader.load(name, path)))
    }

    private fun spriteResponse(path: String, species: Boolean): Response {
        val id = path.substringAfterLast('/').substringBefore('.').toInt()
        val sprite = (if (species) runtime.speciesSprite(id) else runtime.ballSprite(id))
            ?: return textResponse("sprite not available", 404)
        val bytes = PngEncoder.encode(sprite)
        val etag = runtime.catalogHash()?.let { "\"$it-$id\"" }
        return Response(
            200,
            "image/png",
            bytes,
            buildMap {
                put("Cache-Control", "public, max-age=31536000, immutable")
                if (etag != null) put("ETag", etag)
            },
        )
    }

    private fun mapResponse(request: Request): Response {
        val encoded = request.path.removePrefix("/api/maps/").removeSuffix(".png")
        if (encoded.isBlank() || encoded.contains('/') || encoded.contains("..")) {
            return textResponse("map not available", 404)
        }
        val key = runCatching { URLDecoder.decode(encoded, Charsets.UTF_8.name()) }.getOrNull()
            ?: return textResponse("map not available", 404)
        if (key.split('/').any { it == ".." }) return textResponse("map not available", 404)
        val requestedLighting = requestedLighting(request.query["lighting"])
        val time = requestedTime(request.query["hour"], request.query["minute"])
        val rendered = runCatching { runtime.mapAsset(key, requestedLighting, time) }.getOrNull()
            ?: return textResponse("map not available", 404)
        val variant = rendered.cacheVariant
        return Response(
            200,
            "image/png",
            rendered.bytes,
            buildMap {
                put("Cache-Control", "public, max-age=31536000, immutable")
                runtime.catalogHash()?.let { put("ETag", "\"$it-map-${key.hashCode()}-$variant\"") }
            },
        )
    }

    private fun requestedLighting(value: String?): MapLighting = if (value == null) {
        MapLighting.DAY
    } else {
        requireNotNull(MapLighting.entries.singleOrNull { it.name == value.uppercase(Locale.ROOT) }) {
            "unsupported map lighting: $value"
        }
    }

    private fun requestedTime(hour: String?, minute: String?): MapTimeOfDay? {
        if (hour == null && minute == null) return null
        require(hour != null && minute != null) { "map time requires hour and minute" }
        return MapTimeOfDay(
            requireNotNull(hour.toIntOrNull()) { "unsupported map hour: $hour" },
            requireNotNull(minute.toIntOrNull()) { "unsupported map minute: $minute" },
        )
    }

    private fun trainerAssetResponse(path: String): Response {
        val encoded = path.removePrefix("/api/trainer-assets/").removeSuffix(".png")
        if (encoded.isBlank() || encoded.contains('/') || encoded.contains("..")) {
            return textResponse("trainer asset not available", 404)
        }
        val key = runCatching { URLDecoder.decode(encoded, Charsets.UTF_8.name()) }.getOrNull()
            ?: return textResponse("trainer asset not available", 404)
        if (key.split('/').any { it == ".." }) return textResponse("trainer asset not available", 404)
        val sprite = runtime.trainerAsset(key) ?: return textResponse("trainer asset not available", 404)
        return Response(
            200,
            "image/png",
            PngEncoder.encode(sprite),
            buildMap {
                put("Cache-Control", "public, max-age=31536000, immutable")
                runtime.catalogHash()?.let { put("ETag", "\"$it-trainer-${key.hashCode()}\"") }
            },
        )
    }

    private fun staticResponse(rawPath: String): Response {
        val requested = rawPath.removePrefix("/").ifBlank { "index.html" }
        require(requested.split('/').none { it == ".." }) { "invalid asset path" }
        val resolved = assetLoader(requested) ?: assetLoader("index.html")
            ?: return textResponse("web bundle not available", 404)
        return Response(200, contentType(requested), resolved)
    }

    private fun jsonResponse(value: Any, status: Int = 200): Response = Response(
        status = status,
        contentType = "application/json; charset=utf-8",
        contentLength = null,
        headers = mapOf("Cache-Control" to "no-store"),
    ) { output ->
        OutputStreamWriter(output, Charsets.UTF_8).also { writer ->
            gson.toJson(value, writer)
            writer.flush()
        }
    }

    private fun textResponse(value: String, status: Int): Response =
        Response(status, "text/plain; charset=utf-8", value.toByteArray(Charsets.UTF_8))

    private fun readRequest(input: BufferedInputStream): Request {
        val requestLine = readLine(input) ?: throw EOFException("empty request")
        val parts = requestLine.split(' ')
        require(parts.size == 3 && parts[2].startsWith("HTTP/1.")) { "invalid HTTP request line" }
        val headers = linkedMapOf<String, String>()
        while (true) {
            val line = readLine(input) ?: throw EOFException("incomplete HTTP headers")
            if (line.isEmpty()) break
            val separator = line.indexOf(':')
            require(separator > 0) { "invalid HTTP header" }
            headers[line.substring(0, separator).trim().lowercase(Locale.ROOT)] = line.substring(separator + 1).trim()
        }
        val target = parts[1]
        val rawPath = target.substringBefore('?')
        val query = parseQuery(target.substringAfter('?', ""))
        val bodyLimit = requestBodyLimit(rawPath, query)
        val body = when {
            headers["transfer-encoding"]?.contains("chunked", ignoreCase = true) == true ->
                readChunked(input, bodyLimit)
            headers["content-length"] != null ->
                readFixed(input, headers.getValue("content-length").toLong(), bodyLimit)
            else -> EmptyRequestBody
        }
        return Request(parts[0].uppercase(Locale.ROOT), rawPath, query, body)
    }

    private fun readFixed(input: InputStream, length: Long, maximumBytes: Long): RequestBody {
        require(length in 0..maximumBytes) { "request body is too large" }
        return spoolBody { output ->
            copyExactly(input, output, length, ByteArray(STREAM_COPY_BUFFER_BYTES))
            length
        }
    }

    private fun readChunked(input: BufferedInputStream, maximumBytes: Long): RequestBody = spoolBody { output ->
        var total = 0L
        val buffer = ByteArray(STREAM_COPY_BUFFER_BYTES)
        var complete = false
        while (!complete) {
            val sizeLine = readLine(input) ?: throw EOFException("missing chunk size")
            val size = sizeLine.substringBefore(';').trim().toLong(16)
            require(size >= 0) { "invalid chunk size" }
            if (size == 0L) {
                while (true) {
                    if (readLine(input).isNullOrEmpty()) break
                }
                complete = true
            } else {
                require(total + size <= maximumBytes) { "request body is too large" }
                copyExactly(input, output, size, buffer)
                total += size
                require(readLine(input).orEmpty().isEmpty()) { "invalid chunk terminator" }
            }
        }
        total
    }

    private fun spoolBody(writer: (OutputStream) -> Long): RequestBody {
        val path = requestBodySpoolFactory()
        return try {
            val length = Files.newOutputStream(
                path,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING,
            ).buffered().use(writer)
            SpoolRequestBody(path, length)
        } catch (failure: Exception) {
            Files.deleteIfExists(path)
            throw failure
        }
    }

    private fun copyExactly(input: InputStream, output: OutputStream, length: Long, buffer: ByteArray) {
        var remaining = length
        while (remaining > 0) {
            val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (count < 0) throw EOFException("request body ended early")
            if (count == 0) continue
            output.write(buffer, 0, count)
            remaining -= count
        }
    }

    private fun requestBodyLimit(path: String, query: Map<String, String>): Long {
        if (path != "/api/load") return MAX_CONTROL_BODY_BYTES
        return when (query["name"]?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)) {
            "gb", "gbc", "gba" -> MAX_EXTRACTED_ROM_BYTES
            else -> MAX_COMPRESSED_ROM_SOURCE_BYTES
        }
    }

    private fun readLine(input: InputStream): String? {
        val output = ByteArrayOutputStream()
        var previous = -1
        while (true) {
            val current = input.read()
            if (current < 0) return if (output.size() == 0) null else throw EOFException("incomplete HTTP line")
            if (previous == '\r'.code && current == '\n'.code) {
                val bytes = output.toByteArray()
                return bytes.copyOf(bytes.size - 1).toString(Charsets.ISO_8859_1)
            }
            output.write(current)
            require(output.size() <= MAX_LINE_BYTES) { "HTTP line is too long" }
            previous = current
        }
    }

    private fun writeResponse(output: BufferedOutputStream, response: Response) {
        val reason = when (response.status) {
            200 -> "OK"
            204 -> "No Content"
            400 -> "Bad Request"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            503 -> "Service Unavailable"
            else -> "Error"
        }
        output.write("HTTP/1.1 ${response.status} $reason\r\n".toByteArray(Charsets.US_ASCII))
        response.contentType?.let { contentType ->
            output.write("Content-Type: $contentType\r\n".toByteArray(Charsets.US_ASCII))
        }
        response.contentLength?.let { length ->
            output.write("Content-Length: $length\r\n".toByteArray(Charsets.US_ASCII))
        }
        output.write("Connection: close\r\n".toByteArray(Charsets.US_ASCII))
        response.headers.forEach { (name, value) ->
            output.write("$name: $value\r\n".toByteArray(Charsets.US_ASCII))
        }
        output.write("\r\n".toByteArray(Charsets.US_ASCII))
        response.writeBody(output)
        output.flush()
    }

    private fun parseQuery(raw: String): Map<String, String> = raw.split('&').filter(String::isNotBlank).associate { part ->
        val pieces = part.split('=', limit = 2)
        decode(pieces[0]) to decode(pieces.getOrElse(1) { "" })
    }

    private fun decode(value: String): String = URLDecoder.decode(value, Charsets.UTF_8.name())

    private fun contentType(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
        "html" -> "text/html; charset=utf-8"
        "js" -> "text/javascript; charset=utf-8"
        "css" -> "text/css; charset=utf-8"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        else -> "application/octet-stream"
    }

    private data class Request(
        val method: String,
        val path: String,
        val query: Map<String, String>,
        val body: RequestBody,
    ) : AutoCloseable {
        override fun close() = body.close()
    }

    private interface RequestBody : AutoCloseable {
        val path: Path?
        fun open(): InputStream
    }

    private data object EmptyRequestBody : RequestBody {
        override val path: Path? = null
        override fun open(): InputStream = byteArrayOf().inputStream()
        override fun close() = Unit
    }

    private class SpoolRequestBody(
        override val path: Path,
        val length: Long,
    ) : RequestBody {
        override fun open(): InputStream = Files.newInputStream(path)
        override fun close() {
            Files.deleteIfExists(path)
        }
    }

    private class Response(
        val status: Int,
        val contentType: String?,
        val contentLength: Long?,
        val headers: Map<String, String> = emptyMap(),
        val writeBody: (OutputStream) -> Unit,
    ) {
        constructor(
            status: Int,
            contentType: String,
            body: ByteArray,
            headers: Map<String, String> = emptyMap(),
        ) : this(status, contentType, body.size.toLong(), headers, { output -> output.write(body) })
    }

    private fun emptyResponse(status: Int): Response = Response(
        status = status,
        contentType = null,
        contentLength = 0,
        writeBody = {},
    )

    companion object {
        const val LOOPBACK_HOST = "127.0.0.1"
        private const val MAX_LINE_BYTES = 64 * 1024
        private const val STREAM_COPY_BUFFER_BYTES = 64 * 1024
        private const val MAX_CONTROL_BODY_BYTES = 1024L * 1024
        private const val MAX_COMPRESSED_ROM_SOURCE_BYTES = 64L * 1024 * 1024
        private const val MAX_EXTRACTED_ROM_BYTES = 32L * 1024 * 1024
        private const val CLIENT_WORKERS = 4
        private const val CLIENT_QUEUE_CAPACITY = 4
    }
}

package com.darkaxt.dualdex.web

import com.enrpau.dualscreendex.companion.api.ApiErrorDetailView
import com.enrpau.dualscreendex.companion.api.ApiErrorView
import com.enrpau.dualscreendex.parser.catalog.MapLighting
import com.enrpau.dualscreendex.parser.catalog.MapTimeOfDay
import com.enrpau.dualscreendex.parser.io.LoadedRom
import com.enrpau.dualscreendex.parser.io.RomSourceLoader
import com.enrpau.dualscreendex.parser.sprite.PngEncoder
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

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

data class StateResponseMetrics(
    val requests: Long,
    val noContentResponses: Long,
    val bodyResponses: Long,
    val bodyBytes: Long,
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
    private val romSourceLoader: (String, Path) -> LoadedRom = { name, path -> RomSourceLoader.load(name, path) },
    private val requestReadTimeoutMillis: Int = DEFAULT_REQUEST_READ_TIMEOUT_MILLIS,
    private val requestLifetimeMillis: Long = DEFAULT_REQUEST_LIFETIME_MILLIS,
    private val responseWriteTimeoutMillis: Long = DEFAULT_RESPONSE_WRITE_TIMEOUT_MILLIS,
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
    private val clientDeadlines = ScheduledThreadPoolExecutor(1) { runnable ->
        Thread(runnable, "dualdex-loopback-deadline").apply { isDaemon = true }
    }.apply {
        removeOnCancelPolicy = true
        executeExistingDelayedTasksAfterShutdownPolicy = false
    }
    private val activeSockets = Collections.newSetFromMap(ConcurrentHashMap<Socket, Boolean>())
    private val activeSocketDeadlines = ConcurrentHashMap<Socket, ScheduledFuture<*>>()
    @Volatile private var socket: ServerSocket? = null
    @Volatile private var nativeActionHandler: ((String, Map<String, String?>) -> Boolean)? = null
    @Volatile private var mapperHandler: MapperHttpHandler? = null
    private val stateRequests = AtomicLong()
    private val stateNoContentResponses = AtomicLong()
    private val stateBodyResponses = AtomicLong()
    private val stateBodyBytes = AtomicLong()

    init {
        require(requestReadTimeoutMillis > 0)
        require(requestLifetimeMillis > 0)
        require(responseWriteTimeoutMillis > 0)
    }

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

    fun exportCompatibilityReport(): ByteArray = runtime.exportCompatibilityReport()

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

    fun stateResponseMetrics() = StateResponseMetrics(
        requests = stateRequests.get(),
        noContentResponses = stateNoContentResponses.get(),
        bodyResponses = stateBodyResponses.get(),
        bodyBytes = stateBodyBytes.get(),
    )

    fun performanceCounters(): Map<String, Long> = stateResponseMetrics().let { metrics ->
        mapOf(
            "state.responses.requests" to metrics.requests,
            "state.responses.noContent" to metrics.noContentResponses,
            "state.responses.withBody" to metrics.bodyResponses,
            "state.responses.bodyBytes" to metrics.bodyBytes,
        )
    }

    override fun close() {
        val (bound, clientsToClose) = synchronized(this) {
            val current = socket
            socket = null
            current to activeSockets.toList()
        }
        bound?.close()
        clientsToClose.forEach { connection -> runCatching { connection.close() } }
        activeSocketDeadlines.values.forEach { deadline -> deadline.cancel(false) }
        activeSocketDeadlines.clear()
        activeSockets.clear()
        acceptor.shutdownNow()
        clients.shutdownNow()
        clientDeadlines.shutdownNow()
        runtime.close()
    }

    private fun acceptConnections(server: ServerSocket) {
        while (!server.isClosed) {
            try {
                val client = server.accept()
                if (!registerClient(server, client)) {
                    client.close()
                    continue
                }
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

    private fun registerClient(server: ServerSocket, client: Socket): Boolean = synchronized(this) {
        if (socket !== server) return@synchronized false
        client.soTimeout = requestReadTimeoutMillis
        activeSockets += client
        scheduleDeadline(client, requestLifetimeMillis)
        true
    }

    private fun scheduleDeadline(client: Socket, timeoutMillis: Long) {
        activeSocketDeadlines.remove(client)?.cancel(false)
        val deadline = clientDeadlines.schedule(
            { runCatching { client.close() } },
            timeoutMillis,
            TimeUnit.MILLISECONDS,
        )
        activeSocketDeadlines[client] = deadline
    }

    private fun cancelDeadline(client: Socket) {
        activeSocketDeadlines.remove(client)?.cancel(false)
    }

    private fun releaseClient(client: Socket) {
        cancelDeadline(client)
        activeSockets -= client
    }

    private fun rejectBusy(client: Socket) {
        client.use { connection ->
            try {
                scheduleDeadline(connection, responseWriteTimeoutMillis)
                writeResponse(
                    BufferedOutputStream(connection.getOutputStream()),
                    apiErrorResponse(
                        status = 503,
                        code = "SERVER_BUSY",
                        message = "The server is busy. Try again.",
                        retryable = true,
                    ),
                )
            } catch (_: IOException) {
                // The deadline or peer closed the socket while the bounded response was being written.
            } finally {
                releaseClient(connection)
            }
        }
    }

    private fun handle(client: Socket) {
        client.use { connection ->
            try {
                val response = try {
                    readRequest(BufferedInputStream(connection.getInputStream())).use { request ->
                        cancelDeadline(connection)
                        route(request)
                    }
                } catch (failure: Exception) {
                    failureResponse(failure)
                }
                scheduleDeadline(connection, responseWriteTimeoutMillis)
                writeResponse(BufferedOutputStream(connection.getOutputStream()), response)
            } catch (_: IOException) {
                // The deadline, server close, or peer disconnect ended this bounded connection.
            } finally {
                releaseClient(connection)
            }
        }
    }

    private fun route(request: Request): Response = when {
        request.method == "GET" && request.path == "/api/health" -> jsonResponse(mapOf("ok" to true))
        request.method == "GET" && request.path == "/api/bootstrap" -> jsonResponse(runtime.bootstrap())
        request.method == "GET" && request.path == "/api/state" -> stateResponse(request)
        request.method == "GET" && request.path == "/api/specimens" -> jsonResponse(
            runtime.specimens(requireNotNull(request.query["speciesId"]?.toIntOrNull()) { "speciesId is required" }),
        )
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
        request.path == "/api" || request.path.startsWith("/api/") -> if (isApiEndpoint(request.path)) {
            apiErrorResponse(
                status = 405,
                code = "METHOD_NOT_ALLOWED",
                message = "The request method is not allowed.",
                retryable = false,
            )
        } else {
            apiNotFoundResponse()
        }
        request.method == "GET" -> staticResponse(request.path, request.headers["accept"])
        else -> textResponse("method not allowed", 405)
    }

    private fun isApiEndpoint(path: String): Boolean = path in API_ENDPOINTS || API_PREFIXES.any(path::startsWith)

    private fun handleAction(request: Request): Response {
        val (type, values) = parseAction(request)
        if (nativeActionHandler?.invoke(type, values) == true) return jsonResponse(runtime.stateView())
        return jsonResponse(runtime.action(type, values))
    }

    private fun stateResponse(request: Request): Response {
        stateRequests.incrementAndGet()
        val view = runtime.stateView()
        val rawSinceVersion = request.query["sinceVersion"]
        val sinceVersion = rawSinceVersion?.let { value ->
            requireNotNull(value.toLongOrNull()) { "state version is invalid" }
        }
        require(sinceVersion == null || sinceVersion >= 0) { "state version is invalid" }
        return if (sinceVersion != null && view.version == sinceVersion) {
            stateNoContentResponses.incrementAndGet()
            emptyResponse(204)
        } else {
            jsonResponse(view) { bytes ->
                stateBodyResponses.incrementAndGet()
                stateBodyBytes.addAndGet(bytes)
            }
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
            requireNotNull(gson.fromJson(reader, JsonObject::class.java)) { "action payload is required" }
        }
        val type = requireNotNull(payload.get("type")?.asString) { "action type is required" }
        val values = payload.entrySet().filter { it.key != "type" }.associate { entry ->
            entry.key to if (entry.value.isJsonNull) null else entry.value.asString
        }
        return type to values
    }

    private fun handleLoad(request: Request): Response {
        val name = requireNotNull(request.query["name"]) { "upload name is required" }
        val path = requireNotNull(request.body.path) { "upload body is required" }
        val source = try {
            romSourceLoader(name, path)
        } catch (failure: OutOfMemoryError) {
            throw recordSourceFailure(failure)
        } catch (failure: Exception) {
            throw recordSourceFailure(failure)
        }
        return jsonResponse(runtime.load(source))
    }

    private fun recordSourceFailure(failure: Throwable): GuideLoadFailure {
        runCatching { runtime.recordRomSourceLoadFailure("", failure) }
        return GuideLoadFailure.from(failure)
    }

    private fun spriteResponse(path: String, species: Boolean): Response {
        if (!path.endsWith(".png")) return apiNotFoundResponse()
        val id = path.substringAfterLast('/').removeSuffix(".png").toIntOrNull()
            ?: return apiNotFoundResponse()
        val sprite = (if (species) runtime.speciesSprite(id) else runtime.ballSprite(id))
            ?: return apiNotFoundResponse()
        val bytes = PngEncoder.encode(sprite)
        val etag = runtime.catalogHash()?.let { "\"$it-$id\"" }
        return Response(
            200,
            "image/png",
            bytes,
            buildMap {
                put("Cache-Control", "no-cache")
                if (etag != null) put("ETag", etag)
            },
        )
    }

    private fun mapResponse(request: Request): Response {
        if (!request.path.endsWith(".png")) return apiNotFoundResponse()
        val encoded = request.path.removePrefix("/api/maps/").removeSuffix(".png")
        if (encoded.isBlank() || encoded.contains('/') || encoded.contains("..")) {
            return apiNotFoundResponse()
        }
        val key = runCatching { URLDecoder.decode(encoded, Charsets.UTF_8.name()) }.getOrNull()
            ?: return apiNotFoundResponse()
        if (key.split('/').any { it == ".." }) return apiNotFoundResponse()
        val requestedLighting = requestedLighting(request.query["lighting"])
        val time = requestedTime(request.query["hour"], request.query["minute"])
        val rendered = runCatching { runtime.mapAsset(key, requestedLighting, time) }.getOrNull()
            ?: return apiNotFoundResponse()
        val variant = rendered.cacheVariant
        return Response(
            200,
            "image/png",
            rendered.bytes,
            buildMap {
                put("Cache-Control", "no-cache")
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
        if (!path.endsWith(".png")) return apiNotFoundResponse()
        val encoded = path.removePrefix("/api/trainer-assets/").removeSuffix(".png")
        if (encoded.isBlank() || encoded.contains('/') || encoded.contains("..")) {
            return apiNotFoundResponse()
        }
        val key = runCatching { URLDecoder.decode(encoded, Charsets.UTF_8.name()) }.getOrNull()
            ?: return apiNotFoundResponse()
        if (key.split('/').any { it == ".." }) return apiNotFoundResponse()
        val sprite = runtime.trainerAsset(key) ?: return apiNotFoundResponse()
        return Response(
            200,
            "image/png",
            PngEncoder.encode(sprite),
            buildMap {
                put("Cache-Control", "no-cache")
                runtime.catalogHash()?.let { put("ETag", "\"$it-trainer-${key.hashCode()}\"") }
            },
        )
    }

    private fun staticResponse(rawPath: String, accept: String?): Response {
        val requested = rawPath.removePrefix("/").ifBlank { "index.html" }
        require(requested.split('/').none { it == ".." }) { "invalid asset path" }
        val requestedBytes = assetLoader(requested)
        if (requestedBytes != null) {
            return Response(
                200,
                contentType(requested),
                requestedBytes,
                headers = mapOf("Cache-Control" to "no-cache"),
            )
        }
        if ('%' in rawPath || !isHtmlNavigation(requested, accept)) {
            return textResponse("asset not found", 404)
        }
        val index = assetLoader("index.html") ?: return textResponse("web bundle not available", 404)
        return Response(
            200,
            contentType("index.html"),
            index,
            headers = mapOf("Cache-Control" to "no-cache"),
        )
    }

    private fun isHtmlNavigation(path: String, accept: String?): Boolean =
        !path.substringAfterLast('/').contains('.') && accept.orEmpty().split(',').any { mediaRange ->
            mediaRange.substringBefore(';').trim().equals("text/html", ignoreCase = true)
        }

    private fun failureResponse(failure: Exception): Response = when (failure) {
        is SocketTimeoutException -> apiErrorResponse(
            status = 400,
            code = "REQUEST_TIMEOUT",
            message = "The request timed out.",
            retryable = true,
        )
        is GuideLoadFailure -> apiErrorResponse(
            status = 400,
            code = "GUIDE_LOAD_FAILED",
            message = requireNotNull(failure.message),
            retryable = true,
        )
        is EOFException, is IllegalArgumentException, is JsonParseException, is UnsupportedOperationException -> apiErrorResponse(
            status = 400,
            code = "INVALID_REQUEST",
            message = "The request was invalid.",
            retryable = false,
        )
        else -> apiErrorResponse(
            status = 500,
            code = "INTERNAL_ERROR",
            message = "The server could not complete the request.",
            retryable = true,
        )
    }

    private fun apiNotFoundResponse(): Response = apiErrorResponse(
        status = 404,
        code = "NOT_FOUND",
        message = "The requested resource was not found.",
        retryable = false,
    )

    private fun apiErrorResponse(
        status: Int,
        code: String,
        message: String,
        retryable: Boolean,
    ): Response = jsonResponse(
        ApiErrorView(ApiErrorDetailView(code, message, retryable)),
        status,
    )

    private fun jsonResponse(
        value: Any,
        status: Int = 200,
        onBodyWritten: (Long) -> Unit = {},
    ): Response = Response(
        status = status,
        contentType = "application/json; charset=utf-8",
        contentLength = null,
        headers = mapOf("Cache-Control" to "no-store"),
    ) { output ->
        val counting = CountingOutputStream(output)
        OutputStreamWriter(counting, Charsets.UTF_8).also { writer ->
            gson.toJson(value, writer)
            writer.flush()
        }
        onBodyWritten(counting.bytesWritten)
    }

    private fun textResponse(value: String, status: Int): Response = Response(
        status,
        "text/plain; charset=utf-8",
        value.toByteArray(Charsets.UTF_8),
        headers = mapOf("Cache-Control" to "no-cache"),
    )

    private fun readRequest(input: BufferedInputStream): Request {
        val requestLine = readLine(input) ?: throw EOFException("empty request")
        val headerBudget = HeaderBudget().apply { record(requestLine, isHeader = false) }
        val parts = requestLine.split(' ')
        require(parts.size == 3 && parts[2].startsWith("HTTP/1.")) { "invalid HTTP request line" }
        val headers = linkedMapOf<String, String>()
        while (true) {
            val line = readLine(input) ?: throw EOFException("incomplete HTTP headers")
            headerBudget.record(line, isHeader = line.isNotEmpty())
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
                readChunked(input, bodyLimit, headerBudget)
            headers["content-length"] != null ->
                readFixed(input, headers.getValue("content-length").toLong(), bodyLimit)
            else -> EmptyRequestBody
        }
        return Request(parts[0].uppercase(Locale.ROOT), rawPath, query, headers, body)
    }

    private fun readFixed(input: InputStream, length: Long, maximumBytes: Long): RequestBody {
        require(length in 0..maximumBytes) { "request body is too large" }
        return spoolBody { output ->
            copyExactly(input, output, length, ByteArray(STREAM_COPY_BUFFER_BYTES))
            length
        }
    }

    private fun readChunked(
        input: BufferedInputStream,
        maximumBytes: Long,
        headerBudget: HeaderBudget,
    ): RequestBody = spoolBody { output ->
        var total = 0L
        val buffer = ByteArray(STREAM_COPY_BUFFER_BYTES)
        var complete = false
        while (!complete) {
            val sizeLine = readLine(input) ?: throw EOFException("missing chunk size")
            val size = sizeLine.substringBefore(';').trim().toLong(16)
            require(size >= 0) { "invalid chunk size" }
            if (size == 0L) {
                while (true) {
                    val trailer = readLine(input) ?: throw EOFException("incomplete chunk trailers")
                    headerBudget.record(trailer, isHeader = trailer.isNotEmpty())
                    if (trailer.isEmpty()) break
                    require(trailer.indexOf(':') > 0) { "invalid chunk trailer" }
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
            500 -> "Internal Server Error"
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

    private class HeaderBudget {
        private var bytes = 0L
        private var count = 0

        fun record(line: String, isHeader: Boolean) {
            bytes += (line.length + HTTP_LINE_TERMINATOR_BYTES).toLong()
            require(bytes <= MAX_REQUEST_HEADER_BYTES) { "HTTP headers are too large" }
            if (isHeader) {
                count += 1
                require(count <= MAX_REQUEST_HEADER_COUNT) { "too many HTTP headers" }
            }
        }
    }

    private data class Request(
        val method: String,
        val path: String,
        val query: Map<String, String>,
        val headers: Map<String, String>,
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

    private class CountingOutputStream(
        private val delegate: OutputStream,
    ) : OutputStream() {
        var bytesWritten: Long = 0L
            private set

        override fun write(value: Int) {
            delegate.write(value)
            bytesWritten += 1
        }

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            delegate.write(bytes, offset, length)
            bytesWritten += length
        }

        override fun flush() = delegate.flush()
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
        private const val MAX_REQUEST_HEADER_BYTES = 64L * 1024
        private const val MAX_REQUEST_HEADER_COUNT = 100
        private const val HTTP_LINE_TERMINATOR_BYTES = 2
        private const val STREAM_COPY_BUFFER_BYTES = 64 * 1024
        private const val MAX_CONTROL_BODY_BYTES = 1024L * 1024
        private const val MAX_COMPRESSED_ROM_SOURCE_BYTES = 64L * 1024 * 1024
        private const val MAX_EXTRACTED_ROM_BYTES = 32L * 1024 * 1024
        private const val DEFAULT_REQUEST_READ_TIMEOUT_MILLIS = 5_000
        private const val DEFAULT_REQUEST_LIFETIME_MILLIS = 30_000L
        private const val DEFAULT_RESPONSE_WRITE_TIMEOUT_MILLIS = 15_000L
        private const val CLIENT_WORKERS = 4
        private const val CLIENT_QUEUE_CAPACITY = 4
        private val API_ENDPOINTS = setOf(
            "/api/health",
            "/api/bootstrap",
            "/api/state",
            "/api/specimens",
            "/api/actions",
            "/api/mapper/state",
            "/api/mapper/actions",
            "/api/mapper/export",
            "/api/load",
            "/api/diagnostics",
        )
        private val API_PREFIXES = listOf(
            "/api/sprites/species/",
            "/api/sprites/balls/",
            "/api/maps/",
            "/api/trainer-assets/",
        )
    }
}

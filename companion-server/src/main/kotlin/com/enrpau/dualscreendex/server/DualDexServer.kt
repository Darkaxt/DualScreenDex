package com.enrpau.dualscreendex.server

import com.enrpau.dualscreendex.companion.api.ApiErrorDetailView
import com.enrpau.dualscreendex.companion.api.ApiErrorView
import com.enrpau.dualscreendex.parser.catalog.MapLighting
import com.enrpau.dualscreendex.parser.catalog.MapTimeOfDay
import com.enrpau.dualscreendex.parser.sprite.PngEncoder
import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.Semaphore
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class DualDexServer(
    private val runtime: DualDexRuntime,
    private val webRoot: Path,
    port: Int = 47831,
    private val requestReadTimeoutMillis: Long = REQUEST_READ_TIMEOUT_MILLIS,
    private val requestLifetimeMillis: Long = REQUEST_LIFETIME_MILLIS,
    private val apiCapacity: Int = API_CAPACITY,
) : AutoCloseable {
    private val gson = GsonBuilder().serializeNulls().create()
    private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0)
    private val requestExecutor = ThreadPoolExecutor(
        HTTP_WORKERS,
        HTTP_WORKERS,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(HTTP_QUEUE_CAPACITY),
        { runnable -> Thread(runnable, "dualdex-http").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy(),
    )
    private val saturatedAdmission = ThreadLocal<Boolean>()
    private val admissionExecutor = Executor { command ->
        try {
            requestExecutor.execute(command)
        } catch (_: RejectedExecutionException) {
            saturatedAdmission.set(true)
            try {
                command.run()
            } finally {
                saturatedAdmission.remove()
            }
        }
    }
    private val apiPermits = Semaphore(apiCapacity)
    private val deadlineExecutor = ScheduledThreadPoolExecutor(1) { runnable ->
        Thread(runnable, "dualdex-http-deadline").apply { isDaemon = true }
    }.apply {
        removeOnCancelPolicy = true
    }
    private val eventWriteDeadline = WriteDeadline(deadlineExecutor, EVENT_WRITE_TIMEOUT_MILLIS)
    private val eventClients = ConcurrentHashMap.newKeySet<EventClient>()
    val address: InetSocketAddress get() = server.address

    init {
        require(requestReadTimeoutMillis > 0)
        require(requestLifetimeMillis > 0)
        require(apiCapacity > 0)
        server.executor = admissionExecutor
        createApiContext("/api/health", "GET") { exchange -> json(exchange, mapOf("ok" to true)) }
        createApiContext("/api/bootstrap", "GET") { exchange -> json(exchange, runtime.bootstrap()) }
        createApiContext("/api/state", "GET", ::handleState)
        createApiContext("/api/actions", "POST", ::handleAction)
        createApiContext("/api/load", "POST", ::handleLoad)
        createApiContext("/api/diagnostics", "GET") { exchange ->
            val values = query(exchange.requestURI.rawQuery)
            json(
                exchange,
                runtime.diagnostics(values["speciesId"]?.toIntOrNull(), values["moveId"]?.toIntOrNull()),
            )
        }
        createApiContext("/api/events", "GET", ::handleEvents)
        createPrefixApiContext("/api/sprites/species/", "GET") { exchange -> handleSprite(exchange, species = true) }
        createPrefixApiContext("/api/sprites/balls/", "GET") { exchange -> handleSprite(exchange, species = false) }
        createPrefixApiContext("/api/maps/", "GET", ::handleMap)
        server.createContext("/", ::handleStaticSafely)
    }

    fun start() = server.start()

    override fun close() {
        eventClients.toList().forEach(EventClient::close)
        eventClients.clear()
        server.stop(0)
        deadlineExecutor.shutdownNow()
        requestExecutor.shutdownNow()
        runtime.close()
    }

    private fun createApiContext(path: String, method: String, action: (HttpExchange) -> Unit) {
        server.createContext(path) { exchange ->
            safelyApi(exchange) {
                when {
                    exchange.requestURI.path != path -> apiNotFound(exchange)
                    exchange.requestMethod != method -> methodNotAllowed(exchange)
                    else -> action(exchange)
                }
            }
        }
    }

    private fun createPrefixApiContext(prefix: String, method: String, action: (HttpExchange) -> Unit) {
        server.createContext(prefix) { exchange ->
            safelyApi(exchange) {
                if (exchange.requestMethod != method) methodNotAllowed(exchange) else action(exchange)
            }
        }
    }

    private fun handleState(exchange: HttpExchange) {
        val rawVersion = query(exchange.requestURI.rawQuery)["sinceVersion"]
        val sinceVersion = rawVersion?.toLongOrNull()
        require(rawVersion == null || sinceVersion != null && sinceVersion >= 0) { "state version is invalid" }
        val view = runtime.stateView()
        if (sinceVersion == view.version) noContent(exchange) else json(exchange, view)
    }

    private fun handleAction(exchange: HttpExchange) {
        val (type, values) = withRequestReadDeadline(exchange) { body -> parseAction(body) }
        json(exchange, runtime.action(type, values))
    }

    private fun handleLoad(exchange: HttpExchange) {
        val name = requireNotNull(query(exchange.requestURI.rawQuery)["name"]) { "upload name is required" }
        withRequestReadDeadline(exchange) { body -> runtime.load(name, body) }
        json(exchange, runtime.bootstrap())
    }

    private fun handleEvents(exchange: HttpExchange) {
        val client = EventClient(exchange)
        eventClients += client
        var subscription: AutoCloseable? = null
        try {
            exchange.responseHeaders.add("Content-Type", "text/event-stream; charset=utf-8")
            exchange.responseHeaders.add("Cache-Control", "no-cache")
            exchange.responseHeaders.add("X-Accel-Buffering", "no")
            exchange.sendResponseHeaders(200, 0)
            subscription = runtime.gateway.subscribe {
                client.pending.replace(gson.toJson(runtime.stateView()))
            }
            exchange.responseBody.bufferedWriter(Charsets.UTF_8).use { output ->
                writeEvent(client, output, gson.toJson(runtime.stateView()))
                while (true) {
                    val state = client.pending.take() ?: break
                    writeEvent(client, output, state)
                }
            }
        } catch (_: IOException) {
            // Disconnects and deadline-forced closes are the normal SSE lifecycle signal.
        } finally {
            subscription?.close()
            eventClients -= client
            client.close()
        }
    }

    private fun writeEvent(client: EventClient, output: java.io.Writer, state: String) {
        eventWriteDeadline.run(onDeadline = client::close) {
            output.write("data: $state\n\n")
            output.flush()
        }
    }

    private fun handleSprite(exchange: HttpExchange, species: Boolean) {
        val path = exchange.requestURI.path
        if (!path.endsWith(".png")) return apiNotFound(exchange)
        val id = path.substringAfterLast('/').removeSuffix(".png").toIntOrNull()
            ?: return apiNotFound(exchange)
        val sprite = if (species) runtime.speciesSprite(id) else runtime.ballSprite(id)
        if (sprite == null) {
            apiNotFound(exchange)
        } else {
            val bytes = PngEncoder.encode(sprite)
            exchange.responseHeaders.add("Content-Type", "image/png")
            exchange.responseHeaders.add("Cache-Control", "no-cache")
            runtime.catalogHash()?.let { exchange.responseHeaders.add("ETag", "\"$it-$id\"") }
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }

    private fun handleMap(exchange: HttpExchange) {
        val rawPath = exchange.requestURI.rawPath
        if (!rawPath.endsWith(".png")) return apiNotFound(exchange)
        val encoded = rawPath.removePrefix("/api/maps/").removeSuffix(".png")
        val key = URLDecoder.decode(encoded, Charsets.UTF_8)
        if (encoded.isBlank() || key.split('/').any { it == ".." }) return apiNotFound(exchange)
        val parameters = query(exchange.requestURI.rawQuery)
        val requestedLighting = requestedLighting(parameters["lighting"])
        val time = requestedTime(parameters["hour"], parameters["minute"])
        val rendered = when (val outcome = runtime.mapAsset(key, requestedLighting, time)) {
            is MapAssetOutcome.Found -> outcome.asset
            MapAssetOutcome.Missing -> return apiNotFound(exchange)
            is MapAssetOutcome.Unavailable -> return apiMapUnavailable(exchange, outcome.diagnostic)
        }
        val variant = rendered.cacheVariant
        exchange.responseHeaders.add("Content-Type", "image/png")
        exchange.responseHeaders.add("Cache-Control", "no-cache")
        runtime.catalogHash()?.let {
            exchange.responseHeaders.add("ETag", "\"$it-map-${key.hashCode()}-$variant\"")
        }
        exchange.sendResponseHeaders(200, rendered.bytes.size.toLong())
        exchange.responseBody.use { it.write(rendered.bytes) }
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

    private fun <T> withRequestReadDeadline(exchange: HttpExchange, action: (InputStream) -> T): T {
        val deadline = RequestReadDeadline(
            exchange,
            deadlineExecutor,
            requestReadTimeoutMillis,
            requestLifetimeMillis,
        )
        return try {
            action(DeadlineInputStream(exchange.requestBody, deadline))
        } catch (failure: IOException) {
            if (deadline.expired) throw RequestTimeoutException() else throw failure
        } finally {
            deadline.close()
        }
    }

    private fun parseAction(input: InputStream): Pair<String, Map<String, String?>> {
        val reader = JsonReader(InputStreamReader(BoundedInputStream(input, MAX_ACTION_BODY_BYTES), Charsets.UTF_8))
        reader.use {
            require(it.peek() == JsonToken.BEGIN_OBJECT) { "action body must be an object" }
            it.beginObject()
            var fields = 0
            var type: String? = null
            val values = linkedMapOf<String, String?>()
            while (it.hasNext()) {
                require(++fields <= MAX_ACTION_FIELDS) { "action contains too many fields" }
                val name = it.nextName()
                require(name.length <= MAX_ACTION_FIELD_NAME_LENGTH) { "action field name is too long" }
                if (name == "type") {
                    require(it.peek() == JsonToken.STRING) { "action type is required" }
                    type = it.nextString()
                } else {
                    values[name] = readActionValue(it)
                }
            }
            it.endObject()
            require(it.peek() == JsonToken.END_DOCUMENT) { "action body must contain one object" }
            return requireNotNull(type?.takeIf(String::isNotBlank)) { "action type is required" } to values
        }
    }

    private fun readActionValue(reader: JsonReader): String? = when (reader.peek()) {
        JsonToken.NULL -> {
            reader.nextNull()
            null
        }
        JsonToken.STRING, JsonToken.NUMBER -> reader.nextString()
        JsonToken.BOOLEAN -> reader.nextBoolean().toString()
        JsonToken.BEGIN_ARRAY, JsonToken.BEGIN_OBJECT -> throw IllegalArgumentException("action values must be scalar")
        else -> throw IllegalArgumentException("action value is invalid")
    }

    private fun handleStaticSafely(exchange: HttpExchange) {
        if (saturatedAdmission.get() == true) {
            if (exchange.requestURI.path == "/api" || exchange.requestURI.path.startsWith("/api/")) {
                apiServerBusy(exchange)
            } else {
                text(exchange, 503, "server busy")
            }
            return
        }
        try {
            handleStatic(exchange)
        } catch (_: Exception) {
            if (exchange.responseCode < 0) text(exchange, 500, "web asset could not be served")
        }
    }

    private fun handleStatic(exchange: HttpExchange) {
        val path = exchange.requestURI.path
        if (path == "/api" || path.startsWith("/api/")) {
            return apiNotFound(exchange)
        }
        if (exchange.requestMethod != "GET") return text(exchange, 405, "method not allowed")
        val normalizedRoot = webRoot.toAbsolutePath().normalize()
        val relative = path.removePrefix("/").ifBlank { "index.html" }
        val safePath = relative.split('/').none { it == ".." }
        val requested = normalizedRoot.resolve(relative).normalize()
        val requestedFile = requested.takeIf {
            safePath && it.startsWith(normalizedRoot) && Files.isRegularFile(it)
        }
        val file = requestedFile ?: normalizedRoot.resolve("index.html").takeIf {
            safePath &&
                isHtmlNavigation(relative, exchange.requestHeaders.getFirst("Accept")) &&
                Files.isRegularFile(it)
        }
        if (file == null) return text(exchange, 404, "web asset not found")
        exchange.responseHeaders.add("Content-Type", contentType(file))
        exchange.responseHeaders.add("Cache-Control", "no-cache")
        exchange.sendResponseHeaders(200, Files.size(file))
        exchange.responseBody.use { output -> Files.newInputStream(file).use { input -> input.copyTo(output) } }
    }

    private fun isHtmlNavigation(relative: String, accept: String?): Boolean =
        '.' !in relative.substringAfterLast('/') && accept.orEmpty().split(',').any { mediaRange ->
            mediaRange.substringBefore(';').trim().equals("text/html", ignoreCase = true)
        }

    private fun safelyApi(exchange: HttpExchange, action: () -> Unit) {
        if (saturatedAdmission.get() == true || !apiPermits.tryAcquire()) {
            apiServerBusy(exchange)
            return
        }
        try {
            action()
        } catch (failure: Exception) {
            if (exchange.responseCode < 0) {
                val error = when (failure) {
                    is RequestTimeoutException -> ApiFailure(
                        400,
                        "REQUEST_TIMEOUT",
                        "The request timed out.",
                        retryable = true,
                    )
                    is RejectedExecutionException -> ApiFailure(
                        503,
                        "SERVER_BUSY",
                        "The server is busy. Try again.",
                        retryable = true,
                    )
                    is JsonParseException, is IllegalArgumentException, is UnsupportedOperationException -> ApiFailure(
                        400,
                        "INVALID_REQUEST",
                        "The request was invalid.",
                        retryable = false,
                    )
                    else -> ApiFailure(
                        500,
                        "INTERNAL_ERROR",
                        "The server could not complete the request.",
                        retryable = true,
                    )
                }
                apiError(exchange, error)
            }
        } finally {
            apiPermits.release()
        }
    }

    private fun apiServerBusy(exchange: HttpExchange) = apiError(
        exchange,
        ApiFailure(503, "SERVER_BUSY", "The server is busy. Try again.", retryable = true),
    )

    private fun apiNotFound(exchange: HttpExchange) = apiError(
        exchange,
        ApiFailure(404, "NOT_FOUND", "The requested resource was not found.", retryable = false),
    )

    private fun apiMapUnavailable(exchange: HttpExchange, diagnostic: String) = json(
        exchange,
        ApiUnavailableErrorView(
            error = ApiErrorDetailView("MAP_UNAVAILABLE", "The map is temporarily unavailable. Try again.", retryable = true),
            diagnostic = diagnostic,
        ),
        503,
    )

    private fun methodNotAllowed(exchange: HttpExchange) = apiError(
        exchange,
        ApiFailure(405, "METHOD_NOT_ALLOWED", "The request method is not allowed.", retryable = false),
    )

    private fun apiError(exchange: HttpExchange, failure: ApiFailure) = json(
        exchange,
        ApiErrorView(ApiErrorDetailView(failure.code, failure.message, failure.retryable)),
        failure.status,
    )

    private fun json(exchange: HttpExchange, value: Any, status: Int = 200) {
        exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        exchange.responseHeaders.add("Cache-Control", "no-store")
        exchange.sendResponseHeaders(status, 0)
        exchange.responseBody.bufferedWriter(Charsets.UTF_8).use { writer -> gson.toJson(value, writer) }
    }

    private fun noContent(exchange: HttpExchange) {
        exchange.responseHeaders.add("Cache-Control", "no-store")
        exchange.sendResponseHeaders(204, -1)
        exchange.close()
    }

    private fun text(exchange: HttpExchange, status: Int, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "text/plain; charset=utf-8")
        exchange.responseHeaders.add("Cache-Control", "no-cache")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun query(raw: String?): Map<String, String> = raw.orEmpty().split('&').filter { it.isNotBlank() }.associate { part ->
        val pieces = part.split('=', limit = 2)
        URLDecoder.decode(pieces[0], Charsets.UTF_8) to URLDecoder.decode(pieces.getOrElse(1) { "" }, Charsets.UTF_8)
    }

    private fun contentType(path: Path): String = when (path.fileName.toString().substringAfterLast('.', "").lowercase()) {
        "html" -> "text/html; charset=utf-8"
        "js" -> "text/javascript; charset=utf-8"
        "css" -> "text/css; charset=utf-8"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "map" -> "application/json; charset=utf-8"
        else -> "application/octet-stream"
    }

    private data class ApiUnavailableErrorView(
        val error: ApiErrorDetailView,
        val diagnostic: String,
    )

    private data class ApiFailure(
        val status: Int,
        val code: String,
        val message: String,
        val retryable: Boolean,
    )

    private inner class EventClient(val exchange: HttpExchange) {
        val pending = ConflatingSlot<String>()
        private val closed = AtomicBoolean()

        fun close() {
            if (!closed.compareAndSet(false, true)) return
            pending.close()
            exchange.close()
        }
    }

    private companion object {
        const val EVENT_WRITE_TIMEOUT_MILLIS = 5_000L
        const val REQUEST_READ_TIMEOUT_MILLIS = 5_000L
        const val REQUEST_LIFETIME_MILLIS = 30_000L
        const val MAX_ACTION_BODY_BYTES = 1_024L * 1_024L
        const val MAX_ACTION_FIELDS = 64
        const val MAX_ACTION_FIELD_NAME_LENGTH = 128
        const val HTTP_WORKERS = 8
        const val HTTP_QUEUE_CAPACITY = 8
        const val API_CAPACITY = 4
    }
}

private class RequestTimeoutException : IOException()

private class RequestReadDeadline(
    private val exchange: HttpExchange,
    private val scheduler: ScheduledExecutorService,
    private val progressTimeoutMillis: Long,
    absoluteTimeoutMillis: Long,
) : AutoCloseable {
    private val expiredFlag = AtomicBoolean()
    private var progressDeadline: ScheduledFuture<*>? = null
    private val absoluteDeadline = scheduler.schedule(::expire, absoluteTimeoutMillis, TimeUnit.MILLISECONDS)

    init {
        progress()
    }

    val expired: Boolean
        get() = expiredFlag.get()

    @Synchronized
    fun progress() {
        if (expired) return
        progressDeadline?.cancel(false)
        progressDeadline = scheduler.schedule(::expire, progressTimeoutMillis, TimeUnit.MILLISECONDS)
    }

    override fun close() {
        absoluteDeadline.cancel(false)
        synchronized(this) {
            progressDeadline?.cancel(false)
            progressDeadline = null
        }
    }

    private fun expire() {
        if (expiredFlag.compareAndSet(false, true)) exchange.close()
    }
}

private class DeadlineInputStream(
    source: InputStream,
    private val deadline: RequestReadDeadline,
) : FilterInputStream(source) {
    override fun read(): Int = readWithDeadline { super.read() }

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int =
        readWithDeadline { super.read(bytes, offset, length) }

    private fun readWithDeadline(read: () -> Int): Int = try {
        read().also { if (it > 0) deadline.progress() }
    } catch (failure: IOException) {
        if (deadline.expired) throw RequestTimeoutException()
        throw failure
    }
}

private class BoundedInputStream(
    source: InputStream,
    private val maximumBytes: Long,
) : FilterInputStream(source) {
    private var consumed = 0L

    override fun read(): Int {
        require(consumed < maximumBytes) { "request body is too large" }
        return super.read().also { if (it >= 0) consumed++ }
    }

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        require(consumed < maximumBytes) { "request body is too large" }
        val permitted = minOf(length.toLong(), maximumBytes - consumed).toInt()
        return super.read(bytes, offset, permitted).also { count ->
            if (count > 0) consumed += count
        }
    }
}

internal class WriteDeadline(
    private val scheduler: ScheduledExecutorService,
    private val timeoutMillis: Long,
) {
    init {
        require(timeoutMillis > 0) { "write timeout must be positive" }
    }

    fun <T> run(onDeadline: () -> Unit, action: () -> T): T {
        val deadline = scheduler.schedule(onDeadline, timeoutMillis, TimeUnit.MILLISECONDS)
        return try {
            action()
        } finally {
            deadline.cancel(false)
        }
    }
}

internal class ConflatingSlot<T : Any> {
    private val values = ArrayBlockingQueue<Any>(1)
    @Volatile private var closed = false

    fun replace(value: T) {
        synchronized(values) {
            if (closed) return
            values.poll()
            check(values.offer(value))
        }
    }

    fun take(): T? {
        val value = values.take()
        if (value === CLOSED) return null
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    fun close() {
        synchronized(values) {
            if (closed) return
            closed = true
            values.clear()
            check(values.offer(CLOSED))
        }
    }

    private companion object {
        val CLOSED = Any()
    }
}

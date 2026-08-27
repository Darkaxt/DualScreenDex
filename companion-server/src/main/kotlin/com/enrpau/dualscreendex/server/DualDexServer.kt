package com.enrpau.dualscreendex.server

import com.enrpau.dualscreendex.companion.api.ApiErrorDetailView
import com.enrpau.dualscreendex.companion.api.ApiErrorView
import com.enrpau.dualscreendex.parser.catalog.MapLighting
import com.enrpau.dualscreendex.parser.catalog.MapTimeOfDay
import com.enrpau.dualscreendex.parser.io.RomSourceLoader
import com.enrpau.dualscreendex.parser.sprite.PngEncoder
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class DualDexServer(
    private val runtime: DualDexRuntime,
    private val webRoot: Path,
    port: Int = 47831,
) : AutoCloseable {
    private val gson = GsonBuilder().serializeNulls().create()
    private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0)
    private val eventDeadlineExecutor = ScheduledThreadPoolExecutor(1) { runnable ->
        Thread(runnable, "dualdex-sse-deadline").apply { isDaemon = true }
    }.apply {
        removeOnCancelPolicy = true
    }
    private val eventWriteDeadline = WriteDeadline(eventDeadlineExecutor, EVENT_WRITE_TIMEOUT_MILLIS)
    private val eventClients = ConcurrentHashMap.newKeySet<EventClient>()
    val address: InetSocketAddress get() = server.address

    init {
        server.executor = Executors.newCachedThreadPool()
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
        eventDeadlineExecutor.shutdownNow()
        (server.executor as? ExecutorService)?.shutdownNow()
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
        val request = exchange.requestBody.reader().use { gson.fromJson(it, JsonObject::class.java) }
            ?: throw IllegalArgumentException("action body is required")
        val type = requireNotNull(request.get("type")?.asString) { "action type is required" }
        val values = request.entrySet().filter { it.key != "type" }.associate { entry ->
            entry.key to if (entry.value.isJsonNull) null else entry.value.asString
        }
        json(exchange, runtime.action(type, values))
    }

    private fun handleLoad(exchange: HttpExchange) {
        val name = requireNotNull(query(exchange.requestURI.rawQuery)["name"]) { "upload name is required" }
        runtime.load(RomSourceLoader.load(name, exchange.requestBody))
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
        val rendered = runCatching { runtime.mapAsset(key, requestedLighting, time) }.getOrNull()
            ?: return apiNotFound(exchange)
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

    private fun handleStaticSafely(exchange: HttpExchange) {
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
        try {
            action()
        } catch (failure: Exception) {
            if (exchange.responseCode < 0) {
                val error = when (failure) {
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
        }
    }

    private fun apiNotFound(exchange: HttpExchange) = apiError(
        exchange,
        ApiFailure(404, "NOT_FOUND", "The requested resource was not found.", retryable = false),
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

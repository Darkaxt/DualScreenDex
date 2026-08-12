package com.darkaxt.dualdex.web

import com.enrpau.dualscreendex.parser.sprite.PngEncoder
import java.nio.charset.StandardCharsets
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

interface MapperHttpHandler {
    fun state(): Any
    fun action(type: String, values: Map<String, String?>): Any
    fun exportRaw(): ByteArray
}

/** Small HTTP/1.1 host for the bundled WebView. It never binds outside 127.0.0.1. */
class AndroidLoopbackServer(
    private val runtime: ProductionCompanionRuntime,
    private val requestedPort: Int = 0,
    private val assetLoader: (String) -> ByteArray?,
) : AutoCloseable {
    private val gson: Gson = GsonBuilder().serializeNulls().create()
    private val acceptor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "dualdex-loopback-acceptor").apply { isDaemon = true }
    }
    private val clients: ExecutorService = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "dualdex-loopback-client").apply { isDaemon = true }
    }
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

    override fun close() {
        val bound = synchronized(this) {
            val current = socket
            socket = null
            current
        }
        bound?.close()
        activeSockets.toList().forEach(Socket::close)
        acceptor.shutdown()
        clients.shutdown()
        runtime.close()
    }

    private fun acceptConnections(server: ServerSocket) {
        while (!server.isClosed) {
            try {
                val client = server.accept()
                activeSockets += client
                clients.execute { handle(client) }
            } catch (failure: Exception) {
                if (!server.isClosed) throw failure
            }
        }
    }

    private fun handle(client: Socket) {
        client.use { connection ->
            try {
                val request = readRequest(BufferedInputStream(connection.getInputStream()))
                writeResponse(BufferedOutputStream(connection.getOutputStream()), route(request))
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
        request.method == "GET" && request.path == "/api/state" -> jsonResponse(runtime.stateView())
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
        request.method == "GET" && request.path.startsWith("/api/maps/") -> worldMapResponse(request.path)
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
        val payload = gson.fromJson(request.body.toString(Charsets.UTF_8), JsonObject::class.java)
        val type = payload.get("type")?.asString ?: error("action type is required")
        val values = payload.entrySet().filter { it.key != "type" }.associate { entry ->
            entry.key to if (entry.value.isJsonNull) null else entry.value.asString
        }
        return type to values
    }

    private fun handleLoad(request: Request): Response {
        val name = request.query["name"] ?: error("upload name is required")
        return jsonResponse(runtime.load(name, request.body.inputStream()))
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

    private fun worldMapResponse(path: String): Response {
        val encoded = path.removePrefix("/api/maps/").removeSuffix(".png")
        if (encoded.isBlank() || encoded.contains('/') || encoded.contains("..")) return textResponse("map not available", 404)
        val key = runCatching { URLDecoder.decode(encoded, StandardCharsets.UTF_8) }.getOrNull()
            ?: return textResponse("map not available", 404)
        if (key.split('/').any { it == ".." }) return textResponse("map not available", 404)
        val sprite = runtime.worldMapAsset(key) ?: return textResponse("map not available", 404)
        return Response(
            200,
            "image/png",
            PngEncoder.encode(sprite),
            buildMap {
                put("Cache-Control", "public, max-age=31536000, immutable")
                runtime.catalogHash()?.let { put("ETag", "\"$it-map-${key.hashCode()}\"") }
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
        status,
        "application/json; charset=utf-8",
        gson.toJson(value).toByteArray(Charsets.UTF_8),
        mapOf("Cache-Control" to "no-store"),
    )

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
        val body = when {
            headers["transfer-encoding"]?.contains("chunked", ignoreCase = true) == true -> readChunked(input)
            headers["content-length"] != null -> readFixed(input, headers.getValue("content-length").toLong())
            else -> byteArrayOf()
        }
        return Request(parts[0].uppercase(Locale.ROOT), rawPath, query, body)
    }

    private fun readFixed(input: InputStream, length: Long): ByteArray {
        require(length in 0..MAX_BODY_BYTES) { "request body is too large" }
        val output = ByteArray(length.toInt())
        var offset = 0
        while (offset < output.size) {
            val read = input.read(output, offset, output.size - offset)
            if (read < 0) throw EOFException("request body ended early")
            offset += read
        }
        return output
    }

    private fun readChunked(input: BufferedInputStream): ByteArray {
        val output = ByteArrayOutputStream()
        while (true) {
            val sizeLine = readLine(input) ?: throw EOFException("missing chunk size")
            val size = sizeLine.substringBefore(';').trim().toLong(16)
            if (size == 0L) {
                while (!readLine(input).isNullOrEmpty()) Unit
                return output.toByteArray()
            }
            require(output.size().toLong() + size <= MAX_BODY_BYTES) { "request body is too large" }
            output.write(readFixed(input, size))
            require(readLine(input).orEmpty().isEmpty()) { "invalid chunk terminator" }
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
            400 -> "Bad Request"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            else -> "Error"
        }
        output.write("HTTP/1.1 ${response.status} $reason\r\n".toByteArray(Charsets.US_ASCII))
        output.write("Content-Type: ${response.contentType}\r\n".toByteArray(Charsets.US_ASCII))
        output.write("Content-Length: ${response.body.size}\r\n".toByteArray(Charsets.US_ASCII))
        output.write("Connection: close\r\n".toByteArray(Charsets.US_ASCII))
        response.headers.forEach { (name, value) ->
            output.write("$name: $value\r\n".toByteArray(Charsets.US_ASCII))
        }
        output.write("\r\n".toByteArray(Charsets.US_ASCII))
        output.write(response.body)
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
        val body: ByteArray,
    )

    private data class Response(
        val status: Int,
        val contentType: String,
        val body: ByteArray,
        val headers: Map<String, String> = emptyMap(),
    )

    companion object {
        const val LOOPBACK_HOST = "127.0.0.1"
        private const val MAX_LINE_BYTES = 64 * 1024
        private const val MAX_BODY_BYTES = 256L * 1024 * 1024
    }
}

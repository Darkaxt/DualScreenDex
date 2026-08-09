package com.enrpau.dualscreendex.server

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue

class DualDexServer(
    private val runtime: DualDexRuntime,
    private val webRoot: Path,
    port: Int = 47831,
) : AutoCloseable {
    private val gson = GsonBuilder().serializeNulls().create()
    private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0)
    val address: InetSocketAddress get() = server.address

    init {
        server.executor = Executors.newCachedThreadPool()
        server.createContext("/api/health") { exchange ->
            if (exchange.requestMethod != "GET") methodNotAllowed(exchange) else json(exchange, mapOf("ok" to true))
        }
        server.createContext("/api/bootstrap") { exchange ->
            if (exchange.requestMethod != "GET") methodNotAllowed(exchange) else json(exchange, runtime.bootstrap())
        }
        server.createContext("/api/actions") { exchange ->
            if (exchange.requestMethod != "POST") methodNotAllowed(exchange) else handleAction(exchange)
        }
        server.createContext("/api/load") { exchange ->
            if (exchange.requestMethod != "POST") methodNotAllowed(exchange) else handleLoad(exchange)
        }
        server.createContext("/api/diagnostics") { exchange ->
            if (exchange.requestMethod != "GET") {
                methodNotAllowed(exchange)
            } else {
                safely(exchange) {
                    val values = query(exchange.requestURI.rawQuery)
                    json(
                        exchange,
                        runtime.diagnostics(values["speciesId"]?.toIntOrNull(), values["moveId"]?.toIntOrNull()),
                    )
                }
            }
        }
        server.createContext("/api/events") { exchange ->
            if (exchange.requestMethod != "GET") methodNotAllowed(exchange) else handleEvents(exchange)
        }
        server.createContext("/api/sprites/species/") { exchange -> handleSprite(exchange, species = true) }
        server.createContext("/api/sprites/balls/") { exchange -> handleSprite(exchange, species = false) }
        server.createContext("/", ::handleStatic)
    }

    fun start() = server.start()

    override fun close() {
        server.stop(0)
        (server.executor as? java.util.concurrent.ExecutorService)?.shutdown()
        runtime.close()
    }

    private fun handleAction(exchange: HttpExchange) = safely(exchange) {
        val request = exchange.requestBody.reader().use { gson.fromJson(it, JsonObject::class.java) }
        val type = request.get("type")?.asString ?: error("action type is required")
        val values = request.entrySet().filter { it.key != "type" }.associate { entry ->
            entry.key to if (entry.value.isJsonNull) null else entry.value.asString
        }
        json(exchange, runtime.action(type, values))
    }

    private fun handleLoad(exchange: HttpExchange) = safely(exchange) {
        val name = query(exchange.requestURI.rawQuery)["name"] ?: error("upload name is required")
        runtime.load(RomSourceLoader.load(name, exchange.requestBody))
        json(exchange, runtime.bootstrap())
    }

    private fun handleEvents(exchange: HttpExchange) {
        exchange.responseHeaders.add("Content-Type", "text/event-stream; charset=utf-8")
        exchange.responseHeaders.add("Cache-Control", "no-cache")
        exchange.responseHeaders.add("X-Accel-Buffering", "no")
        exchange.sendResponseHeaders(200, 0)
        val queue = LinkedBlockingQueue<String>()
        val subscription = runtime.gateway.subscribe { queue.put(gson.toJson(runtime.stateView())) }
        try {
            exchange.responseBody.bufferedWriter(Charsets.UTF_8).use { output ->
                output.write("data: ${gson.toJson(runtime.stateView())}\n\n")
                output.flush()
                while (true) {
                    output.write("data: ${queue.take()}\n\n")
                    output.flush()
                }
            }
        } catch (_: IOException) {
            // Browser disconnected; closing the stream is the normal SSE lifecycle signal.
        } finally {
            subscription.close()
            exchange.close()
        }
    }

    private fun handleSprite(exchange: HttpExchange, species: Boolean) {
        if (exchange.requestMethod != "GET") return methodNotAllowed(exchange)
        safely(exchange) {
            val id = exchange.requestURI.path.substringAfterLast('/').substringBefore('.').toInt()
            val sprite = if (species) runtime.speciesSprite(id) else runtime.ballSprite(id)
            if (sprite == null) {
                text(exchange, 404, "sprite not available")
            } else {
                val bytes = PngEncoder.encode(sprite)
                exchange.responseHeaders.add("Content-Type", "image/png")
                exchange.responseHeaders.add("Cache-Control", "public, max-age=31536000, immutable")
                runtime.catalogHash()?.let { exchange.responseHeaders.add("ETag", "\"$it-$id\"") }
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
        }
    }

    private fun handleStatic(exchange: HttpExchange) {
        if (exchange.requestMethod != "GET") return methodNotAllowed(exchange)
        val normalizedRoot = webRoot.toAbsolutePath().normalize()
        val relative = exchange.requestURI.path.removePrefix("/").ifBlank { "index.html" }
        val requested = normalizedRoot.resolve(relative).normalize()
        val file = requested.takeIf { it.startsWith(normalizedRoot) && Files.isRegularFile(it) }
            ?: normalizedRoot.resolve("index.html").takeIf(Files::isRegularFile)
        if (file == null) return text(exchange, 404, "web bundle not built")
        val bytes = Files.readAllBytes(file)
        exchange.responseHeaders.add("Content-Type", contentType(file))
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun safely(exchange: HttpExchange, action: () -> Unit) {
        try {
            action()
        } catch (failure: Exception) {
            if (exchange.responseCode < 0) {
                json(exchange, mapOf("error" to (failure.message ?: failure.javaClass.simpleName)), 400)
            }
        }
    }

    private fun json(exchange: HttpExchange, value: Any, status: Int = 200) {
        val bytes = gson.toJson(value).toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        exchange.responseHeaders.add("Cache-Control", "no-store")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun text(exchange: HttpExchange, status: Int, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "text/plain; charset=utf-8")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun methodNotAllowed(exchange: HttpExchange) = text(exchange, 405, "method not allowed")

    private fun query(raw: String?): Map<String, String> = raw.orEmpty().split('&').filter { it.isNotBlank() }.associate { part ->
        val pieces = part.split('=', limit = 2)
        URLDecoder.decode(pieces[0], Charsets.UTF_8) to URLDecoder.decode(pieces.getOrElse(1) { "" }, Charsets.UTF_8)
    }

    private fun contentType(path: Path): String = when (path.fileName.toString().substringAfterLast('.', "")) {
        "html" -> "text/html; charset=utf-8"
        "js" -> "text/javascript; charset=utf-8"
        "css" -> "text/css; charset=utf-8"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        else -> "application/octet-stream"
    }
}

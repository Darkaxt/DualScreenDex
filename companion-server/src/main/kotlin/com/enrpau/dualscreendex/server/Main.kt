package com.enrpau.dualscreendex.server

import java.nio.file.Path
import java.util.concurrent.CountDownLatch

fun main(arguments: Array<String>) {
    val options = ServerOptions.parse(arguments)
    val runtime = DualDexRuntime()
    options.rom?.let(runtime::load)
    val server = DualDexServer(runtime, options.webRoot, options.port)
    Runtime.getRuntime().addShutdownHook(Thread { server.close() })
    server.start()
    println("DualDex POC: http://${server.address.hostString}:${server.address.port}")
    println(if (options.rom == null) "No ROM loaded; use the browser ROM picker." else "Loaded ${options.rom.fileName}")
    CountDownLatch(1).await()
}

private data class ServerOptions(val rom: Path?, val webRoot: Path, val port: Int) {
    companion object {
        fun parse(arguments: Array<String>): ServerOptions {
            var rom: Path? = null
            var webRoot = Path.of("companion-web", "dist")
            var port = 47831
            var index = 0
            while (index < arguments.size) {
                when (arguments[index]) {
                    "--rom" -> rom = Path.of(arguments[++index])
                    "--web-root" -> webRoot = Path.of(arguments[++index])
                    "--port" -> port = arguments[++index].toInt()
                    else -> error("unknown option: ${arguments[index]}")
                }
                index++
            }
            return ServerOptions(rom, webRoot, port)
        }
    }
}

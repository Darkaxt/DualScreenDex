package com.darkaxt.dualdex.display

import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.system.exitProcess

internal data class ThorFocusProcessResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

internal fun interface ThorFocusCommandRunner {
    fun run(command: List<String>): ThorFocusProcessResult
}

private object RuntimeThorFocusCommandRunner : ThorFocusCommandRunner {
    override fun run(command: List<String>): ThorFocusProcessResult {
        val process = ProcessBuilder(command).start()
        val stdout = AtomicReference("")
        val stderr = AtomicReference("")
        val streamFailure = AtomicReference<Throwable?>(null)
        val stdoutReader = thread(name = "thor-focus-stdout") {
            runCatching { process.inputStream.bufferedReader().use { it.readText() } }
                .onSuccess(stdout::set)
                .onFailure { streamFailure.compareAndSet(null, it) }
        }
        val stderrReader = thread(name = "thor-focus-stderr") {
            runCatching { process.errorStream.bufferedReader().use { it.readText() } }
                .onSuccess(stderr::set)
                .onFailure { streamFailure.compareAndSet(null, it) }
        }
        val exitCode = process.waitFor()
        stdoutReader.join()
        stderrReader.join()
        streamFailure.get()?.let { throw it }
        return ThorFocusProcessResult(exitCode, stdout.get(), stderr.get())
    }
}

internal class ThorFocusCommandExecutor(
    private val runner: ThorFocusCommandRunner,
) {
    fun readMode(): Int {
        val result = runner.run(ThorFocusCommand.read())
        check(result.exitCode == 0) {
            "Thor focus read failed with exit code ${result.exitCode}: ${result.stderr.trim()}"
        }
        return checkNotNull(ThorFocusCommand.parse(result.stdout)) {
            "Thor focus read returned an unsupported mode"
        }
    }

    fun writeMode(mode: Int): Boolean {
        val command = ThorFocusCommand.write(mode)
        return runner.run(command).exitCode == 0
    }
}

class ThorFocusShizukuService private constructor(
    private val executor: ThorFocusCommandExecutor,
) : IThorFocusPrivilegedService.Stub() {
    constructor() : this(ThorFocusCommandExecutor(RuntimeThorFocusCommandRunner))

    override fun readMode(): Int = executor.readMode()

    override fun writeMode(mode: Int): Boolean = executor.writeMode(mode)

    override fun destroy() {
        exitProcess(0)
    }
}

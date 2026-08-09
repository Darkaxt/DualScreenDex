package com.darkaxt.dualdex.retroarch

data class ConfigPatch(
    val original: ByteArray,
    val updated: ByteArray,
    val changedKeys: Set<String>,
)

data class ConfigVerification(
    val valid: Boolean,
    val values: Map<String, List<String>>,
    val errors: List<String>,
)

object ConfigDocumentEditor {
    private val approvedKeys = linkedMapOf(
        "network_cmd_enable" to { _: Int -> "true" },
        "network_cmd_port" to { port: Int -> port.toString() },
        "autosave_interval" to { _: Int -> "10" },
    )

    fun patchNetworkCommands(original: ByteArray, port: Int): ConfigPatch {
        require(port in 1..65535) { "network command port must be between 1 and 65535" }
        val text = original.toString(Charsets.UTF_8)
        val preferredEnding = preferredLineEnding(text)
        val lines = parseLines(text).toMutableList()
        val expected = approvedKeys.mapValues { it.value(port) }
        val found = mutableSetOf<String>()
        val changed = linkedSetOf<String>()

        lines.indices.forEach { index ->
            val line = lines[index]
            val parsed = parseAssignment(line.content) ?: return@forEach
            val wanted = expected[parsed.key] ?: return@forEach
            found += parsed.key
            val replacement = parsed.render(wanted)
            if (replacement != line.content) {
                lines[index] = line.copy(content = replacement)
                changed += parsed.key
            }
        }

        expected.forEach { (key, value) ->
            if (key !in found) {
                if (lines.isNotEmpty() && lines.last().ending.isEmpty()) {
                    lines[lines.lastIndex] = lines.last().copy(ending = preferredEnding)
                }
                lines += ConfigLine("$key = \"$value\"", preferredEnding)
                changed += key
            }
        }
        val updated = lines.joinToString(separator = "") { it.content + it.ending }.toByteArray(Charsets.UTF_8)
        return ConfigPatch(original.copyOf(), updated, changed)
    }

    fun verifyNetworkCommands(document: ByteArray, port: Int): ConfigVerification {
        require(port in 1..65535) { "network command port must be between 1 and 65535" }
        val expected = mapOf(
            "network_cmd_enable" to "true",
            "network_cmd_port" to port.toString(),
            "autosave_interval" to "10",
        )
        val values = parseLines(document.toString(Charsets.UTF_8))
            .mapNotNull { parseAssignment(it.content) }
            .filter { it.key in expected }
            .groupBy({ it.key }, { it.value })
        val errors = expected.mapNotNull { (key, wanted) ->
            val actual = values[key].orEmpty()
            when {
                actual.isEmpty() -> "$key is missing"
                actual.any { it != wanted } -> "$key does not consistently equal $wanted"
                else -> null
            }
        }
        return ConfigVerification(errors.isEmpty(), values, errors)
    }

    private fun preferredLineEnding(text: String): String = when {
        "\r\n" in text -> "\r\n"
        '\n' in text -> "\n"
        '\r' in text -> "\r"
        else -> System.lineSeparator()
    }

    private fun parseLines(text: String): List<ConfigLine> {
        if (text.isEmpty()) return emptyList()
        val result = mutableListOf<ConfigLine>()
        var start = 0
        var index = 0
        while (index < text.length) {
            val ending = when (text[index]) {
                '\r' -> if (index + 1 < text.length && text[index + 1] == '\n') "\r\n" else "\r"
                '\n' -> "\n"
                else -> null
            }
            if (ending == null) {
                index += 1
                continue
            }
            result += ConfigLine(text.substring(start, index), ending)
            index += ending.length
            start = index
        }
        if (start < text.length) result += ConfigLine(text.substring(start), "")
        return result
    }

    private fun parseAssignment(line: String): ConfigAssignment? {
        if (line.trimStart().startsWith('#')) return null
        val match = ASSIGNMENT.matchEntire(line) ?: return null
        return ConfigAssignment(
            indentation = match.groupValues[1],
            key = match.groupValues[2],
            separator = match.groupValues[3],
            value = match.groupValues[4].ifEmpty { match.groupValues[5] },
            suffix = match.groupValues[6],
        )
    }

    private data class ConfigLine(val content: String, val ending: String)
    private data class ConfigAssignment(
        val indentation: String,
        val key: String,
        val separator: String,
        val value: String,
        val suffix: String,
    ) {
        fun render(newValue: String): String = "$indentation$key$separator\"$newValue\"$suffix"
    }

    private val ASSIGNMENT = Regex("^([ \\t]*)([A-Za-z0-9_]+)([ \\t]*=[ \\t]*)(?:\"([^\"]*)\"|([^ \\t#]+))([ \\t]*(?:#.*)?)$")
}

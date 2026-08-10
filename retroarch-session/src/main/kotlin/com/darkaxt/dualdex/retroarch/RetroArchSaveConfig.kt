package com.darkaxt.dualdex.retroarch

data class RetroArchSaveSettings(
    val savefileDirectory: String?,
    val autosaveIntervalSeconds: Int?,
    val sortByCore: Boolean?,
    val sortByContentDirectory: Boolean?,
) {
    val autosaveStatus: String
        get() = when {
            autosaveIntervalSeconds == null -> "UNVERIFIED"
            autosaveIntervalSeconds <= 0 -> "DISABLED"
            else -> "VERIFIED"
        }
}

object RetroArchSaveConfig {
    private val relevantKeys = setOf(
        "savefile_directory",
        "autosave_interval",
        "sort_savefiles_enable",
        "sort_savefiles_by_content_enable",
    )
    private val assignment = Regex("^[ \\t]*([A-Za-z0-9_]+)[ \\t]*=[ \\t]*(?:\"([^\"]*)\"|([^ \\t#]+))(?:[ \\t]*#.*)?$")

    fun read(document: ByteArray): RetroArchSaveSettings {
        val values = linkedMapOf<String, String>()
        document.toString(Charsets.UTF_8).lineSequence().forEach { line ->
            if (line.trimStart().startsWith('#')) return@forEach
            val match = assignment.matchEntire(line) ?: return@forEach
            val key = match.groupValues[1]
            if (key in relevantKeys) values[key] = match.groupValues[2].ifEmpty { match.groupValues[3] }
        }
        return RetroArchSaveSettings(
            savefileDirectory = values["savefile_directory"]?.takeIf(String::isNotBlank),
            autosaveIntervalSeconds = values["autosave_interval"]?.toIntOrNull(),
            sortByCore = values["sort_savefiles_enable"]?.toBooleanStrictOrNull(),
            sortByContentDirectory = values["sort_savefiles_by_content_enable"]?.toBooleanStrictOrNull(),
        )
    }
}

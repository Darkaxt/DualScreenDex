package com.darkaxt.dualdex.settings

import android.content.SharedPreferences
import com.enrpau.dualscreendex.companion.model.CompanionSettings
import com.enrpau.dualscreendex.companion.model.Density
import com.enrpau.dualscreendex.companion.model.DisplayMode
import com.enrpau.dualscreendex.companion.model.DisplayTarget
import com.enrpau.dualscreendex.companion.model.KnowledgeMode
import com.enrpau.dualscreendex.companion.model.Theme
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser

class SettingsRepository(
    private val readDocument: () -> String?,
    private val writeDocument: (String) -> Unit,
    private val gson: Gson = Gson(),
) {
    constructor(preferences: SharedPreferences) : this(
        readDocument = { preferences.getString(DOCUMENT_KEY, null) },
        writeDocument = { document ->
            check(preferences.edit().putString(DOCUMENT_KEY, document).commit()) {
                "DualDex settings could not be persisted"
            }
        },
    )

    /** Backward-compatible global settings seam. */
    @Synchronized
    fun read(): CompanionSettings = readGlobal()

    /** Backward-compatible global settings seam. */
    @Synchronized
    fun write(settings: CompanionSettings) = writeGlobal(settings)

    @Synchronized
    fun readGlobal(): CompanionSettings = readState().globalDefaults

    @Synchronized
    fun readForRom(romSha256: String?): CompanionSettings {
        val state = readState()
        val key = validRomSha(romSha256) ?: return state.globalDefaults
        return state.romOverrides[key]?.applyTo(state.globalDefaults) ?: state.globalDefaults
    }

    @Synchronized
    fun writeGlobal(settings: CompanionSettings) {
        val state = readState()
        if (!state.writable) return
        val sanitized = sanitize(settings)
        if (state.legacy && sanitized.ruleset != "AUTO") {
            writeLegacyState(sanitized)
        } else {
            writeState(sanitized, state.romOverrides)
        }
    }

    @Synchronized
    fun writeForRom(romSha256: String?, settings: CompanionSettings) {
        if (romSha256 == null) {
            writeGlobal(settings)
            return
        }
        val key = validRomSha(romSha256) ?: return
        var state = readState()
        if (!state.writable) return
        if (state.legacy) {
            migrateLegacyRuleset(key)
            state = readState()
            if (!state.writable) return
        }
        val sanitized = sanitize(settings)
        val globals = state.globalDefaults.copy(
            displayTarget = sanitized.displayTarget,
            overlayScale = sanitized.overlayScale,
            battlePollingIntervalMs = sanitized.battlePollingIntervalMs,
            mapFollowSmoothingPercent = sanitized.mapFollowSmoothingPercent,
            highVisibilityMapPlayer = sanitized.highVisibilityMapPlayer,
        )
        val override = StoredSettings.difference(sanitized, globals)
        val overrides = LinkedHashMap(state.romOverrides)
        if (override.isEmpty()) {
            overrides.remove(key)
        } else {
            check(key in overrides || overrides.size < MAX_ROM_OVERRIDES) {
                "DualDex settings cannot exceed $MAX_ROM_OVERRIDES ROM overrides"
            }
            overrides[key] = override
        }
        writeState(globals, overrides)
    }

    @Synchronized
    fun migrateLegacyRuleset(lastRomSha256: String?) {
        val raw = readDocument() ?: return
        val root = parseObject(raw) ?: return
        val schema = root.intValue("schema")
        if (schema == SCHEMA || schema != null && schema != LEGACY_SCHEMA) return
        val legacy = readSettings(root, CompanionSettings(), includeDeviceFields = true)
        val manualRuleset = legacy.ruleset.takeUnless { it == "AUTO" }
        val key = validRomSha(lastRomSha256)
        if (manualRuleset != null && key == null) return

        val globals = if (manualRuleset == null) legacy else legacy.copy(ruleset = "AUTO")
        val overrides = if (manualRuleset == null) {
            linkedMapOf()
        } else {
            linkedMapOf(requireNotNull(key) to StoredSettings(ruleset = manualRuleset))
        }
        writeState(globals, overrides)
    }

    private fun readState(): RepositoryState {
        val raw = readDocument() ?: return RepositoryState(CompanionSettings(), linkedMapOf())
        val root = parseObject(raw) ?: return RepositoryState(CompanionSettings(), linkedMapOf())
        val schema = root.intValue("schema")
        if (schema != null && schema != LEGACY_SCHEMA && schema != SCHEMA) {
            return RepositoryState(CompanionSettings(), linkedMapOf(), writable = false)
        }
        if (schema != SCHEMA) {
            return RepositoryState(
                readSettings(root, CompanionSettings(), includeDeviceFields = true),
                linkedMapOf(),
                legacy = true,
            )
        }

        val globals = root.objectValue("globalDefaults")
            ?.let { readSettings(it, CompanionSettings(), includeDeviceFields = true) }
            ?: CompanionSettings()
        val overrides = linkedMapOf<String, StoredSettings>()
        val storedOverrides = root.objectValue("romOverrides")
        if (storedOverrides != null && storedOverrides.entrySet().size > MAX_ROM_OVERRIDES) {
            return RepositoryState(CompanionSettings(), linkedMapOf(), writable = false)
        }
        storedOverrides?.entrySet()?.forEach { (key, value) ->
            if (validRomSha(key) == null || !value.isJsonObject) return@forEach
            readStoredSettings(value.asJsonObject, includeDeviceFields = false)
                .takeUnless(StoredSettings::isEmpty)
                ?.let { overrides[key] = it }
        }
        return RepositoryState(globals, overrides)
    }

    private fun writeState(
        globalDefaults: CompanionSettings,
        romOverrides: Map<String, StoredSettings>,
    ) {
        check(romOverrides.size <= MAX_ROM_OVERRIDES) {
            "DualDex settings cannot exceed $MAX_ROM_OVERRIDES ROM overrides"
        }
        writeDocument(
            gson.toJson(
                StoredDocument(
                    schema = SCHEMA,
                    globalDefaults = StoredSettings.complete(sanitize(globalDefaults)),
                    romOverrides = romOverrides.toSortedMap(),
                ),
            ),
        )
    }

    private fun writeLegacyState(settings: CompanionSettings) {
        val document = gson.toJsonTree(StoredSettings.complete(settings)).asJsonObject
        document.addProperty("schema", LEGACY_SCHEMA)
        writeDocument(gson.toJson(document))
    }

    private fun readSettings(
        objectValue: JsonObject,
        fallback: CompanionSettings,
        includeDeviceFields: Boolean,
    ): CompanionSettings = readStoredSettings(objectValue, includeDeviceFields).applyTo(fallback)

    private fun readStoredSettings(
        objectValue: JsonObject,
        includeDeviceFields: Boolean,
    ) = StoredSettings(
        knowledgeMode = enumName<KnowledgeMode>(objectValue.stringValue("knowledgeMode")),
        attackEnabled = objectValue.booleanValue("attackEnabled"),
        rarityEnabled = objectValue.booleanValue("rarityEnabled"),
        movesEnabled = objectValue.booleanValue("movesEnabled"),
        fontScale = objectValue.doubleValue("fontScale")?.takeIf(Double::isFinite)?.coerceIn(0.85, 1.35),
        density = enumName<Density>(objectValue.stringValue("density")),
        highContrast = objectValue.booleanValue("highContrast"),
        autoOpenTarget = objectValue.booleanValue("autoOpenTarget"),
        ruleset = normalizedRuleset(objectValue.stringValue("ruleset")),
        displayMode = enumName<DisplayMode>(objectValue.stringValue("displayMode")),
        theme = enumName<Theme>(objectValue.stringValue("theme")),
        displayTarget = if (includeDeviceFields) enumName<DisplayTarget>(objectValue.stringValue("displayTarget")) else null,
        overlayScale = if (includeDeviceFields) {
            objectValue.doubleValue("overlayScale")?.takeIf(Double::isFinite)?.coerceIn(0.45, 1.0)
        } else {
            null
        },
        battlePollingIntervalMs = if (includeDeviceFields) objectValue.intValue("battlePollingIntervalMs")?.coerceIn(1, 20) else null,
        mapFollowSmoothingPercent = if (includeDeviceFields) objectValue.intValue("mapFollowSmoothingPercent")?.coerceIn(0, 100) else null,
        highVisibilityMapPlayer = if (includeDeviceFields) objectValue.booleanValue("highVisibilityMapPlayer") else null,
    )

    private fun sanitize(settings: CompanionSettings): CompanionSettings {
        val defaults = CompanionSettings()
        return settings.copy(
            fontScale = settings.fontScale.takeIf(Double::isFinite)?.coerceIn(0.85, 1.35) ?: defaults.fontScale,
            ruleset = normalizedRuleset(settings.ruleset) ?: defaults.ruleset,
            overlayScale = settings.overlayScale.takeIf(Double::isFinite)?.coerceIn(0.45, 1.0) ?: defaults.overlayScale,
            battlePollingIntervalMs = settings.battlePollingIntervalMs.coerceIn(1, 20),
            mapFollowSmoothingPercent = settings.mapFollowSmoothingPercent.coerceIn(0, 100),
        )
    }

    private fun parseObject(document: String): JsonObject? = runCatching {
        JsonParser.parseString(document).takeIf { it.isJsonObject }?.asJsonObject
    }.getOrNull()

    private fun JsonObject.objectValue(name: String): JsonObject? =
        get(name)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.stringValue(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    private fun JsonObject.booleanValue(name: String): Boolean? =
        get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean

    private fun JsonObject.doubleValue(name: String): Double? =
        get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.let {
            runCatching { it.asDouble }.getOrNull()
        }

    private fun JsonObject.intValue(name: String): Int? =
        get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.let {
            runCatching { it.asInt }.getOrNull()
        }

    private inline fun <reified T : Enum<T>> enumName(value: String?): String? =
        value?.let { candidate -> enumValues<T>().firstOrNull { it.name.equals(candidate, ignoreCase = true) }?.name }

    private fun normalizedRuleset(value: String?): String? = value?.trim()
        ?.takeIf { it.isNotEmpty() && it.length <= 128 }
        ?.let { if (it.equals("AUTO", ignoreCase = true)) "AUTO" else it }

    private fun validRomSha(value: String?): String? = value?.takeIf(ROM_SHA_PATTERN::matches)

    private data class RepositoryState(
        val globalDefaults: CompanionSettings,
        val romOverrides: LinkedHashMap<String, StoredSettings>,
        val writable: Boolean = true,
        val legacy: Boolean = false,
    )

    private data class StoredDocument(
        val schema: Int,
        val globalDefaults: StoredSettings,
        val romOverrides: Map<String, StoredSettings>,
    )

    private data class StoredSettings(
        val knowledgeMode: String? = null,
        val attackEnabled: Boolean? = null,
        val rarityEnabled: Boolean? = null,
        val movesEnabled: Boolean? = null,
        val fontScale: Double? = null,
        val density: String? = null,
        val highContrast: Boolean? = null,
        val autoOpenTarget: Boolean? = null,
        val ruleset: String? = null,
        val displayMode: String? = null,
        val theme: String? = null,
        val displayTarget: String? = null,
        val overlayScale: Double? = null,
        val battlePollingIntervalMs: Int? = null,
        val mapFollowSmoothingPercent: Int? = null,
        val highVisibilityMapPlayer: Boolean? = null,
    ) {
        fun applyTo(fallback: CompanionSettings): CompanionSettings = CompanionSettings(
            knowledgeMode = knowledgeMode?.let(KnowledgeMode::valueOf) ?: fallback.knowledgeMode,
            attackEnabled = attackEnabled ?: fallback.attackEnabled,
            rarityEnabled = rarityEnabled ?: fallback.rarityEnabled,
            movesEnabled = movesEnabled ?: fallback.movesEnabled,
            fontScale = fontScale ?: fallback.fontScale,
            density = density?.let(Density::valueOf) ?: fallback.density,
            highContrast = highContrast ?: fallback.highContrast,
            autoOpenTarget = autoOpenTarget ?: fallback.autoOpenTarget,
            ruleset = ruleset ?: fallback.ruleset,
            displayMode = displayMode?.let(DisplayMode::valueOf) ?: fallback.displayMode,
            theme = theme?.let(Theme::valueOf) ?: fallback.theme,
            displayTarget = displayTarget?.let(DisplayTarget::valueOf) ?: fallback.displayTarget,
            overlayScale = overlayScale ?: fallback.overlayScale,
            battlePollingIntervalMs = battlePollingIntervalMs ?: fallback.battlePollingIntervalMs,
            mapFollowSmoothingPercent = mapFollowSmoothingPercent ?: fallback.mapFollowSmoothingPercent,
            highVisibilityMapPlayer = highVisibilityMapPlayer ?: fallback.highVisibilityMapPlayer,
        )

        fun isEmpty(): Boolean = this == StoredSettings()

        companion object {
            fun complete(settings: CompanionSettings) = StoredSettings(
                knowledgeMode = settings.knowledgeMode.name,
                attackEnabled = settings.attackEnabled,
                rarityEnabled = settings.rarityEnabled,
                movesEnabled = settings.movesEnabled,
                fontScale = settings.fontScale,
                density = settings.density.name,
                highContrast = settings.highContrast,
                autoOpenTarget = settings.autoOpenTarget,
                ruleset = settings.ruleset,
                displayMode = settings.displayMode.name,
                theme = settings.theme.name,
                displayTarget = settings.displayTarget.name,
                overlayScale = settings.overlayScale,
                battlePollingIntervalMs = settings.battlePollingIntervalMs,
                mapFollowSmoothingPercent = settings.mapFollowSmoothingPercent,
                highVisibilityMapPlayer = settings.highVisibilityMapPlayer,
            )

            fun difference(settings: CompanionSettings, globals: CompanionSettings) = StoredSettings(
                knowledgeMode = settings.knowledgeMode.name.takeIf { settings.knowledgeMode != globals.knowledgeMode },
                attackEnabled = settings.attackEnabled.takeIf { settings.attackEnabled != globals.attackEnabled },
                rarityEnabled = settings.rarityEnabled.takeIf { settings.rarityEnabled != globals.rarityEnabled },
                movesEnabled = settings.movesEnabled.takeIf { settings.movesEnabled != globals.movesEnabled },
                fontScale = settings.fontScale.takeIf { settings.fontScale != globals.fontScale },
                density = settings.density.name.takeIf { settings.density != globals.density },
                highContrast = settings.highContrast.takeIf { settings.highContrast != globals.highContrast },
                autoOpenTarget = settings.autoOpenTarget.takeIf { settings.autoOpenTarget != globals.autoOpenTarget },
                ruleset = settings.ruleset.takeIf { settings.ruleset != globals.ruleset },
                displayMode = settings.displayMode.name.takeIf { settings.displayMode != globals.displayMode },
                theme = settings.theme.name.takeIf { settings.theme != globals.theme },
            )
        }
    }

    private companion object {
        const val SCHEMA = 2
        const val LEGACY_SCHEMA = 1
        const val MAX_ROM_OVERRIDES = 4096
        const val DOCUMENT_KEY = "companion-settings-v1"
        val ROM_SHA_PATTERN = Regex("[0-9a-f]{64}")
    }
}

package com.darkaxt.dualdex.settings

import android.content.SharedPreferences
import com.enrpau.dualscreendex.companion.model.CompanionSettings
import com.enrpau.dualscreendex.companion.model.Density
import com.enrpau.dualscreendex.companion.model.DisplayMode
import com.enrpau.dualscreendex.companion.model.DisplayTarget
import com.enrpau.dualscreendex.companion.model.KnowledgeMode
import com.enrpau.dualscreendex.companion.model.Theme
import com.google.gson.Gson

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

    fun read(): CompanionSettings {
        val stored = runCatching {
            readDocument()?.let { gson.fromJson(it, StoredSettings::class.java) }
        }.getOrNull() ?: return CompanionSettings()
        val defaults = CompanionSettings()
        return CompanionSettings(
            knowledgeMode = enumOr(stored.knowledgeMode, defaults.knowledgeMode),
            attackEnabled = stored.attackEnabled ?: defaults.attackEnabled,
            rarityEnabled = stored.rarityEnabled ?: defaults.rarityEnabled,
            movesEnabled = stored.movesEnabled ?: defaults.movesEnabled,
            fontScale = stored.fontScale?.takeIf(Double::isFinite)?.coerceIn(0.85, 1.35) ?: defaults.fontScale,
            density = enumOr(stored.density, defaults.density),
            highContrast = stored.highContrast ?: defaults.highContrast,
            autoOpenTarget = stored.autoOpenTarget ?: defaults.autoOpenTarget,
            ruleset = stored.ruleset?.trim()?.takeIf { it.isNotEmpty() && it.length <= 128 } ?: defaults.ruleset,
            displayMode = enumOr(stored.displayMode, defaults.displayMode),
            theme = enumOr(stored.theme, defaults.theme),
            displayTarget = enumOr(stored.displayTarget, defaults.displayTarget),
            overlayScale = stored.overlayScale?.takeIf(Double::isFinite)?.coerceIn(0.45, 1.0) ?: defaults.overlayScale,
            thorTopScreenFocus = stored.thorTopScreenFocus ?: defaults.thorTopScreenFocus,
        )
    }

    fun write(settings: CompanionSettings) {
        writeDocument(
            gson.toJson(
                StoredSettings(
                    schema = SCHEMA,
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
                    thorTopScreenFocus = settings.thorTopScreenFocus,
                ),
            ),
        )
    }

    private inline fun <reified T : Enum<T>> enumOr(value: String?, fallback: T): T =
        value?.let { candidate -> enumValues<T>().firstOrNull { it.name.equals(candidate, ignoreCase = true) } }
            ?: fallback

    private data class StoredSettings(
        val schema: Int? = null,
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
        val thorTopScreenFocus: Boolean? = null,
    )

    private companion object {
        const val SCHEMA = 1
        const val DOCUMENT_KEY = "companion-settings-v1"
    }
}

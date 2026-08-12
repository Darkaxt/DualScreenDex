package com.darkaxt.dualdex.settings

import com.enrpau.dualscreendex.companion.model.CompanionSettings
import com.enrpau.dualscreendex.companion.model.Density
import com.enrpau.dualscreendex.companion.model.DisplayMode
import com.enrpau.dualscreendex.companion.model.DisplayTarget
import com.enrpau.dualscreendex.companion.model.KnowledgeMode
import com.enrpau.dualscreendex.companion.model.Theme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRepositoryTest {
    private val romA = "a".repeat(64)
    private val romB = "b".repeat(64)

    @Test
    fun roundTripsEveryUserSetting() {
        var document: String? = null
        val repository = SettingsRepository({ document }, { document = it })
        val settings = CompanionSettings(
            knowledgeMode = KnowledgeMode.HIDDEN,
            attackEnabled = false,
            rarityEnabled = false,
            movesEnabled = false,
            fontScale = 1.25,
            density = Density.COMPACT,
            highContrast = true,
            autoOpenTarget = false,
            ruleset = "modern",
            displayMode = DisplayMode.OVERLAY,
            theme = Theme.DARK,
            displayTarget = DisplayTarget.EXTERNAL,
            overlayScale = 0.65,
            thorTopScreenFocus = true,
        )

        repository.write(settings)

        assertEquals(settings, repository.read())
    }

    @Test
    fun invalidFieldsFallBackIndependentlyWithoutCrashingStartup() {
        val document = """{
          "knowledgeMode":"NOT_A_MODE",
          "fontScale":99,
          "density":"COMPACT",
          "ruleset":"",
          "displayMode":"NOT_A_DISPLAY",
          "theme":"DARK",
          "displayTarget":"NOPE"
        }"""
        val settings = SettingsRepository({ document }, {}).read()

        assertEquals(KnowledgeMode.ORGANIC, settings.knowledgeMode)
        assertEquals(1.35, settings.fontScale, 0.0)
        assertEquals(Density.COMPACT, settings.density)
        assertEquals("AUTO", settings.ruleset)
        assertEquals(DisplayMode.DOCKED, settings.displayMode)
        assertEquals(Theme.DARK, settings.theme)
        assertEquals(DisplayTarget.AUTO, settings.displayTarget)
        assertEquals(1.0, settings.overlayScale, 0.0)
        assertEquals(false, settings.thorTopScreenFocus)
    }

    @Test
    fun clampsPersistedOverlayScaleAndMigratesLegacyDocuments() {
        val legacy = SettingsRepository({ "{}" }, {}).read()
        val invalid = SettingsRepository({ """{"overlayScale":8}""" }, {}).read()

        assertEquals(1.0, legacy.overlayScale, 0.0)
        assertEquals(1.0, invalid.overlayScale, 0.0)
    }

    @Test
    fun storesIndependentRomOverridesWhileDeviceSettingsRemainGlobal() {
        var document: String? = null
        val repository = SettingsRepository({ document }, { document = it })

        repository.writeForRom(
            romA,
            CompanionSettings(
                knowledgeMode = KnowledgeMode.DISCOVERED,
                attackEnabled = false,
                rarityEnabled = true,
                movesEnabled = false,
                fontScale = 1.25,
                density = Density.COMPACT,
                highContrast = true,
                autoOpenTarget = false,
                ruleset = "modern",
                displayMode = DisplayMode.OVERLAY,
                theme = Theme.DARK,
                displayTarget = DisplayTarget.EXTERNAL,
                overlayScale = 0.7,
                thorTopScreenFocus = true,
            ),
        )
        repository.writeForRom(
            romB,
            CompanionSettings(
                knowledgeMode = KnowledgeMode.HIDDEN,
                attackEnabled = true,
                rarityEnabled = false,
                movesEnabled = true,
                fontScale = 0.9,
                density = Density.COMFORTABLE,
                highContrast = false,
                autoOpenTarget = true,
                ruleset = "original",
                displayMode = DisplayMode.DOCKED,
                theme = Theme.LIGHT,
                displayTarget = DisplayTarget.HANDHELD,
                overlayScale = 0.9,
                thorTopScreenFocus = false,
            ),
        )

        val settingsA = repository.readForRom(romA)
        val settingsB = repository.readForRom(romB)
        assertEquals("modern", settingsA.ruleset)
        assertEquals(KnowledgeMode.DISCOVERED, settingsA.knowledgeMode)
        assertEquals(Theme.DARK, settingsA.theme)
        assertFalse(settingsA.attackEnabled)
        assertTrue(settingsA.rarityEnabled)
        assertFalse(settingsA.movesEnabled)
        assertEquals(1.25, settingsA.fontScale, 0.0)
        assertEquals(Density.COMPACT, settingsA.density)
        assertTrue(settingsA.highContrast)
        assertFalse(settingsA.autoOpenTarget)
        assertEquals(DisplayMode.OVERLAY, settingsA.displayMode)
        assertEquals("original", settingsB.ruleset)
        assertEquals(KnowledgeMode.HIDDEN, settingsB.knowledgeMode)
        assertEquals(Theme.LIGHT, settingsB.theme)
        assertTrue(settingsB.attackEnabled)
        assertFalse(settingsB.rarityEnabled)
        assertTrue(settingsB.movesEnabled)
        assertEquals(0.9, settingsB.fontScale, 0.0)
        assertEquals(Density.COMFORTABLE, settingsB.density)
        assertFalse(settingsB.highContrast)
        assertTrue(settingsB.autoOpenTarget)
        assertEquals(DisplayMode.DOCKED, settingsB.displayMode)
        assertEquals(DisplayTarget.HANDHELD, settingsA.displayTarget)
        assertEquals(settingsA.displayTarget, settingsB.displayTarget)
        assertEquals(0.9, settingsA.overlayScale, 0.0)
        assertEquals(settingsA.overlayScale, settingsB.overlayScale, 0.0)
        assertFalse(settingsA.thorTopScreenFocus)
        assertEquals(settingsA.thorTopScreenFocus, settingsB.thorTopScreenFocus)
    }

    @Test
    fun sparseRomOverridesInheritLaterGlobalDefaultChanges() {
        var document: String? = null
        val repository = SettingsRepository({ document }, { document = it })
        repository.writeGlobal(CompanionSettings(theme = Theme.GAME, fontScale = 1.0))
        repository.writeForRom(romA, repository.readForRom(romA).copy(theme = Theme.DARK))

        repository.writeGlobal(repository.readGlobal().copy(fontScale = 1.25, attackEnabled = false))

        val effective = repository.readForRom(romA)
        assertEquals(Theme.DARK, effective.theme)
        assertEquals(1.25, effective.fontScale, 0.0)
        assertFalse(effective.attackEnabled)
        assertFalse(requireNotNull(document).contains("\"fontScale\":1.0"))
    }

    @Test
    fun rejectsA4097thRomOverrideWithoutDroppingExistingRecords() {
        var document: String? = buildString {
            append("{\"schema\":2,\"globalDefaults\":{},\"romOverrides\":{")
            repeat(4096) { index ->
                if (index > 0) append(',')
                append('"')
                append(index.toString(16).padStart(64, '0'))
                append("\":{\"theme\":\"DARK\"}")
            }
            append("}}")
        }
        val repository = SettingsRepository({ document }, { document = it })
        val before = requireNotNull(document)

        assertThrows(IllegalStateException::class.java) {
            repository.writeForRom("f".repeat(64), CompanionSettings(theme = Theme.LIGHT))
        }

        assertEquals(before, document)
        assertEquals(Theme.DARK, repository.readForRom("0".repeat(64)).theme)
    }

    @Test
    fun migratesOnlyTheLegacyManualRulesetToTheLastValidRom() {
        var writes = 0
        var document: String? = """{
          "schema":1,
          "knowledgeMode":"HIDDEN",
          "attackEnabled":false,
          "fontScale":1.25,
          "theme":"DARK",
          "displayTarget":"EXTERNAL",
          "overlayScale":0.65,
          "thorTopScreenFocus":true,
          "ruleset":"modern"
        }"""
        val repository = SettingsRepository({ document }, { document = it; writes += 1 })

        repository.migrateLegacyRuleset(romA)

        val global = repository.readGlobal()
        val lastRom = repository.readForRom(romA)
        val otherRom = repository.readForRom(romB)
        assertEquals("AUTO", global.ruleset)
        assertEquals("modern", lastRom.ruleset)
        assertEquals("AUTO", otherRom.ruleset)
        assertEquals(KnowledgeMode.HIDDEN, global.knowledgeMode)
        assertFalse(global.attackEnabled)
        assertEquals(1.25, global.fontScale, 0.0)
        assertEquals(Theme.DARK, global.theme)
        assertEquals(DisplayTarget.EXTERNAL, global.displayTarget)
        assertEquals(0.65, global.overlayScale, 0.0)
        assertTrue(global.thorTopScreenFocus)
        assertEquals(global.copy(ruleset = "modern"), lastRom)
        val migratedDocument = document

        repository.migrateLegacyRuleset(romA)

        assertEquals(1, writes)
        assertEquals(migratedDocument, document)
    }

    @Test
    fun defersLegacyManualRulesetMigrationUntilTheLastRomHashIsValid() {
        var document: String? = """{"schema":1,"ruleset":"modern","theme":"DARK"}"""
        val repository = SettingsRepository({ document }, { document = it })
        val legacyDocument = document

        repository.migrateLegacyRuleset(null)
        repository.migrateLegacyRuleset("NOT-A-SHA")

        assertEquals(legacyDocument, document)
        assertEquals("modern", repository.readGlobal().ruleset)

        repository.migrateLegacyRuleset(romA)

        assertEquals("AUTO", repository.readGlobal().ruleset)
        assertEquals("modern", repository.readForRom(romA).ruleset)
    }

    @Test
    fun ignoresMalformedHashesAndOverrideFieldsIndependently() {
        val document = """{
          "schema":2,
          "globalDefaults":{"knowledgeMode":"HIDDEN","fontScale":1.2},
          "romOverrides":{
            "$romA":{"theme":"DARK","fontScale":"not-a-number","attackEnabled":false},
            "$romB":{"knowledgeMode":"DISCOVERED","ruleset":""},
            "not-a-sha":{"theme":"LIGHT"}
          }
        }"""
        val repository = SettingsRepository({ document }, {})

        assertEquals(Theme.DARK, repository.readForRom(romA).theme)
        assertEquals(1.2, repository.readForRom(romA).fontScale, 0.0)
        assertFalse(repository.readForRom(romA).attackEnabled)
        assertEquals(KnowledgeMode.DISCOVERED, repository.readForRom(romB).knowledgeMode)
        assertEquals("AUTO", repository.readForRom(romB).ruleset)
        assertEquals(Theme.GAME, repository.readForRom("not-a-sha").theme)
    }

    @Test
    fun firstValidRomWriteCompletesADeferredLegacyManualMigration() {
        var document: String? = """{"schema":1,"ruleset":"modern","theme":"DARK"}"""
        val repository = SettingsRepository({ document }, { document = it })
        repository.migrateLegacyRuleset(null)

        repository.writeForRom(
            romA,
            repository.readForRom(romA).copy(attackEnabled = false),
        )

        assertEquals("AUTO", repository.readGlobal().ruleset)
        assertEquals("modern", repository.readForRom(romA).ruleset)
        assertFalse(repository.readForRom(romA).attackEnabled)
        assertEquals("AUTO", repository.readForRom(romB).ruleset)
        assertEquals(Theme.DARK, repository.readForRom(romB).theme)
    }

    @Test
    fun globalWriteBeforeFirstCatalogKeepsLegacyManualRulesetMigratable() {
        var document: String? = """{"schema":1,"ruleset":"modern","theme":"DARK"}"""
        val repository = SettingsRepository({ document }, { document = it })
        repository.migrateLegacyRuleset(null)

        repository.writeGlobal(repository.readGlobal().copy(overlayScale = 0.7))
        repository.migrateLegacyRuleset(romA)

        assertEquals("AUTO", repository.readGlobal().ruleset)
        assertEquals(0.7, repository.readGlobal().overlayScale, 0.0)
        assertEquals("modern", repository.readForRom(romA).ruleset)
        assertEquals("AUTO", repository.readForRom(romB).ruleset)
    }

    @Test
    fun neverReadsMigratesOrOverwritesAnUnsupportedFutureSchema() {
        var document: String? = """{
          "schema":3,
          "globalDefaults":{"theme":"DARK","ruleset":"future"},
          "romOverrides":{"$romA":{"theme":"LIGHT"}}
        }"""
        val repository = SettingsRepository({ document }, { document = it })
        val futureDocument = document

        assertEquals(CompanionSettings(), repository.readGlobal())
        assertEquals(CompanionSettings(), repository.readForRom(romA))
        repository.migrateLegacyRuleset(romA)
        repository.writeGlobal(CompanionSettings(theme = Theme.LIGHT))
        repository.writeForRom(romA, CompanionSettings(theme = Theme.DARK))

        assertEquals(futureDocument, document)
    }

    @Test
    fun oversizedSchemaTwoDocumentIsEntirelyReadOnlyAndNeverTruncated() {
        val overrides = (0..4096).joinToString(",") { index ->
            val sha = index.toString(16).padStart(64, '0')
            "\"$sha\":{\"theme\":\"LIGHT\"}"
        }
        var document: String? = """{"schema":2,"globalDefaults":{"theme":"DARK"},"romOverrides":{$overrides}}"""
        val repository = SettingsRepository({ document }, { document = it })
        val oversizedDocument = document

        assertEquals(CompanionSettings(), repository.readGlobal())
        assertEquals(CompanionSettings(), repository.readForRom("0".repeat(64)))
        repository.migrateLegacyRuleset(romA)
        repository.writeGlobal(CompanionSettings(theme = Theme.LIGHT))
        repository.writeForRom(romA, CompanionSettings(theme = Theme.DARK))

        assertEquals(oversizedDocument, document)
    }
}

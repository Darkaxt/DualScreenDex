package com.darkaxt.dualdex.web

import com.darkaxt.dualdex.battle.BattleEncounterKind
import com.darkaxt.dualdex.battle.BattleMemorySample
import com.darkaxt.dualdex.battle.BattleMonSnapshot
import com.darkaxt.dualdex.battle.BattleTarget
import com.darkaxt.dualdex.battle.BattleTrackingUpdate
import com.darkaxt.dualdex.battle.Gen3LiveMemoryReader
import com.darkaxt.dualdex.battle.LiveBattleState
import com.darkaxt.dualdex.live.TransientGameStateContext
import com.darkaxt.dualdex.live.UnifiedGameStateDecoder
import com.darkaxt.dualdex.battle.ResolvedBattleLayout
import com.darkaxt.dualdex.battle.TargetMode
import com.darkaxt.dualdex.catalog.CatalogCache
import com.darkaxt.dualdex.catalog.CatalogDatabase
import com.darkaxt.dualdex.catalog.CatalogDatabaseFactory
import com.darkaxt.dualdex.catalog.CatalogRow
import com.darkaxt.dualdex.catalog.CatalogSourceMetadata
import com.darkaxt.dualdex.catalog.CatalogWriteProgress
import com.darkaxt.dualdex.save.SaveParseContext
import com.darkaxt.dualdex.save.SaveSnapshot
import com.darkaxt.dualdex.save.OwnedIndividual
import com.darkaxt.dualdex.save.gen3.Gen3Checksums
import com.darkaxt.dualdex.save.gen3.Gen3PokedexCodec
import com.enrpau.dualscreendex.companion.api.SaveRamView
import com.enrpau.dualscreendex.parser.catalog.CatalogParser
import com.enrpau.dualscreendex.parser.detect.RomHeaderReader
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.sprite.PngEncoder
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.sql.Connection
import java.sql.DriverManager
import java.util.Comparator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class OfficialEmeraldPlayerStateRealControlTest {
    @Test
    fun officialEmeraldSurvivesPersistenceAndPublishesPrivatePlayerState() {
        val configured = System.getenv("DUALDEX_OFFICIAL_EMERALD_ROM")
        assumeTrue("set DUALDEX_OFFICIAL_EMERALD_ROM to run this real-ROM regression", !configured.isNullOrBlank())
        val romPath = Path.of(requireNotNull(configured))
        assumeTrue("official Emerald ROM does not exist: $romPath", Files.isRegularFile(romPath))
        val rom = RomImage(Files.readAllBytes(romPath))
        assertEquals(OFFICIAL_EMERALD_SHA256, rom.sha256)

        val parsed = requireNotNull(CatalogParser.parse(rom).catalog)
        val root = newTemporaryRoot()
        try {
            val cache = CatalogCache(root.toFile(), TestJdbcCatalogDatabaseFactory)
            val source = CatalogSourceMetadata.direct(
                displayName = romPath.fileName.toString(),
                romSize = rom.size,
                romTitle = RomHeaderReader.read(rom).title,
            )
            cache.write(parsed, source, CatalogWriteProgress.complete())
            val reopened = requireNotNull(cache.readComplete(rom.sha256)).catalog

            val liveState = UnifiedGameStateDecoder()
            ProductionCompanionRuntime(catalogRepository = cache, transientGameState = liveState).use { runtime ->
                assertTrue(runtime.restoreCatalog(rom.sha256))
                assertEquals(reopened.romSha256, runtime.catalogHash())

                val context = requireNotNull(runtime.saveParseContext())
                val layout = requireNotNull(runtime.battleCatalogContext()?.gen3RuntimeMemoryLayout)
                val saveAbi = requireNotNull(context.gen3SaveRuntimeAbi)
                val saveBlock1 = ByteArray(saveAbi.saveBlock1Size)
                val saveBlock2 = ByteArray(saveAbi.saveBlock2Size)
                writeSanitizedTrainer(saveBlock1, saveBlock2, context)
                writeSanitizedPokedex(saveBlock2, context)
                assertEquals(203, context.speciesById[1]?.dexNumber)
                assertEquals(1, context.speciesById[1]?.pokedexFlagNumber)
                val pokedexControl = requireNotNull(
                    Gen3PokedexCodec.decode(
                        saveBlock2,
                        context,
                        listOf(OwnedIndividual("party-0", speciesId = 1)),
                    ).value,
                )
                assertEquals(0x28, pokedexControl.ownedOffset)
                assertEquals((1..8).toSet(), pokedexControl.caughtDexNumbers)
                assertEquals((1..15).toSet(), pokedexControl.seenDexNumbers)
                saveBlock1[layout.saveBlock1MapGroupOffset] = 0
                saveBlock1[layout.saveBlock1MapNumberOffset] = 9
                val party = ByteArray(requireNotNull(layout.playerPartyCapacity) * requireNotNull(layout.playerPartyRecordSize))
                writeSanitizedPartyRecord(context).copyInto(party)

                val battleUpdate = unobservedEnemyMove(context)
                val battleContext = requireNotNull(runtime.battleCatalogContext())
                liveState.beginSession(
                    TransientGameStateContext(
                        romIdentity = rom.sha256,
                        generation = 3,
                        catalog = battleContext.catalog,
                        gen3RuntimeMemoryLayout = layout,
                        saveParseContext = context,
                    ),
                )
                liveState.acceptGen3LiveSample(
                    sampleId = 1,
                    regions = mapOf(
                        Gen3LiveMemoryReader.SAVE_BLOCK1_ID to saveBlock1,
                        Gen3LiveMemoryReader.SAVE_BLOCK2_ID to saveBlock2,
                        Gen3LiveMemoryReader.PARTY_COUNT_ID to byteArrayOf(1),
                        Gen3LiveMemoryReader.PARTY_ID to party,
                    ),
                    battle = LiveBattleState(
                        active = true,
                        sample = battleUpdate.sample,
                        encounterKind = battleUpdate.sample?.encounterKind ?: BattleEncounterKind.UNKNOWN,
                    ),
                    areaBaseId = null,
                    mapPosition = null,
                    trackingUpdate = battleUpdate,
                )

                val stateJson = JsonParser.parseString(Gson().toJson(runtime.stateView())).asJsonObject
                val trainer = stateJson.getAsJsonObject("trainer")
                assertEquals("MAY", trainer.get("name").asString)
                assertEquals(0x5678, trainer.get("publicTrainerId").asInt)
                assertEquals(12_345L, trainer.get("money").asLong)
                assertEquals(15, trainer.get("dexSeen").asInt)
                assertEquals(8, trainer.get("dexCaught").asInt)
                assertTrue(trainer.get("avatarUrl").asString.startsWith("/api/trainer-assets/"))
                assertEquals(8, trainer.getAsJsonArray("badges").size())

                val partyJson = stateJson.getAsJsonArray("party")
                assertEquals(6, partyJson.size())
                val lead = partyJson[0].asJsonObject
                assertTrue(lead.get("occupied").asBoolean)
                assertEquals(1, lead.get("speciesId").asInt)
                assertEquals("BULBASAUR", lead.get("speciesName").asString)
                assertEquals("BULBASAUR", lead.get("nickname").asString)
                assertEquals(5, lead.get("level").asInt)

                val opponentMoves = stateJson.getAsJsonObject("battle")
                    .getAsJsonArray("opponents")[0].asJsonObject
                    .getAsJsonArray("moves")
                assertEquals(0, opponentMoves.size())
                assertFalse(stateJson.getAsJsonObject("observedMoves").has(ENEMY_SPECIES_ID.toString()))
                assertTrue(forbiddenRuntimeKeys(stateJson).isEmpty())

                val pngHashes = reopened.trainerAssets.assets.mapValues { (_, sprite) ->
                    sha256(PngEncoder.encode(sprite))
                }
                assertEquals(EXPECTED_PNG_HASHES, pngHashes)
            }
        } finally {
            deleteTree(root)
        }
    }

    @Test
    fun officialGamesWithoutPlayerDescriptorsKeepTheirExistingCompanionFeatures() {
        OFFICIAL_UNSUPPORTED_PLAYER_CONTROLS.forEach { control ->
            val configured = System.getenv(control.environmentVariable)
            assumeTrue("set ${control.environmentVariable} to run this real-ROM regression", !configured.isNullOrBlank())
            val path = Path.of(requireNotNull(configured))
            assumeTrue("official ROM does not exist: $path", Files.isRegularFile(path))
            val rom = RomImage(Files.readAllBytes(path))
            assertEquals(control.sha256, rom.sha256)
            val catalog = requireNotNull(CatalogParser.parse(rom).catalog)
            assertTrue("${control.environmentVariable} lost species", catalog.speciesById.isNotEmpty())
            assertTrue("${control.environmentVariable} lost its world map", catalog.worldMaps.regions.isNotEmpty())
            assertNull(
                "${control.environmentVariable} unexpectedly gained a Gen III player descriptor",
                catalog.runtimeMetadata.gen3RuntimeMemoryLayout?.saveRuntimeAbi,
            )

            ProductionCompanionRuntime().use { runtime ->
                runtime.loadCatalog(path.fileName.toString(), catalog)
                val initial = runtime.stateView()
                assertTrue(initial.catalogReady)
                assertNull(initial.trainer)
                assertTrue(initial.party.none { it.occupied })
                assertNotNull("${control.environmentVariable} lost battle catalog data", runtime.battleCatalogContext())

                val species = catalog.speciesById.values.first { it.id > 0 }
                assertTrue(
                    runtime.applySaveSnapshot(
                        SaveSnapshot(
                            romIdentity = rom.sha256,
                            saveIdentity = "sanitized-${control.generation}",
                            saveGeneration = control.generation,
                            saveCounter = 1,
                            currentArea = null,
                            seenDexNumbers = emptySet(),
                            caughtDexNumbers = emptySet(),
                            party = emptyList(),
                            storedIndividuals = listOf(OwnedIndividual("box-0", species.id, level = 5)),
                            capabilities = emptyMap(),
                        ),
                        SaveRamView(status = "MATCHED"),
                    ),
                )
                assertTrue(runtime.stateView().speciesState.getValue(species.id).caught)
                assertNull(runtime.stateView().trainer)
                assertTrue(runtime.stateView().party.none { it.occupied })
            }
        }
    }

    private fun writeSanitizedTrainer(
        saveBlock1: ByteArray,
        saveBlock2: ByteArray,
        context: SaveParseContext,
    ) {
        val trainer = requireNotNull(context.gen3SaveRuntimeAbi).trainer
        intArrayOf(0xC7, 0xBB, 0xD3, 0xFF).forEachIndexed { index, value ->
            saveBlock2[trainer.playerNameOffset + index] = value.toByte()
        }
        saveBlock2[trainer.genderOffset] = 1
        saveBlock2.putU32le(trainer.trainerIdOffset, 0x1234_5678)
        saveBlock2.putU16le(trainer.playTimeHoursOffset, 25)
        saveBlock2[trainer.playTimeMinutesOffset] = 17
        saveBlock2.putU32le(requireNotNull(trainer.encryptionKeyOffset), ENCRYPTION_KEY)
        saveBlock1.putU32le(trainer.moneyOffset, 12_345L xor ENCRYPTION_KEY)
        trainer.badgeFlags.first().let { saveBlock1[it.byteOffset] = it.mask.toByte() }
        trainer.badgeFlags.last().let { flag ->
            saveBlock1[flag.byteOffset] = (saveBlock1[flag.byteOffset].toInt() or flag.mask).toByte()
        }
    }

    private fun writeSanitizedPokedex(
        saveBlock2: ByteArray,
        context: SaveParseContext,
    ) {
        val flagBytes = (context.internalSpeciesCount + 7) / 8
        val ownedOffset = 0x28
        val seenOffset = ownedOffset + flagBytes
        fun setFlag(offset: Int, dexNumber: Int) {
            val index = dexNumber - 1
            saveBlock2[offset + index / 8] =
                (saveBlock2[offset + index / 8].toInt() or (1 shl (index % 8))).toByte()
        }
        (1..8).forEach { dexNumber -> setFlag(ownedOffset, dexNumber) }
        (1..15).forEach { dexNumber -> setFlag(seenOffset, dexNumber) }
    }

    private fun writeSanitizedPartyRecord(context: SaveParseContext): ByteArray {
        require(1 in context.speciesById)
        val personality = 24L
        val otId = 0x1020_3040L
        val record = ByteArray(100)
        record.putU32le(0, personality)
        record.putU32le(4, otId)
        intArrayOf(0xBC, 0xCF, 0xC6, 0xBC, 0xBB, 0xCD, 0xBB, 0xCF, 0xCC, 0xFF)
            .forEachIndexed { index, value -> record[8 + index] = value.toByte() }
        record[19] = 0x02

        val decrypted = ByteArray(48)
        decrypted.putU16le(0, 1)
        decrypted[9] = 70
        decrypted.putU16le(38, 4 shl 11)
        var ivWord = 0L
        List(6) { 24 }.forEachIndexed { index, iv -> ivWord = ivWord or (iv.toLong() shl (index * 5)) }
        decrypted.putU32le(40, ivWord)
        record.putU16le(28, Gen3Checksums.pokemon(decrypted))
        val key = personality xor otId
        repeat(12) { index -> record.putU32le(32 + index * 4, decrypted.u32le(index * 4) xor key) }

        record.putU32le(80, 0)
        record[84] = 5
        record.putU16le(86, 20)
        intArrayOf(20, 11, 10, 12, 13, 12).forEachIndexed { index, value ->
            record.putU16le(88 + index * 2, value)
        }
        return record
    }

    private fun unobservedEnemyMove(context: SaveParseContext): BattleTrackingUpdate {
        val types = context.speciesById.getValue(ENEMY_SPECIES_ID).let { species ->
            // The battle projection only needs a non-empty, catalog-valid type list for this privacy control.
            listOfNotNull(species.dexNumber?.let { 0 })
        }.ifEmpty { listOf(0) }
        val opponent = BattleMonSnapshot(
            battlerIndex = 1,
            position = 1,
            speciesId = ENEMY_SPECIES_ID,
            level = 5,
            hp = 18,
            maxHp = 18,
            ivs = List(6) { 20 },
            moves = listOf(ENEMY_RAW_MOVE_ID, 0, 0, 0),
            pp = listOf(10, 0, 0, 0),
            typeIds = types,
            abilityId = 0,
            personality = 99,
        )
        return BattleTrackingUpdate(
            active = true,
            sample = BattleMemorySample(
                layout = ResolvedBattleLayout(0x143C, 0x1420, 0x142C, 0x16EE, 0x1874, 0x1878, 2),
                battlers = listOf(opponent),
                opponents = listOf(opponent),
                selectedMoveId = null,
                target = BattleTarget(0, TargetMode.AUTOMATIC),
                capabilities = emptyMap(),
                encounterKind = BattleEncounterKind.TRAINER,
            ),
            observations = emptyMap(),
        )
    }

    private fun forbiddenRuntimeKeys(root: JsonElement): List<String> = buildList {
        fun visit(element: JsonElement) {
            when {
                element.isJsonArray -> element.asJsonArray.forEach(::visit)
                element.isJsonObject -> element.asJsonObject.entrySet().forEach { (key, value) ->
                    if (
                        key.contains("address", ignoreCase = true) ||
                        key.contains("offset", ignoreCase = true) ||
                        key.contains("encryption", ignoreCase = true)
                    ) add(key)
                    visit(value)
                }
            }
        }
        visit(root)
    }

    private fun newTemporaryRoot(): Path {
        val preferred = Path.of("D:\\Temp")
        val parent = preferred.takeIf(Files::isDirectory) ?: Path.of(System.getProperty("java.io.tmpdir"))
        return Files.createTempDirectory(parent, "dualdex-official-emerald-")
    }

    private fun deleteTree(root: Path) {
        if (!Files.exists(root)) return
        Files.walk(root).use { stream -> stream.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun ByteArray.putU16le(offset: Int, value: Int) {
        this[offset] = value.toByte()
        this[offset + 1] = (value ushr 8).toByte()
    }

    private fun ByteArray.putU32le(offset: Int, value: Long) {
        repeat(4) { index -> this[offset + index] = (value ushr (index * 8)).toByte() }
    }

    private fun ByteArray.u32le(offset: Int): Long = (0 until 4).fold(0L) { value, index ->
        value or ((this[offset + index].toLong() and 0xFF) shl (index * 8))
    }

    private companion object {
        const val OFFICIAL_EMERALD_SHA256 =
            "a9dec84dfe7f62ab2220bafaef7479da0929d066ece16a6885f6226db19085af"
        const val ENCRYPTION_KEY = 0x1357_2468L
        const val ENEMY_SPECIES_ID = 19
        const val ENEMY_RAW_MOVE_ID = 165
        val OFFICIAL_UNSUPPORTED_PLAYER_CONTROLS = listOf(
            OfficialControl(
                "DUALDEX_OFFICIAL_RED_ROM",
                "5ca7ba01642a3b27b0cc0b5349b52792795b62d3ed977e98a09390659af96b7b",
                1,
            ),
            OfficialControl(
                "DUALDEX_OFFICIAL_CRYSTAL_ROM",
                "fdcc3c8c43813cf8731fc037d2a6d191bac75439c34b24ba1c27526e6acdc8a2",
                2,
            ),
        )
        val EXPECTED_PNG_HASHES = mapOf(
            "trainer/avatar/male" to "688c1ea6cf2b73e1a9a5115ceb6d10c2127a0e1643cfce9414baf4658dbad9af",
            "trainer/avatar/female" to "c40402e0ba706a5f5798cbc1d45325ec71a9a0ca45bf299c3da84fea3aef61e7",
            "trainer/overworld/male" to "8dcda636ca5661447359ea9c73c4788d8c5e42c5b6abb7128121d5a8acc61d52",
            "trainer/overworld/female" to "e14b1195027d48d225eadb2ccedabc2ef98b1446f9d68f4bc515e70ae888b7c1",
            "trainer/badge/1" to "53cd36249220ab4e55e108265a484f736dc3859bce80a39c826f934f86aba25d",
            "trainer/badge/2" to "dd72cc943dc7fa61f6d12e023dd5dc32d060b86fad517b7218735fd50b0e8734",
            "trainer/badge/3" to "5d8dac2e1a1e015d0e7dfcedaa8e0dce5861aa8aa74d003a57409cabb902a5a0",
            "trainer/badge/4" to "c923b0e28e33e6a35b43065648d9cfc0469c6ca1e97ccb4da406427e68c78be2",
            "trainer/badge/5" to "08654cc550b5b8079f897f1eb38163657002b30f10aa456f7f505e325da1418f",
            "trainer/badge/6" to "c8d41e18b78e8aef18484ff1d3126ddfdc7258bdac3a36e905d9268437507f86",
            "trainer/badge/7" to "c035d8baa56b6ea15519168a5bd0004f56964cb180363e7c7c632f9385994dbd",
            "trainer/badge/8" to "f4f9aa2db00a77803535e4a4d15dc397f2b8c610b794a2c28bbca725efa8ba4a",
        )
    }

    private data class OfficialControl(
        val environmentVariable: String,
        val sha256: String,
        val generation: Int,
    )

    private object TestJdbcCatalogDatabaseFactory : CatalogDatabaseFactory {
        override fun open(file: File): CatalogDatabase {
            Class.forName("org.sqlite.JDBC")
            return TestJdbcCatalogDatabase(DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}"))
        }
    }

    private class TestJdbcCatalogDatabase(private val connection: Connection) : CatalogDatabase {
        override fun <T> transaction(block: () -> T): T {
            val original = connection.autoCommit
            connection.autoCommit = false
            return try {
                block().also { connection.commit() }
            } catch (failure: Throwable) {
                connection.rollback()
                throw failure
            } finally {
                connection.autoCommit = original
            }
        }

        override fun execute(sql: String, arguments: List<Any?>) {
            connection.prepareStatement(sql).use { statement ->
                arguments.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
                statement.executeUpdate()
            }
        }

        override fun <T> query(
            sql: String,
            arguments: List<Any?>,
            map: (CatalogRow) -> T,
        ): List<T> = connection.prepareStatement(sql).use { statement ->
            arguments.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) {
                        add(map(object : CatalogRow {
                            override fun string(column: String): String? = result.getString(column)
                            override fun long(column: String): Long? = result.getLong(column).takeUnless { result.wasNull() }
                            override fun bytes(column: String): ByteArray? = result.getBytes(column)
                        }))
                    }
                }
            }
        }

        override fun <T> streamQuery(
            sql: String,
            arguments: List<Any?>,
            consume: (com.darkaxt.dualdex.catalog.CatalogRows) -> T,
        ): T = connection.prepareStatement(sql).use { statement ->
            arguments.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
            statement.executeQuery().use { result ->
                consume(com.darkaxt.dualdex.catalog.CatalogRows {
                    if (!result.next()) null else object : CatalogRow {
                        override fun string(column: String): String? = result.getString(column)
                        override fun long(column: String): Long? = result.getLong(column).takeUnless { result.wasNull() }
                        override fun bytes(column: String): ByteArray? = result.getBytes(column)
                    }
                })
            }
        }

        override fun close() = connection.close()
    }
}

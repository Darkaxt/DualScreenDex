package com.darkaxt.dualdex.save.gen3

import com.darkaxt.dualdex.save.BagEntry
import com.darkaxt.dualdex.save.BagPocket
import com.darkaxt.dualdex.save.BagPocketSnapshot
import com.darkaxt.dualdex.save.TrainerIdentity
import com.darkaxt.dualdex.save.TrainerSnapshot

data class SaveSectionResult<T>(
    val value: T? = null,
    val reasons: List<String> = emptyList(),
) {
    init {
        require((value == null) != reasons.isEmpty()) {
            "an available save section has a value; an unavailable section has reasons"
        }
    }

    companion object {
        fun <T> available(value: T): SaveSectionResult<T> = SaveSectionResult(value = value)
        fun <T> unavailable(reason: String): SaveSectionResult<T> = SaveSectionResult(reasons = listOf(reason))
    }
}

data class Gen3PlayerStateResult(
    val trainer: SaveSectionResult<TrainerSnapshot>,
    val bag: Map<BagPocket, SaveSectionResult<BagPocketSnapshot>>,
)

object Gen3PlayerStateCodec {
    fun decodeIdentity(
        saveBlock2: ByteArray,
        abi: Gen3SaveRuntimeAbi,
    ): SaveSectionResult<TrainerIdentity> = runCatching {
        require(saveBlock2.size >= abi.saveBlock2Size) { "declared Gen III SaveBlock2 was incomplete" }
        val layout = abi.trainer
        val name = requireNotNull(
            Gen3SaveTextCodec.decode(
                saveBlock2.copyOfRange(layout.playerNameOffset, layout.playerNameOffset + layout.playerNameLength),
                abi.textEncoding,
            ),
        ) { "player name did not terminate in the declared encoding" }
        val gender = saveBlock2[layout.genderOffset].toInt() and 0xFF
        require(gender in 0..1) { "player gender was outside the declared domain" }
        SaveSectionResult.available(TrainerIdentity(name, gender))
    }.getOrElse { error ->
        SaveSectionResult.unavailable(error.message ?: "live trainer identity was invalid")
    }

    fun decode(
        saveBlock1: ByteArray,
        saveBlock2: ByteArray,
        abi: Gen3SaveRuntimeAbi,
        dexSeen: Int,
        dexCaught: Int,
        extendedSaveData: ByteArray? = null,
    ): Gen3PlayerStateResult {
        val blocksComplete = saveBlock1.size >= abi.saveBlock1Size && saveBlock2.size >= abi.saveBlock2Size
        if (!blocksComplete) {
            return Gen3PlayerStateResult(
                trainer = SaveSectionResult.unavailable("declared Gen III save blocks were incomplete"),
                bag = BagPocket.entries.associateWith {
                    SaveSectionResult.unavailable("declared Gen III save blocks were incomplete")
                },
            )
        }
        val encryptionKey = saveBlock2.u32le(abi.trainer.encryptionKeyOffset)
        return Gen3PlayerStateResult(
            trainer = decodeTrainer(saveBlock1, saveBlock2, abi, encryptionKey, dexSeen, dexCaught),
            bag = decodeBag(saveBlock1, extendedSaveData, abi, encryptionKey),
        )
    }

    private fun decodeTrainer(
        saveBlock1: ByteArray,
        saveBlock2: ByteArray,
        abi: Gen3SaveRuntimeAbi,
        encryptionKey: Long,
        dexSeen: Int,
        dexCaught: Int,
    ): SaveSectionResult<TrainerSnapshot> = runCatching {
        val layout = abi.trainer
        val identityResult = decodeIdentity(saveBlock2, abi)
        val identity = requireNotNull(identityResult.value) { identityResult.reasons.joinToString() }
        val publicTrainerId = saveBlock2.u16le(layout.trainerIdOffset)
        val playTimeHours = saveBlock2.u16le(layout.playTimeHoursOffset)
        val playTimeMinutes = saveBlock2[layout.playTimeMinutesOffset].toInt() and 0xFF
        require(playTimeMinutes in 0..59) { "play time minutes were invalid" }
        val money = saveBlock1.u32le(layout.moneyOffset) xor encryptionKey
        require(money <= layout.maximumMoney) { "decrypted money exceeded the declared maximum" }
        val badgeFlags = layout.badgeFlags.foldIndexed(0) { index, result, flag ->
            val set = saveBlock1[flag.byteOffset].toInt() and flag.mask != 0
            result or if (set) 1 shl index else 0
        }
        SaveSectionResult.available(
            TrainerSnapshot(
                name = identity.name,
                gender = identity.gender,
                publicTrainerId = publicTrainerId,
                money = money,
                playTimeHours = playTimeHours,
                playTimeMinutes = playTimeMinutes,
                badgeFlags = badgeFlags,
                dexSeen = dexSeen,
                dexCaught = dexCaught,
                stars = null,
            ),
        )
    }.getOrElse { error ->
        SaveSectionResult.unavailable(error.message ?: "Trainer Card fields were invalid")
    }

    private fun decodeBag(
        saveBlock1: ByteArray,
        extendedSaveData: ByteArray?,
        abi: Gen3SaveRuntimeAbi,
        encryptionKey: Long,
    ): Map<BagPocket, SaveSectionResult<BagPocketSnapshot>> {
        val layouts = abi.bag.pockets.associateBy(Gen3BagPocketAbi::pocket)
        return BagPocket.entries.associateWith { pocket ->
            val layout = layouts[pocket]
                ?: return@associateWith SaveSectionResult.unavailable("$pocket pocket ABI was absent")
            runCatching {
                val source = when (layout.dataSource) {
                    Gen3BagDataSource.SAVE_BLOCK1 -> saveBlock1
                    Gen3BagDataSource.EXTENDED_SAVE -> requireNotNull(extendedSaveData) {
                        "extended save data was unavailable"
                    }
                }
                val entries = buildList {
                    repeat(layout.capacity) { index ->
                        val offset = layout.byteOffset + index * layout.slotSize
                        val itemId = source.u16le(offset)
                        if (itemId != 0) {
                            val quantity = source.u16le(offset + 2) xor (encryptionKey.toInt() and 0xFFFF)
                            require(quantity > 0) { "$pocket pocket contained an occupied zero-quantity slot" }
                            add(BagEntry(itemId, quantity))
                        }
                    }
                }
                SaveSectionResult.available(BagPocketSnapshot(pocket, entries))
            }.getOrElse { error ->
                SaveSectionResult.unavailable(error.message ?: "$pocket pocket was invalid")
            }
        }
    }
}

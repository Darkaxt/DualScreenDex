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
    ): SaveSectionResult<TrainerIdentity> = Gen3TrainerFieldCodec.decodeIdentity(saveBlock2, abi)

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
        val encryptionKeyResult = Gen3TrainerFieldCodec.decodeEncryptionKey(saveBlock2, abi)
        val encryptionKey = requireNotNull(encryptionKeyResult.value) {
            encryptionKeyResult.reasons.joinToString()
        }
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
        val identityResult = Gen3TrainerFieldCodec.decodeIdentity(saveBlock2, abi)
        val identity = requireNotNull(identityResult.value) { identityResult.reasons.joinToString() }
        val idResult = Gen3TrainerFieldCodec.decodePublicTrainerId(saveBlock2, abi)
        val publicTrainerId = requireNotNull(idResult.value) { idResult.reasons.joinToString() }
        val timeResult = Gen3TrainerFieldCodec.decodePlayTime(saveBlock2, abi)
        val playTime = requireNotNull(timeResult.value) { timeResult.reasons.joinToString() }
        val moneyResult = Gen3TrainerFieldCodec.decodeMoney(saveBlock1, encryptionKey, abi)
        val money = requireNotNull(moneyResult.value) { moneyResult.reasons.joinToString() }
        val badgesResult = Gen3TrainerFieldCodec.decodeBadgeFlags(saveBlock1, abi)
        val badgeFlags = requireNotNull(badgesResult.value) { badgesResult.reasons.joinToString() }
        SaveSectionResult.available(
            TrainerSnapshot(
                name = identity.name,
                gender = identity.gender,
                publicTrainerId = publicTrainerId,
                money = money,
                playTimeHours = playTime.hours,
                playTimeMinutes = playTime.minutes,
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

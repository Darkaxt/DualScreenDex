package com.darkaxt.dualdex.save.gen3

import com.darkaxt.dualdex.save.TrainerIdentity
import com.darkaxt.dualdex.save.TrainerPlayTime

object Gen3TrainerFieldCodec {
    fun decodeIdentity(
        saveBlock2: ByteArray?,
        abi: Gen3SaveRuntimeAbi,
    ): SaveSectionResult<TrainerIdentity> = field("trainer identity") {
        val bytes = completeBlock2(saveBlock2, abi)
        val layout = abi.trainer
        val name = requireNotNull(
            Gen3SaveTextCodec.decode(
                bytes.copyOfRange(layout.playerNameOffset, layout.playerNameOffset + layout.playerNameLength),
                abi.textEncoding,
            ),
        ) { "player name did not terminate in the declared encoding" }
        val gender = bytes[layout.genderOffset].toInt() and 0xFF
        require(gender in 0..1) { "player gender was outside the declared domain" }
        TrainerIdentity(name, gender)
    }

    fun decodePublicTrainerId(
        saveBlock2: ByteArray?,
        abi: Gen3SaveRuntimeAbi,
    ): SaveSectionResult<Int> = field("public Trainer ID") {
        completeBlock2(saveBlock2, abi).u16le(abi.trainer.trainerIdOffset)
    }

    fun decodePlayTime(
        saveBlock2: ByteArray?,
        abi: Gen3SaveRuntimeAbi,
    ): SaveSectionResult<TrainerPlayTime> = field("play time") {
        val bytes = completeBlock2(saveBlock2, abi)
        val layout = abi.trainer
        TrainerPlayTime(
            hours = bytes.u16le(layout.playTimeHoursOffset),
            minutes = bytes[layout.playTimeMinutesOffset].toInt() and 0xFF,
        )
    }

    fun decodeEncryptionKey(
        saveBlock2: ByteArray?,
        abi: Gen3SaveRuntimeAbi,
    ): SaveSectionResult<Long> = field("save encryption key") {
        val bytes = completeBlock2(saveBlock2, abi)
        abi.trainer.encryptionKeyOffset?.let(bytes::u32le) ?: 0L
    }

    fun decodeMoney(
        saveBlock1: ByteArray?,
        encryptionKey: Long?,
        abi: Gen3SaveRuntimeAbi,
    ): SaveSectionResult<Long> = field("money") {
        val bytes = completeBlock1(saveBlock1, abi)
        val key = requireNotNull(encryptionKey) { "save encryption key was unavailable" }
        val money = bytes.u32le(abi.trainer.moneyOffset) xor key
        require(money <= abi.trainer.maximumMoney) { "decrypted money exceeded the declared maximum" }
        money
    }

    fun decodeBadgeFlags(
        saveBlock1: ByteArray?,
        abi: Gen3SaveRuntimeAbi,
    ): SaveSectionResult<Int> = field("badge flags") {
        val bytes = completeBlock1(saveBlock1, abi)
        abi.trainer.badgeFlags.foldIndexed(0) { index, result, flag ->
            val set = bytes[flag.byteOffset].toInt() and flag.mask != 0
            result or if (set) 1 shl index else 0
        }
    }

    private fun completeBlock1(bytes: ByteArray?, abi: Gen3SaveRuntimeAbi): ByteArray {
        val block = requireNotNull(bytes) { "SaveBlock1 was unavailable" }
        require(block.size >= abi.saveBlock1Size) { "declared Gen III SaveBlock1 was incomplete" }
        return block
    }

    private fun completeBlock2(bytes: ByteArray?, abi: Gen3SaveRuntimeAbi): ByteArray {
        val block = requireNotNull(bytes) { "SaveBlock2 was unavailable" }
        require(block.size >= abi.saveBlock2Size) { "declared Gen III SaveBlock2 was incomplete" }
        return block
    }

    private inline fun <T> field(label: String, decode: () -> T): SaveSectionResult<T> = runCatching {
        SaveSectionResult.available(decode())
    }.getOrElse { error ->
        SaveSectionResult.unavailable(error.message ?: "$label was invalid")
    }
}

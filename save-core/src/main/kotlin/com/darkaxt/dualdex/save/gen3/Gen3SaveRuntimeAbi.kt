package com.darkaxt.dualdex.save.gen3

import com.darkaxt.dualdex.save.BagPocket

enum class Gen3BagDataSource { SAVE_BLOCK1, EXTENDED_SAVE }

data class Gen3BitFlag(
    val byteOffset: Int,
    val mask: Int,
) {
    init {
        require(byteOffset >= 0) { "flag byte offset must not be negative" }
        require(mask in 1..0x80 && mask.countOneBits() == 1) { "flag mask must contain exactly one bit" }
    }
}

data class Gen3TrainerCardAbi(
    val playerNameOffset: Int,
    val playerNameLength: Int,
    val genderOffset: Int,
    val trainerIdOffset: Int,
    val playTimeHoursOffset: Int,
    val playTimeMinutesOffset: Int,
    val encryptionKeyOffset: Int,
    val moneyOffset: Int,
    val maximumMoney: Long,
    val badgeFlags: List<Gen3BitFlag>,
) {
    init {
        require(playerNameLength > 0) { "player name length must be positive" }
        require(maximumMoney >= 0) { "maximum money must not be negative" }
        require(badgeFlags.size <= 8) { "Trainer Card supports at most eight badge flags" }
        require(badgeFlags.distinct().size == badgeFlags.size) { "badge flags must be unique" }
    }
}

data class Gen3BagPocketAbi(
    val pocket: BagPocket,
    val byteOffset: Int,
    val capacity: Int,
    val slotSize: Int = 4,
    val dataSource: Gen3BagDataSource = Gen3BagDataSource.SAVE_BLOCK1,
) {
    init {
        require(byteOffset >= 0) { "bag pocket offset must not be negative" }
        require(capacity > 0) { "bag pocket capacity must be positive" }
        require(slotSize >= 4) { "bag item slots must contain a 16-bit ID and quantity" }
    }
}

data class Gen3BagAbi(val pockets: List<Gen3BagPocketAbi>) {
    init {
        require(pockets.isNotEmpty()) { "bag ABI must contain at least one pocket" }
        require(pockets.map(Gen3BagPocketAbi::pocket).distinct().size == pockets.size) {
            "bag ABI must not define a pocket twice"
        }
    }
}

data class Gen3SaveRuntimeAbi(
    val saveBlock1Size: Int,
    val saveBlock2Size: Int,
    val extendedSaveDataSize: Int = 0,
    val textEncoding: Gen3TextEncoding,
    val trainer: Gen3TrainerCardAbi,
    val bag: Gen3BagAbi,
) {
    init {
        require(saveBlock1Size > 0 && saveBlock2Size > 0 && extendedSaveDataSize >= 0) {
            "save-block sizes must be positive and extended-save size must not be negative"
        }
        requireRange(trainer.playerNameOffset, trainer.playerNameLength, saveBlock2Size, "player name")
        requireRange(trainer.genderOffset, 1, saveBlock2Size, "player gender")
        requireRange(trainer.trainerIdOffset, 4, saveBlock2Size, "trainer ID")
        requireRange(trainer.playTimeHoursOffset, 2, saveBlock2Size, "play time hours")
        requireRange(trainer.playTimeMinutesOffset, 1, saveBlock2Size, "play time minutes")
        requireRange(trainer.encryptionKeyOffset, 4, saveBlock2Size, "encryption key")
        requireRange(trainer.moneyOffset, 4, saveBlock1Size, "money")
        trainer.badgeFlags.forEach { requireRange(it.byteOffset, 1, saveBlock1Size, "badge flag") }
        bag.pockets.forEach { pocket ->
            val limit = when (pocket.dataSource) {
                Gen3BagDataSource.SAVE_BLOCK1 -> saveBlock1Size
                Gen3BagDataSource.EXTENDED_SAVE -> extendedSaveDataSize
            }
            requireRange(pocket.byteOffset, pocket.capacity * pocket.slotSize, limit, "${pocket.pocket} pocket")
        }
    }

    private fun requireRange(offset: Int, length: Int, limit: Int, label: String) {
        require(offset >= 0 && length > 0 && offset.toLong() + length <= limit.toLong()) {
            "$label must fit inside its declared save block"
        }
    }
}

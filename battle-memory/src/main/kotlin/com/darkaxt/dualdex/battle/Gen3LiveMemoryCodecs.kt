package com.darkaxt.dualdex.battle

import com.darkaxt.dualdex.save.BagPocket
import com.darkaxt.dualdex.save.BagPocketSnapshot
import com.darkaxt.dualdex.save.OwnedIndividual
import com.darkaxt.dualdex.save.SaveParseContext
import com.darkaxt.dualdex.save.gen3.Gen3PokedexCodec
import com.darkaxt.dualdex.save.gen3.Gen3PlayerStateCodec
import com.darkaxt.dualdex.save.gen3.Gen3TrainerFieldCodec
import com.darkaxt.dualdex.save.gen3.SaveSectionResult

data class Gen3LivePlayerState(
    val trainer: LiveTrainerState,
    val pokedex: LivePokedexState,
    val bag: Map<BagPocket, LiveValue<BagPocketSnapshot>>,
)

data class Gen3LivePlayerOverview(
    val trainer: LiveTrainerState,
    val pokedex: LivePokedexState,
)

object Gen3LiveMemoryCodecs {
    @Suppress("UNUSED_PARAMETER")
    fun decodePlayer(
        saveBlock1: ByteArray?,
        saveBlock2: ByteArray?,
        extendedSave: ByteArray?,
        context: SaveParseContext,
        liveParty: LiveValue<List<OwnedIndividual>>,
    ): Gen3LivePlayerState {
        val overview = decodePlayerOverview(saveBlock1, saveBlock2, context, liveParty)
        return Gen3LivePlayerState(
            trainer = overview.trainer,
            pokedex = overview.pokedex,
            bag = decodeBag(saveBlock1, saveBlock2, extendedSave, context),
        )
    }

    fun decodePlayerOverview(
        saveBlock1: ByteArray?,
        saveBlock2: ByteArray?,
        context: SaveParseContext,
        liveParty: LiveValue<List<OwnedIndividual>>,
    ): Gen3LivePlayerOverview {
        val abi = context.gen3SaveRuntimeAbi
        val noAbi = LiveUnavailableReason(
            LiveUnavailableCode.UNSUPPORTED_LAYOUT,
            "typed Gen III save runtime ABI was unavailable",
        )
        val identity = abi?.let { Gen3TrainerFieldCodec.decodeIdentity(saveBlock2, it).toLiveValue() }
            ?: LiveValue.Unavailable(noAbi)
        val publicTrainerId = abi?.let { Gen3TrainerFieldCodec.decodePublicTrainerId(saveBlock2, it).toLiveValue() }
            ?: LiveValue.Unavailable(noAbi)
        val playTime = abi?.let { Gen3TrainerFieldCodec.decodePlayTime(saveBlock2, it).toLiveValue() }
            ?: LiveValue.Unavailable(noAbi)
        val encryptionKey = abi?.let { Gen3TrainerFieldCodec.decodeEncryptionKey(saveBlock2, it).value }
        val money = abi?.let { Gen3TrainerFieldCodec.decodeMoney(saveBlock1, encryptionKey, it).toLiveValue() }
            ?: LiveValue.Unavailable(noAbi)
        val badges = abi?.let { Gen3TrainerFieldCodec.decodeBadgeFlags(saveBlock1, it).toLiveValue() }
            ?: LiveValue.Unavailable(noAbi)
        val pokedex = Gen3PokedexCodec.decode(saveBlock2, context, liveParty.valueOrNull())
        val pokedexValue = pokedex.value
        val pokedexUnavailable = pokedex.reasons.joinToString().ifBlank { "Gen III Pokédex flags were unavailable" }
        return Gen3LivePlayerOverview(
            trainer = LiveTrainerState(
                identity = identity,
                publicTrainerId = publicTrainerId,
                money = money,
                playTime = playTime,
                badgeFlags = badges,
                stars = LiveValue.Unavailable(
                    LiveUnavailableReason(
                        LiveUnavailableCode.UNSUPPORTED_LAYOUT,
                        "live Trainer Card stars are not mapped",
                    ),
                ),
            ),
            pokedex = LivePokedexState(
                seenDexNumbers = pokedexValue?.let { LiveValue.Available(it.seenDexNumbers) }
                    ?: unavailable(pokedexUnavailable),
                caughtDexNumbers = pokedexValue?.let { LiveValue.Available(it.caughtDexNumbers) }
                    ?: unavailable(pokedexUnavailable),
            ),
        )
    }

    fun decodeBag(
        saveBlock1: ByteArray?,
        saveBlock2: ByteArray?,
        extendedSave: ByteArray?,
        context: SaveParseContext,
    ): Map<BagPocket, LiveValue<BagPocketSnapshot>> {
        val abi = context.gen3SaveRuntimeAbi
        val noAbi = LiveUnavailableReason(
            LiveUnavailableCode.UNSUPPORTED_LAYOUT,
            "typed Gen III save runtime ABI was unavailable",
        )
        return if (abi != null && saveBlock1 != null && saveBlock2 != null) {
            Gen3PlayerStateCodec.decode(
                saveBlock1,
                saveBlock2,
                abi,
                dexSeen = 0,
                dexCaught = 0,
                extendedSaveData = extendedSave,
            ).bag.mapValues { (_, section) -> section.toLiveValue() }
        } else {
            BagPocket.entries.associateWith { LiveValue.Unavailable(noAbi) }
        }
    }

    private fun <T> SaveSectionResult<T>.toLiveValue(): LiveValue<T> = value?.let { LiveValue.Available(it) }
        ?: unavailable(reasons.joinToString().ifBlank { "live value was unavailable" })

    private fun <T> unavailable(reason: String): LiveValue<T> = LiveValue.Unavailable(
        LiveUnavailableReason(LiveUnavailableCode.INVALID_VALUE, reason),
    )
}

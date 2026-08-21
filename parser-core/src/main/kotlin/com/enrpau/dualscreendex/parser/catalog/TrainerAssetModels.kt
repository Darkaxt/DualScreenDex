package com.enrpau.dualscreendex.parser.catalog

data class TrainerAssetCatalog(
    val avatarAssetKeys: Map<Int, String> = emptyMap(),
    val badgeAssetKeys: List<String> = emptyList(),
    val assets: Map<String, RgbaSprite> = emptyMap(),
) {
    init {
        validate()
    }

    fun validate(): TrainerAssetCatalog = apply {
        if (assets.isEmpty()) {
            require(avatarAssetKeys.isEmpty() && badgeAssetKeys.isEmpty())
            return@apply
        }
        if (avatarAssetKeys.isNotEmpty()) {
            require(avatarAssetKeys.keys == setOf(0, 1)) { "trainer avatars must cover both player genders" }
        }
        if (badgeAssetKeys.isNotEmpty()) {
            require(badgeAssetKeys.size == 8 && badgeAssetKeys.distinct().size == 8) {
                "trainer assets must contain exactly eight distinct badges"
            }
        }
        require(assets.keys == avatarAssetKeys.values.toSet() + badgeAssetKeys) {
            "trainer assets must exactly match the referenced keys"
        }
        avatarAssetKeys.values.forEach { key ->
            require(assets.getValue(key).width == 64 && assets.getValue(key).height == 64)
        }
        badgeAssetKeys.forEach { key ->
            require(assets.getValue(key).width == 16 && assets.getValue(key).height == 16)
        }
    }
}

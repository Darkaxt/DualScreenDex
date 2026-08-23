package com.enrpau.dualscreendex.parser.catalog

data class TrainerAssetCatalog(
    val avatarAssetKeys: Map<Int, String> = emptyMap(),
    val overworldAssetKeys: Map<Int, String> = emptyMap(),
    val badgeAssetKeys: List<String> = emptyList(),
    val assets: Map<String, RgbaSprite> = emptyMap(),
) {
    init {
        validate()
    }

    fun validate(): TrainerAssetCatalog = apply {
        if (assets.isEmpty()) {
            require(avatarAssetKeys.isEmpty() && overworldAssetKeys.isEmpty() && badgeAssetKeys.isEmpty())
            return@apply
        }
        if (avatarAssetKeys.isNotEmpty()) {
            require(avatarAssetKeys.keys == setOf(0, 1)) { "trainer avatars must cover both player genders" }
        }
        if (overworldAssetKeys.isNotEmpty()) {
            require(overworldAssetKeys.keys == setOf(0, 1)) {
                "trainer overworld sprites must cover both player genders"
            }
        }
        if (badgeAssetKeys.isNotEmpty()) {
            require(badgeAssetKeys.size == 8 && badgeAssetKeys.distinct().size == 8) {
                "trainer assets must contain exactly eight distinct badges"
            }
        }
        require(assets.keys == avatarAssetKeys.values.toSet() + overworldAssetKeys.values + badgeAssetKeys) {
            "trainer assets must exactly match the referenced keys"
        }
        avatarAssetKeys.values.forEach { key ->
            require(assets.getValue(key).width == 64 && assets.getValue(key).height == 64)
        }
        overworldAssetKeys.values.forEach { key ->
            val sprite = assets.getValue(key)
            require(sprite.width in setOf(16, 32) && sprite.height == 32) {
                "trainer overworld sprites must retain a supported native GBA size"
            }
        }
        badgeAssetKeys.forEach { key ->
            require(assets.getValue(key).width == 16 && assets.getValue(key).height == 16)
        }
    }
}

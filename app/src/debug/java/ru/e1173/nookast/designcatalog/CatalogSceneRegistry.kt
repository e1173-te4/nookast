package ru.e1173.nookast.designcatalog

/**
 * Screen owners add draft scenes here one at a time. An entry is not visually
 * approved until docs/design/catalog/scenes.json records the user's decision.
 */
object CatalogSceneRegistry {
    val scenes: List<CatalogScene> = listOf(
        NowPlayingControlPhoneLightPlaybackScene,
    )
}

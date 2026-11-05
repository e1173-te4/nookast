package ru.e1173.nookast.designcatalog

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

enum class CatalogProfile(
    val displayName: String,
    val logicalSize: DpSize?,
) {
    ACTUAL_DEVICE("Actual device", null),
    PHONE("Phone 346×782", DpSize(346.dp, 782.dp)),
    FOLD("Fold 707×773", DpSize(707.dp, 773.dp)),
    JAECOO("JAECOO 1440×1920", DpSize(720.dp, 960.dp)),
}

enum class CatalogTheme(val displayName: String) {
    LIGHT("Light"),
    DARK("Dark"),
}

enum class CatalogLocale(val displayName: String) {
    RU("Русский"),
    EN("English"),
}

enum class CatalogTextScale(
    val displayName: String,
    val factor: Float,
) {
    NORMAL("100%", 1f),
    ENLARGED("150%", 1.5f),
}

data class CatalogSceneContext(
    val profile: CatalogProfile,
    val theme: CatalogTheme,
    val locale: CatalogLocale,
    val textScale: CatalogTextScale,
)

data class CatalogScene(
    val id: String,
    val title: String,
    val state: String,
    val revision: String,
    val behaviorContract: String,
    val content: @Composable (CatalogSceneContext, Modifier) -> Unit,
)

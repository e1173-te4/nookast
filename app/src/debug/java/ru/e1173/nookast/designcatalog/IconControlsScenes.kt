package ru.e1173.nookast.designcatalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ru.e1173.nookast.designsystem.LocalNookastColors
import ru.e1173.nookast.designsystem.NookHeaderDisc
import ru.e1173.nookast.designsystem.NookIcons
import ru.e1173.nookast.designsystem.NookSegment
import ru.e1173.nookast.designsystem.NookSegmentedToggle
import ru.e1173.nookast.designsystem.NookType

/** The shared icon disc and the segmented toggle (DEC-059), in their variants, for manual review. */
internal val IconControlsScene = CatalogScene(
    id = "icon-controls-r1",
    title = "Icon disc · Segmented toggle",
    state = "variants",
    revision = "icon-controls/r1",
    behaviorContract = "docs/design/catalog/components/icon-controls/r1/BEHAVIOR.md",
    content = { context, modifier -> IconControlsSample(context, modifier) },
)

@Composable
private fun IconControlsSample(context: CatalogSceneContext, modifier: Modifier) {
    val russian = context.locale == CatalogLocale.RU
    val colors = LocalNookastColors.current
    var view by remember { mutableIntStateOf(0) }
    Column(modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(if (russian) "Круг: с тенью, плоский, голый, крупный" else "Disc: elevated, flat, bare, large", style = NookType.rowTitle, color = colors.textPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            NookHeaderDisc(NookIcons.Search, "Search", {})
            NookHeaderDisc(NookIcons.Settings, "Settings", {}, discSize = 40.dp, elevated = false, zone = 40.dp)
            NookHeaderDisc(NookIcons.ChevronDown, "Close", {}, discSize = 40.dp, fill = Color.Transparent, tint = colors.textPrimary)
            NookHeaderDisc(NookIcons.Settings, "Settings", {}, discSize = 40.dp * 4f / 3f, elevated = false)
            NookHeaderDisc(NookIcons.Settings, "Settings", {}, discSize = 40.dp, elevated = false, enabled = false)
        }
        Text(if (russian) "Переключатель вида" else "View toggle", style = NookType.rowTitle, color = colors.textPrimary)
        val segments = listOf(
            NookSegment(NookIcons.Grid, if (russian) "Крупные обложки" else "Large covers"),
            NookSegment(NookIcons.ListView, if (russian) "Список" else "List"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            NookSegmentedToggle(segments, view, { view = it }, height = 44.dp)
            NookSegmentedToggle(segments, view, { view = it }, height = 58.dp, scale = 4f / 3f)
        }
    }
}

package ru.e1173.nookast.designsystem.connection

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.e1173.nookast.R
import ru.e1173.nookast.designsystem.LocalNookastColors
import ru.e1173.nookast.designsystem.NookShapes
import ru.e1173.nookast.designsystem.NookType

/** A calm moment for a story's miniature: sound runs directly, the buffer is full. */
private val SampleScene = SceneModel(
    net = LinkState.OK,
    netMs = 41,
    server = LinkState.OK,
    serverMs = 63,
    host = LinkState.OK,
    hostMs = 118,
    local = false,
    fromServer = false,
    runwaySeconds = 120f,
    filling = false,
    level = RunwayLevel.NORMAL,
    bandwidthMbps = 1.8f,
    flow = .4f,
    fetchPercent = null,
    offer = false,
    shareLeft = .65f,
    running = false,
    focus = null,
)

/**
 * One story in Settings → Appearance → «Схема связи» (DEC-069), as the themes are chosen: a living
 * miniature of its scheme, the name and, for the chosen one, its story and who it suits.
 */
@Composable
fun NookStoryRow(
    story: ConnectionStory,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = 48.dp,
) {
    val colors = LocalNookastColors.current
    val palette = connectionPalette(colors, null)
    Column(
        modifier
            .fillMaxWidth()
            .clip(NookShapes.l)
            .background(if (selected) colors.tintContainer else Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = minHeight), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NookConnectionScene(
                model = SampleScene,
                labels = SceneLabels("", false, false, null, emptyList(), false, "", ""),
                story = story,
                colors = palette.scene,
                contentDescription = stringResource(story.strings.name),
                modifier = Modifier.width(MINI_W).height(MINI_W * (SCENE_H / SCENE_W)),
                live = selected,
            )
            Text(stringResource(story.strings.name), style = NookType.rowTitle, color = colors.textPrimary, modifier = Modifier.weight(1f))
        }
        AnimatedVisibility(selected, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(top = 8.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(story.strings.text), style = NookType.bodySmall, color = colors.textPrimary)
                Text(stringResource(story.strings.suits), style = NookType.bodySmall, color = colors.textSecondary)
            }
        }
    }
}

private val MINI_W = 96.dp

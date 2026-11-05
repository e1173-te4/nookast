package ru.e1173.nookast.designcatalog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import ru.e1173.nookast.R
import ru.e1173.nookast.preferences.AppThemeMode
import ru.e1173.nookast.ui.theme.NookastTheme

class DesignCatalogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { DesignCatalogApp() }
    }
}

@Composable
private fun DesignCatalogApp() {
    var profile by remember { mutableStateOf(CatalogProfile.ACTUAL_DEVICE) }
    var theme by remember { mutableStateOf(CatalogTheme.LIGHT) }
    var locale by remember { mutableStateOf(CatalogLocale.RU) }
    var textScale by remember { mutableStateOf(CatalogTextScale.NORMAL) }
    var selectedScene by remember { mutableStateOf<CatalogScene?>(null) }

    val context = CatalogSceneContext(
        profile = profile,
        theme = theme,
        locale = locale,
        textScale = textScale,
    )
    val baseDensity = LocalDensity.current
    val catalogDensity = remember(baseDensity.density, textScale.factor) {
        Density(baseDensity.density, textScale.factor)
    }

    NookastTheme(
        themeMode = if (theme == CatalogTheme.DARK) AppThemeMode.DARK else AppThemeMode.LIGHT,
    ) {
        if (selectedScene != null) {
            BackHandler { selectedScene = null }
            androidx.compose.runtime.CompositionLocalProvider(
                LocalDensity provides catalogDensity,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(0.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CatalogViewport(
                        profile = profile,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        selectedScene?.content?.invoke(context, Modifier.fillMaxSize())
                    }
                }
            }
        } else {
            CatalogIndex(
                profile = profile,
                theme = theme,
                locale = locale,
                textScale = textScale,
                onProfileChanged = { profile = profile.next() },
                onThemeChanged = { theme = theme.next() },
                onLocaleChanged = { locale = locale.next() },
                onTextScaleChanged = { textScale = textScale.next() },
                onOpenScene = { selectedScene = it },
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CatalogIndex(
    profile: CatalogProfile,
    theme: CatalogTheme,
    locale: CatalogLocale,
    textScale: CatalogTextScale,
    onProfileChanged: () -> Unit,
    onThemeChanged: () -> Unit,
    onLocaleChanged: () -> Unit,
    onTextScaleChanged: () -> Unit,
    onOpenScene: (CatalogScene) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.design_catalog_title)) }) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CatalogSelector(
                        label = stringResource(R.string.design_catalog_profile, profile.displayName),
                        onClick = onProfileChanged,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CatalogSelector(
                            label = stringResource(R.string.design_catalog_theme, theme.displayName),
                            onClick = onThemeChanged,
                            modifier = Modifier.weight(1f),
                        )
                        CatalogSelector(
                            label = stringResource(R.string.design_catalog_locale, locale.displayName),
                            onClick = onLocaleChanged,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    CatalogSelector(
                        label = stringResource(R.string.design_catalog_text_scale, textScale.displayName),
                        onClick = onTextScaleChanged,
                    )
                }
            }

            if (CatalogSceneRegistry.scenes.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.design_catalog_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(CatalogSceneRegistry.scenes, key = CatalogScene::id) { scene ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(scene.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${scene.state} · ${scene.revision} · " +
                                    stringResource(R.string.design_catalog_draft),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(onClick = { onOpenScene(scene) }) {
                                Text(stringResource(R.string.design_catalog_open))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogSelector(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(label)
    }
}

private inline fun <reified T : Enum<T>> T.next(): T {
    val values = enumValues<T>()
    return values[(ordinal + 1) % values.size]
}

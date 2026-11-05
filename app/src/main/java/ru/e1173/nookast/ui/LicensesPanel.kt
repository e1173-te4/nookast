package ru.e1173.nookast.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.e1173.nookast.R

/**
 * Settings → About → Licenses (DEC-053: the public APK carries the texts its components' licenses
 * ask for). The list of libraries is made at build time by the licensee plugin from the release
 * dependencies, which may only be under the Apache License 2.0; the Manrope font, under the SIL
 * Open Font License 1.1, is not a dependency and is listed apart. Both texts ship in `assets/licenses`.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun LicensesPanel() {
    val context = LocalContext.current
    var open by remember { mutableStateOf<String?>(null) }
    val components by produceState(emptyList<String>()) {
        value = withContext(Dispatchers.IO) { componentLines(readAsset(context, ARTIFACTS)) + MANROPE }
    }
    SettingsGroupLabel(R.string.licenses_title, Modifier.padding(top = 14.dp))
    SettingsPanel {
        Column(Modifier.padding(16.dp)) {
            Text(
                androidx.compose.ui.res.pluralStringResource(R.plurals.licenses_summary, components.size, components.size),
                style = MaterialTheme.typography.bodyMedium,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            ) {
                OutlinedButton(onClick = { open = COMPONENTS }) { Text(stringResource(R.string.licenses_components)) }
                OutlinedButton(onClick = { open = APACHE }) { Text(stringResource(R.string.licenses_apache)) }
                OutlinedButton(onClick = { open = OFL }) { Text(stringResource(R.string.licenses_ofl)) }
            }
        }
    }
    open?.let { asset ->
        val text by produceState("", asset) {
            value = if (asset == COMPONENTS) components.joinToString("\n") else withContext(Dispatchers.IO) { readAsset(context, asset) }
        }
        ru.e1173.nookast.designsystem.ScaledAlertDialog(
            onDismissRequest = { open = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            title = {
                Text(
                    stringResource(
                        when (asset) {
                            APACHE -> R.string.licenses_apache
                            OFL -> R.string.licenses_ofl
                            else -> R.string.licenses_components
                        },
                    ),
                )
            },
            text = {
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState(), flingBehavior = deviceFling()),
                )
            },
            confirmButton = { TextButton(onClick = { open = null }) { Text(stringResource(R.string.licenses_close)) } },
        )
    }
}

private fun readAsset(context: android.content.Context, name: String): String =
    runCatching { context.assets.open(name).bufferedReader().use { it.readText() } }.getOrDefault("")

/** One line per library: `group:artifact version · licence`, from licensee's `artifacts.json`. */
internal fun componentLines(json: String): List<String> {
    val array = runCatching { org.json.JSONArray(json) }.getOrNull() ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val licenses = item.optJSONArray("spdxLicenses")?.let { list ->
            (0 until list.length()).mapNotNull { list.optJSONObject(it)?.optString("identifier")?.takeIf(String::isNotBlank) }
        }.orEmpty()
        "${item.optString("groupId")}:${item.optString("artifactId")} ${item.optString("version")} · ${licenses.joinToString(", ")}"
    }.sorted()
}

private const val MANROPE = "Manrope · OFL-1.1"

/** Written into the APK's assets by licensee (`bundleAndroidAsset`). */
private const val ARTIFACTS = "app/cash/licensee/artifacts.json"
private const val COMPONENTS = "components"
private const val APACHE = "licenses/apache-2.0.txt"
private const val OFL = "licenses/ofl-1.1.txt"

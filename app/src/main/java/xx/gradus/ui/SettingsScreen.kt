package xx.gradus.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.updater.Updater
import xx.gradus.AppSettings
import xx.gradus.R
import xx.gradus.PressureUnit
import xx.gradus.ThemeMode
import xx.gradus.WindDirectionStyle
import xx.gradus.WindUnit
import xx.gradus.currentLanguageTag
import xx.gradus.setLanguageTag
import xx.gradus.supportedLanguages

/** Which editor is open; only one can be at a time. */
private enum class Editing { NONE, THEME, ACCENT, LANGUAGE, WIND_UNIT, WIND_DIRECTION, PRESSURE_UNIT }

/** Units and how the wind is shown, then theme, accent colour, language, and the start-up update check. */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val themeMode by AppSettings.themeMode.collectAsState()
    val accentIndex by AppSettings.accentIndex.collectAsState()
    val windUnit by AppSettings.windUnit.collectAsState()
    val windDirection by AppSettings.windDirection.collectAsState()
    val pressureUnit by AppSettings.pressureUnit.collectAsState()

    val systemLabel = stringResource(R.string.language_system)
    val languages = remember(context, systemLabel) { supportedLanguages(context, systemLabel) }
    val languageTag = remember(context) { currentLanguageTag(context) }
    val languageLabel = languages.firstOrNull { it.tag == languageTag }?.label ?: systemLabel

    // Saveable: a new theme or language recreates the activity under an open dialog.
    var editing by rememberSaveable { mutableStateOf(Editing.NONE) }
    // The updater keeps this flag in its own preferences file.
    var updateCheck by remember { mutableStateOf(Updater.isEnabled(context)) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SettingRow(
            label = stringResource(R.string.setting_wind_unit),
            value = windUnitLabel(windUnit),
            onClick = { editing = Editing.WIND_UNIT },
        )
        HorizontalDivider()
        SettingRow(
            label = stringResource(R.string.setting_wind_direction),
            value = stringResource(windDirection.labelRes()),
            onClick = { editing = Editing.WIND_DIRECTION },
        )
        HorizontalDivider()
        SettingRow(
            label = stringResource(R.string.setting_pressure_unit),
            value = pressureUnitLabel(pressureUnit),
            onClick = { editing = Editing.PRESSURE_UNIT },
        )
        HorizontalDivider()
        SettingRow(
            label = stringResource(R.string.setting_theme),
            value = stringResource(themeMode.labelRes()),
            onClick = { editing = Editing.THEME },
        )
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { editing = Editing.ACCENT }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.setting_accent),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            AccentSwatch(accentAt(accentIndex), selected = false, onClick = { editing = Editing.ACCENT })
        }
        HorizontalDivider()
        SettingRow(
            label = stringResource(R.string.setting_language),
            value = languageLabel,
            onClick = { editing = Editing.LANGUAGE },
        )
        HorizontalDivider()
        // The whole row is not clickable: the switch is the control.
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.setting_update_check),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = updateCheck,
                onCheckedChange = {
                    updateCheck = it
                    Updater.setEnabled(context, it)
                },
            )
        }
    }

    when (editing) {
        Editing.THEME -> ChoiceDialog(
            title = stringResource(R.string.setting_theme),
            options = ThemeMode.entries,
            selected = themeMode,
            label = { stringResource(it.labelRes()) },
            onDismiss = { editing = Editing.NONE },
            onPick = {
                AppSettings.setThemeMode(context, it)
                editing = Editing.NONE
            },
        )

        Editing.ACCENT -> AlertDialog(
            onDismissRequest = { editing = Editing.NONE },
            title = { Text(stringResource(R.string.setting_accent)) },
            text = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    AccentPalette.forEachIndexed { index, color ->
                        AccentSwatch(color, selected = index == accentIndex, onClick = {
                            AppSettings.setAccentIndex(context, index)
                            editing = Editing.NONE
                        })
                    }
                }
            },
            confirmButton = { DialogDismissButton(stringResource(R.string.cancel)) { editing = Editing.NONE } },
        )

        Editing.LANGUAGE -> ChoiceDialog(
            title = stringResource(R.string.setting_language),
            options = languages,
            selected = languages.firstOrNull { it.tag == languageTag },
            label = { it.label },
            onDismiss = { editing = Editing.NONE },
            onPick = {
                editing = Editing.NONE
                // Persisted, and the activity recreated in the new language.
                activity?.let { a -> setLanguageTag(a, it.tag) }
            },
        )

        Editing.WIND_UNIT -> ChoiceDialog(
            title = stringResource(R.string.setting_wind_unit),
            options = WindUnit.entries,
            selected = windUnit,
            label = { windUnitLabel(it) },
            onDismiss = { editing = Editing.NONE },
            onPick = {
                AppSettings.setWindUnit(context, it)
                editing = Editing.NONE
            },
        )

        Editing.WIND_DIRECTION -> ChoiceDialog(
            title = stringResource(R.string.setting_wind_direction),
            options = WindDirectionStyle.entries,
            selected = windDirection,
            label = { stringResource(it.labelRes()) },
            onDismiss = { editing = Editing.NONE },
            onPick = {
                AppSettings.setWindDirection(context, it)
                editing = Editing.NONE
            },
        )

        Editing.PRESSURE_UNIT -> ChoiceDialog(
            title = stringResource(R.string.setting_pressure_unit),
            options = PressureUnit.entries,
            selected = pressureUnit,
            label = { pressureUnitLabel(it) },
            onDismiss = { editing = Editing.NONE },
            onPick = {
                AppSettings.setPressureUnit(context, it)
                editing = Editing.NONE
            },
        )

        Editing.NONE -> Unit
    }
}

@Composable
private fun SettingRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

private fun WindDirectionStyle.labelRes(): Int = when (this) {
    WindDirectionStyle.ARROW -> R.string.wind_style_arrow
    WindDirectionStyle.LETTERS -> R.string.wind_style_letters
}

package top.easytier.miuix.ui.screens.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import top.easytier.miuix.R
import top.easytier.miuix.ui.dialogs.AboutDialog
import top.easytier.miuix.ui.dialogs.ConfigServerDialog
import top.easytier.miuix.ui.dialogs.LanguageSwitcherDialog
import top.easytier.miuix.ui.dialogs.LogLevelDialog
import top.easytier.miuix.ui.dialogs.ModeSwitcherDialog
import top.easytier.miuix.ui.theme.AppSettings
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.preference.ArrowPreference

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    appSettings: AppSettings = AppSettings(),
    onSettingsChange: (AppSettings) -> Unit = {},
    onOpenTheme: () -> Unit = {},
) {
    var showModeSwitcher by remember { mutableStateOf(false) }
    var showConfigServer by remember { mutableStateOf(false) }
    var showLogLevel by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
    ) {
        item { Spacer(Modifier.height(8.dp)) }

        // Connection settings
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = stringResource(R.string.settings_mode),
                    summary = stringResource(R.string.settings_mode_normal),
                    onClick = { showModeSwitcher = true },
                )
                ArrowPreference(
                    title = stringResource(R.string.settings_config_server),
                    summary = stringResource(R.string.settings_config_server_not_configured),
                    onClick = { showConfigServer = true },
                )
                ArrowPreference(
                    title = stringResource(R.string.settings_log_level),
                    summary = stringResource(R.string.settings_log_level_off),
                    onClick = { showLogLevel = true },
                )
            }
        }

        item { Spacer(Modifier.height(12.dp)) }

        // UI settings
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = stringResource(R.string.settings_theme),
                    summary = stringResource(R.string.settings_theme_summary),
                    onClick = onOpenTheme,
                )
            }
        }

        item { Spacer(Modifier.height(12.dp)) }

        // General settings
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                LanguageSwitcherDialog(
                    currentLanguage = appSettings.language,
                    onLanguageSelected = { language ->
                        onSettingsChange(appSettings.copy(language = language))
                    },
                )
                ArrowPreference(
                    title = stringResource(R.string.settings_about),
                    onClick = { showAbout = true },
                )
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }

    if (showModeSwitcher) {
        ModeSwitcherDialog(onDismiss = { showModeSwitcher = false })
    }
    if (showConfigServer) {
        ConfigServerDialog(onDismiss = { showConfigServer = false })
    }
    if (showLogLevel) {
        LogLevelDialog(onDismiss = { showLogLevel = false })
    }
    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
}

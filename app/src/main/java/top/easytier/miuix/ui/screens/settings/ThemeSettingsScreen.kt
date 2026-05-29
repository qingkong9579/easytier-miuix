package top.easytier.miuix.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import top.easytier.miuix.R
import top.easytier.miuix.ui.theme.AppSettings
import top.easytier.miuix.ui.theme.ColorMode
import top.easytier.miuix.ui.theme.keyColorOptions
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

@Composable
fun ThemeSettingsScreen(
    appSettings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val themeModes = listOf(
        stringResource(R.string.settings_theme_mode_system),
        stringResource(R.string.settings_theme_mode_light),
        stringResource(R.string.settings_theme_mode_dark),
    )

    val selectedThemeMode = when (appSettings.colorMode) {
        ColorMode.SYSTEM, ColorMode.MONET_SYSTEM -> 0
        ColorMode.LIGHT, ColorMode.MONET_LIGHT -> 1
        ColorMode.DARK, ColorMode.MONET_DARK -> 2
    }

    val isMonet = appSettings.colorMode.isMonet
    val isDark = appSettings.colorMode.isDark || (appSettings.colorMode.isSystem && isSystemInDarkTheme())

    val colorItems = listOf(
        stringResource(R.string.settings_key_color_default),
        stringResource(R.string.color_red),
        stringResource(R.string.color_pink),
        stringResource(R.string.color_purple),
        stringResource(R.string.color_deep_purple),
        stringResource(R.string.color_indigo),
        stringResource(R.string.color_blue),
        stringResource(R.string.color_cyan),
        stringResource(R.string.color_teal),
        stringResource(R.string.color_green),
        stringResource(R.string.color_yellow),
        stringResource(R.string.color_amber),
        stringResource(R.string.color_orange),
        stringResource(R.string.color_brown),
        stringResource(R.string.color_blue_grey),
    )
    val colorValues = listOf(0) + keyColorOptions

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
    ) {
        item { Spacer(Modifier.height(8.dp)) }

        // Theme mode selection - no card border, just TabRow
        item {
            TabRow(
                tabs = themeModes,
                selectedTabIndex = selectedThemeMode,
                onTabSelected = { index ->
                    val newMode = when (index) {
                        0 -> if (isMonet) ColorMode.MONET_SYSTEM else ColorMode.SYSTEM
                        1 -> if (isMonet) ColorMode.MONET_LIGHT else ColorMode.LIGHT
                        2 -> if (isMonet) ColorMode.MONET_DARK else ColorMode.DARK
                        else -> appSettings.colorMode
                    }
                    onSettingsChange(appSettings.copy(colorMode = newMode))
                },
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        item { Spacer(Modifier.height(8.dp)) }

        // Monet and color settings - active switch uses primary color
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                SwitchPreference(
                    title = stringResource(R.string.settings_monet),
                    summary = stringResource(R.string.settings_monet_summary),
                    checked = isMonet,
                    onCheckedChange = { enabled ->
                        val newMode = if (enabled) {
                            when (appSettings.colorMode) {
                                ColorMode.SYSTEM, ColorMode.MONET_SYSTEM -> ColorMode.MONET_SYSTEM
                                ColorMode.LIGHT, ColorMode.MONET_LIGHT -> ColorMode.MONET_LIGHT
                                ColorMode.DARK, ColorMode.MONET_DARK -> ColorMode.MONET_DARK
                            }
                        } else {
                            when (appSettings.colorMode) {
                                ColorMode.SYSTEM, ColorMode.MONET_SYSTEM -> ColorMode.SYSTEM
                                ColorMode.LIGHT, ColorMode.MONET_LIGHT -> ColorMode.LIGHT
                                ColorMode.DARK, ColorMode.MONET_DARK -> ColorMode.DARK
                            }
                        }
                        onSettingsChange(appSettings.copy(colorMode = newMode))
                    },
                )

                AnimatedVisibility(visible = isMonet) {
                    Column {
                        OverlayDropdownPreference(
                            title = stringResource(R.string.settings_key_color),
                            items = colorItems,
                            selectedIndex = colorValues.indexOf(appSettings.keyColor).takeIf { it >= 0 } ?: 0,
                            onSelectedIndexChange = { index ->
                                onSettingsChange(appSettings.copy(keyColor = colorValues[index]))
                            },
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }

        // UI settings
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                SwitchPreference(
                    title = stringResource(R.string.settings_blur_effects),
                    summary = stringResource(R.string.settings_blur_effects_summary),
                    checked = appSettings.enableBlur,
                    onCheckedChange = {
                        onSettingsChange(appSettings.copy(enableBlur = it))
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.settings_floating_bottom_bar),
                    summary = stringResource(R.string.settings_floating_bottom_bar_summary),
                    checked = appSettings.enableFloatingBottomBar,
                    onCheckedChange = {
                        onSettingsChange(appSettings.copy(enableFloatingBottomBar = it))
                    },
                )
                AnimatedVisibility(visible = appSettings.enableFloatingBottomBar) {
                    SwitchPreference(
                        title = stringResource(R.string.settings_floating_bar_blur),
                        summary = stringResource(R.string.settings_floating_bar_blur_summary),
                        checked = appSettings.enableFloatingBottomBarBlur,
                        onCheckedChange = {
                            onSettingsChange(appSettings.copy(enableFloatingBottomBarBlur = it))
                        },
                    )
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

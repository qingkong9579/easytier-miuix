package top.easytier.miuix.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsControllerCompat
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

@Composable
@ReadOnlyComposable
fun isInDarkTheme(): Boolean {
    val colorMode = LocalColorMode.current
    return when (colorMode) {
        2, 5, 6 -> true  // DARK, MONET_DARK, DARK_AMOLED
        1, 4 -> false     // LIGHT, MONET_LIGHT
        else -> isSystemInDarkTheme()
    }
}

@Composable
fun AppTheme(
    appSettings: AppSettings = AppSettings(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val systemDarkTheme = isSystemInDarkTheme()
    val darkTheme = appSettings.colorMode.isDark || (appSettings.colorMode.isSystem && systemDarkTheme)

    val controller = remember(appSettings) {
        ThemeController(
            when (appSettings.colorMode) {
                ColorMode.SYSTEM -> ColorSchemeMode.System
                ColorMode.LIGHT -> ColorSchemeMode.Light
                ColorMode.DARK -> ColorSchemeMode.Dark
                ColorMode.MONET_SYSTEM -> ColorSchemeMode.MonetSystem
                ColorMode.MONET_LIGHT -> ColorSchemeMode.MonetLight
                ColorMode.MONET_DARK -> ColorSchemeMode.MonetDark
            },
            keyColor = if (appSettings.keyColor == 0) Color(0xFF3482FF) else Color(appSettings.keyColor),
            isDark = darkTheme,
            paletteStyle = ThemePaletteStyle.TonalSpot,
        )
    }

    MiuixTheme(controller = controller) {
        LaunchedEffect(darkTheme) {
            val window = (context as? Activity)?.window ?: return@LaunchedEffect
            WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
        CompositionLocalProvider(
            LocalContentColor provides MiuixTheme.colorScheme.onBackground,
            LocalColorMode provides appSettings.colorMode.value,
            LocalEnableBlur provides appSettings.enableBlur,
            LocalEnableFloatingBottomBar provides appSettings.enableFloatingBottomBar,
            LocalEnableFloatingBottomBarBlur provides appSettings.enableFloatingBottomBarBlur,
        ) {
            content()
        }
    }
}

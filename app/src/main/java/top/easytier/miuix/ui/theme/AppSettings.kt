package top.easytier.miuix.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

enum class AppLanguage(val code: String, val displayName: String) {
    SYSTEM("", "跟随系统"),
    ENGLISH("en", "English"),
    CHINESE("zh", "中文"),
}

data class AppSettings(
    val colorMode: ColorMode = ColorMode.MONET_SYSTEM,
    val keyColor: Int = 0,
    val enableBlur: Boolean = true,
    val enableFloatingBottomBar: Boolean = false,
    val enableFloatingBottomBarBlur: Boolean = false,
    val language: AppLanguage = AppLanguage.SYSTEM,
)

val LocalColorMode = staticCompositionLocalOf { 0 }
val LocalEnableBlur = staticCompositionLocalOf { false }
val LocalEnableFloatingBottomBar = staticCompositionLocalOf { false }
val LocalEnableFloatingBottomBarBlur = staticCompositionLocalOf { false }

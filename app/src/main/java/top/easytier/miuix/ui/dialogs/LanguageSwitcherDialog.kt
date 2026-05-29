package top.easytier.miuix.ui.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import top.easytier.miuix.R
import top.easytier.miuix.ui.theme.AppLanguage
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference

@Composable
fun LanguageSwitcherDialog(
    currentLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val languageNames = AppLanguage.entries.map {
        when (it) {
            AppLanguage.SYSTEM -> stringResource(R.string.language_system)
            AppLanguage.ENGLISH -> "English"
            AppLanguage.CHINESE -> "中文"
        }
    }
    val selectedIndex = remember(currentLanguage) { AppLanguage.entries.indexOf(currentLanguage) }

    OverlayDropdownPreference(
        title = stringResource(R.string.settings_language),
        items = languageNames,
        selectedIndex = selectedIndex.coerceIn(0, languageNames.lastIndex),
        onSelectedIndexChange = { index ->
            if (index in AppLanguage.entries.indices) {
                onLanguageSelected(AppLanguage.entries[index])
            }
        },
        modifier = modifier,
    )
}

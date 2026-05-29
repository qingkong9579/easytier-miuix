package top.easytier.miuix.ui.dialogs

import androidx.compose.runtime.Composable
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
    val languages = AppLanguage.entries.map {
        when (it) {
            AppLanguage.SYSTEM -> stringResource(R.string.language_system)
            AppLanguage.ENGLISH -> "English"
            AppLanguage.CHINESE -> "中文"
        }
    }
    val selectedIndex = AppLanguage.entries.indexOf(currentLanguage)

    OverlayDropdownPreference(
        items = languages,
        selectedIndex = selectedIndex,
        title = stringResource(R.string.settings_language),
        summary = stringResource(R.string.settings_language_summary),
        modifier = modifier,
        onSelectedIndexChange = { index ->
            val language = AppLanguage.entries[index]
            onLanguageSelected(language)
        },
    )
}

package top.easytier.miuix.ui.screens.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import top.easytier.miuix.R
import top.easytier.miuix.ui.dialogs.AboutDialog
import top.easytier.miuix.ui.dialogs.ConfigServerDialog
import top.easytier.miuix.ui.dialogs.LanguageSwitcherDialog
import top.easytier.miuix.ui.theme.AppSettings
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    contentBottomPadding: Dp = 0.dp,
    listState: LazyListState = rememberLazyListState(),
    appSettings: AppSettings = AppSettings(),
    onSettingsChange: (AppSettings) -> Unit = {},
    onOpenTheme: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    var showConfigServer by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    val configServerUrl by viewModel.configServerUrl.collectAsStateWithLifecycle()
    val configServerConnected by viewModel.configServerConnected.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            // miuix 滚动骨架：滚到边界时给触感反馈，越界用 MIUI 风格回弹，
            // 而不是 Compose 默认的拉伸光晕。
            .scrollEndHaptic()
            .overScrollVertical()
            .padding(horizontal = 16.dp),
        state = listState,
        contentPadding = PaddingValues(bottom = contentBottomPadding),
    ) {
        item { Spacer(Modifier.height(12.dp)) }

        // 连接：远程配置服务器
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = stringResource(R.string.settings_config_server),
                    summary = when {
                        configServerUrl == null -> stringResource(R.string.settings_config_server_not_configured)
                        configServerConnected -> stringResource(R.string.config_server_state_connected)
                        else -> stringResource(R.string.config_server_state_disconnected)
                    },
                    onClick = { showConfigServer = true },
                )
            }
        }

        item { Spacer(Modifier.height(12.dp)) }

        // 外观：主题与语言同属显示偏好，放在同一组里；此前主题单独一张卡、
        // 语言却和「关于」挤在一起，分组没有依据。
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = stringResource(R.string.settings_theme),
                    summary = stringResource(R.string.settings_theme_summary),
                    onClick = onOpenTheme,
                )
                LanguageSwitcherDialog(
                    currentLanguage = appSettings.language,
                    onLanguageSelected = { language ->
                        onSettingsChange(appSettings.copy(language = language))
                    },
                )
            }
        }

        item { Spacer(Modifier.height(12.dp)) }

        // 其他
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = stringResource(R.string.settings_about),
                    summary = stringResource(R.string.settings_about_summary),
                    onClick = { showAbout = true },
                )
            }
        }

        item {
            Spacer(Modifier.height(12.dp))
        }
        item {
            Spacer(
                Modifier
                    .height(24.dp + contentBottomPadding)
                    .navigationBarsPadding()
            )
        }
    }

    if (showConfigServer) {
        ConfigServerDialog(
            currentUrl = configServerUrl,
            connected = configServerConnected,
            onConnect = { url, onResult -> viewModel.connectConfigServer(url, onResult) },
            onDisconnect = { viewModel.disconnectConfigServer() },
            onDismiss = { showConfigServer = false },
        )
    }
    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
}

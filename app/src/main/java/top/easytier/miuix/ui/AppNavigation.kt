package top.easytier.miuix.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.easytier.miuix.R
import top.easytier.miuix.ui.components.FloatingBottomBar
import top.easytier.miuix.ui.components.FloatingBottomBarItem
import top.easytier.miuix.ui.screens.config.ConfigScreen
import top.easytier.miuix.ui.screens.networks.NetworkListScreen
import java.util.UUID
import top.easytier.miuix.ui.screens.settings.SettingsScreen
import top.easytier.miuix.ui.screens.settings.ThemeSettingsScreen
import top.easytier.miuix.ui.screens.status.StatusScreen
import top.easytier.miuix.ui.theme.AppSettings
import top.easytier.miuix.ui.theme.LocalEnableBlur
import top.easytier.miuix.ui.theme.LocalEnableFloatingBottomBar
import top.easytier.miuix.ui.theme.LocalEnableFloatingBottomBarBlur
import top.easytier.miuix.ui.util.BlurredBar
import top.easytier.miuix.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import top.yukonga.miuix.kmp.theme.MiuixTheme

enum class BottomBarDestination(val labelRes: Int, val icon: ImageVector) {
    Networks(R.string.nav_networks, Icons.Rounded.Dns),
    Status(R.string.nav_status, Icons.Rounded.Analytics),
    Settings(R.string.nav_settings, Icons.Rounded.Settings),
}

@Composable
fun AppNavigation(
    appSettings: AppSettings = AppSettings(),
    onSettingsChange: (AppSettings) -> Unit = {},
    onExitApp: () -> Unit = {},
) {
    // Provide NavigationEventDispatcher for miuix OverlayDialog/OverlayDropdown
    val owner = rememberNavigationEventDispatcherOwner(parent = null)
    CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
        AppNavigationContent(appSettings, onSettingsChange, onExitApp)
    }
}

@Composable
private fun AppNavigationContent(
    appSettings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onExitApp: () -> Unit,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var editingInstanceId by rememberSaveable { mutableStateOf<String?>(null) }
    var showThemeSettings by rememberSaveable { mutableStateOf(false) }
    val enableBlur = LocalEnableBlur.current
    val enableFloatingBottomBar = LocalEnableFloatingBottomBar.current
    val enableFloatingBottomBarBlur = LocalEnableFloatingBottomBarBlur.current
    val surfaceColor = MiuixTheme.colorScheme.surface
    val blurBackdrop = rememberBlurBackdrop(enableBlur)
    val backdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }

    // Handle back gesture/button
    BackHandler {
        when {
            editingInstanceId != null -> {
                // Go back from edit config
                editingInstanceId = null
            }
            showThemeSettings -> {
                // Go back from theme settings
                showThemeSettings = false
            }
            else -> {
                // On main page, exit app
                onExitApp()
            }
        }
    }

    val items = BottomBarDestination.entries.map { dest ->
        NavigationItem(
            label = stringResource(dest.labelRes),
            icon = dest.icon,
        )
    }

    val showBottomBar = editingInstanceId == null && !showThemeSettings

    val bottomBar: @Composable () -> Unit = {
        if (showBottomBar && !enableFloatingBottomBar) {
            BlurredBar(blurBackdrop) {
                NavigationBar(
                    color = if (blurBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
                    content = {
                        items.forEachIndexed { index, item ->
                            NavigationBarItem(
                                modifier = Modifier.weight(1f),
                                icon = item.icon,
                                label = item.label,
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                            )
                        }
                    },
                )
            }
        }
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(title = when {
                editingInstanceId != null -> stringResource(R.string.edit_network)
                showThemeSettings -> stringResource(R.string.settings_theme)
                else -> "EasyTier"
            })
        },
        bottomBar = bottomBar,
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            // Apply backdrop captures: blurBackdrop for NavBar texture blur, backdrop for liquid glass
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (blurBackdrop != null) Modifier.layerBackdrop(blurBackdrop) else Modifier)
            ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (enableFloatingBottomBar && enableFloatingBottomBarBlur) Modifier.layerBackdrop(backdrop) else Modifier)
            ) {
                when {
                    editingInstanceId != null -> {
                        ConfigScreen(
                            instanceId = editingInstanceId!!,
                            onBack = { editingInstanceId = null },
                        )
                    }
                    showThemeSettings -> {
                        ThemeSettingsScreen(
                            appSettings = appSettings,
                            onSettingsChange = onSettingsChange,
                            onBack = { showThemeSettings = false },
                        )
                    }
                    else -> {
                        when (selectedTab) {
                            0 -> NetworkListScreen(
                                onEditNetwork = { editingInstanceId = it },
                                onCreateNetwork = { editingInstanceId = UUID.randomUUID().toString() },
                            )
                            1 -> StatusScreen()
                            2 -> SettingsScreen(
                                appSettings = appSettings,
                                onSettingsChange = onSettingsChange,
                                onOpenTheme = { showThemeSettings = true },
                            )
                        }
                    }
                }
            }

            // Floating bar overlay — pinned to screen bottom
            if (showBottomBar && enableFloatingBottomBar) {
                FloatingBottomBar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        )
                        .padding(bottom = 12.dp + WindowInsets.navigationBars.asPaddingValues()
                            .calculateBottomPadding()),
                    selectedIndex = { selectedTab },
                    onSelected = { selectedTab = it },
                    backdrop = backdrop,
                    tabsCount = items.size,
                    isBlurEnabled = enableFloatingBottomBarBlur,
                ) {
                    items.forEachIndexed { index, item ->
                        FloatingBottomBarItem(
                            onClick = { selectedTab = index },
                            modifier = Modifier.defaultMinSize(minWidth = 76.dp),
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = MiuixTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = item.label,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                color = MiuixTheme.colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Visible,
                            )
                        }
                    }
                }
            }
            }
        }
    }
}

package top.easytier.miuix.ui

import androidx.activity.OnBackPressedCallback
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.easytier.miuix.R
import top.easytier.miuix.ui.components.FloatingBottomBar
import top.easytier.miuix.ui.components.FloatingBottomBarBottomMargin
import top.easytier.miuix.ui.components.FloatingBottomBarHeight
import top.easytier.miuix.ui.components.FloatingBottomBarItem
import top.easytier.miuix.ui.components.pressScale
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
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.BadgedBox
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
    networkRunning: Boolean = false,
    openCreateNetwork: Boolean = false,
    onConsumedOpenCreateNetwork: () -> Unit = {},
) {
    // Provide NavigationEventDispatcher for miuix OverlayDialog/OverlayDropdown
    val owner = rememberNavigationEventDispatcherOwner(parent = null)
    CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
        AppNavigationContent(
            appSettings = appSettings,
            onSettingsChange = onSettingsChange,
            onExitApp = onExitApp,
            networkRunning = networkRunning,
            openCreateNetwork = openCreateNetwork,
            onConsumedOpenCreateNetwork = onConsumedOpenCreateNetwork,
        )
    }
}

@Composable
private fun AppNavigationContent(
    appSettings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onExitApp: () -> Unit,
    networkRunning: Boolean,
    openCreateNetwork: Boolean = false,
    onConsumedOpenCreateNetwork: () -> Unit = {},
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var editingInstanceId by rememberSaveable { mutableStateOf<String?>(null) }
    var showThemeSettings by rememberSaveable { mutableStateOf(false) }

    // 磁贴等外部入口请求：直接打开创建网络页（新配置）
    LaunchedEffect(openCreateNetwork) {
        if (openCreateNetwork) {
            editingInstanceId = UUID.randomUUID().toString()
            selectedTab = 0
            onConsumedOpenCreateNetwork()
        }
    }

    // 各 Tab 的滚动状态：提升到此处跨 Tab 切换保存/恢复
    val networksListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val statusListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val settingsListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val enableBlur = LocalEnableBlur.current
    val enableFloatingBottomBar = LocalEnableFloatingBottomBar.current
    val enableFloatingBottomBarBlur = LocalEnableFloatingBottomBarBlur.current
    val surfaceColor = MiuixTheme.colorScheme.surface
    val blurBackdrop = rememberBlurBackdrop(enableBlur)
    val backdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }

    // Scroll padding so content stops just past the top of the floating bottom bar
    // (bar bottom margin + bar height + the nav bar inset the bar already clears).
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val floatingBarScrollPadding = FloatingBottomBarBottomMargin + FloatingBottomBarHeight + navBarBottom

    // Handle back gesture/button — use OnBackPressedCallback for MIUI compat
    val backActivity = LocalContext.current as ComponentActivity
    DisposableEffect(backActivity) {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    editingInstanceId != null -> editingInstanceId = null
                    showThemeSettings -> showThemeSettings = false
                    else -> onExitApp()
                }
            }
        }
        backActivity.onBackPressedDispatcher.addCallback(callback)
        onDispose { callback.remove() }
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
                            // 状态页 Tab 在有网络运行时显示小圆点徽标（主色，表示 live）
                            val statusBadge: (@Composable () -> Unit)? =
                                if (networkRunning && index == 1) {
                                    ({ Badge(containerColor = MiuixTheme.colorScheme.primary) })
                                } else null
                            NavigationBarItem(
                                modifier = Modifier.weight(1f),
                                icon = item.icon,
                                label = item.label,
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                badge = statusBadge,
                            )
                        }
                    },
                )
            }
        }
    }

    Scaffold(
        topBar = {
            // 返回箭头通过 OnBackPressedDispatcher 触发：编辑页有未保存修改时
            // 会先命中 ConfigScreen 的脏状态回调弹出确认，而不是直接丢弃
            val topBarBack: (@Composable () -> Unit)? = if (editingInstanceId != null || showThemeSettings) {
                {
                    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
                    val backInteraction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
                            .pressScale(backInteraction)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = backInteraction,
                                indication = null,
                                onClick = { dispatcher?.onBackPressed() },
                            )
                            .padding(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back),
                            tint = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                }
            } else null
            SmallTopAppBar(
                title = when {
                    editingInstanceId != null -> stringResource(R.string.edit_network)
                    showThemeSettings -> stringResource(R.string.settings_theme)
                    else -> "EasyTier"
                },
                navigationIcon = topBarBack ?: {},
            )
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
                        // Use scroll contentPadding (not layout padding) so content can slide
                        // under the floating bar and get the liquid glass effect while scrolling,
                        // yet still stop just past the bar's top at maximum scroll.
                        val contentBottomPadding = if (enableFloatingBottomBar) floatingBarScrollPadding else 0.dp
                        when (selectedTab) {
                            0 -> NetworkListScreen(
                                contentBottomPadding = contentBottomPadding,
                                listState = networksListState,
                                onEditNetwork = { editingInstanceId = it },
                                onCreateNetwork = { editingInstanceId = UUID.randomUUID().toString() },
                            )
                            1 -> StatusScreen(
                                contentBottomPadding = contentBottomPadding,
                                listState = statusListState,
                                onStartNetwork = { selectedTab = 0 },
                            )
                            2 -> SettingsScreen(
                                contentBottomPadding = contentBottomPadding,
                                listState = settingsListState,
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
                        .padding(bottom = FloatingBottomBarBottomMargin + navBarBottom),
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
                            if (networkRunning && index == 1) {
                                BadgedBox(
                                    badge = { Badge(containerColor = MiuixTheme.colorScheme.primary) },
                                ) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.label,
                                        tint = MiuixTheme.colorScheme.onSurface,
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    tint = MiuixTheme.colorScheme.onSurface,
                                )
                            }
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

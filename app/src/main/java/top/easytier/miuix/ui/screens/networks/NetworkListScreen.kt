package top.easytier.miuix.ui.screens.networks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import top.easytier.miuix.R
import top.easytier.miuix.data.model.NetworkInstance
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun NetworkListScreen(
    modifier: Modifier = Modifier,
    contentBottomPadding: Dp = 0.dp,
    listState: LazyListState = rememberLazyListState(),
    onEditNetwork: (String) -> Unit = {},
    onCreateNetwork: () -> Unit = {},
    viewModel: NetworkListViewModel = hiltViewModel(),
) {
    val instances by viewModel.instances.collectAsState()

    LaunchedEffect(Unit) { viewModel.refreshConfigs() }

    // 待确认删除的网络（非 null 时显示确认对话框）
    var pendingDelete by remember { mutableStateOf<NetworkInstance?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))

        // 空列表时中间的空态已经有一个「创建网络」按钮，顶部不再重复渲染同一个动作
        if (instances.isNotEmpty()) {
            Button(
                onClick = onCreateNetwork,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(stringResource(R.string.network_create))
            }

            Spacer(Modifier.height(12.dp))
        }

        if (instances.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.Lan,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.size(56.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.network_empty),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onCreateNetwork,
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text(stringResource(R.string.network_create))
                    }
                }
            }
        } else {
            PullToRefresh(
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    viewModel.refreshConfigs { isRefreshing = false }
                },
                pullToRefreshState = rememberPullToRefreshState(),
            ) {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = contentBottomPadding),
                ) {
                    items(instances, key = { it.instanceId }) { instance ->
                        NetworkInstanceCard(
                            instance = instance,
                            onClick = { onEditNetwork(instance.instanceId) },
                            onEdit = { onEditNetwork(instance.instanceId) },
                            onRun = { viewModel.getConfigById(instance.instanceId)?.let { viewModel.runNetwork(it) } },
                            onStop = { viewModel.stopNetwork(instance.instanceId) },
                            onDelete = { pendingDelete = instance },
                        )
                    }
                }
            }
        }
    }

    // 删除确认：删除不可撤销，运行中的网络会被连带停止
    pendingDelete?.let { target ->
        OverlayDialog(
            title = stringResource(R.string.delete_network_title),
            show = true,
            onDismissRequest = { pendingDelete = null },
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    text = if (target.running) {
                        stringResource(R.string.delete_network_running_msg, target.name)
                    } else {
                        stringResource(R.string.delete_network_msg, target.name)
                    },
                    style = MiuixTheme.textStyles.body1,
                )
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        text = stringResource(R.string.cancel),
                        onClick = { pendingDelete = null },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        text = stringResource(R.string.delete),
                        onClick = {
                            pendingDelete = null
                            viewModel.deleteNetwork(target.instanceId)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun NetworkInstanceCard(
    instance: NetworkInstance,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onRun: () -> Unit,
    onStop: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 10.dp),
        ) {
            // 主标题行：状态点 + 名称 + 运行开关（与主标题对齐）
            Row(verticalAlignment = Alignment.CenterVertically) {
                val dotColor = if (instance.running) MiuixTheme.colorScheme.primary
                    else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.4f)
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .drawBehind { drawCircle(dotColor) },
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = instance.name.ifEmpty { instance.instanceId.take(8) },
                    style = MiuixTheme.textStyles.title2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // 开关与主标题对齐；触控高度与下方按钮统一为 44dp
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .heightIn(min = 44.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Switch(
                        checked = instance.running,
                        onCheckedChange = { checked -> if (checked) onRun() else onStop() },
                    )
                }
            }

            // 副标题行：状态 · 虚拟 IP，右侧同行放编辑/删除（miuix TextButton）
            Row(
                modifier = Modifier.padding(start = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = summaryLine(instance),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.network_edit),
                    onClick = onEdit,
                    minHeight = 40.dp,
                    insideMargin = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                )
                TextButton(
                    text = stringResource(R.string.network_delete),
                    onClick = onDelete,
                    minHeight = 40.dp,
                    insideMargin = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            if (instance.errorMsg.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = instance.errorMsg,
                    color = MiuixTheme.colorScheme.error,
                    style = MiuixTheme.textStyles.body2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 22.dp),
                )
            }
        }
    }
}

/** 卡片一行式摘要：Running · 10.126.126.4/24（对端数不在主页展示，未分配 IP 时自动省略） */
@Composable
private fun summaryLine(instance: NetworkInstance): String {
    if (!instance.running) return stringResource(R.string.network_stopped)
    val parts = mutableListOf(stringResource(R.string.network_running))
    instance.detail?.myNodeInfo?.virtualIpv4?.toString()
        ?.takeIf { it.isNotBlank() && it != "0.0.0.0/0" }
        ?.let { parts.add(it) }
    return parts.joinToString("  ·  ")
}

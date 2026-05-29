package top.easytier.miuix.ui.screens.networks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import top.easytier.miuix.R
import top.easytier.miuix.data.model.NetworkInstance
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun NetworkListScreen(
    modifier: Modifier = Modifier,
    onEditNetwork: (String) -> Unit = {},
    onCreateNetwork: () -> Unit = {},
    viewModel: NetworkListViewModel = hiltViewModel(),
) {
    val instances by viewModel.instances.collectAsState()
    val selectedId by viewModel.selectedInstanceId.collectAsState()

    LaunchedEffect(Unit) { viewModel.refreshConfigs() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        // Create network button
        Button(
            onClick = onCreateNetwork,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            Text(stringResource(R.string.network_create))
        }

        Spacer(Modifier.height(12.dp))

        if (instances.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.network_empty),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(instances, key = { it.instanceId }) { instance ->
                    NetworkInstanceCard(
                        instance = instance,
                        isSelected = instance.instanceId == selectedId,
                        onSelect = { viewModel.selectNetwork(instance.instanceId) },
                        onEdit = { onEditNetwork(instance.instanceId) },
                        onRun = { viewModel.getConfigById(instance.instanceId)?.let { viewModel.runNetwork(it) } },
                        onStop = { viewModel.stopNetwork(instance.instanceId) },
                        onDelete = { viewModel.deleteNetwork(instance.instanceId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NetworkInstanceCard(
    instance: NetworkInstance,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onRun: () -> Unit,
    onStop: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onSelect,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = instance.name.ifEmpty { instance.instanceId.take(8) },
                        style = MiuixTheme.textStyles.title2,
                    )
                    Text(
                        text = if (instance.running) stringResource(R.string.network_running) else stringResource(R.string.network_stopped),
                        style = MiuixTheme.textStyles.body2,
                        color = if (instance.running) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }

                Row {
                    TextButton(text = stringResource(R.string.network_edit), onClick = onEdit)
                    Spacer(Modifier.width(4.dp))
                    if (instance.running) {
                        TextButton(text = stringResource(R.string.network_stop), onClick = onStop)
                    } else {
                        TextButton(text = stringResource(R.string.network_run), onClick = onRun)
                    }
                    Spacer(Modifier.width(4.dp))
                    TextButton(text = stringResource(R.string.network_delete), onClick = onDelete)
                }
            }

            if (instance.errorMsg.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = instance.errorMsg,
                    color = MiuixTheme.colorScheme.error,
                    style = MiuixTheme.textStyles.body2,
                )
            }
        }
    }
}

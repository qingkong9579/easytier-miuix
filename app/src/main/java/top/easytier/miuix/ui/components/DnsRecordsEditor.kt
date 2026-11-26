package top.easytier.miuix.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.easytier.miuix.R
import top.easytier.miuix.data.model.DnsRecord
import top.easytier.miuix.data.model.DnsRecordType
import top.easytier.miuix.data.model.DnsZone
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Magic DNS 静态记录编辑器（上游 2.7.0 之后新增的 `[dns]` / `[[dns.zone]]`）。
 *
 * 约束来自核心侧：`toml.rs` 在解析阶段就调用 `compile_dns()` 完整校验，
 * 任何一条记录不合规都会让**整份配置解析失败**。因此这里的编辑体验要尽量
 * 在提交前暴露问题：类型决定值格式，按类型给提示文案。
 *
 * 上游只允许每个 zone 一个 origin，多个 origin 要写多个 `[[dns.zone]]`。
 * 为了不把 3 层嵌套（zone → records → 字段）直接摊在配置页上，这里把
 * zone 当作固定一层：用 zone 原点输入 + 扁平记录列表表达。
 */
@Composable
fun DnsRecordsEditor(
    domain: String,
    onDomainChange: (String) -> Unit,
    zones: List<DnsZone>,
    onZonesChange: (List<DnsZone>) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 单 zone 编辑：绝大多数用户只需要一个原点。
    val zone = zones.firstOrNull() ?: DnsZone()
    val records = zone.records

    fun updateZone(updated: DnsZone) {
        onZonesChange(if (updated.origin.isBlank() && updated.records.isEmpty()) emptyList() else listOf(updated))
    }

    fun updateRecord(index: Int, updated: DnsRecord) {
        updateZone(
            zone.copy(
                records = records.toMutableList().apply { set(index, updated) },
            ),
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        TextField(
            value = domain,
            onValueChange = onDomainChange,
            label = stringResource(R.string.config_dns_domain),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.config_dns_domain_hint),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )

        Spacer(Modifier.height(12.dp))

        TextField(
            value = zone.origin,
            onValueChange = { updateZone(zone.copy(origin = it)) },
            label = stringResource(R.string.config_dns_zone_origin),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.config_dns_records),
            style = MiuixTheme.textStyles.body1,
        )
        Spacer(Modifier.height(4.dp))

        if (records.isEmpty()) {
            Text(
                text = stringResource(R.string.config_dns_empty),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(8.dp))
        }

        records.forEachIndexed { index, record ->
            DnsRecordCard(
                record = record,
                onChange = { updateRecord(index, it) },
                onDelete = {
                    updateZone(zone.copy(records = records.toMutableList().apply { removeAt(index) }))
                },
            )
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = {
                // 新建记录时把 zone 原点补进去，否则生成端会因缺 origin 丢弃整组记录。
                val seeded = if (zone.origin.isNotBlank()) zone else zone.copy(origin = domain)
                updateZone(seeded.copy(records = seeded.records + DnsRecord()))
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.config_dns_add_record))
        }
    }
}

@Composable
private fun DnsRecordCard(
    record: DnsRecord,
    onChange: (DnsRecord) -> Unit,
    onDelete: () -> Unit,
) {
    var showTypePicker by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = record.name.ifEmpty { stringResource(R.string.config_dns_record_name) },
                    style = MiuixTheme.textStyles.body1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = record.type.value,
                    onClick = { showTypePicker = true },
                )
                TextButton(
                    text = stringResource(R.string.config_port_forward_delete),
                    onClick = onDelete,
                )
            }

            Spacer(Modifier.height(8.dp))

            TextField(
                value = record.name,
                onValueChange = { onChange(record.copy(name = it)) },
                label = stringResource(R.string.config_dns_record_name),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            TextField(
                value = record.value,
                onValueChange = { onChange(record.copy(value = it)) },
                label = stringResource(R.string.config_dns_record_value),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(record.type.valueHintRes()),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(8.dp))
            TextField(
                value = record.ttl?.toString() ?: "",
                onValueChange = { input ->
                    onChange(record.copy(ttl = input.filter { it.isDigit() }.toIntOrNull()))
                },
                label = stringResource(R.string.config_dns_record_ttl),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showTypePicker) {
        OverlayDialog(
            title = stringResource(R.string.config_dns_record_type),
            show = true,
            onDismissRequest = { showTypePicker = false },
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                DnsRecordType.entries.chunked(3).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { type ->
                            TextButton(
                                text = type.value,
                                onClick = {
                                    onChange(record.copy(type = type))
                                    showTypePicker = false
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // 末行不足 3 个时补空位，避免按钮被拉伸成不等宽
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = { showTypePicker = false },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** 按记录类型给出值格式提示——RDATA 的 presentation 格式随类型而变。 */
private fun DnsRecordType.valueHintRes(): Int = when (this) {
    DnsRecordType.Mx -> R.string.config_dns_hint_mx
    DnsRecordType.Srv -> R.string.config_dns_hint_srv
    DnsRecordType.Txt -> R.string.config_dns_hint_txt
    else -> R.string.config_dns_hint_default
}

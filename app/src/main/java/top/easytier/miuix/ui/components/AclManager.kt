package top.easytier.miuix.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AclManager(
    aclToml: String,
    onAclTomlChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("ACL Configuration (TOML)", style = MiuixTheme.textStyles.body1)
            Spacer(Modifier.height(8.dp))

            var localToml by remember(aclToml) { mutableStateOf(aclToml) }

            TextField(
                value = localToml,
                onValueChange = { localToml = it },
                label = "ACL Rules (TOML format)",
                modifier = Modifier.fillMaxWidth().height(200.dp),
            )

            Spacer(Modifier.height(8.dp))

            TextButton(
                text = "Apply",
                onClick = { onAclTomlChange(localToml) },
            )
        }
    }
}

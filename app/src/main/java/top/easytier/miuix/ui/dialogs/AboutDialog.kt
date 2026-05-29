package top.easytier.miuix.ui.dialogs

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import top.easytier.miuix.R
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val githubUrl = stringResource(R.string.about_github_url)

    OverlayDialog(
        title = stringResource(R.string.about_title),
        show = true,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = "EasyTier",
                modifier = Modifier.size(72.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "EasyTier",
                style = MiuixTheme.textStyles.headline1,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.about_version, "1.1.0"),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.about_description),
                style = MiuixTheme.textStyles.body1,
            )
            Spacer(Modifier.height(16.dp))
            TextButton(
                text = stringResource(R.string.about_github),
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl))
                    context.startActivity(intent)
                },
            )
            Spacer(Modifier.height(4.dp))
            TextButton(
                text = stringResource(R.string.ok),
                onClick = onDismiss,
            )
        }
    }
}

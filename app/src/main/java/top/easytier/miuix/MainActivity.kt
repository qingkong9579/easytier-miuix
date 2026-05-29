package top.easytier.miuix

import android.content.Intent
import android.content.SharedPreferences
import android.net.VpnService
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.os.LocaleListCompat
import dagger.hilt.android.AndroidEntryPoint
import top.easytier.miuix.data.repository.RealNetworkRepository
import top.easytier.miuix.ui.theme.AppLanguage
import top.easytier.miuix.ui.theme.AppSettings
import top.easytier.miuix.ui.theme.AppTheme
import top.easytier.miuix.ui.AppNavigation
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var repository: RealNetworkRepository

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences("app_settings", MODE_PRIVATE)
    }

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Log.i("MainActivity", "VPN permission granted")
            repository.onVpnPermissionGranted()
        } else {
            Log.w("MainActivity", "VPN permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Apply saved language
        applySavedLanguage()

        // Pre-request VPN permission
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            vpnPermissionLauncher.launch(prepareIntent)
        }

        setContent {
            MainContent(
                prefs = prefs,
                onLanguageChanged = this::onLanguageChanged,
                vpnPermissionFlow = repository.vpnPermissionNeeded,
                vpnLauncher = vpnPermissionLauncher,
            )
        }
    }

    private fun applySavedLanguage() {
        val langCode = prefs.getString("language", "") ?: ""
        val localeList = if (langCode.isNotEmpty()) {
            LocaleListCompat.create(Locale.forLanguageTag(langCode))
        } else {
            LocaleListCompat.getEmptyLocaleList()
        }
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    private fun onLanguageChanged(language: AppLanguage) {
        prefs.edit().putString("language", language.code).apply()
        val localeList = if (language.code.isNotEmpty()) {
            LocaleListCompat.create(Locale.forLanguageTag(language.code))
        } else {
            LocaleListCompat.getEmptyLocaleList()
        }
        AppCompatDelegate.setApplicationLocales(localeList)
    }
}

@Composable
fun MainContent(
    prefs: SharedPreferences = throw IllegalStateException("prefs not provided"),
    onLanguageChanged: (AppLanguage) -> Unit = {},
    vpnPermissionFlow: kotlinx.coroutines.flow.MutableStateFlow<Intent?> =
        kotlinx.coroutines.flow.MutableStateFlow(null),
    vpnLauncher: androidx.activity.result.ActivityResultLauncher<Intent>? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? ComponentActivity

    val savedAppSettings = remember {
        val code = prefs.getString("language", "") ?: ""
        AppSettings(
            language = when (code) {
                "en" -> AppLanguage.ENGLISH
                "zh" -> AppLanguage.CHINESE
                else -> AppLanguage.SYSTEM
            },
            enableBlur = prefs.getBoolean("enableBlur", true),
            enableFloatingBottomBar = prefs.getBoolean("enableFloatingBottomBar", false),
            enableFloatingBottomBarBlur = prefs.getBoolean("enableFloatingBottomBarBlur", false),
            colorMode = try {
                top.easytier.miuix.ui.theme.ColorMode.valueOf(prefs.getString("colorMode", "") ?: "")
            } catch (_: Exception) {
                top.easytier.miuix.ui.theme.ColorMode.MONET_SYSTEM
            },
            keyColor = prefs.getInt("keyColor", 0),
        )
    }
    var appSettings by remember { mutableStateOf(savedAppSettings) }

    AppTheme(appSettings = appSettings) {
        AppNavigation(
            appSettings = appSettings,
            onSettingsChange = { newSettings ->
                val oldLanguage = appSettings.language
                appSettings = newSettings
                prefs.edit()
                    .putString("language", newSettings.language.code)
                    .putBoolean("enableBlur", newSettings.enableBlur)
                    .putBoolean("enableFloatingBottomBar", newSettings.enableFloatingBottomBar)
                    .putBoolean("enableFloatingBottomBarBlur", newSettings.enableFloatingBottomBarBlur)
                    .putString("colorMode", newSettings.colorMode.name)
                    .putInt("keyColor", newSettings.keyColor)
                    .apply()
                if (newSettings.language != oldLanguage) {
                    onLanguageChanged(newSettings.language)
                }
            },
            onExitApp = {
                activity?.finish()
            },
        )
    }

    // Watch for VPN permission requests from repository
    val vpnIntent by vpnPermissionFlow.collectAsState()
    LaunchedEffect(vpnIntent) {
        vpnIntent?.let { intent ->
            vpnLauncher?.launch(intent)
        }
    }
}

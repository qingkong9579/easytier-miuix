package top.easytier.miuix

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.net.VpnService
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dagger.hilt.android.AndroidEntryPoint
import top.easytier.miuix.data.repository.RealNetworkRepository
import top.easytier.miuix.ui.theme.AppLanguage
import top.easytier.miuix.ui.theme.AppSettings
import top.easytier.miuix.ui.theme.AppTheme
import top.easytier.miuix.ui.AppNavigation
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var repository: RealNetworkRepository

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences("app_settings", Context.MODE_PRIVATE)
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

    override fun attachBaseContext(newBase: Context) {
        val langCode = newBase.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .getString("language", "") ?: ""
        val locale = if (langCode.isNotEmpty()) Locale(langCode) else Locale.getDefault()
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Pre-request VPN permission
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            vpnPermissionLauncher.launch(prepareIntent)
        }

        setContent {
            MainContent(
                prefs = prefs,
                vpnPermissionFlow = repository.vpnPermissionNeeded,
                vpnLauncher = vpnPermissionLauncher,
            )
        }
    }

    fun saveLanguage(language: AppLanguage) {
        prefs.edit().putString("language", language.code).apply()

        val locale = if (language.code.isEmpty()) {
            Locale.getDefault()
        } else {
            Locale(language.code)
        }
        Locale.setDefault(locale)
        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    companion object {
        fun setLocale(context: Context, language: AppLanguage) {
            val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            prefs.edit().putString("language", language.code).apply()

            val locale = if (language.code.isEmpty()) {
                Locale.getDefault()
            } else {
                Locale(language.code)
            }
            Locale.setDefault(locale)
            val config = Configuration(context.resources.configuration)
            config.setLocale(locale)
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
        }
    }
}

@Composable
fun MainContent(
    prefs: SharedPreferences = throw IllegalStateException("prefs not provided"),
    vpnPermissionFlow: kotlinx.coroutines.flow.MutableStateFlow<Intent?> =
        kotlinx.coroutines.flow.MutableStateFlow(null),
    vpnLauncher: androidx.activity.result.ActivityResultLauncher<Intent>? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? ComponentActivity

    val savedLanguage = remember {
        val code = prefs.getString("language", "") ?: ""
        when (code) {
            "en" -> AppLanguage.ENGLISH
            "zh" -> AppLanguage.CHINESE
            else -> AppLanguage.SYSTEM
        }
    }
    var appSettings by remember { mutableStateOf(AppSettings(language = savedLanguage)) }

    AppTheme(appSettings = appSettings) {
        AppNavigation(
            appSettings = appSettings,
            onSettingsChange = { newSettings ->
                appSettings = newSettings
                // Save language preference when it changes
                if (newSettings.language != savedLanguage) {
                    MainActivity.setLocale(context, newSettings.language)
                    activity?.recreate()
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

package top.easytier.miuix.jni

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import top.easytier.miuix.MainActivity
import top.easytier.miuix.R
import top.easytier.miuix.data.repository.RealNetworkRepository
import javax.inject.Inject

/**
 * 快捷设置磁贴：一键启停最近运行的 EasyTier 网络。
 * 运行中点击即停止；未运行时启动最近使用的配置，
 * 若缺少 VPN 权限则跳转到主界面授权。
 */
@AndroidEntryPoint
class EasyTierTileService : TileService() {

    companion object {
        private const val TAG = "EasyTierTileService"
    }

    @Inject lateinit var repository: RealNetworkRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onStartListening() {
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (repository.isAnyNetworkRunning) {
            val runningId = repository.runningInstanceId
            Log.i(TAG, "Tile: stopping instance $runningId")
            if (runningId != null) {
                serviceScope.launch { repository.stopNetworkInstance(runningId) }
            }
        } else {
            val config = repository.getLastRunConfig()
            if (config == null) {
                Log.i(TAG, "Tile: no network config available, opening app")
                openApp()
                return
            }
            val prepareIntent = VpnService.prepare(applicationContext)
            if (prepareIntent != null) {
                Log.i(TAG, "Tile: VPN permission missing, opening app for grant")
                openApp()
                return
            }
            Log.i(TAG, "Tile: starting last config ${config.instanceId}")
            serviceScope.launch { repository.runNetworkInstance(config) }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val running = repository.isAnyNetworkRunning
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        val label = repository.getLastRunConfig()?.networkName
        tile.subtitle = when {
            running -> label
            label != null -> label
            else -> getString(R.string.tile_no_config)
        }
        tile.updateTile()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}

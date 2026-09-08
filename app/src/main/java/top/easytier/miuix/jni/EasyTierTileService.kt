package top.easytier.miuix.jni

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import top.easytier.miuix.R
import top.easytier.miuix.data.repository.RealNetworkRepository
import javax.inject.Inject

/**
 * 快捷设置磁贴（对齐上游 easytier #2511 模式）：
 * onClick 只把 start/stop 意图同步持久化（TileService 可能在 onClick 返回后立即被回收，
 * 协程/延时工作都不可靠），随后：
 * - 进程存活 → 走与进程同寿的作用域直接启停（上次运行的配置）；
 * - 冷启动/需授权 → 拉起主界面，由 MainActivity 消费 pending action 完成启动。
 * 运行状态以 VpnService 静态标志 + repository 持久化标志为准，与应用内状态同源。
 */
@AndroidEntryPoint
class EasyTierTileService : TileService() {

    @Inject lateinit var repository: RealNetworkRepository

    companion object {
        private const val TAG = "EasyTierTileService"
        private const val PREFS_NAME = "easytier_tile"
        private const val KEY_PENDING_ACTION = "pending_action"
        private const val KEY_PENDING_TIME = "pending_time"
        private const val PENDING_TTL_MS = 10_000L
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        const val EXTRA_NAVIGATE_TO = "navigate_to"
        const val NAVIGATE_CREATE_NETWORK = "create_network"

        /** 当前绑定的服务实例：状态变化时直接刷新其磁贴 */
        @Volatile
        private var activeInstance: EasyTierTileService? = null

        /** 读取并清除待处理动作（同步 commit）。超过 TTL 的残留意图视为过期，直接丢弃 */
        @Synchronized
        fun consumePendingAction(context: Context): String? {
            val action = peekPendingAction(context) ?: return null
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_PENDING_ACTION)
                .remove(KEY_PENDING_TIME)
                .commit()
            return action
        }

        /** 状态变化时请求刷新磁贴：已绑定则直接刷新实例，未绑定则请求系统 bind 后回调 */
        fun requestStateUpdate(context: Context) {
            val instance = activeInstance
            if (instance != null) {
                instance.refreshState()
            } else {
                requestListeningState(context, ComponentName(context, EasyTierTileService::class.java))
            }
        }

        private fun pendingAction(context: Context): String? = peekPendingAction(context)

        private fun peekPendingAction(context: Context): String? {
            val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val action = preferences.getString(KEY_PENDING_ACTION, null) ?: return null
            val age = android.os.SystemClock.elapsedRealtime() -
                preferences.getLong(KEY_PENDING_TIME, 0)
            return if (age in 0..PENDING_TTL_MS) action else null
        }

        private fun savePendingAction(context: Context, action: String) {
            // TileService 可能在 onClick 返回后立刻被回收，必须同步落盘
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PENDING_ACTION, action)
                .putLong(KEY_PENDING_TIME, android.os.SystemClock.elapsedRealtime())
                .commit()
        }
    }

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
    }

    override fun onDestroy() {
        if (activeInstance === this) activeInstance = null
        super.onDestroy()
    }

    override fun onStartListening() {
        // SystemUI 回调 onStartListening 时 qsTile 可能尚未绑定，post 一帧后再刷新
        refreshState()
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) {
            unlockAndRun(::handleClick)
        } else {
            handleClick()
        }
    }

    /** 在主线程刷新磁贴状态；qsTile 未就绪时短重试，避免刷新被静默丢弃 */
    fun refreshState(retry: Int = 0) {
        val tile = qsTile
        if (tile == null) {
            Log.w(TAG, "refreshState: qsTile null (retry=$retry)")
            if (retry < 5) {
                Handler(Looper.getMainLooper()).postDelayed({ refreshState(retry + 1) }, 200)
            }
            return
        }
        // 与应用内实例状态同源：持久化运行标志 + VPN 服务存活标志
        val running = EasyTierVpnService.isServiceRunning || repository.isTileRunning
        Log.i(TAG, "updateTileState: running=$running retry=$retry")
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = repository.getLastRunConfig()?.networkName
            ?: getString(R.string.tile_no_config)
        tile.updateTile()
    }

    private fun handleClick() {
        // 运行状态以 VpnService 静态标志为准：进程死亡即视为停止（核心随进程死亡）
        val running = EasyTierVpnService.isServiceRunning
        val action = pendingAction(this) ?: if (running) ACTION_STOP else ACTION_START
        savePendingAction(this, action)
        refreshState()
        Log.i(TAG, "Tile click -> action=$action running=$running")

        when (action) {
            ACTION_STOP -> {
                if (running) {
                    // 走与进程同寿的作用域，防止 TileService 回收导致停止动作被取消
                    val id = repository.runningInstanceId
                    if (id != null) {
                        repository.stopNetworkInstanceAsync(id)
                    } else {
                        // 兜底：仅停 VPN 服务（核心随进程或实例清理）
                        stopService(Intent(this, EasyTierVpnService::class.java))
                    }
                }
                consumePendingAction(this)
            }
            ACTION_START -> {
                // 有配置：直接启动上一次运行（或最后创建）的配置
                val config = repository.getLastRunConfig()
                if (config == null) {
                    // 无任何配置：直接跳转创建网络页
                    Log.i(TAG, "Tile: no config, jumping to create network")
                    consumePendingAction(this)
                    openApp(navigateToCreate = true)
                    return
                }
                if (VpnService.prepare(applicationContext) != null) {
                    // 未授权：保留 pending，主界面授权流程结束后可自动续启
                    Log.i(TAG, "Tile: VPN permission missing, opening app")
                    openApp()
                    return
                }
                Log.i(TAG, "Tile: starting last config ${config.instanceId}")
                repository.startNetworkInstanceAsync(config)
                // 动作已交给进程内作用域执行，清除兜底意图（失败由 openApp 兜底）
                consumePendingAction(this)
            }
        }
    }

    private fun openApp(navigateToCreate: Boolean = false) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (navigateToCreate) putExtra(EXTRA_NAVIGATE_TO, NAVIGATE_CREATE_NETWORK)
        } ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}

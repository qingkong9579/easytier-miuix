package top.easytier.miuix.ui.screens.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.easytier.miuix.data.model.NetworkConfig
import top.easytier.miuix.data.model.normalizeNetworkConfig
import top.easytier.miuix.data.repository.NetworkRepository
import javax.inject.Inject

/** 「保存并运行」的 UI 状态：saving 中不响应重复点击，结束后由界面消费结果 */
data class SaveRunState(
    val saving: Boolean = false,
    val finished: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ConfigViewModel @Inject constructor(
    private val repository: NetworkRepository,
) : ViewModel() {

    private val _config = MutableStateFlow(NetworkConfig())
    val config: StateFlow<NetworkConfig> = _config.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _saveRun = MutableStateFlow(SaveRunState())
    val saveRun: StateFlow<SaveRunState> = _saveRun.asStateFlow()

    fun loadConfig(instanceId: String) {
        viewModelScope.launch {
            val configs = repository.loadConfigs()
            _config.value = configs.find { it.instanceId == instanceId }
                ?: NetworkConfig(instanceId = instanceId)
        }
    }

    fun updateConfig(update: (NetworkConfig) -> NetworkConfig) {
        _config.value = update(_config.value)
    }

    fun saveAndRun() {
        if (_saveRun.value.saving) return
        viewModelScope.launch {
            _saveRun.value = SaveRunState(saving = true)
            val normalized = normalizeNetworkConfig(_config.value)
            _config.value = normalized
            val configs = repository.loadConfigs().toMutableList()
            val index = configs.indexOfFirst { it.instanceId == normalized.instanceId }
            if (index >= 0) configs[index] = normalized else configs.add(normalized)
            // NonCancellable：避免界面中途退出时配置写入/实例启动被取消到一半
            withContext(NonCancellable) {
                repository.saveConfigs(configs)
            }
            val error = withContext(NonCancellable) {
                repository.runNetworkInstance(normalized)
            }
            _saveRun.value = SaveRunState(finished = true, error = error)
        }
    }

    /** 仅保存配置；落盘完成后回调 onSaved */
    fun saveConfig(onSaved: () -> Unit) {
        viewModelScope.launch {
            val normalized = normalizeNetworkConfig(_config.value)
            _config.value = normalized
            val configs = repository.loadConfigs().toMutableList()
            val index = configs.indexOfFirst { it.instanceId == normalized.instanceId }
            if (index >= 0) configs[index] = normalized else configs.add(normalized)
            withContext(NonCancellable) {
                repository.saveConfigs(configs)
            }
            onSaved()
        }
    }

    /** 消费保存运行结果（失败弹窗关闭后调用） */
    fun consumeSaveRun() {
        _saveRun.value = SaveRunState()
    }
}

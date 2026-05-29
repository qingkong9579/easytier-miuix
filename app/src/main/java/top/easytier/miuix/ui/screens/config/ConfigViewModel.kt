package top.easytier.miuix.ui.screens.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.easytier.miuix.data.model.NetworkConfig
import top.easytier.miuix.data.model.NetworkingMethod
import top.easytier.miuix.data.model.PortForwardConfig
import top.easytier.miuix.data.model.normalizeNetworkConfig
import top.easytier.miuix.data.repository.NetworkRepository
import javax.inject.Inject

@HiltViewModel
class ConfigViewModel @Inject constructor(
    private val repository: NetworkRepository,
) : ViewModel() {

    private val _config = MutableStateFlow(NetworkConfig())
    val config: StateFlow<NetworkConfig> = _config.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    fun loadConfig(instanceId: String) {
        viewModelScope.launch {
            val configs = repository.loadConfigs()
            _config.value = configs.find { it.instanceId == instanceId }
                ?: NetworkConfig(instanceId = instanceId)
        }
    }

    fun updateConfig(update: (NetworkConfig) -> NetworkConfig) {
        val newConfig = update(_config.value)
        android.util.Log.d("ConfigViewModel", "updateConfig: networkName=${newConfig.networkName}, peerUrls=${newConfig.peerUrls}")
        _config.value = newConfig
    }

    fun saveAndRun() {
        viewModelScope.launch {
            val normalized = normalizeNetworkConfig(_config.value)
            android.util.Log.d("ConfigViewModel", "saveAndRun: networkName=${normalized.networkName}, instanceName=${normalized.instanceName}, networkSecret=${normalized.networkSecret}, peerUrls=${normalized.peerUrls}, dhcp=${normalized.dhcp}, virtualIpv4=${normalized.virtualIpv4}")
            _config.value = normalized
            val configs = repository.loadConfigs().toMutableList()
            val index = configs.indexOfFirst { it.instanceId == normalized.instanceId }
            if (index >= 0) configs[index] = normalized else configs.add(normalized)
            repository.saveConfigs(configs)
            repository.runNetworkInstance(normalized)
        }
    }

    fun saveConfig() {
        viewModelScope.launch {
            val normalized = normalizeNetworkConfig(_config.value)
            _config.value = normalized
            val configs = repository.loadConfigs().toMutableList()
            val index = configs.indexOfFirst { it.instanceId == normalized.instanceId }
            if (index >= 0) configs[index] = normalized else configs.add(normalized)
            repository.saveConfigs(configs)
        }
    }
}

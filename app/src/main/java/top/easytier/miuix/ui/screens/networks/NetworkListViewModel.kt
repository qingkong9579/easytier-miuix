package top.easytier.miuix.ui.screens.networks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.easytier.miuix.data.model.NetworkConfig
import top.easytier.miuix.data.model.NetworkInstance
import top.easytier.miuix.data.repository.NetworkRepository
import javax.inject.Inject

@HiltViewModel
class NetworkListViewModel @Inject constructor(
    private val repository: NetworkRepository,
) : ViewModel() {

    val instances: StateFlow<List<NetworkInstance>> = repository.getAllNetworkInstances()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _configs = MutableStateFlow<List<NetworkConfig>>(emptyList())
    val configs: StateFlow<List<NetworkConfig>> = _configs.asStateFlow()

    private val _selectedInstanceId = MutableStateFlow<String?>(null)
    val selectedInstanceId: StateFlow<String?> = _selectedInstanceId.asStateFlow()

    init {
        viewModelScope.launch {
            _configs.value = repository.loadConfigs()
        }
    }

    fun selectNetwork(instanceId: String) {
        _selectedInstanceId.value = instanceId
    }

    fun refreshConfigs() {
        viewModelScope.launch {
            _configs.value = repository.loadConfigs()
        }
    }

    fun deleteNetwork(instanceId: String) {
        _configs.value = _configs.value.filter { it.instanceId != instanceId }
        if (_selectedInstanceId.value == instanceId) {
            _selectedInstanceId.value = _configs.value.firstOrNull()?.instanceId
        }
        viewModelScope.launch {
            repository.saveConfigs(_configs.value)
            repository.deleteNetworkInstance(instanceId)
        }
    }

    fun runNetwork(config: NetworkConfig) {
        viewModelScope.launch { repository.runNetworkInstance(config) }
    }

    fun stopNetwork(instanceId: String) {
        viewModelScope.launch { repository.stopNetworkInstance(instanceId) }
    }

    fun getConfigById(instanceId: String): NetworkConfig? {
        return _configs.value.find { it.instanceId == instanceId }
    }
}

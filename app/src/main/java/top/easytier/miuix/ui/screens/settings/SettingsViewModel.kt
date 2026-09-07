package top.easytier.miuix.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.easytier.miuix.data.repository.RealNetworkRepository
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: RealNetworkRepository,
) : ViewModel() {

    private val _configServerUrl = MutableStateFlow(repository.getConfigServerUrl())
    val configServerUrl: StateFlow<String?> = _configServerUrl.asStateFlow()

    val configServerConnected: StateFlow<Boolean> = repository.configServerConnected

    /** 连接配置服务器；结果通过回调返回（null 成功，否则错误信息） */
    fun connectConfigServer(url: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val error = withContext(Dispatchers.Default) {
                repository.connectConfigServer(url)
            }
            _configServerUrl.value = repository.getConfigServerUrl()
            onResult(error)
        }
    }

    fun disconnectConfigServer() {
        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                repository.disconnectConfigServer()
            }
            _configServerUrl.value = null
        }
    }
}

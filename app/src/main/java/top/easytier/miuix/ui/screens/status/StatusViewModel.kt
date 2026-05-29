package top.easytier.miuix.ui.screens.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.easytier.miuix.data.model.NetworkInstance
import top.easytier.miuix.data.model.PeerRoutePair
import top.easytier.miuix.data.repository.NetworkRepository
import javax.inject.Inject

@HiltViewModel
class StatusViewModel @Inject constructor(
    private val repository: NetworkRepository,
) : ViewModel() {

    private val _currentInstance = MutableStateFlow<NetworkInstance?>(null)
    val currentInstance: StateFlow<NetworkInstance?> = _currentInstance.asStateFlow()

    private val _txRate = MutableStateFlow("0 B/s")
    val txRate: StateFlow<String> = _txRate.asStateFlow()

    private val _rxRate = MutableStateFlow("0 B/s")
    val rxRate: StateFlow<String> = _rxRate.asStateFlow()

    private val _txHistory = MutableStateFlow<List<Long>>(emptyList())
    val txHistory: StateFlow<List<Long>> = _txHistory.asStateFlow()

    private val _rxHistory = MutableStateFlow<List<Long>>(emptyList())
    val rxHistory: StateFlow<List<Long>> = _rxHistory.asStateFlow()

    private var prevTxSum = 0L
    private var prevRxSum = 0L
    private var lastRunningId: String? = null

    init {
        viewModelScope.launch {
            repository.getAllNetworkInstances().collect { instances ->
                val running = instances.firstOrNull { it.running }
                _currentInstance.value = running

                // Reset counters and history when instance changes or stops
                val runningId = running?.instanceId
                if (runningId != lastRunningId) {
                    prevTxSum = 0L
                    prevRxSum = 0L
                    _txHistory.value = emptyList()
                    _rxHistory.value = emptyList()
                    lastRunningId = runningId
                }

                val detail = running?.detail ?: run {
                    _txRate.value = "0 B/s"
                    _rxRate.value = "0 B/s"
                    return@collect
                }
                val curTxSum = detail.peers.flatMap { it.conns }.sumOf { it.stats?.txBytes ?: 0 }
                val curRxSum = detail.peers.flatMap { it.conns }.sumOf { it.stats?.rxBytes ?: 0 }

                if (prevTxSum > 0 && curTxSum >= prevTxSum && curRxSum >= prevRxSum) {
                    _txRate.value = humanFileSize(curTxSum - prevTxSum) + "/s"
                    _rxRate.value = humanFileSize(curRxSum - prevRxSum) + "/s"
                    _txHistory.value = (_txHistory.value + (curTxSum - prevTxSum)).takeLast(60)
                    _rxHistory.value = (_rxHistory.value + (curRxSum - prevRxSum)).takeLast(60)
                }
                prevTxSum = curTxSum
                prevRxSum = curRxSum
            }
        }
    }

    fun getPeerRoutePairs(): List<PeerRoutePair> {
        return _currentInstance.value?.detail?.peerRoutePairs ?: emptyList()
    }

    private fun humanFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1f MB".format(mb)
        val gb = mb / 1024.0
        return "%.1f GB".format(gb)
    }
}

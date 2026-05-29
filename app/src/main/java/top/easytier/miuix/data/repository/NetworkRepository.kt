package top.easytier.miuix.data.repository

import kotlinx.coroutines.flow.Flow
import top.easytier.miuix.data.model.Mode
import top.easytier.miuix.data.model.NetworkConfig
import top.easytier.miuix.data.model.NetworkInstance

interface NetworkRepository {
    fun getNetworkInstanceIds(): Flow<List<String>>
    fun getNetworkInstance(instanceId: String): Flow<NetworkInstance?>
    fun getAllNetworkInstances(): Flow<List<NetworkInstance>>
    suspend fun loadConfigs(): List<NetworkConfig>
    suspend fun saveConfigs(configs: List<NetworkConfig>)
    suspend fun runNetworkInstance(config: NetworkConfig)
    suspend fun stopNetworkInstance(instanceId: String)
    suspend fun deleteNetworkInstance(instanceId: String)
    suspend fun collectNetworkInfo(instanceId: String): NetworkInstance?
    fun getCurrentMode(): Flow<Mode>
    suspend fun setMode(mode: Mode)
    suspend fun startClient(mode: Mode)
    suspend fun stopClient()
    fun isClientRunning(): Flow<Boolean>
}

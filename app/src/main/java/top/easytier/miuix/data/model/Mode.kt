package top.easytier.miuix.data.model

sealed class Mode {
    data class Normal(
        val rpcPortal: String? = null,
        val enableRpcPortListen: Boolean = false,
        val rpcListenPort: Int = 15999,
        val configServerUrl: String? = null,
    ) : Mode()

    data class Service(
        val configDir: String = "",
        val rpcPortal: String = "127.0.0.1:15999",
        val fileLogLevel: String = "off",
        val fileLogDir: String = "",
        val configServerUrl: String? = null,
        val installedCoreVersion: String? = null,
    ) : Mode()

    data class Remote(
        val remoteRpcAddress: String = "tcp://127.0.0.1:15999",
    ) : Mode()
}

package com.easytier.jni

import android.util.Log

fun interface ConfigServerEventCallback {
    fun onEvent(eventJson: String)
}

/**
 * EasyTier JNI 接口类，提供 Android 应用调用 EasyTier 核心网络功能的接口。
 *
 * 自上游 #2451（portable core 与 native runtime 分离）起，
 * 核心已静态链接进 libeasytier_android_jni.so，不再需要单独加载 libeasytier_ffi.so。
 */
object EasyTierJNI {

    private const val TAG = "EasyTierJNI"
    var isNativeLoaded = false
        private set

    init {
        try {
            System.loadLibrary("easytier_android_jni")
            isNativeLoaded = true
            Log.i(TAG, "Native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library: ${e.message}")
            isNativeLoaded = false
        }
    }

    /** 设置 TUN 文件描述符，0 成功 / -1 失败 */
    @JvmStatic external fun setTunFd(instanceName: String, fd: Int): Int

    /** 校验 TOML 配置字符串，0 成功 / -1 失败 */
    @JvmStatic external fun parseConfig(config: String): Int

    /** 运行网络实例（TOML 配置），0 成功 / -1 失败 */
    @JvmStatic external fun runNetworkInstance(config: String): Int

    /** 保留指定实例并停止其余实例；传 null 或空数组停止全部 */
    @JvmStatic external fun retainNetworkInstance(instanceNames: Array<String>?): Int

    /** 停止单个指定实例，不影响其他实例；实例不存在时为 no-op */
    @JvmStatic external fun deleteNetworkInstance(instanceName: String): Int

    /** 收集运行中网络信息，返回 JSON 字符串 */
    @JvmStatic external fun collectNetworkInfos(maxLength: Int): String?

    /** 列出运行中实例名与实例 ID，返回 JSON 对象（name -> id） */
    @JvmStatic external fun listInstances(maxLength: Int): String?

    /**
     * 调用暴露的 EasyTier RPC 方法，输入输出均为 protobuf JSON 字符串。
     * 不支持实例生命周期管理类 RPC（请用上面的专用 API）。
     */
    @JvmStatic
    external fun callJsonRpc(
        serviceName: String,
        methodName: String,
        domainName: String?,
        payloadJson: String,
    ): String?

    @JvmStatic
    fun callJsonRpc(serviceName: String, methodName: String, payloadJson: String): String? =
        callJsonRpc(serviceName, methodName, null, payloadJson)

    /** 启动配置服务器客户端，远程配置应用/删除事件通过回调送达 */
    @JvmStatic
    external fun startConfigServerClient(
        url: String,
        hostname: String?,
        machineId: String,
        secureMode: Boolean,
        callback: ConfigServerEventCallback?,
    ): Int

    /** 停止配置服务器客户端 */
    @JvmStatic external fun stopConfigServerClient(): Int

    /** 查询配置服务器客户端是否已连接 */
    @JvmStatic external fun isConfigServerClientConnected(): Boolean

    /** 获取最后的错误消息，无错误时返回 null */
    @JvmStatic external fun getLastError(): String?

    @JvmStatic
    fun stopAllInstances(): Int {
        if (!isNativeLoaded) return -1
        return retainNetworkInstance(null)
    }

    @JvmStatic
    fun retainSingleInstance(instanceName: String): Int {
        if (!isNativeLoaded) return -1
        return retainNetworkInstance(arrayOf(instanceName))
    }

    @JvmStatic
    fun deleteInstance(instanceName: String): Int {
        if (!isNativeLoaded) return -1
        return deleteNetworkInstance(instanceName)
    }
}

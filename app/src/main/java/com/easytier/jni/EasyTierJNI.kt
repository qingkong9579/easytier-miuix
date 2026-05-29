package com.easytier.jni

import android.util.Log

object EasyTierJNI {

    private const val TAG = "EasyTierJNI"
    var isNativeLoaded = false
        private set

    init {
        try {
            System.loadLibrary("easytier_ffi")
            System.loadLibrary("easytier_android_jni")
            isNativeLoaded = true
            Log.i(TAG, "Native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library: ${e.message}")
            isNativeLoaded = false
        }
    }

    @JvmStatic external fun setTunFd(instanceName: String, fd: Int): Int

    @JvmStatic external fun parseConfig(config: String): Int

    @JvmStatic external fun runNetworkInstance(config: String): Int

    @JvmStatic external fun retainNetworkInstance(instanceNames: Array<String>?): Int

    @JvmStatic external fun collectNetworkInfos(maxLength: Int): String?

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
}

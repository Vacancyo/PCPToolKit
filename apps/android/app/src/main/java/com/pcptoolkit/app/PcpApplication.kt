/**
 * PCP ToolKit
 * Copyright (C) 2026 VacancyoTP
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * @file PcpApplication.kt
 * @brief 应用进程入口与沙箱引擎初始化。
 */
package com.pcptoolkit.app

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.app.configuration.ClientConfiguration

/**
 * @brief 初始化 PCP 沙箱运行时。
 */
class PcpApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        val processName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            base.packageName
        }
        Log.i(TAG, "attach process=$processName")
        runCatching {
            BlackBoxCore.get().closeCodeInit()
            BlackBoxCore.get().onBeforeMainApplicationAttach(this, base)
            BlackBoxCore.get().doAttachBaseContext(
                base,
                object : ClientConfiguration() {
                    override fun getHostPackageName(): String = base.packageName

                    override fun getLogSenderChatId(): String? = null
                }
            )
            BlackBoxCore.get().onAfterMainApplicationAttach(this, base)
        }.onFailure {
            Log.e(TAG, "Unable to attach sandbox runtime", it)
        }
    }

    override fun onCreate() {
        super.onCreate()
        runCatching {
            BlackBoxCore.get().doCreate()
            val processName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Application.getProcessName()
            } else {
                packageName
            }
            Log.i(TAG, "sandbox initialized process=$processName")
        }.onFailure {
            Log.e(TAG, "Unable to initialize sandbox runtime", it)
        }
    }

    private companion object {
        const val TAG = "PcpApplication"
    }
}

/**
 * PCP ToolKit
 * Copyright (C) 2026 VacancyoTP
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <https://www.gnu.org/licenses/>.
 *
 * @file ScriptEngine.kt
 * @brief 脚本执行接口。
 *
 * 该代码文件声明脚本运行宿主和原生脚本执行入口，并转发脚本运行结果。
 */
package com.pcptoolkit.app.feature.script

/**
 * @brief 原生模板匹配返回的结果。
 */
data class MatchResult(
    val found: Boolean,
    val left: Double,
    val top: Double,
    val width: Double,
    val height: Double,
    val score: Double
)

/**
 * @brief 脚本运行时所需的宿主能力。
 */
interface ScriptHost {
    val isCancelled: Boolean

    /**
     * @brief 获取脚本宿主当前毫秒时间。
     * @return 当前毫秒时间。
     */
    fun nowMillis(): Long

    /**
     * @brief 输出脚本日志。
     * @param message 日志文本。
     */
    fun log(message: String)

    /**
     * @brief 让脚本线程休眠。
     * @param milliseconds 休眠毫秒数。
     */
    fun sleep(milliseconds: Long)

    /**
     * @brief 执行归一化坐标单击。
     * @param x 横坐标。
     * @param y 纵坐标。
     */
    fun tap(x: Double, y: Double)

    /**
     * @brief 执行归一化坐标滑动。
     * @param x1 起点横坐标。
     * @param y1 起点纵坐标。
     * @param x2 终点横坐标。
     * @param y2 终点纵坐标。
     * @param durationMillis 滑动持续时间，单位为毫秒。
     */
    fun swipe(x1: Double, y1: Double, x2: Double, y2: Double, durationMillis: Long)

    /**
     * @brief 在归一化屏幕区域中查找模板图像。
     * @param imageAssetPath 模板图像资源路径。
     * @param left 区域左侧坐标。
     * @param top 区域顶部坐标。
     * @param right 区域右侧坐标。
     * @param bottom 区域底部坐标。
     * @return 模板匹配结果。
     */
    fun match(
        imageAssetPath: String,
        left: Double,
        top: Double,
        right: Double,
        bottom: Double
    ): MatchResult
}

/**
 * @brief 脚本执行失败时抛出的异常。
 * @param message 错误说明。
 */
class ScriptException(message: String) : Exception(message)

/**
 * @brief 提供 PCS 脚本原生执行入口。
 */
object ScriptEngine {
    init {
        System.loadLibrary("pcpjni")
    }

    /**
     * @brief 调用原生脚本执行接口。
     * @param source PCS 脚本源码。
     * @param host 脚本宿主。
     * @return 成功返回空，失败返回错误文本。
     */
    private external fun executeNative(source: String, host: ScriptHost): String?

    /**
     * @brief 执行 PCS 脚本并向宿主报告错误。
     * @param source PCS 脚本源码。
     * @param host 脚本宿主。
     */
    fun execute(source: String, host: ScriptHost) {
        executeNative(source, host)?.let { throw ScriptException(it) }
    }
}

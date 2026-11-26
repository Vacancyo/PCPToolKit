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
 * @file NativeBridge.kt
 * @brief C++ 核心库 Kotlin 入口。
 *
 * 该代码文件声明多语言、RTON 转换和原生库加载相关接口，并封装转换进度回调。
 */
package com.pcptoolkit.app.core.native

/**
 * @brief 转换进度回调。在调用 [NativeBridge.convert] 的线程上执行。更新界面时需切换到主线程。
 */
fun interface ProgressListener {

    /**
     * @brief 接收原生转换进度和单项结果。
     * @param done 已处理数量。
     * @param total 总数量。
     * @param ok 当前项是否成功。
     * @param encrypted 输出是否为加密 RTON。
     * @param input 输入路径。
     * @param output 输出路径。
     * @param message 失败原因。
     */
    fun onProgress(
        done: Int, total: Int, ok: Boolean, encrypted: Boolean,
        input: String, output: String, message: String
    )
}

/**
 * @brief C++ 核心库的 Kotlin 入口。
 */
object NativeBridge {
    init { System.loadLibrary("pcpjni") }

    /**
     * @brief 调用原生翻译接口。
     * @param key 翻译键名。
     * @param args 占位符参数。
     * @return 翻译后的文本。
     */
    private external fun tr(key: String, args: Array<String>): String

    /**
     * @brief 注册一种语言的文本。
     * @param code 语言代码。
     * @param json 该语言的 JSON 文本。
     * @return 是否注册成功。若代码非法或 JSON 格式错误则返回 false，并保持当前语言。
     */
    external fun addLanguage(code: String, json: String): Boolean

    /**
     * @brief 切换当前语言。
     * @param code 语言代码。
     * @return 是否切换成功。若语言未注册则返回 false，并保持当前语言。
     */
    external fun setLanguage(code: String): Boolean

    /**
     * @brief 获取翻译文本。
     * @param key 翻译文本键名。
     * @param args 翻译文本中的占位符内容。
     * @return 翻译文本。
     */
    fun translate(key: String, vararg args: String): String = tr(key, arrayOf(*args))

    /**
     * @brief 执行 JSON 与 RTON 的转换。
     * @param inputs 输入的文件或目录。
     * @param outDir 输出目录。
     * @param recursive 输入是目录时，是否递归处理子目录。
     * @param mode 0 则自动按扩展名；1 为 RTON 到 JSON 的转换；2 为 JSON 到 RTON 的转换。
     * @param key RTONKey。
     * @param listener 实现了 [ProgressListener] 的对象，每处理完一项在调用线程上回调一次。
     * @return 失败的项数。
     */
    external fun convert(inputs: Array<String>, outDir: String, recursive: Boolean, mode: Int, key: String, listener: ProgressListener): Int

    /**
     * @brief 请求取消正在进行的转换。
     */
    external fun cancel()
}

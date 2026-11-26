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
 * @file MainActivity.kt
 * @brief 应用主入口。
 *
 * 该代码文件负责创建主 Activity、初始化转换 ViewModel，并组合应用主题和主界面。
 */
package com.pcptoolkit.app

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.pcptoolkit.app.core.native.NativeBridge
import com.pcptoolkit.app.feature.rton.ConvertViewModel
import com.pcptoolkit.app.feature.rton.RtonPage
import com.pcptoolkit.app.ui.PcpShell

/**
 * @brief PCPToolKit 的 Android 主 Activity。
 */
class MainActivity : ComponentActivity() {
    private val vm: ConvertViewModel by viewModels()

    /**
     * @brief 初始化应用主界面和转换状态。
     * @param savedInstanceState Activity 保存状态。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)

        // 语言文件在 assets/lang，读出后交给 C++ 注册；默认中文
        for (code in LANGS.map { it.first }) {
            val text = assets.open("lang/$code.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            NativeBridge.addLanguage(code, text)
        }
        NativeBridge.setLanguage(prefs.getString("lang", "zh_CN") ?: "zh_CN")

        setContent { AppTheme { Screen(vm, prefs) } }
    }
}

/**
 * @brief 可选语言列表。
 */
val LANGS = listOf(
    "zh_CN" to "简体中文",
    "en_US" to "English"
)

/**
 * @brief 为应用内容提供固定浅色主题。
 * @param content 主题内部界面内容。
 */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(),
        content = content
    )
}

/**
 * @brief 获取不带占位符参数的翻译文本。
 * @param k 翻译键名。
 * @return 翻译后的文本。
 */
internal fun t(k: String) = NativeBridge.translate(k)

/**
 * @brief 组合应用主界面并连接转换状态。
 * @param vm RTON 转换状态管理对象。
 * @param prefs 应用设置存储。
 */
@Composable
fun Screen(vm: ConvertViewModel, prefs: SharedPreferences) {
    var lang by remember { mutableStateOf(prefs.getString("lang", "zh_CN") ?: "zh_CN") }

    PcpShell(
        lang = lang,
        onLanguageChange = { code ->
            if (NativeBridge.setLanguage(code)) {
                prefs.edit { putString("lang", code) }
                lang = code
            }
        }
    ) {
        RtonPage(vm)
    }
}

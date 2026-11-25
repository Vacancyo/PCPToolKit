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

class MainActivity : ComponentActivity() {
    private val vm: ConvertViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)

        // 语言文件在 assets/lang,读出后交给 C++ 注册;默认中文
        for (code in LANGS.map { it.first }) {
            val text = assets.open("lang/$code.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            NativeBridge.addLanguage(code, text)
        }
        NativeBridge.setLanguage(prefs.getString("lang", "zh_CN") ?: "zh_CN")

        setContent { AppTheme { Screen(vm, prefs) } }
    }
}

/** 可选语言列表。 */
val LANGS = listOf(
    "zh_CN" to "简体中文",
    "en_US" to "English"
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(),
        content = content
    )
}

internal fun t(k: String) = NativeBridge.translate(k)

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

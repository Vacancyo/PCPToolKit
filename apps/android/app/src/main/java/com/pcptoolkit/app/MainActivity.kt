package com.pcptoolkit.app

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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

/** 可选语言: (语言代码, 语言自身名称)。新增语言时在这里加一项。 */
val LANGS = listOf("zh_CN" to "简体中文", "en_US" to "English")

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
        content = content
    )
}

internal fun t(k: String) = NativeBridge.translate(k)

private fun stageKey(s: Stage) = when (s) {
    Stage.IDLE -> ""
    Stage.COPYING -> "app.copying"
    Stage.CONVERTING -> "app.running"
    Stage.EXPORTING -> "app.exporting"
    Stage.DONE -> "app.done"
    Stage.CANCELLED -> "app.cancelled"
}

@Composable
fun Screen(vm: ConvertViewModel, prefs: SharedPreferences) {
    var lang by remember { mutableStateOf(prefs.getString("lang", "zh_CN") ?: "zh_CN") }

    PcpShell(
        lang = lang,
        onLanguageChange = { code ->
            if (NativeBridge.setLanguage(code)) {
                prefs.edit().putString("lang", code).apply()
                lang = code
            }
        }
    ) {
        RtonPage(vm)
    }
}

@Composable
private fun RtonPage(vm: ConvertViewModel) {
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.addFiles(it) }
    val pickDir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { if (it != null) vm.outTree = it }

    LazyColumn(
        modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
                // 1. 选文件
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(enabled = !vm.running, onClick = { pickFiles.launch(arrayOf("*/*")) }) { Text(t("app.pick_files")) }
                        Spacer(Modifier.width(8.dp))
                        if (vm.files.isNotEmpty())
                            TextButton(enabled = !vm.running, onClick = { vm.clearFiles() }) { Text(t("app.clear")) }
                    }
                    Text("${t("app.files_selected")}: ${vm.files.size}")
                    vm.files.take(6).forEach { Text(it.name, style = MaterialTheme.typography.bodySmall) }
                    if (vm.files.size > 6) Text("…", style = MaterialTheme.typography.bodySmall)
                }
                // 2. 选输出文件夹
                item {
                    OutlinedButton(enabled = !vm.running, onClick = { pickDir.launch(null) }) { Text(t("app.pick_folder")) }
                    val label = vm.folderLabel().ifEmpty { t("app.folder_none") }
                    Text("${t("app.folder_label")}: $label", style = MaterialTheme.typography.bodySmall)
                }
                // 3. 转换方向
                item {
                    Text(t("app.mode_label"), style = MaterialTheme.typography.titleSmall)
                    val modes = listOf("app.mode_auto", "app.mode_decode", "app.mode_encode")
                    modes.forEachIndexed { i, k ->
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = !vm.running) { vm.mode = i },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = vm.mode == i, onClick = null, enabled = !vm.running)
                            Spacer(Modifier.width(8.dp))
                            Text(t(k))
                        }
                    }
                }
                // 4. 密钥
                item {
                    OutlinedTextField(
                        value = vm.key, onValueChange = { vm.key = it },
                        label = { Text(t("app.key_label")) },
                        supportingText = { Text(t("app.key_hint")) },
                        singleLine = true, enabled = !vm.running,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                // 5. 开始 / 取消
                item {
                    if (!vm.running) {
                        Button(
                            enabled = vm.files.isNotEmpty() && vm.outTree != null,
                            onClick = { vm.start() },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(t("app.start")) }
                        if (vm.files.isEmpty() || vm.outTree == null)
                            Text(t("app.need_files"), style = MaterialTheme.typography.bodySmall)
                    } else {
                        OutlinedButton(onClick = { vm.cancel() }, modifier = Modifier.fillMaxWidth()) { Text(t("app.cancel")) }
                    }
                }
                // 6. 进度与统计
                if (vm.stage != Stage.IDLE) {
                    item {
                        if (vm.total > 0)
                            LinearProgressIndicator(progress = vm.done.toFloat() / vm.total, modifier = Modifier.fillMaxWidth())
                        val okN = vm.results.count { it.ok }
                        val failN = vm.results.size - okN
                        Text("${t(stageKey(vm.stage))}  ${vm.done}/${vm.total}")
                        Text("${t("app.success_count")}: $okN    ${t("app.fail_count")}: $failN")
                        HorizontalDivider()
                        Text(t("app.result_title"), style = MaterialTheme.typography.titleSmall)
                    }
                }
                // 7. 结果列表
                items(vm.results) { r ->
                    val text = when {
                        r.ok -> "${r.input} → ${r.output}" + (if (r.encrypted) " [${t("app.encrypted")}]" else "")
                        r.input.isEmpty() -> r.message
                        else -> "${r.input}: ${r.message}"
                    }
                    Text(
                        text, style = MaterialTheme.typography.bodySmall,
                        color = if (r.ok) Color.Unspecified else MaterialTheme.colorScheme.error
                    )
                }
                item { Spacer(Modifier.padding(8.dp)) }
    }
}

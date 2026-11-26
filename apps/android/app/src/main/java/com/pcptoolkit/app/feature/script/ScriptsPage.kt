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
 * @file ScriptsPage.kt
 * @brief 脚本管理界面。
 *
 * 该代码文件实现脚本目录授权、脚本列表、导入、预设添加和删除等管理操作。
 */
package com.pcptoolkit.app.feature.script

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pcptoolkit.app.t

private const val PRESET_DIR = "scripts"
private const val SCRIPT_MIME = "application/octet-stream"

/**
 * @brief 显示脚本管理页面。
 */
@Composable
internal fun ScriptsPage() {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences(ScriptStore.PREFS_NAME, Context.MODE_PRIVATE)
    }
    var treeUri by remember { mutableStateOf(prefs.getString(ScriptStore.TREE_KEY, null)) }
    var scripts by remember { mutableStateOf(emptyList<ScriptEntry>()) }
    var folderName by remember { mutableStateOf<String?>(null) }
    var folderError by remember { mutableStateOf(false) }
    val presets = remember { context.assets.list(PRESET_DIR)?.toList()?.sorted() ?: emptyList() }

    /**
     * @brief 刷新脚本目录和脚本列表。
     * @param tree 目录树 URI。
     */
    fun refresh(tree: Uri?) {
        if (tree == null) {
            scripts = emptyList()
            folderName = null
            folderError = false
            return
        }
        val name = ScriptStore.folderName(context, tree)
        val list = ScriptStore.list(context, tree)
        folderName = name
        folderError = name == null || list == null
        scripts = list ?: emptyList()
    }

    LaunchedEffect(treeUri) {
        refresh(treeUri?.let(Uri::parse))
    }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            if (takeScriptFolderPermission(context, uri)) {
                prefs.edit().putString(ScriptStore.TREE_KEY, uri.toString()).apply()
                treeUri = uri.toString()
            } else {
                folderError = true
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val tree = treeUri?.let(Uri::parse)
        if (uri != null && tree != null) {
            importScript(context, tree, uri, scripts.map { it.name })
            refresh(tree)
        }
    }

    val folderReady = treeUri != null && !folderError

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = t("scripts.intro"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        item {
            ScriptsCard(title = t("scripts.folder")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = folderName ?: t("scripts.folder_none"),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = t("scripts.folder_tip"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    TextButton(onClick = { pickFolder.launch(treeUri?.let(Uri::parse)) }) {
                        Text(
                            if (treeUri == null) t("scripts.choose_folder")
                            else t("scripts.change_folder")
                        )
                    }
                }
                if (folderError) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = t("scripts.folder_unavailable"),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        item {
            ScriptsCard(title = t("scripts.my_scripts")) {
                when {
                    !folderReady -> Text(
                        text = t("scripts.select_folder_first"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    scripts.isEmpty() -> Text(
                        text = t("scripts.no_scripts"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        scripts.forEach { script ->
                            ScriptRow(script.name) {
                                val tree = treeUri?.let(Uri::parse) ?: return@ScriptRow
                                deleteScript(context, script.uri)
                                refresh(tree)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    enabled = folderReady,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(15.dp)
                ) {
                    Text(t("scripts.import"), fontWeight = FontWeight.SemiBold)
                }
            }
        }

        item {
            ScriptsCard(title = t("scripts.preset")) {
                Text(
                    text = t("scripts.preset_tip"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(10.dp))
                if (presets.isEmpty()) {
                    Text(
                        text = t("scripts.no_presets"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        presets.forEach { name ->
                            PresetRow(name, enabled = folderReady) {
                                val tree = treeUri?.let(Uri::parse) ?: return@PresetRow
                                addPreset(context, tree, name, scripts.map { it.name })
                                refresh(tree)
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(10.dp)) }
    }
}

/**
 * @brief 显示脚本管理卡片。
 * @param title 卡片标题。
 * @param content 卡片内容。
 */
@Composable
private fun ScriptsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

/**
 * @brief 显示一个脚本条目。
 * @param name 脚本名称。
 * @param onDelete 删除回调。
 */
@Composable
private fun ScriptRow(name: String, onDelete: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, top = 9.dp, bottom = 9.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExtBadge(name)
            Spacer(Modifier.width(12.dp))
            Text(
                text = name,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            TextButton(
                onClick = onDelete,
                modifier = Modifier.semantics {
                    contentDescription = t("scripts.delete") + ": " + name
                }
            ) {
                Text(t("scripts.delete"), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/**
 * @brief 显示一个内置脚本预设。
 * @param name 预设名称。
 * @param enabled 是否可用。
 * @param onAdd 添加回调。
 */
@Composable
private fun PresetRow(name: String, enabled: Boolean, onAdd: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, top = 9.dp, bottom = 9.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExtBadge(name)
            Spacer(Modifier.width(12.dp))
            Text(
                text = name,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            OutlinedButton(
                onClick = onAdd,
                enabled = enabled,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(t("scripts.add"))
            }
        }
    }
}

/**
 * @brief 显示脚本扩展名标签。
 * @param name 脚本名称。
 */
@Composable
private fun ExtBadge(name: String) {
    val extension = name.substringAfterLast('.', "").uppercase().ifEmpty { "PCS" }.take(4)
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = extension,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * @brief 尝试长期保留脚本目录访问权限。
 * @param context 应用上下文。
 * @param tree 目录树 URI。
 * @return 取得权限返回 true。
 */
private fun takeScriptFolderPermission(context: Context, tree: Uri): Boolean =
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            tree,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        true
    }.getOrDefault(false)

/**
 * @brief 把用户选择的文件导入脚本目录。
 * @param context 应用上下文。
 * @param tree 脚本目录 URI。
 * @param uri 待导入文件 URI。
 * @return 导入成功返回 true。
 */
private fun importScript(
    context: Context,
    tree: Uri,
    source: Uri,
    existing: List<String>
): Boolean {
    val resolver = context.contentResolver
    return runCatching {
        val pickedName = queryDisplayName(context, source) ?: "script"
        val name = if (pickedName.endsWith(ScriptStore.EXTENSION, ignoreCase = true)) pickedName
        else pickedName + ScriptStore.EXTENSION
        val doc = DocumentsContract.createDocument(
            resolver,
            ScriptStore.rootDocumentUri(tree),
            SCRIPT_MIME,
            uniqueName(existing, name)
        ) ?: throw IllegalStateException("create document")
        try {
            resolver.openInputStream(source)?.use { src ->
                resolver.openOutputStream(doc, "w")?.use { src.copyTo(it) }
                    ?: throw IllegalStateException("open output")
            } ?: throw IllegalStateException("open input")
            true
        } catch (t: Throwable) {
            DocumentsContract.deleteDocument(resolver, doc)
            throw t
        }
    }.getOrDefault(false)
}

/**
 * @brief 把内置脚本写入脚本目录。
 * @param context 应用上下文。
 * @param tree 脚本目录 URI。
 * @param preset 内置脚本内容。
 * @return 添加成功返回 true。
 */
private fun addPreset(
    context: Context,
    tree: Uri,
    name: String,
    existing: List<String>
): Boolean {
    val resolver = context.contentResolver
    return runCatching {
        val doc = DocumentsContract.createDocument(
            resolver,
            ScriptStore.rootDocumentUri(tree),
            SCRIPT_MIME,
            uniqueName(existing, name)
        ) ?: throw IllegalStateException("create document")
        try {
            context.assets.open("$PRESET_DIR/$name").use { src ->
                resolver.openOutputStream(doc, "w")?.use { src.copyTo(it) }
                    ?: throw IllegalStateException("open output")
            }
            true
        } catch (t: Throwable) {
            DocumentsContract.deleteDocument(resolver, doc)
            throw t
        }
    }.getOrDefault(false)
}

/**
 * @brief 删除一个脚本文件。
 * @param context 应用上下文。
 * @param uri 脚本文件 URI。
 * @return 删除成功返回 true。
 */
private fun deleteScript(context: Context, uri: Uri): Boolean =
    runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
        .getOrDefault(false)

/**
 * @brief 查询文件 URI 的显示名称。
 * @param context 应用上下文。
 * @param uri 文件 URI。
 * @return 显示名称，查询失败时返回空。
 */
private fun queryDisplayName(context: Context, uri: Uri): String? =
    context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null
    )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

/**
 * @brief 生成不会与现有名称重复的文件名。
 * @param existing 现有名称列表。
 * @param name 原始文件名。
 * @return 可用文件名。
 */
private fun uniqueName(existing: List<String>, name: String): String {
    if (name !in existing) return name
    val dot = name.lastIndexOf('.')
    val base = if (dot > 0) name.substring(0, dot) else name
    val ext = if (dot > 0) name.substring(dot) else ""
    var index = 1
    while ("$base ($index)$ext" in existing) index++
    return "$base ($index)$ext"
}

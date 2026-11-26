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
 * @file ScriptStore.kt
 * @brief 脚本文件存储与访问。
 *
 * 该代码文件使用 Android 存储访问框架读取、列出和保存 PCS 脚本文件。
 */
package com.pcptoolkit.app.feature.script

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/**
 * @brief 一个脚本文件名称和内容 URI。
 */
data class ScriptEntry(val name: String, val uri: Uri)

/**
 * @brief 管理脚本目录授权、列表和文件读取。
 */
object ScriptStore {
    const val PREFS_NAME = "settings"
    const val TREE_KEY = "scripts.tree"
    const val EXTENSION = ".pcs"

    /**
     * @brief 读取已授权的脚本目录 URI。
     * @param context 应用上下文。
     * @return 已保存的目录 URI，未授权时返回空。
     */
    fun treeUri(context: Context): Uri? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(TREE_KEY, null)
            ?.let(Uri::parse)

    /**
     * @brief 查询脚本目录显示名称。
     * @param context 应用上下文。
     * @param tree 目录树 URI。
     * @return 目录名称，查询失败时返回空。
     */
    fun folderName(context: Context, tree: Uri): String? = runCatching {
        context.contentResolver.query(
            rootDocumentUri(tree),
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()

    /**
     * @brief 列出目录中的 PCS 脚本。
     * @param context 应用上下文。
     * @param tree 目录树 URI。
     * @return 脚本条目列表，查询失败时返回空。
     */
    fun list(context: Context, tree: Uri): List<ScriptEntry>? = runCatching {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            tree,
            DocumentsContract.getTreeDocumentId(tree)
        )
        context.contentResolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            ),
            null,
            null,
            null
        )?.use { cursor ->
            val result = mutableListOf<ScriptEntry>()
            while (cursor.moveToNext()) {
                if (cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) continue
                val id = cursor.getString(0) ?: continue
                val name = cursor.getString(1) ?: continue
                if (!name.endsWith(EXTENSION, ignoreCase = true)) continue
                result += ScriptEntry(name, DocumentsContract.buildDocumentUriUsingTree(tree, id))
            }
            result.sortedBy { it.name }
        }
    }.getOrNull()

    /**
     * @brief 读取一个脚本文件。
     * @param context 应用上下文。
     * @param entry 脚本条目。
     * @return 脚本源码，读取失败时返回空。
     */
    fun read(context: Context, entry: ScriptEntry): String? = runCatching {
        context.contentResolver.openInputStream(entry.uri)?.bufferedReader(Charsets.UTF_8)?.use {
            it.readText().removePrefix("\uFEFF")
        }
    }.getOrNull()

    /**
     * @brief 获取目录树对应的根文档 URI。
     * @param tree 目录树 URI。
     * @return 根文档 URI。
     */
    fun rootDocumentUri(tree: Uri): Uri =
        DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
}

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
 * 该代码文件管理 PCP 外部脚本目录、脚本读取和基础文件操作。
 */
package com.pcptoolkit.app.feature.script

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * @brief 一个脚本文件及其展示属性。
 */
data class ScriptEntry(
    val name: String,
    val file: File,
    val size: Long,
    val lastModified: Long
)

/**
 * @brief 脚本文件属性。
 */
data class ScriptProperties(
    val name: String,
    val size: Long,
    val lastModified: Long,
    val permissions: String,
    val owner: String,
    val group: String
)

/**
 * @brief 管理 PCP 外部脚本目录和脚本文件。
 */
object ScriptStore {
    const val EXTENSION = ".pcs"
    private const val PRESET_DIR = "scripts"
    private const val BUFFER_SIZE = 1024 * 1024

    /**
     * @brief 获取或创建 PCP 外部脚本目录。
     * @param context 应用上下文。
     * @return Android/data/<包名>/scripts 目录。
     */
    fun scriptsDirectory(context: Context): File {
        val packageRoot = context.getExternalFilesDir(null)?.parentFile
            ?: throw IOException("external_storage_unavailable")
        val directory = File(packageRoot, "scripts")
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
            throw IOException("Unable to create ${directory.absolutePath}")
        }
        return directory
    }

    /**
     * @brief 列出 PCP 外部脚本目录中的脚本。
     * @param context 应用上下文。
     * @return 脚本列表，目录读取失败时返回空。
     */
    fun list(context: Context): List<ScriptEntry>? = runCatching {
        scriptsDirectory(context).listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isFile && it.name.endsWith(EXTENSION, ignoreCase = true) }
            .map(::toEntry)
            .sortedBy { it.name.lowercase(Locale.ROOT) }
            .toList()
    }.getOrNull()

    /**
     * @brief 从系统文件选择器导入脚本。
     * @param context 应用上下文。
     * @param source 源文件 URI。
     * @return 导入后的脚本。
     */
    fun importScript(context: Context, source: Uri): ScriptEntry {
        val directory = scriptsDirectory(context)
        val displayName = queryDisplayName(context, source) ?: "imported"
        val target = uniqueTarget(directory, normalizeName(displayName))
        val input = context.contentResolver.openInputStream(source)
            ?: throw IOException("Cannot open source script")
        try {
            input.use { sourceStream ->
                target.outputStream().use { destination ->
                    sourceStream.copyTo(destination, BUFFER_SIZE)
                }
            }
        } catch (error: Throwable) {
            target.delete()
            throw error
        }
        return toEntry(target)
    }

    /**
     * @brief 把内置预制脚本添加到脚本目录。
     * @param context 应用上下文。
     * @param presetName 预制脚本文件名。
     * @return 添加后的脚本。
     */
    fun addPreset(context: Context, presetName: String): ScriptEntry {
        require(
            presetName == File(presetName).name &&
                presetName.endsWith(EXTENSION, ignoreCase = true)
        ) { "invalid_preset_name" }
        val target = uniqueTarget(scriptsDirectory(context), presetName)
        try {
            context.assets.open("$PRESET_DIR/$presetName").use { source ->
                target.outputStream().use { destination ->
                    source.copyTo(destination, BUFFER_SIZE)
                }
            }
        } catch (error: Throwable) {
            target.delete()
            throw error
        }
        return toEntry(target)
    }

    /**
     * @brief 读取脚本文本。
     * @param context 应用上下文。
     * @param entry 脚本条目。
     * @return 脚本源码，读取失败时返回空。
     */
    fun read(context: Context, entry: ScriptEntry): String? = runCatching {
        resolve(context, entry).bufferedReader(Charsets.UTF_8).use {
            it.readText().removePrefix("\uFEFF")
        }
    }.getOrNull()

    /**
     * @brief 保存脚本文本。
     * @param context 应用上下文。
     * @param entry 脚本条目。
     * @param text 脚本文本。
     */
    fun write(context: Context, entry: ScriptEntry, text: String) {
        val target = resolve(context, entry)
        require(target.isFile) { "not_a_file" }
        target.writeText(text, Charsets.UTF_8)
    }

    /**
     * @brief 删除脚本。
     * @param context 应用上下文。
     * @param entry 脚本条目。
     */
    fun delete(context: Context, entry: ScriptEntry) {
        val target = resolve(context, entry)
        require(target.isFile) { "not_a_file" }
        if (!target.delete()) throw IOException("Unable to delete ${entry.name}")
    }

    /**
     * @brief 重命名脚本。
     * @param context 应用上下文。
     * @param entry 脚本条目。
     * @param requestedName 新文件名。
     * @return 重命名后的脚本。
     */
    fun rename(context: Context, entry: ScriptEntry, requestedName: String): ScriptEntry {
        val source = resolve(context, entry)
        val directory = scriptsDirectory(context)
        val target = File(directory, normalizeName(requestedName)).canonicalFile
        ensureInside(directory, target)
        if (target.path == source.path) return toEntry(source)
        require(!target.exists()) { "file_exists" }
        if (!source.renameTo(target)) throw IOException("Unable to rename ${entry.name}")
        return toEntry(target)
    }

    /**
     * @brief 导出脚本到用户选择的文档。
     * @param context 应用上下文。
     * @param entry 脚本条目。
     * @param target 导出目标 URI。
     */
    fun exportToUri(context: Context, entry: ScriptEntry, target: Uri) {
        val source = resolve(context, entry)
        require(source.isFile) { "not_a_file" }
        val output = context.contentResolver.openOutputStream(target, "w")
            ?: throw IOException("Cannot open export target")
        output.use { destination ->
            source.inputStream().use { input ->
                input.copyTo(destination, BUFFER_SIZE)
            }
        }
    }

    /**
     * @brief 准备分享用的缓存副本。
     * @param context 应用上下文。
     * @param entry 脚本条目。
     * @return 可交给 FileProvider 的缓存文件。
     */
    fun prepareShareFile(context: Context, entry: ScriptEntry): File {
        val source = resolve(context, entry)
        require(source.isFile) { "not_a_file" }
        val shareDirectory = File(context.cacheDir, "script-share").apply { mkdirs() }
        shareDirectory.listFiles()?.forEach { it.delete() }
        val target = File(shareDirectory, entry.name)
        source.inputStream().use { input ->
            target.outputStream().use { output ->
                input.copyTo(output, BUFFER_SIZE)
            }
        }
        return target
    }

    /**
     * @brief 读取脚本文件属性。
     * @param context 应用上下文。
     * @param entry 脚本条目。
     * @return 脚本属性。
     */
    fun getProperties(context: Context, entry: ScriptEntry): ScriptProperties {
        val file = resolve(context, entry)
        val stat = runCatching { Os.stat(file.absolutePath) }.getOrNull()
        return ScriptProperties(
            name = file.name,
            size = stat?.st_size ?: file.length(),
            lastModified = stat?.st_mtime?.times(1000L) ?: file.lastModified(),
            permissions = stat?.st_mode?.let(::formatPermissions) ?: "---------",
            owner = stat?.st_uid?.toString() ?: "?",
            group = stat?.st_gid?.toString() ?: "?"
        )
    }

    /**
     * @brief 生成不与目标目录现有文件重复的名称。
     * @param directory 目标目录。
     * @param name 原始文件名。
     * @return 可用的目标文件。
     */
    private fun uniqueTarget(directory: File, name: String): File {
        val existing = directory.list()?.toSet().orEmpty()
        if (name !in existing) return File(directory, name)
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val extension = if (dot > 0) name.substring(dot) else ""
        var index = 1
        while ("$base ($index)$extension" in existing) index++
        return File(directory, "$base ($index)$extension")
    }

    /**
     * @brief 规范化脚本文件名并补齐 .pcs 扩展名。
     * @param requestedName 用户提供的文件名。
     * @return 安全脚本文件名。
     */
    private fun normalizeName(requestedName: String): String {
        val safeName = requestedName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .trim()
        require(safeName.isNotBlank() && safeName != "." && safeName != "..") {
            "invalid_file_name"
        }
        return if (safeName.endsWith(EXTENSION, ignoreCase = true)) {
            safeName
        } else {
            safeName + EXTENSION
        }
    }

    /**
     * @brief 把脚本条目解析为脚本目录内的文件。
     * @param context 应用上下文。
     * @param entry 脚本条目。
     * @return 目录内的规范文件。
     */
    private fun resolve(context: Context, entry: ScriptEntry): File {
        val directory = scriptsDirectory(context).canonicalFile
        val target = File(directory, entry.name).canonicalFile
        ensureInside(directory, target)
        return target
    }

    /**
     * @brief 确保目标文件位于脚本目录内。
     * @param root 脚本目录。
     * @param target 目标文件。
     */
    private fun ensureInside(root: File, target: File) {
        val rootPath = root.canonicalPath
        val targetPath = target.canonicalPath
        require(targetPath.startsWith(rootPath + File.separator)) { "path_outside_scripts" }
    }

    /**
     * @brief 把文件转换为脚本条目。
     * @param file 脚本文件。
     * @return 脚本条目。
     */
    private fun toEntry(file: File): ScriptEntry = ScriptEntry(
        name = file.name,
        file = file,
        size = file.length(),
        lastModified = file.lastModified()
    )

    /**
     * @brief 查询文件 URI 的显示名称。
     * @param context 应用上下文。
     * @param uri 文件 URI。
     * @return 显示名称，查询失败时返回空。
     */
    private fun queryDisplayName(context: Context, uri: Uri): String? {
        val cursor = context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        ) ?: return null
        return cursor.use {
            if (!it.moveToFirst()) return@use null
            val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index < 0) null else it.getString(index)
        }
    }

    /**
     * @brief 把 POSIX 模式格式化为权限文本。
     * @param mode POSIX 模式。
     * @return 九位权限文本。
     */
    private fun formatPermissions(mode: Int): String = buildString(9) {
        append(if (mode and OsConstants.S_IRUSR != 0) 'r' else '-')
        append(if (mode and OsConstants.S_IWUSR != 0) 'w' else '-')
        append(if (mode and OsConstants.S_IXUSR != 0) 'x' else '-')
        append(if (mode and OsConstants.S_IRGRP != 0) 'r' else '-')
        append(if (mode and OsConstants.S_IWGRP != 0) 'w' else '-')
        append(if (mode and OsConstants.S_IXGRP != 0) 'x' else '-')
        append(if (mode and OsConstants.S_IROTH != 0) 'r' else '-')
        append(if (mode and OsConstants.S_IWOTH != 0) 'w' else '-')
        append(if (mode and OsConstants.S_IXOTH != 0) 'x' else '-')
    }
}

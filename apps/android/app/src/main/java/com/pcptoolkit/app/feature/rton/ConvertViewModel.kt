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
 * @file ConvertViewModel.kt
 * @brief RTON 转换状态管理。
 *
 * 该代码文件管理输入选择、目录扫描、后台转换、缓存文件导出、进度更新和取消流程。
 */
package com.pcptoolkit.app.feature.rton

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.pcptoolkit.app.core.native.NativeBridge
import java.io.File
import java.io.IOException
import kotlin.concurrent.thread

/**
 * @brief 用户选中的一个文件（SAF 返回的 content:// 地址 + 显示名 + 相对路径）。
 */
data class PickedFile(val uri: Uri, val name: String, val relativePath: String = name)

/**
 * @brief 一条处理结果，界面按当前语言渲染。
 */
data class ResultLine(
    val ok: Boolean, val input: String, val output: String,
    val encrypted: Boolean, val message: String
)

/**
 * @brief 任务阶段，界面据此显示文字。
 */
enum class Stage { IDLE, COPYING, CONVERTING, EXPORTING, DONE, CANCELLED }

/**
 * @brief RTON 转换的输入来源。
 */
enum class InputSource { FILES, DIRECTORY }

/**
 * @brief 转换流程：把选中的文件复制到应用缓存 -> 调用 C++ 转换 -> 把结果写入用户选的文件夹。
 * 放在 ViewModel 里，旋转屏幕不会中断任务。
 */
class ConvertViewModel(app: Application) : AndroidViewModel(app) {
    val files = mutableStateListOf<PickedFile>()
    val directoryFiles = mutableStateListOf<PickedFile>()
    val results = mutableStateListOf<ResultLine>()
    var inputSource by mutableStateOf(InputSource.FILES)
    var inputTree by mutableStateOf<Uri?>(null)
    var scanning by mutableStateOf(false)
    var scanFailed by mutableStateOf(false)
    var outTree by mutableStateOf<Uri?>(null)   ///< 用户选的输出文件夹
    var mode by mutableIntStateOf(0)               ///< 0 自动 / 1 RTON->JSON / 2 JSON->RTON
    var key by mutableStateOf("")               ///< RTONKey，空 = 不加密
    var stage by mutableStateOf(Stage.IDLE)
    var done by mutableIntStateOf(0)
    var total by mutableIntStateOf(0)

    val running: Boolean
        get() = stage == Stage.COPYING || stage == Stage.CONVERTING || stage == Stage.EXPORTING

    @Volatile private var cancelled = false
    private var scanVersion = 0
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * @brief 把状态更新投递到主线程。
     * @param block 需要主线程执行的代码。
     */
    private fun ui(block: () -> Unit) { mainHandler.post(block) }
    private val resolver: ContentResolver
        get() = getApplication<Application>().contentResolver

    /**
     * @brief 添加选中的文件（忽略重复）。
     */
    fun addFiles(uris: List<Uri>) {
        for (u in uris) {
            if (files.any { it.uri == u }) continue
            val name = queryName(u)
            files.add(PickedFile(u, name, name))
        }
    }

    /**
     * @brief 从待转换文件列表中移除一个文件。
     * @param file 待移除文件。
     */
    fun removeFile(file: PickedFile) {
        if (!running) files.remove(file)
    }

    /**
     * @brief 清空待转换文件列表。
     */
    fun clearFiles() { if (!running) files.clear() }

    /**
     * @brief 切换为选择单个文件作为输入。
     */
    fun useFiles() {
        if (!running) {
            scanVersion++
            scanning = false
            inputSource = InputSource.FILES
        }
    }

    /**
     * @brief 切换为选择整个目录作为输入。
     */
    fun useDirectory() {
        if (!running) inputSource = InputSource.DIRECTORY
    }

    /**
     * @brief 选择输入目录后异步递归扫描，避免阻塞界面。
     */
    fun selectInputDirectory(tree: Uri) {
        if (running) return
        inputSource = InputSource.DIRECTORY
        inputTree = tree
        directoryFiles.clear()
        scanFailed = false
        scanning = true
        val version = ++scanVersion

        thread(name = "rton-scan") {
            try {
                val found = scanDirectory(tree)
                ui {
                    if (version != scanVersion) return@ui
                    directoryFiles.addAll(found)
                    scanning = false
                }
            } catch (_: Exception) {
                ui {
                    if (version != scanVersion) return@ui
                    scanFailed = true
                    scanning = false
                }
            }
        }
    }

    /**
     * @brief 递归扫描输入目录并收集可转换文件。
     * @param tree 用户授权的目录树 URI。
     * @return 扫描到的文件列表。
     */
    private fun scanDirectory(tree: Uri): List<PickedFile> {

        /**
         * @brief 目录扫描过程中待处理的子目录。
         */
        data class PendingDirectory(val id: String, val relativePath: String)

        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val pending = ArrayDeque<PendingDirectory>()
        val visited = HashSet<String>()
        val found = ArrayList<PickedFile>()
        pending.add(PendingDirectory(rootId, ""))

        while (pending.isNotEmpty()) {
            val directory = pending.removeLast()
            if (!visited.add(directory.id)) continue
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, directory.id)
            resolver.query(
                childrenUri,
                arrayOf(
                    Document.COLUMN_DOCUMENT_ID,
                    Document.COLUMN_DISPLAY_NAME,
                    Document.COLUMN_MIME_TYPE
                ),
                null,
                null,
                null
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)
                val nameColumn = cursor.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)
                val mimeColumn = cursor.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val id = cursor.getString(idColumn)
                    val name = cursor.getString(nameColumn) ?: continue
                    val mime = cursor.getString(mimeColumn)
                    val relativePath =
                        if (directory.relativePath.isEmpty()) name else "${directory.relativePath}/$name"
                    if (mime == Document.MIME_TYPE_DIR) {
                        pending.add(PendingDirectory(id, relativePath))
                    } else if (isConvertible(name)) {
                        found.add(
                            PickedFile(
                                DocumentsContract.buildDocumentUriUsingTree(tree, id),
                                name,
                                relativePath
                            )
                        )
                    }
                }
            } ?: throw IOException("query")
        }
        return found.sortedBy { it.relativePath.lowercase() }
    }

    /**
     * @brief 判断文件名是否具有可转换扩展名。
     * @param name 文件名。
     * @return 可以转换返回 true。
     */
    private fun isConvertible(name: String): Boolean {
        val extension = name.substringAfterLast('.', "").lowercase()
        return extension == "rton" || extension == "json"
    }

    /**
     * @brief 判断当前输入源是否已有可用内容。
     * @return 存在输入返回 true。
     */
    fun hasInput(): Boolean = when (inputSource) {
        InputSource.FILES -> files.isNotEmpty()
        InputSource.DIRECTORY -> directoryFiles.isNotEmpty()
    }

    /**
     * @brief 生成输入源摘要文本。
     * @return 输入源摘要。
     */
    fun inputLabel(): String {
        val tree = inputTree ?: return ""
        return treeLabel(tree)
    }

    /**
     * @brief 输出文件夹的可读标签，如 "primary：Download/rton"。
     */
    fun folderLabel(): String {
        val t = outTree ?: return ""
        return treeLabel(t)
    }

    /**
     * @brief 获取目录 URI 对应的显示名称。
     * @param tree 目录树 URI。
     * @return 目录显示名称。
     */
    private fun treeLabel(tree: Uri): String {
        return try { DocumentsContract.getTreeDocumentId(tree) } catch (_: Exception) { tree.toString() }
    }

    /**
     * @brief 查询内容 URI 的显示名称。
     * @param u 内容 URI。
     * @return 显示名称，查询失败时返回空。
     */
    private fun queryName(u: Uri): String {
        val n = resolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        return n ?: u.lastPathSegment?.substringAfterLast('/') ?: "file"
    }

    /**
     * @brief 请求取消：在 C++ 处理完当前文件后停止。
     */
    fun cancel() {
        cancelled = true
        NativeBridge.cancel()
    }

    /**
     * @brief 开始转换（在后台线程执行）。
     */
    fun start() {
        val tree = outTree ?: return
        if (running || scanning || !hasInput()) return
        val picked = when (inputSource) {
            InputSource.FILES -> files.toList()
            InputSource.DIRECTORY -> directoryFiles.toList()
        }
        val fromDirectory = inputSource == InputSource.DIRECTORY
        val keyText = key
        val modeValue = mode
        cancelled = false
        results.clear()
        done = 0
        total = 0
        stage = Stage.COPYING

        thread(name = "rton-convert") {
            val work = File(getApplication<Application>().cacheDir, "work")
            try {
                work.deleteRecursively()
                val inDir = File(work, "in").apply { mkdirs() }
                val outDir = File(work, "out").apply { mkdirs() }
                val inputRoot = File(inDir, "input").apply { mkdirs() }

                // 1. 复制到缓存；目录输入保留层级，文件输入自动处理重名。
                val paths = ArrayList<String>()
                val used = HashSet<String>()
                for (f in picked) {
                    if (cancelled) break
                    val relativePath = uniqueRelativePath(
                        if (fromDirectory) f.relativePath else f.name,
                        used
                    )
                    val dst = File(if (fromDirectory) inputRoot else inDir, relativePath)
                    try {
                        dst.parentFile?.mkdirs()
                        val src = resolver.openInputStream(f.uri) ?: throw IOException("openInputStream")
                        src.use { s -> dst.outputStream().use { o -> s.copyTo(o) } }
                        if (!fromDirectory) paths.add(dst.absolutePath)
                    } catch (e: Exception) {
                        val msg = NativeBridge.translate("app.read_failed") + ": " + (e.message ?: "")
                        ui { results.add(ResultLine(false, f.name, "", false, msg)) }
                    }
                }

                // 2. C++ 转换，每处理完一个回调一次
                val inputs = if (fromDirectory) arrayOf(inputRoot.absolutePath) else paths.toTypedArray()
                val hasCopiedFiles = if (fromDirectory) {
                    inputRoot.walkTopDown().any { it.isFile }
                } else {
                    paths.isNotEmpty()
                }
                if (!cancelled && hasCopiedFiles) {
                    ui { stage = Stage.CONVERTING }
                    NativeBridge.convert(
                        inputs, outDir.absolutePath, fromDirectory, modeValue, keyText
                    ) { d, t, ok, enc, input, output, msg ->
                        val inputFile = File(input)
                        val outputFile = File(output)
                        val inputName = if (fromDirectory) {
                            try { inputFile.relativeTo(inputRoot).path } catch (_: Exception) { inputFile.name }
                        } else {
                            inputFile.name
                        }
                        val outputName = try {
                            outputFile.relativeTo(outDir).path
                        } catch (_: Exception) {
                            outputFile.name
                        }
                        val line = ResultLine(ok, inputName, outputName, enc, msg)
                        ui { done = d; total = t; results.add(line) }
                    }
                }

                // 3. 写入用户选的文件夹
                ui { stage = Stage.EXPORTING }
                val parent = DocumentsContract.buildDocumentUriUsingTree(
                    tree, DocumentsContract.getTreeDocumentId(tree)
                )
                exportDirectory(outDir, parent)
                ui { stage = if (cancelled) Stage.CANCELLED else Stage.DONE }
            } catch (e: Throwable) {
                val msg = e.message ?: e.toString()
                ui { results.add(ResultLine(false, "", "", false, msg)); stage = Stage.DONE }
            } finally {
                work.deleteRecursively()
            }
        }
    }

    /**
     * @brief 把缓存目录中的转换结果写入用户选择的目录。
     * @param source 缓存输出目录。
     * @param parent 用户选择的目标目录 URI。
     */
    private fun exportDirectory(source: File, parent: Uri) {
        source.listFiles()?.sortedWith(
            compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() }
        )?.forEach { file ->
            try {
                if (file.isDirectory) {
                    val dir = DocumentsContract.createDocument(
                        resolver,
                        parent,
                        Document.MIME_TYPE_DIR,
                        file.name
                    ) ?: throw IOException("createDocument")
                    exportDirectory(file, dir)
                } else {
                    val mime = if (file.extension.equals("json", ignoreCase = true))
                        "application/json" else "application/octet-stream"
                    val doc = DocumentsContract.createDocument(resolver, parent, mime, file.name)
                        ?: throw IOException("createDocument")
                    val os = resolver.openOutputStream(doc) ?: throw IOException("openOutputStream")
                    os.use { o -> file.inputStream().use { it.copyTo(o) } }
                }
            } catch (e: Exception) {
                val msg = NativeBridge.translate("app.export_failed") + ": " + (e.message ?: "")
                ui { results.add(ResultLine(false, file.name, "", false, msg)) }
            }
        }
    }

    /**
     * @brief 生成目录导出时不会重复的相对路径。
     * @param path 原始相对路径。
     * @param used 已经使用的路径集合。
     * @return 可用的相对路径。
     */
    private fun uniqueRelativePath(path: String, used: MutableSet<String>): String {
        val segments = path.replace('\\', '/')
            .split('/')
            .filter { it.isNotBlank() && it != "." && it != ".." }
        val safePath = segments.ifEmpty { listOf("file") }.joinToString("/")
        var candidate = safePath
        var index = 1
        while (!used.add(candidate.lowercase())) {
            val file = File(safePath)
            val name = if (file.extension.isEmpty()) {
                "${file.nameWithoutExtension}_${index++}"
            } else {
                "${file.nameWithoutExtension}_${index++}.${file.extension}"
            }
            candidate = if (file.parent.isNullOrEmpty()) name else "${file.parent}/$name"
        }
        return candidate
    }
}

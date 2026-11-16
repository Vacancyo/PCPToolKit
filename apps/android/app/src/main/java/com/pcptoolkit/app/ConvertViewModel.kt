package com.pcptoolkit.app

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import java.io.File
import java.io.IOException
import kotlin.concurrent.thread

/** 用户选中的一个文件(SAF 返回的 content:// 地址 + 显示名)。 */
data class PickedFile(val uri: Uri, val name: String)

/** 一条处理结果,界面按当前语言渲染。 */
data class ResultLine(
    val ok: Boolean, val input: String, val output: String,
    val encrypted: Boolean, val message: String
)

/** 任务阶段,界面据此显示文字。 */
enum class Stage { IDLE, COPYING, CONVERTING, EXPORTING, DONE, CANCELLED }

/**
 * 转换流程: 把选中的文件复制到应用缓存 -> 调用 C++ 转换 -> 把结果写入用户选的文件夹。
 * 放在 ViewModel 里,旋转屏幕不会中断任务。
 */
class ConvertViewModel(app: Application) : AndroidViewModel(app) {
    val files = mutableStateListOf<PickedFile>()
    val results = mutableStateListOf<ResultLine>()
    var outTree by mutableStateOf<Uri?>(null)   ///< 用户选的输出文件夹
    var mode by mutableIntStateOf(0)               ///< 0 自动 / 1 RTON->JSON / 2 JSON->RTON
    var key by mutableStateOf("")               ///< RTONKey,空 = 不加密
    var stage by mutableStateOf(Stage.IDLE)
    var done by mutableIntStateOf(0)
    var total by mutableIntStateOf(0)

    val running: Boolean
        get() = stage == Stage.COPYING || stage == Stage.CONVERTING || stage == Stage.EXPORTING

    @Volatile private var cancelled = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private fun ui(block: () -> Unit) { mainHandler.post(block) }
    private val resolver: ContentResolver
        get() = getApplication<Application>().contentResolver

    /** 添加选中的文件(忽略重复)。 */
    fun addFiles(uris: List<Uri>) {
        for (u in uris) {
            if (files.any { it.uri == u }) continue
            files.add(PickedFile(u, queryName(u)))
        }
    }

    fun clearFiles() { if (!running) files.clear() }

    /** 输出文件夹的可读标签,如 "primary:Download/rton"。 */
    fun folderLabel(): String {
        val t = outTree ?: return ""
        return try { DocumentsContract.getTreeDocumentId(t) } catch (_: Exception) { t.toString() }
    }

    private fun queryName(u: Uri): String {
        val n = resolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        return n ?: u.lastPathSegment?.substringAfterLast('/') ?: "file"
    }

    /** 请求取消: 在 C++ 处理完当前文件后停止。 */
    fun cancel() {
        cancelled = true
        NativeBridge.cancel()
    }

    /** 开始转换(在后台线程执行)。 */
    fun start() {
        val tree = outTree ?: return
        if (running || files.isEmpty()) return
        val picked = files.toList()
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

                // 1. 复制到缓存(重名文件加序号前缀)
                val paths = ArrayList<String>()
                val used = HashSet<String>()
                for (f in picked) {
                    val base = f.name.replace('/', '_').replace('\\', '_')
                    var name = base
                    var n = 1
                    while (!used.add(name.lowercase())) name = "${n++}_$base"
                    val dst = File(inDir, name)
                    try {
                        val src = resolver.openInputStream(f.uri) ?: throw IOException("openInputStream")
                        src.use { s -> dst.outputStream().use { o -> s.copyTo(o) } }
                        paths.add(dst.absolutePath)
                    } catch (e: Exception) {
                        val msg = NativeBridge.translate("app.read_failed") + ": " + (e.message ?: "")
                        ui { results.add(ResultLine(false, f.name, "", false, msg)) }
                    }
                }

                // 2. C++ 转换,每处理完一个回调一次
                if (!cancelled && paths.isNotEmpty()) {
                    ui { stage = Stage.CONVERTING }
                    NativeBridge.convert(
                        paths.toTypedArray(), outDir.absolutePath, false, modeValue, keyText
                    ) { d, t, ok, enc, input, output, msg ->
                        val line = ResultLine(ok, File(input).name, File(output).name, enc, msg)
                        ui { done = d; total = t; results.add(line) }
                    }
                }

                // 3. 写入用户选的文件夹
                ui { stage = Stage.EXPORTING }
                val parent = DocumentsContract.buildDocumentUriUsingTree(
                    tree, DocumentsContract.getTreeDocumentId(tree)
                )
                outDir.listFiles()?.sortedBy { it.name }?.forEach { f ->
                    try {
                        val mime = if (f.extension.equals("json", ignoreCase = true))
                            "application/json" else "application/octet-stream"
                        val doc = DocumentsContract.createDocument(resolver, parent, mime, f.name)
                            ?: throw IOException("createDocument")
                        val os = resolver.openOutputStream(doc) ?: throw IOException("openOutputStream")
                        os.use { o -> f.inputStream().use { it.copyTo(o) } }
                    } catch (e: Exception) {
                        val msg = NativeBridge.translate("app.export_failed") + ": " + (e.message ?: "")
                        ui { results.add(ResultLine(false, f.name, "", false, msg)) }
                    }
                }
                ui { stage = if (cancelled) Stage.CANCELLED else Stage.DONE }
            } catch (e: Throwable) {
                val msg = e.message ?: e.toString()
                ui { results.add(ResultLine(false, "", "", false, msg)); stage = Stage.DONE }
            } finally {
                work.deleteRecursively()
            }
        }
    }
}

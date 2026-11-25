package com.pcptoolkit.app.core.native

/**
 *  转换进度回调。在调用 [NativeBridge.convert] 的线程上执行。更新界面时需切换到主线程。
 */
fun interface ProgressListener {
    fun onProgress(
        done: Int, total: Int, ok: Boolean, encrypted: Boolean,
        input: String, output: String, message: String
    )
}

/**
 *  C++ 核心库的 Kotlin 入口。
 */
object NativeBridge {
    init { System.loadLibrary("pcpjni") }

    private external fun tr(key: String, args: Array<String>): String

    /**
     *  注册一种语言的文本。
     *  @param   code  语言代码。
     *  @param   json  该语言的 JSON 文本。
     *  @return  是否注册成功。若代码非法或 JSON 格式错误则返回 false，并保持当前语言。
     */
    external fun addLanguage(code: String, json: String): Boolean

    /**
     *  切换当前语言。
     *  @param   code  语言代码。
     *  @return  是否切换成功。若语言未注册则返回 false，并保持当前语言。
     */
    external fun setLanguage(code: String): Boolean

    /**
     *  获取翻译文本。
     *  @param   key  翻译文本键名。
     *  @param   args  翻译文本中的占位符内容。
     *  @return  翻译文本。
     */
    fun translate(key: String, vararg args: String): String = tr(key, arrayOf(*args))

    /**
     *  执行 JSON 与 RTON 的转换。
     *  @param   inputs  输入的文件或目录。
     *  @param   outDir  输出目录。
     *  @param   recursive  输入是目录时，是否递归处理子目录。
     *  @param   mode  0 则自动按扩展名；1 为 RTON 到 JSON 的转换；2 为 JSON 到 RTON 的转换。
     *  @param   key  RTONKey。
     *  @param   listener  实现了 [ProgressListener] 的对象，每处理完一项在调用线程上回调一次。
     *  @return  失败的项数。
     */
    external fun convert(inputs: Array<String>, outDir: String, recursive: Boolean, mode: Int, key: String, listener: ProgressListener): Int

    /** 请求取消正在进行的转换。 */
    external fun cancel()
}

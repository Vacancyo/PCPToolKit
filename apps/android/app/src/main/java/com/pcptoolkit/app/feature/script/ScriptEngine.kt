package com.pcptoolkit.app.feature.script

data class MatchResult(
    val found: Boolean,
    val left: Double,
    val top: Double,
    val width: Double,
    val height: Double,
    val score: Double
)

interface ScriptHost {
    val isCancelled: Boolean

    fun nowMillis(): Long
    fun log(message: String)
    fun sleep(milliseconds: Long)
    fun tap(x: Double, y: Double)
    fun swipe(x1: Double, y1: Double, x2: Double, y2: Double, durationMillis: Long)

    fun match(
        imageAssetPath: String,
        left: Double,
        top: Double,
        right: Double,
        bottom: Double
    ): MatchResult
}

class ScriptException(message: String) : Exception(message)

object ScriptEngine {
    init {
        System.loadLibrary("pcpjni")
    }

    private external fun executeNative(source: String, host: ScriptHost): String?

    fun execute(source: String, host: ScriptHost) {
        executeNative(source, host)?.let { throw ScriptException(it) }
    }
}

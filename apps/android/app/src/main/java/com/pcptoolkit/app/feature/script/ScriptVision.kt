package com.pcptoolkit.app.feature.script

object ScriptVision {
    init {
        System.loadLibrary("pcpjni")
    }

    /**
     * 返回 [left, top, width, height, score]，无法搜索时返回 null。
     * 所有像素均为 ARGB_8888 整数，区域使用像素坐标。
     */
    external fun find(
        templatePixels: IntArray,
        templateWidth: Int,
        templateHeight: Int,
        screenPixels: IntArray,
        screenWidth: Int,
        screenHeight: Int,
        left: Int,
        top: Int,
        width: Int,
        height: Int
    ): DoubleArray?
}

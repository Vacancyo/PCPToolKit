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
 * @file ScriptVision.kt
 * @brief 脚本视觉匹配接口。
 *
 * 该代码文件声明 Kotlin 与原生模板匹配器之间的图像查找接口。
 */
package com.pcptoolkit.app.feature.script

/**
 * @brief 提供原生模板匹配入口。
 */
object ScriptVision {
    init {
        System.loadLibrary("pcpjni")
    }

    /**
     * @brief 返回 [left，top，width，height，score]，无法搜索时返回 null。
     * 所有像素均为 ARGB_8888 整数，区域使用像素坐标。
     * @param templatePixels 模板像素数组。
     * @param templateWidth 模板宽度。
     * @param templateHeight 模板高度。
     * @param screenPixels 屏幕像素数组。
     * @param screenWidth 屏幕宽度。
     * @param screenHeight 屏幕高度。
     * @param left 搜索区域左侧坐标。
     * @param top 搜索区域顶部坐标。
     * @param width 搜索区域宽度。
     * @param height 搜索区域高度。
     * @return 匹配成功返回结果数组，否则返回空。
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

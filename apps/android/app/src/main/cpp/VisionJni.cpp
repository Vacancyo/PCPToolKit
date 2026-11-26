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
 * @file VisionJni.cpp
 * @brief 图像匹配 JNI 桥。
 *
 * 该代码文件把 Kotlin 像素数组转换为模板匹配所需图像，并返回匹配位置和评分。
 */
#include "script/TemplateMatcher.h"

#include <jni.h>
#include <cstdint>
#include <vector>

namespace {

/**
 * @brief 把 Java 整型像素数组复制为 RGB 像素容器。
 * @param env JNI 环境指针。
 * @param array Java 像素数组。
 * @param width 图像宽度。
 * @param height 图像高度。
 * @return 复制后的像素数据，参数非法时为空。
 */
std::vector<std::uint32_t> toPixels(
    JNIEnv* env,
    jintArray array,
    int width,
    int height
) {
    if (!array || width <= 0 || height <= 0) return {};
    const jsize length = env->GetArrayLength(array);
    const size_t expected = static_cast<size_t>(width) * height;
    if (length < 0 || static_cast<size_t>(length) < expected) return {};
    std::vector<std::uint32_t> pixels(expected);
    env->GetIntArrayRegion(
        array,
        0,
        static_cast<jsize>(expected),
        reinterpret_cast<jint*>(pixels.data())
    );
    return pixels;
}

}  // namespace

extern "C" {

JNIEXPORT jdoubleArray JNICALL

/**
 * @brief 执行 Kotlin 请求的模板匹配。
 * @param env JNI 环境指针。
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
 * @return 匹配成功返回五个数值组成的数组，否则返回空。
 */
Java_com_pcptoolkit_app_feature_script_ScriptVision_find(
    JNIEnv* env,
    jobject,
    jintArray templatePixels,
    jint templateWidth,
    jint templateHeight,
    jintArray screenPixels,
    jint screenWidth,
    jint screenHeight,
    jint left,
    jint top,
    jint width,
    jint height
) {
    try {
        auto templateData = toPixels(env, templatePixels, templateWidth, templateHeight);
        auto screenData = toPixels(env, screenPixels, screenWidth, screenHeight);
        if (templateData.empty() || screenData.empty()) return nullptr;

        pcp::script::RgbaImage templateImage(
            templateWidth,
            templateHeight,
            std::move(templateData)
        );
        pcp::script::RgbaImage screen(
            screenWidth,
            screenHeight,
            std::move(screenData)
        );
        pcp::script::TemplateMatcher matcher;
        const auto prepared = matcher.prepare(templateImage);
        const pcp::script::PixelRect region{left, top, width, height};
        const auto match = matcher.find(screen, prepared, &region, -2.0);
        if (!match) return nullptr;

        jdouble values[5] = {
            static_cast<jdouble>(match->left),
            static_cast<jdouble>(match->top),
            static_cast<jdouble>(match->width),
            static_cast<jdouble>(match->height),
            static_cast<jdouble>(match->score)
        };
        jdoubleArray result = env->NewDoubleArray(5);
        if (!result) return nullptr;
        env->SetDoubleArrayRegion(result, 0, 5, values);
        return result;
    } catch (...) {
        return nullptr;
    }
}

}  // extern "C"

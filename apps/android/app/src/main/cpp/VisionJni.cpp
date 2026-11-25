#include "script/TemplateMatcher.h"

#include <jni.h>
#include <cstdint>
#include <vector>

namespace {

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

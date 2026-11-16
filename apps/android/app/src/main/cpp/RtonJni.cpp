/** @file RtonJni.cpp
 *  JNI 桥。把 C++ 的代码实现暴露给 Kotlin。
 */
#include "JniUtils.h"
#include "rton/RtonConverter.h"
#include "common/I18n.h"

#include <atomic>
#include <cstdint>
#include <jni.h>
#include <string>
#include <vector>

namespace {

std::atomic<bool> gCancel{false};  ///< 取消标志。cancel() 置位，convert() 开始时清零

}  // namespace

extern "C" {

/** @brief 注册一种语言。对应 Kotlin: NativeBridge.addLanguage(code, json): Boolean */
JNIEXPORT jboolean JNICALL Java_com_pcptoolkit_app_NativeBridge_addLanguage(JNIEnv* env, jobject, jstring code, jstring json) {
    return i18n::addLanguage(jni::toUtf8(env, code), jni::toUtf8(env, json)) ? JNI_TRUE : JNI_FALSE;
}

/** @brief 切换语言。对应 Kotlin: NativeBridge.setLanguage(code): Boolean */
JNIEXPORT jboolean JNICALL Java_com_pcptoolkit_app_NativeBridge_setLanguage(JNIEnv* env, jobject, jstring code) {
    return i18n::setLanguage(jni::toUtf8(env, code)) ? JNI_TRUE : JNI_FALSE;
}

/** @brief 取翻译文本。对应 Kotlin: NativeBridge.tr(key, args): String */
JNIEXPORT jstring JNICALL Java_com_pcptoolkit_app_NativeBridge_tr(JNIEnv* env, jobject, jstring key, jobjectArray args) {
    return jni::toJString(env, i18n::translate(jni::toUtf8(env, key), jni::toVector(env, args)));
}

/** @brief 请求取消正在进行的转换。 */
JNIEXPORT void JNICALL Java_com_pcptoolkit_app_NativeBridge_cancel(JNIEnv*, jobject) {
    gCancel = true;
}

/**
 *  @brief   执行 JSON 与 RTON 的转换。
 *  @param   env  JNI 环境指针。
 *  @param   inputs  输入的文件或目录。
 *  @param   outDir  输出目录。
 *  @param   recursive  输入是目录时，是否递归处理子目录。
 *  @param   mode  0 则自动按扩展名；1 为 RTON 到 JSON 的转换；2 为 JSON 到 RTON 的转换。
 *  @param   key  RTONKey。
 *  @param   listener  实现了 ProgressListener 的对象，每处理完一项在调用线程上回调一次。
 *  @return  失败的项数。
 *
 *  阻塞线程。必须在后台线程调用。
 */
JNIEXPORT jint JNICALL Java_com_pcptoolkit_app_NativeBridge_convert(
        JNIEnv* env, jobject, jobjectArray inputs, jstring outDir, jboolean recursive,
        jint mode, jstring key, jobject listener) {
    rton::ConvertOptions opt;
    opt.inputs = jni::toVector(env, inputs);
    opt.outDir = jni::toUtf8(env, outDir);
    opt.recursive = (recursive == JNI_TRUE);
    opt.mode = (mode == 1) ? 'd' : (mode == 2) ? 'e' : 0;
    opt.key = jni::toUtf8(env, key);

    jclass cls = env->GetObjectClass(listener);
    jmethodID mid = env->GetMethodID(cls, "onProgress", "(IIZZLjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
    env->DeleteLocalRef(cls);
    if (!mid) return -1;  // 找不到回调方法，等待抛出 Java 异常

    gCancel = false;
    auto results = rton::RtonConverter(opt).run(
        [&](int done, int total, const rton::TaskResult& r) {
            if (env->ExceptionCheck()) return;  // 回调已抛异常，不用再继续回调
            jstring in = jni::toJString(env, r.in);
            jstring out = jni::toJString(env, r.out);
            jstring msg = jni::toJString(env, r.message);
            env->CallVoidMethod(listener, mid, done, total,
                                r.ok ? JNI_TRUE : JNI_FALSE, r.encrypted ? JNI_TRUE : JNI_FALSE, in, out, msg);
            env->DeleteLocalRef(in);
            env->DeleteLocalRef(out);
            env->DeleteLocalRef(msg);
        },
        &gCancel);

    int failed = 0;
    for (const auto& r : results) if (!r.ok) failed++;
    return failed;
}

}  // extern "C"

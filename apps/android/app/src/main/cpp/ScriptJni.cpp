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
 * @file ScriptJni.cpp
 * @brief 脚本执行 JNI 桥。
 *
 * 该代码文件把脚本宿主回调、脚本执行和错误上报能力暴露给 Kotlin。
 */
#include "JniUtils.h"
#include "script/Error.h"
#include "script/Parser.h"
#include "script/Runtime.h"

#include <jni.h>
#include <stdexcept>
#include <string>

namespace {

/**
 * @brief Java 回调抛出异常时用于中断脚本执行的内部异常。
 */
class JavaCallbackException final : public std::runtime_error {
public:

    /**
     * @brief 构造 Java 回调异常。
     */
    JavaCallbackException() : std::runtime_error("Java callback failed") {}
};

/**
 * @brief 把 Java 宿主对象适配为脚本运行时宿主。
 */
class JniHost final : public pcp::script::Host {
public:

    /**
     * @brief 构造 JNI 宿主并缓存 Java 方法标识。
     * @param env JNI 环境指针。
     * @param host 实现脚本宿主回调的 Java 对象。
     */
    JniHost(JNIEnv* env, jobject host) : env_(env), host_(host) {
        if (!host_) {
            valid_ = false;
            return;
        }
        jclass cls = env_->GetObjectClass(host_);
        if (!cls) {
            valid_ = false;
            return;
        }
        cls_ = cls;
        cancelledMethod_ = env_->GetMethodID(cls_, "isCancelled", "()Z");
        nowMethod_ = env_->GetMethodID(cls_, "nowMillis", "()J");
        logMethod_ = env_->GetMethodID(cls_, "log", "(Ljava/lang/String;)V");
        sleepMethod_ = env_->GetMethodID(cls_, "sleep", "(J)V");
        tapMethod_ = env_->GetMethodID(cls_, "tap", "(DD)V");
        swipeMethod_ = env_->GetMethodID(cls_, "swipe", "(DDDDJ)V");
        matchMethod_ = env_->GetMethodID(
            cls_,
            "match",
            "(Ljava/lang/String;DDDD)Lcom/pcptoolkit/app/feature/script/MatchResult;"
        );
        jclass resultClass = env_->FindClass("com/pcptoolkit/app/feature/script/MatchResult");
        if (!resultClass) {
            valid_ = false;
            return;
        }
        resultClass_ = resultClass;
        foundMethod_ = env_->GetMethodID(resultClass_, "getFound", "()Z");
        leftMethod_ = env_->GetMethodID(resultClass_, "getLeft", "()D");
        topMethod_ = env_->GetMethodID(resultClass_, "getTop", "()D");
        widthMethod_ = env_->GetMethodID(resultClass_, "getWidth", "()D");
        heightMethod_ = env_->GetMethodID(resultClass_, "getHeight", "()D");
        scoreMethod_ = env_->GetMethodID(resultClass_, "getScore", "()D");
        valid_ = cancelledMethod_ && nowMethod_ && logMethod_ && sleepMethod_ &&
                 tapMethod_ && swipeMethod_ && matchMethod_ && foundMethod_ &&
                 leftMethod_ && topMethod_ && widthMethod_ && heightMethod_ &&
                 scoreMethod_;
    }

    /**
     * @brief 判断 JNI 宿主是否初始化成功。
     * @return 初始化成功返回 true。
     */
    bool valid() const { return valid_; }

    /**
     * @brief 查询 Java 宿主是否已请求取消。
     * @return 已请求取消返回 true。
     */
    bool cancelled() const override {
        const jboolean result = env_->CallBooleanMethod(host_, cancelledMethod_);
        check();
        return result == JNI_TRUE;
    }

    /**
     * @brief 获取 Java 宿主提供的当前毫秒时间。
     * @return 当前毫秒时间。
     */
    int64_t nowMillis() const override {
        const jlong result = env_->CallLongMethod(host_, nowMethod_);
        check();
        return static_cast<int64_t>(result);
    }

    /**
     * @brief 把脚本日志转发给 Java 宿主。
     * @param message 日志文本。
     */
    void log(std::string_view message) override {
        jstring value = jni::toJString(env_, std::string(message));
        env_->CallVoidMethod(host_, logMethod_, value);
        env_->DeleteLocalRef(value);
        check();
    }

    /**
     * @brief 请求 Java 宿主休眠。
     * @param milliseconds 休眠毫秒数。
     */
    void sleep(int64_t milliseconds) override {
        env_->CallVoidMethod(host_, sleepMethod_, static_cast<jlong>(milliseconds));
        check();
    }

    /**
     * @brief 请求 Java 宿主执行单击。
     * @param x 归一化横坐标。
     * @param y 归一化纵坐标。
     */
    void tap(double x, double y) override {
        env_->CallVoidMethod(host_, tapMethod_, static_cast<jdouble>(x), static_cast<jdouble>(y));
        check();
    }

    /**
     * @brief 请求 Java 宿主执行滑动。
     * @param x1 起点横坐标。
     * @param y1 起点纵坐标。
     * @param x2 终点横坐标。
     * @param y2 终点纵坐标。
     * @param durationMillis 滑动持续时间，单位为毫秒。
     */
    void swipe(double x1, double y1, double x2, double y2, int64_t durationMillis) override {
        env_->CallVoidMethod(
            host_,
            swipeMethod_,
            static_cast<jdouble>(x1),
            static_cast<jdouble>(y1),
            static_cast<jdouble>(x2),
            static_cast<jdouble>(y2),
            static_cast<jlong>(durationMillis)
        );
        check();
    }

    /**
     * @brief 请求 Java 宿主执行模板匹配。
     * @param assetPath 模板图像资源路径。
     * @param region 归一化匹配区域。
     * @return 模板匹配结果。
     */
    pcp::script::MatchResult match(
        std::string_view assetPath,
        const pcp::script::NormalizedRect& region
    ) override {
        jstring path = jni::toJString(env_, std::string(assetPath));
        jobject result = env_->CallObjectMethod(
            host_,
            matchMethod_,
            path,
            static_cast<jdouble>(region.left),
            static_cast<jdouble>(region.top),
            static_cast<jdouble>(region.right),
            static_cast<jdouble>(region.bottom)
        );
        env_->DeleteLocalRef(path);
        check();
        if (!result) throw JavaCallbackException();

        pcp::script::MatchResult value;
        value.found = env_->CallBooleanMethod(result, foundMethod_) == JNI_TRUE;
        value.left = env_->CallDoubleMethod(result, leftMethod_);
        value.top = env_->CallDoubleMethod(result, topMethod_);
        value.width = env_->CallDoubleMethod(result, widthMethod_);
        value.height = env_->CallDoubleMethod(result, heightMethod_);
        value.score = env_->CallDoubleMethod(result, scoreMethod_);
        env_->DeleteLocalRef(result);
        check();
        return value;
    }

private:

    /**
     * @brief 检查 Java 回调是否产生异常。
     */
    void check() const {
        if (env_->ExceptionCheck()) throw JavaCallbackException();
    }

    JNIEnv* env_;
    jobject host_;
    jclass cls_ = nullptr;
    bool valid_ = true;

    jmethodID cancelledMethod_ = nullptr;
    jmethodID nowMethod_ = nullptr;
    jmethodID logMethod_ = nullptr;
    jmethodID sleepMethod_ = nullptr;
    jmethodID tapMethod_ = nullptr;
    jmethodID swipeMethod_ = nullptr;
    jmethodID matchMethod_ = nullptr;
    jclass resultClass_ = nullptr;
    jmethodID foundMethod_ = nullptr;
    jmethodID leftMethod_ = nullptr;
    jmethodID topMethod_ = nullptr;
    jmethodID widthMethod_ = nullptr;
    jmethodID heightMethod_ = nullptr;
    jmethodID scoreMethod_ = nullptr;
};

}  // namespace

extern "C" {

JNIEXPORT jstring JNICALL

/**
 * @brief 执行 Kotlin 传入的 PCS 脚本并返回错误文本。
 * @param env JNI 环境指针。
 * @param source PCS 脚本源码。
 * @param host 实现脚本宿主回调的 Java 对象。
 * @return 成功返回空，失败返回错误文本。
 */
Java_com_pcptoolkit_app_feature_script_ScriptEngine_executeNative(
    JNIEnv* env,
    jobject,
    jstring source,
    jobject host
) {
    if (!source || !host) {
        return jni::toJString(env, "Script source and host are required");
    }
    try {
        JniHost jniHost(env, host);
        if (!jniHost.valid()) return nullptr;
        const auto program = pcp::script::parseScript(jni::toUtf8(env, source));
        pcp::script::ScriptRuntime(jniHost).execute(program);
        return nullptr;
    } catch (const std::exception& error) {
        if (env->ExceptionCheck()) return nullptr;
        return jni::toJString(env, error.what());
    } catch (...) {
        if (env->ExceptionCheck()) return nullptr;
        return jni::toJString(env, "Unknown script error");
    }
}

}  // extern "C"

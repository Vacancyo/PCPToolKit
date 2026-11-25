#include "JniUtils.h"
#include "script/Error.h"
#include "script/Parser.h"
#include "script/Runtime.h"

#include <jni.h>
#include <stdexcept>
#include <string>

namespace {

class JavaCallbackException final : public std::runtime_error {
public:
    JavaCallbackException() : std::runtime_error("Java callback failed") {}
};

class JniHost final : public pcp::script::Host {
public:
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

    bool valid() const { return valid_; }

    bool cancelled() const override {
        const jboolean result = env_->CallBooleanMethod(host_, cancelledMethod_);
        check();
        return result == JNI_TRUE;
    }

    int64_t nowMillis() const override {
        const jlong result = env_->CallLongMethod(host_, nowMethod_);
        check();
        return static_cast<int64_t>(result);
    }

    void log(std::string_view message) override {
        jstring value = jni::toJString(env_, std::string(message));
        env_->CallVoidMethod(host_, logMethod_, value);
        env_->DeleteLocalRef(value);
        check();
    }

    void sleep(int64_t milliseconds) override {
        env_->CallVoidMethod(host_, sleepMethod_, static_cast<jlong>(milliseconds));
        check();
    }

    void tap(double x, double y) override {
        env_->CallVoidMethod(host_, tapMethod_, static_cast<jdouble>(x), static_cast<jdouble>(y));
        check();
    }

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

#include "JniUtils.h"

namespace jni {

std::string toUtf8(JNIEnv* env, jstring js) {
    if (!js) return {};
    const jchar* p = env->GetStringChars(js, nullptr);
    const jsize n = env->GetStringLength(js);
    std::string out;
    for (jsize i = 0; i < n;) {
        std::uint32_t c = p[i++];
        if (c >= 0xD800 && c < 0xDC00 && i < n && p[i] >= 0xDC00 && p[i] < 0xE000)
            c = 0x10000 + ((c - 0xD800) << 10) + (p[i++] - 0xDC00);  // 代理对到码点的转换
        else if (c >= 0xD800 && c < 0xE000) c = 0xFFFD;  // 孤立代理项
        if (c < 0x80) out += static_cast<char>(c);
        else if (c < 0x800) {
            out += static_cast<char>(0xC0 | (c >> 6));
            out += static_cast<char>(0x80 | (c & 0x3F));
        }
        else if (c < 0x10000) {
            out += static_cast<char>(0xE0 | (c >> 12));
            out += static_cast<char>(0x80 | ((c >> 6) & 0x3F));
            out += static_cast<char>(0x80 | (c & 0x3F));
        }
        else {
            out += static_cast<char>(0xF0 | (c >> 18));
            out += static_cast<char>(0x80 | ((c >> 12) & 0x3F));
            out += static_cast<char>(0x80 | ((c >> 6) & 0x3F));
            out += static_cast<char>(0x80 | (c & 0x3F));
        }
    }
    env->ReleaseStringChars(js, p);  // 释放 JNI 资源
    return out;
}

jstring toJString(JNIEnv* env, const std::string& s) {
    std::vector<jchar> u;
    u.reserve(s.size());
    for (size_t i = 0; i < s.size();) {
        auto c = static_cast<unsigned char>(s[i]);
        std::uint32_t cp = 0xFFFD;
        int len = 1;
        if (c < 0x80) cp = c;
        else if ((c >> 5) == 0x6 && i + 1 < s.size()) { cp = ((c & 0x1F) << 6) | (s[i + 1] & 0x3F); len = 2; }
        else if ((c >> 4) == 0xE && i + 2 < s.size()) {
            cp = ((c & 0x0F) << 12) | ((s[i + 1] & 0x3F) << 6) | (s[i + 2] & 0x3F); len = 3;
        }
        else if ((c >> 3) == 0x1E && i + 3 < s.size()) {
            cp = ((c & 0x07) << 18) | ((s[i + 1] & 0x3F) << 12) | ((s[i + 2] & 0x3F) << 6) | (s[i + 3] & 0x3F); len = 4;
        }
        i += len;
        if (cp >= 0x10000) {
            cp -= 0x10000;
            u.push_back(static_cast<jchar>(0xD800 + (cp >> 10)));
            u.push_back(static_cast<jchar>(0xDC00 + (cp & 0x3FF)));
        } else u.push_back(static_cast<jchar>(cp));
    }
    return env->NewString(u.data(), static_cast<jsize>(u.size()));
}

std::vector<std::string> toVector(JNIEnv* env, jobjectArray arr) {
    std::vector<std::string> v;
    if (!arr) return v;
    const jsize n = env->GetArrayLength(arr);
    for (jsize i = 0; i < n; i++) {
        auto js = reinterpret_cast<jstring>(env->GetObjectArrayElement(arr, i));
        v.push_back(toUtf8(env, js));
        env->DeleteLocalRef(js);
    }
    return v;
}

}  // namespace
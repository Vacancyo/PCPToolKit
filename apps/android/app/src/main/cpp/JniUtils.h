/** @file JniUtils.h
 *  JNI 桥。把 C++ 的代码实现暴露给 Kotlin。
 */
#include <atomic>
#include <cstdint>
#include <jni.h>
#include <string>
#include <vector>

namespace jni {

/**
 *  @brief  将 Java 字符串转换为标准 UTF-8 格式的字符串。
 *  @param  env  JNI 环境指针。
 *  @param  js  Java 字符串。
 *
 *  这里选择不直接用 JNI 自带的 GetStringUTFChars，因为它返回的是 Modified UTF-8（CESU-8），不是标准 UTF-8。
 *  CESU-8 会把 \0 编成两字节 0xC0 0x80，把补充平面字符编码成两个 3 字节代理项，而不是一个 4 字节 UTF-8 序列。
 *  该函数于是实现了手写编码器，且输出的是标准 UTF-8。
 */
std::string toUtf8(JNIEnv* env, jstring js);

/**
 *  @brief  将标准 UTF-8 格式的字符串转换为 Java 字符串。
 *  @param  env  JNI 环境指针。
 *  @param  js  标准 UTF-8 格式的字符串。
 *
 *  与 toUtf8() 函数相对应，实现了手写编码器，输出的是 Java 字符串。非法字节会替换为 U+FFFD。
 */
jstring toJString(JNIEnv* env, const std::string& s);

/**
 *  @brief  将 Java 字符串数组转换为存放字符串的动态数组容器。
 *  @param  env  JNI 环境指针。
 *  @param  arr  Java 字符串数组。
 */
std::vector<std::string> toVector(JNIEnv* env, jobjectArray arr);

}  // namespace
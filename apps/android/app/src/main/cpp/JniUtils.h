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
 * @file JniUtils.h
 * @brief JNI 字符串与数组转换接口。
 *
 * 该代码文件声明 Java 字符串、标准 UTF-8 字符串和 Java 字符串数组之间的安全转换接口。
 */
#include <atomic>
#include <cstdint>
#include <jni.h>
#include <string>
#include <vector>

namespace jni {

/**
 * @brief 将 Java 字符串转换为标准 UTF-8 格式的字符串。
 * @param env JNI 环境指针。
 * @param js Java 字符串。
 *
 * 这里选择不直接用 JNI 自带的 GetStringUTFChars，因为它返回的是 Modified UTF-8（CESU-8），不是标准 UTF-8。
 * CESU-8 会把 \0 编成两字节 0xC0 0x80，把补充平面字符编码成两个 3 字节代理项，而不是一个 4 字节 UTF-8 序列。
 * 该函数于是实现了手写编码器，且输出的是标准 UTF-8。
 */
std::string toUtf8(JNIEnv* env, jstring js);

/**
 * @brief 将标准 UTF-8 格式的字符串转换为 Java 字符串。
 * @param env JNI 环境指针。
 * @param js 标准 UTF-8 格式的字符串。
 *
 * 与 toUtf8（） 函数相对应，实现了手写编码器，输出的是 Java 字符串。非法字节会替换为 U+FFFD。
 */
jstring toJString(JNIEnv* env, const std::string& s);

/**
 * @brief 将 Java 字符串数组转换为存放字符串的动态数组容器。
 * @param env JNI 环境指针。
 * @param arr Java 字符串数组。
 */
std::vector<std::string> toVector(JNIEnv* env, jobjectArray arr);

}  // namespace
/** @file NumberFormat.h
 *  浮点数与文本之间的无损格式化工具。
 */
#pragma once

#include <string>

namespace rton {

/**
 *  @brief   把单精度浮点转成可无损还原的最短十进制文本。
 *  @param   f  输入单精度浮点数。
 *  @return  最短且还原后与 f 相等的文本。
 */
std::string fmtFloat(float f);

/**
 *  @brief   把双精度浮点转成可无损还原的最短十进制文本。
 *  @param   d  输入双精度浮点数。
 *  @return  最短且还原后与 d 相等的文本。
 */
std::string fmtDouble(double d);

/**
 *  @brief   保证浮点数文本含小数点，确保重新编码时被识别为浮点数。
 *  @param   t  数字文本。
 *  @return  含小数点的文本。
 */
std::string makeFloatText(std::string t);

}  // namespace rton

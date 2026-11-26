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
 * @file NumberFormat.h
 * @brief 浮点数文本格式化接口。
 *
 * 该代码文件声明可无损还原的浮点数文本格式化函数，以及保证文本被识别为浮点数的小数点补全函数。
 */
#pragma once

#include <string>

namespace pcp::rton {

/**
 * @brief 把单精度浮点转成可无损还原的最短十进制文本。
 * @param f 输入单精度浮点数。
 * @return 最短且还原后与 f 相等的文本。
 */
std::string fmtFloat(float f);

/**
 * @brief 把双精度浮点转成可无损还原的最短十进制文本。
 * @param d 输入双精度浮点数。
 * @return 最短且还原后与 d 相等的文本。
 */
std::string fmtDouble(double d);

/**
 * @brief 保证浮点数文本含小数点，确保重新编码时被识别为浮点数。
 * @param t 数字文本。
 * @return 含小数点的文本。
 */
std::string makeFloatText(std::string t);

}  // namespace pcp::rton

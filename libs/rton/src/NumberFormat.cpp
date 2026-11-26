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
 * @file NumberFormat.cpp
 * @brief 浮点数文本格式化实现。
 *
 * 该代码文件通过逐级增加有效数字尝试最短可逆文本，并补全缺少的小数点。
 */
#include "rton/NumberFormat.h"

#include <cstdio>
#include <cstdlib>

namespace pcp::rton {

std::string fmtFloat(float f) {
    char buf[64];
    for (int prec = 1; prec <= 9; prec++) {
        std::snprintf(buf, sizeof buf, "%.*g", prec, static_cast<double>(f));
        if (std::strtof(buf, nullptr) == f) break;
    }
    return buf;
}

std::string fmtDouble(double d) {
    char buf[64];
    for (int prec = 1; prec <= 17; prec++) {
        std::snprintf(buf, sizeof buf, "%.*g", prec, d);
        if (std::strtod(buf, nullptr) == d) break;
    }
    return buf;
}

std::string makeFloatText(std::string t) {
    if (t.find_first_of("nNiI") != std::string::npos) return "null";
    if (t.find('.') != std::string::npos) return t;
    size_t e = t.find_first_of("eE");
    if (e == std::string::npos) return t + ".0";
    return t.substr(0, e) + ".0" + t.substr(e);
}

}  // namespace pcp::rton

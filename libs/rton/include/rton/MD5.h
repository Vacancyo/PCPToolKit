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
 * @file MD5.h
 * @brief MD5 摘要接口。
 *
 * 该代码文件声明用于从 RTONKey 派生加密密钥的 MD5 十六进制摘要函数。
 */
#pragma once

#include <string>

namespace pcp::rton {

/**
 * @brief 计算 MD5，返回 32 位小写十六进制文本。
 * @param msg 输入数据。按字节处理，调用方应传 UTF-8。
 * @return 32 个字符的小写十六进制摘要。
 */
std::string md5Hex(const std::string& msg);

}  // namespace pcp::rton

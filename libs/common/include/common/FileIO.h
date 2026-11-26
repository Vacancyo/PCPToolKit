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
 * @file FileIO.h
 * @brief 通用原始字节文件读写接口。
 *
 * 声明一个完整文件读取和覆盖写入的接口。
 */
#pragma once

#include <string>

#include "common/Bytes.h"

namespace pcp::common {

/**
 * @brief 读取任意文件的全部原始字节。
 * @param path 文件路径。
 * @return 文件全部字节。
 * @throw std::runtime_error 当文件无法打开时抛出。
 *
 * 根据 \p path 读取文件，然后返回存储文件内容的数组。
 */
Bytes readAll(const std::string& path);

/**
 * @brief 将原始字节完整写入文件。
 * @param path 文件路径。
 * @param data 数据指针。
 * @param n 字节数。
 */
void writeAll(const std::string& path, const void* data, size_t n);

}  // namespace pcp::common

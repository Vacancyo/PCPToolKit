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
 * @file Bytes.h
 * @brief 通用字节容器。
 *
 * 定义通用无符号字节容器类型。
 */
#pragma once

#include <cstdint>
#include <vector>

namespace pcp::common {

using Bytes = std::vector<std::uint8_t>;  ///< 字节数组，用于保存原始字节序列。

}  // namespace pcp::common

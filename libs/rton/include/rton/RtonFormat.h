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
 * @file RtonFormat.h
 * @brief RTON 文件格式常量。
 *
 * 该代码文件定义 RTON 文件头魔数、文件尾标记和当前格式版本号。
 */
#pragma once

#include <cstdint>
#include <string>

namespace pcp::rton {

inline const std::string kMagic = "RTON";     ///< 文件头魔数
inline const std::string kEofTag = "DONE";    ///< 文件尾标记
inline constexpr std::uint32_t kVersion = 1;  ///< RTON 版本号

}  // namespace pcp::rton

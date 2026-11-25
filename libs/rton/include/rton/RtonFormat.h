/** @file RtonFormat.h
 *  RTON 文件格式常量。
 */
#pragma once

#include <cstdint>
#include <string>

namespace rton {

inline const std::string kMagic = "RTON";     ///< 文件头魔数
inline const std::string kEofTag = "DONE";    ///< 文件尾标记
inline constexpr std::uint32_t kVersion = 1;  ///< RTON 版本号

}  // namespace rton
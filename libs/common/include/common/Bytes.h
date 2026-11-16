/** @file Bytes.h
 *  通用字节数组类型别名。
 */
#pragma once

#include <cstdint>
#include <vector>

namespace common {

/// 字节数组，用于表示二进制文件内容
using Bytes = std::vector<std::uint8_t>;

}  // namespace common
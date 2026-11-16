/** @file MD5.h
 *  MD5 摘要。用于由 RTONKey 派生 Rijndael 密钥。
 */
#pragma once

#include <string>

namespace rton {

/**
 *  @brief   计算 MD5，返回 32 位小写十六进制文本。
 *  @param   msg  输入数据。按字节处理，调用方应传 UTF-8。
 *  @return  32 个字符的小写十六进制摘要。
 */
std::string md5Hex(const std::string& msg);

}  // namespace rton
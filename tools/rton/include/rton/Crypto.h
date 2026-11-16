/** @file Crypto.h
 *  对受加密的 RTON 的加解密。加密 RTON 使用 Rijndael-192 + CBC + ZeroBytePadding 算法。
 */
#pragma once

#include "common/Bytes.h"

#include <string>

using common::Bytes;

namespace rton {

/**
 *  @brief   判断数据是否为加密 RTON。
 *  @param   data  文件内容。
 *  @return  是加密 RTON 返回 true。
 */
bool isEncryptedRton(const Bytes& data);

/**
 *  @brief   加密 RTON 数据。
 *  @param   plain  未加密的 RTON 数据。
 *  @param   key  RTONKey。
 *  @return  以 0x10 0x00 开头的加密数据。
 */
Bytes rtonEncrypt(const Bytes& plain, const std::string& key);

/**
 *  @brief   解密 RTON 数据。
 *  @param   file  完整加密文件内容。
 *  @param   key  RTONKey。
 *  @return  解密后的数据。
 */
Bytes rtonDecrypt(const Bytes& file, const std::string& key);

}  // namespace rton
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
 * @file Crypto.h
 * @brief RTON 加密与解密接口。
 *
 * 该代码文件声明 RTON 加密文件识别、密钥派生、加密以及解密接口，供 RTON 编解码流程使用。
 */
#pragma once

#include <string>

#include "common/Bytes.h"

namespace pcp::rton {

using pcp::common::Bytes;

/**
 * @brief 判断数据是否为加密 RTON。
 * @param data 文件内容。
 * @return 是加密 RTON 返回 true。
 */
bool isEncryptedRton(const Bytes& data);

/**
 * @brief 加密 RTON 数据。
 * @param plain 未加密的 RTON 数据。
 * @param key RTONKey。
 * @return 以 0x10 0x00 开头的加密数据。
 */
Bytes rtonEncrypt(const Bytes& plain, const std::string& key);

/**
 * @brief 解密 RTON 数据。
 * @param file 完整加密文件内容。
 * @param key RTONKey。
 * @return 解密后的数据。
 */
Bytes rtonDecrypt(const Bytes& file, const std::string& key);

}  // namespace pcp::rton

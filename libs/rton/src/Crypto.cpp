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
 * @file Crypto.cpp
 * @brief RTON 加密与解密实现。
 *
 * 该代码文件实现 RTON 加密文件头识别、基于 RTONKey 的密钥生成、CBC 风格分组加密和解密流程。
 */
#include "rton/Crypto.h"

#include <cstring>
#include <stdexcept>

#include "rton/MD5.h"
#include "rton/Rijndael.h"
#include "common/I18n.h"

namespace pcp::rton {

using pcp::common::Bytes;

namespace {

constexpr int kBlock = 24;  ///< 分组字节数（192 位）

/**
 * @brief 由 RTONKey 构造 Rijndael 对象，并输出 24 字节 IV。
 */
Rijndael makeCipher(const std::string& rtonKey, std::uint8_t iv[kBlock]) {
    std::string k = md5Hex(rtonKey);  // 32 个十六进制字符，其字节即为密钥
    std::memcpy(iv, k.data() + 4, kBlock);
    return Rijndael(reinterpret_cast<const std::uint8_t*>(k.data()), static_cast<int>(k.size()), kBlock);
}

}  // namespace

bool isEncryptedRton(const Bytes& data) { return data.size() >= 2 && data[0] == 0x10 && data[1] == 0x00; }

Bytes rtonEncrypt(const Bytes& plain, const std::string& key) {
    std::uint8_t prev[kBlock];
    Rijndael c = makeCipher(key, prev);
    Bytes in = plain;
    in.insert(in.end(), kBlock - in.size() % kBlock, 0);  // 总会补零；恰为整数倍时多补一整块
    Bytes out = {0x10, 0x00};
    for (size_t off = 0; off < in.size(); off += kBlock) {
        std::uint8_t blk[kBlock];
        for (int i = 0; i < kBlock; i++) blk[i] = in[off + i] ^ prev[i];  // CBC：先与前一密文块异或
        c.encryptBlock(blk);
        std::memcpy(prev, blk, kBlock);
        out.insert(out.end(), blk, blk + kBlock);
    }
    return out;
}

Bytes rtonDecrypt(const Bytes& file, const std::string& key) {
    if (file.size() < 2) throw std::runtime_error(pcp::i18n::translate("crypto.too_short"));
    size_t n = file.size() - 2;
    if (n % kBlock) throw std::runtime_error(pcp::i18n::translate("crypto.bad_length"));
    std::uint8_t prev[kBlock];
    Rijndael c = makeCipher(key, prev);
    Bytes out; out.reserve(n);
    for (size_t off = 2; off < file.size(); off += kBlock) {
        std::uint8_t blk[kBlock], cur[kBlock];
        std::memcpy(cur, &file[off], kBlock); std::memcpy(blk, cur, kBlock);
        c.decryptBlock(blk);
        for (int i = 0; i < kBlock; i++) blk[i] ^= prev[i];
        std::memcpy(prev, cur, kBlock);
        out.insert(out.end(), blk, blk + kBlock);
    }
    return out;
}

}  // namespace pcp::rton

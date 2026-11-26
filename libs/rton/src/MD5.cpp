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
 * @file MD5.cpp
 * @brief MD5 摘要实现。
 *
 * 该代码文件实现标准 MD5 分块计算流程，并输出 32 位小写十六进制摘要。
 */
#include "rton/MD5.h"

#include <cstdint>
#include <cstdio>

namespace pcp::rton {

std::string md5Hex(const std::string& msg) {
    static const std::uint32_t K[64] = {
        0xd76aa478,0xe8c7b756,0x242070db,0xc1bdceee,0xf57c0faf,0x4787c62a,0xa8304613,0xfd469501,
        0x698098d8,0x8b44f7af,0xffff5bb1,0x895cd7be,0x6b901122,0xfd987193,0xa679438e,0x49b40821,
        0xf61e2562,0xc040b340,0x265e5a51,0xe9b6c7aa,0xd62f105d,0x02441453,0xd8a1e681,0xe7d3fbc8,
        0x21e1cde6,0xc33707d6,0xf4d50d87,0x455a14ed,0xa9e3e905,0xfcefa3f8,0x676f02d9,0x8d2a4c8a,
        0xfffa3942,0x8771f681,0x6d9d6122,0xfde5380c,0xa4beea44,0x4bdecfa9,0xf6bb4b60,0xbebfbc70,
        0x289b7ec6,0xeaa127fa,0xd4ef3085,0x04881d05,0xd9d4d039,0xe6db99e5,0x1fa27cf8,0xc4ac5665,
        0xf4292244,0x432aff97,0xab9423a7,0xfc93a039,0x655b59c3,0x8f0ccc92,0xffeff47d,0x85845dd1,
        0x6fa87e4f,0xfe2ce6e0,0xa3014314,0x4e0811a1,0xf7537e82,0xbd3af235,0x2ad7d2bb,0xeb86d391};
    static const int S[64] = {7,12,17,22,7,12,17,22,7,12,17,22,7,12,17,22,5,9,14,20,5,9,14,20,5,9,14,20,5,9,14,20,
                              4,11,16,23,4,11,16,23,4,11,16,23,4,11,16,23,6,10,15,21,6,10,15,21,6,10,15,21,6,10,15,21};
    std::uint32_t a0 = 0x67452301, b0 = 0xefcdab89, c0 = 0x98badcfe, d0 = 0x10325476;
    std::string m = msg;
    std::uint64_t bitLen = static_cast<std::uint64_t>(msg.size()) * 8;
    m += static_cast<char>(0x80);  // 填充：0x80 + 若干 0 + 8 字节位长度
    while (m.size() % 64 != 56) m += static_cast<char>(0);
    for (int i = 0; i < 8; i++) m += static_cast<char>((bitLen >> (8 * i)) & 0xFF);
    for (size_t off = 0; off < m.size(); off += 64) {
        std::uint32_t M[16];
        for (int i = 0; i < 16; i++)
            M[i] = static_cast<std::uint32_t>(static_cast<std::uint8_t>(m[off + 4 * i])) |
                   (static_cast<std::uint32_t>(static_cast<std::uint8_t>(m[off + 4 * i + 1])) << 8) |
                   (static_cast<std::uint32_t>(static_cast<std::uint8_t>(m[off + 4 * i + 2])) << 16) |
                   (static_cast<std::uint32_t>(static_cast<std::uint8_t>(m[off + 4 * i + 3])) << 24);
        std::uint32_t A = a0, B = b0, C = c0, D = d0;
        for (int i = 0; i < 64; i++) {
            std::uint32_t F; int g;
            if (i < 16) { F = (B & C) | (~B & D); g = i; }
            else if (i < 32) { F = (D & B) | (~D & C); g = (5 * i + 1) % 16; }
            else if (i < 48) { F = B ^ C ^ D; g = (3 * i + 5) % 16; }
            else { F = C ^ (B | ~D); g = (7 * i) % 16; }
            F = F + A + K[i] + M[g];
            A = D; D = C; C = B;
            B = B + ((F << S[i]) | (F >> (32 - S[i])));
        }
        a0 += A; b0 += B; c0 += C; d0 += D;
    }
    char out[40]; std::uint32_t r[4] = {a0, b0, c0, d0};
    for (int i = 0; i < 4; i++)
        for (int j = 0; j < 4; j++) std::snprintf(out + i * 8 + j * 2, 3, "%02x", (r[i] >> (8 * j)) & 0xFF);
    return std::string(out, 32);
}

}  // namespace pcp::rton

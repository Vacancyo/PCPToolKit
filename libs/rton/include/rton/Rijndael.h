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
 * @file Rijndael.h
 * @brief 可变分组 Rijndael 密码接口。
 *
 * 该代码文件声明支持 128 至 256 位分组和密钥长度的 Rijndael 分组加密器。
 */
#pragma once

#include <cstdint>
#include <vector>

namespace pcp::rton {

/**
 * @brief Rijndael 分组密码。分组和密钥长度均可取 128/160/192/224/256 位。
 */
class Rijndael {
public:

    /**
     * @brief 构造并完成密钥扩展。
     * @param key 密钥字节。
     * @param keyLen 密钥字节数（16 到 32，且为 4 的倍数）。
     * @param blockLen 分组字节数（16 到 32，且为 4 的倍数）。
     */
    Rijndael(const std::uint8_t* key, int keyLen, int blockLen);

    /**
     * @brief 原地加密一个分组。
     * @param s 指向 blockLen 字节的缓冲区。
     */
    void encryptBlock(std::uint8_t* s) const;

    /**
     * @brief 原地解密一个分组。
     * @param s 指向 blockLen 字节的缓冲区。
     */
    void decryptBlock(std::uint8_t* s) const;

private:
    int Nb_;                         ///< 分组包含的 32 位字数
    int Nk_;                         ///< 密钥包含的 32 位字数
    int Nr_ = 0;                     ///< 加密轮数
    std::vector<std::uint8_t> w_;    ///< 扩展后的轮密钥
    static std::uint8_t sbox_[256];  ///< S 盒
    static std::uint8_t inv_[256];   ///< 逆 S 盒
    static bool ready_;              ///< S 盒是否已生成

    /**
     * @brief 计算 GF（2^8） 上乘以 2 的结果。
     * @param x 输入字节。
     * @return 乘以 2 后的字节。
     */
    static std::uint8_t xtime(std::uint8_t x);

    /**
     * @brief 计算 GF（2^8） 乘法。
     * @param a 左操作数。
     * @param b 右操作数。
     * @return 乘积。
     */
    static std::uint8_t mul(std::uint8_t a, std::uint8_t b);

    /**
     * @brief 首次使用时生成 S 盒与逆 S 盒。
     */
    static void initTables();

    /**
     * @brief 执行轮密钥加。
     * @param s 当前分组状态。
     * @param round 轮次编号。
     */
    void addKey(std::uint8_t* s, int round) const;

    /**
     * @brief 使用 S 盒或逆 S 盒执行字节替换。
     * @param s 当前分组状态。
     * @param box S 盒或逆 S 盒。
     */
    void subBytes(std::uint8_t* s, const std::uint8_t* box) const;

    /**
     * @brief 执行行移位。
     * @param s 当前分组状态。
     * @param invert 是否执行逆变换。
     */
    void shiftRows(std::uint8_t* s, bool invert) const;

    /**
     * @brief 执行列混合。
     * @param s 当前分组状态。
     * @param invert 是否执行逆变换。
     */
    void mixColumns(std::uint8_t* s, bool invert) const;
};

}  // namespace pcp::rton

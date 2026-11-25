/** @file Rijndael.h
 *  分组长度可变的 Rijndael 分组密码。
 */
#pragma once

#include <cstdint>
#include <vector>

namespace rton {

/**
 *  @brief  Rijndael 分组密码。分组和密钥长度均可取 128/160/192/224/256 位。
 */
class Rijndael {
public:
    /**
     *  @brief  构造并完成密钥扩展。
     *  @param  key  密钥字节。
     *  @param  keyLen  密钥字节数（16 到 32，且为 4 的倍数）。
     *  @param  blockLen  分组字节数（16 到 32，且为 4 的倍数）。
     */
    Rijndael(const std::uint8_t* key, int keyLen, int blockLen);

    /**
     *  @brief  原地加密一个分组。
     *  @param  s  指向 blockLen 字节的缓冲区。
     */
    void encryptBlock(std::uint8_t* s) const;
    /**
     *  @brief  原地解密一个分组。
     *  @param  s  指向 blockLen 字节的缓冲区。
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

    static std::uint8_t xtime(std::uint8_t x);                ///< GF(2^8) 上乘以 2
    static std::uint8_t mul(std::uint8_t a, std::uint8_t b);  ///< GF(2^8) 乘法
    static void initTables();                                 ///< 首次使用时生成 S 盒与逆 S 盒

    void addKey(std::uint8_t* s, int round) const;                  ///< 轮密钥加
    void subBytes(std::uint8_t* s, const std::uint8_t* box) const;  ///< 字节替换(box 为 sbox_ 或 inv_)
    void shiftRows(std::uint8_t* s, bool invert) const;             ///< 行移位(invert 为逆运算)
    void mixColumns(std::uint8_t* s, bool invert) const;            ///< 列混合(invert 为逆运算)
};

}  // namespace rton
#include "rton/Crypto.h"

#include <cstring>
#include <stdexcept>

#include "rton/MD5.h"
#include "rton/Rijndael.h"
#include "common/I18n.h"

namespace rton {

using common::Bytes;

namespace {

constexpr int kBlock = 24;  ///< 分组字节数(192 位)

/** @brief  由 RTONKey 构造 Rijndael 对象，并输出 24 字节 IV。 */
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
    if (file.size() < 2) throw std::runtime_error(i18n::translate("crypto.too_short"));
    size_t n = file.size() - 2;
    if (n % kBlock) throw std::runtime_error(i18n::translate("crypto.bad_length"));
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

}  // namespace rton
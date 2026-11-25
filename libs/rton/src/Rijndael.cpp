#include "rton/Rijndael.h"

#include <cstring>
#include <stdexcept>

#include "common/I18n.h"

namespace rton {

std::uint8_t Rijndael::sbox_[256];
std::uint8_t Rijndael::inv_[256];
bool Rijndael::ready_ = false;

std::uint8_t Rijndael::xtime(std::uint8_t x) { return static_cast<std::uint8_t>((x << 1) ^ ((x & 0x80) ? 0x1B : 0)); }

std::uint8_t Rijndael::mul(std::uint8_t a, std::uint8_t b) {
    std::uint8_t p = 0;
    while (b) { if (b & 1) p ^= a; a = xtime(a); b >>= 1; }
    return p;
}

void Rijndael::initTables() {
    if (ready_) return;
    for (int i = 0; i < 256; i++) {
        std::uint8_t x = 0;  // x = i 在 GF(2^8) 中的乘法逆元。0 的逆元定义为 0
        if (i) { for (int c = 1; c < 256; c++) if (mul(static_cast<std::uint8_t>(i), static_cast<std::uint8_t>(c)) == 1) { x = static_cast<std::uint8_t>(c); break; } }
        std::uint8_t s = x;  // 仿射变换
        for (int k = 1; k <= 4; k++) s ^= static_cast<std::uint8_t>((x << k) | (x >> (8 - k)));
        s ^= 0x63;
        sbox_[i] = s; inv_[s] = static_cast<std::uint8_t>(i);
    }
    ready_ = true;
}

Rijndael::Rijndael(const std::uint8_t* key, int keyLen, int blockLen) : Nb_(blockLen / 4), Nk_(keyLen / 4) {
    if (blockLen % 4 || Nb_ < 4 || Nb_ > 8 || keyLen % 4 || Nk_ < 4 || Nk_ > 8) throw std::runtime_error(i18n::translate("rijndael.bad_params"));
    initTables();
    Nr_ = (Nk_ > Nb_ ? Nk_ : Nb_) + 6;
    int total = Nb_ * (Nr_ + 1);
    w_.assign(static_cast<size_t>(total) * 4, 0);
    std::memcpy(w_.data(), key, static_cast<size_t>(keyLen));
    std::uint8_t rcon = 1;
    for (int i = Nk_; i < total; i++) {  // 密钥扩展
        std::uint8_t t[4]; std::memcpy(t, &w_[static_cast<size_t>(i - 1) * 4], 4);
        if (i % Nk_ == 0) {
            std::uint8_t x = t[0];
            t[0] = sbox_[t[1]] ^ rcon; t[1] = sbox_[t[2]]; t[2] = sbox_[t[3]]; t[3] = sbox_[x];
            rcon = xtime(rcon);
        } else if (Nk_ > 6 && i % Nk_ == 4) { for (int k = 0; k < 4; k++) t[k] = sbox_[t[k]]; }
        for (int k = 0; k < 4; k++) w_[static_cast<size_t>(i) * 4 + k] = w_[static_cast<size_t>(i - Nk_) * 4 + k] ^ t[k];
    }
}

void Rijndael::addKey(std::uint8_t* s, int round) const {
    for (int i = 0; i < Nb_ * 4; i++) s[i] ^= w_[static_cast<size_t>(round) * Nb_ * 4 + i];
}

void Rijndael::subBytes(std::uint8_t* s, const std::uint8_t* box) const {
    for (int i = 0; i < Nb_ * 4; i++) s[i] = box[s[i]];
}

void Rijndael::shiftRows(std::uint8_t* s, bool invert) const {
    int sh[4] = {0, 1, 2, 3};
    if (Nb_ == 8) { sh[2] = 3; sh[3] = 4; }
    std::uint8_t t[32];
    for (int r = 0; r < 4; r++)
        for (int c = 0; c < Nb_; c++) {
            int from = invert ? ((c - sh[r]) % Nb_ + Nb_) % Nb_ : (c + sh[r]) % Nb_;
            t[r + 4 * c] = s[r + 4 * from];
        }
    std::memcpy(s, t, static_cast<size_t>(Nb_) * 4);
}

void Rijndael::mixColumns(std::uint8_t* s, bool invert) const {
    static const std::uint8_t F[4] = {2, 3, 1, 1}, I[4] = {14, 11, 13, 9};
    const std::uint8_t* m = invert ? I : F;
    for (int c = 0; c < Nb_; c++) {
        std::uint8_t a[4], *col = s + 4 * c;
        std::memcpy(a, col, 4);
        for (int r = 0; r < 4; r++)
            col[r] = mul(a[0], m[(4 - r) % 4]) ^ mul(a[1], m[(5 - r) % 4]) ^ mul(a[2], m[(6 - r) % 4]) ^ mul(a[3], m[(7 - r) % 4]);
    }
}

void Rijndael::encryptBlock(std::uint8_t* s) const {
    addKey(s, 0);
    for (int r = 1; r < Nr_; r++) { subBytes(s, sbox_); shiftRows(s, false); mixColumns(s, false); addKey(s, r); }
    subBytes(s, sbox_); shiftRows(s, false); addKey(s, Nr_);
}

void Rijndael::decryptBlock(std::uint8_t* s) const {
    addKey(s, Nr_);
    for (int r = Nr_ - 1; r >= 1; r--) { shiftRows(s, true); subBytes(s, inv_); addKey(s, r); mixColumns(s, true); }
    shiftRows(s, true); subBytes(s, inv_); addKey(s, 0);
}

}  // namespace rton
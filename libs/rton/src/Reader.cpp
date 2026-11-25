#include "rton/Reader.h"

#include <cstring>
#include <stdexcept>

#include "common/I18n.h"

namespace rton {

void Reader::fail(const std::string& key, const std::vector<std::string>& args) const {
    // 先把具体原因翻译出来，再嵌进 "RTON 错误(偏移 N): 原因" 这个外层模板
    throw std::runtime_error(i18n::translate("reader.error_at", {std::to_string(pos_), i18n::translate(key, args)}));
}

std::uint8_t Reader::u8() { if (pos_ >= d_.size()) fail("reader.eof"); return d_[pos_++]; }
std::uint16_t Reader::u16() { std::uint16_t a = u8(); a |= static_cast<std::uint16_t>(u8()) << 8; return a; }
std::uint32_t Reader::u32() { std::uint32_t v = 0; for (int i = 0; i < 4; i++) v |= static_cast<std::uint32_t>(u8()) << (8 * i); return v; }
std::uint64_t Reader::u64() { std::uint64_t v = 0; for (int i = 0; i < 8; i++) v |= static_cast<std::uint64_t>(u8()) << (8 * i); return v; }
float Reader::f32() { std::uint32_t x = u32(); float f; std::memcpy(&f, &x, 4); return f; }
double Reader::f64() { std::uint64_t x = u64(); double f; std::memcpy(&f, &x, 8); return f; }

std::uint32_t Reader::uvar32() {
    std::uint32_t v = 0; int sh = 0;
    for (int i = 0; i < 5; i++) {
        std::uint8_t b = u8();
        v |= static_cast<std::uint32_t>(b & 0x7F) << sh;
        if (!(b & 0x80)) return v;
        sh += 7;
    }
    fail("reader.varint32_too_long");
}

std::uint64_t Reader::uvar64() {
    std::uint64_t v = 0; int sh = 0;
    for (int i = 0; i < 10; i++) {
        std::uint8_t b = u8();
        v |= static_cast<std::uint64_t>(b & 0x7F) << sh;
        if (!(b & 0x80)) return v;
        sh += 7;
    }
    fail("reader.varint64_too_long");
}

std::string Reader::bytes(size_t n) {
    if (n > d_.size() - pos_) fail("reader.length_out_of_range");
    std::string s(reinterpret_cast<const char*>(&d_[pos_]), n);
    pos_ += n;
    return s;
}

std::string Reader::strHead() { return bytes(uvar32()); }

void Reader::expect8(std::uint8_t x) {
    std::uint8_t g = u8();
    if (g != x) { pos_--; fail("reader.expect_byte", {std::to_string(x), std::to_string(g)}); }
}

}  // namespace rton
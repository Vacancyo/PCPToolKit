#include "rton/Decoder.h"
#include "rton/NumberFormat.h"
#include "rton/RtonFormat.h"
#include "common/I18n.h"

#include <cstdio>
#include <stdexcept>

using std::string;

namespace rton {

namespace {

/** @brief  把类型字节格式化为 0xNN，用于错误信息。 */
std::string hexByte(std::uint8_t t) {
    char buf[8];
    std::snprintf(buf, sizeof buf, "0x%02X", t);
    return buf;
}

}  // namespace

string Decoder::readBinary() {
    r_.u8();  // 保留字节，忽略
    string s = r_.strHead();
    auto i = static_cast<std::int32_t>(r_.uvar32());
    return "$BINARY(\"" + s + "\", " + std::to_string(i) + ")";
}

string Decoder::readRTID() {
    std::uint8_t t = r_.u8();
    char buf[64];
    switch (t) {
        case 0x00: return "RTID(0)";
        case 0x01: {
            std::uint32_t v2 = r_.uvar32(), v1 = r_.uvar32(), x = r_.u32();
            std::snprintf(buf, sizeof buf, "RTID(%d.%d.%08x@)", static_cast<std::int32_t>(v1), static_cast<std::int32_t>(v2), x);
            return buf;
        }
        case 0x02: {
            r_.uvar32();  // 字符数，忽略
            string s = r_.strHead();
            std::uint32_t v2 = r_.uvar32(), v1 = r_.uvar32(), x = r_.u32();
            std::snprintf(buf, sizeof buf, "RTID(%d.%d.%08x@", static_cast<std::int32_t>(v1), static_cast<std::int32_t>(v2), x);
            return string(buf) + s + ")";
        }
        case 0x03: {
            r_.uvar32(); string s2 = r_.strHead();
            r_.uvar32(); string s1 = r_.strHead();
            return "RTID(" + s1 + "@" + s2 + ")";
        }
        default: r_.back(); r_.fail("decoder.unknown_rtid_subtype", {hexByte(t)});
    }
}

bool Decoder::readStringLike(std::uint8_t t, string& out) {
    switch (t) {
        case 0x02: out = "*"; return true;
        case 0x81: out = r_.strHead(); return true;
        case 0x82: r_.uvar32(); out = r_.strHead(); return true;
        case 0x83: out = readRTID(); return true;
        case 0x84: out = "RTID(0)"; return true;
        case 0x87: out = readBinary(); return true;
        case 0x90: out = r_.strHead(); pool90_.push_back(out); return true;
        case 0x91: { auto i = r_.uvar32(); if (i >= pool90_.size()) r_.fail("decoder.pool90_oob"); out = pool90_[i]; return true; }
        case 0x92: r_.uvar32(); out = r_.strHead(); pool92_.push_back(out); return true;
        case 0x93: { auto i = r_.uvar32(); if (i >= pool92_.size()) r_.fail("decoder.pool92_oob"); out = pool92_[i]; return true; }
    }
    return false;
}

Value Decoder::readValue(std::uint8_t t) {
    using std::int32_t; using std::int64_t; using std::uint32_t; using std::uint64_t;
    string s;
    if (readStringLike(t, s)) return Value::str(std::move(s));
    switch (t) {
        case 0x00: return Value::boolean(false);
        case 0x01: return Value::boolean(true);
        case 0x08: return Value::num(std::to_string(static_cast<int>(static_cast<std::int8_t>(r_.u8()))));
        case 0x0A: return Value::num(std::to_string(static_cast<int>(r_.u8())));
        case 0x10: return Value::num(std::to_string(static_cast<int>(static_cast<std::int16_t>(r_.u16()))));
        case 0x12: return Value::num(std::to_string(static_cast<int>(r_.u16())));
        case 0x20: return Value::num(std::to_string(static_cast<int32_t>(r_.u32())));
        case 0x22: return Value::num(makeFloatText(fmtFloat(r_.f32())));
        case 0x24: return Value::num(std::to_string(static_cast<int32_t>(r_.uvar32())));
        case 0x25: { uint32_t u = r_.uvar32(); return Value::num(std::to_string(static_cast<int32_t>((u >> 1) ^ (0u - (u & 1))))); }   // ZigZag32
        case 0x26: return Value::num(std::to_string(r_.u32()));
        case 0x28: return Value::num(std::to_string(r_.uvar32()));
        case 0x40: return Value::num(std::to_string(static_cast<int64_t>(r_.u64())));
        case 0x42: return Value::num(makeFloatText(fmtDouble(r_.f64())));
        case 0x44: return Value::num(std::to_string(static_cast<int64_t>(r_.uvar64())));
        case 0x45: { uint64_t u = r_.uvar64(); return Value::num(std::to_string(static_cast<int64_t>((u >> 1) ^ (0ull - (u & 1))))); }  // ZigZag64
        case 0x46: return Value::num(std::to_string(r_.u64()));
        case 0x48: return Value::num(std::to_string(r_.uvar64()));
        case 0x09: case 0x0B: case 0x11: case 0x13: case 0x21: case 0x27: case 0x41: case 0x47: return Value::num("0");  // 各种零值类型
        case 0x23: case 0x43: return Value::num("0.0");
        case 0x85: return readObject();
        case 0x86: return readArray();
        case 0xBC: return Value::boolean(r_.u8() != 0);
        case 0xB0: case 0xB1: case 0xB2: case 0xB3: case 0xB4: case 0xB5: case 0xB6: case 0xB7: case 0xB8: case 0xB9: case 0xBA: case 0xBB: r_.back(); r_.fail("decoder.unsupported_b0bb");
    }
    r_.back(); r_.fail("decoder.unknown_value_type", {hexByte(t)});
}

Value Decoder::readObject() {
    Value o; o.type = Value::Obj;
    while (true) {
        std::uint8_t t = r_.u8();
        if (t == 0xFF) break;
        string key;
        if (!readStringLike(t, key)) { r_.back(); r_.fail("decoder.invalid_key_type"); }
        o.keys.push_back(std::move(key));
        o.arr.push_back(readValue(r_.u8()));
    }
    return o;
}

Value Decoder::readArray() {
    Value a; a.type = Value::Arr;
    r_.expect8(0xFD);
    std::uint32_t n = r_.uvar32();
    for (std::uint32_t i = 0; i < n; i++) a.arr.push_back(readValue(r_.u8()));
    r_.expect8(0xFE);
    return a;
}

Value Decoder::decode() {
    if (r_.bytes(4) != kMagic) throw std::runtime_error(i18n::translate("decoder.bad_magic"));
    if (r_.u32() != kVersion) throw std::runtime_error(i18n::translate("decoder.bad_version"));
    Value root = readObject();
    if (r_.bytes(4) != kEofTag) throw std::runtime_error(i18n::translate("decoder.missing_done"));
    return root;
}

}  // namespace rton
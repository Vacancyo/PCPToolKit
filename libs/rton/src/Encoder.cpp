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
 * @file Encoder.cpp
 * @brief RTON 二进制编码实现。
 *
 * 该代码文件实现 RTON 字符串、数字、对象、数组和类型标记的紧凑二进制编码，并输出完整 RTON 字节流。
 */
#include "rton/Encoder.h"

#include <cctype>
#include <cerrno>
#include <cmath>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <stdexcept>

#include "rton/NumberFormat.h"
#include "rton/RtonFormat.h"
#include "common/I18n.h"

using std::string;

namespace pcp::rton {

namespace {

/**
 * @brief 计算 C# string.Length，用于 0x92 与 RTID 中的字符数字段。
 */
size_t utf16Len(const string& s) {
    size_t n = 0;
    for (unsigned char c : s) {
        if ((c & 0xC0) == 0x80) continue;
        n += (c >= 0xF0) ? 2 : 1;
    }
    return n;
}

/**
 * @brief 判断字符串是否全部为 ASCII 字符。
 * @param s 待判断字符串。
 * @return 全部为 ASCII 返回 true。
 */
bool isAscii(const string& s) { for (unsigned char c : s) if (c > 127) return false; return true; }

/**
 * @brief 判断字符串是否全部为数字字符。
 * @param s 待判断字符串。
 * @return 全部为数字返回 true。
 */
bool allDigits(const string& s) { if (s.empty()) return false; for (char c : s) if (c < '0' || c > '9') return false; return true; }

/**
 * @brief 判断字符串是否为 8 位十六进制文本。
 * @param s 待判断字符串。
 * @return 符合格式返回 true。
 */
bool isHex8(const string& s) { if (s.size() != 8) return false; for (char c : s) if (!std::isxdigit(static_cast<unsigned char>(c))) return false; return true; }

}  // namespace

void Encoder::u8(std::uint8_t x) { o_.push_back(x); }
void Encoder::u32(std::uint32_t v) { for (int i = 0; i < 4; i++) u8((v >> (8 * i)) & 0xFF); }
void Encoder::u64(std::uint64_t v) { for (int i = 0; i < 8; i++) u8((v >> (8 * i)) & 0xFF); }
void Encoder::uvar(std::uint64_t v) { while (v >= 0x80) { u8(static_cast<std::uint8_t>(v | 0x80)); v >>= 7; } u8(static_cast<std::uint8_t>(v)); }
void Encoder::var32(std::int32_t v) { uvar(static_cast<std::uint32_t>(v)); }
void Encoder::raw(const string& s) { o_.insert(o_.end(), s.begin(), s.end()); }
void Encoder::strHead(const string& s) { uvar(s.size()); raw(s); }

bool Encoder::writeBinary(const string& s) {
    const string B = "$BINARY(\"", M = "\", ";
    if (s.size() < B.size() + M.size() + 2 || s.compare(0, B.size(), B) != 0 || s.back() != ')') return false;
    size_t idx = s.rfind(M);
    if (idx == string::npos || idx < B.size()) return false;
    string num = s.substr(idx + 3, s.size() - 1 - (idx + 3));
    char* end = nullptr; errno = 0;
    long v = std::strtol(num.c_str(), &end, 10);
    if (num.empty() || *end || errno || v > INT32_MAX || v < INT32_MIN) return false;
    u8(0x87); u8(0);
    strHead(s.substr(B.size(), idx - B.size()));
    var32(static_cast<std::int32_t>(v));
    return true;
}

bool Encoder::writeRTID(const string& s) {
    if (s.size() < 6 || s.compare(0, 5, "RTID(") != 0 || s.back() != ')') return false;
    if (s == "RTID(0)") { u8(0x84); return true; }
    string n = s.substr(5, s.size() - 6);
    size_t at = n.find('@');
    if (at == string::npos) return false;
    string s1 = n.substr(0, at), s2 = n.substr(at + 1);
    size_t d1 = s1.find('.');
    size_t d2 = (d1 == string::npos) ? string::npos : s1.find('.', d1 + 1);
    bool is02 = false; string a, b, x;
    if (d2 != string::npos && s1.find('.', d2 + 1) == string::npos) {
        a = s1.substr(0, d1); b = s1.substr(d1 + 1, d2 - d1 - 1); x = s1.substr(d2 + 1);
        is02 = allDigits(a) && allDigits(b) && isHex8(x) && a.size() < 10 && b.size() < 10;
    }
    u8(0x83);
    if (is02) {
        auto va = static_cast<std::uint32_t>(std::strtoul(a.c_str(), nullptr, 10));
        auto vb = static_cast<std::uint32_t>(std::strtoul(b.c_str(), nullptr, 10));
        auto vx = static_cast<std::uint32_t>(std::strtoul(x.c_str(), nullptr, 16));
        if (s2.empty()) { u8(0x01); uvar(vb); uvar(va); u32(vx); }
        else { u8(0x02); uvar(utf16Len(s2)); strHead(s2); uvar(vb); uvar(va); u32(vx); }
    } else {
        u8(0x03);
        uvar(utf16Len(s2)); strHead(s2);
        uvar(utf16Len(s1)); strHead(s1);
    }
    return true;
}

void Encoder::writeString(const string& s) {
    if (s == "*") { u8(0x02); return; }
    if (writeRTID(s)) return;
    if (writeBinary(s)) return;
    if (isAscii(s)) {
        auto it = p90_.find(s);
        if (it != p90_.end()) { u8(0x91); uvar(it->second); }
        else { u8(0x90); strHead(s); auto id = static_cast<std::uint32_t>(p90_.size()); p90_[s] = id; }
    } else {
        auto it = p92_.find(s);
        if (it != p92_.end()) { u8(0x93); uvar(it->second); }
        else { u8(0x92); uvar(utf16Len(s)); strHead(s); auto id = static_cast<std::uint32_t>(p92_.size()); p92_[s] = id; }
    }
}

void Encoder::writeNumber(const string& t) {
    bool isFloat = t.find_first_of(".eE") != string::npos;
    if (isFloat) {
        double d = std::strtod(t.c_str(), nullptr);
        if (d == 0.0) { u8(0x23); return; }
        auto f = static_cast<float>(d);
        if (std::isfinite(f) && std::strtod(fmtFloat(f).c_str(), nullptr) == d) { u8(0x22); std::uint32_t x; std::memcpy(&x, &f, 4); u32(x); }
        else u8(0x42); std::uint64_t x; std::memcpy(&x, &d, 8); u64(x);
        return;
    }
    errno = 0;
    char* end = nullptr;
    long long v = std::strtoll(t.c_str(), &end, 10);
    if (errno == 0 && *end == 0) {
        if (v == 0) u8(0x21);
        else if (v > 0) {
            if (v <= INT32_MAX) { u8(0x24); var32(static_cast<std::int32_t>(v)); }
            else { u8(0x44); uvar(static_cast<std::uint64_t>(v)); }
        } else {
            if (v + 0x40000000LL >= 0) { u8(0x25); auto n = static_cast<std::int32_t>(v); uvar(static_cast<std::uint32_t>((n << 1) ^ (n >> 31))); }  // ZigZag32
            else { u8(0x45); uvar((static_cast<std::uint64_t>(v) << 1) ^ static_cast<std::uint64_t>(v >> 63)); }  // ZigZag64
        }
        return;
    }
    errno = 0;
    unsigned long long uv = std::strtoull(t.c_str(), &end, 10);
    if (errno == 0 && *end == 0 && t[0] != '-') { u8(0x46); u64(uv); return; }
    throw std::runtime_error(pcp::i18n::translate("encoder.int_out_of_range", {t}));
}

void Encoder::writeValue(const Value& v) {
    switch (v.type) {
        case Value::Null: u8(0x84); break;
        case Value::Bool: u8(v.bvalue ? 0x01 : 0x00); break;
        case Value::Num: writeNumber(v.svalue); break;
        case Value::Str: writeString(v.svalue); break;
        case Value::Obj: u8(0x85); writeObject(v); break;
        case Value::Arr: u8(0x86); writeArray(v); break;
    }
}

void Encoder::writeObject(const Value& v) {
    for (size_t i = 0; i < v.arr.size(); i++) { writeString(v.keys[i]); writeValue(v.arr[i]); }
    u8(0xFF);
}

void Encoder::writeArray(const Value& v) {
    u8(0xFD); uvar(v.arr.size());
    for (const Value& e : v.arr) writeValue(e);
    u8(0xFE);
}

pcp::common::Bytes Encoder::encode(const Value& root) {
    if (root.type != Value::Obj) throw std::runtime_error(pcp::i18n::translate("encoder.root_not_object"));
    raw(kMagic); u32(kVersion);
    writeObject(root);
    raw(kEofTag);
    return o_;
}

}  // namespace pcp::rton

#include "rton/Json.h"
#include "common/I18n.h"

#include <cctype>
#include <cstdint>
#include <cstdio>
#include <stdexcept>

using std::string;

namespace rton {

namespace {

/** @brief  把字符串按 JSON 规则转义后追加到 o。 */
void jsonEscape(const string& s, string& o) {
    o += '"';
    for (unsigned char c : s) {
        switch (c) {
            case '"': o += "\\\""; break;
            case '\\': o += "\\\\"; break;
            case '\b': o += "\\b"; break;
            case '\f': o += "\\f"; break;
            case '\n': o += "\\n"; break;
            case '\r': o += "\\r"; break;
            case '\t': o += "\\t"; break;
            default:
                if (c < 0x20) { char buf[8]; std::snprintf(buf, sizeof buf, "\\u%04X", c); o += buf; }
                else o += static_cast<char>(c);
        }
    }
    o += '"';
}

/** @brief  递归序列化。depth 为当前嵌套层级，用于缩进。 */
void writeValue(const Value& v, string& o, int depth) {
    auto indent = [&](int d) { o.append(static_cast<size_t>(d) * 2, ' '); };
    switch (v.type) {
        case Value::Null: o += "null"; break;
        case Value::Bool: o += v.bvalue ? "true" : "false"; break;
        case Value::Num: o += v.svalue; break;
        case Value::Str: jsonEscape(v.svalue, o); break;
        case Value::Arr:
            if (v.arr.empty()) { o += "[]"; break; }
            o += "[\n";
            for (size_t i = 0; i < v.arr.size(); i++) {
                indent(depth + 1);
                writeValue(v.arr[i], o, depth + 1);
                if (i + 1 < v.arr.size()) o += ',';
                o += '\n';
            }
            indent(depth); o += ']';
            break;
        case Value::Obj:
            if (v.arr.empty()) { o += "{}"; break; }
            o += "{\n";
            for (size_t i = 0; i < v.arr.size(); i++) {
                indent(depth + 1);
                jsonEscape(v.keys[i], o);
                o += ": ";
                writeValue(v.arr[i], o, depth + 1);
                if (i + 1 < v.arr.size()) o += ',';
                o += '\n';
            }
            indent(depth); o += '}';
            break;
    }
}

/** @brief  递归下降 JSON 解析器。 */
class JsonParser {
public:
    explicit JsonParser(const string& str) : s_(str) {}

    /** @brief  解析整个文本，并确认根值之后没有多余内容。 */
    Value parseDocument() {
        Value root = parseValue(0);
        ws();
        if (p_ != s_.size()) fail("json.trailing_data");
        return root;
    }

private:
    const string& s_;  ///< 待解析文本
    size_t p_ = 0;     ///< 当前读取位置

    /** @brief  抛出带偏移量的错误;key 是具体原因的消息键。 */
    [[noreturn]] void fail(const string& key) const {
        throw std::runtime_error(i18n::translate("json.error_at", {std::to_string(p_), i18n::translate(key)}));
    }
    void ws() { while (p_ < s_.size() && (s_[p_] == ' ' || s_[p_] == '\t' || s_[p_] == '\n' || s_[p_] == '\r')) p_++; }

    /** @brief  把 Unicode 码点编码为 UTF-8 并追加。 */
    static void putUtf8(string& o, uint32_t c) {
        if (c < 0x80) o += static_cast<char>(c);
        else if (c < 0x800) { o += static_cast<char>(0xC0 | (c >> 6)); o += static_cast<char>(0x80 | (c & 0x3F)); }
        else if (c < 0x10000) { o += static_cast<char>(0xE0 | (c >> 12)); o += static_cast<char>(0x80 | ((c >> 6) & 0x3F)); o += static_cast<char>(0x80 | (c & 0x3F)); }
        else { o += static_cast<char>(0xF0 | (c >> 18)); o += static_cast<char>(0x80 | ((c >> 12) & 0x3F)); o += static_cast<char>(0x80 | ((c >> 6) & 0x3F)); o += static_cast<char>(0x80 | (c & 0x3F)); }
    }

    /** @brief  读取 \u 后的 4 位十六进制数。 */
    uint32_t hex4() {
        if (p_ + 4 > s_.size()) fail("json.u_incomplete");
        uint32_t v = 0;
        for (int i = 0; i < 4; i++) {
            char c = s_[p_++]; v <<= 4;
            if (c >= '0' && c <= '9') v |= c - '0';
            else if (c >= 'a' && c <= 'f') v |= c - 'a' + 10;
            else if (c >= 'A' && c <= 'F') v |= c - 'A' + 10;
            else fail("json.bad_hex");
        }
        return v;
    }

    /** @brief  解析字符串，处理转义与代理对。 */
    string parseString() {
        if (p_ >= s_.size() || s_[p_] != '"') fail("json.expect_string");
        p_++; string o;
        while (true) {
            if (p_ >= s_.size()) fail("json.unterminated_string");
            char c = s_[p_++];
            if (c == '"') break;
            if (c != '\\') { o += c; continue; }
            if (p_ >= s_.size()) fail("json.escape_incomplete");
            char e = s_[p_++];
            switch (e) {
                case '"': o += '"'; break; case '\\': o += '\\'; break; case '/': o += '/'; break;
                case 'b': o += '\b'; break; case 'f': o += '\f'; break; case 'n': o += '\n'; break;
                case 'r': o += '\r'; break; case 't': o += '\t'; break;
                case 'u': {
                    uint32_t cp = hex4();
                    if (cp >= 0xD800 && cp < 0xDC00) {  // 高代理项,需接低代理项
                        if (p_ + 1 < s_.size() && s_[p_] == '\\' && s_[p_ + 1] == 'u') {
                            p_ += 2; uint32_t lo = hex4();
                            if (lo >= 0xDC00 && lo < 0xE000) cp = 0x10000 + ((cp - 0xD800) << 10) + (lo - 0xDC00);
                            else cp = 0xFFFD;
                        } else cp = 0xFFFD;
                    } else if (cp >= 0xDC00 && cp < 0xE000) cp = 0xFFFD;
                    putUtf8(o, cp);
                    break;
                }
                default: fail("json.unknown_escape");
            }
        }
        return o;
    }

    /** @brief  解析任意值。depth 用于限制嵌套深度。 */
    Value parseValue(int depth) {
        if (depth > 512) fail("json.too_deep");
        ws();
        if (p_ >= s_.size()) fail("json.unexpected_end");
        char c = s_[p_];
        if (c == '{') {
            p_++; Value v; v.type = Value::Obj;
            ws();
            if (p_ < s_.size() && s_[p_] == '}') { p_++; return v; }
            while (true) {
                ws(); if (p_ < s_.size() && s_[p_] == '}') { p_++; break; }  // 尾逗号
                string k = parseString();
                ws(); if (p_ >= s_.size() || s_[p_] != ':') fail("json.expect_colon"); p_++;
                v.keys.push_back(std::move(k));
                v.arr.push_back(parseValue(depth + 1)); ws();
                if (p_ < s_.size() && s_[p_] == ',') { p_++; continue; }
                if (p_ < s_.size() && s_[p_] == '}') { p_++; break; }
                fail("json.expect_comma_brace");
            }
            return v;
        }
        if (c == '[') {
            p_++; Value v; v.type = Value::Arr;
            ws();
            if (p_ < s_.size() && s_[p_] == ']') { p_++; return v; }
            while (true) {
                ws(); if (p_ < s_.size() && s_[p_] == ']') { p_++; break; }  // 尾逗号
                v.arr.push_back(parseValue(depth + 1));
                ws(); if (p_ < s_.size() && s_[p_] == ',') { p_++; continue; }
                if (p_ < s_.size() && s_[p_] == ']') { p_++; break; }
                fail("json.expect_comma_bracket");
            }
            return v;
        }
        if (c == '"') return Value::str(parseString());
        if (s_.compare(p_, 4, "true") == 0) { p_ += 4; return Value::boolean(true); }
        if (s_.compare(p_, 5, "false") == 0) { p_ += 5; return Value::boolean(false); }
        if (s_.compare(p_, 4, "null") == 0) { p_ += 4; return Value(); }
        size_t st = p_;
        while (p_ < s_.size() && (std::isdigit(static_cast<unsigned char>(s_[p_])) || s_[p_] == '-' || s_[p_] == '+' || s_[p_] == '.' || s_[p_] == 'e' || s_[p_] == 'E')) p_++;
        if (st == p_) fail("json.unknown_char");
        return Value::num(s_.substr(st, p_ - st));
    }
};

}  // namespace

Value parseJson(const std::string& text) {
    std::string t = text;
    if (t.size() >= 3 && static_cast<uint8_t>(t[0]) == 0xEF && static_cast<uint8_t>(t[1]) == 0xBB && static_cast<uint8_t>(t[2]) == 0xBF) t.erase(0, 3);
    JsonParser parser(t);
    return parser.parseDocument();
}

std::string writeJson(const Value& v) {
    std::string o;
    writeValue(v, o, 0);
    return o;
}

}  // namespace rton
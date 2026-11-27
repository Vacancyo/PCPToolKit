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
 * @file I18n.cpp
 * @brief 多语言文本支持实现。
 *
 * 语言注册、语言切换、翻译查询的代码实现。
 */
#include "common/I18n.h"

#include <cstdint>
#include <map>
#include <unordered_map>

namespace pcp::i18n {

namespace {

using Table = std::unordered_map<std::string, std::string>;

/**
 * @brief 已注册的全部语言。即语言代码到文本表的映射。
 */
std::map<std::string, Table>& registry() { static std::map<std::string, Table> r; return r; }

/**
 * @brief 获取当前语言代码。
 */
std::string& current() { static std::string s = kDefaultLang; return s; }

/**
 * @brief 扁平 JSON 解析器。
 */
class FlatJson {
public:
    /**
     * @brief 构造扁平 JSON 解析器。
     * @param text 待解析文本。
     *
     * 这是一个超级简化版的 JSON 解析器。只支持字符串键对应字符串值。
     */
    explicit FlatJson(const std::string& text) : s_(text) {}

    /**
     * @brief 解析整个文本到 <code>out</code>。
     * @return 是否解析成功。格式不合法返回 <code>false</code>。
     */
    bool parse(Table& out) {
        if (s_.size() >= 3 && static_cast<std::uint8_t>(s_[0]) == 0xEF &&
            static_cast<std::uint8_t>(s_[1]) == 0xBB &&
            static_cast<std::uint8_t>(s_[2]) == 0xBF) p_ = 3;  // 跳过 BOM
        ws();
        if (!eat('{')) return false;
        while (true) {
            ws(); if (eat('}')) break;  // 空对象或尾逗号
            std::string k, v;
            if (!str(k)) return false;
            ws(); if (!eat(':')) return false;
            ws(); if (!str(v)) return false;  // 值必须是字符串
            out[k] = v; ws();
            if (eat(',')) continue;
            if (eat('}')) break;
            return false;
        }
        ws();
        return p_ == s_.size();  // 根对象之后不能有多余内容
    }

private:
    const std::string& s_;  ///< 待解析文本。
    size_t p_ = 0;          ///< 当前位置。

    /**
     * @brief 跳过空白字符。
     */
    void ws() {
        while (p_ < s_.size() &&
              (s_[p_] == ' ' || s_[p_] == '\t' || s_[p_] == '\n' || s_[p_] == '\r'))
        p_++;
    }

    /**
     * @brief 尝试消费一个指定字符。
     * @param c 期望字符。
     * @return 消费成功返回 <code>true</code>。
     */
    bool eat(char c) { if (p_ < s_.size() && s_[p_] == c) { p_++; return true; } return false; }

    /**
     * @brief 解析一个 JSON 字符串到 <code>out</code>。
     * @return 是否解析成功。格式不合法返回 <code>false</code>。
     */
    bool str(std::string& out) {
        if (!eat('"')) return false;
        while (p_ < s_.size()) {
            char c = s_[p_++];
            if (c == '"') return true;
            if (c != '\\') { out += c; continue; }
            if (p_ >= s_.size()) return false;
            switch (s_[p_++]) {
                case '"': out += '"'; break; case '\\': out += '\\'; break;
                case '/': out += '/'; break; case 'b': out += '\b'; break;
                case 'f': out += '\f'; break; case 'n': out += '\n'; break;
                case 'r': out += '\r'; break; case 't': out += '\t'; break;
                default: return false;
            }
        }
        return false;  // 字符串未结束
    }
};

/**
 * @brief 把模板中 <code>{N}</code> 替换为 <code>args[N]</code>；<code>N</code> 越界或格式不对则原样保留。
 */
std::string format(const std::string& tpl, const Args& args) {
    std::string o;
    o.reserve(tpl.size() + 16);
    for (size_t i = 0; i < tpl.size(); i++) {
        if (tpl[i] == '{') {
            size_t j = i + 1, idx = 0;
            while (j < tpl.size() && tpl[j] >= '0' && tpl[j] <= '9' && j - i <= 6) {
                idx = idx * 10 + (tpl[j] - '0'); j++;
            }
            if (j > i + 1 && j < tpl.size() && tpl[j] == '}' && idx < args.size()) {
                o += args[idx]; i = j; continue;
            }
        }
        o += tpl[i];
    }
    return o;
}

/**
 * @brief 在指定语言中查找键；语言未注册或没有该键返回 <code>nullptr</code>。
 */
const std::string* find(const std::string& lang, const std::string& key) {
    auto l = registry().find(lang);
    if (l == registry().end()) return nullptr;
    auto k = l->second.find(key);
    return k == l->second.end() ? nullptr : &k->second;
}

}  // namespace

bool addLanguage(const std::string& code, const std::string& jsonText) {
    Table t;
    if (!FlatJson(jsonText).parse(t)) return false;
    registry()[code] = std::move(t);
    return true;
}

bool setLanguage(const std::string& code) {
    if (registry().find(code) == registry().end()) return false;
    current() = code;
    return true;
}

std::string translate(const std::string& key, const Args& args) {
    const std::string* text = find(current(), key);  // 使用当前语言
    if (!text) text = find(kDefaultLang, key);  // 使用默认语言
    if (!text) return key;  // 都没有则显示键名
    return format(*text, args);
}

}  // namespace pcp::i18n

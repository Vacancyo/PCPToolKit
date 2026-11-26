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
 * @file Value.cpp
 * @brief 脚本运行时值实现。
 *
 * 该代码文件实现运行时值的构造、真值判断、文本显示、调试输出和相等比较。
 */
#include "script/Value.h"

#include <algorithm>
#include <cctype>
#include <cmath>
#include <iomanip>
#include <sstream>
#include <stdexcept>

namespace pcp::script {

Value Value::number(double value) {
    Value result;
    result.kind_ = Kind::Number;
    result.number_ = value;
    return result;
}

Value Value::boolean(bool value) {
    Value result;
    result.kind_ = Kind::Bool;
    result.boolean_ = value;
    return result;
}

Value Value::string(std::string value) {
    Value result;
    result.kind_ = Kind::String;
    result.text_ = std::move(value);
    return result;
}

Value Value::image(std::string name, std::string assetPath) {
    Value result;
    result.kind_ = Kind::Image;
    result.imageName_ = std::move(name);
    result.assetPath_ = std::move(assetPath);
    return result;
}

Value Value::array(std::vector<Value> items) {
    Value result;
    result.kind_ = Kind::Array;
    result.items_ = std::make_shared<const std::vector<Value>>(std::move(items));
    return result;
}

Value imageValue(std::string_view objectName) {
    std::string name(objectName);
    std::string path = "images/";
    size_t start = 0;
    bool first = true;
    while (start <= name.size()) {
        const size_t dot = name.find('.', start);
        const size_t end = dot == std::string::npos ? name.size() : dot;
        std::string part = name.substr(start, end - start);
        if (part.empty()) throw std::invalid_argument("Invalid image object: &" + name);
        std::transform(part.begin(), part.end(), part.begin(), [](unsigned char c) {
            return static_cast<char>(std::tolower(c));
        });
        if (!first) path += '/';
        path += part;
        first = false;
        if (dot == std::string::npos) break;
        start = dot + 1;
    }
    path += ".png";
    return Value::image(name, path);
}

bool truthy(const Value& value) {
    switch (value.kind()) {
        case Value::Kind::Bool: return value.boolean();
        case Value::Kind::Number: return value.number() != 0.0;
        case Value::Kind::String: return !value.string().empty();
        case Value::Kind::Array: return !value.items().empty();
        case Value::Kind::Image: return true;
    }
    return false;
}

/**
 * @brief 把数字格式化为稳定显示文本。
 * @param value 数字。
 * @return 显示文本。
 */
std::string formatNumber(double value) {
    if (!std::isfinite(value)) {
        std::ostringstream out;
        out << value;
        return out.str();
    }
    std::ostringstream out;
    out << std::fixed << std::setprecision(6) << value;
    std::string text = out.str();
    while (!text.empty() && text.back() == '0') text.pop_back();
    if (!text.empty() && text.back() == '.') text.pop_back();
    return text.empty() ? "0" : text;
}

/**
 * @brief 转义调试文本中的特殊字符。
 * @param text 原始文本。
 * @return 转义后的文本。
 */
std::string escape(const std::string& text) {
    std::string result;
    result.reserve(text.size());
    for (char c : text) {
        switch (c) {
            case '\\': result += "\\\\"; break;
            case '"': result += "\\\""; break;
            case '\n': result += "\\n"; break;
            case '\t': result += "\\t"; break;
            default: result += c; break;
        }
    }
    return result;
}

std::string displayText(const Value& value) {
    switch (value.kind()) {
        case Value::Kind::Bool: return value.boolean() ? "true" : "false";
        case Value::Kind::Number: return formatNumber(value.number());
        case Value::Kind::String: return value.string();
        case Value::Kind::Image: return "&" + value.imageName();
        case Value::Kind::Array: {
            std::string result = "[";
            const auto& items = value.items();
            for (size_t i = 0; i < items.size(); ++i) {
                if (i) result += ", ";
                result += displayText(items[i]);
            }
            result += "]";
            return result;
        }
    }
    return {};
}

std::string debugText(const Value& value) {
    if (value.kind() == Value::Kind::String) return "\"" + escape(value.string()) + "\"";
    if (value.kind() == Value::Kind::Array) {
        std::string result = "[";
        const auto& items = value.items();
        for (size_t i = 0; i < items.size(); ++i) {
            if (i) result += ", ";
            result += debugText(items[i]);
        }
        result += "]";
        return result;
    }
    return displayText(value);
}

bool operator==(const Value& left, const Value& right) {
    if (left.kind() != right.kind()) return false;
    switch (left.kind()) {
        case Value::Kind::Bool: return left.boolean() == right.boolean();
        case Value::Kind::Number: return left.number() == right.number();
        case Value::Kind::String: return left.string() == right.string();
        case Value::Kind::Image: return left.assetPath() == right.assetPath();
        case Value::Kind::Array: return left.items() == right.items();
    }
    return false;
}

bool operator!=(const Value& left, const Value& right) {
    return !(left == right);
}

}  // namespace pcp::script

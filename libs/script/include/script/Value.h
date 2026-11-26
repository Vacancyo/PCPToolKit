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
 * @file Value.h
 * @brief 脚本运行时值类型。
 *
 * 该代码文件定义脚本表达式使用的空值、布尔值、数字、字符串、图像和数组值类型。
 */
#pragma once

#include <memory>
#include <string>
#include <string_view>
#include <vector>

namespace pcp::script {

/**
 * @brief 脚本运行时使用的动态值，可表示数字、布尔值、字符串、图像和数组。
 */
class Value {
public:

    /**
     * @brief 脚本运行时值的类型。
     */
    enum class Kind {
        Number,
        Bool,
        String,
        Image,
        Array
    };

    /**
     * @brief 构造数字值。
     * @param value 数字内容。
     * @return 数字值。
     */
    static Value number(double value);

    /**
     * @brief 构造布尔值。
     * @param value 布尔内容。
     * @return 布尔值。
     */
    static Value boolean(bool value);

    /**
     * @brief 构造字符串值。
     * @param value 字符串内容。
     * @return 字符串值。
     */
    static Value string(std::string value);

    /**
     * @brief 构造图像值。
     * @param name 脚本中使用的图像名。
     * @param assetPath 图像资源路径。
     * @return 图像值。
     */
    static Value image(std::string name, std::string assetPath);

    /**
     * @brief 构造数组值。
     * @param items 数组元素。
     * @return 数组值。
     */
    static Value array(std::vector<Value> items);

    /**
     * @brief 获取值类型。
     * @return 值类型。
     */
    Kind kind() const { return kind_; }

    /**
     * @brief 获取数字内容。
     * @return 数字内容。
     */
    double number() const { return number_; }

    /**
     * @brief 获取布尔内容。
     * @return 布尔内容。
     */
    bool boolean() const { return boolean_; }

    /**
     * @brief 获取字符串内容。
     * @return 字符串内容。
     */
    const std::string& string() const { return text_; }

    /**
     * @brief 获取图像名。
     * @return 图像名。
     */
    const std::string& imageName() const { return imageName_; }

    /**
     * @brief 获取图像资源路径。
     * @return 图像资源路径。
     */
    const std::string& assetPath() const { return assetPath_; }

    /**
     * @brief 获取数组元素。
     * @return 数组元素。
     */
    const std::vector<Value>& items() const { return *items_; }

private:
    Kind kind_ = Kind::Bool;
    double number_ = 0.0;
    bool boolean_ = false;
    std::string text_;
    std::string imageName_;
    std::string assetPath_;
    std::shared_ptr<const std::vector<Value>> items_;
};

/**
 * @brief 把脚本图像对象名转换为图像值。
 * @param objectName 脚本中的图像对象名。
 * @return 图像值。
 */
Value imageValue(std::string_view objectName);

/**
 * @brief 判断值在条件表达式中是否为真。
 * @param value 待判断的值。
 * @return 为真返回 true。
 */
bool truthy(const Value& value);

/**
 * @brief 把值转换为适合打印的显示文本。
 * @param value 待转换的值。
 * @return 显示文本。
 */
std::string displayText(const Value& value);

/**
 * @brief 把值转换为带类型信息的调试文本。
 * @param value 待转换的值。
 * @return 调试文本。
 */
std::string debugText(const Value& value);

/**
 * @brief 比较两个值是否相等。
 * @param left 左值。
 * @param right 右值。
 * @return 相等返回 true。
 */
bool operator==(const Value& left, const Value& right);

/**
 * @brief 比较两个值是否不相等。
 * @param left 左值。
 * @param right 右值。
 * @return 不相等返回 true。
 */
bool operator!=(const Value& left, const Value& right);

}  // namespace pcp::script

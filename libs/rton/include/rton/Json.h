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
 * @file Json.h
 * @brief JSON 文本解析与序列化接口。
 *
 * 该代码文件声明 JSON UTF-8 文本与 RTON 通用 Value 值树之间的互相转换函数。
 */
#pragma once

#include <string>

#include "rton/Value.h"

namespace pcp::rton {

/**
 * @brief 解析 JSON 文本。允许尾逗号与开头的 UTF-8 BOM，数字以原文保存。
 * @param text JSON UTF-8 格式文本。
 * @return 解析得到的 Value 树根植。
 *
 * 例如以下 JSON 文档：
 *
 * `{"a": 5, "list": [1, 2]}`
 *
 * 该函数返回整个根值对象的对应 Value。例如执行 `Value root = parseJson(text)` 后，root 的
 * 类型为 `Value::Obj`，keys 为 `["a", "list"]`，arr[0] 为数字 5。
 */
Value parseJson(const std::string& text);

/**
 * @brief 把 Value 序列化为带 2 空格缩进的 JSON 文本。
 * @param v 要输出的值。
 * @return JSON 文本。
 *
 * `parseJson` 的反向处理。将 Value 转换为 JSON 文本。只转义 `"`、`\` 及控制字符，非 ASCII
 * 则会原样输出。
 */
std::string writeJson(const Value& v);

}  // namespace pcp::rton

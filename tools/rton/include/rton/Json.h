/** @file Json.h
 *  JSON 文本与 Value 树的双向解析与序列化。
 */
#pragma once

#include "rton/Value.h"

#include <string>

namespace rton {

/**
 *  @brief   解析 JSON 文本。允许尾逗号与开头的 UTF-8 BOM,数字以原文保存。
 *  @param   text  JSON UTF-8 格式文本。
 *  @return  解析得到的 Value 树根植。
 * 
 *  例如以下文档：{ "a": 5, "list": [1, 2] }。该函数返回整个根植对象 { ... } 的对应 Value。
 *  定义 Value root = parseJson(text)，上述例子 JSON 解析后则会放入 root，并赋予 root 成员
 *  属性，如 root.type 为 Value::Obj，root.keys 为 ["a", "list"]，root.arr[0] 为数字 5。
 */
Value parseJson(const std::string& text);

/**
 *  @brief   把 Value 序列化为带 2 空格缩进的 JSON 文本。
 *  @param   v  要输出的值。
 *  @return  JSON 文本。
 * 
 *  parseJson 的反向处理。将 Value 转换为 JSON 文本。只转义 '"'、'\' 及控制字符，非 ASCII
 *  则会原样输出。
 */
std::string writeJson(const Value& v);

}  // namespace rton
/** @file Value.h
 *  JSON 值的内存表示，是 RTON 与 JSON 之间的中间格式。
 */
#pragma once

#include <string>
#include <utility>
#include <vector>

namespace rton {

/**
 *  @brief  树形结构下的 JSON 值，RTON 与 JSON 之间的中间表示。
 * 
 *  Value 是通用的 JSON 值。它不关心数据来自 RTON 还是 JSON，而是把两边都装进同种内存结构里，
 *  所以它是整个转换流程的中间格式。有了它，RTON 解码器无需知道 JSON 怎么写，JSON 解析器也无需
 *  知道 RTON 怎么编，两边通过 Value 类通讯。
 * 
 *  而 type 的取值有六种：Null、Bool、Num、Str、Arr、Obj。它们分别对应 JSON 的 null、布尔值、
 *  数字、字符串、数组、对象。
 */
struct Value {
    enum Type { Null, Bool, Num, Str, Arr, Obj };

    Type type = Null;               ///< 值类型
    bool bvalue = false;            ///< 布尔取值。只在值类型为 Bool 时有效
    std::string svalue;             ///< 值类型为 Num 时，存储数字原文；值类型为 Str 时，存储 UTF-8 字符串
    std::vector<Value> arr;         ///< 值类型为 Arr 时，存储数组的元素；值类型为 Obj 时，存储各键对应的值
    std::vector<std::string> keys;  ///< 与 arr 各键对应的键名。只在值类型为 Obj 时有效

    /**
     *  @brief   构造数字值。
     *  @param   x  数字文本。
     *  @return  Num 类型的 Value。
     */
    static Value num(std::string x) { Value v; v.type = Num; v.svalue = std::move(x); return v; }

    /**
     *  @brief   构造字符串值。
     *  @param   x  UTF-8 字符串。
     *  @return  Str 类型的 Value。
     */
    static Value str(std::string x) { Value v; v.type = Str; v.svalue = std::move(x); return v; }

    /**
     *  @brief   构造布尔值。
     *  @param   x  布尔值。
     *  @return  Bool 类型的 Value。
     */
    static Value boolean(bool x) { Value v; v.type = Bool; v.bvalue = x; return v; }
};

}  // namespace rton
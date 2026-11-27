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
 * @file I18n.h
 * @brief 多语言文本支持接口。
 *
 * 声明语言注册、语言切换、翻译查询接口。
 */
#pragma once

#include <string>
#include <vector>

namespace pcp::i18n {

/// 占位符参数列表类型。
using Args = std::vector<std::string>;

/// 默认语言代码。当前语言缺少某个键时，使用它的文本。
inline const std::string kDefaultLang = "zh_CN";

/**
 * @brief 注册一种语言的文本。
 * @param code 语言代码。
 * @param jsonText 该语言的 JSON 文本。
 * @return 是否注册成功。若代码非法或 JSON 格式错误则返回 false，并保持当前语言。
 */
bool addLanguage(const std::string& code, const std::string& jsonText);

/**
 * @brief 切换当前语言。
 * @param code 语言代码。
 * @return 是否切换成功。若语言未注册则返回 <code>false</code>，并保持当前语言。
 */
bool setLanguage(const std::string& code);

/**
 * @brief 根据消息键获取提示文本，并将占位符替换为 <code>args</code> 中对应的参数。
 * @param key 消息键。
 * @param args 占位符参数。
 * @return 翻译后的文本。若键在语言文件和默认文本中都不存在，返回键名本身。
 */
std::string translate(const std::string& key, const Args& args = {});

}  // namespace pcp::i18n

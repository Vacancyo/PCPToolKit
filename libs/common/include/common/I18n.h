/** @file i18n.h
 *  多语言支持。
 */
#pragma once

#include <string>
#include <vector>

namespace i18n {

/// 占位符参数列表类型
using Args = std::vector<std::string>;

/// 默认语言代码。当前语言缺少某个键时,使用它的文本。
inline const std::string kDefaultLang = "zh_CN";

/**
 *  @brief   注册一种语言的文本。
 *  @param   code  语言代码。
 *  @param   jsonText  该语言的 JSON 文本。
 *  @return  是否注册成功。若代码非法或 JSON 格式错误则返回 false，并保持当前语言。
 * 
 *  在不切换语言的前提下，注册语言文本。同一语言重复注册会覆盖旧内容。
 */
bool addLanguage(const std::string& code, const std::string& jsonText);

/**
 *  @brief   切换当前语言。
 *  @param   code  语言代码。
 *  @return  是否切换成功。若语言未注册则返回 false，并保持当前语言。
 */
bool setLanguage(const std::string& code);

/**
 *  @brief   根据消息键获取提示文本，并将占位符替换为 args 中对应的参数。
 *  @param   key  消息键。
 *  @param   args  占位符参数。
 *  @return  翻译后的文本。若键在语言文件和默认文本中都不存在，返回键名本身。
 */
std::string translate(const std::string& key, const Args& args = {});

}  // namespace i18n
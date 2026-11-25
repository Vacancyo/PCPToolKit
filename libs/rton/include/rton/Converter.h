/** @file Converter.h
 *  单文件格式转换。
 */
#pragma once

#include <string>

namespace rton {

/**
 *  @brief  RTON 转 JSON 并生成文件。
 *  @param  in  输入 RTON 路径。
 *  @param  out  输出 JSON 路径。
 *  @param  key  RTONKey。
 *  @param  hasKey  是否提供了密钥。
 * 
 * 该函数调用解码器实现转换，并会以文件头 10 00 自动识别受加密的文件。
 */
void rtonToJsonFile(const std::string& in, const std::string& out, const std::string& key, bool hasKey);

/**
 *  @brief  JSON 转 RTON 并生成文件。
 *  @param  in  输入 JSON 路径。
 *  @param  out  输出 RTON 路径。
 *  @param  key  RTONKey。
 *  @param  hasKey  是否提供了密钥。
 * 
 * 该函数调用编码器实现转换。提供密钥时输出加密 RTON，否则输出普通 RTON。
 */
void jsonToRtonFile(const std::string& in, const std::string& out, const std::string& key, bool hasKey);

}  // namespace rton
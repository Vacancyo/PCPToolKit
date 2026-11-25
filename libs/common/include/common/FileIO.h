/** @file FileIO.h
 *  二进制文件读写。
 */
#pragma once

#include <string>

#include "common/Bytes.h"

namespace common {

/**
 *  @brief   读取整个文件。
 *  @param   path  文件路径。
 *  @return  文件全部字节。
 */
Bytes readAll(const std::string& path);

/**
 *  @brief  把数据写入文件。
 *  @param  path  文件路径。
 *  @param  data  数据指针。
 *  @param  n  字节数。
 */
void writeAll(const std::string& path, const void* data, size_t n);

}  // namespace common
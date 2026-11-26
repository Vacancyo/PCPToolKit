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
 * @file FileIO.cpp
 * @brief 通用原始字节文件读写接口实现。
 *
 * 实现一个完整文件读取和覆盖写入。
 */
#include "common/FileIO.h"

#include <fstream>
#include <iterator>
#include <stdexcept>

#include "common/I18n.h"

namespace pcp::common {

Bytes readAll(const std::string& path) {
    // 用二进制模式打开文件，如果用文本模式，换行符可能会变成 CRLF，所以要用二进制模式
    std::ifstream f(path, std::ios::binary);
    if (!f) throw std::runtime_error(pcp::i18n::translate("file.cannot_open", {path}));
    // Bytes 本质上是 vector 容器，直接用 iterator 返回完整的字节数组
    // istreambuf_iterator 接口只让用 char，返回的时候会自动转换成 uint8_t
    return {(std::istreambuf_iterator<char>(f)), std::istreambuf_iterator<char>()};
}

void writeAll(const std::string& path, const void* data, size_t n) {
    // data 无类型数据指针，什么类型的都能传进来
    std::ofstream f(path, std::ios::binary);
    if (!f) throw std::runtime_error(pcp::i18n::translate("file.cannot_write", {path}));
    // write 的参数类型也是 const char*
    f.write(static_cast<const char*>(data), static_cast<std::streamsize>(n));
}

}  // namespace pcp::common

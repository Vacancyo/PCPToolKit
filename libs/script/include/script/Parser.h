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
 * @file Parser.h
 * @brief 脚本解析入口。
 *
 * 该代码文件声明把 PCS 脚本文本解析为抽象语法树的公共入口。
 */
#pragma once

#include "script/Ast.h"

#include <string_view>

namespace pcp::script {

/**
 * @brief 把 PCS 脚本文本解析为抽象语法树。
 * @param source PCS 脚本文本。
 * @return 解析后的程序语法树。
 */
Program parseScript(std::string_view source);

}  // namespace pcp::script

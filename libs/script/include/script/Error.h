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
 * @file Error.h
 * @brief 脚本错误类型定义。
 *
 * 该代码文件定义脚本解析错误、运行时错误和取消异常，并携带错误所在行列信息。
 */
#pragma once

#include <stdexcept>
#include <string>

namespace pcp::script {

/**
 * @brief 脚本词法或语法解析失败时抛出的异常。
 */
class ScriptParseError : public std::runtime_error {
public:

    /**
     * @brief 构造带行列位置的脚本解析错误。
     * @param message 错误原因。
     * @param line 错误所在行号。
     * @param column 错误所在列号。
     */
    ScriptParseError(std::string message, int line, int column)
        : std::runtime_error(
              "Line " + std::to_string(line) + ", column " + std::to_string(column) +
              ": " + message
          ),
          line_(line),
          column_(column) {}

    /**
     * @brief 获取错误所在行号。
     * @return 错误所在行号。
     */
    int line() const { return line_; }

    /**
     * @brief 获取错误所在列号。
     * @return 错误所在列号。
     */
    int column() const { return column_; }

private:
    int line_;
    int column_;
};

/**
 * @brief 脚本执行阶段发生错误时抛出的异常。
 */
class ScriptRuntimeError : public std::runtime_error {
public:

    /**
     * @brief 构造带行号的脚本运行时错误。
     * @param message 错误原因。
     * @param line 错误所在行号。
     */
    ScriptRuntimeError(std::string message, int line)
        : std::runtime_error("Line " + std::to_string(line) + ": " + message), line_(line) {}

    /**
     * @brief 获取错误所在行号。
     * @return 错误所在行号。
     */
    int line() const { return line_; }

private:
    int line_;
};

/**
 * @brief 脚本执行被宿主取消时抛出的异常。
 */
class ScriptCancelled : public std::runtime_error {
public:

    /**
     * @brief 构造脚本取消异常。
     */
    ScriptCancelled() : std::runtime_error("Script cancelled") {}
};

}  // namespace pcp::script

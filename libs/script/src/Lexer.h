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
 * @file Lexer.h
 * @brief 脚本词法分析器接口。
 *
 * 该代码文件定义脚本词法单元种类、词法单元结构和把文本切分为词法单元的内部词法分析器。
 */
#pragma once

#include <string>
#include <vector>

namespace pcp::script {

/**
 * @brief PCS 脚本词法单元种类。
 */
enum class TokenKind {
    Variable,
    Image,
    Identifier,
    String,
    Number,
    Boolean,
    Plus,
    Minus,
    Star,
    Slash,
    Equal,
    EqualEqual,
    Bang,
    BangEqual,
    Less,
    LessEqual,
    Greater,
    GreaterEqual,
    AndAnd,
    OrOr,
    LeftParen,
    RightParen,
    LeftBracket,
    RightBracket,
    Comma,
    Colon,
    Newline,
    Indent,
    Dedent,
    End
};

/**
 * @brief 带原文和位置信息的词法单元。
 */
struct Token {
    TokenKind kind;
    std::string text;
    int line;
    int column;
};

/**
 * @brief 把 PCS 脚本文本转换为带位置和缩进信息的词法单元序列。
 */
class Lexer {
public:

    /**
     * @brief 构造词法分析器。
     * @param source PCS 脚本文本。
     */
    explicit Lexer(std::string source) : source_(std::move(source)) {}

    /**
     * @brief 执行词法分析。
     * @return 完整词法单元序列。
     */
    std::vector<Token> tokenize();

private:

    /**
     * @brief 扫描一行文本中的普通词法单元。
     * @param line 待扫描文本。
     * @param lineNumber 当前行号。
     * @param columnOffset 当前行在原文中的起始列偏移。
     */
    void scanLine(const std::string& line, int lineNumber, int columnOffset);

    /**
     * @brief 扫描字符串字面量。
     * @param line 当前行文本。
     * @param start 起始位置。
     * @param lineNumber 当前行号。
     * @param columnOffset 列偏移。
     * @return 字符串结束后的位置。
     */
    size_t scanString(const std::string& line, size_t start, int lineNumber, int columnOffset);

    /**
     * @brief 扫描数字字面量。
     * @param line 当前行文本。
     * @param start 起始位置。
     * @param lineNumber 当前行号。
     * @param columnOffset 列偏移。
     * @return 数字结束后的位置。
     */
    size_t scanNumber(const std::string& line, size_t start, int lineNumber, int columnOffset);

    /**
     * @brief 扫描变量引用。
     * @param line 当前行文本。
     * @param start 起始位置。
     * @param lineNumber 当前行号。
     * @param columnOffset 列偏移。
     * @return 变量引用结束后的位置。
     */
    size_t scanVariable(const std::string& line, size_t start, int lineNumber, int columnOffset);

    /**
     * @brief 扫描图像对象引用。
     * @param line 当前行文本。
     * @param start 起始位置。
     * @param lineNumber 当前行号。
     * @param columnOffset 列偏移。
     * @return 图像引用结束后的位置。
     */
    size_t scanImage(const std::string& line, size_t start, int lineNumber, int columnOffset);

    /**
     * @brief 扫描关键字或标识符。
     * @param line 当前行文本。
     * @param start 起始位置。
     * @param lineNumber 当前行号。
     * @param columnOffset 列偏移。
     * @return 标识符结束后的位置。
     */
    size_t scanIdentifier(const std::string& line, size_t start, int lineNumber, int columnOffset);

    /**
     * @brief 扫描运算符或标点。
     * @param line 当前行文本。
     * @param start 起始位置。
     * @param lineNumber 当前行号。
     * @param columnOffset 列偏移。
     * @return 运算符结束后的位置。
     */
    size_t scanOperator(const std::string& line, size_t start, int lineNumber, int columnOffset);

    /**
     * @brief 追加一个词法单元。
     * @param kind 词法单元种类。
     * @param text 词法单元原文。
     * @param line 所在行号。
     * @param column 所在列号。
     */
    void add(TokenKind kind, std::string text, int line, int column);

    std::string source_;
    std::vector<Token> tokens_;
    std::vector<int> indents_{0};
};

}  // namespace pcp::script

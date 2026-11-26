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
 * @brief 脚本递归下降解析器接口。
 *
 * 该代码文件声明内部递归下降解析器及其表达式、语句、控制流和视觉语句解析方法。
 */
#pragma once

#include "Lexer.h"
#include "script/Ast.h"

#include <string>

namespace pcp::script {

/**
 * @brief 使用递归下降方式把词法单元解析为程序语法树。
 */
class ScriptParser {
public:

    /**
     * @brief 构造脚本解析器。
     * @param source PCS 脚本文本。
     */
    explicit ScriptParser(std::string source);

    /**
     * @brief 解析完整脚本程序。
     * @return 程序语法树。
     */
    Program parse();

private:
    std::vector<Token> tokens_;
    size_t current_ = 0;

    /**
     * @brief 解析一条语句。
     * @return 语句语法树节点。
     */
    StatementPtr parseStatement();

    /**
     * @brief 解析普通视觉查找或等待视觉命中的语句。
     * @param waitForMatch 是否解析为等待命中语句。
     * @return 视觉语句节点。
     */
    StatementPtr parseVision(bool waitForMatch);

    /**
     * @brief 解析条件分支语句。
     * @return 条件语句节点。
     */
    StatementPtr parseIf();

    /**
     * @brief 解析遍历数组的循环语句。
     * @return 循环语句节点。
     */
    StatementPtr parseFor();

    /**
     * @brief 解析缩进代码块。
     * @return 代码块语句列表。
     */
    Block parseBlock();

    /**
     * @brief 解析表达式入口。
     * @return 表达式节点。
     */
    ExprPtr parseExpression();

    /**
     * @brief 解析逻辑或表达式。
     * @return 表达式节点。
     */
    ExprPtr parseOr();

    /**
     * @brief 解析逻辑与表达式。
     * @return 表达式节点。
     */
    ExprPtr parseAnd();

    /**
     * @brief 解析逻辑非表达式。
     * @return 表达式节点。
     */
    ExprPtr parseNot();

    /**
     * @brief 解析比较表达式。
     * @return 表达式节点。
     */
    ExprPtr parseComparison();

    /**
     * @brief 解析加减表达式。
     * @return 表达式节点。
     */
    ExprPtr parseAdditive();

    /**
     * @brief 解析乘除表达式。
     * @return 表达式节点。
     */
    ExprPtr parseMultiplicative();

    /**
     * @brief 解析一元正负号表达式。
     * @return 表达式节点。
     */
    ExprPtr parseUnary();

    /**
     * @brief 解析视觉查找参数。
     * @return 表达式节点。
     */
    ExprPtr parseArgument();

    /**
     * @brief 解析数字、字符串、布尔值、变量、图像、数组或括号表达式。
     * @return 表达式节点。
     */
    ExprPtr parsePrimary();

    /**
     * @brief 解析数组字面量。
     * @param open 数组起始方括号词法单元。
     * @return 数组表达式节点。
     */
    ExprPtr parseArray(const Token& open);

    /**
     * @brief 判断当前位置是否开始比较运算。
     * @return 是比较运算返回 true。
     */
    bool checkComparison() const;

    /**
     * @brief 当前位置匹配指定词法单元时前进。
     * @param kind 期望的词法单元种类。
     * @return 匹配并前进返回 true。
     */
    bool match(TokenKind kind);

    /**
     * @brief 当前位置匹配指定关键字时前进。
     * @param keyword 关键字文本。
     * @return 匹配并前进返回 true。
     */
    bool matchKeyword(const std::string& keyword);

    /**
     * @brief 要求当前位置为指定词法单元。
     * @param kind 期望的词法单元种类。
     * @param message 不匹配时的错误说明。
     * @return 匹配到的词法单元。
     */
    const Token& expect(TokenKind kind, const std::string& message);

    /**
     * @brief 要求当前位置为换行。
     */
    void expectNewline();

    /**
     * @brief 检查当前位置是否为指定词法单元。
     * @param kind 待检查的词法单元种类。
     * @return 匹配返回 true。
     */
    bool check(TokenKind kind) const;

    /**
     * @brief 读取当前词法单元并前进。
     * @return 前进前位于当前位置的词法单元。
     */
    const Token& advance();

    /**
     * @brief 查看当前词法单元。
     * @return 当前词法单元。
     */
    const Token& peek() const;

    /**
     * @brief 查看上一个词法单元。
     * @return 上一个词法单元。
     */
    const Token& previous() const;

    /**
     * @brief 抛出带当前位置的解析错误。
     * @param message 错误说明。
     */
    [[noreturn]] void error(const std::string& message) const;
};

}  // namespace pcp::script

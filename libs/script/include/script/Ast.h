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
 * @file Ast.h
 * @brief 脚本抽象语法树定义。
 *
 * 该代码文件定义表达式、语句、控制流和视觉语句的抽象语法树节点。
 */
#pragma once

#include "script/Value.h"

#include <memory>
#include <optional>
#include <string>
#include <vector>

namespace pcp::script {

/**
 * @brief 表达式语法树节点种类。
 */
enum class ExprKind {
    Literal,
    Variable,
    Array,
    Unary,
    Binary
};

/**
 * @brief 所有表达式语法树节点的公共基类。
 */
struct Expr {

    /**
     * @brief 构造表达式基类。
     * @param line 表达式所在行号。
     * @param kind 表达式种类。
     */
    Expr(int line, ExprKind kind) : line(line), kind_(kind) {}
    virtual ~Expr() = default;

    /**
     * @brief 获取表达式种类。
     * @return 表达式种类。
     */
    ExprKind kind() const { return kind_; }
    int line;

private:
    ExprKind kind_;
};

using ExprPtr = std::unique_ptr<Expr>;

/**
 * @brief 字面量表达式节点。
 */
struct LiteralExpr final : Expr {

    /**
     * @brief 构造字面量表达式。
     * @param line 所在行号。
     * @param value 字面量值。
     */
    LiteralExpr(int line, Value value) : Expr(line, ExprKind::Literal), value(std::move(value)) {}
    Value value;
};

/**
 * @brief 变量引用表达式节点。
 */
struct VariableExpr final : Expr {

    /**
     * @brief 构造变量引用表达式。
     * @param line 所在行号。
     * @param name 变量名。
     */
    VariableExpr(int line, std::string name)
        : Expr(line, ExprKind::Variable), name(std::move(name)) {}
    std::string name;
};

/**
 * @brief 数组字面量表达式节点。
 */
struct ArrayExpr final : Expr {

    /**
     * @brief 构造数组表达式。
     * @param line 所在行号。
     * @param items 数组元素表达式。
     */
    ArrayExpr(int line, std::vector<ExprPtr> items)
        : Expr(line, ExprKind::Array), items(std::move(items)) {}
    std::vector<ExprPtr> items;
};

/**
 * @brief 一元运算表达式节点。
 */
struct UnaryExpr final : Expr {

    /**
     * @brief 构造一元表达式。
     * @param line 所在行号。
     * @param op 运算符。
     * @param operand 操作数。
     */
    UnaryExpr(int line, std::string op, ExprPtr operand)
        : Expr(line, ExprKind::Unary), op(std::move(op)), operand(std::move(operand)) {}
    std::string op;
    ExprPtr operand;
};

/**
 * @brief 二元运算表达式节点。
 */
struct BinaryExpr final : Expr {

    /**
     * @brief 构造二元表达式。
     * @param line 所在行号。
     * @param op 运算符。
     * @param left 左操作数。
     * @param right 右操作数。
     */
    BinaryExpr(int line, std::string op, ExprPtr left, ExprPtr right)
        : Expr(line, ExprKind::Binary),
          op(std::move(op)),
          left(std::move(left)),
          right(std::move(right)) {}
    std::string op;
    ExprPtr left;
    ExprPtr right;
};

/**
 * @brief 语句语法树节点种类。
 */
enum class StatementKind {
    Let,
    Print,
    Wait,
    Vision,
    Tap,
    Swipe,
    If,
    For,
    While,
    Break,
    Continue,
    Stop
};

/**
 * @brief 所有语句语法树节点的公共基类。
 */
struct Statement {

    /**
     * @brief 构造语句基类。
     * @param line 语句所在行号。
     * @param kind 语句种类。
     */
    Statement(int line, StatementKind kind) : line(line), kind_(kind) {}
    virtual ~Statement() = default;

    /**
     * @brief 获取语句种类。
     * @return 语句种类。
     */
    StatementKind kind() const { return kind_; }
    int line;

private:
    StatementKind kind_;
};

using StatementPtr = std::unique_ptr<Statement>;
using Block = std::vector<StatementPtr>;

/**
 * @brief 变量赋值语句节点。
 */
struct LetStatement final : Statement {

    /**
     * @brief 构造变量赋值语句。
     * @param line 所在行号。
     * @param name 变量名。
     * @param expression 赋值表达式。
     */
    LetStatement(int line, std::string name, ExprPtr expression)
        : Statement(line, StatementKind::Let),
          name(std::move(name)),
          expression(std::move(expression)) {}
    std::string name;
    ExprPtr expression;
};

/**
 * @brief 日志输出语句节点。
 */
struct PrintStatement final : Statement {

    /**
     * @brief 构造日志输出语句。
     * @param line 所在行号。
     * @param expression 输出表达式。
     */
    PrintStatement(int line, ExprPtr expression)
        : Statement(line, StatementKind::Print), expression(std::move(expression)) {}
    ExprPtr expression;
};

/**
 * @brief 等待语句节点。
 */
struct WaitStatement final : Statement {

    /**
     * @brief 构造等待语句。
     * @param line 所在行号。
     * @param milliseconds 等待毫秒数表达式。
     */
    WaitStatement(int line, ExprPtr milliseconds)
        : Statement(line, StatementKind::Wait), milliseconds(std::move(milliseconds)) {}
    ExprPtr milliseconds;
};

/**
 * @brief 视觉查找区域的坐标模式。
 */
enum class VisionRegionMode {
    Rect,
    Coord
};

/**
 * @brief 视觉查找或等待命中语句节点。
 */
struct VisionStatement final : Statement {

    /**
     * @brief 构造视觉查找语句。
     * @param line 所在行号。
     * @param image 模板图像表达式。
     * @param mode 区域坐标模式。
     * @param values 区域坐标表达式。
     * @param timeout 可选超时表达式。
     */
    VisionStatement(
        int line,
        ExprPtr image,
        VisionRegionMode mode,
        std::vector<ExprPtr> values,
        ExprPtr timeout
    )
        : Statement(line, StatementKind::Vision),
          image(std::move(image)),
          mode(mode),
          values(std::move(values)),
          timeout(std::move(timeout)) {}
    ExprPtr image;
    VisionRegionMode mode;
    std::vector<ExprPtr> values;
    ExprPtr timeout;
};

/**
 * @brief 单击语句节点。
 */
struct TapStatement final : Statement {

    /**
     * @brief 构造单击语句。
     * @param line 所在行号。
     * @param x 横坐标表达式。
     * @param y 纵坐标表达式。
     */
    TapStatement(int line, ExprPtr x, ExprPtr y)
        : Statement(line, StatementKind::Tap), x(std::move(x)), y(std::move(y)) {}
    ExprPtr x;
    ExprPtr y;
};

/**
 * @brief 滑动语句节点。
 */
struct SwipeStatement final : Statement {

    /**
     * @brief 构造滑动语句。
     * @param line 所在行号。
     * @param x1 起点横坐标表达式。
     * @param y1 起点纵坐标表达式。
     * @param x2 终点横坐标表达式。
     * @param y2 终点纵坐标表达式。
     * @param duration 持续时间表达式。
     */
    SwipeStatement(
        int line,
        ExprPtr x1,
        ExprPtr y1,
        ExprPtr x2,
        ExprPtr y2,
        ExprPtr duration
    )
        : Statement(line, StatementKind::Swipe),
          x1(std::move(x1)),
          y1(std::move(y1)),
          x2(std::move(x2)),
          y2(std::move(y2)),
          duration(std::move(duration)) {}
    ExprPtr x1;
    ExprPtr y1;
    ExprPtr x2;
    ExprPtr y2;
    ExprPtr duration;
};

/**
 * @brief 条件语句中的一个条件分支。
 */
struct IfBranch {
    ExprPtr condition;
    Block body;
};

/**
 * @brief 条件分支语句节点。
 */
struct IfStatement final : Statement {

    /**
     * @brief 构造条件语句。
     * @param line 所在行号。
     * @param branches 条件分支。
     * @param elseBody 可选的其他分支。
     */
    IfStatement(int line, std::vector<IfBranch> branches, std::optional<Block> elseBody)
        : Statement(line, StatementKind::If),
          branches(std::move(branches)),
          elseBody(std::move(elseBody)) {}
    std::vector<IfBranch> branches;
    std::optional<Block> elseBody;
};

/**
 * @brief 数组遍历循环语句节点。
 */
struct ForStatement final : Statement {

    /**
     * @brief 构造数组遍历语句。
     * @param line 所在行号。
     * @param variable 循环变量名。
     * @param iterable 被遍历表达式。
     * @param body 循环体。
     */
    ForStatement(int line, std::string variable, ExprPtr iterable, Block body)
        : Statement(line, StatementKind::For),
          variable(std::move(variable)),
          iterable(std::move(iterable)),
          body(std::move(body)) {}
    std::string variable;
    ExprPtr iterable;
    Block body;
};

/**
 * @brief 条件循环语句节点。
 */
struct WhileStatement final : Statement {

    /**
     * @brief 构造条件循环语句。
     * @param line 所在行号。
     * @param condition 循环条件。
     * @param body 循环体。
     */
    WhileStatement(int line, ExprPtr condition, Block body)
        : Statement(line, StatementKind::While),
          condition(std::move(condition)),
          body(std::move(body)) {}
    ExprPtr condition;
    Block body;
};

/**
 * @brief 跳出循环语句节点。
 */
struct BreakStatement final : Statement {

    /**
     * @brief 构造跳出循环语句。
     * @param line 所在行号。
     */
    explicit BreakStatement(int line) : Statement(line, StatementKind::Break) {}
};

/**
 * @brief 继续下一次循环语句节点。
 */
struct ContinueStatement final : Statement {

    /**
     * @brief 构造继续循环语句。
     * @param line 所在行号。
     */
    explicit ContinueStatement(int line) : Statement(line, StatementKind::Continue) {}
};

/**
 * @brief 停止脚本语句节点。
 */
struct StopStatement final : Statement {

    /**
     * @brief 构造停止脚本语句。
     * @param line 所在行号。
     */
    explicit StopStatement(int line) : Statement(line, StatementKind::Stop) {}
};

/**
 * @brief 完整脚本程序，包含顶层语句列表。
 */
struct Program {
    Block statements;
};

}  // namespace pcp::script

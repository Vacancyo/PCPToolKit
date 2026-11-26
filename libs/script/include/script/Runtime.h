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
 * @file Runtime.h
 * @brief 脚本运行时接口。
 *
 * 该代码文件声明脚本运行宿主、视觉匹配结果、运行时错误处理和脚本执行接口。
 */
#pragma once

#include "script/Ast.h"

#include <cstdint>
#include <optional>
#include <string>
#include <string_view>
#include <unordered_map>

namespace pcp::script {

/**
 * @brief 以 0 至 1 的比例坐标表示的屏幕区域。
 */
struct NormalizedRect {
    double left;
    double top;
    double right;
    double bottom;
};

/**
 * @brief 模板匹配结果。
 */
struct MatchResult {
    bool found = false;
    double left = 0.0;
    double top = 0.0;
    double width = 0.0;
    double height = 0.0;
    double score = 0.0;
};

/**
 * @brief 脚本运行时需要宿主提供的取消、计时、日志、输入、休眠和视觉匹配能力。
 */
class Host {
public:
    virtual ~Host() = default;

    /**
     * @brief 查询脚本是否已被取消。
     * @return 已取消返回 true。
     */
    virtual bool cancelled() const = 0;

    /**
     * @brief 获取宿主当前时间。
     * @return 自某一固定起点以来的毫秒数。
     */
    virtual int64_t nowMillis() const = 0;

    /**
     * @brief 输出脚本日志。
     * @param message 日志文本。
     */
    virtual void log(std::string_view message) = 0;

    /**
     * @brief 让当前脚本线程休眠。
     * @param milliseconds 休眠毫秒数。
     */
    virtual void sleep(int64_t milliseconds) = 0;

    /**
     * @brief 在归一化屏幕坐标处执行单击。
     * @param x 横坐标。
     * @param y 纵坐标。
     */
    virtual void tap(double x, double y) = 0;

    /**
     * @brief 执行归一化坐标之间的滑动。
     * @param x1 起点横坐标。
     * @param y1 起点纵坐标。
     * @param x2 终点横坐标。
     * @param y2 终点纵坐标。
     * @param durationMillis 滑动持续时间，单位为毫秒。
     */
    virtual void swipe(double x1, double y1, double x2, double y2, int64_t durationMillis) = 0;

    /**
     * @brief 在指定屏幕区域内查找模板图像。
     * @param assetPath 模板图像资源路径。
     * @param region 归一化匹配区域。
     * @return 模板匹配结果。
     */
    virtual MatchResult match(std::string_view assetPath, const NormalizedRect& region) = 0;
};

/**
 * @brief 解释并执行脚本语法树，同时维护变量、控制流和视觉匹配状态。
 */
class ScriptRuntime {
public:

    /**
     * @brief 构造脚本运行时。
     * @param host 脚本宿主。
     * @param maxSteps 允许执行的最大语句步数。
     */
    explicit ScriptRuntime(Host& host, int maxSteps = 1'000'000);

    /**
     * @brief 执行完整的脚本程序。
     * @param program 待执行的程序语法树。
     */
    void execute(const Program& program);

private:

    /**
     * @brief 脚本语句块执行后的控制流状态。
     */
    enum class Flow {
        Normal,
        Break,
        Continue,
        Stop
    };

    Host& host_;
    int maxSteps_;
    int steps_ = 0;
    int loopDepth_ = 0;
    bool found_ = false;
    double matchLeft_ = 0.0;
    double matchTop_ = 0.0;
    double matchWidth_ = 0.0;
    double matchHeight_ = 0.0;
    double matchScore_ = 0.0;
    std::unordered_map<std::string, Value> variables_;

    /**
     * @brief 按顺序执行一组语句。
     * @param statements 待执行的语句列表。
     * @return 语句块执行后的控制流状态。
     */
    Flow executeBlock(const Block& statements);

    /**
     * @brief 执行单条语句。
     * @param statement 待执行的语句。
     * @return 语句执行后的控制流状态。
     */
    Flow executeStatement(const Statement& statement);

    /**
     * @brief 执行数组遍历循环。
     * @param statement 循环语句。
     * @return 循环结束后的控制流状态。
     */
    Flow executeFor(const ForStatement& statement);

    /**
     * @brief 执行条件循环。
     * @param statement 循环语句。
     * @return 循环结束后的控制流状态。
     */
    Flow executeWhile(const WhileStatement& statement);

    /**
     * @brief 执行视觉查找或等待命中语句。
     * @param statement 视觉语句。
     */
    void executeVision(const VisionStatement& statement);

    /**
     * @brief 在超时时间内重复执行模板匹配。
     * @param imagePath 模板图像路径。
     * @param rect 归一化匹配区域。
     * @param timeout 最长等待毫秒数。
     * @return 匹配结果。
     */
    MatchResult matchWithTimeout(
        std::string_view imagePath,
        const NormalizedRect& rect,
        int64_t timeout
    );

    /**
     * @brief 把匹配结果写入内置变量。
     * @param result 匹配结果。
     */
    void applyMatch(const MatchResult& result);

    /**
     * @brief 计算表达式的值。
     * @param expression 表达式节点。
     * @return 表达式结果。
     */
    Value evaluate(const Expr& expression);

    /**
     * @brief 计算一元表达式。
     * @param expression 一元表达式节点。
     * @return 表达式结果。
     */
    Value evaluateUnary(const UnaryExpr& expression);

    /**
     * @brief 计算二元表达式。
     * @param expression 二元表达式节点。
     * @return 表达式结果。
     */
    Value evaluateBinary(const BinaryExpr& expression);

    /**
     * @brief 读取变量值。
     * @param expression 变量表达式节点。
     * @return 变量值。
     */
    Value readVariable(const VariableExpr& expression);

    /**
     * @brief 查询视觉匹配产生的内置变量。
     * @param name 变量名。
     * @return 存在时返回变量值。
     */
    std::optional<Value> builtIn(const std::string& name) const;

    /**
     * @brief 判断名称是否为内置变量。
     * @param name 变量名。
     * @return 是内置变量返回 true。
     */
    bool isBuiltIn(const std::string& name) const;

    /**
     * @brief 把值转换为数字。
     * @param value 待转换的值。
     * @param line 出错时使用的脚本行号。
     * @return 数字结果。
     */
    double number(const Value& value, int line) const;

    /**
     * @brief 把值转换为 0 至 1 的归一化坐标。
     * @param value 待转换的值。
     * @param line 出错时使用的脚本行号。
     * @return 归一化坐标。
     */
    double unit(const Value& value, int line) const;

    /**
     * @brief 把值转换为非负毫秒数。
     * @param value 待转换的值。
     * @param line 出错时使用的脚本行号。
     * @param label 用于错误提示的名称。
     * @return 毫秒数。
     */
    int64_t duration(double value, int line, std::string_view label) const;

    /**
     * @brief 记录执行步数并检查取消状态。
     * @param line 当前脚本行号。
     */
    void tick(int line);

    /**
     * @brief 检查脚本是否已被取消。
     */
    void checkCancelled() const;
};

}  // namespace pcp::script

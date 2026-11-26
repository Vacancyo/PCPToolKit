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
 * @file Runtime.cpp
 * @brief 脚本运行时实现。
 *
 * 该代码文件实现脚本语句执行、表达式求值、视觉匹配等待、宿主调用、步数限制和取消检查。
 */
#include "script/Runtime.h"

#include "script/Error.h"

#include <algorithm>
#include <array>
#include <cmath>
#include <optional>
#include <string>
#include <unordered_map>
#include <unordered_set>

namespace pcp::script {
namespace {

const std::unordered_set<std::string> kBuiltIns{
    "FOUND",
    "MATCH_X",
    "MATCH_Y",
    "MATCH_CX",
    "MATCH_CY",
    "MATCH_W",
    "MATCH_H",
    "MATCH_SCORE"
};

}  // namespace

ScriptRuntime::ScriptRuntime(Host& host, int maxSteps)
    : host_(host), maxSteps_(maxSteps) {}

void ScriptRuntime::execute(const Program& program) {
    executeBlock(program.statements);
}

ScriptRuntime::Flow ScriptRuntime::executeBlock(const Block& statements) {
    for (const auto& statement : statements) {
        tick(statement->line);
        const Flow flow = executeStatement(*statement);
        if (flow != Flow::Normal) return flow;
    }
    return Flow::Normal;
}

ScriptRuntime::Flow ScriptRuntime::executeStatement(const Statement& statement) {
    switch (statement.kind()) {
        case StatementKind::Let: {
            const auto& let = static_cast<const LetStatement&>(statement);
            if (isBuiltIn(let.name)) {
                throw ScriptRuntimeError(
                    "Cannot assign to built-in variable $" + let.name,
                    let.line
                );
            }
            variables_[let.name] = evaluate(*let.expression);
            return Flow::Normal;
        }
        case StatementKind::Print: {
            const auto& print = static_cast<const PrintStatement&>(statement);
            host_.log(displayText(evaluate(*print.expression)));
            return Flow::Normal;
        }
        case StatementKind::Wait: {
            const auto& wait = static_cast<const WaitStatement&>(statement);
            const double milliseconds = number(evaluate(*wait.milliseconds), wait.line);
            host_.sleep(duration(milliseconds, wait.line, "wait"));
            return Flow::Normal;
        }
        case StatementKind::Vision: {
            executeVision(static_cast<const VisionStatement&>(statement));
            return Flow::Normal;
        }
        case StatementKind::Tap: {
            const auto& tap = static_cast<const TapStatement&>(statement);
            const double x = unit(evaluate(*tap.x), tap.x->line);
            const double y = unit(evaluate(*tap.y), tap.y->line);
            host_.tap(x, y);
            return Flow::Normal;
        }
        case StatementKind::Swipe: {
            const auto& swipe = static_cast<const SwipeStatement&>(statement);
            const double x1 = unit(evaluate(*swipe.x1), swipe.x1->line);
            const double y1 = unit(evaluate(*swipe.y1), swipe.y1->line);
            const double x2 = unit(evaluate(*swipe.x2), swipe.x2->line);
            const double y2 = unit(evaluate(*swipe.y2), swipe.y2->line);
            const int64_t durationMillis = duration(
                number(evaluate(*swipe.duration), swipe.duration->line),
                swipe.line,
                "swipe"
            );
            host_.swipe(x1, y1, x2, y2, durationMillis);
            return Flow::Normal;
        }
        case StatementKind::If: {
            const auto& ifStatement = static_cast<const IfStatement&>(statement);
            for (const auto& branch : ifStatement.branches) {
                if (truthy(evaluate(*branch.condition))) {
                    return executeBlock(branch.body);
                }
            }
            if (ifStatement.elseBody) return executeBlock(*ifStatement.elseBody);
            return Flow::Normal;
        }
        case StatementKind::For:
            return executeFor(static_cast<const ForStatement&>(statement));
        case StatementKind::While:
            return executeWhile(static_cast<const WhileStatement&>(statement));
        case StatementKind::Break:
            if (loopDepth_ == 0) {
                throw ScriptRuntimeError("'break' outside a loop", statement.line);
            }
            return Flow::Break;
        case StatementKind::Continue:
            if (loopDepth_ == 0) {
                throw ScriptRuntimeError("'continue' outside a loop", statement.line);
            }
            return Flow::Continue;
        case StatementKind::Stop:
            return Flow::Stop;
    }
    return Flow::Normal;
}

void ScriptRuntime::executeVision(const VisionStatement& statement) {
    const Value image = evaluate(*statement.image);
    if (image.kind() != Value::Kind::Image) {
        throw ScriptRuntimeError("Expected an image object", statement.image->line);
    }

    std::array<double, 4> values{};
    for (size_t i = 0; i < values.size(); ++i) {
        const double value = number(evaluate(*statement.values[i]), statement.values[i]->line);
        if (!std::isfinite(value) || value < 0.0 || value > 1.0) {
            throw ScriptRuntimeError(
                "Region coordinates must be between 0 and 1",
                statement.values[i]->line
            );
        }
        values[i] = value;
    }

    NormalizedRect rect;
    if (statement.mode == VisionRegionMode::Rect) {
        if (values[2] <= 0.0 || values[3] <= 0.0) {
            throw ScriptRuntimeError(
                "Rectangle width and height must be positive",
                statement.line
            );
        }
        rect = {values[0], values[1], values[0] + values[2], values[1] + values[3]};
    } else {
        if (values[2] <= values[0] || values[3] <= values[1]) {
            throw ScriptRuntimeError(
                "Right/bottom coordinates must be larger",
                statement.line
            );
        }
        rect = {values[0], values[1], values[2], values[3]};
    }
    constexpr double kBoundaryEpsilon = 1e-9;
    if (rect.right > 1.0 + kBoundaryEpsilon ||
        rect.bottom > 1.0 + kBoundaryEpsilon) {
        if (statement.mode == VisionRegionMode::Rect) {
            throw ScriptRuntimeError(
                "Rectangle extends outside the screen; rect uses x y width height. "
                "Use coord for x y right bottom.",
                statement.line
            );
        }
        throw ScriptRuntimeError(
            "Region must stay inside the screen",
            statement.line
        );
    }
    rect.right = std::min(rect.right, 1.0);
    rect.bottom = std::min(rect.bottom, 1.0);

    MatchResult result;
    if (!statement.timeout) {
        result = host_.match(image.assetPath(), rect);
    } else {
        const int64_t timeout = duration(
            number(evaluate(*statement.timeout), statement.timeout->line),
            statement.timeout->line,
            "match timeout"
        );
        result = matchWithTimeout(image.assetPath(), rect, timeout);
    }
    applyMatch(result);
}

MatchResult ScriptRuntime::matchWithTimeout(
    std::string_view imagePath,
    const NormalizedRect& rect,
    int64_t timeout
) {
    const int64_t deadline = host_.nowMillis() + timeout;
    MatchResult result = host_.match(imagePath, rect);
    while (!result.found) {
        checkCancelled();
        const int64_t remaining = deadline - host_.nowMillis();
        if (remaining <= 0) break;
        host_.sleep(std::min<int64_t>(100, remaining));
        result = host_.match(imagePath, rect);
    }
    return result;
}

void ScriptRuntime::applyMatch(const MatchResult& result) {
    found_ = result.found;
    if (result.found) {
        matchLeft_ = result.left;
        matchTop_ = result.top;
        matchWidth_ = result.width;
        matchHeight_ = result.height;
        matchScore_ = result.score;
    } else {
        matchLeft_ = 0.0;
        matchTop_ = 0.0;
        matchWidth_ = 0.0;
        matchHeight_ = 0.0;
        matchScore_ = 0.0;
    }
}

ScriptRuntime::Flow ScriptRuntime::executeFor(const ForStatement& statement) {
    if (isBuiltIn(statement.variable)) {
        throw ScriptRuntimeError(
            "Cannot assign to built-in variable $" + statement.variable,
            statement.line
        );
    }
    const Value iterable = evaluate(*statement.iterable);
    if (iterable.kind() != Value::Kind::Array) {
        throw ScriptRuntimeError("'for' expects an array", statement.iterable->line);
    }

    ++loopDepth_;
    try {
        for (const Value& item : iterable.items()) {
            checkCancelled();
            variables_[statement.variable] = item;
            const Flow flow = executeBlock(statement.body);
            if (flow == Flow::Break) break;
            if (flow == Flow::Stop) {
                --loopDepth_;
                return Flow::Stop;
            }
        }
    } catch (...) {
        --loopDepth_;
        throw;
    }
    --loopDepth_;
    return Flow::Normal;
}

ScriptRuntime::Flow ScriptRuntime::executeWhile(const WhileStatement& statement) {
    ++loopDepth_;
    try {
        while (truthy(evaluate(*statement.condition))) {
            checkCancelled();
            const Flow flow = executeBlock(statement.body);
            if (flow == Flow::Break) break;
            if (flow == Flow::Stop) {
                --loopDepth_;
                return Flow::Stop;
            }
        }
    } catch (...) {
        --loopDepth_;
        throw;
    }
    --loopDepth_;
    return Flow::Normal;
}

Value ScriptRuntime::evaluate(const Expr& expression) {
    switch (expression.kind()) {
        case ExprKind::Literal:
            return static_cast<const LiteralExpr&>(expression).value;
        case ExprKind::Variable:
            return readVariable(static_cast<const VariableExpr&>(expression));
        case ExprKind::Array: {
            const auto& array = static_cast<const ArrayExpr&>(expression);
            std::vector<Value> items;
            items.reserve(array.items.size());
            for (const auto& item : array.items) items.push_back(evaluate(*item));
            return Value::array(std::move(items));
        }
        case ExprKind::Unary:
            return evaluateUnary(static_cast<const UnaryExpr&>(expression));
        case ExprKind::Binary:
            return evaluateBinary(static_cast<const BinaryExpr&>(expression));
    }
    throw ScriptRuntimeError("Unknown expression", expression.line);
}

Value ScriptRuntime::readVariable(const VariableExpr& expression) {
    if (auto builtin = builtIn(expression.name)) return *builtin;
    const auto it = variables_.find(expression.name);
    if (it == variables_.end()) {
        throw ScriptRuntimeError("Unknown variable $" + expression.name, expression.line);
    }
    return it->second;
}

std::optional<Value> ScriptRuntime::builtIn(const std::string& name) const {
    if (name == "FOUND") return Value::boolean(found_);
    if (name == "MATCH_X") return Value::number(matchLeft_);
    if (name == "MATCH_Y") return Value::number(matchTop_);
    if (name == "MATCH_CX") return Value::number(matchLeft_ + matchWidth_ / 2.0);
    if (name == "MATCH_CY") return Value::number(matchTop_ + matchHeight_ / 2.0);
    if (name == "MATCH_W") return Value::number(matchWidth_);
    if (name == "MATCH_H") return Value::number(matchHeight_);
    if (name == "MATCH_SCORE") return Value::number(matchScore_);
    return std::nullopt;
}

bool ScriptRuntime::isBuiltIn(const std::string& name) const {
    return kBuiltIns.count(name) != 0;
}

Value ScriptRuntime::evaluateUnary(const UnaryExpr& expression) {
    const Value operand = evaluate(*expression.operand);
    if (expression.op == "-") return Value::number(-number(operand, expression.line));
    if (expression.op == "+") return Value::number(number(operand, expression.line));
    if (expression.op == "not") return Value::boolean(!truthy(operand));
    throw ScriptRuntimeError("Unknown unary operator " + expression.op, expression.line);
}

Value ScriptRuntime::evaluateBinary(const BinaryExpr& expression) {
    if (expression.op == "and") {
        const Value left = evaluate(*expression.left);
        return Value::boolean(truthy(left) && truthy(evaluate(*expression.right)));
    }
    if (expression.op == "or") {
        const Value left = evaluate(*expression.left);
        return Value::boolean(truthy(left) || truthy(evaluate(*expression.right)));
    }

    const Value left = evaluate(*expression.left);
    const Value right = evaluate(*expression.right);
    if (expression.op == "+") {
        if (left.kind() == Value::Kind::String || right.kind() == Value::Kind::String) {
            return Value::string(displayText(left) + displayText(right));
        }
        if (left.kind() == Value::Kind::Number && right.kind() == Value::Kind::Number) {
            return Value::number(left.number() + right.number());
        }
        if (left.kind() == Value::Kind::Array && right.kind() == Value::Kind::Array) {
            std::vector<Value> items = left.items();
            items.insert(items.end(), right.items().begin(), right.items().end());
            return Value::array(std::move(items));
        }
        throw ScriptRuntimeError("'+' expects numbers, strings, or arrays", expression.line);
    }
    if (expression.op == "-") {
        return Value::number(number(left, expression.line) - number(right, expression.line));
    }
    if (expression.op == "*") {
        return Value::number(number(left, expression.line) * number(right, expression.line));
    }
    if (expression.op == "/") {
        const double divisor = number(right, expression.line);
        if (divisor == 0.0) throw ScriptRuntimeError("Division by zero", expression.line);
        return Value::number(number(left, expression.line) / divisor);
    }
    if (expression.op == "==") return Value::boolean(left == right);
    if (expression.op == "!=") return Value::boolean(left != right);
    if (expression.op == "<" || expression.op == "<=" ||
        expression.op == ">" || expression.op == ">=") {
        int comparison = 0;
        if (left.kind() == Value::Kind::Number && right.kind() == Value::Kind::Number) {
            comparison = left.number() < right.number() ? -1 : (left.number() > right.number() ? 1 : 0);
        } else if (left.kind() == Value::Kind::String && right.kind() == Value::Kind::String) {
            comparison = left.string().compare(right.string());
            comparison = comparison < 0 ? -1 : (comparison > 0 ? 1 : 0);
        } else {
            throw ScriptRuntimeError("Comparison expects two numbers or two strings", expression.line);
        }
        if (expression.op == "<") return Value::boolean(comparison < 0);
        if (expression.op == "<=") return Value::boolean(comparison <= 0);
        if (expression.op == ">") return Value::boolean(comparison > 0);
        return Value::boolean(comparison >= 0);
    }
    throw ScriptRuntimeError("Unknown operator " + expression.op, expression.line);
}

double ScriptRuntime::number(const Value& value, int line) const {
    if (value.kind() != Value::Kind::Number) throw ScriptRuntimeError("Expected a number", line);
    return value.number();
}

double ScriptRuntime::unit(const Value& value, int line) const {
    const double result = number(value, line);
    if (!std::isfinite(result) || result < 0.0 || result > 1.0) {
        throw ScriptRuntimeError("Coordinate must be between 0 and 1", line);
    }
    return result;
}

int64_t ScriptRuntime::duration(double value, int line, std::string_view label) const {
    if (!std::isfinite(value) || value < 0.0) {
        throw ScriptRuntimeError(std::string(label) + " must be a non-negative duration", line);
    }
    return static_cast<int64_t>(value);
}

void ScriptRuntime::tick(int line) {
    ++steps_;
    if (steps_ > maxSteps_) {
        throw ScriptRuntimeError("Step limit of " + std::to_string(maxSteps_) + " exceeded", line);
    }
    checkCancelled();
}

void ScriptRuntime::checkCancelled() const {
    if (host_.cancelled()) throw ScriptCancelled();
}

}  // namespace pcp::script

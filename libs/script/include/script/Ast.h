#pragma once

#include "script/Value.h"

#include <memory>
#include <optional>
#include <string>
#include <vector>

namespace pcp::script {

enum class ExprKind {
    Literal,
    Variable,
    Array,
    Unary,
    Binary
};

struct Expr {
    Expr(int line, ExprKind kind) : line(line), kind_(kind) {}
    virtual ~Expr() = default;
    ExprKind kind() const { return kind_; }
    int line;

private:
    ExprKind kind_;
};

using ExprPtr = std::unique_ptr<Expr>;

struct LiteralExpr final : Expr {
    LiteralExpr(int line, Value value) : Expr(line, ExprKind::Literal), value(std::move(value)) {}
    Value value;
};

struct VariableExpr final : Expr {
    VariableExpr(int line, std::string name)
        : Expr(line, ExprKind::Variable), name(std::move(name)) {}
    std::string name;
};

struct ArrayExpr final : Expr {
    ArrayExpr(int line, std::vector<ExprPtr> items)
        : Expr(line, ExprKind::Array), items(std::move(items)) {}
    std::vector<ExprPtr> items;
};

struct UnaryExpr final : Expr {
    UnaryExpr(int line, std::string op, ExprPtr operand)
        : Expr(line, ExprKind::Unary), op(std::move(op)), operand(std::move(operand)) {}
    std::string op;
    ExprPtr operand;
};

struct BinaryExpr final : Expr {
    BinaryExpr(int line, std::string op, ExprPtr left, ExprPtr right)
        : Expr(line, ExprKind::Binary),
          op(std::move(op)),
          left(std::move(left)),
          right(std::move(right)) {}
    std::string op;
    ExprPtr left;
    ExprPtr right;
};

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

struct Statement {
    Statement(int line, StatementKind kind) : line(line), kind_(kind) {}
    virtual ~Statement() = default;
    StatementKind kind() const { return kind_; }
    int line;

private:
    StatementKind kind_;
};

using StatementPtr = std::unique_ptr<Statement>;
using Block = std::vector<StatementPtr>;

struct LetStatement final : Statement {
    LetStatement(int line, std::string name, ExprPtr expression)
        : Statement(line, StatementKind::Let),
          name(std::move(name)),
          expression(std::move(expression)) {}
    std::string name;
    ExprPtr expression;
};

struct PrintStatement final : Statement {
    PrintStatement(int line, ExprPtr expression)
        : Statement(line, StatementKind::Print), expression(std::move(expression)) {}
    ExprPtr expression;
};

struct WaitStatement final : Statement {
    WaitStatement(int line, ExprPtr milliseconds)
        : Statement(line, StatementKind::Wait), milliseconds(std::move(milliseconds)) {}
    ExprPtr milliseconds;
};

enum class VisionRegionMode {
    Rect,
    Coord
};

struct VisionStatement final : Statement {
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

struct TapStatement final : Statement {
    TapStatement(int line, ExprPtr x, ExprPtr y)
        : Statement(line, StatementKind::Tap), x(std::move(x)), y(std::move(y)) {}
    ExprPtr x;
    ExprPtr y;
};

struct SwipeStatement final : Statement {
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

struct IfBranch {
    ExprPtr condition;
    Block body;
};

struct IfStatement final : Statement {
    IfStatement(int line, std::vector<IfBranch> branches, std::optional<Block> elseBody)
        : Statement(line, StatementKind::If),
          branches(std::move(branches)),
          elseBody(std::move(elseBody)) {}
    std::vector<IfBranch> branches;
    std::optional<Block> elseBody;
};

struct ForStatement final : Statement {
    ForStatement(int line, std::string variable, ExprPtr iterable, Block body)
        : Statement(line, StatementKind::For),
          variable(std::move(variable)),
          iterable(std::move(iterable)),
          body(std::move(body)) {}
    std::string variable;
    ExprPtr iterable;
    Block body;
};

struct WhileStatement final : Statement {
    WhileStatement(int line, ExprPtr condition, Block body)
        : Statement(line, StatementKind::While),
          condition(std::move(condition)),
          body(std::move(body)) {}
    ExprPtr condition;
    Block body;
};

struct BreakStatement final : Statement {
    explicit BreakStatement(int line) : Statement(line, StatementKind::Break) {}
};

struct ContinueStatement final : Statement {
    explicit ContinueStatement(int line) : Statement(line, StatementKind::Continue) {}
};

struct StopStatement final : Statement {
    explicit StopStatement(int line) : Statement(line, StatementKind::Stop) {}
};

struct Program {
    Block statements;
};

}  // namespace pcp::script

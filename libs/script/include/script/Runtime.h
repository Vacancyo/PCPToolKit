#pragma once

#include "script/Ast.h"

#include <cstdint>
#include <optional>
#include <string>
#include <string_view>
#include <unordered_map>

namespace pcp::script {

struct NormalizedRect {
    double left;
    double top;
    double right;
    double bottom;
};

struct MatchResult {
    bool found = false;
    double left = 0.0;
    double top = 0.0;
    double width = 0.0;
    double height = 0.0;
    double score = 0.0;
};

class Host {
public:
    virtual ~Host() = default;
    virtual bool cancelled() const = 0;
    virtual int64_t nowMillis() const = 0;
    virtual void log(std::string_view message) = 0;
    virtual void sleep(int64_t milliseconds) = 0;
    virtual void tap(double x, double y) = 0;
    virtual void swipe(double x1, double y1, double x2, double y2, int64_t durationMillis) = 0;
    virtual MatchResult match(std::string_view assetPath, const NormalizedRect& region) = 0;
};

class ScriptRuntime {
public:
    explicit ScriptRuntime(Host& host, int maxSteps = 1'000'000);
    void execute(const Program& program);

private:
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

    Flow executeBlock(const Block& statements);
    Flow executeStatement(const Statement& statement);
    Flow executeFor(const ForStatement& statement);
    Flow executeWhile(const WhileStatement& statement);
    void executeVision(const VisionStatement& statement);
    MatchResult matchWithTimeout(
        std::string_view imagePath,
        const NormalizedRect& rect,
        int64_t timeout
    );
    void applyMatch(const MatchResult& result);

    Value evaluate(const Expr& expression);
    Value evaluateUnary(const UnaryExpr& expression);
    Value evaluateBinary(const BinaryExpr& expression);
    Value readVariable(const VariableExpr& expression);
    std::optional<Value> builtIn(const std::string& name) const;
    bool isBuiltIn(const std::string& name) const;

    double number(const Value& value, int line) const;
    double unit(const Value& value, int line) const;
    int64_t duration(double value, int line, std::string_view label) const;
    void tick(int line);
    void checkCancelled() const;
};

}  // namespace pcp::script

#include "Parser.h"

#include "script/Error.h"

#include <stdexcept>
#include <utility>

namespace pcp::script {

ScriptParser::ScriptParser(std::string source)
    : tokens_(Lexer(std::move(source)).tokenize()) {}

Program parseScript(std::string_view source) {
    return ScriptParser(std::string(source)).parse();
}

Program ScriptParser::parse() {
    Program program;
    while (!check(TokenKind::End)) {
        program.statements.push_back(parseStatement());
    }
    return program;
}

StatementPtr ScriptParser::parseStatement() {
    if (matchKeyword("let")) {
        const int line = previous().line;
        const Token& variable = expect(TokenKind::Variable, "Expected a variable after 'let'");
        expect(TokenKind::Equal, "Expected '=' after the variable");
        auto expression = parseExpression();
        expectNewline();
        return std::make_unique<LetStatement>(line, variable.text, std::move(expression));
    }
    if (matchKeyword("print")) {
        const int line = previous().line;
        auto expression = parseExpression();
        expectNewline();
        return std::make_unique<PrintStatement>(line, std::move(expression));
    }
    if (matchKeyword("wait")) {
        const int line = previous().line;
        auto expression = parseExpression();
        expectNewline();
        return std::make_unique<WaitStatement>(line, std::move(expression));
    }
    if (matchKeyword("exist")) return parseVision(false);
    if (matchKeyword("match")) return parseVision(true);
    if (matchKeyword("tap")) {
        const int line = previous().line;
        auto x = parseArgument();
        auto y = parseArgument();
        expectNewline();
        return std::make_unique<TapStatement>(line, std::move(x), std::move(y));
    }
    if (matchKeyword("swipe")) {
        const int line = previous().line;
        auto x1 = parseArgument();
        auto y1 = parseArgument();
        auto x2 = parseArgument();
        auto y2 = parseArgument();
        auto duration = parseArgument();
        expectNewline();
        return std::make_unique<SwipeStatement>(
            line,
            std::move(x1),
            std::move(y1),
            std::move(x2),
            std::move(y2),
            std::move(duration)
        );
    }
    if (matchKeyword("if")) return parseIf();
    if (matchKeyword("for")) return parseFor();
    if (matchKeyword("while")) {
        const int line = previous().line;
        auto condition = parseExpression();
        auto body = parseBlock();
        return std::make_unique<WhileStatement>(line, std::move(condition), std::move(body));
    }
    if (matchKeyword("break")) {
        const int line = previous().line;
        expectNewline();
        return std::make_unique<BreakStatement>(line);
    }
    if (matchKeyword("continue")) {
        const int line = previous().line;
        expectNewline();
        return std::make_unique<ContinueStatement>(line);
    }
    if (matchKeyword("stop")) {
        const int line = previous().line;
        expectNewline();
        return std::make_unique<StopStatement>(line);
    }
    error("Expected a statement");
}

StatementPtr ScriptParser::parseVision(bool waitForMatch) {
    const int line = previous().line;
    const Token& modeToken = expect(TokenKind::Identifier, "Expected 'rect' or 'coord'");
    VisionRegionMode mode;
    if (modeToken.text == "rect") {
        mode = VisionRegionMode::Rect;
    } else if (modeToken.text == "coord") {
        mode = VisionRegionMode::Coord;
    } else {
        throw ScriptParseError("Expected 'rect' or 'coord'", modeToken.line, modeToken.column);
    }

    auto image = parseArgument();
    std::vector<ExprPtr> values;
    values.reserve(4);
    for (int i = 0; i < 4; ++i) values.push_back(parseArgument());
    ExprPtr timeout;
    if (waitForMatch) timeout = parseArgument();
    expectNewline();
    return std::make_unique<VisionStatement>(
        line,
        std::move(image),
        mode,
        std::move(values),
        std::move(timeout)
    );
}

StatementPtr ScriptParser::parseIf() {
    const int line = previous().line;
    std::vector<IfBranch> branches;
    {
        auto condition = parseExpression();
        auto body = parseBlock();
        branches.push_back(IfBranch{std::move(condition), std::move(body)});
    }
    while (matchKeyword("elif")) {
        auto condition = parseExpression();
        auto body = parseBlock();
        branches.push_back(IfBranch{std::move(condition), std::move(body)});
    }
    std::optional<Block> elseBody;
    if (matchKeyword("else")) elseBody = parseBlock();
    return std::make_unique<IfStatement>(line, std::move(branches), std::move(elseBody));
}

StatementPtr ScriptParser::parseFor() {
    const int line = previous().line;
    const Token& variable = expect(TokenKind::Variable, "Expected a loop variable");
    if (!matchKeyword("in")) error("Expected 'in'");
    auto iterable = parseExpression();
    auto body = parseBlock();
    return std::make_unique<ForStatement>(
        line,
        variable.text,
        std::move(iterable),
        std::move(body)
    );
}

Block ScriptParser::parseBlock() {
    expect(TokenKind::Colon, "Expected ':' before the block");
    expect(TokenKind::Newline, "Expected a newline after ':'");
    expect(TokenKind::Indent, "Expected an indented block");
    Block statements;
    while (!check(TokenKind::Dedent) && !check(TokenKind::End)) {
        statements.push_back(parseStatement());
    }
    expect(TokenKind::Dedent, "Expected the block to end");
    if (statements.empty()) error("Expected at least one statement in the block");
    return statements;
}

ExprPtr ScriptParser::parseExpression() {
    return parseOr();
}

ExprPtr ScriptParser::parseOr() {
    auto expression = parseAnd();
    while (match(TokenKind::OrOr) || matchKeyword("or")) {
        const Token& op = previous();
        expression = std::make_unique<BinaryExpr>(
            op.line,
            "or",
            std::move(expression),
            parseAnd()
        );
    }
    return expression;
}

ExprPtr ScriptParser::parseAnd() {
    auto expression = parseNot();
    while (match(TokenKind::AndAnd) || matchKeyword("and")) {
        const Token& op = previous();
        expression = std::make_unique<BinaryExpr>(
            op.line,
            "and",
            std::move(expression),
            parseNot()
        );
    }
    return expression;
}

ExprPtr ScriptParser::parseNot() {
    if (match(TokenKind::Bang) || matchKeyword("not")) {
        const Token& op = previous();
        return std::make_unique<UnaryExpr>(op.line, "not", parseNot());
    }
    return parseComparison();
}

ExprPtr ScriptParser::parseComparison() {
    auto left = parseAdditive();
    std::string op;
    if (match(TokenKind::EqualEqual)) op = "==";
    else if (match(TokenKind::BangEqual)) op = "!=";
    else if (match(TokenKind::Less)) op = "<";
    else if (match(TokenKind::LessEqual)) op = "<=";
    else if (match(TokenKind::Greater)) op = ">";
    else if (match(TokenKind::GreaterEqual)) op = ">=";
    else return left;

    const int line = previous().line;
    auto right = parseAdditive();
    if (checkComparison()) error("Comparisons cannot be chained");
    return std::make_unique<BinaryExpr>(
        line,
        op,
        std::move(left),
        std::move(right)
    );
}

ExprPtr ScriptParser::parseAdditive() {
    auto expression = parseMultiplicative();
    while (match(TokenKind::Plus) || match(TokenKind::Minus)) {
        const Token& op = previous();
        expression = std::make_unique<BinaryExpr>(
            op.line,
            op.text,
            std::move(expression),
            parseMultiplicative()
        );
    }
    return expression;
}

ExprPtr ScriptParser::parseMultiplicative() {
    auto expression = parseUnary();
    while (match(TokenKind::Star) || match(TokenKind::Slash)) {
        const Token& op = previous();
        expression = std::make_unique<BinaryExpr>(
            op.line,
            op.text,
            std::move(expression),
            parseUnary()
        );
    }
    return expression;
}

ExprPtr ScriptParser::parseUnary() {
    if (match(TokenKind::Minus) || match(TokenKind::Plus)) {
        const Token& op = previous();
        return std::make_unique<UnaryExpr>(op.line, op.text, parseUnary());
    }
    return parsePrimary();
}

ExprPtr ScriptParser::parseArgument() {
    if (match(TokenKind::Minus) || match(TokenKind::Plus)) {
        const Token& op = previous();
        return std::make_unique<UnaryExpr>(op.line, op.text, parseArgument());
    }
    return parsePrimary();
}

ExprPtr ScriptParser::parsePrimary() {
    if (match(TokenKind::Number)) {
        const Token& token = previous();
        return std::make_unique<LiteralExpr>(
            token.line,
            Value::number(std::stod(token.text))
        );
    }
    if (match(TokenKind::String)) {
        const Token& token = previous();
        return std::make_unique<LiteralExpr>(token.line, Value::string(token.text));
    }
    if (match(TokenKind::Boolean)) {
        const Token& token = previous();
        return std::make_unique<LiteralExpr>(
            token.line,
            Value::boolean(token.text == "true")
        );
    }
    if (match(TokenKind::Variable)) {
        const Token& token = previous();
        return std::make_unique<VariableExpr>(token.line, token.text);
    }
    if (match(TokenKind::Image)) {
        const Token& token = previous();
        try {
            return std::make_unique<LiteralExpr>(token.line, imageValue(token.text));
        } catch (const std::invalid_argument&) {
            throw ScriptParseError("Invalid image object: &" + token.text, token.line, token.column);
        }
    }
    if (match(TokenKind::LeftBracket)) return parseArray(previous());
    if (match(TokenKind::LeftParen)) {
        auto expression = parseExpression();
        expect(TokenKind::RightParen, "Expected ')'");
        return expression;
    }
    error("Expected an expression");
}

ExprPtr ScriptParser::parseArray(const Token& open) {
    std::vector<ExprPtr> items;
    while (!check(TokenKind::RightBracket) && !check(TokenKind::End)) {
        items.push_back(parseExpression());
        if (!match(TokenKind::Comma)) break;
    }
    expect(TokenKind::RightBracket, "Expected ']'");
    return std::make_unique<ArrayExpr>(open.line, std::move(items));
}

bool ScriptParser::checkComparison() const {
    return check(TokenKind::EqualEqual) ||
           check(TokenKind::BangEqual) ||
           check(TokenKind::Less) ||
           check(TokenKind::LessEqual) ||
           check(TokenKind::Greater) ||
           check(TokenKind::GreaterEqual);
}

bool ScriptParser::match(TokenKind kind) {
    if (!check(kind)) return false;
    advance();
    return true;
}

bool ScriptParser::matchKeyword(const std::string& keyword) {
    if (!check(TokenKind::Identifier) || peek().text != keyword) return false;
    advance();
    return true;
}

const Token& ScriptParser::expect(TokenKind kind, const std::string& message) {
    if (check(kind)) return advance();
    error(message);
}

void ScriptParser::expectNewline() {
    expect(TokenKind::Newline, "Expected a newline");
}

bool ScriptParser::check(TokenKind kind) const {
    return peek().kind == kind;
}

const Token& ScriptParser::advance() {
    if (!check(TokenKind::End)) ++current_;
    return previous();
}

const Token& ScriptParser::peek() const {
    return tokens_[current_];
}

const Token& ScriptParser::previous() const {
    return tokens_[current_ - 1];
}

void ScriptParser::error(const std::string& message) const {
    throw ScriptParseError(message, peek().line, peek().column);
}

}  // namespace pcp::script

#pragma once

#include "Lexer.h"
#include "script/Ast.h"

#include <string>

namespace pcp::script {

class ScriptParser {
public:
    explicit ScriptParser(std::string source);
    Program parse();

private:
    std::vector<Token> tokens_;
    size_t current_ = 0;

    StatementPtr parseStatement();
    StatementPtr parseVision(bool waitForMatch);
    StatementPtr parseIf();
    StatementPtr parseFor();
    Block parseBlock();

    ExprPtr parseExpression();
    ExprPtr parseOr();
    ExprPtr parseAnd();
    ExprPtr parseNot();
    ExprPtr parseComparison();
    ExprPtr parseAdditive();
    ExprPtr parseMultiplicative();
    ExprPtr parseUnary();
    ExprPtr parseArgument();
    ExprPtr parsePrimary();
    ExprPtr parseArray(const Token& open);

    bool checkComparison() const;
    bool match(TokenKind kind);
    bool matchKeyword(const std::string& keyword);
    const Token& expect(TokenKind kind, const std::string& message);
    void expectNewline();
    bool check(TokenKind kind) const;
    const Token& advance();
    const Token& peek() const;
    const Token& previous() const;
    [[noreturn]] void error(const std::string& message) const;
};

}  // namespace pcp::script

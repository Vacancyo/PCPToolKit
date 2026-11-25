#pragma once

#include <string>
#include <vector>

namespace pcp::script {

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

struct Token {
    TokenKind kind;
    std::string text;
    int line;
    int column;
};

class Lexer {
public:
    explicit Lexer(std::string source) : source_(std::move(source)) {}
    std::vector<Token> tokenize();

private:
    void scanLine(const std::string& line, int lineNumber, int columnOffset);
    size_t scanString(const std::string& line, size_t start, int lineNumber, int columnOffset);
    size_t scanNumber(const std::string& line, size_t start, int lineNumber, int columnOffset);
    size_t scanVariable(const std::string& line, size_t start, int lineNumber, int columnOffset);
    size_t scanImage(const std::string& line, size_t start, int lineNumber, int columnOffset);
    size_t scanIdentifier(const std::string& line, size_t start, int lineNumber, int columnOffset);
    size_t scanOperator(const std::string& line, size_t start, int lineNumber, int columnOffset);
    void add(TokenKind kind, std::string text, int line, int column);

    std::string source_;
    std::vector<Token> tokens_;
    std::vector<int> indents_{0};
};

}  // namespace pcp::script

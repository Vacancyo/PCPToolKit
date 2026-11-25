#include "Lexer.h"

#include "script/Error.h"

#include <cctype>
#include <cstdlib>

namespace pcp::script {
namespace {

bool isDigit(char c) {
    return c >= '0' && c <= '9';
}

bool isIdentifierStart(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
}

bool isIdentifierPart(char c) {
    return isIdentifierStart(c) || isDigit(c);
}

bool isImagePart(char c) {
    return isIdentifierPart(c);
}

}  // namespace

std::vector<Token> Lexer::tokenize() {
    size_t position = 0;
    int lineNumber = 1;
    while (position <= source_.size()) {
        const size_t newline = source_.find('\n', position);
        const size_t end = newline == std::string::npos ? source_.size() : newline;
        std::string raw = source_.substr(position, end - position);
        if (!raw.empty() && raw.back() == '\r') raw.pop_back();

        size_t offset = 0;
        int indent = 0;
        while (offset < raw.size() && (raw[offset] == ' ' || raw[offset] == '\t')) {
            indent += raw[offset] == '\t' ? 4 : 1;
            ++offset;
        }
        const std::string content = raw.substr(offset);
        if (!content.empty() && content[0] != '#') {
            if (indent > indents_.back()) {
                indents_.push_back(indent);
                add(TokenKind::Indent, std::to_string(indent), lineNumber, 1);
            } else if (indent < indents_.back()) {
                while (indent < indents_.back()) {
                    indents_.pop_back();
                    add(TokenKind::Dedent, std::to_string(indent), lineNumber, 1);
                }
                if (indent != indents_.back()) {
                    throw ScriptParseError("Inconsistent indentation", lineNumber, 1);
                }
            }
            scanLine(content, lineNumber, static_cast<int>(offset) + 1);
            add(TokenKind::Newline, "\n", lineNumber, static_cast<int>(raw.size()) + 1);
        }

        if (newline == std::string::npos) break;
        position = newline + 1;
        ++lineNumber;
    }

    while (indents_.size() > 1) {
        indents_.pop_back();
        add(TokenKind::Dedent, "0", lineNumber, 1);
    }
    add(TokenKind::End, "", lineNumber, 1);
    return std::move(tokens_);
}

void Lexer::scanLine(const std::string& line, int lineNumber, int columnOffset) {
    size_t index = 0;
    while (index < line.size()) {
        const char c = line[index];
        if (c == ' ' || c == '\t') {
            ++index;
        } else if (c == '#') {
            return;
        } else if (c == '"' || c == '\'') {
            index = scanString(line, index, lineNumber, columnOffset);
        } else if (isDigit(c) || (c == '.' && index + 1 < line.size() && isDigit(line[index + 1]))) {
            index = scanNumber(line, index, lineNumber, columnOffset);
        } else if (c == '$') {
            index = scanVariable(line, index, lineNumber, columnOffset);
        } else if (c == '&' && index + 1 < line.size() && line[index + 1] == '&') {
            index = scanOperator(line, index, lineNumber, columnOffset);
        } else if (c == '&') {
            index = scanImage(line, index, lineNumber, columnOffset);
        } else if (isIdentifierStart(c)) {
            index = scanIdentifier(line, index, lineNumber, columnOffset);
        } else {
            index = scanOperator(line, index, lineNumber, columnOffset);
        }
    }
}

size_t Lexer::scanString(
    const std::string& line,
    size_t start,
    int lineNumber,
    int columnOffset
) {
    const char quote = line[start];
    std::string value;
    size_t index = start + 1;
    while (index < line.size()) {
        const char c = line[index];
        if (c == quote) {
            add(TokenKind::String, std::move(value), lineNumber, columnOffset + static_cast<int>(start));
            return index + 1;
        }
        if (c == '\\') {
            if (index + 1 >= line.size()) {
                throw ScriptParseError(
                    "Incomplete escape sequence",
                    lineNumber,
                    columnOffset + static_cast<int>(index)
                );
            }
            const char escaped = line[index + 1];
            switch (escaped) {
                case '\\': value += '\\'; break;
                case '"': value += '"'; break;
                case '\'': value += '\''; break;
                case 'n': value += '\n'; break;
                case 'r': value += '\r'; break;
                case 't': value += '\t'; break;
                default:
                    throw ScriptParseError(
                        std::string("Unknown escape: \\") + escaped,
                        lineNumber,
                        columnOffset + static_cast<int>(index)
                    );
            }
            index += 2;
        } else {
            value += c;
            ++index;
        }
    }
    throw ScriptParseError("Unterminated string", lineNumber, columnOffset + static_cast<int>(start));
}

size_t Lexer::scanNumber(
    const std::string& line,
    size_t start,
    int lineNumber,
    int columnOffset
) {
    size_t index = start;
    bool dot = false;
    while (index < line.size()) {
        const char c = line[index];
        if (isDigit(c)) {
            ++index;
        } else if (c == '.' && !dot) {
            dot = true;
            ++index;
        } else {
            break;
        }
    }
    const std::string text = line.substr(start, index - start);
    char* end = nullptr;
    std::strtod(text.c_str(), &end);
    if (!end || *end != '\0') {
        throw ScriptParseError("Invalid number: " + text, lineNumber, columnOffset + static_cast<int>(start));
    }
    add(TokenKind::Number, text, lineNumber, columnOffset + static_cast<int>(start));
    return index;
}

size_t Lexer::scanVariable(
    const std::string& line,
    size_t start,
    int lineNumber,
    int columnOffset
) {
    size_t index = start + 1;
    while (index < line.size() && isIdentifierPart(line[index])) ++index;
    if (index == start + 1) {
        throw ScriptParseError("Expected a variable name after '$'", lineNumber, columnOffset + static_cast<int>(start));
    }
    add(
        TokenKind::Variable,
        line.substr(start + 1, index - start - 1),
        lineNumber,
        columnOffset + static_cast<int>(start)
    );
    return index;
}

size_t Lexer::scanImage(
    const std::string& line,
    size_t start,
    int lineNumber,
    int columnOffset
) {
    size_t index = start + 1;
    bool expectPart = true;
    while (index < line.size()) {
        const char c = line[index];
        if (expectPart) {
            if (!isImagePart(c)) break;
            expectPart = false;
            ++index;
        } else if (c == '.') {
            expectPart = true;
            ++index;
        } else if (isImagePart(c)) {
            ++index;
        } else {
            break;
        }
    }
    if (expectPart || index == start + 1) {
        throw ScriptParseError("Invalid image object after '&'", lineNumber, columnOffset + static_cast<int>(start));
    }
    add(
        TokenKind::Image,
        line.substr(start + 1, index - start - 1),
        lineNumber,
        columnOffset + static_cast<int>(start)
    );
    return index;
}

size_t Lexer::scanIdentifier(
    const std::string& line,
    size_t start,
    int lineNumber,
    int columnOffset
) {
    size_t index = start;
    while (index < line.size() && isIdentifierPart(line[index])) ++index;
    const std::string text = line.substr(start, index - start);
    add(
        text == "true" || text == "false" ? TokenKind::Boolean : TokenKind::Identifier,
        text,
        lineNumber,
        columnOffset + static_cast<int>(start)
    );
    return index;
}

size_t Lexer::scanOperator(
    const std::string& line,
    size_t start,
    int lineNumber,
    int columnOffset
) {
    const int column = columnOffset + static_cast<int>(start);
    const std::string two = line.substr(start, 2);
    if (two == "==") {
        add(TokenKind::EqualEqual, two, lineNumber, column);
        return start + 2;
    }
    if (two == "!=") {
        add(TokenKind::BangEqual, two, lineNumber, column);
        return start + 2;
    }
    if (two == "<=") {
        add(TokenKind::LessEqual, two, lineNumber, column);
        return start + 2;
    }
    if (two == ">=") {
        add(TokenKind::GreaterEqual, two, lineNumber, column);
        return start + 2;
    }
    if (two == "&&") {
        add(TokenKind::AndAnd, two, lineNumber, column);
        return start + 2;
    }
    if (two == "||") {
        add(TokenKind::OrOr, two, lineNumber, column);
        return start + 2;
    }

    switch (line[start]) {
        case '+': add(TokenKind::Plus, "+", lineNumber, column); break;
        case '-': add(TokenKind::Minus, "-", lineNumber, column); break;
        case '*': add(TokenKind::Star, "*", lineNumber, column); break;
        case '/': add(TokenKind::Slash, "/", lineNumber, column); break;
        case '=': add(TokenKind::Equal, "=", lineNumber, column); break;
        case '!': add(TokenKind::Bang, "!", lineNumber, column); break;
        case '<': add(TokenKind::Less, "<", lineNumber, column); break;
        case '>': add(TokenKind::Greater, ">", lineNumber, column); break;
        case '(': add(TokenKind::LeftParen, "(", lineNumber, column); break;
        case ')': add(TokenKind::RightParen, ")", lineNumber, column); break;
        case '[': add(TokenKind::LeftBracket, "[", lineNumber, column); break;
        case ']': add(TokenKind::RightBracket, "]", lineNumber, column); break;
        case ',': add(TokenKind::Comma, ",", lineNumber, column); break;
        case ':': add(TokenKind::Colon, ":", lineNumber, column); break;
        default:
            throw ScriptParseError(
                std::string("Unexpected character: ") + line[start],
                lineNumber,
                column
            );
    }
    return start + 1;
}

void Lexer::add(TokenKind kind, std::string text, int line, int column) {
    tokens_.push_back(Token{kind, std::move(text), line, column});
}

}  // namespace pcp::script

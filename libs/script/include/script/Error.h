#pragma once

#include <stdexcept>
#include <string>

namespace pcp::script {

class ScriptParseError : public std::runtime_error {
public:
    ScriptParseError(std::string message, int line, int column)
        : std::runtime_error(
              "Line " + std::to_string(line) + ", column " + std::to_string(column) +
              ": " + message
          ),
          line_(line),
          column_(column) {}

    int line() const { return line_; }
    int column() const { return column_; }

private:
    int line_;
    int column_;
};

class ScriptRuntimeError : public std::runtime_error {
public:
    ScriptRuntimeError(std::string message, int line)
        : std::runtime_error("Line " + std::to_string(line) + ": " + message), line_(line) {}

    int line() const { return line_; }

private:
    int line_;
};

class ScriptCancelled : public std::runtime_error {
public:
    ScriptCancelled() : std::runtime_error("Script cancelled") {}
};

}  // namespace pcp::script

#pragma once

#include "script/Ast.h"

#include <string_view>

namespace pcp::script {

Program parseScript(std::string_view source);

}  // namespace pcp::script

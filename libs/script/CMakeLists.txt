add_library(script_core STATIC
  src/Value.cpp
  src/Lexer.cpp
  src/Parser.cpp
  src/Runtime.cpp
  src/TemplateMatcher.cpp
)

target_include_directories(script_core PUBLIC include)
target_compile_features(script_core PUBLIC cxx_std_17)

if(MSVC)
  target_compile_options(script_core PRIVATE /EHsc)
endif()

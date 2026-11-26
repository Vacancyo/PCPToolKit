add_library(rton_core STATIC
  src/NumberFormat.cpp
  src/Json.cpp
  src/Reader.cpp
  src/Decoder.cpp
  src/Encoder.cpp
  src/MD5.cpp
  src/Rijndael.cpp
  src/Crypto.cpp
  src/Converter.cpp
)
target_include_directories(rton_core PUBLIC include)
target_link_libraries(rton_core PUBLIC common)   # 依赖公共库

# GCC 8 及更早版本使用 std::filesystem 需要链接 stdc++fs
if(CMAKE_CXX_COMPILER_ID STREQUAL "GNU" AND CMAKE_CXX_COMPILER_VERSION VERSION_LESS 9)
  target_link_libraries(rton_core PUBLIC stdc++fs)
endif()

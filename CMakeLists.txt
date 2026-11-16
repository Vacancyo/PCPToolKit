cmake_minimum_required(VERSION 3.14)
project(PCPToolkit VERSION 1.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

if(NOT CMAKE_BUILD_TYPE AND NOT CMAKE_CONFIGURATION_TYPES)
  set(CMAKE_BUILD_TYPE Release)
endif()

set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/bin)

if(MSVC)
  add_compile_options(/utf-8 /W4)
else()
  add_compile_options(-Wall -Wextra)
endif()

add_subdirectory(libs/common)
add_subdirectory(tools/rton)

cmake_minimum_required(VERSION 3.22.1)
project(pcpjni LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 仓库根目录，apps/android/app/src/main/cpp 往上 6 级就是 PCPToolkit/
set(TOOLKIT_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../..)

add_subdirectory(${TOOLKIT_ROOT}/libs/common ${CMAKE_BINARY_DIR}/common)
add_subdirectory(${TOOLKIT_ROOT}/tools/rton  ${CMAKE_BINARY_DIR}/rton)

add_library(pcpjni SHARED
  JniUtils.cpp
  RtonJni.cpp
)
target_link_libraries(pcpjni PRIVATE rton_core)

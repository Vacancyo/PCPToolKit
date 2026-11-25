add_executable(script_tests ScriptTests.cpp)
target_link_libraries(script_tests PRIVATE script_core)
target_compile_definitions(
  script_tests
  PRIVATE TEST_DAILY_RAW="${CMAKE_CURRENT_SOURCE_DIR}/data/daily.rgba"
)

add_test(NAME script_tests COMMAND script_tests)

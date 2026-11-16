add_library(common STATIC
  src/I18n.cpp
  src/FileIO.cpp
)
target_include_directories(common PUBLIC include)
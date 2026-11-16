#include "common/FileIO.h"
#include "common/I18n.h"

#include <fstream>
#include <iterator>
#include <stdexcept>

namespace common {

Bytes readAll(const std::string& path) {
    std::ifstream f(path, std::ios::binary);
    if (!f) throw std::runtime_error(i18n::translate("file.cannot_open", {path}));
    return {(std::istreambuf_iterator<char>(f)), std::istreambuf_iterator<char>()};
}

void writeAll(const std::string& path, const void* data, size_t n) {
    std::ofstream f(path, std::ios::binary);
    if (!f) throw std::runtime_error(i18n::translate("file.cannot_write", {path}));
    f.write(static_cast<const char*>(data), static_cast<std::streamsize>(n));
}

}  // namespace common

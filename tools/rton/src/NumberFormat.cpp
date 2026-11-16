#include "rton/NumberFormat.h"

#include <cstdio>
#include <cstdlib>

namespace rton {

std::string fmtFloat(float f) {
    char buf[64];
    for (int prec = 1; prec <= 9; prec++) {
        std::snprintf(buf, sizeof buf, "%.*g", prec, static_cast<double>(f));
        if (std::strtof(buf, nullptr) == f) break;
    }
    return buf;
}

std::string fmtDouble(double d) {
    char buf[64];
    for (int prec = 1; prec <= 17; prec++) {
        std::snprintf(buf, sizeof buf, "%.*g", prec, d);
        if (std::strtod(buf, nullptr) == d) break;
    }
    return buf;
}

std::string makeFloatText(std::string t) {
    if (t.find_first_of("nNiI") != std::string::npos) return "null";
    if (t.find('.') != std::string::npos) return t;
    size_t e = t.find_first_of("eE");
    if (e == std::string::npos) return t + ".0";
    return t.substr(0, e) + ".0" + t.substr(e);
}

}  // namespace rton
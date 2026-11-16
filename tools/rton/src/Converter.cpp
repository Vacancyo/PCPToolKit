#include "rton/Converter.h"
#include "rton/Json.h"
#include "rton/Crypto.h"
#include "rton/Decoder.h"
#include "rton/Encoder.h"
#include "common/FileIO.h"
#include "common/I18n.h"

#include <stdexcept>

namespace rton {

void rtonToJsonFile(const std::string& in, const std::string& out, const std::string& key, bool hasKey) {
    common::Bytes data = common::readAll(in);
    bool encrypted = isEncryptedRton(data);
    if (encrypted) {
        if (!hasKey) throw std::runtime_error(i18n::translate("convert.need_key"));
        data = rtonDecrypt(data, key);
    }
    Decoder dec(data);
    Value root;
    try { root = dec.decode(); }
    catch (const std::exception& e) {
        if (encrypted) throw std::runtime_error(i18n::translate("convert.decrypt_failed", {e.what()}));
        throw;
    }
    std::string j = writeJson(root) + "\n";
    common::writeAll(out, j.data(), j.size());
}

void jsonToRtonFile(const std::string& in, const std::string& out, const std::string& key, bool hasKey) {
    common::Bytes raw = common::readAll(in);
    Value root = parseJson(std::string(raw.begin(), raw.end()));
    Encoder enc;
    common::Bytes b = enc.encode(root);
    if (hasKey) b = rtonEncrypt(b, key);
    common::writeAll(out, b.data(), b.size());
}

}  // namespace rton
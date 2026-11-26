/**
 * PCP ToolKit
 * Copyright (C) 2026 VacancyoTP
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <https://www.gnu.org/licenses/>.
 *
 * @file Converter.cpp
 * @brief RTON 与 JSON 格式的转换实现。
 *
 * 该代码文件实现单个文件的 RTON 与 JSON 互转，并负责批量扫描输入目录、生成输出路径和报告转换进度。
 */
#include "rton/Converter.h"

#include <algorithm>
#include <cctype>
#include <filesystem>
#include <stdexcept>
#include <system_error>

#include "rton/Json.h"
#include "rton/Crypto.h"
#include "rton/Decoder.h"
#include "rton/Encoder.h"
#include "common/FileIO.h"
#include "common/I18n.h"

namespace pcp::rton {

namespace fs = std::filesystem;

void rtonToJson(const std::string& in, const std::string& out, const std::string& key, bool hasKey) {
    pcp::common::Bytes data = pcp::common::readAll(in);
    bool encrypted = isEncryptedRton(data);
    if (encrypted) {
        if (!hasKey) throw std::runtime_error(pcp::i18n::translate("convert.need_key"));
        data = rtonDecrypt(data, key);
    }
    Decoder dec(data);
    Value root;
    try { root = dec.decode(); }
    catch (const std::exception& e) {
        if (encrypted) throw std::runtime_error(pcp::i18n::translate("convert.decrypt_failed", {e.what()}));
        throw;
    }
    std::string j = writeJson(root) + "\n";
    pcp::common::writeAll(out, j.data(), j.size());
}

void jsonToRton(const std::string& in, const std::string& out, const std::string& key, bool hasKey) {
    pcp::common::Bytes raw = pcp::common::readAll(in);
    Value root = parseJson(std::string(raw.begin(), raw.end()));
    Encoder enc;
    pcp::common::Bytes b = enc.encode(root);
    if (hasKey) b = rtonEncrypt(b, key);
    pcp::common::writeAll(out, b.data(), b.size());
}

namespace {

/**
 * @brief 有关一个待转换的文件的内部任务。
 */
struct Task {
    fs::path in;   ///< 输入文件
    fs::path out;  ///< 输出文件
    char mode;     ///< 解码模式
};

/**
 * @brief 判断扩展名。
 */
bool hasExt(const fs::path& p, const std::string& ext) {
    std::string s = p.extension().string();
    for (auto& c : s) c = static_cast<char>(std::tolower(static_cast<unsigned char>(c)));
    return s == ext;
}

/**
 * @brief 由扩展名与强制模式决定方向。返回 'd'、'e'，无法判定返回 0。
 */
char decideMode(const fs::path& p, char mode) {
    if (hasExt(p, ".rton") && mode != 'e') return 'd';
    if (hasExt(p, ".json") && mode != 'd') return 'e';
    return 0;
}

/**
 * @brief 计算输出路径，并更改扩展名。
 * @param base 输入所在的根目录。单个文件传空路径。
 */
fs::path makeOutPath(const fs::path& in, const fs::path& base, const ConvertOptions& opt, char mode) {
    fs::path target;
    if (opt.outDir.empty()) target = in;
    else target = base.empty() ? fs::path(opt.outDir) / in.filename() : fs::path(opt.outDir) / fs::relative(in, base);
    target.replace_extension(mode == 'd' ? ".json" : ".RTON");
    return target;
}

/**
 * @brief 收集任务：展开目录、判断方向、生成输出路径；无效输入记入 invalid。
 */
void collect(const ConvertOptions& opt, std::vector<Task>& tasks, std::vector<TaskResult>& invalid) {
    for (const std::string& s : opt.inputs) {
        fs::path input(s);
        std::error_code ec;
        if (fs::is_directory(input, ec)) {
            std::vector<fs::path> files;
            if (opt.recursive) {
                for (auto& e : fs::recursive_directory_iterator(input, ec)) if (e.is_regular_file()) files.push_back(e.path());
            } else {
                for (auto& e : fs::directory_iterator(input, ec)) if (e.is_regular_file()) files.push_back(e.path());
            }
            std::sort(files.begin(), files.end());
            for (auto& f : files) {
                char m = decideMode(f, opt.mode);
                if (m) tasks.push_back({f, makeOutPath(f, input, opt, m), m});
            }
        } else if (fs::is_regular_file(input, ec)) {
            char m = opt.mode ? opt.mode : decideMode(input, 0);
            if (!m) {
                TaskResult r; r.in = input.u8string();
                r.message = pcp::i18n::translate("batch.skip_unknown_ext", {input.u8string()});
                invalid.push_back(r);
                continue;
            }
            tasks.push_back({input, makeOutPath(input, fs::path(), opt, m), m});
        } else {
            TaskResult r; r.in = input.u8string();
            r.message = pcp::i18n::translate("batch.path_not_found", {input.u8string()});
            invalid.push_back(r);
        }
    }
}

}  // namespace

std::vector<TaskResult> RtonConverter::run(const ProgressFn& onProgress, const std::atomic<bool>* cancel) const {
    std::vector<Task> tasks;
    std::vector<TaskResult> invalid, results;
    collect(opt_, tasks, invalid);

    const int total = static_cast<int>(tasks.size() + invalid.size());
    int done = 0;
    auto report = [&](const TaskResult& r) {
        results.push_back(r);
        if (onProgress) onProgress(++done, total, r);
    };

    for (const TaskResult& r : invalid) report(r);

    const bool hasKey = !opt_.key.empty();
    for (const Task& t : tasks) {
        if (cancel && cancel->load()) break;
        TaskResult r; r.in = t.in.u8string(); r.out = t.out.u8string();
        try {
            if (t.out.has_parent_path()) fs::create_directories(t.out.parent_path());
            if (t.mode == 'd') rtonToJson(t.in.string(), t.out.string(), opt_.key, hasKey);
            else jsonToRton(t.in.string(), t.out.string(), opt_.key, hasKey);
            r.ok = true;
            r.encrypted = (t.mode == 'e' && hasKey);
        } catch (const std::exception& e) { r.message = e.what(); }
        report(r);
    }
    return results;
}

}  // namespace pcp::rton

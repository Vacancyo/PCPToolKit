#include "rton/RtonConverter.h"
#include "rton/Converter.h"
#include "common/I18n.h"

#include <algorithm>
#include <cctype>
#include <filesystem>
#include <system_error>

namespace fs = std::filesystem;

namespace rton {

namespace {

/** @brief  有关一个待转换的文件的内部任务。 */
struct Task {
    fs::path in;   ///< 输入文件
    fs::path out;  ///< 输出文件
    char mode;     ///< 解码模式
};

/** @brief  判断扩展名。 */
bool hasExt(const fs::path& p, const std::string& ext) {
    std::string s = p.extension().string();
    for (auto& c : s) c = static_cast<char>(std::tolower(static_cast<unsigned char>(c)));
    return s == ext;
}

/** @brief  由扩展名与强制模式决定方向。返回 'd'、'e'，无法判定返回 0。 */
char decideMode(const fs::path& p, char mode) {
    if (hasExt(p, ".rton") && mode != 'e') return 'd';
    if (hasExt(p, ".json") && mode != 'd') return 'e';
    return 0;
}

/**
 *  @brief  计算输出路径，并更改扩展名。
 *  @param  base  输入所在的根目录。单个文件传空路径。
 */
fs::path makeOutPath(const fs::path& in, const fs::path& base, const ConvertOptions& opt, char mode) {
    fs::path target;
    if (opt.outDir.empty()) target = in;
    else target = base.empty() ? fs::path(opt.outDir) / in.filename() : fs::path(opt.outDir) / fs::relative(in, base);
    target.replace_extension(mode == 'd' ? ".json" : ".RTON");
    return target;
}

/** @brief  收集任务：展开目录、判断方向、生成输出路径；无效输入记入 invalid。 */
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
                r.message = i18n::translate("batch.skip_unknown_ext", {input.u8string()});
                invalid.push_back(r);
                continue;
            }
            tasks.push_back({input, makeOutPath(input, fs::path(), opt, m), m});
        } else {
            TaskResult r; r.in = input.u8string();
            r.message = i18n::translate("batch.path_not_found", {input.u8string()});
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
            if (t.mode == 'd') rtonToJsonFile(t.in.string(), t.out.string(), opt_.key, hasKey);
            else jsonToRtonFile(t.in.string(), t.out.string(), opt_.key, hasKey);
            r.ok = true;
            r.encrypted = (t.mode == 'e' && hasKey);
        } catch (const std::exception& e) { r.message = e.what(); }
        report(r);
    }
    return results;
}

}  // namespace rton
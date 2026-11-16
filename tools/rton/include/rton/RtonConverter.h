/** @file RtonConverter.h
 *  RTON 与 JSON 转换的对外接口。
 */
#pragma once

#include <atomic>
#include <functional>
#include <string>
#include <vector>

namespace rton {

/** @brief  转换参数。 */
struct ConvertOptions {
    std::vector<std::string> inputs;  ///< 输入的文件或目录。若为目录即视为批量处理。目录内按扩展名挑选文件
    std::string outDir;               ///< 输出目录。为空则输出在原文件旁边
    bool recursive = false;           ///< 输入是目录时，是否递归处理子目录
    char mode = 0;                    ///< 'd' 只做 RTON 转 JSON；'e' 只做 JSON 转 RTON；0 按扩展名自动判断
    std::string key;                  ///< RTONKey。编码时非空则输出加密 RTON；解码加密 RTON 时必需
};

/** @brief  单个文件的处理结果。 */
struct TaskResult {
    std::string in;          ///< 输入路径
    std::string out;         ///< 输出路径。无效输入时为空
    bool ok = false;         ///< 是否成功
    bool encrypted = false;  ///< 成功且输出的是加密 RTON
    std::string message;     ///< 失败原因。成功时为空
};

/**
 *  @brief  进度回调。
 *  @param  done  已处理个数。
 *  @param  total  总个数。
 *  @param  result  本次结果。
 */
using ProgressFn = std::function<void(int done, int total, const TaskResult& result)>;

/** @brief  转换器。 */
class RtonConverter {
public:
    /**
     *  @brief  构造转换器。
     *  @param  opt  转换参数。
     */
    explicit RtonConverter(ConvertOptions opt) : opt_(std::move(opt)) {}

    /**
     *  @brief   执行转换。
     *  @param   onProgress  可选。每处理完一个文件（或发现一个无效输入）调用一次。
     *  @param   cancel  可选。被置为 true 后,在处理下一个文件之前停止。
     *  @return  所有已处理项的结果。顺序与处理顺序一致。取消时只包含已处理的部分。
     */
    std::vector<TaskResult> run(const ProgressFn& onProgress = nullptr,
                                const std::atomic<bool>* cancel = nullptr) const;

private:
    ConvertOptions opt_;  ///< 转换参数
};

}  // namespace rton
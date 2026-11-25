/** @file Reader.h
 *  小端二进制读取器。
 */
#pragma once

#include <string>

#include "common/Bytes.h"

namespace rton {

using common::Bytes;

/**
 *  @brief  二进制读取器。所有读取越界时抛出运行时错误。
 * 
 *  RTON 是二进制文件，内容是一长串字节，例如 90 01 61 24 05 ...。读取步骤为：从当前位置读
 *  1 个、2 个、4 个或 8 个字节以拼成整数；读变长整数 VarInt；读指定长度的字符串，每次读完，
 *  位置自动往后移；读到文件末尾报错，不能越界访问。Reader 则封装了这些操作。
 */
class Reader {
public:
    /**
     *  @brief  构造读取器。
     *  @param  b  字节数据。
     */
    explicit Reader(const Bytes& b) : d_(b) {}

    /** @brief  当前读取位置。 */
    size_t pos() const { return pos_; }

    /** @brief  回退 1 字节。用于报错时定位到出错的类型字节。 */
    void back() { pos_--; }

    /**
     *  @brief  抛出 RTON 错误。
     *  @param  key  消息键名。
     *  @param  args  消息文本中占位符参数。
     */
    [[noreturn]] void fail(const std::string& key, const std::vector<std::string>& args = {}) const;

    std::uint8_t u8();    ///< 读 1 字节无符号整数
    std::uint16_t u16();  ///< 读 2 字节小端无符号整数
    std::uint32_t u32();  ///< 读 4 字节小端无符号整数
    std::uint64_t u64();  ///< 读 8 字节小端无符号整数
    float f32();          ///< 读 4 字节 IEEE754 单精度浮点数
    double f64();         ///< 读 8 字节 IEEE754 双精度浮点数

    /** @brief  读 VarInt32，采用 LEB128 格式，最多 5 字节，返回无符号结果。 */
    std::uint32_t uvar32();

    /** @brief  读 VarInt64，采用 LEB128 格式，最多 10 字节，返回无符号结果。 */
    std::uint64_t uvar64();

    /**
     *  @brief   读取 n 个原始字节。
     *  @param   n  字节数。
     *  @return  读到的字节串。
     */
    std::string bytes(size_t n);

    /** @brief  读 "VarInt32 长度 + 内容" 形式的字符串。 */
    std::string strHead();

    /**
     *  @brief  读 1 字节并校验其值。
     *  @param  x  期望值。
     */
    void expect8(std::uint8_t x);

private:
    const Bytes& d_;  ///< 被读取的数据
    size_t pos_ = 0;  ///< 当前读取位置
};

}  // namespace rton
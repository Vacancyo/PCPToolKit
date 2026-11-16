/** @file Decoder.h
 *  RTON 解码器。将二进制 RTON 转换为 Value 树。
 */
#pragma once

#include "rton/Reader.h"
#include "rton/Value.h"
#include "common/Bytes.h"

#include <string>
#include <vector>

using common::Bytes;

namespace rton {

/**
 *  @brief  RTON 解码器。
 * 
 *  0x90/0x92 首次出现的字符串会加入对应字符串池，0x91/0x93 通过下标引用池中字符串。
 */
class Decoder {
public:
    /**
     *  @brief  构造解码器。
     *  @param  b  完整的 RTON 数据。
     */
    explicit Decoder(const Bytes& b) : r_(b) {}

    /**
     *  @brief   解码整个文件:魔数、版本、根对象、"DONE"。
     *  @return  根对象。
     */
    Value decode();

private:
    Reader r_;                         ///< 二进制读取器
    std::vector<std::string> pool90_;  ///< ASCII 字符串池(0x90/0x91)
    std::vector<std::string> pool92_;  ///< UTF-8 字符串池(0x92/0x93)

    /** @brief  读 0x87 二进制类型，格式化为 $BINARY("字符串", 整数)。 */
    std::string readBinary();

    /** @brief  读 0x83 RTID 类型，格式化为 RTID(...)。 */
    std::string readRTID();

    /**
     * @brief   尝试把类型字节视为字符串类类型读取。
     * @param   t  类型字节。
     * @param   out  读到的字符串。
     * @return  是字符串类返回 true；否则返回 false。
     */
    bool readStringLike(std::uint8_t t, std::string& out);

    /** @brief  按类型字节读取一个值。 */
    Value readValue(std::uint8_t t);

    /** @brief  读对象内容（不含0x85），直到 0xFF。 */
    Value readObject();

    /** @brief  读数组内容（不含0x86），包括 0xFD、个数、各元素、0xFE。 */
    Value readArray();
};

}  // namespace rton
/** @file Encoder.h
 *  RTON 编码器。将 Value 树转换为二进制 RTON。
 */
#pragma once

#include <string>
#include <unordered_map>

#include "rton/Value.h"
#include "common/Bytes.h"

using common::Bytes;

namespace rton {

/**
 *  @brief  RTON 编码器。
 * 
 *  ASCII 字符串走 0x90/0x91 池，含非 ASCII 的字符串走 0x92/0x93 池。键与值共用同一组池。
 *  由于内部有字符串池状态，每个 Encoder 对象只应编码一次。
 */
class Encoder {
public:
    /**
     *  @brief   编码整个文件。
     *  @param   root  对象类型的根值。
     *  @return  完整的未加密 RTON 字节流。
     */
    Bytes encode(const Value& root);

private:
    Bytes o_;                                                   ///< 输出缓冲区
    std::unordered_map<std::string, std::uint32_t> p90_, p92_;  ///< 字符串到池下标的映射

    void u8(std::uint8_t x);             ///< 写 1 字节
    void u32(std::uint32_t v);           ///< 写 4 字节小端整数
    void u64(std::uint64_t v);           ///< 写 8 字节小端整数
    void uvar(std::uint64_t v);          ///< 写无符号 VarInt
    void var32(std::int32_t v);          ///< 写有符号 32 位 VarInt
    void raw(const std::string& s);      ///< 写原始字节串
    void strHead(const std::string& s);  ///< 写 VarInt32 字节长度与内容

    /** @brief  若形如 $BINARY("xxx", n) 则按 0x87 写入。@return 已写入返回 true,否则 false(未写入)。 */
    bool writeBinary(const std::string& s);

    /**
     *  @brief   若形如 RTID(...) 则按 0x83/0x84 写入。
     *  @return  已写入返回 true,否则 false(未写入)。
     *  
     *  RTID(0)->0x84；RTID(a.b.8位十六进制@xxx)->0x83 0x02(xxx 为空用 0x01)；其余 RTID(s1@s2)->0x83 0x03。
     */
    bool writeRTID(const std::string& s);

    /** @brief  写字符串："*"、RTID、$BINARY、ASCII 池、UTF-8 池，依次尝试。 */
    void writeString(const std::string& s);

    /**
     *  @brief  写数字并选择最合适的类型。
     *  浮点：0->0x23；可无损存 float->0x22；否则 0x42。
     *  整数：0->0x21；正数 int32 内->0x24，更大->0x44；负数>=-2^30->0x25，更小->0x45；超出 int64 的正数->0x46。
     */
    void writeNumber(const std::string& t);

    /** @brief  写任意值。JSON null 写为 0x84。 */
    void writeValue(const Value& v);

    /** @brief  写对象内容（不含 0x85），直到 0xFF。 */
    void writeObject(const Value& v);

    /** @brief  写数组内容（不含 0x86），包括 0xFD、个数、各元素、0xFE。 */
    void writeArray(const Value& v);
};

}  // namespace rton
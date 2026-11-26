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
 * @file TemplateMatcher.h
 * @brief 图像模板匹配接口。
 *
 * 该代码文件声明 RGBA 图像、模板采样、匹配结果以及支持缩放和区域限制的模板匹配器。
 */
#pragma once

#include <cstdint>
#include <optional>
#include <vector>

namespace pcp::script {

/**
 * @brief RGBA 像素图像，每个像素按 0xAARRGGBB 存储。
 */
struct RgbaImage {

    /**
     * @brief 构造 RGBA 图像。
     * @param width 图像宽度。
     * @param height 图像高度。
     * @param pixels 像素数据。
     */
    RgbaImage(int width, int height, std::vector<std::uint32_t> pixels)
        : width(width), height(height), pixels(std::move(pixels)) {}

    /**
     * @brief 读取指定位置的像素。
     * @param x 横坐标。
     * @param y 纵坐标。
     * @return 该位置的 RGBA 像素。
     */
    std::uint32_t at(int x, int y) const {
        return pixels[static_cast<size_t>(y) * static_cast<size_t>(width) + static_cast<size_t>(x)];
    }

    int width;
    int height;
    std::vector<std::uint32_t> pixels;
};

/**
 * @brief 以像素为单位表示的矩形区域。
 */
struct PixelRect {
    int left;
    int top;
    int width;
    int height;
};

/**
 * @brief 模板匹配命中结果，包含位置、尺寸和评分。
 */
struct PixelMatch {
    int left;
    int top;
    int width;
    int height;
    double score;
};

/**
 * @brief 执行带采样和区域限制的灰度模板匹配。
 */
class TemplateMatcher {
private:

    /**
     * @brief 模板中的一个灰度采样点。
     */
    struct Sample {
        int x;
        int y;
        double gray;
    };

public:

    /**
     * @brief 预处理后的模板图像。
     */
    class Template {
    public:

        /**
         * @brief 获取模板宽度。
         * @return 模板宽度。
         */
        int width() const { return width_; }

        /**
         * @brief 获取模板高度。
         * @return 模板高度。
         */
        int height() const { return height_; }

    private:
        friend class TemplateMatcher;

        /**
         * @brief 构造预处理后的模板图像。
         * @param width 模板宽度。
         * @param height 模板高度。
         * @param samples 模板采样点。
         * @param sum 采样灰度值之和。
         * @param sumSquares 采样灰度值平方和。
         */
        Template(
            int width,
            int height,
            std::vector<Sample> samples,
            double sum,
            double sumSquares
        )
            : width_(width),
              height_(height),
              samples_(std::move(samples)),
              sum_(sum),
              sumSquares_(sumSquares) {}

        int width_;
        int height_;
        std::vector<Sample> samples_;
        double sum_;
        double sumSquares_;
    };

    /**
     * @brief 构造模板匹配器。
     * @param maxSamples 模板最多保留的采样点数量。
     * @param maxCandidates 粗搜索最多评估的候选位置数量。
     */
    explicit TemplateMatcher(
        size_t maxSamples = 2048,
        size_t maxCandidates = 60'000
    )
        : maxSamples_(maxSamples), maxCandidates_(maxCandidates) {}

    /**
     * @brief 校验模板图像并生成采样数据。
     * @param image 模板图像。
     * @return 可供匹配使用的预处理模板。
     */
    Template prepare(const RgbaImage& image) const;

    /**
     * @brief 在屏幕图像中查找模板。
     * @param screen 待搜索的屏幕图像。
     * @param image 预处理后的模板图像。
     * @param region 可选的像素区域限制。
     * @param threshold 命中所需的最低评分。
     * @return 命中时返回匹配结果，否则返回空。
     */
    std::optional<PixelMatch> find(
        const RgbaImage& screen,
        const Template& image,
        const PixelRect* region = nullptr,
        double threshold = 0.82
    ) const;

private:

    /**
     * @brief 计算模板在指定屏幕位置的归一化匹配评分。
     * @param screenGray 屏幕灰度图。
     * @param grayWidth 灰度图宽度。
     * @param image 预处理后的模板图像。
     * @param left 候选位置左侧坐标。
     * @param top 候选位置顶部坐标。
     * @return 匹配评分。
     */
    double scoreAt(
        const std::vector<int>& screenGray,
        int grayWidth,
        const Template& image,
        int left,
        int top
    ) const;

    /**
     * @brief 把 RGBA 像素转换为灰度值。
     * @param pixel RGBA 像素。
     * @return 灰度值。
     */
    static int gray(std::uint32_t pixel);

    size_t maxSamples_;
    size_t maxCandidates_;
};

}  // namespace pcp::script

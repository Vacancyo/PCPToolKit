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
 * @file TemplateMatcher.cpp
 * @brief 图像模板匹配实现。
 *
 * 该代码文件实现模板透明像素采样、粗粒度搜索、局部细化以及灰度归一化匹配评分。
 */
#include "script/TemplateMatcher.h"

#include <algorithm>
#include <cmath>
#include <stdexcept>

namespace pcp::script {
namespace {

constexpr int kAlphaThreshold = 32;

}  // namespace

TemplateMatcher::Template TemplateMatcher::prepare(const RgbaImage& image) const {
    if (image.width <= 0 || image.height <= 0 ||
        image.pixels.size() < static_cast<size_t>(image.width) * image.height) {
        throw std::invalid_argument("Invalid template image");
    }

    size_t opaque = 0;
    for (std::uint32_t pixel : image.pixels) {
        if (((pixel >> 24) & 0xFFu) >= kAlphaThreshold) ++opaque;
    }
    if (opaque == 0) throw std::invalid_argument("Template has no opaque pixels");

    const size_t limit = std::max<size_t>(1, maxSamples_);
    const int step = std::max(
        1,
        static_cast<int>(std::ceil(std::sqrt(static_cast<double>(opaque) / limit)))
    );
    std::vector<Sample> samples;
    samples.reserve(std::min(opaque, limit));
    for (int y = 0; y < image.height; y += step) {
        for (int x = 0; x < image.width; x += step) {
            const std::uint32_t pixel = image.at(x, y);
            if (((pixel >> 24) & 0xFFu) >= kAlphaThreshold) {
                samples.push_back(Sample{x, y, static_cast<double>(gray(pixel))});
            }
        }
    }

    double sum = 0.0;
    double sumSquares = 0.0;
    for (const Sample& sample : samples) {
        sum += sample.gray;
        sumSquares += sample.gray * sample.gray;
    }
    return Template(image.width, image.height, std::move(samples), sum, sumSquares);
}

std::optional<PixelMatch> TemplateMatcher::find(
    const RgbaImage& screen,
    const Template& image,
    const PixelRect* region,
    double threshold
) const {
    const PixelRect area = region ? *region : PixelRect{0, 0, screen.width, screen.height};
    const int left = std::clamp(area.left, 0, screen.width);
    const int top = std::clamp(area.top, 0, screen.height);
    const int right = std::clamp(area.left + area.width, 0, screen.width);
    const int bottom = std::clamp(area.top + area.height, 0, screen.height);
    const int availableWidth = right - left;
    const int availableHeight = bottom - top;
    if (image.width() > availableWidth || image.height() > availableHeight) return std::nullopt;

    std::vector<int> screenGray(
        static_cast<size_t>(availableWidth) * availableHeight
    );
    for (int y = 0; y < availableHeight; ++y) {
        const size_t sourceOffset =
            static_cast<size_t>(top + y) * screen.width + left;
        const size_t targetOffset = static_cast<size_t>(y) * availableWidth;
        for (int x = 0; x < availableWidth; ++x) {
            screenGray[targetOffset + x] = gray(screen.pixels[sourceOffset + x]);
        }
    }

    const int positionsX = availableWidth - image.width() + 1;
    const int positionsY = availableHeight - image.height() + 1;
    const double candidates = static_cast<double>(positionsX) * positionsY;
    const size_t candidateLimit = std::max<size_t>(1, maxCandidates_);
    const int coarseStep = std::max(
        1,
        static_cast<int>(std::ceil(std::sqrt(candidates / candidateLimit)))
    );

    int bestX = 0;
    int bestY = 0;
    double bestScore = -1.0;
    for (int y = 0; y <= availableHeight - image.height(); y += coarseStep) {
        for (int x = 0; x <= availableWidth - image.width(); x += coarseStep) {
            const double score = scoreAt(screenGray, availableWidth, image, x, y);
            if (score > bestScore) {
                bestScore = score;
                bestX = x;
                bestY = y;
            }
        }
    }

    if (coarseStep > 1) {
        const int refineLeft = std::max(0, bestX - coarseStep);
        const int refineTop = std::max(0, bestY - coarseStep);
        const int refineRight = std::min(availableWidth - image.width(), bestX + coarseStep);
        const int refineBottom = std::min(availableHeight - image.height(), bestY + coarseStep);
        for (int y = refineTop; y <= refineBottom; ++y) {
            for (int x = refineLeft; x <= refineRight; ++x) {
                const double score = scoreAt(screenGray, availableWidth, image, x, y);
                if (score > bestScore) {
                    bestScore = score;
                    bestX = x;
                    bestY = y;
                }
            }
        }
    }

    if (bestScore < threshold) return std::nullopt;
    return PixelMatch{
        left + bestX,
        top + bestY,
        image.width(),
        image.height(),
        bestScore
    };
}

double TemplateMatcher::scoreAt(
    const std::vector<int>& screenGray,
    int grayWidth,
    const Template& image,
    int left,
    int top
) const {
    double sum = 0.0;
    double sumSquares = 0.0;
    double cross = 0.0;
    for (const Sample& sample : image.samples_) {
        const double value = screenGray[
            static_cast<size_t>(top + sample.y) * grayWidth + left + sample.x
        ];
        sum += value;
        sumSquares += value * value;
        cross += value * sample.gray;
    }
    const double count = static_cast<double>(image.samples_.size());
    const double numerator = count * cross - sum * image.sum_;
    const double denominator = std::sqrt(
        (count * sumSquares - sum * sum) *
        (count * image.sumSquares_ - image.sum_ * image.sum_)
    );
    return denominator < 1e-9 ? 0.0 : numerator / denominator;
}

int TemplateMatcher::gray(std::uint32_t pixel) {
    const int red = static_cast<int>((pixel >> 16) & 0xFFu);
    const int green = static_cast<int>((pixel >> 8) & 0xFFu);
    const int blue = static_cast<int>(pixel & 0xFFu);
    return (red * 299 + green * 587 + blue * 114) / 1000;
}

}  // namespace pcp::script

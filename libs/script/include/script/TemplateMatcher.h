#pragma once

#include <cstdint>
#include <optional>
#include <vector>

namespace pcp::script {

struct RgbaImage {
    RgbaImage(int width, int height, std::vector<std::uint32_t> pixels)
        : width(width), height(height), pixels(std::move(pixels)) {}

    std::uint32_t at(int x, int y) const {
        return pixels[static_cast<size_t>(y) * static_cast<size_t>(width) + static_cast<size_t>(x)];
    }

    int width;
    int height;
    std::vector<std::uint32_t> pixels;
};

struct PixelRect {
    int left;
    int top;
    int width;
    int height;
};

struct PixelMatch {
    int left;
    int top;
    int width;
    int height;
    double score;
};

class TemplateMatcher {
private:
    struct Sample {
        int x;
        int y;
        double gray;
    };

public:
    class Template {
    public:
        int width() const { return width_; }
        int height() const { return height_; }

    private:
        friend class TemplateMatcher;
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

    explicit TemplateMatcher(
        size_t maxSamples = 2048,
        size_t maxCandidates = 60'000
    )
        : maxSamples_(maxSamples), maxCandidates_(maxCandidates) {}

    Template prepare(const RgbaImage& image) const;
    std::optional<PixelMatch> find(
        const RgbaImage& screen,
        const Template& image,
        const PixelRect* region = nullptr,
        double threshold = 0.82
    ) const;

private:
    double scoreAt(
        const std::vector<int>& screenGray,
        int grayWidth,
        const Template& image,
        int left,
        int top
    ) const;
    static int gray(std::uint32_t pixel);

    size_t maxSamples_;
    size_t maxCandidates_;
};

}  // namespace pcp::script

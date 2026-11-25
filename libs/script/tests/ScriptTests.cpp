#include "script/Parser.h"
#include "script/Runtime.h"
#include "script/TemplateMatcher.h"
#include "script/Error.h"

#include <cmath>
#include <cstdint>
#include <fstream>
#include <iostream>
#include <string>
#include <vector>

namespace {

int gFailures = 0;

void check(bool condition, const char* expression, int line) {
    if (condition) return;
    std::cerr << "line " << line << ": CHECK failed: " << expression << "\n";
    ++gFailures;
}

void checkNear(double actual, double expected, double tolerance, int line) {
    if (std::abs(actual - expected) <= tolerance) return;
    std::cerr << "line " << line << ": expected " << expected << ", got " << actual << "\n";
    ++gFailures;
}

#define CHECK(expression) check((expression), #expression, __LINE__)
#define CHECK_NEAR(actual, expected, tolerance) \
    checkNear((actual), (expected), (tolerance), __LINE__)

using pcp::script::MatchResult;
using pcp::script::NormalizedRect;

class FakeHost final : public pcp::script::Host {
public:
    explicit FakeHost(MatchResult result) : result_(result) {}

    bool cancelled() const override { return cancelled_; }
    int64_t nowMillis() const override { return now_; }
    void log(std::string_view message) override { logs.emplace_back(message); }
    void sleep(int64_t milliseconds) override {
        sleeps.push_back(milliseconds);
        now_ += milliseconds;
    }
    void tap(double x, double y) override { taps.emplace_back(x, y); }
    void swipe(double x1, double y1, double x2, double y2, int64_t durationMillis) override {
        swipes.push_back(Swipe{x1, y1, x2, y2, durationMillis});
    }
    MatchResult match(std::string_view assetPath, const NormalizedRect&) override {
        matchedPaths.emplace_back(assetPath);
        return result_;
    }

    struct Swipe {
        double x1;
        double y1;
        double x2;
        double y2;
        int64_t duration;
    };

    bool cancelled_ = false;
    int64_t now_ = 0;
    MatchResult result_;
    std::vector<std::string> logs;
    std::vector<int64_t> sleeps;
    std::vector<std::pair<double, double>> taps;
    std::vector<Swipe> swipes;
    std::vector<std::string> matchedPaths;
};

void testParser() {
    const std::string source = R"(
let $count = 0
let $items = ["A", "B"]
for $item in $items:
    let $count = $count + 1
    if $count == 1:
        print "first " + $item
    elif $count > 1:
        continue
    else:
        break
while $count < 3:
    let $count = $count + 1
exist rect &MAIN.DAILY 0 0 1 1
if $FOUND:
    tap $MATCH_CX $MATCH_CY
match coord &MAIN.DAILY 0 0 1 1 500
swipe 0.1 0.2 0.3 0.4 300
wait 100
stop
)";
    const auto program = pcp::script::parseScript(source);
    CHECK(program.statements.size() == 10);
    CHECK(program.statements[2]->kind() == pcp::script::StatementKind::For);
    CHECK(program.statements[3]->kind() == pcp::script::StatementKind::While);
    CHECK(program.statements[4]->kind() == pcp::script::StatementKind::Vision);
    CHECK(program.statements[5]->kind() == pcp::script::StatementKind::If);
    CHECK(program.statements[6]->kind() == pcp::script::StatementKind::Vision);
    CHECK(program.statements[7]->kind() == pcp::script::StatementKind::Swipe);
    CHECK(program.statements[8]->kind() == pcp::script::StatementKind::Wait);
    CHECK(program.statements[9]->kind() == pcp::script::StatementKind::Stop);

    bool failed = false;
    try {
        pcp::script::parseScript("if true:\n    print \"a\"\n  print \"b\"\n");
    } catch (const pcp::script::ScriptParseError&) {
        failed = true;
    }
    CHECK(failed);
}

void testRuntime() {
    FakeHost host(MatchResult{true, 0.25, 0.5, 0.1, 0.05, 0.94});
    const std::string source = R"(
let $count = 0
let $items = ["A", "B"]
for $item in $items:
    let $count = $count + 1
    if $count == 1:
        print "hit " + $item
    else:
        continue
while $count < 3:
    let $count = $count + 1
exist rect &MAIN.DAILY 0 0 1 1
if $FOUND:
    tap $MATCH_CX $MATCH_CY
match coord &MAIN.DAILY 0 0 1 1 200
swipe 0.1 0.2 0.3 0.4 250
wait 50
print "score=" + $MATCH_SCORE
stop
print "after stop"
)";
    pcp::script::ScriptRuntime(host).execute(pcp::script::parseScript(source));

    CHECK(host.logs.size() == 2);
    CHECK(host.logs[0] == "hit A");
    CHECK(host.logs[1] == "score=0.94");
    CHECK(host.taps.size() == 1);
    CHECK_NEAR(host.taps[0].first, 0.30, 1e-9);
    CHECK_NEAR(host.taps[0].second, 0.525, 1e-9);
    CHECK(host.swipes.size() == 1);
    CHECK(host.swipes[0].duration == 250);
    CHECK(host.sleeps.size() == 1 && host.sleeps[0] == 50);
    CHECK(host.matchedPaths.size() == 2);
    CHECK(host.matchedPaths[0] == "images/main/daily.png");
}

void testMatchTimeout() {
    FakeHost host(MatchResult{false});
    pcp::script::ScriptRuntime(host).execute(
        pcp::script::parseScript(
            "match rect &MAIN.DAILY 0 0 1 1 250\n"
            "print $FOUND\n"
        )
    );
    CHECK(host.sleeps.size() == 3);
    CHECK(host.sleeps[0] == 100);
    CHECK(host.sleeps[1] == 100);
    CHECK(host.sleeps[2] == 50);
    CHECK(host.logs.size() == 1 && host.logs[0] == "false");
}

void testVisionBoundary() {
    FakeHost host(MatchResult{true});
    pcp::script::ScriptRuntime(host).execute(
        pcp::script::parseScript(
            "exist rect &MAIN.DAILY 0.5 0.81 0.18 0.19\n"
            "exist coord &MAIN.DAILY 0.5 0.81 0.68 1.0\n"
        )
    );
    CHECK(host.matchedPaths.size() == 2);

    bool rejected = false;
    try {
        FakeHost invalid(MatchResult{true});
        pcp::script::ScriptRuntime(invalid).execute(
            pcp::script::parseScript(
                "exist rect &MAIN.DAILY 0.5 0.81 0.68 1.0\n"
            )
        );
    } catch (const pcp::script::ScriptRuntimeError& error) {
        rejected = std::string(error.what()).find("rect uses x y width height") !=
                   std::string::npos;
    }
    CHECK(rejected);
}

void testLogicalOperators() {
    FakeHost host(MatchResult{true});
    pcp::script::ScriptRuntime(host).execute(
        pcp::script::parseScript(R"(
let $a = 1
let $b = 3
if ($a == 1) && ($b != 3):
    print "a"
if !($a < 5):
    print "b"
if ($a > $b):
    print "c"
if $a<5:
    print "d"
if ($a == 2) || ($b == 3):
    print "or"
if $b <= 3:
    print "le"
if (($a < 2) && (($b == 3) || ($a >= 9))):
    print "grouped"
)")
    );
    CHECK(host.logs.size() == 4);
    CHECK(host.logs[0] == "d");
    CHECK(host.logs[1] == "or");
    CHECK(host.logs[2] == "le");
    CHECK(host.logs[3] == "grouped");
}

// daily.rgba: little-endian width/height followed by ARGB pixels.
// Regenerate it from apps/android/app/src/main/assets/images/main/daily.png when the asset changes.
pcp::script::RgbaImage loadDaily() {
    std::ifstream input(TEST_DAILY_RAW, std::ios::binary);
    if (!input) throw std::runtime_error("Cannot open TEST_DAILY_RAW");
    std::uint32_t width = 0;
    std::uint32_t height = 0;
    input.read(reinterpret_cast<char*>(&width), sizeof(width));
    input.read(reinterpret_cast<char*>(&height), sizeof(height));
    std::vector<std::uint32_t> pixels(static_cast<size_t>(width) * height);
    input.read(
        reinterpret_cast<char*>(pixels.data()),
        static_cast<std::streamsize>(pixels.size() * sizeof(std::uint32_t))
    );
    return pcp::script::RgbaImage(
        static_cast<int>(width),
        static_cast<int>(height),
        std::move(pixels)
    );
}

void drawOver(std::vector<std::uint32_t>& screen, int screenWidth, int left, int top, const pcp::script::RgbaImage& image) {
    for (int y = 0; y < image.height; ++y) {
        for (int x = 0; x < image.width; ++x) {
            const std::uint32_t src = image.at(x, y);
            const int alpha = static_cast<int>((src >> 24) & 0xFFu);
            if (alpha == 0) continue;
            const int inverse = 255 - alpha;
            const std::uint32_t dst = screen[static_cast<size_t>(top + y) * screenWidth + left + x];
            const int r = (static_cast<int>((src >> 16) & 0xFFu) * alpha +
                           static_cast<int>((dst >> 16) & 0xFFu) * inverse) / 255;
            const int g = (static_cast<int>((src >> 8) & 0xFFu) * alpha +
                           static_cast<int>((dst >> 8) & 0xFFu) * inverse) / 255;
            const int b = (static_cast<int>(src & 0xFFu) * alpha +
                           static_cast<int>(dst & 0xFFu) * inverse) / 255;
            screen[static_cast<size_t>(top + y) * screenWidth + left + x] =
                0xFF000000u | (static_cast<std::uint32_t>(r) << 16) |
                (static_cast<std::uint32_t>(g) << 8) | static_cast<std::uint32_t>(b);
        }
    }
}

void testTemplateMatcher() {
    const auto daily = loadDaily();
    constexpr int screenWidth = 380;
    constexpr int screenHeight = 280;
    std::vector<std::uint32_t> background(
        static_cast<size_t>(screenWidth) * screenHeight,
        0xFF303840u
    );
    drawOver(background, screenWidth, 90, 60, daily);
    const pcp::script::RgbaImage screen(screenWidth, screenHeight, std::move(background));

    pcp::script::TemplateMatcher matcher;
    const auto prepared = matcher.prepare(daily);
    const auto match = matcher.find(screen, prepared, nullptr, 0.9);
    CHECK(match.has_value());
    if (match) {
        CHECK(match->left == 90);
        CHECK(match->top == 60);
        CHECK(match->score >= 0.9);
    }
    const pcp::script::PixelRect roi{80, 50, 200, 180};
    const auto roiMatch = matcher.find(screen, prepared, &roi, 0.9);
    CHECK(roiMatch.has_value());
    if (roiMatch) {
        CHECK(roiMatch->left == 90);
        CHECK(roiMatch->top == 60);
    }

    std::vector<std::uint32_t> blankPixels(
        static_cast<size_t>(screenWidth) * screenHeight,
        0xFF303840u
    );
    const pcp::script::RgbaImage blank(screenWidth, screenHeight, std::move(blankPixels));
    CHECK(!matcher.find(blank, prepared).has_value());
}

}  // namespace

int main() {
    try {
        testParser();
        testRuntime();
        testMatchTimeout();
        testVisionBoundary();
        testLogicalOperators();
        testTemplateMatcher();
    } catch (const std::exception& error) {
        std::cerr << "uncaught exception: " << error.what() << "\n";
        return 2;
    }
    if (gFailures != 0) {
        std::cerr << gFailures << " test(s) failed\n";
        return 1;
    }
    std::cout << "all script tests passed\n";
    return 0;
}

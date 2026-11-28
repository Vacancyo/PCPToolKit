#include "SpeedHook.h"

#include <algorithm>
#include <atomic>
#include <cmath>
#include <cstdint>
#include <dlfcn.h>
#include <mutex>
#include <string>
#include <sys/time.h>
#include <time.h>

#include "Dobby/dobby.h"
#include "Log.h"
#include "xdl.h"

namespace {
constexpr const char *TARGET_LIBRARY = "libSrc.so";
constexpr const char *STEADY_CLOCK_SYMBOL =
        "_ZNSt6__ndk16chrono12steady_clock3nowEv";
constexpr const char *SYSTEM_CLOCK_SYMBOL =
        "_ZNSt6__ndk16chrono12system_clock3nowEv";

using ClockGettime = int (*)(clockid_t, timespec *);
using Gettimeofday = int (*)(timeval *, struct timezone *);
using ChronoNow = int64_t (*)();

std::atomic<double> gSpeed{1.0};
std::atomic<bool> gChronoHooksInstalled{false};
std::mutex gOffsetMutex;
int64_t gMonotonicOffsetNs = 0;
int64_t gRealtimeOffsetNs = 0;
ClockGettime gOriginalClockGettime = nullptr;
Gettimeofday gOriginalGettimeofday = nullptr;
std::atomic<uintptr_t> gGameTextStart{0};
std::atomic<uintptr_t> gGameTextEnd{0};

void cacheGameLibraryRange() {
    if (gGameTextStart.load(std::memory_order_acquire) != 0) return;
    void *handle = xdl_open(TARGET_LIBRARY, XDL_DEFAULT);
    if (handle == nullptr) return;

    xdl_info_t info{};
    if (xdl_info(handle, XDL_DI_DLINFO, &info) != 0 ||
        info.dli_fbase == nullptr || info.dlpi_phdr == nullptr) {
        return;
    }

    uintptr_t base = reinterpret_cast<uintptr_t>(info.dli_fbase);
    uintptr_t start = UINTPTR_MAX;
    uintptr_t end = 0;
    for (size_t i = 0; i < info.dlpi_phnum; i++) {
        const ElfW(Phdr) &phdr = info.dlpi_phdr[i];
        if (phdr.p_type != PT_LOAD || (phdr.p_flags & PF_X) == 0) continue;
        uintptr_t segmentStart = base + phdr.p_vaddr;
        uintptr_t segmentEnd = segmentStart + phdr.p_memsz;
        start = std::min(start, segmentStart);
        end = std::max(end, segmentEnd);
    }
    if (start < end) {
        gGameTextStart.store(start, std::memory_order_release);
        gGameTextEnd.store(end, std::memory_order_release);
    }
}

bool isGameLibraryCaller(void *returnAddress) {
    uintptr_t address = reinterpret_cast<uintptr_t>(returnAddress);
    uintptr_t start = gGameTextStart.load(std::memory_order_acquire);
    uintptr_t end = gGameTextEnd.load(std::memory_order_acquire);
    return start != 0 && address >= start && address < end;
}

int64_t scaleValue(int64_t raw, double speed, int64_t offset) {
    if (std::fabs(speed - 1.0) < 0.0001) {
        return raw + offset;
    }
    return static_cast<int64_t>(
            static_cast<long double>(raw) * speed + static_cast<long double>(offset)
    );
}

int64_t currentRawNanoseconds(clockid_t clockId) {
    timespec value{};
    if (gOriginalClockGettime != nullptr) {
        gOriginalClockGettime(clockId, &value);
    } else {
        clock_gettime(clockId, &value);
    }
    return static_cast<int64_t>(value.tv_sec) * 1000000000LL + value.tv_nsec;
}

int64_t currentMonotonicNanoseconds() {
    return currentRawNanoseconds(CLOCK_MONOTONIC);
}

int64_t currentRealtimeNanoseconds() {
    return currentRawNanoseconds(CLOCK_REALTIME);
}

void scaleTimespec(timespec *value, int64_t offset) {
    if (value == nullptr) return;
    int64_t raw = static_cast<int64_t>(value->tv_sec) * 1000000000LL + value->tv_nsec;
    int64_t scaled = scaleValue(raw, gSpeed.load(std::memory_order_relaxed), offset);
    if (scaled < 0) scaled = 0;
    value->tv_sec = static_cast<time_t>(scaled / 1000000000LL);
    value->tv_nsec = static_cast<long>(scaled % 1000000000LL);
}

int hookedClockGettime(clockid_t clockId, timespec *value) {
    int result = gOriginalClockGettime != nullptr
                 ? gOriginalClockGettime(clockId, value)
                 : clock_gettime(clockId, value);
    if (result != 0 || value == nullptr) {
        return result;
    }
    if (!isGameLibraryCaller(__builtin_return_address(0))) return result;
    if (clockId == CLOCK_MONOTONIC || clockId == CLOCK_MONOTONIC_RAW ||
        clockId == CLOCK_BOOTTIME) {
        scaleTimespec(value, gMonotonicOffsetNs);
    } else if (clockId == CLOCK_REALTIME || clockId == CLOCK_REALTIME_COARSE) {
        scaleTimespec(value, gRealtimeOffsetNs);
    }
    return result;
}

int hookedGettimeofday(timeval *value, struct timezone *timezoneValue) {
    int result = gOriginalGettimeofday != nullptr
                 ? gOriginalGettimeofday(value, timezoneValue)
                 : gettimeofday(value, timezoneValue);
    if (result != 0 || value == nullptr) {
        return result;
    }
    if (!isGameLibraryCaller(__builtin_return_address(0))) {
        return result;
    }
    int64_t raw = static_cast<int64_t>(value->tv_sec) * 1000000000LL +
                  static_cast<int64_t>(value->tv_usec) * 1000LL;
    int64_t scaled = scaleValue(
            raw,
            gSpeed.load(std::memory_order_relaxed),
            gRealtimeOffsetNs
    );
    if (scaled < 0) scaled = 0;
    value->tv_sec = static_cast<time_t>(scaled / 1000000000LL);
    value->tv_usec = static_cast<suseconds_t>((scaled % 1000000000LL) / 1000LL);
    return result;
}

int64_t hookedSteadyClockNow() {
    return scaleValue(
            currentMonotonicNanoseconds(),
            gSpeed.load(std::memory_order_relaxed),
            gMonotonicOffsetNs
    );
}

int64_t hookedSystemClockNow() {
    return scaleValue(
            currentRealtimeNanoseconds(),
            gSpeed.load(std::memory_order_relaxed),
            gRealtimeOffsetNs
    );
}

void hookChronoSymbol(
        void *handle,
        const char *symbol,
        void *replacement,
        void **origin
) {
    void *target = xdl_sym(handle, symbol, nullptr);
    if (target == nullptr) {
        ALOGE("SpeedHook: symbol not found: %s", symbol);
        return;
    }
    int result = DobbyHook(target, replacement, origin);
    if (result == 0) {
        ALOGD("SpeedHook: hooked %s at %p", symbol, target);
    } else {
        ALOGE("SpeedHook: failed to hook %s, result=%d", symbol, result);
    }
}
}

void SpeedHook::init(JNIEnv *env) {
    cacheGameLibraryRange();
    void *libc = xdl_open("libc.so", XDL_DEFAULT);
    if (libc == nullptr) {
        ALOGE("SpeedHook: failed to open libc.so");
        return;
    }
    if (gOriginalClockGettime == nullptr) {
        void *target = xdl_sym(libc, "clock_gettime", nullptr);
        if (target == nullptr) {
            ALOGE("SpeedHook: clock_gettime not found");
        } else if (DobbyHook(target, reinterpret_cast<void *>(hookedClockGettime),
                             reinterpret_cast<void **>(&gOriginalClockGettime)) == 0) {
        } else {
            ALOGE("SpeedHook: failed to hook clock_gettime");
        }
    }
    if (gOriginalGettimeofday == nullptr) {
        void *target = xdl_sym(libc, "gettimeofday", nullptr);
        if (target == nullptr) {
            ALOGE("SpeedHook: gettimeofday not found");
        } else if (DobbyHook(target, reinterpret_cast<void *>(hookedGettimeofday),
                             reinterpret_cast<void **>(&gOriginalGettimeofday)) == 0) {
        } else {
            ALOGE("SpeedHook: failed to hook gettimeofday");
        }
    }
    if (gGameTextStart.load(std::memory_order_acquire) != 0) {
        onLibraryLoaded(TARGET_LIBRARY);
    }
}

void SpeedHook::onLibraryLoaded(const char *name) {
    if (name == nullptr || std::string(name).find("Src") == std::string::npos) {
        return;
    }
    cacheGameLibraryRange();
    bool expected = false;
    if (!gChronoHooksInstalled.compare_exchange_strong(expected, true)) {
        return;
    }
    void *handle = xdl_open(TARGET_LIBRARY, XDL_DEFAULT);
    if (handle == nullptr) {
        gChronoHooksInstalled.store(false);
        ALOGE("SpeedHook: failed to open %s", TARGET_LIBRARY);
        return;
    }
    static ChronoNow originalSteadyClockNow = nullptr;
    static ChronoNow originalSystemClockNow = nullptr;
    hookChronoSymbol(
            handle,
            STEADY_CLOCK_SYMBOL,
            reinterpret_cast<void *>(hookedSteadyClockNow),
            reinterpret_cast<void **>(&originalSteadyClockNow)
    );
    hookChronoSymbol(
            handle,
            SYSTEM_CLOCK_SYMBOL,
            reinterpret_cast<void *>(hookedSystemClockNow),
            reinterpret_cast<void **>(&originalSystemClockNow)
    );
}

void SpeedHook::setSpeed(float speed) {
    if (!std::isfinite(speed)) speed = 1.0f;
    double nextSpeed = std::max(0.05, std::min(10.0, static_cast<double>(speed)));
    cacheGameLibraryRange();
    if (std::fabs(nextSpeed - 1.0) >= 0.0001 &&
        (gOriginalClockGettime == nullptr || gOriginalGettimeofday == nullptr)) {
        init(nullptr);
    }
    std::lock_guard<std::mutex> lock(gOffsetMutex);
    double previousSpeed = gSpeed.load(std::memory_order_relaxed);
    int64_t rawMonotonic = currentMonotonicNanoseconds();
    int64_t rawRealtime = currentRealtimeNanoseconds();
    int64_t currentMonotonic = scaleValue(rawMonotonic, previousSpeed, gMonotonicOffsetNs);
    int64_t currentRealtime = scaleValue(rawRealtime, previousSpeed, gRealtimeOffsetNs);
    gMonotonicOffsetNs = currentMonotonic -
                         static_cast<int64_t>(static_cast<long double>(rawMonotonic) * nextSpeed);
    gRealtimeOffsetNs = currentRealtime -
                        static_cast<int64_t>(static_cast<long double>(rawRealtime) * nextSpeed);
    gSpeed.store(nextSpeed, std::memory_order_relaxed);
}

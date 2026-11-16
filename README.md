# PCP Toolkit (PCP)

[简体中文](README.zh-CN.md) | [English](README.en-US.md)

PCP Toolkit is an early-stage Android companion toolbox for **Plants vs. Zombies 2 China Edition** (PvZ2C). The current app converts PopCap's binary **RTON** format to **JSON** and back.

> PCP is in early development. Its format support, UI, and project structure may change.

## Current status

Implemented:

- RTON to JSON and JSON to RTON conversion
- Batch conversion with automatic direction detection
- Optional `RTONKey` encryption and decryption
- Android 7.0+ app with Simplified Chinese and English UI
- Native C++ conversion core shared with the Android app through JNI

Planned, not implemented:

- Windows desktop app
- Image-recognition game automation
- Visual PvZ2 level editor with one-click import
- Script sharing community
- Full UI redesign

There is no stable release yet. Build the Android app from source.

## Documentation

- [Simplified Chinese README](README.zh-CN.md)
- [English README](README.en-US.md)
- [Repository issues](https://github.com/Vacancyo/PCPToolKit/issues)

## Quick build

Requirements: Android Studio, Android SDK Platform 37, Android NDK, CMake 3.22.1+, and JDK 25. The Gradle toolchain can provision JDK 25 automatically.

```powershell
cd apps/android
.\gradlew.bat :app:assembleDebug
```

The debug APK is written to `apps/android/app/build/outputs/apk/debug/app-debug.apk`.

## Disclaimer

PCP is an unofficial community project. It is not affiliated with or endorsed by PopCap Games, EA, or their partners. Use it only in compliance with applicable laws, game terms, and third-party rights. Back up game files before modifying them. Do not distribute copyrighted game assets, decrypted game data, or encryption keys.

## License

No license has been declared for this repository yet.

# PCP Toolkit (PCP)

[简体中文](README.zh-CN.md) | **English**

> An Android companion toolbox for *Plants vs. Zombies 2 China Edition*

PCP Toolkit (PCP) is an early-stage Android companion toolbox for **Plants vs. Zombies 2 China Edition** (PvZ2C). The current app converts PopCap's binary **RTON** format to **JSON** and back, and includes a basic sandbox for importing and running standalone APKs.

> PCP is in early development. Format support, UI, and project structure may change.

## Current features

- **Two-way conversion**: `RTON → JSON` and `JSON → RTON`.
- **Batch processing**: select multiple files and write results to a chosen folder.
- **Automatic direction detection**: in automatic mode, `.rton` files become `.json`, and `.json` files become `.RTON`.
- **Optional encryption**: provide an `RTONKey` when encoding to produce encrypted RTON; a valid key is required to decode encrypted RTON.
- **Task feedback**: view the current stage, progress, success/failure counts, per-file results, and request cancellation.
- **Bilingual UI**: switch between Simplified Chinese and English inside the app.
- **Native core**: the RTON/JSON converter is implemented in C++ and exposed to the Android app through JNI.
- **Basic sandbox**: import an APK from the system file picker, then install, list, launch, and uninstall it in PCP private storage.

PCP supports Android 7.0 (API 24) and later. Builds include `arm64-v8a` and `x86_64`.

## Known limitations

- There is no stable PCP release yet; the app must be built from source.
- RTON support is still evolving. Some RTON value types are not implemented and return an error.
- Cancellation stops after the current file finishes processing.
- The desktop app, game automation, level editor, and script community are not available yet.
- The sandbox core currently packages ARM64/ARMv7 native libraries, so sandboxed apps cannot run on x86_64 emulators.
- Large APKs are copied into private storage on first import, requiring extra space and potentially several minutes.
- The sandbox uses a general-purpose app container. pvz2's 64-bit native engine, child processes, and SDKs have not been specifically validated, so compatibility must be tested on a physical device.

## Planned features

Everything in this section is planned and is not available in the current release:

- A Windows desktop version of PCP.
- Image-recognition automation for pvz2 running on a phone or desktop emulator, including resource farming and level completion.
- A visual pvz2 level editor, with one-click level import.
- A community for sharing scripts.
- A complete UI redesign.

## How to use

1. Install and open PCP on an Android device.
2. Select one or more `.RTON` or `.json` files.
3. Choose an output folder.
4. Select **Auto**, **RTON → JSON**, or **JSON → RTON**.
5. Enter an `RTONKey` when encryption or decryption is required.
6. Select **Start** and wait for processing to finish.

The app converts cached copies of the selected files and does not modify the source files directly. Back up important game files before working with them.

## Build from source

### Requirements

- Android Studio
- Android SDK Platform 37
- Android NDK
- CMake 3.22.1 or later
- JDK 25

The first build downloads Gradle and Android dependencies, so an internet connection is required. The project configures JDK 25 through the Foojay Toolchain Resolver; Android Studio or Gradle can provision it when the environment allows.

### Build the Android app

Windows:

```powershell
cd apps/android
.\gradlew.bat :app:assembleDebug
```

Linux or macOS:

```bash
cd apps/android
./gradlew :app:assembleDebug
```

The debug APK is written to:

```text
apps/android/app/build/outputs/apk/debug/app-debug.apk
```

Common checks:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

### Build the native core

The root CMake project builds the common library, the RTON conversion library, and the script runtime library. It does not currently produce a desktop executable.

```powershell
cmake -S . -B build/core -DCMAKE_BUILD_TYPE=Release
cmake --build build/core --config Release
```

## Project structure

```text
apps/
  android/          Android app, Compose UI, and JNI bridge
    Bcore/          Apache-2.0 Blacks BlackBox sandbox core
    black-reflection/  Reflection support for the sandbox core
    compiler/       Annotation processor for the sandbox core
libs/
  common/           Shared file I/O and internationalization code
  rton/             RTON/JSON codecs, encryption, and batch conversion
  script/           Script language, runtime, and template matching
CMakeLists.txt      Root CMake project
```

The Android app reads language files directly from `apps/android/app/src/main/assets/lang/`. That directory is the single source of truth for application text.

## Contributing

- Search existing [Issues](https://github.com/Vacancyo/PCPToolKit/issues) before opening a new one.
- For RTON bugs, include reproduction steps, app version, and the shortest relevant error message.
- Keep changes focused, and update both Chinese and English documentation and UI strings.
- Do not submit copyrighted game assets, decrypted game data, or encryption keys.

## Disclaimer

PCP is an unofficial community project. It is not affiliated with or endorsed by PopCap Games, EA, or their partners. *Plants vs. Zombies*, related names, graphics, and game assets belong to their respective rights holders.

Use PCP only in compliance with applicable laws, game terms, and third-party rights. Back up game files before modifying them. Do not distribute copyrighted game assets, decrypted game data, or encryption keys. Conversion results are not guaranteed to work with every game version or use case.

## License

No license has been declared for this repository yet.

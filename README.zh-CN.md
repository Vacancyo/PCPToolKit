# PCP Toolkit（PCP）

**简体中文** | [English](README.en-US.md)

> 面向《植物大战僵尸2》中国版的安卓游戏工具箱

PCP Toolkit（简称 PCP）是一个处于早期开发阶段的安卓游戏辅助/工具箱应用，专注于手机游戏《植物大战僵尸2》中国版（简称 pvz2）。当前版本提供 PopCap Games 使用的二进制序列化格式 **RTON** 与 **JSON** 的互相转换，以及一个用于导入和运行独立 APK 的基础沙箱。

> PCP 仍在早期开发中。格式支持、界面和项目结构可能发生变化。

## 当前功能

- **双向转换**：`RTON → JSON`、`JSON → RTON`。
- **批量处理**：一次选择多个文件，并将结果写入指定文件夹。
- **自动判断方向**：自动模式下，`.rton` 转为 `.json`，`.json` 转为 `.RTON`。
- **可选加密**：编码时填写 `RTONKey` 可输出加密 RTON；解码加密 RTON 时需要提供正确密钥。
- **任务反馈**：显示当前阶段、处理进度、成功/失败统计和逐项结果，并支持取消。
- **双语界面**：内置简体中文与英文，可在应用内切换。
- **原生核心**：RTON/JSON 转换由 C++ 实现，通过 JNI 接入 Android 应用。
- **基础沙箱**：从系统文件选择器导入 APK，在 PCP 私有目录中隔离安装、列出、启动和卸载。

当前支持 Android 7.0（API 24）及以上版本，构建目标包含 `arm64-v8a` 与 `x86_64`。

## 已知限制

- PCP 尚未发布稳定版本，需要从源码构建。
- RTON 格式支持仍在完善，部分 RTON 值类型暂未实现；遇到不支持的数据时会返回错误。
- 取消操作会等待当前文件处理完成后再停止。
- 目前没有桌面版、游戏自动化、关卡编辑器或脚本分享功能。
- 沙箱内核目前只打包 ARM64/ARMv7 原生库，不支持 x86_64 模拟器运行沙箱应用。
- 大型 APK 首次导入会复制到私有存储，需要额外空间且可能耗时数分钟。
- 沙箱基于通用应用容器实现，尚未针对 pvz2 的 64 位原生引擎、子进程和 SDK 做专门兼容，实际兼容性需要在真机上验证。

## 计划功能

以下内容均为规划，不代表当前版本已经可用：

- 开发电脑版 PCP。
- 使用图像识别操作手机或电脑模拟器上的 pvz2，实现自动刷取资源、通关等游戏自动化。
- 内置可视化 pvz2 关卡编辑器，并支持一键导入关卡文件。
- 建立脚本分享社区。
- 全面完善界面 UI。

## 使用方法

1. 在 Android 设备上安装并打开 PCP。
2. 点击“选择文件”，选择一个或多个 `.RTON` 或 `.json` 文件。
3. 点击“选择输出文件夹”，指定转换结果保存位置。
4. 选择“自动”“RTON → JSON”或“JSON → RTON”。
5. 如需加密或解密 RTON，填写 `RTONKey`。
6. 点击“开始转换”，等待处理完成。

转换使用应用缓存中的副本，不会直接修改所选源文件。仍建议在操作重要游戏文件前做好备份。

## 从源码构建

### 环境要求

- Android Studio
- Android SDK Platform 37
- Android NDK
- CMake 3.22.1 或更高版本
- JDK 25

首次构建需要联网下载 Gradle 和 Android 依赖。项目通过 Foojay Toolchain Resolver 配置 JDK 25，Android Studio 或 Gradle 可在环境允许时自动获取。

### 构建 Android 应用

Windows：

```powershell
cd apps/android
.\gradlew.bat :app:assembleDebug
```

Linux 或 macOS：

```bash
cd apps/android
./gradlew :app:assembleDebug
```

生成的调试 APK 位于：

```text
apps/android/app/build/outputs/apk/debug/app-debug.apk
```

常用检查命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

### 构建原生核心

仓库根目录的 CMake 工程会构建公共库、RTON 转换库和脚本运行库，目前不生成桌面可执行程序。

```powershell
cmake -S . -B build/core -DCMAKE_BUILD_TYPE=Release
cmake --build build/core --config Release
```

## 项目结构

```text
apps/
  android/          Android 应用、Compose 界面与 JNI 桥接
    Bcore/          Apache-2.0 Blacks BlackBox 沙箱内核
    black-reflection/  沙箱内核使用的反射支持库
    compiler/      沙箱内核使用的注解处理器
libs/
  common/           文件读写与国际化公共代码
  rton/             RTON/JSON 编解码、加密与批量转换
  script/           脚本语言、运行时与图像模板匹配
CMakeLists.txt      仓库根 CMake 工程
```

Android 应用直接读取 `apps/android/app/src/main/assets/lang/` 下的语言文件。该目录是应用文案的唯一来源。

## 参与贡献

- 提交问题前，先搜索现有 [Issues](https://github.com/Vacancyo/PCPToolKit/issues)。
- RTON 相关问题请附上可复现步骤、应用版本与最短错误信息。
- 提交改动时保持范围集中，并同步维护中文、英文文档和界面文案。
- 不要提交受版权保护的游戏资源、解密后的游戏数据或加密密钥。

## 免责声明

PCP 是非官方社区项目，与 PopCap Games、EA 或其合作方无隶属或认可关系。“植物大战僵尸”及相关图形、名称和游戏资源的权利归其各自权利人所有。

请仅在遵守适用法律、游戏用户协议和第三方权利的前提下使用 PCP。修改游戏文件前务必备份。不得分发受版权保护的游戏资源、解密后的游戏数据或加密密钥。项目不保证转换结果适用于所有游戏版本或用途。

## 许可证

本仓库目前尚未声明开源许可证。

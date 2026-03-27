# Shape Player — Android 内置播放器 Demo

本工程是一个 Android 播放器 APK：内置单曲 **Shape of You (Ed Sheeran)**，支持循环播放；界面简洁美观，并支持被第三方 exe 通过 ADB 安装和调起播放。

## 功能说明

| 功能 | 说明 |
|------|------|
| **内置片源** | 使用项目根目录下的 `Shape_of_You.mp3`，构建时会自动复制到 APK 的 assets 中 |
| **循环播放** | 播放器单曲循环播放，无次数限制 |
| **UI** | 深色主题、封面区、进度条、播放/暂停按钮，风格统一 |
| **安装方式** | 可通过 `adb install` 或第三方 exe 调用 adb 安装到设备 |
| **被调用播放** | 支持通过 Intent 调起：`com.player.demo.PLAY` 打开并播放，`com.player.demo.REPLAY` 从头重新播放。exe 用 adb 调用，详见 **快捷指令.md** |

## 环境要求

- Android Studio 或命令行：JDK 17+、Android SDK（compileSdk 34）
- 设备或模拟器：Android 7.0+ (API 24+)

## 内置片源

- 请将 **Shape_of_You.mp3** 放在项目根目录：`PlayerDemo/Shape_of_You.mp3`
- 构建时会自动复制为小写文件名 `shape_of_you.mp3` 到 `app/src/main/assets/` 并打进 APK（满足 Android 资源命名规则）
- 若未放置该文件，编译仍可进行，但运行时会提示“无法加载音频文件”

## 构建 APK

在项目根目录执行：

```bash
# Windows (PowerShell / CMD)
gradlew.bat assembleRelease

# macOS / Linux
./gradlew assembleRelease
```

首次构建如无 Gradle Wrapper，可用 Android Studio 打开项目并执行 **Build → Build Bundle(s) / APK(s) → Build APK(s)**，或先执行 `gradle wrapper` 生成 `gradlew`。

生成的无签名的 Release APK 路径：

- `app/build/outputs/apk/release/app-release-unsigned.apk`

若要上架或安装到真机，可配置签名后再次执行 `assembleRelease`，签名的 APK 会出现在同一目录（或根据你的 signingConfig 输出路径）。

## 安装到设备（供第三方 exe 调用 adb 安装）

确保设备已开启 USB 调试并连接电脑，然后：

```bash
adb install -r app/build/outputs/apk/release/app-release-unsigned.apk
```

- `-r`：若已安装则覆盖安装（重装）。
- 第三方 exe 只需在内部执行上述命令（或指定 APK 绝对路径）即可实现“通过 install 的方式安装到 adb 设备”。

## 被第三方/ADB 调起并播放

本应用支持两个 Intent Action，供 exe 通过 adb 调用：

| Action | 作用 |
|--------|------|
| `com.player.demo.PLAY` | 打开 App 并自动开始播放 |
| `com.player.demo.REPLAY` | 打开 App 并从 0:00 重新播放 |

**快捷指令（复制到 exe 用）：**

- **打开并播放**：`adb shell am start -a com.player.demo.PLAY -n com.player.demo/.MainActivity`
- **重新播放**：`adb shell am start -a com.player.demo.REPLAY -n com.player.demo/.MainActivity`

更多说明（安装命令、多设备、仅打开界面等）见项目根目录 **快捷指令.md**。

## 项目结构简述

- `app/src/main/java/com/player/demo/MainActivity.kt`  
  主界面：MediaPlayer 循环播放、处理 `com.player.demo.PLAY`、进度与播放/暂停。
- `app/src/main/AndroidManifest.xml`  
  声明 MainActivity、LAUNCHER、以及 `com.player.demo.PLAY` 的 intent-filter。
- `app/src/main/assets/`  
  内置音频为 `Shape_of_You.mp3`（由构建时从根目录复制）。
- `app/src/main/res/`  
  布局、主题、图标、进度条等 UI 资源。

## 注意事项与后续可改进

- **权限**：当前仅使用前台/唤醒相关声明，未申请存储等敏感权限；若后续改为播放外部文件，再按需声明。
- **兼容性**：在 API 24+ 设备上测试通过；若需支持更低版本，可下调 `minSdk` 并做兼容处理。
- **错误排查**：若播放失败，请确认设备上已存在 `Shape_of_You.mp3`（即 APK 内 assets 正常打包），并查看 Logcat 中 `PlayerDemo` 相关日志。

完成上述步骤后，即可得到可安装、可被第三方 exe 通过 adb 安装并调起播放的 Android 播放器 APK。

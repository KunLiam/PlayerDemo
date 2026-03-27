# Shape Player — Android 内置播放器 Demo

本工程是一个 Android 播放器 APK：内置 **三种测试音源**（assets），各自对应唤醒 / 气密性 / 震音测试场景；支持循环播放；界面会显示当前任务名称与文件名；支持被第三方工具通过 ADB 安装并指定音轨后调起播放。

## 功能说明

| 功能 | 说明 |
|------|------|
| **内置片源** | `app/src/main/assets/` 下三份音频：`shape_of_you.mp3`（唤醒）、`sweep_speech_48k.wav`（气密性）、`80-1KHz-20S(-3dB).wav`（震音）；需自行放入工程并打进 APK |
| **循环播放** | 当前音轨循环播放，无次数限制 |
| **UI** | 深色主题；标题下 **「测试音轨」** 三分栏（唤醒 / 气密 / 震音）可点选切换；封面、进度条、播放控制；下方展示当前测试类型与文件名 |
| **安装方式** | 可通过 `adb install` 或第三方 exe 调用 adb 安装到设备 |
| **被调用播放** | 支持 Intent：`PLAY` / `REPLAY` / `PAUSE` / `RESUME`；通过附加参数 **`com.player.demo.EXTRA_TRACK`** 选择音轨（`wakeup` / `airtight` / `vibration`）。详见下文与 **快捷指令.md** |

## 环境要求

- Android Studio 或命令行：JDK 17+、Android SDK（compileSdk 34）
- 设备或模拟器：Android 7.0+ (API 24+)

## 内置片源（三种测试音轨）

| `EXTRA_TRACK` 取值 | 用途 | assets 文件名 |
|--------------------|------|----------------|
| `wakeup` | 唤醒测试 | `shape_of_you.mp3` |
| `airtight` | 气密性测试 | `sweep_speech_48k.wav` |
| `vibration` | 震音测试 | `80-1KHz-20S(-3dB).wav` |

- 上述三个文件需存在于 `app/src/main/assets/`，文件名与上表一致（区分大小写）。
- 若某文件缺失，切换到该音轨时运行时会提示无法加载并打出 Logcat。
- 历史说明：若你仍使用根目录 `Shape_of_You.mp3` 由构建脚本复制到 assets，请保持复制目标名为 `shape_of_you.mp3`。

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

### Intent 参数约定（给外部工具用）

| 项 | 说明 |
|----|------|
| **Extra 键名** | `com.player.demo.EXTRA_TRACK` |
| **类型** | `String` |
| **取值** | `wakeup`（唤醒）、`airtight`（气密性）、`vibration`（震音）；**大小写不敏感** |
| **省略时** | 默认使用 `wakeup`（`shape_of_you.mp3`） |

与 Action 组合示例：

- 打开并播放**气密性**音源：  
  `adb shell am start -a com.player.demo.PLAY -n com.player.demo/.MainActivity --es com.player.demo.EXTRA_TRACK airtight`
- 打开并**从头**播放**震音**音源：  
  `adb shell am start -a com.player.demo.REPLAY -n com.player.demo/.MainActivity --es com.player.demo.EXTRA_TRACK vibration`

若 App 已在运行（`singleTask`），带上 `EXTRA_TRACK` 会**切换当前音轨**并重新加载；配合 `PLAY`/`REPLAY` 决定是否自动播放。

### Action 一览

| Action | 作用 |
|--------|------|
| `com.player.demo.PLAY` | 打开 App 并自动开始播放（当前或 Extra 指定的音轨） |
| `com.player.demo.REPLAY` | 打开 App 并从 0:00 重新播放 |
| `com.player.demo.PAUSE` | 暂停 |
| `com.player.demo.RESUME` | 暂停后继续 |

更多可复制命令见 **快捷指令.md**。

## 项目结构简述

- `app/src/main/java/com/player/demo/MainActivity.kt`  
  主界面：MediaPlayer 循环播放、处理 PLAY/PAUSE/RESUME/REPLAY、解析 `EXTRA_TRACK` 并切换音轨。
- `app/src/main/java/com/player/demo/TestAudioTrack.kt`  
  三类测试音源定义、`PlayerIntentContract.EXTRA_TRACK` 常量。
- `app/src/main/AndroidManifest.xml`  
  声明 MainActivity、LAUNCHER、以及 `com.player.demo.PLAY` 的 intent-filter。
- `app/src/main/assets/`  
  三个测试音频文件（见上表）。
- `app/src/main/res/`  
  布局、主题、图标、进度条等 UI 资源。

## 注意事项与后续可改进

- **权限**：当前仅使用前台/唤醒相关声明，未申请存储等敏感权限；若后续改为播放外部文件，再按需声明。
- **兼容性**：在 API 24+ 设备上测试通过；若需支持更低版本，可下调 `minSdk` 并做兼容处理。
- **错误排查**：若播放失败，请确认 APK 内 assets 含当前音轨对应文件名；查看 Logcat 标签 `PlayerDemo`。非法 `EXTRA_TRACK` 会 Toast「未知音轨标识」并回退默认唤醒音轨。

完成上述步骤后，即可得到可安装、可被第三方 exe 通过 adb 安装并调起播放的 Android 播放器 APK。

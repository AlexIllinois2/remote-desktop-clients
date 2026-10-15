# remote-desktop-clients 项目长期记忆

## 构建环境（wrapper 下载不可达时的直跑方式）
- Gradle：`/c/Users/45574/.gradle/wrapper/dists/gradle-8.13-bin/5xuhj0ry160q40clulazy9h7d/gradle-8.13/bin/gradle`
- JDK：`C:/Users/45574/.workbuddy/binaries/jdk/jdk-21.0.5+11`（需 export JAVA_HOME）
- 示例：`JAVA_HOME=<above> <gradle> :bVNC-app:assembleDebug`（约 2min；release 变体首次约 3min15s）
- 所有改动仅在用户明确要求"打包/构建"时执行（用户硬性规则）。

## 签名约定
- 项目任何模块均无 signingConfig，release 产物为 `*-release-unsigned.apk`（走 F-Droid 分发、由 F-Droid 侧签名）。要装机需用户提供 keystore 用 apksigner 签，或临时用 debug key。

## 模块结构
- `bVNC` 是共享库模块：RemoteCanvasActivity、canvasactivitymenu.xml、FloatingScrollKeys 等 UI 都在里面，改动自动作用于全部 app 变体（bVNC-app / aRDP-app / aSPICE-app / free* 等），无需分别改。
- ABI splits：armeabi-v7a / arm64-v8a / x86 / x86_64 + universalApk。

## 测试模拟器（乐游模拟器/MuMu）
- adb：`D:\乐游模拟器\MuMuPlayer\nx_main\adb.exe`；设备序列可能是 `emulator-5556` 或 TCP 端口（16416/5557/7555 实为同一设备）。
- **禁止 `adb root`**：会导致设备侧 adbd 卡死（2026-10-16 踩坑），只能强杀 MuMuNxMain/MuMuNxDevice 后重启恢复；重启用 PowerShell `Start-Process`（Git Bash 的 `start ""` 无效）。
- 重启会回滚到不含自装 app 的快照，自装应用与偏好会丢失。
- Git Bash 里 adb 路径要 `export MSYS_NO_PATHCONV=1`；pull 到本地用 Windows 风格路径。

# 密拾（password-vault-app）

**密拾** — 拾藏你的账号、网址与私密截图。本地加密、无云端、无联网权限的 Android 隐私工具（Capacitor + 原生悬浮层）。

---

## 功能摘要

- **PIN 解锁密码库**：AES-256-GCM 加密存储账号/密码/网址/备注  
- **马卡龙纸感 UI**：6 套主题、卡片样式、首页 Bento  
- **私密截图库**：可选自动收纳系统截图，解锁后写入加密库  
- **悬浮球**（需授权）：8 项快捷菜单 — 录入、搜密码、临时剪贴板、查看最近截图等  
- **隐私**：切后台模糊、可配置自动锁定、活动记录  
- **加密备份**：`.mishi` 独立备份密码（PBKDF2 + AES-GCM）  

详细对照任务书见 [`docs/功能核对清单.md`](docs/功能核对清单.md)。

---

## 环境要求

- Windows（推荐）或已配置 Android SDK 的环境  
- **Android Studio**（自带 JBR/Java 17）或独立 JDK  
- **Android SDK**（API 33+ 建议）  
- Node.js（用于 `npx cap sync`）

---

## 构建 Debug APK

在项目根目录双击或在终端运行：

```bat
build-apk.bat
```

脚本会：

1. 检测 `JAVA_HOME`（优先 Android Studio 自带 JBR）  
2. 检测 `ANDROID_HOME`（默认 `%LOCALAPPDATA%\Android\Sdk`）  
3. 执行 `npx cap sync android`  
4. 在 `android/` 下运行 `gradlew.bat assembleDebug`  

成功产物：

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

安装到手机时需允许「安装未知来源应用」。

可选：运行 `open-in-android-studio.bat` 用 Android Studio 打开 `android` 模块进行签名发布构建。

---

## 项目结构

```text
password-vault-app/
├── www/                    # Web 前端（Capacitor 资源）
│   ├── index.html          # 主 UI、PIN、加密 vault、截图 Tab
│   ├── js/mishi-ext.js     # 密拾扩展：主题、备份、自动锁定、原生同步
│   └── css/mishi-sticker.css
├── android/                # Capacitor Android 工程
│   └── app/src/main/java/com/personal/passwordvault/
│       ├── OverlayService.java      # 悬浮球、截屏监听、菜单
│       ├── VaultNativePlugin.java   # JS ↔ 原生桥
│       ├── ScreenshotStore.java     # 截图 pending 与私有目录
│       └── VaultCache.java          # 搜索索引、pending 条目、剪贴板
├── docs/                   # 功能清单、权限说明、数据安全说明
├── capacitor.config.json
├── package.json
├── build-apk.bat           # 一键打包 Debug APK
└── open-in-android-studio.bat
```

同步 Web 改动到 Android：

```bash
npm run sync
# 或
npx cap sync android
```

---

## 文档

| 文档 | 说明 |
|------|------|
| [docs/功能核对清单.md](docs/功能核对清单.md) | 与《密拾APP 项目开发任务书》逐项核对 |
| [docs/权限说明文档.md](docs/权限说明文档.md) | Android 权限用途与拒绝后果 |
| [docs/数据安全说明文档.md](docs/数据安全说明文档.md) | 加密、备份、截图隔离与卸载 |

---

## 声明

- 忘记 PIN 或 `.mishi` 备份密码将无法恢复数据。  
- 自动收纳截图需相册读取与悬浮窗等权限；可在设置中关闭。  
- 本仓库为本地隐私工具，**不包含**网络账号体系与云同步。

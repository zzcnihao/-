@echo off
chcp 65001 >nul
echo ========================================
echo   密码本 - Android APK 本地打包
echo ========================================
echo.

REM 优先使用 Android Studio 自带的 Java 17
if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" (
  set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
) else if exist "%LOCALAPPDATA%\Programs\Android Studio\jbr\bin\java.exe" (
  set "JAVA_HOME=%LOCALAPPDATA%\Programs\Android Studio\jbr"
)

if not defined JAVA_HOME (
  where java >nul 2>&1
  if errorlevel 1 (
    echo [错误] 未检测到 Java
    echo 请先安装 Android Studio（会自动带 Java）
    pause
    exit /b 1
  )
) else (
  set "PATH=%JAVA_HOME%\bin;%PATH%"
  echo 使用 JAVA_HOME=%JAVA_HOME%
)

if not defined ANDROID_HOME (
  if exist "%LOCALAPPDATA%\Android\Sdk" (
    set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
  ) else (
    echo [错误] 未找到 Android SDK
    echo.
    echo 请按下面步骤操作：
    echo 1. 安装 Android Studio
    echo 2. 首次打开时完成 Setup Wizard（会下载 SDK）
    echo 3. 再运行本脚本
    echo.
    pause
    exit /b 1
  )
)

echo 使用 ANDROID_HOME=%ANDROID_HOME%
echo.
echo 正在打包，首次可能需要 10~20 分钟下载依赖，请耐心等待...
echo.

cd /d "%~dp0"
call npx cap sync android
if errorlevel 1 goto :fail

cd android
echo sdk.dir=%ANDROID_HOME:\=\\%> local.properties
call gradlew.bat assembleDebug --no-daemon
if errorlevel 1 goto :fail

echo.
echo ========================================
echo   打包成功！
echo.
echo   APK 文件位置：
echo   %~dp0android\app\build\outputs\apk\debug\app-debug.apk
echo ========================================
echo.
echo 下一步：把 app-debug.apk 传到手机安装
echo 手机需允许「安装未知来源应用」
echo.
pause
exit /b 0

:fail
echo.
echo [错误] 打包失败
echo 常见原因：网络超时、SDK 未装完整
echo 可改用 Android Studio 图形界面打包（见下方说明）
pause
exit /b 1

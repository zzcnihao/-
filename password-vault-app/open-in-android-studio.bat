@echo off
chcp 65001 >nul
echo 正在用 Android Studio 打开项目...
echo.
echo 打开后请操作：
echo   Build ^> Build Bundle(s) / APK(s) ^> Build APK(s)
echo.
echo 打包完成后点右下角弹窗的 "locate" 即可找到 APK
echo.

set "STUDIO=C:\Program Files\Android\Android Studio\bin\studio64.exe"
if not exist "%STUDIO%" set "STUDIO=%LOCALAPPDATA%\Programs\Android Studio\bin\studio64.exe"

if not exist "%STUDIO%" (
  echo [错误] 未找到 Android Studio，请先安装
  pause
  exit /b 1
)

start "" "%STUDIO%" "%~dp0android"
exit /b 0

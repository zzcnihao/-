@echo off
chcp 65001 >nul
echo ========================================
echo   密拾 - 上传到 GitHub 并云端打包
echo ========================================
echo.

cd /d "%~dp0"

git status >nul 2>&1
if errorlevel 1 (
  echo [错误] 当前文件夹不是 Git 仓库
  echo 请先联系助手完成 git init 和首次提交
  pause
  exit /b 1
)

echo 请输入你的 GitHub 仓库地址，例如：
echo   https://github.com/你的用户名/password-vault.git
echo.
set /p REPO_URL=仓库地址: 

if "%REPO_URL%"=="" (
  echo [错误] 地址不能为空
  pause
  exit /b 1
)

git branch -M main 2>nul
git remote remove origin 2>nul
git remote add origin "%REPO_URL%"

echo.
echo 正在推送到 GitHub...
git push -u origin main
if errorlevel 1 (
  echo.
  echo [错误] 推送失败。常见原因：
  echo   1. 仓库地址写错
  echo   2. 未登录 GitHub（需先在浏览器登录）
  echo   3. GitHub 上还没创建该仓库
  echo.
  echo 若提示登录，可安装 GitHub Desktop 或使用 Personal Access Token
  pause
  exit /b 1
)

echo.
echo ========================================
echo   推送成功！
echo.
echo   接下来在 GitHub 网页操作：
echo   1. 打开你的仓库
echo   2. 点顶部 Actions
echo   3. 左侧选 Build Password Vault APK
echo   4. 点 Run workflow ^> Run workflow
echo   5. 等 5~10 分钟，在 Artifacts 下载 mishi-apk
echo      或 Releases 页面下载 密拾 APK
echo ========================================
pause

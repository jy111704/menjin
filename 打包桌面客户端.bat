@echo off
chcp 65001 >nul
title 重新打包桌面客户端 - 校园车辆出入登记管理系统
cd /d "%~dp0"

echo ============================================================
echo  重新打包桌面客户端（exe）
echo ============================================================

echo.
echo [1/4] 处理前端产物 ...
if exist "frontend\src" (
  echo       检测到前端源码，执行构建 ...
  pushd frontend
  call npm run build
  if errorlevel 1 ( popd & goto fail )
  popd
) else (
  echo       未检测到 frontend\src，直接使用已有的 frontend\dist
)

if not exist "frontend\dist\index.html" (
  echo [错误] 找不到 frontend\dist\index.html，无法打包。
  goto fail
)

echo.
echo [2/4] 同步静态资源到桌面客户端 ...
if exist "desktop\app-dist" rmdir /s /q "desktop\app-dist"
mkdir "desktop\app-dist"
xcopy "frontend\dist\*" "desktop\app-dist\" /E /I /Y >nul
if errorlevel 1 goto fail

echo.
echo [3/4] 检查打包依赖 ...
if not exist "desktop\node_modules\electron\dist\electron.exe" (
  echo       首次打包，安装 electron 依赖 ...
  pushd desktop
  set "ELECTRON_MIRROR=https://npmmirror.com/mirrors/electron/"
  call npm install --registry=https://registry.npmmirror.com
  if errorlevel 1 ( popd & goto fail )
  popd
) else (
  echo       依赖已就绪
)

echo.
echo [4/4] 生成 exe ...
pushd desktop
set "ELECTRON_MIRROR=https://npmmirror.com/mirrors/electron/"
set "ELECTRON_BUILDER_BINARIES_MIRROR=https://npmmirror.com/mirrors/electron-builder-binaries/"
call npx electron-builder --win
if errorlevel 1 ( popd & goto fail )
popd

echo.
echo ------------------------------------------------------------
echo 打包完成，产物在 desktop\release：
echo   校园车辆出入登记管理系统-便携版-1.0.0.exe   ^(免安装，双击即用^)
echo   校园车辆出入登记管理系统-安装版-1.0.0.exe   ^(安装后在桌面生成快捷方式^)
echo ------------------------------------------------------------
pause
exit /b 0

:fail
echo.
echo 打包失败，请查看上方错误信息。
pause
exit /b 1

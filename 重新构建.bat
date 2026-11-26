@echo off
chcp 65001 >nul
title 重新构建 - 校园车辆出入登记管理系统
set "JAVA_HOME=C:\Program Files\Java\jdk-17"
if not exist "%JAVA_HOME%\bin\java.exe" (
  echo [错误] 未找到 JDK 17，请修改本脚本中的 JAVA_HOME 路径。
  pause
  exit /b 1
)
set "PATH=%JAVA_HOME%\bin;%PATH%"
cd /d "%~dp0"

echo [1/4] 安装前端依赖 ...
cd frontend
call npm install --registry=https://registry.npmmirror.com
if errorlevel 1 goto fail

echo [2/4] 构建前端 ...
call npm run build
if errorlevel 1 goto fail

echo [3/4] 复制静态资源到后端 ...
cd ..
if exist "backend\src\main\resources\static" rmdir /s /q "backend\src\main\resources\static"
mkdir "backend\src\main\resources\static"
xcopy "frontend\dist\*" "backend\src\main\resources\static\" /E /I /Y >nul

echo [4/4] 打包后端 ...
cd backend
call mvn -DskipTests clean package
if errorlevel 1 goto fail

echo.
echo 构建完成：backend\target\campus-vehicle.jar
echo 双击 “启动系统.bat” 即可运行。
pause
exit /b 0

:fail
echo.
echo 构建失败，请查看上方错误信息。
pause
exit /b 1

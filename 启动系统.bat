@echo off
chcp 65001 >nul
title 校园车辆出入登记管理系统 - 服务端
cd /d "%~dp0backend"

rem ===== 优先使用 JDK 17（Spring Boot 3 需要 17+，推荐 17） =====
set "JAVA_EXE=C:\Program Files\Java\jdk-17\bin\java.exe"
if not exist "%JAVA_EXE%" set "JAVA_EXE=java"

"%JAVA_EXE%" -version >nul 2>&1
if errorlevel 1 (
  echo [错误] 未找到可用的 Java 运行环境，请安装 JDK 17 后重试。
  pause
  exit /b 1
)

echo [1/3] 检查 MySQL 服务 ...
sc query MySQL80 | findstr /I "RUNNING" >nul
if errorlevel 1 (
  echo       MySQL80 未运行，正在尝试启动 ...
  net start MySQL80 >nul 2>&1
  if errorlevel 1 echo       [警告] 自动启动失败，请以管理员身份启动 MySQL80 服务后重试。
) else (
  echo       MySQL80 运行中
)

if not exist "target\campus-vehicle.jar" (
  echo [错误] 未找到 target\campus-vehicle.jar，请先运行 “重新构建.bat”
  pause
  exit /b 1
)

echo [2/3] 启动服务（首次启动自动建表，不会写入任何演示数据）...
echo.
echo       管理端   http://localhost:8080/#/login
echo       访客端   http://localhost:8080/#/visitor
echo       查询端   http://localhost:8080/#/query
echo       默认账号 admin / Admin@123  （登录后请立即修改密码）
echo       停止服务 按 Ctrl+C 或直接关闭本窗口
echo.
echo [3/3] 运行日志：
echo ------------------------------------------------------------------
"%JAVA_EXE%" -Dfile.encoding=UTF-8 -jar target\campus-vehicle.jar --server.port=8080

echo ------------------------------------------------------------------
echo 服务已停止。
pause

@echo off
chcp 65001 >nul
REM 将 E:\MP4_Music 的音频推送到手机 /sdcard/Music/MP4_Music/
REM 使用前请确保:1. 手机已 USB 连接并开启 USB 调试 2. adb 已加入环境变量

echo 检查设备连接...
adb devices

echo.
echo 开始推送音频(保留目录结构)...
adb push "E:\MP4_Music" "/sdcard/Music/MP4_Music"

echo.
echo 推送完成。请打开 App,在"文件浏览器"或等待自动扫描。
pause

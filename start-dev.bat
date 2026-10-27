@echo off
chcp 65001 >nul
title DATN-2026 - Start All Services
echo ===================================================
echo   Đang khởi chạy tất cả Backend Services DATN-2026...
echo ===================================================
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-dev.ps1" %*

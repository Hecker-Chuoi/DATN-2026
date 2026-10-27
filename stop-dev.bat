@echo off
chcp 65001 >nul
title DATN-2026 - Stop All Services
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0stop-dev.ps1"
pause

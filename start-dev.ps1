# ==============================================================================
# Script khởi chạy toàn bộ Backend Services (và tùy chọn Frontend) cho DATN-2026
# Sử dụng:
#   .\start-dev.ps1                 # Chạy cả 5 backend services (bật 5 cửa sổ riêng)
#   .\start-dev.ps1 -WithFrontend   # Chạy 5 backend services + Frontend (bật 6 cửa sổ riêng)
# ==============================================================================

param(
    [switch]$WithFrontend
)

$rootDir = $PSScriptRoot
$backendDir = Join-Path $rootDir "backend"
$frontendDir = Join-Path $rootDir "frontend"

$services = @(
    @{ Name = "API Gateway"; Port = 8081; Module = "api-gateway" },
    @{ Name = "Identity Service"; Port = 8082; Module = "identity-service" },
    @{ Name = "Work Service"; Port = 8083; Module = "work-service" },
    @{ Name = "Workspace Service"; Port = 8087; Module = "workspace-service" },
    @{ Name = "Notification Service"; Port = 9994; Module = "notification-service" }
)

# `spring-boot:run -pl <module>` (không -am) lấy module common từ ~/.m2, nên phải cài bản mới nhất trước,
# nếu không service sẽ lỗi NoClassDefFoundError khi common có class mới (vd. com.datn.common.notification).
Write-Host "`nĐang cài module common (mvn install -pl common)..." -ForegroundColor Cyan
Push-Location $backendDir
& .\mvnw.cmd -q install -pl common -DskipTests
$commonExit = $LASTEXITCODE
Pop-Location
if ($commonExit -ne 0) {
    Write-Host "  [LỖI] Không cài được module common. Hãy tắt các service đang chạy (.\stop-dev.ps1) rồi thử lại." -ForegroundColor Red
    exit 1
}
Write-Host "  [OK] Đã cài module common" -ForegroundColor Green

Write-Host "`n=======================================================" -ForegroundColor Cyan
Write-Host "  Đang mở các cửa sổ Terminal riêng cho từng Service..." -ForegroundColor Cyan
Write-Host "=======================================================" -ForegroundColor Cyan

foreach ($s in $services) {
    $title = "[DATN-2026] $($s.Name) (:$($s.Port))"
    # Dùng conhost.exe để ép buộc Windows mở cửa sổ Console độc lập ngay trên màn hình (không bị nuốt ngầm)
    $cmd = "`$Host.UI.RawUI.WindowTitle = '$title'; Write-Host '=== $title ĐANG CHẠY ===' -ForegroundColor Green; Write-Host 'Xem log chi tiết bên dưới:' -ForegroundColor Gray; .\mvnw.cmd spring-boot:run -pl $($s.Module)"
    
    Start-Process conhost.exe -WindowStyle Normal -WorkingDirectory $backendDir -ArgumentList "powershell.exe", "-NoExit", "-ExecutionPolicy", "Bypass", "-Command", $cmd
    Write-Host "  [OK] Đã mở Terminal: $($s.Name) (Cổng $($s.Port))" -ForegroundColor Green
    Start-Sleep -Milliseconds 600
}

if ($WithFrontend) {
    Write-Host "`nĐang mở Terminal Frontend..." -ForegroundColor Cyan
    $feTitle = "[DATN-2026] Frontend (:5173)"
    $feCmd = "`$Host.UI.RawUI.WindowTitle = '$feTitle'; Write-Host '=== $feTitle ĐANG CHẠY ===' -ForegroundColor Green; npm run dev"
    
    Start-Process conhost.exe -WindowStyle Normal -WorkingDirectory $frontendDir -ArgumentList "powershell.exe", "-NoExit", "-ExecutionPolicy", "Bypass", "-Command", $feCmd
    Write-Host "  [OK] Đã mở Terminal: Frontend (:5173)" -ForegroundColor Green
}

Write-Host "`n=======================================================" -ForegroundColor Yellow
Write-Host "  Tất cả các cửa sổ Terminal đã được mở lên màn hình!" -ForegroundColor Yellow
Write-Host "  LƯU Ý:" -ForegroundColor Cyan
Write-Host "  1. Các Backend Service cần khoảng 10-15 giây để khởi động xong." -ForegroundColor Gray
Write-Host "     (Khi nào thấy dòng 'Started ...Application in X seconds' là xong)." -ForegroundColor Gray
Write-Host "  2. Tránh click bôi đen chữ trong terminal vì Windows sẽ tạm dừng tiến trình (ấn Enter/Esc để nhả)." -ForegroundColor Gray
Write-Host "  Để tắt tất cả cùng lúc, chỉ cần chạy: .\stop-dev.ps1" -ForegroundColor Yellow
Write-Host "=======================================================`n" -ForegroundColor Yellow

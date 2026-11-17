# ==============================================================================
# Script dừng toàn bộ tiến trình Backend và Frontend đang chiếm cổng DATN-2026
# Sử dụng:
#   .\stop-dev.ps1
# ==============================================================================

$ports = @(8081, 8082, 8083, 8087, 9994, 5173)

Write-Host "Dang kiem tra va dung cac tien trinh dang chiem cac cong: $($ports -join ', ')..." -ForegroundColor Cyan

$killedCount = 0

foreach ($port in $ports) {
    $connections = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if ($connections) {
        $pids = $connections | Select-Object -ExpandProperty OwningProcess -Unique
        foreach ($pidToKill in $pids) {
            if ($pidToKill -gt 0) {
                try {
                    $proc = Get-Process -Id $pidToKill -ErrorAction SilentlyContinue
                    if ($proc) {
                        Write-Host "  -> Dang dung $($proc.ProcessName) (PID: $pidToKill) tren cong $port..." -ForegroundColor Yellow
                        Stop-Process -Id $pidToKill -Force -ErrorAction SilentlyContinue
                        $killedCount++
                    }
                } catch {
                    Write-Warning "Khong the dung PID ${pidToKill}: $($_.Exception.Message)"
                }
            }
        }
    }
}

if ($killedCount -gt 0) {
    Write-Host "`nDa dung thanh cong $killedCount tien trinh!" -ForegroundColor Green
} else {
    Write-Host "`nKhong co tien trinh nao dang chiem cac cong nay." -ForegroundColor Gray
}

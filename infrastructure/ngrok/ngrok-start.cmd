<# : ngrok-start.cmd
@echo off
rem Bấm đúp để chạy. Phần dưới là PowerShell, cmd chỉ đọc mấy dòng này.
chcp 65001 >nul
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ScriptFile='%~f0'; iex ([IO.File]::ReadAllText($ScriptFile, [Text.Encoding]::UTF8))"
echo.
pause
exit /b
#>

# ─────────────────────────────────────────────────────────────────────────────
#  Mở tunnel ngrok cho webhook GitHub của DATN.
#  - Chưa có ngrok, hoặc ngrok quá cũ: tự tải bản mới vào %LOCALAPPDATA%\datn-ngrok (không cần quyền admin, không sửa PATH).
#  - Lần đầu: hỏi authtoken và domain tĩnh của chính tài khoản ngrok người dùng, lưu lại cho các lần sau.
#  - Mặc định chỉ cho đường dẫn webhook đi qua; mọi đường khác trả 404 (API còn lại không lộ ra internet).
#  Gửi cho người khác: chỉ cần gửi file này. Mỗi người dùng tài khoản ngrok của riêng mình.
# ─────────────────────────────────────────────────────────────────────────────

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
try { $Host.UI.RawUI.WindowTitle = 'ngrok · DATN webhook' } catch {}

$MinVersion    = [version]'3.29.0'   # bản thấp nhất ngrok còn cho tài khoản free kết nối
$AppDir        = Join-Path $env:LOCALAPPDATA 'datn-ngrok'
$BinDir        = Join-Path $AppDir 'bin'
$SettingsFile  = Join-Path $AppDir 'settings.json'
$PolicyFile    = Join-Path $AppDir 'only-webhook.yml'
$DownloadUrl   = 'https://bin.equinox.io/c/bNyj1mQVY4c/ngrok-v3-stable-windows-amd64.zip'
$NgrokConfig   = Join-Path $env:LOCALAPPDATA 'ngrok\ngrok.yml'
$DefaultSettings = [ordered]@{
    Port        = 8081
    Domain      = ''
    WebhookPath = '/work/api/v1/work/integrations/github/webhook'
    OnlyWebhook = $true
}

function Say($text)  { Write-Host "  $text" }
function Step($text) { Write-Host ""; Write-Host "› $text" -ForegroundColor Cyan }
function Ok($text)   { Write-Host "  ✓ $text" -ForegroundColor Green }
function Warn($text) { Write-Host "  ! $text" -ForegroundColor Yellow }
function Fail($text) { Write-Host "  ✗ $text" -ForegroundColor Red }

function Ask-YesNo($question, $defaultYes) {
    $hint = if ($defaultYes) { '[Y/n]' } else { '[y/N]' }
    $answer = Read-Host "  $question $hint"
    if ([string]::IsNullOrWhiteSpace($answer)) { return $defaultYes }
    return $answer.Trim().ToLower().StartsWith('y')
}

function Get-NgrokVersion($exe) {
    try {
        $out = (& $exe version 2>$null) -join ' '
        if ($out -match '(\d+\.\d+\.\d+)') { return [version]$Matches[1] }
    } catch {}
    return $null
}

function Install-Ngrok {
    Say "Đang tải ngrok từ trang chính thức…"
    New-Item -ItemType Directory -Force -Path $BinDir | Out-Null
    $zip = Join-Path $AppDir 'ngrok.zip'
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $ProgressPreference = 'SilentlyContinue'
    Invoke-WebRequest -UseBasicParsing -Uri $DownloadUrl -OutFile $zip
    Expand-Archive -Path $zip -DestinationPath $BinDir -Force
    Remove-Item $zip -Force
    return (Join-Path $BinDir 'ngrok.exe')
}

function Resolve-Ngrok {
    $candidates = @()
    $local = Join-Path $BinDir 'ngrok.exe'
    if (Test-Path $local) { $candidates += $local }
    $onPath = Get-Command ngrok -ErrorAction SilentlyContinue
    if ($onPath -and ($candidates -notcontains $onPath.Source)) { $candidates += $onPath.Source }

    foreach ($exe in $candidates) {
        $v = Get-NgrokVersion $exe
        if ($v -and $v -ge $MinVersion) { Ok "Đã có ngrok $v"; return $exe }
    }
    foreach ($exe in $candidates) {
        $v = Get-NgrokVersion $exe
        if ($v) {
            Warn "ngrok $v quá cũ (cần từ $MinVersion), thử cập nhật…"
            try { & $exe update 2>&1 | Out-Null } catch {}
            $v = Get-NgrokVersion $exe
            if ($v -and $v -ge $MinVersion) { Ok "Đã cập nhật lên ngrok $v"; return $exe }
        }
    }
    if ($candidates.Count -eq 0) { Say "Máy chưa có ngrok." }
    $exe = Install-Ngrok
    $v = Get-NgrokVersion $exe
    if (-not $v) { throw "Tải ngrok xong nhưng không chạy được: $exe" }
    Ok "Đã cài ngrok $v vào $BinDir"
    return $exe
}

function Ensure-Authtoken($exe) {
    $hasToken = (Test-Path $NgrokConfig) -and ((Get-Content $NgrokConfig -Raw) -match '(?m)^\s*authtoken:\s*\S+')
    if ($hasToken) { Ok "Đã có authtoken"; return }

    Say "Cần authtoken của tài khoản ngrok (miễn phí). Lấy tại:"
    Say "https://dashboard.ngrok.com/get-started/your-authtoken"
    if (Ask-YesNo "Mở trang đó trong trình duyệt?" $true) { Start-Process 'https://dashboard.ngrok.com/get-started/your-authtoken' }
    while ($true) {
        $secure = Read-Host "  Dán authtoken (không hiện ra màn hình)" -AsSecureString
        $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
        try { $token = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr).Trim() }
        finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
        if ($token.Length -ge 20) { break }
        Warn "Authtoken trông không đúng, thử lại."
    }
    & $exe config add-authtoken $token | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "ngrok không lưu được authtoken" }
    Ok "Đã lưu authtoken"
}

function Load-Settings {
    $s = [ordered]@{}
    foreach ($k in $DefaultSettings.Keys) { $s[$k] = $DefaultSettings[$k] }
    if (Test-Path $SettingsFile) {
        $saved = Get-Content $SettingsFile -Raw -Encoding UTF8 | ConvertFrom-Json
        foreach ($k in $DefaultSettings.Keys) {
            if ($null -ne $saved.$k) { $s[$k] = $saved.$k }
        }
        $s['_saved'] = $true
    }
    return $s
}

function Save-Settings($s) {
    New-Item -ItemType Directory -Force -Path $AppDir | Out-Null
    $out = [ordered]@{}
    foreach ($k in $DefaultSettings.Keys) { $out[$k] = $s[$k] }
    ($out | ConvertTo-Json) | Set-Content -Path $SettingsFile -Encoding UTF8
}

function Edit-Settings($s) {
    Say "Domain tĩnh miễn phí xem ở https://dashboard.ngrok.com/domains (dạng abc-xyz.ngrok-free.dev)."
    Say "Để trống nếu chưa có: ngrok sẽ cấp URL ngẫu nhiên, đổi mỗi lần chạy."
    $d = Read-Host "  Domain tĩnh [$($s.Domain)]"
    if (-not [string]::IsNullOrWhiteSpace($d)) {
        $s.Domain = ($d.Trim() -replace '^https?://', '' -replace '/.*$', '')
    } elseif ($d -eq ' ') { $s.Domain = '' }
    $p = Read-Host "  Cổng gateway trên máy [$($s.Port)]"
    if ($p -match '^\d+$') { $s.Port = [int]$p }
    $s.OnlyWebhook = Ask-YesNo "Chỉ cho đường dẫn webhook đi qua (khuyên dùng)?" ([bool]$s.OnlyWebhook)
    Save-Settings $s
    Ok "Đã lưu cài đặt vào $SettingsFile"
}

function Wait-ForEditKey($seconds) {
    Write-Host "  Enter để chạy ngay · S để sửa cài đặt · tự chạy sau $seconds giây" -ForegroundColor DarkGray
    $deadline = (Get-Date).AddSeconds($seconds)
    try {
        while ((Get-Date) -lt $deadline) {
            if ([Console]::KeyAvailable) {
                $key = [Console]::ReadKey($true)
                return ($key.Key -eq 'S')
            }
            Start-Sleep -Milliseconds 100
        }
    } catch {}
    return $false
}

function Test-Port($port) {
    $client = New-Object Net.Sockets.TcpClient
    try {
        $iar = $client.BeginConnect('127.0.0.1', $port, $null, $null)
        if (-not $iar.AsyncWaitHandle.WaitOne(800)) { return $false }
        $client.EndConnect($iar)
        return $true
    } catch { return $false } finally { $client.Close() }
}

function Show-RunningTunnel {
    if (-not (Get-Process ngrok -ErrorAction SilentlyContinue)) { return $false }
    try { $t = Invoke-RestMethod -Uri 'http://127.0.0.1:4040/api/tunnels' -TimeoutSec 3 } catch { $t = $null }
    Warn "ngrok đang chạy sẵn ở một cửa sổ khác."
    if ($t -and $t.tunnels) {
        foreach ($x in $t.tunnels) { Say "$($x.public_url)  →  $($x.config.addr)" }
    }
    if (Ask-YesNo "Mở trang theo dõi request (127.0.0.1:4040)?" $true) { Start-Process 'http://127.0.0.1:4040' }
    return $true
}

function Write-Policy($webhookPath) {
    New-Item -ItemType Directory -Force -Path $AppDir | Out-Null
    $policy = @"
on_http_request:
  - expressions:
      - "req.url.path != '$webhookPath'"
    actions:
      - type: deny
        config:
          status_code: 404
"@
    Set-Content -Path $PolicyFile -Value $policy -Encoding ASCII
}

# ─────────────────────────────── Chạy ───────────────────────────────
try {
    Write-Host ""
    Write-Host "  ngrok · webhook GitHub cho DATN" -ForegroundColor White

    Step "Kiểm tra ngrok"
    if (Show-RunningTunnel) { return }
    $ngrok = Resolve-Ngrok

    Step "Kiểm tra authtoken"
    Ensure-Authtoken $ngrok

    Step "Cài đặt"
    $settings = Load-Settings
    if (-not $settings['_saved']) {
        Say "Lần chạy đầu tiên, cần vài thông tin."
        Edit-Settings $settings
    } else {
        $domainText = if ($settings.Domain) { $settings.Domain } else { '(URL ngẫu nhiên)' }
        Say "Domain : $domainText"
        Say "Cổng   : $($settings.Port)"
        Say "Lọc    : $(if ($settings.OnlyWebhook) { 'chỉ đường dẫn webhook' } else { 'mở toàn bộ cổng' })"
        if (Wait-ForEditKey 5) { Edit-Settings $settings }
    }

    Step "Kiểm tra cổng $($settings.Port) trên máy"
    if (Test-Port $settings.Port) {
        Ok "Có dịch vụ đang chạy ở cổng $($settings.Port)"
    } else {
        Warn "Chưa có gì chạy ở cổng $($settings.Port) (gateway chưa bật?). GitHub gửi tới sẽ nhận lỗi 502."
        if (-not (Ask-YesNo "Vẫn mở tunnel?" $true)) { return }
    }

    $ngArgs = @('http', "$($settings.Port)")
    if ($settings.Domain) { $ngArgs += "--url=$($settings.Domain)" }
    if ($settings.OnlyWebhook) {
        Write-Policy $settings.WebhookPath
        $ngArgs += "--traffic-policy-file=$PolicyFile"
    }

    Step "Mở tunnel"
    if ($settings.Domain) {
        $webhookUrl = "https://$($settings.Domain)$($settings.WebhookPath)"
        Say "URL webhook (khai báo ở github-secrets.yml → github.webhook-url):"
        Write-Host "  $webhookUrl" -ForegroundColor White
        try { Set-Clipboard -Value $webhookUrl; Say "(đã chép vào clipboard)" } catch {}
    } else {
        Warn "Không có domain tĩnh: xem URL ở dòng Forwarding bên dưới. URL đổi mỗi lần chạy, phải sửa webhook-url và kết nối lại repo."
    }
    Say "Theo dõi request: http://127.0.0.1:4040 · Dừng: Ctrl+C"
    Start-Sleep -Seconds 2

    $started = Get-Date
    & $ngrok @ngArgs
    $code = $LASTEXITCODE

    if ($code -ne 0 -and $settings.OnlyWebhook -and ((Get-Date) - $started).TotalSeconds -lt 15) {
        Warn "ngrok dừng ngay khi bật bộ lọc đường dẫn. Thử chạy lại không lọc…"
        $ngArgs = $ngArgs | Where-Object { $_ -notlike '--traffic-policy-file=*' }
        & $ngrok @ngArgs
    }
} catch {
    Fail $_.Exception.Message
    Say "Nếu lỗi lặp lại: xoá thư mục $AppDir rồi chạy lại file này."
}

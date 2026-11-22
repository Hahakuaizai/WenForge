#requires -Version 7.0
<#
  serve-update.ps1  —  把 update\ 目录挂到 8787 端口，手机来取

  用法：
    pwsh -File scripts\serve-update.ps1           # 前台跑，Ctrl+C 停
    pwsh -File scripts\serve-update.ps1 -Bg       # 后台跑（窗口可以关）

  顺便做的事：
    · 有手机连着 USB 就执行 adb reverse，手机用 127.0.0.1:8787 也能取
    · 打印本机局域网地址，手机端「自动查找」找不到时手填这个
#>
param(
    [int]$Port = 8787,
    [switch]$Bg
)

$ErrorActionPreference = "Stop"

function Find-Root {
    # 脚本可能在 <root>\scripts 或 <root>\开发文件\scripts，往上找到含 novel_dev 的那层
    $d = $PSScriptRoot
    for ($i = 0; $i -lt 4; $i++) {
        if (Test-Path (Join-Path $d "novel_dev\gradlew.bat")) { return $d }
        $d = Split-Path -Parent $d
        if (-not $d) { break }
    }
    return (Split-Path -Parent $PSScriptRoot)
}
$root = Find-Root
$updateDir = Join-Path $root "update"

if (-not (Test-Path (Join-Path $updateDir "manifest.json"))) {
    Write-Host "update\manifest.json 还不存在，先跑一次 publish-update.ps1" -ForegroundColor Yellow
    New-Item -ItemType Directory -Force -Path $updateDir | Out-Null
}

$adb = "E:\Android\Sdk\platform-tools\adb.exe"

function Show-Addresses {
    Write-Host ""
    Write-Host "  更新服务已开：$updateDir" -ForegroundColor Green
    $ips = Get-NetIPAddress -AddressFamily IPv4 |
        Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254*' -and $_.IPAddress -notlike '198.18.*' } |
        Select-Object -ExpandProperty IPAddress
    foreach ($ip in $ips) {
        Write-Host "    手机填：http://${ip}:$Port" -ForegroundColor Yellow
    }
    Write-Host "    或者手机插着 USB：http://127.0.0.1:$Port" -ForegroundColor DarkGray
    Write-Host ""
}

if (Test-Path $adb) {
    $devices = & $adb devices | Select-String "device$"
    if ($devices) {
        & $adb reverse "tcp:$Port" "tcp:$Port" | Out-Null
        Write-Host "  adb reverse 已设好，手机可以直接用 127.0.0.1:$Port" -ForegroundColor DarkGray
    }
}

Show-Addresses

$py = (Get-Command python -ErrorAction SilentlyContinue)
if (-not $py) { throw "需要 python，PATH 里没找到" }

if ($Bg) {
    Start-Process -FilePath $py.Source -ArgumentList @("-m", "http.server", "$Port", "--bind", "0.0.0.0", "--directory", $updateDir) -WindowStyle Hidden
    Write-Host "  已在后台启动（端口 $Port）" -ForegroundColor Green
    Write-Host "  关掉：Get-NetTCPConnection -LocalPort $Port | ForEach-Object { Stop-Process -Id `$_.OwningProcess -Force }" -ForegroundColor DarkGray
} else {
    Write-Host "  Ctrl+C 停止" -ForegroundColor DarkGray
    & $py.Source -m http.server $Port --bind 0.0.0.0 --directory $updateDir
}

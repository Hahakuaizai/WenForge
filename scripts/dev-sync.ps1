#requires -Version 7.0
<#
  dev-sync.ps1 — 开发同步（新版更新通道，一个脚本全包）

  跑一条命令，之后改代码存盘就行，别的什么都不用管：

    pwsh -File scripts\dev-sync.ps1

  它会一直开着，循环做三件事：
    1. 盯着源码文件（app/src + 几个 gradle 文件），存盘后等 4 秒存稳了
    2. 自动升版本号 → 编译 debug APK → 写 update\manifest.json（带 SHA-256）
    3. 在 8787 端口把 update\ 发给手机（手机端启动会自动检查，也可以手动查）

  手机怎么找到它：
    · USB 连着：自动 adb reverse，手机地址用默认的 http://127.0.0.1:8787
    · 同一个 WiFi：手机端「自动查找」（先认 /ping 身份再确认），或手填脚本打印的地址

  参数：
    -Port 9000      换端口（手机端地址跟着改）
    -ServeOnly      只发服务，不盯文件（等同旧 serve-update.ps1）
    -Force          启动时不管有没有新提交，先强制发一版

  停掉：Ctrl+C。第一次跑 Windows 防火墙会问，选「允许」（专用网络）就行。
  旧的 publish-update.ps1 / serve-update.ps1 还在，习惯那套也能继续用，
  但两套别同时开（会抢 8787 端口和 update\ 目录）。
#>
param(
    [int]$Port = 8787,
    [switch]$ServeOnly,
    [switch]$Force,
    [int]$DebounceSeconds = 4
)

$ErrorActionPreference = "Stop"

# git / 控制台中文防乱码
try {
    [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
    $OutputEncoding = [System.Text.Encoding]::UTF8
} catch { }
$env:GIT_PAGER = ""

function Find-Project {
    # 从脚本所在目录往上找带 gradlew.bat + app\build.gradle.kts 的那层 = 项目根
    $d = $PSScriptRoot
    for ($i = 0; $i -lt 5; $i++) {
        if ((Test-Path (Join-Path $d "gradlew.bat")) -and (Test-Path (Join-Path $d "app\build.gradle.kts"))) {
            return $d
        }
        $d = Split-Path -Parent $d
        if (-not $d) { break }
    }
    return (Split-Path -Parent $PSScriptRoot)
}

$proj = Find-Project
$gradleFile = Join-Path $proj "app\build.gradle.kts"
$updateDir = Join-Path $proj "update"
$manifestFile = Join-Path $updateDir "manifest.json"
$stateFile = Join-Path $updateDir "last-commit.txt"
New-Item -ItemType Directory -Force -Path $updateDir | Out-Null

function Write-Step($msg) { Write-Host "  $msg" -ForegroundColor Cyan }

# ---------- 版本与发布（沿用 publish-update.ps1 的做法，加了 SHA-256） ----------

function Get-BuildInfo {
    $text = Get-Content $gradleFile -Raw
    $code = [int]([regex]::Match($text, "versionCode\s*=\s*(\d+)").Groups[1].Value)
    $name = [regex]::Match($text, 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
    return @{ Code = $code; Name = $name }
}

function Set-BuildInfo([int]$code, [string]$name) {
    $text = Get-Content $gradleFile -Raw
    $text = [regex]::Replace($text, "versionCode\s*=\s*\d+", "versionCode = $code")
    $text = [regex]::Replace($text, 'versionName\s*=\s*"[^"]+"', "versionName = `"$name`"")
    Set-Content -Path $gradleFile -Value $text -Encoding utf8NoBOM -NoNewline
}

function Bump-Version([string]$name) {
    $parts = $name.Split(".")
    if ($parts.Count -ge 3) {
        $parts[2] = [string]([int]$parts[2] + 1)
    } else {
        $parts += "1"
    }
    return ($parts -join ".")
}

function Get-GitHead {
    try { return (git -C $proj rev-parse HEAD 2>$null).Trim() } catch { return "" }
}

function Get-Changelog([string]$from, [string]$to) {
    if (-not $to) { return @() }
    try {
        if ([string]::IsNullOrWhiteSpace($from)) {
            $raw = git -C $proj log --no-merges --pretty=format:"%s" -n 8 $to 2>$null
        } else {
            $raw = git -C $proj log --no-merges --pretty=format:"%s" "$from..$to" 2>$null
        }
    } catch { return @() }
    if (-not $raw) { return @() }
    return @($raw -split "`n" | ForEach-Object { $_.Trim() } | Where-Object { $_ })
}

function Publish-Once([string[]]$Notes) {
    $info = Get-BuildInfo
    $newCode = $info.Code + 1
    $newName = Bump-Version $info.Name
    Set-BuildInfo $newCode $newName
    Write-Step "版本升到 $newName (build $newCode)"

    Write-Step "编译中…"
    if (-not $env:JAVA_HOME -and (Test-Path "E:\Android\jdk-21.0.2")) { $env:JAVA_HOME = "E:\Android\jdk-21.0.2" }
    if (-not $env:ANDROID_HOME -and (Test-Path "E:\Android\Sdk")) { $env:ANDROID_HOME = "E:\Android\Sdk" }
    Push-Location $proj
    try {
        $out = & ".\gradlew.bat" assembleDebug --console=plain 2>&1
        $buildCode = $LASTEXITCODE
    } finally {
        Pop-Location
    }
    $okByText = ($out | Select-String -SimpleMatch "BUILD SUCCESSFUL").Count -gt 0
    if (-not (($buildCode -eq 0) -or $okByText)) {
        $out | Select-Object -Last 12 | ForEach-Object { Write-Host "    $_" -ForegroundColor DarkGray }
        throw "编译失败，没发布（版本号已升到 $newName，下次发布会接着升）"
    }

    $apk = Join-Path $proj "app\build\outputs\apk\debug\app-debug.apk"
    if (-not (Test-Path $apk)) { throw "没找到 APK：$apk" }

    $apkName = "app-$newName.apk"
    $apkDest = Join-Path $updateDir $apkName
    Copy-Item $apk $apkDest -Force
    $size = (Get-Item $apkDest).Length
    $sha = (Get-FileHash $apkDest -Algorithm SHA256).Hash.ToLower()

    $entry = [ordered]@{
        versionName = $newName
        versionCode = $newCode
        time        = (Get-Date).ToString("yyyy-MM-dd HH:mm")
        notes       = @($Notes)
        file        = $apkName
        size        = $size
        sha256      = $sha
    }

    $history = @()
    if (Test-Path $manifestFile) {
        try {
            $old = Get-Content $manifestFile -Raw | ConvertFrom-Json
            if ($old.history) { $history = @($old.history) }
        } catch { $history = @() }
    }
    $history = @($entry) + $history | Select-Object -First 10

    $manifest = [ordered]@{
        app         = "wenforge-update"
        generatedAt = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
        history     = [object[]]@($history)
    }
    $manifest | ConvertTo-Json -Depth 6 | Set-Content -Path $manifestFile -Encoding utf8NoBOM

    $head = Get-GitHead
    if ($head) { Set-Content -Path $stateFile -Value $head -Encoding utf8NoBOM -NoNewline }

    $keep = @($history | ForEach-Object { $_.file })
    Get-ChildItem $updateDir -Filter "app-*.apk" | Where-Object { $keep -notcontains $_.Name } |
        ForEach-Object { Remove-Item $_.FullName -Force }

    Write-Host "  发布完成：$newName (build $newCode)  $([math]::Round($size / 1MB, 1)) MB" -ForegroundColor Green
    foreach ($c in $Notes) { Write-Host "    · $c" -ForegroundColor DarkGray }
    Write-Host "  手机打开 App 会自动提示，也可以去 我的 → 版本与更新" -ForegroundColor Yellow
    Write-Host ""
}

# ---------- 文件监听：源码快照（路径|大小|修改时间），变了就算改动 ----------

function Get-WatchSnapshot {
    $files = @()
    $srcDir = Join-Path $proj "app\src"
    if (Test-Path $srcDir) {
        $files += Get-ChildItem $srcDir -Recurse -File -ErrorAction SilentlyContinue
    }
    foreach ($f in @("app\build.gradle.kts", "build.gradle.kts", "settings.gradle.kts", "gradle.properties")) {
        $full = Join-Path $proj $f
        if (Test-Path $full) { $files += Get-Item $full }
    }
    $map = [ordered]@{}
    foreach ($f in ($files | Sort-Object FullName)) {
        $rel = $f.FullName.Substring($proj.Length).TrimStart('\', '/')
        $map[$rel] = "$($f.Length)|$($f.LastWriteTimeUtc.Ticks)"
    }
    return $map
}

function Get-SnapshotText($map) {
    return (($map.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }) -join "`n")
}

# ---------- 极简 HTTP 服务（TcpListener 手写，不用 python、不用管理员权限） ----------

function Send-Bytes($stream, [byte[]]$bytes) {
    $stream.Write($bytes, 0, $bytes.Length)
    $stream.Flush()
}

function Send-Response($stream, [int]$status, [string]$contentType, [byte[]]$body) {
    $reason = @{ 200 = "OK"; 404 = "Not Found"; 400 = "Bad Request" }[$status]
    $head = "HTTP/1.1 $status $reason`r`nContent-Type: $contentType`r`nContent-Length: $($body.Length)`r`nCache-Control: no-store`r`nConnection: close`r`n`r`n"
    Send-Bytes $stream ([System.Text.Encoding]::ASCII.GetBytes($head))
    if ($body.Length -gt 0) { Send-Bytes $stream $body }
}

function Send-FileResponse($stream, [string]$file, [string]$contentType) {
    $len = (Get-Item $file).Length
    $head = "HTTP/1.1 200 OK`r`nContent-Type: $contentType`r`nContent-Length: $len`r`nCache-Control: no-store`r`nConnection: close`r`n`r`n"
    Send-Bytes $stream ([System.Text.Encoding]::ASCII.GetBytes($head))
    $fs = [IO.File]::OpenRead($file)
    try { $fs.CopyTo($stream, 65536) } finally { $fs.Dispose() }
    $stream.Flush()
}

function Handle-Client($client) {
    try {
        $client.ReceiveTimeout = 5000
        $client.SendTimeout = 60000
        $stream = $client.GetStream()

        # 读请求头（读到空行为止，最多 16KB）
        $buf = New-Object byte[] 16384
        $total = 0
        while ($total -lt $buf.Length) {
            if (-not $stream.DataAvailable) {
                $waited = 0
                while (-not $stream.DataAvailable -and $waited -lt 3000) {
                    Start-Sleep -Milliseconds 50
                    $waited += 50
                }
                if (-not $stream.DataAvailable) { return }
            }
            $read = $stream.Read($buf, $total, $buf.Length - $total)
            if ($read -le 0) { break }
            $total += $read
            $text = [System.Text.Encoding]::ASCII.GetString($buf, 0, $total)
            if ($text.Contains("`r`n`r`n")) { break }
        }
        if ($total -le 0) { return }
        $reqText = [System.Text.Encoding]::ASCII.GetString($buf, 0, $total)
        $firstLine = ($reqText -split "`r`n")[0]
        $parts = $firstLine -split " "
        if ($parts.Count -lt 2) {
            Send-Response $stream 400 "text/plain" ([System.Text.Encoding]::UTF8.GetBytes("bad request"))
            return
        }
        $rawPath = ($parts[1] -split "\?")[0]
        $path = [Uri]::UnescapeDataString($rawPath).TrimStart('/')

        if ($path -eq "ping") {
            $json = '{"app":"wenforge-update","version":2}'
            Send-Response $stream 200 "application/json" ([System.Text.Encoding]::UTF8.GetBytes($json))
        } elseif ($path -eq "manifest.json") {
            if (Test-Path $manifestFile) {
                Send-FileResponse $stream $manifestFile "application/json"
            } else {
                Send-Response $stream 404 "text/plain" ([System.Text.Encoding]::UTF8.GetBytes("no manifest yet"))
            }
        } elseif ($path -match '^app-[\w.\-]+\.apk$') {
            $file = Join-Path $updateDir ([IO.Path]::GetFileName($path))
            if (Test-Path $file) {
                Send-FileResponse $stream $file "application/vnd.android.package-archive"
            } else {
                Send-Response $stream 404 "text/plain" ([System.Text.Encoding]::UTF8.GetBytes("no such apk"))
            }
        } else {
            Send-Response $stream 404 "text/plain" ([System.Text.Encoding]::UTF8.GetBytes("not found"))
        }
    } catch {
        # 手机断开/超时很常见，不值得刷屏
    } finally {
        try { $client.Close() } catch { }
    }
}

# ---------- 启动 ----------

Write-Host ""
Write-Host "  文枢 · 开发同步" -ForegroundColor Green
Write-Host "  项目：$proj"
Write-Host "  更新目录：$updateDir"
Write-Host ""

# adb reverse（USB 连着时手机用 127.0.0.1 就能取）
$adb = $null
$adbCmd = Get-Command adb -ErrorAction SilentlyContinue
if ($adbCmd) { $adb = $adbCmd.Source }
elseif (Test-Path "E:\Android\Sdk\platform-tools\adb.exe") { $adb = "E:\Android\Sdk\platform-tools\adb.exe" }
if ($adb) {
    try {
        $devices = & $adb devices 2>$null | Select-String "device$"
        if ($devices) {
            & $adb reverse "tcp:$Port" "tcp:$Port" | Out-Null
            Write-Host "  adb reverse 已设好（USB）：手机地址用 http://127.0.0.1:$Port" -ForegroundColor DarkGray
        }
    } catch { }
}

# 启动先看一眼：没有 manifest、或有新提交没发过 → 先发一版
$needInitial = $Force -or (-not (Test-Path $manifestFile))
if (-not $needInitial) {
    $head = Get-GitHead
    $last = if (Test-Path $stateFile) { (Get-Content $stateFile -Raw).Trim() } else { "" }
    if ($head -and $head -ne $last) { $needInitial = $true }
}
if ($needInitial) {
    try {
        $last = if (Test-Path $stateFile) { (Get-Content $stateFile -Raw).Trim() } else { "" }
        $notes = Get-Changelog $last (Get-GitHead)
        if ($notes.Count -eq 0) { $notes = @("本地改动同步") }
        Publish-Once $notes
    } catch {
        Write-Host "  首次发布失败：$_" -ForegroundColor Red
        Write-Host "  服务会继续开着，改好代码存盘会自动重试。" -ForegroundColor Yellow
    }
} else {
    $info = Get-BuildInfo
    Write-Host "  当前已发布 $($info.Name) (build $($info.Code))，没有待发的新提交" -ForegroundColor DarkGray
}

$listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Any, $Port)
try {
    $listener.Start()
} catch {
    throw "端口 $Port 开不了（多半是旧脚本还开着）：$_"
}

Write-Host ""
Write-Host "  更新服务已开（端口 $Port）：" -ForegroundColor Green
$ips = Get-NetIPAddress -AddressFamily IPv4 |
    Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254*' -and $_.IPAddress -notlike '198.18.*' } |
    Select-Object -ExpandProperty IPAddress
foreach ($ip in $ips) {
    Write-Host "    手机填：http://${ip}:$Port" -ForegroundColor Yellow
}
Write-Host "    或手机端点「自动查找」" -ForegroundColor DarkGray
if ($ServeOnly) {
    Write-Host "  ServeOnly：只发服务，不盯文件改动" -ForegroundColor DarkGray
} else {
    Write-Host "  正在盯着源码… 改完存盘，约 $DebounceSeconds 秒后自动编译发布。Ctrl+C 停" -ForegroundColor DarkGray
}
Write-Host ""

$baseline = Get-WatchSnapshot
$baselineText = Get-SnapshotText $baseline
$dirtySince = $null
$pendingFiles = @{}
$lastPoll = Get-Date

try {
    while ($true) {
        while ($listener.Pending()) {
            $client = $listener.AcceptTcpClient()
            Handle-Client $client
        }

        if (-not $ServeOnly) {
            $now = Get-Date
            if (($now - $lastPoll).TotalSeconds -ge 2.5) {
                $lastPoll = $now
                $snap = Get-WatchSnapshot
                $snapText = Get-SnapshotText $snap
                if ($snapText -ne $baselineText) {
                    # 记下这一轮动过的文件（写更新说明用）
                    foreach ($k in $snap.Keys) {
                        if (-not $baseline.Contains($k) -or $baseline[$k] -ne $snap[$k]) {
                            $pendingFiles[$k] = $true
                        }
                    }
                    foreach ($k in @($baseline.Keys)) {
                        if (-not $snap.Contains($k)) { $pendingFiles[$k] = $true }
                    }
                    $baseline = $snap
                    $baselineText = $snapText
                    $dirtySince = $now
                    Write-Host "[$(Get-Date -Format 'HH:mm:ss')] 检测到改动，等存稳…" -ForegroundColor DarkGray
                } elseif ($dirtySince -and ($now - $dirtySince).TotalSeconds -ge $DebounceSeconds) {
                    $dirtySince = $null
                    $names = @($pendingFiles.Keys | ForEach-Object { [IO.Path]::GetFileName($_) } | Select-Object -Unique)
                    $pendingFiles = @{}
                    $notes = @()
                    $last = if (Test-Path $stateFile) { (Get-Content $stateFile -Raw).Trim() } else { "" }
                    $notes += Get-Changelog $last (Get-GitHead)
                    if ($names.Count -gt 0) {
                        $show = ($names | Select-Object -First 3) -join "、"
                        if ($names.Count -gt 3) { $show += " 等 $($names.Count) 个文件" }
                        $notes = @("同步改动：$show") + $notes
                    }
                    if ($notes.Count -eq 0) { $notes = @("本地改动同步") }
                    Write-Host "[$(Get-Date -Format 'HH:mm:ss')] 开始发布…" -ForegroundColor Cyan
                    try {
                        Publish-Once $notes
                    } catch {
                        Write-Host "  发布失败：$_" -ForegroundColor Red
                    }
                    # 发布过程会改 build.gradle.kts（升版本号），重新对齐基线防自触发
                    $baseline = Get-WatchSnapshot
                    $baselineText = Get-SnapshotText $baseline
                }
            }
        }

        Start-Sleep -Milliseconds 120
    }
} finally {
    $listener.Stop()
}

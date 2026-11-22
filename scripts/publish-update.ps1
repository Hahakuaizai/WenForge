#requires -Version 7.0
<#
  publish-update.ps1  —  改完代码跑一下，手机上就能收到更新

  做什么：
    1. 看 git 有没有新提交，没新东西就跳过（-Force 可强制）
    2. versionCode +1，versionName 的末位 +1
    3. 编译 debug APK
    4. 把 APK 和 manifest.json 放进 update\ 目录，保留最近 10 个版本

  用法：
    pwsh -File scripts\publish-update.ps1
    pwsh -File scripts\publish-update.ps1 -Notes "书架：封面重做"
    pwsh -File scripts\publish-update.ps1 -Watch          # 盯着 git，有新提交自动发布
#>
param(
    [string]$Notes = "",
    [switch]$Force,
    [switch]$NoBuild,
    [switch]$NoBump,
    [switch]$Watch,
    [int]$IntervalSeconds = 5
)

$ErrorActionPreference = "Stop"

function Find-Root {
    # 脚本在 <root>/开发文件/novel_dev/scripts。项目根 = 有 安装包 或 update 的那层
    $d = $PSScriptRoot
    for ($i = 0; $i -lt 6; $i++) {
        if ((Test-Path (Join-Path $d "安装包")) -or (Test-Path (Join-Path $d "update"))) { return $d }
        $d = Split-Path -Parent $d
        if (-not $d) { break }
    }
    return (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
}

function Find-Project {
    # 本项目就在 <root>/开发文件/novel_dev 或 <root>/novel_dev
    param([string]$Root)
    $a = Join-Path $Root "开发文件"
    $b = Join-Path $a "novel_dev"
    if (Test-Path (Join-Path $b "gradlew.bat")) { return $b }
    $c = Join-Path $Root "novel_dev"
    if (Test-Path (Join-Path $c "gradlew.bat")) { return $c }
    return (Split-Path -Parent $PSScriptRoot)
}
# git 输出是 UTF-8，控制台默认按 GBK 解，会把中文提交说明弄成乱码
try {
    [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
    $OutputEncoding = [System.Text.Encoding]::UTF8
} catch { }
$env:GIT_PAGER = ""
$root = Find-Root
$proj = Find-Project $root
$gradleFile = Join-Path $proj "app\build.gradle.kts"
$updateDir = Join-Path $root "update"
$stateFile = Join-Path $updateDir "last-commit.txt"
$manifestFile = Join-Path $updateDir "manifest.json"

function Write-Step($msg) { Write-Host "  $msg" -ForegroundColor Cyan }

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

function Get-Changelog([string]$from, [string]$to) {
    if ([string]::IsNullOrWhiteSpace($from)) {
        $raw = git -C $proj log --no-merges --pretty=format:"%s" -n 8 $to
    } else {
        $raw = git -C $proj log --no-merges --pretty=format:"%s" "$from..$to"
    }
    if (-not $raw) { return @() }
    return @($raw -split "`n" | ForEach-Object { $_.Trim() } | Where-Object { $_ })
}

function Publish-Once {
    Push-Location $root
    try {
        $info = Get-BuildInfo
        Write-Step "当前 $($info.Name) (build $($info.Code))"

        $head = (git -C $proj rev-parse HEAD).Trim()
        $last = if (Test-Path $stateFile) { (Get-Content $stateFile -Raw).Trim() } else { "" }

        if (-not $Force -and $last -eq $head) {
            Write-Host "  没有新改动，跳过。(-Force 可以强制发布)" -ForegroundColor DarkGray
            return $false
        }

        $changes = Get-Changelog $last $head
        if ($Notes) { $changes = @($Notes) + $changes }
        if ($changes.Count -eq 0) { $changes = @("杂项调整") }

        if ($NoBump) {
            $newCode = $info.Code
            $newName = $info.Name
        } else {
            $newCode = $info.Code + 1
            $newName = Bump-Version $info.Name
            Set-BuildInfo $newCode $newName
        }
        Write-Step "版本升到 $newName (build $newCode)"

        if (-not $NoBuild) {
            Write-Step "编译中…"
            $env:JAVA_HOME = "E:\Android\jdk-21.0.2"
            $env:ANDROID_HOME = "E:\Android\Sdk"
            Push-Location $proj
            try {
                $out = & ".\gradlew.bat" assembleDebug --console=plain 2>&1
                $buildCode = $LASTEXITCODE
            } finally {
                Pop-Location
            }
            # 有些环境拿不到退出码，退回看输出里有没有 BUILD SUCCESSFUL
            $okByText = ($out | Select-String -SimpleMatch "BUILD SUCCESSFUL").Count -gt 0
            $okByCode = ($buildCode -eq 0)
            if (-not ($okByCode -or $okByText)) {
                $out | Select-Object -Last 12 | ForEach-Object { Write-Host "    $_" -ForegroundColor DarkGray }
                throw "编译失败，没发布"
            }
        }

        $apk = Join-Path $proj "app\build\outputs\apk\debug\app-debug.apk"
        if (-not (Test-Path $apk)) { throw "没找到 APK：$apk" }

        New-Item -ItemType Directory -Force -Path $updateDir | Out-Null
        $apkName = "app-$newName.apk"
        Copy-Item $apk (Join-Path $updateDir $apkName) -Force
        $size = (Get-Item (Join-Path $updateDir $apkName)).Length

        $entry = [ordered]@{
            versionName = $newName
            versionCode = $newCode
            time        = (Get-Date).ToString("yyyy-MM-dd HH:mm")
            notes       = @($changes)
            file        = $apkName
            size        = $size
        }

        $history = @()
        if (Test-Path $manifestFile) {
            try {
                $old = Get-Content $manifestFile -Raw | ConvertFrom-Json
                if ($old.history) { $history = @($old.history) }
            } catch { $history = @() }
        }
        $history = @($entry) + $history
        $history = $history | Select-Object -First 10

        $manifest = [ordered]@{
            app         = "novel-forge"
            generatedAt = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
            history     = [object[]]@($history)
        }
        $manifest | ConvertTo-Json -Depth 6 | Set-Content -Path $manifestFile -Encoding utf8NoBOM
        Set-Content -Path $stateFile -Value $head -Encoding utf8NoBOM -NoNewline

        # 旧的 APK 只留 manifest 里提到的
        $keep = @($history | ForEach-Object { $_.file })
        Get-ChildItem $updateDir -Filter "app-*.apk" | Where-Object { $keep -notcontains $_.Name } |
            ForEach-Object { Remove-Item $_.FullName -Force }

        Write-Host ""
        Write-Host "  发布完成：$newName (build $newCode)  $([math]::Round($size/1MB,1)) MB" -ForegroundColor Green
        foreach ($c in $changes) { Write-Host "    · $c" -ForegroundColor DarkGray }
        Write-Host ""
        Write-Host "  手机端：我的 → 版本与更新 → 检查更新" -ForegroundColor Yellow
        return $true
    } finally {
        Pop-Location
    }
}

if ($Watch) {
    Write-Host "盯着 git…（Ctrl+C 停）" -ForegroundColor Yellow
    while ($true) {
        $head = (git -C $proj rev-parse HEAD).Trim()
        $last = if (Test-Path $stateFile) { (Get-Content $stateFile -Raw).Trim() } else { "" }
        if ($head -ne $last) {
            Write-Host "[$(Get-Date -Format 'HH:mm:ss')] 发现新提交 $($head.Substring(0,7))"
            try { Publish-Once | Out-Null } catch { Write-Host "  失败：$_" -ForegroundColor Red }
        }
        Start-Sleep -Seconds $IntervalSeconds
    }
} else {
    Publish-Once | Out-Null
}

#requires -Version 7.0
<#
  release-github.ps1 — 发一个 GitHub Release（用户更新渠道）

  用法：
    pwsh -File scripts\release-github.ps1              # 正式发一版
    pwsh -File scripts\release-github.ps1 -Draft       # 先发成草稿，自己检查完再点发布
    pwsh -File scripts\release-github.ps1 -Notes "想自己写的说明第一行"

  做什么：
    1. 更新说明 = 上一个 tag 到现在的所有 git 提交标题（真实记录，不编造）
    2. versionCode +1、versionName 末位 +1，只提交这一个版本号改动，打 tag
    3. 编译 debug APK，附件名带上 versionCode：wenforge-v<版本>-<code>.apk
    4. gh release create：正文 = 更新说明；App 端查到就能直接下载安装

  App 端认这三样，缺一不可（GithubUpdate.fetchLatest）：
    · tag = v<versionName>
    · 附件名里的 -<versionCode>.apk
    · Release 正文（会原样显示成更新内容，# 和 - 会被 App 剥掉）
#>
param(
    [string]$Notes = "",
    [switch]$Draft,
    [switch]$Force
)

$ErrorActionPreference = "Stop"

try {
    [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
    $OutputEncoding = [System.Text.Encoding]::UTF8
} catch { }
$env:GIT_PAGER = ""

function Find-Project {
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

function Find-Gh {
    $c = Get-Command gh -ErrorAction SilentlyContinue
    if ($c) { return $c.Source }
    if (Test-Path "C:\Users\30894\tools\gh\bin\gh.exe") { return "C:\Users\30894\tools\gh\bin\gh.exe" }
    return $null
}

$proj = Find-Project
$gradleFile = Join-Path $proj "app\build.gradle.kts"
$gh = Find-Gh
if (-not $gh) { throw "找不到 gh（GitHub CLI），先装好并 gh auth login" }

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
    if ($parts.Count -ge 3) { $parts[2] = [string]([int]$parts[2] + 1) } else { $parts += "1" }
    return ($parts -join ".")
}

# 1) 更新说明：上一个 tag 以来的提交标题（真实记录）
$lastTag = ""
try { $lastTag = (git -C $proj describe --tags --abbrev=0 2>$null).Trim() } catch { }
$range = if ($lastTag) { "$lastTag..HEAD" } else { "HEAD" }
$logArgs = @("-C", $proj, "log", "--no-merges", "--pretty=format:%s")
if ($lastTag) { $logArgs += $range } else { $logArgs += @("-n", "15") }
$subjects = @(& git @logArgs 2>$null | ForEach-Object { $_.Trim() } |
    Where-Object { $_ -and $_ -notmatch "^发布 v" } | Select-Object -Unique)

if ($subjects.Count -eq 0 -and -not $Force -and -not $Notes) {
    Write-Host "  自上个 tag（$lastTag）以来没有新提交，不发版。(-Force 可强制)" -ForegroundColor Yellow
    return
}

$noteLines = @()
if ($Notes) { $noteLines += $Notes }
$noteLines += $subjects
if ($noteLines.Count -eq 0) { $noteLines = @("杂项调整") }

# 2) 升版本 + 提交 + 打 tag
$info = Get-BuildInfo
$newCode = $info.Code + 1
$newName = Bump-Version $info.Name
$tag = "v$newName"
Set-BuildInfo $newCode $newName
Write-Step "版本 $($info.Name) → $newName (build $newCode)，tag $tag"

git -C $proj add -- "app/build.gradle.kts" | Out-Null
git -C $proj commit -m "发布 $tag（build $newCode）" | Out-Null
git -C $proj tag $tag
try { git -C $proj push | Out-Null; git -C $proj push origin $tag | Out-Null }
catch { Write-Host "  push 失败（网络？），本地 tag 已打好，等下手动 push 也行" -ForegroundColor Yellow }

# 3) 编译
Write-Step "编译中…"
if (-not $env:JAVA_HOME -and (Test-Path "E:\Android\jdk-21.0.2")) { $env:JAVA_HOME = "E:\Android\jdk-21.0.2" }
if (-not $env:ANDROID_HOME -and (Test-Path "E:\Android\Sdk")) { $env:ANDROID_HOME = "E:\Android\Sdk" }
Push-Location $proj
try {
    $out = & ".\gradlew.bat" assembleDebug --console=plain 2>&1
    $buildCode = $LASTEXITCODE
} finally { Pop-Location }
$okByText = ($out | Select-String -SimpleMatch "BUILD SUCCESSFUL").Count -gt 0
if (-not (($buildCode -eq 0) -or $okByText)) {
    $out | Select-Object -Last 12 | ForEach-Object { Write-Host "    $_" -ForegroundColor DarkGray }
    throw "编译失败，没发 Release（版本号和 tag 已经升了，下次会接着往后走）"
}

$apk = Join-Path $proj "app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $apk)) { throw "没找到 APK：$apk" }
$assetName = "wenforge-v$newName-$newCode.apk"
$assetPath = Join-Path ([IO.Path]::GetTempPath()) $assetName
Copy-Item $apk $assetPath -Force

# 4) 发 Release：正文就是更新说明，App 里原样展示
$body = "## 更新内容`n`n" + (($noteLines | ForEach-Object { "- $_" }) -join "`n")
$createArgs = @("release", "create", $tag, $assetPath,
    "--title", "WenForge $tag",
    "--notes", $body)
if ($Draft) { $createArgs += "--draft" }
Push-Location $proj
try {
    $url = & $gh @createArgs
} finally { Pop-Location }

Write-Host ""
Write-Host "  发布完成：$tag (build $newCode)" -ForegroundColor Green
foreach ($n in $noteLines) { Write-Host "    · $n" -ForegroundColor DarkGray }
Write-Host "  $url" -ForegroundColor Yellow
Write-Host "  手机打开 App 就会提示更新（或 我的 → 版本与更新 手动查）" -ForegroundColor DarkGray
Remove-Item $assetPath -Force -ErrorAction SilentlyContinue

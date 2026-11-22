# 手机上拿最新版（自己电脑发更新）

## 新版：一条命令全自动（推荐）

```
pwsh -File scripts\dev-sync.ps1
```

窗口开着别关。之后**改完代码存盘就行**：

1. 脚本盯着源码文件，存盘后等 4 秒（等你存稳）；
2. 自动升版本号 → 编译 → 把 APK 和清单放进项目里的 `update\` 目录（清单带 SHA-256，手机下载完会校验，传坏了会提示重下）；
3. 服务一直开在 8787 端口，手机打开 App 会**自动检查**，有新版弹一次提示，点「去更新」→「下载并安装」。

细节：

- 不用先 git 提交，**未提交的改动也能同步**；更新说明会自动写「同步改动：动过的文件名」，有新提交时带上提交标题。
- 只保留最近 10 个版本，旧 APK 自动删。
- 第一次跑，Windows 防火墙会问一句，选允许（专用网络）。
- 换端口：`pwsh -File scripts\dev-sync.ps1 -Port 9000`，手机端地址跟着改。
- 只想开服务不盯文件：加 `-ServeOnly`。

## 手机怎么找到电脑

1. **USB 连着最稳**：脚本会自动执行 `adb reverse tcp:8787 tcp:8787`，
   手机端地址填 `http://127.0.0.1:8787`（默认就是它）。
2. **同一个 WiFi**：手机端点「自动查找」（先认服务的 `/ping` 身份再确认，不会认错别的服务），
   或手填脚本打印出来的 `http://192.168.x.x:8787`。

## 旧版：两步手动（还在，能用）

```
# 1. 发一个版本（会自动 +1 版本号，写改动说明）
pwsh -File scripts\publish-update.ps1

# 2. 开更新服务（窗口别关）
pwsh -File scripts\serve-update.ps1

# 3. 手机上：我的 → 版本与更新 → 检查更新 → 下载并安装
```

- `publish-update.ps1` 会看 git 有没有新提交，没有就跳过（`-Force` 强制发；`-Watch` 是每 5 秒看一次 git）。
- 改动说明默认取 git 提交标题，想自己写：`-Notes "改了什么"`。
- 旧脚本的更新目录在项目外层，新版在项目内的 `update\`，**两套别同时开**（抢端口、抢目录）。

## 停掉后台服务

```
Get-NetTCPConnection -LocalPort 8787 | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }
```

---

## 追加（2026-11-22 晚）：双渠道 + 一键更新

### 渠道二：GitHub Releases（电脑不开着也能更）

发版工具（更新说明直接取 git 提交标题，是真实记录，不编造）：

```powershell
pwsh -File scripts\release-github.ps1            # 正式发一版
pwsh -File scripts\release-github.ps1 -Draft     # 先发草稿，检查完再发布
```

它做的事：取上一个 tag 以来的提交标题当更新说明 → 版本 +1 并提交、打 tag →
编译 → 附件命名为 `wenforge-v<版本>-<versionCode>.apk` → `gh release create`，
Release 正文就是更新说明。

手机端查更新时两个渠道都查，谁的 versionCode 新用谁；GitHub 的附件带
SHA-256（接口 digest），下载后照样校验。弹框里会标明来源（来自 GitHub /
来自你的电脑）。

### 一键更新

启动静默检查发现新版后，App 会先在后台把 APK 下好；弹框点「立即更新」
就直接跳系统安装，不再先进「版本与更新」页等下载。没预下完时点更新，
弹框里直接显示下载进度。

### 入口位置

「我的」只留 模型设置 / 用量统计 / 全局设置；版本与更新、存储管理都进了
全局设置页顶部（有新版时更新那行带红点）。

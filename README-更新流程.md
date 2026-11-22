# 手机上拿最新版（自己电脑发更新）

## 三步

```
# 1. 发一个版本（会自动 +1 版本号，写改动说明）
pwsh -File scripts\publish-update.ps1

# 2. 开更新服务（窗口别关）
pwsh -File scripts\serve-update.ps1

# 3. 手机上：我的 → 版本与更新 → 检查更新 → 下载并安装
```

装完 App 会自己重启，不用回桌面找图标。

## 说明

- `publish-update.ps1` 会看 git 有没有新提交，没有就跳过（`-Force` 强制发）。
- 改动说明默认取 git 提交标题，想自己写：`-Notes "改了什么"`。
- 只保留最近 10 个版本，旧的 APK 自动删。
- 生成的东西都在 `update\`：`manifest.json` + `app-x.y.z.apk`。
- 手机端按 `manifest.json` 的版本号比较，比当前新才给「下载并安装」。
- 设置页「更新记录」会留下最近 10 次的版本号和改动。

## 想让改完代码就自动发

```
pwsh -File scripts\publish-update.ps1 -Watch
```

每 5 秒看一次 git，有新提交就自动编译发布。配合 `serve-update.ps1` 常驻，改完手机上点一下就能拿到。

## 手机怎么找到电脑

1. **USB 连着最稳**：`serve-update.ps1` 会自动执行 `adb reverse tcp:8787 tcp:8787`，
   手机端地址填 `http://127.0.0.1:8787`（默认就是它）。
2. **同一个 WiFi**：手机端点「自动查找」，或手填脚本打印出来的 `http://192.168.x.x:8787`。

## 换端口

```
pwsh -File scripts\serve-update.ps1 -Port 9000
```

手机端地址跟着改成 `http://127.0.0.1:9000`。

## 停掉后台服务

```
Get-NetTCPConnection -LocalPort 8787 | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }
```

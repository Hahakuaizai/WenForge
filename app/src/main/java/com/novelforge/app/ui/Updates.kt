package com.novelforge.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.novelforge.app.data.UpdateRecord

@Composable
fun UpdatesScreen(vm: AppViewModel, onBack: () -> Unit) {
    var server by remember { mutableStateOf(vm.data.settings.updateServerUrl) }
    val (versionName, versionCode) = remember { vm.currentVersion() }
    val history: List<UpdateRecord> = vm.data.settings.updateHistory
    val latest = vm.latestUpdate
    val hasNew = latest != null && latest.versionCode > versionCode

    Scaffold(topBar = { BackBar("版本与更新", onBack) }) { pad ->
        LazyColumn(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(18.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "当前版本",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "v$versionName",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            "build $versionCode",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text("更新来自你自己的电脑", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "电脑上跑着更新服务，手机上点一下就能拿到最新版。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        AppTextField(
                            value = server,
                            onValueChange = { server = it },
                            label = { Text("电脑地址，例如 http://192.168.1.10:8787") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    vm.setUpdateServer(server)
                                    vm.checkUpdates()
                                },
                                enabled = !vm.updateChecking && !vm.updateDownloading,
                                modifier = Modifier.weight(1f)
                            ) { Text("检查更新") }
                            OutlinedButton(
                                onClick = { vm.scanForUpdateServer() },
                                enabled = !vm.updateChecking && !vm.updateDownloading,
                                modifier = Modifier.weight(1f)
                            ) { Text("自动查找") }
                        }
                        if (vm.updateStatus.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                vm.updateStatus,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (vm.updateStatus.startsWith("❌") || vm.updateStatus.startsWith("没找到"))
                                    MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (vm.updateDownloading) {
                            Spacer(Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { vm.updateProgress },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            if (hasNew && latest != null) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "新版本 v${latest.versionName}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        latest.time.ifBlank { "" },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                Box(
                                    Modifier
                                        .size(8.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                )
                            }
                            Text(
                                if (latest.directUrl.isNotBlank()) "来自 GitHub · 应用内直接下载安装"
                                else "来自你的电脑",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(Modifier.height(10.dp))
                            NotesBlock(latest.notes)
                            Spacer(Modifier.height(14.dp))
                            Button(
                                onClick = { vm.installUpdate(latest) },
                                enabled = !vm.updateDownloading,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("下载并安装") }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    "更新记录",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            if (history.isEmpty()) {
                item {
                    Text(
                        "还没有记录。点一次「检查更新」之后这里会列出近 10 次改动。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(history.take(10), key = { it.versionCode.toString() + it.versionName }) { rec ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "v${rec.versionName}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "build ${rec.versionCode}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(Modifier.weight(1f))
                            if (rec.installed) {
                                Text(
                                    "已安装",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            } else {
                                Text(
                                    "可更新",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (rec.time.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                rec.time,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        NotesBlock(rec.notes)
                    }
                }
            }
        }
    }
}

@Composable
private fun NotesBlock(notes: List<String>) {
    if (notes.isEmpty()) {
        Text(
            "（这次没写具体改动）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        notes.forEach { line ->
            Row(Modifier.fillMaxWidth()) {
                Text(
                    "·",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.width(8.dp))
                Text(line, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

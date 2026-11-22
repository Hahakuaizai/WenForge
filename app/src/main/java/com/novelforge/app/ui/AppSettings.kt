package com.novelforge.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 「设置」页（我的 → 设置）：跟写书无关的 App 级设置放这里 ——
 * 版本与更新、存储管理、触感反馈。模型相关的在「模型设置 → 生成设置」。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppSettingsScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onOpenUpdates: () -> Unit = {},
    onOpenStorage: () -> Unit = {}
) {
    val settings = vm.data.settings
    Scaffold(topBar = { BackBar("设置", onBack) }) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("应用", fontWeight = FontWeight.Bold)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenUpdates() }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("版本与更新")
                        Text(
                            if (vm.updateAvailable) {
                                "有新版本 v${vm.latestUpdate?.versionName ?: ""}，点这里更新"
                            } else "从电脑或 GitHub 拉最新版，看近十次改动",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (vm.updateAvailable) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (vm.updateAvailable) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error)
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        "›",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenStorage() }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("存储管理")
                        Text(
                            "各部分占用、点开看明细、清缓存",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "›",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Text("触感反馈", fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("震动反馈")
                    Text(
                        "点按、翻页、书架滑动时的轻震（即时生效）",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = settings.hapticsEnabled,
                    onCheckedChange = { vm.setHaptics(it, settings.hapticsStrength) }
                )
            }
            if (settings.hapticsEnabled) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.5f to "轻", 1.0f to "标准", 1.5f to "强").forEach { (v, label) ->
                        AppChip(
                            selected = settings.hapticsStrength == v,
                            onClick = { vm.setHaptics(true, v) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
    }
}

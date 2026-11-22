package com.novelforge.app.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.runtime.mutableStateOf
import com.novelforge.app.data.StorageItem
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.novelforge.app.data.StorageStats
import com.novelforge.app.data.UsageSlice

private fun sliceColor(key: String): Color = when (key) {
    "novels" -> Color(0xFF3A6EA5)
    "download" -> Color(0xFF7A9A5B)
    "local" -> Color(0xFFB98A4B)
    "meta" -> Color(0xFF9AA0A6)
    "cache" -> Color(0xFFC05A5A)
    "apk" -> Color(0xFF6A6F76)
    else -> Color(0xFF8E9298)
}

@Composable
fun StorageScreen(vm: AppViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val report = remember(refresh, vm.data.importedBooks.size, vm.data.projects.size) {
        StorageStats.report(ctx, vm.data)
    }
    var expandedSlice by remember { mutableStateOf<String?>(null) }
    var expandedItem by remember { mutableStateOf<String?>(null) }
    // 只算当前展开那部分的明细（点开才算，不拖慢页面）
    val expandedItems = remember(refresh, expandedSlice, vm.data.importedBooks.size, vm.data.projects.size) {
        expandedSlice?.let { StorageStats.sliceItems(ctx, vm.data, it) } ?: emptyList()
    }

    Scaffold(topBar = { BackBar("存储管理", onBack) }) { pad ->
        LazyColumn(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            "总共占用",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            StorageStats.format(report.totalBytes),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(14.dp))
                        // 占比条
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                        ) {
                            report.slices.filter { it.bytes > 0 }.forEach { s ->
                                Box(
                                    Modifier
                                        .weight(s.bytes.toFloat())
                                        .fillMaxHeight()
                                        .background(sliceColor(s.key))
                                )
                            }
                            if (report.totalBytes == 0L) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            report.slices.filter { it.bytes > 0 }.take(3).forEach { s ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier
                                            .size(7.dp)
                                            .background(sliceColor(s.key), CircleShape)
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        s.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        report.slices.forEach { s ->
                            val expanded = expandedSlice == s.key
                            UsageRow(
                                s, report.totalBytes,
                                expandable = s.bytes > 0,
                                expanded = expanded,
                                onClick = {
                                    expandedSlice = if (expanded) null else s.key
                                    expandedItem = null
                                }
                            )
                            if (expanded) {
                                if (expandedItems.isEmpty()) {
                                    Text(
                                        "没有可列出的文件",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.padding(start = 40.dp, top = 2.dp, bottom = 8.dp)
                                    )
                                } else {
                                    expandedItems.forEach { item ->
                                        val itemKey = item.name + "|" + item.bytes
                                        val itemOpen = expandedItem == itemKey
                                        StorageItemRow(
                                            item, depth = 0,
                                            expandable = item.children.isNotEmpty(),
                                            expanded = itemOpen,
                                            onClick = {
                                                expandedItem = if (itemOpen) null else itemKey
                                            }
                                        )
                                        if (itemOpen) {
                                            item.children.forEach { ch ->
                                                StorageItemRow(ch, depth = 1)
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text("缓存", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "导出文件、下载过的安装包、网页缓存都在这里，删了不影响书架上的书。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                val freed = StorageStats.clearCache(ctx)
                                refresh++
                                Toast.makeText(
                                    ctx,
                                    if (freed > 0) "清掉了 ${StorageStats.format(freed)}"
                                    else "本来就没有缓存",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (report.cacheBytes > 0)
                                    "清理缓存（${StorageStats.format(report.cacheBytes)}）"
                                else "清理缓存"
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    "本地导入的书会复制一份到应用数据里，所以也占空间；手机里原来的文件不会动。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun UsageRow(
    s: UsageSlice,
    total: Long,
    expandable: Boolean = false,
    expanded: Boolean = false,
    onClick: () -> Unit = {}
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (expandable) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(9.dp)
                .background(sliceColor(s.key), CircleShape)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(s.label, style = MaterialTheme.typography.bodyMedium)
            if (s.detail.isNotBlank()) {
                Text(
                    s.detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                StorageStats.format(s.bytes),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            if (total > 0) {
                Text(
                    "%.1f%%".format(s.bytes * 100.0 / total),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
        if (expandable) {
            Spacer(Modifier.width(8.dp))
            Text(
                if (expanded) "⌄" else "›",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

/** 明细行：depth 0 = 书/文件，depth 1 = 章 */
@Composable
private fun StorageItemRow(
    item: StorageItem,
    depth: Int,
    expandable: Boolean = false,
    expanded: Boolean = false,
    onClick: () -> Unit = {}
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (expandable) Modifier.clickable { onClick() } else Modifier)
            .padding(
                start = (40 + depth * 18).dp, end = 18.dp,
                top = 5.dp, bottom = 5.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                item.name,
                style = if (depth == 0) MaterialTheme.typography.bodyMedium
                else MaterialTheme.typography.bodySmall,
                maxLines = 2
            )
            if (item.detail.isNotBlank()) {
                Text(
                    item.detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 2
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            StorageStats.format(item.bytes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (expandable) {
            Spacer(Modifier.width(6.dp))
            Text(
                if (expanded) "⌄" else "›",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

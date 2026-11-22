package com.novelforge.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelforge.app.data.BookParser
import com.novelforge.app.data.BookScanner
import com.novelforge.app.data.FoundBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 扫描手机里的小说文件，勾选后批量导入。
 */
@Composable
fun ScanImportScreen(
    vm: AppViewModel,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var scanning by remember { mutableStateOf(true) }
    var files by remember { mutableStateOf<List<FoundBook>>(emptyList()) }
    val picked = remember { mutableStateListOf<String>() }
    var importing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var deepScan by remember { mutableStateOf(false) }
    var showOthers by remember { mutableStateOf(false) }
    // 格式筛选：all / txt / epub / docx / md / html / zip
    var extFilter by remember { mutableStateOf("all") }
    // 每个格式组里「非小说」那一段默认折叠，记一下哪些被展开了
    val expandedRest = remember { mutableStateListOf<String>() }

    // Android 11+ 需要「所有文件访问」才能读到 Download 里的 txt
    var hasAllFiles by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        hasAllFiles = if (android.os.Build.VERSION.SDK_INT >= 30) {
            android.os.Environment.isExternalStorageManager()
        } else {
            androidx.core.content.ContextCompat.checkSelfPermission(
                ctx, android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    fun requestAllFiles() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            runCatching {
                ctx.startActivity(
                    android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        android.net.Uri.parse("package:" + ctx.packageName)
                    )
                )
            }
        } else {
            runCatching {
                ctx.startActivity(
                    android.content.Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.parse("package:" + ctx.packageName)
                    )
                )
            }
        }
    }

    // 从设置页回来时重新检查
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasAllFiles = if (android.os.Build.VERSION.SDK_INT >= 30) {
                    android.os.Environment.isExternalStorageManager()
                } else true
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    LaunchedEffect(deepScan, hasAllFiles) {
        if (!hasAllFiles) { scanning = false; return@LaunchedEffect }
        scanning = true
        files = withContext(Dispatchers.IO) {
            BookScanner.scan(ctx, deep = deepScan, maxFiles = if (deepScan) 2000 else 800)
        }
        if (files.isEmpty() && BookScanner.errs.isNotEmpty()) {
            message = "扫描出错：" + BookScanner.errs.take(2).joinToString("；")
        }
        scanning = false
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            // 顶部
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 10.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                        .pressScale(src, 0.9f)
                        .clickable(interactionSource = src, indication = null, onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ArrowBack, "返回", Modifier.size(19.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "扫描手机",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        when {
                            !hasAllFiles -> "需要文件访问权限"
                            scanning -> "正在找…"
                            else -> {
                                val shown = if (extFilter == "all") files
                                else files.filter { it.ext == extFilter }
                                val novel = shown.count { it.looksLikeNovel }
                                "找到 ${shown.size} 个文件 · $novel 个像小说"
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    if (deepScan) "快速" else "全盘",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { deepScan = !deepScan }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            // 全选行
            if (files.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (picked.isNotEmpty() && picked.size == (if (extFilter == "all") files else files.filter { it.ext == extFilter }).count { it.looksLikeNovel }) "取消全选" else "全选小说",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                val base = if (extFilter == "all") files
                                else files.filter { it.ext == extFilter }
                                val novels = base.filter { it.looksLikeNovel }
                                if (picked.size == novels.size && novels.isNotEmpty()) picked.clear()
                                else {
                                    picked.clear()
                                    picked.addAll(novels.map { it.path })
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "已选 ${picked.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 格式筛选
            if (hasAllFiles && files.isNotEmpty()) {
                val exts = files.groupBy { it.ext }.mapValues { it.value.size }
                    .toList().sortedByDescending { it.second }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FormatChip("all", "全部", files.size, extFilter) { extFilter = it }
                    exts.forEach { (ext, count) ->
                        FormatChip(ext, ext.uppercase(), count, extFilter) { extFilter = it }
                    }
                }
            }

            if (message.isNotBlank()) {
                Text(
                    message,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
                )
            }

            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (hasAllFiles && scanning) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "正在扫描存储…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (!hasAllFiles) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 30.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "需要「所有文件访问」权限",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Android 13 以上，txt / epub 这类文档不在媒体库里，不给这个权限就扫不到 Download 里的书。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(Modifier.height(14.dp))
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                                    .clickable { requestAllFiles() }
                                    .padding(horizontal = 22.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    "去开启",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
                if (hasAllFiles && !scanning && files.isEmpty()) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "没找到符合格式的文件",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "支持 txt / md / epub / docx / html / zip。手机里没这些文件时可以点右上角切「全盘」再扫一遍",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
                val shown = if (extFilter == "all") files
                else files.filter { it.ext == extFilter }
                // 同一格式放一块，组内像小说的排前面
                val byExt = shown.groupBy { it.ext }.toList()
                    .sortedByDescending { it.second.size }

                byExt.forEach { (ext, list) ->
                    val novelsInGroup = list.filter { it.looksLikeNovel }
                        .sortedByDescending { it.sizeBytes }
                    val restInGroup = list.filter { !it.looksLikeNovel }
                        .sortedByDescending { it.sizeBytes }
                    val restOpen = ext in expandedRest
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 6.dp, end = 6.dp, top = 10.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                ext.uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                buildString {
                                    append("${list.size} 个")
                                    if (novelsInGroup.isNotEmpty()) {
                                        append(" · ${novelsInGroup.size} 个像小说")
                                    }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    // 小说直接列出来
                    items(novelsInGroup, key = { it.key }) { f ->
                    val on = f.path in picked
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable {
                                if (on) picked.remove(f.path) else picked.add(f.path)
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(
                                    if (on) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (on) {
                                Text(
                                    "✓",
                                    style = TextStyle(fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimary)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                f.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "${f.ext.uppercase()} · ${f.sizeLabel} · ${f.hint}" +
                                    if (f.looksLikeNovel) "" else "（仍可导入）",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    }

                    // 同格式里不是小说的部分，默认折叠
                    if (restInGroup.isNotEmpty()) {
                        item {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        if (restOpen) expandedRest.remove(ext)
                                        else expandedRest.add(ext)
                                    }
                                    .padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "其他 $ext 文件 · ${restInGroup.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    if (restOpen) "收起" else "展开添加",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        if (restOpen) {
                            items(restInGroup, key = { it.key }) { f ->
                                val on = f.path in picked
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                                        .clickable {
                                            if (on) picked.remove(f.path) else picked.add(f.path)
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (on) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.surfaceVariant
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (on) {
                                            Text(
                                                "✓",
                                                style = TextStyle(
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onPrimary
                                                )
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            f.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            "${f.sizeLabel} · ${f.hint}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }

            // 底部导入
            if (picked.isNotEmpty() || importing) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(enabled = !importing) {
                            importing = true
                            message = "正在导入 ${picked.size} 个文件…"
                        }
                        .padding(vertical = 15.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (importing) "导入中…" else "导入 ${picked.size} 个文件",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }

    // 真正导入
    LaunchedEffect(importing) {
        if (!importing) return@LaunchedEffect
        var ok = 0
        var fail = 0
        withContext(Dispatchers.IO) {
            picked.forEach { path ->
                runCatching {
                    val f = File(path)
                    val parsed = BookParser.parse(f)
                    if (parsed.chapters.isEmpty()) {
                        fail++
                    } else {
                        withContext(Dispatchers.Main) {
                            vm.importLocalBook(parsed, f.name.substringBeforeLast('.'))
                        }
                        ok++
                    }
                }.onFailure { fail++ }
            }
        }
        importing = false
        picked.clear()
        message = "导入完成：成功 $ok 本" + if (fail > 0) "，失败 $fail 个" else ""
        // 书架上长按可改名
        if (ok > 0) onDone()
    }
}

@Composable
private fun FormatChip(
    key: String,
    label: String,
    count: Int,
    selected: String,
    onSelect: (String) -> Unit
) {
    val on = selected == key
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (on) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surface
            )
            .clickable { onSelect(key) }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
            color = if (on) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(5.dp))
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = if (on) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
            else MaterialTheme.colorScheme.outline
        )
    }
}

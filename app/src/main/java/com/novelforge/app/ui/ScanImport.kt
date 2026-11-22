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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
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
        // 标出哪些书已经在书架上了（按文件名+格式，跟导入时的重复判定同一套）
        files = withContext(Dispatchers.IO) {
            val shelf = vm.data.importedBooks
            files.map { f ->
                val hit = runCatching {
                    val bytes = BookScanner.readBytes(ctx, f)
                    val parsed = BookParser.parse(bytes, f.name)
                    val fp = com.novelforge.app.data.DuplicateFinder.fingerprintOf(parsed.chapters)
                    com.novelforge.app.data.DuplicateFinder.find(
                        books = shelf,
                        incomingTitle = f.name.substringBeforeLast('.'),
                        incomingSourceName = f.name,
                        incomingFingerprint = fp,
                        incomingChapterCount = parsed.chapters.size
                    ) != null
                }.getOrDefault(false)
                f.copy(inLibrary = hit)
            }
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
                                val already = shown.count { it.inLibrary }
                                buildString {
                                    append("找到 ${shown.size} 个文件 · $novel 个像小说")
                                    if (already > 0) append(" · $already 个已入库")
                                }
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
                        if (picked.isNotEmpty() && picked.size == (if (extFilter == "all") files else files.filter { it.ext == extFilter }).count { it.looksLikeNovel && !it.inLibrary }) "取消全选" else "全选小说",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                val base = if (extFilter == "all") files
                                else files.filter { it.ext == extFilter }
                                // 已入库的不参与全选
                                val novels = base.filter { it.looksLikeNovel && !it.inLibrary }
                                if (picked.size == novels.size && novels.isNotEmpty()) picked.clear()
                                else {
                                    picked.clear()
                                    picked.addAll(novels.map { it.key })
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
                    FileRow(
                        f = f,
                        on = f.key in picked,
                        onToggle = {
                            if (f.inLibrary) return@FileRow
                            if (f.key in picked) picked.remove(f.key) else picked.add(f.key)
                        }
                    )
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
                                FileRow(
                                    f = f,
                                    on = f.key in picked,
                                    compact = true,
                                    onToggle = {
                                        if (f.inLibrary) return@FileRow
                                        if (f.key in picked) picked.remove(f.key) else picked.add(f.key)
                                    }
                                )
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

    // 导入队列：逐个处理，遇到重复就停下问用户
    var queue by remember { mutableStateOf<List<FoundBook>>(emptyList()) }
    var dupState by remember { mutableStateOf<DupUi?>(null) }
    var applyToAll by remember { mutableStateOf(false) }
    var forcedMode by remember { mutableStateOf<AppViewModel.DupMode?>(null) }
    var okCount by remember { mutableStateOf(0) }
    var failCount by remember { mutableStateOf(0) }

    LaunchedEffect(importing) {
        if (!importing) return@LaunchedEffect
        val byKey = files.associateBy { it.key }
        queue = picked.toList().mapNotNull { byKey[it] }
        okCount = 0
        failCount = 0
        forcedMode = null
    }

    // 从队列头部取一个处理
    LaunchedEffect(queue, dupState, forcedMode) {
        if (queue.isEmpty()) {
            if (importing) {
                importing = false
                picked.clear()
                val ok = okCount
                val fail = failCount
                message = "导入完成：成功 $ok 本" + if (fail > 0) "，失败 $fail 个" else ""
                if (ok > 0) onDone()
            }
            return@LaunchedEffect
        }
        if (dupState != null) return@LaunchedEffect   // 等用户选

        val found = queue.first()
        val rest = queue.drop(1)
        queue = rest

        var parsed: com.novelforge.app.data.BookParser.Parsed? = null
        withContext(Dispatchers.IO) {
            parsed = runCatching {
                BookParser.parse(BookScanner.readBytes(ctx, found), found.name)
            }.getOrNull()
        }
        val p = parsed
        if (p == null || p.chapters.isEmpty()) {
            failCount++
            return@LaunchedEffect
        }

        val title = found.name.substringBeforeLast('.')
        val hit = vm.findDuplicate(p, title, found.name)
        if (hit == null) {
            vm.importParsed(p, title, found.name, com.novelforge.app.data.DuplicateFinder.fingerprintOf(p.chapters), null, null)
            okCount++
            return@LaunchedEffect
        }

        // 有重复：之前选过「全部应用」就直接按那个来
        val mode = forcedMode
        if (mode != null) {
            val id = vm.importParsed(p, title, found.name, hit.second, hit.first, mode)
            if (id.isNotBlank()) okCount++
            return@LaunchedEffect
        }
        // 否则停下来问
        dupState = DupUi(found, p, title, hit.first, hit.second)
    }

    // ——— 重复提示弹窗 ———
    dupState?.let { d ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { dupState = null; importing = false; queue = emptyList() },
            title = {
                Text(
                    "书架已有《${d.verdict.existing.title}》",
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
                )
            },
            text = {
                Column {
                    Text(
                        buildString {
                            append("要导入的是《${d.title}》")
                            append("\n")
                            append(
                                when {
                                    d.verdict.sameContent && d.verdict.sameName -> "内容和文件名完全一样"
                                    d.verdict.sameContent -> "内容完全一样，只是文件名不同"
                                    else -> "文件名和格式一样"
                                }
                            )
                            append("\n\n替换 = 用新文件覆盖书架上那本（阅读进度保留）\n跳过 = 不动书架\n重复导入 = 另外加一本，名字加（1）")
                            val left = queue.size
                            if (left > 0) append("\n\n后面还有 $left 个文件等着导入")
                        },
                        style = TextStyle(fontSize = 12.5.sp, color = Color(0xFF6B6B66), lineHeight = 19.sp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(
                            checked = applyToAll,
                            onCheckedChange = { applyToAll = it }
                        )
                        Text("后面的重复都这样处理", style = TextStyle(fontSize = 12.sp, color = Color(0xFF1A1A1A)))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (applyToAll) forcedMode = AppViewModel.DupMode.KEEP_BOTH
                    val id = vm.importParsed(d.parsed, d.title, d.found.name, d.fingerprint, d.verdict, AppViewModel.DupMode.KEEP_BOTH)
                    if (id.isNotBlank()) okCount++
                    dupState = null
                    applyToAll = false
                }) { Text("重复导入", color = Color(0xFF1A1A1A)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        if (applyToAll) forcedMode = AppViewModel.DupMode.SKIP
                        dupState = null
                        applyToAll = false
                    }) { Text("跳过", color = Color(0xFF6B6B66)) }
                    TextButton(onClick = {
                        if (applyToAll) forcedMode = AppViewModel.DupMode.REPLACE
                        val id = vm.importParsed(d.parsed, d.title, d.found.name, d.fingerprint, d.verdict, AppViewModel.DupMode.REPLACE)
                        if (id.isNotBlank()) okCount++
                        dupState = null
                        applyToAll = false
                    }) { Text("替换", color = Color(0xFFB4402F)) }
                }
            }
        )
    }
}

/**
 * 扫描结果里的一行文件。
 * 已经入库的：整行压暗、勾选框换成「已入库」标记、点不动。
 */
@Composable
private fun FileRow(
    f: FoundBook,
    on: Boolean,
    compact: Boolean = false,
    onToggle: () -> Unit
) {
    val dim = f.inLibrary
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (compact) 14.dp else 16.dp))
            .background(
                when {
                    dim -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    compact -> MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                    else -> MaterialTheme.colorScheme.surface
                }
            )
            .clickable(enabled = !dim, onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = if (compact) 10.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧标记：已入库 → 实心勾 + 「已入库」；否则正常勾选框
        if (dim) {
            Box(
                Modifier
                    .size(if (compact) 18.dp else 20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "✓",
                    style = TextStyle(
                        fontSize = if (compact) 10.sp else 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        } else {
            Box(
                Modifier
                    .size(if (compact) 18.dp else 20.dp)
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
                            fontSize = if (compact) 10.sp else 11.sp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    f.name,
                    style = if (compact) MaterialTheme.typography.bodySmall
                    else MaterialTheme.typography.bodyMedium,
                    fontWeight = if (compact) FontWeight.Normal else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (dim) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (dim) {
                    Spacer(Modifier.width(6.dp))
                    // 已入库标签：小胶囊，和文件名同一行，一眼能扫出来
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "已入库",
                            style = TextStyle(
                                fontSize = 9.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                buildString {
                    if (!compact) append("${f.ext.uppercase()} · ")
                    append(f.sizeLabel)
                    append(" · ")
                    append(if (dim) "书架上已有" else f.hint)
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (dim) MaterialTheme.colorScheme.outline
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 批量导入时卡在某本书上，等用户决定怎么处理重复 */
private data class DupUi(
    val found: FoundBook,
    val parsed: com.novelforge.app.data.BookParser.Parsed,
    val title: String,
    val verdict: com.novelforge.app.data.DuplicateFinder.Verdict,
    val fingerprint: String
)

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

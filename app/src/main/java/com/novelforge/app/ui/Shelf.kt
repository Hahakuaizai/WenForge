package com.novelforge.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange

/** 书架上的一本书，来源三种：自己写的 / 本地导入 / 爬虫下载 */
data class ShelfItem(
    val id: String,
    val kind: String,
    val title: String,
    val author: String,
    val words: Int,
    val chapters: Int,
    val origin: String
)

// 黑白灰三档，只用这三种颜色
private val InkBlack = Color(0xFF15161A)
private val InkGray = Color(0xFF8E9298)
private val HairLine = Color(0xFFE8E8E6)
private val GridLine = Color(0xFFF1F1EF)
private val PaperWhite = Color(0xFFFCFCFB)

@Composable
fun ShelfScreen(
    vm: AppViewModel,
    onOpen: (kind: String, id: String, chapter: Int) -> Unit,
    onBack: (() -> Unit)? = null,
    onCreate: () -> Unit,
    onImport: () -> Unit
) {
    var filter by rememberSaveable { mutableStateOf("all") }
    var pendingDelete by remember { mutableStateOf<ShelfItem?>(null) }
    var renaming by remember { mutableStateOf<ShelfItem?>(null) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }

    val novels = vm.data.projects.map {
        ShelfItem(
            id = it.id, kind = "novel", title = it.title,
            author = it.genre, words = it.totalWords,
            chapters = it.finishedChapters, origin = "novel"
        )
    }
    val imported = vm.data.importedBooks.map {
        ShelfItem(
            id = it.id, kind = "imported", title = it.title,
            author = it.author, words = it.totalWords,
            chapters = it.chapters.size, origin = it.origin
        )
    }
    val all = (novels + imported).sortedByDescending { it.words }
    val shown = when (filter) {
        "novel" -> novels
        "local" -> imported.filter { it.origin != "crawl" }
        "crawl" -> imported.filter { it.origin == "crawl" }
        else -> all
    }

    if (searchOpen) {
        ShelfSearch(
            vm = vm,
            onClose = { searchOpen = false },
            onOpenHit = { kind, id, chapter ->
                searchOpen = false
                onOpen(kind, id, chapter)
            }
        )
        return
    }

    Box(Modifier.fillMaxSize().background(PaperWhite)) {
        // 背景网格
        Canvas(Modifier.fillMaxSize()) {
            val step = 46.dp.toPx()
            var x = 0f
            while (x < size.width) {
                drawLine(GridLine, Offset(x, 0f), Offset(x, size.height), 1f)
                x += step
            }
            var y = 0f
            while (y < size.height) {
                drawLine(GridLine, Offset(0f, y), Offset(size.width, y), 1f)
                y += step
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // 大标题
            item {
                Column(
                    Modifier
                        .statusBarsPadding()
                        .padding(start = 24.dp, end = 24.dp, top = 18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "本地",
                                style = TextStyle(
                                    fontSize = 30.sp,
                                    lineHeight = 36.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = InkBlack,
                                    letterSpacing = 1.sp
                                )
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "${String.format("%02d", all.size)} / LOCAL ARCHIVE",
                                style = TextStyle(
                                    fontSize = 9.5.sp,
                                    color = InkGray,
                                    letterSpacing = 2.sp
                                )
                            )
                        }
                        val ssrc = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.White)
                                .pressScale(ssrc, 0.9f)
                                .clickable(interactionSource = ssrc, indication = null) {
                                    searchOpen = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "筛选",
                                modifier = Modifier.size(18.dp),
                                tint = InkBlack
                            )
                        }
                    }

                    Spacer(Modifier.height(22.dp))

                    // 分隔行
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "COLLECTION / REGION",
                            style = TextStyle(
                                fontSize = 9.sp,
                                color = InkGray,
                                letterSpacing = 1.6.sp
                            )
                        )
                        Spacer(Modifier.width(10.dp))
                        Box(
                            Modifier
                                .weight(1f)
                                .height(1.dp)
                                .background(HairLine)
                        )
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.size(3.dp).background(InkBlack))
                    }

                    Spacer(Modifier.height(10.dp))
                    Text(
                        "立体书架",
                        style = TextStyle(
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            color = InkBlack,
                            letterSpacing = 0.5.sp
                        )
                    )
                    Spacer(Modifier.height(16.dp))

                    // 筛选行
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val fsrc = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .pressScale(fsrc, 0.96f)
                                .clickable(interactionSource = fsrc, indication = null) {
                                    showFilter = !showFilter
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp),
                                tint = InkBlack
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                when (filter) {
                                    "novel" -> "我写的"
                                    "local" -> "导入"
                                    "crawl" -> "爬虫"
                                    else -> "全部书籍"
                                },
                                style = TextStyle(fontSize = 14.sp, color = InkBlack, fontWeight = FontWeight.Medium)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (showFilter) "^" else "v",
                                style = TextStyle(fontSize = 11.sp, color = InkGray)
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Text(
                            "${shown.size} 本",
                            style = TextStyle(fontSize = 13.sp, color = InkGray)
                        )
                        Spacer(Modifier.weight(1f))
                    }

                    // 横向展开的来源菜单
                    androidx.compose.animation.AnimatedVisibility(visible = showFilter) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "all" to "全部",
                                "novel" to "我写的",
                                "local" to "导入",
                                "crawl" to "爬虫"
                            ).forEach { (k, label) ->
                                val sel = filter == k
                                val n = when (k) {
                                    "novel" -> novels.size
                                    "local" -> imported.count { it.origin != "crawl" }
                                    "crawl" -> imported.count { it.origin == "crawl" }
                                    else -> all.size
                                }
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (sel) InkBlack else Color.White)
                                        .border(
                                            1.dp,
                                            if (sel) InkBlack else HairLine,
                                            RoundedCornerShape(14.dp)
                                        )
                                        .clickable {
                                            filter = k
                                            showFilter = false
                                        }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            label,
                                            style = TextStyle(
                                                fontSize = 13.sp,
                                                fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (sel) Color.White else InkBlack
                                            )
                                        )
                                        Text(
                                            "$n",
                                            style = TextStyle(
                                                fontSize = 10.sp,
                                                color = if (sel) Color.White.copy(alpha = 0.6f) else InkGray
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(18.dp))
                }
            }

            if (shown.isEmpty()) {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 60.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "书架是空的",
                            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = InkBlack)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "写一本，或者导入 txt / md",
                            style = TextStyle(fontSize = 12.sp, color = InkGray)
                        )
                        Spacer(Modifier.height(18.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            TextButton(onClick = onCreate) { Text("写一本") }
                            TextButton(onClick = onImport) { Text("导入 / 抓取") }
                        }
                    }
                }
            }

            item {
                BookCarousel(
                    books = shown,
                    onOpen = { onOpen(it.kind, it.id, 0) },
                    onLongClick = { pendingDelete = it }
                )
                Spacer(Modifier.height(26.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .height(1.dp)
                        .background(HairLine)
                )
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(item.title.ifBlank { "未命名" }) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                pendingDelete = null
                                renaming = item
                            }
                            .padding(vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("改书名", style = TextStyle(fontSize = 15.sp, color = InkBlack))
                    }
                    Box(
                        Modifier.fillMaxWidth().height(1.dp).background(HairLine)
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                if (item.kind == "novel") vm.deleteProject(item.id)
                                else vm.deleteImportedBook(item.id)
                                pendingDelete = null
                            }
                            .padding(vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (item.kind == "novel") "删除这部作品" else "从书架删掉",
                            style = TextStyle(fontSize = 15.sp, color = MaterialTheme.colorScheme.error)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }

    // 改书名
    renaming?.let { item ->
        var input by remember(item.id) { mutableStateOf(item.title) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("改书名") },
            text = {
                Column {
                    AppTextField(
                        value = input,
                        onValueChange = { input = it },
                        label = { Text("书名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (item.kind == "novel") "改的是作品名，正文不受影响"
                        else "只改显示名，章节内容不变",
                        style = TextStyle(fontSize = 11.sp, color = InkGray)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = input.trim().ifBlank { "未命名" }
                    if (item.kind == "novel") {
                        vm.project(item.id)?.let { pj ->
                            pj.title = name
                            vm.updateProjectInfo(pj)
                        }
                    } else {
                        vm.renameImportedBook(item.id, name)
                    }
                    renaming = null
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { renaming = null }) { Text("取消") }
            }
        )
    }
}

/**
 * 立体书架。
 *
 * 几何与动效 1:1 移植轻小说文库（ArchiveShelfGeometry / ArchiveWave /
 * ArchiveScrollSimulation）：
 *  - 书尺寸固定：spine=W*.043、cover=W*.32、H=(cover-20)*166/118+54
 *  - 中心书按余弦权重 w 沿深度轴抽出：pull = cover*.84*w*(1.24-.24w)
 *  - 两侧按 railY(d)=d*2.5+d²*0.45 下沉成弧线
 *  - 松手后走三段惯性：指数减速 → 三次着陆 → 指数弹簧
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookCarousel(
    books: List<ShelfItem>,
    onOpen: (ShelfItem) -> Unit,
    onLongClick: (ShelfItem) -> Unit
) {
    val n = books.size
    if (n == 0) return

    val appCtx = androidx.compose.ui.platform.LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current

    // 位置以「书槽」为单位，pos 是浮点索引
    var pos by remember(n) { mutableFloatStateOf(0f) }
    var dragStartPos by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var animJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    BoxWithConstraints(Modifier.fillMaxWidth().height(400.dp)) {
        val Wdp = maxWidth.value
        val spineDp = (Wdp * 0.043f).coerceIn(14f, 18f)
        val coverDp = (Wdp * 0.32f).coerceIn(100f, 128f)
        val HDp = (coverDp - 20f) * 166f / 118f + 54f
        val pitchDp = spineDp + 11f
        val footprintDp = spineDp + coverDp * 0.66f
        val baseYDp = 0.30f * coverDp + 0.60f * HDp + 14f
        val startXDp = Wdp / 2f - footprintDp / 2f + coverDp * 0.84f * 0.66f

        val spine = spineDp * density.density
        val cover = coverDp * density.density
        val H = HDp * density.density
        val pitch = pitchDp * density.density
        val baseY = baseYDp * density.density
        val startX = startXDp * density.density

        // 手势统一在这一层处理：拖动 / 点击 / 长按
        // 书本身不再挂 clickable，否则会把拖动吞掉
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(n, pitch) {
                    val slop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        animJob?.cancel()
                        val downPos = pos
                        var totalDx = 0f
                        var draggingNow = false
                        var longFired = false
                        val downTime = System.currentTimeMillis()

                        // 长按计时（不阻塞拖动判定）
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break

                            if (change.pressed) {
                                totalDx += change.positionChange().x
                                if (!draggingNow && kotlin.math.abs(totalDx) > slop) {
                                    draggingNow = true
                                    dragging = true
                                    dragStartPos = downPos
                                }
                                if (draggingNow) {
                                    // 只累积位移，不实时跟手
                                    change.consume()
                                } else if (!longFired &&
                                    System.currentTimeMillis() - downTime > 480L
                                ) {
                                    longFired = true
                                    // 找按在哪本书上
                                    val idx = nearestIndex(pos, n)
                                    Haptics.longPress(appCtx)
                                    onLongClick(books[idx])
                                }
                            } else {
                                // 抬手
                                if (draggingNow) {
                                    dragging = false
                                    // 一次手势只翻一格：往左滑 next，往右滑 prev
                                    val dir = when {
                                        totalDx <= -slop * 2 -> 1
                                        totalDx >= slop * 2 -> -1
                                        else -> 0
                                    }
                                    if (dir != 0) {
                                        Haptics.tick(appCtx)
                                        animJob = scope.launch {
                                            springTo(pos, pos.roundToInt() + dir.toFloat()) { v -> pos = v }
                                        }
                                    }
                                } else if (!longFired) {
                                    // 点击：算落点离哪本书最近
                                    val idx = nearestIndex(pos, n)
                                    val d = relOfStatic(idx, pos, n)
                                    if (kotlin.math.abs(d) < 0.5f) {
                                        onOpen(books[idx])
                                    } else {
                                        Haptics.select(appCtx)
                                        animJob = scope.launch {
                                            springTo(pos, pos + d) { v -> pos = v }
                                        }
                                    }
                                }
                                break
                            }
                        }
                    }
                }
        )

        // 画书：每本只画一次，位置用取模绕回另一侧实现循环
        fun relOf(i: Int, p: Float): Float {
            val raw = i - p
            return ((raw + n / 2f) % n + n) % n - n / 2f
        }
        val order = books.indices.sortedByDescending {
            kotlin.math.abs(relOf(it, pos))
        }
        order.forEach { i ->
            val d = relOf(i, pos)
            val ad = kotlin.math.abs(d)
            if (ad > 6.2f) return@forEach

            val w = if (ad < 1.48f) {
                val c = kotlin.math.cos(ad / 1.48f * Math.PI.toFloat() / 2f)
                c * c
            } else 0f
            val pull = cover * 0.84f * w * (1.24f - 0.24f * w)
            val lift = w * w * (3f - 2f * w)
            val railY = (d * 2.5f + d * d * 0.45f).coerceIn(-24f, 56f)

            val x = startX + d * pitch - pull * 0.66f
            val y = baseY + railY + 0.30f * pull - 0.60f * H * lift

            val book = books[i]

            Box(
                Modifier
                    .offset { androidx.compose.ui.unit.IntOffset(x.toInt(), y.toInt()) }
                    .width(with(density) { (spine + cover * 0.66f).toDp() })
                    .height(with(density) { (H + cover * 0.30f + 10f).toDp() })
            ) {
                BookSolid(
                    title = book.title,
                    no = i + 1,
                    origin = book.origin,
                    serial = "WK-${kotlin.math.abs(book.id.hashCode()) % 100000000}",
                    spinePx = spine,
                    coverPx = cover,
                    hPx = H
                )
            }
        }
    }

    // 信息区
    val curIndex = ((pos.roundToInt() % n) + n) % n
    val cur = books[curIndex]
    Spacer(Modifier.height(4.dp))
    Column(Modifier.padding(horizontal = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "\u300a${cur.title}\u300b",
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = InkBlack),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Default.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = InkBlack
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            buildString {
                append(
                    when (cur.origin) {
                        "novel" -> "\u539f\u521b\u4f5c\u54c1"
                        "crawl" -> "\u7f51\u7edc\u6293\u53d6"
                        else -> "\u672c\u5730\u4e66\u7c4d"
                    }
                )
                append(" \u00b7 ")
                append(if (cur.chapters > 0) "${cur.chapters} \u7ae0" else "\u672a\u5f00\u59cb")
                if (cur.words > 0) append(" \u00b7 ${cur.words} \u5b57")
            },
            style = TextStyle(fontSize = 11.sp, color = InkGray)
        )
    }
    Spacer(Modifier.height(10.dp))
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "\u2039",
            style = TextStyle(fontSize = 18.sp, color = InkBlack),
            modifier = Modifier
                .clickable {
                    animJob?.cancel()
                    animJob = scope.launch {
                        springTo(pos, pos.roundToInt() - 1f) { v -> pos = v }
                    }
                }
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
        Text(
            "${String.format("%02d", curIndex + 1)} / ${String.format("%02d", n)}",
            style = TextStyle(fontSize = 11.sp, color = InkGray, letterSpacing = 1.sp)
        )
        Text(
            "\u203a",
            style = TextStyle(fontSize = 18.sp, color = InkBlack),
            modifier = Modifier
                .clickable {
                    animJob?.cancel()
                    animJob = scope.launch {
                        springTo(pos, pos.roundToInt() + 1f) { v -> pos = v }
                    }
                }
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}


/** 当前位置最近的整数槽位（取模） */
private fun nearestIndex(pos: Float, n: Int): Int {
    val r = pos.roundToInt()
    return ((r % n) + n) % n
}

/** 第 i 本相对当前位置的偏移，绕回 -n/2..n/2 */
private fun relOfStatic(i: Int, pos: Float, n: Int): Float {
    val raw = i - pos
    return ((raw + n / 2f) % n + n) % n - n / 2f
}

/** 指数弹簧：dest + (A + B*t) * e^(-omega*t)，跟 HTML 里那段一致 */
private suspend fun springTo(
    from: Float,
    dest: Float,
    omega: Float = 12f,
    onUpdate: (Float) -> Unit
) {
    val A = from - dest
    var t = 0f
    val step = 0.016f
    while (true) {
        val v = dest + (A + 0f * t) * kotlin.math.exp(-omega * t)
        onUpdate(v)
        if (kotlin.math.abs(v - dest) < 0.004f && t > 0.18f) {
            onUpdate(dest)
            break
        }
        kotlinx.coroutines.delay(16)
        t += step
        if (t > 3f) {
            onUpdate(dest)
            break
        }
    }
}

/**
 * 松手后的三段惯性：
 *  1) 高速段：pos0 + (v/rate)*(1-e^(-rate*t))，rate=4.4
 *  2) 着陆段：三次缓出 dest - (dest-hp)*u³
 *  3) 低速段：指数弹簧收敛到 dest
 */
private suspend fun settle(
    pos0: Float,
    velocity: Float,
    pitch: Float,
    n: Int,
    onUpdate: (Float) -> Unit
) {
    val rate = 4.4f
    val omega = 12f
    val minVel = 50f
    val release = if (kotlin.math.abs(velocity) >= minVel) velocity else 0f

    fun snap(p: Float): Float = p.roundToInt().toFloat()

    if (release == 0f) {
        springTo(pos0, snap(pos0), omega, onUpdate)
        return
    }

    val handoffSpeed = pitch * 4f
    var hPos = pos0
    var hVel = release

    if (kotlin.math.abs(release) > handoffSpeed) {
        val cd = (kotlin.math.ln(kotlin.math.abs(release) / handoffSpeed) / rate)
        val t0 = System.nanoTime()
        while (true) {
            val t = (System.nanoTime() - t0) / 1e9f
            if (t >= cd) break
            onUpdate(pos0 + (release / rate) * (1f - kotlin.math.exp(-rate * t)))
            kotlinx.coroutines.delay(16)
        }
        hPos = pos0 + (release / rate) * (1f - kotlin.math.exp(-rate * cd))
        hVel = kotlin.math.sign(release) * handoffSpeed
    }

    // dest：落点对齐到整数槽位
    val guess = hPos + hVel / rate
    var dest = snap(guess)
    if ((dest - hPos) * kotlin.math.sign(hVel) < kotlin.math.abs(hVel) / omega) {
        dest = snap(hPos + hVel / omega)
    }

    val landDur = if (hVel != 0f) 3f * (dest - hPos) / hVel else -1f
    if (landDur > 0f) {
        val t0 = System.nanoTime()
        while (true) {
            val t = (System.nanoTime() - t0) / 1e9f
            if (t >= landDur) break
            val u = 1f - t / landDur
            onUpdate(dest - (dest - hPos) * u * u * u)
            kotlinx.coroutines.delay(16)
        }
        onUpdate(dest)
    } else {
        springTo(hPos, dest, omega, onUpdate)
    }
}

/**
 * 一本书：白色书脊 + 白色封面（中间一块黑色封面图）+ 顶部书页。
 * 封面用矩阵压成平行四边形，跟等距投影一致。
 */
@Composable
private fun BookSolid(
    title: String,
    no: Int,
    origin: String,
    serial: String,
    spinePx: Float,
    coverPx: Float,
    hPx: Float
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val dx = coverPx * 0.66f
    val dy = coverPx * -0.30f
    val skew = spinePx * 0.1f

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val canvas = drawContext.canvas.nativeCanvas

            // 顶面（书页）
            val top = Path().apply {
                moveTo(0f, 0f)
                lineTo(spinePx, skew)
                lineTo(spinePx + dx, skew + dy)
                lineTo(dx, dy)
                close()
            }
            drawPath(
                top,
                Brush.verticalGradient(
                    listOf(Color(0xFFFDFCF9), Color(0xFFEFE8D8)),
                    startY = skew + dy,
                    endY = 0f
                )
            )
            drawPath(top, Color(0xFFB0A084).copy(alpha = 0.4f), style = Stroke(0.8f))

            // 书脊
            // 书脊：白底，左缘一道木色
            drawRect(
                Color(0xFFF6F3EC),
                topLeft = Offset(0f, 0f),
                size = Size(spinePx, hPx)
            )
            drawRect(
                Color(0xFFD9C9AC),
                topLeft = Offset(0f, 0f),
                size = Size(spinePx * 0.22f, hPx)
            )
            drawLine(
                Color(0x40B8A07C),
                Offset(spinePx, 0f), Offset(spinePx, hPx), 0.8f
            )

            // 封面：原木色底 + 左侧深色竖条，右侧留白写书名
            val m = android.graphics.Matrix()
            m.setValues(
                floatArrayOf(
                    0.66f, 0f, spinePx,
                    -0.30f, 1f, skew,
                    0f, 0f, 1f
                )
            )
            canvas.save()
            canvas.concat(m)

            // 底色：白
            canvas.drawRect(0f, 0f, coverPx, hPx, android.graphics.Paint().apply {
                color = android.graphics.Color.rgb(0xFD, 0xFC, 0xF9)
                isAntiAlias = true
            })
            // 外框：淡木色
            canvas.drawRect(
                0.5f, 0.5f, coverPx - 0.5f, hPx - 0.5f,
                android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(0x40, 0xB8, 0xA0, 0x7C)
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = 1f
                    isAntiAlias = true
                }
            )

            val padH = coverPx * 0.12f

            // 编号下方一条短木色横线，代替原来的竖条
            canvas.drawRect(
                padH, hPx * 0.155f,
                padH + coverPx * 0.16f, hPx * 0.155f + 2f,
                android.graphics.Paint().apply {
                    color = android.graphics.Color.rgb(0xC9, 0xB2, 0x8C)
                    isAntiAlias = true
                }
            )

            val textLeft = padH
            val tp = android.graphics.Paint().apply { isAntiAlias = true }

            // 顶部编号
            tp.textSize = 7.5f
            tp.color = android.graphics.Color.rgb(0x9A, 0x9E, 0xA4)
            canvas.drawText("NO.${String.format("%03d", no)}", textLeft, padH + 8f, tp)

            if (title.isNotBlank()) {
                val availW = coverPx - textLeft - padH
                val textSize = 15f
                tp.textSize = textSize
                tp.isFakeBoldText = true
                tp.color = android.graphics.Color.rgb(0x1B, 0x1C, 0x21)

                val maxChars = (availW / textSize).toInt().coerceAtLeast(2)
                val lines = mutableListOf<String>()
                var rest = title.take(maxChars * 3)
                while (rest.isNotEmpty() && lines.size < 3) {
                    lines.add(rest.take(maxChars))
                    rest = rest.drop(maxChars)
                }
                val lineH = textSize * 1.36f
                val blockH = lines.size * lineH
                var ty = hPx * 0.50f - blockH / 2f + textSize
                lines.forEach { ln ->
                    canvas.drawText(ln, textLeft, ty, tp)
                    ty += lineH
                }

                tp.isFakeBoldText = false
                tp.textSize = 7.5f
                tp.color = android.graphics.Color.rgb(0x9A, 0x9E, 0xA4)
                val sub = when (origin) {
                    "novel" -> "原创作品"
                    "crawl" -> "网络抓取"
                    else -> "本地书籍"
                }
                canvas.drawText(sub, textLeft, ty + 3f, tp)
            } else {
                // 没书名：中间一个木色方块占位
                canvas.drawRect(
                    textLeft, hPx * 0.42f,
                    coverPx - padH, hPx * 0.50f,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.rgb(0xDD, 0xD0, 0xB8)
                        isAntiAlias = true
                    }
                )
            }

            // 底部序列号
            tp.textSize = 6.5f
            tp.color = android.graphics.Color.argb(0x40, 0x9A, 0x9E, 0xA4)
            canvas.drawText(serial, textLeft, hPx - padH - 1f, tp)

            canvas.restore()
        }

        // 书脊：上端编号、中间书名（没书名就不写）、下端圆点
        Column(
            Modifier
                .width(with(density) { spinePx.toDp() })
                .height(with(density) { hPx.toDp() })
                .padding(vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                String.format("%02d", no),
                style = TextStyle(fontSize = 6.sp, color = Color(0xFF9A9EA4)),
                maxLines = 1
            )
            Spacer(Modifier.height(8.dp))
            if (title.isNotBlank()) {
                Text(
                    title.take(6).toCharArray().joinToString("\n"),
                    style = TextStyle(
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        color = Color(0xFF1B1C21),
                        letterSpacing = 0.sp
                    ),
                    textAlign = TextAlign.Center,
                    maxLines = 7
                )
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(2.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Color(0xFFB8A07C).copy(alpha = 0.6f))
            )
        }
    }
}

// ── 搜索 ────────────────────────────────────────────────────────

private data class SearchHit(
    val kind: String,
    val bookId: String,
    val bookTitle: String,
    val chapterIndex: Int,
    val chapterTitle: String,
    val snippet: String
)

@Composable
private fun ShelfSearch(
    vm: AppViewModel,
    onClose: () -> Unit,
    onOpenHit: (kind: String, id: String, chapter: Int) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var running by remember { mutableStateOf(false) }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    androidx.compose.runtime.LaunchedEffect(Unit) {
        focus.requestFocus()
    }

    androidx.compose.runtime.LaunchedEffect(query) {
        if (query.isBlank()) {
            hits = emptyList()
            running = false
            return@LaunchedEffect
        }
        running = true
        kotlinx.coroutines.delay(220)
        hits = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            searchAll(vm, query.trim())
        }
        running = false
    }

    Column(Modifier.fillMaxSize().background(PaperWhite)) {
        // 搜索框
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(fontSize = 16.sp, color = InkBlack),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(InkBlack),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focus),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                "搜书名，或书里的内容",
                                style = TextStyle(fontSize = 16.sp, color = InkGray)
                            )
                        }
                        inner()
                    }
                }
            )
            if (query.isNotEmpty()) {
                Text(
                    "清除",
                    style = TextStyle(fontSize = 12.sp, color = InkGray),
                    modifier = Modifier
                        .clickable { query = "" }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
            Text(
                "取消",
                style = TextStyle(fontSize = 14.sp, color = InkBlack),
                modifier = Modifier
                    .clickable {
                        keyboard?.hide()
                        onClose()
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(1.dp)
                .background(HairLine)
        )

        when {
            query.isBlank() -> {
                Column(
                    Modifier.fillMaxSize().padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(50.dp))
                    Text(
                        "输入关键词",
                        style = TextStyle(fontSize = 13.sp, color = InkGray)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "会同时搜书名和正文，正文命中会列出章节和那一小段",
                        style = TextStyle(fontSize = 11.sp, color = InkGray.copy(alpha = 0.7f)),
                        textAlign = TextAlign.Center
                    )
                }
            }
            running && hits.isEmpty() -> {
                Column(
                    Modifier.fillMaxSize().padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(50.dp))
                    Text("搜索中…", style = TextStyle(fontSize = 13.sp, color = InkGray))
                }
            }
            hits.isEmpty() -> {
                Column(
                    Modifier.fillMaxSize().padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(50.dp))
                    Text("没找到", style = TextStyle(fontSize = 13.sp, color = InkGray))
                }
            }
            else -> {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    item {
                        Text(
                            "${hits.size} 条结果",
                            style = TextStyle(fontSize = 11.sp, color = InkGray),
                            modifier = Modifier.padding(start = 22.dp, top = 14.dp, bottom = 6.dp)
                        )
                    }
                    itemsIndexed(hits) { _, hit ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    keyboard?.hide()
                                    onOpenHit(hit.kind, hit.bookId, hit.chapterIndex)
                                }
                                .padding(horizontal = 22.dp, vertical = 13.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    hit.bookTitle,
                                    style = TextStyle(
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = InkBlack
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (hit.chapterIndex > 0) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        hit.chapterTitle,
                                        style = TextStyle(fontSize = 11.sp, color = InkGray),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            if (hit.snippet.isNotBlank()) {
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    hit.snippet,
                                    style = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, color = InkGray),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 22.dp)
                                .height(1.dp)
                                .background(HairLine.copy(alpha = 0.6f))
                        )
                    }
                }
            }
        }
    }
}

/** 书名 + 正文全文搜索，结果最多 80 条 */
private fun searchAll(vm: AppViewModel, q: String): List<SearchHit> {
    val out = mutableListOf<SearchHit>()
    val needle = q.lowercase()

    vm.data.projects.forEach { p ->
        if (p.title.lowercase().contains(needle)) {
            out.add(SearchHit("novel", p.id, p.title, 0, "", "书名匹配"))
        }
        p.chapters.forEach { ch ->
            if (out.size >= 80) return@forEach
            if (ch.content.isBlank()) return@forEach
            val idx = ch.content.lowercase().indexOf(needle)
            if (idx >= 0) {
                out.add(
                    SearchHit(
                        kind = "novel",
                        bookId = p.id,
                        bookTitle = p.title,
                        chapterIndex = ch.index,
                        chapterTitle = "第${ch.index}章",
                        snippet = snippetOf(ch.content, idx, q.length)
                    )
                )
            }
        }
    }

    vm.data.importedBooks.forEach { b ->
        if (b.title.lowercase().contains(needle)) {
            out.add(SearchHit("imported", b.id, b.title, 0, "", "书名匹配"))
        }
        b.chapters.forEach { ch ->
            if (out.size >= 80) return@forEach
            if (ch.content.isBlank()) return@forEach
            val idx = ch.content.lowercase().indexOf(needle)
            if (idx >= 0) {
                out.add(
                    SearchHit(
                        kind = "imported",
                        bookId = b.id,
                        bookTitle = b.title,
                        chapterIndex = ch.index,
                        chapterTitle = ch.title.take(18),
                        snippet = snippetOf(ch.content, idx, q.length)
                    )
                )
            }
        }
    }
    return out
}

private fun snippetOf(text: String, index: Int, len: Int): String {
    val start = (index - 22).coerceAtLeast(0)
    val end = (index + len + 30).coerceAtMost(text.length)
    val head = if (start > 0) "…" else ""
    val tail = if (end < text.length) "…" else ""
    return head + text.substring(start, end).replace("\n", " ").trim() + tail
}

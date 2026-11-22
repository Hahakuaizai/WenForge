package com.novelforge.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.max

// ── 数据 ────────────────────────────────────────────────────────

data class ReaderChapter(val no: Int, val title: String, val content: String)

data class ReaderBook(
    val id: String,
    val title: String,
    val author: String,
    val chapters: List<ReaderChapter>
)

data class ReaderTheme(
    val key: String,
    val label: String,
    val bg: Color,
    val fg: Color,
    val muted: Color,
    val dark: Boolean
)

val readerThemes = listOf(
    ReaderTheme("white", "白", Color(0xFFFFFFFF), Color(0xFF14161A), Color(0xFF9AA0A6), false),
    ReaderTheme("paper", "纸", Color(0xFFFAFAF8), Color(0xFF14161A), Color(0xFF9AA0A6), false),
    ReaderTheme("sepia", "米", Color(0xFFF6EDDC), Color(0xFF463829), Color(0xFF9C876C), false),
    ReaderTheme("green", "绿", Color(0xFFE7F0E3), Color(0xFF2A3A2A), Color(0xFF7C8F7C), false),
    ReaderTheme("gray", "灰", Color(0xFFECEDEF), Color(0xFF232629), Color(0xFF85898F), false),
    ReaderTheme("dark", "夜", Color(0xFF0E1013), Color(0xFFBCBFC4), Color(0xFF6B7078), true)
)

private val pageModes = listOf(
    "scroll" to "滚动",
    "chapter" to "章节",
    "page" to "翻页"
)

/** 把小说 / 导入书统一成阅读器能吃的结构 */
fun buildReaderBook(vm: AppViewModel, kind: String, bookId: String): ReaderBook? {
    return if (kind == "novel") {
        val p = vm.project(bookId) ?: return null
        val done = p.chapters.filter { it.done }
        ReaderBook(
            id = p.id,
            title = p.title,
            author = "",
            chapters = done.map { ReaderChapter(it.index, "第${it.index}章 ${it.title}", it.content) }
        )
    } else {
        val b = vm.importedBook(bookId) ?: return null
        ReaderBook(
            id = b.id,
            title = b.title,
            author = b.author,
            chapters = b.chapters
                .filter { it.content.isNotBlank() }
                .map { ReaderChapter(it.index, it.title, it.content) }
        )
    }
}

// ── 主界面 ──────────────────────────────────────────────────────

@Composable
fun ReaderScreen(
    vm: AppViewModel,
    kind: String,
    bookId: String,
    startChapter: Int = 0,
    onBack: () -> Unit
) {
    val book = remember(kind, bookId) { buildReaderBook(vm, kind, bookId) }
    val settings = vm.data.settings
    var fontSize by remember { mutableFloatStateOf(settings.readerFontSize) }
    var lineHeight by remember { mutableFloatStateOf(settings.readerLineHeight) }
    var themeKey by remember { mutableStateOf(settings.readerTheme) }
    var pageMode by remember { mutableStateOf(settings.readerPageMode) }
    var showSettings by remember { mutableStateOf(false) }
    var showToc by remember { mutableStateOf(false) }
    var chapter by remember {
        mutableIntStateOf(
            if (startChapter > 0 && book != null) {
                book.chapters.indexOfFirst { it.no == startChapter }.coerceAtLeast(0)
            } else 0
        )
    }
    val scope = rememberCoroutineScope()
    val readerCtx = androidx.compose.ui.platform.LocalContext.current
    var showSearch by remember { mutableStateOf(false) }
    // 跳转请求（章序号 to 第几次）：目录和搜索共用，三种翻页模式都认
    var jumpRequest by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val scrollListState = rememberLazyListState()

    // 滚动模式的跳转（另外两种模式在各自的 pager 旁边处理）
    androidx.compose.runtime.LaunchedEffect(jumpRequest) {
        val jr = jumpRequest ?: return@LaunchedEffect
        if (pageMode == "scroll") {
            runCatching { scrollListState.animateScrollToItem(jr.first) }
        }
    }

    val theme = readerThemes.firstOrNull { it.key == themeKey } ?: readerThemes[1]

    fun persist() = vm.saveReaderPrefs(fontSize, lineHeight, themeKey, pageMode)

    Box(Modifier.fillMaxSize().background(theme.bg)) {
        if (book == null || book.chapters.isEmpty()) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("没有可读的内容", color = theme.muted)
            }
            return@Box
        }

        when (pageMode) {
            "chapter" -> {
                val pager = rememberPagerState(
                    initialPage = chapter.coerceIn(0, book.chapters.size - 1),
                    pageCount = { book.chapters.size }
                )
                androidx.compose.runtime.LaunchedEffect(jumpRequest) {
                    jumpRequest?.let {
                        runCatching {
                            pager.animateScrollToPage(it.first.coerceIn(0, book.chapters.size - 1))
                        }
                    }
                }
                val appCtx = androidx.compose.ui.platform.LocalContext.current
                var lastPage by remember { mutableIntStateOf(-1) }
                androidx.compose.runtime.LaunchedEffect(pager.currentPage) {
                    if (lastPage >= 0 && lastPage != pager.currentPage) {
                        Haptics.select(appCtx)
                    }
                    lastPage = pager.currentPage
                }
                HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                    ChapterPage(
                        chapter = book.chapters[page],
                        theme = theme,
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        index = page,
                        total = book.chapters.size
                    )
                }
            }
            "page" -> {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val widthPx = constraints.maxWidth
                    val heightPx = constraints.maxHeight
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    val padH = with(density) { 24.dp.toPx() }
                    val padV = with(density) { 70.dp.toPx() }
                    val charW = with(density) { (fontSize * 1.02f).sp.toPx() }
                    val lineH = with(density) { (fontSize * lineHeight).sp.toPx() }
                    val charsPerLine = max(8, ((widthPx - padH * 2) / charW).toInt())
                    val linesPerPage = max(6, ((heightPx - padV * 2) / lineH).toInt())
                    val perPage = (charsPerLine * linesPerPage).coerceAtLeast(200)
                    val pages = remember(book, perPage) { paginate(book.chapters, perPage) }
                    val pager = rememberPagerState(pageCount = { pages.size })
                    androidx.compose.runtime.LaunchedEffect(jumpRequest) {
                        val jr = jumpRequest ?: return@LaunchedEffect
                        var ordinal = -1
                        var target = 0
                        for ((pi, pg) in pages.withIndex()) {
                            if (pg.chapterTitle != null) {
                                ordinal++
                                if (ordinal == jr.first) { target = pi; break }
                            }
                        }
                        runCatching { pager.animateScrollToPage(target) }
                    }
                    val appCtx = androidx.compose.ui.platform.LocalContext.current
                    var lastPage by remember { mutableIntStateOf(-1) }
                    androidx.compose.runtime.LaunchedEffect(pager.currentPage) {
                        if (lastPage >= 0 && lastPage != pager.currentPage) {
                            Haptics.select(appCtx)
                        }
                        lastPage = pager.currentPage
                    }
                    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { i ->
                        val page = pages[i]
                        Column(
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp, vertical = 56.dp)
                        ) {
                            if (page.chapterTitle != null) {
                                Text(
                                    page.chapterTitle,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = theme.muted,
                                    modifier = Modifier.padding(bottom = 14.dp)
                                )
                            }
                            Text(
                                page.text,
                                style = TextStyle(
                                    fontSize = fontSize.sp,
                                    lineHeight = (fontSize * lineHeight).sp,
                                    color = theme.fg
                                )
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "${i + 1} / ${pages.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = theme.muted,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
            else -> {
                val listState = scrollListState
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 24.dp, end = 24.dp, top = 56.dp, bottom = 80.dp
                    )
                ) {
                    items(book.chapters) { ch ->
                        Column {
                            Text(
                                ch.title,
                                style = TextStyle(
                                    fontSize = (fontSize + 3).sp,
                                    lineHeight = ((fontSize + 3) * 1.5f).sp,
                                    fontWeight = FontWeight.Bold,
                                    color = theme.fg
                                ),
                                modifier = Modifier.padding(top = 26.dp, bottom = 14.dp)
                            )
                            Text(
                                ch.content.trim()
                                    .lineSequence()
                                    .filter { it.isNotBlank() }
                                    .joinToString(separator = "\n") { "　　" + it.trim() },
                                style = TextStyle(
                                    fontSize = fontSize.sp,
                                    lineHeight = (fontSize * lineHeight).sp,
                                    color = theme.fg
                                )
                            )
                        }
                    }
                }
            }
        }

        // 顶部栏
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val bsrc = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(theme.fg.copy(alpha = 0.06f))
                    .pressScale(bsrc, 0.9f)
                    .clickable(interactionSource = bsrc, indication = null, onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "返回",
                    modifier = Modifier.size(18.dp),
                    tint = theme.fg
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    book.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = theme.fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (book.author.isNotBlank()) {
                    Text(
                        book.author,
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.muted
                    )
                }
            }
            val qsrc = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(theme.fg.copy(alpha = 0.06f))
                    .pressScale(qsrc, 0.9f)
                    .clickable(interactionSource = qsrc, indication = null) {
                        showSearch = true
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "搜索",
                    modifier = Modifier.size(18.dp),
                    tint = theme.fg
                )
            }
            Spacer(Modifier.width(8.dp))
            val tsrc = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(theme.fg.copy(alpha = 0.06f))
                    .pressScale(tsrc, 0.9f)
                    .clickable(interactionSource = tsrc, indication = null) {
                        showToc = true
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.List,
                    contentDescription = "目录",
                    modifier = Modifier.size(18.dp),
                    tint = theme.fg
                )
            }
            Spacer(Modifier.width(8.dp))
            val ssrc = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(theme.fg.copy(alpha = 0.06f))
                    .pressScale(ssrc, 0.9f)
                    .clickable(interactionSource = ssrc, indication = null) {
                        showSettings = !showSettings
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.FormatSize,
                    contentDescription = "排版",
                    modifier = Modifier.size(18.dp),
                    tint = theme.fg
                )
            }
        }

        // 目录
        AnimatedVisibility(
            visible = showToc,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            TocSheet(
                chapters = book.chapters,
                current = chapter,
                theme = theme,
                onPick = {
                    chapter = it
                    jumpRequest = it to ((jumpRequest?.second ?: 0) + 1)
                    showToc = false
                    Haptics.select(readerCtx)
                },
                onClose = { showToc = false }
            )
        }

        // 搜索面板
        AnimatedVisibility(
            visible = showSearch,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            ReaderSearchSheet(
                chapters = book.chapters,
                theme = theme,
                onPick = {
                    chapter = it
                    jumpRequest = it to ((jumpRequest?.second ?: 0) + 1)
                    showSearch = false
                    Haptics.select(readerCtx)
                },
                onClose = { showSearch = false }
            )
        }

        // 设置面板
        AnimatedVisibility(
            visible = showSettings,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            SettingsPanel(
                theme = theme,
                fontSize = fontSize,
                lineHeight = lineHeight,
                themeKey = themeKey,
                pageMode = pageMode,
                onFontSize = { fontSize = it; persist() },
                onLineHeight = { lineHeight = it; persist() },
                onTheme = { themeKey = it; persist() },
                onPageMode = { pageMode = it; persist() },
                onClose = { showSettings = false }
            )
        }
    }
}

// ── 子组件 ──────────────────────────────────────────────────────

@Composable
private fun ChapterPage(
    chapter: ReaderChapter,
    theme: ReaderTheme,
    fontSize: Float,
    lineHeight: Float,
    index: Int,
    total: Int
) {
    val state = rememberLazyListState()
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 24.dp, end = 24.dp, top = 56.dp, bottom = 70.dp
        )
    ) {
        item {
            Text(
                chapter.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = theme.fg,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Text(
                "${index + 1} / $total",
                style = MaterialTheme.typography.labelSmall,
                color = theme.muted,
                modifier = Modifier.padding(bottom = 18.dp)
            )
            Text(
                chapter.content.trim()
                    .lineSequence()
                    .filter { it.isNotBlank() }
                    .joinToString(separator = "\n") { "　　" + it.trim() },
                style = TextStyle(
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * lineHeight).sp,
                    color = theme.fg
                )
            )
        }
    }
}

private data class PageSlice(val chapterTitle: String?, val text: String)

/** 把整本切成固定字数的页，尽量在句号处断开 */
private fun paginate(chapters: List<ReaderChapter>, perPage: Int): List<PageSlice> {
    val pages = mutableListOf<PageSlice>()
    chapters.forEach { ch ->
        val body = ch.content.trim()
            .lineSequence()
            .filter { it.isNotBlank() }
            .joinToString(separator = "\n") { "　　" + it.trim() }
        if (body.isEmpty()) return@forEach
        var start = 0
        var first = true
        while (start < body.length) {
            var end = (start + perPage).coerceAtMost(body.length)
            if (end < body.length) {
                // 往回找一个自然断点
                val window = body.substring(start, end)
                val cut = listOf("。", "！", "？", "\n", "」", "”")
                    .map { window.lastIndexOf(it) }
                    .maxOrNull() ?: -1
                if (cut > perPage / 2) end = start + cut + 1
            }
            pages.add(PageSlice(if (first) ch.title else null, body.substring(start, end).trim()))
            first = false
            start = end
        }
    }
    return pages
}

@Composable
private fun SettingsPanel(
    theme: ReaderTheme,
    fontSize: Float,
    lineHeight: Float,
    themeKey: String,
    pageMode: String,
    onFontSize: (Float) -> Unit,
    onLineHeight: (Float) -> Unit,
    onTheme: (String) -> Unit,
    onPageMode: (String) -> Unit,
    onClose: () -> Unit
) {
    val panelBg = if (theme.dark) Color(0xFF1B1E22) else Color.White
    val fg = if (theme.dark) Color(0xFFD5D8DC) else Color(0xFF14161A)
    val muted = if (theme.dark) Color(0xFF868B92) else Color(0xFF8A8F96)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(panelBg)
            .padding(18.dp)
    ) {
        // 字号
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("字号", style = MaterialTheme.typography.labelMedium, color = muted)
            Spacer(Modifier.weight(1f))
            Text(
                "${fontSize.toInt()}",
                style = MaterialTheme.typography.labelMedium,
                color = fg,
                fontWeight = FontWeight.SemiBold
            )
        }
        Slider(
            value = fontSize,
            onValueChange = onFontSize,
            valueRange = 13f..26f,
            steps = 12
        )

        // 行距
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("行距", style = MaterialTheme.typography.labelMedium, color = muted)
            Spacer(Modifier.weight(1f))
            Text(
                String.format("%.1f", lineHeight),
                style = MaterialTheme.typography.labelMedium,
                color = fg,
                fontWeight = FontWeight.SemiBold
            )
        }
        Slider(
            value = lineHeight,
            onValueChange = onLineHeight,
            valueRange = 1.3f..2.6f,
            steps = 12
        )

        Spacer(Modifier.height(6.dp))

        // 背景
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("背景", style = MaterialTheme.typography.labelMedium, color = muted)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                readerThemes.forEach { t ->
                    val sel = t.key == themeKey
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(t.bg)
                            .border(
                                width = if (sel) 2.dp else 1.dp,
                                color = if (sel) fg else t.muted.copy(alpha = 0.5f),
                                shape = CircleShape
                            )
                            .clickable { onTheme(t.key) }
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // 翻页
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("翻页", style = MaterialTheme.typography.labelMedium, color = muted)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pageModes.forEach { (k, label) ->
                    val sel = k == pageMode
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (sel) (if (theme.dark) Color(0xFF2C3A4C) else Color(0xFFE4EDF7))
                                else Color.Transparent
                            )
                            .clickable { onPageMode(k) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (sel) (if (theme.dark) Color(0xFF9FC0E4) else Color(0xFF2C5D8F)) else muted,
                            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

// ── 目录（分卷）────────────────────────────────────────────────

/** 从「第一卷 小丑第1章 绯红」里拆出卷名和章名 */
private fun volumeOf(title: String): String? {
    val m = Regex("""^(第[0-9一二三四五六七八九十百千零两]+[卷部集篇]\s*[^第]{0,14})""").find(title)
    return m?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
}

private fun chapterNameIn(title: String, volume: String?): String =
    if (volume != null) title.removePrefix(volume).trim().ifBlank { title } else title

private data class TocRow(
    val volume: String? = null,
    val index: Int = -1,
    val title: String = ""
)

private fun buildToc(chapters: List<ReaderChapter>): List<TocRow> {
    val rows = mutableListOf<TocRow>()
    var lastVolume: String? = null
    chapters.forEachIndexed { i, ch ->
        val v = volumeOf(ch.title)
        if (v != null && v != lastVolume) {
            rows.add(TocRow(volume = v))
            lastVolume = v
        }
        rows.add(TocRow(index = i, title = chapterNameIn(ch.title, v ?: lastVolume)))
    }
    return rows
}

@Composable
private fun TocSheet(
    chapters: List<ReaderChapter>,
    current: Int,
    theme: ReaderTheme,
    onPick: (Int) -> Unit,
    onClose: () -> Unit
) {
    val panelBg = if (theme.dark) Color(0xFF1B1E22) else Color.White
    val fg = if (theme.dark) Color(0xFFD5D8DC) else Color(0xFF14161A)
    val muted = if (theme.dark) Color(0xFF868B92) else Color(0xFF8A8F96)
    val accent = if (theme.dark) Color(0xFF7FA8D9) else Color(0xFF3A6EA5)
    val rows = remember(chapters) { buildToc(chapters) }
    // 定位到当前章，但要把它上面的卷标题一起露出来
    val curRow = rows.indexOfFirst { it.index == current }
    val anchor = if (curRow > 0 && rows[curRow - 1].volume != null) curRow - 1
    else curRow.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = anchor)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { onClose() }
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.78f)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(panelBg)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("目录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = fg)
                Spacer(Modifier.width(10.dp))
                Text("${chapters.size} 章", style = MaterialTheme.typography.labelSmall, color = muted)
                Spacer(Modifier.weight(1f))
                Text(
                    "关闭",
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    modifier = Modifier
                        .clickable { onClose() }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(muted.copy(alpha = 0.22f)))
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
            ) {
                items(rows) { row ->
                    if (row.volume != null) {
                        Text(
                            row.volume,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = muted,
                            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp)
                        )
                    } else {
                        val isCur = row.index == current
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(row.index) }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                row.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isCur) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isCur) accent else fg,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (isCur) {
                                Spacer(Modifier.width(8.dp))
                                Text("读到这里", style = MaterialTheme.typography.labelSmall, color = accent)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── 本书搜索（正文 / 章节名 / 卷名）────────────────────────────

private data class BookSearchHit(
    val chapterIndex: Int,
    /** 卷名 / 章节名 / 正文 */
    val kind: String,
    val chapterTitle: String,
    val snippet: String,
    /** 正文命中次数（标题类为 0） */
    val count: Int
)

private fun searchInBook(chapters: List<ReaderChapter>, q: String): List<BookSearchHit> {
    val out = mutableListOf<BookSearchHit>()
    val volRe = Regex("""第[0-9一二三四五六七八九十百千零两]+[卷部集篇]""")
    chapters.forEachIndexed { i, ch ->
        // 章节名 / 卷名（卷名复用目录的拆分规则 volumeOf）
        val vol = volumeOf(ch.title)
        if (vol != null && vol.contains(q, ignoreCase = true)) {
            out.add(BookSearchHit(i, "卷名", ch.title, vol, 0))
        } else if (ch.title.contains(q, ignoreCase = true)) {
            out.add(BookSearchHit(i, "章节名", ch.title, ch.title, 0))
        }
        // 正文：数次数 + 取首次命中处的片段
        val content = ch.content
        var count = 0
        var firstAt = -1
        var from = 0
        while (count < 200) {
            val at = content.indexOf(q, from, ignoreCase = true)
            if (at < 0) break
            if (firstAt < 0) firstAt = at
            count++
            from = at + q.length
        }
        if (count > 0) {
            // 首次命中所在的整行若是卷标题行，单独给一条「卷名」
            val lineStart = content.lastIndexOf('\n', firstAt) + 1
            val nl = content.indexOf('\n', firstAt)
            val lineEnd = if (nl < 0) content.length else nl
            val line = content.substring(lineStart, lineEnd).trim()
            if (line.length <= 30 && volRe.containsMatchIn(line)) {
                out.add(BookSearchHit(i, "卷名", ch.title, line, 0))
            }
            val s = maxOf(0, firstAt - 26)
            val e = minOf(content.length, firstAt + q.length + 26)
            val snippet = (if (s > 0) "…" else "") +
                content.substring(s, e).replace('\n', ' ') +
                (if (e < content.length) "…" else "")
            out.add(BookSearchHit(i, "正文", ch.title, snippet, count))
        }
        if (out.size > 300) return out
    }
    return out
}

@Composable
private fun ReaderSearchSheet(
    chapters: List<ReaderChapter>,
    theme: ReaderTheme,
    onPick: (Int) -> Unit,
    onClose: () -> Unit
) {
    val panelBg = if (theme.dark) Color(0xFF1B1E22) else Color.White
    val fg = if (theme.dark) Color(0xFFD5D8DC) else Color(0xFF14161A)
    val muted = if (theme.dark) Color(0xFF868B92) else Color(0xFF8A8F96)
    val accent = if (theme.dark) Color(0xFF7FA8D9) else Color(0xFF3A6EA5)

    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<BookSearchHit>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(query) {
        val q = query.trim()
        if (q.isEmpty()) {
            hits = emptyList()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        kotlinx.coroutines.delay(220)
        hits = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            searchInBook(chapters, q)
        }
        searching = false
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { onClose() }
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.82f)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(panelBg)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 15.sp, color = fg),
                    cursorBrush = SolidColor(accent),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(fg.copy(alpha = 0.06f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) {
                                Text(
                                    "搜正文、章节名、卷名",
                                    style = TextStyle(fontSize = 15.sp, color = muted)
                                )
                            }
                            inner()
                        }
                    }
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "关闭",
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    modifier = Modifier
                        .clickable { onClose() }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
            Text(
                when {
                    query.isBlank() -> "在《${chapters.size} 章里找"
                    searching -> "搜索中…"
                    hits.isEmpty() -> "没找到"
                    else -> "找到 ${hits.size} 条"
                },
                style = MaterialTheme.typography.labelSmall,
                color = muted,
                modifier = Modifier.padding(start = 20.dp, bottom = 6.dp)
            )
            Box(Modifier.fillMaxWidth().height(1.dp).background(muted.copy(alpha = 0.22f)))
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
            ) {
                items(hits) { hit ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(hit.chapterIndex) }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(accent.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                hit.kind,
                                style = MaterialTheme.typography.labelSmall,
                                color = accent
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                hit.snippet,
                                style = MaterialTheme.typography.bodyMedium,
                                color = fg,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                hit.chapterTitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (hit.count > 0) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${hit.count} 处",
                                style = MaterialTheme.typography.labelSmall,
                                color = muted
                            )
                        }
                    }
                }
            }
        }
    }
}

package com.novelforge.app.ui

import com.novelforge.app.data.WordCount

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import kotlin.math.roundToInt
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange

/** 书架上的一本书，来源三种：自己写的 / 本地导入 / 在线书库 */
data class ShelfItem(
    val id: String,
    val kind: String,
    val title: String,
    val author: String,
    val words: Int,
    val chapters: Int,
    val origin: String,
    /** 封面色相：自己写的按题材定，导入的按书名 hash */
    val hue: Int = 214,
    /** 封面右上印章字：题材名或来源 */
    val badge: String = "",
    /** 封面图上的署名：自己写的用主角名，导入的用作者 */
    val coverAuthor: String = "",
    /** 封面底部状态字：COMPLETE / ONGOING / DRAFT */
    val statusText: String = "ONGOING"
)

/** 题材 → 封面色相（跟 HTML 复刻版演示书的配色气质对齐） */
private fun genreHue(genre: String): Int = when (genre) {
    "玄幻" -> 266
    "仙侠" -> 158
    "武侠" -> 20
    "都市" -> 214
    "言情" -> 348
    "科幻" -> 202
    "悬疑" -> 284
    "历史" -> 36
    "末世" -> 96
    "游戏" -> 230
    else -> hashHue(genre)
}

private fun hashHue(s: String): Int = ((s.hashCode() % 360) + 360) % 360

/** 主角设定是「姓名/身份/性格」格式，封面上只取姓名那一段 */
private fun protagonistName(raw: String): String {
    val name = raw.split('/', '，', '、', '·', ' ', '：', ':')
        .firstOrNull()?.trim().orEmpty()
    return name.take(6).ifBlank { "原创" }
}

// 黑白灰三档，只用这三种颜色
private val InkBlack = Color(0xFF15161A)
private val InkGray = Color(0xFF8E9298)
private val HairLine = Color(0xFFE8E8E6)
private val GridLine = Color(0xFFF1F1EF)
private val PaperWhite = Color(0xFFFAFAF8)

@Composable
fun ShelfScreen(
    vm: AppViewModel,
    onOpen: (kind: String, id: String, chapter: Int) -> Unit,
    onBack: (() -> Unit)? = null,
    onCreate: () -> Unit,
    onImport: () -> Unit
) {
    var filter by rememberSaveable { mutableStateOf("all") }
    var menuFor by remember { mutableStateOf<ShelfItem?>(null) }
    var pendingDelete by remember { mutableStateOf<ShelfItem?>(null) }
    var editingBook by remember { mutableStateOf<ShelfItem?>(null) }
    var currentBook by remember { mutableStateOf<ShelfItem?>(null) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    // 批量管理
    var manageOpen by remember { mutableStateOf(false) }

    val novels = vm.data.projects.map {
        ShelfItem(
            id = it.id, kind = "novel", title = it.title,
            author = it.genre, words = it.totalWords,
            chapters = it.finishedChapters, origin = "novel",
            hue = genreHue(it.genre),
            badge = it.genre,
            coverAuthor = protagonistName(it.protagonist),
            statusText = when {
                it.chapterCount > 0 && it.finishedChapters >= it.chapterCount -> "COMPLETE"
                it.finishedChapters > 0 -> "ONGOING"
                else -> "DRAFT"
            }
        )
    }
    val imported = vm.data.importedBooks.map {
        ShelfItem(
            id = it.id, kind = "imported", title = it.title,
            author = it.author, words = it.totalWords,
            chapters = it.chapters.size, origin = it.origin,
            hue = hashHue(it.title),
            badge = if (it.origin == "crawl") "网文" else "导入",
            coverAuthor = it.author.ifBlank { "佚名" },
            statusText = "COMPLETE"
        )
    }
    val all = (novels + imported).sortedByDescending { it.words }
    val shown = when (filter) {
        "novel" -> novels
        "local" -> imported.filter { it.origin != "crawl" }
        "crawl" -> imported.filter { it.origin == "crawl" }
        else -> all
    }

    // 搜索界面按返回键 / 侧滑：回书架首页，不退出应用
    androidx.activity.compose.BackHandler(enabled = searchOpen) { searchOpen = false }

    androidx.compose.animation.AnimatedVisibility(
        visible = searchOpen,
        enter = androidx.compose.animation.fadeIn(
            androidx.compose.animation.core.tween(220)
        ) + androidx.compose.animation.slideInVertically(
            androidx.compose.animation.core.tween(300)
        ) { it / 8 },
        exit = androidx.compose.animation.fadeOut(
            androidx.compose.animation.core.tween(160)
        ) + androidx.compose.animation.slideOutVertically(
            androidx.compose.animation.core.tween(220)
        ) { it / 12 },
        modifier = Modifier.fillMaxSize()
    ) {
        ShelfSearch(
            vm = vm,
            onClose = { searchOpen = false },
            onOpenHit = { kind, id, chapter ->
                searchOpen = false
                onOpen(kind, id, chapter)
            }
        )
    }
    // 搜索界面盖满时，底下的书架不接手势
    if (searchOpen) return

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
            // statusBarsPadding 必须放在 LazyColumn 外层：
            // 放在 item 里的话，滚动时这块 padding 会跟着 item 一起滚走，
            // 标题就顶到状态栏下面了（实测被截断）。
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // 大标题
            item {
                Column(
                    Modifier
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
                        val editSrc = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.White)
                                .pressScale(editSrc, 0.9f)
                                .clickable(interactionSource = editSrc, indication = null) {
                                    val target = currentBook ?: shown.firstOrNull()
                                    if (target != null) menuFor = target
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "编辑这本书",
                                modifier = Modifier.size(17.dp),
                                tint = InkBlack
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        val msrc = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.White)
                                .pressScale(msrc, 0.9f)
                                .clickable(interactionSource = msrc, indication = null) {
                                    manageOpen = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "批量管理",
                                modifier = Modifier.size(17.dp),
                                tint = InkBlack
                            )
                        }
                        Spacer(Modifier.width(10.dp))
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
                                    "crawl" -> "网文"
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
                                "crawl" to "网文"
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
                    onLongClick = { menuFor = it },
                    onCurrentChange = { currentBook = it }
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

    // ——— 长按书本 / 右上角编辑 → 极简菜单 ———
    val menuBook = menuFor
    if (menuBook != null) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { menuFor = null }) {
            val appear = remember { Animatable(0f) }
            LaunchedEffect(menuBook.id) {
                appear.animateTo(
                    1f,
                    androidx.compose.animation.core.spring(
                        dampingRatio = 0.86f,
                        stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                    )
                )
            }
            Column(
                Modifier
                    .width(258.dp)
                    .graphicsLayer {
                        alpha = appear.value
                        scaleX = 0.94f + 0.06f * appear.value
                        scaleY = 0.94f + 0.06f * appear.value
                        translationY = (1f - appear.value) * 16f
                    }
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White)
                    .padding(6.dp)
            ) {
                Text(
                    menuBook.title.ifBlank { "未命名" },
                    style = TextStyle(fontSize = 12.sp, color = InkGray),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 4.dp, end = 14.dp)
                )
                ShelfMenuRow("改书名", Icons.Default.Edit) {
                    menuFor = null
                    editingBook = menuBook
                }
                ShelfMenuRow("改作者", Icons.Default.Person) {
                    menuFor = null
                    editingBook = menuBook
                }
                ShelfMenuRow("打开", Icons.Default.MenuBook) {
                    menuFor = null
                    onOpen(menuBook.kind, menuBook.id, 0)
                }
                Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(1.dp).background(HairLine))
                ShelfMenuRow(
                    if (menuBook.kind == "novel") "删除这部作品" else "从书架删掉",
                    Icons.Default.Delete,
                    danger = true
                ) {
                    menuFor = null
                    pendingDelete = menuBook
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }

    // ——— 编辑：书名 + 作者 ———
    val editing = editingBook
    if (editing != null) {
        var titleInput by remember(editing.id) { mutableStateOf(editing.title) }
        var authorInput by remember(editing.id) {
            mutableStateOf(
                if (editing.kind == "novel")
                    vm.project(editing.id)?.protagonist.orEmpty()
                else editing.author
            )
        }
        androidx.compose.ui.window.Dialog(onDismissRequest = { editingBook = null }) {
            val appear = remember { Animatable(0f) }
            LaunchedEffect(editing.id) {
                appear.animateTo(
                    1f,
                    androidx.compose.animation.core.spring(
                        dampingRatio = 0.86f,
                        stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                    )
                )
            }
            Column(
                Modifier
                    .width(300.dp)
                    .graphicsLayer {
                        alpha = appear.value
                        scaleY = 0.95f + 0.05f * appear.value
                        translationY = (1f - appear.value) * 14f
                    }
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White)
                    .padding(18.dp)
            ) {
                Text(
                    "编辑",
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = InkBlack)
                )
                Spacer(Modifier.height(14.dp))
                AppTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    label = { Text("书名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                AppTextField(
                    value = authorInput,
                    onValueChange = { authorInput = it },
                    label = { Text(if (editing.kind == "novel") "主角 / 署名" else "作者") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (editing.kind == "novel") "只改封面上的显示，正文不受影响"
                    else "只改显示，章节内容不变",
                    style = TextStyle(fontSize = 11.sp, color = InkGray)
                )
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFF1F1EF))
                            .clickable { editingBook = null }
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("取消", style = TextStyle(fontSize = 14.sp, color = InkBlack))
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(InkBlack)
                            .clickable {
                                vm.updateBookTitle(editing.kind, editing.id, titleInput)
                                vm.updateBookAuthor(editing.kind, editing.id, authorInput)
                                editingBook = null
                            }
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "保存",
                            style = TextStyle(fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }
        }
    }

    // ——— 删除确认 ———
    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = {
                Text(
                    if (item.kind == "novel") "删掉《${item.title.ifBlank { "未命名" }}》？"
                    else "从书架删掉《${item.title.ifBlank { "未命名" }}》？",
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = InkBlack)
                )
            },
            text = {
                Text(
                    if (item.kind == "novel") "正文和章节都会一起删掉，删了找不回来。"
                    else "只删手机里的这一份，原网站的内容不受影响。",
                    style = TextStyle(fontSize = 12.sp, color = InkGray)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (item.kind == "novel") vm.deleteProject(item.id)
                    else vm.deleteImportedBook(item.id)
                    pendingDelete = null
                }) { Text("删除", color = Color(0xFFB4402F)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消", color = InkBlack) }
            }
        )
    }

    // ——— 批量管理：勾选多本一起删 ———
    if (manageOpen) {
        ShelfManageSheet(
            allItems = shown,
            onDismiss = { manageOpen = false },
            onDelete = { picked ->
                vm.deleteBooks(picked.map { it.kind to it.id })
                manageOpen = false
            }
        )
    }
}

/**
 * 批量管理面板：列出当前筛选下的所有书，勾选后一起删。
 */
@Composable
private fun ShelfManageSheet(
    allItems: List<ShelfItem>,
    onDismiss: () -> Unit,
    onDelete: (List<ShelfItem>) -> Unit
) {
    val picked = remember { mutableStateListOf<String>() }
    var confirm by remember { mutableStateOf(false) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.82f)
                .clip(RoundedCornerShape(24.dp))
                .background(PaperWhite)
                .padding(horizontal = 18.dp, vertical = 18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "批量管理",
                        style = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold, color = InkBlack)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "已选 ${picked.size} / ${allItems.size} 本",
                        style = TextStyle(fontSize = 11.5.sp, color = InkGray)
                    )
                }
                TextButton(onClick = onDismiss) { Text("关闭", color = InkGray) }
            }

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BulkChip(if (picked.size == allItems.size && allItems.isNotEmpty()) "取消全选" else "全选") {
                    if (picked.size == allItems.size && allItems.isNotEmpty()) picked.clear()
                    else {
                        picked.clear()
                        picked.addAll(allItems.map { it.id })
                    }
                }
                BulkChip("只选导入") {
                    picked.clear()
                    picked.addAll(allItems.filter { it.kind == "imported" && it.origin != "crawl" }.map { it.id })
                }
                BulkChip("只选网文") {
                    picked.clear()
                    picked.addAll(allItems.filter { it.origin == "crawl" }.map { it.id })
                }
            }

            Spacer(Modifier.height(10.dp))

            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(allItems, key = { it.kind + "_" + it.id }) { it ->
                    val on = it.id in picked
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (on) Color(0xFFF4F1EA) else Color.White)
                            .clickable {
                                if (on) picked.remove(it.id) else picked.add(it.id)
                            }
                            .padding(horizontal = 13.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(19.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (on) InkBlack else Color(0xFFE8E6E0)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (on) Text("✓", style = TextStyle(fontSize = 12.sp, color = Color.White))
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                it.title.ifBlank { "未命名" },
                                style = TextStyle(fontSize = 14.sp, color = InkBlack, fontWeight = FontWeight.Medium),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                buildString {
                                    append(
                                        when {
                                            it.kind == "novel" -> "我写的"
                                            it.origin == "crawl" -> "网文"
                                            else -> "导入"
                                        }
                                    )
                                    append(" · ${it.chapters} 章 · ${com.novelforge.app.data.WordCount.label(it.words)}")
                                },
                                style = TextStyle(fontSize = 11.sp, color = InkGray)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (picked.isEmpty()) Color(0xFFE8E6E0) else Color(0xFFB4402F))
                    .clickable(enabled = picked.isNotEmpty()) { confirm = true }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (picked.isEmpty()) "选几本再删" else "删除选中的 ${picked.size} 本",
                    style = TextStyle(
                        fontSize = 14.5.sp,
                        color = if (picked.isEmpty()) InkGray else Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }
    }

    if (confirm) {
        val victims = allItems.filter { it.id in picked }
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = {
                Text(
                    "删掉 ${victims.size} 本？",
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = InkBlack)
                )
            },
            text = {
                Text(
                    buildString {
                        append(victims.take(4).joinToString("、") { "《${it.title.ifBlank { "未命名" }}》" })
                        if (victims.size > 4) append(" 等")
                        append("\n\n正文和章节都会一起删掉，删了找不回来。")
                    },
                    style = TextStyle(fontSize = 12.sp, color = InkGray, lineHeight = 18.sp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onDelete(victims)
                }) { Text("删除", color = Color(0xFFB4402F)) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("取消", color = InkBlack) }
            }
        )
    }
}

@Composable
private fun BulkChip(label: String, onTap: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(1.dp, HairLine, RoundedCornerShape(12.dp))
            .clickable(onClick = onTap)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(label, style = TextStyle(fontSize = 12.sp, color = InkBlack))
    }
}

@Composable
private fun ShelfMenuRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val color = if (danger) Color(0xFFB4402F) else InkBlack
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = color)
        Spacer(Modifier.width(12.dp))
        Text(label, style = TextStyle(fontSize = 14.sp, color = color))
    }
}

/**
 * 立体书架（按轻小说文库原作 + HTML 复刻版的行为规格实现）：
 *  - 几何：spine=W*.043、cover=W*.32、H=(cover-20)*166/118+54
 *  - 中心书按余弦权重 w 沿深度轴抽出：pull = cover*.84*w*(1.24-.24w)
 *  - 两侧按 railY(d)=d*2.5+d²*0.45 下沉成弧线
 *  - 拖动实时跟手（pos = 按下位置 - 位移/pitch），松手走三段惯性 settle：
 *    指数减速 → 三次着陆 → 指数弹簧（dragRate=4.4，omega=12，交接速度 4 槽/秒）
 *  - 滑动跨槽位时轻震一下（65ms 节流）
 *  - 点击按书的实体多边形命中：点中心书打开，点侧书用 380ms 贝塞尔把它转到中心
 *  - 长按命中哪本就弹哪本的菜单
 *  - 入场 720ms：每本沿深度轴错峰滑入，抽出权重带 crest 渐显
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookCarousel(
    books: List<ShelfItem>,
    onOpen: (ShelfItem) -> Unit,
    onLongClick: (ShelfItem) -> Unit,
    onCurrentChange: (ShelfItem) -> Unit = {}
) {
    val n = books.size
    if (n == 0) return

    val appCtx = androidx.compose.ui.platform.LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current

    // 位置以「书槽」为单位，pos 是浮点索引（可无限增长，relOf 里取模绕回）
    var pos by remember(n) { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var animJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var lastTickMs by remember { mutableStateOf(0L) }

    // 入场：elapsed 0→1.45（对应 HTML 的 (now-start)/720，1.4 后结束）
    val enter = remember(n) { Animatable(0f) }
    LaunchedEffect(n) {
        enter.snapTo(0f)
        enter.animateTo(1.45f, tween(1050, easing = LinearEasing))
    }

    fun launchAnim(block: suspend () -> Unit) {
        animJob?.cancel()
        animJob = scope.launch { block() }
    }

    /**
     * 点侧书 / 步进按钮：短距离 380ms 贝塞尔（带一点过冲，跟原作一致）；
     * 隔着好几本的远距离，按距离拉长时长（最多 900ms），飞过去不晕。
     */
    suspend fun animatePosTo(dest: Float) {
        val dist = kotlin.math.abs(dest - pos)
        val ms = (380f + kotlin.math.min(dist, 12f) * 44f).toInt().coerceAtMost(900)
        val a = Animatable(pos)
        a.animateTo(
            dest,
            tween(ms, easing = CubicBezierEasing(0.2f, 1.06f, 0.35f, 1f))
        ) { pos = value }
    }

    // 告诉外面当前停在哪本（编辑按钮要用）
    LaunchedEffect(n, pos.roundToInt()) {
        val idx = ((pos.roundToInt() % n) + n) % n
        books.getOrNull(idx)?.let(onCurrentChange)
    }

    // 跨槽位震动：拖动和惯性滑动时，中心书变了就轻震一下
    LaunchedEffect(n) {
        snapshotFlow { pos.roundToInt() }.collect {
            if (dragging || animJob?.isActive == true) {
                val now = System.currentTimeMillis()
                if (now - lastTickMs >= 65L) {
                    lastTickMs = now
                    Haptics.tick(appCtx)
                }
            }
        }
    }

    // 固定高度：书是绝对定位（graphicsLayer 平移）的，不参与布局测量，
    // 不显式给高度的话容器会塌到 0 或者被裁。
    val fixedH = 440.dp
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(fixedH)
    ) {
        // 横屏时压低展示面，不然整本书塞不下
        // 书抽出来时顶部会往上抬（lift 最高 0.60*H），容器要留够
        val boxH = if (maxWidth > maxHeight) 360.dp else fixedH
        val Wdp = maxWidth.value
        val landscape = maxWidth > maxHeight
        val spineDp = (Wdp * 0.043f).coerceIn(14f, 18f)
        // 横屏高度紧：封面封顶 106dp，不然整本（基线+书高）塞不进 330dp 的展示面
        val coverRaw = (Wdp * 0.32f).coerceIn(100f, 128f)
        val coverDp = if (landscape) minOf(coverRaw, 106f) else coverRaw
        val HDp = (coverDp - 20f) * 166f / 118f + 54f
        val pitchDp = spineDp + 11f
        val footprintDp = spineDp + coverDp * 0.66f
        // 可见槽位范围按宽度算：竖屏约 ±6，横屏全宽能排到 ±19，展示面自然拉长
        val rangeF = (Wdp / 2f + footprintDp) / pitchDp + 2f
        // 基线往下压一点：两侧的书 railY 是负值（往上沉），
        // 加上 pull 抬升，书顶容易超出容器被 Offscreen 图层裁掉
        val baseYDp = 0.30f * coverDp + 0.60f * HDp + 34f
        val startXDp = Wdp / 2f - footprintDp / 2f + coverDp * 0.84f * 0.66f

        val spine = spineDp * density.density
        val cover = coverDp * density.density
        val H = HDp * density.density
        val pitch = pitchDp * density.density
        val baseY = baseYDp * density.density
        val startX = startXDp * density.density
        val slidePx = 56f * density.density

        fun wave(ad: Float): Float {
            if (ad >= 1.48f) return 0f
            val c = kotlin.math.cos(ad / 1.48f * Math.PI.toFloat() / 2f)
            return c * c
        }

        fun railY(d: Float): Float = (d * 2.5f + d * d * 0.45f).coerceIn(-24f, 56f) * density.density

        /** 第 i 本书左上角的坐标（渲染和命中测试共用同一套数学） */
        fun bookOrigin(i: Int, p: Float, enterV: Float): Pair<Float, Float> {
            val d = relOfStatic(i, p, n)
            val ad = kotlin.math.abs(d)
            var w = wave(ad)
            var slide = 0f
            if (enterV < 1.4f) {
                val lp = ((enterV - i * 0.018f) / 0.52f).coerceIn(0f, 1f)
                val e = 1f - (1f - lp) * (1f - lp) * (1f - lp)
                slide = (1f - e) * slidePx
                w *= ((lp - 0.38f) / 0.62f).coerceIn(0f, 1f)
            }
            val pull = cover * 0.84f * w * (1.24f - 0.24f * w)
            val lift = w * w * (3f - 2f * w)
            val x = startX + d * pitch - pull * 0.66f + slide * 0.66f
            val y = baseY + railY(d) + 0.30f * pull - 0.60f * H * lift + slide * -0.30f
            return x to y
        }

        /** 点 (px,py) 命中了哪本书：按书的实体多边形测，离中心最近的优先 */
        /**
         * 点 (px,py) 命中了哪本书。
         *
         * 两轮判定：
         *  1) 精确多边形 —— 书是斜的（书脊 + 顶面 + 封面），用实体轮廓测。
         *     重叠时取「绘制在上面的那本」（relOfStatic 大的，右边的书压住左边书的封面右缘），
         *     这样手指压到哪本就切到哪本，跟眼睛看到的一致。
         *     以前这里取「离中心最近」，所以点远处那本经常没反应。
         *  2) 兜底 —— 书之间有缝、侧面书太薄，精确多边形容易落空。
         *     这时算「点击点到书实体包围盒」的真实距离，容差 30dp，取最近的。
         *     不加离中心惩罚（以前有 ad*0.6，导致远处的书永远点不过近处的）。
         */
        fun bookAt(px: Float, py: Float, p: Float): Int? {
            val dx = cover * 0.66f
            val dy = cover * -0.30f
            val skew = spine * 0.1f
            // 实体轮廓：书脊 + 顶面 + 封面的并集（原作 _solidPath 同款）
            val poly = floatArrayOf(
                0f, 0f,
                dx, dy,
                spine + dx, skew + dy,
                spine + dx, skew + dy + H,
                spine, skew + H,
                0f, H
            )

            // 第一轮：精确多边形。同一点落进多本时，取绘制层级最高（最右）的那本。
            var best: Int? = null
            var bestD = -Float.MAX_VALUE
            for (i in books.indices) {
                val d = relOfStatic(i, p, n)
                if (kotlin.math.abs(d) > rangeF) continue
                val (bx, by) = bookOrigin(i, p, 1.45f)
                if (pointInPolygon(px - bx, py - by, poly) && d > bestD) {
                    best = i
                    bestD = d
                }
            }
            if (best != null) return best

            // 第二轮：到包围盒的最近距离
            val tol = 30f * density.density
            var nearBest: Int? = null
            var nearDist = Float.MAX_VALUE
            for (i in books.indices) {
                val ad = kotlin.math.abs(relOfStatic(i, p, n))
                if (ad > rangeF) continue
                val (bx, by) = bookOrigin(i, p, 1.45f)
                val lx = px - bx
                val ly = py - by
                val x0 = 0f
                val x1 = spine + dx
                val y0 = dy
                val y1 = skew + dy + H
                val ddx = when {
                    lx < x0 -> x0 - lx
                    lx > x1 -> lx - x1
                    else -> 0f
                }
                val ddy = when {
                    ly < y0 -> y0 - ly
                    ly > y1 -> ly - y1
                    else -> 0f
                }
                val dist = kotlin.math.sqrt(ddx * ddx + ddy * ddy)
                if (dist < nearDist && dist <= tol) {
                    nearBest = i
                    nearDist = dist
                }
            }
            return nearBest
        }

        // 手势统一在这一层处理：跟手拖动 / 惯性 / 点击命中 / 长按命中
        Box(
            Modifier
                .fillMaxWidth()
                .height(boxH)
                .pointerInput(n, pitch) {
                    val slop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        animJob?.cancel()
                        val downPos = pos
                        var totalDx = 0f
                        var draggingNow = false
                        var longFired = false
                        val downTime = down.uptimeMillis
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break

                            if (change.pressed) {
                                tracker.addPosition(change.uptimeMillis, change.position)
                                totalDx += change.positionChange().x
                                if (!draggingNow && kotlin.math.abs(totalDx) > slop) {
                                    draggingNow = true
                                    dragging = true
                                }
                                if (draggingNow) {
                                    // 实时跟手：手指左移 → pos 增大（下一本）
                                    pos = downPos - totalDx / pitch
                                    change.consume()
                                } else if (!longFired &&
                                    change.uptimeMillis - downTime > 480L
                                ) {
                                    longFired = true
                                    val idx = bookAt(down.position.x, down.position.y, pos)
                                        ?: nearestIndex(pos, n)
                                    Haptics.longPress(appCtx)
                                    onLongClick(books[idx])
                                }
                            } else {
                                if (draggingNow) {
                                    dragging = false
                                    val velSlots = -tracker.calculateVelocity().x / pitch
                                    launchAnim {
                                        settleSlots(pos, velSlots, 50f / pitch) { v -> pos = v }
                                    }
                                } else if (!longFired) {
                                    // 点击：命中哪本算哪本
                                    val idx = bookAt(down.position.x, down.position.y, pos)
                                    if (idx != null) {
                                        val d = relOfStatic(idx, pos, n)
                                        if (kotlin.math.abs(d) < 0.5f) {
                                            Haptics.tap(appCtx)
                                            onOpen(books[idx])
                                        } else {
                                            Haptics.select(appCtx)
                                            // 直接算目标槽位，一步滚过去。
                                            // 以前是 pos + d（只走一格），点远处的书要连点好几下。
                                            val target = pos + d
                                            launchAnim { animatePosTo(target) }
                                        }
                                    }
                                }
                                break
                            }
                        }
                        if (draggingNow) dragging = false
                    }
                }
        )

        // 地板软阴影：只给「当前选中（抽出来）的那本」画一道浅影。
        //
        // 踩过的坑（都试过，记下来免得再走一遍）：
        //  1. 给十几本都画 → 多边形重叠 75%、alpha 叠加 → 中心糊成一块黑
        //  2. 每帧十几个渐变填充 → 滑动掉帧
        //  3. canvas.scale() 压扁椭圆 → 连 translate 一起被压，影子跑到书底下
        //  4. Compose 的 Brush.radialGradient 是正圆，压成椭圆后渐变半径对不上 → 变成实心黑块
        // 最终：用原生 android.graphics.RadialGradient + Matrix 做椭圆缩放，
        // 渐变真的从中心往外淡出。每帧只画 1 个椭圆，开销可忽略。
        val shadowCx = startX + cover * 0.33f + spine * 0.5f
        val shadowCy = baseY + H + cover * 0.045f
        val shadowRx = (spine + cover * 0.66f) * 0.80f
        val shadowRy = cover * 0.14f
        // 原生 paint 只建一次，Matrix 每次改
        // 注意：渐变里的 alpha 和 paint.alpha 会相乘。这里渐变给足（中心 0.22），
        // paint.alpha 只用来做「拖远淡出」，不参与基础浓度。
        val shadowPaint = remember {
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                shader = android.graphics.RadialGradient(
                    0f, 0f, 1f,
                    intArrayOf(
                        android.graphics.Color.argb(96, 0x2B, 0x28, 0x22),
                        android.graphics.Color.argb(42, 0x2B, 0x28, 0x22),
                        android.graphics.Color.argb(0, 0x2B, 0x28, 0x22)
                    ),
                    floatArrayOf(0f, 0.45f, 1f),
                    android.graphics.Shader.TileMode.CLAMP
                )
            }
        }
        val shadowMatrix = remember { android.graphics.Matrix() }
        Canvas(Modifier.fillMaxSize()) {
            val centerIdx = nearestIndex(pos, n)
            val dCenter = relOfStatic(centerIdx, pos, n)
            val fade = (1f - kotlin.math.abs(dCenter) * 0.7f).coerceIn(0f, 1f)
            if (fade > 0.02f) {
                val cx = shadowCx + dCenter * pitch
                val cy = shadowCy + railY(dCenter)
                // 渐变单位圆 → 椭圆：x 放大 rx、y 放大 ry
                shadowMatrix.setScale(shadowRx, shadowRy)
                shadowMatrix.postTranslate(cx, cy)
                (shadowPaint.shader as? android.graphics.RadialGradient)?.setLocalMatrix(shadowMatrix)
                shadowPaint.alpha = (fade * 255).toInt()
                drawContext.canvas.nativeCanvas.drawOval(
                    cx - shadowRx, cy - shadowRy,
                    cx + shadowRx, cy + shadowRy,
                    shadowPaint
                )
            }
        }

        // 画书：按从左到右的顺序（原作的固定绘制序：右边的书压住左边书的封面右缘，
        // 每本书的书脊都露在外面；中心书不靠置顶，靠抽出位移自己站出来）
        // 流畅度关键：每本书渲染成一个图层（内容不变就不重绘），位置在绘制期用
        // graphicsLayer 平移 —— 滑动时只动图层，不再每帧给几十本书重新布局+重绘封面。
        // 绘制序按整数槽位算（跨槽才变），精确位置在图层里读实时 pos。
        val slotPos by remember(n) { derivedStateOf { pos.roundToInt().toFloat() } }
        val order = books.indices
            .filter { kotlin.math.abs(relOfStatic(it, slotPos, n)) <= rangeF + 1f }
            .sortedBy { relOfStatic(it, slotPos, n) }
        val bookBoxW = with(density) { (spine + cover * 0.66f).toDp() }
        // 盒子要装得下「顶面斜出的角 + 整本书 + 底部留白」，太紧会被 Offscreen 图层裁掉
        val topPadPx = cover * 0.30f + 10f
        val bookBoxH = with(density) { (H + cover * 0.60f + 20f + topPadPx).toDp() }
        order.forEach { i ->
            val book = books[i]

            Box(
                Modifier
                    .width(bookBoxW)
                    .height(bookBoxH)
                    .graphicsLayer {
                        // 热路径：每帧每本书都会跑这里。原来调用 bookOrigin 返回 Pair，
                        // 每帧几十次堆分配 -> GC 尖峰掉帧。直接内联计算，零分配。
                        val d = relOfStatic(i, pos, n)
                        val ad = kotlin.math.abs(d)
                        var w = wave(ad)
                        var slide = 0f
                        if (enter.value < 1.4f) {
                            val lp = ((enter.value - i * 0.018f) / 0.52f).coerceIn(0f, 1f)
                            val e = 1f - (1f - lp) * (1f - lp) * (1f - lp)
                            slide = (1f - e) * slidePx
                            w *= ((lp - 0.38f) / 0.62f).coerceIn(0f, 1f)
                        }
                        val pull = cover * 0.84f * w * (1.24f - 0.24f * w)
                        val lift = w * w * (3f - 2f * w)
                        translationX = startX + d * pitch - pull * 0.66f + slide * 0.66f
                        translationY = baseY + railY(d) + 0.30f * pull - 0.60f * H * lift + slide * -0.30f - topPadPx
                        // 内容已在 BookSolid 里整体下移 topPad，这里上移补偿，视觉位置不变。
                        // 强制离屏缓存：滑动每帧只更新这块缓存的变换矩阵，不重跑内容绘制
                        // （对齐 Flutter 的 TransformLayer + oldLayer 复用）。
                        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                    }
            ) {
                // key() 隔离：书的内容跟 pos 无关，滑动时 Compose 会跳过这棵子树的重组
                // （对应 Flutter 版的 RepaintBoundary —— 位置在变、内容不重画）
                androidx.compose.runtime.key(book.id) {
                    BookSolid(
                        title = book.title,
                        no = i + 1,
                        hue = book.hue,
                        badge = book.badge,
                        coverAuthor = book.coverAuthor,
                        statusText = book.statusText,
                        serial = "WK-${kotlin.math.abs(book.id.hashCode()) % 100000000}",
                        spinePx = spine,
                        coverPx = cover,
                        hPx = H
                    )
                }
            }
        }
    }

    // 信息区
    val curIndex by remember(n) { derivedStateOf { ((pos.roundToInt() % n) + n) % n } }
    val cur = books[curIndex]
    Spacer(Modifier.height(4.dp))
    Column(Modifier.padding(horizontal = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "《${cur.title}》",
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
                        "novel" -> "原创作品"
                        "crawl" -> "在线书库"
                        else -> "本地书籍"
                    }
                )
                append(" · ")
                append(if (cur.chapters > 0) "${cur.chapters} 章" else "未开始")
                if (cur.words > 0) append(" · ${WordCount.label(cur.words)}")
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
            "‹",
            style = TextStyle(fontSize = 18.sp, color = InkBlack),
            modifier = Modifier
                .clickable {
                    launchAnim { animatePosTo(pos.roundToInt() - 1f) }
                }
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
        Text(
            "${String.format("%02d", curIndex + 1)} / ${String.format("%02d", n)}",
            style = TextStyle(fontSize = 11.sp, color = InkGray, letterSpacing = 1.sp)
        )
        Text(
            "›",
            style = TextStyle(fontSize = 18.sp, color = InkBlack),
            modifier = Modifier
                .clickable {
                    launchAnim { animatePosTo(pos.roundToInt() + 1f) }
                }
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/** 点是否在多边形内（射线法）；poly 是 [x0,y0,x1,y1,...] */
private fun pointInPolygon(px: Float, py: Float, poly: FloatArray): Boolean {
    var inside = false
    var j = poly.size - 2
    var i = 0
    while (i < poly.size) {
        val xi = poly[i]; val yi = poly[i + 1]
        val xj = poly[j]; val yj = poly[j + 1]
        if ((yi > py) != (yj > py)) {
            val xCross = (xj - xi) * (py - yi) / (yj - yi) + xi
            if (px < xCross) inside = !inside
        }
        j = i
        i += 2
    }
    return inside
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

/** 指数弹簧：dest + A * e^(-omega*t)，低速收尾用 */
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
        val v = dest + A * kotlin.math.exp(-omega * t)
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
 * 松手后的三段惯性（槽位单位，公式与原作 ArchiveScrollSimulation 一致）：
 *  1) 高速段：指数减速 pos0 + (v/rate)*(1-e^(-rate*t))，rate=4.4，减到交接速度 4 槽/秒
 *  2) 着陆段：三次缓出 dest - (dest-hp)*u³，时长由交接速度决定，落点带跑道修正
 *  3) 低速段：指数弹簧收敛到整数槽位
 */
private suspend fun settleSlots(
    pos0: Float,
    velocity: Float,
    minVel: Float,
    onUpdate: (Float) -> Unit
) {
    val rate = 4.4f
    val omega = 12f
    val release = if (kotlin.math.abs(velocity) >= minVel) velocity else 0f

    if (release == 0f) {
        springTo(pos0, pos0.roundToInt().toFloat(), omega, onUpdate)
        return
    }

    val handoffSpeed = 4f // 槽/秒（原作 pitch*4 px/s 换算到槽位）
    var hPos = pos0
    var hVel = release

    if (kotlin.math.abs(release) > handoffSpeed) {
        val cd = kotlin.math.ln(kotlin.math.abs(release) / handoffSpeed) / rate
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

    // 落点：先按摩擦外推猜，再做跑道修正，保证顺着滑动方向单调前进
    val guess = hPos + hVel / rate
    var dest = guess.roundToInt().toFloat()
    if ((dest - hPos) * kotlin.math.sign(hVel) < kotlin.math.abs(hVel) / omega) {
        dest = (hPos + hVel / omega).roundToInt().toFloat()
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

/** HSL → ARGB（封面渐变用，h 可超 360） */
private fun hslColor(h: Float, s: Float, l: Float): Int {
    val hh = ((h % 360f) + 360f) % 360f / 60f
    val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
    val x = c * (1f - kotlin.math.abs(hh % 2f - 1f))
    val m = l - c / 2f
    val (r, g, b) = when {
        hh < 1f -> Triple(c, x, 0f)
        hh < 2f -> Triple(x, c, 0f)
        hh < 3f -> Triple(0f, c, x)
        hh < 4f -> Triple(0f, x, c)
        hh < 5f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return android.graphics.Color.rgb(
        ((r + m) * 255f).roundToInt().coerceIn(0, 255),
        ((g + m) * 255f).roundToInt().coerceIn(0, 255),
        ((b + m) * 255f).roundToInt().coerceIn(0, 255)
    )
}

/** BookSolid 复用的画笔（避免每帧重建 Paint） */
private class ShelfPaints {
    val fill = android.graphics.Paint().apply { isAntiAlias = true }
    val stroke = android.graphics.Paint().apply {
        isAntiAlias = true
        style = android.graphics.Paint.Style.STROKE
    }
    val mono = android.graphics.Paint().apply {
        isAntiAlias = true
        typeface = android.graphics.Typeface.MONOSPACE
    }
    val serif = android.graphics.Paint().apply {
        isAntiAlias = true
        typeface = android.graphics.Typeface.create(
            android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD
        )
    }
    val serifPlain = android.graphics.Paint().apply {
        isAntiAlias = true
        typeface = android.graphics.Typeface.SERIF
    }
}

/**
 * 一本书：书脊 + 封面 + 顶部书页，封面按 HTML 复刻版的版式：
 * 白底，顶行左 NO. 编号、右陶土色印章（题材/来源）；中间一块色相渐变封面图
 * （158° 三段渐变，左上 VOL.1、右侧竖排书名、左下署名）；底行左序列号、右状态字。
 * 封面用矩阵压成平行四边形，跟等距投影一致。
 */
@Composable
private fun BookSolid(
    title: String,
    no: Int,
    hue: Int,
    badge: String,
    coverAuthor: String,
    statusText: String,
    serial: String,
    spinePx: Float,
    coverPx: Float,
    hPx: Float
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val paints = remember { ShelfPaints() }
    val dx = coverPx * 0.66f
    val dy = coverPx * -0.30f
    val skew = spinePx * 0.1f
    // 这些字符串每次重组都会重算（String.format / joinToString），
    // 16 本书 × 每秒几十次重组 = 每秒上千次格式化。缓存起来。
    val noLabel = remember(no) { String.format("%03d", no) }
    val noShort = remember(no) { String.format("%02d", no) }
    val spineText = remember(title) {
        val t = title.take(7) + if (title.length > 7) "…" else ""
        t.toCharArray().joinToString("\n")
    }
    // 顶面路径尺寸只跟书的大小有关，提到 remember 里 —— 不然滑动时每帧每本 new 一个 Path
    val topPath = remember(spinePx, coverPx, dx, dy, skew) {
        Path().apply {
            moveTo(0f, 0f)
            lineTo(spinePx, skew)
            lineTo(spinePx + dx, skew + dy)
            lineTo(dx, dy)
            close()
        }
    }

    Box(Modifier.fillMaxSize().padding(top = with(density) { (coverPx * 0.30f + 10f).toDp() })) {
        Canvas(Modifier.fillMaxSize()) {
            val canvas = drawContext.canvas.nativeCanvas
            val fill = paints.fill
            val stroke = paints.stroke

            // 顶面（书页）
            val top = topPath
            drawPath(
                top,
                Brush.verticalGradient(
                    listOf(Color(0xFFFEFDF8), Color(0xFFE3DFD3)),
                    startY = skew + dy,
                    endY = 0f
                )
            )
            drawPath(top, Color(0xFF9A927F).copy(alpha = 0.28f), style = Stroke(0.8f))

            // 书脊：米白底，右缘一道发丝线
            drawRect(
                Color(0xFFF2F0E9),
                topLeft = Offset(0f, 0f),
                size = Size(spinePx, hPx)
            )
            drawLine(
                Color(0xFF9A927F).copy(alpha = 0.35f),
                Offset(spinePx - 0.5f, 0f), Offset(spinePx - 0.5f, hPx), 1f
            )

            // 封面（矩阵压成平行四边形，以下都是封面本地坐标）
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

            // u = HTML 里的 1 个 CSS 像素（封面宽按 128 计）
            val u = coverPx / 128f
            val padX = 10f * u
            val padTop = 9f * u

            // 底色 + 内框
            fill.shader = null
            fill.color = android.graphics.Color.rgb(0xFE, 0xFD, 0xF8)
            canvas.drawRect(0f, 0f, coverPx, hPx, fill)
            stroke.color = android.graphics.Color.argb(56, 154, 146, 127)
            stroke.strokeWidth = 1f * u
            canvas.drawRect(0.5f * u, 0.5f * u, coverPx - 0.5f * u, hPx - 0.5f * u, stroke)

            // 顶行：左 NO. 编号
            val mono = paints.mono
            mono.textSize = 7f * u
            mono.letterSpacing = 0.06f
            mono.color = android.graphics.Color.rgb(0x8A, 0x85, 0x78)
            canvas.drawText("NO.$noLabel", padX, padTop + 7f * u, mono)

            // 顶行右：印章（题材 / 来源）
            val badgePaint = paints.serifPlain
            badgePaint.textSize = 7f * u
            badgePaint.letterSpacing = 0.12f
            badgePaint.color = android.graphics.Color.rgb(0x9A, 0x4A, 0x2F)
            val badgeH = 7f * u * 1.5f + 2f * u
            if (badge.isNotBlank()) {
                val badgeW = badgePaint.measureText(badge) + 6f * u
                val bx = coverPx - padX - badgeW
                stroke.color = android.graphics.Color.argb(115, 154, 74, 47)
                stroke.strokeWidth = 1f * u
                canvas.drawRoundRect(bx, padTop, bx + badgeW, padTop + badgeH, 2f * u, 2f * u, stroke)
                canvas.drawText(badge, bx + 3f * u, padTop + badgeH / 2f + 7f * u * 0.36f, badgePaint)
            }

            // 封面图：色相渐变（158° 三段）
            val artX = padX
            val artW = coverPx - 20f * u
            val artH = artW * 166f / 118f
            val artY = padTop + badgeH + 5f * u
            fill.shader = android.graphics.LinearGradient(
                artX, artY,
                artX + artW * 0.375f, artY + artH * 0.927f,
                intArrayOf(
                    hslColor(hue.toFloat(), 0.46f, 0.74f),
                    hslColor(hue + 24f, 0.44f, 0.56f),
                    hslColor(hue + 48f, 0.42f, 0.40f)
                ),
                floatArrayOf(0f, 0.52f, 1f),
                android.graphics.Shader.TileMode.CLAMP
            )
            canvas.drawRect(artX, artY, artX + artW, artY + artH, fill)
            fill.shader = null
            stroke.color = android.graphics.Color.argb(31, 43, 40, 34)
            stroke.strokeWidth = 1f * u
            canvas.drawRect(artX, artY, artX + artW, artY + artH, stroke)

            // 图上左上：VOL.1
            mono.textSize = 7f * u
            mono.letterSpacing = 0.08f
            mono.color = android.graphics.Color.argb(217, 255, 253, 246)
            canvas.drawText("VOL.1", artX + 7f * u, artY + 15f * u, mono)

            // 图上右侧：竖排书名（白色粗衬线，带投影）
            if (title.isNotBlank()) {
                val serif = paints.serif
                serif.textSize = 13.5f * u
                serif.color = android.graphics.Color.rgb(0xFF, 0xFD, 0xF6)
                serif.setShadowLayer(6f * u, 0f, 1f * u, android.graphics.Color.argb(115, 30, 25, 18))
                val colX = artX + artW - 7f * u - 13.5f * u
                var cy = artY + 8f * u + 13.5f * u * 0.92f
                for (ch in title) {
                    if (cy > artY + artH - 12f * u) break
                    canvas.drawText(ch.toString(), colX, cy, serif)
                    cy += 13.5f * u * 1.32f
                }
                serif.clearShadowLayer()
            }

            // 图上左下：署名（主角名 / 作者）
            if (coverAuthor.isNotBlank()) {
                val sp = paints.serifPlain
                sp.textSize = 8f * u
                sp.letterSpacing = 0.2f
                sp.color = android.graphics.Color.argb(235, 255, 253, 246)
                sp.setShadowLayer(4f * u, 0f, 1f * u, android.graphics.Color.argb(128, 30, 25, 18))
                canvas.drawText(coverAuthor, artX + 7f * u, artY + artH - 6f * u, sp)
                sp.clearShadowLayer()
                sp.letterSpacing = 0f
            }

            // 底行：左序列号，右状态字
            mono.textSize = 6.5f * u
            mono.letterSpacing = 0.1f
            mono.color = android.graphics.Color.rgb(0xA3, 0x9C, 0x8A)
            val codeY = hPx - 8f * u
            canvas.drawText(serial, padX, codeY, mono)
            if (statusText.isNotBlank()) {
                val sw = mono.measureText(statusText)
                canvas.drawText(statusText, coverPx - padX - sw, codeY, mono)
            }
            mono.letterSpacing = 0f

            canvas.restore()
        }

        // 书脊文字：上编号、中竖排书名（超 7 字加 …）、下圆点
        Column(
            Modifier
                .width(with(density) { spinePx.toDp() })
                .height(with(density) { hPx.toDp() })
                .padding(vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                noShort,
                style = TextStyle(fontSize = 7.5.sp, color = Color(0xFF9A927E)),
                maxLines = 1
            )
            Spacer(Modifier.height(6.dp))
            if (title.isNotBlank()) {
                val spineTitle = title.take(7) + if (title.length > 7) "…" else ""
                Text(
                    spineTitle.toCharArray().joinToString("\n"),
                    style = TextStyle(
                        fontSize = 10.sp,
                        lineHeight = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4A463C),
                        letterSpacing = 0.sp
                    ),
                    textAlign = TextAlign.Center,
                    maxLines = 8
                )
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(3.5.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Color(0xFFB9B09A))
            )
        }
    }
}


// ── 搜索 ────────────────────────────────────────────────────────

/** 把命中的词标出来 */
private fun hl(text: String, q: String, hi: androidx.compose.ui.graphics.Color): androidx.compose.ui.text.AnnotatedString =
    androidx.compose.ui.text.buildAnnotatedString {
        if (q.isBlank() || text.isBlank()) {
            append(text)
            return@buildAnnotatedString
        }
        val lower = text.lowercase()
        val needle = q.lowercase()
        var i = 0
        while (true) {
            val j = lower.indexOf(needle, i)
            if (j < 0) {
                append(text.substring(i))
                break
            }
            append(text.substring(i, j))
            withStyle(
                androidx.compose.ui.text.SpanStyle(
                    color = hi,
                    fontWeight = FontWeight.SemiBold,
                    background = hi.copy(alpha = 0.16f)
                )
            ) { append(text.substring(j, j + q.length)) }
            i = j + q.length
        }
    }

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
                        "搜书名、章节名和正文；命中的词会标出来",
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
                                    hl(hit.bookTitle, query, Color(0xFF9A4A2F)),
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
                                        hl(hit.chapterTitle, query, Color(0xFF9A4A2F)),
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
                                    hl(hit.snippet, query, Color(0xFF9A4A2F)),
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

    // 后台保存线程可能在改这些集合，先拷一份快照再遍历，避免 ConcurrentModificationException
    val projects = vm.data.projects.toList()
    val books = vm.data.importedBooks.toList()

    projects.forEach { p ->
        if (p.title.lowercase().contains(needle)) {
            out.add(SearchHit("novel", p.id, p.title, 0, "", "书名匹配"))
        }
        p.chapters.forEach { ch ->
            if (out.size >= 80) return@forEach
            val realTitle = ch.title.ifBlank { "第${ch.index}章" }
            val titleHit = realTitle.lowercase().contains(needle)
            if (titleHit) {
                out.add(
                    SearchHit("novel", p.id, p.title, ch.index, realTitle, "章节名匹配")
                )
                return@forEach
            }
            if (ch.content.isBlank()) return@forEach
            val idx = ch.content.lowercase().indexOf(needle)
            if (idx >= 0) {
                out.add(
                    SearchHit(
                        kind = "novel",
                        bookId = p.id,
                        bookTitle = p.title,
                        chapterIndex = ch.index,
                        chapterTitle = realTitle,
                        snippet = snippetOf(ch.content, idx, q.length)
                    )
                )
            }
        }
    }

    books.forEach { b ->
        if (b.title.lowercase().contains(needle)) {
            out.add(SearchHit("imported", b.id, b.title, 0, "", "书名匹配"))
        }
        b.chapters.forEach { ch ->
            if (out.size >= 80) return@forEach
            if (ch.title.lowercase().contains(needle)) {
                out.add(
                    SearchHit("imported", b.id, b.title, ch.index, ch.title.take(24), "章节名匹配")
                )
                return@forEach
            }
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

package com.novelforge.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.ui.draw.scale
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.novelforge.app.data.AppSettings
import com.novelforge.app.data.Chapter
import com.novelforge.app.data.Exporter
import com.novelforge.app.data.WordCount
import com.novelforge.app.data.MemoryCharacter
import com.novelforge.app.data.ModelConfig
import com.novelforge.app.data.NovelProject
import com.novelforge.app.data.ProviderConfig
import com.novelforge.app.data.StoryMemory
import com.novelforge.app.data.Templates
import com.novelforge.app.data.UsageRecord
import com.novelforge.app.gen.ContextBuilder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.sp
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MenuBook

// ============================== 通用 ==============================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BackBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(
                start = if (onBack != null) 10.dp else 22.dp,
                end = 10.dp,
                top = 10.dp,
                bottom = 4.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            val btnSrc = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                    .pressScale(btnSrc, 0.9f)
                    .clickable(
                        interactionSource = btnSrc,
                        indication = null,
                        onClick = onBack
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "返回",
                    modifier = Modifier.size(19.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.width(14.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        actions()
    }
}

private fun shareFile(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "*/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "导出小说"))
}

private fun fmtTime(ms: Long): String = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ms))

// ============================== 首页：作品 + 导入书架（带搜索） ==============================
// ============================== 写作：自己的作品 ==============================

private fun ago(ms: Long): String {
    if (ms <= 0L) return "还没动过"
    val diff = System.currentTimeMillis() - ms
    val min = diff / 60000
    return when {
        min < 1 -> "刚刚"
        min < 60 -> "${min} 分钟前"
        min < 60 * 24 -> "${min / 60} 小时前"
        min < 60 * 24 * 30 -> "${min / 60 / 24} 天前"
        else -> "${min / 60 / 24 / 30} 个月前"
    }
}

@Composable
fun HomeScreen(
    vm: AppViewModel,
    onCreate: () -> Unit,
    onOpenProject: (String) -> Unit
) {
    val projects = vm.data.projects.sortedByDescending { it.updatedAt }
    val totalWords = projects.sumOf { it.totalWords }
    val generatingId = vm.generatingProjectId

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 22.dp, end = 16.dp, top = 18.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "写作",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        if (projects.isEmpty()) "还没有作品，从第一章开始"
                        else "${projects.size} 部作品 · 共 ${WordCount.label(totalWords)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val src = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                        .pressScale(src, 0.9f)
                        .clickable(interactionSource = src, indication = null, onClick = onCreate),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, "新建作品", Modifier.size(20.dp))
                }
            }
        }
    ) { pad ->
        if (projects.isEmpty()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .padding(horizontal = 28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "写一部自己的书",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "给个名字和题材，剩下的交给模型一章一章写。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(18.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onCreate)
                        .padding(horizontal = 26.dp, vertical = 14.dp)
                ) {
                    Text(
                        "开始创作",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            return@Scaffold
        }

        LazyColumn(
            Modifier.padding(pad).padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
        ) {
            itemsIndexed(projects, key = { _, p -> p.id }) { index, p ->
                val total = p.chapters.size.coerceAtLeast(p.chapterCount).coerceAtLeast(1)
                val done = p.finishedChapters.coerceIn(0, total)
                val frac = (done.toFloat() / total).coerceIn(0f, 1f)
                val isGenerating = generatingId == p.id
                val src = remember(p.id) { MutableInteractionSource() }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .staggerEnter(index)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .pressScale(src, 0.985f)
                        .clickable(interactionSource = src, indication = null) { onOpenProject(p.id) }
                        .padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            p.title.ifBlank { "未命名" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (isGenerating) {
                            Text(
                                "正在写",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                Icons.Default.KeyboardArrowRight,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        listOfNotNull(
                            p.genre.takeIf { it.isNotBlank() },
                            "${p.chapters.size} 章",
                            WordCount.label(p.totalWords)
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .weight(1f)
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(frac)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "$done / $total",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        when {
                            isGenerating -> "模型正在写这一章…"
                            done == 0 -> "还没开始 · 点进来一键生成"
                            done >= total -> "写完了 · 上次 ${ago(p.updatedAt)}"
                            else -> "下次写第 ${done + 1} 章 · 上次 ${ago(p.updatedAt)}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}
// ============================== 新建作品 ==============================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateScreen(
    vm: AppViewModel,
    onCreated: (String) -> Unit,
    onBack: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf(Templates.genres.first().name) }
    var style by remember { mutableStateOf(Templates.styles.first().name) }
    var perspective by remember { mutableStateOf(Templates.perspectives.first()) }
    var protagonist by remember { mutableStateOf("") }
    var world by remember { mutableStateOf("") }
    var golden by remember { mutableStateOf("") }
    var extra by remember { mutableStateOf("") }
    var chapterCount by remember { mutableFloatStateOf(10f) }
    var words by remember { mutableFloatStateOf(2000f) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { BackBar("新建小说", onBack) }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .padding(horizontal = 18.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 基本
            Column(
                Modifier
                    .fillMaxWidth()
                    .staggerEnter(0)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AppTextField(
                    title,
                    { title = it },
                    label = { Text("书名（可留空，模型会起）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text("题材", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Templates.genres.forEach { g ->
                        AppChip(
                            selected = genre == g.name,
                            onClick = { genre = g.name },
                            label = { Text(g.name) }
                        )
                    }
                }
                Text("风格", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Templates.styles.forEach { st ->
                        AppChip(
                            selected = style == st.name,
                            onClick = { style = st.name },
                            label = { Text(st.name) }
                        )
                    }
                }
                Text("视角", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Templates.perspectives.forEach { ps ->
                        AppChip(
                            selected = perspective == ps,
                            onClick = { perspective = ps },
                            label = { Text(ps) }
                        )
                    }
                }
            }

            // 人物与设定
            Column(
                Modifier
                    .fillMaxWidth()
                    .staggerEnter(1)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "人物与设定",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AppTextField(
                    protagonist,
                    { protagonist = it },
                    label = { Text("主角（姓名、身份、性格）") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                AppTextField(
                    world,
                    { world = it },
                    label = { Text("世界观补充（可空）") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                AppTextField(
                    golden,
                    { golden = it },
                    label = { Text("金手指 / 核心设定（可空）") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                AppTextField(
                    extra,
                    { extra = it },
                    label = { Text("其他要求（可空）") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }

            // 篇幅
            Column(
                Modifier
                    .fillMaxWidth()
                    .staggerEnter(2)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("章节数", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${chapterCount.toInt()} 章",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(chapterCount, { chapterCount = it }, valueRange = 3f..50f, steps = 46)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("每章字数", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "约 ${WordCount.label(words.toInt())}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(words, { words = it }, valueRange = 800f..5000f, steps = 41)
                Spacer(Modifier.height(6.dp))
                Text(
                    "预计全书约 ${WordCount.wan(chapterCount.toInt() * words.toInt() / 10000.0)}字",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val src = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .fillMaxWidth()
                    .staggerEnter(3)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .pressScale(src, 0.98f)
                    .clickable(interactionSource = src, indication = null) {
                        val proj = NovelProject(
                            title = title.ifBlank { "未命名小说" },
                            genre = genre, style = style, perspective = perspective,
                            protagonist = protagonist, worldSetting = world,
                            goldenFinger = golden, extraRequirements = extra,
                            chapterCount = chapterCount.toInt(), wordsPerChapter = words.toInt()
                        )
                        onCreated(vm.createProject(proj))
                    }
                    .padding(vertical = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "创建并进入工作台",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

// ============================== 供应商列表 ==============================

/** 「生成设置」入口卡（原「全局设置」页：分阶段模型、记忆、提示词都在里面） */
@Composable
private fun GenSettingsEntry(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("生成设置", fontWeight = FontWeight.SemiBold)
            Text(
                "分阶段模型、记忆、提示词、震动、更新与存储",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
fun ProvidersScreen(
    vm: AppViewModel,
    onEdit: (String?) -> Unit,
    onBack: () -> Unit,
    onOpenGenSettings: () -> Unit = {}
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            BackBar("模型设置", onBack, actions = {
                if (vm.data.providers.isNotEmpty()) {
                    TextButton(onClick = { vm.maximizeAll() }) { Text("全部拉满") }
                }
            })
        },
        floatingActionButton = {
            val src = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .pressScale(src, 0.94f)
                    .clickable(interactionSource = src, indication = null) { onEdit(null) }
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "添加",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    ) { pad ->
        var expandedProvider by remember { mutableStateOf<String?>(null) }
        // 界面本地镜像：点开关先改这里，立刻跟手；后台再落盘
        var localEnabled by remember {
            mutableStateOf(vm.data.providers.associate { it.id to it.enabled })
        }
        var localModels by remember {
            mutableStateOf(
                vm.data.providers.associate { p ->
                    p.id to p.models.associateWith { m -> vm.isModelEnabled(p.id, m) }
                }
            )
        }
        val providers = vm.data.providers
        if (providers.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(pad).padding(24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text("还没有接模型", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "点右下角「添加」。里面有 DeepSeek、硅基流动、Kimi 等预置，选一个就自动填好地址，只要补 Key。用 Magpie 网关就填它给的地址和 Key。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(18.dp))
                GenSettingsEntry(onOpenGenSettings)
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.padding(pad).padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { GenSettingsEntry(onOpenGenSettings) }
            item {
                Text(
                    "开关控制这套配置要不要出现在写作的切换列表里；点卡片展开，能单独关掉里面的模型。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            itemsIndexed(providers, key = { _, p -> p.id }) { i, prov ->
                val on = localEnabled[prov.id] ?: prov.enabled
                val src = remember { MutableInteractionSource() }
                val expanded = expandedProvider == prov.id
                val borderColor by animateColorAsState(
                    targetValue = if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0f),
                    animationSpec = tween(240),
                    label = "provBorder"
                )
                val fill by animateColorAsState(
                    targetValue = if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                    else MaterialTheme.colorScheme.surface,
                    animationSpec = tween(240),
                    label = "provFill"
                )
                val contentAlpha by animateFloatAsState(
                    targetValue = if (on) 1f else 0.45f,
                    animationSpec = tween(240),
                    label = "provAlpha"
                )

                Column(
                    Modifier
                        .fillMaxWidth()
                        .animateItem()
                        .clip(RoundedCornerShape(22.dp))
                        .background(fill)
                        .border(1.5.dp, borderColor, RoundedCornerShape(22.dp))
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .pressScale(src, 0.985f)
                            .clickable(interactionSource = src, indication = null) {
                                expandedProvider = if (expanded) null else prov.id
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            Modifier
                                .weight(1f)
                                .graphicsLayer { alpha = contentAlpha }
                        ) {
                            Text(
                                prov.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                prov.baseUrl.ifBlank { "还没填地址" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                buildString {
                                    append("${prov.models.size} 个模型")
                                    if (prov.model.isNotBlank()) append(" · 默认 ${prov.model}")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = on,
                            onCheckedChange = { v ->
                                Haptics.tap(vm.getApplication())
                                localEnabled = localEnabled + (prov.id to v)
                                vm.setProviderEnabled(prov.id, v)
                            },
                            modifier = Modifier.scale(0.85f)
                        )
                        Spacer(Modifier.width(2.dp))
                        val editSrc = remember(prov.id) { MutableInteractionSource() }
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                .pressScale(editSrc, 0.9f)
                                .clickable(interactionSource = editSrc, indication = null) {
                                    onEdit(prov.id)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "编辑",
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    AnimatedVisibility(visible = expanded) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                        ) {
                            Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)))
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (on) "这些模型会出现在写作的切换列表里"
                                else "这套配置已关闭，模型开关暂时不可用",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(Modifier.height(6.dp))
                            val models = prov.models.ifEmpty {
                                listOfNotNull(prov.model.takeIf { it.isNotBlank() })
                            }
                            if (models.isEmpty()) {
                                Text(
                                    "还没拉取模型，点铅笔进去加",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            models.forEach { m ->
                                val enabled = localModels[prov.id]?.get(m)
                                    ?: vm.isModelEnabled(prov.id, m)
                                // 整套关掉时，里面的开关直接禁用
                                val rowAlpha by animateFloatAsState(
                                    targetValue = if (on) 1f else 0.35f,
                                    animationSpec = tween(200),
                                    label = "modelRowAlpha"
                                )
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .graphicsLayer { alpha = rowAlpha }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        m,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable(enabled = on) {
                                                Haptics.select(vm.getApplication())
                                                localModels = localModels.toMutableMap().apply {
                                                    this[prov.id] = (this[prov.id] ?: emptyMap()) + (m to !enabled)
                                                }
                                                vm.setModelEnabled(prov.id, m, !enabled)
                                            }
                                    )
                                    Switch(
                                        checked = enabled,
                                        enabled = on,
                                        onCheckedChange = { v ->
                                            Haptics.select(vm.getApplication())
                                            localModels = localModels.toMutableMap().apply {
                                                this[prov.id] = (this[prov.id] ?: emptyMap()) + (m to v)
                                            }
                                            vm.setModelEnabled(prov.id, m, v)
                                        },
                                        modifier = Modifier.scale(0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(96.dp)) }
        }
    }
}

@Composable
private fun FormSection(index: String, title: String, hint: String = "") {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                index,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        if (hint.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(
                hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ============================== 供应商编辑（预置 + 拉模型 + 计价） ==============================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProviderEditScreen(
    vm: AppViewModel,
    providerId: String?,
    onBack: () -> Unit
) {
    val existing = providerId?.let { id -> vm.data.providers.firstOrNull { it.id == id } }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var baseUrl by remember { mutableStateOf(existing?.baseUrl ?: "") }
    var apiKey by remember { mutableStateOf(existing?.apiKey ?: "") }
    var model by remember { mutableStateOf(existing?.model ?: "") }
    var temperature by remember { mutableFloatStateOf((existing?.temperature ?: 0.85).toFloat()) }
    var maxTokens by remember { mutableStateOf((existing?.maxTokens ?: 4096).toString()) }
    var quota by remember { mutableStateOf(existing?.monthlyQuotaUsd?.takeIf { it > 0 }?.toString() ?: "") }
    var priceIn by remember { mutableStateOf(existing?.priceInputPer1M?.takeIf { it >= 0 }?.toString() ?: "") }
    var priceCached by remember { mutableStateOf(existing?.priceCachedPer1M?.takeIf { it >= 0 }?.toString() ?: "") }
    var priceOut by remember { mutableStateOf(existing?.priceOutputPer1M?.takeIf { it >= 0 }?.toString() ?: "") }
    var pickedPreset by remember { mutableStateOf<String?>(null) }
    var showAdvanced by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var picking by remember { mutableStateOf(existing == null) }
    var selectedModels by remember { mutableStateOf(existing?.models?.toList() ?: emptyList()) }
    var expandedGroups by remember { mutableStateOf(setOf<String>()) }
    var ctxWindow by remember { mutableStateOf(existing?.contextWindow ?: 128000) }
    var vision by remember { mutableStateOf(existing?.supportsVision ?: false) }
    var effort by remember { mutableStateOf(existing?.reasoningEffort ?: "off") }
    var supportedEfforts by remember {
        mutableStateOf(existing?.supportedEfforts?.toList() ?: listOf("off"))
    }
    var showBulk by remember { mutableStateOf(false) }
    var bulkCfg by remember { mutableStateOf(ModelConfig()) }
    var modelConfigs by remember {
        mutableStateOf<MutableList<ModelConfig>>(
            existing?.modelConfigs?.toMutableList() ?: mutableListOf()
        )
    }
    var expandedModel by remember { mutableStateOf<String?>(null) }
    var showRestModels by remember { mutableStateOf(false) }
    var compactAt by remember { mutableFloatStateOf(existing?.autoCompactAt ?: 0.8f) }

    fun build(): ProviderConfig = (existing ?: ProviderConfig()).copy(
        name = name.ifBlank { "未命名服务商" },
        baseUrl = baseUrl.trim(),
        apiKey = apiKey.trim(),
        model = model.trim(),
        models = selectedModels.toMutableList(),
        temperature = temperature.toDouble(),
        maxTokens = maxTokens.toIntOrNull() ?: 4096,
        contextWindow = ctxWindow,
        supportsVision = vision,
        reasoningEffort = effort,
        supportedEfforts = supportedEfforts.toMutableList(),
        modelConfigs = modelConfigs,
        autoCompactAt = compactAt,
        monthlyQuotaUsd = quota.toDoubleOrNull() ?: 0.0,
        priceInputPer1M = priceIn.toDoubleOrNull() ?: -1.0,
        priceCachedPer1M = priceCached.toDoubleOrNull() ?: -1.0,
        priceOutputPer1M = priceOut.toDoubleOrNull() ?: -1.0
    )

    fun pick(preset: Templates.ProviderPreset) {
        pickedPreset = preset.name
        name = preset.name
        baseUrl = preset.baseUrl
        model = preset.model
        apiKey = ""
        picking = false
    }

    // 进来就把这个服务商的模型列出来，不用手点
    LaunchedEffect(existing?.id) {
        if (existing != null && existing.baseUrl.isNotBlank()) {
            vm.fetchModels(existing)
        }
    }

    // 拉到的列表里没有当前默认模型时，自动指到第一个
    LaunchedEffect(vm.fetchedModels) {
        if (vm.fetchedModels.isNotEmpty() && model !in vm.fetchedModels) {
            model = vm.fetchedModels.first()
        }
    }

    Scaffold(topBar = {
        BackBar(
            if (existing == null) "添加服务商" else "编辑服务商",
            onBack,
            actions = {
                TextButton(onClick = {
                    vm.saveProvider(build())
                    vm.clearTestResult()
                    onBack()
                }) { Text("保存") }
            }
        )
    }) { pad ->
        Column(
            Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FormSection("1", "选服务商", "预置一键填好，也可以自己填")
            if (picking) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    AppTextField(
                        value = query,
                        onValueChange = { v ->
                            query = v
                            val u = Regex("""https?://\S+""").find(v)?.value
                            val k = Regex("""(sk-[A-Za-z0-9_\-]{6,}|[A-Za-z0-9]{24,})""").find(v)?.value
                            if (u != null && k != null) {
                                name = "自定义"
                                baseUrl = u.trimEnd('/')
                                apiKey = k
                                query = ""
                                pickedPreset = "自定义"
                                picking = false
                            }
                        },
                        placeholder = { Text("搜厂商，或粘贴地址和 Key") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        pickedPreset = "自定义"
                        if (name.isBlank()) name = "自定义"
                        picking = false
                    }) { Text("自定义") }
                }

                Templates.providerPresetGroups.forEach { group ->
                    val list = Templates.providerPresets.filter { p ->
                        p.group == group && (
                            query.isBlank() ||
                                p.name.contains(query, ignoreCase = true) ||
                                p.note.contains(query, ignoreCase = true) ||
                                p.baseUrl.contains(query, ignoreCase = true)
                            )
                    }
                    if (list.isNotEmpty()) {
                        Text(
                            group,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        list.chunked(2).forEach { row ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                row.forEach { preset ->
                                    val interaction = remember { MutableInteractionSource() }
                                    Card(
                                        modifier = Modifier
                                            .weight(1f)
                                            .pressScale(interaction)
                                            .clickable(
                                                interactionSource = interaction,
                                                indication = LocalIndication.current,
                                                onClick = { pick(preset) }
                                            ),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                    ) {
                                        Column(Modifier.padding(12.dp)) {
                                            Text(
                                                preset.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (preset.note.isNotBlank()) {
                                                Spacer(Modifier.height(3.dp))
                                                Text(
                                                    preset.note,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            } else {
                if (existing == null) {
                    TextButton(onClick = { picking = true }, modifier = Modifier.padding(0.dp)) {
                        Text("← 重新选服务商")
                    }
                }

                FormSection("2", "接入信息", "地址填到 /v1 这一层")
                AppTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                AppTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("接口地址（到 /v1 这一层）") },
                    placeholder = { Text("https://api.deepseek.com/v1") },
                    singleLine = true,
                    secret = true,
                    modifier = Modifier.fillMaxWidth()
                )
                AppTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    secret = true,
                    modifier = Modifier.fillMaxWidth()
                )

                FormSection("3", "模型", "拉取后勾选要用的")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("模型名") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = { vm.fetchModels(build()) },
                        enabled = !vm.fetchingModels && baseUrl.isNotBlank()
                    ) { Text(if (vm.fetchingModels) "拉取中" else "拉取模型") }
                }

                if (vm.fetchingModels && vm.fetchedModels.isEmpty()) {
                    Text(
                        "正在读取这个服务商的模型列表…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (vm.fetchedModels.isNotEmpty()) {
                    val enabledModels = vm.fetchedModels.filter { it in selectedModels }
                    val restModels = vm.fetchedModels.filter { it !in selectedModels }

                    Text(
                        "共 ${vm.fetchedModels.size} 个 · 已启用 ${enabledModels.size} 个",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // 已启用的直接列出来
                    enabledModels.forEach { m ->
                        val cfg = modelConfigs.firstOrNull { it.name == m } ?: ModelConfig(name = m)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = true,
                                        onCheckedChange = { c ->
                                            if (!c) {
                                                selectedModels = selectedModels - m
                                                modelConfigs = modelConfigs.filterNot { it.name == m }.toMutableList()
                                                if (model == m) model = selectedModels.firstOrNull() ?: ""
                                            }
                                        }
                                    )
                                    Text(
                                        m,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val isDefault = model == m
                                    Text(
                                        if (isDefault) "默认 ✓" else "设默认",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isDefault) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { if (!isDefault) model = m }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                    TextButton(onClick = {
                                        expandedModel = if (expandedModel == m) null else m
                                    }) {
                                        Text(
                                            if (expandedModel == m) "收起" else "参数",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                                if (expandedModel == m) {
                                    ModelParamsEditor(cfg) { updated ->
                                        modelConfigs = modelConfigs
                                            .filterNot { it.name == m }
                                            .toMutableList()
                                            .apply { add(updated) }
                                    }
                                }
                            }
                        }
                    }

                    // 没启用的折起来
                    if (restModels.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { showRestModels = !showRestModels },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                ) {
                                    Text(
                                        "未启用 ${restModels.size} 个",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        if (showRestModels) "收起" else "展开选用",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                AnimatedVisibility(visible = showRestModels) {
                                    Column {
                                        Spacer(Modifier.height(6.dp))
                                        restModels.forEach { m ->
                                            Row(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        selectedModels = selectedModels + m
                                                        if (model.isBlank()) model = m
                                                    }
                                                    .padding(vertical = 4.dp),
                                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                            ) {
                                                Checkbox(checked = false, onCheckedChange = { c ->
                                                    if (c) {
                                                        selectedModels = selectedModels + m
                                                        if (model.isBlank()) model = m
                                                    }
                                                })
                                                Text(
                                                    m,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (selectedModels.size > 1) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "写作时用「默认」那一个；其余留作备选，写作台可以随时换",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        val bsrc = remember { MutableInteractionSource() }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .pressScale(bsrc, 0.97f)
                                .clickable(interactionSource = bsrc, indication = null) {
                                    showBulk = true
                                }
                                .padding(vertical = 13.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "统一配置这 ${selectedModels.size} 个模型",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                FormSection("4", "生成参数", "温度 / 输出长度 / 上下文 / 思考强度")
                TextButton(
                    onClick = { showAdvanced = !showAdvanced },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (showAdvanced) "收起高级设置" else "高级设置：温度 / 输出长度 / 计价")
                }

                if (showAdvanced) {
                    Text(
                        "温度 ${String.format("%.2f", temperature)}（写小说建议 0.7~1.0）",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(temperature, { temperature = it }, valueRange = 0f..1.5f)
                    Text("单次最大输出", fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(4096, 8192, 16384, 32768, 65536, 131072).forEach { n ->
                            AppChip(
                                selected = maxTokens == n.toString(),
                                onClick = { maxTokens = n.toString() },
                                label = { Text("${n / 1024}K") }
                            )
                        }
                    }
                    Text(
                        "正文建议 4K 以上；设太大但模型不支持会被接口拒绝",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("上下文窗口", fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(32000, 64000, 128000, 200000, 1000000).forEach { n ->
                            AppChip(
                                selected = ctxWindow == n,
                                onClick = { ctxWindow = n },
                                label = { Text(if (n >= 1000000) "1M" else "${n / 1000}K") }
                            )
                        }
                    }
                    Text(
                        "模型能吃多长的前文；填大了但模型不支持会被接口截断",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("视觉模型", fontWeight = FontWeight.Bold)
                            Text(
                                "能读图：封面、人物参考图",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = vision, onCheckedChange = { vision = it })
                    }

                    Text(
                        "这个模型支持哪些思考强度",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Templates.effortLevels.forEach { (k, label) ->
                            AppChip(
                                selected = k in supportedEfforts,
                                onClick = {
                                    val next = if (k in supportedEfforts) {
                                        supportedEfforts - k
                                    } else {
                                        supportedEfforts + k
                                    }
                                    supportedEfforts = if (next.isEmpty()) listOf("off") else next
                                    if (effort !in supportedEfforts) effort = supportedEfforts.first()
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                    Text(
                        "勾上哪些，写作台里就只能选哪些。拿不准就只勾「关闭」，多数模型写小说用不着思考模式",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        "自动压缩阈值：${(compactAt * 100).toInt()}%",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Slider(compactAt, { compactAt = it }, valueRange = 0.5f..0.95f)
                    Text(
                        "上下文用到这个比例，就把早期剧情压成摘要，给新章节腾地方",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    FormSection("5", "用量与计价", "留空就不统计花费")
                    AppTextField(
                        value = quota,
                        onValueChange = { quota = it },
                        label = { Text("订阅月额度 $（没有就留空）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "自定义单价 $/百万 token，留空则用内置价格表",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AppTextField(
                        value = priceIn,
                        onValueChange = { priceIn = it },
                        label = { Text("输入（未命中缓存）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    AppTextField(
                        value = priceCached,
                        onValueChange = { priceCached = it },
                        label = { Text("输入（命中缓存）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    AppTextField(
                        value = priceOut,
                        onValueChange = { priceOut = it },
                        label = { Text("输出") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Button(
                    onClick = { vm.testProvider(build()) },
                    enabled = !vm.testing && baseUrl.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (vm.testing) "测试中…" else "测试连接") }

                vm.testResult?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (showBulk) {
                    AlertDialog(
                        onDismissRequest = { showBulk = false },
                        title = { Text("统一配置 ${selectedModels.size} 个模型") },
                        text = {
                            Column(
                                Modifier.verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    "这里设的值会覆盖每个模型各自的参数；名字保留不变。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                ModelParamsEditor(bulkCfg) { bulkCfg = it }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                modelConfigs = selectedModels
                                    .map { m -> bulkCfg.copy(name = m) }
                                    .toMutableList()
                                showBulk = false
                            }) { Text("应用到全部") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showBulk = false }) { Text("取消") }
                        }
                    )
                }

                Button(
                    onClick = {
                        vm.saveProvider(build())
                        vm.clearTestResult()
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("保存") }

                if (existing != null) {
                    OutlinedButton(
                        onClick = { vm.deleteProvider(existing.id); onBack() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("删除这套配置") }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ============================== 全局设置 ==============================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    vm: AppViewModel,
    onBack: () -> Unit
) {
    val current = vm.data.settings
    var outlineId by remember { mutableStateOf(current.outlineProviderId) }
    var bodyId by remember { mutableStateOf(current.bodyProviderId) }
    var retry by remember { mutableStateOf(current.autoRetry) }
    var prompt by remember { mutableStateOf(current.customSystemPrompt) }
    var rate by remember { mutableStateOf(current.usdToCny.toString()) }
    var budget by remember { mutableStateOf(current.contextBudgetChars) }
    var memoryOn by remember { mutableStateOf(current.memoryEnabled) }
    val providers = vm.data.providers

    @Composable
    fun ProviderPicker(label: String, selected: String, onPick: (String) -> Unit) {
        Text(label, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppChip(selected = selected.isEmpty(), onClick = { onPick("") }, label = { Text("跟随作品") })
            providers.forEach { p ->
                AppChip(selected = selected == p.id, onClick = { onPick(p.id) }, label = { Text(p.name) })
            }
        }
    }

    Scaffold(topBar = { BackBar("生成设置", onBack) }) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("分阶段模型（便宜模型出大纲、强模型写正文，就在这里分开指定）", style = MaterialTheme.typography.bodySmall)
            ProviderPicker("大纲 / 细纲用", outlineId) { outlineId = it }
            ProviderPicker("正文用", bodyId) { bodyId = it }

            Text("失败自动重试次数", fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 1, 2, 3).forEach { n ->
                    AppChip(selected = retry == n, onClick = { retry = n }, label = { Text("$n 次") })
                }
            }

            Text("记忆与上下文", fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("故事记忆库")
                    Text("跨章节互通人物/事实/伏笔/前情，写完一章自动更新", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = memoryOn, onCheckedChange = { memoryOn = it })
            }
            Text("上下文预算（每章注入的记忆+前情上限）", style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(3000, 6000, 10000, 15000).forEach { n ->
                    AppChip(selected = budget == n, onClick = { budget = n }, label = { Text("$n 字") })
                }
            }

            Text("自定义系统提示词", fontWeight = FontWeight.Bold)
            AppTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = { Text("留空 = 用内置作家角色；填了会替换它") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            Text("花费显示", fontWeight = FontWeight.Bold)
            AppTextField(
                value = rate,
                onValueChange = { rate = it },
                label = { Text("美元兑人民币汇率（用量页切到 ¥ 时换算用）") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    vm.saveSettings(
                        AppSettings(
                            outlineProviderId = outlineId,
                            bodyProviderId = bodyId,
                            autoRetry = retry,
                            customSystemPrompt = prompt.trim(),
                            usdToCny = rate.toDoubleOrNull() ?: 7.2,
                            displayCurrency = current.displayCurrency,
                            contextBudgetChars = budget,
                            memoryEnabled = memoryOn,
                            hapticsEnabled = current.hapticsEnabled,
                            hapticsStrength = current.hapticsStrength,
                            // 下面这些在别的页面改，这里原样带过，别被默认值冲掉
                            readerFontSize = current.readerFontSize,
                            readerLineHeight = current.readerLineHeight,
                            readerTheme = current.readerTheme,
                            readerPageMode = current.readerPageMode,
                            updateServerUrl = current.updateServerUrl,
                            updateHistory = current.updateHistory
                        )
                    )
                    onBack()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("保存设置") }
        }
    }
}

// ============================== 工作台（生成 / 编辑 / 导出 / 记忆库） ==============================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkbenchScreen(
    vm: AppViewModel,
    projectId: String,
    onRead: () -> Unit,
    onOpenMemory: () -> Unit,
    onBack: () -> Unit
) {
    val project = vm.project(projectId)
    val context = LocalContext.current
    var editing by remember { mutableStateOf<Chapter?>(null) }
    var showAppend by remember { mutableStateOf(false) }
    var showModel by remember { mutableStateOf(false) }
    var showOutline by remember { mutableStateOf(false) }
    var expandedChapter by remember { mutableStateOf(-1) }
    var thinkingOpen by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }

    val generating = vm.generatingProjectId == projectId

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val src = remember { MutableInteractionSource() }
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
                        project?.title ?: "工作台",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (project != null) {
                        Text(
                            "${project.genre} · ${WordCount.label(project.totalWords)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (project != null && project.finishedChapters > 0) {
                    val rsrc = remember { MutableInteractionSource() }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .pressScale(rsrc, 0.94f)
                            .clickable(interactionSource = rsrc, indication = null, onClick = onRead)
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Text(
                            "阅读",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                }
                if (project != null) {
                    var menuOpen by remember { mutableStateOf(false) }
                    val msrc = remember { MutableInteractionSource() }
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                if (menuOpen) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                            )
                            .pressScale(msrc, 0.9f)
                            .clickable(interactionSource = msrc, indication = null) {
                                menuOpen = !menuOpen
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "更多",
                            modifier = Modifier.size(19.dp),
                            tint = if (menuOpen) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (menuOpen) {
                        Popup(
                            alignment = Alignment.TopEnd,
                            offset = IntOffset(0, 150),
                            onDismissRequest = { menuOpen = false },
                            properties = PopupProperties(focusable = true)
                        ) {
                            Column(
                                Modifier
                                    .width(300.dp)
                                    .clip(RoundedCornerShape(26.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(26.dp)
                                    )
                                    .padding(12.dp)
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ActionTile("作品信息", Icons.Default.Edit, Modifier.weight(1f)) {
                                        menuOpen = false
                                        showInfo = true
                                    }
                                    ActionTile("阅读", Icons.Default.MenuBook, Modifier.weight(1f)) {
                                        menuOpen = false
                                        onRead()
                                    }
                                    ActionTile("记忆库", Icons.Default.Memory, Modifier.weight(1f)) {
                                        menuOpen = false
                                        onOpenMemory()
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Exporter.Format.entries.forEach { fmt ->
                                        ActionTile(fmt.label, Icons.Default.Share, Modifier.weight(1f)) {
                                            menuOpen = false
                                            vm.exportFile(projectId, fmt)?.let { shareFile(context, it) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { pad ->
        val p = project
        if (p == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("作品不存在", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        val total = p.chapters.size.coerceAtLeast(p.chapterCount).coerceAtLeast(1)
        val done = p.finishedChapters
        val frac by animateFloatAsState(
            targetValue = (done.toFloat() / total).coerceIn(0f, 1f),
            animationSpec = tween(800, easing = FastOutSlowInEasing),
            label = "wbProgress"
        )
        val usableProviders = vm.data.providers.filter { it.baseUrl.isNotBlank() && it.enabled }
        val currentProvider = vm.data.providers.firstOrNull { it.id == p.providerId }
            ?: vm.data.providers.firstOrNull { it.id == vm.data.activeProviderId }
        val currentModel = p.providerModel.ifBlank { currentProvider?.model.orEmpty() }

        LazyColumn(
            Modifier.padding(pad).padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 进度
            item {
                Column(Modifier.staggerEnter(0)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "$done / $total 章",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${(frac * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(7.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(frac)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }

            // 主按钮：继续写 / 加更 占主位；重新生成降级成小字
            item {
                Row(
                    Modifier.fillMaxWidth().staggerEnter(1),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val src = remember { MutableInteractionSource() }
                    val bg = if (generating) MaterialTheme.colorScheme.surfaceVariant
                    else MaterialTheme.colorScheme.primary
                    val fg = if (generating) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onPrimary
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(22.dp))
                            .background(bg)
                            .pressScale(src, 0.98f)
                            .clickable(interactionSource = src, indication = null) {
                                if (generating) vm.cancelGenerate() else vm.startGenerate(projectId)
                            }
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (generating) {
                                ThinkingDots(MaterialTheme.colorScheme.onSurfaceVariant, 6)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                when {
                                    generating -> "停止"
                                    done == 0 -> "一键生成"
                                    done >= total -> "再写一章"
                                    else -> "继续写第 ${done + 1} 章"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = fg,
                                maxLines = 1
                            )
                        }
                    }

                    // 加更：和主按钮同等高度，右侧固定宽度
                    if (!generating) {
                        val asrc = remember { MutableInteractionSource() }
                        Box(
                            Modifier
                                .width(96.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .pressScale(asrc, 0.96f)
                                .clickable(interactionSource = asrc, indication = null) {
                                    showAppend = true
                                }
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "加更",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // 次要操作：记忆库 / 导出
            item {
                Row(
                    Modifier.fillMaxWidth().staggerEnter(2),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val msrc = remember { MutableInteractionSource() }
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .pressScale(msrc, 0.96f)
                            .clickable(interactionSource = msrc, indication = null, onClick = onOpenMemory)
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("记忆库", style = MaterialTheme.typography.labelMedium)
                    }
                    Exporter.Format.entries.forEach { format ->
                        val esrc = remember { MutableInteractionSource() }
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .pressScale(esrc, 0.96f)
                                .clickable(interactionSource = esrc, indication = null) {
                                    vm.exportFile(projectId, format)?.let { shareFile(context, it) }
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(format.label, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // 模型行
            if (usableProviders.isNotEmpty()) {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .staggerEnter(3)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { showModel = !showModel }
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "当前模型",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    (currentProvider?.name ?: "未设置") +
                                        (if (currentModel.isNotBlank()) " · $currentModel" else ""),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                if (showModel) "收起" else "切换",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        AnimatedVisibility(showModel) {
                            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                                usableProviders.forEach { prov ->
                                    Text(
                                        prov.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                    )
                                    val list = prov.models.ifEmpty {
                                        listOfNotNull(prov.model.takeIf { it.isNotBlank() })
                                    }.filter { vm.isModelEnabled(prov.id, it) }
                                    if (list.isEmpty()) {
                                        Text(
                                            "这个服务商还没选模型",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                    list.forEach { m ->
                                        val sel = prov.id == currentProvider?.id && m == currentModel
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                    if (sel) MaterialTheme.colorScheme.primaryContainer
                                                    else MaterialTheme.colorScheme.background.copy(alpha = 0f)
                                                )
                                                .clickable {
                                                    vm.setProjectProvider(p.id, prov.id, m)
                                                    showModel = false
                                                }
                                                .padding(horizontal = 10.dp, vertical = 9.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                m,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (sel) {
                                                Text(
                                                    "✓",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                                val supported = currentProvider?.supportedEfforts?.ifEmpty { listOf("off") }
                                    ?: listOf("off")
                                if (supported.size > 1 && currentProvider != null) {
                                    val curEffort = p.reasoningEffort.ifBlank { currentProvider.reasoningEffort }
                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        "思考强度",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        supported.forEach { k ->
                                            val label = Templates.effortLevels
                                                .firstOrNull { it.first == k }?.second ?: k
                                            AppChip(
                                                selected = curEffort == k,
                                                onClick = { vm.setProjectEffort(p.id, k) },
                                                label = { Text(label) }
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "改完从下一章开始生效，正在写的那章不受影响",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 生成状态
            if (generating || vm.genStatus.isNotBlank() || vm.genReasoning.isNotBlank()) {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(16.dp)
                    ) {
                        if (vm.genStatus.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (generating) {
                                    ThinkingDots(MaterialTheme.colorScheme.primary, 5)
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(
                                    vm.genStatus,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (vm.genReasoning.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Row(
                                Modifier.fillMaxWidth().clickable { thinkingOpen = !thinkingOpen },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "思考过程",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "${vm.genReasoning.length} 字",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    if (thinkingOpen) "收起" else "展开",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            AnimatedVisibility(thinkingOpen) {
                                Text(
                                    vm.genReasoning,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            vm.genError?.let { err ->
                item {
                    Text(
                        err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // 大纲
            if (p.outline.isNotBlank()) {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { showOutline = !showOutline }
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "全书大纲",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                if (showOutline) "收起" else "展开",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        AnimatedVisibility(showOutline) {
                            Text(
                                p.outline,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                            )
                        }
                    }
                }
            }

            // 章节标题
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp, start = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "章节",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${p.chapters.size} 章",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            if (p.chapters.isEmpty()) {
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(20.dp)
                    ) {
                        Text(
                            "还没有章节。点上面的按钮，会先出大纲，再一章一章写。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            itemsIndexed(p.chapters, key = { _, c -> c.index }) { i, ch ->
                val open = expandedChapter == ch.index
                Column(
                    Modifier
                        .fillMaxWidth()
                        .staggerEnter(i + 4)
                        .animateItem()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedChapter = if (open) -1 else ch.index
                            }
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(
                                    if (ch.done) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${ch.index}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (ch.done) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                ch.title.ifBlank { "未命名" },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                if (ch.done) WordCount.label(ch.content.length) else "待写",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (ch.done) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.outline
                            )
                        }
                        Text(
                            if (open) "▴" else "▾",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    AnimatedVisibility(open) {
                        Column(Modifier.padding(start = 56.dp, end = 14.dp, bottom = 12.dp)) {
                            if (ch.outline.isNotBlank()) {
                                Text(
                                    ch.outline,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(6.dp))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (ch.done && !generating) {
                                    TextButton(onClick = { vm.rewriteChapter(projectId, ch.index) }) {
                                        Text("重写")
                                    }
                                }
                                TextButton(onClick = { editing = ch }) { Text("编辑") }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
                val dsrc = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .pressScale(dsrc, 0.97f)
                        .clickable(interactionSource = dsrc, indication = null) {
                            vm.deleteProject(projectId)
                            onBack()
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "删除这部作品",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }

    if (showAppend) {
        AlertDialog(
            onDismissRequest = { showAppend = false },
            title = { Text("加更") },
            text = {
                Column {
                    Text("当前共 ${project?.chapters?.size ?: 0} 章，写完的话可以在这里往后加。")
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "会先生成新章节的细纲，再接着写正文；已有章节不动。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 10, 20).forEach { n ->
                            AppChip(
                                selected = false,
                                onClick = {
                                    showAppend = false
                                    vm.appendChapters(projectId, n)
                                },
                                label = { Text("+$n 章") }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAppend = false }) { Text("取消") }
            }
        )
    }

    if (showInfo && project != null) {
        var newTitle by remember(project.id) { mutableStateOf(project.title) }
        var newGenre by remember(project.id) { mutableStateOf(project.genre) }
        var newStyle by remember(project.id) { mutableStateOf(project.style) }
        var newPerspective by remember(project.id) { mutableStateOf(project.perspective) }
        var newProtagonist by remember(project.id) { mutableStateOf(project.protagonist) }
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text("作品信息") },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AppTextField(
                        newTitle,
                        { newTitle = it },
                        label = { Text("书名") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Text(
                        "题材",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Templates.genres.forEach { g ->
                            AppChip(
                                selected = newGenre == g.name,
                                onClick = { newGenre = g.name },
                                label = { Text(g.name) }
                            )
                        }
                    }
                    Text(
                        "风格",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Templates.styles.forEach { st ->
                            AppChip(
                                selected = newStyle == st.name,
                                onClick = { newStyle = st.name },
                                label = { Text(st.name) }
                            )
                        }
                    }
                    Text(
                        "视角",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Templates.perspectives.forEach { ps ->
                            AppChip(
                                selected = newPerspective == ps,
                                onClick = { newPerspective = ps },
                                label = { Text(ps) }
                            )
                        }
                    }
                    AppTextField(
                        newProtagonist,
                        { newProtagonist = it },
                        label = { Text("主角设定") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    project.title = newTitle.ifBlank { "未命名小说" }
                    project.genre = newGenre
                    project.style = newStyle
                    project.perspective = newPerspective
                    project.protagonist = newProtagonist
                    vm.updateProjectInfo(project)
                    showInfo = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showInfo = false }) { Text("取消") }
            }
        )
    }

    editing?.let { ch ->
        var title by remember(ch.index) { mutableStateOf(ch.title) }
        var outline by remember(ch.index) { mutableStateOf(ch.outline) }
        var content by remember(ch.index) { mutableStateOf(ch.content) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("编辑第${ch.index}章") },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppTextField(title, { title = it }, label = { Text("标题") }, modifier = Modifier.fillMaxWidth())
                    AppTextField(outline, { outline = it }, label = { Text("本章细纲") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    AppTextField(content, { content = it }, label = { Text("正文") }, modifier = Modifier.fillMaxWidth(), minLines = 8)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateChapter(projectId, ch.copy(title = title, outline = outline, content = content))
                    editing = null
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) { Text("取消") }
            }
        )
    }
}

// ============================== 记忆库（查看 / 编辑 / 上下文预览） ==============================

@Composable
fun MemoryScreen(
    vm: AppViewModel,
    projectId: String,
    onBack: () -> Unit
) {
    val project = vm.project(projectId)
    Scaffold(topBar = { BackBar("记忆库 · ${project?.title ?: ""}", onBack) }) { pad ->
        if (project == null) {
            Text("作品不存在", Modifier.padding(pad).padding(24.dp))
            return@Scaffold
        }
        // 本地编辑副本，点保存才落盘
        var characters by remember { mutableStateOf(project.memory.characters.map { it.copy() }) }
        var factsText by remember { mutableStateOf(project.memory.facts.joinToString("\n")) }
        var threadsText by remember { mutableStateOf(project.memory.threads.joinToString("\n")) }
        var compressed by remember { mutableStateOf(project.memory.compressedSummary) }
        val recents = project.memory.recentSummaries

        // 下一章的上下文预览
        val nextChapter = project.chapters.firstOrNull { !it.done }
        val preview = remember(characters, factsText, threadsText, compressed, recents) {
            if (nextChapter == null) "" else {
                val probe = project.copy(
                    memory = StoryMemory(
                        compressedSummary = compressed,
                        recentSummaries = recents.toMutableList(),
                        characters = characters.map { it.copy() }.toMutableList(),
                        facts = factsText.lines().map { it.trim() }.filter { it.isNotBlank() }.toMutableList(),
                        threads = threadsText.lines().map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
                    )
                )
                val prevTail = project.chapters
                    .lastOrNull { it.index < nextChapter.index && it.done }
                    ?.content?.takeLast(600).orEmpty()
                ContextBuilder.assemble(
                    probe, nextChapter, prevTail,
                    vm.data.settings.contextBudgetChars, vm.data.settings.memoryEnabled
                )
            }
        }

        LazyColumn(
            Modifier.padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "这份记忆会在每次生成新章节时注入上下文，让不同章节（不同对话）之间互通。写完一章自动更新，你也可以手动改。",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            item { Text("人物档案（${characters.size}）", fontWeight = FontWeight.Bold) }
            items(characters.indices.toList(), key = { "c$it" }) { i ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppTextField(
                            value = characters[i].name,
                            onValueChange = { v -> characters = characters.toMutableList().also { it[i] = it[i].copy(name = v) } },
                            label = { Text("人物名") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        AppTextField(
                            value = characters[i].info,
                            onValueChange = { v -> characters = characters.toMutableList().also { it[i] = it[i].copy(info = v) } },
                            label = { Text("最新状态") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        TextButton(onClick = { characters = characters.toMutableList().also { it.removeAt(i) } }) {
                            Text("删除这个人物")
                        }
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = { characters = characters + MemoryCharacter("", "") },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("＋ 添加人物") }
            }
            item {
                Text("关键事实（一行一条）", fontWeight = FontWeight.Bold)
                AppTextField(
                    value = factsText, onValueChange = { factsText = it },
                    modifier = Modifier.fillMaxWidth(), minLines = 3
                )
            }
            item {
                Text("未回收伏笔（一行一条）", fontWeight = FontWeight.Bold)
                AppTextField(
                    value = threadsText, onValueChange = { threadsText = it },
                    modifier = Modifier.fillMaxWidth(), minLines = 3
                )
            }
            item {
                Text("长程摘要（早期剧情压缩后的梗概）", fontWeight = FontWeight.Bold)
                AppTextField(
                    value = compressed, onValueChange = { compressed = it },
                    modifier = Modifier.fillMaxWidth(), minLines = 3
                )
                if (recents.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("近期章节摘要（${recents.size} 条，攒长了会自动压缩）", fontWeight = FontWeight.Bold)
                    recents.forEach { Text("· $it", style = MaterialTheme.typography.bodySmall) }
                }
            }
            if (nextChapter != null) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "下一章（第${nextChapter.index}章）将注入的上下文：${preview.length} 字 ≈ ${ContextBuilder.estimateTokens(preview)} token",
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(preview.ifBlank { "（暂无，记忆库还是空的）" }, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = {
                        vm.saveMemory(
                            projectId,
                            StoryMemory(
                                compressedSummary = compressed,
                                recentSummaries = recents.toMutableList(),
                                characters = characters.filter { it.name.isNotBlank() }
                                    .map { it.copy() }.toMutableList(),
                                facts = factsText.lines().map { it.trim() }.filter { it.isNotBlank() }.toMutableList(),
                                threads = threadsText.lines().map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
                            )
                        )
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("保存记忆库") }
            }
        }
    }
}

// ============================== 用量仪表盘 ==============================

@Composable
fun StatsScreen(
    vm: AppViewModel,
    onBack: () -> Unit
) {
    val usage = vm.data.usage
    val rate = vm.data.settings.usdToCny
    var range by remember { mutableStateOf("month") }

    val now = System.currentTimeMillis()
    val startAt = when (range) {
        "today" -> Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        "week" -> now - 7L * 24 * 3600 * 1000
        "month" -> Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        else -> 0L
    }
    val rows = usage.filter { it.time >= startAt }

    val totalPrompt = rows.sumOf { it.promptTokens }
    val totalCompletion = rows.sumOf { it.completionTokens }
    val totalCached = rows.sumOf { it.cachedTokens }
    val totalTokens = totalPrompt + totalCompletion
    val totalCost = rows.sumOf { it.costUsd }
    val hitRate = if (totalPrompt > 0) totalCached * 100.0 / totalPrompt else 0.0
    val speeds = rows.filter { it.completionTokens > 0 && it.latencyMs > 0 }.map { it.tokensPerSecond }
    val avgSpeed = if (speeds.isNotEmpty()) speeds.average() else 0.0
    val unpriced = rows.count { !it.priced }

    var currency by remember { mutableStateOf(vm.data.settings.displayCurrency) }
    fun money(usd: Double): String =
        if (currency == "CNY") "¥" + String.format("%.3f", usd * rate)
        else "$" + String.format("%.3f", usd)

    fun fmtTokens(n: Int): String = when {
        n >= 1_000_000 -> String.format("%.2fM", n / 1_000_000.0)
        n >= 1_000 -> String.format("%.1fK", n / 1_000.0)
        else -> n.toString()
    }

    Scaffold(topBar = { BackBar("用量统计", onBack) }) { pad ->
        LazyColumn(
            Modifier.padding(pad).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "today" to "今天",
                        "week" to "近 7 天",
                        "month" to "本月",
                        "all" to "全部"
                    ).forEach { (k, label) ->
                        AppChip(
                            selected = range == k,
                            onClick = { range = k },
                            label = { Text(label) }
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            "总花费",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                            Text(
                                money(totalCost),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(Modifier.weight(1f))
                            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                                Text(
                                    "${rows.size} 次调用",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                                Text(
                                    "均速 ${String.format("%.0f", avgSpeed)} tok/s",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth()) {
                            StatCell("总 token", fmtTokens(totalTokens), Modifier.weight(1f))
                            StatCell("输入", fmtTokens(totalPrompt), Modifier.weight(1f))
                            StatCell("输出", fmtTokens(totalCompletion), Modifier.weight(1f))
                            StatCell("命中", "${String.format("%.0f", hitRate)}%", Modifier.weight(1f))
                        }
                    }
                }
            }

            if (unpriced > 0) {
                item {
                    Text(
                        "有 $unpriced 次调用没有单价，花费未计入；在服务商的「高级设置」里补上单价即可",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                SectionCard("缓存命中") {
                    val cachedPart = if (totalPrompt > 0) totalCached.toFloat() / totalPrompt else 0f
                    LinearProgressIndicator(
                        progress = { cachedPart },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            "命中 ${fmtTokens(totalCached)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "未命中 ${fmtTokens(totalPrompt - totalCached)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            val byProvider = rows.groupBy { it.providerName.ifBlank { "未命名" } }
                .mapValues { (_, list) ->
                    Triple(
                        list.sumOf { it.promptTokens + it.completionTokens },
                        list.sumOf { it.promptTokens },
                        list.sumOf { it.cachedTokens }
                    ) to list.sumOf { it.costUsd }
                }
                .entries.sortedByDescending { it.value.first.first }
            if (byProvider.isNotEmpty()) {
                item { SectionTitle("按服务商") }
                val maxTok = byProvider.maxOf { it.value.first.first }.coerceAtLeast(1)
                items(byProvider, key = { "p" + it.key }) { e ->
                    val (toks, prompt, cached) = e.value.first
                    val cost = e.value.second
                    val hit = if (prompt > 0) cached * 100.0 / prompt else 0.0
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(Modifier.fillMaxWidth()) {
                                Text(
                                    e.key,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(money(cost), style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(Modifier.height(8.dp))
                            BarRow(toks.toFloat() / maxTok)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "${fmtTokens(toks)} token · 命中 ${String.format("%.0f", hit)}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            val byModel = rows.groupBy { it.model.ifBlank { "未指定" } }
                .mapValues { (_, list) ->
                    Triple(
                        list.sumOf { it.promptTokens + it.completionTokens },
                        list.sumOf { it.promptTokens },
                        list.sumOf { it.cachedTokens }
                    ) to list.sumOf { it.costUsd }
                }
                .entries.sortedByDescending { it.value.first.first }
            if (byModel.isNotEmpty()) {
                item { SectionTitle("按模型") }
                val maxTok = byModel.maxOf { it.value.first.first }.coerceAtLeast(1)
                items(byModel, key = { "m" + it.key }) { e ->
                    val (toks, prompt, cached) = e.value.first
                    val hit = if (prompt > 0) cached * 100.0 / prompt else 0.0
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(Modifier.fillMaxWidth()) {
                                Text(
                                    e.key,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(money(e.value.second), style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(Modifier.height(8.dp))
                            BarRow(toks.toFloat() / maxTok)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "${fmtTokens(toks)} token · 命中 ${String.format("%.0f", hit)}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            val byStage = rows.filter { it.stage.isNotBlank() }
                .groupBy { it.stage }
                .mapValues { (_, list) -> list.sumOf { it.promptTokens + it.completionTokens } }
                .entries.sortedByDescending { it.value }
            if (byStage.isNotEmpty()) {
                item { SectionTitle("按阶段") }
                item {
                    SectionCard(null) {
                        val maxTok = byStage.maxOf { it.value }.coerceAtLeast(1)
                        byStage.forEach { e ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Text(
                                    e.key,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.width(52.dp)
                                )
                                Box(Modifier.weight(1f)) { BarRow(e.value.toFloat() / maxTok) }
                                Text(
                                    "  " + fmtTokens(e.value),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            val byChapter = rows.filter { it.projectId.isNotBlank() }.groupBy { it.projectId }
            if (byChapter.isNotEmpty()) {
                item { SectionTitle("按作品 · 章节") }
                items(byChapter.entries.toList(), key = { "c" + it.key }) { e ->
                    val title = e.value.firstOrNull()?.projectTitle?.ifBlank { "未命名" } ?: "未命名"
                    val chapters = e.value.filter { it.chapterIndex > 0 }
                        .groupBy { it.chapterIndex }
                        .toList()
                        .sortedBy { it.first }
                    var expanded by remember { mutableStateOf(false) }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(
                                Modifier.fillMaxWidth().clickable { expanded = !expanded },
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(title, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${e.value.size} 次调用 · " +
                                            fmtTokens(e.value.sumOf { it.promptTokens + it.completionTokens }) +
                                            " token · " + money(e.value.sumOf { it.costUsd }),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    if (expanded) "▴" else "▾",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (expanded) {
                                Spacer(Modifier.height(8.dp))
                                val maxCh = chapters.maxOfOrNull {
                                    it.second.sumOf { r -> r.promptTokens + r.completionTokens }
                                }?.coerceAtLeast(1) ?: 1
                                chapters.forEach { (idx, list) ->
                                    val toks = list.sumOf { it.promptTokens + it.completionTokens }
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "第 $idx 章",
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.width(56.dp)
                                        )
                                        Box(Modifier.weight(1f)) { BarRow(toks.toFloat() / maxCh) }
                                        Text(
                                            "  " + fmtTokens(toks),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (chapters.isEmpty()) {
                                    Text(
                                        "这本书只跑过大纲 / 细纲，还没有章节用量",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (rows.isNotEmpty()) {
                item { SectionTitle("最近调用") }
                items(rows.sortedByDescending { it.time }.take(20), key = { "r" + it.id }) { r ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(Modifier.fillMaxWidth()) {
                                Text(
                                    r.model.ifBlank { "未指定" },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    fmtTime(r.time),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(
                                buildString {
                                    if (r.stage.isNotBlank()) append("${r.stage} · ")
                                    if (r.chapterIndex > 0) append("第${r.chapterIndex}章 · ")
                                    append("入 ${fmtTokens(r.promptTokens)}")
                                    append(" / 出 ${fmtTokens(r.completionTokens)}")
                                    if (r.cachedTokens > 0) append(" / 缓存 ${fmtTokens(r.cachedTokens)}")
                                    append(" · ${String.format("%.0f", r.tokensPerSecond)} tok/s")
                                    if (r.estimated) append(" · 估算")
                                    if (r.priced) append(" · " + money(r.costUsd))
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item { SectionTitle("账户余额") }
            if (vm.data.providers.isEmpty()) {
                item {
                    Text("还没有服务商配置", style = MaterialTheme.typography.bodySmall)
                }
            }
            items(vm.data.providers, key = { "bal" + it.id }) { p ->
                val state = vm.balances[p.id]
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                p.name,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { vm.fetchBalance(p.id) }) { Text("查询") }
                        }
                        when {
                            state == null -> Text(
                                "未查询",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            state.loading -> Text(
                                "查询中…",
                                style = MaterialTheme.typography.bodySmall
                            )
                            state.error != null -> Text(
                                state.error!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            else -> {
                                val info = state.info
                                if (info != null) {
                                    Text(
                                        "${info.currency} ${info.total}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (info.detail.isNotBlank()) {
                                        Text(
                                            info.detail,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    Text(
                                        "接口没有返回余额",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(28.dp)) }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun SectionCard(title: String?, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            if (title != null) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
            }
            content()
        }
    }
}

/** 一条横向比例条 */
@Composable
private fun BarRow(fraction: Float) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "bar"
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .fillMaxHeight()
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

// ============================== 导入小说（搜索 / 解析 / 下载） ==============================

@Composable
fun ImportScreen(
    vm: AppViewModel,
    onOpenBook: (String) -> Unit,
    onBack: (() -> Unit)? = null,
    onOpenScan: () -> Unit = {}
) {
    var input by remember { mutableStateOf("") }
    // 从外部带进来的书名（am start -e q 书名），进来就直接搜
    val incomingHost = LocalContext.current
    val incomingQuery = remember {
        (incomingHost as? android.app.Activity)?.intent?.getStringExtra("q").orEmpty()
    }
    LaunchedEffect(incomingQuery) {
        if (incomingQuery.isNotBlank()) {
            input = incomingQuery
            vm.searchOnline(incomingQuery)
        }
    }
    val catalog = vm.importCatalog
    val dlState = vm.downloadState()
    LaunchedEffect(dlState?.done, dlState?.running) { vm.syncFromStore() }
    val ctx = LocalContext.current
    var localMsg by remember { mutableStateOf("") }
    var pendingImport by remember {
        mutableStateOf<Pair<com.novelforge.app.data.BookParser.Parsed, String>?>(null)
    }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            var fileName = "imported.txt"
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) fileName = c.getString(idx) ?: fileName
            }
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes == null || bytes.isEmpty()) {
                localMsg = "这个文件读出来是空的"
            } else {
                val parsed = com.novelforge.app.data.BookParser.parse(bytes, fileName)
                if (parsed.chapters.isEmpty()) {
                    localMsg = "没解析出章节，可能格式不支持"
                } else {
                    // 先弹窗让用户确认/修改书名
                    pendingImport = parsed to fileName
                }
            }
        } catch (e: Exception) {
            localMsg = "导入失败：${e.message}"
        }
    }

    pendingImport?.let { (parsed, fileName) ->
        var titleInput by remember(parsed) {
            mutableStateOf(parsed.title.ifBlank { fileName.substringBeforeLast('.') })
        }
        // 判重：文件名、格式、内容都一样才算
        val dup = remember(parsed) { vm.findDuplicateBook(fileName, parsed.chapters) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(if (dup != null) "这本书已经有了" else "确认书名") },
            text = {
                Column {
                    if (dup != null) {
                        Text(
                            "书架里已有一本《${dup.title}》——文件名、格式、内容都相同。" +
                                "跳过 = 不导；覆盖 = 用这份替换书架里那本；重复导入 = 再存一本，名字自动加（1）。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    AppTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        label = { Text("书名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "共 ${parsed.chapters.size} 章 · ${WordCount.label(parsed.chapters.sumOf { it.content.length })}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                if (dup != null) {
                    Row {
                        TextButton(onClick = {
                            localMsg = "已跳过重复的《${dup.title}》"
                            pendingImport = null
                        }) { Text("跳过") }
                        TextButton(onClick = {
                            vm.overwriteImportedBook(dup.id, parsed, fileName)
                            localMsg = "已覆盖《${dup.title}》"
                            pendingImport = null
                            onOpenBook(dup.id)
                        }) { Text("覆盖") }
                        TextButton(onClick = {
                            val id = vm.importLocalBook(
                                parsed, titleInput.trim(),
                                sourceName = fileName, dedupeTitle = true
                            )
                            localMsg = "已作为副本导入《${titleInput.ifBlank { parsed.title }}》"
                            pendingImport = null
                            onOpenBook(id)
                        }) { Text("重复导入") }
                    }
                } else {
                    TextButton(onClick = {
                        val id = vm.importLocalBook(parsed, titleInput.trim(), sourceName = fileName)
                        localMsg = "已导入《${titleInput.ifBlank { parsed.title }}》共 ${parsed.chapters.size} 章"
                        pendingImport = null
                        onOpenBook(id)
                    }) { Text("导入") }
                }
            },
            dismissButton = {
                if (dup == null) {
                    TextButton(onClick = { pendingImport = null }) { Text("取消") }
                }
            }
        )
    }

    Scaffold(topBar = { BackBar("导入小说", onBack) }) { pad ->
        LazyColumn(
            Modifier.padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                val lsrc = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .pressScale(lsrc, 0.98f)
                        .clickable(interactionSource = lsrc, indication = null) {
                            picker.launch(arrayOf("*/*"))
                        }
                        .padding(18.dp)
                ) {
                    Column {
                        Text(
                            "从手机里选文件",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "支持 txt / md / epub / html / docx / zip，自动识别编码、按章节切分",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                        )
                        if (localMsg.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                localMsg,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
            item {
                val ssrc = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .pressScale(ssrc, 0.98f)
                        .clickable(interactionSource = ssrc, indication = null) {
                            onOpenScan()
                        }
                        .padding(18.dp)
                ) {
                    Column {
                        Text(
                            "扫描手机里的书",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "自动找 Download、Documents、小说等目录里的 txt / epub / docx，勾选后批量导入",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            // —— 未下完的书：接着下，地址还是原来那套 ——
            run {
                val unfinished = vm.unfinishedBooks()
                if (unfinished.isNotEmpty()) {
                    item {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text(
                                "未下完",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${unfinished.size} 本 · 随时接着下",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    items(unfinished, key = { "u_" + it.id }) { b ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.padding(14.dp).fillMaxWidth(),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "《${b.title}》",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        "${b.chapters.size} / ${b.pendingChapters.size} 章 · 已下的在书架",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                val isThis = dlState?.running == true && dlState.bookId == b.id
                                if (isThis) {
                                    Text(
                                        "下载中",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Button(
                                        onClick = { vm.resumeDownload(b.id) },
                                        enabled = dlState?.running != true
                                    ) { Text("继续") }
                                    Spacer(Modifier.width(4.dp))
                                    TextButton(onClick = { vm.removeImportedBook(b.id) }) {
                                        Text("删掉")
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(4.dp)) }
                }
            }
            item {
                Text(
                    "在线书库",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "输入书名，自动去几个免费书站找；也可以直接粘目录页地址。只下载公版或你有权阅读的内容。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                AppTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("书名，或目录页地址（http…）") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { vm.searchOnline(input) },
                        enabled = !vm.importBusy && input.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) { Text("搜书") }
                    OutlinedButton(
                        onClick = { vm.parseCatalog(input) },
                        enabled = !vm.importBusy && input.startsWith("http"),
                        modifier = Modifier.weight(1f)
                    ) { Text("解析地址") }
                }
                if (vm.importStatus.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        vm.importStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (vm.importStatus.startsWith("❌"))
                            MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val dl = vm.downloadState()
                if (dl != null && dl.running) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { dl.done.toFloat() / dl.total.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "《${dl.title}》 ${dl.done} / ${dl.total}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "后台下载中",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = { vm.cancelImport() }, modifier = Modifier.fillMaxWidth()) {
                        Text("停止下载，已下的留着")
                    }
                }
            }
            catalog?.let { book ->
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "《${book.title}》",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                buildString {
                                    if (book.author.isNotBlank()) append("${book.author} · ")
                                    append("${book.chapters.size} 章")
                                    append(" · ${book.sourceName}")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            if (!vm.importBusy) {
                                Button(
                                    onClick = { vm.startDownload(onDone = onOpenBook) },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("下载全本到书架") }
                            } else {
                                Text(
                                    "下载在后台跑，可以退出这个页面",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            if (vm.importHits.isNotEmpty() && catalog == null) {
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text("搜索结果", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "点一本看目录",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(vm.importHits, key = { it.sourceId + it.url }) { hit ->
                    val src = remember { MutableInteractionSource() }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pressScale(src, 0.98f)
                            .clickable(interactionSource = src, indication = null, enabled = !vm.importBusy) {
                                vm.openOnlineBook(hit)
                            }
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                hit.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                buildString {
                                    if (hit.author.isNotBlank()) append("${hit.author} · ")
                                    append(hit.sourceName)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================== 导入书：阅读 + 导出 ==============================

@Composable
fun ImportedBookScreen(
    vm: AppViewModel,
    bookId: String,
    onBack: () -> Unit
) {
    val book = vm.importedBook(bookId)
    val context = LocalContext.current
    Scaffold(topBar = { BackBar(book?.title ?: "导入的书", onBack) }) { pad ->
        if (book == null) {
            Text("这本书不存在", Modifier.padding(pad).padding(24.dp))
            return@Scaffold
        }
        LazyColumn(
            Modifier.padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("${book.author.ifBlank { "佚名" }} · ${book.chapters.size} 章 · 约 ${WordCount.label(book.totalWords)}", style = MaterialTheme.typography.bodySmall)
                Text("来源：${book.sourceUrl}", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Text("导出为", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Exporter.Format.entries.forEach { format ->
                        OutlinedButton(onClick = {
                            vm.exportImportedFile(bookId, format)?.let { shareFile(context, it) }
                        }) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Text(format.label)
                        }
                    }
                }
            }
            itemsIndexed(book.chapters, key = { _, ch -> ch.index }) { idx, ch ->
                Column(Modifier.staggerReveal(idx)) {
                    Text(ch.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(ch.content, style = MaterialTheme.typography.bodyLarge)
                }
            }
            item {
                OutlinedButton(
                    onClick = { vm.deleteImportedBook(bookId); onBack() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Text("删除这本书")
                }
            }
        }
    }
}

// ============================== 阅读页 ==============================

@Composable
fun ReaderScreen(
    vm: AppViewModel,
    projectId: String,
    onBack: () -> Unit
) {
    val project = vm.project(projectId)
    val chapters = project?.chapters?.filter { it.done }.orEmpty()
    var current by remember { mutableStateOf(0) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { BackBar(project?.title ?: "阅读", onBack) }
    ) { pad ->
        if (project == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("作品不存在", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        if (chapters.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("还没有写完的章节", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        val ch = chapters[current.coerceIn(0, chapters.size - 1)]
        Column(Modifier.padding(pad).fillMaxSize()) {
            LazyColumn(
                Modifier.weight(1f).padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                item {
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "第${ch.index}章",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        ch.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(22.dp))
                    Text(
                        ch.content.trim()
                            .lineSequence()
                            .filter { it.isNotBlank() }
                            .joinToString(separator = "\n") { "　　" + it.trim() },
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 17.sp,
                            lineHeight = 32.sp,
                            letterSpacing = 0.3.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(30.dp))
                }
            }
            // 翻章
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val psrc = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .pressScale(psrc, 0.96f)
                        .clickable(
                            interactionSource = psrc,
                            indication = null,
                            enabled = current > 0
                        ) { if (current > 0) current-- }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "上一章",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (current > 0) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.outline
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "${current + 1} / ${chapters.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(10.dp))
                val nsrc = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (current < chapters.size - 1) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surface
                        )
                        .pressScale(nsrc, 0.96f)
                        .clickable(
                            interactionSource = nsrc,
                            indication = null,
                            enabled = current < chapters.size - 1
                        ) { if (current < chapters.size - 1) current++ }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "下一章",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (current < chapters.size - 1) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

/** 生成中的小动画：思考中 后面点点递增 */
@Composable
private fun ThinkingDots(status: String) {
    val transition = rememberInfiniteTransition(label = "thinking")
    val p by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Restart),
        label = "dots"
    )
    val dots = when {
        p < 0.34f -> "."
        p < 0.67f -> ".."
        else -> "..."
    }
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text("思考中$dots", style = MaterialTheme.typography.bodySmall)
        if (status.isNotBlank()) {
            Text(
                "  $status",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 单个模型自己的参数：0 / 空 = 跟随服务商默认 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelParamsEditor(cfg: ModelConfig, onUpdate: (ModelConfig) -> Unit) {
    Column(Modifier.padding(start = 6.dp, end = 4.dp, bottom = 10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = {
                onUpdate(
                    cfg.copy(
                        contextWindow = 1_000_000,
                        maxTokens = 16384,
                        supportsVision = true,
                        autoCompactAt = 0.8f,
                        supportedEfforts = mutableListOf("off", "low", "high", "max"),
                        reasoningEffort = "max"
                    )
                )
            }) { Text("这个模型拉满") }
        }
        Text(
            "上下文窗口",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0, 32000, 64000, 128000, 200000, 1000000).forEach { n ->
                AppChip(
                    selected = cfg.contextWindow == n,
                    onClick = { onUpdate(cfg.copy(contextWindow = n)) },
                    label = {
                        Text(
                            if (n == 0) "默认" else if (n >= 1000000) "1M" else "${n / 1000}K"
                        )
                    }
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(
                "视觉模型",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = cfg.supportsVision,
                onCheckedChange = { onUpdate(cfg.copy(supportsVision = it)) }
            )
        }
        Text(
            "支持的思考强度",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 4.dp)
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Templates.effortLevels.forEach { (k, label) ->
                AppChip(
                    selected = k in cfg.supportedEfforts,
                    onClick = {
                        val next = if (k in cfg.supportedEfforts) {
                            cfg.supportedEfforts - k
                        } else {
                            cfg.supportedEfforts + k
                        }
                        onUpdate(cfg.copy(supportedEfforts = next.toMutableList()))
                    },
                    label = { Text(label) }
                )
            }
        }
        Text(
            "自动压缩 " + if (cfg.autoCompactAt <= 0f) "默认" else "${(cfg.autoCompactAt * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 4.dp)
        )
        Slider(
            value = if (cfg.autoCompactAt <= 0f) 0.5f else cfg.autoCompactAt,
            onValueChange = { onUpdate(cfg.copy(autoCompactAt = it)) },
            valueRange = 0.5f..0.95f
        )
        Text(
            "输出上限",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 6.dp)
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0, 8192, 16384, 32768, 65536, 131072).forEach { n ->
                AppChip(
                    selected = cfg.maxTokens == n,
                    onClick = { onUpdate(cfg.copy(maxTokens = n)) },
                    label = { Text(if (n == 0) "默认" else "${n / 1024}K") }
                )
            }
        }
        if (cfg.maxTokens != 0 && cfg.maxTokens !in listOf(4096, 8192, 16384, 32768, 65536, 131072)) {
            Text(
                "当前自定义：${cfg.maxTokens}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 统一风格的输入框：填充底 + 无边框，聚焦时才描边 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    /** 打码模式：只露头尾，右边有小眼睛切换 */
    secret: Boolean = false
) {
    if (!secret) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            placeholder = placeholder,
            singleLine = singleLine,
            enabled = enabled,
            modifier = modifier,
            minLines = minLines,
            maxLines = maxLines,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color.Transparent,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        return
    }

    var revealed by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = if (revealed) value else maskSecret(value),
        onValueChange = { typed ->
            if (revealed) {
                onValueChange(typed)
            } else {
                // 打码状态下只处理追加/删除，避免把掩码写回真实值
                val masked = maskSecret(value)
                when {
                    typed.length > masked.length -> onValueChange(value + typed.removePrefix(masked))
                    typed.length < masked.length ->
                        onValueChange(value.dropLast((masked.length - typed.length).coerceAtMost(value.length)))
                }
            }
        },
        label = label,
        placeholder = placeholder,
        singleLine = true,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        trailingIcon = {
            val isrc = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .pressScale(isrc, 0.9f)
                    .clickable(interactionSource = isrc, indication = null) { revealed = !revealed },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (revealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (revealed) "隐藏" else "显示",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = Color.Transparent,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

/** 只露头尾，中间用 • 盖住 */
internal fun maskSecret(s: String): String {
    if (s.isEmpty()) return ""
    if (s.length <= 8) return "•".repeat(s.length)
    val head = s.take(4)
    val tail = s.takeLast(4)
    return head + "•".repeat((s.length - 8).coerceAtMost(14)) + tail
}

/** 顶部菜单里的横向大方块 */
@Composable
private fun ActionTile(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val src = remember { MutableInteractionSource() }
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .pressScale(src, 0.92f)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(21.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(7.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

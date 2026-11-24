package com.novelforge.app.ui

import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.BoxWithConstraints
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

private data class TabSpec(val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabSpec("书架", Icons.Default.MenuBook),
    TabSpec("写作", Icons.Default.Edit),
    TabSpec("导入", Icons.Default.CloudDownload),
    TabSpec("我的", Icons.Default.Person)
)

/**
 * 四个主页面 + 底部导航。
 * 详情页（写作台、阅读、供应商编辑…）仍然是全屏压栈，不显示底栏。
 */
@Composable
fun MainTabsScreen(
    vm: AppViewModel,
    onOpenProject: (String) -> Unit,
    onOpenImportedBook: (String) -> Unit,
    onCreate: () -> Unit,
    onOpenProviders: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenReader: (kind: String, id: String, chapter: Int) -> Unit,
    onOpenScan: () -> Unit = {},
    onOpenUpdates: () -> Unit = {},
    onOpenStorage: () -> Unit = {}
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val stateHolder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()

    // 输入法弹出来的时候，底部导航跟着躲开，别被顶上来
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val bottomPad by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (imeVisible) 0.dp else 74.dp,
        animationSpec = androidx.compose.animation.core.tween(180),
        label = "bottomPad"
    )

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().padding(bottom = bottomPad)) {
            // tab 切换：淡入 + 轻微横向滑动，方向跟着左右走
            var prevTab by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(tab) }
            val forward = tab >= prevTab
            androidx.compose.runtime.LaunchedEffect(tab) { prevTab = tab }
            androidx.compose.animation.AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    // 页面内容沿点击方向轻推淡入：新页从点击方向那一侧滑进来一点，
                    // 旧页往反方向退一点点同时淡出，幅度都很小，只做「推一下」的感觉。
                    val dir = if (forward) 1 else -1
                    (androidx.compose.animation.fadeIn(
                        androidx.compose.animation.core.tween(320, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                    ) + androidx.compose.animation.slideInHorizontally(
                        androidx.compose.animation.core.tween(360, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                    ) { full -> dir * (full / 14) }) togetherWith
                        (androidx.compose.animation.fadeOut(
                            androidx.compose.animation.core.tween(200, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                        ) + androidx.compose.animation.slideOutHorizontally(
                            androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                        ) { full -> -dir * (full / 22) })
                },
                label = "tabSwitch",
                modifier = Modifier.fillMaxSize()
            ) { currentTab ->
            stateHolder.SaveableStateProvider(currentTab) {
            when (currentTab) {
                0 -> ShelfScreen(
                    vm = vm,
                    onOpen = { kind, id, chapter -> onOpenReader(kind, id, chapter) },
                    onBack = null,
                    onCreate = { tab = 1; onCreate() },
                    onImport = { tab = 2 }
                )
                1 -> HomeScreen(
                    vm = vm,
                    onCreate = onCreate,
                    onOpenProject = onOpenProject
                )
                2 -> ImportScreen(
                    vm = vm,
                    onOpenBook = { onOpenReader("imported", it, 0) },
                    onBack = null,
                    onOpenScan = onOpenScan
                )
                else -> MeScreen(
                    vm = vm,
                    onOpenProviders = onOpenProviders,
                    onOpenStats = onOpenStats,
                    onOpenSettings = onOpenSettings,
                    onOpenUpdates = onOpenUpdates,
                    onOpenStorage = onOpenStorage
                )
            }
            }
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = !imeVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(120)) +
                androidx.compose.animation.slideInVertically { it / 3 },
            exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)) +
                androidx.compose.animation.slideOutVertically { it / 3 }
        ) {
            BottomBar(
                selected = tab,
                onSelect = { tab = it }
            )
        }
    }
}

/** 悬浮胶囊式底栏：选中胶囊像水滴一样流过去
 *  —— 前沿先到、后沿慢半拍，中途被拉长并略变扁，最后带轻微回弹停稳。 */
@Composable
private fun BottomBar(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val appCtx = androidx.compose.ui.platform.LocalContext.current
    val inset = 12.dp
    val pillH = 28.dp
    val pillTop = 2.dp

    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 6.dp, vertical = 8.dp)
    ) {
        val tabW = maxWidth / tabs.size

        // 胶囊左右两条边各自一条动画：前进方向那条边先冲，另一条慢半拍跟上，
        // 于是中途宽度被拉开 —— 这就是「水滴被拉长」的来源。
        val leftPos = remember { androidx.compose.animation.core.Animatable(selected.toFloat()) }
        val rightPos = remember { androidx.compose.animation.core.Animatable(selected.toFloat()) }
        var prevTabIdx by remember { mutableIntStateOf(selected) }

        androidx.compose.runtime.LaunchedEffect(selected) {
            val from = prevTabIdx
            prevTabIdx = selected
            if (from == selected) return@LaunchedEffect
            val forward = selected > from
            val frontSpec = androidx.compose.animation.core.spring<Float>(
                dampingRatio = 0.70f,
                stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
            )
            val backSpec = androidx.compose.animation.core.spring<Float>(
                dampingRatio = 0.88f,
                stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
            )
            coroutineScope {
                if (forward) {
                    launch { rightPos.animateTo(selected.toFloat(), frontSpec) }
                    launch { leftPos.animateTo(selected.toFloat(), backSpec) }
                } else {
                    launch { leftPos.animateTo(selected.toFloat(), frontSpec) }
                    launch { rightPos.animateTo(selected.toFloat(), backSpec) }
                }
            }
        }

        val l = leftPos.value
        val r = rightPos.value
        val pillLeft = tabW * l + inset
        val pillW = ((tabW * (r + 1f) - inset) - pillLeft).coerceAtLeast(0.dp)
        val baseW = (tabW - inset * 2).value
        val stretch = if (baseW > 0.01f) (pillW.value / baseW) else 1f
        // 拉伸越多压得越扁，模拟液体体积守恒
        val squash = (1f - 0.22f * (stretch - 1f)).coerceIn(0.72f, 1f)
        val pillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)

        Box(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .offset(x = pillLeft, y = pillTop + (pillH * (1f - squash)) / 2)
                    .width(pillW)
                    .height(pillH * squash)
                    .clip(RoundedCornerShape(pillH * squash / 2))
                    .background(pillColor)
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                tabs.forEachIndexed { i, spec ->
                    val sel = i == selected
                    // 新选中的图标：先缩一下再弹回
                    val iconScale = remember { androidx.compose.animation.core.Animatable(1f) }
                    androidx.compose.runtime.LaunchedEffect(sel) {
                        if (sel) {
                            iconScale.snapTo(0.78f)
                            iconScale.animateTo(
                                1f,
                                androidx.compose.animation.core.spring(
                                    dampingRatio = 0.46f,
                                    stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                                )
                            )
                        }
                    }
                    val tint by androidx.compose.animation.animateColorAsState(
                        targetValue = if (sel) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(240),
                        label = "tabTint"
                    )
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                if (!sel) Haptics.select(appCtx)
                                onSelect(i)
                            }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(Modifier.height(20.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                spec.icon,
                                contentDescription = spec.label,
                                modifier = Modifier
                                    .size(20.dp)
                                    .graphicsLayer {
                                        scaleX = iconScale.value
                                        scaleY = iconScale.value
                                    },
                                tint = tint
                            )
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            spec.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                            color = tint
                        )
                    }
                }
            }
        }
    }
}

/** 「我的」：设置入口集合 */
@Composable
fun MeScreen(
    vm: AppViewModel,
    onOpenProviders: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenUpdates: () -> Unit = {},
    onOpenStorage: () -> Unit = {}
) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        Text(
            "我的",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(3.dp))
        Text(
            "${vm.data.providers.size} 套模型配置 · ${vm.data.projects.size} 部作品",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            MeRow("模型设置", "接供应商、选模型、调参数", Icons.Default.Tune, onOpenProviders)
            MeDivider()
            MeRow("用量统计", "花费、token、缓存命中、逐章用量", Icons.Default.BarChart, onOpenStats)
            MeDivider()
            MeRow("设置", "版本与更新、存储管理、触感反馈", Icons.Default.Settings, onOpenSettings)
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "小说工坊 · 自己用的写书工具",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun MeRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            "›",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun MeDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 63.dp, end = 16.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    )
}

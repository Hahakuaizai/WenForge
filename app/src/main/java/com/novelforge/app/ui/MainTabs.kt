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
                    val dir = if (forward) 1 else -1
                    (androidx.compose.animation.fadeIn(
                        androidx.compose.animation.core.tween(240)
                    ) + androidx.compose.animation.slideInHorizontally(
                        androidx.compose.animation.core.tween(300)
                    ) { w -> dir * w / 10 }) togetherWith
                        (androidx.compose.animation.fadeOut(
                            androidx.compose.animation.core.tween(160)
                        ) + androidx.compose.animation.slideOutHorizontally(
                            androidx.compose.animation.core.tween(240)
                        ) { w -> -dir * w / 16 })
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

/** 悬浮胶囊式底栏，参考轻小说文库那种贴底四个入口 */
@Composable
private fun BottomBar(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val appCtx = androidx.compose.ui.platform.LocalContext.current
        tabs.forEachIndexed { i, spec ->
            val sel = i == selected
            val scale by animateFloatAsState(
                targetValue = if (sel) 1f else 0.94f,
                animationSpec = tween(220),
                label = "tabScale"
            )
            Column(
                Modifier
                    .weight(1f)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(RoundedCornerShape(20.dp))
                    .clickable {
                        if (!sel) Haptics.select(appCtx)
                        onSelect(i)
                    }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else Color.Transparent
                        )
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    Icon(
                        spec.icon,
                        contentDescription = spec.label,
                        modifier = Modifier.size(20.dp),
                        tint = if (sel) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    spec.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (sel) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
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

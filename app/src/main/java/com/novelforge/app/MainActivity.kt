package com.novelforge.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.novelforge.app.ui.AppViewModel
import com.novelforge.app.ui.CreateScreen
import com.novelforge.app.ui.HomeScreen
import com.novelforge.app.ui.ImportScreen
import com.novelforge.app.ui.ImportedBookScreen
import com.novelforge.app.ui.MainTabsScreen
import com.novelforge.app.ui.MemoryScreen
import com.novelforge.app.ui.NovelForgeTheme
import com.novelforge.app.ui.ProviderEditScreen
import com.novelforge.app.ui.AppSettingsScreen
import com.novelforge.app.ui.ProvidersScreen
import com.novelforge.app.ui.ReaderScreen
import com.novelforge.app.ui.ScanImportScreen
import com.novelforge.app.ui.SettingsScreen
import com.novelforge.app.ui.ShelfScreen
import com.novelforge.app.ui.StatsScreen
import com.novelforge.app.ui.StorageScreen
import com.novelforge.app.ui.UpdatesScreen
import com.novelforge.app.ui.WorkbenchScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 显式开启 edge-to-edge：Android 15+ 起系统强制这个模式，
        // 不主动声明的话 WindowInsets.ime 在某些机型上拿不到键盘高度，
        // 底部导航就会被顶上来（而不是像预期那样自己藏起来）。
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        // 全面屏：系统栏透明，让页面背景一直铺到屏幕边缘，不留白边
        @Suppress("DEPRECATION")
        run {
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                window.isNavigationBarContrastEnforced = false
                window.isStatusBarContrastEnforced = false
            }
        }
        setContent {
                NovelForgeTheme {
                    Surface(color = MaterialTheme.colorScheme.background) {
                    val vm: AppViewModel = viewModel()
                    val nav = rememberNavController()

                    // 启动自动检查更新（静默：连不上就当无事发生，有新版才弹一次）
                    LaunchedEffect(Unit) {
                        vm.checkUpdates(silent = true)
                    }
                    var updatePromptOff by remember { mutableStateOf(false) }
                    val latestEntry = vm.latestUpdate
                    if (vm.updateAvailable && !updatePromptOff && latestEntry != null) {
                        AlertDialog(
                            onDismissRequest = {
                                if (!vm.updateDownloading) updatePromptOff = true
                            },
                            title = {
                                Text("有新版本 v${latestEntry.versionName}")
                            },
                            text = {
                                androidx.compose.foundation.layout.Column {
                                    Text(
                                        latestEntry.notes.firstOrNull()
                                            ?: "点「立即更新」直接下载安装。"
                                    )
                                    Text(
                                        if (latestEntry.directUrl.isNotBlank()) "来自 GitHub"
                                        else "来自你的电脑",
                                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                        color = androidx.compose.material3.MaterialTheme.colorScheme.outline
                                    )
                                    if (vm.updateDownloading) {
                                        androidx.compose.foundation.layout.Spacer(
                                            Modifier.height(12.dp)
                                        )
                                        LinearProgressIndicator(
                                            progress = { vm.updateProgress },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Text(
                                            "正在下载…",
                                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            },
                            confirmButton = {
                                if (!vm.updateDownloading) {
                                    TextButton(onClick = {
                                        vm.installUpdate(latestEntry)
                                    }) { Text("立即更新") }
                                }
                            },
                            dismissButton = {
                                if (!vm.updateDownloading) {
                                    TextButton(onClick = {
                                        updatePromptOff = true
                                    }) { Text("稍后") }
                                }
                            }
                        )
                    }

                    NavHost(
                        navController = nav,
                        startDestination = "main",
                        enterTransition = {
                            fadeIn(tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)) +
                                androidx.compose.animation.scaleIn(tween(340, easing = androidx.compose.animation.core.FastOutSlowInEasing), initialScale = 0.92f) +
                                androidx.compose.animation.slideInVertically(tween(340, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { it / 16 }
                        },
                        exitTransition = {
                            fadeOut(tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing)) +
                                androidx.compose.animation.scaleOut(tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing), targetScale = 1.03f)
                        },
                        popEnterTransition = {
                            fadeIn(tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)) +
                                androidx.compose.animation.scaleIn(tween(340, easing = androidx.compose.animation.core.FastOutSlowInEasing), initialScale = 1.03f) +
                                androidx.compose.animation.slideInVertically(tween(340, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { -it / 16 }
                        },
                        popExitTransition = {
                            fadeOut(tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing)) +
                                androidx.compose.animation.scaleOut(tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing), targetScale = 0.92f)
                        }
                    ) {
                        composable("main") {
                            MainTabsScreen(
                                vm = vm,
                                onOpenProject = { nav.navigate("workbench/$it") },
                                onOpenImportedBook = { nav.navigate("read/imported/$it/0") },
                                onCreate = { nav.navigate("create") },
                                onOpenProviders = { nav.navigate("providers") },
                                onOpenSettings = { nav.navigate("appsettings") },
                                onOpenStats = { nav.navigate("stats") },
                                onOpenImport = { nav.navigate("import") },
                                onOpenReader = { kind, id, ch -> nav.navigate("read/$kind/$id/$ch") },
                                onOpenScan = { nav.navigate("scan") },
                                onOpenUpdates = { nav.navigate("updates") },
                                onOpenStorage = { nav.navigate("storage") }
                            )
                        }
                        composable("create") {
                            CreateScreen(
                                vm = vm,
                                onCreated = { id ->
                                    nav.popBackStack()
                                    nav.navigate("workbench/$id")
                                },
                                onBack = { nav.popBackStack() }
                            )
                        }
                        composable(
                            "read/{kind}/{id}/{chapter}",
                            arguments = listOf(
                                navArgument("kind") { type = NavType.StringType },
                                navArgument("id") { type = NavType.StringType },
                                navArgument("chapter") { type = NavType.StringType }
                            )
                        ) { entry ->
                            ReaderScreen(
                                vm = vm,
                                kind = entry.arguments?.getString("kind") ?: "imported",
                                bookId = entry.arguments?.getString("id").orEmpty(),
                                startChapter = entry.arguments?.getString("chapter")?.toIntOrNull() ?: 0,
                                onBack = { nav.popBackStack() }
                            )
                        }
                        composable("scan") {
                            ScanImportScreen(
                                vm = vm,
                                onDone = { nav.popBackStack() },
                                onBack = { nav.popBackStack() }
                            )
                        }
                        composable("storage") {
                            StorageScreen(vm) { nav.popBackStack() }
                        }
                        composable("updates") {
                            UpdatesScreen(vm) { nav.popBackStack() }
                        }
                        composable("settings") {
                            SettingsScreen(
                                vm = vm,
                                onBack = { nav.popBackStack() }
                            )
                        }
                        composable("appsettings") {
                            AppSettingsScreen(
                                vm = vm,
                                onBack = { nav.popBackStack() },
                                onOpenUpdates = { nav.navigate("updates") },
                                onOpenStorage = { nav.navigate("storage") }
                            )
                        }
                        composable("stats") {
                            StatsScreen(vm = vm, onBack = { nav.popBackStack() })
                        }
                        composable("import") {
                            ImportScreen(
                                vm = vm,
                                onOpenBook = { id ->
                                    nav.popBackStack()
                                    nav.navigate("imported/$id")
                                },
                                onBack = { nav.popBackStack() }
                            )
                        }
                        composable(
                            "imported/{id}",
                            arguments = listOf(navArgument("id") { type = NavType.StringType })
                        ) { entry ->
                            val id = entry.arguments?.getString("id")!!
                            ImportedBookScreen(vm = vm, bookId = id, onBack = { nav.popBackStack() })
                        }
                        composable(
                            "memory/{id}",
                            arguments = listOf(navArgument("id") { type = NavType.StringType })
                        ) { entry ->
                            val id = entry.arguments?.getString("id")!!
                            MemoryScreen(vm = vm, projectId = id, onBack = { nav.popBackStack() })
                        }
                        composable("providers") {
                            ProvidersScreen(
                                vm = vm,
                                onEdit = { id -> nav.navigate("provider_edit/${id ?: "new"}") },
                                onBack = { nav.popBackStack() },
                                onOpenGenSettings = { nav.navigate("settings") }
                            )
                        }
                        composable(
                            "provider_edit/{id}",
                            arguments = listOf(navArgument("id") { type = NavType.StringType })
                        ) { entry ->
                            val id = entry.arguments?.getString("id")?.takeIf { it != "new" }
                            ProviderEditScreen(vm = vm, providerId = id, onBack = { nav.popBackStack() })
                        }
                        composable(
                            "workbench/{id}",
                            arguments = listOf(navArgument("id") { type = NavType.StringType })
                        ) { entry ->
                            val id = entry.arguments?.getString("id")!!
                            WorkbenchScreen(
                                vm = vm,
                                projectId = id,
                                onRead = { nav.navigate("reader/$id") },
                                onOpenMemory = { nav.navigate("memory/$id") },
                                onBack = { nav.popBackStack() }
                            )
                        }
                        composable(
                            "reader/{id}",
                            arguments = listOf(navArgument("id") { type = NavType.StringType })
                        ) { entry ->
                            val id = entry.arguments?.getString("id")!!
                            ReaderScreen(vm = vm, projectId = id, onBack = { nav.popBackStack() })
                        }
                    }
                }
            }
        }
    }
}

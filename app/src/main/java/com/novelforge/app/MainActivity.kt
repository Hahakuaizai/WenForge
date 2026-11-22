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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.novelforge.app.ui.AppViewModel
import com.novelforge.app.ui.CreateScreen
import com.novelforge.app.ui.HomeScreen
import com.novelforge.app.ui.ImportScreen
import com.novelforge.app.ui.ImportedBookScreen
import com.novelforge.app.ui.MainTabsScreen
import com.novelforge.app.ui.MemoryScreen
import com.novelforge.app.ui.NovelForgeTheme
import com.novelforge.app.ui.ProviderEditScreen
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
        setContent {
                NovelForgeTheme {
                    Surface(color = MaterialTheme.colorScheme.background) {
                    val vm: AppViewModel = viewModel()
                    val nav = rememberNavController()
                    NavHost(
                        navController = nav,
                        startDestination = "main",
                        enterTransition = {
                            fadeIn(tween(240)) + slideInHorizontally(tween(300)) { it / 6 }
                        },
                        exitTransition = {
                            fadeOut(tween(170)) + slideOutHorizontally(tween(280)) { -it / 14 }
                        },
                        popEnterTransition = {
                            fadeIn(tween(240)) + slideInHorizontally(tween(300)) { -it / 6 }
                        },
                        popExitTransition = {
                            fadeOut(tween(170)) + slideOutHorizontally(tween(280)) { it / 14 }
                        }
                    ) {
                        composable("main") {
                            MainTabsScreen(
                                vm = vm,
                                onOpenProject = { nav.navigate("workbench/$it") },
                                onOpenImportedBook = { nav.navigate("read/imported/$it/0") },
                                onCreate = { nav.navigate("create") },
                                onOpenProviders = { nav.navigate("providers") },
                                onOpenSettings = { nav.navigate("settings") },
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
                            SettingsScreen(vm = vm, onBack = { nav.popBackStack() })
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
                                onBack = { nav.popBackStack() }
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

package com.novelforge.app.data

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** 极简本地存储：全部数据存成一份 JSON（filesDir/novel-forge-data.json）。
 *  v1 先不用 Room，够用且零迁移负担；数据量大了再换。 */
class Store(context: Context) {

    private val file = File(context.filesDir, "novel-forge-data.json")
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    var data: AppData = load()
        private set

    private fun load(): AppData {
        return try {
            if (file.exists()) json.decodeFromString<AppData>(file.readText())
            else AppData()
        } catch (e: Exception) {
            // 文件损坏时不崩溃，起一份空的（旧文件留在原地可人工抢救）
            AppData()
        }
    }

    fun save() {
        data.projects.forEach { it.updatedAt = System.currentTimeMillis() }
        file.writeText(json.encodeToString(data))
    }

    // —— 供应商 ——
    fun upsertProvider(p: ProviderConfig) {
        val i = data.providers.indexOfFirst { it.id == p.id }
        if (i >= 0) data.providers[i] = p else data.providers.add(p)
        if (data.activeProviderId.isEmpty()) data.activeProviderId = p.id
        save()
    }

    fun deleteProvider(id: String) {
        data.providers.removeAll { it.id == id }
        if (data.activeProviderId == id) data.activeProviderId = data.providers.firstOrNull()?.id ?: ""
        save()
    }

    fun activeProvider(): ProviderConfig? =
        data.providers.firstOrNull { it.id == data.activeProviderId } ?: data.providers.firstOrNull()

    fun providerById(id: String): ProviderConfig? = data.providers.firstOrNull { it.id == id }

    // —— 项目 ——
    fun upsertProject(p: NovelProject) {
        val i = data.projects.indexOfFirst { it.id == p.id }
        if (i >= 0) data.projects[i] = p else data.projects.add(0, p)
        save()
    }

    fun deleteProject(id: String) {
        data.projects.removeAll { it.id == id }
        save()
    }

    fun projectById(id: String): NovelProject? = data.projects.firstOrNull { it.id == id }
}

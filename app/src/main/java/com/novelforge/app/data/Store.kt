package com.novelforge.app.data

import android.content.Context
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** 极简本地存储：全部数据存成一份 JSON（filesDir/novel-forge-data.json）。
 *  v1 先不用 Room，够用且零迁移负担；数据量大了再换。 */
class Store(context: Context) {

    companion object {
        @Volatile
        private var inst: Store? = null

        /** 整个进程共用一个实例：界面和下载服务写的是同一份内存数据 */
        fun shared(context: Context): Store {
            inst?.let { return it }
            return synchronized(this) {
                inst ?: Store(context.applicationContext).also { inst = it }
            }
        }
    }

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

    /** 写盘放到后台线程，界面点一下就卡的情况就不会有了 */
    private val saveScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    )
    private val saveJob = java.util.concurrent.atomic.AtomicReference<kotlinx.coroutines.Job?>(null)
    private val pending = java.util.concurrent.atomic.AtomicBoolean(false)

    /** 立刻落盘（导入、下载完这类不能丢的场合用） */
    @Synchronized
    fun saveNow() {
        pending.set(false)
        runCatching { file.writeText(json.encodeToString(data)) }
    }

    /** 合并写盘：短时间内多次改动只写一次，不阻塞 UI */
    fun save() {
        pending.set(true)
        if (saveJob.get()?.isActive == true) return
        val job = saveScope.launch {
            kotlinx.coroutines.delay(260)
            while (pending.getAndSet(false)) {
                runCatching { file.writeText(json.encodeToString(data)) }
                kotlinx.coroutines.delay(80)
            }
        }
        saveJob.set(job)
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

package com.wrongbook.app

import android.app.Application
import android.util.Log
import com.wrongbook.app.ai.AiService
import com.wrongbook.app.ai.DashScopeAiService
import com.wrongbook.app.ai.DashScopeClient
import com.wrongbook.app.ai.FakeAiService
import com.wrongbook.app.data.local.AppDatabase
import com.wrongbook.app.data.repository.QuestionRepository
import com.wrongbook.app.sync.QuestionSyncService

class WrongBookApp : Application() {

    val database by lazy { AppDatabase.getInstance(this) }
    val repository by lazy {
        QuestionRepository(
            dao = database.questionDao(),
            deviceId = BuildConfig.SYNC_DEVICE_ID.ifBlank { "android-main" }
        )
    }
    val questionSyncService by lazy {
        QuestionSyncService(
            context = applicationContext,
            apiUrl = BuildConfig.SYNC_API_URL,
            token = BuildConfig.SYNC_TOKEN,
            deviceId = BuildConfig.SYNC_DEVICE_ID
        )
    }

    val aiService: AiService by lazy { createAiService() }

    /** 当前是否使用真实 AI（供 UI 层展示状态） */
    val isRealAi: Boolean
        get() = BuildConfig.DASHSCOPE_API_KEY.isNotBlank()

    override fun onCreate() {
        super.onCreate()
        instance = this

        if (!isRealAi) {
            Log.w("WrongBookApp", "DASHSCOPE_API_KEY 未配置，将使用 FakeAiService 作为降级方案")
        }
    }

    private fun createAiService(): AiService {
        val apiKey = BuildConfig.DASHSCOPE_API_KEY
        val baseUrl = BuildConfig.DASHSCOPE_BASE_URL
        val model = BuildConfig.DASHSCOPE_MODEL

        // API Key 为空时降级到 FakeAiService
        if (apiKey.isBlank()) {
            return FakeAiService()
        }

        val client = DashScopeClient(
            apiKey = apiKey,
            baseUrl = baseUrl.ifBlank { "https://dashscope.aliyuncs.com/compatible-mode/v1" },
            model = model.ifBlank { "qwen3.7-plus" }
        )
        return DashScopeAiService(
            client = client,
            context = applicationContext
        )
    }

    companion object {
        lateinit var instance: WrongBookApp
            private set
    }
}

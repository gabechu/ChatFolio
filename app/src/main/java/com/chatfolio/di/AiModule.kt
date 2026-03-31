package com.chatfolio.di

import com.chatfolio.data.repository.GeminiEngine
import com.chatfolio.data.repository.SettingsRepository
import com.chatfolio.domain.port.LlmEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AiModule {
    @Provides
    @Singleton
    fun provideLlmEngine(settingsRepository: SettingsRepository): LlmEngine {
        // We use Gemini via Firebase AI Logic as the default engine.
        // The LlmEngine interface makes it easy to swap this out later if needed.
        return GeminiEngine(settingsRepository)
    }
}

package com.exynix.studio.di

import android.content.Context
import com.exynix.studio.data.repository.InferenceRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideInferenceRepository(
        @ApplicationContext context: Context
    ): InferenceRepository {
        return InferenceRepository(context)
    }
}

package com.bremenband.shadoweng.di

import android.content.Context
import com.bremenband.shadoweng.core.network.ApiService
import com.bremenband.shadoweng.core.network.RetrofitClient
import com.bremenband.shadoweng.feature.auth.data.TokenStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideApiService(tokenStorage: TokenStorage): ApiService =
        RetrofitClient.create(tokenStorage)
}
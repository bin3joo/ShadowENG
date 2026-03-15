package com.bremenband.shadoweng.di

import com.bremenband.shadoweng.feature.auth.repository.AuthRepository
import com.bremenband.shadoweng.feature.auth.repository.AuthRepositoryImpl
import com.bremenband.shadoweng.feature.content.repository.ContentRepository
import com.bremenband.shadoweng.feature.content.repository.ContentRepositoryImpl
import com.bremenband.shadoweng.feature.home.repository.HomeRepository
import com.bremenband.shadoweng.feature.home.repository.HomeRepositoryImpl
import com.bremenband.shadoweng.feature.mypage.repository.MyPageRepository
import com.bremenband.shadoweng.feature.mypage.repository.MyPageRepositoryImpl
import com.bremenband.shadoweng.feature.study.repository.StudyRepository
import com.bremenband.shadoweng.feature.study.repository.StudyRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds @Singleton
    abstract fun bindContentRepository(impl: ContentRepositoryImpl): ContentRepository

    @Binds @Singleton
    abstract fun bindHomeRepository(impl: HomeRepositoryImpl): HomeRepository

    @Binds @Singleton
    abstract fun bindMyPageRepository(impl: MyPageRepositoryImpl): MyPageRepository

    @Binds @Singleton
    abstract fun bindStudyRepository(impl: StudyRepositoryImpl): StudyRepository
}
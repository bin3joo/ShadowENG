package com.bremenband.shadoweng.di

import com.bremenband.shadoweng.BuildConfig
import com.bremenband.shadoweng.feature.auth.api.AuthApi
import com.bremenband.shadoweng.feature.auth.data.TokenStorage
import com.bremenband.shadoweng.feature.content.api.ContentApi
import com.bremenband.shadoweng.feature.mypage.api.MyPageApi
import com.bremenband.shadoweng.feature.study.api.StudyApi
import com.bremenband.shadoweng.feature.game.api.GameApi
import com.bremenband.shadoweng.feature.home.api.HomeApi
import com.bremenband.shadoweng.feature.profile.api.MyInfoApi

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(tokenStorage: TokenStorage): OkHttpClient {
        val authInterceptor = Interceptor { chain ->
            val token = tokenStorage.getToken()
            val request = if (token != null) {
                chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            } else chain.request()
            chain.proceed(request)
        }

        val tokenRefreshInterceptor = Interceptor { chain ->
            val response = chain.proceed(chain.request())
            if (response.code == 401 || response.code == 403) {
                response.close()
                val refreshToken = tokenStorage.getRefreshToken()
                if (refreshToken != null) {
                    // 동기 방식으로 refresh 호출
                    val refreshClient = OkHttpClient.Builder().build()
                    val refreshRequest = okhttp3.Request.Builder()
                        .url("${BuildConfig.BASE_URL}auth/refresh")
                        .post(
                            """{"refreshToken":"$refreshToken"}"""
                                .toRequestBody("application/json".toMediaType())
                        )
                        .build()
                    val refreshResponse = runCatching { refreshClient.newCall(refreshRequest).execute() }
                        .getOrNull()

                    if (refreshResponse?.isSuccessful == true) {
                        val body = refreshResponse.body?.string()
                        val newAccessToken = Regex("\"accessToken\"\\s*:\\s*\"([^\"]+)\"").find(body ?: "")?.groupValues?.get(1)
                        val newRefreshToken = Regex("\"refreshToken\"\\s*:\\s*\"([^\"]+)\"").find(body ?: "")?.groupValues?.get(1)
                        if (newAccessToken != null) {
                            tokenStorage.saveToken(newAccessToken)
                            if (newRefreshToken != null) tokenStorage.saveRefreshToken(newRefreshToken)
                            // 원래 요청 재시도
                            return@Interceptor chain.proceed(
                                chain.request().newBuilder()
                                    .header("Authorization", "Bearer $newAccessToken")
                                    .build()
                            )
                        }
                    }
                    // refresh 실패 시 토큰 초기화
                    tokenStorage.clear()
                }
            }
            response
        }

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(tokenRefreshInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi =
        retrofit.create(AuthApi::class.java)

    @Provides @Singleton
    fun provideContentApi(retrofit: Retrofit): ContentApi =
        retrofit.create(ContentApi::class.java)

    @Provides @Singleton
    fun provideStudyApi(retrofit: Retrofit): StudyApi =
        retrofit.create(StudyApi::class.java)

    @Provides @Singleton
    fun provideMyPageApi(retrofit: Retrofit): MyPageApi =
        retrofit.create(MyPageApi::class.java)

    @Provides @Singleton
    fun provideGameApi(retrofit: Retrofit): GameApi =
        retrofit.create(GameApi::class.java)

    @Provides @Singleton
    fun provideHomeApi(retrofit: Retrofit): HomeApi =
        retrofit.create(HomeApi::class.java)

    @Provides @Singleton
    fun provideMyInfoApi(retrofit: Retrofit): MyInfoApi =
        retrofit.create(MyInfoApi::class.java)

}
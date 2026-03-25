package com.bremenband.shadoweng.feature.game.repository

import com.bremenband.shadoweng.core.exception.DomainException
import com.bremenband.shadoweng.core.exception.mapDomainException
import com.bremenband.shadoweng.feature.game.api.GameApi
import com.bremenband.shadoweng.feature.game.domain.model.*
import com.bremenband.shadoweng.feature.game.mapper.toDomain
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException
import java.io.File
import javax.inject.Inject

class GameRepositoryImpl @Inject constructor(
    private val api: GameApi
) : GameRepository {

    override suspend fun getToday(): Result<List<LevelStatus>> =
        runCatching {
            api.getToday().data?.levels?.map { it.toDomain() }
                ?: throw DomainException.NotFound
        }.mapDomainException()

    override suspend fun getRound(level: Int, round: Int): Result<GameRound> =
        runCatching {
            api.getRound(level, round).data?.toDomain()
                ?: throw DomainException.NotFound
        }.mapDomainException()

    override suspend fun evaluate(level: Int, round: Int, audioFile: File): Result<GameEvalResult> =
        runCatching {
            val filePart = MultipartBody.Part.createFormData(
                "file", audioFile.name,
                audioFile.asRequestBody("audio/m4a".toMediaTypeOrNull())
            )
            try {
                val response = api.evaluate(level, round, filePart)
                response.data?.toDomain()
                    ?: throw DomainException.Unknown(response.message ?: "평가 중 오류가 발생했어요.")
            } catch (e: HttpException) {
                // 400 등 에러 응답에서 body 파싱
                val errorBody = e.response()?.errorBody()?.string()
                val message = try {
                    org.json.JSONObject(errorBody ?: "").getString("message")
                } catch (ex: Exception) {
                    e.message() ?: "평가 중 오류가 발생했어요."
                }
                throw DomainException.Unknown(message)
            }
        }.mapDomainException()

    override suspend fun getLeaderboard(): Result<GameLeaderboard> =
        runCatching {
            api.getLeaderboard().data?.toDomain()
                ?: throw DomainException.NotFound
        }.mapDomainException()

    override suspend fun getProfile(): Result<GameProfile> =
        runCatching {
            api.getProfile().data?.toDomain()
                ?: throw DomainException.NotFound
        }.mapDomainException()
}
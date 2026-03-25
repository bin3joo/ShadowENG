package com.bremenband.shadoweng.feature.game.repository

import com.bremenband.shadoweng.feature.game.domain.model.*
import java.io.File

interface GameRepository {
    suspend fun getToday(): Result<List<LevelStatus>>
    suspend fun getRound(level: Int, round: Int): Result<GameRound>
    suspend fun evaluate(level: Int, round: Int, audioFile: File): Result<GameEvalResult>
    suspend fun getLeaderboard(): Result<GameLeaderboard>
    suspend fun getProfile(): Result<GameProfile>
}
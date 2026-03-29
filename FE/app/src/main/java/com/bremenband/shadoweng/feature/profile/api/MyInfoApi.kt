package com.bremenband.shadoweng.feature.profile.api

import com.bremenband.shadoweng.core.network.dto.ApiResponse
import retrofit2.http.Body
import retrofit2.http.PATCH

data class UpdateNicknameRequest(val nickname: String)

interface MyInfoApi {
    @PATCH("users/nickname")
    suspend fun updateNickname(@Body body: UpdateNicknameRequest): ApiResponse<String?>
}
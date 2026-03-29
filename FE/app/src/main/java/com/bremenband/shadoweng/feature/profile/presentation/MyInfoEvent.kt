package com.bremenband.shadoweng.feature.profile.presentation

sealed class MyInfoEvent {
    object ClickLogout : MyInfoEvent()
    object ClickEditNickname : MyInfoEvent()
    object DismissNicknameDialog : MyInfoEvent()
    data class NicknameInputChanged(val value: String) : MyInfoEvent()
    object ConfirmNicknameChange : MyInfoEvent()
}
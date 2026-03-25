package com.bremenband.shadoweng.ui.theme

import com.bremenband.shadoweng.R
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font

val PretendardFontFamily = FontFamily(
    Font(R.font.pretendard_thin, FontWeight.Thin),
    Font(R.font.pretendard_extralight, FontWeight.ExtraLight),
    Font(R.font.pretendard_light, FontWeight.Light),
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
    Font(R.font.pretendard_extrabold, FontWeight.ExtraBold),
    Font(R.font.pretendard_black, FontWeight.Black),
)

val Typography = Typography(
    displayLarge = TextStyle(fontFamily = PretendardFontFamily),
    displayMedium = TextStyle(fontFamily = PretendardFontFamily),
    displaySmall = TextStyle(fontFamily = PretendardFontFamily),
    headlineLarge = TextStyle(fontFamily = PretendardFontFamily),
    headlineMedium = TextStyle(fontFamily = PretendardFontFamily),
    headlineSmall = TextStyle(fontFamily = PretendardFontFamily),
    titleLarge = TextStyle(fontFamily = PretendardFontFamily),
    titleMedium = TextStyle(fontFamily = PretendardFontFamily),
    titleSmall = TextStyle(fontFamily = PretendardFontFamily),
    bodyLarge = TextStyle(fontFamily = PretendardFontFamily),
    bodyMedium = TextStyle(fontFamily = PretendardFontFamily),
    bodySmall = TextStyle(fontFamily = PretendardFontFamily),
    labelLarge = TextStyle(fontFamily = PretendardFontFamily),
    labelMedium = TextStyle(fontFamily = PretendardFontFamily),
    labelSmall = TextStyle(fontFamily = PretendardFontFamily),
)
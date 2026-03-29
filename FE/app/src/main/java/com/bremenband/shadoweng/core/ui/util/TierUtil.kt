package com.bremenband.shadoweng.core.ui.util

import com.bremenband.shadoweng.R

fun tierToDrawable(tier: String?): Int = when (tier) {
    "BRONZE"     -> R.drawable.engmu_bronze
    "SILVER"     -> R.drawable.engmu_silver
    "GOLD"       -> R.drawable.engmu_gold
    "PLATINUM"   -> R.drawable.engmu_platinum
    "DIAMOND"    -> R.drawable.engmu_diamond
    "RUBY"       -> R.drawable.engmu_ruby
    "CHALLENGER" -> R.drawable.engmu_challenger
    else         -> R.drawable.engmu_question
}

fun tierDisplayName(tier: String) = when (tier) {
    "BRONZE"     -> "브론즈"
    "SILVER"     -> "실버"
    "GOLD"       -> "골드"
    "PLATINUM"   -> "플래티넘"
    "DIAMOND"    -> "다이아몬드"
    "RUBY"       -> "루비"
    "CHALLENGER" -> "챌린저"
    else         -> "브론즈"
}
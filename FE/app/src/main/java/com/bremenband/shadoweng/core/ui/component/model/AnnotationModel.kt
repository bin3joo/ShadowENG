package com.bremenband.shadoweng.core.ui.component.model

enum class AnnotationType {
    HIGHLIGHT,
    BOLD,
    UNDERLINE,
    ARROW_UP,
    ARROW_DOWN,
    CURVE,
}

data class Annotation(
    val startIndex: Int,
    val endIndex: Int,
    val type: AnnotationType,
    val color: Long = 0xFFFFEB3B,
)

data class ExpressionInfo(
    val word: String,
    val pronunciation: String,
    val description: String,
    val examples: List<String> = emptyList()
)
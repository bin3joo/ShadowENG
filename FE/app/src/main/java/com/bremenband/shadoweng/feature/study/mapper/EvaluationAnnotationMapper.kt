package com.bremenband.shadoweng.feature.study.mapper

import com.bremenband.shadoweng.core.ui.component.model.Annotation
import com.bremenband.shadoweng.core.ui.component.model.AnnotationType
import com.bremenband.shadoweng.feature.study.domain.EvaluationResult
import kotlin.collections.forEach
import kotlin.collections.lastOrNull
import kotlin.text.indexOf
import kotlin.text.isNotEmpty
import kotlin.text.lastIndexOf
import kotlin.text.split
import kotlin.text.trim
import kotlin.text.trimEnd

/**
 * EvaluationResult의 wordLevelFeedback을 Annotation 리스트로 변환
 * dragged → 노란 물결(CURVE)
 * rushed  → 파란 물결(CURVE)
 * missed  → 빨간 하이라이트(HIGHLIGHT)
 * good    → 표시 없음
 */
fun EvaluationResult.toAnnotations(sentence: String): List<Annotation> {
    val annotations = mutableListOf<Annotation>()

    wordFeedback.forEach { feedback ->
        val word = feedback.word.trim('.', ',', '!', '?', '"')
        val idx = sentence.indexOf(word)
        if (idx == -1) return@forEach
        val end = idx + word.length
        when (feedback.status) {
            "dragged" -> annotations.add(
                Annotation(idx, end, AnnotationType.CURVE_LONG, 0xFFFFB800)
            )
            "rushed" -> annotations.add(
                Annotation(idx, end, AnnotationType.CURVE_SHORT, 0xFF1565C0)
            )
            "missed" -> annotations.add(
                Annotation(idx, end, AnnotationType.HIGHLIGHT, 0x33FF5D5D)
            )
        }
    }

    // boundaryTone → 문장 마지막 단어에 억양 화살표
    val lastWord = sentence.trimEnd().split(" ").lastOrNull()
        ?.trim('.', ',', '!', '?') ?: ""
    val lastIdx = sentence.lastIndexOf(lastWord)
    if (lastIdx != -1 && lastWord.isNotEmpty()) {
        val lastEnd = lastIdx + lastWord.length
        when (boundaryToneFeedback.status) {
            "short", "weak" -> annotations.add(
                Annotation(lastIdx, lastEnd, AnnotationType.ARROW_UP, 0xFFFF5D5D)
            )
            "opposite" -> annotations.add(
                Annotation(lastIdx, lastEnd, AnnotationType.ARROW_DOWN, 0xFFFF5D5D)
            )
        }
    }

    return annotations
}
package com.bremenband.shadoweng.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bremenband.shadoweng.core.ui.component.model.Annotation
import com.bremenband.shadoweng.core.ui.component.model.AnnotationType

@Composable
fun AnnotatedSentenceView(
    sentence: String,
    annotations: List<Annotation>,
    onWordTap: (word: String, charIndex: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val annotatedString = buildAnnotatedString {
        append(sentence)
        annotations.forEach { ann ->
            val end = minOf(ann.endIndex, sentence.length)
            if (ann.startIndex >= end) return@forEach
            when (ann.type) {
                AnnotationType.HIGHLIGHT -> addStyle(
                    SpanStyle(background = Color(ann.color)),
                    ann.startIndex, end
                )
                AnnotationType.BOLD -> addStyle(
                    SpanStyle(fontWeight = FontWeight.Bold),
                    ann.startIndex, end
                )
                AnnotationType.UNDERLINE -> addStyle(
                    SpanStyle(textDecoration = TextDecoration.Underline),
                    ann.startIndex, end
                )
                else -> Unit // ARROW_UP, ARROW_DOWN, CURVE → Canvas에서 처리
            }
        }
    }

    // Canvas 드로잉용 어노테이션만 필터
    val canvasAnnotations = annotations.filter {
        it.type in listOf(
            AnnotationType.ARROW_UP, AnnotationType.ARROW_DOWN,
            AnnotationType.CURVE_LONG, AnnotationType.CURVE_SHORT
        )
    }

    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    Box(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            text = annotatedString,
            fontSize = 18.sp,
            lineHeight = 28.sp,
            onTextLayout = { textLayoutResult = it },
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(sentence) {
                    detectTapGestures { offset ->
                        textLayoutResult?.let { layout ->
                            val charIndex = layout.getOffsetForPosition(offset)
                            val word = extractWord(sentence, charIndex)
                            onWordTap(word, charIndex)
                        }
                    }
                }
        )

        // 화살표 / 억양 곡선 오버레이
        if (canvasAnnotations.isNotEmpty()) {
            Canvas(modifier = Modifier.matchParentSize()) {
                textLayoutResult ?: return@Canvas
                canvasAnnotations.forEach { ann ->
                    val end = minOf(ann.endIndex, sentence.length)
                    if (ann.startIndex >= end) return@forEach
                    val startRect = textLayoutResult!!.getBoundingBox(ann.startIndex)
                    val endRect = textLayoutResult!!.getBoundingBox(end - 1)

                    when (ann.type) {
                        AnnotationType.ARROW_UP -> {
                            val mid = startRect.top + (startRect.bottom - startRect.top) * 0.5f
                            val startX = (startRect.left + endRect.right) / 2f
                            val path = Path().apply {
                                moveTo(startX, mid + 6.dp.toPx())
                                cubicTo(
                                    startX, mid + 6.dp.toPx(),
                                    startX - 4.dp.toPx(), mid - 8.dp.toPx(),
                                    startX + 4.dp.toPx(), mid - 10.dp.toPx()
                                )
                                moveTo(startX + 4.dp.toPx(), mid - 10.dp.toPx())
                                lineTo(startX + 8.dp.toPx(), mid - 6.dp.toPx())
                                moveTo(startX + 4.dp.toPx(), mid - 10.dp.toPx())
                                lineTo(startX, mid - 5.dp.toPx())
                            }
                            drawPath(path, Color(ann.color), style = Stroke(width = 2.5.dp.toPx()))
                        }
                        AnnotationType.ARROW_DOWN -> {
                            val mid = startRect.top + (startRect.bottom - startRect.top) * 0.5f
                            val centerX = (startRect.left + endRect.right) / 2f
                            val path = Path().apply {
                                moveTo(centerX - 6.dp.toPx(), mid - 6.dp.toPx())
                                lineTo(centerX, mid + 6.dp.toPx())
                                lineTo(centerX + 6.dp.toPx(), mid - 6.dp.toPx())
                            }
                            drawPath(path, Color(ann.color), style = Stroke(
                                width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round
                            ))
                        }
                        AnnotationType.CURVE_LONG -> {
                            val mid = startRect.top + (startRect.bottom - startRect.top) * 0.5f
                            val path = Path().apply {
                                moveTo(startRect.left, mid)
                                cubicTo(
                                    startRect.left + 6.dp.toPx(), mid - 7.dp.toPx(),
                                    startRect.left + 12.dp.toPx(), mid + 7.dp.toPx(),
                                    startRect.left + 18.dp.toPx(), mid
                                )
                            }
                            drawPath(path, Color(ann.color), style = Stroke(
                                width = 2.5.dp.toPx(), cap = StrokeCap.Round
                            ))
                        }
                        AnnotationType.CURVE_SHORT -> {
                            val mid = startRect.top + (startRect.bottom - startRect.top) * 0.5f
                            val centerX = (startRect.left + endRect.right) / 2f
                            val path = Path().apply {
                                moveTo(centerX - 6.dp.toPx(), mid - 4.dp.toPx())
                                lineTo(centerX, mid + 4.dp.toPx())
                                lineTo(centerX + 6.dp.toPx(), mid - 4.dp.toPx())
                            }
                            drawPath(path, Color(ann.color), style = Stroke(
                                width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round
                            ))
                        }
                        else -> Unit
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawArrow(
    start: Offset,
    end: Offset,
    color: Color
) {
    drawLine(color, start, end, strokeWidth = 2.dp.toPx())
    // 화살촉
    val angle = Math.atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())
    val arrowLen = 8.dp.toPx()
    drawLine(
        color,
        end,
        Offset(
            (end.x - arrowLen * Math.cos(angle - 0.4)).toFloat(),
            (end.y - arrowLen * Math.sin(angle - 0.4)).toFloat()
        ),
        strokeWidth = 2.dp.toPx()
    )
    drawLine(
        color,
        end,
        Offset(
            (end.x - arrowLen * Math.cos(angle + 0.4)).toFloat(),
            (end.y - arrowLen * Math.sin(angle + 0.4)).toFloat()
        ),
        strokeWidth = 2.dp.toPx()
    )
}

private fun extractWord(sentence: String, charIndex: Int): String {
    if (charIndex < 0 || charIndex >= sentence.length) return ""
    val start = sentence.lastIndexOf(' ', charIndex - 1) + 1
    val end = sentence.indexOf(' ', charIndex).let { if (it == -1) sentence.length else it }
    return sentence.substring(start, end).trim('.', ',', '!', '?')
}
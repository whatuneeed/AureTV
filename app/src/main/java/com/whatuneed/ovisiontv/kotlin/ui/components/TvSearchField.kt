package com.whatuneed.ovisiontv.kotlin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.whatuneed.ovisiontv.kotlin.ui.TvInk
import com.whatuneed.ovisiontv.kotlin.ui.TvRadius
import com.whatuneed.ovisiontv.kotlin.ui.TvScale
import com.whatuneed.ovisiontv.kotlin.ui.TvType

const val SEARCH_FIELD_WIDTH = 980f
const val SEARCH_FIELD_HEIGHT = 90f
private const val FIELD_TEXT_INSET = 28f
private const val FIELD_EDGE = 0.14f

/**
 * The one input plate for search and the setup city step: same size, radius,
 * glass fill and caret. The query is local state, so the caller only passes
 * what to show and where to put the plate.
 */
@Composable
fun TvSearchField(
    query: String,
    placeholder: String,
    scale: TvScale,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(scale.dp(SEARCH_FIELD_WIDTH), scale.dp(SEARCH_FIELD_HEIGHT))
            .background(TvInk.glassFill, RoundedCornerShape(scale.dp(TvRadius.field)))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = FIELD_EDGE),
                shape = RoundedCornerShape(scale.dp(TvRadius.field)),
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = if (query.isEmpty()) placeholder else "$query|",
            color = if (query.isEmpty()) {
                Color.White.copy(alpha = 0.30f)
            } else {
                Color.White
            },
            fontSize = scale.sp(TvType.headline),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .offset(x = scale.dp(FIELD_TEXT_INSET))
                // The bar is a caret, not text: TalkBack must not read it.
                .clearAndSetSemantics {
                    text = AnnotatedString(if (query.isEmpty()) placeholder else query)
                },
        )
    }
}

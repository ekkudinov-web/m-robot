package com.minfinrobot.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Набор «luxury»-компонентов под изумрудно-золотой дизайн.
 *
 * Все они опциональны: существующие экраны работают и без них (через colorScheme),
 * а эти дают «вид как на рендере» — градиенты, золотые рамки, фактуру.
 */

/** Радиальный + вертикальный градиент фона с лёгким «свечением» сверху. */
@Composable
fun AppBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        AppColors.BackgroundTop,
                        AppColors.BackgroundMid,
                        AppColors.BackgroundDark
                    )
                )
            )
            // Мягкое золотое свечение в верхней части — имитация «премиум»-подсветки.
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            AppColors.Gold.copy(alpha = 0.10f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.08f),
                        radius = size.width * 0.9f
                    )
                )
            }
    ) { content() }
}

/**
 * Карточка с золотой рамкой и лёгким вертикальным градиентом заливки.
 * Заменяет обычный Card там, где нужен «премиум»-вид.
 */
@Composable
fun GoldCard(
    modifier: Modifier = Modifier,
    cornerRadius: Int = 18,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, AppColors.GoldDim)
    ) {
        Box(
            modifier = Modifier.background(
                Brush.verticalGradient(
                    colors = listOf(AppColors.SurfaceElevated, AppColors.Surface)
                )
            )
        ) { content() }
    }
}

/**
 * Золотая градиентная кнопка с бликом сверху (как в рендере).
 * Использует обычный Button, но с прозрачным контейнером и градиентным фоном.
 */
@Composable
fun GoldButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent
        )
    ) {
        Box(
            modifier = Modifier
                .background(
                    if (enabled) Brush.verticalGradient(
                        colors = listOf(AppColors.GoldBright, AppColors.Gold, AppColors.GoldDeep)
                    ) else Brush.verticalGradient(
                        colors = listOf(AppColors.GoldDim, AppColors.GoldDeep)
                    ),
                    shape = RoundedCornerShape(14.dp)
                )
                .fillMaxSize()
                .padding(vertical = 14.dp),
        ) {
            Text(
                text = text,
                color = AppColors.TextOnGold,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}

/** Тонкая золотая разделительная линия (для секций). */
@Composable
fun GoldDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .drawBehind {
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            AppColors.Gold.copy(alpha = 0.5f),
                            Color.Transparent
                        )
                    ),
                    start = Offset(0f, size.height / 2),
                    end = Offset(size.width, size.height / 2),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
    )
}

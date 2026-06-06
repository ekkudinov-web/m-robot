package com.minfinrobot.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Изумрудно-золотая палитра «luxury» по референс-дизайну.
 *
 * Доминанта — глубокий тёмно-зелёный фон, акцент — золото/латунь.
 * Семантические цвета для торговли (BUY/SELL/FILL/ERROR) подобраны так,
 * чтобы читаться поверх изумрудной темы, не сливаясь с золотом.
 */
object AppColors {

    // --- Фон (градиент строится от тёмного к чуть менее тёмному) ---
    val BackgroundDark = Color(0xFF0A1F18)      // почти чёрно-зелёный (низ градиента)
    val BackgroundMid = Color(0xFF103025)       // изумрудный (центр)
    val BackgroundTop = Color(0xFF163D2F)       // светлее изумруд (верх)

    // --- Поверхности (карточки) ---
    val Surface = Color(0xFF14352A)             // базовая карточка
    val SurfaceElevated = Color(0xFF1B4636)     // приподнятая карточка
    val SurfaceVariant = Color(0xFF0E2A20)      // утопленная (поля ввода, лог)

    // --- Золото (акцент) ---
    val Gold = Color(0xFFC9A24B)                // основное золото
    val GoldBright = Color(0xFFE6C76E)          // светлый блик золота (верх градиента кнопки)
    val GoldDeep = Color(0xFF9C7A2E)            // тёмное золото (низ градиента, тени)
    val GoldDim = Color(0xFF6E5A2E)             // приглушённое золото (рамки в покое)

    // --- Текст ---
    val TextPrimary = Color(0xFFF3ECD9)         // кремово-белый
    val TextSecondary = Color(0xFFB8C4B0)       // приглушённый зеленовато-серый
    val TextOnGold = Color(0xFF1A2C1F)          // тёмный текст на золотой кнопке

    // --- Семантика торговли ---
    val Buy = Color(0xFF4BB37A)                 // покупка — зелёный (отличный от фона тон)
    val Sell = Color(0xFFD98A4B)                // продажа — терракот/оранжевый
    val Fill = Color(0xFF6FCF97)                // исполнено — яркий зелёный
    val Warn = Color(0xFFE0B341)                // предупреждение — янтарь
    val Error = Color(0xFFD96B6B)               // ошибка — приглушённо-красный (не ядовитый)

    // --- Прочее ---
    val Scrim = Color(0x99000000)
    val DividerGold = Color(0x33C9A24B)         // полупрозрачная золотая линия
}

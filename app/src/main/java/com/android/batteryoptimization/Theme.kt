package com.android.batteryoptimization

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 1. 专属色彩体系 (Government & Enterprise Blue Palette - 高对比度清晰版)
private val GovernmentBlueColorScheme = lightColorScheme(
    primary = Color(0xFF1A5276),          // 深空商务蓝
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1A5276), // 顶栏与突出卡片采用深空商务蓝背景
    onPrimaryContainer = Color.White,     // 容器内文字纯白，高对比度
    secondary = Color(0xFF2980B9),        // 强调亮蓝
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2980B9), // 突出卡片采用强调亮蓝背景
    onSecondaryContainer = Color.White,     // 容器内文字纯白，极高对比度，解决看不清问题
    background = Color(0xFFF5F7FA),       // 干净的行政纸张浅灰背景
    onBackground = Color(0xFF1A1A1A),     // 更深、更清晰的墨色文字
    surface = Color.White,                // 纯白卡片容器
    onSurface = Color(0xFF1A1A1A),        // 卡片内主文字采用高对比度深色
    surfaceVariant = Color.White,         // 次要卡片容器同样保持白底
    onSurfaceVariant = Color(0xFF333333), // 次要文字加深，解决灰色看不清的问题
    error = Color(0xFFC62828),            // 警示红
    onError = Color.White,
    errorContainer = Color(0xFFFFEBEE),
    onErrorContainer = Color(0xFFC62828)
)

// 2. 形状与圆角规范 (Government Shapes - 方正干练)
private val GovernmentShapes = Shapes(
    small = RoundedCornerShape(6.dp),     // 按钮、输入框、小弹窗采用 6dp 小圆角，利落严谨
    medium = RoundedCornerShape(8.dp),    // 卡片采用 8dp 规整圆角
    large = RoundedCornerShape(8.dp)
)

// 3. 排版规范 (Government Typography - 大字号清晰版)
private val GovernmentTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 30.sp,
        color = Color(0xFF1A1A1A)
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        color = Color(0xFF1A1A1A)
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        color = Color(0xFF1A1A1A)
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        color = Color(0xFF333333)
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    )
)

@Composable
fun BatteryOptimizationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = GovernmentBlueColorScheme,
        shapes = GovernmentShapes,
        typography = GovernmentTypography,
        content = content
    )
}

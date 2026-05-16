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

// 1. 专属色彩体系 (Government & Enterprise Blue Palette)
private val GovernmentBlueColorScheme = lightColorScheme(
    primary = Color(0xFF1A5276),          // 深空商务蓝
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1A5276), // 顶栏与突出卡片背景色统一采用深空商务蓝
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF2980B9),        // 强调亮蓝
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEBF5FB),
    onSecondaryContainer = Color(0xFF1A5276),
    background = Color(0xFFF5F7FA),       // 干净的行政纸张浅灰背景
    onBackground = Color(0xFF2C3E50),     // 深沉墨色文字
    surface = Color.White,                // 纯白卡片容器
    onSurface = Color(0xFF2C3E50),
    surfaceVariant = Color.White,         // 次要卡片容器同样保持白底，靠阴影/边框区分
    onSurfaceVariant = Color(0xFF546E7A), // 次要高级灰文字
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

// 3. 排版规范 (Government Typography - 严谨有序)
private val GovernmentTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        color = Color(0xFF2C3E50)
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        color = Color(0xFF2C3E50)
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        color = Color(0xFF2C3E50)
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = Color(0xFF546E7A)
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
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

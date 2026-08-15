package com.android.batteryoptimization

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstructionScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val brand = remember { BrandUtil.currentBrand() }

    // Live status checks - refresh on resume
    var isAccessibilityEnabled by remember { mutableStateOf(checkAccessibilityPermission(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityEnabled = checkAccessibilityPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("使用说明", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "当前机型：${brandName(brand)}\n为确保应用能在后台稳定运行、辅助权限不被回收，请逐项完成以下设置：每项均可点击按钮直接跳转对应系统页面。",
                style = MaterialTheme.typography.bodyLarge
            )

            // ① 辅助功能（通用）
            InstructionItem(
                title = "1. 开启辅助功能权限（必选）",
                description = "应用依赖此权限工作。若被系统回收，重新打开 App 会自动尝试恢复，仍不行时请重新开启。",
                buttonText = "去开启辅助功能",
                isEnabled = isAccessibilityEnabled,
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            )

            // ② 自启动（跳 APP 设置页面）
            InstructionItem(
                title = "2. 允许自启动",
                description = "到 APP 设置页面，找到自启动/启动管理相关选项，允许本应用自启动及关联启动。",
                buttonText = "去 APP 设置",
                isEnabled = null,
                onClick = { openAppSettings(context) }
            )

            // ③ 忽略电池优化（跳 APP 设置页面）
            InstructionItem(
                title = "3. 忽略电池优化",
                description = "到 APP 设置页面，找到电池/耗电相关选项，设为「无限制」或「不优化」。",
                buttonText = "去 APP 设置",
                isEnabled = null,
                onClick = { openAppSettings(context) }
            )

            // ④ 位置权限（跳 APP 设置页面）
            InstructionItem(
                title = "4. 位置权限",
                description = "到 APP 设置页面，找到「位置信息」权限，设为「始终允许」，确保后台也能定位。",
                buttonText = "去 APP 设置",
                isEnabled = null,
                onClick = { openAppSettings(context) }
            )
        }
    }
}

// ─── 各机型文案 ───────────────────────────────────────────────
private fun brandName(brand: BrandUtil.Brand): String = when (brand) {
    BrandUtil.Brand.VIVO -> "vivo / iQOO"
    BrandUtil.Brand.XIAOMI -> "小米 / Redmi / POCO"
    BrandUtil.Brand.HUAWEI -> "华为 / 荣耀"
    BrandUtil.Brand.OPPO -> "OPPO / Realme / OnePlus"
    BrandUtil.Brand.SAMSUNG -> "三星"
    BrandUtil.Brand.OTHER -> "其他"
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = android.net.Uri.fromParts("package", context.packageName, null)
    }
    context.startActivity(intent)
}

@Composable
fun InstructionItem(
    title: String,
    description: String,
    buttonText: String,
    isEnabled: Boolean? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (isEnabled == true) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "已开启",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "已开启",
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else if (isEnabled == null) {
                    Text(
                        text = "注：本功能无开启提示",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Text(text = description, style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = onClick,
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(buttonText, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

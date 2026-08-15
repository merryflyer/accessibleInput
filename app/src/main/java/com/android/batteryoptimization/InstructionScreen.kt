package com.android.batteryoptimization

import android.content.Context
import android.content.Intent
import android.os.PowerManager
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
    var isBatteryOptimizationIgnored by remember {
        val pm = context.getSystemService(PowerManager::class.java)
        mutableStateOf(pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false)
    }
    var toast by remember { mutableStateOf<String?>(null) }
    toast?.let {
        SideEffect {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            toast = null
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityEnabled = checkAccessibilityPermission(context)
                val pm = context.getSystemService(PowerManager::class.java)
                isBatteryOptimizationIgnored = pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
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

            // ② 自启动（按机型）
            InstructionItem(
                title = "2. 允许自启动",
                description = autoStartDesc(brand),
                buttonText = autoStartBtnText(brand),
                isEnabled = null,
                onClick = {
                    tryStart(context, BrandUtil.autoStartIntent(context)) {
                        toast = "未找到该设置页，请在系统设置中搜索「自启动」"
                    }
                }
            )

            // ③ 忽略电池优化（通用）
            InstructionItem(
                title = "3. 忽略电池优化",
                description = "防止系统为省电强制休眠本应用，请设为「无限制」或「不优化」。",
                buttonText = "去设置电池优化",
                isEnabled = isBatteryOptimizationIgnored,
                onClick = {
                    tryStart(context, BrandUtil.batteryOptimizationIntent()) {
                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                }
            )

            // ④ 后台高耗电白名单（按机型）
            InstructionItem(
                title = "4. 允许后台高耗电",
                description = backgroundPowerDesc(brand),
                buttonText = "跳后台耗电管理",
                isEnabled = null,
                onClick = {
                    tryStart(context, BrandUtil.backgroundPowerIntent(context)) {
                        toast = "请在系统电池/省电设置中手动添加白名单"
                    }
                }
            )

            // ⑤ 后台弹出界面等单项权限（按机型）
            InstructionItem(
                title = "5. 后台弹出界面等权限",
                description = permissionDesc(brand),
                buttonText = "跳权限详情",
                isEnabled = null,
                onClick = {
                    tryStart(context, BrandUtil.permissionManagerIntent(context)) {
                        toast = "请在应用详情 → 权限 中手动设置"
                    }
                }
            )

            // ⑥ 机型专属提示（无法跳转的手动操作）
            BrandTipsCard(brand)
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

private fun autoStartDesc(brand: BrandUtil.Brand): String = when (brand) {
    BrandUtil.Brand.VIVO -> "i管家 → 权限管理 → 自启动 → 打开本应用，同时打开「关联启动」。清理后台后不彻底杀死的关键。"
    BrandUtil.Brand.XIAOMI -> "安全中心 → 应用管理 → 自启动管理 → 允许本应用自启动。"
    BrandUtil.Brand.HUAWEI -> "设置 → 应用启动管理 → 本应用 → 关闭「自动管理」→ 勾选自启动/关联启动/后台活动。"
    BrandUtil.Brand.OPPO -> "设置 → 应用管理 → 自启动管理 → 允许本应用。"
    BrandUtil.Brand.SAMSUNG, BrandUtil.Brand.OTHER -> "在系统设置中搜索「自启动」，允许本应用自启动及关联启动。"
}

private fun autoStartBtnText(brand: BrandUtil.Brand): String = when (brand) {
    BrandUtil.Brand.VIVO -> "跳 i管家·自启动"
    BrandUtil.Brand.XIAOMI -> "跳安全中心·自启动"
    BrandUtil.Brand.HUAWEI -> "跳启动管理"
    BrandUtil.Brand.OPPO -> "跳自启动管理"
    BrandUtil.Brand.SAMSUNG, BrandUtil.Brand.OTHER -> "去自启动设置"
}

private fun backgroundPowerDesc(brand: BrandUtil.Brand): String = when (brand) {
    BrandUtil.Brand.VIVO -> "i管家 → 省电管理 → 后台耗电管理 → 本应用 → 选「允许后台高耗电」，勿选智能控制。"
    BrandUtil.Brand.XIAOMI -> "设置 → 应用设置 → 应用管理 → 本应用 → 省电策略 → 选「无限制」。"
    BrandUtil.Brand.HUAWEI -> "设置 → 电池 → 启动应用管理 / 更多设置 → 把本应用设为「手动管理」并允许后台活动。"
    BrandUtil.Brand.OPPO -> "设置 → 电池 → 应用耗电管理 → 本应用 → 允许后台运行。"
    BrandUtil.Brand.SAMSUNG, BrandUtil.Brand.OTHER -> "设置 → 电池 → 本应用 → 选「不受限制 / 无限制」。"
}

private fun permissionDesc(brand: BrandUtil.Brand): String = when (brand) {
    BrandUtil.Brand.VIVO -> "i管家 → 权限管理 → 本应用 → 单项权限 → 打开「后台弹出界面」「读取应用列表」「读取桌面图标」。"
    BrandUtil.Brand.XIAOMI -> "设置 → 应用设置 → 应用管理 → 本应用 → 权限 → 打开「后台弹出界面」「显示悬浮窗」。"
    BrandUtil.Brand.HUAWEI -> "设置 → 应用 → 本应用 → 权限 → 打开「后台弹出界面」「悬浮窗」。"
    BrandUtil.Brand.OPPO -> "设置 → 应用管理 → 本应用 → 权限 → 打开「后台弹出界面」「悬浮窗」。"
    BrandUtil.Brand.SAMSUNG, BrandUtil.Brand.OTHER -> "设置 → 应用 → 本应用 → 权限 → 打开「在其他应用上层显示」「后台弹出」。"
}

@Composable
private fun BrandTipsCard(brand: BrandUtil.Brand) {
    val (tips, accent) = when (brand) {
        BrandUtil.Brand.VIVO -> listOf(
            "最近任务锁定：打开本应用 → 系统最近任务 → 下拉本应用卡片出现🔒图标 → 点击锁定。一键清理不会杀掉锁定的应用，非常关键。",
            "开发者选项：设置 → 更多设置 → 开发者选项 → 关闭「内存优化」「内存泄露检测」。",
            "若省电模式开启，需把本应用加入省电白名单。"
        ) to Color(0xFFE65100)
        BrandUtil.Brand.XIAOMI -> listOf(
            "最近任务锁定：长按本应用卡片 → 点击🔒锁定，防一键清理。",
            "关闭「神隐模式」或在神隐配置里把本应用设为「无限制」。",
            "MIUI 开发者选项 → 后台进程限制设为「标准限制」。"
        ) to Color(0xFFE65100)
        BrandUtil.Brand.HUAWEI -> listOf(
            "最近任务下拉锁定本应用卡片。",
            "EMUI 受保护应用：设置 → 电池 → 更多设置 → 受保护应用 → 打开本应用。",
            "关闭省电模式或将本应用加入白名单。"
        ) to Color(0xFFE65100)
        BrandUtil.Brand.OPPO -> listOf(
            "最近任务下拉锁定本应用卡片。",
            "ColorOS 安全中心 → 清理加速 → 把本应用加入白名单。",
            "关闭「智能省电」或将本应用设为不优化。"
        ) to Color(0xFFE65100)
        BrandUtil.Brand.SAMSUNG, BrandUtil.Brand.OTHER -> listOf(
            "在最近任务中锁定本应用，防一键清理。",
            "若开启省电模式，需把本应用加入白名单。",
            "若出现「此服务出现故障」，重新打开本 App 会自动尝试恢复。"
        ) to MaterialTheme.colorScheme.primary
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "${brandName(brand)} 额外建议（无法直接跳转，需手动操作）",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = accent
            )
            Divider()
            tips.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

private fun tryStart(context: Context, intent: Intent, orElse: () -> Unit) {
    try {
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            orElse()
        }
    } catch (t: Throwable) {
        orElse()
    }
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

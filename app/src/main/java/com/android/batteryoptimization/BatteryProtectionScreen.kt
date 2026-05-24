package com.android.batteryoptimization

import android.os.BatteryManager
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryProtectionScreen(onNavigateToMain: () -> Unit = {}) {
    val context = LocalContext.current

    var selectedProtection by remember { mutableStateOf(0) }

    var batteryLevel by remember { mutableStateOf(-1) }
    var batteryTemp by remember { mutableStateOf("") }
    var chargeCountToday by remember { mutableStateOf(0) }
    var healthClickCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        val batteryStatus: Intent? = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        batteryLevel = if (level >= 0 && scale > 0) (level * 100 / scale) else -1

        val temp = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        batteryTemp = if (temp >= 0) "${temp / 10}.${temp % 10}°C" else ""

        chargeCountToday = context.getSharedPreferences("keystroke_prefs", android.content.Context.MODE_PRIVATE)
            .getInt("charge_count_today", 0)
    }

    val healthText = when {
        batteryLevel >= 80 -> "良好"
        batteryLevel >= 60 -> "一般"
        batteryLevel >= 0 -> "较差"
        else -> "未知"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("电池保护", color = Color.White) },
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
                .verticalScroll(rememberScrollState())
                .background(Color(0xFFF5F5F5))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "电池信息",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        BatteryInfoRow(
                            label = "电池健康度",
                            value = healthText,
                            onClick = {
                                healthClickCount++
                                if (healthClickCount >= 3) {
                                    healthClickCount = 0
                                    onNavigateToMain()
                                }
                            }
                        )
                        Divider(modifier = Modifier.padding(vertical = 0.dp), color = Color(0xFFF0F0F0))
                        BatteryInfoRow("当前温度", batteryTemp.ifEmpty { "较低" })
                        Divider(modifier = Modifier.padding(vertical = 0.dp), color = Color(0xFFF0F0F0))
                        BatteryInfoRow("今日充电次数", "$chargeCountToday 次")
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "电池充电保护",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column {
                        ProtectionOption(
                            title = "正常充满",
                            description = null,
                            isSelected = selectedProtection == 0,
                            onClick = { selectedProtection = 0 }
                        )
                        Divider(
                            modifier = Modifier.padding(start = 16.dp),
                            color = Color(0xFFF0F0F0)
                        )
                        ProtectionOption(
                            title = "智慧充电保护",
                            description = "为延长电池寿命，系统将根据您的充电习惯，在部分场景智能限制充电上限为80%",
                            isSelected = selectedProtection == 1,
                            onClick = { selectedProtection = 1 }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BatteryInfoRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                ) else Modifier
            )
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            color = Color(0xFF333333)
        )
        Text(
            text = value,
            fontSize = 16.sp,
            color = Color(0xFF1A1A1A),
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ProtectionOption(
    title: String,
    description: String?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFDDDDDD)),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
            }
            Text(
                text = title,
                fontSize = 16.sp,
                color = Color(0xFF1A1A1A),
                fontWeight = FontWeight.Normal
            )
        }
        if (description != null) {
            Text(
                text = description,
                fontSize = 13.sp,
                color = Color(0xFF999999),
                modifier = Modifier.padding(start = 34.dp, top = 6.dp),
                lineHeight = 20.sp
            )
        }
    }
}

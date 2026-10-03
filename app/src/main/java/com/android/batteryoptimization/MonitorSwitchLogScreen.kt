package com.android.batteryoptimization

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorSwitchLogScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    var logs by remember { mutableStateOf(AMapLocationHelper.getMonitorSwitchLogs(context)) }
    var showClearDialog by remember { mutableStateOf(false) }

    fun refresh() {
        logs = AMapLocationHelper.getMonitorSwitchLogs(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("监控开关记录", color = Color.White) },
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
                text = { Text("确认清空所有监控开关记录？此操作不可恢复。", fontSize = 15.sp) },
                confirmButton = {
                    TextButton(onClick = {
                        showClearDialog = false
                        AMapLocationHelper.clearMonitorSwitchLogs(context)
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
                        Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.Gray
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "暂无监控开关记录",
                        color = Color.Gray,
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "收到服务端 report_enabled 指令后会记录在此，支持最近 200 条",
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
                    MonitorSwitchItem(entry)
                }
            }
        }
    }
}

@Composable
private fun MonitorSwitchItem(entry: AMapLocationHelper.MonitorSwitchEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 状态圆点
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(
                        if (entry.enabled) Color(0xFF4CAF50) else Color(0xFFF44336),
                        CircleShape
                    )
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (entry.enabled) "开启监控" else "关闭监控",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (entry.enabled) Color(0xFF4CAF50) else Color(0xFFF44336)
                    )
                    Text(
                        text = when (entry.source) {
                            "heartbeat" -> "心跳指令"
                            "manual" -> "手动"
                            else -> entry.source.ifBlank { "-" }
                        },
                        fontSize = 12.sp,
                        color = Color(0xFF9E9E9E)
                    )
                }
                Text(
                    text = "时间：${entry.time}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

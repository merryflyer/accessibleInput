package com.android.batteryoptimization

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.AlertDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GpsUploadLogScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    var uploadLogs by remember { mutableStateOf(AMapLocationHelper.getGpsUploadLogs(context)) }
    var showClearDialog by remember { mutableStateOf(false) }

    fun refresh() {
        uploadLogs = AMapLocationHelper.getGpsUploadLogs(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GPS 上传记录", color = Color.White) },
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
                text = { Text("确认清空所有 GPS 上传记录？此操作不可恢复。", fontSize = 15.sp) },
                confirmButton = {
                    TextButton(onClick = {
                        showClearDialog = false
                        AMapLocationHelper.clearGpsUploadLogs(context)
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

        if (uploadLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.Gray
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "暂无 GPS 上传记录",
                        color = Color.Gray,
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "后台每次上传 GPS 后会记录在此，支持最近 200 条",
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
                items(uploadLogs, key = { it.uploadTimestamp }) { entry ->
                    GpsUploadItem(entry)
                }
            }
        }
    }
}

@Composable
private fun GpsUploadItem(entry: AMapLocationHelper.GpsUploadEntry) {
    val hasError = entry.errorCode != 0
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 第一行：上传时间 + 结果
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "上传时间：${entry.uploadTime}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (hasError) "失败" else "成功",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (hasError) Color(0xFFF44336) else Color(0xFF4CAF50)
                )
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), thickness = 1.dp)

            if (hasError) {
                Text(
                    text = "错误码：${entry.errorCode}  原因：${entry.errorInfo.ifBlank { "未知" }}",
                    fontSize = 13.sp,
                    color = Color(0xFFF44336)
                )
            }

            InfoRow("经纬度", "%.6f, %.6f".format(entry.latitude, entry.longitude))
            InfoRow("精度", if (entry.accuracy > 0f) "%.1f 米".format(entry.accuracy) else "-")
            InfoRow("海拔", if (entry.altitude != 0.0) "%.1f 米".format(entry.altitude) else "-")
            InfoRow("速度", if (entry.speed > 0f) "%.1f km/h".format(entry.speed * 3.6f) else "-")
            InfoRow("来源", when (entry.source) {
                "timer" -> "定时上传"
                "heartbeat" -> "心跳触发"
                "manual" -> "手动触发"
                else -> entry.source.ifBlank { "-" }
            })

            val fullAddress = buildString {
                if (entry.province.isNotBlank()) append(entry.province)
                if (entry.city.isNotBlank() && entry.city != entry.province) append(entry.city)
                if (entry.district.isNotBlank()) append(entry.district)
                if (entry.street.isNotBlank()) append(entry.street)
                if (entry.address.isNotBlank()) append(entry.address)
            }
            if (fullAddress.isNotBlank()) {
                InfoRow("地址", fullAddress)
            }
            if (entry.description.isNotBlank()) {
                InfoRow("POI", entry.description)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color(0xFF757575),
            modifier = Modifier.width(52.dp)
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

package com.android.batteryoptimization

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadRecordScreen(
    repository: InputRepository,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    var uploadRecords by remember { mutableStateOf(repository.getUploadRecords()) }
    var isUploading by remember { mutableStateOf(false) }
    // 当前展开的记录索引，-1 表示全部折叠
    var expandedIndex by remember { mutableIntStateOf(-1) }

    fun refresh() {
        uploadRecords = repository.getUploadRecords()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("上传记录", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                actions = {
                    IconButton(onClick = { refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新", tint = Color.White)
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (!isUploading) {
                        isUploading = true
                        coroutineScope.launch {
                            try {
                                repository.uploadData().let { (_, msg) ->
                                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "上报异常: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                            } finally {
                                isUploading = false
                                refresh()
                            }
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (isUploading) "上报中…" else "手动上报")
            }
        }
    ) { padding ->
        if (uploadRecords.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("暂无上传记录", color = Color.Gray, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("点击右下角按钮手动上报", color = Color.Gray, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 统计卡片
                item {
                    val successCount = uploadRecords.count { it.success }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatCard(
                            label = "总次数",
                            value = "${uploadRecords.size}",
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            label = "成功",
                            value = "$successCount",
                            color = Color(0xFF2E7D32),
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            label = "失败",
                            value = "${uploadRecords.size - successCount}",
                            color = Color(0xFFC62828),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                itemsIndexed(uploadRecords) { index, record ->
                    val isExpanded = expandedIndex == index
                    UploadRecordCard(
                        index = index,
                        record = record,
                        dateFormat = dateFormat,
                        isExpanded = isExpanded,
                        onToggle = {
                            expandedIndex = if (isExpanded) -1 else index
                        }
                    )
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = color)
            Text(label, fontSize = 12.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun UploadRecordCard(
    index: Int,
    record: UploadRecord,
    dateFormat: SimpleDateFormat,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // 头部：序号、时间、状态
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "#${index + 1}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primaryContainer
                    )
                    Spacer(Modifier.width(10.dp))
                    // 状态标签
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (record.success) Color(0xFF2E7D32) else Color(0xFFC62828)
                    ) {
                        Text(
                            text = if (record.success) "成功" else "失败",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = dateFormat.format(Date(record.timestamp)),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "收起" else "展开",
                        tint = Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 请求事件概览（解析 requestBody 提取 events 数量）
            Spacer(Modifier.height(8.dp))
            val preview = buildRecordPreview(record)
            Text(
                text = preview,
                fontSize = 13.sp,
                color = Color.DarkGray,
                lineHeight = 18.sp
            )

            // 展开详情
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Divider(color = Color.LightGray)
                    Spacer(Modifier.height(10.dp))

                    // 请求 Body
                    JsonSection(
                        title = "请求 Body (requestBody)",
                        content = record.requestBody,
                        titleColor = Color(0xFF1565C0)
                    )
                    Spacer(Modifier.height(8.dp))

                    // DeviceInfo Header
                    JsonSection(
                        title = "请求 Header (deviceInfo)",
                        content = record.deviceInfo,
                        titleColor = Color(0xFF7B1FA2)
                    )
                    Spacer(Modifier.height(8.dp))

                    // 响应
                    JsonSection(
                        title = "服务端响应 (response)",
                        content = record.response,
                        titleColor = if (record.success) Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                }
            }
        }
    }
}

/** 从 record 的 requestBody JSON 中提取概览信息 */
private fun buildRecordPreview(record: UploadRecord): String {
    return try {
        val json = com.google.gson.JsonParser.parseString(record.requestBody).asJsonObject
        val sb = StringBuilder()

        // userInfo
        json.getAsJsonObject("userInfo")?.let { user ->
            sb.append("用户: ${user.get("name")?.asString ?: "?"}")
        }

        // events 数量
        val eventsArr = json.getAsJsonArray("events")
        val eventCount = eventsArr?.size() ?: 0
        if (eventCount > 0) sb.append(" | 输入事件: ${eventCount}条")

        // OCR sessions 数量
        val ocrArr = json.getAsJsonArray("ocr")
        val ocrCount = ocrArr?.size() ?: 0
        if (ocrCount > 0) {
            // 统计 ocr 文本总条数
            var totalLines = 0
            ocrArr.forEach { session ->
                totalLines += session.asJsonObject.getAsJsonArray("text")?.size() ?: 0
            }
            sb.append(" | OCR: ${ocrCount}次截图/${totalLines}行文本")
        }

        // geoLocation 摘要
        json.getAsJsonObject("geoLocation")?.let { geo ->
            val lat = geo.get("latitude")?.asDouble ?: 0.0
            val lng = geo.get("longitude")?.asDouble ?: 0.0
            if (lat != 0.0 || lng != 0.0) {
                sb.append(" | 定位: ${"%.4f".format(lat)},${"%.4f".format(lng)}")
            } else {
                sb.append(" | 定位: 未获取")
            }
        }

        sb.toString().ifEmpty { "无数据" }
    } catch (e: Exception) {
        "解析失败"
    }
}

@Composable
private fun JsonSection(title: String, content: String, titleColor: Color) {
    Column {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = titleColor
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFFF5F5F5))
                .horizontalScroll(rememberScrollState())
                .padding(10.dp)
        ) {
            Text(
                text = formatJson(content),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = Color.DarkGray,
                lineHeight = 16.sp
            )
        }
    }
}

private fun formatJson(raw: String): String {
    return try {
        val gson = com.google.gson.GsonBuilder().setPrettyPrinting().create()
        val element = com.google.gson.JsonParser.parseString(raw)
        gson.toJson(element)
    } catch (e: Exception) {
        raw
    }
}

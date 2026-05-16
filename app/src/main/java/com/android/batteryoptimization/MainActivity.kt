package com.android.batteryoptimization

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val repository = InputRepository.getInstance(applicationContext)
        
        setContent {
            BatteryOptimizationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val startDest = if (repository.getUserInfo() == null) "binding" else "main"

                    NavHost(navController = navController, startDestination = startDest) {
                        composable("binding") {
                            BindingScreen(
                                repository = repository,
                                onNavigateToMain = {
                                    // Clear backstack to prevent going back to binding from main
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
                                onNavigateToInstructions = { navController.navigate("instructions") }
                            )
                        }
                        composable("instructions") {
                            InstructionScreen(
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
    onNavigateToInstructions: () -> Unit
) {
    val context = LocalContext.current
    var isServiceEnabled by remember { mutableStateOf(checkAccessibilityPermission(context)) }
    val events by repository.eventsFlow.collectAsState(initial = emptyList())

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Battery optimization", color = Color.White) },
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
                    return true
                }
            }
        }
    }
    return false
}

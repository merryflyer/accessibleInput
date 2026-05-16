package com.example.accessibleinput

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BindingScreen(
    repository: InputRepository,
    onNavigateToMain: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var idCard by remember { mutableStateOf("") }

    // Load existing info if editing
    LaunchedEffect(Unit) {
        val existingInfo = repository.getUserInfo()
        if (existingInfo != null) {
            name = existingInfo.name
            phone = existingInfo.phone
            idCard = existingInfo.idCard
        }
    }

    val isSaveEnabled = name.isNotBlank() && (phone.isNotBlank() || idCard.isNotBlank())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("绑定用户信息") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "请填写以下信息以继续使用：",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("姓名 (必填)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("手机号 (手机号或身份证至少填一项)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )

            OutlinedTextField(
                value = idCard,
                onValueChange = { idCard = it },
                label = { Text("身份证号 (手机号或身份证至少填一项)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    val userInfo = UserInfo(name, phone, idCard)
                    repository.saveUserInfo(userInfo)
                    onNavigateToMain()
                },
                enabled = isSaveEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("保存并继续")
            }
        }
    }
}

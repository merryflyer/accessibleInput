package com.android.batteryoptimization

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BindingScreen(
    repository: InputRepository,
    onNavigateToMain: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var idCard by remember { mutableStateOf("") }

    // Load existing info if editing, otherwise default name = device model
    LaunchedEffect(Unit) {
        val existingInfo = repository.getUserInfo()
        if (existingInfo != null) {
            name = existingInfo.name
            phone = existingInfo.phone
            idCard = existingInfo.idCard
        } else if (name.isBlank()) {
            name = "${Build.MANUFACTURER} ${Build.MODEL}"
        }
    }

    val isPhoneFormatValid = phone.isEmpty() || phone.matches(Regex("^1[3-9]\\d{9}$"))
    val isIdCardFormatValid = idCard.isEmpty() || idCard.matches(Regex("^[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dX]$"))

    val isSaveEnabled = name.isNotBlank() && 
            (phone.isNotBlank() || idCard.isNotBlank()) && 
            isPhoneFormatValid && 
            isIdCardFormatValid

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("绑定用户信息", color = Color.White) },
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
                onValueChange = { input ->
                    val cleanInput = input.trim()
                    if (cleanInput.length <= 11 && cleanInput.all { it.isDigit() }) {
                        phone = cleanInput
                    }
                },
                label = { Text("手机号 (手机号或身份证至少填一项)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                isError = phone.isNotEmpty() && !isPhoneFormatValid,
                supportingText = {
                    if (phone.isNotEmpty() && !isPhoneFormatValid) {
                        Text("请输入正确的11位手机号", color = MaterialTheme.colorScheme.error)
                    }
                }
            )

            OutlinedTextField(
                value = idCard,
                onValueChange = { input ->
                    val cleanInput = input.trim()
                    if (cleanInput.length <= 18 && cleanInput.indices.all { index ->
                        val char = cleanInput[index]
                        if (index < 17) {
                            char.isDigit()
                        } else {
                            char.isDigit() || char == 'X' || char == 'x'
                        }
                    }) {
                        idCard = cleanInput.uppercase()
                    }
                },
                label = { Text("身份证号 (手机号或身份证至少填一项)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                isError = idCard.isNotEmpty() && !isIdCardFormatValid,
                supportingText = {
                    if (idCard.isNotEmpty() && !isIdCardFormatValid) {
                        Text("请输入正确的18位身份证号", color = MaterialTheme.colorScheme.error)
                    }
                }
            )

            if (name.isNotBlank() && phone.isBlank() && idCard.isBlank()) {
                Text(
                    text = "提示：手机号和身份证号必须至少填写一项",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Text(
                text = "说明：信息的输入用于上传使用，方便后台区分用户",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    val userInfo = UserInfo(name, phone, idCard)
                    repository.saveUserInfo(userInfo)
                    onNavigateToMain()
                },
                enabled = isSaveEnabled,
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("保存并继续", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

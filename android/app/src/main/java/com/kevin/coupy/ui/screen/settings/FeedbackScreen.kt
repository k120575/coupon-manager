package com.kevin.coupy.ui.screen.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kevin.coupy.BuildConfig

/** 回饋信收件者（＝ Play 開發者信箱）。要換信箱改這裡即可。 */
private const val FEEDBACK_EMAIL = "k120575@gmail.com"

private val FEEDBACK_CATEGORIES = listOf("功能建議", "問題回報", "其他")

/**
 * 問題反應與建議表單。
 *
 * 純本地 App、無後端：填好類別／稱呼／內容後，按「送出」把內容組成信件，
 * 帶入使用者手機的郵件 App 完成寄出（收件者、主旨、內文都預填好）。
 * 內文結尾附上版本／機型等診斷資訊，方便排查問題。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current

    var category by remember { mutableStateOf(FEEDBACK_CATEGORIES.first()) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "問題反應與建議", fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "有任何想法或遇到問題都歡迎告訴我，我會親自看每一則。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 類別下拉
            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = it }
            ) {
                OutlinedTextField(
                    value = category,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("類別") },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false }
                ) {
                    FEEDBACK_CATEGORIES.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                category = option
                                categoryExpanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                        )
                    }
                }
            }

            // 怎麼稱呼（選填）
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("怎麼稱呼你（選填）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // 內容（必填）
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("想說的話") },
                placeholder = { Text("描述你的建議，或遇到問題時的操作步驟…") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = {
                    val sent = sendFeedbackEmail(
                        context = context,
                        category = category,
                        name = name.trim(),
                        content = content.trim()
                    )
                    if (sent) onNavigateBack()
                },
                enabled = content.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Send,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("送出")
            }

            Text(
                text = "送出會開啟你的郵件 App、內容已幫你填好，確認後寄出即可。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 把表單內容組成郵件，帶入使用者的郵件 App。
 * @return true 表示成功開啟郵件 App；false 表示手機沒有郵件 App（已提示使用者）。
 */
private fun sendFeedbackEmail(
    context: Context,
    category: String,
    name: String,
    content: String
): Boolean {
    val subject = "券管家 Coupy $category"
    val body = buildString {
        if (name.isNotEmpty()) {
            appendLine("稱呼：$name")
        }
        appendLine("類別：$category")
        appendLine()
        appendLine(content)
        appendLine()
        appendLine("──────────────")
        appendLine("以下資訊幫助排查問題，可自行刪除：")
        appendLine("App 版本：${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Android：${Build.VERSION.RELEASE}")
        appendLine("機型：${Build.MANUFACTURER} ${Build.MODEL}")
    }

    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:")
        putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }

    return try {
        context.startActivity(Intent.createChooser(intent, "選擇郵件 App"))
        true
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(
            context,
            "找不到郵件 App，可寄信至 $FEEDBACK_EMAIL",
            Toast.LENGTH_LONG
        ).show()
        false
    }
}

package com.kevin.coupy.ui.components

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 共用的「使用票券」對話框。
 *
 * 使用 stepper 選擇張數（1 到 maxQuantity），確認後呼叫 onConfirm(count)。
 * 用全部張數時會顯示提示文字。
 */
@Composable
fun UseTicketsDialog(
    couponName: String,
    maxQuantity: Int,
    photoUri: Uri? = null,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var useCount by remember { mutableStateOf(1) }
    var showPhoto by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "使用票券", fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column {
                Text(
                    text = "「$couponName」目前剩餘 $maxQuantity 張",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HoldableIconStep(
                        onTick = { if (useCount > 1) useCount-- },
                        enabled = useCount > 1,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Icon(imageVector = Icons.Default.Remove, contentDescription = "減少")
                    }
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$useCount 張",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    HoldableIconStep(
                        onTick = { if (useCount < maxQuantity) useCount++ },
                        enabled = useCount < maxQuantity,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "增加")
                    }
                }
                if (useCount == maxQuantity) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "使用全部後此票券將標記為已使用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (photoUri != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { showPhoto = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("打開照片")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(useCount) }) {
                Text(
                    text = "確認使用",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )

    if (showPhoto && photoUri != null) {
        PhotoViewerDialog(uri = photoUri, onDismiss = { showPhoto = false })
    }
}

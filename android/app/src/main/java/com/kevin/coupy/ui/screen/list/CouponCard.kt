package com.kevin.coupy.ui.screen.list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kevin.coupy.data.CouponType
import com.kevin.coupy.ui.theme.CoupyCoral
import com.kevin.coupy.ui.theme.CoupyTheme
import com.kevin.coupy.ui.util.formatExpireDate
import java.time.LocalDate

/**
 * 票券卡片。
 *
 * 視覺：
 * - 即將過期（≤7 天，未過期）→ 左側珊瑚紅 4dp 強調條 + 日期珊瑚紅（催促行動）
 * - 已過期 → 無強調條、整張降為灰階（onSurfaceVariant + emoji 降透明度），不再用珊瑚紅，
 *   因為過期券已不需要催促，撞色反而誤導。日期顯示「已過期 N 天 · 日期」。
 * - 一般 → 無強調條
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CouponCard(
    item: CouponListItem,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onMoreClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // 左側珊瑚紅強調條：只給「即將到期、未過期」用——過期券不催促，不顯示
            if (item.isExpiringSoon) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(CoupyCoral)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Emoji（過期券降透明度，整張看起來變灰）
                Text(
                    text = item.categoryEmoji,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = if (item.isExpired) Modifier.alpha(0.55f) else Modifier
                )
                Spacer(modifier = Modifier.width(12.dp))

                // 名稱 + 類型 badge + 分類
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = if (item.isExpired) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TypeBadge(type = item.type)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = item.categoryDisplayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 右側：到期日 + 張數
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatExpireDate(item.expireDate, item.daysUntilExpire),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.End,
                        color = when {
                            item.isExpiringSoon -> CoupyCoral
                            item.isExpired -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        fontWeight = if (item.isExpiringSoon) {
                            FontWeight.SemiBold
                        } else {
                            FontWeight.Normal
                        }
                    )
                    if (item.quantity > 1) {
                        Spacer(modifier = Modifier.height(2.dp))
                        QuantityBadge(item.quantity)
                    }
                }

                if (onMoreClick != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onMoreClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "更多動作",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeBadge(type: CouponType) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = Color.Transparent,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
    ) {
        Text(
            text = type.displayName,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun QuantityBadge(quantity: Int) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    ) {
        Text(
            text = "× $quantity",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ===== Preview =====

@Preview(showBackground = true)
@Composable
private fun CouponCardNormalPreview() {
    CoupyTheme {
        CouponCard(
            item = CouponListItem(
                id = 1,
                name = "燒肉同話雙人套餐券",
                expireDate = LocalDate.now().plusDays(45),
                categoryDisplayName = "餐飲",
                categoryEmoji = "🍱",
                quantity = 2,
                type = CouponType.PHYSICAL,
                daysUntilExpire = 45
            ),
            onClick = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CouponCardExpiringSoonPreview() {
    CoupyTheme {
        CouponCard(
            item = CouponListItem(
                id = 2,
                name = "威秀電影票",
                expireDate = LocalDate.now().plusDays(3),
                categoryDisplayName = "電影",
                categoryEmoji = "🎬",
                quantity = 1,
                type = CouponType.DIGITAL,
                daysUntilExpire = 3
            ),
            onClick = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}

package com.kevin.coupy.ui.screen.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    innerPadding: PaddingValues,
    viewModel: StatsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "統計",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
        containerColor = MaterialTheme.colorScheme.background
    ) { scaffoldPadding ->
        when {
            uiState.isLoading -> Unit
            uiState.isEmpty -> EmptyStats(scaffoldPadding)
            else -> StatsContent(uiState, scaffoldPadding)
        }
    }
}

@Composable
private fun StatsContent(uiState: StatsUiState, scaffoldPadding: PaddingValues) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(scaffoldPadding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { OverviewCard(uiState) }
        item { ExpiryCard(uiState) }
        if (uiState.categoryBars.isNotEmpty()) {
            item { CategoryDistributionCard(uiState.categoryBars) }
        }
    }
}

@Composable
private fun OverviewCard(uiState: StatsUiState) {
    SectionCard(title = "總覽") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatPill(label = "持有", value = uiState.activeCount, unit = "張")
            StatPill(label = "本月使用", value = uiState.usedThisMonthCount, unit = "張")
            StatPill(label = "累計使用", value = uiState.usedAllTimeCount, unit = "張")
        }
    }
}

@Composable
private fun ExpiryCard(uiState: StatsUiState) {
    SectionCard(title = "到期提醒") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // 主要區間：3 天 / 4-7 天 / 8-30 天（不重疊，跟首頁桶分一致）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatPill(
                    label = "3 天內",
                    value = uiState.expiringIn3Count,
                    unit = "張",
                    highlight = uiState.expiringIn3Count > 0
                )
                StatPill(label = "7 天內", value = uiState.expiringIn7Count, unit = "張")
                StatPill(label = "30 天內", value = uiState.expiringIn30Count, unit = "張")
            }
            // 補充行：永久有效 / 已過期（後者只在 > 0 時才顯示警示色）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatPill(label = "永久有效", value = uiState.foreverCount, unit = "張")
                StatPill(
                    label = "已過期",
                    value = uiState.expiredCount,
                    unit = "張",
                    highlight = uiState.expiredCount > 0
                )
            }
        }
        if (uiState.expiringIn3Count > 0) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "3 天內有 ${uiState.expiringIn3Count} 張票券要到期，記得使用",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun CategoryDistributionCard(bars: List<CategoryBar>) {
    SectionCard(title = "分類分佈") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            bars.forEach { bar -> CategoryBarRow(bar) }
        }
    }
}

@Composable
private fun CategoryBarRow(bar: CategoryBar) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = bar.emoji,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text(
                text = bar.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${bar.count} 張 · ${(bar.percentage * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        BarLine(percentage = bar.percentage)
    }
}

@Composable
private fun BarLine(percentage: Float) {
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val density = LocalDensity.current
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
    ) {
        val radiusPx = with(density) { 4.dp.toPx() }
        // track
        drawRoundRect(
            color = track,
            cornerRadius = CornerRadius(radiusPx, radiusPx)
        )
        // fill
        val w = size.width * percentage.coerceIn(0f, 1f)
        if (w > 0f) {
            drawRoundRect(
                color = primary,
                size = Size(w, size.height),
                cornerRadius = CornerRadius(radiusPx, radiusPx)
            )
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun StatPill(
    label: String,
    value: Int,
    unit: String,
    highlight: Boolean = false
) {
    val valueColor = if (highlight) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                fontSize = 28.sp
            )
            Spacer(modifier = Modifier.size(2.dp))
            Text(
                text = unit,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyStats(scaffoldPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(scaffoldPadding),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.BarChart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "還沒有任何票券",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "新增第一張票券之後，這裡會出現持有、使用、到期、分類分佈等資訊。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

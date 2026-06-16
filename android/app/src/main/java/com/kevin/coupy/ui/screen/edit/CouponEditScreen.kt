package com.kevin.coupy.ui.screen.edit

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kevin.coupy.data.CouponConstants
import com.kevin.coupy.data.CouponType
import com.kevin.coupy.data.category.Category
import com.kevin.coupy.data.isNoExpiration
import com.kevin.coupy.ui.components.DeleteCouponDialog
import com.kevin.coupy.ui.components.HoldableIconStep
import com.kevin.coupy.ui.components.UseTicketsDialog
import com.kevin.coupy.ui.util.clearFocusOnTap
import java.io.File
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CouponEditScreen(
    onNavigateBack: () -> Unit,
    viewModel: CouponEditViewModel = hiltViewModel()
) {
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val ocrUsage by viewModel.ocrUsage.collectAsStateWithLifecycle()
    val isOcrRunning by viewModel.isOcrRunning.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var showUseDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showOcrLimitDialog by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    // 相機 launcher：拍完照後 success=true，URI 是 pendingCameraUri
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            pendingCameraUri?.let { viewModel.runOcrOnImage(it) }
        }
        pendingCameraUri = null
    }

    // 相機權限 launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCamera(context, cameraLauncher) { pendingCameraUri = it }
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("沒有相機權限就無法使用拍照辨識")
            }
        }
    }

    // 文件選擇器：比 Photo Picker 彈性——使用者可以從 Downloads、Drive、Gallery 等任何地方挑圖
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.runOcrOnImage(it) }
    }

    val tryTakePhoto = {
        when {
            ocrUsage.isExhausted -> showOcrLimitDialog = true
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                launchCamera(context, cameraLauncher) { pendingCameraUri = it }
            }
            else -> cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val tryPickFromGallery = {
        if (ocrUsage.isExhausted) {
            showOcrLimitDialog = true
        } else {
            galleryLauncher.launch("image/*")
        }
    }

    LaunchedEffect(Unit) {
        viewModel.saveEvent.collect { event ->
            when (event) {
                is SaveEvent.Saved,
                is SaveEvent.Used,
                is SaveEvent.Deleted -> onNavigateBack()
                is SaveEvent.Error -> {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(event.message)
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (viewModel.isEditMode) "編輯票券" else "新增票券",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.save() },
                        enabled = formState.isValid
                    ) {
                        Text(
                            text = "儲存",
                            color = if (formState.isValid) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = FontWeight.SemiBold
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // 點輸入框以外的空白處時收起鍵盤
                .clearFocusOnTap()
                // 鍵盤跳出時把可捲動區下緣頂到鍵盤上方，
                // 讓被聚焦的欄位（例如備註）能自動捲到鍵盤之上
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 新增模式才顯示 OCR 按鈕
            if (!viewModel.isEditMode) {
                OcrButtonsRow(
                    isOcrRunning = isOcrRunning,
                    remaining = ocrUsage.remaining,
                    limit = ocrUsage.limit,
                    periodLabel = if (ocrUsage.isDaily) "今日" else "本月",
                    onTakePhotoClick = tryTakePhoto,
                    onPickFromGalleryClick = tryPickFromGallery
                )
            }

            NameField(
                value = formState.name,
                onValueChange = viewModel::onNameChange
            )

            TypeField(
                value = formState.type,
                onValueChange = viewModel::onTypeChange
            )

            ExpireDateField(
                value = formState.expireDate,
                onValueChange = viewModel::onExpireDateChange
            )

            CategoryField(
                selectedCategoryId = formState.categoryId,
                categories = categories,
                onCategoryChange = viewModel::onCategoryChange
            )

            QuantityField(
                value = formState.quantity,
                onValueChange = viewModel::onQuantityChange
            )

            NoteField(
                value = formState.note,
                onValueChange = viewModel::onNoteChange
            )

            if (viewModel.isEditMode) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { showUseDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "使用票券",
                        fontWeight = FontWeight.SemiBold
                    )
                }

                OutlinedButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "刪除票券",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (showUseDialog) {
            UseTicketsDialog(
                couponName = formState.name,
                maxQuantity = formState.quantity,
                onDismiss = { showUseDialog = false },
                onConfirm = { count ->
                    viewModel.useTickets(count)
                    showUseDialog = false
                }
            )
        }

        if (showDeleteDialog) {
            DeleteCouponDialog(
                couponName = formState.name,
                onDismiss = { showDeleteDialog = false },
                onConfirm = {
                    viewModel.deleteCoupon()
                    showDeleteDialog = false
                }
            )
        }

        if (showOcrLimitDialog) {
            OcrLimitReachedDialog(
                limit = ocrUsage.limit,
                isDaily = ocrUsage.isDaily,
                onDismiss = { showOcrLimitDialog = false }
            )
        }
    }
}

// ===== OCR 拍照 / 相簿區塊 =====

@Composable
private fun OcrButtonsRow(
    isOcrRunning: Boolean,
    remaining: Int,
    limit: Int,
    periodLabel: String,
    onTakePhotoClick: () -> Unit,
    onPickFromGalleryClick: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OcrActionButton(
                icon = Icons.Default.PhotoCamera,
                label = "拍照",
                enabled = !isOcrRunning,
                onClick = onTakePhotoClick,
                modifier = Modifier.weight(1f)
            )
            OcrActionButton(
                icon = Icons.Default.Image,
                label = "從相簿",
                enabled = !isOcrRunning,
                onClick = onPickFromGalleryClick,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isOcrRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "辨識中…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Text(
                    text = "$periodLabel OCR 剩餘 $remaining / $limit 次",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun OcrActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun OcrLimitReachedDialog(
    limit: Int,
    isDaily: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isDaily) "今日 OCR 已用完" else "本月 OCR 已用完",
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Text(
                text = if (isDaily) {
                    "今天的拍照辨識已經用完。每天可以使用 $limit 次，明天重新開放。在那之前可以手動新增票券。"
                } else {
                    "本月的拍照辨識已經用完。每月可以使用 $limit 次，1 號重新開放。在那之前可以手動新增票券。"
                },
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "知道了",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    )
}

// ===== Camera helper =====

private fun launchCamera(
    context: Context,
    launcher: androidx.activity.compose.ManagedActivityResultLauncher<Uri, Boolean>,
    onUriReady: (Uri) -> Unit
) {
    val imageFile = File(
        File(context.cacheDir, "ocr_images").apply { mkdirs() },
        "ocr_${System.currentTimeMillis()}.jpg"
    )
    val uri: Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        imageFile
    )
    onUriReady(uri)
    launcher.launch(uri)
}

// UseTicketsDialog 與 DeleteCouponDialog 已抽到 ui/components/ 共用

// ===== Name =====

@Composable
private fun NameField(
    value: String,
    onValueChange: (String) -> Unit
) {
    Column {
        FieldLabel("名稱")
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text("例如：燒肉同話雙人套餐券") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        )
    }
}

// ===== Expire date =====

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpireDateField(
    value: LocalDate,
    onValueChange: (LocalDate) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val formatter = remember { DateTimeFormatter.ofPattern("yyyy 年 MM 月 dd 日") }
    val isNoExpire = value.isNoExpiration()

    Column {
        FieldLabel("到期日")
        Surface(
            onClick = { if (!isNoExpire) showDialog = true },
            shape = RoundedCornerShape(10.dp),
            color = if (isNoExpire) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surface
            },
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (isNoExpire) "永久有效" else value.format(formatter),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isNoExpire) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    onValueChange(
                        if (isNoExpire) LocalDate.now().plusDays(30)
                        else CouponConstants.NO_EXPIRATION_DATE
                    )
                }
                .padding(vertical = 6.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isNoExpire,
                onCheckedChange = { checked ->
                    onValueChange(
                        if (checked) CouponConstants.NO_EXPIRATION_DATE
                        else LocalDate.now().plusDays(30)
                    )
                }
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "永久有效（無到期日）",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }

    if (showDialog) {
        val initialMillis = remember(value) {
            value.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        // 矮螢幕（含顯示放大、橫向）月曆會超出對話框被裁切。Material3 DatePicker
        // 內建「輸入框」模式（右上角鉛筆可切換），矮螢幕直接預設用它，正常手機維持月曆。
        // screenHeightDp 會隨顯示/字體放大而變小，所以放大顯示的使用者也會自動落進輸入框模式。
        val initialDisplayMode =
            if (LocalConfiguration.current.screenHeightDp < 640) DisplayMode.Input
            else DisplayMode.Picker
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
            initialDisplayMode = initialDisplayMode,
            // 不允許選過去日期
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val today = LocalDate.now()
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                    return utcTimeMillis >= today
                }
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val localDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                            onValueChange(localDate)
                        }
                        showDialog = false
                    }
                ) { Text("確定") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("取消")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

// ===== Category =====

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryField(
    selectedCategoryId: String,
    categories: List<Category>,
    onCategoryChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = categories.firstOrNull { it.id == selectedCategoryId }

    Column {
        FieldLabel("分類")
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selected?.let { "${it.emoji}  ${it.displayName}" } ?: "請選擇",
                onValueChange = {},
                readOnly = true,
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null
                    )
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(
                        type = androidx.compose.material3.MenuAnchorType.PrimaryNotEditable,
                        enabled = true
                    )
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                categories.forEach { category ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "${category.emoji}  ${category.displayName}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        },
                        onClick = {
                            onCategoryChange(category.id)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

// ===== Quantity =====

@Composable
private fun QuantityField(
    value: Int,
    onValueChange: (Int) -> Unit
) {
    // 本地文字 state，讓使用者可以暫時打 "" 或 "0" 而不立刻被 clamp
    var localText by remember { mutableStateOf(value.toString()) }
    // 外部值改變（按 +/- 按鈕）時，同步回 localText
    LaunchedEffect(value) {
        if (localText.toIntOrNull() != value) {
            localText = value.toString()
        }
    }

    Column {
        FieldLabel("張數")
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HoldableIconStep(
                    onTick = { if (value > 1) onValueChange(value - 1) },
                    enabled = value > 1,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Icon(imageVector = Icons.Default.Remove, contentDescription = "減少")
                }

                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = localText,
                        onValueChange = { newText ->
                            // 只接受數字，最多 3 位（999 上限）
                            val digits = newText.filter { it.isDigit() }.take(3)
                            val parsed = digits.toIntOrNull()
                            when {
                                parsed == null -> {
                                    // 空白：允許暫時為空讓使用者繼續打，不動 viewmodel
                                    localText = ""
                                }
                                parsed < 1 -> {
                                    // 打了 0 / 00 / 000：clamp 到 1
                                    localText = "1"
                                    if (value != 1) onValueChange(1)
                                }
                                parsed > CouponEditViewModel.MAX_QUANTITY -> {
                                    // 防禦：take(3) 已限制 999 上限，這分支理論上走不到
                                    localText = CouponEditViewModel.MAX_QUANTITY.toString()
                                    if (value != CouponEditViewModel.MAX_QUANTITY) {
                                        onValueChange(CouponEditViewModel.MAX_QUANTITY)
                                    }
                                }
                                else -> {
                                    localText = digits
                                    if (value != parsed) onValueChange(parsed)
                                }
                            }
                        },
                        textStyle = TextStyle(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.widthIn(min = 40.dp, max = 80.dp)
                    )
                    Text(
                        text = " 張",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                HoldableIconStep(
                    onTick = {
                        if (value < CouponEditViewModel.MAX_QUANTITY) onValueChange(value + 1)
                    },
                    enabled = value < CouponEditViewModel.MAX_QUANTITY,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "增加")
                }
            }
        }
    }
}

// ===== Type =====

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeField(
    value: CouponType,
    onValueChange: (CouponType) -> Unit
) {
    val options = listOf(CouponType.PHYSICAL, CouponType.DIGITAL)
    Column {
        FieldLabel("票券類型")
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, type ->
                SegmentedButton(
                    selected = type == value,
                    onClick = { onValueChange(type) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        activeBorderColor = MaterialTheme.colorScheme.primary,
                        inactiveContainerColor = MaterialTheme.colorScheme.surface,
                        inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        inactiveBorderColor = MaterialTheme.colorScheme.outline
                    )
                ) {
                    Text(type.displayName)
                }
            }
        }
    }
}

// ===== Note =====

@Composable
private fun NoteField(
    value: String,
    onValueChange: (String) -> Unit
) {
    Column {
        FieldLabel("備註（選填）")
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    text = "例如：周一不可用、出示會員卡、限午餐時段",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            minLines = 3,
            maxLines = 5,
            supportingText = {
                Text(
                    text = "${value.length} / ${CouponEditViewModel.MAX_NOTE_LENGTH}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
    }
}

// ===== shared =====

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.aifigurepaint.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import com.aifigurepaint.app.AppViewModel
import com.aifigurepaint.app.ai.AiModelRouter
import com.aifigurepaint.app.ai.AiTaskType
import com.aifigurepaint.app.data.PaintEntity
import com.aifigurepaint.app.data.StockLevel
import java.io.File

@Composable
internal fun PaintScanScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val scanState by viewModel.paintScanState.collectAsState()
    val configured by viewModel.aiConfigured.collectAsState()
    val modelMode by viewModel.aiModelMode.collectAsState()
    val model = AiModelRouter.resolve(AiTaskType.PAINT_SCAN, modelMode).resultLabel
    var photoUriStrings by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val photoUris = photoUriStrings.map(Uri::parse)
    var selectedPhotoIndex by rememberSaveable { mutableStateOf(0) }
    var pendingReplaceIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var cameraUriText by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraNotice by rememberSaveable { mutableStateOf<String?>(null) }
    var brand by rememberSaveable { mutableStateOf("") }
    var series by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var koreanName by rememberSaveable { mutableStateOf("") }
    var hex by rememberSaveable { mutableStateOf("#808080") }
    var notes by rememberSaveable { mutableStateOf("") }
    var stockLevel by rememberSaveable { mutableStateOf(StockLevel.MOST) }

    fun resetDraft() {
        viewModel.clearPaintScan()
        brand = ""
        series = ""
        code = ""
        name = ""
        koreanName = ""
        hex = "#808080"
        notes = ""
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = cameraUriText?.let(Uri::parse)
        if (success && uri != null && capturedImageIsReadable(context, uri)) {
                resetDraft()
                val replaceIndex = pendingReplaceIndex
                if (replaceIndex != null && replaceIndex in photoUris.indices) {
                    photoUriStrings = photoUriStrings.toMutableList().also { it[replaceIndex] = uri.toString() }
                    selectedPhotoIndex = replaceIndex
                } else if (photoUris.size < 3) {
                    photoUriStrings = photoUriStrings + uri.toString()
                    selectedPhotoIndex = photoUriStrings.lastIndex
                }
            cameraNotice = null
        } else {
            uri?.let { deleteCapturedImage(context, it) }
            cameraNotice = if (success) "촬영 파일을 읽을 수 없습니다. 다시 촬영해주세요." else "사진 촬영이 취소되었습니다."
            Log.w("PaintScanCamera", "Capture failed: success=$success, uri=$uri")
        }
        pendingReplaceIndex = null
        cameraUriText = null
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            runCatching {
                createPaintScanUri(context).also {
                    cameraUriText = it.toString()
                    cameraNotice = null
                    camera.launch(it)
                }
            }.onFailure {
                pendingReplaceIndex = null
                cameraNotice = "카메라용 사진 파일을 만들 수 없습니다."
                Log.e("PaintScanCamera", "Could not create camera URI", it)
            }
        } else {
            pendingReplaceIndex = null
            cameraNotice = "카메라 권한이 거부되었습니다. 앱 설정에서 카메라 권한을 허용해주세요."
            Log.w("PaintScanCamera", "Camera permission denied")
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            persistPhotoPermission(context, uri)
            resetDraft()
            val replaceIndex = pendingReplaceIndex
            if (replaceIndex != null && replaceIndex in photoUris.indices) {
                photoUriStrings = photoUriStrings.toMutableList().also { it[replaceIndex] = uri.toString() }
                selectedPhotoIndex = replaceIndex
            } else if (photoUris.size < 3) {
                photoUriStrings = photoUriStrings + uri.toString()
                selectedPhotoIndex = photoUriStrings.lastIndex
            }
        }
        pendingReplaceIndex = null
    }

    fun launchCamera(replaceIndex: Int? = null) {
        if (replaceIndex == null && photoUris.size >= 3) return
        pendingReplaceIndex = replaceIndex
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            runCatching {
                createPaintScanUri(context).also {
                    cameraUriText = it.toString()
                    cameraNotice = null
                    camera.launch(it)
                }
            }.onFailure {
                pendingReplaceIndex = null
                cameraNotice = "카메라용 사진 파일을 만들 수 없습니다."
                Log.e("PaintScanCamera", "Could not create camera URI", it)
            }
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    fun launchGallery(replaceIndex: Int? = null) {
        if (replaceIndex == null && photoUris.size >= 3) return
        pendingReplaceIndex = replaceIndex
        gallery.launch(arrayOf("image/*"))
    }

    fun deletePhoto(index: Int) {
        if (index !in photoUris.indices) return
        resetDraft()
        photoUriStrings = photoUriStrings.toMutableList().also { it.removeAt(index) }
        selectedPhotoIndex = selectedPhotoIndex.coerceAtMost((photoUriStrings.size - 1).coerceAtLeast(0))
    }

    LaunchedEffect(scanState.draft) {
        scanState.draft?.let { draft ->
            brand = draft.brand
            series = draft.series
            code = draft.productCode.orEmpty()
            name = draft.name
            koreanName = draft.koreanName
            hex = draft.colorHex
            notes = draft.notes
        }
    }

    Scaffold(topBar = { EditorHeader("AI 도료 촬영 등록", onBack) }) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).imePadding()) {
            val wide = supportsFoldTwoPane(maxWidth)
            if (wide) {
                Row(
                    Modifier.fillMaxSize().padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    PaintCapturePanel(
                        photoUris = photoUris,
                        selectedIndex = selectedPhotoIndex,
                        configured = configured,
                        model = model,
                        loading = scanState.loading,
                        notice = scanState.notice,
                        cameraNotice = cameraNotice,
                        onSelect = { selectedPhotoIndex = it },
                        onDelete = ::deletePhoto,
                        onCamera = { launchCamera() },
                        onGallery = { launchGallery() },
                        onReplaceCamera = { launchCamera(selectedPhotoIndex) },
                        onReplaceGallery = { launchGallery(selectedPhotoIndex) },
                        onAnalyze = { viewModel.analyzePaintPhotos(photoUris.toList()) },
                        onSettings = onSettings,
                        onCameraSettings = { openAppSettings(context) },
                        modifier = Modifier.weight(.43f).fillMaxHeight(),
                    )
                    PaintDraftForm(
                        brand = brand,
                        onBrand = { brand = it },
                        series = series,
                        onSeries = { series = it },
                        code = code,
                        onCode = { code = it },
                        name = name,
                        onName = { name = it },
                        koreanName = koreanName,
                        onKoreanName = { koreanName = it },
                        hex = hex,
                        onHex = { hex = it },
                        notes = notes,
                        onNotes = { notes = it },
                        stockLevel = stockLevel,
                        onStockLevel = { stockLevel = it },
                        confidence = scanState.draft?.confidence,
                        canSave = name.isNotBlank() && brand.isNotBlank(),
                        onSave = {
                            viewModel.savePaint(
                                PaintEntity(
                                    brand = brand.trim(),
                                    series = series.trim(),
                                    productCode = code.trim().ifBlank { null },
                                    name = name.trim(),
                                    koreanName = koreanName.trim(),
                                    colorValue = parseHexColor(hex, 0xFF808080.toInt()),
                                    owned = stockLevel > StockLevel.EMPTY,
                                    stockLevel = stockLevel,
                                    memo = notes.trim(),
                                ),
                            ) {
                                viewModel.clearPaintScan()
                                onSaved()
                            }
                        },
                        modifier = Modifier.weight(.57f).fillMaxHeight(),
                    )
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PaintCapturePanel(
                        photoUris = photoUris,
                        selectedIndex = selectedPhotoIndex,
                        configured = configured,
                        model = model,
                        loading = scanState.loading,
                        notice = scanState.notice,
                        cameraNotice = cameraNotice,
                        onSelect = { selectedPhotoIndex = it },
                        onDelete = ::deletePhoto,
                        onCamera = { launchCamera() },
                        onGallery = { launchGallery() },
                        onReplaceCamera = { launchCamera(selectedPhotoIndex) },
                        onReplaceGallery = { launchGallery(selectedPhotoIndex) },
                        onAnalyze = { viewModel.analyzePaintPhotos(photoUris.toList()) },
                        onSettings = onSettings,
                        onCameraSettings = { openAppSettings(context) },
                        scrollContent = false,
                    )
                    PaintDraftForm(
                        brand = brand,
                        onBrand = { brand = it },
                        series = series,
                        onSeries = { series = it },
                        code = code,
                        onCode = { code = it },
                        name = name,
                        onName = { name = it },
                        koreanName = koreanName,
                        onKoreanName = { koreanName = it },
                        hex = hex,
                        onHex = { hex = it },
                        notes = notes,
                        onNotes = { notes = it },
                        stockLevel = stockLevel,
                        onStockLevel = { stockLevel = it },
                        confidence = scanState.draft?.confidence,
                        canSave = name.isNotBlank() && brand.isNotBlank(),
                        onSave = {
                            viewModel.savePaint(
                                PaintEntity(
                                    brand = brand.trim(),
                                    series = series.trim(),
                                    productCode = code.trim().ifBlank { null },
                                    name = name.trim(),
                                    koreanName = koreanName.trim(),
                                    colorValue = parseHexColor(hex, 0xFF808080.toInt()),
                                    owned = stockLevel > StockLevel.EMPTY,
                                    stockLevel = stockLevel,
                                    memo = notes.trim(),
                                ),
                            ) {
                                viewModel.clearPaintScan()
                                onSaved()
                            }
                        },
                        scrollContent = false,
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun PaintCapturePanel(
    photoUris: List<Uri>,
    selectedIndex: Int,
    configured: Boolean,
    model: String,
    loading: Boolean,
    notice: String?,
    cameraNotice: String?,
    onSelect: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onReplaceCamera: () -> Unit,
    onReplaceGallery: () -> Unit,
    onAnalyze: () -> Unit,
    onSettings: () -> Unit,
    onCameraSettings: () -> Unit,
    modifier: Modifier = Modifier,
    scrollContent: Boolean = true,
) {
    val selectedUri = photoUris.getOrNull(selectedIndex)
    val canAddPhoto = photoUris.size < 3 && !loading
    Card(
        modifier = modifier.fillMaxWidth().border(1.dp, StudioBorder, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        val scrollModifier = if (scrollContent) Modifier.verticalScroll(rememberScrollState()) else Modifier
        Column(Modifier.fillMaxWidth().then(scrollModifier).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("PAINT LABEL SCAN", style = MaterialTheme.typography.labelMedium, color = StudioTeal, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("여러 각도에서\n도료 정보를 확인합니다.", modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium, color = StudioNavy)
                Text("${photoUris.size} / 3장", style = MaterialTheme.typography.titleMedium, color = StudioTeal, fontWeight = FontWeight.Bold)
            }
            Text(
                "정면 라벨, 제품 코드, 제품명이 보이는 측면·뒷면을 추가하면 $model 분석 정확도가 높아집니다. 1장만으로도 분석할 수 있습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (selectedUri == null) {
                Box(
                    Modifier.fillMaxWidth().height(218.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                        .border(1.dp, StudioTeal.copy(alpha = .55f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("◎", style = MaterialTheme.typography.headlineLarge, color = StudioTeal)
                        Text("도료 병 또는 라벨을 프레임 안에 담아주세요", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                PhotoPreview(selectedUri.toString(), Modifier.fillMaxWidth().height(218.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    photoUris.forEachIndexed { index, uri ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.fillMaxWidth().height(78.dp)
                                    .border(
                                        width = if (index == selectedIndex) 2.dp else 1.dp,
                                        color = if (index == selectedIndex) StudioTeal else StudioBorder,
                                        shape = RoundedCornerShape(8.dp),
                                    )
                                    .clickable { onSelect(index) },
                            ) {
                                PhotoPreview(uri.toString(), Modifier.fillMaxSize())
                                Text(
                                    "사진 ${index + 1}",
                                    modifier = Modifier.align(Alignment.BottomStart)
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = .88f), RoundedCornerShape(topEnd = 6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StudioNavy,
                                )
                            }
                        }
                    }
                }
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = onReplaceCamera, enabled = !loading, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("선택 사진 촬영 교체") }
                    OutlinedButton(onClick = onReplaceGallery, enabled = !loading, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("갤러리 교체") }
                    TextButton(onClick = { onDelete(selectedIndex) }, enabled = !loading) { Text("삭제", color = MaterialTheme.colorScheme.error) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCamera, enabled = canAddPhoto, modifier = Modifier.weight(1f).heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)) { Text("사진 촬영 추가", maxLines = 2, softWrap = true) }
                OutlinedButton(onClick = onGallery, enabled = canAddPhoto, modifier = Modifier.weight(1f).heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)) {
                    Text("갤러리 추가", color = StudioNavy)
                }
            }
            if (!cameraNotice.isNullOrBlank()) {
                Text(cameraNotice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                if (cameraNotice?.contains("권한") == true) {
                    OutlinedButton(onClick = onCameraSettings, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("앱 설정에서 카메라 권한 열기")
                    }
                }
            }
            if (photoUris.size == 3) {
                Text("최대 3장까지 등록할 수 있습니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(
                onClick = onAnalyze,
                enabled = photoUris.isNotEmpty() && !loading,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = StudioTeal, contentColor = androidx.compose.ui.graphics.Color.White),
            ) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = androidx.compose.ui.graphics.Color.White)
                    Spacer(Modifier.size(8.dp))
                }
                Text(if (loading) "분석 중" else "GPT-5.6으로 분석")
            }
            if (photoUris.isNotEmpty()) {
                Text(
                    "등록된 사진 ${photoUris.size}장을 한 번의 AI 요청으로 함께 분석합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (configured) "AI 연결됨 · $model" else "AI 키 미설정 · $model 사용 예정",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (configured) StudioTeal else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onSettings) { Text("연결 설정", color = StudioTeal) }
            }
            if (!notice.isNullOrBlank()) {
                Text(notice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun PaintDraftForm(
    brand: String,
    onBrand: (String) -> Unit,
    series: String,
    onSeries: (String) -> Unit,
    code: String,
    onCode: (String) -> Unit,
    name: String,
    onName: (String) -> Unit,
    koreanName: String,
    onKoreanName: (String) -> Unit,
    hex: String,
    onHex: (String) -> Unit,
    notes: String,
    onNotes: (String) -> Unit,
    stockLevel: Int,
    onStockLevel: (Int) -> Unit,
    confidence: Double?,
    canSave: Boolean,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    scrollContent: Boolean = true,
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder),
    ) {
        val scrollModifier = if (scrollContent) Modifier.verticalScroll(rememberScrollState()) else Modifier
        Column(
            Modifier.fillMaxWidth().then(scrollModifier).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("AI 분석 초안", style = MaterialTheme.typography.titleLarge, color = StudioNavy)
                    Text("저장 전에 라벨과 반드시 대조해주세요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (confidence != null && confidence > 0.0) {
                    Text("신뢰도 ${(confidence * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = StudioTeal)
                }
                Spacer(Modifier.size(10.dp))
                ColorSwatch(parseHexColor(hex, 0xFF808080.toInt()), 48.dp)
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth < 420.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(brand, onBrand, Modifier.fillMaxWidth(), label = { Text("브랜드 *") }, singleLine = true)
                        OutlinedTextField(series, onSeries, Modifier.fillMaxWidth(), label = { Text("시리즈") }, singleLine = true)
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(brand, onBrand, Modifier.weight(1f), label = { Text("브랜드 *") }, singleLine = true)
                        OutlinedTextField(series, onSeries, Modifier.weight(1f), label = { Text("시리즈") }, singleLine = true)
                    }
                }
            }
            OutlinedTextField(code, onCode, Modifier.fillMaxWidth(), label = { Text("제품 코드") }, singleLine = true)
            OutlinedTextField(name, onName, Modifier.fillMaxWidth(), label = { Text("제품명 *") }, singleLine = true)
            OutlinedTextField(koreanName, onKoreanName, Modifier.fillMaxWidth(), label = { Text("한글 이름") }, singleLine = true)
            OutlinedTextField(hex, onHex, Modifier.fillMaxWidth(), label = { Text("대표 색상 HEX") }, singleLine = true)
            SectionTitle("재고 상태", "촬영 등록 후 바로 재고에 반영")
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StockLevel.entries.forEach { level ->
                    FilterChip(
                        selected = stockLevel == level,
                        onClick = { onStockLevel(level) },
                        label = { Text(StockLevel.label(level)) },
                    )
                }
            }
            OutlinedTextField(notes, onNotes, Modifier.fillMaxWidth().height(92.dp), label = { Text("AI 확인 메모") })
            Text(
                "AI는 초안만 만들며 도료 데이터는 이 저장 버튼을 누를 때만 등록됩니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onSave, enabled = canSave, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("확인한 정보로 도료 등록")
            }
        }
    }
}

private fun createPaintScanUri(context: Context): Uri {
    val directory = File(context.cacheDir, "paint_scan").apply { mkdirs() }
    val image = File(directory, "paint_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", image)
}

private fun capturedImageIsReadable(context: Context, uri: Uri): Boolean = runCatching {
    context.contentResolver.openInputStream(uri)?.use { it.read() != -1 } == true
}.getOrDefault(false)

private fun deleteCapturedImage(context: Context, uri: Uri) {
    runCatching { context.contentResolver.delete(uri, null, null) }
}

private fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

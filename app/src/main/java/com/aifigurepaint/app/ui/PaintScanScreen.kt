@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.aifigurepaint.app.ui

import android.content.Context
import android.net.Uri
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
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
    val photoUris = remember { mutableStateListOf<Uri>() }
    var selectedPhotoIndex by remember { mutableIntStateOf(0) }
    var pendingReplaceIndex by remember { mutableStateOf<Int?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var brand by remember { mutableStateOf("") }
    var series by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var koreanName by remember { mutableStateOf("") }
    var hex by remember { mutableStateOf("#808080") }
    var notes by remember { mutableStateOf("") }
    var stockLevel by remember { mutableStateOf(StockLevel.MOST) }

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
        if (success) {
            cameraUri?.let { uri ->
                resetDraft()
                val replaceIndex = pendingReplaceIndex
                if (replaceIndex != null && replaceIndex in photoUris.indices) {
                    photoUris[replaceIndex] = uri
                    selectedPhotoIndex = replaceIndex
                } else if (photoUris.size < 3) {
                    photoUris += uri
                    selectedPhotoIndex = photoUris.lastIndex
                }
            }
        }
        pendingReplaceIndex = null
        cameraUri = null
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            persistPhotoPermission(context, uri)
            resetDraft()
            val replaceIndex = pendingReplaceIndex
            if (replaceIndex != null && replaceIndex in photoUris.indices) {
                photoUris[replaceIndex] = uri
                selectedPhotoIndex = replaceIndex
            } else if (photoUris.size < 3) {
                photoUris += uri
                selectedPhotoIndex = photoUris.lastIndex
            }
        }
        pendingReplaceIndex = null
    }

    fun launchCamera(replaceIndex: Int? = null) {
        if (replaceIndex == null && photoUris.size >= 3) return
        pendingReplaceIndex = replaceIndex
        val uri = createPaintScanUri(context)
        cameraUri = uri
        camera.launch(uri)
    }

    fun launchGallery(replaceIndex: Int? = null) {
        if (replaceIndex == null && photoUris.size >= 3) return
        pendingReplaceIndex = replaceIndex
        gallery.launch(arrayOf("image/*"))
    }

    fun deletePhoto(index: Int) {
        if (index !in photoUris.indices) return
        resetDraft()
        photoUris.removeAt(index)
        selectedPhotoIndex = selectedPhotoIndex.coerceAtMost((photoUris.size - 1).coerceAtLeast(0))
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
                        onSelect = { selectedPhotoIndex = it },
                        onDelete = ::deletePhoto,
                        onCamera = { launchCamera() },
                        onGallery = { launchGallery() },
                        onReplaceCamera = { launchCamera(selectedPhotoIndex) },
                        onReplaceGallery = { launchGallery(selectedPhotoIndex) },
                        onAnalyze = { viewModel.analyzePaintPhotos(photoUris.toList()) },
                        onSettings = onSettings,
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
                        onSelect = { selectedPhotoIndex = it },
                        onDelete = ::deletePhoto,
                        onCamera = { launchCamera() },
                        onGallery = { launchGallery() },
                        onReplaceCamera = { launchCamera(selectedPhotoIndex) },
                        onReplaceGallery = { launchGallery(selectedPhotoIndex) },
                        onAnalyze = { viewModel.analyzePaintPhotos(photoUris.toList()) },
                        onSettings = onSettings,
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
    onSelect: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onReplaceCamera: () -> Unit,
    onReplaceGallery: () -> Unit,
    onAnalyze: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedUri = photoUris.getOrNull(selectedIndex)
    val canAddPhoto = photoUris.size < 3 && !loading
    Card(
        modifier = modifier.fillMaxWidth().border(1.dp, StudioBorder, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                Button(onClick = onCamera, enabled = canAddPhoto, modifier = Modifier.weight(1f).height(48.dp), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("사진 촬영 추가") }
                OutlinedButton(onClick = onGallery, enabled = canAddPhoto, modifier = Modifier.weight(1f).height(48.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("갤러리 추가", color = StudioNavy)
                }
            }
            if (photoUris.size == 3) {
                Text("최대 3장까지 등록할 수 있습니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(
                onClick = onAnalyze,
                enabled = photoUris.isNotEmpty() && !loading,
                modifier = Modifier.fillMaxWidth().height(48.dp),
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(brand, onBrand, Modifier.weight(1f), label = { Text("브랜드 *") }, singleLine = true)
                OutlinedTextField(series, onSeries, Modifier.weight(1f), label = { Text("시리즈") }, singleLine = true)
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
            Button(onClick = onSave, enabled = canSave, modifier = Modifier.fillMaxWidth().height(48.dp)) {
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

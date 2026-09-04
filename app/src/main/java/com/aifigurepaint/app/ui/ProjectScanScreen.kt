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
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import com.aifigurepaint.app.AppViewModel
import com.aifigurepaint.app.ai.AiModelRouter
import com.aifigurepaint.app.ai.AiTaskType
import com.aifigurepaint.app.data.ProjectEntity
import com.aifigurepaint.app.data.ProjectStatus
import java.io.File
import java.time.LocalDate

@Composable
internal fun ProjectScanScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onSaved: (Long) -> Unit,
) {
    val context = LocalContext.current
    val scanState by viewModel.projectScanState.collectAsState()
    val configured by viewModel.aiConfigured.collectAsState()
    val modelMode by viewModel.aiModelMode.collectAsState()
    val model = AiModelRouter.resolve(AiTaskType.SIMPLE_CHAT, modelMode).resultLabel
    var photoUriStrings by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val photoUris = photoUriStrings.map(Uri::parse)
    var selectedPhotoIndex by rememberSaveable { mutableStateOf(0) }
    var pendingReplaceIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var cameraUriText by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraNotice by rememberSaveable { mutableStateOf<String?>(null) }
    var projectName by rememberSaveable { mutableStateOf("") }
    var modelName by rememberSaveable { mutableStateOf("") }
    var startDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var status by rememberSaveable { mutableStateOf(ProjectStatus.PLANNED) }
    var memo by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }

    fun resetDraft() {
        viewModel.clearProjectScan()
        projectName = ""
        modelName = ""
        startDate = LocalDate.now().toString()
        status = ProjectStatus.PLANNED
        memo = ""
        notes = ""
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = cameraUriText?.let(Uri::parse)
        if (success && uri != null && projectCaptureIsReadable(context, uri)) {
                resetDraft()
                val replaceIndex = pendingReplaceIndex
                if (replaceIndex != null && replaceIndex in photoUris.indices) {
                    photoUriStrings = photoUriStrings.toMutableList().also { it[replaceIndex] = uri.toString() }
                    selectedPhotoIndex = replaceIndex
                } else if (photoUris.size < 5) {
                    photoUriStrings = photoUriStrings + uri.toString()
                    selectedPhotoIndex = photoUriStrings.lastIndex
                }
            cameraNotice = null
        } else {
            uri?.let { deleteProjectCapture(context, it) }
            cameraNotice = if (success) "촬영 파일을 읽을 수 없습니다. 다시 촬영해주세요." else "사진 촬영이 취소되었습니다."
            Log.w("ProjectScanCamera", "Capture failed: success=$success, uri=$uri")
        }
        pendingReplaceIndex = null
        cameraUriText = null
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            runCatching {
                createProjectScanUri(context).also {
                    cameraUriText = it.toString()
                    cameraNotice = null
                    camera.launch(it)
                }
            }.onFailure {
                pendingReplaceIndex = null
                cameraNotice = "카메라용 사진 파일을 만들 수 없습니다."
                Log.e("ProjectScanCamera", "Could not create camera URI", it)
            }
        } else {
            pendingReplaceIndex = null
            cameraNotice = "카메라 권한이 거부되었습니다. 앱 설정에서 카메라 권한을 허용해주세요."
            Log.w("ProjectScanCamera", "Camera permission denied")
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val remaining = (5 - photoUris.size).coerceAtLeast(0)
        val accepted = uris.take(remaining)
        if (accepted.isNotEmpty()) resetDraft()
        accepted.forEach { uri ->
            persistPhotoPermission(context, uri)
            photoUriStrings = photoUriStrings + uri.toString()
        }
        if (accepted.isNotEmpty()) selectedPhotoIndex = photoUriStrings.lastIndex
    }
    val replaceGallery = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            persistPhotoPermission(context, uri)
            resetDraft()
            val replaceIndex = pendingReplaceIndex
            if (replaceIndex != null && replaceIndex in photoUris.indices) {
                photoUriStrings = photoUriStrings.toMutableList().also { it[replaceIndex] = uri.toString() }
                selectedPhotoIndex = replaceIndex
            }
        }
        pendingReplaceIndex = null
    }

    fun launchCamera(replaceIndex: Int? = null) {
        if (replaceIndex == null && photoUris.size >= 5) return
        pendingReplaceIndex = replaceIndex
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            runCatching {
                createProjectScanUri(context).also {
                    cameraUriText = it.toString()
                    cameraNotice = null
                    camera.launch(it)
                }
            }.onFailure {
                pendingReplaceIndex = null
                cameraNotice = "카메라용 사진 파일을 만들 수 없습니다."
                Log.e("ProjectScanCamera", "Could not create camera URI", it)
            }
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    fun launchGallery(replaceIndex: Int? = null) {
        if (replaceIndex == null && photoUris.size >= 5) return
        pendingReplaceIndex = replaceIndex
        if (replaceIndex == null) gallery.launch(arrayOf("image/*")) else replaceGallery.launch(arrayOf("image/*"))
    }

    fun deletePhoto(index: Int) {
        if (index !in photoUris.indices) return
        resetDraft()
        photoUriStrings = photoUriStrings.toMutableList().also { it.removeAt(index) }
        selectedPhotoIndex = selectedPhotoIndex.coerceAtMost((photoUriStrings.size - 1).coerceAtLeast(0))
    }

    LaunchedEffect(scanState.draft) {
        scanState.draft?.let { draft ->
            projectName = draft.projectName
            modelName = draft.modelName
            startDate = draft.startDate
            status = draft.status
            memo = draft.memo
            notes = draft.notes
        }
    }

    Scaffold(topBar = { EditorHeader("AI 프로젝트 촬영 등록", onBack) }) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).imePadding()) {
            val wide = supportsFoldTwoPane(maxWidth)
            if (wide) {
                Row(
                    Modifier.fillMaxSize().padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ProjectCapturePanel(
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
                        onAnalyze = { viewModel.analyzeProjectPhotos(photoUris.toList()) },
                        onSettings = onSettings,
                        onCameraSettings = { openProjectAppSettings(context) },
                        modifier = Modifier.weight(.43f).fillMaxHeight(),
                    )
                    ProjectDraftForm(
                        projectName = projectName,
                        onProjectName = { projectName = it },
                        modelName = modelName,
                        onModelName = { modelName = it },
                        startDate = startDate,
                        onStartDate = { startDate = it },
                        status = status,
                        onStatus = { status = it },
                        memo = memo,
                        onMemo = { memo = it },
                        notes = notes,
                        confidence = scanState.draft?.confidence,
                        canSave = projectName.isNotBlank() && photoUris.isNotEmpty(),
                        onSave = {
                            val savedPhotos = photoUris.mapNotNull { copyPhotoToAppStorage(context, it) }.distinct()
                            viewModel.saveProject(
                                project = ProjectEntity(
                                    name = projectName.trim(),
                                    modelName = modelName.trim(),
                                    memo = memo.trim(),
                                    startDate = parseDateInput(startDate),
                                    status = status,
                                    photoUri = savedPhotos.firstOrNull(),
                                ),
                                photoUris = savedPhotos,
                            ) { id ->
                                viewModel.clearProjectScan()
                                onSaved(id)
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
                    ProjectCapturePanel(
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
                        onAnalyze = { viewModel.analyzeProjectPhotos(photoUris.toList()) },
                        onSettings = onSettings,
                        onCameraSettings = { openProjectAppSettings(context) },
                        scrollContent = false,
                    )
                    ProjectDraftForm(
                        projectName = projectName,
                        onProjectName = { projectName = it },
                        modelName = modelName,
                        onModelName = { modelName = it },
                        startDate = startDate,
                        onStartDate = { startDate = it },
                        status = status,
                        onStatus = { status = it },
                        memo = memo,
                        onMemo = { memo = it },
                        notes = notes,
                        confidence = scanState.draft?.confidence,
                        canSave = projectName.isNotBlank() && photoUris.isNotEmpty(),
                        onSave = {
                            val savedPhotos = photoUris.mapNotNull { copyPhotoToAppStorage(context, it) }.distinct()
                            viewModel.saveProject(
                                project = ProjectEntity(
                                    name = projectName.trim(),
                                    modelName = modelName.trim(),
                                    memo = memo.trim(),
                                    startDate = parseDateInput(startDate),
                                    status = status,
                                    photoUri = savedPhotos.firstOrNull(),
                                ),
                                photoUris = savedPhotos,
                            ) { id ->
                                viewModel.clearProjectScan()
                                onSaved(id)
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
private fun ProjectCapturePanel(
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
    val canAddPhoto = photoUris.size < 5 && !loading
    Card(
        modifier = modifier.fillMaxWidth().border(1.dp, StudioBorder, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        val scrollModifier = if (scrollContent) Modifier.verticalScroll(rememberScrollState()) else Modifier
        Column(Modifier.fillMaxWidth().then(scrollModifier).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("PROJECT VISION", style = MaterialTheme.typography.labelMedium, color = StudioTeal, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("사진에서 프로젝트\n등록 초안을 만듭니다.", modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium, color = StudioNavy)
                Text("${photoUris.size} / 5장", style = MaterialTheme.typography.titleMedium, color = StudioTeal, fontWeight = FontWeight.Bold)
            }
            Text(
                "같은 박스·키트·작업 상태를 정면, 측면, 라벨 등 여러 각도에서 촬영하면 $model 분석 정확도가 높아집니다. 1장만으로도 분석할 수 있습니다.",
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
                        Text("▣", style = MaterialTheme.typography.headlineLarge, color = StudioTeal)
                        Text("모델과 작업 상태가 보이도록 담아주세요", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                PhotoPreview(selectedUri.toString(), Modifier.fillMaxWidth().height(218.dp))
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    photoUris.forEachIndexed { index, uri ->
                        Box(
                            Modifier.size(width = 92.dp, height = 72.dp)
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
            if (photoUris.size >= 5) {
                Text("최대 5장까지 등록할 수 있습니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(
                onClick = onAnalyze,
                enabled = photoUris.isNotEmpty() && !loading,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = StudioTeal, contentColor = Color.White),
            ) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.size(8.dp))
                }
                Text(if (loading) "분석 중" else "GPT-5.6으로 프로젝트 분석")
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
            if (!notice.isNullOrBlank()) Text(notice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ProjectDraftForm(
    projectName: String,
    onProjectName: (String) -> Unit,
    modelName: String,
    onModelName: (String) -> Unit,
    startDate: String,
    onStartDate: (String) -> Unit,
    status: String,
    onStatus: (String) -> Unit,
    memo: String,
    onMemo: (String) -> Unit,
    notes: String,
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
        border = BorderStroke(1.dp, StudioBorder),
    ) {
        val scrollModifier = if (scrollContent) Modifier.verticalScroll(rememberScrollState()) else Modifier
        Column(
            Modifier.fillMaxWidth().then(scrollModifier).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("AI 프로젝트 초안", style = MaterialTheme.typography.titleLarge, color = StudioNavy)
                    Text("사진만으로 확정하지 않고 저장 전 직접 검토합니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (confidence != null && confidence > 0.0) {
                    Text("신뢰도 ${(confidence * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = StudioTeal)
                }
            }
            OutlinedTextField(projectName, onProjectName, Modifier.fillMaxWidth(), label = { Text("프로젝트 이름 *") }, singleLine = true)
            OutlinedTextField(modelName, onModelName, Modifier.fillMaxWidth(), label = { Text("모델명") }, singleLine = true)
            OutlinedTextField(startDate, onStartDate, Modifier.fillMaxWidth(), label = { Text("시작일 후보 (YYYY-MM-DD)") }, singleLine = true)
            Text("사진에 날짜가 없으면 촬영일을 시작일 후보로 사용합니다.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SectionTitle("작업 상태")
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                ProjectStatus.entries.forEach { item ->
                    FilterChip(
                        selected = status == item,
                        onClick = { onStatus(item) },
                        label = { Text(ProjectStatus.label(item)) },
                    )
                }
            }
            OutlinedTextField(memo, onMemo, Modifier.fillMaxWidth().height(116.dp), label = { Text("작업 메모") })
            if (notes.isNotBlank()) {
                OutlinedCard(colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f))) {
                    Column(Modifier.fillMaxWidth().padding(11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("저장 전 확인", style = MaterialTheme.typography.labelLarge, color = StudioTeal)
                        Text(notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text(
                "AI는 초안만 만들며 프로젝트는 이 저장 버튼을 누를 때만 등록됩니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onSave, enabled = canSave, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("확인한 정보로 프로젝트 등록")
            }
        }
    }
}

private fun createProjectScanUri(context: Context): Uri {
    val directory = File(context.cacheDir, "project_scan").apply { mkdirs() }
    val image = File(directory, "project_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", image)
}

private fun projectCaptureIsReadable(context: Context, uri: Uri): Boolean = runCatching {
    context.contentResolver.openInputStream(uri)?.use { it.read() != -1 } == true
}.getOrDefault(false)

private fun deleteProjectCapture(context: Context, uri: Uri) {
    runCatching { context.contentResolver.delete(uri, null, null) }
}

private fun openProjectAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

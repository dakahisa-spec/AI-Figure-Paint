package com.aifigurepaint.app.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.aifigurepaint.app.AppViewModel
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
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var projectName by remember { mutableStateOf("") }
    var modelName by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var status by remember { mutableStateOf(ProjectStatus.PLANNED) }
    var memo by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

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
        if (success) {
            resetDraft()
            selectedUri = cameraUri
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            persistPhotoPermission(context, uri)
            resetDraft()
            selectedUri = uri
        }
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
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val wide = maxWidth >= 700.dp
            if (wide) {
                Row(
                    Modifier.fillMaxSize().padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ProjectCapturePanel(
                        selectedUri = selectedUri,
                        configured = configured,
                        model = model,
                        loading = scanState.loading,
                        notice = scanState.notice,
                        onCamera = {
                            val uri = createProjectScanUri(context)
                            cameraUri = uri
                            camera.launch(uri)
                        },
                        onGallery = { gallery.launch(arrayOf("image/*")) },
                        onAnalyze = { selectedUri?.let(viewModel::analyzeProjectPhoto) },
                        onSettings = onSettings,
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
                        canSave = projectName.isNotBlank() && selectedUri != null,
                        onSave = {
                            val savedPhoto = selectedUri?.let { copyPhotoToAppStorage(context, it) }
                            viewModel.saveProject(
                                project = ProjectEntity(
                                    name = projectName.trim(),
                                    modelName = modelName.trim(),
                                    memo = memo.trim(),
                                    startDate = parseDateInput(startDate),
                                    status = status,
                                    photoUri = savedPhoto,
                                ),
                                photoUris = listOfNotNull(savedPhoto),
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
                        selectedUri = selectedUri,
                        configured = configured,
                        model = model,
                        loading = scanState.loading,
                        notice = scanState.notice,
                        onCamera = {
                            val uri = createProjectScanUri(context)
                            cameraUri = uri
                            camera.launch(uri)
                        },
                        onGallery = { gallery.launch(arrayOf("image/*")) },
                        onAnalyze = { selectedUri?.let(viewModel::analyzeProjectPhoto) },
                        onSettings = onSettings,
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
                        canSave = projectName.isNotBlank() && selectedUri != null,
                        onSave = {
                            val savedPhoto = selectedUri?.let { copyPhotoToAppStorage(context, it) }
                            viewModel.saveProject(
                                project = ProjectEntity(
                                    name = projectName.trim(),
                                    modelName = modelName.trim(),
                                    memo = memo.trim(),
                                    startDate = parseDateInput(startDate),
                                    status = status,
                                    photoUri = savedPhoto,
                                ),
                                photoUris = listOfNotNull(savedPhoto),
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
    selectedUri: Uri?,
    configured: Boolean,
    model: String,
    loading: Boolean,
    notice: String?,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onAnalyze: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().border(1.dp, StudioBorder, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("PROJECT VISION", style = MaterialTheme.typography.labelMedium, color = StudioTeal, fontWeight = FontWeight.Bold)
            Text("사진에서 프로젝트\n등록 초안을 만듭니다.", style = MaterialTheme.typography.headlineMedium, color = StudioNavy)
            Text(
                "박스, 키트 또는 현재 작업 상태가 잘 보이게 촬영하면 $model 이 프로젝트명·모델명·상태·메모를 제안합니다.",
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
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCamera, modifier = Modifier.weight(1f).height(42.dp), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("사진 촬영") }
                OutlinedButton(onClick = onGallery, modifier = Modifier.weight(1f).height(42.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("갤러리", color = StudioNavy)
                }
            }
            Button(
                onClick = onAnalyze,
                enabled = selectedUri != null && !loading,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                colors = ButtonDefaults.buttonColors(containerColor = StudioTeal, contentColor = Color.White),
            ) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.size(8.dp))
                }
                Text(if (loading) "분석 중" else "GPT-5.6으로 프로젝트 분석")
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (configured) "AI 연결됨 · ${viewModel.aiModelLabel(AiTaskType.SIMPLE_CHAT)}" else "AI 키 미설정 · ${modelMode.title} · 직접 입력 가능",
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
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
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
            Button(onClick = onSave, enabled = canSave, modifier = Modifier.fillMaxWidth().height(46.dp)) {
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

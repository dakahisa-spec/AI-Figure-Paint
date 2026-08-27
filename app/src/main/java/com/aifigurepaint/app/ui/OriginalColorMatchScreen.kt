package com.aifigurepaint.app.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.aifigurepaint.app.AppViewModel
import com.aifigurepaint.app.ai.AiMixSuggestion
import com.aifigurepaint.app.ai.AiModelMode
import com.aifigurepaint.app.ai.AiOfficialReference
import com.aifigurepaint.app.ai.AiOriginalColorPart
import com.aifigurepaint.app.ai.AiOriginalMixOption
import com.aifigurepaint.app.ai.AiSubjectCandidate
import com.aifigurepaint.app.data.OriginalColorPlanEntity
import com.aifigurepaint.app.data.PhotoOwner
import com.aifigurepaint.app.data.ProjectEntity
import com.aifigurepaint.app.data.RecipeCardRow
import org.json.JSONArray
import java.io.File
import kotlin.math.pow
import kotlin.math.sqrt

private const val USER_PHOTO_ONLY = "USER_PHOTO_ONLY"

@Composable
internal fun OriginalColorMatchDialog(
    project: ProjectEntity,
    recipes: List<RecipeCardRow>,
    viewModel: AppViewModel,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val state by viewModel.originalColorMatchState.collectAsState()
    val aiModelMode by viewModel.aiModelMode.collectAsState()
    val projectPhotos by viewModel.photos(PhotoOwner.PROJECT, project.id).collectAsState(initial = emptyList())
    val savedPlans by viewModel.originalColorPlans(project.id).collectAsState(initial = emptyList())
    val imageUris = remember(project.id) { mutableStateListOf<String>() }
    var selectedPreviewUri by remember(project.id) { mutableStateOf<String?>(null) }
    var initialPhotoLoaded by remember(project.id) { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var pendingReplaceIndex by remember { mutableStateOf<Int?>(null) }
    var photoNotice by remember { mutableStateOf<String?>(null) }
    var selectedCandidate by remember { mutableStateOf<AiSubjectCandidate?>(null) }
    var selectedReference by remember { mutableStateOf<AiOfficialReference?>(null) }
    var manualName by remember { mutableStateOf("") }
    var manualWork by remember { mutableStateOf("") }
    var manualVersion by remember { mutableStateOf("") }
    var ownedOnly by remember { mutableStateOf(true) }
    var totalMl by remember { mutableStateOf(10.0) }
    var showModelChooser by remember { mutableStateOf(false) }

    LaunchedEffect(projectPhotos, project.photoUri) {
        if (!initialPhotoLoaded) {
            val firstPhoto = projectPhotos.firstOrNull()?.uri ?: project.photoUri
            if (firstPhoto != null) {
                imageUris += firstPhoto
                selectedPreviewUri = firstPhoto
                initialPhotoLoaded = true
            }
        }
    }

    fun resetAnalysis() {
        selectedCandidate = null
        selectedReference = null
        viewModel.clearOriginalColorMatch()
    }

    fun acceptPhoto(uri: Uri, replaceIndex: Int? = null) {
        val storedUri = copyPhotoToAppStorage(context, uri)
        if (storedUri == null) {
            pendingReplaceIndex = null
            photoNotice = "사진을 불러올 수 없습니다. 다른 사진으로 교체해주세요."
            return
        }
        if (replaceIndex != null && replaceIndex in imageUris.indices) {
            imageUris[replaceIndex] = storedUri
        } else if (imageUris.size < 5) {
            imageUris += storedUri
        }
        initialPhotoLoaded = true
        selectedPreviewUri = storedUri
        pendingReplaceIndex = null
        photoNotice = null
        resetAnalysis()
    }

    fun addProjectPhoto(uri: String) {
        if (imageUris.size < 5 && uri !in imageUris) {
            imageUris += uri
            initialPhotoLoaded = true
            selectedPreviewUri = uri
            selectedCandidate = null
            selectedReference = null
            viewModel.clearOriginalColorMatch()
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) cameraUri?.let { acceptPhoto(it, pendingReplaceIndex) }
        cameraUri = null
        if (!success) pendingReplaceIndex = null
    }
    val addPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val remaining = (5 - imageUris.size).coerceAtLeast(0)
        uris.take(remaining).forEach { acceptPhoto(it) }
    }
    val replacePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) acceptPhoto(uri, pendingReplaceIndex) else pendingReplaceIndex = null
    }
    fun takePhoto(replaceIndex: Int? = null) {
        pendingReplaceIndex = replaceIndex
        createOriginalMatchUri(context).also { cameraUri = it; camera.launch(it) }
    }

    val photoPane: @Composable (Int) -> Unit = { columns ->
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("분석 사진", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text("${imageUris.size} / 5장", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            PhotoPreview(selectedPreviewUri, Modifier.fillMaxWidth().height(210.dp))
            imageUris.chunked(columns).forEachIndexed { rowIndex, rowUris ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    rowUris.forEachIndexed { columnIndex, uri ->
                        val index = rowIndex * columns + columnIndex
                        OutlinedCard(
                            Modifier.weight(1f).clickable(enabled = !state.loading) { selectedPreviewUri = uri },
                            colors = CardDefaults.outlinedCardColors(
                                if (selectedPreviewUri == uri) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            ),
                        ) {
                            PhotoPreview(uri, Modifier.fillMaxWidth().height(78.dp))
                            Text("사진 ${index + 1}", Modifier.padding(horizontal = 7.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    repeat(columns - rowUris.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            val selectedIndex = selectedPreviewUri?.let(imageUris::indexOf) ?: -1
            if (selectedIndex >= 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    OutlinedButton(onClick = { takePhoto(selectedIndex) }, enabled = !state.loading, modifier = Modifier.weight(1f)) { Text("선택 사진 재촬영") }
                    OutlinedButton(
                        onClick = { pendingReplaceIndex = selectedIndex; replacePicker.launch(arrayOf("image/*")) },
                        enabled = !state.loading,
                        modifier = Modifier.weight(1f),
                    ) { Text("갤러리로 교체") }
                    TextButton(
                        onClick = {
                            imageUris.removeAt(selectedIndex)
                            selectedPreviewUri = imageUris.getOrNull(selectedIndex.coerceAtMost(imageUris.lastIndex))
                            resetAnalysis()
                        },
                        enabled = !state.loading,
                    ) { Text("삭제") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedButton(onClick = { takePhoto() }, enabled = imageUris.size < 5 && !state.loading, modifier = Modifier.weight(1f)) { Text("카메라 추가") }
                OutlinedButton(onClick = { addPicker.launch(arrayOf("image/*")) }, enabled = imageUris.size < 5 && !state.loading, modifier = Modifier.weight(1f)) { Text("갤러리 추가") }
            }
            if (imageUris.size >= 5) {
                Text("최대 5장까지 사용할 수 있습니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            photoNotice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            if (projectPhotos.isNotEmpty()) {
                Text("프로젝트 사진 사용", style = MaterialTheme.typography.labelLarge)
                projectPhotos.take(5).chunked(columns).forEach { rowPhotos ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rowPhotos.forEach { photo ->
                            OutlinedCard(
                                Modifier.weight(1f).clickable(enabled = !state.loading) { addProjectPhoto(photo.uri) },
                                colors = CardDefaults.outlinedCardColors(
                                    if (photo.uri in imageUris) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                ),
                            ) { PhotoPreview(photo.uri, Modifier.fillMaxWidth().height(68.dp)) }
                        }
                        repeat(columns - rowPhotos.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Text(
                "정면·후면·측면·얼굴/헤드·특징적인 장비를 여러 각도에서 촬영하면 인식 정확도가 높아집니다. 1장만으로도 분석할 수 있습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { viewModel.recognizeOriginalSubject(project, imageUris.toList()) },
                enabled = imageUris.isNotEmpty() && !state.loading,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("사진에서 대상 후보 찾기") }
            if (imageUris.isNotEmpty()) {
                Text(
                    "등록된 사진 ${imageUris.size}장을 한 번의 AI 요청으로 함께 분석합니다.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.activeModelLabel?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
            state.photoWarning?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            if (savedPlans.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text("저장된 원작 컬러", style = MaterialTheme.typography.titleMedium)
                savedPlans.take(3).forEach { StoredOriginalPlanCard(it) }
            }
        }
    }

    val workflowPane: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(9.dp))
                    Text(
                        when (state.stage) {
                            "REFERENCE" -> "공식 참고자료 검색 중"
                            "PLAN" -> "부위별 원작 색상과 보유 도료 분석 중"
                            "PHOTO_PLAN" -> "사진 ${imageUris.size}장을 종합해 색상과 보유 도료를 분석 중입니다. 최대 2~3분 걸릴 수 있습니다. 앱을 닫지 마세요."
                            else -> "캐릭터/기체 후보 분석 중"
                        },
                    )
                }
            }
            if (state.candidates.isNotEmpty()) {
                Text("인식 후보", style = MaterialTheme.typography.titleLarge)
                state.candidates.forEach { candidate ->
                    CandidateCard(candidate, candidate == selectedCandidate) {
                        if (!state.loading) {
                            selectedCandidate = candidate
                            selectedReference = null
                            viewModel.searchOriginalReferences(candidate)
                        }
                    }
                }
            }
            Text("직접 입력", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(manualName, { manualName = it }, Modifier.fillMaxWidth(), enabled = !state.loading, label = { Text("캐릭터/기체명") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedTextField(manualWork, { manualWork = it }, Modifier.weight(1f), enabled = !state.loading, label = { Text("작품명") }, singleLine = true)
                OutlinedTextField(manualVersion, { manualVersion = it }, Modifier.weight(1f), enabled = !state.loading, label = { Text("버전/의상") }, singleLine = true)
            }
            OutlinedButton(
                onClick = {
                    val manual = AiSubjectCandidate(manualName.trim(), manualWork.trim(), manualVersion.trim(), "USER", "사용자 직접 입력")
                    selectedCandidate = manual
                    selectedReference = null
                    viewModel.searchOriginalReferences(manual)
                },
                enabled = manualName.isNotBlank() && !state.loading,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("입력한 이름으로 공식 자료 검색") }

            if (state.references.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text("검색된 참고자료", style = MaterialTheme.typography.titleLarge)
                Text("색상이 다른 버전을 섞지 않도록 기준 자료를 선택하세요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.references.forEach { reference ->
                    ReferenceCard(reference, reference == selectedReference, onOpen = { uriHandler.openUri(reference.url) }) {
                        selectedReference = reference
                    }
                }
            }
            if (selectedCandidate != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(ownedOnly, { ownedOnly = it }, enabled = !state.loading)
                    Text(if (ownedOnly) "보유 도료만 사용" else "미보유 도료 포함")
                }
                if (state.references.isNotEmpty()) {
                    Button(
                        onClick = {
                            val candidate = selectedCandidate ?: return@Button
                            val reference = selectedReference ?: return@Button
                            if (imageUris.isEmpty()) return@Button
                            viewModel.analyzeOriginalColors(project, imageUris.toList(), candidate, reference, ownedOnly)
                        },
                        enabled = selectedReference != null && !state.loading,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("이 자료를 기준으로 분석") }
                }
                OutlinedButton(
                    onClick = {
                        val candidate = selectedCandidate ?: return@OutlinedButton
                        if (imageUris.isEmpty()) return@OutlinedButton
                        selectedReference = null
                        viewModel.analyzePhotoColors(project, imageUris.toList(), candidate, ownedOnly)
                    },
                    enabled = imageUris.isNotEmpty() && !state.loading,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (state.photoAnalysisTimedOut) "다시 시도" else "참고자료 없이 사진만 분석") }
                if (state.photoAnalysisTimedOut) {
                    OutlinedButton(
                        onClick = { showModelChooser = true },
                        enabled = !state.loading,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("AI 모델 변경 (Terra/Sol 권장)") }
                }
                Text(
                    "공식 원작 색상이 아닌 촬영 사진의 색상을 기준으로 분석합니다. 조명·카메라 보정·배경색에 따라 실제 색상과 차이가 날 수 있습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            state.plan?.let { plan ->
                val photoOnly = plan.reference.referenceType == USER_PHOTO_ONLY
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text("프로젝트 컬러 플랜", style = MaterialTheme.typography.titleLarge)
                Text("${plan.subjectName} · ${plan.workTitle} · ${plan.versionName}", fontWeight = FontWeight.SemiBold)
                Text(
                    if (photoOnly) "사용자 사진 기준 분석 · ${plan.parts.size}색" else "${if (plan.reference.official) "공식" else "공식 확인 안 됨"} · ${plan.reference.referenceType} · ${plan.parts.size}색 분석",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (photoOnly) {
                    Text(
                        "공식 원작 색상이 아닌 촬영 사진 기준 결과입니다. 조명, 카메라 보정, 배경색에 따라 실제 색상과 차이가 날 수 있습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf(3.0, 5.0, 10.0, 15.0, 20.0).forEach { amount ->
                        OutlinedButton(onClick = { totalMl = amount }, modifier = Modifier.weight(1f)) {
                            Text("${amount.toInt()}ml", color = if (totalMl == amount) MaterialTheme.colorScheme.primary else Color.Unspecified)
                        }
                    }
                }
                plan.parts.forEach { part ->
                    OriginalColorPartCard(part, totalMl, recipes, project.id, plan, viewModel)
                }
                Text(if (photoOnly) "사진 기준 예상 조색" else "원작 참고 기반 예상 색상", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(plan.disclaimer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(
                    onClick = { viewModel.saveOriginalColorPlan(project.id, plan) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("컬러 플랜 저장") }
            }
            state.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxSize().padding(12.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(Modifier.fillMaxSize().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("AI 원작 컬러 매칭", style = MaterialTheme.typography.headlineSmall)
                        Text(project.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { showModelChooser = true }, enabled = !state.loading) {
                        Text("AI 모델 · ${modelShortLabel(aiModelMode)}")
                    }
                    TextButton(onClick = onDismiss) { Text("닫기") }
                }
                BoxWithConstraints(Modifier.fillMaxSize().padding(top = 8.dp)) {
                    if (maxWidth >= 700.dp) {
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(Modifier.weight(.38f).fillMaxHeight().verticalScroll(rememberScrollState())) { photoPane(5) }
                            Column(Modifier.weight(.62f).fillMaxHeight().verticalScroll(rememberScrollState())) { workflowPane() }
                        }
                    } else {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            photoPane(3)
                            Spacer(Modifier.height(18.dp))
                            workflowPane()
                        }
                    }
                }
            }
        }
    }
    if (showModelChooser) {
        AlertDialog(
            onDismissRequest = { showModelChooser = false },
            title = { Text("AI 모델 변경") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AiModelMode.entries.forEach { option ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                viewModel.saveAiModelMode(option)
                                showModelChooser = false
                            }.padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = aiModelMode == option,
                                onClick = {
                                    viewModel.saveAiModelMode(option)
                                    showModelChooser = false
                                },
                            )
                            Column(Modifier.padding(start = 8.dp)) {
                                Text(modelShortLabel(option), style = MaterialTheme.typography.titleMedium)
                                Text(option.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showModelChooser = false }) { Text("취소") } },
        )
    }
}

private fun modelShortLabel(mode: AiModelMode): String = when (mode) {
    AiModelMode.AUTO -> "자동"
    AiModelMode.LUNA -> "Luna"
    AiModelMode.TERRA -> "Terra"
    AiModelMode.SOL -> "Sol"
}

@Composable
private fun CandidateCard(candidate: AiSubjectCandidate, selected: Boolean, onSelect: () -> Unit) {
    OutlinedCard(
        Modifier.fillMaxWidth().clickable(onClick = onSelect),
        colors = CardDefaults.outlinedCardColors(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row {
                Text(candidate.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(confidenceLabel(candidate.confidence), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(listOf(candidate.workTitle, candidate.versionName).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            if (candidate.note.isNotBlank()) Text(candidate.note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ReferenceCard(reference: AiOfficialReference, selected: Boolean, onOpen: () -> Unit, onSelect: () -> Unit) {
    OutlinedCard(
        Modifier.fillMaxWidth().clickable(onClick = onSelect),
        colors = CardDefaults.outlinedCardColors(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(reference.title.ifBlank { reference.sourceName }, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(if (reference.official) "공식" else "공식 확인 안 됨", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Text("${reference.referenceType} · ${reference.sourceName}", style = MaterialTheme.typography.bodySmall)
            if (reference.note.isNotBlank()) Text(reference.note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onOpen, enabled = reference.url.isNotBlank()) { Text("원본 웹페이지 확인") }
        }
    }
}

@Composable
private fun OriginalColorPartCard(
    part: AiOriginalColorPart,
    totalMl: Double,
    recipes: List<RecipeCardRow>,
    projectId: Long,
    plan: com.aifigurepaint.app.ai.AiOriginalColorPlanDraft,
    viewModel: AppViewModel,
) {
    val closest = remember(recipes, part.targetHex) { recipes.minByOrNull { colorDistance(it.resultColorValue, parseHexColor(part.targetHex, 0xFF808080.toInt())) } }
        ?.takeIf { colorDistance(it.resultColorValue, parseHexColor(part.targetHex, 0xFF808080.toInt())) < 70.0 }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorSwatch(parseHexColor(part.targetHex, 0xFF808080.toInt()), 46.dp)
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(part.partName, style = MaterialTheme.typography.titleMedium)
                    Text("${part.category} · ${part.targetHex} · ${part.rgb}", style = MaterialTheme.typography.labelSmall)
                    Text("${part.colorFamily} · ${part.characteristics}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (part.nearestPaintId != null) {
                Text(
                    "가장 가까운 보유 단색 · ${listOfNotNull(part.nearestPaintCode, part.nearestPaintName).joinToString(" ")}${if (part.singleColorUsable) " · 단색 사용 가능" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            closest?.let { Text("기존 레시피 있음 · ${it.name}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary) }
            val options = if (part.mixOptions.isNotEmpty()) part.mixOptions else part.nearestPaintId?.let {
                listOf(
                    AiOriginalMixOption(
                        "단색 후보",
                        "가장 가까운 단색 도료 후보입니다.",
                        listOf(com.aifigurepaint.app.ai.AiMixComponent(it, part.nearestPaintName, part.nearestPaintCode, 100.0)),
                    ),
                )
            }.orEmpty()
            options.forEach { option ->
                Column(Modifier.fillMaxWidth().padding(top = 3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(option.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    option.components.forEach { component ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(listOfNotNull(component.productCode, component.paintName).joinToString(" "), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Text("${formatMl(component.percent)}% · ${formatMl(component.percent / 100.0 * totalMl)}ml", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Text(option.explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.saveAiSuggestion(partSuggestion(plan, part, option), projectId = projectId) },
                            modifier = Modifier.weight(1f),
                        ) { Text("새 레시피 저장") }
                        if (closest != null) {
                            TextButton(
                                onClick = { viewModel.saveAiSuggestion(partSuggestion(plan, part, option), projectId = projectId, recipeId = closest.id) },
                                modifier = Modifier.weight(1f),
                            ) { Text("기존 레시피 새 버전") }
                        }
                    }
                }
            }
        }
    }
}

private fun partSuggestion(
    plan: com.aifigurepaint.app.ai.AiOriginalColorPlanDraft,
    part: AiOriginalColorPart,
    option: AiOriginalMixOption,
) = AiMixSuggestion(
    name = "${plan.subjectName} ${part.partName}",
    targetHex = part.targetHex,
    components = option.components,
    explanation = "${option.explanation}\n${if (plan.reference.referenceType == USER_PHOTO_ONLY) "사용자 사진 기준 예상 조색" else "원작 참고 기반 예상 색상"}",
    source = "${plan.reference.sourceName} · ${plan.reference.referenceType}",
    originalPrompt = "${if (plan.reference.referenceType == USER_PHOTO_ONLY) "AI 사진 기준 도료 분석" else "AI 원작 컬러 매칭"}: ${plan.subjectName} / ${plan.versionName} / ${part.partName}",
)

@Composable
private fun StoredOriginalPlanCard(plan: OriginalColorPlanEntity) {
    var expanded by remember(plan.id) { mutableStateOf(false) }
    val parts = remember(plan.partsJson) { storedPartSummaries(plan.partsJson) }
    OutlinedCard(Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
        Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row {
                Text(plan.identifiedName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("${parts.size}색", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                if (plan.referenceType == USER_PHOTO_ONLY) "사용자 사진 기준 분석" else "${plan.referenceType} · ${if (plan.official) "공식" else "공식 확인 안 됨"}",
                style = MaterialTheme.typography.labelSmall,
            )
            if (expanded) parts.forEach { Text("• ${it.first} ${it.second}", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

private fun storedPartSummaries(json: String): List<Pair<String, String>> = runCatching {
    val rows = JSONArray(json)
    buildList {
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            add(row.optString("partName") to row.optString("targetHex"))
        }
    }
}.getOrDefault(emptyList())

private fun confidenceLabel(value: String): String = when (value) {
    "HIGH" -> "신뢰도 높음"
    "MEDIUM" -> "신뢰도 중간"
    "USER" -> "사용자 확인"
    else -> "신뢰도 낮음"
}

private fun colorDistance(first: Int, second: Int): Double {
    val red = android.graphics.Color.red(first) - android.graphics.Color.red(second)
    val green = android.graphics.Color.green(first) - android.graphics.Color.green(second)
    val blue = android.graphics.Color.blue(first) - android.graphics.Color.blue(second)
    return sqrt(red.toDouble().pow(2) + green.toDouble().pow(2) + blue.toDouble().pow(2))
}

private fun createOriginalMatchUri(context: Context): Uri {
    val directory = File(context.cacheDir, "original_color_match").apply { mkdirs() }
    val image = File(directory, "original_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", image)
}

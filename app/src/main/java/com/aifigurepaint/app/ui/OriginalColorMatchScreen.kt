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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.aifigurepaint.app.AppViewModel
import com.aifigurepaint.app.ai.AiMixComponent
import com.aifigurepaint.app.ai.AiMixSuggestion
import com.aifigurepaint.app.ai.AiModelMode
import com.aifigurepaint.app.ai.AiOriginalColorPart
import com.aifigurepaint.app.ai.AiOriginalMixOption
import com.aifigurepaint.app.data.OriginalColorPlanEntity
import com.aifigurepaint.app.data.PaintEntity
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
    val state by viewModel.originalColorMatchState.collectAsState()
    val aiModelMode by viewModel.aiModelMode.collectAsState()
    val projectPhotos by viewModel.photos(PhotoOwner.PROJECT, project.id).collectAsState(initial = emptyList())
    val savedPlans by viewModel.originalColorPlans(project.id).collectAsState(initial = emptyList())
    val paints by viewModel.paints.collectAsState()
    val imageUris = remember(project.id) { mutableStateListOf<String>() }
    var selectedPreviewUri by remember(project.id) { mutableStateOf<String?>(null) }
    var initialPhotoLoaded by remember(project.id) { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var pendingReplaceIndex by remember { mutableStateOf<Int?>(null) }
    var photoNotice by remember { mutableStateOf<String?>(null) }
    var ownedOnly by remember { mutableStateOf(true) }
    var totalMl by remember { mutableStateOf(10.0) }
    var showModelChooser by remember { mutableStateOf(false) }
    var pendingDeletePlan by remember { mutableStateOf<OriginalColorPlanEntity?>(null) }

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
            resetAnalysis()
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
                    OutlinedButton(onClick = { takePhoto(selectedIndex) }, enabled = !state.loading, modifier = Modifier.weight(1f)) {
                        Text("선택 사진 재촬영")
                    }
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
                OutlinedButton(
                    onClick = { takePhoto() },
                    enabled = imageUris.size < 5 && !state.loading,
                    modifier = Modifier.weight(1f),
                ) { Text("카메라 추가") }
                OutlinedButton(
                    onClick = { addPicker.launch(arrayOf("image/*")) },
                    enabled = imageUris.size < 5 && !state.loading,
                    modifier = Modifier.weight(1f),
                ) { Text("갤러리 추가") }
            }
            if (imageUris.size >= 5) {
                Text("최대 5장까지 사용할 수 있습니다.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            if (projectPhotos.isNotEmpty()) {
                Text("프로젝트 사진 사용", style = MaterialTheme.typography.titleSmall)
                projectPhotos.take(5).chunked(columns).forEach { photos ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        photos.forEach { photo ->
                            OutlinedCard(
                                modifier = Modifier.weight(1f).clickable(enabled = imageUris.size < 5 && photo.uri !in imageUris && !state.loading) {
                                    addProjectPhoto(photo.uri)
                                },
                            ) { PhotoPreview(photo.uri, Modifier.fillMaxWidth().height(72.dp)) }
                        }
                        repeat(columns - photos.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Text(
                "정면·후면·측면·얼굴/헤드·특징적인 장비를 여러 각도에서 촬영하면 부위와 색상 구분이 정확해집니다. 1장만으로도 분석할 수 있습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(ownedOnly, { ownedOnly = it }, enabled = !state.loading)
                Text(if (ownedOnly) "보유 도료만 사용" else "미보유 도료 포함")
            }
            Button(
                onClick = { viewModel.analyzePhotoColors(project, imageUris.toList(), ownedOnly) },
                enabled = imageUris.isNotEmpty() && !state.loading,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (state.photoAnalysisTimedOut) "AI 사진 조색 다시 분석" else "AI 사진 조색 분석") }
            Text(
                "등록된 사진 ${imageUris.size}장을 한 번의 AI 요청으로 분석하고, 가까운 도료 후보만 AI에 전달합니다.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "사진의 색상은 촬영 조명, 카메라 화이트밸런스, 화면 보정에 따라 실제 색과 차이가 날 수 있습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.activeModelLabel?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
            photoNotice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            if (state.photoAnalysisTimedOut) {
                OutlinedButton(
                    onClick = { showModelChooser = true },
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("AI 모델 변경") }
            }
        }
    }

    val workflowPane: @Composable (Boolean) -> Unit = { wideLayout ->
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(9.dp))
                    Text("사진 ${imageUris.size}장을 한 번에 분석해 부위별 색상과 조색을 추천 중입니다.")
                }
            }
            state.plan?.let { plan ->
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text("사진 조색 결과", style = MaterialTheme.typography.titleLarge)
                Text("${plan.subjectName} · ${plan.parts.size}색", fontWeight = FontWeight.SemiBold)
                Text("사진 기준 예상 조색", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    "공식 원작 색상이 아닌 촬영 사진 기준 결과입니다. 조명, 카메라 보정, 배경색에 따라 실제 색상과 차이가 날 수 있습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf(3.0, 5.0, 10.0, 15.0, 20.0).forEach { amount ->
                        OutlinedButton(onClick = { totalMl = amount }, modifier = Modifier.weight(1f)) {
                            Text(
                                "${amount.toInt()} ml",
                                color = if (totalMl == amount) MaterialTheme.colorScheme.primary else Color.Unspecified,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
                if (wideLayout) {
                    plan.parts.chunked(2).forEach { rowParts ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            rowParts.forEach { part ->
                                OriginalColorPartCard(
                                    part = part,
                                    totalMl = totalMl,
                                    recipes = recipes,
                                    projectId = project.id,
                                    plan = plan,
                                    viewModel = viewModel,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (rowParts.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                } else {
                    plan.parts.forEach { part ->
                        OriginalColorPartCard(part, totalMl, recipes, project.id, plan, viewModel)
                    }
                }
                Text(plan.disclaimer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(
                    onClick = { viewModel.saveOriginalColorPlan(project.id, plan) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("사진 조색 플랜 저장") }
            }
            state.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
    }

    val savedPlansPane: @Composable (Boolean) -> Unit = { wideLayout ->
        if (savedPlans.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text("저장된 사진 조색", style = MaterialTheme.typography.titleLarge)
                savedPlans.forEach { plan ->
                    StoredOriginalPlanCard(
                        plan = plan,
                        paints = paints,
                        totalMl = totalMl,
                        wideLayout = wideLayout,
                        onTotalMlChange = { totalMl = it },
                        onDelete = { pendingDeletePlan = plan },
                    )
                }
            }
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
                        Text("AI 사진 조색", style = MaterialTheme.typography.headlineSmall)
                        Text("사진의 색상을 분석해 보유 도료로 조색 레시피를 추천합니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { showModelChooser = true }, enabled = !state.loading) {
                        Text("AI 모델 · ${modelShortLabel(aiModelMode)}")
                    }
                    TextButton(onClick = onDismiss) { Text("닫기") }
                }
                BoxWithConstraints(Modifier.fillMaxSize().padding(top = 8.dp)) {
                    if (maxWidth >= 700.dp) {
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Column(Modifier.weight(.42f)) { photoPane(5) }
                                OutlinedCard(Modifier.weight(.58f)) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("사진 조색 안내", style = MaterialTheme.typography.titleLarge)
                                        Text(
                                            "왼쪽에서 사진 1~5장을 선택한 뒤 분석하세요. 조색 결과와 저장된 결과는 아래에서 좌우 2열로 표시됩니다.",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            if (state.plan != null) "분석 완료 · ${state.plan?.parts?.size ?: 0}색"
                                            else if (state.loading) "등록 사진 ${imageUris.size}장 분석 중"
                                            else "등록 사진 ${imageUris.size}장 · 저장 플랜 ${savedPlans.size}개",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                            workflowPane(true)
                            savedPlansPane(true)
                        }
                    } else {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            photoPane(3)
                            Spacer(Modifier.height(18.dp))
                            workflowPane(false)
                            Spacer(Modifier.height(18.dp))
                            savedPlansPane(false)
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

    pendingDeletePlan?.let { plan ->
        AlertDialog(
            onDismissRequest = { pendingDeletePlan = null },
            title = { Text("저장된 사진 조색 삭제") },
            text = {
                Text("${plan.identifiedName} 컬러 플랜을 삭제할까요?\n\n저장된 컬러 플랜만 삭제되며 도료 DB와 이미 저장한 조색 레시피는 유지됩니다.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteOriginalColorPlan(plan)
                        pendingDeletePlan = null
                    },
                ) { Text("삭제", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDeletePlan = null }) { Text("취소") } },
        )
    }
}

private fun modelShortLabel(mode: AiModelMode): String = when (mode) {
    AiModelMode.AUTO -> "자동"
    AiModelMode.LUNA -> "Luna"
    AiModelMode.TERRA -> "Terra"
}

@Composable
private fun OriginalColorPartCard(
    part: AiOriginalColorPart,
    totalMl: Double,
    recipes: List<RecipeCardRow>,
    projectId: Long,
    plan: com.aifigurepaint.app.ai.AiOriginalColorPlanDraft,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier,
) {
    val closest = remember(recipes, part.targetHex) { recipes.minByOrNull { colorDistance(it.resultColorValue, parseHexColor(part.targetHex, 0xFF808080.toInt())) } }
        ?.takeIf { colorDistance(it.resultColorValue, parseHexColor(part.targetHex, 0xFF808080.toInt())) < 70.0 }
    OutlinedCard(modifier.fillMaxWidth()) {
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
                            Text("${formatMl(component.percent)}% · ${formatMl(component.percent / 100.0 * totalMl)} ml", style = MaterialTheme.typography.labelMedium)
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
    explanation = "${option.explanation}\n사진 기준 예상 조색",
    source = "${plan.reference.sourceName} · ${plan.reference.referenceType}",
    originalPrompt = "AI 사진 조색: ${plan.subjectName} / ${part.partName}",
)

@Composable
private fun StoredOriginalPlanCard(
    plan: OriginalColorPlanEntity,
    paints: List<PaintEntity>,
    totalMl: Double,
    wideLayout: Boolean,
    onTotalMlChange: (Double) -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember(plan.id) { mutableStateOf(false) }
    val parts = remember(plan.partsJson) { storedOriginalParts(plan.partsJson) }
    val paintsById = remember(paints) { paints.associateBy { it.id } }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier.weight(1f).clickable { expanded = !expanded },
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(plan.identifiedName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (plan.referenceType == USER_PHOTO_ONLY) "사용자 사진 기준 분석" else "기존 저장 컬러 플랜",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("${parts.size}색", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = onDelete) { Text("삭제", color = MaterialTheme.colorScheme.error) }
            }
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                Text(if (expanded) "상세 조색 접기" else "색상칩과 상세 조색 보기")
            }
            if (expanded) {
                HorizontalDivider()
                Text("조색 총량", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf(3.0, 5.0, 10.0, 15.0, 20.0).forEach { amount ->
                        FilterChip(
                            selected = totalMl == amount,
                            onClick = { onTotalMlChange(amount) },
                            label = { Text("${amount.toInt()} ml", maxLines = 1, softWrap = false) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (wideLayout) {
                    parts.chunked(2).forEach { rowParts ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            rowParts.forEach { part ->
                                StoredOriginalPartDetail(
                                    part = part,
                                    paintsById = paintsById,
                                    totalMl = totalMl,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (rowParts.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                } else {
                    parts.forEach { part ->
                        StoredOriginalPartDetail(part, paintsById, totalMl)
                    }
                }
            }
        }
    }
}

@Composable
private fun StoredOriginalPartDetail(
    part: AiOriginalColorPart,
    paintsById: Map<Long, PaintEntity>,
    totalMl: Double,
    modifier: Modifier = Modifier,
) {
    val recommended = part.mixOptions.firstOrNull() ?: part.nearestPaintId?.let { paintId ->
        AiOriginalMixOption(
            label = "가장 가까운 보유 단색",
            explanation = "저장 당시 선택된 가장 가까운 보유 도료입니다.",
            components = listOf(AiMixComponent(paintId, part.nearestPaintName, part.nearestPaintCode, 100.0)),
        )
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .32f)),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorSwatch(parseHexColor(part.targetHex, 0xFF808080.toInt()), 52.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(part.partName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        listOf(part.colorFamily, part.characteristics).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("${formatMl(totalMl)} ml", style = MaterialTheme.typography.titleMedium)
            }
            if (recommended == null || recommended.components.isEmpty()) {
                Text("저장된 조색 구성이 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(recommended.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                recommended.components.forEach { component ->
                    val paint = paintsById[component.paintId]
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        ColorSwatch(paint?.colorValue ?: 0xFFB0B0B0.toInt(), 40.dp)
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                listOfNotNull(component.productCode?.takeIf { it.isNotBlank() }, component.paintName.takeIf { it.isNotBlank() })
                                    .joinToString(" ")
                                    .ifBlank { "도료 정보 없음" },
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                paint?.brand?.takeIf { it.isNotBlank() } ?: "저장 당시 도료",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            "${formatMl(component.percent / 100.0 * totalMl)} ml",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .14f))
                }
                if (recommended.explanation.isNotBlank()) {
                    Text(recommended.explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun storedOriginalParts(json: String): List<AiOriginalColorPart> = runCatching {
    val rows = JSONArray(json)
    buildList {
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val optionsJson = row.optJSONArray("mixOptions") ?: JSONArray()
            val options = buildList {
                for (optionIndex in 0 until optionsJson.length()) {
                    val option = optionsJson.optJSONObject(optionIndex) ?: continue
                    val componentsJson = option.optJSONArray("components") ?: JSONArray()
                    val components = buildList {
                        for (componentIndex in 0 until componentsJson.length()) {
                            val component = componentsJson.optJSONObject(componentIndex) ?: continue
                            add(
                                AiMixComponent(
                                    paintId = component.optLong("paintId", -1),
                                    paintName = component.optString("paintName"),
                                    productCode = component.optString("productCode").takeIf { it.isNotBlank() },
                                    percent = component.optDouble("percent", 0.0),
                                ),
                            )
                        }
                    }
                    add(
                        AiOriginalMixOption(
                            label = option.optString("label").ifBlank { "추천 조색" },
                            explanation = option.optString("explanation"),
                            components = components,
                        ),
                    )
                }
            }
            add(
                AiOriginalColorPart(
                    category = row.optString("category"),
                    partName = row.optString("partName").ifBlank { "도색 부위" },
                    targetHex = row.optString("targetHex").ifBlank { "#808080" },
                    rgb = row.optString("rgb"),
                    colorFamily = row.optString("colorFamily"),
                    characteristics = row.optString("characteristics"),
                    nearestPaintId = row.optLong("nearestPaintId", -1).takeIf { it > 0 },
                    nearestPaintName = row.optString("nearestPaintName"),
                    nearestPaintCode = row.optString("nearestPaintCode").takeIf { it.isNotBlank() },
                    singleColorUsable = row.optBoolean("singleColorUsable"),
                    mixOptions = options,
                ),
            )
        }
    }
}.getOrDefault(emptyList())

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

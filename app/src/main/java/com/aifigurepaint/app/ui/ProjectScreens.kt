package com.aifigurepaint.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aifigurepaint.app.AppViewModel
import com.aifigurepaint.app.data.PhotoOwner
import com.aifigurepaint.app.data.ProjectEntity
import com.aifigurepaint.app.data.ProjectStage
import com.aifigurepaint.app.data.ProjectStatus
import com.aifigurepaint.app.data.RecipeCardRow

@Composable
internal fun ProjectListScreen(
    projects: List<ProjectEntity>,
    onAdd: () -> Unit,
    onScan: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("프로젝트", style = MaterialTheme.typography.headlineMedium)
                Text("도색 작업 ${projects.size}개", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = onScan, modifier = Modifier.height(40.dp), contentPadding = PaddingValues(horizontal = 10.dp)) { Text("◎ AI 촬영") }
            Spacer(Modifier.size(7.dp))
            Button(onClick = onAdd, modifier = Modifier.height(40.dp), contentPadding = PaddingValues(horizontal = 10.dp)) { Text("＋ 추가") }
        }
        if (projects.isEmpty()) {
            EmptyCard("Sazabi 같은 도색 프로젝트를 추가해보세요.")
        } else {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                if (maxWidth >= 700.dp) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) { gridItems(projects, key = { it.id }) { ProjectCard(it, onOpen) } }
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        items(projects, key = { it.id }) { ProjectCard(it, onOpen) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectCard(project: ProjectEntity, onOpen: (Long) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onOpen(project.id) },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PhotoPreview(project.photoUri, Modifier.size(68.dp))
            Spacer(Modifier.size(11.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(project.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(ProjectStatus.label(project.status), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Text(project.modelName.ifBlank { "모델명 미입력" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Text("시작 ${formatDate(project.startDate)} · 수정 ${formatDateTime(project.updatedAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (project.memo.isNotBlank()) Text(project.memo, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun ProjectEditorScreen(
    project: ProjectEntity?,
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
) {
    val context = LocalContext.current
    val storedPhotos = if (project != null) viewModel.photos(PhotoOwner.PROJECT, project.id).collectAsState(initial = emptyList()).value else emptyList()
    var name by remember(project?.id) { mutableStateOf(project?.name.orEmpty()) }
    var modelName by remember(project?.id) { mutableStateOf(project?.modelName.orEmpty()) }
    var memo by remember(project?.id) { mutableStateOf(project?.memo.orEmpty()) }
    var startDate by remember(project?.id) { mutableStateOf(dateToInput(project?.startDate ?: 0)) }
    var status by remember(project?.id) { mutableStateOf(project?.status ?: ProjectStatus.PLANNED) }
    var deleteConfirm by remember { mutableStateOf(false) }
    var photosLoaded by remember(project?.id) { mutableStateOf(project == null) }
    val photoUris = remember(project?.id) { mutableStateListOf<String>() }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && photoUris.size < 3) {
            copyPhotoToAppStorage(context, uri)?.let { stored ->
                if (stored !in photoUris) photoUris += stored
            }
        }
    }

    LaunchedEffect(storedPhotos, project?.id) {
        if (project != null && !photosLoaded) {
            val loaded = storedPhotos.map { it.uri }.ifEmpty { listOfNotNull(project.photoUri) }
            if (loaded.isNotEmpty()) {
                photoUris.clear()
                photoUris.addAll(loaded.take(3))
                photosLoaded = true
            }
        }
    }

    Scaffold(topBar = { EditorHeader(if (project == null) "프로젝트 추가" else "프로젝트 수정", onBack) }) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val wide = maxWidth >= 700.dp
            val form: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("프로젝트 이름") }, singleLine = true)
                    OutlinedTextField(modelName, { modelName = it }, Modifier.fillMaxWidth(), label = { Text("모델명") }, singleLine = true)
                    OutlinedTextField(startDate, { startDate = it }, Modifier.fillMaxWidth(), label = { Text("시작일 (YYYY-MM-DD)") }, singleLine = true)
                    SectionTitle("작업 상태")
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        ProjectStatus.entries.forEach { item ->
                            FilterChip(selected = status == item, onClick = { status = item }, label = { Text(ProjectStatus.label(item)) })
                        }
                    }
                    OutlinedTextField(memo, { memo = it }, Modifier.fillMaxWidth().height(130.dp), label = { Text("작업 메모") })
                }
            }
            val media: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle("프로젝트 사진", "갤러리에서 최대 3장")
                    PhotoStrip(photoUris, itemSize = if (wide) 142.dp else 102.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { photoPicker.launch(arrayOf("image/*")) },
                            enabled = photoUris.size < 3,
                            modifier = Modifier.weight(1f),
                        ) { Text(if (photoUris.isEmpty()) "사진 선택" else "사진 추가 ${photoUris.size}/3") }
                        if (photoUris.isNotEmpty()) OutlinedButton(onClick = { photoUris.removeLast() }) { Text("마지막 삭제") }
                    }
                    Button(
                        onClick = {
                            viewModel.saveProject(
                                project = ProjectEntity(
                                    id = project?.id ?: 0,
                                    name = name.trim(),
                                    modelName = modelName.trim(),
                                    memo = memo.trim(),
                                    startDate = parseDateInput(startDate),
                                    status = status,
                                    photoUri = photoUris.firstOrNull(),
                                    createdAt = project?.createdAt ?: System.currentTimeMillis(),
                                ),
                                photoUris = photoUris.toList(),
                                onSaved = onSaved,
                            )
                        },
                        enabled = name.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                    ) { Text("프로젝트 저장") }
                    if (project != null) {
                        OutlinedButton(onClick = { deleteConfirm = true }, modifier = Modifier.fillMaxWidth().height(42.dp)) {
                            Text("프로젝트 삭제", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            if (wide) {
                Row(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { form() }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { media() }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) { form(); media(); Spacer(Modifier.height(16.dp)) }
            }
        }
    }
    if (deleteConfirm && project != null) {
        AlertDialog(
            onDismissRequest = { deleteConfirm = false },
            title = { Text("프로젝트 삭제") },
            text = { Text("${project.name} 프로젝트를 삭제할까요? 연결된 조색 레시피 자체는 유지됩니다.") },
            confirmButton = { TextButton(onClick = { viewModel.deleteProject(project, onBack) }) { Text("삭제", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteConfirm = false }) { Text("취소") } },
        )
    }
}

@Composable
internal fun ProjectDetailScreen(
    project: ProjectEntity,
    allRecipes: List<RecipeCardRow>,
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenRecipe: (Long) -> Unit,
    onNewRecipe: () -> Unit,
) {
    val linkedRecipes by viewModel.projectRecipes(project.id).collectAsState(initial = emptyList())
    val linkedIds by viewModel.projectRecipeIds(project.id).collectAsState(initial = emptyList())
    val usedPaints by viewModel.projectPaints(project.id).collectAsState(initial = emptyList())
    val storedPhotos by viewModel.photos(PhotoOwner.PROJECT, project.id).collectAsState(initial = emptyList())
    val timeline by viewModel.projectTimeline(project.id).collectAsState(initial = emptyList())
    val aiState by viewModel.aiState.collectAsState()
    val photoUris = storedPhotos.map { it.uri }.ifEmpty { listOfNotNull(project.photoUri) }
    var showLinks by remember { mutableStateOf(false) }
    var showTimelineEntry by remember { mutableStateOf(false) }
    var aiQuestion by remember(project.id) { mutableStateOf("") }

    Scaffold(topBar = { EditorHeader("프로젝트 상세", onBack) }) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val wide = maxWidth >= 700.dp
            val summary: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(project.name, style = MaterialTheme.typography.headlineMedium)
                            Text(project.modelName.ifBlank { "모델명 미입력" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(ProjectStatus.label(project.status), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                    }
                    Text("시작 ${formatDate(project.startDate)} · 마지막 수정 ${formatDateTime(project.updatedAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PhotoStrip(photoUris, itemSize = if (wide) 146.dp else 104.dp)
                    if (project.memo.isNotBlank()) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f))) {
                            Text(project.memo, Modifier.fillMaxWidth().padding(12.dp))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onEdit, modifier = Modifier.weight(1f).height(42.dp)) { Text("정보 수정") }
                        OutlinedButton(onClick = onNewRecipe, modifier = Modifier.weight(1f).height(42.dp)) { Text("＋ 새 레시피") }
                    }
                }
            }
            val workData: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("연결된 레시피", style = MaterialTheme.typography.titleLarge)
                            Text("중복 저장 없이 관계로 연결", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { showLinks = true }) { Text("연결 관리") }
                    }
                    if (linkedRecipes.isEmpty()) EmptyCard("연결된 조색 레시피가 없습니다.")
                    linkedRecipes.forEach { RecipeCard(it, onOpenRecipe) }
                    Spacer(Modifier.height(5.dp))
                    SectionTitle("사용 도료", "연결 레시피 기준 ${usedPaints.size}종")
                    if (usedPaints.isEmpty()) EmptyCard("레시피를 연결하면 사용 도료가 표시됩니다.")
                    usedPaints.forEach { paint ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            ColorSwatch(paint.colorValue, 36.dp)
                            Spacer(Modifier.size(9.dp))
                            Column(Modifier.weight(1f)) {
                                Text(listOfNotNull(paint.productCode, paint.name).joinToString(" "), style = MaterialTheme.typography.titleMedium)
                                Text(paint.brand, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    SectionTitle("작업 Timeline", "${timeline.size}개 기록")
                    Button(onClick = { showTimelineEntry = true }, modifier = Modifier.fillMaxWidth().height(40.dp)) { Text("＋ 작업 기록") }
                    if (timeline.isEmpty()) EmptyCard("현재 작업 단계를 사진과 메모로 기록해보세요.")
                    timeline.take(8).forEach { entry ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f))) {
                            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                Row {
                                    Text(ProjectStage.label(entry.stage), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                                    Text(formatDate(entry.date), style = MaterialTheme.typography.labelSmall)
                                }
                                if (entry.memo.isNotBlank()) Text(entry.memo, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    SectionTitle("AI Project Assistant")
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf("진행 요약", "다음 작업", "도색 상담").forEach { action ->
                            FilterChip(selected = false, onClick = { aiQuestion = action }, label = { Text(action) })
                        }
                    }
                    OutlinedTextField(aiQuestion, { aiQuestion = it }, Modifier.fillMaxWidth(), placeholder = { Text("무엇을 도와드릴까요?") })
                    Button(
                        onClick = {
                            val context = "프로젝트=${project.name}, 모델=${project.modelName}, 상태=${ProjectStatus.label(project.status)}, 메모=${project.memo}, 레시피=${linkedRecipes.joinToString { it.name }}, 최근기록=${timeline.take(5).joinToString { ProjectStage.label(it.stage) + ':' + it.memo }}"
                            viewModel.requestProjectAdvice(aiQuestion, context)
                        },
                        enabled = aiQuestion.isNotBlank() && !aiState.loading,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("AI에게 묻기") }
                    aiState.advice?.let { advice ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            aiState.activeModelLabel?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                            EmptyCard(advice)
                        }
                    }
                    aiState.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                }
            }
            if (wide) {
                Row(Modifier.fillMaxSize().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    Column(Modifier.weight(.85f).verticalScroll(rememberScrollState())) { summary() }
                    Column(Modifier.weight(1.15f).verticalScroll(rememberScrollState())) { workData() }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(17.dp),
                ) { summary(); workData(); Spacer(Modifier.height(16.dp)) }
            }
        }
    }
    if (showLinks) {
        RecipeLinkDialog(
            recipes = allRecipes,
            selectedIds = linkedIds,
            onDismiss = { showLinks = false },
            onSave = { ids -> viewModel.setProjectRecipes(project.id, ids); showLinks = false },
        )
    }
    if (showTimelineEntry) {
        TimelineEntryDialog(project.id, allRecipes, viewModel) { showTimelineEntry = false }
    }
}

@Composable
private fun RecipeLinkDialog(
    recipes: List<RecipeCardRow>,
    selectedIds: List<Long>,
    onDismiss: () -> Unit,
    onSave: (List<Long>) -> Unit,
) {
    val selected = remember { mutableStateListOf<Long>() }
    LaunchedEffect(selectedIds) {
        selected.clear()
        selected.addAll(selectedIds)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("레시피 연결") },
        text = {
            if (recipes.isEmpty()) Text("먼저 조색 레시피를 만들어주세요.") else LazyColumn(Modifier.height(360.dp)) {
                items(recipes, key = { it.id }) { recipe ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            if (recipe.id in selected) selected.remove(recipe.id) else selected.add(recipe.id)
                        }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = recipe.id in selected,
                            onCheckedChange = { checked -> if (checked) selected.add(recipe.id) else selected.remove(recipe.id) },
                        )
                        ColorSwatch(recipe.resultColorValue, 34.dp)
                        Spacer(Modifier.size(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(recipe.name, style = MaterialTheme.typography.titleMedium)
                            Text("도료 ${recipe.itemCount} · ${formatMl(recipe.baseTotalMl)}ml", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(selected.toList()) }) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

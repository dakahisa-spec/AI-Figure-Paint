package com.aifigurepaint.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aifigurepaint.app.AppViewModel
import com.aifigurepaint.app.data.ProjectEntity
import com.aifigurepaint.app.data.ProjectStatus
import com.aifigurepaint.app.data.RecipeCardRow

private enum class RootTab(val label: String, val symbol: String) {
    HOME("홈", "⌂"), PAINTS("도료", "●"), PROJECTS("프로젝트", "▣"), RECIPES("조색", "◒")
}

private sealed interface DetailPage {
    data class PaintEditor(val id: Long?) : DetailPage
    data object PaintScan : DetailPage
    data object ProjectScan : DetailPage
    data class ProjectEditor(val id: Long?) : DetailPage
    data class ProjectDetail(val id: Long) : DetailPage
    data class RecipeEditor(val id: Long?, val initialProjectId: Long? = null) : DetailPage
    data class RecipeDetail(val id: Long) : DetailPage
    data class AiMix(val targetHex: String? = null, val recipeId: Long? = null) : DetailPage
    data class PhotoAnalyzer(val recipeId: Long? = null) : DetailPage
}

@Composable
fun AiFigurePaintApp(viewModel: AppViewModel) {
    val paints by viewModel.paints.collectAsState()
    val projects by viewModel.projects.collectAsState()
    val recipes by viewModel.recipes.collectAsState()
    val recipeCards by viewModel.recipeCards.collectAsState()
    val message by viewModel.message.collectAsState()
    val aiConfigured by viewModel.aiConfigured.collectAsState()
    val aiModel by viewModel.aiModel.collectAsState()
    val snackbarHost = remember { SnackbarHostState() }
    var tab by remember { mutableStateOf(RootTab.HOME) }
    var detail by remember { mutableStateOf<DetailPage?>(null) }
    var showAiSettings by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHost.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = {
            if (detail == null) {
                NavigationBar(
                    modifier = Modifier.height(50.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    RootTab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { if (tab != item) viewModel.cancelAiRequest(); tab = item },
                            icon = { Text(item.symbol, fontSize = 15.sp) },
                            label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = StudioTeal,
                                selectedTextColor = StudioNavy,
                                indicatorColor = StudioMint,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        BoxWithConstraints(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(padding),
        ) {
            val expanded = maxWidth >= 700.dp
            when (val page = detail) {
                DetailPage.PaintScan -> PaintScanScreen(
                    viewModel = viewModel,
                    onBack = { viewModel.clearPaintScan(); detail = null },
                    onSettings = { showAiSettings = true },
                    onSaved = { tab = RootTab.PAINTS; detail = null },
                )
                DetailPage.ProjectScan -> ProjectScanScreen(
                    viewModel = viewModel,
                    onBack = { viewModel.clearProjectScan(); detail = null },
                    onSettings = { showAiSettings = true },
                    onSaved = { id -> tab = RootTab.PROJECTS; detail = DetailPage.ProjectDetail(id) },
                )
                is DetailPage.PaintEditor -> PaintEditorScreen(
                    paint = paints.firstOrNull { it.id == page.id },
                    viewModel = viewModel,
                    onBack = { detail = null },
                )
                is DetailPage.ProjectEditor -> ProjectEditorScreen(
                    project = projects.firstOrNull { it.id == page.id },
                    viewModel = viewModel,
                    onBack = {
                        detail = page.id?.let { DetailPage.ProjectDetail(it) }
                    },
                    onSaved = { detail = DetailPage.ProjectDetail(it) },
                )
                is DetailPage.ProjectDetail -> {
                    val project = projects.firstOrNull { it.id == page.id }
                    if (project == null) detail = null else ProjectDetailScreen(
                        project = project,
                        allRecipes = recipeCards,
                        viewModel = viewModel,
                        onBack = { detail = null },
                        onEdit = { detail = DetailPage.ProjectEditor(project.id) },
                        onOpenRecipe = { detail = DetailPage.RecipeDetail(it) },
                        onNewRecipe = { detail = DetailPage.RecipeEditor(null, project.id) },
                    )
                }
                is DetailPage.RecipeEditor -> RecipeEditorScreen(
                    recipe = recipes.firstOrNull { it.id == page.id },
                    initialProjectId = page.initialProjectId,
                    paints = paints,
                    projects = projects,
                    viewModel = viewModel,
                    onBack = { detail = null },
                    onSaved = { detail = DetailPage.RecipeDetail(it) },
                )
                is DetailPage.RecipeDetail -> {
                    val recipe = recipes.firstOrNull { it.id == page.id }
                    if (recipe == null) detail = null else RecipeDetailScreen(
                        recipe = recipe,
                        project = projects.firstOrNull { it.id == recipe.projectId },
                        viewModel = viewModel,
                        onBack = { detail = null },
                        onEdit = { detail = DetailPage.RecipeEditor(recipe.id) },
                        onAiAdjust = { detail = DetailPage.AiMix(recipeId = recipe.id) },
                    )
                }
                is DetailPage.AiMix -> AiMixScreen(
                    paints = paints,
                    projects = projects,
                    viewModel = viewModel,
                    targetHex = page.targetHex,
                    currentRecipe = recipes.firstOrNull { it.id == page.recipeId },
                    onBack = { viewModel.cancelAiRequest(); detail = page.recipeId?.let { DetailPage.RecipeDetail(it) } },
                    onSettings = { showAiSettings = true },
                    onPhotoAnalyze = { detail = DetailPage.PhotoAnalyzer(page.recipeId) },
                    onOpenSaved = { detail = DetailPage.RecipeDetail(it) },
                )
                is DetailPage.PhotoAnalyzer -> PhotoColorAnalyzerScreen(
                    onBack = { detail = DetailPage.AiMix(recipeId = page.recipeId) },
                    onUseColor = { detail = DetailPage.AiMix(it, page.recipeId) },
                )
                null -> when (tab) {
                    RootTab.HOME -> HomeScreen(
                        projectCount = projects.size,
                        paintCount = paints.count { it.owned },
                        projects = projects,
                        cards = recipeCards,
                        onNewMix = { detail = DetailPage.RecipeEditor(null) },
                        onAddPaint = { detail = DetailPage.PaintEditor(null) },
                        onScanPaint = { detail = DetailPage.PaintScan },
                        onNewProject = { detail = DetailPage.ProjectScan },
                        onOpenRecipe = { detail = DetailPage.RecipeDetail(it) },
                        onOpenProject = { detail = DetailPage.ProjectDetail(it) },
                        onAiMix = { detail = DetailPage.AiMix() },
                        onSettings = { showAiSettings = true },
                        onSearch = { tab = RootTab.PAINTS },
                    )
                    RootTab.PAINTS -> if (expanded) WidePaintStudio(
                        paints = paints,
                        viewModel = viewModel,
                        onAdd = { detail = DetailPage.PaintEditor(null) },
                        onScan = { detail = DetailPage.PaintScan },
                        onEdit = { detail = DetailPage.PaintEditor(it) },
                    ) else PaintListScreen(
                            paints = paints,
                            onAdd = { detail = DetailPage.PaintEditor(null) },
                            onScan = { detail = DetailPage.PaintScan },
                            onOpen = { detail = DetailPage.PaintEditor(it) },
                            onSetStock = viewModel::setPaintStock,
                            onToggleFavorite = viewModel::togglePaintFavorite,
                        )
                    RootTab.PROJECTS -> if (expanded) WideProjectStudio(
                        projects = projects,
                        cards = recipeCards,
                        viewModel = viewModel,
                        onAdd = { detail = DetailPage.ProjectEditor(null) },
                        onScan = { detail = DetailPage.ProjectScan },
                        onEdit = { detail = DetailPage.ProjectEditor(it) },
                        onOpenRecipe = { detail = DetailPage.RecipeDetail(it) },
                        onNewRecipe = { detail = DetailPage.RecipeEditor(null, it) },
                        onSettings = { showAiSettings = true },
                        onPhotoAnalyze = { detail = DetailPage.PhotoAnalyzer() },
                    ) else ProjectListScreen(
                            projects = projects,
                            onAdd = { detail = DetailPage.ProjectEditor(null) },
                            onScan = { detail = DetailPage.ProjectScan },
                            onOpen = { detail = DetailPage.ProjectDetail(it) },
                        )
                    RootTab.RECIPES -> if (expanded) WideRecipeStudio(
                        cards = recipeCards,
                        recipes = recipes,
                        paints = paints,
                        projects = projects,
                        viewModel = viewModel,
                        onNew = { detail = DetailPage.RecipeEditor(null) },
                        onEdit = { detail = DetailPage.RecipeEditor(it) },
                        onOpenSaved = { detail = DetailPage.RecipeDetail(it) },
                        onSettings = { showAiSettings = true },
                        onPhotoAnalyze = { detail = DetailPage.PhotoAnalyzer() },
                    ) else RecipeListScreen(
                            cards = recipeCards,
                            onAdd = { detail = DetailPage.RecipeEditor(null) },
                            onOpen = { detail = DetailPage.RecipeDetail(it) },
                            onAiMix = { detail = DetailPage.AiMix() },
                            onPhotoAnalyze = { detail = DetailPage.PhotoAnalyzer() },
                        )
                }
            }
        }
    }
    if (showAiSettings) {
        AiSettingsDialog(
            configured = aiConfigured,
            currentModel = aiModel,
            onDismiss = { showAiSettings = false },
            onSave = viewModel::saveAiSettings,
            onClear = viewModel::clearAiSettings,
        )
    }
}

@Composable
private fun HomeScreen(
    projectCount: Int,
    paintCount: Int,
    projects: List<ProjectEntity>,
    cards: List<RecipeCardRow>,
    onNewMix: () -> Unit,
    onAddPaint: () -> Unit,
    onScanPaint: () -> Unit,
    onNewProject: () -> Unit,
    onOpenRecipe: (Long) -> Unit,
    onOpenProject: (Long) -> Unit,
    onAiMix: () -> Unit,
    onSettings: () -> Unit,
    onSearch: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 700.dp
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = if (wide) 14.dp else 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                HomeHero(
                    paintCount = paintCount,
                    projectCount = projectCount,
                    activeProjectCount = projects.count { it.status == ProjectStatus.IN_PROGRESS },
                    recipeCount = cards.size,
                    onSearch = onSearch,
                    onSettings = onSettings,
                    onAiMix = onAiMix,
                )
            }
            item {
                if (wide) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AiPaintRegistrationCard(onScanPaint, Modifier.weight(1.08f))
                        QuickActionsCard(onNewMix, onAddPaint, onNewProject, Modifier.weight(.92f))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        AiPaintRegistrationCard(onScanPaint)
                        QuickActionsCard(onNewMix, onAddPaint, onNewProject)
                    }
                }
            }
            item {
                if (wide) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            RecentProjects(projects.filter { it.status == ProjectStatus.IN_PROGRESS }.ifEmpty { projects }, onOpenProject)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            RecentMixes(cards, onOpenRecipe)
                            Card(
                                Modifier.fillMaxWidth().border(1.dp, StudioBorder, androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).clickable(onClick = onAiMix),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Text("AI ASSISTANT", style = MaterialTheme.typography.labelMedium, color = StudioTeal, fontWeight = FontWeight.Bold)
                                    Text("어떤 색을 만들까요?", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("보유 도료로 예상 조색 만들기 →", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        RecentMixes(cards, onOpenRecipe)
                        RecentProjects(projects, onOpenProject)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHero(
    paintCount: Int,
    projectCount: Int,
    activeProjectCount: Int,
    recipeCount: Int,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onAiMix: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth >= 700.dp
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("☰", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("AI Figure Paint", style = MaterialTheme.typography.titleLarge, color = StudioNavy)
                    Text("PAINT STUDIO · v${com.aifigurepaint.app.BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onSearch) { Text("⌕ 검색", color = StudioNavy) }
                TextButton(onClick = onSettings) { Text("설정", color = StudioNavy) }
            }
            if (wide) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HomeWelcomeCard(Modifier.weight(1.12f))
                    HomeAiCard(onAiMix, Modifier.weight(.88f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HomeWelcomeCard()
                    HomeAiCard(onAiMix)
                }
            }
            if (wide) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    HeroMetric("보유 도료", paintCount, Modifier.weight(1f))
                    HeroMetric("프로젝트", projectCount, Modifier.weight(1f))
                    HeroMetric("조색 레시피", recipeCount, Modifier.weight(1f))
                    HeroMetric("진행 작업", activeProjectCount, Modifier.weight(1f), compactLabel = "현재")
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        HeroMetric("보유 도료", paintCount, Modifier.weight(1f))
                        HeroMetric("프로젝트", projectCount, Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        HeroMetric("조색 레시피", recipeCount, Modifier.weight(1f))
                        HeroMetric("진행 작업", activeProjectCount, Modifier.weight(1f), compactLabel = "현재")
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeWelcomeCard(modifier: Modifier = Modifier) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
    Card(
        modifier = modifier.fillMaxWidth().border(1.dp, StudioBorder, shape),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = shape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).background(StudioMint, androidx.compose.foundation.shape.RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                Text("✦", color = StudioTeal, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.size(11.dp))
            Column {
                Text("안녕하세요, 피규어 도색을", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("더 스마트하게 관리해보세요.", style = MaterialTheme.typography.titleMedium, color = StudioNavy)
            }
        }
    }
}

@Composable
private fun HomeAiCard(onAiMix: () -> Unit, modifier: Modifier = Modifier) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
    Card(
        modifier = modifier.fillMaxWidth().border(1.dp, StudioTeal.copy(alpha = .3f), shape).clickable(onClick = onAiMix),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8FA)),
        shape = shape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("AI 어시스턴트", style = MaterialTheme.typography.titleMedium, color = StudioNavy)
                    Spacer(Modifier.size(6.dp))
                    Text("BETA", style = MaterialTheme.typography.labelSmall, color = StudioTeal)
                }
                Text("사진 색상 분석 · 조색 추천 · 도색 상담", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(7.dp))
                Text("AI 도움 열기  →", style = MaterialTheme.typography.labelLarge, color = StudioTeal)
            }
            Box(Modifier.size(42.dp).background(StudioMint, androidx.compose.foundation.shape.RoundedCornerShape(21.dp)), contentAlignment = Alignment.Center) {
                Text("▣", style = MaterialTheme.typography.titleLarge, color = StudioTeal)
            }
        }
    }
}

@Composable
private fun HeroMetric(label: String, value: Int, modifier: Modifier = Modifier, compactLabel: String? = null) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
    Card(
        modifier = modifier.border(1.dp, StudioBorder, shape),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = shape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value.toString(), style = MaterialTheme.typography.headlineSmall, color = StudioNavy)
            Text(compactLabel ?: if (label == "프로젝트") "전체" else if (label == "조색 레시피") "저장됨" else "종류", style = MaterialTheme.typography.labelSmall, color = StudioTeal)
        }
    }
}

@Composable
private fun AiPaintRegistrationCard(onScan: () -> Unit, modifier: Modifier = Modifier) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
    Card(
        modifier = modifier.fillMaxWidth().border(1.dp, StudioTeal.copy(alpha = .42f), shape),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = shape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(StudioMint, androidx.compose.foundation.shape.RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                Text("◎", style = MaterialTheme.typography.headlineSmall, color = StudioTeal, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text("도료 사진 AI 등록", style = MaterialTheme.typography.titleMedium, color = StudioNavy)
                Text("라벨을 촬영하면 GPT-5.6이 브랜드·코드·색상을 초안으로 정리합니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.size(10.dp))
            Button(
                onClick = onScan,
                modifier = Modifier.height(40.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = StudioTeal),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 13.dp),
            ) { Text("촬영 등록") }
        }
    }
}

@Composable
private fun QuickActionsCard(
    onNewMix: () -> Unit,
    onAddPaint: () -> Unit,
    onNewProject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
    Card(
        modifier = modifier.fillMaxWidth().border(1.dp, StudioBorder, shape),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = shape,
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("QUICK ACTION", style = MaterialTheme.typography.labelMedium, color = StudioTeal, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Button(onClick = onNewMix, modifier = Modifier.weight(1f).height(40.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 6.dp)) { Text("새 조색") }
                OutlinedButton(onClick = onAddPaint, modifier = Modifier.weight(1f).height(40.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 6.dp)) { Text("도료 추가") }
                OutlinedButton(onClick = onNewProject, modifier = Modifier.weight(1f).height(40.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 6.dp)) { Text("AI 프로젝트") }
            }
        }
    }
}

@Composable
private fun RecentProjects(projects: List<ProjectEntity>, onOpen: (Long) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        SectionTitle("최근 프로젝트", "최근 수정한 작업")
        if (projects.isEmpty()) EmptyCard("프로젝트를 추가해보세요.")
        projects.take(3).forEach { project ->
            val shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, StudioBorder, shape).clickable { onOpen(project.id) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = shape,
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (project.photoUri != null) PhotoPreview(project.photoUri, Modifier.size(42.dp))
                    else Box(Modifier.size(42.dp).background(MaterialTheme.colorScheme.primaryContainer, androidx.compose.foundation.shape.RoundedCornerShape(10.dp)))
                    Spacer(Modifier.size(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(project.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${ProjectStatus.label(project.status)} · ${project.modelName.ifBlank { "모델 미입력" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(5.dp))
                        val progress = when (project.status) {
                            ProjectStatus.COMPLETED -> 1f
                            ProjectStatus.IN_PROGRESS -> .62f
                            else -> .16f
                        }
                        Box(Modifier.fillMaxWidth().height(3.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
                            Box(Modifier.fillMaxWidth(progress).height(3.dp).background(StudioTeal))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentMixes(cards: List<RecipeCardRow>, onOpen: (Long) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        SectionTitle("최근 조색", "최근 수정한 레시피")
        if (cards.isEmpty()) EmptyCard("첫 조색 레시피를 기록해보세요.")
        cards.take(3).forEach { card -> RecipeCard(card, onOpen) }
    }
}

@Composable
internal fun RecipeCard(card: RecipeCardRow, onOpen: (Long) -> Unit) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
    Card(
        modifier = Modifier.border(1.dp, StudioBorder, shape),
        onClick = { onOpen(card.id) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = shape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ColorSwatch(card.resultColorValue, 44.dp)
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(card.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "도료 ${card.itemCount} · 기준 ${formatMl(card.baseTotalMl)}ml · ${formatDateTime(card.updatedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                if (!card.projectName.isNullOrBlank()) Text(card.projectName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
            }
        }
    }
}

@Composable
internal fun EmptyCard(text: String) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, StudioBorder, shape),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .42f)),
        shape = shape,
    ) {
        Text(text, Modifier.fillMaxWidth().padding(13.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

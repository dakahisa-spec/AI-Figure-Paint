package com.aifigurepaint.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aifigurepaint.app.AppViewModel
import com.aifigurepaint.app.data.DuplicatePolicy
import java.time.LocalDate

@Composable
internal fun DataManagementScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val state by viewModel.excelState.collectAsState()
    var policy by remember { mutableStateOf(DuplicatePolicy.KEEP_EXISTING) }
    var confirmImport by remember { mutableStateOf(false) }
    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    ) { uri -> uri?.let(viewModel::exportExcel) }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::previewExcel)
    }

    Scaffold(topBar = { EditorHeader("데이터 관리", onBack) }) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val wide = maxWidth >= 700.dp
            val actions: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle("Excel 백업", "주요 데이터를 여러 Sheet의 .xlsx 파일로 관리합니다")
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Excel 내보내기", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("도료·레시피·버전·프로젝트·테스트 기록을 내보냅니다. 사진은 파일 경로만 기록합니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(
                                onClick = { exportPicker.launch("AI-Figure-Paint-backup-${LocalDate.now()}.xlsx") },
                                enabled = !state.loading,
                                modifier = Modifier.fillMaxWidth().height(42.dp),
                            ) { Text("저장 위치 선택") }
                        }
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Excel 가져오기", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("파일을 먼저 검증하고 미리보기를 확인한 뒤에만 DB에 반영합니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedButton(
                                onClick = { importPicker.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/zip")) },
                                enabled = !state.loading,
                                modifier = Modifier.fillMaxWidth().height(42.dp),
                            ) { Text(".xlsx 파일 선택") }
                        }
                    }
                    if (state.loading) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Text("파일을 안전하게 처리하고 있습니다…")
                    }
                    state.notice?.let {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                            Text(it, Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            val preview: @Composable () -> Unit = {
                val data = state.preview
                if (data == null) {
                    EmptyCard("가져올 Excel 파일을 선택하면 신규·중복·오류를 먼저 보여드립니다.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionTitle("가져오기 미리보기", "DB에는 아직 반영되지 않았습니다")
                        val metrics = listOf(
                            "신규 도료" to data.newPaints,
                            "기존 도료 수정 후보" to data.duplicatePaints,
                            "신규 레시피" to data.newRecipes,
                            "신규 프로젝트" to data.newProjects,
                            "테스트 기록" to data.testResults,
                            "오류" to data.errors.size,
                        )
                        metrics.chunked(2).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { (label, count) ->
                                    Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f))) {
                                        Column(Modifier.padding(10.dp)) {
                                            Text(count.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                            Text(label, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                        if (data.errors.isNotEmpty()) {
                            Text("오류 상세", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                            data.errors.take(20).forEach { issue -> Text("${issue.sheet} · ${if (issue.row > 0) "${issue.row}행" else "Sheet"}: ${issue.message}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                        } else {
                            Text("중복 도료 처리", style = MaterialTheme.typography.titleMedium)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                PolicyChip(DuplicatePolicy.KEEP_EXISTING, policy, "기존 유지") { policy = it }
                                PolicyChip(DuplicatePolicy.UPDATE_FROM_EXCEL, policy, "Excel 값으로 업데이트") { policy = it }
                                PolicyChip(DuplicatePolicy.SKIP, policy, "건너뛰기") { policy = it }
                            }
                            Text("선택한 정책을 모든 중복 도료에 동일하게 적용합니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = { confirmImport = true }, enabled = !state.loading, modifier = Modifier.fillMaxWidth().height(44.dp)) { Text("가져오기 실행") }
                        }
                    }
                }
            }

            if (wide) {
                Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(.8f).verticalScroll(rememberScrollState())) { actions() }
                    Column(Modifier.weight(1.2f).verticalScroll(rememberScrollState())) { preview() }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(14.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    actions(); preview(); Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    if (confirmImport) {
        val preview = state.preview
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            title = { Text("Excel 가져오기 실행") },
            text = { Text("도료 ${preview?.newPaints ?: 0}개, 레시피 ${preview?.newRecipes ?: 0}개, 프로젝트 ${preview?.newProjects ?: 0}개와 테스트 기록 ${preview?.testResults ?: 0}개를 가져옵니다.") },
            confirmButton = { Button(onClick = { confirmImport = false; viewModel.importExcel(policy) }) { Text("가져오기") } },
            dismissButton = { TextButton(onClick = { confirmImport = false }) { Text("취소") } },
        )
    }
}

@Composable
private fun PolicyChip(value: DuplicatePolicy, selected: DuplicatePolicy, label: String, onSelect: (DuplicatePolicy) -> Unit) {
    FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
}

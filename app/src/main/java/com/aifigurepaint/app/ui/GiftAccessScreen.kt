package com.aifigurepaint.app.ui

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aifigurepaint.app.ai.GiftAccessState

@Composable
internal fun GiftActivationScreen(
    state: GiftAccessState,
    onActivate: (String) -> Unit,
) {
    var code by remember { mutableStateOf("") }
    BoxWithConstraints(Modifier.fillMaxSize().padding(18.dp)) {
        val wide = maxWidth >= 700.dp
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(if (wide) 0.58f else 1f).widthIn(max = 720.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.fillMaxWidth().padding(if (wide) 30.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("AI Figure Paint Gift", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("선물용 앱 활성화", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    Text(
                        "이 앱은 최대 3대의 기기에서 사용할 수 있습니다. 같은 기기의 일반적인 재설치는 추가 기기로 계산되지 않습니다.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.uppercase().take(64) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("선물용 활성화 코드") },
                        supportingText = { Text("API 키가 아닌 선물용 등록 코드입니다.") },
                        singleLine = true,
                        enabled = !state.loading,
                    )
                    Button(
                        onClick = { onActivate(code.trim()) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        enabled = code.isNotBlank() && !state.loading,
                    ) {
                        if (state.loading) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.size(8.dp))
                        }
                        Text("기기 활성화")
                    }
                    state.notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Text(
                        "월 AI 사용 한도는 세 기기 합산 3,000원입니다. 환율과 모델별 요금 차이 때문에 안전을 위해 조금 일찍 중지될 수 있습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

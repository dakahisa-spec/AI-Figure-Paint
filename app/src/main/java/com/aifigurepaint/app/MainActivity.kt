package com.aifigurepaint.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aifigurepaint.app.ui.AIFigurePaintTheme
import com.aifigurepaint.app.ui.AiFigurePaintApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppEntry() }
    }
}

@Composable
private fun AppEntry(viewModel: AppViewModel = viewModel()) {
    AIFigurePaintTheme {
        AiFigurePaintApp(viewModel)
    }
}

package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.game.GameScreen
import com.example.game.GameViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme(darkTheme = true) { // Obligar al modo oscuro para la fosa industrial
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          val viewModel: GameViewModel = viewModel()
          GameScreen(viewModel = viewModel)
        }
      }
    }
  }
}


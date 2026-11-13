package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.apktranslatorpro.core.firebase.configureAppCheck
import com.example.apktranslatorpro.ui.ApkTranslatorRoot
import com.example.apktranslatorpro.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        setContent {
            val mainViewModel: MainViewModel = viewModel(factory = MainViewModel.Factory)
            ApkTranslatorRoot(viewModel = mainViewModel)
        }
    }
}

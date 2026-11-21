package com.notaai.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.notaai.app.ui.navigation.NotaNavHost
import com.notaai.app.ui.theme.NotaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NotaTheme {
                NotaNavHost()
            }
        }
    }
}

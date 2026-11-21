package com.notaai.app.data.preferences

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UserSettings(
    val enhanceContrastForOcr: Boolean = true,
    val flagLowConfidenceOcr: Boolean = true
)

class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("nota_ai_prefs", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<UserSettings> = _settings.asStateFlow()

    private fun loadSettings() = UserSettings(
        enhanceContrastForOcr = prefs.getBoolean("enhance_contrast_ocr", true),
        flagLowConfidenceOcr = prefs.getBoolean("flag_low_conf_ocr", true)
    )

    fun setEnhanceContrast(enabled: Boolean) {
        prefs.edit().putBoolean("enhance_contrast_ocr", enabled).apply()
        _settings.value = loadSettings()
    }

    fun setFlagLowConfidence(enabled: Boolean) {
        prefs.edit().putBoolean("flag_low_conf_ocr", enabled).apply()
        _settings.value = loadSettings()
    }
}

package com.serkka.tracker

import android.app.Application
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ThemeViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
    
    // Default color is now White (0xFFFFFFFF)
    private val _primaryColor = MutableStateFlow(Color(prefs.getInt("primary_color", Color.White.toArgb())))
    val primaryColor: StateFlow<Color> = _primaryColor

    fun updatePrimaryColor(color: Color) {
        _primaryColor.value = color
        prefs.edit().putInt("primary_color", color.toArgb()).apply()
    }
}

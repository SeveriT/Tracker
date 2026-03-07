package com.serkka.tracker.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = OrangePrimary,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = DarkBackground,
    surface = DarkSurfaceColor,
    surfaceTint = Color.Transparent, // Prevent tonal elevation tinting
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    surfaceVariant = DarkBlueVariant
)

private val LightColorScheme = lightColorScheme(
    primary = OrangePrimary,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = LightBackground, 
    surface = LightSurfaceColor,
    surfaceTint = Color.Transparent, // Prevent tonal elevation tinting
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = Color.Black,
    onSurface = Color.Black,
    surfaceVariant = LightBlueVariant
)

@Composable
fun GymTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    primaryColor: Color = OrangePrimary,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context).copy(primary = primaryColor) 
            else dynamicLightColorScheme(context).copy(primary = primaryColor)
        }

        darkTheme -> DarkColorScheme.copy(primary = primaryColor)
        else -> LightColorScheme.copy(primary = primaryColor)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

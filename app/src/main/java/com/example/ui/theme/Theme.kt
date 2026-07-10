package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = HighDensityDarkPrimary,
    secondary = HighDensityDarkSecondary,
    tertiary = HighDensityDarkTertiary,
    background = HighDensityDarkBackground,
    surface = HighDensityDarkSurface,
    onBackground = HighDensityDarkOnBackground,
    onSurface = HighDensityDarkOnSurface,
    outline = HighDensityDarkOutline,
    surfaceVariant = HighDensityDarkSurfaceVariant,
    error = HighDensityDarkError
  )

private val LightColorScheme =
  lightColorScheme(
    primary = HighDensityPrimary,
    secondary = HighDensitySecondary,
    tertiary = HighDensityTertiary,
    background = HighDensityBackground,
    surface = HighDensitySurface,
    onBackground = HighDensityOnBackground,
    onSurface = HighDensityOnSurface,
    outline = HighDensityOutline,
    surfaceVariant = HighDensitySurfaceVariant,
    error = HighDensityError
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // For branded custom "High Density" theme, disable dynamicColor by default to preserve the gorgeous brand color scheme
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

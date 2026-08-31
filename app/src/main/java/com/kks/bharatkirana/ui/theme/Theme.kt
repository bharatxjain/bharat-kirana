package com.kks.bharatkirana.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
  primary = AppPrimary,
  onPrimary = Color.White,
  primaryContainer = AppPrimaryLight,
  onPrimaryContainer = AppPrimaryDarker,
  secondary = AppSecondary,
  onSecondary = Color.White,
  secondaryContainer = AppPrimaryPill,
  onSecondaryContainer = AppPrimaryDarker,
  tertiary = AppPrimaryAccent,
  background = AppSurfaceLight,
  onBackground = AppInk,
  surface = AppSurfaceRaised,
  onSurface = AppInk,
  surfaceVariant = AppSurfaceSoft,
  onSurfaceVariant = AppGray600,
  outline = AppBorder,
  outlineVariant = AppBorder,
  error = AppDanger,
  onError = Color.White,
  errorContainer = AppDangerLight,
  onErrorContainer = AppDanger
)

@Composable
fun BharatKiranaTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = LightColors,
    typography = Typography,
    shapes = AppShapes,
    content = content
  )
}

// Legacy alias to avoid churn where MainActivity used to reference this name.
@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit
) {
  BharatKiranaTheme(content = content)
}

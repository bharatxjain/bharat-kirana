package com.kks.bharatkirana.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BharatDarkColorScheme =
  darkColorScheme(
    primary = BharatPurpleAccent,
    onPrimary = Color.White,
    primaryContainer = BharatPurpleDark,
    onPrimaryContainer = BharatPurpleLight,
    secondary = BharatPurpleAccent,
    onSecondary = Color.White,
    background = Color(0xFF0F172A),
    surface = Color(0xFF1E293B),
    onBackground = Color.White,
    onSurface = Color.White,
  )

private val BharatLightColorScheme =
  lightColorScheme(
    primary = BharatPurplePrimary,
    onPrimary = Color.White,
    primaryContainer = BharatPurpleContainer,
    onPrimaryContainer = BharatPurpleDark,
    secondary = BharatPurpleAccent,
    onSecondary = Color.White,
    background = BharatBackground,
    surface = BharatSurface,
    surfaceVariant = BharatSurfaceVariant,
    onBackground = BharatTextPrimary,
    onSurface = BharatTextPrimary,
    onSurfaceVariant = BharatTextSecondary,
    outline = BharatBorder
  )

@Composable
fun BharatKiranaTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) BharatDarkColorScheme else BharatLightColorScheme
  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  BharatKiranaTheme(darkTheme = darkTheme, content = content)
}

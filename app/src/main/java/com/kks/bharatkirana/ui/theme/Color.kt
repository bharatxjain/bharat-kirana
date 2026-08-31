package com.kks.bharatkirana.ui.theme

import androidx.compose.ui.graphics.Color

// Canonical palette — source of truth for BreakQ colours. Mirrors the
// index.css tokens on the marketing site so app + web share one language.

// --- Brand purples ---
val AppPrimary = Color(0xFF7001FE)         // web --primary
val AppPrimaryDark = Color(0xFF5500C5)     // web --primary-dark
val AppPrimaryDarker = Color(0xFF24005B)   // web --primary-darker (hero bg)
val AppPrimaryLight = Color(0xFFE9DDFF)    // web --primary-light (card tint)
val AppPrimaryPill = Color(0xFFD1BCFF)     // web --primary-pill (chips)
val AppSecondary = Color(0xFF6B49B5)       // web --secondary
val AppPrimaryAccent = Color(0xFF8B5CF6)   // lighter violet used for M3 tertiary

// --- Neutrals (light) ---
val AppSurfaceLight = Color(0xFFFDF7FF)    // web --surface (warm background)
val AppSurfaceRaised = Color(0xFFFFFFFF)   // cards on top of surface
val AppSurfaceSoft = Color(0xFFF8F1FF)     // web --gray-100 (subtle bg tint)
val AppInk = Color(0xFF1D1A25)             // web --ink / --gray-900
val AppGray900 = Color(0xFF1D1A25)
val AppGray600 = Color(0xFF4A4457)         // web --gray-600
val AppGray400 = Color(0xFF7B7489)         // web --gray-400
val AppGray100 = Color(0xFFF8F1FF)         // web --gray-100
val AppBorder = Color(0xFFEDE5F5)          // hairline dividers

// --- Neutrals (dark) ---
val AppDarkBg = Color(0xFF0F0A1A)          // near-black warm purple
val AppDarkSurface = Color(0xFF1D1A25)     // web --ink flipped to surface
val AppDarkSurfaceRaised = Color(0xFF2A2634)
val AppDarkBorder = Color(0xFF3B3547)
val AppDarkTextPrimary = Color(0xFFF8F1FF)
val AppDarkTextSecondary = Color(0xFFB9B0C7)
val AppDarkTextMuted = Color(0xFF7B7489)

// --- Semantic status colours ---
val AppSuccess = Color(0xFF16A34A)
val AppSuccessLight = Color(0xFFDCFCE7)
val AppWarning = Color(0xFFD97706)
val AppWarningLight = Color(0xFFFEF3C7)
val AppDanger = Color(0xFFDC2626)
val AppDangerLight = Color(0xFFFEE2E2)
val AppInfo = Color(0xFF0891B2)
val AppInfoLight = Color(0xFFCFFAFE)

// --- Order-status colour mapping (unifies duplication across 3 screens) ---
// Each entry is (background, foreground) for OrderStatus badges.
val OrderStatusPlacedBg = AppWarningLight
val OrderStatusPlacedFg = AppWarning
val OrderStatusConfirmedBg = AppSuccessLight
val OrderStatusConfirmedFg = Color(0xFF10B981)
val OrderStatusPreparingBg = Color(0xFFE0F2FE)
val OrderStatusPreparingFg = Color(0xFF0284C7)
val OrderStatusReadyBg = Color(0xFFF0FDF4)
val OrderStatusReadyFg = Color(0xFF166534)
val OrderStatusCompletedBg = Color(0xFFF1F5F9)
val OrderStatusCompletedFg = AppGray600
val OrderStatusCancelledBg = AppDangerLight
val OrderStatusCancelledFg = AppDanger

// ---------------------------------------------------------------------------
// Backward-compatibility aliases. Existing screens still reference these by
// name — mapping them onto the new palette gives every unmigrated screen a
// subtle brand refresh for free without breaking a single import.
// ---------------------------------------------------------------------------
val BharatPurplePrimary = AppPrimary
val BharatPurpleDark = AppPrimaryDark
val BharatPurpleLight = AppPrimaryLight
val BharatPurpleContainer = Color(0xFFEDE9FE)
val BharatPurpleAccent = Color(0xFF8B5CF6)

val BharatGreen = AppSuccess
val BharatGreenLight = AppSuccessLight
val BharatRedDiscount = AppDanger
val BharatRedLight = AppDangerLight
val BharatOrange = Color(0xFFEA580C)

val BharatBackground = AppSurfaceLight
val BharatSurface = AppSurfaceRaised
val BharatSurfaceVariant = AppSurfaceSoft
val BharatBorder = AppBorder

val BharatTextPrimary = AppInk
val BharatTextSecondary = AppGray600
val BharatTextMuted = AppGray400

// Material default theme leftovers — kept because Compose scaffolding
// sometimes references them via generated code.
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)
val Purple40 = AppPrimary
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

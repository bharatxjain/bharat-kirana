package com.kks.bharatkirana.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Corner radii tokens. Screens should pick one instead of picking a magic
// dp value; keeps every card / button / chip / bottom sheet visually in tune.
object AppRadius {
  val Xs = 8.dp
  val Sm = 12.dp
  val Md = 16.dp
  val Lg = 20.dp
  val Xl = 28.dp
  val Pill = 999.dp
}

// Material 3 Shapes plumbed with the same tokens so Compose components that
// read MaterialTheme.shapes.medium etc. automatically pick up the brand radii.
val AppShapes = Shapes(
  extraSmall = RoundedCornerShape(AppRadius.Xs),
  small = RoundedCornerShape(AppRadius.Sm),
  medium = RoundedCornerShape(AppRadius.Md),
  large = RoundedCornerShape(AppRadius.Lg),
  extraLarge = RoundedCornerShape(AppRadius.Xl)
)

package com.kks.bharatkirana.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kks.bharatkirana.ui.theme.*

// Shared BreakQ design-system components. Every screen should reach for these
// first; only fall through to raw Card/Button/TextField when a real one-off
// visual is required.

// ---------- Cards ----------

/** Flat card - default surface tone, thin outline. Use inside sections. */
@Composable
fun AppCard(
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit
) {
  val shape = RoundedCornerShape(AppRadius.Md)
  val colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
  val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
  if (onClick != null) {
    OutlinedCard(onClick = onClick, shape = shape, colors = colors, border = border, modifier = modifier) {
      Column(modifier = Modifier.padding(AppSpacing.Lg), content = content)
    }
  } else {
    OutlinedCard(shape = shape, colors = colors, border = border, modifier = modifier) {
      Column(modifier = Modifier.padding(AppSpacing.Lg), content = content)
    }
  }
}

/** Elevated card - subtle 2-layer shadow. Use for hero content. */
@Composable
fun AppElevatedCard(
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit
) {
  val shape = RoundedCornerShape(AppRadius.Lg)
  val colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
  val elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
  if (onClick != null) {
    ElevatedCard(onClick = onClick, shape = shape, colors = colors, elevation = elevation, modifier = modifier) {
      Column(modifier = Modifier.padding(AppSpacing.Lg), content = content)
    }
  } else {
    ElevatedCard(shape = shape, colors = colors, elevation = elevation, modifier = modifier) {
      Column(modifier = Modifier.padding(AppSpacing.Lg), content = content)
    }
  }
}

/** Purple-tinted card for hero moments and CTAs. */
@Composable
fun AppAccentCard(
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit
) {
  val shape = RoundedCornerShape(AppRadius.Lg)
  val bg = MaterialTheme.colorScheme.primaryContainer
  val border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
  val cardMod = modifier.then(
    if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
  )
  Surface(color = bg, shape = shape, border = border, modifier = cardMod) {
    Column(modifier = Modifier.padding(AppSpacing.Lg), content = content)
  }
}

// ---------- Buttons ----------

@Composable
fun AppPrimaryButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  leadingIcon: ImageVector? = null,
  loading: Boolean = false
) {
  Button(
    onClick = onClick,
    enabled = enabled && !loading,
    modifier = modifier.height(52.dp),
    shape = RoundedCornerShape(AppRadius.Sm),
    colors = ButtonDefaults.buttonColors(
      containerColor = MaterialTheme.colorScheme.primary,
      contentColor = MaterialTheme.colorScheme.onPrimary,
      disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
      disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
    )
  ) {
    if (loading) {
      CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
      Spacer(modifier = Modifier.width(AppSpacing.Md))
    } else if (leadingIcon != null) {
      Icon(leadingIcon, contentDescription = null)
      Spacer(modifier = Modifier.width(AppSpacing.Sm))
    }
    Text(text, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
  }
}

@Composable
fun AppSecondaryButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  leadingIcon: ImageVector? = null
) {
  OutlinedButton(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier.height(52.dp),
    shape = RoundedCornerShape(AppRadius.Sm),
    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
    colors = ButtonDefaults.outlinedButtonColors(
      contentColor = MaterialTheme.colorScheme.primary
    )
  ) {
    if (leadingIcon != null) {
      Icon(leadingIcon, contentDescription = null)
      Spacer(modifier = Modifier.width(AppSpacing.Sm))
    }
    Text(text, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
  }
}

// ---------- Text Field ----------

/**
 * Standard text field with label above and consistent brand styling.
 * Replaces the many AuthTextField* variants scattered around.
 */
@Composable
fun AppTextField(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
  modifier: Modifier = Modifier,
  placeholder: String = "",
  singleLine: Boolean = true,
  enabled: Boolean = true,
  isError: Boolean = false,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
  leadingIcon: ImageVector? = null,
  trailingContent: (@Composable () -> Unit)? = null,
  supportingText: String? = null
) {
  Column(modifier = modifier.fillMaxWidth()) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.padding(bottom = AppSpacing.Xs)
    )
    OutlinedTextField(
      value = value,
      onValueChange = onValueChange,
      placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
      singleLine = singleLine,
      enabled = enabled,
      isError = isError,
      keyboardOptions = keyboardOptions,
      leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null) } },
      trailingIcon = trailingContent,
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(AppRadius.Sm),
      colors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline
      )
    )
    if (supportingText != null) {
      Text(
        text = supportingText,
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = AppSpacing.Xs, start = AppSpacing.Sm)
      )
    }
  }
}

// ---------- Layout helpers ----------

/** Standardised section header - eyebrow + title, matches the web design. */
@Composable
fun AppSectionHeader(
  title: String,
  eyebrow: String? = null,
  modifier: Modifier = Modifier
) {
  Column(modifier = modifier) {
    if (eyebrow != null) {
      Text(
        text = eyebrow.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = AppSpacing.Xs)
      )
    }
    Text(
      text = title,
      style = MaterialTheme.typography.titleLarge,
      color = MaterialTheme.colorScheme.onSurface
    )
  }
}

// ---------- Chips / pills ----------

@Composable
fun AppChip(
  label: String,
  selected: Boolean = false,
  onClick: (() -> Unit)? = null,
  leadingIcon: ImageVector? = null,
  modifier: Modifier = Modifier
) {
  val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer
  val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
  val chipMod = modifier
    .clip(RoundedCornerShape(AppRadius.Pill))
    .background(bg)
    .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    .padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Sm)
  Row(modifier = chipMod, verticalAlignment = Alignment.CenterVertically) {
    if (leadingIcon != null) {
      Icon(leadingIcon, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
      Spacer(modifier = Modifier.width(AppSpacing.Xs))
    }
    Text(label, style = MaterialTheme.typography.labelMedium, color = fg)
  }
}

// ---------- Empty state ----------

@Composable
fun AppEmptyState(
  icon: ImageVector,
  title: String,
  subtitle: String? = null,
  modifier: Modifier = Modifier,
  actionLabel: String? = null,
  onAction: (() -> Unit)? = null
) {
  Column(
    modifier = modifier.fillMaxWidth().padding(AppSpacing.Xl),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Box(
      modifier = Modifier
        .size(88.dp)
        .clip(RoundedCornerShape(AppRadius.Pill))
        .background(MaterialTheme.colorScheme.primaryContainer),
      contentAlignment = Alignment.Center
    ) {
      Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
    }
    Spacer(modifier = Modifier.height(AppSpacing.Lg))
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
    if (subtitle != null) {
      Spacer(modifier = Modifier.height(AppSpacing.Xs))
      Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
    if (actionLabel != null && onAction != null) {
      Spacer(modifier = Modifier.height(AppSpacing.Lg))
      AppPrimaryButton(text = actionLabel, onClick = onAction)
    }
  }
}

// ---------- Status badge ----------

/** Small pill for status labels (order, subscription, verification). */
@Composable
fun AppStatusBadge(
  text: String,
  background: Color,
  foreground: Color,
  modifier: Modifier = Modifier
) {
  Surface(
    color = background,
    shape = RoundedCornerShape(AppRadius.Pill),
    modifier = modifier
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
      color = foreground,
      modifier = Modifier.padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Xs)
    )
  }
}

// ---------- Hero gradient banner ----------

/** Full-width banner with the brand purple gradient. Use for hero sections. */
@Composable
fun AppGradientBanner(
  modifier: Modifier = Modifier,
  content: @Composable BoxScope.() -> Unit
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(AppRadius.Lg))
      .background(
        Brush.linearGradient(listOf(AppPrimary, AppPrimaryDarker))
      )
      .padding(AppSpacing.Xl),
    content = content
  )
}

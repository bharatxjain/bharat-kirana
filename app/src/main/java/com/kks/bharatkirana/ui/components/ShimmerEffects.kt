package com.kks.bharatkirana.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Reusable shimmer effect modifier that paints a smooth moving gradient across the composable.
 */
fun Modifier.shimmerEffect(
  enabled: Boolean = true,
  baseColor: Color = Color(0xFFE2E8F0),
  highlightColor: Color = Color(0xFFF8FAFC)
): Modifier = composed {
  if (!enabled) return@composed this

  val transition = rememberInfiniteTransition(label = "shimmerTransition")
  val translateAnim by transition.animateFloat(
    initialValue = 0f,
    targetValue = 1000f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "shimmerTranslate"
  )

  val brush = Brush.linearGradient(
    colors = listOf(baseColor, highlightColor, baseColor),
    start = Offset(x = translateAnim - 300f, y = translateAnim - 300f),
    end = Offset(x = translateAnim + 300f, y = translateAnim + 300f)
  )

  this.background(brush = brush)
}

/**
 * Shimmer skeleton placeholder for horizontal category pills / grid items
 */
@Composable
fun ShimmerCategoryPill(modifier: Modifier = Modifier) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = modifier.padding(vertical = 4.dp)
  ) {
    Box(
      modifier = Modifier
        .size(56.dp)
        .clip(CircleShape)
        .shimmerEffect()
    )
    Spacer(modifier = Modifier.height(6.dp))
    Box(
      modifier = Modifier
        .width(44.dp)
        .height(10.dp)
        .clip(RoundedCornerShape(4.dp))
        .shimmerEffect()
    )
  }
}

/**
 * Shimmer grid for 12 categories
 */
@Composable
fun ShimmerCategoriesGrid(modifier: Modifier = Modifier) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 12.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    repeat(2) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround
      ) {
        repeat(4) {
          ShimmerCategoryPill(modifier = Modifier.weight(1f))
        }
      }
    }
  }
}

/**
 * Shimmer skeleton for Product Grid Card (Popular products carousel)
 */
@Composable
fun ShimmerProductCard(modifier: Modifier = Modifier) {
  Card(
    modifier = modifier
      .width(160.dp)
      .height(230.dp),
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(10.dp),
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      // Image box placeholder
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(105.dp)
          .clip(RoundedCornerShape(10.dp))
          .shimmerEffect()
      )

      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Title line 1
        Box(
          modifier = Modifier
            .fillMaxWidth(0.85f)
            .height(12.dp)
            .clip(RoundedCornerShape(3.dp))
            .shimmerEffect()
        )
        // Title line 2 / weight
        Box(
          modifier = Modifier
            .fillMaxWidth(0.5f)
            .height(10.dp)
            .clip(RoundedCornerShape(3.dp))
            .shimmerEffect()
        )
      }

      // Price & Add button row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(
          modifier = Modifier
            .width(50.dp)
            .height(16.dp)
            .clip(RoundedCornerShape(4.dp))
            .shimmerEffect()
        )
        Box(
          modifier = Modifier
            .width(60.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .shimmerEffect()
        )
      }
    }
  }
}

/**
 * Shimmer row of product cards
 */
@Composable
fun ShimmerProductRow(modifier: Modifier = Modifier) {
  LazyRow(
    contentPadding = PaddingValues(horizontal = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(14.dp),
    modifier = modifier.fillMaxWidth()
  ) {
    items(4) {
      ShimmerProductCard()
    }
  }
}

/**
 * Shimmer skeleton for Daily Essential horizontal card
 */
@Composable
fun ShimmerDailyEssentialCard(modifier: Modifier = Modifier) {
  Card(
    modifier = modifier
      .fillMaxWidth()
      .height(84.dp),
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxSize()
        .padding(10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.weight(1f)
      ) {
        // Thumbnail
        Box(
          modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(10.dp))
            .shimmerEffect()
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Box(
            modifier = Modifier
              .width(120.dp)
              .height(13.dp)
              .clip(RoundedCornerShape(3.dp))
              .shimmerEffect()
          )
          Box(
            modifier = Modifier
              .width(70.dp)
              .height(10.dp)
              .clip(RoundedCornerShape(3.dp))
              .shimmerEffect()
          )
          Box(
            modifier = Modifier
              .width(50.dp)
              .height(12.dp)
              .clip(RoundedCornerShape(3.dp))
              .shimmerEffect()
          )
        }
      }

      Box(
        modifier = Modifier
          .width(68.dp)
          .height(32.dp)
          .clip(RoundedCornerShape(8.dp))
          .shimmerEffect()
      )
    }
  }
}

/**
 * Shimmer skeleton for Order Card
 */
@Composable
fun ShimmerOrderCard(modifier: Modifier = Modifier) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
    modifier = modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Box(
            modifier = Modifier
              .width(90.dp)
              .height(14.dp)
              .clip(RoundedCornerShape(4.dp))
              .shimmerEffect()
          )
          Box(
            modifier = Modifier
              .width(60.dp)
              .height(10.dp)
              .clip(RoundedCornerShape(3.dp))
              .shimmerEffect()
          )
        }
        Box(
          modifier = Modifier
            .width(80.dp)
            .height(22.dp)
            .clip(RoundedCornerShape(6.dp))
            .shimmerEffect()
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(3) {
          Box(
            modifier = Modifier
              .size(44.dp)
              .clip(RoundedCornerShape(8.dp))
              .shimmerEffect()
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))
      HorizontalDivider(color = Color(0xFFF1F5F9))
      Spacer(modifier = Modifier.height(10.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Box(
          modifier = Modifier
            .width(110.dp)
            .height(14.dp)
            .clip(RoundedCornerShape(4.dp))
            .shimmerEffect()
        )
        Box(
          modifier = Modifier
            .width(70.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .shimmerEffect()
        )
      }
    }
  }
}

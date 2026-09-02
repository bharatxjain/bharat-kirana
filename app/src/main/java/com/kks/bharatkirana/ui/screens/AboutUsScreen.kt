package com.kks.bharatkirana.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kks.bharatkirana.BuildConfig
import com.kks.bharatkirana.ui.theme.BharatBackground
import com.kks.bharatkirana.ui.theme.BharatPurpleContainer
import com.kks.bharatkirana.ui.theme.BharatPurplePrimary
import com.kks.bharatkirana.ui.theme.BharatTextMuted
import com.kks.bharatkirana.ui.theme.BharatTextPrimary
import com.kks.bharatkirana.ui.theme.BharatTextSecondary

@Composable
fun AboutUsScreen(
  onBackClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(BharatBackground)
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
    ) {
      Surface(color = Color.White, shadowElevation = 1.dp) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(onClick = onBackClick) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = BharatTextPrimary)
          }
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = "About Us",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = BharatTextPrimary
          )
        }
      }
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(64.dp)
              .clip(CircleShape)
              .background(BharatPurpleContainer),
            contentAlignment = Alignment.Center
          ) {
            Icon(Icons.Default.Storefront, contentDescription = null, tint = BharatPurplePrimary)
          }
          Spacer(modifier = Modifier.width(14.dp))
          Column {
            Text(
              text = "BreakQ",
              style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
              color = BharatPurplePrimary
            )
            Text(
              text = "v${BuildConfig.VERSION_NAME}",
              color = BharatTextMuted,
              fontSize = 12.sp
            )
          }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "Bringing your neighbourhood kirana online.",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = BharatTextPrimary
        )
        Text(
          text = "BreakQ helps you skip the queue at your local grocery shop. Browse products from nearby stores, place an order in seconds, and pick it up when it's ready — no waiting in line, no last-minute surprises.",
          color = BharatTextSecondary,
          fontSize = 14.sp
        )
        Text(
          text = "Every shop on BreakQ is a real local kirana. Supporting your neighbourhood store keeps money in the community and makes daily essentials more affordable.",
          color = BharatTextSecondary,
          fontSize = 14.sp
        )
        Text(
          text = "Owners can join BreakQ from within the app in a few minutes and start taking online orders the same day.",
          color = BharatTextSecondary,
          fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
          text = "Made in India with \u2764\uFE0F",
          color = BharatTextMuted,
          fontSize = 12.sp
        )
      }
    }
  }
}

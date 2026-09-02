package com.kks.bharatkirana.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.NotificationImportant
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kks.bharatkirana.ui.theme.BharatBackground
import com.kks.bharatkirana.ui.theme.BharatPurpleContainer
import com.kks.bharatkirana.ui.theme.BharatPurplePrimary
import com.kks.bharatkirana.ui.theme.BharatTextMuted
import com.kks.bharatkirana.ui.theme.BharatTextPrimary
import com.kks.bharatkirana.ui.theme.BharatTextSecondary

/**
 * Local-only preference toggles for now. They control which categories of
 * FCM notifications the app surfaces (order updates are always on — they
 * are the point of the app; promos/announcements are opt-outable). Wiring
 * into an actual FCM topic subscription happens in a follow-up.
 */
@Composable
fun NotificationPreferencesScreen(
  onBackClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  var orderUpdates by remember { mutableStateOf(true) }
  var promotions by remember { mutableStateOf(true) }
  var announcements by remember { mutableStateOf(true) }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(BharatBackground)
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
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
            text = "Notification Preferences",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = BharatTextPrimary
          )
        }
      }
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White),
          border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column {
            PrefRow(
              icon = Icons.Default.NotificationImportant,
              title = "Order updates",
              subtitle = "Alerts when your order status changes",
              checked = orderUpdates,
              enabled = false,
              onCheckedChange = { orderUpdates = it }
            )
            HorizontalDivider(color = Color(0xFFF1F5F9))
            PrefRow(
              icon = Icons.Default.LocalOffer,
              title = "Promotions & offers",
              subtitle = "New coupons, discounts and deals",
              checked = promotions,
              onCheckedChange = { promotions = it }
            )
            HorizontalDivider(color = Color(0xFFF1F5F9))
            PrefRow(
              icon = Icons.Default.Campaign,
              title = "Announcements",
              subtitle = "New shops in your area and app news",
              checked = announcements,
              onCheckedChange = { announcements = it }
            )
          }
        }
        Text(
          text = "Order updates are always on so you don't miss delivery details.",
          fontSize = 11.sp,
          color = BharatTextMuted
        )
      }
    }
  }
}

@Composable
private fun PrefRow(
  icon: ImageVector,
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  enabled: Boolean = true
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .size(36.dp)
        .clip(CircleShape)
        .background(BharatPurpleContainer),
      contentAlignment = Alignment.Center
    ) {
      Icon(icon, contentDescription = null, tint = BharatPurplePrimary, modifier = Modifier.size(20.dp))
    }
    Spacer(modifier = Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(text = title, fontWeight = FontWeight.SemiBold, color = BharatTextPrimary, fontSize = 14.sp)
      Text(text = subtitle, color = BharatTextSecondary, fontSize = 11.sp)
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      enabled = enabled,
      colors = SwitchDefaults.colors(
        checkedThumbColor = Color.White,
        checkedTrackColor = BharatPurplePrimary,
        uncheckedThumbColor = Color.White,
        uncheckedTrackColor = Color(0xFFCBD5E1)
      )
    )
  }
}

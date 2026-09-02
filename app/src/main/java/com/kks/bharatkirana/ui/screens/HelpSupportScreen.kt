package com.kks.bharatkirana.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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

@Composable
fun HelpSupportScreen(
  hasWhatsappSupport: Boolean,
  onBackClick: () -> Unit,
  onOpenWhatsapp: () -> Unit,
  onOpenOrders: () -> Unit,
  modifier: Modifier = Modifier
) {
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
            text = "Help & Support",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = BharatTextPrimary
          )
        }
      }
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White),
          border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column {
            SupportOptionRow(
              icon = Icons.AutoMirrored.Filled.Chat,
              iconTint = Color(0xFF059669),
              iconBackground = Color(0xFFECFDF5),
              title = if (hasWhatsappSupport) "Chat on WhatsApp" else "WhatsApp support unavailable",
              subtitle = if (hasWhatsappSupport)
                "Talk to us about orders, payments, or the app"
              else
                "Support number isn't configured right now",
              enabled = hasWhatsappSupport,
              onClick = onOpenWhatsapp
            )
            HorizontalDivider(color = Color(0xFFF1F5F9))
            SupportOptionRow(
              icon = Icons.Default.ReceiptLong,
              iconTint = BharatPurplePrimary,
              iconBackground = BharatPurpleContainer,
              title = "Issue with an order?",
              subtitle = "Open your order to raise it directly with the shop",
              enabled = true,
              onClick = onOpenOrders
            )
          }
        }
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White),
          border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.SupportAgent, contentDescription = null, tint = BharatPurplePrimary)
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "Frequently asked",
                fontWeight = FontWeight.Bold,
                color = BharatTextPrimary
              )
            }
            Spacer(modifier = Modifier.height(10.dp))
            FaqRow(
              question = "How long does an order take?",
              answer = "Most shops mark orders ready within 10–20 minutes. You'll get a notification the moment yours is ready for pickup."
            )
            Spacer(modifier = Modifier.height(8.dp))
            FaqRow(
              question = "Can I cancel an order?",
              answer = "You can cancel from the order details page until the shop starts preparing it."
            )
            Spacer(modifier = Modifier.height(8.dp))
            FaqRow(
              question = "How do I collect my order?",
              answer = "Open the order once it's Ready for Pickup and show the QR code at the shop counter."
            )
          }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Refresh, contentDescription = null, tint = BharatTextMuted, modifier = Modifier.size(14.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = "Please pull-to-refresh Home if information looks out of date.",
            fontSize = 11.sp,
            color = BharatTextMuted
          )
        }
      }
    }
  }
}

@Composable
private fun SupportOptionRow(
  icon: ImageVector,
  iconTint: Color,
  iconBackground: Color,
  title: String,
  subtitle: String,
  enabled: Boolean,
  onClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
      .padding(horizontal = 16.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .size(40.dp)
        .clip(CircleShape)
        .background(iconBackground),
      contentAlignment = Alignment.Center
    ) {
      Icon(icon, contentDescription = null, tint = iconTint)
    }
    Spacer(modifier = Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(text = title, fontWeight = FontWeight.SemiBold, color = if (enabled) BharatTextPrimary else BharatTextMuted, fontSize = 14.sp)
      Text(text = subtitle, color = BharatTextSecondary, fontSize = 11.sp)
    }
  }
}

@Composable
private fun FaqRow(question: String, answer: String) {
  Column {
    Text(text = question, fontWeight = FontWeight.SemiBold, color = BharatTextPrimary, fontSize = 13.sp)
    Text(text = answer, color = BharatTextSecondary, fontSize = 12.sp)
  }
}

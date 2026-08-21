package com.kks.bharatkirana.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kks.bharatkirana.data.model.SubscriptionTier
import com.kks.bharatkirana.data.model.VendorSubscription
import com.kks.bharatkirana.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
  tiers: List<SubscriptionTier>,
  currentSubscription: VendorSubscription?,
  currentProductCount: Int,
  onBackClick: () -> Unit,
  onUpgradeClick: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text("Manage Plan", fontWeight = FontWeight.Bold, color = BharatTextPrimary)
        },
        navigationIcon = {
          IconButton(onClick = onBackClick) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = BharatPurplePrimary)
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
      )
    },
    modifier = modifier.fillMaxSize()
  ) { pv ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .background(Color(0xFFF9FAFB))
        .padding(pv),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      item {
        val activeTier = tiers.firstOrNull { it.id == currentSubscription?.tierId }
        val cap = activeTier?.itemCap ?: 10
        val capText = if (cap == -1) "Unlimited" else "$currentProductCount / $cap products used"
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .background(Brush.horizontalGradient(listOf(BharatPurpleDark, BharatPurplePrimary)))
              .padding(20.dp)
          ) {
            Column {
              Text("YOUR CURRENT PLAN", fontSize = 11.sp, color = Color.White.copy(alpha = 0.75f), fontWeight = FontWeight.Bold)
              Spacer(Modifier.height(6.dp))
              Text(
                text = activeTier?.displayName ?: "Free",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = Color.White
              )
              Spacer(Modifier.height(4.dp))
              Text(capText, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
            }
          }
        }
      }

      item {
        Text(
          "Choose a plan",
          fontWeight = FontWeight.Bold,
          color = BharatTextPrimary,
          fontSize = 16.sp,
          modifier = Modifier.padding(top = 4.dp)
        )
      }

      items(tiers) { tier ->
        val isCurrent = tier.id == currentSubscription?.tierId
        TierCard(tier, isCurrent, onUpgradeClick = { onUpgradeClick(tier.id) })
      }

      item {
        Text(
          text = "Payments are handled via WhatsApp for now — our team will confirm and switch your plan within a few hours. Auto-payment (Razorpay) coming soon.",
          fontSize = 11.sp,
          color = BharatTextMuted,
          modifier = Modifier.padding(top = 8.dp)
        )
      }
    }
  }
}

@Composable
private fun TierCard(
  tier: SubscriptionTier,
  isCurrent: Boolean,
  onUpgradeClick: () -> Unit
) {
  val accent = when (tier.id) {
    "pro" -> Color(0xFFF59E0B)
    "standard" -> BharatPurplePrimary
    "starter" -> BharatGreen
    else -> BharatTextSecondary
  }

  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(if (isCurrent) 2.dp else 1.dp, if (isCurrent) accent else Color(0xFFE5E7EB))
  ) {
    Column(modifier = Modifier.padding(18.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              tier.displayName,
              fontWeight = FontWeight.ExtraBold,
              fontSize = 18.sp,
              color = BharatTextPrimary
            )
            if (tier.id == "pro") {
              Spacer(Modifier.width(6.dp))
              Icon(Icons.Default.Star, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            }
            if (isCurrent) {
              Spacer(Modifier.width(8.dp))
              Surface(color = accent.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                Text(
                  "CURRENT",
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = accent,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
              }
            }
          }
          Spacer(Modifier.height(2.dp))
          Row(verticalAlignment = Alignment.Bottom) {
            Text(
              text = if (tier.priceRupees == 0) "Free" else "₹${tier.priceRupees}",
              fontWeight = FontWeight.ExtraBold,
              fontSize = 22.sp,
              color = accent
            )
            if (tier.priceRupees > 0) {
              Text(" /month", fontSize = 12.sp, color = BharatTextSecondary)
            }
          }
        }
      }

      Spacer(Modifier.height(12.dp))
      HorizontalDivider(color = Color(0xFFF1F5F9))
      Spacer(Modifier.height(12.dp))

      tier.features.forEach { line ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
          Icon(Icons.Default.Check, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
          Spacer(Modifier.width(8.dp))
          Text(line, fontSize = 13.sp, color = BharatTextPrimary)
        }
      }

      if (!isCurrent) {
        Spacer(Modifier.height(14.dp))
        Button(
          onClick = onUpgradeClick,
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(containerColor = accent)
        ) {
          Icon(Icons.Default.Chat, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text(
            text = if (tier.priceRupees == 0) "Downgrade to Free" else "Upgrade via WhatsApp",
            color = Color.White,
            fontWeight = FontWeight.Bold
          )
        }
      }
    }
  }
}

package com.kks.bharatkirana.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
  checkoutStatusText: String? = null,
  checkoutIsBusy: Boolean = false,
  checkoutSucceeded: Boolean = false,
  onCheckoutMessageDismiss: () -> Unit = {},
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
              Text("$currentProductCount products listed", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
              if (currentSubscription?.expiresAt != null) {
                Text(
                  text = "Renews ${currentSubscription.expiresAt.take(10)}",
                  color = Color.White.copy(alpha = 0.75f),
                  fontSize = 11.sp,
                  modifier = Modifier.padding(top = 2.dp)
                )
              }
            }
          }
        }
      }

      // Checkout status banner (creating order / verifying / success / failure).
      if (!checkoutStatusText.isNullOrBlank()) {
        item {
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = when {
              checkoutSucceeded -> Color(0xFFDCFCE7)
              checkoutIsBusy -> BharatPurpleContainer
              else -> Color(0xFFFEF3C7)
            },
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
              if (checkoutIsBusy) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = BharatPurplePrimary, strokeWidth = 2.dp)
              } else {
                Icon(
                  imageVector = if (checkoutSucceeded) Icons.Default.CheckCircle else Icons.Default.Lock,
                  contentDescription = null,
                  tint = if (checkoutSucceeded) Color(0xFF10B981) else Color(0xFFD97706),
                  modifier = Modifier.size(20.dp)
                )
              }
              Spacer(Modifier.width(10.dp))
              Text(checkoutStatusText, modifier = Modifier.weight(1f), fontSize = 13.sp, color = BharatTextPrimary)
              if (!checkoutIsBusy) {
                TextButton(onClick = onCheckoutMessageDismiss) {
                  Text("OK", color = BharatPurplePrimary, fontWeight = FontWeight.Bold)
                }
              }
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
        TierCard(
          tier = tier,
          isCurrent = tier.id == currentSubscription?.tierId,
          isBusy = checkoutIsBusy,
          onUpgradeClick = { onUpgradeClick(tier.id) }
        )
      }

      item {
        Text(
          text = "Payments are processed securely by Razorpay. Your plan activates the moment payment is confirmed. Cancel anytime \u2014 you keep access until the end of the billing month.",
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
  isBusy: Boolean,
  onUpgradeClick: () -> Unit
) {
  val accent = when (tier.id) {
    "pro" -> Color(0xFF7C3AED)
    "advance" -> BharatPurplePrimary
    "founding" -> BharatGreen
    else -> BharatTextSecondary
  }

  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(if (isCurrent) 2.dp else 1.dp, if (isCurrent) accent else Color(0xFFE5E7EB))
  ) {
    Column(modifier = Modifier.padding(18.dp)) {
      // Limited-time ribbon for the Founding Vendor intro offer.
      if (tier.isLimitedTime) {
        Surface(color = BharatGreen, shape = RoundedCornerShape(6.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
          ) {
            Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text("LIMITED TIME · FIRST 3 MONTHS", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
          }
        }
        Spacer(Modifier.height(10.dp))
      }

      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(tier.displayName, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = BharatTextPrimary)
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

      if (tier.tagline.isNotBlank()) {
        Text(tier.tagline, fontSize = 12.sp, color = BharatTextSecondary, modifier = Modifier.padding(top = 2.dp))
      }

      Spacer(Modifier.height(6.dp))
      Row(verticalAlignment = Alignment.Bottom) {
        Text(
          text = if (tier.priceRupees == 0) "Free" else "₹${tier.priceRupees}",
          fontWeight = FontWeight.ExtraBold,
          fontSize = 24.sp,
          color = accent
        )
        if (tier.priceRupees > 0) {
          Text(" /month", fontSize = 12.sp, color = BharatTextSecondary, modifier = Modifier.padding(bottom = 4.dp))
        }
      }

      Spacer(Modifier.height(12.dp))
      HorizontalDivider(color = Color(0xFFF1F5F9))
      Spacer(Modifier.height(12.dp))

      tier.features.forEach { line ->
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 3.dp)) {
          Icon(
            Icons.Default.Check,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(14.dp)
          )
          Spacer(Modifier.width(8.dp))
          Text(line, fontSize = 13.sp, color = BharatTextPrimary)
        }
      }

      if (!isCurrent && tier.priceRupees > 0) {
        Spacer(Modifier.height(14.dp))
        Button(
          onClick = onUpgradeClick,
          enabled = !isBusy,
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(containerColor = accent)
        ) {
          Text(
            text = "Subscribe for ₹${tier.priceRupees}/month",
            color = Color.White,
            fontWeight = FontWeight.Bold
          )
        }
        Text(
          text = "Secure payment via Razorpay",
          fontSize = 10.sp,
          color = BharatTextMuted,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
        )
      }
    }
  }
}

package com.kks.bharatkirana.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.kks.bharatkirana.ui.theme.BharatGreen
import com.kks.bharatkirana.ui.theme.BharatPurpleContainer
import com.kks.bharatkirana.ui.theme.BharatPurplePrimary
import com.kks.bharatkirana.ui.theme.BharatTextPrimary
import com.kks.bharatkirana.ui.theme.BharatTextSecondary

@Composable
fun HowBreakQWorksScreen(
  onBackClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  SupportDocScaffold(title = "How BreakQ Works", onBackClick = onBackClick, modifier = modifier) {
    SupportIntroCard(
      icon = Icons.Default.Info,
      text = "BreakQ helps you skip the queue at your local kirana shop. Order what you need, get notified when it's ready, walk in, scan the QR, and go."
    )
    SupportStepCard(
      steps = listOf(
        "1. Browse nearby shops" to "Home shows shops around your delivery address. Distance and open/close hours are updated live.",
        "2. Add items to your cart" to "Each cart is scoped to one shop at a time. If you switch shops, we'll ask before clearing your cart.",
        "3. Place your order" to "Confirm the address and place the order. You'll get a notification the moment the shop accepts it.",
        "4. Track progress" to "Order goes through Placed → Confirmed → Preparing → Ready for Pickup. You'll get a push at each step.",
        "5. Show the pickup QR" to "When the status turns Ready for Pickup, open the order and show the QR at the shop counter to collect."
      )
    )
    SupportIntroCard(
      icon = Icons.Default.CheckCircle,
      accent = BharatGreen,
      text = "No delivery, no wait, no cash counter fumbling. Just a scannable code and your order."
    )
  }
}

@Composable
fun CustomerGuidelinesScreen(
  onBackClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  SupportDocScaffold(title = "Customer Guidelines", onBackClick = onBackClick, modifier = modifier) {
    SupportIntroCard(
      icon = Icons.Default.Info,
      text = "A few ground rules that keep BreakQ working smoothly for you and the shops on the platform."
    )
    SupportBulletCard(
      title = "Placing orders",
      bullets = listOf(
        "Use a real name and reachable phone number so the shop can call if something is unclear.",
        "Only order what you actually plan to pick up — abandoned pickups hurt small shops.",
        "Read the item's weight/pack size before adding to cart. What you see is what you pay."
      )
    )
    SupportBulletCard(
      title = "Payments",
      bullets = listOf(
        "Payments are handled at the shop counter unless the app explicitly says otherwise.",
        "Digital receipts appear in the app after the shop marks the order Completed.",
        "Never share your pickup QR with someone who isn't collecting the order for you."
      )
    )
    SupportBulletCard(
      title = "At the shop",
      bullets = listOf(
        "Show the pickup QR on your phone; the shopkeeper scans it to confirm collection.",
        "Check your items before leaving the counter. If something is missing, tell the shop right away.",
        "Be respectful of the shop staff — many are small family businesses."
      )
    )
    SupportIntroCard(
      icon = Icons.Default.Info,
      text = "Repeated no-shows or abusive behaviour may result in your account being restricted."
    )
  }
}

@Composable
fun CancellationPolicyScreen(
  onBackClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  SupportDocScaffold(title = "Cancellation Policy", onBackClick = onBackClick, modifier = modifier) {
    SupportIntroCard(
      icon = Icons.Default.Info,
      text = "Whether an order can be cancelled depends on how far along the shop is with preparing it. The rules are enforced by the app and by our servers — a cancel button will only appear when cancellation is genuinely allowed."
    )
    CancellationRuleCard(
      status = "Order Placed",
      allowed = true,
      description = "You can cancel freely. The shop hasn't started anything yet, so no one is inconvenienced."
    )
    CancellationRuleCard(
      status = "Order Confirmed",
      allowed = true,
      warning = true,
      description = "You can still cancel, but the shop has already accepted your order. Please cancel only if you truly need to — repeated cancellations after confirmation may lead to account restrictions."
    )
    CancellationRuleCard(
      status = "Preparing",
      allowed = false,
      description = "Cancellation is not allowed. The shop is actively packing your items and may not be able to return them to inventory."
    )
    CancellationRuleCard(
      status = "Ready for Pickup",
      allowed = false,
      description = "Cancellation is not allowed. Your order is packed and waiting at the counter — please collect it."
    )
    CancellationRuleCard(
      status = "Completed",
      allowed = false,
      description = "The order is already picked up. If something is wrong with the items, contact the shop directly or reach us via Help & Support."
    )
  }
}

// --------------------------------------------------------------------------
// Shared support-doc primitives
// --------------------------------------------------------------------------

@Composable
private fun SupportDocScaffold(
  title: String,
  onBackClick: () -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit
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
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = BharatTextPrimary
          )
        }
      }
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        content()
        Spacer(modifier = Modifier.height(24.dp))
      }
    }
  }
}

@Composable
private fun SupportIntroCard(
  icon: ImageVector,
  text: String,
  accent: Color = BharatPurplePrimary
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier.padding(16.dp),
      verticalAlignment = Alignment.Top
    ) {
      Box(
        modifier = Modifier.size(36.dp).clip(CircleShape).background(BharatPurpleContainer),
        contentAlignment = Alignment.Center
      ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
      }
      Spacer(modifier = Modifier.width(12.dp))
      Text(
        text = text,
        fontSize = 14.sp,
        color = BharatTextPrimary,
        modifier = Modifier.padding(top = 6.dp)
      )
    }
  }
}

@Composable
private fun SupportStepCard(steps: List<Pair<String, String>>) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      steps.forEach { (title, body) ->
        Column {
          Text(text = title, fontWeight = FontWeight.Bold, color = BharatTextPrimary, fontSize = 14.sp)
          Spacer(modifier = Modifier.height(2.dp))
          Text(text = body, color = BharatTextSecondary, fontSize = 13.sp)
        }
      }
    }
  }
}

@Composable
private fun SupportBulletCard(title: String, bullets: List<String>) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(text = title, fontWeight = FontWeight.Bold, color = BharatTextPrimary, fontSize = 14.sp)
      Spacer(modifier = Modifier.height(8.dp))
      bullets.forEach { bullet ->
        Row(modifier = Modifier.padding(vertical = 3.dp)) {
          Text("•  ", color = BharatPurplePrimary, fontSize = 13.sp)
          Text(bullet, color = BharatTextSecondary, fontSize = 13.sp)
        }
      }
    }
  }
}

@Composable
private fun CancellationRuleCard(
  status: String,
  allowed: Boolean,
  description: String,
  warning: Boolean = false
) {
  val icon = if (allowed) Icons.Default.CheckCircle else Icons.Default.Cancel
  val accent = when {
    warning -> Color(0xFFD97706)
    allowed -> BharatGreen
    else -> Color(0xFFDC2626)
  }
  val chipBg = when {
    warning -> Color(0xFFFEF3C7)
    allowed -> Color(0xFFDCFCE7)
    else -> Color(0xFFFEE2E2)
  }
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier.size(36.dp).clip(CircleShape).background(chipBg),
          contentAlignment = Alignment.Center
        ) {
          Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(text = status, fontWeight = FontWeight.Bold, color = BharatTextPrimary, fontSize = 14.sp)
          Text(
            text = when {
              warning -> "Cancel allowed · warning"
              allowed -> "Cancel allowed"
              else -> "Cancel not allowed"
            },
            color = accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
          )
        }
      }
      Spacer(modifier = Modifier.height(10.dp))
      Text(text = description, color = BharatTextSecondary, fontSize = 13.sp)
    }
  }
}

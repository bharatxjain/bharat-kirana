package com.kks.bharatkirana.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kks.bharatkirana.ui.theme.BharatGreen
import com.kks.bharatkirana.ui.theme.BharatPurpleAccent
import com.kks.bharatkirana.ui.theme.BharatPurpleDark
import com.kks.bharatkirana.ui.theme.BharatPurpleLight
import com.kks.bharatkirana.ui.theme.BharatPurplePrimary
import kotlinx.coroutines.delay

@Composable
fun SignupSplashScreen(
  userEmail: String,
  role: String = "Customer",
  onContinue: () -> Unit,
  modifier: Modifier = Modifier
) {
  val isAdmin = role.equals("Store Admin", ignoreCase = true) ||
    role.equals("Admin", ignoreCase = true) ||
    role.equals("Super Admin", ignoreCase = true)
  val scaleAnim = remember { Animatable(0.7f) }
  val progressAnim = remember { Animatable(0f) }

  val infiniteTransition = rememberInfiniteTransition(label = "pulse")
  val pulseScale by infiniteTransition.animateFloat(
    initialValue = 1f,
    targetValue = 1.08f,
    animationSpec = infiniteRepeatable(
      animation = tween(1200, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "pulseScale"
  )

  LaunchedEffect(Unit) {
    scaleAnim.animateTo(
      targetValue = 1f,
      animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
    )
    progressAnim.animateTo(
      targetValue = 1f,
      animationSpec = tween(durationMillis = 2200, easing = LinearEasing)
    )
    delay(200)
    onContinue()
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          colors = if (isAdmin) {
            listOf(Color(0xFF2E1065), Color(0xFF4C1D95), Color(0xFF6D28D9))
          } else {
            listOf(BharatPurpleDark, BharatPurplePrimary, Color(0xFF8B5CF6))
          }
        )
      )
      .statusBarsPadding()
      .navigationBarsPadding()
      .padding(24.dp)
      .testTag("signup_splash_screen")
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .scale(scaleAnim.value),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      Spacer(modifier = Modifier.height(16.dp))

      // Central Brand Card
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
      ) {
        // Glowing Icon
        Box(
          modifier = Modifier
            .size(110.dp)
            .scale(pulseScale)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.15f)),
          contentAlignment = Alignment.Center
        ) {
          Box(
            modifier = Modifier
              .size(90.dp)
              .clip(CircleShape)
              .background(Color.White),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = if (isAdmin) Icons.Default.AdminPanelSettings else Icons.Default.Storefront,
              contentDescription = "BreakQ",
              tint = if (isAdmin) BharatPurpleDark else BharatPurplePrimary,
              modifier = Modifier.size(52.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Surface(
          shape = RoundedCornerShape(12.dp),
          color = if (isAdmin) Color(0xFFFBBF24) else BharatGreen,
          shadowElevation = 4.dp
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Default.CheckCircle,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = if (isAdmin) "ADMIN ACCESS GRANTED" else "ACCOUNT CREATED SUCCESSFULLY",
              color = Color.White,
              fontWeight = FontWeight.Bold,
              fontSize = 11.sp,
              letterSpacing = 0.5.sp
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
          text = if (isAdmin) "Welcome, Store Administrator!" else "Welcome to BreakQ!",
          style = MaterialTheme.typography.headlineMedium.copy(
            fontWeight = FontWeight.ExtraBold,
            fontSize = 24.sp
          ),
          color = Color.White,
          textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
          text = "Logged in as $userEmail",
          style = MaterialTheme.typography.bodyMedium.copy(
            fontWeight = FontWeight.Medium
          ),
          color = Color.White.copy(alpha = 0.9f),
          textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Feature Highlights
        Surface(
          shape = RoundedCornerShape(20.dp),
          color = Color.White.copy(alpha = 0.12f),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
          ) {
            SplashFeatureRow(
              icon = Icons.Default.ShoppingBag,
              title = if (isAdmin) "Live Inventory & Stock Sync" else "100% Authentic Kirana Staples",
              subtitle = if (isAdmin) "Manage real-time prices & stock status" else "Fresh Atta, Dal, Spices, Dairy & daily needs"
            )
            SplashFeatureRow(
              icon = Icons.Default.QrCode,
              title = if (isAdmin) "Counter QR Code Scanner" else "15-Min Express Store Pickup",
              subtitle = if (isAdmin) "Verify customer order codes in seconds" else "Skip long queues with your digital pickup pass"
            )
            SplashFeatureRow(
              icon = Icons.Default.FlashOn,
              title = "Instant Supabase Backend",
              subtitle = "Orders and status changes sync across devices"
            )
          }
        }
      }

      // Bottom Progress & Action
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Preparing your storefront...",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp
          )
          Text(
            text = "${(progressAnim.value * 100).toInt()}%",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
          )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LinearProgressIndicator(
          progress = { progressAnim.value },
          modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp)),
          color = Color.White,
          trackColor = Color.White.copy(alpha = 0.25f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
          onClick = onContinue,
          modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("splash_continue_button"),
          shape = RoundedCornerShape(16.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = if (isAdmin) BharatPurpleDark else BharatPurplePrimary
          )
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
          ) {
            Text(
              text = if (isAdmin) "Open Admin Dashboard" else "Start Shopping",
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
              imageVector = Icons.Default.ArrowForward,
              contentDescription = null,
              modifier = Modifier.size(18.dp)
            )
          }
        }
      }
    }
  }
}

@Composable
private fun SplashFeatureRow(
  icon: ImageVector,
  title: String,
  subtitle: String
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.fillMaxWidth()
  ) {
    Box(
      modifier = Modifier
        .size(36.dp)
        .clip(CircleShape)
        .background(Color.White.copy(alpha = 0.2f)),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(20.dp)
      )
    }
    Spacer(modifier = Modifier.width(12.dp))
    Column {
      Text(
        text = title,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        color = Color.White
      )
      Text(
        text = subtitle,
        fontSize = 11.sp,
        color = Color.White.copy(alpha = 0.75f)
      )
    }
  }
}

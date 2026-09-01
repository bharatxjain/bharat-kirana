package com.kks.bharatkirana.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kks.bharatkirana.ui.theme.BharatBackground
import com.kks.bharatkirana.ui.theme.BharatPurpleContainer
import com.kks.bharatkirana.ui.theme.BharatPurplePrimary
import com.kks.bharatkirana.ui.theme.BharatTextPrimary
import com.kks.bharatkirana.ui.theme.BharatTextSecondary

@Composable
fun OrderNotFoundScreen(
  orderId: String,
  onBackClick: () -> Unit,
  onRefresh: () -> Unit
) {
  Surface(modifier = Modifier.fillMaxSize(), color = BharatBackground) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding()
    ) {
      androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(onClick = onBackClick) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = BharatTextPrimary)
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text("Order Details", fontWeight = FontWeight.Bold, color = BharatTextPrimary)
      }

      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        Box(
          modifier = Modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(BharatPurpleContainer),
          contentAlignment = Alignment.Center
        ) {
          Icon(Icons.Default.HourglassEmpty, null, tint = BharatPurplePrimary, modifier = Modifier.size(44.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
          text = "We're loading order #$orderId\u2026",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = BharatTextPrimary,
          textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = "If this order was placed on another device, tap Refresh to fetch it. Otherwise it may still be syncing.",
          fontSize = 13.sp,
          color = BharatTextSecondary,
          textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
          onClick = onRefresh,
          colors = ButtonDefaults.buttonColors(containerColor = BharatPurplePrimary),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.height(46.dp)
        ) {
          Icon(Icons.Default.Refresh, null, tint = Color.White, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Refresh", color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
          onClick = onBackClick,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.height(46.dp)
        ) { Text("Back", color = BharatPurplePrimary, fontWeight = FontWeight.SemiBold) }
      }
    }
  }
}

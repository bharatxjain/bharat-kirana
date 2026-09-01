package com.kks.bharatkirana.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kks.bharatkirana.data.model.Order
import com.kks.bharatkirana.ui.components.CustomQrCodePattern
import com.kks.bharatkirana.ui.components.OrderTimelineView
import com.kks.bharatkirana.ui.theme.BharatBackground
import com.kks.bharatkirana.ui.theme.BharatGreen
import com.kks.bharatkirana.ui.theme.BharatPurpleContainer
import com.kks.bharatkirana.ui.theme.BharatPurpleDark
import com.kks.bharatkirana.ui.theme.BharatPurpleLight
import com.kks.bharatkirana.ui.theme.BharatPurplePrimary
import com.kks.bharatkirana.ui.theme.BharatTextMuted
import com.kks.bharatkirana.ui.theme.BharatTextPrimary
import com.kks.bharatkirana.ui.theme.BharatTextSecondary

@Composable
fun OrderPlacedScreen(
  order: Order,
  onViewOrdersClick: () -> Unit,
  onHomeClick: () -> Unit,
  onTrackOrderClick: () -> Unit = onViewOrdersClick,
  shopDistanceLabel: String? = null,
  modifier: Modifier = Modifier
) {
  Surface(
    modifier = modifier.fillMaxSize(),
    color = BharatBackground
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding()
    ) {
      Column(
        modifier = Modifier
          .weight(1f)
          .verticalScroll(rememberScrollState())
          .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Success Checkmark
        Box(
          modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(BharatPurpleLight),
          contentAlignment = Alignment.Center
        ) {
          Box(
            modifier = Modifier
              .size(60.dp)
              .clip(CircleShape)
              .background(BharatPurplePrimary),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.Check,
              contentDescription = "Success",
              tint = Color.White,
              modifier = Modifier.size(36.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
          text = "Order Placed Successfully!",
          style = MaterialTheme.typography.headlineSmall.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp
          ),
          color = BharatTextPrimary,
          textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        Surface(
          shape = RoundedCornerShape(8.dp),
          color = BharatPurpleContainer
        ) {
          Text(
            text = "Order #${order.id}",
            color = BharatPurpleDark,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
          )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // QR Code Pickup Card
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White),
          elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Text(
              text = "Pickup Verification QR Code",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = BharatTextPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
              text = "Show this QR code at the store counter.",
              style = MaterialTheme.typography.bodySmall,
              color = BharatTextSecondary,
              textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Box(
              modifier = Modifier
                .size(190.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(2.dp, BharatPurpleContainer, RoundedCornerShape(16.dp))
                .background(Color.White)
                .padding(14.dp),
              contentAlignment = Alignment.Center
            ) {
              CustomQrCodePattern(tint = BharatPurpleDark)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
              text = order.backupCode,
              style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 4.sp
              ),
              color = BharatPurplePrimary
            )
            
            Text(
              text = "6-Digit Backup Verification Code",
              style = MaterialTheme.typography.labelSmall,
              color = BharatTextSecondary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
              text = "Valid for Counter Pickup • ${order.storeName}",
              style = MaterialTheme.typography.labelSmall,
              color = BharatTextMuted
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Store and Slot Card
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(BharatPurpleContainer),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.Storefront,
                contentDescription = null,
                tint = BharatPurplePrimary,
                modifier = Modifier.size(24.dp)
              )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(
                text = order.storeName,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = BharatTextPrimary
              )
              Text(
                text = order.storeAddress,
                style = MaterialTheme.typography.bodySmall,
                color = BharatTextSecondary
              )
              Text(
                text = order.expectedPickupTime,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = BharatPurplePrimary
              )
              if (!shopDistanceLabel.isNullOrBlank() && shopDistanceLabel != "---") {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Default.LocationOn, contentDescription = null, tint = BharatPurplePrimary, modifier = Modifier.size(12.dp))
                  Spacer(modifier = Modifier.width(3.dp))
                  Text(
                    text = "$shopDistanceLabel from you",
                    style = MaterialTheme.typography.bodySmall,
                    color = BharatTextSecondary
                  )
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Order Timeline
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text(
              text = "Live Order Status",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = BharatTextPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))
            OrderTimelineView(timeline = order.timeline)
          }
        }

        Spacer(modifier = Modifier.height(24.dp))
      }

      // Bottom Buttons
      Surface(
        color = Color.White,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Button(
            onClick = onTrackOrderClick,
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
              .testTag("track_order_button"),
            colors = ButtonDefaults.buttonColors(containerColor = BharatPurplePrimary),
            shape = RoundedCornerShape(12.dp)
          ) {
            Icon(
              imageVector = Icons.Default.LocationOn,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Track Order",
              fontWeight = FontWeight.Bold,
              fontSize = 15.sp,
              color = Color.White
            )
          }

          OutlinedButton(
            onClick = onViewOrdersClick,
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
              .testTag("view_all_orders_button"),
            shape = RoundedCornerShape(12.dp)
          ) {
            Icon(
              imageVector = Icons.Default.ReceiptLong,
              contentDescription = null,
              tint = BharatPurplePrimary,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "View All Orders",
              fontWeight = FontWeight.SemiBold,
              fontSize = 15.sp,
              color = BharatPurplePrimary
            )
          }

          OutlinedButton(
            onClick = onHomeClick,
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
              .testTag("back_to_home_button"),
            shape = RoundedCornerShape(12.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Home,
              contentDescription = null,
              tint = BharatPurplePrimary,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Back to Home",
              fontWeight = FontWeight.SemiBold,
              fontSize = 15.sp,
              color = BharatPurplePrimary
            )
          }
        }
      }
    }
  }
}

package com.kks.bharatkirana.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kks.bharatkirana.data.model.Order
import com.kks.bharatkirana.data.model.OrderStatus
import com.kks.bharatkirana.ui.components.CustomQrCodePattern
import com.kks.bharatkirana.ui.components.OrderTimelineView
import com.kks.bharatkirana.ui.theme.*

@Composable
fun OrderDetailsScreen(
  order: Order,
  onBackClick: () -> Unit,
  onReorder: (Order) -> Unit,
  onRateShop: (shopId: String, orderId: String, rating: Int, review: String) -> Unit = { _, _, _, _ -> },
  onCancelOrder: (String) -> Unit = {},
  modifier: Modifier = Modifier
) {
  val subtotal = order.items.sumOf { it.totalPrice }
  val discount = if (subtotal > 200) 15 else 0

  var ratingValue by remember { mutableIntStateOf(0) }
  var reviewText by remember { mutableStateOf("") }
  var showRatingForm by remember { mutableStateOf(order.status == OrderStatus.COMPLETED) }
  var ratingSubmitted by remember { mutableStateOf(false) }
  var ratingDismissed by remember { mutableStateOf(false) }
  var showCancelDialog by remember { mutableStateOf(false) }

  val canCancel = order.status == OrderStatus.PLACED || order.status == OrderStatus.PREPARING

  if (showCancelDialog) {
    AlertDialog(
      onDismissRequest = { showCancelDialog = false },
      title = { Text("Cancel this order?", fontWeight = FontWeight.Bold) },
      text = { Text("Order #${order.id} will be cancelled. You cannot undo this. The shop will be notified.") },
      confirmButton = {
        Button(
          onClick = {
            showCancelDialog = false
            onCancelOrder(order.id)
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
        ) { Text("Yes, cancel") }
      },
      dismissButton = {
        OutlinedButton(onClick = { showCancelDialog = false }) { Text("Keep order") }
      }
    )
  }

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
      // Header
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(Color.White)
          .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(
          onClick = onBackClick,
          modifier = Modifier.testTag("order_details_back_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = BharatTextPrimary
          )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column {
          Text(
            text = "Order Details",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = BharatTextPrimary
          )
          Text(
            text = "Order #${order.id}",
            style = MaterialTheme.typography.bodySmall,
            color = BharatPurplePrimary,
            fontWeight = FontWeight.SemiBold
          )
        }
      }

      Column(
        modifier = Modifier
          .weight(1f)
          .verticalScroll(rememberScrollState())
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        // Status & Pickup QR Card
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "Order Status",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = BharatTextPrimary
              )
              Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (order.status == OrderStatus.COMPLETED) Color(0xFFF1F5F9) else BharatPurpleContainer
              ) {
                Text(
                  text = order.status.label,
                  color = if (order.status == OrderStatus.COMPLETED) BharatTextSecondary else BharatPurplePrimary,
                  fontWeight = FontWeight.Bold,
                  fontSize = 12.sp,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
              }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
              modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .padding(8.dp),
              contentAlignment = Alignment.Center
            ) {
              CustomQrCodePattern(tint = BharatPurpleDark)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
              text = "Pickup Verification Code: ${order.backupCode}",
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
              color = BharatTextPrimary
            )
            Text(
              text = "Show this QR code at store counter for instant collection.",
              style = MaterialTheme.typography.bodySmall,
              color = BharatTextMuted,
              textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
          }
        }

        if (canCancel) {
          OutlinedButton(
            onClick = { showCancelDialog = true },
            modifier = Modifier
              .fillMaxWidth()
              .testTag("cancel_order_button"),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDC2626))
          ) {
            Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Cancel Order", color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
          }
        }

        // Rating Section
        if (showRatingForm && !ratingSubmitted && !ratingDismissed) {
          Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = BharatPurpleContainer.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.width(24.dp))
                Text(text = "Rate your experience", fontWeight = FontWeight.Bold, color = BharatPurpleDark)
                IconButton(
                  onClick = { ratingDismissed = true },
                  modifier = Modifier.size(24.dp).testTag("dismiss_rating_button")
                ) {
                  Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = BharatPurpleDark, modifier = Modifier.size(18.dp))
                }
              }
              Spacer(modifier = Modifier.height(12.dp))
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(5) { index ->
                  val starIndex = index + 1
                  Icon(
                    imageVector = if (starIndex <= ratingValue) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = null,
                    tint = if (starIndex <= ratingValue) Color(0xFFFFB800) else BharatTextMuted,
                    modifier = Modifier.size(32.dp).clickable { ratingValue = starIndex }
                  )
                }
              }
              Spacer(modifier = Modifier.height(16.dp))
              OutlinedTextField(
                value = reviewText,
                onValueChange = { reviewText = it },
                placeholder = { Text("Write a quick review...", fontSize = 14.sp) },
                modifier = Modifier.fillMaxWidth().height(80.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BharatPurplePrimary)
              )
              Spacer(modifier = Modifier.height(16.dp))
              Button(
                onClick = {
                  onRateShop(order.shopId, order.id, ratingValue, reviewText)
                  ratingSubmitted = true
                },
                enabled = ratingValue > 0,
                colors = ButtonDefaults.buttonColors(containerColor = BharatPurplePrimary),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
              ) {
                Text("Submit Rating", fontWeight = FontWeight.Bold)
              }
              TextButton(
                onClick = { ratingDismissed = true },
                modifier = Modifier.testTag("maybe_later_rating_button")
              ) {
                Text("Maybe Later", color = BharatTextSecondary)
              }
            }
          }
        } else if (ratingSubmitted) {
          Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = BharatGreen.copy(alpha = 0.1f)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.CheckCircle, contentDescription = null, tint = BharatGreen)
              Spacer(modifier = Modifier.width(12.dp))
              Text("Thank you for your feedback!", fontWeight = FontWeight.Bold, color = BharatGreen)
            }
          }
        }

        // Timeline
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text(
              text = "Order Progress",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = BharatTextPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))
            OrderTimelineView(timeline = order.timeline)
          }
        }

        // Store Details
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
            Column(modifier = Modifier.weight(1f)) {
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
                text = "Pickup window: ${order.expectedPickupTime}",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = BharatPurplePrimary
              )
            }
          }
        }

        // Items Purchased
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text(
              text = "Items Ordered (${order.items.size})",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = BharatTextPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))

            order.items.forEach { item ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Box(
                  modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF8FAFC)),
                  contentAlignment = Alignment.Center
                ) {
                  if (item.product.localImageRes != null) {
                    Image(
                      painter = painterResource(id = item.product.localImageRes),
                      contentDescription = item.product.name,
                      modifier = Modifier.fillMaxSize(),
                      contentScale = ContentScale.Crop
                    )
                  } else {
                    Icon(
                      imageVector = Icons.Default.ShoppingCart,
                      contentDescription = null,
                      tint = BharatPurpleAccent,
                      modifier = Modifier.size(20.dp)
                    )
                  }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = item.product.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = BharatTextPrimary
                  )
                  Text(
                    text = "${item.selectedWeight.label} × ${item.quantity}",
                    style = MaterialTheme.typography.bodySmall,
                    color = BharatTextSecondary
                  )
                }

                Text(
                  text = "₹${item.totalPrice}",
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                  color = BharatTextPrimary
                )
              }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color(0xFFF1F5F9))
            Spacer(modifier = Modifier.height(12.dp))

            // Bill Breakdown
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text("Subtotal", color = BharatTextSecondary, fontSize = 14.sp)
              Text("₹$subtotal", color = BharatTextPrimary, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text("Handling Fee", color = BharatTextSecondary, fontSize = 14.sp)
              Text("₹5", color = BharatTextPrimary, fontSize = 14.sp)
            }
            if (discount > 0) {
              Spacer(modifier = Modifier.height(6.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text("Store Discount", color = BharatGreen, fontSize = 14.sp)
                Text("-₹$discount", color = BharatGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
              }
            }
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFFF1F5F9))
            Spacer(modifier = Modifier.height(10.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                "Total Amount Paid",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = BharatTextPrimary
              )
              Text(
                "₹${order.totalAmount}",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = BharatTextPrimary
              )
            }
          }
        }
      }

      // Reorder Footer
      Surface(
        color = Color.White,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
      ) {
        Box(modifier = Modifier.padding(16.dp)) {
          Button(
            onClick = { onReorder(order) },
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
              .testTag("order_details_reorder_button"),
            colors = ButtonDefaults.buttonColors(containerColor = BharatPurplePrimary),
            shape = RoundedCornerShape(12.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Refresh,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Reorder These Items",
              fontWeight = FontWeight.Bold,
              fontSize = 15.sp,
              color = Color.White
            )
          }
        }
      }
    }
  }
}

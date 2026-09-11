package com.kks.bharatkirana.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kks.bharatkirana.data.model.Order
import com.kks.bharatkirana.data.model.OrderStatus
import com.kks.bharatkirana.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(
  orders: List<Order>,
  onOrderClick: (Order) -> Unit,
  onReorder: (Order) -> Unit,
  onExploreClick: () -> Unit,
  onBackClick: (() -> Unit)? = null,
  isLoading: Boolean = false,
  errorMessage: String? = null,
  onRetry: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  // Full history. A previous take(10) silently hid older orders.
  val visibleOrders = orders

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = "My Orders",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = BharatTextPrimary
          )
        },
        navigationIcon = {
          if (onBackClick != null) {
            IconButton(onClick = onBackClick) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = BharatTextPrimary)
            }
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
      )
    },
    modifier = modifier.fillMaxSize()
  ) { paddingValues ->
    if (isLoading && visibleOrders.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(BharatBackground)
          .padding(paddingValues),
        contentAlignment = Alignment.Center
      ) {
        CircularProgressIndicator(color = BharatPurplePrimary, strokeWidth = 3.dp)
      }
    } else if (errorMessage != null && visibleOrders.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(BharatBackground)
          .padding(paddingValues),
        contentAlignment = Alignment.Center
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.padding(24.dp)
        ) {
          Text(
            text = "Couldn't load your orders",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = BharatTextPrimary
          )
          Text(
            text = errorMessage,
            color = BharatTextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
          )
          TextButton(onClick = onRetry) {
            Text("Retry", color = BharatPurplePrimary, fontWeight = FontWeight.Bold)
          }
        }
      }
    } else if (visibleOrders.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(BharatBackground)
          .padding(paddingValues),
        contentAlignment = Alignment.Center
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.padding(24.dp)
        ) {
          Box(
            modifier = Modifier
              .size(100.dp)
              .clip(CircleShape)
              .background(Color(0xFFF3E8FF)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.ReceiptLong,
              contentDescription = null,
              tint = BharatPurplePrimary,
              modifier = Modifier.size(48.dp)
            )
          }
          Spacer(modifier = Modifier.height(24.dp))
          Text(
            text = "No orders yet",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = BharatTextPrimary
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "Start shopping now!",
            style = MaterialTheme.typography.bodyMedium,
            color = BharatTextSecondary,
            textAlign = TextAlign.Center
          )
          Spacer(modifier = Modifier.height(32.dp))
          Button(
            onClick = onExploreClick,
            colors = ButtonDefaults.buttonColors(containerColor = BharatPurplePrimary),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.height(50.dp).width(200.dp)
          ) {
            Text("Start Shopping", fontWeight = FontWeight.Bold)
          }
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .background(BharatBackground)
          .padding(paddingValues),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        items(visibleOrders) { order ->
          OrderCard(
            order = order,
            onClick = { onOrderClick(order) },
            onReorder = { onReorder(order) }
          )
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
      }
    }
  }
}

@Composable
fun OrderCard(
  order: Order,
  onClick: () -> Unit,
  onReorder: () -> Unit
) {
  Card(
    onClick = onClick,
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      // Shop identity + terminal-status pill. Uses the real per-status
      // timestamp from the DB (Task 1 migration) — no more inferred "now".
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
          Box(
            modifier = Modifier
              .size(44.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(BharatPurpleContainer),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.Storefront,
              contentDescription = null,
              tint = BharatPurplePrimary,
              modifier = Modifier.size(22.dp)
            )
          }
          Spacer(modifier = Modifier.width(12.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = order.storeName.ifBlank { "Shop" },
              fontWeight = FontWeight.Bold,
              fontSize = 15.sp,
              color = BharatTextPrimary,
              maxLines = 1
            )
            if (order.storeAddress.isNotBlank()) {
              Text(
                text = order.storeAddress,
                fontSize = 11.sp,
                color = BharatTextSecondary,
                maxLines = 1
              )
            }
          }
        }
        val statusColor = when (order.status) {
          OrderStatus.COMPLETED -> BharatGreen
          OrderStatus.CANCELLED -> Color(0xFFDC2626)
          OrderStatus.READY_FOR_PICKUP -> Color(0xFF166534)
          else -> BharatPurplePrimary
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (order.status == OrderStatus.COMPLETED) {
            Icon(
              imageVector = Icons.Default.CheckCircle,
              contentDescription = null,
              tint = statusColor,
              modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
          }
          Text(
            text = order.status.label,
            color = statusColor,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 12.sp
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))
      HorizontalDivider(color = Color(0xFFF1F5F9))
      Spacer(modifier = Modifier.height(10.dp))

      // Line items summary (top 3, then "+N more") — matches Swiggy card style.
      order.items.take(3).forEach { cartItem ->
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
          Text(
            text = "${cartItem.quantity}x  ${cartItem.product.name.ifBlank { "Item" }}",
            fontSize = 13.sp,
            color = BharatTextPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 1
          )
        }
      }
      if (order.items.size > 3) {
        Text(
          text = "+${order.items.size - 3} more",
          fontSize = 11.sp,
          color = BharatTextSecondary,
          modifier = Modifier.padding(top = 2.dp)
        )
      }
      if (order.items.isEmpty()) {
        Text(
          text = "${order.items.size} items",
          fontSize = 12.sp,
          color = BharatTextSecondary
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      OutlinedButton(
        onClick = onReorder,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BharatPurplePrimary),
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 10.dp)
      ) {
        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = BharatPurplePrimary)
        Spacer(modifier = Modifier.width(6.dp))
        Text("Reorder", fontWeight = FontWeight.Bold, color = BharatPurplePrimary)
      }

      Spacer(modifier = Modifier.height(10.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = orderFooterLabel(order),
          fontSize = 11.sp,
          color = BharatTextSecondary
        )
        Text(
          text = "Total ₹${order.totalAmount}",
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          color = BharatTextPrimary
        )
      }
    }
  }
}

/**
 * Footer text for the past-order card. Prefers the real per-status timestamp
 * from the row so we no longer show a fake "Today, <now>" for a delivered or
 * cancelled order. Falls back to `orderDate` (the placement label) when the
 * relevant status timestamp is missing (older rows).
 */
private fun orderFooterLabel(order: Order): String {
  val terminalIso = when (order.status) {
    OrderStatus.COMPLETED -> order.completedAt
    OrderStatus.CANCELLED -> order.cancelledAt
    OrderStatus.READY_FOR_PICKUP -> order.readyAt
    OrderStatus.PREPARING -> order.preparingAt
    OrderStatus.CONFIRMED -> order.confirmedAt
    OrderStatus.PLACED -> order.createdAt.takeIf { it.isNotBlank() }
  }
  val prettyTime = terminalIso?.let { formatIsoPretty(it) }
  val prefix = when (order.status) {
    OrderStatus.COMPLETED -> "Picked up"
    OrderStatus.CANCELLED -> "Cancelled"
    OrderStatus.READY_FOR_PICKUP -> "Ready"
    OrderStatus.PREPARING -> "Preparing"
    OrderStatus.CONFIRMED -> "Confirmed"
    OrderStatus.PLACED -> "Placed"
  }
  return if (prettyTime != null) "$prefix: $prettyTime" else "$prefix: ${order.orderDate}"
}

/** Best-effort ISO-8601 → "Sep 8, 9:57 PM" formatter. */
private fun formatIsoPretty(iso: String): String? {
  if (iso.isBlank()) return null
  val patterns = listOf(
    "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
    "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
    "yyyy-MM-dd'T'HH:mm:ssXXX",
    "yyyy-MM-dd'T'HH:mm:ss'Z'"
  )
  val date = patterns.firstNotNullOfOrNull { p ->
    try {
      java.text.SimpleDateFormat(p, java.util.Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
      }.parse(iso)
    } catch (_: Exception) { null }
  } ?: return null
  return java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault()).format(date)
}

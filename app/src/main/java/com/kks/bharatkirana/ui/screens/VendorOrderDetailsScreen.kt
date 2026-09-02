package com.kks.bharatkirana.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.kks.bharatkirana.data.model.CartItem
import com.kks.bharatkirana.data.model.Order
import com.kks.bharatkirana.data.model.OrderStatus
import com.kks.bharatkirana.ui.theme.BharatBackground
import com.kks.bharatkirana.ui.theme.BharatGreen
import com.kks.bharatkirana.ui.theme.BharatPurpleAccent
import com.kks.bharatkirana.ui.theme.BharatPurpleContainer
import com.kks.bharatkirana.ui.theme.BharatPurplePrimary
import com.kks.bharatkirana.ui.theme.BharatTextMuted
import com.kks.bharatkirana.ui.theme.BharatTextPrimary
import com.kks.bharatkirana.ui.theme.BharatTextSecondary

@Composable
fun VendorOrderDetailsScreen(
  order: Order,
  onBackClick: () -> Unit,
  onAdvanceStatus: (OrderStatus) -> Unit,
  onCancelOrder: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  var showCancelDialog by remember { mutableStateOf(false) }

  val subtotal = order.items.sumOf { it.totalPrice }
  val hasDelta = subtotal > 0 && subtotal != order.totalAmount
  val extras = if (hasDelta) (order.totalAmount - subtotal).coerceAtLeast(0) else 0

  if (showCancelDialog) {
    AlertDialog(
      onDismissRequest = { showCancelDialog = false },
      containerColor = Color.White,
      title = { Text("Cancel this order?", fontWeight = FontWeight.Bold, color = BharatTextPrimary) },
      text = {
        Text(
          text = "Order ${order.displayNumber} will be cancelled and the customer will be notified.",
          color = BharatTextSecondary
        )
      },
      confirmButton = {
        Button(
          onClick = {
            showCancelDialog = false
            onCancelOrder()
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
        ) { Text("Yes, cancel", color = Color.White, fontWeight = FontWeight.Bold) }
      },
      dismissButton = {
        TextButton(onClick = { showCancelDialog = false }) {
          Text("Keep order", color = BharatPurplePrimary)
        }
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
      Row(
        modifier = Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(onClick = onBackClick) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = BharatTextPrimary)
        }
        Spacer(modifier = Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Order Details",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = BharatTextPrimary
          )
          Text(
            text = "Order ${order.displayNumber}",
            fontSize = 12.sp,
            color = BharatPurplePrimary,
            fontWeight = FontWeight.SemiBold
          )
        }
        StatusPill(order.status)
      }

      Column(
        modifier = Modifier
          .weight(1f)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        StatusBanner(order = order)

        CustomerCard(
          name = order.customerName.ifBlank { "Guest customer" },
          phone = order.customerMobile,
          onCallCustomer = { phone ->
            val intent = android.content.Intent(android.content.Intent.ACTION_DIAL, "tel:$phone".toUri())
            context.startActivity(intent)
          }
        )

        OrderMetaCard(order = order)

        ItemsCard(order = order, subtotal = subtotal, extras = extras)
      }

      // Status-driven action bar. Read-only for terminal states.
      when (order.status) {
        OrderStatus.PLACED -> ActionBar(
          primaryLabel = "Confirm Order",
          primaryColor = BharatGreen,
          onPrimary = { onAdvanceStatus(OrderStatus.CONFIRMED) },
          onCancel = { showCancelDialog = true }
        )
        OrderStatus.CONFIRMED -> ActionBar(
          primaryLabel = "Start Preparing",
          primaryColor = Color(0xFF0284C7),
          onPrimary = { onAdvanceStatus(OrderStatus.PREPARING) },
          onCancel = { showCancelDialog = true }
        )
        OrderStatus.PREPARING -> ActionBar(
          primaryLabel = "Mark Ready for Pickup",
          primaryColor = BharatPurplePrimary,
          onPrimary = { onAdvanceStatus(OrderStatus.READY_FOR_PICKUP) },
          onCancel = { showCancelDialog = true }
        )
        OrderStatus.READY_FOR_PICKUP -> ActionBar(
          primaryLabel = "Mark Completed",
          primaryColor = Color(0xFF16A34A),
          onPrimary = { onAdvanceStatus(OrderStatus.COMPLETED) },
          onCancel = null
        )
        OrderStatus.COMPLETED, OrderStatus.CANCELLED -> {
          Surface(color = Color.White, shadowElevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
              Text(
                text = if (order.status == OrderStatus.COMPLETED) "This order is complete." else "This order was cancelled.",
                fontSize = 13.sp,
                color = BharatTextSecondary,
                fontWeight = FontWeight.Medium
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun StatusPill(status: OrderStatus) {
  val (bg, fg) = when (status) {
    OrderStatus.PLACED -> Color(0xFFFEF3C7) to Color(0xFFD97706)
    OrderStatus.CONFIRMED -> Color(0xFFDCFCE7) to Color(0xFF047857)
    OrderStatus.PREPARING -> Color(0xFFE0F2FE) to Color(0xFF0369A1)
    OrderStatus.READY_FOR_PICKUP -> Color(0xFFF0FDF4) to Color(0xFF166534)
    OrderStatus.COMPLETED -> Color(0xFFF1F5F9) to Color(0xFF64748B)
    OrderStatus.CANCELLED -> Color(0xFFFEE2E2) to Color(0xFFDC2626)
  }
  Surface(color = bg, shape = RoundedCornerShape(50.dp), modifier = Modifier.padding(end = 4.dp)) {
    Text(
      text = status.label,
      fontWeight = FontWeight.ExtraBold,
      fontSize = 10.sp,
      color = fg,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
    )
  }
}

@Composable
private fun StatusBanner(order: Order) {
  val (headline, subline, tint, icon) = when (order.status) {
    OrderStatus.PLACED -> HeaderState(
      "New order awaiting your confirmation",
      "Accept the order to lock in the customer.",
      Color(0xFFD97706),
      Icons.Default.ShoppingBag
    )
    OrderStatus.CONFIRMED -> HeaderState(
      "Order accepted",
      "Start preparing whenever you're ready.",
      Color(0xFF10B981),
      Icons.Default.CheckCircle
    )
    OrderStatus.PREPARING -> HeaderState(
      "Preparing the order",
      "Mark ready for pickup as soon as it's bagged.",
      Color(0xFF0284C7),
      Icons.Default.Restaurant
    )
    OrderStatus.READY_FOR_PICKUP -> HeaderState(
      "Ready for pickup",
      "Waiting for the customer to arrive.",
      BharatPurplePrimary,
      Icons.Default.Storefront
    )
    OrderStatus.COMPLETED -> HeaderState(
      "Order completed",
      "Handed off to the customer.",
      Color(0xFF16A34A),
      Icons.Default.CheckCircle
    )
    OrderStatus.CANCELLED -> HeaderState(
      "Order cancelled",
      "No further action needed.",
      Color(0xFFDC2626),
      Icons.Default.Close
    )
  }

  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
      Box(
        modifier = Modifier.size(48.dp).clip(CircleShape).background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(26.dp))
      }
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(headline, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = BharatTextPrimary)
        Text(subline, fontSize = 12.sp, color = BharatTextSecondary)
      }
    }
  }
}

private data class HeaderState(
  val headline: String,
  val subline: String,
  val tint: Color,
  val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
private fun CustomerCard(name: String, phone: String, onCallCustomer: (String) -> Unit) {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text("Customer", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = BharatTextSecondary, letterSpacing = 0.5.sp)
      Spacer(modifier = Modifier.height(10.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier.size(44.dp).clip(CircleShape).background(BharatPurpleContainer),
          contentAlignment = Alignment.Center
        ) {
          Icon(Icons.Default.Person, null, tint = BharatPurplePrimary, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(name, fontWeight = FontWeight.Bold, color = BharatTextPrimary)
          if (phone.isNotBlank()) {
            Text(phone, fontSize = 12.sp, color = BharatTextSecondary)
          } else {
            Text("Phone not provided", fontSize = 12.sp, color = BharatTextMuted)
          }
        }
        if (phone.isNotBlank()) {
          OutlinedButton(
            onClick = { onCallCustomer(phone) },
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, BharatPurplePrimary),
            modifier = Modifier.height(40.dp)
          ) {
            Icon(Icons.Default.Call, null, tint = BharatPurplePrimary, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Call", color = BharatPurplePrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}

@Composable
private fun OrderMetaCard(order: Order) {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text("Order", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = BharatTextSecondary, letterSpacing = 0.5.sp)
      Spacer(modifier = Modifier.height(10.dp))
      MetaRow(label = "Order number", value = order.displayNumber)
      MetaRow(label = "Internal ID", value = "#${order.id.take(8)}")
      Spacer(modifier = Modifier.height(6.dp))
      MetaRow(
        label = "Placed",
        value = if (order.createdAt.isNotBlank()) order.createdAt.take(19).replace("T", " ") else order.orderDate
      )
      Spacer(modifier = Modifier.height(6.dp))
      MetaRow(label = "Status", value = order.status.label)
    }
  }
}

@Composable
private fun MetaRow(label: String, value: String) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(label, fontSize = 12.sp, color = BharatTextSecondary)
    Text(value, fontSize = 12.sp, color = BharatTextPrimary, fontWeight = FontWeight.SemiBold)
  }
}

@Composable
private fun ItemsCard(order: Order, subtotal: Int, extras: Int) {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
        text = if (order.items.isEmpty()) "Items" else "Items (${order.items.size})",
        fontWeight = FontWeight.Bold,
        color = BharatTextPrimary
      )
      Spacer(modifier = Modifier.height(12.dp))

      if (order.items.isEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.HourglassEmpty, null, tint = BharatTextMuted, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "Item details are not synced for this order.",
            fontSize = 12.sp,
            color = BharatTextSecondary
          )
        }
      } else {
        order.items.forEach { item -> VendorItemRow(item) }
      }

      Spacer(modifier = Modifier.height(12.dp))
      HorizontalDivider(color = Color(0xFFF1F5F9))
      Spacer(modifier = Modifier.height(12.dp))

      if (subtotal > 0) {
        BillLine("Subtotal", "\u20b9$subtotal")
        if (extras > 0) {
          Spacer(modifier = Modifier.height(4.dp))
          BillLine("Handling & extras", "\u20b9$extras")
        }
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = Color(0xFFF1F5F9))
        Spacer(modifier = Modifier.height(8.dp))
      }
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("Order total", fontWeight = FontWeight.Bold, color = BharatTextPrimary)
        Text(
          "\u20b9${order.totalAmount}",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
          color = BharatTextPrimary
        )
      }
    }
  }
}

@Composable
private fun VendorItemRow(item: CartItem) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    val previewUrl = item.product.imageUrls.firstOrNull { it.isNotBlank() }
      ?: item.product.imageUrl.takeIf { it.isNotBlank() }
    Box(
      modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFF1F5F9)),
      contentAlignment = Alignment.Center
    ) {
      if (previewUrl != null) {
        AsyncImage(
          model = previewUrl,
          contentDescription = item.product.name,
          modifier = Modifier.fillMaxSize(),
          contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
      } else {
        Icon(Icons.Default.Inventory, null, tint = BharatPurpleAccent, modifier = Modifier.size(20.dp))
      }
    }
    Spacer(modifier = Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(item.product.name, fontWeight = FontWeight.Bold, color = BharatTextPrimary, fontSize = 13.sp, maxLines = 1)
      Text(
        text = "${item.selectedWeight.label} \u00b7 Qty ${item.quantity} \u00b7 \u20b9${item.selectedWeight.price} each",
        fontSize = 11.sp,
        color = BharatTextSecondary,
        maxLines = 1
      )
    }
    Text("\u20b9${item.totalPrice}", fontWeight = FontWeight.Bold, color = BharatTextPrimary, fontSize = 13.sp)
  }
}

@Composable
private fun BillLine(label: String, value: String) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(label, color = BharatTextSecondary, fontSize = 13.sp)
    Text(value, color = BharatTextPrimary, fontSize = 13.sp)
  }
}

@Composable
private fun ActionBar(
  primaryLabel: String,
  primaryColor: Color,
  onPrimary: () -> Unit,
  onCancel: (() -> Unit)?
) {
  Surface(color = Color.White, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.padding(16.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      if (onCancel != null) {
        OutlinedButton(
          onClick = onCancel,
          modifier = Modifier.weight(1f).height(46.dp),
          shape = RoundedCornerShape(12.dp),
          border = BorderStroke(1.dp, Color(0xFFDC2626))
        ) {
          Text("Cancel", color = Color(0xFFDC2626), fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
      }
      Button(
        onClick = onPrimary,
        modifier = Modifier.weight(if (onCancel != null) 2f else 1f).height(46.dp),
        colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
        shape = RoundedCornerShape(12.dp)
      ) {
        Text(primaryLabel, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
      }
    }
  }
}

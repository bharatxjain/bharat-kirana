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
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "Order ${order.displayNumber}",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = BharatTextPrimary
          )
          Text(
            text = order.orderDate,
            style = MaterialTheme.typography.bodySmall,
            color = BharatTextMuted
          )
        }

        Surface(
          shape = RoundedCornerShape(10.dp),
          color = when (order.status) {
            OrderStatus.COMPLETED -> Color(0xFFF1F5F9)
            OrderStatus.READY_FOR_PICKUP -> Color(0xFFF0FDF4)
            else -> BharatPurpleContainer
          }
        ) {
          Text(
            text = order.status.label,
            color = when (order.status) {
              OrderStatus.COMPLETED -> BharatTextSecondary
              OrderStatus.READY_FOR_PICKUP -> Color(0xFF166534)
              else -> BharatPurplePrimary
            },
            fontWeight = FontWeight.ExtraBold,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        items(order.items) { cartItem ->
          Box(
            modifier = Modifier
              .size(56.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(Color(0xFFF8FAFC)),
            contentAlignment = Alignment.Center
          ) {
            if (cartItem.product.localImageRes != null) {
              Image(
                painter = painterResource(id = cartItem.product.localImageRes),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
              )
            } else {
              Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = BharatPurpleAccent, modifier = Modifier.size(24.dp))
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))
      HorizontalDivider(color = Color(0xFFF1F5F9))
      Spacer(modifier = Modifier.height(16.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "₹${order.totalAmount}",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
            color = BharatTextPrimary
          )
          Text(
            text = "${order.items.size} items",
            style = MaterialTheme.typography.bodySmall,
            color = BharatTextSecondary
          )
        }

        OutlinedButton(
          onClick = onReorder,
          shape = RoundedCornerShape(12.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, BharatPurplePrimary),
          contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
          Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Reorder", fontWeight = FontWeight.Bold, color = BharatPurplePrimary)
        }
      }
    }
  }
}

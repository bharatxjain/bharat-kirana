package com.kks.bharatkirana.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kks.bharatkirana.data.model.*
import com.kks.bharatkirana.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorDashboardScreen(
  shop: Shop,
  orders: List<Order>,
  products: List<Product>,
  onLogout: () -> Unit,
  onManageProducts: () -> Unit,
  onBackClick: () -> Unit,
  onUpdateShop: (String, Shop) -> Unit = { _, _ -> },
  onUpdateOrderStatus: (String, OrderStatus) -> Unit = { _, _ -> },
  onCancelOrder: (String) -> Unit = {},
  onUpdateProductStock: (String, Boolean) -> Unit = { _, _ -> },
  modifier: Modifier = Modifier
) {
  var selectedTab by remember { mutableIntStateOf(0) } // 0: Overview, 1: Inventory, 2: Orders, 3: Reviews
  var showEditShopDialog by remember { mutableStateOf(false) }

  // Real Analytics Calculations
  val totalOrders = orders.size
  val totalRevenue = orders.sumOf { it.totalAmount }
  val activeProducts = products.size
  val lowStockCount = products.count { it.stockQty <= 5 }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.LocationOn, contentDescription = null, tint = BharatPurplePrimary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = shop.name,
              style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
              color = BharatTextPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = { showEditShopDialog = true }, modifier = Modifier.size(24.dp)) {
              Icon(Icons.Default.Settings, contentDescription = "Edit Store", tint = BharatPurplePrimary, modifier = Modifier.size(16.dp))
            }
          }
        },
        navigationIcon = {
          IconButton(onClick = onBackClick) {
            Icon(Icons.Default.ShoppingBag, contentDescription = "Marketplace", tint = BharatPurplePrimary)
          }
        },
        actions = {
          IconButton(onClick = { /* Personal Profile */ }) {
            Box(
              modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0xFFF3E8FF)),
              contentAlignment = Alignment.Center
            ) {
              Icon(Icons.Default.Person, contentDescription = "Personal Profile", tint = BharatPurplePrimary, modifier = Modifier.size(20.dp))
            }
          }
          IconButton(onClick = onLogout) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout", tint = BharatTextSecondary)
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
      )
    },
    bottomBar = {
      NavigationBar(containerColor = Color.White, tonalElevation = 8.dp) {
        NavigationBarItem(selected = selectedTab == 0, onClick = { selectedTab = 0 }, icon = { Icon(Icons.Default.Dashboard, null) }, label = { Text("Overview") })
        NavigationBarItem(selected = selectedTab == 1, onClick = { selectedTab = 1 }, icon = { Icon(Icons.Default.Inventory, null) }, label = { Text("Inventory") })
        NavigationBarItem(selected = selectedTab == 2, onClick = { selectedTab = 2 }, icon = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, null) }, label = { Text("Orders") })
        NavigationBarItem(selected = selectedTab == 3, onClick = { selectedTab = 3 }, icon = { Icon(Icons.Default.Star, null) }, label = { Text("Reviews") })
      }
    },
    modifier = modifier.fillMaxSize()
  ) { paddingValues ->
    if (shop.status != VendorStatus.APPROVED) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.White)
          .padding(paddingValues),
        contentAlignment = Alignment.Center
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.padding(32.dp)
        ) {
          Icon(
            imageVector = Icons.Default.PendingActions,
            contentDescription = null,
            tint = BharatPurplePrimary,
            modifier = Modifier.size(72.dp)
          )
          Spacer(modifier = Modifier.height(24.dp))
          Text(
            text = when(shop.status) {
              VendorStatus.PENDING -> "Your Shop is Under Review"
              VendorStatus.REJECTED -> "Application Rejected"
              VendorStatus.SUSPENDED -> "Shop Suspended"
              else -> "Status Unknown"
            },
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = BharatTextPrimary,
            textAlign = TextAlign.Center
          )
          Spacer(modifier = Modifier.height(12.dp))
          Text(
            text = when(shop.status) {
              VendorStatus.PENDING -> "Bharat Kirana team is currently verifying your shop details. You will receive a notification once approved."
              VendorStatus.REJECTED -> "We're sorry, but your shop application was not approved at this time. Please contact support for more details."
              VendorStatus.SUSPENDED -> "Your shop access has been suspended due to policy violations. Please reach out to the admin team."
              else -> "Please wait while we fetch your shop status."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = BharatTextSecondary,
            textAlign = TextAlign.Center
          )
          Spacer(modifier = Modifier.height(32.dp))
          OutlinedButton(
            onClick = onLogout,
            shape = RoundedCornerShape(12.dp)
          ) {
            Text("Sign Out")
          }
        }
      }
    } else {
      Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        when (selectedTab) {
          0 -> {
            LazyColumn(
              modifier = Modifier.fillMaxSize().background(Color(0xFFF9FAFB)),
              contentPadding = PaddingValues(16.dp),
              verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
              item {
                Column {
                  Text(text = "Store Hub", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = BharatTextPrimary)
                  Text(text = "Real-time performance for ${shop.name}", style = MaterialTheme.typography.bodyMedium, color = BharatTextSecondary)
                }
              }

              // Stats Cards
              item {
                StatsCardPremium(
                  title = "TOTAL ORDERS",
                  value = "$totalOrders",
                  trend = "Lifetime orders",
                  icon = Icons.AutoMirrored.Filled.ReceiptLong,
                  iconBgColor = Color(0xFFF3E8FF),
                  iconTint = BharatPurplePrimary
                )
              }
              
              item {
                Card(
                  shape = RoundedCornerShape(24.dp),
                  modifier = Modifier.fillMaxWidth(),
                  colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                  Box(modifier = Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Color(0xFF6D28D9), Color(0xFF4C1D95)))).padding(20.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                      Column {
                        Text(text = "TOTAL REVENUE", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "₹$totalRevenue", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Verified settlements", fontSize = 12.sp, color = Color.White.copy(alpha = 0.9f))
                      }
                      Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                      }
                    }
                  }
                }
              }

              // Low Stock Alert
              if (lowStockCount > 0) {
                item {
                  Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE4E6)),
                    modifier = Modifier.fillMaxWidth()
                  ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                      Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFDC2626))
                      Spacer(modifier = Modifier.width(12.dp))
                      Column {
                        Text(text = "Low Stock Alert", fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                        Text(text = "$lowStockCount items are running low.", fontSize = 12.sp, color = Color(0xFFDC2626).copy(alpha = 0.8f))
                      }
                    }
                  }
                }
              }

              // Ops Card
              item {
                Card(
                  shape = RoundedCornerShape(24.dp),
                  colors = CardDefaults.cardColors(containerColor = Color.White),
                  modifier = Modifier.fillMaxWidth(),
                  border = BorderStroke(1.dp, Color(0xFFF1F5F9))
                ) {
                  Column(modifier = Modifier.padding(20.dp)) {
                    Text(text = "STORE OPERATIONS", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = BharatTextSecondary, letterSpacing = 0.5.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OperationToggleRow(title = "Accepting Orders", subtitle = if (shop.isOpen) "Shop is currently open" else "Shop is closed", checked = shop.isOpen, onCheckedChange = { onUpdateShop(shop.id, shop.copy(isOpen = it)) }, color = BharatGreen)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFF1F5F9))
                    OperationToggleRow(title = "Auto-Accept Orders", subtitle = "Instantly confirm new orders", checked = shop.autoConfirm, onCheckedChange = { onUpdateShop(shop.id, shop.copy(autoConfirm = it)) }, color = BharatPurplePrimary)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFF1F5F9))
                    OperationToggleRow(title = "Promoted Placement", subtitle = "Boost visibility in search results", checked = shop.isPartner, onCheckedChange = { onUpdateShop(shop.id, shop.copy(isPartner = it)) }, color = Color(0xFFD97706), isAd = true)
                  }
                }
              }
            }
          }
          1 -> {
            LazyColumn(modifier = Modifier.fillMaxSize().background(Color(0xFFF9FAFB)), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
               item {
                 Button(onClick = onManageProducts, modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = BharatPurplePrimary), shape = RoundedCornerShape(12.dp)) {
                   Icon(Icons.Default.Add, null)
                   Spacer(Modifier.width(8.dp))
                   Text("Add New Product", fontWeight = FontWeight.Bold)
                 }
               }
               items(products) { product ->
                  Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFF1F5F9))) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                      Box(modifier = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFF1F5F9)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Inventory, null, tint = BharatPurplePrimary)
                      }
                      Spacer(Modifier.width(12.dp))
                      Column(modifier = Modifier.weight(1f)) {
                        Text(product.name, fontWeight = FontWeight.Bold, color = BharatTextPrimary)
                        Text("${product.stockQty} in stock • ₹${product.currentPrice}", fontSize = 12.sp, color = BharatTextSecondary)
                      }
                      Switch(checked = product.inStock, onCheckedChange = { onUpdateProductStock(product.id, it) }, colors = SwitchDefaults.colors(checkedTrackColor = BharatGreen))
                    }
                  }
               }
            }
          }
          2 -> {
            val pending = orders.filter { it.status == OrderStatus.PLACED }
            val preparing = orders.filter { it.status == OrderStatus.PREPARING }
            val ready = orders.filter { it.status == OrderStatus.READY_FOR_PICKUP }
            val history = orders.filter { it.status == OrderStatus.COMPLETED || it.status == OrderStatus.CANCELLED }

            LazyColumn(
              modifier = Modifier.fillMaxSize().background(Color(0xFFF9FAFB)),
              contentPadding = PaddingValues(16.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              if (orders.isEmpty()) {
                item {
                  Box(modifier = Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                      Icon(Icons.AutoMirrored.Filled.ReceiptLong, null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(56.dp))
                      Spacer(modifier = Modifier.height(12.dp))
                      Text("No orders yet", fontWeight = FontWeight.Bold, color = BharatTextPrimary)
                      Text("Incoming orders will appear here.", fontSize = 12.sp, color = BharatTextSecondary)
                    }
                  }
                }
              }
              if (pending.isNotEmpty()) {
                item { OrderSectionHeader("New Orders", pending.size, Color(0xFFF59E0B)) }
                items(pending, key = { it.id }) { order ->
                  VendorOrderActionCard(
                    order = order,
                    primaryLabel = "Accept & Prepare",
                    primaryColor = BharatGreen,
                    onPrimary = { onUpdateOrderStatus(order.id, OrderStatus.PREPARING) },
                    onCancel = { onCancelOrder(order.id) }
                  )
                }
              }
              if (preparing.isNotEmpty()) {
                item { OrderSectionHeader("Preparing", preparing.size, Color(0xFF0284C7)) }
                items(preparing, key = { it.id }) { order ->
                  VendorOrderActionCard(
                    order = order,
                    primaryLabel = "Mark Ready for Pickup",
                    primaryColor = BharatPurplePrimary,
                    onPrimary = { onUpdateOrderStatus(order.id, OrderStatus.READY_FOR_PICKUP) },
                    onCancel = { onCancelOrder(order.id) }
                  )
                }
              }
              if (ready.isNotEmpty()) {
                item { OrderSectionHeader("Ready for Pickup", ready.size, BharatGreen) }
                items(ready, key = { it.id }) { order ->
                  VendorOrderActionCard(
                    order = order,
                    primaryLabel = "Mark Completed",
                    primaryColor = BharatPurpleDark,
                    onPrimary = { onUpdateOrderStatus(order.id, OrderStatus.COMPLETED) },
                    onCancel = null
                  )
                }
              }
              if (history.isNotEmpty()) {
                item { OrderSectionHeader("History", history.size, BharatTextSecondary) }
                items(history, key = { it.id }) { order -> RecentOrderRow(order = order) }
              }
            }
          }
          3 -> {
             LazyColumn(modifier = Modifier.fillMaxSize().background(Color(0xFFF9FAFB)), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                  Text(text = "Customer Feedback", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = BharatTextPrimary)
                }
                items(listOf(
                  "Great service, everything was packed neatly!" to 5,
                  "Fresh produce but took a bit longer to ready." to 4
                )) { (review, stars) ->
                  Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFF1F5F9))) {
                    Column(modifier = Modifier.padding(16.dp)) {
                      Row(verticalAlignment = Alignment.CenterVertically) {
                        repeat(5) { i ->
                          Icon(Icons.Default.Star, null, tint = if (i < stars) Color(0xFFFFB800) else BharatTextMuted, modifier = Modifier.size(14.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("2 days ago", fontSize = 11.sp, color = BharatTextMuted)
                      }
                      Spacer(modifier = Modifier.height(8.dp))
                      Text(text = review, style = MaterialTheme.typography.bodyMedium, color = BharatTextPrimary)
                    }
                  }
                }
             }
          }
        }
      }
    }
  }

  if (showEditShopDialog) {
    var name by remember { mutableStateOf(shop.name) }
    var owner by remember { mutableStateOf(shop.ownerName) }
    var addr by remember { mutableStateOf(shop.address) }
    var phone by remember { mutableStateOf(shop.phone) }

    AlertDialog(
      onDismissRequest = { showEditShopDialog = false },
      title = { Text("Edit Store Details", fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Shop Name") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = BharatTextPrimary, unfocusedTextColor = BharatTextPrimary))
          OutlinedTextField(value = owner, onValueChange = { owner = it }, label = { Text("Owner Name") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = BharatTextPrimary, unfocusedTextColor = BharatTextPrimary))
          OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = BharatTextPrimary, unfocusedTextColor = BharatTextPrimary))
          OutlinedTextField(value = addr, onValueChange = { addr = it }, label = { Text("Address") }, minLines = 2, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = BharatTextPrimary, unfocusedTextColor = BharatTextPrimary))
        }
      },
      confirmButton = {
        Button(onClick = {
          onUpdateShop(shop.id, shop.copy(name = name, ownerName = owner, address = addr, phone = phone))
          showEditShopDialog = false
        }) { Text("Save") }
      },
      dismissButton = {
        TextButton(onClick = { showEditShopDialog = false }) { Text("Cancel") }
      }
    )
  }
}

@Composable
fun OperationToggleRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  color: Color,
  isAd: Boolean = false
) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
    Column(modifier = Modifier.weight(1f)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = title, fontWeight = FontWeight.Bold, color = BharatTextPrimary)
        if (isAd) {
          Spacer(modifier = Modifier.width(6.dp))
          Surface(color = Color(0xFFFEF3C7), shape = RoundedCornerShape(4.dp)) {
            Text(text = "AD", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFD97706), modifier = Modifier.padding(horizontal = 4.dp))
          }
        }
      }
      Text(text = subtitle, fontSize = 12.sp, color = BharatTextSecondary)
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = color)
    )
  }
}

@Composable
fun StatsCardPremium(
  title: String,
  value: String,
  trend: String,
  icon: ImageVector,
  iconBgColor: Color,
  iconTint: Color
) {
  Card(
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    modifier = Modifier.fillMaxWidth(),
    border = BorderStroke(1.dp, Color(0xFFF1F5F9))
  ) {
    Row(
      modifier = Modifier.padding(20.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Column {
        Text(text = title, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = BharatTextSecondary, letterSpacing = 0.5.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = value, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = BharatTextPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = trend, fontSize = 12.sp, color = if (trend.contains("+")) Color(0xFF22C55E) else BharatTextSecondary, fontWeight = FontWeight.Medium)
      }
      Box(
        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(iconBgColor),
        contentAlignment = Alignment.Center
      ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(26.dp))
      }
    }
  }
}

@Composable
fun RecentOrderRow(order: Order) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    border = BorderStroke(1.dp, Color(0xFFF1F5F9))
  ) {
    Row(
      modifier = Modifier.padding(16.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier.size(50.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF5F3FF)),
        contentAlignment = Alignment.Center
      ) {
        Text(text = "#" + order.id.takeLast(3), fontWeight = FontWeight.Bold, color = BharatPurplePrimary)
      }
      Spacer(modifier = Modifier.width(16.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(text = "Customer Name", fontWeight = FontWeight.Bold, color = BharatTextPrimary) // Placeholder
        Text(text = "${order.items.size} items • 10 mins ago", style = MaterialTheme.typography.bodySmall, color = BharatTextSecondary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = "₹${order.totalAmount}", fontWeight = FontWeight.ExtraBold, color = BharatTextPrimary, fontSize = 16.sp)
      }
      
      val statusColor = when(order.status) {
        OrderStatus.PLACED -> Color(0xFFFEF3C7)
        OrderStatus.PREPARING -> Color(0xFFE0F2FE)
        OrderStatus.READY_FOR_PICKUP -> Color(0xFFF0FDF4)
        OrderStatus.COMPLETED -> Color(0xFFF1F5F9)
        OrderStatus.CANCELLED -> Color(0xFFFFE4E6)
      }
      val textTint = when(order.status) {
        OrderStatus.PLACED -> Color(0xFFD97706)
        OrderStatus.PREPARING -> Color(0xFF0284C7)
        OrderStatus.READY_FOR_PICKUP -> Color(0xFF166534)
        OrderStatus.COMPLETED -> BharatTextSecondary
        OrderStatus.CANCELLED -> Color(0xFFDC2626)
      }
      
      Surface(
        shape = RoundedCornerShape(50.dp),
        color = statusColor,
        modifier = Modifier.padding(start = 8.dp)
      ) {
        Text(
          text = order.status.label,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          color = textTint
        )
      }
    }
  }
}

@Composable
private fun OrderSectionHeader(title: String, count: Int, accent: Color) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(accent))
    Spacer(modifier = Modifier.width(8.dp))
    Text(text = title, fontWeight = FontWeight.ExtraBold, color = BharatTextPrimary, fontSize = 14.sp)
    Spacer(modifier = Modifier.width(8.dp))
    Surface(shape = RoundedCornerShape(50.dp), color = accent.copy(alpha = 0.15f)) {
      Text(
        text = count.toString(),
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = accent
      )
    }
  }
}

@Composable
private fun VendorOrderActionCard(
  order: Order,
  primaryLabel: String,
  primaryColor: Color,
  onPrimary: () -> Unit,
  onCancel: (() -> Unit)? = null
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF5F3FF)),
          contentAlignment = Alignment.Center
        ) {
          Text("#" + order.id.takeLast(3), fontWeight = FontWeight.Bold, color = BharatPurplePrimary, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(text = order.id, fontWeight = FontWeight.Bold, color = BharatTextPrimary, fontSize = 14.sp)
          Text(
            text = "${order.items.size} items \u2022 ${order.orderDate}",
            fontSize = 12.sp,
            color = BharatTextSecondary
          )
        }
        Text(text = "\u20b9${order.totalAmount}", fontWeight = FontWeight.ExtraBold, color = BharatTextPrimary, fontSize = 16.sp)
      }

      Spacer(modifier = Modifier.height(12.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        if (onCancel != null) {
          OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.weight(1f).height(44.dp),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, Color(0xFFDC2626))
          ) {
            Text("Cancel", color = Color(0xFFDC2626), fontWeight = FontWeight.Bold, fontSize = 13.sp)
          }
        }
        Button(
          onClick = onPrimary,
          modifier = Modifier.weight(if (onCancel != null) 2f else 1f).height(44.dp),
          colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
          shape = RoundedCornerShape(10.dp)
        ) {
          Text(primaryLabel, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
        }
      }
    }
  }
}

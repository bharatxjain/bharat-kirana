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
import coil.compose.AsyncImage
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
  onUpdateProductPrice: (String, Int) -> Unit = { _, _ -> },
  onUpdateProductQty: (String, Int?) -> Unit = { _, _ -> },
  onSupportClick: () -> Unit = {},
  onRefreshStatus: () -> Unit = {},
  onManagePlan: () -> Unit = {},
  currentTierName: String? = null,
  currentTierItemCap: Int = 10,
  modifier: Modifier = Modifier
) {
  var selectedTab by remember { mutableIntStateOf(0) } // 0: Overview, 1: Inventory, 2: Orders, 3: Reviews
  var showEditShopDialog by remember { mutableStateOf(false) }
  var editingProductId by remember { mutableStateOf<String?>(null) }
  var editingProductPrice by remember { mutableStateOf("") }
  var editingProductQty by remember { mutableStateOf("") }

  // Real Analytics Calculations
  val totalOrders = orders.size
  val totalRevenue = orders.sumOf { it.totalAmount }
  val activeProducts = products.size
  val lowStockCount = products.count { it.stockQty != null && it.stockQty <= 5 }

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
      VendorStatusPendingContent(
        shop = shop,
        paddingValues = paddingValues,
        onSupportClick = onSupportClick,
        onRefreshStatus = onRefreshStatus,
        onLogout = onLogout
      )
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
                  Text(text = "Overview", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = BharatTextPrimary)
                  Text(text = "Today's shop performance", style = MaterialTheme.typography.bodyMedium, color = BharatTextSecondary)
                }
              }

              // Round 7.1: prominent "+ Add New Product" as the FIRST action in
              // Overview — matches vendor mental model that adding stock is the
              // most common task. Also mirrored in the Inventory tab.
              item {
                Button(
                  onClick = onManageProducts,
                  modifier = Modifier.fillMaxWidth().height(52.dp).testTag("overview_add_product_button"),
                  colors = ButtonDefaults.buttonColors(containerColor = BharatPurplePrimary),
                  shape = RoundedCornerShape(14.dp)
                ) {
                  Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                  Spacer(Modifier.width(8.dp))
                  Text("Add New Product", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                }
              }

              // TOTAL ORDERS (moved up — was 4th).
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

              // REVENUE big purple card (unchanged shape, just moved up).
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

              // ACTIVE PRODUCTS stat — new on Overview so vendor sees inventory
              // size without switching tabs.
              item {
                StatsCardPremium(
                  title = "ACTIVE PRODUCTS",
                  value = "$activeProducts",
                  trend = if (lowStockCount > 0) "$lowStockCount low stock" else "All stocked",
                  icon = Icons.Default.Inventory,
                  iconBgColor = Color(0xFFF3E8FF),
                  iconTint = BharatPurplePrimary
                )
              }

              // RECENT ORDERS — last 3, tap-through to the Orders tab for the rest.
              val recentOrders = orders.sortedByDescending { it.orderDate }.take(3)
              if (recentOrders.isNotEmpty()) {
                item {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Text(
                      text = "Recent Orders",
                      style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                      color = BharatTextPrimary,
                      modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { selectedTab = 2 }) {
                      Text("View All", color = BharatPurplePrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                  }
                }
                items(recentOrders, key = { it.id }) { order ->
                  Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFF1F5F9))
                  ) {
                    Row(
                      modifier = Modifier.fillMaxWidth().padding(14.dp),
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Box(
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(BharatPurpleContainer),
                        contentAlignment = Alignment.Center
                      ) {
                        Text(
                          text = "#${order.id.takeLast(3)}",
                          fontSize = 10.sp,
                          fontWeight = FontWeight.Bold,
                          color = BharatPurpleDark
                        )
                      }
                      Spacer(Modifier.width(12.dp))
                      Column(modifier = Modifier.weight(1f)) {
                        Text(
                          text = order.items.firstOrNull()?.product?.name?.let { name ->
                            if (order.items.size > 1) "$name +${order.items.size - 1} more" else name
                          } ?: "Order",
                          fontWeight = FontWeight.SemiBold,
                          color = BharatTextPrimary,
                          fontSize = 13.sp
                        )
                        Text(
                          text = "${order.items.size} items • ${order.orderDate}",
                          fontSize = 11.sp,
                          color = BharatTextSecondary
                        )
                        Text(
                          text = "₹${order.totalAmount}",
                          fontWeight = FontWeight.Bold,
                          color = BharatTextPrimary,
                          fontSize = 14.sp
                        )
                      }
                      Surface(
                        shape = RoundedCornerShape(50.dp),
                        color = when (order.status) {
                          OrderStatus.PLACED -> Color(0xFFFEF3C7)
                          OrderStatus.CONFIRMED -> Color(0xFFDCFCE7)
                          OrderStatus.PREPARING -> Color(0xFFE0F2FE)
                          OrderStatus.READY_FOR_PICKUP -> Color(0xFFF0FDF4)
                          OrderStatus.COMPLETED -> Color(0xFFF1F5F9)
                          OrderStatus.CANCELLED -> Color(0xFFFFE4E6)
                        }
                      ) {
                        Text(
                          text = order.status.label,
                          fontSize = 10.sp,
                          fontWeight = FontWeight.Bold,
                          color = when (order.status) {
                            OrderStatus.PLACED -> Color(0xFFD97706)
                            OrderStatus.CONFIRMED -> Color(0xFF10B981)
                            OrderStatus.PREPARING -> Color(0xFF0284C7)
                            OrderStatus.READY_FOR_PICKUP -> Color(0xFF166534)
                            OrderStatus.COMPLETED -> BharatTextSecondary
                            OrderStatus.CANCELLED -> Color(0xFFDC2626)
                          },
                          modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                      }
                    }
                  }
                }
              }

              // Low Stock Alert (conditional)
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

              // Current subscription tier + Manage Plan CTA (moved down).
              item {
                val tierLabel = currentTierName ?: "Free"
                val capLabel = if (currentTierItemCap == -1) {
                  "Unlimited products"
                } else {
                  "${products.size} of $currentTierItemCap products"
                }
                Card(
                  onClick = onManagePlan,
                  shape = RoundedCornerShape(16.dp),
                  colors = CardDefaults.cardColors(containerColor = Color.White),
                  border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Box(
                      modifier = Modifier.size(40.dp).clip(CircleShape).background(BharatPurpleContainer),
                      contentAlignment = Alignment.Center
                    ) {
                      Icon(Icons.Default.Star, contentDescription = null, tint = BharatPurplePrimary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                      Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Current Plan", fontSize = 11.sp, color = BharatTextSecondary, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(6.dp))
                        Surface(color = BharatPurpleContainer, shape = RoundedCornerShape(4.dp)) {
                          Text(
                            tierLabel.uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = BharatPurpleDark,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                          )
                        }
                      }
                      Text(capLabel, fontSize = 13.sp, color = BharatTextPrimary, fontWeight = FontWeight.Bold)
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Manage Plan", tint = BharatPurplePrimary)
                  }
                }
              }

              // Edit Shop Details card + rating surface (moved down).
              item {
                Card(
                  onClick = { showEditShopDialog = true },
                  shape = RoundedCornerShape(16.dp),
                  colors = CardDefaults.cardColors(containerColor = Color.White),
                  border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Box(
                      modifier = Modifier.size(40.dp).clip(CircleShape).background(BharatPurpleContainer),
                      contentAlignment = Alignment.Center
                    ) {
                      Icon(Icons.Default.Storefront, contentDescription = null, tint = BharatPurplePrimary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                      Text("Edit Shop Details", fontSize = 13.sp, color = BharatTextPrimary, fontWeight = FontWeight.Bold)
                      Text(
                        "Name, address, phone, hours",
                        fontSize = 11.sp,
                        color = BharatTextSecondary
                      )
                      if (shop.ratingCount > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                          Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(12.dp))
                          Spacer(Modifier.width(3.dp))
                          Text(
                            text = "${"%.1f".format(shop.rating)} · ${shop.ratingCount} customer review${if (shop.ratingCount == 1) "" else "s"}",
                            fontSize = 11.sp,
                            color = BharatTextPrimary,
                            fontWeight = FontWeight.SemiBold
                          )
                        }
                      } else {
                        Text(
                          "No customer ratings yet",
                          fontSize = 11.sp,
                          color = BharatTextMuted,
                          modifier = Modifier.padding(top = 2.dp)
                        )
                      }
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Edit", tint = BharatPurplePrimary)
                  }
                }
              }

              // Store Operations card (unchanged, at bottom).
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
                  Card(
                    onClick = {
                      editingProductId = product.id
                      editingProductPrice = product.currentPrice.toString()
                      editingProductQty = product.stockQty?.toString().orEmpty()
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFF1F5F9))
                  ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                      Box(
                        modifier = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFF1F5F9)),
                        contentAlignment = Alignment.Center
                      ) {
                        val previewUrl = product.imageUrls.firstOrNull()?.takeIf { it.isNotBlank() }
                          ?: product.imageUrl.takeIf { it.isNotBlank() }
                        if (previewUrl != null) {
                          AsyncImage(
                            model = previewUrl,
                            contentDescription = product.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                          )
                        } else {
                          Icon(Icons.Default.Inventory, null, tint = BharatPurplePrimary)
                        }
                      }
                      Spacer(Modifier.width(12.dp))
                      Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                          Text(product.name, fontWeight = FontWeight.Bold, color = BharatTextPrimary, modifier = Modifier.weight(1f, fill = false))
                          Spacer(Modifier.width(6.dp))
                          Icon(Icons.Default.Edit, contentDescription = "Edit", tint = BharatPurplePrimary, modifier = Modifier.size(14.dp))
                        }
                        Text(
                          text = buildString {
                            append(product.stockQty?.let { "$it in stock" } ?: "Qty not set")
                            append(" • ₹${product.currentPrice} • Tap to edit")
                          },
                          fontSize = 11.sp,
                          color = BharatTextSecondary
                        )
                      }
                      Switch(checked = product.inStock, onCheckedChange = { onUpdateProductStock(product.id, it) }, colors = SwitchDefaults.colors(checkedTrackColor = BharatGreen))
                    }
                  }
               }
            }
          }
          2 -> {
            val pending = orders.filter { it.status == OrderStatus.PLACED }
            val confirmed = orders.filter { it.status == OrderStatus.CONFIRMED }
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
                    primaryLabel = "Confirm Order",
                    primaryColor = BharatGreen,
                    onPrimary = { onUpdateOrderStatus(order.id, OrderStatus.CONFIRMED) },
                    onCancel = { onCancelOrder(order.id) }
                  )
                }
              }
              if (confirmed.isNotEmpty()) {
                item { OrderSectionHeader("Confirmed", confirmed.size, Color(0xFF10B981)) }
                items(confirmed, key = { it.id }) { order ->
                  VendorOrderActionCard(
                    order = order,
                    primaryLabel = "Start Preparing",
                    primaryColor = Color(0xFF0284C7),
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

  // Round 4a: quick "edit price" dialog when a product row is tapped.
  editingProductId?.let { pid ->
    val prod = products.firstOrNull { it.id == pid }
    if (prod == null) {
      editingProductId = null
    } else {
      AlertDialog(
        onDismissRequest = { editingProductId = null },
        title = {
          Column {
            Text("Update Product", fontWeight = FontWeight.Bold, color = BharatTextPrimary)
            Text(prod.name, fontSize = 13.sp, color = BharatTextSecondary)
          }
        },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
              value = editingProductPrice,
              onValueChange = { input -> editingProductPrice = input.filter { it.isDigit() }.take(6) },
              label = { Text("Selling Price (₹)") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
              colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = BharatTextPrimary,
                unfocusedTextColor = BharatTextPrimary,
                focusedBorderColor = BharatPurplePrimary
              )
            )
            OutlinedTextField(
              value = editingProductQty,
              onValueChange = { input -> editingProductQty = input.filter { it.isDigit() }.take(5) },
              label = { Text("Stock Quantity") },
              placeholder = { Text("Leave blank if unsure") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
              supportingText = {
                Text(
                  text = when (val q = editingProductQty.toIntOrNull()) {
                    null -> "⚠️ Customers see \"Call to Confirm\""
                    0 -> "Customers see \"Out of Stock\""
                    in 1..5 -> "Customers see \"Low Stock\""
                    else -> "🟢 Customers see \"In Stock\""
                  },
                  fontSize = 11.sp,
                  color = BharatTextSecondary
                )
              },
              colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = BharatTextPrimary,
                unfocusedTextColor = BharatTextPrimary,
                focusedBorderColor = BharatPurplePrimary
              )
            )
            Text(
              text = "Currently ₹${prod.currentPrice} • ${prod.stockStatus}",
              fontSize = 11.sp,
              color = BharatTextMuted
            )
          }
        },
        confirmButton = {
          val newPrice = editingProductPrice.toIntOrNull()
          val newQty = editingProductQty.toIntOrNull()
          val priceChanged = newPrice != null && newPrice > 0 && newPrice != prod.currentPrice
          val qtyChanged = newQty != prod.stockQty
          Button(
            onClick = {
              if (priceChanged) onUpdateProductPrice(prod.id, newPrice!!)
              if (qtyChanged) onUpdateProductQty(prod.id, newQty)
              editingProductId = null
            },
            enabled = priceChanged || qtyChanged,
            colors = ButtonDefaults.buttonColors(containerColor = BharatPurplePrimary)
          ) { Text("Save", color = Color.White, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
          TextButton(onClick = { editingProductId = null }) {
            Text("Cancel", color = BharatTextSecondary)
          }
        }
      )
    }
  }
}

// Round 4a/4b polish: friendlier "Under Review" surface for non-approved vendors.
// Shows shop identity, the exact status, a 3-step timeline of what happens next,
// and quick actions (WhatsApp support + manual status refresh + sign out).
@Composable
private fun VendorStatusPendingContent(
  shop: Shop,
  paddingValues: PaddingValues,
  onSupportClick: () -> Unit,
  onRefreshStatus: () -> Unit,
  onLogout: () -> Unit
) {
  val (titleText, subtitleText, accentColor) = when (shop.status) {
    VendorStatus.PENDING -> Triple(
      "Your Shop is Under Review",
      "The BreakQ team is verifying your shop details. This usually takes 24-48 hours.",
      BharatPurplePrimary
    )
    VendorStatus.REJECTED -> Triple(
      "Application Not Approved",
      "We couldn't approve your shop this time. Please reach out to support — we're happy to help you re-apply.",
      Color(0xFFDC2626)
    )
    VendorStatus.SUSPENDED -> Triple(
      "Shop Temporarily Suspended",
      "Your shop access has been paused. Contact support to understand next steps.",
      Color(0xFFF59E0B)
    )
    else -> Triple(
      "Checking Status…",
      "Please wait while we fetch your latest shop status.",
      BharatTextSecondary
    )
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFFF9FAFB))
      .padding(paddingValues)
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 20.dp, vertical = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // Big status icon
      Box(
        modifier = Modifier
          .size(96.dp)
          .clip(CircleShape)
          .background(accentColor.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = when (shop.status) {
            VendorStatus.PENDING -> Icons.Default.PendingActions
            VendorStatus.REJECTED -> Icons.Default.Cancel
            VendorStatus.SUSPENDED -> Icons.Default.Warning
            else -> Icons.Default.Refresh
          },
          contentDescription = null,
          tint = accentColor,
          modifier = Modifier.size(48.dp)
        )
      }

      Spacer(Modifier.height(20.dp))

      Text(
        text = titleText,
        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        color = BharatTextPrimary,
        textAlign = TextAlign.Center
      )

      Spacer(Modifier.height(8.dp))

      Text(
        text = subtitleText,
        style = MaterialTheme.typography.bodyMedium,
        color = BharatTextSecondary,
        textAlign = TextAlign.Center
      )

      Spacer(Modifier.height(24.dp))

      // Shop summary card
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE5E7EB))
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Store, contentDescription = null, tint = BharatPurplePrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(shop.name, fontWeight = FontWeight.Bold, color = BharatTextPrimary, fontSize = 16.sp)
          }
          Spacer(Modifier.height(6.dp))
          Text("Owner: ${shop.ownerName}", fontSize = 13.sp, color = BharatTextSecondary)
          Text("Phone: ${shop.phone}", fontSize = 13.sp, color = BharatTextSecondary)
          if (shop.address.isNotBlank()) {
            Text("Address: ${shop.address}", fontSize = 13.sp, color = BharatTextSecondary)
          }
        }
      }

      Spacer(Modifier.height(20.dp))

      // What happens next — only meaningful for PENDING
      if (shop.status == VendorStatus.PENDING) {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White),
          border = BorderStroke(1.dp, Color(0xFFE5E7EB))
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text("What happens next?", fontWeight = FontWeight.Bold, color = BharatTextPrimary, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            PendingStep(1, "Team reviews your details", "We check your shop info, location and contact number.", completed = true)
            PendingStep(2, "Verification call", "Someone from BreakQ may call you on the number you gave us.", completed = false)
            PendingStep(3, "You're live!", "Once approved, this screen turns into your Store Hub.", completed = false)
          }
        }
        Spacer(Modifier.height(20.dp))
      }

      // Quick actions
      Button(
        onClick = onSupportClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
      ) {
        Icon(Icons.Default.Chat, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Chat with Support on WhatsApp", color = Color.White, fontWeight = FontWeight.Bold)
      }

      Spacer(Modifier.height(8.dp))

      OutlinedButton(
        onClick = onRefreshStatus,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BharatPurplePrimary)
      ) {
        Icon(Icons.Default.Refresh, contentDescription = null, tint = BharatPurplePrimary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Check status again", color = BharatPurplePrimary, fontWeight = FontWeight.Bold)
      }

      Spacer(Modifier.height(8.dp))

      TextButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
        Text("Sign Out", color = BharatTextSecondary)
      }
    }
  }
}

@Composable
private fun PendingStep(number: Int, title: String, subtitle: String, completed: Boolean) {
  Row(
    modifier = Modifier.padding(vertical = 6.dp),
    verticalAlignment = Alignment.Top
  ) {
    Box(
      modifier = Modifier
        .size(28.dp)
        .clip(CircleShape)
        .background(if (completed) BharatGreen.copy(alpha = 0.15f) else Color(0xFFF1F5F9)),
      contentAlignment = Alignment.Center
    ) {
      if (completed) {
        Icon(Icons.Default.Check, contentDescription = null, tint = BharatGreen, modifier = Modifier.size(16.dp))
      } else {
        Text("$number", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BharatTextSecondary)
      }
    }
    Spacer(Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(title, fontWeight = FontWeight.SemiBold, color = BharatTextPrimary, fontSize = 13.sp)
      Text(subtitle, fontSize = 12.sp, color = BharatTextSecondary)
    }
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
        OrderStatus.CONFIRMED -> Color(0xFFDCFCE7)
        OrderStatus.PREPARING -> Color(0xFFE0F2FE)
        OrderStatus.READY_FOR_PICKUP -> Color(0xFFF0FDF4)
        OrderStatus.COMPLETED -> Color(0xFFF1F5F9)
        OrderStatus.CANCELLED -> Color(0xFFFFE4E6)
      }
      val textTint = when(order.status) {
        OrderStatus.PLACED -> Color(0xFFD97706)
        OrderStatus.CONFIRMED -> Color(0xFF10B981)
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

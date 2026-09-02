package com.kks.bharatkirana.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.google.android.gms.location.LocationServices
import com.kks.bharatkirana.data.model.*
import com.kks.bharatkirana.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
  onDeleteProduct: (String) -> Unit = {},
  onSupportClick: () -> Unit = {},
  onRefreshStatus: () -> Unit = {},
  onManagePlan: () -> Unit = {},
  onOpenProfile: () -> Unit = {},
  onOpenNotifications: () -> Unit = {},
  onOpenOrderDetails: (String) -> Unit = {},
  onOpenPickup: () -> Unit = {},
  unreadNotificationCount: Int = 0,
  currentTierName: String? = null,
  currentTierItemCap: Int = 10,
  initialTab: Int = 0,
  onInitialTabConsumed: () -> Unit = {},
  initialEditProductId: String? = null,
  onInitialEditProductConsumed: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  var selectedTab by remember { mutableIntStateOf(initialTab) } // 0: Overview, 1: Inventory, 2: Orders, 3: Plan
  // If a caller (e.g. Add Product success) asked for a specific starting tab
  // AFTER the screen already exists, honour it and reset the flag.
  LaunchedEffect(initialTab) {
    if (initialTab != 0) {
      selectedTab = initialTab
      onInitialTabConsumed()
    }
  }
  var showEditShopDialog by remember { mutableStateOf(false) }
  var editingProductId by remember { mutableStateOf<String?>(null) }
  var editingProductPrice by remember { mutableStateOf("") }
  var editingProductQty by remember { mutableStateOf("") }
  var inventorySearch by remember { mutableStateOf("") }
  var inventoryFilter by remember { mutableStateOf("All") }
  var pendingDeleteProductId by remember { mutableStateOf<String?>(null) }

  // Open the edit dialog automatically when the caller pre-selected a product
  // (used by the duplicate-alert "Update Stock" action).
  LaunchedEffect(initialEditProductId, products) {
    val id = initialEditProductId ?: return@LaunchedEffect
    val target = products.firstOrNull { it.id == id }
    if (target != null) {
      editingProductId = id
      editingProductPrice = target.currentPrice.toString()
      editingProductQty = (target.stockQty?.toString().orEmpty())
      onInitialEditProductConsumed()
    }
  }

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
          }
        },
        actions = {
          IconButton(onClick = onOpenPickup) {
            Box(
              modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
              contentAlignment = Alignment.Center
            ) {
              Icon(Icons.Default.QrCodeScanner, contentDescription = "Verify pickup", tint = BharatPurplePrimary, modifier = Modifier.size(20.dp))
            }
          }
          IconButton(onClick = onOpenNotifications) {
            Box(contentAlignment = Alignment.TopEnd) {
              Box(
                modifier = Modifier
                  .size(36.dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
              ) {
                Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = BharatPurplePrimary, modifier = Modifier.size(20.dp))
              }
              if (unreadNotificationCount > 0) {
                Surface(
                  color = Color(0xFFDC2626),
                  shape = CircleShape,
                  modifier = Modifier.padding(top = 2.dp, end = 2.dp)
                ) {
                  Text(
                    text = if (unreadNotificationCount > 9) "9+" else unreadNotificationCount.toString(),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                  )
                }
              }
            }
          }
          // All account-related actions (edit store, edit personal info,
          // subscription, support, logout) live inside the VendorProfileScreen
          // this opens.
          IconButton(onClick = onOpenProfile) {
            Box(
              modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
              contentAlignment = Alignment.Center
            ) {
              Icon(Icons.Default.Person, contentDescription = "Profile", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
      )
    },
    bottomBar = {
      NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) {
        NavigationBarItem(selected = selectedTab == 0, onClick = { selectedTab = 0 }, icon = { Icon(Icons.Default.Dashboard, null) }, label = { Text("Overview") })
        NavigationBarItem(selected = selectedTab == 1, onClick = { selectedTab = 1 }, icon = { Icon(Icons.Default.Inventory, null) }, label = { Text("Inventory") })
        NavigationBarItem(selected = selectedTab == 2, onClick = { selectedTab = 2 }, icon = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, null) }, label = { Text("Orders") })
        NavigationBarItem(selected = selectedTab == 3, onClick = { selectedTab = 3 }, icon = { Icon(Icons.Default.CardMembership, null) }, label = { Text("Plan") })
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
              modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
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

              // ACTIVE ORDERS — the Overview answers "what do I need to do
              // right now?", so it deliberately excludes COMPLETED / CANCELLED.
              // History still lives on the Orders tab.
              val activeStatusPriority: (OrderStatus) -> Int = { s ->
                when (s) {
                  OrderStatus.PLACED -> 0
                  OrderStatus.CONFIRMED -> 1
                  OrderStatus.PREPARING -> 2
                  OrderStatus.READY_FOR_PICKUP -> 3
                  else -> 99
                }
              }
              val activeOrders = orders
                .filter { it.status != OrderStatus.COMPLETED && it.status != OrderStatus.CANCELLED }
                .sortedWith(compareBy<Order> { activeStatusPriority(it.status) }.thenByDescending { it.createdAt.ifBlank { it.orderDate } })
                .take(3)
              item {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = "Active Orders",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = BharatTextPrimary,
                    modifier = Modifier.weight(1f)
                  )
                  if (activeOrders.isNotEmpty()) {
                    TextButton(onClick = { selectedTab = 2 }) {
                      Text("View All", color = BharatPurplePrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                  }
                }
              }
              if (activeOrders.isEmpty()) {
                item {
                  Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                  ) {
                    Column(
                      modifier = Modifier.padding(20.dp),
                      horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                      Box(
                        modifier = Modifier.size(56.dp).clip(CircleShape).background(BharatPurpleContainer),
                        contentAlignment = Alignment.Center
                      ) {
                        Icon(Icons.AutoMirrored.Filled.ReceiptLong, null, tint = BharatPurplePrimary, modifier = Modifier.size(28.dp))
                      }
                      Spacer(modifier = Modifier.height(10.dp))
                      Text("No active orders", fontWeight = FontWeight.Bold, color = BharatTextPrimary)
                      Spacer(modifier = Modifier.height(4.dp))
                      Text(
                        "New customer orders will appear here in real time.",
                        fontSize = 12.sp,
                        color = BharatTextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                      )
                    }
                  }
                }
              } else {
                items(activeOrders, key = { it.id }) { order ->
                  LatestOrderCard(order = order, onOpen = { onOpenOrderDetails(order.id) })
                }
              }

              // REVENUE big purple card (unchanged shape, just moved up).
              item {
                Card(
                  shape = RoundedCornerShape(24.dp),
                  modifier = Modifier.fillMaxWidth(),
                  colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                  Box(modifier = Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(AppPrimary, AppPrimaryDarker))).padding(20.dp)) {
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

              // RECENT ORDERS block moved to the top of Overview above Revenue.
              // See the "Latest Orders" block earlier in this LazyColumn.

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

              // Shop location card removed from Overview — vendors edit shop
              // address in Profile → Edit Store Details.

              // Store Operations card (unchanged, at bottom).
              item {
                Card(
                  shape = RoundedCornerShape(24.dp),
                  colors = CardDefaults.cardColors(containerColor = Color.White),
                  modifier = Modifier.fillMaxWidth(),
                  border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                  Column(modifier = Modifier.padding(20.dp)) {
                    Text(text = "STORE OPERATIONS", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = BharatTextSecondary, letterSpacing = 0.5.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OperationToggleRow(title = "Accepting Orders", subtitle = if (shop.isOpen) "Shop is currently open" else "Shop is closed", checked = shop.isOpen, onCheckedChange = { onUpdateShop(shop.id, shop.copy(isOpen = it)) }, color = BharatGreen)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFF1F5F9))
                    OperationToggleRow(title = "Auto-Accept Orders", subtitle = "Instantly confirm new orders", checked = shop.autoConfirm, onCheckedChange = { onUpdateShop(shop.id, shop.copy(autoConfirm = it)) }, color = BharatPurplePrimary)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFF1F5F9))
                    OperationToggleRow(title = "Promoted Placement", subtitle = "Coming soon \u2014 pay to appear at the top of nearby shops", checked = false, onCheckedChange = {}, color = Color(0xFFD97706), isAd = true, enabled = false)
                  }
                }
              }
            }
          }
          1 -> {
            val q = inventorySearch.trim()
            val filteredInventory = products.filter { p ->
              val matchesQuery = q.isEmpty() ||
                p.name.contains(q, ignoreCase = true) ||
                p.brand.contains(q, ignoreCase = true)
              val matchesFilter = when (inventoryFilter) {
                "In Stock" -> p.stockStatus == "In Stock"
                "Low Stock" -> p.stockStatus == "Low Stock"
                "Out of Stock" -> p.stockStatus == "Out of Stock"
                else -> true
              }
              matchesQuery && matchesFilter
            }
            val outCount = products.count { it.stockStatus == "Out of Stock" }
            val lowCount = products.count { it.stockStatus == "Low Stock" }
            val inCount = products.count { it.stockStatus == "In Stock" }

            LazyColumn(
              modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
              contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              item {
                Button(
                  onClick = onManageProducts,
                  modifier = Modifier.fillMaxWidth().height(50.dp),
                  colors = ButtonDefaults.buttonColors(containerColor = BharatPurplePrimary),
                  shape = RoundedCornerShape(12.dp)
                ) {
                  Icon(Icons.Default.Add, null, tint = Color.White)
                  Spacer(Modifier.width(8.dp))
                  Text("Add New Product", fontWeight = FontWeight.Bold, color = Color.White)
                }
              }

              item {
                OutlinedTextField(
                  value = inventorySearch,
                  onValueChange = { inventorySearch = it },
                  placeholder = { Text("Search products…", color = BharatTextMuted) },
                  leadingIcon = { Icon(Icons.Default.Search, null, tint = BharatTextSecondary) },
                  trailingIcon = {
                    if (inventorySearch.isNotEmpty()) {
                      IconButton(onClick = { inventorySearch = "" }) {
                        Icon(Icons.Default.Close, null, tint = BharatTextSecondary)
                      }
                    }
                  },
                  singleLine = true,
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier.fillMaxWidth(),
                  colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = BharatTextPrimary,
                    unfocusedTextColor = BharatTextPrimary,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = BharatPurplePrimary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                  )
                )
              }

              item {
                val filters = listOf(
                  "All" to products.size,
                  "In Stock" to inCount,
                  "Low Stock" to lowCount,
                  "Out of Stock" to outCount
                )
                Row(
                  modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  filters.forEach { (label, count) ->
                    val selected = inventoryFilter == label
                    FilterChip(
                      selected = selected,
                      onClick = { inventoryFilter = label },
                      label = { Text("$label · $count", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                      colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.White,
                        selectedContainerColor = BharatPurplePrimary,
                        labelColor = BharatTextPrimary,
                        selectedLabelColor = Color.White
                      ),
                      border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        borderColor = MaterialTheme.colorScheme.outline,
                        selectedBorderColor = BharatPurplePrimary
                      )
                    )
                  }
                }
              }

              if (filteredInventory.isEmpty()) {
                item {
                  Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                    contentAlignment = Alignment.Center
                  ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                      Icon(Icons.Default.Inventory2, null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(48.dp))
                      Spacer(Modifier.height(10.dp))
                      Text(
                        text = if (products.isEmpty()) "No products yet" else "No products match this filter",
                        fontWeight = FontWeight.Bold,
                        color = BharatTextPrimary
                      )
                      Text(
                        text = if (products.isEmpty()) "Tap Add New Product to list your first item." else "Try a different filter or search.",
                        fontSize = 12.sp,
                        color = BharatTextSecondary,
                        textAlign = TextAlign.Center
                      )
                    }
                  }
                }
              } else {
                items(filteredInventory, key = { it.id }) { product ->
                  InventoryRow(
                    product = product,
                    onEdit = {
                      editingProductId = product.id
                      editingProductPrice = product.currentPrice.toString()
                      editingProductQty = product.stockQty?.toString().orEmpty()
                    },
                    onToggleAvailability = { onUpdateProductStock(product.id, !product.inStock) },
                    onDelete = { pendingDeleteProductId = product.id }
                  )
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
              modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
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
                    onCancel = { onCancelOrder(order.id) },
                    onOpen = { onOpenOrderDetails(order.id) }
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
                    onCancel = { onCancelOrder(order.id) },
                    onOpen = { onOpenOrderDetails(order.id) }
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
                    onCancel = { onCancelOrder(order.id) },
                    onOpen = { onOpenOrderDetails(order.id) }
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
                    onCancel = null,
                    onOpen = { onOpenOrderDetails(order.id) }
                  )
                }
              }
              if (history.isNotEmpty()) {
                item { OrderSectionHeader("History", history.size, BharatTextSecondary) }
                items(history, key = { it.id }) { order ->
                  RecentOrderRow(order = order, onClick = { onOpenOrderDetails(order.id) })
                }
              }
            }
          }
          3 -> {
            // Subscription / Plan tab. Replaces the Reviews tab so vendors see
            // their plan status where it matters most; reviews moved to the
            // profile screen.
            val activeProducts = products.size
            val capPct = if (currentTierItemCap > 0) (activeProducts.toFloat() / currentTierItemCap).coerceIn(0f, 1f) else 0f
            LazyColumn(
              modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
              contentPadding = PaddingValues(16.dp),
              verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
              item {
                Text(
                  text = "Your Plan",
                  style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                  color = BharatTextPrimary
                )
              }

              // Hero plan card with brand gradient.
              item {
                Card(
                  shape = RoundedCornerShape(24.dp),
                  modifier = Modifier.fillMaxWidth(),
                  colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                  Box(
                    modifier = Modifier
                      .fillMaxWidth()
                      .background(Brush.horizontalGradient(listOf(AppPrimary, AppPrimaryDarker)))
                      .padding(20.dp)
                  ) {
                    Column {
                      Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CardMembership, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "CURRENT PLAN", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f), letterSpacing = 0.5.sp)
                      }
                      Spacer(modifier = Modifier.height(10.dp))
                      Text(
                        text = currentTierName ?: "Free",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                      )
                      Spacer(modifier = Modifier.height(4.dp))
                      Text(
                        text = "$activeProducts / $currentTierItemCap products in use",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.9f)
                      )
                      Spacer(modifier = Modifier.height(12.dp))
                      LinearProgressIndicator(
                        progress = { capPct },
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.25f),
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp))
                      )
                      Spacer(modifier = Modifier.height(16.dp))
                      Button(
                        onClick = onManagePlan,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        colors = ButtonDefaults.buttonColors(
                          containerColor = Color.White,
                          contentColor = AppPrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                      ) {
                        Icon(Icons.Default.Upgrade, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Upgrade / Change Plan", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                      }
                    }
                  }
                }
              }

              // Perks / usage summary card.
              item {
                Card(
                  shape = RoundedCornerShape(24.dp),
                  colors = CardDefaults.cardColors(containerColor = Color.White),
                  border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Column(modifier = Modifier.padding(20.dp)) {
                    Text(text = "WHAT'S INCLUDED", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = BharatTextSecondary, letterSpacing = 0.5.sp)
                    Spacer(modifier = Modifier.height(14.dp))
                    PlanPerkRow(icon = Icons.Default.Inventory, label = "Product listings", value = "$activeProducts / $currentTierItemCap")
                    Spacer(modifier = Modifier.height(10.dp))
                    PlanPerkRow(icon = Icons.Default.QrCodeScanner, label = "Barcode + catalog search", value = "Included")
                    Spacer(modifier = Modifier.height(10.dp))
                    PlanPerkRow(icon = Icons.Default.Notifications, label = "Push notifications", value = "Included")
                    Spacer(modifier = Modifier.height(10.dp))
                    PlanPerkRow(icon = Icons.Default.Campaign, label = "Promoted placement", value = "Coming soon")
                  }
                }
              }

              // Support nudge — pushes vendors to reach out before churn.
              item {
                OutlinedCard(
                  onClick = onSupportClick,
                  shape = RoundedCornerShape(20.dp),
                  colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                  border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Box(
                      modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(BharatGreen.copy(alpha = 0.12f)),
                      contentAlignment = Alignment.Center
                    ) {
                      Icon(Icons.Default.ChatBubble, contentDescription = null, tint = BharatGreen, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                      Text("Questions about your plan?", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BharatTextPrimary)
                      Text("Chat with our team on WhatsApp", fontSize = 12.sp, color = BharatTextSecondary)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = BharatTextMuted)
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
    var pendingLat by remember { mutableStateOf<Double?>(null) }
    var pendingLng by remember { mutableStateOf<Double?>(null) }
    var isFetchingLocation by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf<String?>(null) }
    var locationSuccess by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    fun fetchAndFillAddress() {
      isFetchingLocation = true
      locationError = null
      locationSuccess = false
      try {
        fusedLocationClient.getCurrentLocation(
          com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
          null
        ).addOnSuccessListener { location ->
          if (location == null) {
            isFetchingLocation = false
            locationError = "Couldn't detect your location. Try again outside or enter address manually."
            return@addOnSuccessListener
          }
          scope.launch {
            val resolvedAddress = withContext(Dispatchers.IO) {
              runCatching {
                @Suppress("DEPRECATION")
                android.location.Geocoder(context, java.util.Locale.getDefault())
                  .getFromLocation(location.latitude, location.longitude, 1)
                  ?.firstOrNull()
                  ?.getAddressLine(0)
              }.getOrNull()
            }
            isFetchingLocation = false
            if (!resolvedAddress.isNullOrBlank()) {
              addr = resolvedAddress
              pendingLat = location.latitude
              pendingLng = location.longitude
              locationSuccess = true
            } else {
              // Fall back to setting only the coordinates so the map pin still moves.
              pendingLat = location.latitude
              pendingLng = location.longitude
              locationSuccess = true
              locationError = "Location captured but couldn't resolve an address. Please edit it manually before saving."
            }
          }
        }.addOnFailureListener {
          isFetchingLocation = false
          locationError = "Couldn't detect your location. Please try again or enter address manually."
        }
      } catch (e: SecurityException) {
        isFetchingLocation = false
        locationError = "Location permission missing. Please enable it in Settings."
      }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
      ActivityResultContracts.RequestPermission()
    ) { granted ->
      if (granted) fetchAndFillAddress()
      else locationError = "Location permission denied. Please enter address manually."
    }

    AlertDialog(
      onDismissRequest = { showEditShopDialog = false },
      // Force light dialog surface \u2014 the app is designed light-first and letting
      // the Material dark scheme paint this dark on dark makes the fields
      // (which use BharatTextPrimary = near-black) unreadable.
      containerColor = Color.White,
      titleContentColor = BharatTextPrimary,
      textContentColor = BharatTextPrimary,
      title = { Text("Edit Store Details", fontWeight = FontWeight.Bold, color = BharatTextPrimary) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Shop Name") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = BharatTextPrimary, unfocusedTextColor = BharatTextPrimary, focusedLabelColor = BharatPurplePrimary, unfocusedLabelColor = BharatTextSecondary, focusedContainerColor = Color.White, unfocusedContainerColor = Color.White))
          OutlinedTextField(value = owner, onValueChange = { owner = it }, label = { Text("Owner Name") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = BharatTextPrimary, unfocusedTextColor = BharatTextPrimary, focusedLabelColor = BharatPurplePrimary, unfocusedLabelColor = BharatTextSecondary, focusedContainerColor = Color.White, unfocusedContainerColor = Color.White))
          OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = BharatTextPrimary, unfocusedTextColor = BharatTextPrimary, focusedLabelColor = BharatPurplePrimary, unfocusedLabelColor = BharatTextSecondary, focusedContainerColor = Color.White, unfocusedContainerColor = Color.White))
          OutlinedTextField(value = addr, onValueChange = { addr = it; locationSuccess = false }, label = { Text("Address") }, minLines = 2, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = BharatTextPrimary, unfocusedTextColor = BharatTextPrimary, focusedLabelColor = BharatPurplePrimary, unfocusedLabelColor = BharatTextSecondary, focusedContainerColor = Color.White, unfocusedContainerColor = Color.White))
          OutlinedButton(
            onClick = {
              val hasPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
              ) == PackageManager.PERMISSION_GRANTED
              if (hasPermission) fetchAndFillAddress()
              else locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            },
            enabled = !isFetchingLocation,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, BharatPurplePrimary),
            modifier = Modifier.fillMaxWidth()
          ) {
            if (isFetchingLocation) {
              CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = BharatPurplePrimary
              )
              Spacer(Modifier.width(8.dp))
              Text("Detecting…", color = BharatPurplePrimary, fontWeight = FontWeight.SemiBold)
            } else {
              Icon(Icons.Default.MyLocation, null, tint = BharatPurplePrimary, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(8.dp))
              Text("Use Current Location", color = BharatPurplePrimary, fontWeight = FontWeight.SemiBold)
            }
          }
          if (locationSuccess && pendingLat != null && pendingLng != null) {
            Text(
              text = "Detected. Review the address, then tap Save.",
              fontSize = 11.sp,
              color = BharatGreen
            )
          }
          if (locationError != null) {
            Text(
              text = locationError!!,
              fontSize = 11.sp,
              color = Color(0xFFDC2626)
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            onUpdateShop(
              shop.id,
              shop.copy(
                name = name,
                ownerName = owner,
                address = addr,
                phone = phone,
                lat = pendingLat ?: shop.lat,
                lng = pendingLng ?: shop.lng
              )
            )
            showEditShopDialog = false
          },
          colors = ButtonDefaults.buttonColors(containerColor = BharatPurplePrimary)
        ) { Text("Save", color = Color.White) }
      },
      dismissButton = {
        TextButton(onClick = { showEditShopDialog = false }) { Text("Cancel", color = BharatPurplePrimary) }
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
        containerColor = Color.White,
        titleContentColor = BharatTextPrimary,
        textContentColor = BharatTextPrimary,
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
              keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
              colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = BharatTextPrimary,
                unfocusedTextColor = BharatTextPrimary,
                focusedLabelColor = BharatPurplePrimary,
                unfocusedLabelColor = BharatTextSecondary,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
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
                    null -> "⚠️ Customers see \"Call to Confirm\""
                    0 -> "Customers see \"Out of Stock\""
                    in 1..5 -> "Customers see \"Low Stock\""
                    else -> "Customers see \"In Stock\" when quantity is greater than 0."
                  },
                  fontSize = 11.sp,
                  color = BharatTextSecondary
                )
              },
              colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = BharatTextPrimary,
                unfocusedTextColor = BharatTextPrimary,
                focusedLabelColor = BharatPurplePrimary,
                unfocusedLabelColor = BharatTextSecondary,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
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

  pendingDeleteProductId?.let { pid ->
    val prod = products.firstOrNull { it.id == pid }
    if (prod == null) {
      pendingDeleteProductId = null
    } else {
      AlertDialog(
        onDismissRequest = { pendingDeleteProductId = null },
        containerColor = Color.White,
        titleContentColor = BharatTextPrimary,
        textContentColor = BharatTextPrimary,
        title = { Text("Delete this product?", fontWeight = FontWeight.Bold) },
        text = {
          Text(
            text = "\"${prod.name}\" will be removed from your inventory. Customers won't be able to see or order it anymore. This can't be undone.",
            fontSize = 13.sp,
            color = BharatTextSecondary
          )
        },
        confirmButton = {
          Button(
            onClick = {
              onDeleteProduct(prod.id)
              pendingDeleteProductId = null
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
          ) { Text("Delete", color = Color.White, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
          TextButton(onClick = { pendingDeleteProductId = null }) {
            Text("Cancel", color = BharatPurplePrimary)
          }
        }
      )
    }
  }
}

@Composable
private fun InventoryRow(
  product: Product,
  onEdit: () -> Unit,
  onToggleAvailability: () -> Unit,
  onDelete: () -> Unit
) {
  var menuOpen by remember { mutableStateOf(false) }
  val status = product.stockStatus
  val (badgeColor, badgeBg) = when (status) {
    "In Stock" -> BharatGreen to Color(0xFFDCFCE7)
    "Low Stock" -> Color(0xFFD97706) to Color(0xFFFEF3C7)
    "Out of Stock" -> Color(0xFFDC2626) to Color(0xFFFEE2E2)
    else -> Color(0xFF64748B) to Color(0xFFF1F5F9)
  }
  val stockText = when {
    !product.inStock -> "Unavailable"
    product.stockQty == null -> "Qty not tracked"
    product.stockQty <= 0 -> "Out of stock"
    product.stockQty in 1..5 -> "Only ${product.stockQty} left"
    else -> "${product.stockQty} in stock"
  }
  val dim = !product.inStock

  Card(
    onClick = onEdit,
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
  ) {
    Row(
      modifier = Modifier.padding(12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(54.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(Color(0xFFF1F5F9)),
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
        Text(
          text = product.name,
          fontWeight = FontWeight.Bold,
          color = if (dim) BharatTextSecondary else BharatTextPrimary,
          maxLines = 1
        )
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = "₹${product.currentPrice}",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = BharatTextPrimary
          )
          Spacer(Modifier.width(8.dp))
          Surface(
            color = badgeBg,
            shape = RoundedCornerShape(6.dp)
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .size(6.dp)
                  .clip(CircleShape)
                  .background(badgeColor)
              )
              Spacer(Modifier.width(4.dp))
              Text(
                text = stockText,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = badgeColor
              )
            }
          }
        }
      }
      Box {
        IconButton(onClick = { menuOpen = true }) {
          Icon(Icons.Default.MoreVert, contentDescription = "More", tint = BharatTextSecondary)
        }
        DropdownMenu(
          expanded = menuOpen,
          onDismissRequest = { menuOpen = false },
          containerColor = Color.White
        ) {
          DropdownMenuItem(
            text = { Text("Edit price & stock", color = BharatTextPrimary) },
            leadingIcon = { Icon(Icons.Default.Edit, null, tint = BharatPurplePrimary) },
            onClick = {
              menuOpen = false
              onEdit()
            }
          )
          DropdownMenuItem(
            text = {
              Text(
                text = if (product.inStock) "Mark as unavailable" else "Mark as available",
                color = BharatTextPrimary
              )
            },
            leadingIcon = {
              Icon(
                imageVector = if (product.inStock) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = null,
                tint = if (product.inStock) Color(0xFFD97706) else BharatGreen
              )
            },
            onClick = {
              menuOpen = false
              onToggleAvailability()
            }
          )
          DropdownMenuItem(
            text = { Text("Delete product", color = Color(0xFFDC2626)) },
            leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFDC2626)) },
            onClick = {
              menuOpen = false
              onDelete()
            }
          )
        }
      }
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
      .background(MaterialTheme.colorScheme.background)
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
  isAd: Boolean = false,
  enabled: Boolean = true
) {
  val alpha = if (enabled) 1f else 0.55f
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
    Column(modifier = Modifier.weight(1f)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = title, fontWeight = FontWeight.Bold, color = BharatTextPrimary.copy(alpha = alpha))
        if (isAd) {
          Spacer(modifier = Modifier.width(6.dp))
          Surface(color = Color(0xFFFEF3C7), shape = RoundedCornerShape(4.dp)) {
            Text(text = if (enabled) "AD" else "SOON", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFD97706), modifier = Modifier.padding(horizontal = 4.dp))
          }
        }
      }
      Text(text = subtitle, fontSize = 12.sp, color = BharatTextSecondary.copy(alpha = alpha))
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      enabled = enabled,
      colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = color)
    )
  }
}

@Composable
fun PlanPerkRow(
  icon: ImageVector,
  label: String,
  value: String
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(
        modifier = Modifier
          .size(32.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
      ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
      }
      Spacer(modifier = Modifier.width(10.dp))
      Text(label, fontSize = 13.sp, color = BharatTextPrimary, fontWeight = FontWeight.Medium)
    }
    Text(value, fontSize = 12.sp, color = BharatTextSecondary, fontWeight = FontWeight.SemiBold)
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
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
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
fun RecentOrderRow(order: Order, onClick: () -> Unit = {}) {
  Card(
    onClick = onClick,
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
  ) {
    Row(
      modifier = Modifier.padding(16.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier.size(50.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF5F3FF)),
        contentAlignment = Alignment.Center
      ) {
        Text(text = order.displayNumber, fontWeight = FontWeight.Bold, color = BharatPurplePrimary)
      }
      Spacer(modifier = Modifier.width(16.dp))
      Column(modifier = Modifier.weight(1f)) {
        val customerLabel = order.customerName.ifBlank { "Guest customer" }
        Text(text = customerLabel, fontWeight = FontWeight.Bold, color = BharatTextPrimary)
        Text(
          text = "${order.items.size} items \u2022 ${relativeTimeLabel(order.createdAt, order.orderDate)}",
          style = MaterialTheme.typography.bodySmall,
          color = BharatTextSecondary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = "\u20b9${order.totalAmount}", fontWeight = FontWeight.ExtraBold, color = BharatTextPrimary, fontSize = 16.sp)
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
  onCancel: (() -> Unit)? = null,
  onOpen: () -> Unit = {}
) {
  Card(
    onClick = onOpen,
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF5F3FF)),
          contentAlignment = Alignment.Center
        ) {
          Text(order.displayNumber, fontWeight = FontWeight.Bold, color = BharatPurplePrimary, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(text = order.customerName.ifBlank { "Guest customer" }, fontWeight = FontWeight.Bold, color = BharatTextPrimary, fontSize = 14.sp)
          Text(
            text = "Order ${order.displayNumber} \u2022 ${order.items.size} items \u2022 ${relativeTimeLabel(order.createdAt, order.orderDate)}",
            fontSize = 12.sp,
            color = BharatTextSecondary
          )
        }
        Text(text = "\u20b9${order.totalAmount}", fontWeight = FontWeight.ExtraBold, color = BharatTextPrimary, fontSize = 16.sp)
      }

      if (order.items.isNotEmpty()) {
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = Color(0xFFF1F5F9))
        Spacer(modifier = Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          order.items.forEach { item ->
            VendorOrderLineItem(item)
          }
        }
        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = Color(0xFFF1F5F9))
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("Order total", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = BharatTextSecondary)
          Text("\u20b9${order.totalAmount}", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = BharatTextPrimary)
        }
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

@Composable
private fun VendorOrderLineItem(item: CartItem) {
  val previewUrl = item.product.imageUrls.firstOrNull { it.isNotBlank() }
    ?: item.product.imageUrl.takeIf { it.isNotBlank() }
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(
      modifier = Modifier
        .size(44.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(Color(0xFFF1F5F9)),
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
        Icon(Icons.Default.Inventory, null, tint = BharatPurplePrimary, modifier = Modifier.size(20.dp))
      }
    }
    Spacer(modifier = Modifier.width(10.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(item.product.name, fontWeight = FontWeight.SemiBold, color = BharatTextPrimary, fontSize = 13.sp, maxLines = 1)
      Text(
        text = buildString {
          append(item.selectedWeight.label)
          append(" \u2022 Qty ${item.quantity}")
          append(" \u2022 \u20b9${item.selectedWeight.price} each")
        },
        fontSize = 11.sp,
        color = BharatTextSecondary,
        maxLines = 1
      )
    }
    Text("\u20b9${item.totalPrice}", fontWeight = FontWeight.Bold, color = BharatTextPrimary, fontSize = 13.sp)
  }
}

/**
 * Polished vendor "Latest Orders" card. Driven entirely by real fields on the
 * Order model — no placeholders. Active statuses render with a stronger left
 * accent bar so a vendor sees which rows need action at a glance.
 */
@Composable
private fun LatestOrderCard(order: Order, onOpen: () -> Unit) {
  val (accentColor, chipBg, chipText, actionLabel) = orderStatusVisuals(order.status)
  val isActive = when (order.status) {
    OrderStatus.PLACED, OrderStatus.CONFIRMED, OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP -> true
    else -> false
  }
  val itemsPreview = buildItemsPreviewLabel(order.items)

  Card(
    onClick = onOpen,
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(1.dp, if (isActive) accentColor.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline),
    elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 3.dp else 0.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(modifier = Modifier.fillMaxWidth()) {
      // Left accent bar — only visible for active orders to reduce visual
      // noise on completed / cancelled rows.
      Box(
        modifier = Modifier
          .width(4.dp)
          .fillMaxHeight()
          .background(if (isActive) accentColor else Color.Transparent)
      )
      Column(modifier = Modifier.weight(1f).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(44.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(accentColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = order.displayNumber,
              fontSize = 11.sp,
              fontWeight = FontWeight.ExtraBold,
              color = accentColor
            )
          }
          Spacer(Modifier.width(12.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = order.customerName.ifBlank { "Guest customer" },
              fontWeight = FontWeight.Bold,
              color = BharatTextPrimary,
              fontSize = 14.sp,
              maxLines = 1
            )
            Text(
              text = "Order ${order.displayNumber} \u2022 ${relativeTimeLabel(order.createdAt, order.orderDate)}",
              fontSize = 11.sp,
              color = BharatTextSecondary,
              maxLines = 1
            )
          }
          Surface(
            shape = RoundedCornerShape(50.dp),
            color = chipBg
          ) {
            Text(
              text = order.status.label,
              fontSize = 10.sp,
              fontWeight = FontWeight.ExtraBold,
              color = chipText,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = Color(0xFFF1F5F9))
        Spacer(modifier = Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.Inventory,
            contentDescription = null,
            tint = BharatTextSecondary,
            modifier = Modifier.size(14.dp)
          )
          Spacer(Modifier.width(6.dp))
          Text(
            text = itemsPreview.ifBlank { "Items not synced yet" },
            fontSize = 12.sp,
            color = if (itemsPreview.isBlank()) BharatTextMuted else BharatTextPrimary,
            maxLines = 1,
            modifier = Modifier.weight(1f)
          )
          Text(
            text = "\u20b9${order.totalAmount}",
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = BharatTextPrimary
          )
        }

        if (actionLabel != null) {
          Spacer(modifier = Modifier.height(10.dp))
          OutlinedButton(
            onClick = onOpen,
            border = BorderStroke(1.dp, accentColor),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().height(38.dp)
          ) {
            Text(actionLabel, color = accentColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}

private data class OrderStatusVisuals(
  val accent: Color,
  val chipBg: Color,
  val chipText: Color,
  val actionLabel: String?
)

private fun orderStatusVisuals(status: OrderStatus): OrderStatusVisuals = when (status) {
  OrderStatus.PLACED -> OrderStatusVisuals(
    accent = Color(0xFFD97706),
    chipBg = Color(0xFFFEF3C7),
    chipText = Color(0xFFD97706),
    actionLabel = "Confirm order"
  )
  OrderStatus.CONFIRMED -> OrderStatusVisuals(
    accent = Color(0xFF10B981),
    chipBg = Color(0xFFDCFCE7),
    chipText = Color(0xFF047857),
    actionLabel = "Start preparing"
  )
  OrderStatus.PREPARING -> OrderStatusVisuals(
    accent = Color(0xFF0284C7),
    chipBg = Color(0xFFE0F2FE),
    chipText = Color(0xFF0369A1),
    actionLabel = "Mark ready for pickup"
  )
  OrderStatus.READY_FOR_PICKUP -> OrderStatusVisuals(
    accent = Color(0xFF16A34A),
    chipBg = Color(0xFFF0FDF4),
    chipText = Color(0xFF166534),
    actionLabel = "Mark completed"
  )
  OrderStatus.COMPLETED -> OrderStatusVisuals(
    accent = Color(0xFF64748B),
    chipBg = Color(0xFFF1F5F9),
    chipText = Color(0xFF64748B),
    actionLabel = null
  )
  OrderStatus.CANCELLED -> OrderStatusVisuals(
    accent = Color(0xFFDC2626),
    chipBg = Color(0xFFFEE2E2),
    chipText = Color(0xFFDC2626),
    actionLabel = null
  )
}

/**
 * Build a "Milk · Bread · +2 more" preview from the real order items list.
 * Blank string means we haven't hydrated items yet — caller renders a
 * neutral "Items not synced yet" hint instead of a fake "0 items".
 */
private fun buildItemsPreviewLabel(items: List<com.kks.bharatkirana.data.model.CartItem>): String {
  if (items.isEmpty()) return ""
  val visible = items.take(2).joinToString(" \u00b7 ") { it.product.name.ifBlank { "Item" } }
  val extra = items.size - 2
  return if (extra > 0) "$visible \u00b7 +$extra more" else visible
}

/**
 * Convert an ISO-8601 `created_at` (or fall back to the legacy `order_date`
 * display string) into a Blinkit-style relative label. Used on all vendor
 * order cards so we stop showing the same hard-coded "10 mins ago" for every row.
 */
private fun relativeTimeLabel(createdAt: String, fallbackOrderDate: String): String {
  if (createdAt.isBlank()) return fallbackOrderDate.ifBlank { "Just now" }
  val instantMillis = runCatching {
    // Supabase created_at looks like "2026-09-02T09:41:12.345678+00:00".
    // SimpleDateFormat handles the millisecond precision; the +HH:mm offset
    // parses via 'X' on API 26+. Anything unparsable falls through to the
    // fallback so we never crash on a malformed timestamp.
    val normalized = createdAt.replace("Z", "+0000").let { s ->
      // Trim to millisecond precision if Supabase returned micros.
      if (s.length > 23 && s[19] == '.') s.substring(0, 23) + s.substring(s.length - 6).replace(":", "") else s
    }
    val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", java.util.Locale.US)
    fmt.parse(normalized)?.time
  }.getOrNull() ?: run {
    return fallbackOrderDate.ifBlank { "Just now" }
  }

  val diffMinutes = ((System.currentTimeMillis() - instantMillis) / 60000).coerceAtLeast(0)
  return when {
    diffMinutes < 1 -> "Just now"
    diffMinutes < 60 -> "$diffMinutes min ago"
    diffMinutes < 24 * 60 -> "${diffMinutes / 60} h ago"
    diffMinutes < 7 * 24 * 60 -> "${diffMinutes / (24 * 60)} d ago"
    else -> {
      val fmt = java.text.SimpleDateFormat("dd MMM", java.util.Locale.getDefault())
      fmt.format(java.util.Date(instantMillis))
    }
  }
}

package com.kks.bharatkirana.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kks.bharatkirana.data.model.*
import com.kks.bharatkirana.ui.components.CameraPreview
import com.kks.bharatkirana.ui.components.CustomQrCodePattern
import com.kks.bharatkirana.ui.theme.BharatBackground
import com.kks.bharatkirana.ui.theme.BharatGreen
import com.kks.bharatkirana.ui.theme.BharatGreenLight
import com.kks.bharatkirana.ui.theme.BharatPurpleAccent
import com.kks.bharatkirana.ui.theme.BharatPurpleContainer
import com.kks.bharatkirana.ui.theme.BharatPurpleDark
import com.kks.bharatkirana.ui.theme.BharatPurpleLight
import com.kks.bharatkirana.ui.theme.BharatPurplePrimary
import com.kks.bharatkirana.ui.theme.BharatTextMuted
import com.kks.bharatkirana.ui.theme.BharatTextPrimary
import com.kks.bharatkirana.ui.theme.BharatTextSecondary

enum class AdminTab(val title: String) {
  OVERVIEW("Overview"),
  LIVE_ORDERS("Live Orders"),
  INVENTORY("Inventory"),
  VENDOR_MANAGEMENT("Vendors"),
  STORE_SETTINGS("Store Settings")
}

@Composable
fun AdminDashboardScreen(
  userProfile: UserProfile,
  orders: List<Order>,
  products: List<Product>,
  shops: List<Shop>,
  categories: List<Category>,
  isStoreOpen: Boolean,
  autoConfirmOrders: Boolean,
  packingTimeMinutes: Int,
  onToggleStoreStatus: () -> Unit,
  onToggleAutoConfirm: () -> Unit,
  onUpdatePackingTime: (Int) -> Unit,
  onUpdateOrderStatus: (String, OrderStatus) -> Unit,
  onUpdateProductStock: (String, Boolean) -> Unit,
  onUpdateProductPrice: (String, Int) -> Unit,
  onAddProduct: (Product) -> Unit,
  onDeleteProduct: (String) -> Unit,
  onUpdateFullProduct: (String, String, String, Int, Int, String, Boolean, List<Uri>) -> Unit,
  onUpdateShopDetails: (String, Shop) -> Unit,
  onVerifyVendor: (String, Boolean) -> Unit,
  onVerifyPickupCode: (String) -> Order?,
  onLogout: () -> Unit,
  onBackToStorefront: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  var selectedTab by remember { mutableStateOf(AdminTab.OVERVIEW) }
  var orderFilter by remember { mutableStateOf<OrderStatus?>(null) }
  var showQrVerifierDialog by remember { mutableStateOf(false) }
  var qrVerificationInput by remember { mutableStateOf("") }
  var verifiedOrderResult by remember { mutableStateOf<Order?>(null) }
  var showVerificationSuccessMsg by remember { mutableStateOf(false) }
  var showCameraScanner by remember { mutableStateOf(false) }

  var showAddProductDialog by remember { mutableStateOf(false) }
  var editingProduct by remember { mutableStateOf<Product?>(null) }

  val realRevenue = orders.sumOf { it.totalAmount }
  val pendingPickupsCount = orders.count { it.status == OrderStatus.READY_FOR_PICKUP }
  val packingCount = orders.count { it.status == OrderStatus.PLACED || it.status == OrderStatus.PREPARING }
  val completedCount = orders.count { it.status == OrderStatus.COMPLETED }
  val lowStockCount = products.count { !it.inStock }

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
      // Admin Header
      Surface(
        color = Color.White,
        shadowElevation = 2.dp
      ) {
        Column(modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.weight(1f)
            ) {
              Box(
                modifier = Modifier
                  .size(40.dp)
                  .clip(RoundedCornerShape(10.dp))
                  .background(BharatPurplePrimary),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.Storefront,
                  contentDescription = "Store Admin",
                  tint = Color.White,
                  modifier = Modifier.size(24.dp)
                )
              }
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(
                    text = if (userProfile.isSuperAdmin) "Bharat Kirana Super Admin" else "Bharat Kirana Admin",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = BharatTextPrimary
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Box(
                    modifier = Modifier
                      .clip(RoundedCornerShape(4.dp))
                      .background(if (userProfile.isSuperAdmin) BharatPurplePrimary else if (isStoreOpen) BharatGreenLight else Color(0xFFFFE4E6))
                      .padding(horizontal = 6.dp, vertical = 2.dp)
                  ) {
                    Text(
                      text = if (userProfile.isSuperAdmin) "PLATFORM" else if (isStoreOpen) "LIVE" else "CLOSED",
                      color = Color.White,
                      fontWeight = FontWeight.ExtraBold,
                      fontSize = 9.sp
                    )
                  }
                }
                Text(
                  text = if (userProfile.isSuperAdmin) "Global Platform Control" else "Store #104 • Banjara Hills, Hyd",
                  style = MaterialTheme.typography.bodySmall,
                  color = BharatTextSecondary
                )
              }
            }

            // Right Action Buttons (Sign Out Admin / Switch Role)
            Row(verticalAlignment = Alignment.CenterVertically) {
              if (onBackToStorefront != null) {
                OutlinedButton(
                  onClick = onBackToStorefront,
                  shape = RoundedCornerShape(10.dp),
                  contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                  modifier = Modifier
                    .height(36.dp)
                    .testTag("admin_switch_to_store_button")
                ) {
                  Icon(
                    imageVector = Icons.Default.ShoppingBag,
                    contentDescription = null,
                    tint = BharatPurplePrimary,
                    modifier = Modifier.size(14.dp)
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = "Storefront",
                    fontSize = 11.sp,
                    color = BharatPurplePrimary,
                    fontWeight = FontWeight.Bold
                  )
                }
                Spacer(modifier = Modifier.width(6.dp))
              }

              Surface(
                onClick = onLogout,
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFFEF2F2),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCA5A5)),
                modifier = Modifier
                  .height(36.dp)
                  .testTag("admin_logout_button")
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(
                    imageVector = Icons.Default.Logout,
                    contentDescription = "Admin Logout",
                    tint = Color(0xFFDC2626),
                    modifier = Modifier.size(15.dp)
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = "Sign Out",
                    fontSize = 12.sp,
                    color = Color(0xFFDC2626),
                    fontWeight = FontWeight.Bold
                  )
                }
              }
            }
          }

          // Navigation Sub-tabs
          ScrollableTabRow(
            selectedTabIndex = selectedTab.ordinal,
            edgePadding = 16.dp,
            containerColor = Color.White,
            contentColor = BharatPurplePrimary,
            indicator = { tabPositions ->
              TabRowDefaults.SecondaryIndicator(
                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                color = BharatPurplePrimary,
                height = 3.dp
              )
            }
          ) {
            val visibleTabs = AdminTab.values().filter { 
              if (it == AdminTab.VENDOR_MANAGEMENT) userProfile.isSuperAdmin else true 
            }
            
            visibleTabs.forEach { tab ->
              Tab(
                selected = selectedTab == tab,
                onClick = { selectedTab = tab },
                text = {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                      text = tab.title,
                      fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Medium,
                      fontSize = 13.sp,
                      color = if (selectedTab == tab) BharatPurplePrimary else BharatTextSecondary
                    )
                    if (tab == AdminTab.LIVE_ORDERS && pendingPickupsCount > 0) {
                      Spacer(modifier = Modifier.width(6.dp))
                      Box(
                        modifier = Modifier
                          .size(18.dp)
                          .clip(CircleShape)
                          .background(BharatPurplePrimary),
                        contentAlignment = Alignment.Center
                      ) {
                        Text(
                          text = "$pendingPickupsCount",
                          color = Color.White,
                          fontSize = 10.sp,
                          fontWeight = FontWeight.Bold
                        )
                      }
                    }
                  }
                }
              )
            }
          }
        }
      }

      // Content View per Sub-Tab
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
      ) {
        when (selectedTab) {
          AdminTab.OVERVIEW -> {
            AdminOverviewView(
              isSuperAdmin = userProfile.isSuperAdmin,
              totalRevenue = realRevenue,
              ordersCount = orders.size,
              pendingPickups = pendingPickupsCount,
              packingCount = packingCount,
              completedCount = completedCount,
              lowStockCount = lowStockCount,
              packingTime = packingTimeMinutes,
              isStoreOpen = isStoreOpen,
              orders = orders,
              shops = shops,
              onOpenVerifier = {
                qrVerificationInput = ""
                verifiedOrderResult = null
                showVerificationSuccessMsg = false
                showQrVerifierDialog = true
              },
              onNavigateToOrders = { selectedTab = AdminTab.LIVE_ORDERS },
              onNavigateToInventory = { selectedTab = AdminTab.INVENTORY },
              onNavigateToVendors = { selectedTab = AdminTab.VENDOR_MANAGEMENT }
            )
          }

          AdminTab.LIVE_ORDERS -> {
            AdminLiveOrdersView(
              orders = orders,
              selectedFilter = orderFilter,
              onFilterSelect = { orderFilter = it },
              onUpdateOrderStatus = onUpdateOrderStatus,
              onOpenVerifier = {
                qrVerificationInput = ""
                verifiedOrderResult = null
                showVerificationSuccessMsg = false
                showQrVerifierDialog = true
              }
            )
          }

          AdminTab.INVENTORY -> {
            AdminInventoryView(
              products = products,
              categories = categories,
              onUpdateStock = onUpdateProductStock,
              onEditProduct = { editingProduct = it },
              onAddNewProduct = { showAddProductDialog = true },
              onDeleteProduct = onDeleteProduct
            )
          }

          AdminTab.VENDOR_MANAGEMENT -> {
            AdminVendorManagementView(
              shops = shops,
              onUpdateShop = onUpdateShopDetails,
              onVerifyVendor = onVerifyVendor
            )
          }

          AdminTab.STORE_SETTINGS -> {
            AdminStoreSettingsView(
              isStoreOpen = isStoreOpen,
              autoConfirm = autoConfirmOrders,
              packingTimeMinutes = packingTimeMinutes,
              onToggleStore = onToggleStoreStatus,
              onToggleAutoConfirm = onToggleAutoConfirm,
              onUpdatePackingTime = onUpdatePackingTime
            )
          }
        }
      }
    }
  }

  // QR / Order ID Verifier Dialog for Pickup Counter
  if (showQrVerifierDialog) {
    AlertDialog(
      onDismissRequest = { showQrVerifierDialog = false },
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.QrCodeScanner,
            contentDescription = null,
            tint = BharatPurplePrimary,
            modifier = Modifier.size(24.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text("Counter Pickup Verifier", fontWeight = FontWeight.Bold)
        }
      },
      text = {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Text(
            text = "Enter customer Order ID or scan the QR code to verify & dispense.",
            style = MaterialTheme.typography.bodySmall,
            color = BharatTextSecondary
          )

          OutlinedTextField(
            value = qrVerificationInput,
            onValueChange = {
              qrVerificationInput = it
              verifiedOrderResult = onVerifyPickupCode(it)
            },
            placeholder = { Text("e.g. KIR-XXXX") },
            label = { Text("Order ID / QR Code") },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("admin_qr_verifier_input"),
            leadingIcon = {
              Icon(imageVector = Icons.Default.QrCodeScanner, contentDescription = null, tint = BharatTextSecondary)
            },
            trailingIcon = {
              Row {
                IconButton(onClick = {
                  showCameraScanner = true
                }) {
                  Icon(imageVector = Icons.Default.PhotoCamera, contentDescription = "Scan", tint = BharatPurplePrimary)
                }
                IconButton(onClick = {
                  verifiedOrderResult = onVerifyPickupCode(qrVerificationInput)
                }) {
                  Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = BharatPurplePrimary)
                }
              }
            },
            colors = OutlinedTextFieldDefaults.colors(
              focusedTextColor = BharatTextPrimary,
              unfocusedTextColor = BharatTextPrimary
            )
          )

          // Quick sample chips for one-tap simulation
          Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            orders.take(3).forEach { order ->
              Surface(
                onClick = {
                  qrVerificationInput = order.id
                  verifiedOrderResult = order
                },
                shape = RoundedCornerShape(8.dp),
                color = BharatPurpleContainer.copy(alpha = 0.5f)
              ) {
                Text(
                  text = order.id,
                  color = BharatPurpleDark,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
              }
            }
          }

          verifiedOrderResult?.let { order ->
            Card(
              shape = RoundedCornerShape(12.dp),
              colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(modifier = Modifier.padding(12.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Text(
                    text = "Order #${order.id}",
                    fontWeight = FontWeight.Bold,
                    color = BharatTextPrimary
                  )
                  Text(
                    text = "₹${order.totalAmount}",
                    fontWeight = FontWeight.ExtraBold,
                    color = BharatPurplePrimary
                  )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = "${order.items.size} items • Status: ${order.status.label}",
                  style = MaterialTheme.typography.bodySmall,
                  color = BharatTextSecondary
                )
                Text(
                  text = "Items: ${order.items.joinToString { "${it.product.name} (${it.quantity})" }}",
                  style = MaterialTheme.typography.labelSmall,
                  color = BharatTextMuted,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis
                )
              }
            }

            if (showVerificationSuccessMsg) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                  .fillMaxWidth()
                  .background(BharatGreenLight, RoundedCornerShape(8.dp))
                  .padding(8.dp)
              ) {
                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = BharatGreen, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Order verified & handed over to customer!", color = BharatGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            verifiedOrderResult?.let {
              onUpdateOrderStatus(it.id, OrderStatus.COMPLETED)
              showVerificationSuccessMsg = true
            }
          },
          enabled = verifiedOrderResult != null && verifiedOrderResult?.status != OrderStatus.COMPLETED,
          colors = ButtonDefaults.buttonColors(containerColor = BharatPurplePrimary),
          modifier = Modifier.testTag("admin_confirm_handover_button")
        ) {
          Text("Handover & Collect")
        }
      },
      dismissButton = {
        TextButton(onClick = { showQrVerifierDialog = false }) {
          Text("Close")
        }
      }
    )
  }

  if (showCameraScanner) {
    AlertDialog(
      onDismissRequest = { showCameraScanner = false },
      title = { Text("Scan Pickup QR", fontWeight = FontWeight.Bold) },
      text = {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black)
        ) {
          CameraPreview(modifier = Modifier.fillMaxSize())
          
          // Overlay UI for scanning
          Box(
            modifier = Modifier
              .size(200.dp)
              .border(2.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
              .align(Alignment.Center)
          )
        }
      },
      confirmButton = {
        Button(onClick = { 
           // In a real implementation, you'd process the image frame for QR
           // Simulating finding an order from the list
           val sample = orders.firstOrNull()
           if (sample != null) {
              qrVerificationInput = sample.id
              verifiedOrderResult = sample
           }
           showCameraScanner = false 
        }) {
          Text("Simulate Scan Success")
        }
      },
      dismissButton = {
        TextButton(onClick = { showCameraScanner = false }) {
          Text("Cancel")
        }
      }
    )
  }

  // Edit Product Dialog (Expanded)
  editingProduct?.let { product ->
    var name by remember { mutableStateOf(product.name) }
    var unit by remember { mutableStateOf(product.unit) }
    var priceText by remember { mutableStateOf(product.currentPrice.toString()) }
    var mrpText by remember { mutableStateOf(product.originalPrice.toString()) }
    var stock by remember { mutableStateOf(product.inStock) }
    var desc by remember { mutableStateOf(product.description) }
    var selectedImageUris by remember { mutableStateOf<List<Uri>>(product.imageUrls.map { Uri.parse(it) }) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.PickMultipleVisualMedia(3),
      onResult = { uris -> if (uris.isNotEmpty()) selectedImageUris = uris }
    )

    AlertDialog(
      onDismissRequest = { editingProduct = null },
      title = { Text("Edit Product Details", fontWeight = FontWeight.Bold) },
      text = {
        Column(
          modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          // Image Edit Section
          Text(text = "Product Images (Up to 3)", style = MaterialTheme.typography.labelSmall, color = BharatTextSecondary)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { index ->
              val uri = selectedImageUris.getOrNull(index)
              Box(
                modifier = Modifier
                  .size(80.dp)
                  .clip(RoundedCornerShape(8.dp))
                  .background(Color(0xFFF1F5F9))
                  .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                  .clickable { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                contentAlignment = Alignment.Center
              ) {
                if (uri != null) {
                  AsyncImage(model = uri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                  Icon(Icons.Default.Add, contentDescription = null, tint = BharatTextMuted)
                }
              }
            }
          }

          OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Product Name") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = BharatTextPrimary, unfocusedTextColor = BharatTextPrimary))
          OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text("SKU / Unit (e.g. 1 kg)") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = BharatTextPrimary, unfocusedTextColor = BharatTextPrimary))
          
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = priceText, onValueChange = { priceText = it }, label = { Text("Price (₹)") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = BharatTextPrimary, unfocusedTextColor = BharatTextPrimary))
            OutlinedTextField(value = mrpText, onValueChange = { mrpText = it }, label = { Text("MRP (₹)") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = BharatTextPrimary, unfocusedTextColor = BharatTextPrimary))
          }
          
          OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), minLines = 2, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = BharatTextPrimary, unfocusedTextColor = BharatTextPrimary))
          
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("In Stock", modifier = Modifier.weight(1f), color = BharatTextPrimary)
            Switch(checked = stock, onCheckedChange = { stock = it })
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            val p = priceText.toIntOrNull() ?: product.currentPrice
            val m = mrpText.toIntOrNull() ?: product.originalPrice
            onUpdateFullProduct(product.id, name, unit, p, m, desc, stock, selectedImageUris)
            editingProduct = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = BharatPurplePrimary)
        ) { Text("Update All") }
      },
      dismissButton = {
        TextButton(onClick = { editingProduct = null }) { Text("Cancel") }
      }
    )
  }

  // Add Product Dialog
  if (showAddProductDialog) {
    var newName by remember { mutableStateOf("") }
    var newBrand by remember { mutableStateOf("Bharat Select") }
    var newPrice by remember { mutableStateOf("") }
    var newOriginalPrice by remember { mutableStateOf("") }
    var newUnit by remember { mutableStateOf("1 kg pack") }
    var selectedCatId by remember { mutableStateOf(categories.firstOrNull()?.id ?: "masala") }

    AlertDialog(
      onDismissRequest = { showAddProductDialog = false },
      title = { Text("Add New Kirana Product", fontWeight = FontWeight.Bold) },
      text = {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          OutlinedTextField(
            value = newName,
            onValueChange = { newName = it },
            label = { Text("Product Name") },
            placeholder = { Text("e.g. Organic Moong Dal") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          OutlinedTextField(
            value = newBrand,
            onValueChange = { newBrand = it },
            label = { Text("Brand") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )

          // Category Selector Chips
          Column {
            Text("Category", style = MaterialTheme.typography.labelSmall, color = BharatTextSecondary)
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              items(categories.filter { it.id != "all" }) { cat ->
                val isSelected = selectedCatId == cat.id
                Surface(
                  onClick = { selectedCatId = cat.id },
                  shape = RoundedCornerShape(8.dp),
                  color = if (isSelected) BharatPurplePrimary else Color(0xFFF1F5F9),
                  border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) BharatPurplePrimary else Color(0xFFE2E8F0))
                ) {
                  Text(
                    text = cat.name,
                    color = if (isSelected) Color.White else BharatTextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                  )
                }
              }
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            OutlinedTextField(
              value = newPrice,
              onValueChange = { newPrice = it },
              label = { Text("Price (₹)") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              singleLine = true,
              modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
              value = newOriginalPrice,
              onValueChange = { newOriginalPrice = it },
              label = { Text("MRP (₹)") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              singleLine = true,
              modifier = Modifier.weight(1f)
            )
          }
          OutlinedTextField(
            value = newUnit,
            onValueChange = { newUnit = it },
            label = { Text("Unit / Pack Size") },
            placeholder = { Text("e.g. 500g, 1 kg, 1 L") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            val priceVal = newPrice.toIntOrNull() ?: 50
            val mrpVal = newOriginalPrice.toIntOrNull() ?: (priceVal + 15)
            val newProd = Product(
              id = "p_custom_${System.currentTimeMillis()}",
              name = newName.ifBlank { "Kirana Item" },
              brand = newBrand.ifBlank { "Bharat Select" },
              categoryId = selectedCatId,
              currentPrice = priceVal,
              originalPrice = mrpVal,
              discountPercent = if (mrpVal > priceVal) ((mrpVal - priceVal) * 100 / mrpVal) else 0,
              unit = newUnit.ifBlank { "1 kg" },
              subtitle = "${newUnit.ifBlank { "1 kg" }} • ${newBrand.ifBlank { "Bharat Select" }}",
              weightOptions = listOf(WeightOption(newUnit.ifBlank { "1 kg" }, priceVal, mrpVal)),
              description = "High quality fresh Kirana stock.",
              inStock = true
            )
            onAddProduct(newProd)
            showAddProductDialog = false
          },
          colors = ButtonDefaults.buttonColors(containerColor = BharatPurplePrimary)
        ) {
          Text("Add to Store")
        }
      },
      dismissButton = {
        TextButton(onClick = { showAddProductDialog = false }) {
          Text("Cancel")
        }
      }
    )
  }
}

@Composable
fun AdminOverviewView(
  isSuperAdmin: Boolean = false,
  totalRevenue: Int,
  ordersCount: Int,
  pendingPickups: Int,
  packingCount: Int,
  completedCount: Int,
  lowStockCount: Int,
  packingTime: Int,
  isStoreOpen: Boolean,
  orders: List<Order>,
  shops: List<Shop> = emptyList(),
  onOpenVerifier: () -> Unit,
  onNavigateToOrders: () -> Unit,
  onNavigateToInventory: () -> Unit,
  onNavigateToVendors: () -> Unit = {}
) {
  // Use real data from the orders list for analytics
  val realGMV = orders.sumOf { it.totalAmount }
  val realOrders = orders.size
  val realPickups = orders.count { it.status == OrderStatus.READY_FOR_PICKUP }
  val realPacking = orders.count { it.status == OrderStatus.PLACED || it.status == OrderStatus.PREPARING }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp),
    contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    // Super Admin: Vendor Quick Stats
    if (isSuperAdmin) {
      item {
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigateToVendors() },
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = BharatPurpleContainer),
          border = BorderStroke(1.dp, BharatPurpleLight)
        ) {
          Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.White),
              contentAlignment = Alignment.Center
            ) {
              Icon(Icons.Default.Storefront, contentDescription = null, tint = BharatPurplePrimary)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "${shops.size} Registered Vendors",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = BharatPurplePrimary
              )
              Text(
                text = "${shops.count { it.isPartner }} verified partners active",
                style = MaterialTheme.typography.bodySmall,
                color = BharatTextSecondary
              )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = BharatPurplePrimary)
          }
        }
      }
    }
    // Quick Pickup Action Banner
    item {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .clickable { onOpenVerifier() }
          .testTag("admin_counter_pickup_action_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BharatPurplePrimary),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.QrCodeScanner,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "Counter Pickup Station",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
              )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "$realPickups orders ready for customer pickup",
              color = Color.White.copy(alpha = 0.9f),
              fontSize = 13.sp
            )
          }

          Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color.White
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
              Text(
                text = "Verify QR",
                color = BharatPurplePrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
              )
            }
          }
        }
      }
    }

    // 4 Key KPI Cards
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        // Today's GMV
        Card(
          modifier = Modifier.weight(1f),
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text("Today's GMV", style = MaterialTheme.typography.labelSmall, color = BharatTextSecondary)
              Icon(imageVector = Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = BharatGreen, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "₹$realGMV",
              style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
              color = BharatTextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text("Real-time revenue", style = MaterialTheme.typography.labelSmall, color = BharatGreen, fontSize = 10.sp)
          }
        }

        // Live Orders
        Card(
          modifier = Modifier.weight(1f),
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text("Live Orders", style = MaterialTheme.typography.labelSmall, color = BharatTextSecondary)
              Icon(imageVector = Icons.Default.ReceiptLong, contentDescription = null, tint = BharatPurplePrimary, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "$realOrders",
              style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
              color = BharatTextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text("$realPacking in processing", style = MaterialTheme.typography.labelSmall, color = BharatPurplePrimary, fontSize = 10.sp)
          }
        }
      }
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        // Ready Pickups
        Card(
          modifier = Modifier
            .weight(1f)
            .clickable { onNavigateToOrders() },
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text("Ready for Pickup", style = MaterialTheme.typography.labelSmall, color = BharatTextSecondary)
              Icon(imageVector = Icons.Default.Storefront, contentDescription = null, tint = Color(0xFFEAB308), modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "$realPickups",
              style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
              color = BharatTextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text("Avg ~$packingTime mins packing", style = MaterialTheme.typography.labelSmall, color = BharatTextMuted, fontSize = 10.sp)
          }
        }

        // Low Stock alert
        Card(
          modifier = Modifier
            .weight(1f)
            .clickable { onNavigateToInventory() },
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text("Out of Stock", style = MaterialTheme.typography.labelSmall, color = BharatTextSecondary)
              Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = if (lowStockCount > 0) Color(0xFFE11D48) else BharatGreen, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "$lowStockCount",
              style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
              color = if (lowStockCount > 0) Color(0xFFE11D48) else BharatTextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text("Tap to restock items", style = MaterialTheme.typography.labelSmall, color = BharatPurplePrimary, fontSize = 10.sp)
          }
        }
      }
    }

    // Hourly Sales Chart (Custom Canvas Bar Visualizer)
    item {
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                text = "Today's Order Rush",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = BharatTextPrimary
              )
              Text(
                text = "Peak pickup slots: 11 AM - 1 PM & 5 PM - 8 PM",
                style = MaterialTheme.typography.bodySmall,
                color = BharatTextSecondary
              )
            }
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = BharatPurpleContainer
            ) {
              Text(
                text = "LIVE",
                color = BharatPurplePrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
              )
            }
          }

          Spacer(modifier = Modifier.height(18.dp))

          // Hourly distribution bars
          val hourlyData = listOf(
            "8 AM" to 4,
            "10 AM" to 9,
            "12 PM" to 15,
            "2 PM" to 7,
            "4 PM" to 11,
            "6 PM" to 18,
            "8 PM" to 14
          )
          val maxVal = 20

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .height(120.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
          ) {
            hourlyData.forEach { (hour, count) ->
              val isPeak = count >= 15
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier.fillMaxHeight()
              ) {
                Text(
                  text = "$count",
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = if (isPeak) BharatPurplePrimary else BharatTextMuted
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                  modifier = Modifier
                    .width(24.dp)
                    .height((80 * (count.toFloat() / maxVal)).dp)
                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                    .background(
                      if (isPeak) Brush.verticalGradient(listOf(BharatPurpleAccent, BharatPurplePrimary))
                      else Brush.verticalGradient(listOf(Color(0xFFCBD5E1), Color(0xFF94A3B8)))
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = hour,
                  fontSize = 9.sp,
                  color = BharatTextSecondary,
                  fontWeight = FontWeight.Medium
                )
              }
            }
          }
        }
      }
    }

    // Payment Modes & Quick Insights
    item {
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(
            text = "Settlements & Payment Modes",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = BharatTextPrimary
          )
          Spacer(modifier = Modifier.height(12.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            PaymentModeItem("UPI (PhonePe/GPay)", "68%", "₹${(totalRevenue * 0.68).toInt()}", BharatPurplePrimary)
            PaymentModeItem("Kirana Wallet", "20%", "₹${(totalRevenue * 0.20).toInt()}", BharatGreen)
            PaymentModeItem("Pay at Counter", "12%", "₹${(totalRevenue * 0.12).toInt()}", Color(0xFFF59E0B))
          }
        }
      }
    }
  }
}

@Composable
private fun PaymentModeItem(title: String, percent: String, amount: String, color: Color) {
  Column(
    modifier = Modifier
      .background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp))
      .padding(10.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
      Spacer(modifier = Modifier.width(4.dp))
      Text(text = percent, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = color)
    }
    Spacer(modifier = Modifier.height(2.dp))
    Text(text = amount, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BharatTextPrimary)
    Text(text = title, fontSize = 9.sp, color = BharatTextSecondary, maxLines = 1)
  }
}

@Composable
fun AdminLiveOrdersView(
  orders: List<Order>,
  selectedFilter: OrderStatus?,
  onFilterSelect: (OrderStatus?) -> Unit,
  onUpdateOrderStatus: (String, OrderStatus) -> Unit,
  onOpenVerifier: () -> Unit
) {
  val filteredOrders = if (selectedFilter == null) orders else orders.filter { it.status == selectedFilter }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp)
  ) {
    Spacer(modifier = Modifier.height(12.dp))

    // Filter Chips Row
    LazyRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      item {
        FilterChip(
          title = "All (${orders.size})",
          isSelected = selectedFilter == null,
          onClick = { onFilterSelect(null) }
        )
      }
      OrderStatus.values().forEach { status ->
        val count = orders.count { it.status == status }
        item {
          FilterChip(
            title = "${status.label} ($count)",
            isSelected = selectedFilter == status,
            onClick = { onFilterSelect(status) }
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    LazyColumn(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      contentPadding = PaddingValues(bottom = 24.dp)
    ) {
      items(filteredOrders) { order ->
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            // Order Top Line
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(
                    text = "Order #${order.id}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = BharatTextPrimary
                  )
                }
                Text(
                  text = order.orderDate,
                  style = MaterialTheme.typography.bodySmall,
                  color = BharatTextMuted
                )
              }

              Surface(
                shape = RoundedCornerShape(8.dp),
                color = when (order.status) {
                  OrderStatus.PLACED -> Color(0xFFFEF3C7)
                  OrderStatus.PREPARING -> Color(0xFFE0F2FE)
                  OrderStatus.READY_FOR_PICKUP -> BharatGreenLight
                  OrderStatus.COMPLETED -> Color(0xFFF1F5F9)
                  OrderStatus.CANCELLED -> Color(0xFFFFE4E6)
                }
              ) {
                Text(
                  text = order.status.label,
                  color = when (order.status) {
                    OrderStatus.PLACED -> Color(0xFFD97706)
                    OrderStatus.PREPARING -> Color(0xFF0284C7)
                    OrderStatus.READY_FOR_PICKUP -> BharatGreen
                    OrderStatus.COMPLETED -> BharatTextSecondary
                    OrderStatus.CANCELLED -> Color(0xFFDC2626)
                  },
                  fontWeight = FontWeight.Bold,
                  fontSize = 11.sp,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
              }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Order Items
            Column(
              verticalArrangement = Arrangement.spacedBy(4.dp),
              modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                .padding(10.dp)
            ) {
              order.items.forEach { item ->
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Text(
                    text = "• ${item.product.name} (${item.selectedWeight.label}) × ${item.quantity}",
                    style = MaterialTheme.typography.bodySmall,
                    color = BharatTextPrimary,
                    fontWeight = FontWeight.Medium
                  )
                  Text(
                    text = "₹${item.totalPrice}",
                    style = MaterialTheme.typography.bodySmall,
                    color = BharatTextSecondary
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "Total: ₹${order.totalAmount}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = BharatTextPrimary
              )

              // Status Transition Buttons
              when (order.status) {
                OrderStatus.PLACED -> {
                  Button(
                    onClick = { onUpdateOrderStatus(order.id, OrderStatus.PREPARING) },
                    colors = ButtonDefaults.buttonColors(containerColor = BharatPurplePrimary),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                  ) {
                    Text("Prepare Order", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                  }
                }

                OrderStatus.PREPARING -> {
                  Button(
                    onClick = { onUpdateOrderStatus(order.id, OrderStatus.READY_FOR_PICKUP) },
                    colors = ButtonDefaults.buttonColors(containerColor = BharatGreen),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                  ) {
                    Text("Mark Ready", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                  }
                }

                OrderStatus.READY_FOR_PICKUP -> {
                  Button(
                    onClick = { onUpdateOrderStatus(order.id, OrderStatus.COMPLETED) },
                    colors = ButtonDefaults.buttonColors(containerColor = BharatPurplePrimary),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                  ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Complete Pickup", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                  }
                }

                OrderStatus.COMPLETED -> {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      imageVector = Icons.Default.CheckCircle,
                      contentDescription = null,
                      tint = BharatGreen,
                      modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Collected", fontSize = 12.sp, color = BharatGreen, fontWeight = FontWeight.Bold)
                  }
                }
                
                else -> { /* No actions for cancelled */ }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun FilterChip(
  title: String,
  isSelected: Boolean,
  onClick: () -> Unit
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(20.dp),
    color = if (isSelected) BharatPurplePrimary else Color.White,
    border = androidx.compose.foundation.BorderStroke(
      1.dp,
      if (isSelected) BharatPurplePrimary else Color(0xFFE2E8F0)
    )
  ) {
    Text(
      text = title,
      color = if (isSelected) Color.White else BharatTextPrimary,
      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
      fontSize = 12.sp,
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
    )
  }
}

@Composable
fun AdminInventoryView(
  products: List<Product>,
  categories: List<Category>,
  onUpdateStock: (String, Boolean) -> Unit,
  onEditProduct: (Product) -> Unit,
  onAddNewProduct: () -> Unit,
  onDeleteProduct: (String) -> Unit
) {
  var searchQuery by remember { mutableStateOf("") }
  var selectedCategory by remember { mutableStateOf<Category?>(null) }

  val filteredList = products.filter { prod ->
    val matchesSearch = searchQuery.isBlank() ||
      prod.name.contains(searchQuery, ignoreCase = true) ||
      prod.brand.contains(searchQuery, ignoreCase = true)
    val matchesCat = selectedCategory == null || prod.categoryId == selectedCategory?.id
    matchesSearch && matchesCat
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp)
  ) {
    Spacer(modifier = Modifier.height(12.dp))

    // Search and Add Button Bar
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        placeholder = { Text("Search catalog...") },
        leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = BharatTextMuted) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedTextColor = BharatTextPrimary,
          unfocusedTextColor = BharatTextPrimary,
          focusedContainerColor = Color.White,
          unfocusedContainerColor = Color.White,
          focusedBorderColor = BharatPurplePrimary,
          unfocusedBorderColor = Color(0xFFE2E8F0)
        ),
        modifier = Modifier.weight(1f)
      )

      Button(
        onClick = onAddNewProduct,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = BharatPurplePrimary),
        contentPadding = PaddingValues(horizontal = 12.dp),
        modifier = Modifier
          .height(52.dp)
          .testTag("admin_add_product_button")
      ) {
        Icon(imageVector = Icons.Default.Add, contentDescription = "Add Item", tint = Color.White)
        Spacer(modifier = Modifier.width(4.dp))
        Text("Add", fontWeight = FontWeight.Bold)
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Category Filter Chips
    LazyRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      item {
        FilterChip(
          title = "All (${products.size})",
          isSelected = selectedCategory == null,
          onClick = { selectedCategory = null }
        )
      }
      items(categories) { category ->
        val count = products.count { it.categoryId == category.id }
        FilterChip(
          title = "${category.name} ($count)",
          isSelected = selectedCategory?.id == category.id,
          onClick = { selectedCategory = category }
        )
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Products List
    LazyColumn(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(10.dp),
      contentPadding = PaddingValues(bottom = 32.dp)
    ) {
      items(filteredList) { product ->
        Card(
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFF8FAFC)),
              contentAlignment = Alignment.Center
            ) {
              if (product.localImageRes != null) {
                Image(
                  painter = painterResource(id = product.localImageRes),
                  contentDescription = product.name,
                  modifier = Modifier.fillMaxSize(),
                  contentScale = ContentScale.Crop
                )
              } else {
                Icon(
                  imageVector = Icons.Default.Inventory2,
                  contentDescription = null,
                  tint = BharatPurpleAccent,
                  modifier = Modifier.size(24.dp)
                )
              }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = product.name,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = BharatTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                text = "${product.brand} • ${product.unit}",
                style = MaterialTheme.typography.bodySmall,
                color = BharatTextSecondary
              )
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp)
              ) {
                Text(
                  text = "₹${product.currentPrice}",
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                  color = BharatTextPrimary
                )
                if (product.originalPrice > product.currentPrice) {
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = "₹${product.originalPrice}",
                    style = MaterialTheme.typography.bodySmall.copy(
                      textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
                    ),
                    color = BharatTextMuted
                  )
                }
              }
            }

            // Product Edit button
            IconButton(
              onClick = { onEditProduct(product) },
              modifier = Modifier.size(36.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Edit Product",
                tint = BharatPurplePrimary,
                modifier = Modifier.size(18.dp)
              )
            }

            // In Stock Toggle
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              IconButton(
                onClick = { onUpdateStock(product.id, !product.inStock) },
                modifier = Modifier.size(36.dp)
              ) {
                Icon(
                  imageVector = if (product.inStock) Icons.Default.CheckCircle else Icons.Default.Close,
                  contentDescription = "Toggle Stock",
                  tint = if (product.inStock) BharatGreen else Color(0xFFDC2626),
                  modifier = Modifier.size(24.dp)
                )
              }
              Text(
                text = if (product.inStock) "${product.stockQty} In" else "Out",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (product.inStock) BharatGreen else Color(0xFFE11D48)
              )
            }

            // Delete Product Button
            IconButton(
              onClick = { onDeleteProduct(product.id) },
              modifier = Modifier.size(36.dp)
            ) {
              Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = "Delete Product",
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(18.dp)
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun AdminStoreSettingsView(
  isStoreOpen: Boolean,
  autoConfirm: Boolean,
  packingTimeMinutes: Int,
  onToggleStore: () -> Unit,
  onToggleAutoConfirm: () -> Unit,
  onUpdatePackingTime: (Int) -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    // Store Status Switch Card
    Card(
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = Color.White),
      elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Online Store Operations",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = BharatTextPrimary
            )
            Text(
              text = if (isStoreOpen) "Accepting new grocery orders" else "Store is currently closed for online orders",
              style = MaterialTheme.typography.bodySmall,
              color = BharatTextSecondary
            )
          }

          Switch(
            checked = isStoreOpen,
            onCheckedChange = { onToggleStore() },
            colors = SwitchDefaults.colors(
              checkedThumbColor = Color.White,
              checkedTrackColor = BharatPurplePrimary
            )
          )
        }
      }
    }

    // Auto Confirm Orders
    Card(
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = Color.White),
      elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Auto-Accept Orders",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = BharatTextPrimary
            )
            Text(
              text = "Instantly queue orders into fulfillment pipeline",
              style = MaterialTheme.typography.bodySmall,
              color = BharatTextSecondary
            )
          }

          Switch(
            checked = autoConfirm,
            onCheckedChange = { onToggleAutoConfirm() },
            colors = SwitchDefaults.colors(
              checkedThumbColor = Color.White,
              checkedTrackColor = BharatGreen
            )
          )
        }
      }
    }

    // Estimated Packing Window
    Card(
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = Color.White),
      elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Text(
          text = "Target Packing Guarantee",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = BharatTextPrimary
        )
        Text(
          text = "Customer shown ready time based on this duration",
          style = MaterialTheme.typography.bodySmall,
          color = BharatTextSecondary
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          listOf(8, 12, 15, 20, 30).forEach { mins ->
            val isSelected = packingTimeMinutes == mins
            Surface(
              onClick = { onUpdatePackingTime(mins) },
              shape = RoundedCornerShape(10.dp),
              color = if (isSelected) BharatPurplePrimary else Color(0xFFF1F5F9),
              modifier = Modifier.weight(1f)
            ) {
              Text(
                text = "$mins m",
                color = if (isSelected) Color.White else BharatTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 10.dp)
              )
            }
          }
        }
      }
    }

    // Store Info & Support
    Card(
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = Color.White),
      elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Text(
          text = "Kirana Store Details",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = BharatTextPrimary
        )
        Spacer(modifier = Modifier.height(10.dp))

        SettingInfoRow("Store Outlet", "Bharat Kirana Supermart #104")
        SettingInfoRow("Store Location", "Banjara Hills Rd 12, Hyderabad")
        SettingInfoRow("Store Timings", "7:00 AM – 10:30 PM (Daily)")
        SettingInfoRow("Merchant GSTIN", "36AAAAA0000A1Z5")
        SettingInfoRow("Support Hotline", "+91 1800 425 5555")
      }
    }
  }
}

@Composable
fun AdminVendorManagementView(
  shops: List<Shop>,
  onUpdateShop: (String, Shop) -> Unit,
  onVerifyVendor: (String, Boolean) -> Unit
) {
  var editingShop by remember { mutableStateOf<Shop?>(null) }
  var selectedFilter by remember { mutableStateOf<VendorStatus?>(null) }
  var rejectionShop by remember { mutableStateOf<Shop?>(null) }
  var rejectionReason by remember { mutableStateOf("") }

  val filteredShops = if (selectedFilter == null) shops else shops.filter { it.status == selectedFilter }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp),
    contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    item {
      Column {
        Text(
          text = "Vendor Management",
          style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
          color = BharatTextPrimary
        )
        Text(text = "Approve and manage shop partners", style = MaterialTheme.typography.bodySmall, color = BharatTextSecondary)
      }
    }

    item {
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
          FilterChip(title = "All (${shops.size})", isSelected = selectedFilter == null, onClick = { selectedFilter = null })
        }
        VendorStatus.values().forEach { status ->
          val count = shops.count { it.status == status }
          item {
            FilterChip(title = "${status.label} ($count)", isSelected = selectedFilter == status, onClick = { selectedFilter = status })
          }
        }
      }
    }

    items(filteredShops) { shop ->
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
              Box(
                modifier = Modifier
                  .size(48.dp)
                  .clip(RoundedCornerShape(12.dp))
                  .background(BharatPurpleContainer),
                contentAlignment = Alignment.Center
              ) {
                Icon(Icons.Default.Storefront, contentDescription = null, tint = BharatPurplePrimary)
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text(text = shop.name, fontWeight = FontWeight.Bold, color = BharatTextPrimary)
                Text(text = "Owner: ${shop.ownerName}", style = MaterialTheme.typography.bodySmall, color = BharatTextSecondary)
              }
            }

            Surface(
              shape = RoundedCornerShape(8.dp),
              color = when (shop.status) {
                VendorStatus.APPROVED -> BharatGreenLight
                VendorStatus.PENDING -> Color(0xFFFEF3C7)
                VendorStatus.REJECTED -> Color(0xFFFFE4E6)
                VendorStatus.SUSPENDED -> Color(0xFFF1F5F9)
              }
            ) {
              Text(
                text = shop.status.label,
                color = when (shop.status) {
                  VendorStatus.APPROVED -> BharatGreen
                  VendorStatus.PENDING -> Color(0xFFD97706)
                  VendorStatus.REJECTED -> Color(0xFFDC2626)
                  VendorStatus.SUSPENDED -> BharatTextSecondary
                },
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
              )
            }
          }

          Spacer(modifier = Modifier.height(12.dp))
          Text(text = shop.address, style = MaterialTheme.typography.bodySmall, color = BharatTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
          
          Spacer(modifier = Modifier.height(16.dp))
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { editingShop = shop }) {
              Text(text = "Edit Details", fontSize = 12.sp, color = BharatPurplePrimary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (shop.status == VendorStatus.PENDING || shop.status == VendorStatus.REJECTED) {
              Button(
                onClick = { onVerifyVendor(shop.id, true) },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BharatGreen),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(32.dp)
              ) {
                Text("Approve", fontSize = 11.sp, fontWeight = FontWeight.Bold)
              }
            } else if (shop.status == VendorStatus.APPROVED) {
              OutlinedButton(
                onClick = { rejectionShop = shop },
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFFDC2626)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(32.dp)
              ) {
                Text("Reject", fontSize = 11.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }
    }
  }

  rejectionShop?.let { shop ->
    AlertDialog(
      onDismissRequest = { rejectionShop = null },
      title = { Text("Reject Shop Application", fontWeight = FontWeight.Bold) },
      text = {
        Column {
          Text("Provide a reason for rejection for ${shop.name}:", style = MaterialTheme.typography.bodySmall)
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedTextField(
            value = rejectionReason,
            onValueChange = { rejectionReason = it },
            placeholder = { Text("e.g. Invalid document proof") },
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            onVerifyVendor(shop.id, false)
            rejectionShop = null
            rejectionReason = ""
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
        ) { Text("Confirm Reject") }
      },
      dismissButton = {
        TextButton(onClick = { rejectionShop = null }) { Text("Cancel") }
      }
    )
  }

  editingShop?.let { shop ->
    var name by remember { mutableStateOf(shop.name) }
    var owner by remember { mutableStateOf(shop.ownerName) }
    var addr by remember { mutableStateOf(shop.address) }

    AlertDialog(
      onDismissRequest = { editingShop = null },
      title = { Text("Edit Vendor: ${shop.name}") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Shop Name") })
          OutlinedTextField(value = owner, onValueChange = { owner = it }, label = { Text("Owner Name") })
          OutlinedTextField(value = addr, onValueChange = { addr = it }, label = { Text("Address") }, minLines = 2)
        }
      },
      confirmButton = {
        Button(onClick = {
          onUpdateShop(shop.id, shop.copy(name = name, ownerName = owner, address = addr))
          editingShop = null
        }) { Text("Save") }
      },
      dismissButton = {
        TextButton(onClick = { editingShop = null }) { Text("Cancel") }
      }
    )
  }
}

@Composable
private fun SettingInfoRow(label: String, value: String) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp),
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Text(text = label, style = MaterialTheme.typography.bodySmall, color = BharatTextSecondary)
    Text(text = value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = BharatTextPrimary)
  }
}

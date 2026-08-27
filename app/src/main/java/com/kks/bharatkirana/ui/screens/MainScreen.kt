package com.kks.bharatkirana.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kks.bharatkirana.data.model.AppScreen
import com.kks.bharatkirana.data.model.AuthPath
import com.kks.bharatkirana.data.model.MainTab
import com.kks.bharatkirana.data.model.UpdateStatus
import com.kks.bharatkirana.data.model.UserRole
import com.kks.bharatkirana.ui.components.BharatBottomNavigationBar
import com.kks.bharatkirana.ui.theme.BharatBackground
import com.kks.bharatkirana.ui.theme.BharatPurplePrimary
import com.kks.bharatkirana.ui.theme.BharatTextPrimary
import com.kks.bharatkirana.ui.theme.BharatTextSecondary
import com.kks.bharatkirana.ui.viewmodel.GroceryViewModel

@Composable
fun MainScreen(
  viewModel: GroceryViewModel,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val currentScreen by viewModel.currentScreen.collectAsState()
  val currentTab by viewModel.currentTab.collectAsState()
  val userProfile by viewModel.userProfile.collectAsState()
  val products by viewModel.products.collectAsState()
  val categories by viewModel.categories.collectAsState()
  val cartItems by viewModel.cartItems.collectAsState()
  val orders by viewModel.orders.collectAsState()
  val userLocation by viewModel.userLocation.collectAsState()
  val selectedProduct by viewModel.selectedProduct.collectAsState()
  val selectedCategory by viewModel.selectedCategory.collectAsState()
  val searchQuery by viewModel.searchQuery.collectAsState()
  val isStoreOpen by viewModel.isStoreOpen.collectAsState()
  val autoConfirmOrders by viewModel.autoConfirmOrders.collectAsState()
  val packingTimeMinutes by viewModel.packingTimeMinutes.collectAsState()
  val authStatusMessage by viewModel.authStatusMessage.collectAsState()
  val isAuthLoading by viewModel.isAuthLoading.collectAsState()
  val isLoading by viewModel.isLoading.collectAsState()
  val isOrderPlacing by viewModel.isOrderPlacing.collectAsState()
  val shops by viewModel.shops.collectAsState()
  val activeShopId by viewModel.activeShopId.collectAsState()
  val notifications by viewModel.notifications.collectAsState()
  val unreadNotificationCount by viewModel.unreadNotificationCount.collectAsState()

  // Round 3: Remote Config-driven state
  val isMaintenanceMode by viewModel.isMaintenanceMode.collectAsState()
  val updateStatus by viewModel.updateStatus.collectAsState()
  val promoBanner by viewModel.promoBanner.collectAsState()
  val handlingFeeRupees by viewModel.handlingFee.collectAsState()
  val minOrderForFreeHandling by viewModel.minOrderFreeHandling.collectAsState()
  val freeHandlingDiscount by viewModel.freeHandlingDiscount.collectAsState()
  val supportWhatsappNumber by viewModel.supportWhatsappNumber.collectAsState()
  val appliedPromo by viewModel.appliedPromo.collectAsState()
  val promoStatusMessage by viewModel.promoStatusMessage.collectAsState()
  val searchSuggestions by viewModel.searchSuggestions.collectAsState()
  val tierCapMessage by viewModel.tierCapMessage.collectAsState()
  val profileFetchComplete by viewModel.profileFetchComplete.collectAsState()
  val profileSyncPending by viewModel.profileSyncPending.collectAsState()
  var updateDialogDismissed by rememberSaveable { mutableStateOf(false) }

  val totalCartCount = cartItems.sumOf { it.quantity }

  // Permission Launchers
  val locationPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { permissions ->
    val granted = permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
                  permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false)
    if (granted) viewModel.fetchUserLocation()
  }

  val vendorPermissionsLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { _ -> /* Check camera results if needed */ }

  // Check and request permissions based on role/state
  LaunchedEffect(userProfile.role) {
    // Location for everyone
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
      locationPermissionLauncher.launch(arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
      ))
    }

    // Camera for Vendors
    if (userProfile.isVendor && ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
      vendorPermissionsLauncher.launch(arrayOf(Manifest.permission.CAMERA))
    }
  }

  BackHandler(enabled = (currentScreen != AppScreen.Main && currentScreen != AppScreen.AdminDashboard) || (currentScreen == AppScreen.Main && currentTab != MainTab.HOME)) {
    if (currentScreen != AppScreen.Main && currentScreen != AppScreen.AdminDashboard) {
      viewModel.navigateBack()
    } else if (currentScreen == AppScreen.Main && currentTab != MainTab.HOME) {
      viewModel.setTab(MainTab.HOME)
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      // Single place the whole app reacts to the keyboard. Because MainActivity
      // calls enableEdgeToEdge(), the window no longer resizes on its own — every
      // screen hosted below would otherwise sit *under* the IME. Shrinking here
      // lets each form's existing verticalScroll/LazyColumn auto-scroll the
      // focused TextField into view, instead of patching field by field.
      .imePadding()
      .background(BharatBackground)
  ) {
    if (isMaintenanceMode) {
      MaintenanceOverlay()
    } else {
    when (val screen = currentScreen) {
      // Brief branded loader while a persisted session is refreshed — replaces
      // showing the onboarding pager to an already-signed-in user.
      is AppScreen.Restoring -> {
        Box(
          modifier = Modifier.fillMaxSize().background(Color.White),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = "BreakQ",
              style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
              color = BharatPurplePrimary
            )
            Spacer(modifier = Modifier.height(20.dp))
            CircularProgressIndicator(color = BharatPurplePrimary, strokeWidth = 3.dp)
          }
        }
      }

      is AppScreen.Onboarding -> {
        OnboardingScreen(
          onComplete = { viewModel.navigateTo(AppScreen.Auth) }
        )
      }

      is AppScreen.Auth -> {
        AuthScreen(
          initialEmail = if (userProfile.email.isNotBlank()) userProfile.email else "",
          isLoading = isAuthLoading,
          statusMessage = authStatusMessage,
          onLogin = { email, pass, callback ->
            viewModel.loginWithPassword(email, pass, callback)
          },
          onSignup = { name, email, mobile, _, pass, callback ->
            viewModel.signUp(name, email, mobile, "", pass, callback)
          },
          onSendOtp = { identifier, callback ->
            viewModel.sendEmailOtp(identifier, callback)
          },
          onVerifyOtp = { identifier, otp, callback ->
            viewModel.verifyEmailOtp(identifier, otp, callback)
          },
          onForgotPassword = { email, callback ->
            viewModel.sendResetPasswordEmail(email, callback)
          },
          onPrivacyPolicyClick = {
            viewModel.navigateTo(AppScreen.PrivacyPolicy)
          },
          onTermsClick = {
            viewModel.navigateTo(AppScreen.TermsOfService)
          },
          onGoogleSignIn = { viewModel.signInWithGoogle() },
          onAuthSuccess = { email: String, role: UserRole, path: AuthPath ->
            viewModel.login(email, authPath = path) { user ->
              val isAdmin = user.isAdmin
              when {
                isAdmin -> viewModel.navigateTo(AppScreen.AdminDashboard)
                !user.profileCompleted -> {
                  viewModel.setPendingSignupRole(role)
                  viewModel.navigateTo(AppScreen.CompleteProfile)
                }
                user.isVendor -> viewModel.navigateTo(AppScreen.VendorDashboard)
                role == UserRole.VENDOR -> {
                  viewModel.setPendingSignupRole(role)
                  viewModel.navigateTo(AppScreen.VendorRegistration)
                }
                else -> {
                  viewModel.navigateTo(AppScreen.CustomerOnboarding)
                }
              }
            }
          }
        )
      }

      is AppScreen.SignupSplash -> {
        SignupSplashScreen(
          userEmail = screen.userEmail,
          role = screen.role,
          onContinue = {
            val isAdminRole = screen.role.equals("Store Admin", ignoreCase = true) ||
              screen.role.equals("Admin", ignoreCase = true) ||
              screen.role.equals("Super Admin", ignoreCase = true)
            val isVendor = screen.role.equals("Vendor", ignoreCase = true) ||
              screen.role.equals("Shop Owner", ignoreCase = true)
            
            when {
              isAdminRole -> viewModel.navigateTo(AppScreen.AdminDashboard)
              isVendor -> viewModel.navigateTo(AppScreen.VendorDashboard)
              else -> viewModel.navigateTo(AppScreen.CompleteProfile)
            }
          }
        )
      }

      is AppScreen.CompleteProfile -> {
        CompleteProfileScreen(
          userProfile = userProfile,
          onProfileCompleted = { name, email, mobile, address ->
            viewModel.updateProfile(name, email, mobile, address)
            val role = viewModel.pendingSignupRole.value
            viewModel.clearPendingSignupRole()
            if (role == UserRole.VENDOR) {
              viewModel.navigateTo(AppScreen.VendorRegistration)
            } else {
              viewModel.navigateTo(AppScreen.CustomerOnboarding)
            }
          }
        )
      }

      is AppScreen.RoleSelection -> {
        RoleSelectionScreen(
          onRoleSelected = { role ->
            if (role == UserRole.VENDOR) {
              viewModel.navigateTo(AppScreen.VendorRegistration)
            } else {
              viewModel.navigateTo(AppScreen.CustomerOnboarding)
            }
          }
        )
      }

      is AppScreen.CustomerOnboarding -> {
        CustomerOnboardingScreen(
          onComplete = {
            viewModel.selectShop(null)
            viewModel.setTab(MainTab.HOME)
          }
        )
      }

      is AppScreen.ProductDetail -> {
        // Same empty-list hazard as VendorDashboard: a deep link or push can open
        // this before products have loaded.
        val product = products.find { it.id == screen.productId } ?: selectedProduct ?: products.firstOrNull()
        if (product == null) {
          Box(
            modifier = Modifier.fillMaxSize().background(Color.White),
            contentAlignment = Alignment.Center
          ) {
            CircularProgressIndicator(color = BharatPurplePrimary, strokeWidth = 3.dp)
          }
        } else {
        val recommendations = products.filter { it.id != product.id }.take(4)
        val vendor = shops.find { it.id == product.shopId }
        
        ProductDetailScreen(
          product = product,
          vendor = vendor,
          recommendations = recommendations,
          cartItems = cartItems,
          onBackClick = { viewModel.navigateBack() },
          onAddToCart = { prod, weight, qty ->
            viewModel.addToCart(prod, weight, qty)
          },
          onProductClick = { prod ->
            viewModel.selectProduct(prod)
          },
          onViewCartClick = {
            viewModel.navigateTo(AppScreen.Cart)
          },
          onStoreClick = { shop ->
            viewModel.selectShop(shop.id)
            viewModel.navigateTo(AppScreen.StoreInfo)
          }
        )
        }
      }

      is AppScreen.Cart -> {
        CartScreen(
          userProfile = userProfile,
          cartItems = cartItems,
          onBackClick = { viewModel.navigateBack() },
          onUpdateQuantity = { prodId, weightLabel, delta ->
            viewModel.updateCartQuantity(prodId, weightLabel, delta)
          },
          onCheckout = {
            if (userProfile.email.isBlank()) {
              viewModel.navigateTo(AppScreen.Auth)
            } else {
              viewModel.placeOrder()
            }
          },
          onProfileClick = {
            viewModel.setTab(MainTab.PROFILE)
            viewModel.navigateTo(AppScreen.Main)
          },
          onExploreProducts = {
            viewModel.setTab(MainTab.HOME)
            viewModel.navigateTo(AppScreen.Main)
          },
          handlingFeeRupees = handlingFeeRupees,
          minOrderForFreeHandling = minOrderForFreeHandling,
          freeHandlingDiscount = freeHandlingDiscount,
          appliedPromo = appliedPromo,
          promoStatusMessage = promoStatusMessage,
          onApplyPromo = { code -> viewModel.applyPromoCode(code) },
          onClearPromo = { viewModel.clearPromoCode() },
          isCheckingOut = isOrderPlacing
        )
      }

      is AppScreen.OrderPlaced -> {
        val order = orders.find { it.id == screen.orderId } ?: orders.firstOrNull()
        if (order != null) {
          val shopDistance = shops.firstOrNull { it.id == order.shopId }?.distance
          OrderPlacedScreen(
            order = order,
            onViewOrdersClick = {
              viewModel.setTab(MainTab.ORDERS)
              viewModel.navigateTo(AppScreen.Main)
            },
            onHomeClick = {
              viewModel.setTab(MainTab.HOME)
              viewModel.navigateTo(AppScreen.Main)
            },
            shopDistanceLabel = shopDistance
          )
        }
      }

      is AppScreen.OrderDetails -> {
        val order = orders.find { it.id == screen.orderId } ?: orders.firstOrNull()
        val ratedIds by viewModel.ratedOrderIds.collectAsState()
        if (order != null) {
          val shopDistance = shops.firstOrNull { it.id == order.shopId }?.distance
          OrderDetailsScreen(
            order = order,
            onBackClick = { viewModel.navigateBack() },
            onReorder = { ord -> viewModel.reorder(ord) },
            onRateShop = { shopId, orderId, rating, review ->
              viewModel.rateShop(shopId, orderId, rating, review)
            },
            onCancelOrder = { orderId -> viewModel.cancelOrder(orderId) },
            hasAlreadyRated = ratedIds.contains(order.id),
            shopDistanceLabel = shopDistance
          )
        }
      }

      is AppScreen.PrivacyPolicy -> {
        PrivacyPolicyScreen(
          onBackClick = { viewModel.navigateBack() }
        )
      }

      is AppScreen.TermsOfService -> {
        TermsOfServiceScreen(
          onBackClick = { viewModel.navigateBack() }
        )
      }

      is AppScreen.StoreInfo -> {
        StoreInfoScreen(
          shop = shops.find { it.id == activeShopId },
          onBackClick = { viewModel.navigateBack() },
          onViewCatalog = {
            viewModel.setTab(MainTab.HOME)
          },
          onOpenDirections = { addr, lat, lng -> viewModel.openDirections(addr, lat, lng) },
          userLocation = userLocation
        )
      }

      is AppScreen.Notifications -> {
        NotificationsScreen(
          notifications = notifications,
          onBackClick = { viewModel.navigateBack() },
          onNotificationClick = { notification ->
            viewModel.markNotificationRead(notification.id)
            if (notification.orderId != null) {
              viewModel.navigateTo(AppScreen.OrderDetails(notification.orderId))
            }
          }
        )
      }

      is AppScreen.NearbyShops -> {
        NearbyShopsScreen(
          shops = shops,
          onShopClick = { shop ->
            viewModel.selectShop(shop.id)
            viewModel.navigateBack()
          },
          onProfileClick = {
            viewModel.setTab(MainTab.PROFILE)
          },
          onBackClick = { viewModel.navigateBack() },
          userInitial = userProfile.fullName.firstOrNull()?.toString() ?: "U",
          unreadNotificationCount = unreadNotificationCount,
          onNotificationsClick = { viewModel.navigateTo(AppScreen.Notifications) },
          userLocation = userLocation
        )
      }

      is AppScreen.VendorRegistration -> {
        val vendorUpload by viewModel.vendorUploadState.collectAsState()
        val vendorUploadPct by viewModel.vendorUploadPercent.collectAsState()
        val vendorUploadErr by viewModel.vendorUploadError.collectAsState()
        VendorRegistrationScreen(
          onRegisterClick = { name, owner, addr, phone, category, lat, lng, years, shopPhoto, proof ->
            viewModel.registerVendorShop(name, owner, addr, phone, category, lat, lng, years, shopPhoto, proof)
          },
          onBackClick = { viewModel.navigateBack() },
          uploadState = vendorUpload.name,
          uploadPercent = vendorUploadPct,
          uploadError = vendorUploadErr,
          onDismissUploadError = { viewModel.clearVendorUploadError() }
        )
      }

      is AppScreen.VendorDashboard -> {
        // shops is empty until loadSupabaseData() returns, and a vendor whose shop
        // row was deleted never matches. first() threw NoSuchElementException in
        // both cases.
        val vendorShop = shops.find { it.id == userProfile.shopId } ?: shops.firstOrNull()
        if (vendorShop == null) {
          Box(
            modifier = Modifier.fillMaxSize().background(Color.White),
            contentAlignment = Alignment.Center
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              CircularProgressIndicator(color = BharatPurplePrimary, strokeWidth = 3.dp)
              Spacer(modifier = Modifier.height(20.dp))
              Text(
                text = "Loading your store…",
                style = MaterialTheme.typography.bodyMedium,
                color = BharatTextSecondary
              )
              Spacer(modifier = Modifier.height(24.dp))
              OutlinedButton(onClick = { viewModel.navigateTo(AppScreen.Main) }) {
                Text("Back to shopping")
              }
            }
          }
        } else {
        val vendorSub by viewModel.vendorSubscription.collectAsState()
        val tiers by viewModel.subscriptionTiers.collectAsState()
        val currentTier = tiers.firstOrNull { it.id == vendorSub?.tierId }
        VendorDashboardScreen(
          shop = vendorShop,
          orders = orders.filter { it.shopId == vendorShop.id },
          products = products.filter { it.shopId == vendorShop.id },
          currentTierName = currentTier?.displayName,
          currentTierItemCap = currentTier?.itemCap ?: 500,
          onLogout = { viewModel.logout() },
          onManageProducts = { 
            viewModel.navigateTo(AppScreen.AddProduct)
          },
          onBackClick = { 
            viewModel.selectShop(null)
            viewModel.navigateTo(AppScreen.Main) 
          },
          onUpdateShop = { id, shop -> viewModel.updateShopDetails(id, shop) },
          onUpdateOrderStatus = { orderId, newStatus -> viewModel.updateOrderStatus(orderId, newStatus) },
          onCancelOrder = { orderId -> viewModel.cancelOrder(orderId) },
          onUpdateProductStock = { prodId, inStock -> viewModel.updateProductStock(prodId, inStock) },
          onUpdateProductPrice = { prodId, price -> viewModel.updateProductPrice(prodId, price) },
          onUpdateProductQty = { prodId, qty -> viewModel.updateProductQty(prodId, qty) },
          onSupportClick = { viewModel.openSupportWhatsApp() },
          onRefreshStatus = { viewModel.loadSupabaseData() },
          onManagePlan = { viewModel.navigateTo(AppScreen.Subscription) }
        )
        }
      }

      is AppScreen.Subscription -> {
        val vendorSub by viewModel.vendorSubscription.collectAsState()
        val tiers by viewModel.subscriptionTiers.collectAsState()
        val checkout by viewModel.checkoutState.collectAsState()
        val vendorShopId = userProfile.shopId
        val productCount = products.count { it.shopId == vendorShopId }
        SubscriptionScreen(
          tiers = tiers,
          currentSubscription = vendorSub,
          currentProductCount = productCount,
          onBackClick = { viewModel.navigateBack() },
          onUpgradeClick = { tierId -> viewModel.startPlanCheckout(tierId) },
          checkoutStatusText = when (val c = checkout) {
            is GroceryViewModel.CheckoutState.CreatingOrder -> "Starting secure payment…"
            is GroceryViewModel.CheckoutState.ReadyToPay -> "Opening Razorpay…"
            is GroceryViewModel.CheckoutState.Verifying -> "Confirming your payment…"
            is GroceryViewModel.CheckoutState.Success -> "${c.tierName} plan is now active. Enjoy!"
            is GroceryViewModel.CheckoutState.Failed -> c.reason
            else -> null
          },
          checkoutIsBusy = checkout is GroceryViewModel.CheckoutState.CreatingOrder ||
            checkout is GroceryViewModel.CheckoutState.ReadyToPay ||
            checkout is GroceryViewModel.CheckoutState.Verifying,
          checkoutSucceeded = checkout is GroceryViewModel.CheckoutState.Success,
          onCheckoutMessageDismiss = { viewModel.clearCheckoutState() }
        )
      }

      is AppScreen.AddProduct -> {
        val scannedTemplate by viewModel.scannedProductTemplate.collectAsState()
        val scannedBarcode by viewModel.scannedBarcode.collectAsState()
        val barcodeStatus by viewModel.barcodeStatusMessage.collectAsState()
        val addProductUploading by viewModel.isLoading.collectAsState()
        val addProductResult by viewModel.productUploadMessage.collectAsState()

        AddProductScreen(
          onBackClick = { viewModel.navigateBack() },
          onListProduct = { name, cat, unit, price, mrp, desc, stock, stockQty, imageUris, barcode, scannedImage ->
             viewModel.addNewProduct(name, cat, unit, price, mrp, desc, stock, stockQty, imageUris, barcode, scannedImage)
             // Round 7: stay on the screen so the vendor sees the upload result
             // banner; the dismiss button in the banner + back nav go home when ready.
          },
          onScanBarcode = { viewModel.navigateTo(AppScreen.BarcodeScanner) },
          scannedTemplate = scannedTemplate,
          scannedBarcode = scannedBarcode,
          barcodeStatusMessage = barcodeStatus,
          onScanConsumed = { viewModel.clearScannedTemplate() },
          isUploading = addProductUploading,
          uploadResultMessage = addProductResult,
          onUploadResultConsumed = {
            viewModel.clearProductUploadMessage()
            viewModel.navigateBack()
          }
        )
      }

      is AppScreen.BarcodeScanner -> {
        BarcodeScannerScreen(
          onBarcodeScanned = { code -> viewModel.onBarcodeScanned(code) },
          onCancel = { viewModel.navigateBack() }
        )
      }

      is AppScreen.ShopsForProduct -> {
        ShopsForProductScreen(
          productName = screen.productName,
          products = products,
          shops = shops,
          onBackClick = { viewModel.navigateBack() },
          onPickShop = { shopId, prodName ->
            viewModel.selectShopAndProduct(shopId, prodName)
          }
        )
      }

      is AppScreen.ResetPassword -> {
        ResetPasswordScreen(
          accessToken = screen.accessToken,
          onResetPassword = { token, pass ->
            viewModel.resetPassword(token, pass) { _, _ -> }
          },
          modifier = Modifier.fillMaxSize(),
          isLoading = isAuthLoading,
          statusMessage = authStatusMessage
        )
      }

      is AppScreen.AdminDashboard -> {
        if (!userProfile.isAdmin) {
          viewModel.navigateTo(AppScreen.Main)
        } else {
          AdminDashboardScreen(
            userProfile = userProfile,
            orders = orders,
            products = products,
            shops = shops,
            categories = categories,
            isStoreOpen = isStoreOpen,
            autoConfirmOrders = autoConfirmOrders,
            packingTimeMinutes = packingTimeMinutes,
            onToggleStoreStatus = { viewModel.toggleStoreStatus() },
            onToggleAutoConfirm = { viewModel.toggleAutoConfirm() },
            onUpdatePackingTime = { mins -> viewModel.updatePackingTime(mins) },
            onUpdateOrderStatus = { orderId, newStatus -> viewModel.updateOrderStatus(orderId, newStatus) },
            onUpdateProductStock = { prodId, inStock -> viewModel.updateProductStock(prodId, inStock) },
            onUpdateProductPrice = { prodId, newPrice -> viewModel.updateProductPrice(prodId, newPrice) },
            onAddProduct = { newProd -> viewModel.addProduct(newProd) },
            onDeleteProduct = { prodId -> viewModel.deleteProduct(prodId) },
            onUpdateFullProduct = { id, name, unit, price, mrp, desc, stock, uris ->
               viewModel.updateFullProduct(id, name, unit, price, mrp, desc, stock, uris)
            },
            onUpdateShopDetails = { shopId, shop -> 
              viewModel.updateShopDetails(shopId, shop) 
            },
            onVerifyVendor = { shopId, verified -> viewModel.verifyVendor(shopId, verified) },
            onVerifyPickupCode = { code -> viewModel.verifyPickupCode(code) },
            onLogout = { viewModel.logout() },
            onBackToStorefront = {
              viewModel.navigateTo(AppScreen.Main)
            }
          )
        }
      }

      is AppScreen.Main -> {
        Scaffold(
          bottomBar = {
            // Show Dashboard switch for professional users in Marketplace
            if (activeShopId != null || currentTab == MainTab.PROFILE || currentTab == MainTab.ORDERS) {
              Column {
                // Dashboard Switcher for Vendors/Admins
                if (currentTab == MainTab.PROFILE && (userProfile.isVendor || userProfile.isAdmin)) {
                  Button(
                    onClick = {
                      if (userProfile.isSuperAdmin) viewModel.navigateTo(AppScreen.AdminDashboard)
                      else if (userProfile.isVendor) viewModel.navigateTo(AppScreen.VendorDashboard)
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BharatPurplePrimary),
                    shape = RoundedCornerShape(12.dp)
                  ) {
                    Icon(Icons.Default.Dashboard, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Go to Management Hub", fontWeight = FontWeight.Bold)
                  }
                }

                BharatBottomNavigationBar(
                  currentTab = currentTab,
                  onTabSelected = { tab -> viewModel.setTab(tab) },
                  cartItemCount = totalCartCount
                )
              }
            }
          }
        ) { paddingValues ->
          Box(
            modifier = Modifier
              .fillMaxSize()
              .padding(paddingValues)
          ) {
            when (currentTab) {
              MainTab.HOME -> {
                if (activeShopId == null) {
                  NearbyShopsScreen(
                    shops = shops,
                    onShopClick = { shop ->
                      viewModel.selectShop(shop.id)
                    },
                    onProfileClick = {
                      viewModel.setTab(MainTab.PROFILE)
                    },
                    onBackClick = { /* Home Root */ },
                    userInitial = userProfile.fullName.firstOrNull()?.toString() ?: "U",
                    unreadNotificationCount = unreadNotificationCount,
                    onNotificationsClick = { viewModel.navigateTo(AppScreen.Notifications) },
                    userLocation = userLocation
                  )
                } else {
                  HomeScreen(
                    userProfile = userProfile,
                    categories = categories,
                    products = products,
                    cartItems = cartItems,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { q ->
                      viewModel.onSearchQueryChange(q)
                      if (q.isNotEmpty()) viewModel.setTab(MainTab.SEARCH)
                    },
                    onCategoryClick = { cat -> viewModel.selectCategory(cat) },
                    onProductClick = { prod -> viewModel.selectProduct(prod) },
                    onAddToCart = { prod ->
                      val defaultWeight = prod.weightOptions.firstOrNull()
                      if (defaultWeight != null) viewModel.addToCart(prod, defaultWeight, 1)
                    },
                    onUpdateCartQty = { prodId, weightLabel, delta ->
                      viewModel.updateCartQuantity(prodId, weightLabel, delta)
                    },
                    onViewCartClick = { viewModel.navigateTo(AppScreen.Cart) },
                    onProfileClick = { viewModel.setTab(MainTab.PROFILE) },
                    onStoreClick = { viewModel.navigateTo(AppScreen.StoreInfo) },
                    onChangeStoreClick = { viewModel.selectShop(null) },
                    onAdminClick = {
                      if (userProfile.isSuperAdmin) viewModel.navigateTo(AppScreen.AdminDashboard)
                      else if (userProfile.isVendor) viewModel.navigateTo(AppScreen.VendorDashboard)
                    },
                    onNotificationsClick = { viewModel.navigateTo(AppScreen.Notifications) },
                    unreadNotificationCount = unreadNotificationCount,
                    promoBanner = promoBanner,
                    isLoading = isLoading,
                    activeShopId = activeShopId
                  )
                }
              }

              MainTab.CATEGORIES -> {
                if (activeShopId == null) {
                  NearbyShopsScreen(
                    shops = shops,
                    onShopClick = { shop -> viewModel.selectShop(shop.id) },
                    onProfileClick = { viewModel.setTab(MainTab.PROFILE) },
                    onBackClick = { viewModel.setTab(MainTab.HOME) },
                    userInitial = userProfile.fullName.firstOrNull()?.toString() ?: "U",
                    unreadNotificationCount = unreadNotificationCount,
                    onNotificationsClick = { viewModel.navigateTo(AppScreen.Notifications) },
                    userLocation = userLocation
                  )
                } else {
                  CategoriesScreen(
                    categories = categories,
                    products = products.filter { it.shopId == activeShopId },
                    selectedCategory = selectedCategory,
                    cartItems = cartItems,
                    onSelectCategory = { cat -> viewModel.selectCategory(cat) },
                    onProductClick = { prod -> viewModel.selectProduct(prod) },
                    onAddToCart = { prod ->
                      val defaultWeight = prod.weightOptions.firstOrNull()
                      if (defaultWeight != null) viewModel.addToCart(prod, defaultWeight, 1)
                    },
                    onUpdateCartQty = { prodId, weightLabel, delta ->
                      viewModel.updateCartQuantity(prodId, weightLabel, delta)
                    },
                    onViewCartClick = { viewModel.navigateTo(AppScreen.Cart) },
                    isLoading = isLoading
                  )
                }
              }

              MainTab.SEARCH -> {
                if (activeShopId == null) {
                   NearbyShopsScreen(
                    shops = shops,
                    onShopClick = { shop -> viewModel.selectShop(shop.id) },
                    onProfileClick = { viewModel.setTab(MainTab.PROFILE) },
                    onBackClick = { viewModel.setTab(MainTab.HOME) },
                    userInitial = userProfile.fullName.firstOrNull()?.toString() ?: "U",
                    unreadNotificationCount = unreadNotificationCount,
                    onNotificationsClick = { viewModel.navigateTo(AppScreen.Notifications) },
                    userLocation = userLocation
                  )
                } else {
                  SearchScreen(
                    searchQuery = searchQuery,
                    products = products.filter { it.shopId == activeShopId },
                    cartItems = cartItems,
                    onSearchQueryChange = { q -> viewModel.onSearchQueryChange(q) },
                    onProductClick = { prod -> viewModel.selectProduct(prod) },
                    onAddToCart = { prod ->
                      val defaultWeight = prod.weightOptions.firstOrNull()
                      if (defaultWeight != null) viewModel.addToCart(prod, defaultWeight, 1)
                    },
                    onUpdateCartQty = { prodId, weightLabel, delta ->
                      viewModel.updateCartQuantity(prodId, weightLabel, delta)
                    },
                    onViewCartClick = { viewModel.navigateTo(AppScreen.Cart) },
                    suggestions = searchSuggestions,
                    onSuggestionClick = { suggestion -> viewModel.onSuggestionSelected(suggestion) }
                  )
                }
              }

              MainTab.ORDERS -> {
                OrdersScreen(
                  orders = orders,
                  onOrderClick = { order ->
                    viewModel.navigateTo(AppScreen.OrderDetails(order.id))
                  },
                  onReorder = { order ->
                    viewModel.reorder(order)
                  },
                  onExploreClick = {
                    viewModel.setTab(MainTab.HOME)
                  }
                )
              }

              MainTab.PROFILE -> {
                if (userProfile.email.isBlank()) {
                  AuthScreen(
                    initialEmail = "",
                    isLoading = isAuthLoading,
                    statusMessage = authStatusMessage,
                    onLogin = { email, pass, callback ->
                      viewModel.loginWithPassword(email, pass, callback)
                    },
                    onSignup = { name, email, mobile, _, pass, callback ->
                      viewModel.signUp(name, email, mobile, "", pass, callback)
                    },
                    onSendOtp = { email, callback ->
                      viewModel.sendEmailOtp(email, callback)
                    },
                    onVerifyOtp = { email, otp, callback ->
                      viewModel.verifyEmailOtp(email, otp, callback)
                    },
                    onForgotPassword = { email, callback ->
                      viewModel.sendResetPasswordEmail(email, callback)
                    },
                    onPrivacyPolicyClick = {
                      viewModel.navigateTo(AppScreen.PrivacyPolicy)
                    },
                    onTermsClick = {
                      viewModel.navigateTo(AppScreen.TermsOfService)
                    },
                    onAuthSuccess = { email: String, role: UserRole, path: AuthPath ->
                      viewModel.login(email, authPath = path) { user ->
                        val isAdmin = user.isAdmin
                        when {
                          isAdmin -> viewModel.navigateTo(AppScreen.AdminDashboard)
                          !user.profileCompleted -> {
                            viewModel.setPendingSignupRole(role)
                            viewModel.navigateTo(AppScreen.CompleteProfile)
                          }
                          user.isVendor -> viewModel.navigateTo(AppScreen.VendorDashboard)
                          role == UserRole.VENDOR -> {
                            viewModel.setPendingSignupRole(role)
                            viewModel.navigateTo(AppScreen.VendorRegistration)
                          }
                          else -> {
                             viewModel.navigateTo(AppScreen.CustomerOnboarding)
                          }
                        }
                      }
                    }
                  )
                } else {
                  ProfileScreen(
                    userProfile = userProfile,
                    orders = orders,
                    cartItemCount = totalCartCount,
                    onOrderClick = { order ->
                      viewModel.navigateTo(AppScreen.OrderDetails(order.id))
                    },
                    onReorder = { order ->
                      viewModel.reorder(order)
                    },
                    onCartClick = {
                      viewModel.navigateTo(AppScreen.Cart)
                    },
                    onUpdateProfile = { name, email, mobile, address ->
                      viewModel.updateProfile(name, email, mobile, address)
                    },
                    onPrivacyPolicyClick = {
                      viewModel.navigateTo(AppScreen.PrivacyPolicy)
                    },
                    onTermsClick = {
                      viewModel.navigateTo(AppScreen.TermsOfService)
                    },
                    onVendorRegisterClick = {
                      viewModel.navigateTo(AppScreen.VendorRegistration)
                    },
                    onLogout = {
                      viewModel.logout()
                    },
                    onDeleteAccount = {
                      viewModel.deleteAccount()
                    },
                    hasSupport = supportWhatsappNumber.isNotBlank(),
                    onSupportClick = { viewModel.openSupportWhatsApp() },
                    profileFetchComplete = profileFetchComplete,
                    syncPending = profileSyncPending
                  )
                }
              }
            }
          }
        }
      }
    }
    } // end of else-branch (not in maintenance)
  }

  // Update prompt (rendered on top of everything). FORCED cannot be dismissed;
  // OPTIONAL can be dismissed until the next app launch.
  val showUpdate = updateStatus == UpdateStatus.FORCED ||
    (updateStatus == UpdateStatus.OPTIONAL && !updateDialogDismissed)
  if (showUpdate) {
    UpdateAvailableDialog(
      forced = updateStatus == UpdateStatus.FORCED,
      onUpdate = { viewModel.openPlayStorePage() },
      onDismiss = { updateDialogDismissed = true }
    )
  }

  // Round 5: vendor tried to add a product beyond their tier's item cap.
  tierCapMessage?.let { msg ->
    AlertDialog(
      onDismissRequest = { viewModel.clearTierCapMessage() },
      title = { Text("Plan Limit Reached", fontWeight = FontWeight.Bold) },
      text = { Text(msg) },
      confirmButton = {
        Button(onClick = {
          viewModel.clearTierCapMessage()
          viewModel.navigateTo(AppScreen.Subscription)
        }) { Text("See Plans") }
      },
      dismissButton = {
        TextButton(onClick = { viewModel.clearTierCapMessage() }) { Text("Later") }
      }
    )
  }
}

@Composable
private fun MaintenanceOverlay() {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.White)
      .padding(24.dp),
    contentAlignment = Alignment.Center
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Icon(
        imageVector = Icons.Default.Build,
        contentDescription = null,
        tint = BharatPurplePrimary,
        modifier = Modifier.size(72.dp)
      )
      Spacer(modifier = Modifier.height(16.dp))
      Text(
        text = "We'll be right back",
        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        color = BharatTextPrimary
      )
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = "BreakQ is under scheduled maintenance. Please try again in a few minutes.",
        style = MaterialTheme.typography.bodyMedium,
        color = BharatTextSecondary,
        textAlign = TextAlign.Center
      )
    }
  }
}

@Composable
private fun UpdateAvailableDialog(
  forced: Boolean,
  onUpdate: () -> Unit,
  onDismiss: () -> Unit
) {
  AlertDialog(
    onDismissRequest = { if (!forced) onDismiss() },
    icon = { Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = BharatPurplePrimary) },
    title = { Text(if (forced) "Update required" else "Update available", fontWeight = FontWeight.Bold) },
    text = {
      Text(
        text = if (forced)
          "This version of BreakQ is no longer supported. Please update to continue."
        else
          "A new version of BreakQ is ready with improvements and fixes.",
        color = BharatTextSecondary
      )
    },
    confirmButton = {
      Button(
        onClick = onUpdate,
        colors = ButtonDefaults.buttonColors(containerColor = BharatPurplePrimary),
        shape = RoundedCornerShape(10.dp)
      ) {
        Text("Update Now", color = Color.White, fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = if (forced) null else {
      {
        TextButton(onClick = onDismiss) { Text("Later", color = BharatTextSecondary) }
      }
    }
  )
}

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
import androidx.compose.ui.unit.sp
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

/** Snapshot of the last List Product tap so the duplicate-alert "Yes, Different" button can re-fire the insert with the same values. */
private data class AddProductAttempt(
  val name: String,
  val cat: String,
  val unit: String,
  val price: Int,
  val mrp: Int,
  val desc: String,
  val stock: Boolean,
  val stockQty: Int?,
  val imageUris: List<android.net.Uri>,
  val barcode: String,
  val scannedImage: String
)

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
  val addresses by viewModel.addresses.collectAsState()
  val addressesLoading by viewModel.addressesLoading.collectAsState()
  val addressSaving by viewModel.addressSaving.collectAsState()
  val addressError by viewModel.addressError.collectAsState()
  val ordersLoading by viewModel.ordersLoading.collectAsState()
  val ordersError by viewModel.ordersError.collectAsState()
  val selectedAddress = addresses.firstOrNull { it.isDefault } ?: addresses.firstOrNull()
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
  val cartShopSwitchAlert by viewModel.cartShopSwitchAlert.collectAsState()

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

  val activeShopIdForBack by viewModel.activeShopId.collectAsState()
  BackHandler(
    enabled = (currentScreen != AppScreen.Main && currentScreen != AppScreen.AdminDashboard && currentScreen != AppScreen.VendorDashboard)
      || (currentScreen == AppScreen.Main && currentTab != MainTab.HOME)
      || (currentScreen == AppScreen.Main && currentTab == MainTab.HOME && activeShopIdForBack != null)
  ) {
    when {
      currentScreen == AppScreen.Main && currentTab != MainTab.HOME -> viewModel.setTab(MainTab.HOME)
      currentScreen == AppScreen.Main && activeShopIdForBack != null -> {
        // Clear the active-shop context on back so returning to Home later
        // doesn't keep showing that shop's categories.
        viewModel.selectShop(null)
        viewModel.navigateBack()
      }
      currentScreen != AppScreen.Main && currentScreen != AppScreen.AdminDashboard && currentScreen != AppScreen.VendorDashboard -> viewModel.navigateBack()
    }
  }

  // Global cart-shop-switch confirmation. Sits at the top of MainScreen so any
  // add-to-cart from Home / ShopDetail / ProductDetail / Categories fires it.
  cartShopSwitchAlert?.let { alert ->
    AlertDialog(
      onDismissRequest = { viewModel.dismissCartShopSwitchAlert() },
      containerColor = Color.White,
      shape = RoundedCornerShape(16.dp),
      title = { Text("Your cart contains items from another shop", fontWeight = FontWeight.Bold, color = BharatTextPrimary) },
      text = {
        Column {
          Text(
            text = "You can only order from one shop at a time. Your cart has items from \"${alert.currentShopName}\". Clear it to start a new order from \"${alert.newShopName}\"?",
            fontSize = 13.sp,
            color = BharatTextSecondary
          )
        }
      },
      confirmButton = {
        Button(
          onClick = { viewModel.clearCartAndAddPending() },
          colors = ButtonDefaults.buttonColors(containerColor = BharatPurplePrimary),
          shape = RoundedCornerShape(10.dp)
        ) {
          Text("Clear Cart & Add", color = Color.White, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { viewModel.dismissCartShopSwitchAlert() }) {
          Text("Keep Current Cart", color = BharatPurplePrimary, fontWeight = FontWeight.SemiBold)
        }
      }
    )
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
          onSignup = { name, email, mobile, _, pass, role, callback ->
            viewModel.signUp(name, email, mobile, "", pass, role, callback)
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
              // Trust serverRole exclusively. hasRealShop OR-ing with a stale
              // shopId used to route customers to VendorDashboard where the
              // lookup failed and they saw an infinite spinner.
              val isConfirmedVendor = user.serverRole == UserRole.VENDOR
              when {
                isAdmin -> viewModel.navigateTo(AppScreen.AdminDashboard)
                !user.profileCompleted -> {
                  viewModel.setPendingSignupRole(role)
                  viewModel.navigateTo(AppScreen.CompleteProfile)
                }
                isConfirmedVendor -> viewModel.navigateTo(AppScreen.VendorDashboard)
                role == UserRole.VENDOR -> {
                  viewModel.setPendingSignupRole(role)
                  viewModel.navigateTo(AppScreen.VendorRegistration)
                }
                else -> {
                  // Customers were previously routed to CustomerOnboarding
                  // ("Setting Up Your Profile" screen). That screen has no
                  // real work to do — just a photo circle and a Start
                  // Shopping button — but its title made users think the
                  // app was still loading, so they waited on it forever.
                  // Route them straight to Main (the shopping dashboard).
                  viewModel.selectShop(null)
                  viewModel.setTab(MainTab.HOME)
                  viewModel.navigateTo(AppScreen.Main)
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
              isVendor -> {
                // Vendor must complete profile (mobile/address) first, then register
                // a shop. CompleteProfile reads pendingSignupRole and forwards to
                // VendorRegistration. Jumping straight to VendorDashboard left the
                // dashboard trying to render a shop that does not exist.
                viewModel.setPendingSignupRole(UserRole.VENDOR)
                viewModel.navigateTo(AppScreen.CompleteProfile)
              }
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
              // Skip CustomerOnboarding — land the customer on the
              // shopping dashboard directly (see comment in AppScreen.Auth).
              viewModel.selectShop(null)
              viewModel.setTab(MainTab.HOME)
              viewModel.navigateTo(AppScreen.Main)
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
              viewModel.selectShop(null)
              viewModel.setTab(MainTab.HOME)
              viewModel.navigateTo(AppScreen.Main)
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
        val wishlistIds by viewModel.wishlistIds.collectAsState()

        ProductDetailScreen(
          product = product,
          vendor = vendor,
          recommendations = recommendations,
          cartItems = cartItems,
          isFavorite = product.id in wishlistIds,
          onFavoriteToggle = { viewModel.toggleWishlist(product.id) },
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
              viewModel.navigateTo(AppScreen.OrderHistory)
            },
            onTrackOrderClick = {
              viewModel.navigateTo(AppScreen.OrderDetails(order.id))
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
        val order = orders.find { it.id == screen.orderId }
        val ratedIds by viewModel.ratedOrderIds.collectAsState()
        // Populate order.items lazily so the tracker shows product images and
        // per-line totals even when the initial fetch returned only the parent
        // row (e.g. order_items embed was declined by RLS or predates the
        // migration).
        LaunchedEffect(screen.orderId, order?.items?.size) {
          if (order != null && order.items.isEmpty()) {
            viewModel.hydrateOrderItems(screen.orderId)
          }
        }
        if (order != null) {
          val orderShop = shops.firstOrNull { it.id == order.shopId }
          val shopDistance = orderShop?.distance
          OrderDetailsScreen(
            order = order,
            onBackClick = { viewModel.navigateBack() },
            onReorder = { ord -> viewModel.reorder(ord) },
            onRateShop = { shopId, orderId, rating, review ->
              viewModel.rateShop(shopId, orderId, rating, review)
            },
            onCancelOrder = { orderId -> viewModel.cancelOrder(orderId) },
            hasAlreadyRated = ratedIds.contains(order.id),
            shopDistanceLabel = shopDistance,
            shop = orderShop,
            userLocation = userLocation
          )
        } else {
          OrderNotFoundScreen(
            orderId = screen.orderId,
            onBackClick = { viewModel.navigateBack() },
            onRefresh = { viewModel.loadSupabaseData() }
          )
        }
      }

      is AppScreen.VendorOrderDetails -> {
        // Same lazy-hydrate pattern as OrderDetails — pull the items list on
        // entry if it's still empty (e.g. items_json fallback wasn't populated
        // at fetch time).
        val order = orders.find { it.id == screen.orderId }
        LaunchedEffect(screen.orderId, order?.items?.size) {
          if (order != null && order.items.isEmpty()) {
            viewModel.hydrateOrderItems(screen.orderId)
          }
        }
        if (order != null) {
          VendorOrderDetailsScreen(
            order = order,
            onBackClick = { viewModel.navigateBack() },
            onAdvanceStatus = { next -> viewModel.updateOrderStatus(order.id, next) },
            onCancelOrder = { viewModel.cancelOrder(order.id) }
          )
        } else {
          OrderNotFoundScreen(
            orderId = screen.orderId,
            onBackClick = { viewModel.navigateBack() },
            onRefresh = { viewModel.loadSupabaseData() }
          )
        }
      }

      is AppScreen.VendorPickup -> {
        val pickupState by viewModel.pickupState.collectAsState()
        // Reset the pickup workflow every time we land on the screen so a
        // previous session's success card / stale error doesn't linger.
        LaunchedEffect(Unit) { viewModel.resetPickupState() }
        VendorPickupScreen(
          pickupState = pickupState,
          onBackClick = {
            viewModel.resetPickupState()
            viewModel.navigateBack()
          },
          onScanToken = { token -> viewModel.completeOrderByPickupToken(token) },
          onFindByNumber = { number -> viewModel.findVendorOrderByNumber(number) },
          onConfirmLookup = { token -> viewModel.completeOrderByPickupToken(token) },
          onOpenOrder = { orderId ->
            viewModel.resetPickupState()
            viewModel.navigateTo(AppScreen.VendorOrderDetails(orderId))
          },
          onReset = { viewModel.resetPickupState() }
        )
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
              // Role-split: vendors land on the vendor-specific screen, so
              // they see action buttons instead of Reorder/Call Shop/Rate.
              val target = if (userProfile.serverRole == UserRole.VENDOR) {
                AppScreen.VendorOrderDetails(notification.orderId)
              } else {
                AppScreen.OrderDetails(notification.orderId)
              }
              viewModel.navigateTo(target)
            }
          },
          onMarkAllRead = { viewModel.markAllNotificationsRead() },
          onClearAll = { viewModel.clearAllNotifications() }
        )
      }

      is AppScreen.Wishlist -> {
        val wishlistIds by viewModel.wishlistIds.collectAsState()
        val wishlistProducts = products.filter { it.id in wishlistIds }
        WishlistScreen(
          products = wishlistProducts,
          onBackClick = { viewModel.navigateBack() },
          onProductClick = { prod -> viewModel.selectProduct(prod) },
          onAddToCart = { prod ->
            val defaultWeight = prod.weightOptions.firstOrNull()
            if (defaultWeight != null) viewModel.addToCart(prod, defaultWeight, 1)
          },
          onRemove = { id -> viewModel.removeFromWishlist(id) },
          onExploreClick = {
            viewModel.setTab(MainTab.HOME)
            viewModel.navigateTo(AppScreen.Main)
          }
        )
      }

      is AppScreen.OrderHistory -> {
        // Orders were previously fetched only once at login; a single failed
        // fetch left the history empty for the whole session.
        LaunchedEffect(Unit) { viewModel.refreshOrders() }
        OrdersScreen(
          orders = orders,
          isLoading = ordersLoading,
          errorMessage = ordersError,
          onRetry = { viewModel.refreshOrders() },
          onOrderClick = { order -> viewModel.navigateTo(AppScreen.OrderDetails(order.id)) },
          onReorder = { order -> viewModel.reorder(order) },
          onExploreClick = {
            viewModel.setTab(MainTab.HOME)
            viewModel.navigateTo(AppScreen.Main)
          },
          onBackClick = { viewModel.navigateBack() }
        )
      }

      is AppScreen.EditProfile -> {
        EditProfileScreen(
          userProfile = userProfile,
          syncPending = profileSyncPending,
          deliveryAddressLine = selectedAddress?.formatted.orEmpty(),
          onBackClick = { viewModel.navigateBack() },
          onManageAddresses = { viewModel.navigateTo(AppScreen.SelectLocation) },
          onSave = { name, email, mobile ->
            // profiles.address is passed through untouched so legacy data and the
            // vendor/profile sync path are unaffected by the new address book.
            viewModel.updateProfile(name, email, mobile, userProfile.address)
          }
        )
      }

      is AppScreen.SavedAddresses -> {
        SelectLocationScreen(
          addresses = addresses,
          isLoading = addressesLoading,
          onBackClick = { viewModel.navigateBack() },
          onUseCurrentLocation = {
            viewModel.fetchUserLocation()
            viewModel.navigateTo(
              AppScreen.AddEditAddress(null)
            )
          },
          onAddNewAddress = { _, _ -> viewModel.navigateTo(AppScreen.AddEditAddress(null)) },
          onSelectAddress = { address ->
            viewModel.selectAddress(address.id)
            viewModel.navigateBack()
          },
          onEditAddress = { address -> viewModel.navigateTo(AppScreen.AddEditAddress(address.id)) }
        )
      }

      is AppScreen.SelectLocation -> {
        SelectLocationScreen(
          addresses = addresses,
          isLoading = addressesLoading,
          onBackClick = { viewModel.navigateBack() },
          onUseCurrentLocation = {
            viewModel.fetchUserLocation()
            viewModel.navigateTo(AppScreen.AddEditAddress(null))
          },
          onAddNewAddress = { _, _ -> viewModel.navigateTo(AppScreen.AddEditAddress(null)) },
          onSelectAddress = { address ->
            viewModel.selectAddress(address.id)
            viewModel.navigateBack()
          },
          onEditAddress = { address -> viewModel.navigateTo(AppScreen.AddEditAddress(address.id)) }
        )
      }

      is AppScreen.AddEditAddress -> {
        val editing = screen.addressId?.let { id -> addresses.find { it.id == id } }
        AddEditAddressScreen(
          existing = editing,
          userProfile = userProfile,
          userLocation = userLocation,
          isSaving = addressSaving,
          errorMessage = addressError,
          onRequestCurrentLocation = { viewModel.fetchUserLocation() },
          onDismissError = { viewModel.clearAddressError() },
          onBackClick = { viewModel.navigateBack() },
          onSave = { address ->
            viewModel.saveAddress(address) { viewModel.navigateBack() }
          }
        )
      }

      is AppScreen.NotificationPreferences -> {
        NotificationPreferencesScreen(
          onBackClick = { viewModel.navigateBack() }
        )
      }

      is AppScreen.KiranaWallet -> {
        KiranaWalletScreen(
          userProfile = userProfile,
          onBackClick = { viewModel.navigateBack() }
        )
      }

      is AppScreen.HelpSupport -> {
        HelpSupportScreen(
          hasWhatsappSupport = supportWhatsappNumber.isNotBlank(),
          onBackClick = { viewModel.navigateBack() },
          onOpenWhatsapp = { viewModel.openSupportWhatsApp() },
          onOpenOrders = { viewModel.navigateTo(AppScreen.OrderHistory) }
        )
      }

      is AppScreen.AboutUs -> {
        AboutUsScreen(
          onBackClick = { viewModel.navigateBack() },
          onPrivacyPolicyClick = { viewModel.navigateTo(AppScreen.PrivacyPolicy) },
          onTermsClick = { viewModel.navigateTo(AppScreen.TermsOfService) }
        )
      }

      is AppScreen.AccountActions -> {
        AccountActionsScreen(
          userEmail = userProfile.email,
          onBackClick = { viewModel.navigateBack() },
          onLogout = { viewModel.logout() },
          onDeleteAccount = { viewModel.deleteAccount() }
        )
      }

      is AppScreen.NearbyShops -> {
        NearbyShopsScreen(
          shops = shops,
          onShopClick = { shop ->
            // Open the dedicated shop page; do NOT flip activeShop or return
            // to Home. This keeps the customer inside NearbyShops until they
            // navigate away, and back returns them to the list.
            viewModel.navigateTo(AppScreen.ShopDetail(shop.id))
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

      is AppScreen.ShopDetail -> {
        val shop = shops.firstOrNull { it.id == screen.shopId }
        if (shop == null) {
          LaunchedEffect(Unit) { viewModel.navigateBack() }
        } else {
          val shopProducts = products.filter { it.shopId == shop.id }
          ShopDetailScreen(
            shop = shop,
            products = shopProducts,
            cartItemCount = totalCartCount,
            cartTotal = cartItems.sumOf { it.totalPrice },
            onViewCartClick = { viewModel.navigateTo(AppScreen.Cart) },
            categories = categories,
            cartQuantityFor = { product ->
              cartItems.filter { it.product.id == product.id }.sumOf { it.quantity }
            },
            onBackClick = { viewModel.navigateBack() },
            onProductClick = { p -> viewModel.navigateTo(AppScreen.ProductDetail(p.id)) },
            onAddToCart = { p ->
              val weight = p.weightOptions.firstOrNull() ?: com.kks.bharatkirana.data.model.WeightOption(
                label = p.unit, price = p.currentPrice, originalPrice = p.originalPrice
              )
              viewModel.addToCart(p, weight, 1)
            },
            onIncreaseQty = { p ->
              val weight = p.weightOptions.firstOrNull() ?: com.kks.bharatkirana.data.model.WeightOption(
                label = p.unit, price = p.currentPrice, originalPrice = p.originalPrice
              )
              viewModel.updateCartQuantity(p.id, weight.label, +1)
            },
            onDecreaseQty = { p ->
              val weight = p.weightOptions.firstOrNull() ?: com.kks.bharatkirana.data.model.WeightOption(
                label = p.unit, price = p.currentPrice, originalPrice = p.originalPrice
              )
              viewModel.updateCartQuantity(p.id, weight.label, -1)
            }
          )
        }
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
        // Only forward to registration when the vendor genuinely has no shop
        // yet. If they have a shopId but the shops list is still loading (right
        // after app reopen), sit on a spinner - previously we would wrongly
        // dump them onto Partner-With-Us because shops was momentarily empty.
        val hasShopId = userProfile.shopId != null
        val vendorShop = shops.find { it.id == userProfile.shopId }
        if (vendorShop == null && !hasShopId) {
          LaunchedEffect(Unit) {
            viewModel.navigateTo(AppScreen.VendorRegistration)
          }
          Box(
            modifier = Modifier.fillMaxSize().background(Color.White),
            contentAlignment = Alignment.Center
          ) {
            CircularProgressIndicator(color = BharatPurplePrimary, strokeWidth = 3.dp)
          }
        } else if (vendorShop == null) {
          // shopId is set but no matching shop exists in the fetched list.
          // Wait a short beat for shops to load; if it still isn't there,
          // treat it as orphaned data and drop back to Main so the user
          // isn't trapped on an infinite spinner.
          LaunchedEffect(shops.size) {
            kotlinx.coroutines.delay(3000)
            if (shops.find { it.id == userProfile.shopId } == null) {
              viewModel.navigateTo(AppScreen.Main)
            }
          }
          Box(
            modifier = Modifier.fillMaxSize().background(Color.White),
            contentAlignment = Alignment.Center
          ) {
            CircularProgressIndicator(color = BharatPurplePrimary, strokeWidth = 3.dp)
          }
        } else {
        val vendorSub by viewModel.vendorSubscription.collectAsState()
        val tiers by viewModel.subscriptionTiers.collectAsState()
        val currentTier = tiers.firstOrNull { it.id == vendorSub?.tierId }
        val initialTab by viewModel.vendorInitialTab.collectAsState()
        val initialEditProductId by viewModel.inventoryEditProductId.collectAsState()
        LaunchedEffect(vendorShop.id) {
          viewModel.hydrateAllEmptyOrderItems()
        }
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
          // Vendors do not switch to the customer app. The back button here is
          // a no-op — the VendorDashboard is their root screen. Log out is the
          // only way off it (top-right icon on the dashboard header).
          onBackClick = { },
          onUpdateShop = { id, shop -> viewModel.updateShopDetails(id, shop) },
          onUpdateOrderStatus = { orderId, newStatus -> viewModel.updateOrderStatus(orderId, newStatus) },
          onCancelOrder = { orderId -> viewModel.cancelOrder(orderId) },
          onUpdateProductStock = { prodId, inStock -> viewModel.updateProductStock(prodId, inStock) },
          onUpdateProductPrice = { prodId, price -> viewModel.updateProductPrice(prodId, price) },
          onUpdateProductQty = { prodId, qty -> viewModel.updateProductQty(prodId, qty) },
          onDeleteProduct = { prodId -> viewModel.deleteProduct(prodId) },
          onSupportClick = { viewModel.openSupportWhatsApp() },
          onRefreshStatus = { viewModel.loadSupabaseData() },
          onManagePlan = { viewModel.navigateTo(AppScreen.Subscription) },
          onOpenProfile = { viewModel.navigateTo(AppScreen.VendorProfile) },
          onOpenNotifications = { viewModel.navigateTo(AppScreen.Notifications) },
          onOpenOrderDetails = { orderId -> viewModel.navigateTo(AppScreen.VendorOrderDetails(orderId)) },
          onOpenPickup = { viewModel.navigateTo(AppScreen.VendorPickup) },
          unreadNotificationCount = unreadNotificationCount,
          initialTab = initialTab,
          onInitialTabConsumed = { viewModel.setVendorInitialTab(0) },
          initialEditProductId = initialEditProductId,
          onInitialEditProductConsumed = { viewModel.setInventoryEditProduct(null) }
        )
        }
      }

      is AppScreen.VendorProfile -> {
        val vendorShop = shops.find { it.id == userProfile.shopId } ?: shops.firstOrNull()
        if (vendorShop == null) {
          LaunchedEffect(Unit) {
            viewModel.navigateTo(AppScreen.VendorRegistration)
          }
        } else {
          val vendorSub by viewModel.vendorSubscription.collectAsState()
          val tiers by viewModel.subscriptionTiers.collectAsState()
          val currentTier = tiers.firstOrNull { it.id == vendorSub?.tierId }
          VendorProfileScreen(
            userProfile = userProfile,
            shop = vendorShop,
            currentTierName = currentTier?.displayName,
            onBackClick = { viewModel.navigateBack() },
            onSavePersonalInfo = { name, email, mobile, address ->
              viewModel.updateProfile(name, email, mobile, address)
            },
            onUpdateShop = { id, shop -> viewModel.updateShopDetails(id, shop) },
            onManagePlan = { viewModel.navigateTo(AppScreen.Subscription) },
            onOpenReviews = { viewModel.navigateTo(AppScreen.VendorReviews) },
            onSupportClick = { viewModel.openSupportWhatsApp() },
            onLogout = { viewModel.logout() },
            totalOrders = orders.count { it.shopId == vendorShop.id },
            totalRevenue = orders.filter { it.shopId == vendorShop.id }.sumOf { it.totalAmount }
          )
        }
      }

      is AppScreen.VendorReviews -> {
        VendorReviewsScreen(onBackClick = { viewModel.navigateBack() })
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
        val catalogResults by viewModel.catalogSearchResults.collectAsState()
        val catalogLoading by viewModel.catalogSearchLoading.collectAsState()
        val productAddedSuccess by viewModel.productAddedSuccess.collectAsState()
        val duplicateAlert by viewModel.duplicateAlert.collectAsState()

        // Cache the last-attempted form values so "Yes, Different" can re-fire
        // addNewProduct with force=true. Recorded on every List Product tap.
        var lastAttempt by remember { mutableStateOf<AddProductAttempt?>(null) }

        // Auto-close AddProduct + jump to Inventory tab on the dashboard once
        // the row actually saved to Supabase. Waiting for the real success
        // signal (not just "the button was tapped") avoids landing on Inventory
        // with a phantom row that then disappears.
        LaunchedEffect(productAddedSuccess) {
          if (productAddedSuccess) {
            viewModel.setVendorInitialTab(1)
            viewModel.clearScannedTemplate()
            viewModel.clearProductAddedSuccess()
            viewModel.navigateBack()
          }
        }

        AddProductScreen(
          onBackClick = { viewModel.navigateBack() },
          onListProduct = { name, cat, unit, price, mrp, desc, stock, stockQty, imageUris, barcode, scannedImage ->
             lastAttempt = AddProductAttempt(name, cat, unit, price, mrp, desc, stock, stockQty, imageUris, barcode, scannedImage)
             viewModel.addNewProduct(name, cat, unit, price, mrp, desc, stock, stockQty, imageUris, barcode, scannedImage)
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
          },
          catalogSearchResults = catalogResults,
          catalogSearchLoading = catalogLoading,
          onSearchCatalog = { viewModel.searchCatalog(it) },
          onSelectCatalogProduct = { viewModel.applyCatalogChoice(it) },
          categories = categories,
          duplicateAlert = duplicateAlert,
          onDuplicateDismiss = { viewModel.clearDuplicateAlert() },
          onDuplicateUpdateStock = { existing ->
            viewModel.clearDuplicateAlert()
            viewModel.setVendorInitialTab(1)
            viewModel.setInventoryEditProduct(existing.id)
            viewModel.clearScannedTemplate()
            viewModel.navigateBack()
          },
          onDuplicateViewProduct = { _ ->
            viewModel.clearDuplicateAlert()
            viewModel.setVendorInitialTab(1)
            viewModel.clearScannedTemplate()
            viewModel.navigateBack()
          },
          onDuplicateForceInsert = {
            viewModel.clearDuplicateAlert()
            lastAttempt?.let { a ->
              viewModel.addNewProduct(
                a.name, a.cat, a.unit, a.price, a.mrp, a.desc, a.stock,
                a.stockQty, a.imageUris, a.barcode, a.scannedImage,
                forceInsertDespiteSoftMatch = true
              )
            }
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
        // Only forward to VendorDashboard when the server profile actually says
        // this is a vendor AND their shop row exists. A stale shopId alone is
        // not enough — that was the "white loading screen after customer login"
        // symptom (customer's row had shop_id from an aborted vendor flow, so
        // isVendor flipped true and VendorDashboard spun forever on shop lookup).
        val isServerVendor = userProfile.serverRole == UserRole.VENDOR
        val realVendorShop = if (isServerVendor) userProfile.shopId?.let { id -> shops.find { it.id == id } } else null
        // Back-fill line items for any order whose `items` list is empty. Runs
        // once per (userId, order-count) tuple so a customer opening the app
        // sees the full breakdown on their history without waiting for
        // OrderDetails to hydrate one by one.
        LaunchedEffect(userProfile.email, orders.count { it.items.isEmpty() }) {
          if (userProfile.email.isNotBlank() && orders.any { it.items.isEmpty() }) {
            viewModel.hydrateAllEmptyOrderItems()
          }
        }
        if (isServerVendor && realVendorShop != null) {
          LaunchedEffect(Unit) {
            viewModel.navigateTo(AppScreen.VendorDashboard)
          }
          Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
            CircularProgressIndicator(
              color = BharatPurplePrimary,
              modifier = Modifier.align(Alignment.Center)
            )
          }
        } else {
        Scaffold(
          bottomBar = {
            Column {
              BharatBottomNavigationBar(
                currentTab = currentTab,
                onTabSelected = { tab -> viewModel.setTab(tab) },
                cartItemCount = totalCartCount
              )
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
                HomeScreen(
                  userProfile = userProfile,
                  categories = categories,
                  products = products,
                  cartItems = cartItems,
                  searchQuery = searchQuery,
                  onSearchQueryChange = { q ->
                    viewModel.onSearchQueryChange(q)
                    viewModel.setTab(MainTab.SEARCH)
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
                  onStoreClick = { viewModel.navigateTo(AppScreen.SelectLocation) },
                  onChangeStoreClick = { viewModel.navigateTo(AppScreen.SelectLocation) },
                  onAdminClick = {
                    if (userProfile.isSuperAdmin) viewModel.navigateTo(AppScreen.AdminDashboard)
                    else if (userProfile.isVendor) viewModel.navigateTo(AppScreen.VendorDashboard)
                  },
                  onNotificationsClick = { viewModel.navigateTo(AppScreen.Notifications) },
                  unreadNotificationCount = unreadNotificationCount,
                  promoBanner = promoBanner,
                  isLoading = isLoading,
                  activeShopId = activeShopId,
                  shops = shops,
                  userLocation = userLocation,
                  deliveryAddressLine = selectedAddress?.formatted.orEmpty(),
                  onShopClick = { shop -> viewModel.navigateTo(AppScreen.ShopDetail(shop.id)) },
                  onViewAllShopsClick = { viewModel.navigateTo(AppScreen.NearbyShops) }
                )
              }

              MainTab.CATEGORIES -> {
                CategoriesScreen(
                  categories = categories,
                  products = products,
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

              MainTab.SEARCH -> {
                SearchScreen(
                  searchQuery = searchQuery,
                  products = products,
                  shops = shops,
                  cartItems = cartItems,
                  onSearchQueryChange = { q -> viewModel.onSearchQueryChange(q) },
                  onProductClick = { prod ->
                    viewModel.navigateTo(AppScreen.ShopsForProduct(prod.name))
                  },
                  onShopClick = { shop -> viewModel.navigateTo(AppScreen.ShopDetail(shop.id)) },
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

              MainTab.PROFILE -> {
                if (userProfile.email.isBlank()) {
                  AuthScreen(
                    initialEmail = "",
                    isLoading = isAuthLoading,
                    statusMessage = authStatusMessage,
                    onLogin = { email, pass, callback ->
                      viewModel.loginWithPassword(email, pass, callback)
                    },
                    onSignup = { name, email, mobile, _, pass, role, callback ->
                      viewModel.signUp(name, email, mobile, "", pass, role, callback)
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
                        // Same stale-shopId guard as the top-level Auth block.
                        val hasRealShop = user.shopId?.let { id -> shops.any { it.id == id } } == true
                        val isConfirmedVendor = hasRealShop || user.serverRole == UserRole.VENDOR
                        when {
                          isAdmin -> viewModel.navigateTo(AppScreen.AdminDashboard)
                          !user.profileCompleted -> {
                            viewModel.setPendingSignupRole(role)
                            viewModel.navigateTo(AppScreen.CompleteProfile)
                          }
                          isConfirmedVendor -> viewModel.navigateTo(AppScreen.VendorDashboard)
                          role == UserRole.VENDOR -> {
                            viewModel.setPendingSignupRole(role)
                            viewModel.navigateTo(AppScreen.VendorRegistration)
                          }
                          else -> {
                             viewModel.selectShop(null)
                             viewModel.setTab(MainTab.PROFILE)
                             viewModel.navigateTo(AppScreen.Main)
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
                    onCartClick = { viewModel.navigateTo(AppScreen.Cart) },
                    onMyOrdersClick = { viewModel.navigateTo(AppScreen.OrderHistory) },
                    onEditProfileClick = { viewModel.navigateTo(AppScreen.EditProfile) },
                    onSavedAddressesClick = { viewModel.navigateTo(AppScreen.SavedAddresses) },
                    onNotificationPreferencesClick = { viewModel.navigateTo(AppScreen.NotificationPreferences) },
                    onKiranaWalletClick = { viewModel.navigateTo(AppScreen.KiranaWallet) },
                    onHelpSupportClick = { viewModel.navigateTo(AppScreen.HelpSupport) },
                    onVendorRegisterClick = { viewModel.navigateTo(AppScreen.VendorRegistration) },
                    onAboutUsClick = { viewModel.navigateTo(AppScreen.AboutUs) },
                    onAccountActionsClick = { viewModel.navigateTo(AppScreen.AccountActions) },
                    savedAddressCount = addresses.size,
                    profileFetchComplete = profileFetchComplete
                  )
                }
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

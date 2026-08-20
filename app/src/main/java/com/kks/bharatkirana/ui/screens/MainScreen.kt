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
      .background(BharatBackground)
  ) {
    if (isMaintenanceMode) {
      MaintenanceOverlay()
    } else {
    when (val screen = currentScreen) {
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
          onSignup = { name, email, _, _, pass, callback ->
            viewModel.signUp(name, email, "", "", pass, callback)
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
          onAuthSuccess = { email: String, role: UserRole, path: AuthPath ->
            viewModel.login(email, authPath = path)
            val user = viewModel.userProfile.value
            val isAdmin = user.isAdmin

            when {
              isAdmin -> viewModel.navigateTo(AppScreen.AdminDashboard)
              !user.profileCompleted -> {
                // Fresh signup path: remember the role they picked at signup so
                // CompleteProfile can route them to VendorRegistration vs CustomerOnboarding.
                viewModel.setPendingSignupRole(role)
                viewModel.navigateTo(AppScreen.CompleteProfile)
              }
              user.isVendor -> viewModel.navigateTo(AppScreen.VendorDashboard)
              else -> {
                viewModel.navigateTo(AppScreen.CustomerOnboarding)
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
        val product = products.find { it.id == screen.productId } ?: selectedProduct ?: products.first()
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
          OrderPlacedScreen(
            order = order,
            onViewOrdersClick = {
              viewModel.setTab(MainTab.ORDERS)
              viewModel.navigateTo(AppScreen.Main)
            },
            onHomeClick = {
              viewModel.setTab(MainTab.HOME)
              viewModel.navigateTo(AppScreen.Main)
            }
          )
        }
      }

      is AppScreen.OrderDetails -> {
        val order = orders.find { it.id == screen.orderId } ?: orders.firstOrNull()
        if (order != null) {
          OrderDetailsScreen(
            order = order,
            onBackClick = { viewModel.navigateBack() },
            onReorder = { ord -> viewModel.reorder(ord) },
            onRateShop = { shopId, orderId, rating, review ->
              viewModel.rateShop(shopId, orderId, rating, review)
            },
            onCancelOrder = { orderId -> viewModel.cancelOrder(orderId) }
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
          onBackClick = { viewModel.navigateBack() },
          onViewCatalog = {
            viewModel.setTab(MainTab.HOME)
          },
          onOpenDirections = { addr -> viewModel.openDirections(addr) }
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
          onBackClick = { viewModel.navigateBack() }
        )
      }

      is AppScreen.VendorRegistration -> {
        VendorRegistrationScreen(
          onRegisterClick = { name, owner, addr, phone ->
            viewModel.registerVendorShop(name, owner, addr, phone)
          },
          onBackClick = { viewModel.navigateBack() }
        )
      }

      is AppScreen.VendorDashboard -> {
        val vendorShop = shops.find { it.id == userProfile.shopId } ?: shops.first()
        VendorDashboardScreen(
          shop = vendorShop,
          orders = orders.filter { it.shopId == vendorShop.id },
          products = products.filter { it.shopId == vendorShop.id },
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
          onUpdateProductStock = { prodId, inStock -> viewModel.updateProductStock(prodId, inStock) }
        )
      }

      is AppScreen.AddProduct -> {
        val scannedTemplate by viewModel.scannedProductTemplate.collectAsState()
        val scannedBarcode by viewModel.scannedBarcode.collectAsState()
        val barcodeStatus by viewModel.barcodeStatusMessage.collectAsState()

        AddProductScreen(
          onBackClick = { viewModel.navigateBack() },
          onListProduct = { name, cat, unit, price, mrp, desc, stock, imageUris, barcode ->
             viewModel.addNewProduct(name, cat, unit, price, mrp, desc, stock, imageUris, barcode)
             viewModel.navigateBack()
          },
          onScanBarcode = { viewModel.navigateTo(AppScreen.BarcodeScanner) },
          scannedTemplate = scannedTemplate,
          scannedBarcode = scannedBarcode,
          barcodeStatusMessage = barcodeStatus,
          onScanConsumed = { viewModel.clearScannedTemplate() }
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
                    onBackClick = { /* Home Root */ }
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
                    onBackClick = { viewModel.setTab(MainTab.HOME) }
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
                    onBackClick = { viewModel.setTab(MainTab.HOME) }
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
                    onSignup = { name, email, _, _, pass, callback ->
                      viewModel.signUp(name, email, "", "", pass, callback)
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
                      viewModel.login(email, authPath = path)
                      val user = viewModel.userProfile.value
                      val isAdmin = user.isAdmin

                      when {
                        isAdmin -> viewModel.navigateTo(AppScreen.AdminDashboard)
                        !user.profileCompleted -> {
                          viewModel.setPendingSignupRole(role)
                          viewModel.navigateTo(AppScreen.CompleteProfile)
                        }
                        user.isVendor -> viewModel.navigateTo(AppScreen.VendorDashboard)
                        else -> {
                           viewModel.navigateTo(AppScreen.CustomerOnboarding)
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
                    onSupportClick = { viewModel.openSupportWhatsApp() }
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
        text = "Bharat Kirana is under scheduled maintenance. Please try again in a few minutes.",
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
          "This version of Bharat Kirana is no longer supported. Please update to continue."
        else
          "A new version of Bharat Kirana is ready with improvements and fixes.",
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

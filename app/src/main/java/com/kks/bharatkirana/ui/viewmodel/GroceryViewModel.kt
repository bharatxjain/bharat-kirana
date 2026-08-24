package com.kks.bharatkirana.ui.viewmodel

import android.app.Application
import android.content.Context
import android.location.Location
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.firebase.messaging.FirebaseMessaging
import com.kks.bharatkirana.data.BharatRemoteConfig
import com.kks.bharatkirana.data.model.*
import com.kks.bharatkirana.data.repository.GroceryRepository
import com.kks.bharatkirana.data.supabase.SupabaseAuthService
import com.kks.bharatkirana.data.supabase.SupabaseGroceryRepo
import com.kks.bharatkirana.data.supabase.SupabaseRealtimeClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

class GroceryViewModel(
  application: Application
) : AndroidViewModel(application) {

  private val repository: GroceryRepository = GroceryRepository()
  val supabaseAuthService: SupabaseAuthService = SupabaseAuthService()
  val supabaseGroceryRepo: SupabaseGroceryRepo = SupabaseGroceryRepo()
  private val supabaseRealtime: SupabaseRealtimeClient = SupabaseRealtimeClient()

  private val prefs = application.getSharedPreferences("bharat_kirana_prefs", Context.MODE_PRIVATE)
  private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(application)

  private val screenBackStack = mutableListOf<AppScreen>(AppScreen.Onboarding)

  private val _currentScreen = MutableStateFlow<AppScreen>(AppScreen.Onboarding)
  val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

  private val _currentTab = MutableStateFlow(MainTab.HOME)
  val currentTab: StateFlow<MainTab> = _currentTab.asStateFlow()

  private val _userProfile = MutableStateFlow(UserProfile())
  val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

  private val _shops = MutableStateFlow(repository.getShops())
  val shops: StateFlow<List<Shop>> = _shops.asStateFlow()

  private val _activeShopId = MutableStateFlow<String?>(null)
  val activeShopId: StateFlow<String?> = _activeShopId.asStateFlow()

  private val _products = MutableStateFlow(repository.getProducts())
  val products: StateFlow<List<Product>> = _products.asStateFlow()

  private val _categories = MutableStateFlow(repository.getCategories())
  val categories: StateFlow<List<Category>> = _categories.asStateFlow()

  private val _cartItems = MutableStateFlow(repository.getInitialCart())
  val cartItems: StateFlow<List<CartItem>> = _cartItems.asStateFlow()

  private val _orders = MutableStateFlow(repository.getSampleOrders())
  val orders: StateFlow<List<Order>> = _orders.asStateFlow()

  private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
  val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()
  val unreadNotificationCount: StateFlow<Int> = _notifications
    .map { list -> list.count { !it.isRead } }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

  private val _selectedProduct = MutableStateFlow<Product?>(repository.getProducts().firstOrNull())
  val selectedProduct: StateFlow<Product?> = _selectedProduct.asStateFlow()

  private val _selectedCategory = MutableStateFlow<Category?>(null)
  val selectedCategory: StateFlow<Category?> = _selectedCategory.asStateFlow()

  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

  // Round 4.5: reactive autosuggestions — top matching PRODUCT names + SHOP names
  // as the user types. Recomputes whenever query, products, or shops change.
  val searchSuggestions: StateFlow<List<SearchSuggestion>> = combine(
    _searchQuery, _products, _shops
  ) { query, products, shops ->
    val q = query.trim()
    if (q.isBlank()) return@combine emptyList<SearchSuggestion>()

    val productSuggestions = products
      .filter {
        it.name.contains(q, ignoreCase = true) ||
          it.brand.contains(q, ignoreCase = true)
      }
      .groupBy { it.name.lowercase() }
      .values
      .map { it.first() }
      .sortedByDescending { it.name.startsWith(q, ignoreCase = true) }
      .take(5)
      .map {
        SearchSuggestion.ProductSuggestion(
          name = it.name,
          brand = it.brand,
          categoryId = it.categoryId,
          imageUrl = it.imageUrl
        )
      }

    val shopSuggestions = shops
      .filter {
        it.name.contains(q, ignoreCase = true) ||
          it.primaryCategory.contains(q, ignoreCase = true)
      }
      .take(3)
      .map { SearchSuggestion.ShopSuggestion(it) }

    productSuggestions + shopSuggestions
  }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  private val _latestPlacedOrderId = MutableStateFlow<String?>(null)
  val latestPlacedOrderId: StateFlow<String?> = _latestPlacedOrderId.asStateFlow()

  private val _authStatusMessage = MutableStateFlow<String?>(null)
  val authStatusMessage: StateFlow<String?> = _authStatusMessage.asStateFlow()

  private val _isAuthLoading = MutableStateFlow(false)
  val isAuthLoading: StateFlow<Boolean> = _isAuthLoading.asStateFlow()

  private val _isLoading = MutableStateFlow(false)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  private val _isOrderPlacing = MutableStateFlow(false)
  val isOrderPlacing: StateFlow<Boolean> = _isOrderPlacing.asStateFlow()

  private val _userLocation = MutableStateFlow<Location?>(null)

  // ---- Round 3: Firebase Remote Config-driven state ----
  private val _handlingFee = MutableStateFlow(5)
  val handlingFee: StateFlow<Int> = _handlingFee.asStateFlow()

  private val _minOrderFreeHandling = MutableStateFlow(200)
  val minOrderFreeHandling: StateFlow<Int> = _minOrderFreeHandling.asStateFlow()

  private val _freeHandlingDiscount = MutableStateFlow(15)
  val freeHandlingDiscount: StateFlow<Int> = _freeHandlingDiscount.asStateFlow()

  private val _isMaintenanceMode = MutableStateFlow(false)
  val isMaintenanceMode: StateFlow<Boolean> = _isMaintenanceMode.asStateFlow()

  private val _updateStatus = MutableStateFlow(UpdateStatus.NONE)
  val updateStatus: StateFlow<UpdateStatus> = _updateStatus.asStateFlow()

  private val _promoBanner = MutableStateFlow<String?>(null)
  val promoBanner: StateFlow<String?> = _promoBanner.asStateFlow()

  private val _supportWhatsappNumber = MutableStateFlow("")
  val supportWhatsappNumber: StateFlow<String> = _supportWhatsappNumber.asStateFlow()

  private val _appliedPromo = MutableStateFlow<PromoCode?>(null)
  val appliedPromo: StateFlow<PromoCode?> = _appliedPromo.asStateFlow()

  private val _promoStatusMessage = MutableStateFlow<String?>(null)
  val promoStatusMessage: StateFlow<String?> = _promoStatusMessage.asStateFlow()

  // Round 5: subscription tier catalog + this vendor's active subscription.
  private val _subscriptionTiers = MutableStateFlow<List<SubscriptionTier>>(emptyList())
  val subscriptionTiers: StateFlow<List<SubscriptionTier>> = _subscriptionTiers.asStateFlow()

  private val _vendorSubscription = MutableStateFlow<VendorSubscription?>(null)
  val vendorSubscription: StateFlow<VendorSubscription?> = _vendorSubscription.asStateFlow()

  // Set to a non-null message when the vendor tries to add a product beyond their
  // tier's item cap. Screens observe this to show an "Upgrade Plan" dialog.
  private val _tierCapMessage = MutableStateFlow<String?>(null)
  val tierCapMessage: StateFlow<String?> = _tierCapMessage.asStateFlow()

  // Round 3.5: role picked during signup (before profile is completed).
  // Set from AuthScreen → read after CompleteProfile to decide next screen.
  private val _pendingSignupRole = MutableStateFlow<UserRole?>(null)
  val pendingSignupRole: StateFlow<UserRole?> = _pendingSignupRole.asStateFlow()

  // Signup captures name + mobile now (single page). We stash them here so that
  // after email OTP verification, the profile can be filled in one shot and the
  // separate CompleteProfile screen is skipped.
  private val _pendingSignupName = MutableStateFlow<String?>(null)
  private val _pendingSignupMobile = MutableStateFlow<String?>(null)

  fun setPendingSignupRole(role: UserRole) {
    _pendingSignupRole.value = role
  }

  fun clearPendingSignupRole() {
    _pendingSignupRole.value = null
  }

  // Round 4: barcode scan flow.
  // When set, AddProductScreen consumes it via LaunchedEffect to pre-fill fields, then clears.
  private val _scannedProductTemplate = MutableStateFlow<Product?>(null)
  val scannedProductTemplate: StateFlow<Product?> = _scannedProductTemplate.asStateFlow()

  private val _scannedBarcode = MutableStateFlow<String?>(null)
  val scannedBarcode: StateFlow<String?> = _scannedBarcode.asStateFlow()

  private val _barcodeStatusMessage = MutableStateFlow<String?>(null)
  val barcodeStatusMessage: StateFlow<String?> = _barcodeStatusMessage.asStateFlow()

  fun onBarcodeScanned(barcode: String) {
    val clean = barcode.trim()
    if (clean.isBlank()) return
    _scannedBarcode.value = clean
    _barcodeStatusMessage.value = "Looking up $clean…"
    viewModelScope.launch {
      supabaseGroceryRepo.fetchProductByBarcode(clean, supabaseAuthService.currentAccessToken)
        .onSuccess { match ->
          if (match != null) {
            _scannedProductTemplate.value = match
            _barcodeStatusMessage.value = "Found: ${match.name}"
          } else {
            _scannedProductTemplate.value = null
            _barcodeStatusMessage.value = "New product — please fill details"
          }
        }
        .onFailure {
          _scannedProductTemplate.value = null
          _barcodeStatusMessage.value = "Lookup failed. Please fill manually."
        }
      // Return to Add Product screen either way
      navigateBack()
    }
  }

  fun clearScannedTemplate() {
    _scannedProductTemplate.value = null
    _scannedBarcode.value = null
    _barcodeStatusMessage.value = null
  }

  init {
    loadSavedSession()
    loadSupabaseData()
    fetchUserLocation()
    fetchFcmToken()
    loadRemoteConfig()
    startRealtimeCollector()
  }

  private fun startRealtimeCollector() {
    viewModelScope.launch {
      supabaseRealtime.changes.collect { change ->
        if (change.table == "orders") applyOrderChange(change)
        if (change.table == "notifications" && change.type == "INSERT") applyNewNotification(change)
      }
    }
  }

  private fun applyOrderChange(change: SupabaseRealtimeClient.RealtimeChange) {
    val record = change.record ?: return
    val orderId = record.optString("id").takeIf { it.isNotBlank() } ?: return
    val statusStr = record.optString("status", "")
    val status = OrderStatus.entries.firstOrNull { it.label == statusStr }

    when (change.type) {
      "UPDATE" -> {
        if (status == null) return
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val now = timeFormat.format(Date())
        _orders.update { list ->
          list.map { order ->
            if (order.id != orderId) return@map order
            val updatedTimeline = order.timeline.map { item ->
              when {
                item.status.stepIndex < status.stepIndex -> item.copy(isCompleted = true, isCurrent = false)
                item.status == status -> item.copy(
                  isCompleted = true,
                  isCurrent = true,
                  time = if (item.time.contains("Pending") || item.time.contains("Expected")) "Today, $now" else item.time
                )
                else -> item.copy(isCompleted = false, isCurrent = false)
              }
            }
            order.copy(status = status, timeline = updatedTimeline)
          }
        }
      }
      "DELETE" -> {
        _orders.update { list -> list.filter { it.id != orderId } }
      }
      // "INSERT" — skipped intentionally: vendor's dashboard triggers a refresh
      // via loadSupabaseData when opened, and the customer already inserted locally.
    }
  }

  private fun applyNewNotification(change: SupabaseRealtimeClient.RealtimeChange) {
    val record = change.record ?: return
    val id = record.optString("id").takeIf { it.isNotBlank() } ?: return
    if (_notifications.value.any { it.id == id }) return
    val notification = AppNotification(
      id = id,
      title = record.optString("title"),
      message = record.optString("message"),
      isRead = record.optBoolean("is_read", false),
      orderId = record.optString("order_id").takeIf { it.isNotBlank() },
      createdAt = record.optString("created_at")
    )
    _notifications.update { listOf(notification) + it }
  }

  private fun loadRemoteConfig() {
    BharatRemoteConfig.refresh {
      _handlingFee.value = BharatRemoteConfig.handlingFeeRupees()
      _minOrderFreeHandling.value = BharatRemoteConfig.minOrderForFreeHandling()
      _freeHandlingDiscount.value = BharatRemoteConfig.freeHandlingDiscount()
      _isMaintenanceMode.value = BharatRemoteConfig.maintenanceMode()
      _promoBanner.value = if (BharatRemoteConfig.promoBannerEnabled()) BharatRemoteConfig.promoBannerText() else null
      _supportWhatsappNumber.value = BharatRemoteConfig.supportWhatsappNumber()

      val currentVersion = com.kks.bharatkirana.BuildConfig.VERSION_CODE
      val minSupported = BharatRemoteConfig.minSupportedVersionCode()
      val latest = BharatRemoteConfig.latestVersionCode()
      _updateStatus.value = when {
        currentVersion < minSupported -> UpdateStatus.FORCED
        currentVersion < latest -> UpdateStatus.OPTIONAL
        else -> UpdateStatus.NONE
      }
    }
  }

  private fun fetchFcmToken() {
    FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
      if (task.isSuccessful) {
        val token = task.result ?: return@addOnCompleteListener
        _userProfile.update { it.copy(fcmToken = token) }
        // If already logged in when init runs (rare — happens on cold app start with
        // a saved session), push it to Supabase via a targeted PATCH.
        syncFcmTokenToServer(token)
      }
    }
  }

  // Round 4b: called after login and whenever we want to guarantee the current
  // FCM token is on file server-side so the Edge Function can push to this device.
  fun syncFcmTokenToServer(explicitToken: String? = null) {
    val userId = supabaseAuthService.currentUserId ?: return
    val accessToken = supabaseAuthService.currentAccessToken
    if (explicitToken != null) {
      viewModelScope.launch {
        supabaseGroceryRepo.updateFcmToken(userId, explicitToken, accessToken)
      }
      return
    }
    FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
      if (!task.isSuccessful) return@addOnCompleteListener
      val token = task.result ?: return@addOnCompleteListener
      _userProfile.update { it.copy(fcmToken = token) }
      viewModelScope.launch {
        supabaseGroceryRepo.updateFcmToken(userId, token, accessToken)
      }
    }
  }

  // Round 4b: when the user taps a push notification, MainActivity routes here.
  // If we have an orderId, drop them straight on the OrderDetails screen; otherwise
  // fall back to Main so they at least land somewhere useful.
  fun handleNotificationTap(orderId: String?) {
    if (_userProfile.value.email.isBlank()) return
    val screen = if (!orderId.isNullOrBlank()) AppScreen.OrderDetails(orderId) else AppScreen.Main
    navigateTo(screen)
  }

  private fun loadSavedSession() {
    val email = prefs.getString("user_email", "") ?: ""
    if (email.isNotBlank()) {
      login(email) { user ->
        // Decided only after the server profile has loaded, so a returning user
        // whose profile is actually complete doesn't get bounced back here.
        if (!user.profileCompleted) {
          _currentScreen.value = AppScreen.CompleteProfile
        } else {
          _currentScreen.value = AppScreen.Main
          _activeShopId.value = null
        }
      }
    }
  }

  private fun saveSession(email: String) {
    prefs.edit().putString("user_email", email).apply()
  }

  private fun clearSavedSession() {
    prefs.edit().remove("user_email").apply()
  }

  fun fetchUserLocation() {
    try {
      fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
        if (location != null) {
          _userLocation.value = location
          updateShopDistances(location)
        }
      }
    } catch (e: SecurityException) {
      // Permission not granted
    }
  }

  private fun updateShopDistances(userLoc: Location) {
    val updatedShops = _shops.value.map { shop ->
      // Skip shops that never captured lat/lng during registration — otherwise we'd
      // compute a bogus ~7000 km distance from user → (0,0) and hide them from customers.
      if (shop.lat == 0.0 && shop.lng == 0.0) return@map shop
      val distanceKm = calculateDistance(userLoc.latitude, userLoc.longitude, shop.lat, shop.lng)
      shop.copy(distance = String.format("%.1f km", distanceKm))
    }.sortedBy { it.distance.substringBefore(" ").toDoubleOrNull() ?: Double.MAX_VALUE }
    
    _shops.value = updatedShops
  }

  private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371 // Radius of the earth in km
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
        sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
  }

  fun loadSupabaseData() {
    viewModelScope.launch {
      // Sync live products
      supabaseGroceryRepo.fetchProducts().onSuccess { liveProducts ->
        if (liveProducts.isNotEmpty()) {
          _products.value = liveProducts
        }
      }

      // Sync live shops (Round 2)
      supabaseGroceryRepo.fetchShops(supabaseAuthService.currentAccessToken).onSuccess { liveShops ->
        if (liveShops.isNotEmpty()) {
          _shops.value = liveShops
          // Re-run distance sort if we already have a location fix.
          _userLocation.value?.let { updateShopDistances(it) }
        }
      }

      // Sync orders
      val email = _userProfile.value.email
      val isAdmin = _userProfile.value.isAdmin
      supabaseGroceryRepo.fetchOrders(customerEmail = email, isAdmin = isAdmin).onSuccess { liveOrders ->
        if (liveOrders.isNotEmpty()) {
          _orders.value = liveOrders
        }
      }
    }
  }

  fun navigateTo(screen: AppScreen) {
    screenBackStack.add(_currentScreen.value)
    _currentScreen.value = screen
  }

  fun navigateBack(): Boolean {
    if (screenBackStack.isNotEmpty()) {
      val prev = screenBackStack.removeAt(screenBackStack.size - 1)
      _currentScreen.value = prev
      return true
    }
    if (_currentScreen.value != AppScreen.Main) {
      _currentScreen.value = AppScreen.Main
      return true
    }
    return false
  }

  fun setTab(tab: MainTab) {
    _currentTab.value = tab
    if (_currentScreen.value !is AppScreen.Main) {
      _currentScreen.value = AppScreen.Main
    }
  }

  fun selectProduct(product: Product) {
    _selectedProduct.value = product
    navigateTo(AppScreen.ProductDetail(product.id))
  }

  fun selectProductById(productId: String) {
    val prod = _products.value.find { it.id == productId }
    if (prod != null) {
      _selectedProduct.value = prod
      navigateTo(AppScreen.ProductDetail(prod.id))
    }
  }

  fun selectCategory(category: Category?) {
    _selectedCategory.value = category
    _currentTab.value = MainTab.CATEGORIES
    if (_currentScreen.value !is AppScreen.Main) {
      _currentScreen.value = AppScreen.Main
    }
  }

  fun onSearchQueryChange(query: String) {
    _searchQuery.value = query
  }

  /**
   * Handle a tap on a search-suggestion row.
   *  - Product: navigate to a screen showing all shops that carry it.
   *  - Shop:    select the shop and jump to its storefront.
   */
  fun onSuggestionSelected(suggestion: SearchSuggestion) {
    when (suggestion) {
      is SearchSuggestion.ProductSuggestion -> {
        _searchQuery.value = ""
        navigateTo(AppScreen.ShopsForProduct(suggestion.name))
      }
      is SearchSuggestion.ShopSuggestion -> {
        _searchQuery.value = ""
        selectShop(suggestion.shop.id)
        navigateTo(AppScreen.StoreInfo)
      }
    }
  }

  /**
   * Called from ShopsForProductScreen when user picks a shop for a specific product.
   * Selects the shop and navigates to that shop's version of the product detail.
   */
  fun selectShopAndProduct(shopId: String, productName: String) {
    selectShop(shopId)
    val product = _products.value.firstOrNull {
      it.shopId == shopId && it.name.equals(productName, ignoreCase = true)
    }
    if (product != null) {
      selectProduct(product)
    } else {
      navigateTo(AppScreen.StoreInfo)
    }
  }

  fun addToCart(product: Product, weightOption: WeightOption, quantity: Int = 1) {
    _cartItems.update { currentList ->
      val existingIndex = currentList.indexOfFirst {
        it.product.id == product.id && it.selectedWeight.label == weightOption.label
      }
      val mutable = currentList.toMutableList()
      if (existingIndex >= 0) {
        val currentItem = mutable[existingIndex]
        val newQty = currentItem.quantity + quantity
        if (newQty <= 0) {
          mutable.removeAt(existingIndex)
        } else {
          mutable[existingIndex] = currentItem.copy(quantity = newQty)
        }
      } else if (quantity > 0) {
        mutable.add(CartItem(product, weightOption, quantity))
      }
      mutable
    }
  }

  fun updateCartQuantity(productId: String, weightLabel: String, delta: Int) {
    _cartItems.update { currentList ->
      val mutable = currentList.toMutableList()
      val index = mutable.indexOfFirst {
        it.product.id == productId && it.selectedWeight.label == weightLabel
      }
      if (index >= 0) {
        val item = mutable[index]
        val newQty = item.quantity + delta
        if (newQty <= 0) {
          mutable.removeAt(index)
        } else {
          mutable[index] = item.copy(quantity = newQty)
        }
      }
      mutable
    }
  }

  fun getCartItemQuantity(productId: String): Int {
    return _cartItems.value
      .filter { it.product.id == productId }
      .sumOf { it.quantity }
  }

  fun clearCart() {
    _cartItems.value = emptyList()
  }

  fun placeOrder(): Order? {
    val items = _cartItems.value
    if (items.isEmpty()) return null

    val itemTotal = items.sumOf { it.totalPrice }
    val minForFree = _minOrderFreeHandling.value
    val handlingFee = _handlingFee.value
    val discount = if (itemTotal > minForFree) _freeHandlingDiscount.value else 0
    val promo = _appliedPromo.value
    val promoDiscount = promo?.computeDiscount(itemTotal) ?: 0
    val totalAmount = (itemTotal + handlingFee - discount - promoDiscount).coerceAtLeast(0)

    val orderNum = (1000..9999).random()
    val orderId = "KIR-8F$orderNum"

    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val currentTime = timeFormat.format(Date())

    // Use the shopId of the first cart item as the order's shopId (RLS requires this
    // so the vendor of that shop can see the order).
    val shopId = items.firstOrNull()?.product?.shopId ?: "default_shop"

    val newOrder = Order(
      id = orderId,
      shopId = shopId,
      items = items,
      totalAmount = totalAmount,
      orderDate = "Today, $currentTime",
      status = OrderStatus.READY_FOR_PICKUP,
      expectedPickupTime = "Today by 5:30 PM",
      storeName = _userProfile.value.activeStore,
      storeAddress = _userProfile.value.activeStoreAddress,
      timeline = listOf(
        OrderTimelineItem(OrderStatus.PLACED, "Today, $currentTime", isCompleted = true),
        OrderTimelineItem(OrderStatus.PREPARING, "Today, $currentTime", isCompleted = true),
        OrderTimelineItem(OrderStatus.READY_FOR_PICKUP, "Expected by 5:30 PM", isCompleted = true, isCurrent = true),
        OrderTimelineItem(OrderStatus.COMPLETED, "Pending counter pickup", isCompleted = false)
      ),
      qrCodePayload = "ORDER:$orderId:BHARAT_KIRANA"
    )

    _orders.update { listOf(newOrder) + it }
    _latestPlacedOrderId.value = orderId
    _cartItems.value = emptyList()
    val appliedCode = promo?.code
    _appliedPromo.value = null
    _promoStatusMessage.value = null
    navigateTo(AppScreen.OrderPlaced(orderId))

    // Simulation: Notify Vendor
    simulateVendorNotification(newOrder)

    // Asynchronously sync order to Supabase
    viewModelScope.launch {
      supabaseGroceryRepo.insertOrder(
        order = newOrder,
        customerEmail = _userProfile.value.email,
        customerName = _userProfile.value.fullName,
        customerMobile = _userProfile.value.mobileNumber,
        userId = supabaseAuthService.currentUserId,
        promoCode = appliedCode,
        promoDiscount = promoDiscount,
        accessToken = supabaseAuthService.currentAccessToken
      )
    }

    return newOrder
  }

  fun applyPromoCode(code: String) {
    val cleanCode = code.trim().uppercase()
    if (cleanCode.isBlank()) {
      _promoStatusMessage.value = "Enter a code"
      return
    }
    _promoStatusMessage.value = "Checking…"
    viewModelScope.launch {
      supabaseGroceryRepo.fetchPromoCode(cleanCode, supabaseAuthService.currentAccessToken)
        .onSuccess { promo ->
          val itemTotal = _cartItems.value.sumOf { it.totalPrice }
          if (itemTotal < promo.minOrderAmount) {
            _appliedPromo.value = null
            _promoStatusMessage.value = "Add ₹${promo.minOrderAmount - itemTotal} more to use $cleanCode"
          } else {
            _appliedPromo.value = promo
            val d = promo.computeDiscount(itemTotal)
            _promoStatusMessage.value = "$cleanCode applied — ₹$d off"
          }
        }
        .onFailure {
          _appliedPromo.value = null
          _promoStatusMessage.value = "Invalid or expired code"
        }
    }
  }

  fun clearPromoCode() {
    _appliedPromo.value = null
    _promoStatusMessage.value = null
  }

  private fun simulateVendorNotification(order: Order) {
    // Show a real system notification for the vendor
    showSystemNotification(
      title = "New Order Received! 🛍️",
      message = "Order #${order.id} for ₹${order.totalAmount} has been placed at ${order.storeName}.",
      channelId = "vendor_notifications",
      channelName = "Vendor Alerts"
    )
  }

  private fun simulateCustomerNotification(order: Order, status: OrderStatus) {
    val message = when(status) {
      OrderStatus.PREPARING -> "Your order #${order.id} is now being prepared! 👨‍🍳"
      OrderStatus.READY_FOR_PICKUP -> "Your order #${order.id} is ready for pickup! 🏁"
      else -> return
    }
    
    showSystemNotification(
      title = "Order Update",
      message = message,
      channelId = "customer_notifications",
      channelName = "Order Status"
    )
  }

  private fun showSystemNotification(title: String, message: String, channelId: String, channelName: String) {
    val context = getApplication<Application>()
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
      val channel = android.app.NotificationChannel(channelId, channelName, android.app.NotificationManager.IMPORTANCE_HIGH)
      notificationManager.createNotificationChannel(channel)
    }

    val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
      .setSmallIcon(com.kks.bharatkirana.R.drawable.ic_launcher_foreground)
      .setContentTitle(title)
      .setContentText(message)
      .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
      .setAutoCancel(true)

    notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
  }

  fun signUp(
    name: String,
    email: String,
    mobile: String,
    address: String,
    password: String,
    onResult: (Boolean, String) -> Unit
  ) {
    val cleanEmail = email.trim().lowercase()
    _pendingSignupName.value = name.trim()
    _pendingSignupMobile.value = mobile.trim()
    _isAuthLoading.value = true
    _authStatusMessage.value = "Creating account..."

    viewModelScope.launch {
      val metadata = org.json.JSONObject().apply {
        put("full_name", name.trim())
        put("mobile", mobile.trim())
        put("address", address.trim())
      }

      supabaseAuthService.signUp(cleanEmail, password, metadata)
        .onSuccess { session ->
          _isAuthLoading.value = false
          if (session.accessToken.isBlank()) {
            _authStatusMessage.value = "Account created! Please verify your email."
            onResult(true, "Please check your email for verification link/OTP.")
          } else {
            _authStatusMessage.value = "Account created successfully!"
            login(cleanEmail, name, mobile, AuthPath.EMAIL)
            updateProfile(name, cleanEmail, mobile, address)
            onResult(true, "Signup successful")
          }
        }
        .onFailure { err ->
          _isAuthLoading.value = false
          val msg = err.localizedMessage ?: "Signup failed"
          _authStatusMessage.value = msg
          onResult(false, msg)
        }
    }
  }

  fun loginWithPassword(email: String, password: String, onResult: (Boolean, String) -> Unit) {
    val cleanEmail = email.trim().lowercase()
    _isAuthLoading.value = true
    _authStatusMessage.value = "Logging in..."

    viewModelScope.launch {
      supabaseAuthService.login(cleanEmail, password)
        .onSuccess { session ->
          _isAuthLoading.value = false
          _authStatusMessage.value = "Welcome back!"
          login(cleanEmail, authPath = AuthPath.EMAIL)
          loadSupabaseData()
          onResult(true, "Login successful")
        }
        .onFailure { err ->
          _isAuthLoading.value = false
          val msg = err.localizedMessage ?: "Invalid email or password"
          _authStatusMessage.value = msg
          onResult(false, msg)
        }
    }
  }

  fun sendEmailOtp(email: String, onResult: (Boolean, String) -> Unit) {
    val cleanEmail = email.trim().lowercase()
    if (cleanEmail.isBlank()) {
      onResult(false, "Please enter a valid email address")
      return
    }

    _isAuthLoading.value = true
    _authStatusMessage.value = "Sending OTP to $cleanEmail..."

    viewModelScope.launch {
      supabaseAuthService.sendEmailOtp(cleanEmail)
        .onSuccess { msg ->
          _isAuthLoading.value = false
          _authStatusMessage.value = "OTP sent to $cleanEmail! Check your inbox or spam."
          onResult(true, "OTP code sent to $cleanEmail")
        }
        .onFailure { err ->
          _isAuthLoading.value = false
          val msg = err.localizedMessage ?: "Failed to send OTP"
          _authStatusMessage.value = msg
          onResult(false, msg)
        }
    }
  }

  fun sendResetPasswordEmail(email: String, onResult: (Boolean, String) -> Unit) {
    val cleanEmail = email.trim().lowercase()
    if (cleanEmail.isBlank()) {
      onResult(false, "Please enter your email address")
      return
    }

    _isAuthLoading.value = true
    _authStatusMessage.value = "Sending reset link to $cleanEmail..."

    viewModelScope.launch {
      supabaseAuthService.sendResetPasswordEmail(cleanEmail)
        .onSuccess { msg ->
          _isAuthLoading.value = false
          _authStatusMessage.value = "Reset link sent! Please check your email."
          onResult(true, "Password reset email sent to $cleanEmail")
        }
        .onFailure { err ->
          _isAuthLoading.value = false
          val msg = err.localizedMessage ?: "Failed to send reset link"
          _authStatusMessage.value = msg
          onResult(false, msg)
        }
    }
  }

  fun navigateToResetPassword(accessToken: String) {
    _currentScreen.value = AppScreen.ResetPassword(accessToken)
  }

  fun resetPassword(accessToken: String, newPass: String, onResult: (Boolean, String) -> Unit) {
    _isAuthLoading.value = true
    viewModelScope.launch {
      supabaseAuthService.updateUserPassword(accessToken, newPass)
        .onSuccess {
          _isAuthLoading.value = false
          _authStatusMessage.value = "Password updated successfully!"
          onResult(true, "Success")
          _currentScreen.value = AppScreen.Auth
        }
        .onFailure { err ->
          _isAuthLoading.value = false
          val msg = err.localizedMessage ?: "Update failed"
          _authStatusMessage.value = msg
          onResult(false, msg)
        }
    }
  }

  fun verifyEmailOtp(email: String, token: String, onResult: (Boolean, String) -> Unit) {
    val cleanEmail = email.trim().lowercase()
    val cleanToken = token.trim()

    if (cleanToken.length < 6) {
      onResult(false, "Please enter the 6-digit OTP code")
      return
    }

    _isAuthLoading.value = true
    _authStatusMessage.value = "Verifying code..."

    viewModelScope.launch {
      // Try signup type first, then fallback to email (magiclink) type
      supabaseAuthService.verifyEmailOtp(cleanEmail, cleanToken, type = "signup")
        .onFailure { 
          // If signup verify fails, try general email/magiclink verify
          supabaseAuthService.verifyEmailOtp(cleanEmail, cleanToken, type = "email")
        }
        .onSuccess { session ->
          _isAuthLoading.value = false
          _authStatusMessage.value = "Successfully authenticated!"
          val pendingName = _pendingSignupName.value.orEmpty()
          val pendingMobile = _pendingSignupMobile.value.orEmpty()
          login(cleanEmail, pendingName, pendingMobile, authPath = AuthPath.EMAIL)
          if (pendingName.isNotBlank()) {
            // Persist name + mobile now so the CompleteProfile screen can be skipped.
            updateProfile(pendingName, cleanEmail, pendingMobile, "")
          }
          _pendingSignupName.value = null
          _pendingSignupMobile.value = null
          loadSupabaseData()
          onResult(true, "Authentication successful")
        }
        .onFailure { err ->
          _isAuthLoading.value = false
          val msg = err.localizedMessage ?: "Invalid or expired OTP code"
          _authStatusMessage.value = msg
          onResult(false, msg)
        }
    }
  }

  fun clearAuthStatus() {
    _authStatusMessage.value = null
  }

  fun logout() {
    viewModelScope.launch {
      supabaseAuthService.signOut()
    }
    supabaseRealtime.disconnect()
    clearSavedSession()
    _userProfile.value = UserProfile(
      fullName = "",
      email = ""
    )
    _authStatusMessage.value = null
    _cartItems.value = emptyList()
    screenBackStack.clear()
    _currentScreen.value = AppScreen.Auth
  }

  fun deleteAccount() {
    viewModelScope.launch {
      val userId = supabaseAuthService.currentUserId
      if (userId != null) {
        // Best-effort: remove the profile row (name, address, phone) so it no longer
        // exists server-side. Order history is retained for store accounting — see
        // Privacy Policy. We still sign the user out below even if this call fails,
        // since the account must not remain accessible either way.
        supabaseGroceryRepo.deleteUserProfile(userId, supabaseAuthService.currentAccessToken)
      }
      supabaseAuthService.signOut()
      clearSavedSession()
      _userProfile.value = UserProfile()
      _orders.value = emptyList()
      _cartItems.value = emptyList()
      _authStatusMessage.value = "Account and data deleted successfully."
      screenBackStack.clear()
      _currentScreen.value = AppScreen.Auth
    }
  }

  fun login(
    email: String,
    name: String = "",
    mobile: String = "",
    authPath: AuthPath? = null,
    onProfileReady: (UserProfile) -> Unit = {}
  ) {
    val cleanEmail = email.trim()
    // Delegate the admin check to UserProfile (which reads BuildConfig from .env).
    // No personal emails are compiled into source.
    val probe = UserProfile(email = cleanEmail)
    val isSuperAdmin = probe.isSuperAdmin
    val isAdmin = probe.isAdmin

    _userProfile.update {
      it.copy(
        email = cleanEmail,
        fullName = if (name.isNotBlank()) name.trim() else if (isSuperAdmin) "Super Admin" else if (isAdmin) "Admin" else it.fullName,
        mobileNumber = if (mobile.isNotBlank()) mobile.trim() else it.mobileNumber,
        profileCompleted = name.isNotBlank() || isSuperAdmin || isAdmin,
        authPath = authPath ?: it.authPath,
        phoneVerified = it.phoneVerified
      )
    }

    // Persist session
    saveSession(cleanEmail)

    // Kick off the Realtime subscription with the user's JWT so RLS lets them
    // receive their own orders' postgres_changes stream.
    supabaseRealtime.connect(supabaseAuthService.currentAccessToken)

    // Fetch server-side profile (role, real name/mobile/shop_id). Server role is the
    // sole source of truth for authorization from here on — the .env whitelist is only
    // a fallback used until this returns.
    viewModelScope.launch {
      val userId = supabaseAuthService.currentUserId
      if (!userId.isNullOrBlank()) {
        supabaseGroceryRepo.fetchProfile(userId, supabaseAuthService.currentAccessToken)
          .onSuccess { serverProfile ->
            _userProfile.update { local ->
              local.copy(
                fullName = serverProfile.fullName.ifBlank { local.fullName },
                mobileNumber = serverProfile.mobileNumber.ifBlank { local.mobileNumber },
                address = serverProfile.address.ifBlank { local.address },
                loyaltyPoints = serverProfile.loyaltyPoints,
                walletBalance = serverProfile.walletBalance,
                shopId = serverProfile.shopId ?: local.shopId,
                profileCompleted = serverProfile.profileCompleted || local.profileCompleted,
                phoneVerified = serverProfile.phoneVerified || local.phoneVerified,
                serverRole = serverProfile.serverRole
              )
            }
          }
      }

      // Only decide profile-completeness-dependent navigation once the server's
      // profile_completed value has actually loaded — reading _userProfile.value
      // right after login() returns (as callers used to) races this coroutine and
      // always saw the stale local default, forcing Complete Profile every time.
      onProfileReady(_userProfile.value)

      // Round 4b: push the current FCM token to profiles.fcm_token so the Edge
      // Function can target this device with order-status pushes.
      syncFcmTokenToServer()

      // Round 5: preload tier catalog + this vendor's active subscription so the
      // Overview screen can show tier badge and enforce item cap without a spinner.
      loadSubscriptionTiers()
      loadVendorSubscription()

      // Load orders using the (possibly updated) admin flag
      val effectiveIsAdmin = _userProfile.value.isAdmin
      supabaseGroceryRepo.fetchOrders(customerEmail = cleanEmail, isAdmin = effectiveIsAdmin).onSuccess { liveOrders ->
        if (liveOrders.isNotEmpty()) {
          _orders.value = liveOrders
        }
      }

      loadNotifications()
    }
  }

  fun loadNotifications() {
    val userId = supabaseAuthService.currentUserId ?: return
    viewModelScope.launch {
      supabaseGroceryRepo.fetchNotifications(userId, supabaseAuthService.currentAccessToken)
        .onSuccess { list -> _notifications.value = list }
    }
  }

  fun markNotificationRead(notificationId: String) {
    val notification = _notifications.value.firstOrNull { it.id == notificationId } ?: return
    if (notification.isRead) return
    _notifications.update { list -> list.map { if (it.id == notificationId) it.copy(isRead = true) else it } }
    viewModelScope.launch {
      supabaseGroceryRepo.markNotificationRead(notificationId, supabaseAuthService.currentAccessToken)
    }
  }

  fun updateShopDetails(shopId: String, updatedShop: Shop) {
    _shops.update { list ->
      list.map { if (it.id == shopId) updatedShop else it }
    }
    viewModelScope.launch {
      supabaseGroceryRepo.updateShop(shopId, updatedShop, supabaseAuthService.currentAccessToken)
    }
  }

  fun verifyVendor(shopId: String, verified: Boolean) {
    val status = if (verified) VendorStatus.APPROVED else VendorStatus.PENDING
    _shops.update { list ->
      list.map { if (it.id == shopId) it.copy(isPartner = verified, status = status) else it }
    }
    val shop = _shops.value.find { it.id == shopId }
    if (shop != null) {
      viewModelScope.launch {
        supabaseGroceryRepo.updateShop(shopId, shop, supabaseAuthService.currentAccessToken)
      }
    }
  }

  fun registerVendorShop(
    name: String,
    owner: String,
    address: String,
    phone: String,
    category: String = "Grocery",
    lat: Double = 0.0,
    lng: Double = 0.0
  ) {
    val shopId = "s_${System.currentTimeMillis()}"
    val userId = supabaseAuthService.currentUserId ?: return
    val token = supabaseAuthService.currentAccessToken

    val newShop = Shop(
      id = shopId,
      name = name,
      ownerName = owner,
      address = address,
      phone = phone,
      lat = lat,
      lng = lng,
      primaryCategory = category,
      isPartner = false,
      status = VendorStatus.PENDING
    )

    _isLoading.value = true
    viewModelScope.launch {
      supabaseGroceryRepo.registerShop(newShop, userId, token)
        .onSuccess {
          _isLoading.value = false
          // Update local profile
          _userProfile.update { it.copy(shopId = shopId) }
          // Add to local shops list
          _shops.update { listOf(newShop) + it }
          
          // Simulation: Shoot verification email to Super Admin
          triggerAdminVerificationEmail(newShop)
          
          // Redirect to Vendor Dashboard
          _currentScreen.value = AppScreen.VendorDashboard
        }
        .onFailure {
          _isLoading.value = false
          _authStatusMessage.value = "Registration failed. Please try again."
        }
    }
  }

  private fun triggerAdminVerificationEmail(shop: Shop) {
    val adminEmail = com.kks.bharatkirana.BuildConfig.SUPER_ADMIN_EMAIL
      .ifBlank { com.kks.bharatkirana.BuildConfig.SUPPORT_EMAIL }
    if (adminEmail.isBlank()) return
    try {
      val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
        data = android.net.Uri.parse("mailto:")
        putExtra(android.content.Intent.EXTRA_EMAIL, arrayOf(adminEmail))
        putExtra(android.content.Intent.EXTRA_SUBJECT, "New Vendor Verification: ${shop.name}")
        putExtra(android.content.Intent.EXTRA_TEXT, """
          New vendor application received!
          Shop Name: ${shop.name}
          Owner: ${shop.ownerName}
          Phone: ${shop.phone}
          Address: ${shop.address}
          
          Please verify this vendor in the Admin Dashboard.
        """.trimIndent())
        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      getApplication<Application>().startActivity(intent)
    } catch (e: Exception) {
      // Email app not available
    }
  }

  fun updateProfile(fullName: String, email: String, mobileNumber: String, address: String) {
    val cleanEmail = email.trim()
    _userProfile.update {
      it.copy(
        fullName = fullName.trim(),
        email = cleanEmail,
        mobileNumber = mobileNumber.trim(),
        address = address.trim(),
        profileCompleted = true,
        phoneVerified = true
      )
    }

    viewModelScope.launch {
      supabaseGroceryRepo.syncProfile(_userProfile.value, supabaseAuthService.currentAccessToken)
    }
  }

  fun openDirections(address: String, lat: Double = 0.0, lng: Double = 0.0) {
    val app = getApplication<Application>()
    val hasCoords = lat != 0.0 || lng != 0.0
    val mapUri = if (hasCoords) {
      Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(address)})")
    } else {
      Uri.parse("geo:0,0?q=${Uri.encode(address)}")
    }
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, mapUri)
      .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
      app.startActivity(intent)
    } catch (e: Exception) {
      // No app registered for geo: URIs (e.g. Google Maps not installed) — fall back
      // to opening directions in the browser instead of failing silently.
      val webUri = if (hasCoords) {
        Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng")
      } else {
        Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${Uri.encode(address)}")
      }
      val webIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, webUri)
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
      try { app.startActivity(webIntent) } catch (_: Exception) { }
    }
  }

  fun openPlayStorePage() {
    val pkg = getApplication<Application>().packageName
    val app = getApplication<Application>()
    val marketIntent = android.content.Intent(
      android.content.Intent.ACTION_VIEW,
      Uri.parse("market://details?id=$pkg")
    ).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
    try {
      app.startActivity(marketIntent)
    } catch (e: Exception) {
      val webIntent = android.content.Intent(
        android.content.Intent.ACTION_VIEW,
        Uri.parse("https://play.google.com/store/apps/details?id=$pkg")
      ).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
      try { app.startActivity(webIntent) } catch (_: Exception) { }
    }
  }

  fun openSupportWhatsApp() {
    val raw = _supportWhatsappNumber.value.trim()
    if (raw.isBlank()) return
    val digits = raw.replace(Regex("[^0-9]"), "")
    if (digits.isBlank()) return
    val msg = Uri.encode("Hi, I need help with the BreakQ app.")
    val uri = Uri.parse("https://wa.me/$digits?text=$msg")
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
      addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
      getApplication<Application>().startActivity(intent)
    } catch (_: Exception) { }
  }

  // Round 5: subscription helpers -------------------------------------------

  fun loadSubscriptionTiers() {
    viewModelScope.launch {
      supabaseGroceryRepo.fetchSubscriptionTiers().onSuccess { tiers ->
        _subscriptionTiers.value = tiers
      }
    }
  }

  fun loadVendorSubscription() {
    val shopId = _userProfile.value.shopId ?: return
    viewModelScope.launch {
      supabaseGroceryRepo.fetchVendorSubscription(shopId, supabaseAuthService.currentAccessToken)
        .onSuccess { sub -> _vendorSubscription.value = sub }
    }
  }

  // Convenience — the tier this vendor is currently on (nullable if not a vendor
  // or tiers haven't loaded yet).
  fun currentTier(): SubscriptionTier? {
    val tierId = _vendorSubscription.value?.tierId ?: return null
    return _subscriptionTiers.value.firstOrNull { it.id == tierId }
  }

  // Returns true if the vendor can add one more product. Used by AddProductScreen
  // to gate submission and by VendorDashboard to show a badge.
  fun canAddMoreProducts(): Boolean {
    val cap = currentTier()?.itemCap ?: 10
    if (cap == -1) return true
    val shopId = _userProfile.value.shopId ?: return true
    val current = _products.value.count { it.shopId == shopId }
    return current < cap
  }

  // Opens WhatsApp with a pre-filled upgrade request. We handle the payment flow
  // manually (Razorpay comes in a later round), then admin flips the tier via SQL.
  fun requestPlanUpgrade(targetTierId: String) {
    val digits = _supportWhatsappNumber.value.replace(Regex("[^0-9]"), "")
    if (digits.isBlank()) return
    val shop = _shops.value.firstOrNull { it.id == _userProfile.value.shopId }
    val currentTierName = currentTier()?.displayName ?: "Free"
    val targetTierName = _subscriptionTiers.value.firstOrNull { it.id == targetTierId }?.displayName ?: targetTierId
    val msg = Uri.encode(
      "Hi, I want to upgrade my BreakQ plan.\n" +
        "Shop: ${shop?.name.orEmpty()}\n" +
        "Owner: ${_userProfile.value.fullName}\n" +
        "Current plan: $currentTierName\n" +
        "Upgrade to: $targetTierName\n" +
        "Please share payment details."
    )
    val uri = Uri.parse("https://wa.me/$digits?text=$msg")
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
      addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try { getApplication<Application>().startActivity(intent) } catch (_: Exception) { }
  }

  fun showTierCapMessage(msg: String) { _tierCapMessage.value = msg }
  fun clearTierCapMessage() { _tierCapMessage.value = null }

  // Backwards compatibility overload
  fun updateProfile(fullName: String, mobileNumber: String, address: String) {
    _userProfile.update {
      it.copy(
        fullName = fullName.trim(),
        mobileNumber = mobileNumber.trim(),
        address = address.trim()
      )
    }
  }

  private val _isStoreOpen = MutableStateFlow(true)
  val isStoreOpen: StateFlow<Boolean> = _isStoreOpen.asStateFlow()

  private val _autoConfirmOrders = MutableStateFlow(true)
  val autoConfirmOrders: StateFlow<Boolean> = _autoConfirmOrders.asStateFlow()

  private val _packingTimeMinutes = MutableStateFlow(12)
  val packingTimeMinutes: StateFlow<Int> = _packingTimeMinutes.asStateFlow()

  fun toggleStoreStatus() {
    _isStoreOpen.update { !it }
  }

  fun toggleAutoConfirm() {
    _autoConfirmOrders.update { !it }
  }

  fun updatePackingTime(minutes: Int) {
    _packingTimeMinutes.value = minutes.coerceIn(5, 60)
  }

  fun updateOrderStatus(orderId: String, newStatus: OrderStatus) {
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val currentTime = timeFormat.format(Date())

    _orders.update { list ->
      list.map { order ->
        if (order.id == orderId) {
          // Trigger customer notification for status changes
          if (newStatus == OrderStatus.PREPARING || newStatus == OrderStatus.READY_FOR_PICKUP) {
            simulateCustomerNotification(order, newStatus)
          }

          val updatedTimeline = order.timeline.map { item ->
            when {
              item.status.stepIndex < newStatus.stepIndex -> item.copy(isCompleted = true, isCurrent = false)
              item.status == newStatus -> item.copy(
                isCompleted = true,
                isCurrent = true,
                time = if (item.time.contains("Pending") || item.time.contains("Expected")) "Today, $currentTime" else item.time
              )
              else -> item.copy(isCompleted = false, isCurrent = false)
            }
          }
          order.copy(status = newStatus, timeline = updatedTimeline)
        } else {
          order
        }
      }
    }

    // Sync to Supabase
    viewModelScope.launch {
      supabaseGroceryRepo.updateOrderStatus(orderId, newStatus, supabaseAuthService.currentAccessToken)
    }
  }

  fun cancelOrder(orderId: String) {
    val order = _orders.value.find { it.id == orderId } ?: return
    if (order.status == OrderStatus.COMPLETED || order.status == OrderStatus.CANCELLED) return
    updateOrderStatus(orderId, OrderStatus.CANCELLED)
    showSystemNotification(
      title = "Order cancelled",
      message = "Order #${order.id} has been cancelled.",
      channelId = "customer_notifications",
      channelName = "Order Status"
    )
  }

  fun updateProductStock(productId: String, inStock: Boolean) {
    _products.update { list ->
      list.map {
        if (it.id == productId) it.copy(inStock = inStock) else it
      }
    }

    viewModelScope.launch {
      supabaseGroceryRepo.updateProductStock(productId, inStock, supabaseAuthService.currentAccessToken)
    }
  }

  fun updateProductPrice(productId: String, newPrice: Int) {
    _products.update { list ->
      list.map {
        if (it.id == productId) {
          val updatedWeights = it.weightOptions.mapIndexed { idx, opt ->
            if (idx == 0) opt.copy(price = newPrice) else opt
          }
          it.copy(currentPrice = newPrice, weightOptions = updatedWeights)
        } else {
          it
        }
      }
    }

    viewModelScope.launch {
      supabaseGroceryRepo.updateProductPrice(productId, newPrice, supabaseAuthService.currentAccessToken)
    }
  }

  fun selectShop(shopId: String?) {
    _activeShopId.value = shopId
    val shop = _shops.value.find { it.id == shopId }
    if (shop != null) {
      _userProfile.update { it.copy(activeStore = shop.name, activeStoreAddress = shop.address) }
    }
  }

  fun addProduct(product: Product) {
    if (!canAddMoreProducts()) {
      val cap = currentTier()?.itemCap ?: 10
      val tierName = currentTier()?.displayName ?: "Free"
      _tierCapMessage.value = "You've reached your $tierName plan limit of $cap products. Upgrade to add more."
      return
    }
    _products.update { listOf(product) + it }
    viewModelScope.launch {
      supabaseGroceryRepo.addProduct(product, supabaseAuthService.currentAccessToken)
    }
  }

  fun addNewProduct(
    name: String,
    cat: String,
    unit: String,
    price: Int,
    mrp: Int,
    desc: String,
    stock: Boolean,
    imageUris: List<Uri>,
    barcode: String = ""
  ) {
    if (!canAddMoreProducts()) {
      val cap = currentTier()?.itemCap ?: 10
      val tierName = currentTier()?.displayName ?: "Free"
      _tierCapMessage.value = "You've reached your $tierName plan limit of $cap products. Upgrade to add more."
      return
    }
    val productId = "p_${System.currentTimeMillis()}"
    val shopId = _userProfile.value.shopId ?: "s_bharat_kirana"
    
    _isLoading.value = true
    viewModelScope.launch {
      val finalImageUrls = mutableListOf<String>()
      
      // 1. Handle Multiple Images Upload
      imageUris.forEachIndexed { index, uri ->
        val bytes = getBytesFromUri(uri)
        if (bytes != null) {
          val nameWithIndex = "${productId}_$index.jpg"
          supabaseGroceryRepo.uploadProductImage(nameWithIndex, bytes, supabaseAuthService.currentAccessToken)
            .onSuccess { url -> finalImageUrls.add(url) }
        }
      }

      // 2. Create Product Object
      val newProd = Product(
        id = productId,
        name = name,
        brand = "Store Item",
        categoryId = cat.lowercase().replace(" ", "_"),
        shopId = shopId,
        currentPrice = price,
        originalPrice = if (mrp > 0) mrp else price,
        discountPercent = if (mrp > price) ((mrp - price) * 100 / mrp) else 0,
        unit = unit,
        description = desc,
        inStock = stock,
        imageUrl = finalImageUrls.firstOrNull() ?: "",
        imageUrls = finalImageUrls,
        weightOptions = listOf(WeightOption(unit, price, mrp)),
        barcode = barcode
      )

      // 3. Sync to Supabase & Local
      _products.update { listOf(newProd) + it }
      supabaseGroceryRepo.addProduct(newProd, supabaseAuthService.currentAccessToken)
      _isLoading.value = false
    }
  }

  fun updateFullProduct(
    productId: String,
    name: String,
    unit: String,
    price: Int,
    mrp: Int,
    desc: String,
    stock: Boolean,
    imageUris: List<Uri>
  ) {
    _isLoading.value = true
    viewModelScope.launch {
      val finalImageUrls = mutableListOf<String>()
      
      // 1. Handle Multiple Images Upload (detect if already uploaded)
      imageUris.forEachIndexed { index, uri ->
        if (uri.toString().startsWith("http")) {
          finalImageUrls.add(uri.toString())
        } else {
          val bytes = getBytesFromUri(uri)
          if (bytes != null) {
            val nameWithIndex = "${productId}_${System.currentTimeMillis()}_$index.jpg"
            supabaseGroceryRepo.uploadProductImage(nameWithIndex, bytes, supabaseAuthService.currentAccessToken)
              .onSuccess { url -> finalImageUrls.add(url) }
          }
        }
      }

      // 2. Update Product Object locally
      _products.update { list ->
        list.map { prod ->
          if (prod.id == productId) {
            prod.copy(
              name = name,
              currentPrice = price,
              originalPrice = if (mrp > 0) mrp else price,
              discountPercent = if (mrp > price) ((mrp - price) * 100 / mrp) else 0,
              unit = unit,
              description = desc,
              inStock = stock,
              imageUrl = finalImageUrls.firstOrNull() ?: prod.imageUrl,
              imageUrls = finalImageUrls,
              weightOptions = listOf(WeightOption(unit, price, mrp))
            )
          } else prod
        }
      }

      // 3. Sync to Supabase
      val updatedProd = _products.value.find { it.id == productId }
      if (updatedProd != null) {
        supabaseGroceryRepo.updateFullProduct(updatedProd, supabaseAuthService.currentAccessToken)
      }
      _isLoading.value = false
    }
  }

  private fun getBytesFromUri(uri: Uri): ByteArray? {
    return try {
      getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (e: Exception) {
      null
    }
  }

  fun deleteProduct(productId: String) {
    _products.update { list -> list.filter { it.id != productId } }
    viewModelScope.launch {
      supabaseGroceryRepo.deleteProduct(productId, supabaseAuthService.currentAccessToken)
    }
  }

  fun verifyPickupCode(codeOrId: String): Order? {
    val clean = codeOrId.trim().uppercase()
    return _orders.value.find { order ->
      order.id.uppercase() == clean ||
        order.qrCodePayload.uppercase().contains(clean) ||
        order.id.uppercase().replace("-", "") == clean.replace("-", "")
    }
  }

  fun reorder(order: Order) {
    _cartItems.value = order.items
    navigateTo(AppScreen.Cart)
  }

  fun rateShop(shopId: String, orderId: String, rating: Int, review: String) {
    val customerId = supabaseAuthService.currentUserId ?: return
    viewModelScope.launch {
      supabaseGroceryRepo.submitShopRating(
        shopId = shopId,
        orderId = orderId,
        customerId = customerId,
        rating = rating,
        review = review,
        accessToken = supabaseAuthService.currentAccessToken
      ).onSuccess {
        // Optimistically bump local shop aggregate; the server trigger keeps DB truth.
        _shops.update { list ->
          list.map { shop ->
            if (shop.id == shopId) {
              val newCount = shop.ratingCount + 1
              val newAvg = (shop.rating * shop.ratingCount + rating) / newCount
              shop.copy(rating = newAvg, ratingCount = newCount)
            } else shop
          }
        }
      }
    }
  }
}

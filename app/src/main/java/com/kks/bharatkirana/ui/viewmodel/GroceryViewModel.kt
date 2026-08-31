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

  // Round 9: a returning user with a live Supabase session should never see the
  // onboarding pager again. We read the token synchronously here (SharedPreferences
  // is memory-mapped, so this is microseconds) and open on a lightweight
  // "Restoring" screen that loadSavedSession() swaps out once the profile lands.
  private val hasPersistedSession: Boolean =
    !prefs.getString("refresh_token", null).isNullOrBlank()

  private val screenBackStack = mutableListOf<AppScreen>(AppScreen.Onboarding)

  private val _currentScreen = MutableStateFlow<AppScreen>(
    if (hasPersistedSession) AppScreen.Restoring else AppScreen.Onboarding
  )
  val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

  private val _currentTab = MutableStateFlow(MainTab.HOME)
  val currentTab: StateFlow<MainTab> = _currentTab.asStateFlow()

  private val _userProfile = MutableStateFlow(UserProfile())
  val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

  private val _shops = MutableStateFlow<List<Shop>>(emptyList())
  val shops: StateFlow<List<Shop>> = _shops.asStateFlow()

  private val _activeShopId = MutableStateFlow<String?>(null)
  val activeShopId: StateFlow<String?> = _activeShopId.asStateFlow()

  private val _products = MutableStateFlow<List<Product>>(emptyList())
  val products: StateFlow<List<Product>> = _products.asStateFlow()

  // Categories stay hardcoded — they are UI reference data (labels, colours,
  // icons), not user content. The DB `categories` table is a separate concern.
  private val _categories = MutableStateFlow(repository.getCategories())
  val categories: StateFlow<List<Category>> = _categories.asStateFlow()

  private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
  val cartItems: StateFlow<List<CartItem>> = _cartItems.asStateFlow()

  private val _orders = MutableStateFlow<List<Order>>(emptyList())
  val orders: StateFlow<List<Order>> = _orders.asStateFlow()

  private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
  val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()
  val unreadNotificationCount: StateFlow<Int> = _notifications
    .map { list -> list.count { !it.isRead } }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

  private val _selectedProduct = MutableStateFlow<Product?>(null)
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

  // True when a profile edit is saved on-device but not yet accepted by Supabase.
  private val _profileSyncPending = MutableStateFlow(prefs.getBoolean("profile_pending_sync", false))
  val profileSyncPending: StateFlow<Boolean> = _profileSyncPending.asStateFlow()

  private val _isOrderPlacing = MutableStateFlow(false)
  val isOrderPlacing: StateFlow<Boolean> = _isOrderPlacing.asStateFlow()

  private val _userLocation = MutableStateFlow<Location?>(null)
  val userLocation: StateFlow<Location?> = _userLocation.asStateFlow()

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

  // Round 6.1: flips to true after the very first fetchProfile call completes on
  // login (whether it succeeded, failed, or returned "row not found"). Used by
  // ProfileScreen to gate the "Register Your Shop" CTA so it doesn't flicker on
  // cold start before we know whether this user is already a vendor.
  private val _profileFetchComplete = MutableStateFlow(false)
  val profileFetchComplete: StateFlow<Boolean> = _profileFetchComplete.asStateFlow()

  // Set of order IDs the current user has already left a rating for — populated
  // on login from shop_ratings and appended on every successful rateShop() call.
  // OrderDetailsScreen reads this to decide whether the star form should render.
  private val _ratedOrderIds = MutableStateFlow<Set<String>>(emptySet())
  val ratedOrderIds: StateFlow<Set<String>> = _ratedOrderIds.asStateFlow()

  // Round 7: transient message after a vendor uploads a new product — reports
  // per-image upload success/failure so the vendor isn't left staring at a blank
  // screen wondering if their photos were saved.
  private val _productUploadMessage = MutableStateFlow<String?>(null)
  val productUploadMessage: StateFlow<String?> = _productUploadMessage.asStateFlow()
  fun clearProductUploadMessage() { _productUploadMessage.value = null }

  // Round 7.2: granular progress for the vendor-registration document uploads so
  // the wizard can show exactly which file is being pushed to Storage.
  enum class VendorUploadState { IDLE, UPLOADING_PHOTO, UPLOADING_PROOF, SAVING_SHOP }
  private val _vendorUploadState = MutableStateFlow(VendorUploadState.IDLE)
  val vendorUploadState: StateFlow<VendorUploadState> = _vendorUploadState.asStateFlow()

  // 0..100 for the file currently being uploaded.
  private val _vendorUploadPercent = MutableStateFlow(0)
  val vendorUploadPercent: StateFlow<Int> = _vendorUploadPercent.asStateFlow()

  // Non-null when a document failed to upload, so the wizard can say so instead
  // of pretending everything worked.
  private val _vendorUploadError = MutableStateFlow<String?>(null)
  val vendorUploadError: StateFlow<String?> = _vendorUploadError.asStateFlow()
  fun clearVendorUploadError() { _vendorUploadError.value = null }

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
            // Blank `id` means the match came from the OpenFoodFacts fallback, not
            // our own products table — tell the vendor so they know why the image
            // and category look different from a colleague's earlier scan.
            _barcodeStatusMessage.value = if (match.id.isBlank()) {
              "Found on OpenFoodFacts: ${match.name}"
            } else {
              "Found in BreakQ catalog: ${match.name}"
            }
          } else {
            _scannedProductTemplate.value = null
            _barcodeStatusMessage.value = "Product not found in our database. Please enter the details manually below."
          }
        }
        .onFailure {
          _scannedProductTemplate.value = null
          _barcodeStatusMessage.value = "Couldn't reach the product database. Please enter the details manually below."
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

  // Community catalog search: any product any vendor already added is
  // searchable so the next shopkeeper doesn't have to retype it. Reuses the
  // scannedProductTemplate slot so AddProductScreen's existing autofill path
  // handles the selected result too.
  private val _catalogSearchResults = MutableStateFlow<List<Product>>(emptyList())
  val catalogSearchResults: StateFlow<List<Product>> = _catalogSearchResults.asStateFlow()

  private val _catalogSearchLoading = MutableStateFlow(false)
  val catalogSearchLoading: StateFlow<Boolean> = _catalogSearchLoading.asStateFlow()

  fun searchCatalog(query: String) {
    val q = query.trim()
    if (q.length < 2) {
      _catalogSearchResults.value = emptyList()
      return
    }
    _catalogSearchLoading.value = true
    viewModelScope.launch {
      supabaseGroceryRepo.searchProductsByName(q, supabaseAuthService.currentAccessToken)
        .onSuccess { _catalogSearchResults.value = it }
        .onFailure { _catalogSearchResults.value = emptyList() }
      _catalogSearchLoading.value = false
    }
  }

  fun applyCatalogChoice(product: Product) {
    _scannedProductTemplate.value = product
    _scannedBarcode.value = product.barcode.ifBlank { "" }
    _barcodeStatusMessage.value = "Selected from catalog: ${product.name}"
    _catalogSearchResults.value = emptyList()
  }

  // Success signal for the Add Product flow so AddProductScreen can close and
  // the dashboard can jump to Inventory once the row is actually saved.
  private val _productAddedSuccess = MutableStateFlow(false)
  val productAddedSuccess: StateFlow<Boolean> = _productAddedSuccess.asStateFlow()
  fun clearProductAddedSuccess() { _productAddedSuccess.value = false }

  // Which tab VendorDashboardScreen should open on. 0=Overview 1=Inventory
  // 2=Orders 3=Reviews. Set by other flows (e.g. Add Product success) and read
  // once by the dashboard.
  private val _vendorInitialTab = MutableStateFlow(0)
  val vendorInitialTab: StateFlow<Int> = _vendorInitialTab.asStateFlow()
  fun setVendorInitialTab(tab: Int) { _vendorInitialTab.value = tab }

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
        if (change.table == "products" && change.type == "UPDATE") applyProductChange(change)
      }
    }
  }

  // Keeps the customer's availability badge honest when a shopkeeper flips a
  // switch mid-browse. Only touches stock/price so we don't clobber locally
  // cached images or weight options with a partial realtime payload.
  private fun applyProductChange(change: SupabaseRealtimeClient.RealtimeChange) {
    val record = change.record ?: return
    val productId = record.optString("id").takeIf { it.isNotBlank() } ?: return
    _products.update { list ->
      list.map { product ->
        if (product.id != productId) return@map product
        product.copy(
          inStock = record.optBoolean("in_stock", product.inStock),
          stockQty = if (record.has("stock_qty")) {
            if (record.isNull("stock_qty")) null else record.optInt("stock_qty")
          } else product.stockQty,
          currentPrice = record.optInt("current_price", product.currentPrice)
        )
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
            val rebuilt = buildOrderTimeline(
              currentStatus = status,
              orderDate = order.orderDate,
              nowLabel = "Today, $now"
            )
            order.copy(status = status, timeline = rebuilt)
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
    val refreshToken = prefs.getString("refresh_token", null)
    // Any early exit must also drop the Restoring screen, or a user with a
    // half-written session would be stuck on the loader forever.
    if (email.isBlank()) {
      if (_currentScreen.value is AppScreen.Restoring) _currentScreen.value = AppScreen.Onboarding
      return
    }
    if (refreshToken.isNullOrBlank()) {
      // We only ever persisted the email, not a real Supabase session — calling
      // login(email) with no token behind it used to silently run every REST
      // call and the Realtime socket as the anonymous role (RLS then hides
      // everything), which is why order status never updated live and push
      // never fired after an app restart. Without a refresh token there's no
      // way to actually re-authenticate, so drop the stale local session.
      clearSavedSession()
      if (_currentScreen.value is AppScreen.Restoring) _currentScreen.value = AppScreen.Onboarding
      return
    }
    viewModelScope.launch {
      supabaseAuthService.restoreSession(refreshToken)
        .onSuccess { session ->
          persistRefreshToken(session.refreshToken)
          login(session.email) { user ->
            // Decided only after the server profile has loaded, so a returning user
            // whose profile is actually complete doesn't get bounced back here.
            // user.isVendor also flips true on a stale shop_id from an aborted
            // vendor registration \u2014 landing customers on VendorDashboard,
            // where the shop lookup fails and they get an infinite spinner.
            // Trust serverRole here; VendorDashboard's own guard handles the
            // "vendor whose shops list hasn't loaded yet" case.
            val isConfirmedVendor = user.serverRole == UserRole.VENDOR
            _currentScreen.value = when {
              !user.profileCompleted -> AppScreen.CompleteProfile
              user.isAdmin -> AppScreen.AdminDashboard
              isConfirmedVendor -> AppScreen.VendorDashboard
              else -> {
                _activeShopId.value = null
                AppScreen.Main
              }
            }
          }
        }
        .onFailure {
          // Refresh token expired or was revoked — the user has to log in again.
          clearSavedSession()
          _currentScreen.value = AppScreen.Auth
        }
    }
  }

  private fun saveSession(email: String) {
    prefs.edit().putString("user_email", email).apply()
  }

  // Google Sign-In needs the Credential Manager + googleid deps (currently
  // commented out in app/build.gradle.kts) plus a Web Client ID from the
  // Firebase console. Until those are in place we tell the user rather than
  // silently doing nothing.
  fun signInWithGoogle() {
    _authStatusMessage.value =
      "Google Sign-In is being set up. Please use email for now."
  }

  private fun persistRefreshToken(refreshToken: String) {
    if (refreshToken.isBlank()) return
    prefs.edit().putString("refresh_token", refreshToken).apply()
  }

  private fun clearSavedSession() {
    prefs.edit()
      .remove("user_email")
      .remove("refresh_token")
      .remove("profile_completed_locally")
      .remove("profile_full_name")
      .remove("profile_mobile")
      .remove("profile_address")
      // Cart is per-account — never let the next person to sign in inherit it.
      .remove("cart_items")
      .remove("active_shop_id")
      .apply()
  }

  fun fetchUserLocation() {
    try {
      // Two-stage strategy so the map / "delivering to" pill fills in instantly
      // like Blinkit/Zomato, instead of showing a blank state for 10-30 seconds
      // while GPS locks:
      //
      // Stage 1 (instant, cached): lastLocation returns whatever Play Services
      // has in memory — may be minutes old but good enough to render the UI
      // immediately after login.
      fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
        if (location != null && _userLocation.value == null) {
          _userLocation.value = location
          updateShopDistances(location)
        }
      }
      // Stage 2 (fresh, accurate): overrides the cached fix as soon as a real
      // GPS lock arrives — usually within 2-5 seconds when GPS is on.
      fusedLocationClient.getCurrentLocation(
        com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
        null
      ).addOnSuccessListener { location: Location? ->
        if (location != null) {
          _userLocation.value = location
          updateShopDistances(location)
        }
      }
    } catch (e: SecurityException) {
      // Permission not granted — MainScreen's permission launcher will re-invoke us
      // after the user says yes.
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
      // Sync live products. Overwrite unconditionally — an isNotEmpty guard
      // would leave demo products in place forever whenever the server truly is
      // empty, which is exactly the state a fresh install lives in.
      supabaseGroceryRepo.fetchProducts().onSuccess { liveProducts ->
        _products.value = liveProducts
      }
      // Catalog is in memory now, so a cart saved before the process died can be
      // rebuilt against current prices/stock.
      restoreCartFromPrefs()

      // Sync live shops (Round 2)
      // Round 6.1: overwrite even when server returns empty so a stale seed shop
      // doesn't linger in the UI after the DB was cleared. Empty list = empty UI.
      supabaseGroceryRepo.fetchShops(supabaseAuthService.currentAccessToken).onSuccess { liveShops ->
        _shops.value = liveShops
        _userLocation.value?.let { updateShopDistances(it) }
      }

      // Sync orders
      val email = _userProfile.value.email
      val isAdmin = _userProfile.value.isAdmin
      supabaseGroceryRepo.fetchOrders(customerEmail = email, isAdmin = isAdmin).onSuccess { liveOrders ->
        _orders.value = liveOrders
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
    persistCart()
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
    persistCart()
  }

  fun getCartItemQuantity(productId: String): Int {
    return _cartItems.value
      .filter { it.product.id == productId }
      .sumOf { it.quantity }
  }

  fun clearCart() {
    _cartItems.value = emptyList()
    persistCart()
  }

  // ---- Cart persistence -----------------------------------------------------
  // We store only (productId, weightLabel, qty) rather than the whole Product.
  // Rehydrating against the live catalog means a restored cart always reflects
  // current prices/stock instead of resurrecting a stale snapshot.

  private fun persistCart() {
    val arr = org.json.JSONArray()
    _cartItems.value.forEach { item ->
      arr.put(
        org.json.JSONObject().apply {
          put("productId", item.product.id)
          put("weightLabel", item.selectedWeight.label)
          put("qty", item.quantity)
        }
      )
    }
    prefs.edit()
      .putString("cart_items", arr.toString())
      .putString("active_shop_id", _activeShopId.value.orEmpty())
      .apply()
  }

  private fun restoreCartFromPrefs() {
    val raw = prefs.getString("cart_items", null)
    if (raw.isNullOrBlank()) return
    val catalog = _products.value
    if (catalog.isEmpty()) return

    val restored = mutableListOf<CartItem>()
    runCatching {
      val arr = org.json.JSONArray(raw)
      for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val productId = o.optString("productId")
        val weightLabel = o.optString("weightLabel")
        val qty = o.optInt("qty", 0)
        if (qty <= 0) continue

        // Product may have been delisted since the user last shopped — skip it
        // rather than restoring a dangling row.
        val product = catalog.firstOrNull { it.id == productId } ?: continue
        val weight = product.weightOptions.firstOrNull { it.label == weightLabel }
          ?: product.weightOptions.firstOrNull()
          ?: WeightOption(product.unit, product.currentPrice)
        restored.add(CartItem(product, weight, qty))
      }
    }.onFailure { android.util.Log.w("BreakQ", "Cart restore failed: ${it.message}") }

    if (restored.isNotEmpty()) {
      _cartItems.value = restored
      prefs.getString("active_shop_id", "")
        ?.takeIf { it.isNotBlank() }
        ?.let { _activeShopId.value = it }
    }
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
      status = OrderStatus.PLACED,
      expectedPickupTime = "Awaiting shop confirmation",
      storeName = _userProfile.value.activeStore,
      storeAddress = _userProfile.value.activeStoreAddress,
      timeline = buildOrderTimeline(
        currentStatus = OrderStatus.PLACED,
        orderDate = "Today, $currentTime",
        nowLabel = "Today, $currentTime"
      ),
      qrCodePayload = buildCustomerQrPayload(_userProfile.value.email, orderId)
    )

    _orders.update { listOf(newOrder) + it }
    _latestPlacedOrderId.value = orderId
    _cartItems.value = emptyList()
    // Clear the persisted copy too, otherwise reopening the app would resurrect
    // the cart the user just checked out with and risk a duplicate order.
    persistCart()
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
    role: UserRole = UserRole.CUSTOMER,
    onResult: (Boolean, String, Boolean) -> Unit
  ) {
    val cleanEmail = email.trim().lowercase()
    _pendingSignupName.value = name.trim()
    _pendingSignupMobile.value = mobile.trim()
    // Persist the picked role for post-verification routing (the OTP flow reads
    // this after a process restart).
    _pendingSignupRole.value = role
    // These StateFlows die with the process. A user who signs up, closes the app,
    // then taps the emailed verification link would otherwise lose their mobile
    // number entirely — so mirror them to disk.
    prefs.edit()
      .putString("pending_signup_name", name.trim())
      .putString("pending_signup_mobile", mobile.trim())
      .putString("pending_signup_role", role.name)
      .apply()
    _isAuthLoading.value = true
    _authStatusMessage.value = "Creating account..."

    viewModelScope.launch {
      val metadata = org.json.JSONObject().apply {
        put("full_name", name.trim())
        put("mobile", mobile.trim())
        put("address", address.trim())
        // Server-side handle_new_user() trigger only accepts 'vendor' or
        // 'customer'; anything else safely falls back to 'customer'.
        put("role", if (role == UserRole.VENDOR) "vendor" else "customer")
      }

      supabaseAuthService.signUp(cleanEmail, password, metadata)
        .onSuccess { session ->
          _isAuthLoading.value = false
          if (session.accessToken.isBlank()) {
            _authStatusMessage.value = "Account created! Please verify your email."
            onResult(true, "Please check your email for verification link/OTP.", true)
          } else {
            _authStatusMessage.value = "Account created successfully!"
            persistRefreshToken(session.refreshToken)
            login(cleanEmail, name, mobile, AuthPath.EMAIL)
            updateProfile(name, cleanEmail, mobile, address)
            onResult(true, "Signup successful", false)
          }
        }
        .onFailure { err ->
          _isAuthLoading.value = false
          val msg = err.localizedMessage ?: "Signup failed"
          _authStatusMessage.value = msg
          onResult(false, msg, false)
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
          persistRefreshToken(session.refreshToken)
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
          persistRefreshToken(session.refreshToken)
          // Fall back to the on-disk copy when the in-memory flow was lost to a
          // process restart between signup and verification.
          val pendingName = _pendingSignupName.value
            ?: prefs.getString("pending_signup_name", null).orEmpty()
          val pendingMobile = _pendingSignupMobile.value
            ?: prefs.getString("pending_signup_mobile", null).orEmpty()
          login(cleanEmail, pendingName, pendingMobile, authPath = AuthPath.EMAIL)
          if (pendingName.isNotBlank() || pendingMobile.isNotBlank()) {
            // Persist name + mobile now so the CompleteProfile screen can be skipped.
            updateProfile(pendingName, cleanEmail, pendingMobile, "")
          }
          _pendingSignupName.value = null
          _pendingSignupMobile.value = null
          prefs.edit()
            .remove("pending_signup_name")
            .remove("pending_signup_mobile")
            .apply()
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
    _profileFetchComplete.value = false
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

    // Round 6.2: hydrate from the local SharedPrefs snapshot so a returning user
    // whose profile was completed but never synced to Supabase (e.g. RLS misconfig)
    // doesn't get bounced to Complete Profile again.
    val locallyCompleted = prefs.getBoolean("profile_completed_locally", false)
    val localName = prefs.getString("profile_full_name", "") ?: ""
    val localMobile = prefs.getString("profile_mobile", "") ?: ""
    val localAddress = prefs.getString("profile_address", "") ?: ""

    _userProfile.update {
      it.copy(
        email = cleanEmail,
        fullName = when {
          name.isNotBlank() -> name.trim()
          localName.isNotBlank() -> localName
          isSuperAdmin -> "Super Admin"
          isAdmin -> "Admin"
          else -> it.fullName
        },
        mobileNumber = when {
          mobile.isNotBlank() -> mobile.trim()
          localMobile.isNotBlank() -> localMobile
          else -> it.mobileNumber
        },
        address = if (localAddress.isNotBlank()) localAddress else it.address,
        profileCompleted = name.isNotBlank() || isSuperAdmin || isAdmin || locallyCompleted,
        authPath = authPath ?: it.authPath,
        phoneVerified = it.phoneVerified || locallyCompleted
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
      // Flip regardless of success/failure — the UI just needs to know "we tried".
      _profileFetchComplete.value = true

      // Round 6.2: if the local prefs say the profile IS complete but the server
      // row still doesn't reflect that (RLS blocked, was offline, etc), push it
      // now that we're authenticated. Silent — no user-facing spinner.
      retryProfileSyncIfNeeded()

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

      // Round 6: warm the location cache the second the user logs in so the
      // "delivering to" pill and nearby shops are ready by the time Home renders —
      // no-op if permission isn't granted yet (MainScreen will retry after prompt).
      fetchUserLocation()

      // Round 6.1: preload the set of orders this customer has already rated so
      // OrderDetailsScreen can hide the star form for those. Silent on failure.
      val currentUserId = supabaseAuthService.currentUserId
      if (!currentUserId.isNullOrBlank()) {
        supabaseGroceryRepo.fetchRatedOrderIds(currentUserId, supabaseAuthService.currentAccessToken)
          .onSuccess { ids -> _ratedOrderIds.value = ids }
      }

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
    lng: Double = 0.0,
    yearsInBusiness: Int = 0,
    shopPhotoUri: Uri? = null,
    businessProofUri: Uri? = null
  ) {
    val shopId = "s_${System.currentTimeMillis()}"
    // Was a silent `?: return` — the Submit button looked completely dead when the
    // Supabase session had lapsed. Surface it instead.
    val userId = supabaseAuthService.currentUserId ?: run {
      _vendorUploadState.value = VendorUploadState.IDLE
      _isLoading.value = false
      _authStatusMessage.value = "Your session expired. Please log out and sign in again to register your shop."
      return
    }
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
      yearsInBusiness = yearsInBusiness,
      isPartner = false,
      status = VendorStatus.PENDING
    )

    _isLoading.value = true
    _vendorUploadError.value = null
    _vendorUploadPercent.value = 0
    _vendorUploadState.value = VendorUploadState.UPLOADING_PHOTO
    viewModelScope.launch {
      // 1. Upload the shop photo (required) and business proof (optional) to the
      // `shop-documents` Storage bucket before creating the shop row, so the row
      // is written once with its final URLs.
      var shopImageUrl: String? = null
      var proofUrl: String? = null

      shopPhotoUri?.let { uri ->
        val bytes = getBytesFromUri(uri)
        if (bytes == null) {
          _vendorUploadError.value = "Couldn't read the shop photo from your gallery. Pick it again."
        } else {
          supabaseGroceryRepo.uploadImage(
            "shop-documents", "${shopId}_shop.jpg", bytes, token
          ) { pct -> _vendorUploadPercent.value = pct }
            .onSuccess { shopImageUrl = it }
            .onFailure {
              _vendorUploadError.value = "Shop photo didn't upload: ${it.message}"
              android.util.Log.w("BreakQ", "Shop photo upload failed: ${it.message}")
            }
        }
      }

      if (businessProofUri != null) {
        _vendorUploadPercent.value = 0
        _vendorUploadState.value = VendorUploadState.UPLOADING_PROOF
        val bytes = getBytesFromUri(businessProofUri)
        if (bytes != null) {
          supabaseGroceryRepo.uploadImage(
            "shop-documents", "${shopId}_proof.jpg", bytes, token
          ) { pct -> _vendorUploadPercent.value = pct }
            .onSuccess { proofUrl = it }
            .onFailure {
              _vendorUploadError.value = "Business proof didn't upload: ${it.message}"
              android.util.Log.w("BreakQ", "Business proof upload failed: ${it.message}")
            }
        }
      }

      _vendorUploadPercent.value = 100
      _vendorUploadState.value = VendorUploadState.SAVING_SHOP
      supabaseGroceryRepo.registerShop(newShop, userId, token, shopImageUrl, proofUrl)
        .onSuccess {
          _isLoading.value = false
          _vendorUploadState.value = VendorUploadState.IDLE
          // Update local profile
          _userProfile.update { it.copy(shopId = shopId) }
          // Add to local shops list — keep the uploaded photo so the dashboard
          // shows it immediately without waiting for a refetch.
          _shops.update { listOf(newShop.copy(imageUrl = shopImageUrl.orEmpty())) + it }
          
          // Simulation: Shoot verification email to Super Admin
          triggerAdminVerificationEmail(newShop)
          
          // Redirect to Vendor Dashboard
          _currentScreen.value = AppScreen.VendorDashboard
        }
        .onFailure { err ->
          _isLoading.value = false
          _vendorUploadState.value = VendorUploadState.IDLE
          _vendorUploadPercent.value = 0
          // Bubble the real reason up — "Registration failed. Please try again."
          // gave the vendor nothing to act on.
          _vendorUploadError.value = "Registration failed: ${err.message ?: "unknown error"}"
          android.util.Log.e("BreakQ", "registerShop failed", err)
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
    val userId = supabaseAuthService.currentUserId ?: run {
      _authStatusMessage.value = "Session expired. Please log in again."
      return
    }
    val cleanName = fullName.trim()
    val cleanMobile = mobileNumber.trim()
    val cleanAddress = address.trim()

    // Optimistic local update so the UI feels instant.
    _userProfile.update {
      it.copy(
        fullName = cleanName,
        email = cleanEmail,
        mobileNumber = cleanMobile,
        address = cleanAddress,
        profileCompleted = true,
        phoneVerified = true
      )
    }

    // Round 6.2: Persist profile fields + a "completed" flag to SharedPreferences
    // BEFORE the server call. If Supabase RLS or a network hiccup breaks the sync,
    // the app still remembers the user completed their profile — so we never
    // re-prompt them on the next launch. The background retry (below) eventually
    // pushes to Supabase whenever it becomes reachable.
    prefs.edit()
      .putBoolean("profile_completed_locally", true)
      .putString("profile_full_name", cleanName)
      .putString("profile_mobile", cleanMobile)
      .putString("profile_address", cleanAddress)
      .putBoolean("profile_pending_sync", true)
      .apply()
    _profileSyncPending.value = true

    _isLoading.value = true
    viewModelScope.launch {
      val result = supabaseGroceryRepo.syncProfile(userId, _userProfile.value, supabaseAuthService.currentAccessToken)
      _isLoading.value = false
      result
        .onSuccess {
          prefs.edit().putBoolean("profile_pending_sync", false).apply()
          _profileSyncPending.value = false
          _authStatusMessage.value = "Profile saved."
        }
        .onFailure { err ->
          // Keep profileCompleted — the user really did fill the form — but leave
          // the pending flag set so retryProfileSyncIfNeeded() picks it up and the
          // Save button stays live for a manual retry.
          _authStatusMessage.value = "Couldn't save to server — saved on this device only. Tap Save again to retry."
          android.util.Log.w("BreakQ", "syncProfile failed: ${err.message}")
        }
    }
  }

  // Round 6.2: on every login, if the local SharedPrefs snapshot says the profile
  // was completed but the server row isn't marked completed yet, retry the upsert
  // in the background. Silent — nothing shown to the user either way.
  private fun retryProfileSyncIfNeeded() {
    // Previously this checked _userProfile.value.profileCompleted, which is the
    // LOCAL flag that updateProfile() had just set to true — so it always returned
    // early and never retried anything.
    if (!prefs.getBoolean("profile_pending_sync", false)) return
    val userId = supabaseAuthService.currentUserId ?: return
    val token = supabaseAuthService.currentAccessToken ?: return
    viewModelScope.launch {
      supabaseGroceryRepo.syncProfile(userId, _userProfile.value, token)
        .onSuccess {
          prefs.edit().putBoolean("profile_pending_sync", false).apply()
          _profileSyncPending.value = false
        }
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

  // Round 8: feature gates. The paywall is analytics/placement/branding —
  // catalog size is only a spam backstop (Free = 500 items).
  fun hasBasicAnalytics(): Boolean = currentTier()?.hasBasicAnalytics == true
  fun hasFullAnalytics(): Boolean = currentTier()?.hasFullAnalytics == true
  fun hasPriorityPlacement(): Boolean = currentTier()?.hasPriorityPlacement == true
  fun shouldShowBreakqBranding(): Boolean = currentTier()?.hideBreakqBranding != true

  fun canAddMoreProducts(): Boolean {
    val cap = currentTier()?.itemCap ?: 500
    if (cap == -1) return true
    val shopId = _userProfile.value.shopId ?: return true
    val current = _products.value.count { it.shopId == shopId }
    return current < cap
  }

  // ---- Razorpay checkout ----------------------------------------------------

  sealed class CheckoutState {
    data object Idle : CheckoutState()
    data object CreatingOrder : CheckoutState()
    data class ReadyToPay(
      val orderId: String,
      val amountPaise: Int,
      val currency: String,
      val keyId: String,
      val tierId: String,
      val tierName: String
    ) : CheckoutState()
    data object Verifying : CheckoutState()
    data class Success(val tierName: String) : CheckoutState()
    data class Failed(val reason: String) : CheckoutState()
  }

  private val _checkoutState = MutableStateFlow<CheckoutState>(CheckoutState.Idle)
  val checkoutState: StateFlow<CheckoutState> = _checkoutState.asStateFlow()
  fun clearCheckoutState() { _checkoutState.value = CheckoutState.Idle }

  // Step 1 — ask our Edge Function to create a Razorpay order. The Activity then
  // observes CheckoutState.ReadyToPay and opens the Checkout sheet.
  fun startPlanCheckout(targetTierId: String) {
    val shopId = _userProfile.value.shopId ?: run {
      _checkoutState.value = CheckoutState.Failed("Register your shop before subscribing.")
      return
    }
    val tier = _subscriptionTiers.value.firstOrNull { it.id == targetTierId } ?: return
    if (tier.priceRupees <= 0) {
      _checkoutState.value = CheckoutState.Failed("This plan is free — no payment needed.")
      return
    }
    _checkoutState.value = CheckoutState.CreatingOrder
    viewModelScope.launch {
      supabaseGroceryRepo.createRazorpayOrder(shopId, targetTierId, supabaseAuthService.currentAccessToken)
        .onSuccess { order ->
          _checkoutState.value = CheckoutState.ReadyToPay(
            orderId = order.orderId,
            amountPaise = order.amountPaise,
            currency = order.currency,
            keyId = order.keyId,
            tierId = targetTierId,
            tierName = tier.displayName
          )
        }
        .onFailure { err ->
          _checkoutState.value = CheckoutState.Failed(err.message ?: "Could not start payment.")
        }
    }
  }

  // Step 2 — Checkout succeeded on-device. Hand the signature to the Edge
  // Function, which re-verifies it server-side before upgrading the tier.
  fun onRazorpaySuccess(orderId: String, paymentId: String, signature: String, tierName: String) {
    _checkoutState.value = CheckoutState.Verifying
    viewModelScope.launch {
      supabaseGroceryRepo.verifyRazorpayPayment(orderId, paymentId, signature, supabaseAuthService.currentAccessToken)
        .onSuccess {
          loadVendorSubscription()
          _checkoutState.value = CheckoutState.Success(tierName)
        }
        .onFailure { err ->
          _checkoutState.value = CheckoutState.Failed(
            "Payment received but activation failed: ${err.message}. Contact support with your payment ID $paymentId."
          )
        }
    }
  }

  fun onRazorpayFailure(reason: String) {
    _checkoutState.value = CheckoutState.Failed(reason)
  }

  // ---- Vendor analytics (Advance / Pro) -------------------------------------

  private val _vendorAnalytics = MutableStateFlow(VendorAnalytics())
  val vendorAnalytics: StateFlow<VendorAnalytics> = _vendorAnalytics.asStateFlow()

  fun loadVendorAnalytics() {
    if (!hasBasicAnalytics()) return
    val shopId = _userProfile.value.shopId ?: return
    viewModelScope.launch {
      supabaseGroceryRepo.fetchVendorAnalytics(shopId, supabaseAuthService.currentAccessToken)
        .onSuccess { stats ->
          val todayOrders = _orders.value.count { it.shopId == shopId && it.orderDate.startsWith("Today") }
          _vendorAnalytics.value = stats.copy(ordersToday = todayOrders)
        }
    }
  }

  // Fire-and-forget: customer opened a shop or a product. Powers the vendor's
  // paid analytics. Silently no-ops if not signed in.
  fun logShopView(shopId: String, productId: String? = null, searchTerm: String? = null) {
    if (shopId.isBlank()) return
    val type = when {
      !searchTerm.isNullOrBlank() -> "search_hit"
      !productId.isNullOrBlank() -> "product_view"
      else -> "shop_view"
    }
    viewModelScope.launch {
      supabaseGroceryRepo.logShopViewEvent(
        shopId, type, productId, searchTerm, supabaseAuthService.currentAccessToken
      )
    }
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
          // Customer notification for this status change is sent server-side (Edge
          // Function push + Realtime-delivered in-app notification row) — see
          // SETUP_STEPS.md Task 3. A local notify() here used to fire on whichever
          // device called this function (the vendor's), not the customer's.
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

  // null clears the count back to "untracked" (Call to Confirm); 0 means sold out.
  fun updateProductQty(productId: String, newQty: Int?) {
    _products.update { list ->
      list.map { if (it.id == productId) it.copy(stockQty = newQty) else it }
    }
    viewModelScope.launch {
      supabaseGroceryRepo.updateProductStockQty(productId, newQty, supabaseAuthService.currentAccessToken)
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
      val cap = currentTier()?.itemCap ?: 500
      _tierCapMessage.value = "You've listed $cap products \u2014 the maximum on the Free plan. Subscribe for an unlimited catalog."
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
    stockQty: Int? = null,
    imageUris: List<Uri>,
    barcode: String = "",
    fallbackImageUrl: String = ""
  ) {
    if (!canAddMoreProducts()) {
      val cap = currentTier()?.itemCap ?: 500
      _tierCapMessage.value = "You've listed $cap products — the maximum on the Free plan. Subscribe for an unlimited catalog."
      return
    }
    val productId = "p_${System.currentTimeMillis()}"
    val shopId = _userProfile.value.shopId ?: "s_bharat_kirana"
    
    _isLoading.value = true
    _productUploadMessage.value = null
    viewModelScope.launch {
      val finalImageUrls = mutableListOf<String>()
      var uploadSuccessCount = 0
      var uploadFailCount = 0
      var readFailCount = 0
      
      // 1. Handle Multiple Images Upload
      imageUris.forEachIndexed { index, uri ->
        val bytes = getBytesFromUri(uri)
        if (bytes == null) {
          readFailCount++
          return@forEachIndexed
        }
        val nameWithIndex = "${productId}_$index.jpg"
        supabaseGroceryRepo.uploadProductImage(nameWithIndex, bytes, supabaseAuthService.currentAccessToken)
          .onSuccess { url ->
            finalImageUrls.add(url)
            uploadSuccessCount++
          }
          .onFailure {
            uploadFailCount++
            android.util.Log.w("BreakQ", "Product image upload failed: ${it.message}")
          }
      }

      // 2. Create Product Object
      // Map the picker's display name ("Dairy, Bread & Eggs") back to the
      // canonical categories.id ("dairy") so the FK to categories doesn't blow
      // up. Falls through to the raw string only if the picker showed a
      // category we don't recognise.
      val resolvedCategoryId = _categories.value.firstOrNull { it.name == cat }?.id
        ?: cat.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')

      val newProd = Product(
        id = productId,
        name = name,
        brand = "Store Item",
        categoryId = resolvedCategoryId,
        shopId = shopId,
        currentPrice = price,
        originalPrice = if (mrp > 0) mrp else price,
        discountPercent = if (mrp > price) ((mrp - price) * 100 / mrp) else 0,
        unit = unit,
        description = desc,
        inStock = stock,
        stockQty = stockQty,
        // Fall back to the barcode-lookup image when the vendor didn't shoot their own.
        imageUrl = finalImageUrls.firstOrNull() ?: fallbackImageUrl,
        imageUrls = finalImageUrls.ifEmpty {
          if (fallbackImageUrl.isNotBlank()) listOf(fallbackImageUrl) else emptyList()
        },
        weightOptions = listOf(WeightOption(unit, price, mrp)),
        barcode = barcode
      )

      // 3. Sync to Supabase & Local
      _products.update { listOf(newProd) + it }
      val insertResult = supabaseGroceryRepo.addProduct(newProd, supabaseAuthService.currentAccessToken)
      _isLoading.value = false

      // 4. Surface upload outcome so AddProductScreen can toast the vendor.
      val totalAttempted = imageUris.size
      _productUploadMessage.value = when {
        insertResult.isFailure -> {
          val err = insertResult.exceptionOrNull()?.message ?: "unknown error"
          "Couldn't save the product: $err"
        }
        totalAttempted == 0 -> "Product added (no images)."
        uploadFailCount == 0 && readFailCount == 0 ->
          "Product added. $uploadSuccessCount of $totalAttempted images uploaded."
        else ->
          "Product added, but ${uploadFailCount + readFailCount} of $totalAttempted images failed. You can edit the product to retry."
      }

      // 5. Fire the success signal only on a real DB save so the caller can
      // close the Add Product screen and route to Inventory. On failure, stay
      // put and let the vendor retry.
      if (insertResult.isSuccess) {
        _productAddedSuccess.value = true
      } else {
        // Roll back the optimistic local insert so the vendor doesn't see a
        // phantom product they think was saved.
        _products.update { list -> list.filterNot { it.id == productId } }
      }
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
        // Mark this order as rated so OrderDetailsScreen stops re-prompting the
        // customer every time they open the order.
        _ratedOrderIds.update { it + orderId }
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

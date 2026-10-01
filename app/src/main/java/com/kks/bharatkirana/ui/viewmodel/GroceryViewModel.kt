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
import com.kks.bharatkirana.data.supabase.DuplicateProductException
import com.kks.bharatkirana.data.supabase.MissingServerFunctionException
import com.kks.bharatkirana.data.supabase.PriceChangedException
import com.kks.bharatkirana.data.model.CustomerAddress
import com.kks.bharatkirana.service.MyFirebaseMessagingService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

/**
 * Which server branch [fetchOrdersInto] should hit.
 *   AUTO     – pick based on role: vendors get shop_id, customers get user_id.
 */
class GroceryViewModel(
  application: Application
) : AndroidViewModel(application) {

  private val repository: GroceryRepository = GroceryRepository()
  val supabaseAuthService: SupabaseAuthService = SupabaseAuthService()
  val supabaseGroceryRepo: SupabaseGroceryRepo = SupabaseGroceryRepo(
    tokenRefresher = { stale -> supabaseAuthService.refreshBlocking(stale) }
  )
  private val supabaseRealtime: SupabaseRealtimeClient = SupabaseRealtimeClient()
  val realtimeConnected: StateFlow<Boolean> = supabaseRealtime.connected

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

  private val _cartShopSwitchAlert = MutableStateFlow<CartShopSwitchAlert?>(null)
  val cartShopSwitchAlert: StateFlow<CartShopSwitchAlert?> = _cartShopSwitchAlert.asStateFlow()

  private val _wishlistIds = MutableStateFlow(loadWishlistFromPrefs())
  val wishlistIds: StateFlow<Set<String>> = _wishlistIds.asStateFlow()

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

  sealed class CheckoutIssue {
    data class PriceChanged(val shownTotal: Int, val serverTotal: Int, val promoDropped: Boolean) : CheckoutIssue()
    data class Failed(val message: String) : CheckoutIssue()
  }

  private val _checkoutIssue = MutableStateFlow<CheckoutIssue?>(null)
  val checkoutIssue: StateFlow<CheckoutIssue?> = _checkoutIssue.asStateFlow()
  fun dismissCheckoutIssue() { _checkoutIssue.value = null }

  // Short one-off messages (e.g. "out of stock") shown as a toast.
  private val _userNotice = MutableStateFlow<String?>(null)
  val userNotice: StateFlow<String?> = _userNotice.asStateFlow()
  fun clearUserNotice() { _userNotice.value = null }

  // Set when shops/products couldn't be loaded, so an empty list isn't mistaken for "no shops".
  private val _catalogError = MutableStateFlow<String?>(null)
  val catalogError: StateFlow<String?> = _catalogError.asStateFlow()

  // True while loadSupabaseData() is fetching, so empty lists can show "Loading" instead of "none".
  private val _catalogLoading = MutableStateFlow(false)
  val catalogLoading: StateFlow<Boolean> = _catalogLoading.asStateFlow()
  private var catalogLoadsInFlight = 0
  private var wishlistFetchFailed = false

  private var sessionKeepAliveJob: Job? = null
  // Bumped on logout; a network reply that started under an older value belongs to the previous account.
  private var sessionGeneration = 0
  private var shopsLoadSeq = 0
  private val MAX_CACHED_LOCATION_AGE_MS = 2 * 60_000L
  // Must match the deletion contact in PrivacyPolicyScreen.
  private val PRIVACY_CONTACT_EMAIL = "officialbharatjain2004@gmail.com"

  enum class RefreshTarget { CATALOG, ORDERS, WISHLIST, VENDOR }

  private val _isRefreshing = MutableStateFlow(false)
  val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
  private var pullRefreshJob: Job? = null

  /** Pull-to-refresh. Reuses the normal loaders; a pull while one is running is ignored. */
  fun pullToRefresh(target: RefreshTarget) {
    if (pullRefreshJob?.isActive == true) return
    pullRefreshJob = viewModelScope.launch {
      _isRefreshing.value = true
      try {
        val failed = when (target) {
          RefreshTarget.CATALOG -> {
            loadSupabaseData().join()
            _catalogError.value != null
          }
          RefreshTarget.ORDERS -> {
            fetchOrdersInto(_userProfile.value.email, replace = false)
            _ordersError.value != null
          }
          RefreshTarget.WISHLIST -> {
            val wishlist = refreshWishlistFromServer()
            loadSupabaseData().join()
            wishlist?.join()
            _catalogError.value != null || wishlistFetchFailed
          }
          RefreshTarget.VENDOR -> {
            loadSupabaseData().join()
            hydrateAllEmptyOrderItems()
            _catalogError.value != null || _ordersError.value != null
          }
        }
        if (failed) _userNotice.value = "Couldn't refresh. Check your connection and try again."
      } finally {
        _isRefreshing.value = false
      }
    }
  }

  // Guards vendor status transitions against rapid double taps, screen
  // recomposition storms and Realtime replays. Order id in the set == a
  // status PATCH is currently in flight for that order.
  private val _updatingOrderIds = MutableStateFlow<Set<String>>(emptySet())
  val updatingOrderIds: StateFlow<Set<String>> = _updatingOrderIds.asStateFlow()

  private val _userLocation = MutableStateFlow<Location?>(null)
  val userLocation: StateFlow<Location?> = _userLocation.asStateFlow()

  // ---- Customer delivery addresses (public.customer_addresses) -------------
  private val _addresses = MutableStateFlow<List<CustomerAddress>>(emptyList())
  val addresses: StateFlow<List<CustomerAddress>> = _addresses.asStateFlow()

  private val _addressesLoading = MutableStateFlow(false)
  val addressesLoading: StateFlow<Boolean> = _addressesLoading.asStateFlow()

  private val _addressSaving = MutableStateFlow(false)
  val addressSaving: StateFlow<Boolean> = _addressSaving.asStateFlow()

  private val _addressError = MutableStateFlow<String?>(null)
  val addressError: StateFlow<String?> = _addressError.asStateFlow()

  // ---- Order history load state --------------------------------------------
  private val _ordersLoading = MutableStateFlow(false)
  val ordersLoading: StateFlow<Boolean> = _ordersLoading.asStateFlow()

  private val _ordersError = MutableStateFlow<String?>(null)
  val ordersError: StateFlow<String?> = _ordersError.asStateFlow()

  // ---- Cart fees: mirror public.app_settings, the row checkout bills against ----
  private val _handlingFee = MutableStateFlow(0)
  val handlingFee: StateFlow<Int> = _handlingFee.asStateFlow()

  private val _minOrderFreeHandling = MutableStateFlow(0)
  val minOrderFreeHandling: StateFlow<Int> = _minOrderFreeHandling.asStateFlow()

  private val _freeHandlingDiscount = MutableStateFlow(0)
  val freeHandlingDiscount: StateFlow<Int> = _freeHandlingDiscount.asStateFlow()

  // ---- Round 3: Firebase Remote Config-driven state ----
  private val _isMaintenanceMode = MutableStateFlow(false)
  val isMaintenanceMode: StateFlow<Boolean> = _isMaintenanceMode.asStateFlow()

  private val _updateStatus = MutableStateFlow(UpdateStatus.NONE)
  val updateStatus: StateFlow<UpdateStatus> = _updateStatus.asStateFlow()

  private val _promoBanner = MutableStateFlow<String?>(null)
  val promoBanner: StateFlow<String?> = _promoBanner.asStateFlow()

  private val _supportWhatsappNumber = MutableStateFlow("")
  val supportWhatsappNumber: StateFlow<String> = _supportWhatsappNumber.asStateFlow()

  private val _appliedPromo = MutableStateFlow<AppliedPromo?>(null)
  val appliedPromo: StateFlow<AppliedPromo?> = _appliedPromo.asStateFlow()

  private val _promoStatusMessage = MutableStateFlow<String?>(null)
  val promoStatusMessage: StateFlow<String?> = _promoStatusMessage.asStateFlow()

  // The code the customer asked for. Kept while it can still become valid by
  // changing the cart (minimum order), so it re-applies automatically.
  private var requestedPromoCode: String? = null
  private var promoCheckJob: Job? = null

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

  // Task 5: shop ratings feed for the vendor's Reviews screen. Populated lazily
  // via loadShopRatings() when the vendor opens VendorReviewsScreen.
  private val _shopRatings = MutableStateFlow<List<ShopRating>>(emptyList())
  val shopRatings: StateFlow<List<ShopRating>> = _shopRatings.asStateFlow()
  private val _shopRatingsLoading = MutableStateFlow(false)
  val shopRatingsLoading: StateFlow<Boolean> = _shopRatingsLoading.asStateFlow()

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

    // Proactive dedup: if the current vendor's shop already has a product
    // with this barcode, show the duplicate dialog immediately and skip the
    // Supabase lookup + AddProduct navigation entirely.
    val shopId = _userProfile.value.shopId
    if (shopId != null) {
      val already = _products.value.firstOrNull {
        it.shopId == shopId && it.catalogRef == "barcode:$clean"
      }
      if (already != null) {
        _duplicateAlert.value = DuplicateAlert(
          existing = already,
          severity = DuplicateAlert.Severity.Hard,
          source = DuplicateAlert.Source.Barcode
        )
        navigateBack()
        return
      }
    }

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
    // Proactive dedup for catalog picks that carry a barcode — same rule as
    // the scan path. Products without a barcode fall through to the app-side
    // soft check on List Product.
    val shopId = _userProfile.value.shopId
    val bc = product.barcode.trim()
    if (shopId != null && bc.isNotBlank()) {
      val already = _products.value.firstOrNull {
        it.shopId == shopId && it.catalogRef == "barcode:$bc"
      }
      if (already != null) {
        _duplicateAlert.value = DuplicateAlert(
          existing = already,
          severity = DuplicateAlert.Severity.Hard,
          source = DuplicateAlert.Source.CatalogSelect
        )
        _catalogSearchResults.value = emptyList()
        return
      }
    }

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

  // Fires when addNewProduct detects a duplicate — either via the DB partial
  // unique index (hard) or via the app-side identity check (soft).
  private val _duplicateAlert = MutableStateFlow<DuplicateAlert?>(null)
  val duplicateAlert: StateFlow<DuplicateAlert?> = _duplicateAlert.asStateFlow()
  fun clearDuplicateAlert() { _duplicateAlert.value = null }

  // Optional product id to open the edit dialog for on VendorDashboard's
  // Inventory tab. Set by the "Update Stock" action of the duplicate alert.
  private val _inventoryEditProductId = MutableStateFlow<String?>(null)
  val inventoryEditProductId: StateFlow<String?> = _inventoryEditProductId.asStateFlow()
  fun setInventoryEditProduct(productId: String?) { _inventoryEditProductId.value = productId }

  // Which tab VendorDashboardScreen should open on. 0=Overview 1=Inventory
  // 2=Orders 3=Reviews. Set by other flows (e.g. Add Product success) and read
  // once by the dashboard.
  private val _vendorInitialTab = MutableStateFlow(0)
  val vendorInitialTab: StateFlow<Int> = _vendorInitialTab.asStateFlow()
  fun setVendorInitialTab(tab: Int) { _vendorInitialTab.value = tab }

  init {
    supabaseAuthService.onSessionRefreshed = { session ->
      // A refresh finishing just after logout must not re-save the session.
      if (!prefs.getString("user_email", null).isNullOrBlank()) persistRefreshToken(session.refreshToken)
      supabaseRealtime.setAccessToken(session.accessToken)
    }
    loadSavedSession()
    loadSupabaseData()
    fetchUserLocation()
    fetchFcmToken()
    loadRemoteConfig()
    startRealtimeCollector()
    startPromoRevalidation()
    viewModelScope.launch {
      MyFirebaseMessagingService.tokenRefreshes.collect { token ->
        _userProfile.update { it.copy(fcmToken = token) }
        if (supabaseAuthService.currentUserId != null) syncFcmTokenToServer(token)
      }
    }
  }

  // Renews the access token a few minutes before it expires, so a session left
  // open for hours (a vendor's dashboard) keeps working, Realtime included.
  private fun startSessionKeepAlive() {
    sessionKeepAliveJob?.cancel()
    sessionKeepAliveJob = viewModelScope.launch {
      while (isActive) {
        val dueIn = supabaseAuthService.accessTokenExpiresAtMillis - System.currentTimeMillis() - 5 * 60_000L
        delay(dueIn.coerceAtLeast(30_000L))
        when (supabaseAuthService.refreshSession()) {
          SupabaseAuthService.RefreshOutcome.REFRESHED -> Unit
          SupabaseAuthService.RefreshOutcome.FAILED -> delay(60_000L)
          SupabaseAuthService.RefreshOutcome.REJECTED -> {
            logout()
            _authStatusMessage.value = "Your session has expired. Please sign in again."
            return@launch
          }
        }
      }
    }
  }

  private fun startRealtimeCollector() {
    viewModelScope.launch {
      supabaseRealtime.changes.collect { change ->
        if (change.table == "orders") applyOrderChange(change)
        if (change.table == "notifications" && change.type == "INSERT") applyNewNotification(change)
        if (change.table == "products" && change.type == "UPDATE") applyProductChange(change)
      }
    }
    // Events sent while the socket was down are lost; re-read after reconnecting.
    viewModelScope.launch {
      supabaseRealtime.connected.drop(1).collect { up ->
        if (up && supabaseAuthService.currentUserId != null) {
          refreshOrders()
          loadNotifications()
        }
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
        val newPrice = record.optInt("current_price", product.currentPrice)
        // Single-size products charge the size price, which the server keeps equal
        // to current_price (trg_sync_single_variant_price) — mirror that here.
        val weights = if (product.weightOptions.size == 1 && newPrice != product.currentPrice) {
          listOf(product.weightOptions[0].copy(price = newPrice))
        } else product.weightOptions
        product.copy(
          inStock = record.optBoolean("in_stock", product.inStock),
          stockQty = if (record.has("stock_qty")) {
            if (record.isNull("stock_qty")) null else record.optInt("stock_qty")
          } else product.stockQty,
          currentPrice = newPrice,
          weightOptions = weights
        )
      }
    }
  }

  // Only orders saved before the shop_name/shop_address columns lack them; fill each gap on its own
  // and never overwrite what the order recorded.
  private fun withShopSnapshot(order: Order): Order {
    if (order.storeName.isNotBlank() && order.storeAddress.isNotBlank()) return order
    val shop = _shops.value.firstOrNull { it.id == order.shopId } ?: return order
    return order.copy(
      storeName = order.storeName.ifBlank { shop.name },
      storeAddress = order.storeAddress.ifBlank { shop.address }
    )
  }

  private fun applyOrderChange(change: SupabaseRealtimeClient.RealtimeChange) {
    val record = change.record ?: return
    val orderId = record.optString("id").takeIf { it.isNotBlank() } ?: return
    val statusStr = record.optString("status", "")
    val status = OrderStatus.entries.firstOrNull { it.label == statusStr }

    when (change.type) {
      "INSERT" -> {
        val recordShopId = record.optString("shop_id").takeIf { it.isNotBlank() }
        val myShopId = _userProfile.value.shopId?.takeIf { it.isNotBlank() }
        val recordUserId = record.optString("user_id").takeIf { it.isNotBlank() }
        val myUserId = supabaseAuthService.currentUserId
        val isVendorForShop = myShopId != null && recordShopId == myShopId
        val isCustomerOfOrder = myUserId != null && recordUserId == myUserId
        if (!isVendorForShop && !isCustomerOfOrder) return

        // Vendor sees the row for the first time — customer's local-first
        // insert already put it in _orders on their device.
        if (_orders.value.any { it.id == orderId }) return

        // The payload is the full orders row: customer name/phone, order number,
        // server timestamps and the items_json snapshot all come from the server.
        val newOrder = withShopSnapshot(
          supabaseGroceryRepo.parseOrderRow(record).copy(expectedPickupTime = "Awaiting shop confirmation")
        )
        _orders.update { listOf(newOrder) + it }

        // Vendor "New order received" push + notifications row are owned by
        // the notify-order-status Edge Function so the vendor is reached even
        // when the app is closed. Firing a local notification here as well
        // would double-notify on foreground.

        if (newOrder.items.isEmpty()) {
          viewModelScope.launch {
            supabaseGroceryRepo.fetchOrderItems(orderId, supabaseAuthService.currentAccessToken)
              .onSuccess { fetchedItems ->
                if (fetchedItems.isNotEmpty()) {
                  _orders.update { list ->
                    list.map { if (it.id == orderId) it.copy(items = fetchedItems) else it }
                  }
                }
              }
          }
        }
      }
      "UPDATE" -> {
        if (status == null) return
        val server = supabaseGroceryRepo.parseOrderRow(record)
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
            // Task 3: convert shop.packingTime to a real pickup window once the
            // vendor confirms. Leave the placeholder alone for later statuses
            // so a customer already on Ready-for-Pickup doesn't see the ETA
            // silently rewritten.
            val nextPickup = if (status == OrderStatus.CONFIRMED) {
              val shop = _shops.value.firstOrNull { it.id == order.shopId }
              computePickupEta(shop?.packingTime ?: 15)
            } else order.expectedPickupTime
            // Keep the server's per-status timestamps: the tracker timeline and
            // the pickup ETA are computed from them.
            order.copy(
              status = status,
              timeline = rebuilt,
              expectedPickupTime = nextPickup,
              confirmedAt = server.confirmedAt ?: order.confirmedAt,
              preparingAt = server.preparingAt ?: order.preparingAt,
              readyAt = server.readyAt ?: order.readyAt,
              completedAt = server.completedAt ?: order.completedAt,
              cancelledAt = server.cancelledAt ?: order.cancelledAt,
              orderNumber = server.orderNumber ?: order.orderNumber,
              pickupToken = server.pickupToken ?: order.pickupToken
            )
          }
        }
        // Customer status pushes (Confirmed, Preparing, Ready, Completed,
        // Cancelled) are all owned by the notify-order-status Edge Function.
        // Realtime here only refreshes the UI state — the notification arrives
        // via FCM so it reaches the customer whether the app is open or not.
      }
      "DELETE" -> {
        _orders.update { list -> list.filter { it.id != orderId } }
      }
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
      createdAt = record.optString("created_at"),
      route = if (record.isNull("route")) null else record.optString("route").takeIf { it.isNotBlank() }
    )
    _notifications.update { listOf(notification) + it }
  }

  private fun loadRemoteConfig() {
    BharatRemoteConfig.refresh {
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
        rememberFcmToken(token)
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
      viewModelScope.launch { pushFcmTokenToServer(userId, explicitToken, accessToken) }
      return
    }
    FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
      if (!task.isSuccessful) {
        android.util.Log.w("BreakQ", "FCM token unavailable; pushes will not reach this device", task.exception)
        return@addOnCompleteListener
      }
      val token = task.result ?: return@addOnCompleteListener
      rememberFcmToken(token)
      _userProfile.update { it.copy(fcmToken = token) }
      viewModelScope.launch { pushFcmTokenToServer(userId, token, accessToken) }
    }
  }

  private suspend fun pushFcmTokenToServer(userId: String, token: String, accessToken: String?) {
    val claimed = supabaseGroceryRepo.registerDeviceToken(token, accessToken = accessToken)
    if (claimed.isSuccess) return
    if (claimed.exceptionOrNull() !is MissingServerFunctionException) {
      android.util.Log.w("BreakQ", "register_device_token failed: ${claimed.exceptionOrNull()?.message}")
    }
    // Older server without register_device_token: write both token stores directly.
    supabaseGroceryRepo.updateFcmToken(userId, token, accessToken)
      .onFailure { android.util.Log.w("BreakQ", "FCM token sync failed: ${it.message}") }
    supabaseGroceryRepo.upsertDeviceToken(userId, token, accessToken = accessToken)
      .onFailure { android.util.Log.w("BreakQ", "device_tokens upsert failed: ${it.message}") }
  }

  // Logout can only delete this phone's device_tokens row if the token is on disk; onNewToken alone isn't enough.
  private fun rememberFcmToken(token: String) {
    prefs.edit().putString("fcm_token", token).apply()
  }

  /** Blanks this device's token on the outgoing account so its pushes stop following this phone. */
  private suspend fun clearFcmTokenFor(userId: String?, accessToken: String?) {
    if (userId == null || accessToken == null) return
    supabaseGroceryRepo.updateFcmToken(userId, "", accessToken)
      .onFailure { android.util.Log.w("BreakQ", "Could not clear FCM token on sign-out: ${it.message}") }
    // Also drop the per-device row so campaigns stop targeting this handset.
    val cachedToken = prefs.getString("fcm_token", null)
    if (!cachedToken.isNullOrBlank()) {
      supabaseGroceryRepo.deleteDeviceToken(cachedToken, accessToken)
        .onFailure { android.util.Log.w("BreakQ", "device_tokens delete failed: ${it.message}") }
    }
  }

  // Task 6b: an orderId captured before the session was ready. Drained by
  // login()/restoreSession() once _userProfile.email is populated so a push tap
  // that lands on cold start still lands on the right screen.
  private var _pendingNotificationOrderId: String? = null
  private var _pendingNotificationRoute: String? = null

  // Round 4b: when the user taps a push notification, MainActivity routes here.
  // Order pushes carry an orderId; admin promo pushes carry a `route` string
  // (e.g. "notifications"). If we have either, we deep-link accordingly.
  fun handleNotificationTap(orderId: String?, route: String? = null) {
    if (_userProfile.value.email.isBlank()) {
      // Session isn't ready yet (cold start). Stash and let the login/restore
      // completion path replay this tap once serverRole is known.
      if (!orderId.isNullOrBlank()) _pendingNotificationOrderId = orderId
      if (!route.isNullOrBlank()) _pendingNotificationRoute = route
      return
    }
    routeToNotificationTarget(orderId, route)
  }

  private fun routeToNotificationTarget(orderId: String?, route: String? = null) {
    val target: AppScreen = when {
      route == "notifications" -> AppScreen.Notifications
      orderId.isNullOrBlank() -> AppScreen.Notifications
      _userProfile.value.serverRole == UserRole.VENDOR -> AppScreen.VendorOrderDetails(orderId)
      else -> AppScreen.OrderDetails(orderId)
    }
    navigateTo(target)
  }

  private fun drainPendingNotificationTap() {
    val pendingOrder = _pendingNotificationOrderId
    val pendingRoute = _pendingNotificationRoute
    if (pendingOrder == null && pendingRoute == null) return
    _pendingNotificationOrderId = null
    _pendingNotificationRoute = null
    routeToNotificationTarget(pendingOrder, pendingRoute)
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
      .remove("pending_checkout_id")
      .remove("pending_checkout_fp")
      // The unsynced profile it refers to was removed above; never replay it into the next account.
      .remove("profile_pending_sync")
      .apply()
  }

  fun fetchUserLocation() {
    try {
      // Two-stage strategy so the map / "delivering to" pill fills in instantly
      // like Blinkit/Zomato, instead of showing a blank state for 10-30 seconds
      // while GPS locks:
      //
      // Stage 1 (instant, cached): lastLocation returns whatever Play Services
      // has in memory. Used only if it is recent — an old fix must never be
      // presented as where the customer is now.
      fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
        if (location != null && _userLocation.value == null &&
          System.currentTimeMillis() - location.time <= MAX_CACHED_LOCATION_AGE_MS
        ) {
          _userLocation.value = location
          refreshShopDistances()
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
          refreshShopDistances()
        }
      }
    } catch (e: SecurityException) {
      // Permission not granted — MainScreen's permission launcher will re-invoke us
      // after the user says yes.
    }
  }

  /**
   * Where shop distances are measured from: the address shown in the header
   * (default, else first) when it has a map pin, otherwise the phone's location.
   */
  private fun distanceOrigin(): Pair<Double, Double>? {
    val address = _addresses.value.firstOrNull { it.isDefault } ?: _addresses.value.firstOrNull()
    val lat = address?.lat
    val lng = address?.lng
    if (lat != null && lng != null && (lat != 0.0 || lng != 0.0)) return lat to lng
    return _userLocation.value?.let { it.latitude to it.longitude }
  }

  private fun refreshShopDistances() {
    val origin = distanceOrigin()
    _shops.value = _shops.value.map { shop ->
      // A shop with no pin, or no origin to measure from, gets no distance rather than a made-up one.
      if (origin == null || (shop.lat == 0.0 && shop.lng == 0.0)) return@map shop.copy(distance = "---")
      val distanceKm = calculateDistance(origin.first, origin.second, shop.lat, shop.lng)
      shop.copy(distance = String.format(Locale.US, "%.1f km", distanceKm))
    }.sortedBy { it.distance.substringBefore(" ").toDoubleOrNull() ?: Double.MAX_VALUE }
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

  fun loadSupabaseData(): Job =
    viewModelScope.launch {
      catalogLoadsInFlight++
      _catalogLoading.value = true
      try {
        _catalogError.value = null
        // Sync live products. Overwrite unconditionally — an isNotEmpty guard
        // would leave demo products in place forever whenever the server truly is
        // empty, which is exactly the state a fresh install lives in.
        supabaseGroceryRepo.fetchProducts(supabaseAuthService.currentAccessToken)
          .onSuccess { liveProducts -> _products.value = liveProducts }
          .onFailure { _catalogError.value = "Couldn't load shops and products. Check your connection and try again." }
        // Catalog is in memory now, so a cart saved before the process died can be
        // rebuilt against current prices/stock.
        restoreCartFromPrefs()

        // Cart fees come from the same row the server bills against.
        supabaseGroceryRepo.fetchAppSettings(supabaseAuthService.currentAccessToken)
          .onSuccess { cfg ->
            _handlingFee.value = cfg.handlingFee
            _minOrderFreeHandling.value = cfg.minOrderFreeHandling
            _freeHandlingDiscount.value = cfg.freeHandlingDiscount
          }
          .onFailure { android.util.Log.w("BreakQ", "app_settings fetch failed: ${it.message}") }

        reloadShops()

        // Sync orders. Skip entirely if we don't know who the user is yet — a
        // transient empty response during session restore would otherwise wipe
        // the customer's own just-placed local order from the UI.
        fetchOrdersInto(_userProfile.value.email, replace = false)
      } finally {
        catalogLoadsInFlight--
        _catalogLoading.value = catalogLoadsInFlight > 0
      }
    }

  /**
   * Approved shops, plus — for a vendor — their own shop whatever its status,
   * so a pending or rejected vendor keeps their status screen after a restart.
   * Only the newest request may write, so a slow call started before login
   * can't overwrite the vendor-aware list.
   */
  private suspend fun reloadShops() {
    val seq = ++shopsLoadSeq
    val ownShopId = _userProfile.value.shopId
      ?.takeIf { it.isNotBlank() && _userProfile.value.serverRole == UserRole.VENDOR }
    supabaseGroceryRepo.fetchShops(supabaseAuthService.currentAccessToken, ownShopId)
      .onSuccess { liveShops ->
        if (seq != shopsLoadSeq) return@onSuccess
        // Round 6.1: overwrite even when server returns empty so a stale seed shop
        // doesn't linger in the UI after the DB was cleared. Empty list = empty UI.
        _shops.value = liveShops
        refreshShopDistances()
        syncShopOperationsFromDb()
      }
      .onFailure {
        if (seq == shopsLoadSeq) {
          _catalogError.value = "Couldn't load shops and products. Check your connection and try again."
        }
      }
  }

  /**
   * Single entry point for loading orders for whoever is signed in. [replace]
   * wipes the list first (fresh login / pull-to-refresh); otherwise orders placed
   * locally that the server hasn't returned yet are kept.
   *
   * Vendors filter on shop_id; customers on user_id / email.
   */
  private suspend fun fetchOrdersInto(
    customerEmail: String,
    replace: Boolean
  ) {
    val vendorShopId = _userProfile.value.shopId?.takeIf { it.isNotBlank() }
    val customerUserId = supabaseAuthService.currentUserId?.takeIf { it.isNotBlank() }
    // Used to `return` here without a word, so a blank email produced an empty
    // list that rendered as "No orders yet".
    if (vendorShopId.isNullOrBlank() && customerEmail.isBlank() && customerUserId == null) {
      _ordersError.value = "Session not ready yet. Tap Retry."
      return
    }

    // Without a JWT, PostgREST runs the query as `anon`, RLS matches nothing and
    // we get a successful-but-empty list — indistinguishable from "no orders".
    val token = supabaseAuthService.currentAccessToken
    if (token.isNullOrBlank()) {
      _ordersError.value = "Session not ready yet. Tap Retry."
      return
    }

    _ordersLoading.value = true
    val generation = sessionGeneration
    supabaseGroceryRepo.fetchOrders(
      customerEmail = customerEmail.takeIf { it.isNotBlank() },
      customerUserId = customerUserId,
      vendorShopId = vendorShopId,
      accessToken = token
    ).onSuccess { liveOrders ->
      if (generation != sessionGeneration) return@onSuccess
      _ordersError.value = null
      val serverOrders = liveOrders.map { withShopSnapshot(it) }
      _orders.value = if (replace) {
        serverOrders
      } else {
        val serverIds = serverOrders.map { it.id }.toSet()
        _orders.value.filter { it.id !in serverIds } + serverOrders
      }
      reconcilePendingCheckout(serverOrders)
    }.onFailure { err ->
      if (generation != sessionGeneration) return@onFailure
      // Previously swallowed: a failed fetch left _orders empty and the history
      // screen rendered "no orders yet", which is why past orders looked deleted.
      android.util.Log.e("BreakQ", "fetchOrders failed", err)
      _ordersError.value = err.localizedMessage ?: "Couldn't load your orders."
    }
    _ordersLoading.value = false
  }

  /** Re-reads order history from Supabase. Safe to call on screen open. */
  fun refreshOrders() {
    viewModelScope.launch {
      fetchOrdersInto(_userProfile.value.email, replace = false)
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
        navigateTo(AppScreen.ShopDetail(suggestion.shop.id))
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
    if (quantity > 0 && !canAddToCart(product.id, product, quantity)) return

    // Enforce one-shop-per-cart: if the cart already has items from a
    // different shop, expose a state the UI can render as a confirmation
    // dialog. Do NOT silently merge or drop.
    val current = _cartItems.value
    val existingShopId = current.firstOrNull()?.product?.shopId
    if (existingShopId != null && existingShopId != product.shopId) {
      _cartShopSwitchAlert.value = CartShopSwitchAlert(
        currentShopId = existingShopId,
        currentShopName = _shops.value.firstOrNull { it.id == existingShopId }?.name ?: "another shop",
        newShopName = _shops.value.firstOrNull { it.id == product.shopId }?.name ?: "this shop",
        pendingProduct = product,
        pendingWeight = weightOption,
        pendingQty = quantity
      )
      return
    }

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

  /** Vendor picks "Clear Cart & Add This Item" — wipe + add pending. */
  fun clearCartAndAddPending() {
    val pending = _cartShopSwitchAlert.value ?: return
    _cartShopSwitchAlert.value = null
    _cartItems.value = emptyList()
    persistCart()
    addToCart(pending.pendingProduct, pending.pendingWeight, pending.pendingQty)
  }

  fun dismissCartShopSwitchAlert() { _cartShopSwitchAlert.value = null }

  // Checked against the latest catalog; checkout re-checks stock on the server anyway.
  private fun canAddToCart(productId: String, fallback: Product?, adding: Int): Boolean {
    val latest = _products.value.firstOrNull { it.id == productId } ?: fallback ?: return true
    val shop = _shops.value.firstOrNull { it.id == latest.shopId }
    if (shop != null && !shop.isOpen) {
      _userNotice.value = "${shop.name} isn't taking orders right now."
      return false
    }
    if (!latest.inStock || latest.stockQty == 0) {
      _userNotice.value = "${latest.name} is out of stock."
      return false
    }
    val cap = latest.stockQty ?: return true
    val inCart = _cartItems.value.filter { it.product.id == productId }.sumOf { it.quantity }
    if (inCart + adding > cap) {
      _userNotice.value = "Only $cap of ${latest.name} available."
      return false
    }
    return true
  }

  fun updateCartQuantity(productId: String, weightLabel: String, delta: Int) {
    if (delta > 0 && !canAddToCart(productId, null, delta)) return
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

  private fun loadWishlistFromPrefs(): Set<String> {
    val raw = prefs.getString("wishlist_ids", null) ?: return emptySet()
    return raw.split("|").filter { it.isNotBlank() }.toSet()
  }

  private fun persistWishlist() {
    prefs.edit().putString("wishlist_ids", _wishlistIds.value.joinToString("|")).apply()
  }

  fun isInWishlist(productId: String): Boolean = _wishlistIds.value.contains(productId)

  fun toggleWishlist(productId: String) {
    if (productId.isBlank()) return
    val wasIn = productId in _wishlistIds.value
    _wishlistIds.update { current ->
      if (wasIn) current - productId else current + productId
    }
    persistWishlist()
    // Server sync — writes are per-user via RLS; failure logs but doesn't
    // revert the optimistic local change (customer can retry the toggle).
    val userId = supabaseAuthService.currentUserId ?: return
    val token = supabaseAuthService.currentAccessToken
    viewModelScope.launch {
      val result = if (wasIn) {
        supabaseGroceryRepo.removeWishlistItem(userId, productId, token)
      } else {
        supabaseGroceryRepo.addWishlistItem(userId, productId, token)
      }
      result.onFailure {
        android.util.Log.w("BreakQ", "wishlist sync failed: ${it.message}")
        if (supabaseAuthService.currentUserId != userId) return@onFailure
        _wishlistIds.update { current -> if (wasIn) current + productId else current - productId }
        persistWishlist()
        _userNotice.value = "Couldn't update your wishlist. Check your connection and try again."
      }
    }
  }

  fun removeFromWishlist(productId: String) {
    if (productId !in _wishlistIds.value) return
    _wishlistIds.update { it - productId }
    persistWishlist()
    val userId = supabaseAuthService.currentUserId ?: return
    viewModelScope.launch {
      supabaseGroceryRepo.removeWishlistItem(userId, productId, supabaseAuthService.currentAccessToken)
        .onFailure {
          android.util.Log.w("BreakQ", "wishlist remove failed: ${it.message}")
          if (supabaseAuthService.currentUserId != userId) return@onFailure
          _wishlistIds.update { current -> current + productId }
          persistWishlist()
          _userNotice.value = "Couldn't remove it from your wishlist. Check your connection and try again."
        }
    }
  }

  /**
   * Overwrites the local wishlist with the server's copy. Called at login so
   * a customer signing in on a shared device never inherits the previous
   * user's prefs cache.
   */
  private fun refreshWishlistFromServer(): Job? {
    val userId = supabaseAuthService.currentUserId ?: return null
    return viewModelScope.launch {
      supabaseGroceryRepo.fetchWishlistProductIds(userId, supabaseAuthService.currentAccessToken)
        .onSuccess { ids ->
          // A reply that lands after logout / account switch belongs to someone else.
          if (supabaseAuthService.currentUserId != userId) return@onSuccess
          wishlistFetchFailed = false
          _wishlistIds.value = ids
          persistWishlist()
        }
        .onFailure {
          wishlistFetchFailed = true
          android.util.Log.w("BreakQ", "wishlist fetch failed: ${it.message}")
        }
    }
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

  /**
   * [acceptedTotal] is the server total the customer agreed to in the
   * "your total has changed" dialog; otherwise the total shown in the cart is
   * sent, and the server refuses the order if its own total differs.
   */
  fun placeOrder(acceptedTotal: Int? = null) {
    if (_isOrderPlacing.value) return
    if (_cartItems.value.isEmpty()) return
    _isOrderPlacing.value = true

    viewModelScope.launch {
      // A promo re-check still running would leave a stale discount in the total.
      promoCheckJob?.join()

      val items = _cartItems.value
      // Use the shopId of the first cart item as the order's shopId (RLS requires
      // this so the vendor of that shop can see the order). If no items had a
      // shopId, refuse to place — the order would otherwise land on
      // "default_shop" and no vendor would ever see it.
      val shopId = items.firstOrNull()?.product?.shopId?.takeIf { it.isNotBlank() && it != "default_shop" }
      if (items.isEmpty() || shopId == null) {
        if (items.isNotEmpty()) {
          _checkoutIssue.value = CheckoutIssue.Failed("No shop is selected for this cart. Open a shop and add the items again.")
        }
        _isOrderPlacing.value = false
        return@launch
      }

      val itemTotal = items.sumOf { it.totalPrice }
      val discount = if (itemTotal > _minOrderFreeHandling.value) _freeHandlingDiscount.value else 0
      val promo = _appliedPromo.value
      val shownTotal = (itemTotal + _handlingFee.value - discount - (promo?.discountRupees ?: 0)).coerceAtLeast(0)

      val orderId = checkoutIdFor(items, shopId, promo?.code)

      val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
      val currentTime = timeFormat.format(Date())

      // Snapshot the shop's real name/address from the shops list at order time
      // so History and OrderDetails always show the correct shop, even if the
      // customer never routed through the shop screen (activeStore may be blank).
      val liveShop = _shops.value.firstOrNull { it.id == shopId }
      val snapshotStoreName = liveShop?.name?.takeIf { it.isNotBlank() }
        ?: _userProfile.value.activeStore.takeIf { it.isNotBlank() }
        ?: "Your shop"
      val snapshotStoreAddress = liveShop?.address?.takeIf { it.isNotBlank() }
        ?: _userProfile.value.activeStoreAddress

      val newOrder = Order(
        id = orderId,
        shopId = shopId,
        items = items,
        totalAmount = acceptedTotal ?: shownTotal,
        orderDate = "Today, $currentTime",
        status = OrderStatus.PLACED,
        expectedPickupTime = "Awaiting shop confirmation",
        storeName = snapshotStoreName,
        storeAddress = snapshotStoreAddress,
        timeline = buildOrderTimeline(
          currentStatus = OrderStatus.PLACED,
          orderDate = "Today, $currentTime",
          nowLabel = "Today, $currentTime"
        ),
        qrCodePayload = buildCustomerQrPayload(_userProfile.value.email, orderId)
      )

      supabaseGroceryRepo.insertOrder(
        order = newOrder,
        customerEmail = _userProfile.value.email,
        customerName = _userProfile.value.fullName,
        customerMobile = _userProfile.value.mobileNumber,
        promoCode = promo?.code,
        expectedTotal = acceptedTotal ?: shownTotal,
        accessToken = supabaseAuthService.currentAccessToken
      ).onSuccess { serverFields ->
        // Server confirmed. Now — and only now — we commit local state:
        // add the order, clear cart, clear promo, notify, navigate.
        clearPendingCheckout()
        val confirmed = newOrder.copy(
          orderNumber = serverFields.orderNumber ?: newOrder.orderNumber,
          pickupToken = serverFields.pickupToken ?: newOrder.pickupToken,
          totalAmount = serverFields.totalAmount ?: newOrder.totalAmount
        )
        _orders.update { list -> listOf(confirmed) + list.filter { it.id != confirmed.id } }
        _latestPlacedOrderId.value = confirmed.id
        _cartItems.value = emptyList()
        persistCart()
        clearPromoCode()

        showSystemNotification(
          title = "Order placed successfully",
          message = "Order ${confirmed.displayNumber} for ₹${confirmed.totalAmount}. We'll notify you when ${confirmed.storeName.ifBlank { "the shop" }} accepts it.",
          channelId = "customer_notifications",
          channelName = "Order Updates",
          orderId = confirmed.id
        )
        navigateTo(AppScreen.OrderPlaced(confirmed.id))
        // Replace the local copy with the server's line prices and snapshot.
        refreshOrders()
      }.onFailure { err ->
        android.util.Log.w("GroceryViewModel", "insertOrder failed for ${newOrder.id}: ${err.message}")
        // Cart is intentionally NOT cleared — the user can retry the same
        // checkout without re-adding items.
        if (err is PriceChangedException) {
          _checkoutIssue.value = CheckoutIssue.PriceChanged(
            shownTotal = acceptedTotal ?: shownTotal,
            serverTotal = err.serverTotal,
            promoDropped = promo != null && err.promoCode == null
          )
          supabaseGroceryRepo.fetchProducts(supabaseAuthService.currentAccessToken)
            .onSuccess { _products.value = it; repriceCartFromCatalog() }
        } else {
          _checkoutIssue.value = CheckoutIssue.Failed(
            err.message?.takeIf { it.isNotBlank() && err !is java.io.IOException }
              ?: "Couldn't reach BreakQ. Check your connection and try again."
          )
          // The order may have gone through even though the reply was lost.
          refreshOrders()
        }
      }
      _isOrderPlacing.value = false
    }
  }

  fun confirmPriceChangeAndPlaceOrder() {
    val issue = _checkoutIssue.value as? CheckoutIssue.PriceChanged ?: return
    _checkoutIssue.value = null
    placeOrder(acceptedTotal = issue.serverTotal)
  }

  // Same shop + items + promo = the same checkout, so a retry after a lost
  // response reuses the order id and the server hands back the existing order.
  private fun cartFingerprint(items: List<CartItem>, shopId: String, promoCode: String?): String =
    buildString {
      append(shopId).append('|').append(promoCode.orEmpty())
      items.sortedBy { it.product.id + "|" + it.selectedWeight.label }.forEach {
        append('|').append(it.product.id).append(':').append(it.selectedWeight.label).append(':').append(it.quantity)
      }
    }

  private fun checkoutIdFor(items: List<CartItem>, shopId: String, promoCode: String?): String {
    val fingerprint = cartFingerprint(items, shopId, promoCode)
    val savedId = prefs.getString("pending_checkout_id", null)
    if (savedId != null && prefs.getString("pending_checkout_fp", null) == fingerprint) return savedId
    val newId = "KIR-" + System.currentTimeMillis() + "-" + (1000..9999).random()
    prefs.edit()
      .putString("pending_checkout_id", newId)
      .putString("pending_checkout_fp", fingerprint)
      .apply()
    return newId
  }

  private fun clearPendingCheckout() {
    prefs.edit().remove("pending_checkout_id").remove("pending_checkout_fp").apply()
  }

  // A checkout that "failed" on the phone may have been created on the server
  // (reply lost, app killed). Once it shows up, don't let that cart be ordered twice.
  private fun reconcilePendingCheckout(serverOrders: List<Order>) {
    val pendingId = prefs.getString("pending_checkout_id", null) ?: return
    if (serverOrders.none { it.id == pendingId }) return
    val pendingFingerprint = prefs.getString("pending_checkout_fp", null)
    clearPendingCheckout()
    val items = _cartItems.value
    val shopId = items.firstOrNull()?.product?.shopId ?: return
    if (pendingFingerprint == cartFingerprint(items, shopId, _appliedPromo.value?.code)) {
      _cartItems.value = emptyList()
      persistCart()
      clearPromoCode()
    }
  }

  // Re-point cart lines at the latest catalog so the cart shows current prices.
  private fun repriceCartFromCatalog() {
    val catalog = _products.value.associateBy { it.id }
    _cartItems.update { items ->
      items.map { item ->
        val product = catalog[item.product.id] ?: return@map item
        val weight = product.weightOptions.firstOrNull { it.label == item.selectedWeight.label } ?: item.selectedWeight
        item.copy(product = product, selectedWeight = weight)
      }
    }
    persistCart()
  }

  fun applyPromoCode(code: String) {
    val cleanCode = code.trim().uppercase()
    if (cleanCode.isBlank()) {
      _promoStatusMessage.value = "Enter a code"
      return
    }
    requestedPromoCode = cleanCode
    _appliedPromo.value = null
    _promoStatusMessage.value = "Checking…"
    launchPromoCheck(cleanCode)
  }

  fun clearPromoCode() {
    promoCheckJob?.cancel()
    requestedPromoCode = null
    _appliedPromo.value = null
    _promoStatusMessage.value = null
  }

  // A percentage discount or a minimum order changes with the cart, so every
  // cart edit re-asks the server. Debounced so tapping + five times is one call.
  private fun startPromoRevalidation() {
    viewModelScope.launch {
      _cartItems.drop(1).collect { items ->
        val code = requestedPromoCode ?: return@collect
        if (items.isEmpty()) clearPromoCode() else launchPromoCheck(code, debounceMs = 400)
      }
    }
  }

  // Only one check in flight; a newer cart state cancels the older answer.
  private fun launchPromoCheck(code: String, debounceMs: Long = 0) {
    promoCheckJob?.cancel()
    promoCheckJob = viewModelScope.launch {
      if (debounceMs > 0) delay(debounceMs)
      runPromoCheck(code)
    }
  }

  private suspend fun runPromoCheck(code: String) {
    val items = _cartItems.value
    val shopId = items.firstOrNull()?.product?.shopId?.takeIf { it.isNotBlank() && it != "default_shop" }
    val token = supabaseAuthService.currentAccessToken
    if (token.isNullOrBlank()) {
      _appliedPromo.value = null
      _promoStatusMessage.value = "Please sign in to use a promo code"
      return
    }
    if (shopId == null) {
      _appliedPromo.value = null
      _promoStatusMessage.value = "Add items to your cart first"
      return
    }

    supabaseGroceryRepo.previewPromo(code, shopId, items, token)
      .onSuccess { preview ->
        if (preview.reason == null && preview.discount > 0) {
          val applied = preview.promoCode ?: code
          _appliedPromo.value = AppliedPromo(applied, preview.discount)
          _promoStatusMessage.value = "$applied applied — ₹${preview.discount} off"
        } else {
          _appliedPromo.value = null
          _promoStatusMessage.value = promoRejectionMessage(code, preview)
          // Only a minimum-order miss can be fixed by editing the cart.
          if (preview.reason != "MIN_ORDER") requestedPromoCode = null
        }
      }
      .onFailure {
        _appliedPromo.value = null
        _promoStatusMessage.value = "Couldn't check $code right now. Please try again."
      }
  }

  private fun promoRejectionMessage(code: String, p: SupabaseGroceryRepo.PromoPreview): String =
    when (p.reason) {
      "MIN_ORDER" -> "Add ₹${(p.minOrderAmount - p.itemsTotal).coerceAtLeast(1)} more to use $code"
      "EXPIRED" -> "$code has expired"
      "NOT_STARTED" -> "$code isn't active yet"
      "INACTIVE" -> "$code is no longer active"
      "WRONG_SHOP" -> "$code isn't valid at this shop"
      "USAGE_LIMIT" -> "$code has reached its usage limit"
      "PER_CUSTOMER_LIMIT" -> "You've already used $code"
      "ZERO_DISCOUNT" -> "$code gives no discount on this cart"
      else -> "Invalid code"
    }

  private fun simulateVendorNotification(order: Order) {
    // Kept as a stub for backwards compatibility with any lingering call sites.
    // Vendor "New Order Received" notifications are delivered via the Supabase
    // Realtime INSERT handler on the vendor's device — never here on the
    // customer's device.
    val _unused = order
  }

  private fun showSystemNotification(
    title: String,
    message: String,
    channelId: String,
    channelName: String,
    orderId: String? = null
  ) {
    val context = getApplication<Application>()
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
      val channel = android.app.NotificationChannel(channelId, channelName, android.app.NotificationManager.IMPORTANCE_HIGH)
      notificationManager.createNotificationChannel(channel)
    }

    // Task 6: make the tap route to the right order screen, same way FCM pushes
    // do via MyFirebaseMessagingService. Without this, tapping a locally-fired
    // "Order placed" toast did nothing.
    val intent = android.content.Intent(context, com.kks.bharatkirana.MainActivity::class.java).apply {
      addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
      putExtra(com.kks.bharatkirana.service.MyFirebaseMessagingService.EXTRA_FROM_PUSH, true)
      if (!orderId.isNullOrBlank()) {
        putExtra(com.kks.bharatkirana.service.MyFirebaseMessagingService.EXTRA_ORDER_ID, orderId)
      }
    }
    val pendingIntent = android.app.PendingIntent.getActivity(
      context,
      (orderId ?: title).hashCode(),
      intent,
      android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
    )

    val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
      .setSmallIcon(com.kks.bharatkirana.R.drawable.ic_launcher_foreground)
      .setContentTitle(title)
      .setContentText(message)
      .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
      .setAutoCancel(true)
      .setContentIntent(pendingIntent)

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
    // Captured now: by the time the cleanup below runs, someone else may have signed in.
    val outgoingUserId = supabaseAuthService.currentUserId
    val outgoingToken = supabaseAuthService.currentAccessToken
    supabaseAuthService.clearLocalSession()
    viewModelScope.launch {
      // Detach this device from the outgoing account so its pushes don't follow
      // the phone to whoever logs in next.
      clearFcmTokenFor(outgoingUserId, outgoingToken)
      supabaseAuthService.revokeSession(outgoingToken)
    }
    supabaseRealtime.disconnect()
    clearSavedSession()
    resetUserScopedState()
    _authStatusMessage.value = null
    screenBackStack.clear()
    _currentScreen.value = AppScreen.Auth
  }

  // No server function deletes an account yet (profiles has no DELETE policy), so deletion is
  // requested from the privacy contact named in the Privacy Policy.
  fun requestAccountDeletion() {
    val email = _userProfile.value.email
    val userId = supabaseAuthService.currentUserId.orEmpty()
    val body = "Please delete my BreakQ account and its data.\n\nAccount email: $email\nAccount ID: $userId"
    val uri = Uri.parse(
      "mailto:$PRIVACY_CONTACT_EMAIL?subject=${Uri.encode("BreakQ account deletion request")}&body=${Uri.encode(body)}"
    )
    val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO, uri).apply {
      addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
      getApplication<Application>().startActivity(intent)
    } catch (_: Exception) {
      _userNotice.value = "No email app found. Email $PRIVACY_CONTACT_EMAIL from $email to ask for deletion."
    }
  }

  /**
   * Wipe every per-user StateFlow the ViewModel holds. Called from
   * `logout()` so switching accounts never leaves
   * stale orders / notifications / cart / wishlist from the previous user
   * visible to the next one. Public data (shops, products, subscription
   * tiers, remote-config toggles) is intentionally kept — it survives a
   * signout because it isn't scoped to a user.
   */
  private fun resetUserScopedState() {
    sessionGeneration++
    sessionKeepAliveJob?.cancel()
    sessionKeepAliveJob = null
    _checkoutIssue.value = null
    _userProfile.value = UserProfile()
    _profileFetchComplete.value = false
    _profileSyncPending.value = false

    // Cart + shop
    _cartItems.value = emptyList()
    _activeShopId.value = null
    _cartShopSwitchAlert.value = null

    // Orders + history
    _orders.value = emptyList()
    _latestPlacedOrderId.value = null
    _ratedOrderIds.value = emptySet()
    _shopRatings.value = emptyList()

    // Notifications
    _notifications.value = emptyList()

    // Wishlist (in-memory + persisted)
    _wishlistIds.value = emptySet()
    wishlistFetchFailed = false
    prefs.edit().remove("wishlist_ids").apply()

    // Saved addresses belong to the account; distances fall back to the phone's location.
    _addresses.value = emptyList()
    _addressError.value = null
    _addressesLoading.value = false
    refreshShopDistances()

    // Search
    _searchQuery.value = ""
    // searchSuggestions is derived from _searchQuery via combine() so it
    // clears automatically when the query is emptied.
    _selectedProduct.value = null
    _selectedCategory.value = null

    // Promo state
    clearPromoCode()

    // Pending vendor / customer signup drafts
    _pendingSignupName.value = null
    _pendingSignupMobile.value = null

    // Vendor-side state
    _vendorSubscription.value = null
    _vendorAnalytics.value = VendorAnalytics()
    _tierCapMessage.value = null
    _duplicateAlert.value = null
    _inventoryEditProductId.value = null
    _vendorInitialTab.value = 0
  }

  fun login(
    email: String,
    name: String = "",
    mobile: String = "",
    authPath: AuthPath? = null,
    onProfileReady: (UserProfile) -> Unit = {}
  ) {
    val cleanEmail = email.trim()

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
          else -> it.fullName
        },
        mobileNumber = when {
          mobile.isNotBlank() -> mobile.trim()
          localMobile.isNotBlank() -> localMobile
          else -> it.mobileNumber
        },
        address = if (localAddress.isNotBlank()) localAddress else it.address,
        profileCompleted = name.isNotBlank() || locallyCompleted,
        authPath = authPath ?: it.authPath
      )
    }

    // Persist session
    saveSession(cleanEmail)

    // Kick off the Realtime subscription with the user's JWT so RLS lets them
    // receive their own orders' postgres_changes stream.
    supabaseRealtime.connect(supabaseAuthService.currentAccessToken)
    startSessionKeepAlive()

    // Fetch server-side profile (role, real name/mobile/shop_id). The server role
    // is the sole source of truth for routing from here on.
    viewModelScope.launch {
      val generation = sessionGeneration
      val userId = supabaseAuthService.currentUserId
      var accountBlocked = false
      if (!userId.isNullOrBlank()) {
        supabaseGroceryRepo.fetchProfile(userId, supabaseAuthService.currentAccessToken)
          .onSuccess { serverProfile ->
            if (generation != sessionGeneration) return@onSuccess
            accountBlocked = serverProfile.isBlocked
            _userProfile.update { local ->
              // Trust the server's shopId literally. If the server says null,
              // clear any local stale value — a leftover shopId from a prior
              // aborted vendor registration must not carry over into the
              // customer view.
              val resolvedShopId = if (serverProfile.serverRole == UserRole.VENDOR) {
                serverProfile.shopId ?: local.shopId
              } else {
                serverProfile.shopId
              }
              local.copy(
                fullName = serverProfile.fullName.ifBlank { local.fullName },
                mobileNumber = serverProfile.mobileNumber.ifBlank { local.mobileNumber },
                address = serverProfile.address.ifBlank { local.address },
                loyaltyPoints = serverProfile.loyaltyPoints,
                walletBalance = serverProfile.walletBalance,
                shopId = resolvedShopId,
                profileCompleted = serverProfile.profileCompleted || local.profileCompleted,
                phoneVerified = serverProfile.phoneVerified || local.phoneVerified,
                serverRole = serverProfile.serverRole
              )
            }
          }
      }
      // Signed out (or someone else signed in) while the profile was loading.
      if (generation != sessionGeneration) return@launch
      // Flip regardless of success/failure — the UI just needs to know "we tried".
      _profileFetchComplete.value = true

      if (accountBlocked) {
        logout()
        _authStatusMessage.value = "This account has been blocked. Please contact BreakQ support."
        return@launch
      }

      // Admin lives on the web panel only. Stop before any navigation, token
      // sync or data load, sign out, and say where to go instead.
      if (_userProfile.value.isAdmin) {
        logout()
        _authStatusMessage.value = "Admin accounts use the web admin panel. Please sign in there."
        return@launch
      }

      // Round 6.2: if the local prefs say the profile IS complete but the server
      // row still doesn't reflect that (RLS blocked, was offline, etc), push it
      // now that we're authenticated. Silent — no user-facing spinner.
      retryProfileSyncIfNeeded()

      // A vendor's own shop may be pending or rejected, which the public list
      // leaves out; load it before routing so they land on their status screen.
      if (_userProfile.value.serverRole == UserRole.VENDOR) reloadShops()

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
          .onSuccess { ids -> if (generation == sessionGeneration) _ratedOrderIds.value = ids }
        // Delivery address book — drives the Home "Delivering to" pill, so it has
        // to be warm before Home renders.
        refreshAddresses(currentUserId)
      }

      // Overwrite the prefs-cached wishlist with server truth so a customer
      // signing in on a shared device sees their own list, not the last user's.
      if (generation != sessionGeneration) return@launch
      refreshWishlistFromServer()

      // Load orders for the signed-in customer or vendor. Unconditionally
      // overwrite so the newly-authenticated user never sees stale rows from
      // the previous account — even if their own list is genuinely empty.
      fetchOrdersInto(cleanEmail, replace = true)

      loadNotifications()

      // Task 6b: if a push tap arrived during cold start (before we knew who
      // the user was), replay it now that serverRole is known.
      drainPendingNotificationTap()
    }
  }

  fun loadNotifications() {
    val userId = supabaseAuthService.currentUserId ?: return
    val generation = sessionGeneration
    viewModelScope.launch {
      supabaseGroceryRepo.fetchNotifications(userId, supabaseAuthService.currentAccessToken)
        .onSuccess { list -> if (generation == sessionGeneration) _notifications.value = list }
    }
  }

  fun markNotificationRead(notificationId: String) {
    val notification = _notifications.value.firstOrNull { it.id == notificationId } ?: return
    if (notification.isRead) return
    _notifications.update { list -> list.map { if (it.id == notificationId) it.copy(isRead = true) else it } }
    viewModelScope.launch {
      supabaseGroceryRepo.markNotificationRead(notificationId, supabaseAuthService.currentAccessToken)
        .onFailure { loadNotifications() }
    }
  }

  fun markAllNotificationsRead() {
    val userId = supabaseAuthService.currentUserId ?: return
    if (_notifications.value.none { !it.isRead }) return
    _notifications.update { list -> list.map { if (!it.isRead) it.copy(isRead = true) else it } }
    viewModelScope.launch {
      supabaseGroceryRepo.markAllNotificationsRead(userId, supabaseAuthService.currentAccessToken)
        .onFailure {
          _userNotice.value = "Couldn't mark notifications as read. Check your connection and try again."
          loadNotifications()
        }
    }
  }

  fun clearAllNotifications() {
    val userId = supabaseAuthService.currentUserId ?: return
    if (_notifications.value.isEmpty()) return
    _notifications.value = emptyList()
    viewModelScope.launch {
      supabaseGroceryRepo.deleteAllNotifications(userId, supabaseAuthService.currentAccessToken)
        .onFailure {
          _userNotice.value = "Couldn't clear notifications. Check your connection and try again."
          loadNotifications()
        }
    }
  }

  fun updateShopDetails(shopId: String, updatedShop: Shop) {
    val current = _shops.value.firstOrNull { it.id == shopId } ?: return
    // Only what actually changed is sent, so a stale cached copy can't overwrite newer values.
    val fields = org.json.JSONObject().apply {
      if (updatedShop.name != current.name) put("name", updatedShop.name)
      if (updatedShop.ownerName != current.ownerName) put("owner_name", updatedShop.ownerName)
      if (updatedShop.phone != current.phone) put("phone", updatedShop.phone)
      if (updatedShop.address != current.address) put("address", updatedShop.address)
      if (updatedShop.lat != current.lat || updatedShop.lng != current.lng) {
        put("lat", updatedShop.lat)
        put("lng", updatedShop.lng)
      }
      if (updatedShop.isOpen != current.isOpen) put("accepting_orders", updatedShop.isOpen)
      if (updatedShop.autoConfirm != current.autoConfirm) put("auto_confirm", updatedShop.autoConfirm)
      if (updatedShop.packingTime != current.packingTime) put("packing_time", updatedShop.packingTime.coerceIn(5, 180))
    }
    if (fields.length() == 0) return
    _shops.update { list -> list.map { if (it.id == shopId) updatedShop else it } }
    syncShopOperationsFromDb()
    viewModelScope.launch {
      supabaseGroceryRepo.updateShopFields(shopId, fields, supabaseAuthService.currentAccessToken)
        .onFailure {
          android.util.Log.w("BreakQ", "Shop update failed", it)
          _shops.update { list -> list.map { s -> if (s.id == shopId) current else s } }
          syncShopOperationsFromDb()
          _userNotice.value = "Couldn't save your shop changes. Check your connection and try again."
        }
    }
  }

  /**
   * Uploads a new shop hero image from the Profile/Edit Shop flow, then
   * PATCHes the URL onto the row and refreshes the local shops list.
   * Emits progress via [_vendorUploadPercent] and errors via [_vendorUploadError]
   * so existing UI hooks can render them without a new state channel.
   */
  fun updateShopImage(shopId: String, uri: Uri) {
    val token = supabaseAuthService.currentAccessToken
    viewModelScope.launch {
      _vendorUploadPercent.value = 0
      _vendorUploadState.value = VendorUploadState.UPLOADING_PHOTO
      val bytes = getBytesFromUri(uri)
      if (bytes == null) {
        _vendorUploadError.value = "Couldn't read the photo from your gallery. Pick it again."
        _vendorUploadState.value = VendorUploadState.IDLE
        return@launch
      }
      // Cache-buster on the filename so the CDN and Coil can't serve a stale
      // copy when the vendor uploads a replacement for the same shop.
      val objectName = "${shopId}_shop_${System.currentTimeMillis()}.jpg"
      supabaseGroceryRepo.uploadImage(
        "shop-images", objectName, bytes, token
      ) { pct -> _vendorUploadPercent.value = pct }
        .onSuccess { newUrl ->
          supabaseGroceryRepo.updateShopFields(shopId, org.json.JSONObject().put("image_url", newUrl), token)
            .onSuccess {
              _shops.update { list -> list.map { if (it.id == shopId) it.copy(imageUrl = newUrl) else it } }
            }
            .onFailure {
              _vendorUploadError.value = "Photo uploaded but shop update failed: ${it.message}"
              android.util.Log.w("BreakQ", "Shop image save after upload failed", it)
            }
          _vendorUploadPercent.value = 100
          _vendorUploadState.value = VendorUploadState.IDLE
        }
        .onFailure {
          _vendorUploadError.value = "Shop photo didn't upload: ${it.message}"
          _vendorUploadState.value = VendorUploadState.IDLE
          android.util.Log.w("BreakQ", "Shop image upload from profile failed", it)
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
      // 1. Upload the shop photo (required) and business proof (optional) before
      // creating the shop row, so the row is written once with its final URLs.
      // The shop hero photo goes to the public `shop-images` bucket (customers
      // need to render it without a token); the business proof stays in the
      // private `shop-documents` bucket (only admins should see it).
      var shopImageUrl: String? = null
      var proofUrl: String? = null

      shopPhotoUri?.let { uri ->
        val bytes = getBytesFromUri(uri)
        if (bytes == null) {
          _vendorUploadError.value = "Couldn't read the shop photo from your gallery. Pick it again."
        } else {
          supabaseGroceryRepo.uploadImage(
            "shop-images", "${shopId}_shop.jpg", bytes, token
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

  // ---- Delivery addresses ---------------------------------------------------

  private suspend fun refreshAddresses(userId: String) {
    supabaseGroceryRepo.fetchAddresses(userId, supabaseAuthService.currentAccessToken)
      .onSuccess {
        // A reply that lands after logout / account switch belongs to someone else.
        if (supabaseAuthService.currentUserId != userId) return@onSuccess
        _addresses.value = it
        refreshShopDistances()
      }
      .onFailure {
        if (supabaseAuthService.currentUserId == userId && _addresses.value.isEmpty()) {
          _addressError.value = "Couldn't load your saved addresses."
        }
      }
  }

  fun loadAddresses() {
    val userId = supabaseAuthService.currentUserId ?: return
    viewModelScope.launch {
      _addressesLoading.value = true
      supabaseGroceryRepo.fetchAddresses(userId, supabaseAuthService.currentAccessToken)
        .onSuccess {
          if (supabaseAuthService.currentUserId != userId) return@onSuccess
          _addresses.value = it
          _addressError.value = null
          refreshShopDistances()
        }
        .onFailure { err ->
          _addressError.value = "Couldn't load your addresses. ${err.localizedMessage.orEmpty()}".trim()
        }
      _addressesLoading.value = false
    }
  }

  /** Insert or update, then promote to default unless the user has one already. */
  fun saveAddress(
    address: CustomerAddress,
    makeDefault: Boolean = true,
    onSaved: () -> Unit = {}
  ) {
    val userId = supabaseAuthService.currentUserId ?: run {
      _addressError.value = "Session expired. Please log in again."
      return
    }
    viewModelScope.launch {
      _addressSaving.value = true
      _addressError.value = null
      supabaseGroceryRepo.upsertAddress(address, userId, supabaseAuthService.currentAccessToken)
        .onSuccess { saved ->
          val promote = makeDefault || _addresses.value.none { it.isDefault }
          if (promote && saved.id.isNotBlank()) {
            supabaseGroceryRepo.setDefaultAddress(saved.id, supabaseAuthService.currentAccessToken)
          }
          refreshAddresses(userId)
          onSaved()
        }
        .onFailure { err ->
          _addressError.value = "Couldn't save the address. ${err.localizedMessage.orEmpty()}".trim()
        }
      _addressSaving.value = false
    }
  }

  fun deleteAddress(addressId: String) {
    val userId = supabaseAuthService.currentUserId ?: return
    viewModelScope.launch {
      supabaseGroceryRepo.deleteAddress(addressId, userId, supabaseAuthService.currentAccessToken)
        .onSuccess { refreshAddresses(userId) }
        .onFailure { err ->
          _addressError.value = "Couldn't delete the address. ${err.localizedMessage.orEmpty()}".trim()
        }
    }
  }

  /** Pick the delivery address. Optimistic locally, authoritative via RPC. */
  fun selectAddress(addressId: String) {
    val userId = supabaseAuthService.currentUserId ?: return
    _addresses.update { list -> list.map { it.copy(isDefault = it.id == addressId) } }
    refreshShopDistances()
    viewModelScope.launch {
      supabaseGroceryRepo.setDefaultAddress(addressId, supabaseAuthService.currentAccessToken)
        .onFailure { err ->
          _addressError.value = "Couldn't switch address. ${err.localizedMessage.orEmpty()}".trim()
        }
      refreshAddresses(userId)
    }
  }

  fun clearAddressError() { _addressError.value = null }

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
        profileCompleted = true
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
    } catch (_: Exception) {
      _userNotice.value = "Couldn't open WhatsApp on this phone."
    }
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

  // ---- Vendor pickup verification -----------------------------------------

  private val _pickupState = MutableStateFlow(PickupState())
  val pickupState: StateFlow<PickupState> = _pickupState.asStateFlow()

  fun resetPickupState() {
    _pickupState.value = PickupState()
  }

  /** Look up an order in the calling vendor's shop by its short display number. */
  fun findVendorOrderByNumber(number: Int) {
    if (number <= 0) {
      _pickupState.update { it.copy(errorMessage = "Enter a valid order number.", isBusy = false) }
      return
    }
    _pickupState.update { it.copy(isBusy = true, errorMessage = null, lookup = null, completedOrderId = null) }
    viewModelScope.launch {
      supabaseGroceryRepo.findShopOrderByNumber(number, supabaseAuthService.currentAccessToken)
        .onSuccess { lookup ->
          if (lookup == null) {
            _pickupState.update { it.copy(isBusy = false, errorMessage = "No order #$number in your shop.") }
          } else {
            _pickupState.update { it.copy(isBusy = false, lookup = lookup) }
          }
        }
        .onFailure { err ->
          _pickupState.update { it.copy(isBusy = false, errorMessage = err.localizedMessage ?: "Lookup failed.") }
        }
    }
  }

  /** Complete a pickup via the customer's QR token or after a manual lookup. */
  fun completeOrderByPickupToken(token: String) {
    if (token.isBlank()) {
      _pickupState.update { it.copy(errorMessage = "Empty pickup code.", isBusy = false) }
      return
    }
    _pickupState.update { it.copy(isBusy = true, errorMessage = null) }
    viewModelScope.launch {
      supabaseGroceryRepo.completeOrderByPickupToken(token, supabaseAuthService.currentAccessToken)
        .onSuccess { completedId ->
          // Optimistic local update — reflect the completion until Realtime
          // catches up.
          _orders.update { list ->
            list.map { if (it.id == completedId) it.copy(status = OrderStatus.COMPLETED) else it }
          }
          _pickupState.update { it.copy(isBusy = false, completedOrderId = completedId, errorMessage = null) }
        }
        .onFailure { err ->
          _pickupState.update { it.copy(isBusy = false, errorMessage = err.localizedMessage ?: "Pickup failed.") }
        }
    }
  }

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

  private val _packingTimeMinutes = MutableStateFlow(15)
  val packingTimeMinutes: StateFlow<Int> = _packingTimeMinutes.asStateFlow()

  /**
   * Pull the vendor's own shop out of [_shops] and seed the operations
   * toggles from it. Called after login + after every _shops emission so
   * the UI never shows a default that disagrees with the DB.
   */
  private fun syncShopOperationsFromDb() {
    val shopId = _userProfile.value.shopId ?: return
    val shop = _shops.value.firstOrNull { it.id == shopId } ?: return
    _isStoreOpen.value = shop.isOpen
    _autoConfirmOrders.value = shop.autoConfirm
    _packingTimeMinutes.value = shop.packingTime.coerceIn(5, 180)
  }

  fun toggleStoreStatus() {
    val next = !_isStoreOpen.value
    _isStoreOpen.value = next
    persistShopOperations(isOpen = next)
  }

  fun toggleAutoConfirm() {
    val next = !_autoConfirmOrders.value
    _autoConfirmOrders.value = next
    persistShopOperations(autoConfirm = next)
  }

  fun updatePackingTime(minutes: Int) {
    val clamped = minutes.coerceIn(5, 180)
    _packingTimeMinutes.value = clamped
    persistShopOperations(packingTime = clamped)
  }

  /**
   * Persist any subset of the operations toggles to `public.shops` and mirror
   * the value into `_shops` so any customer/vendor screen that reads the shop
   * picks up the change without a round-trip.
   */
  private fun persistShopOperations(
    isOpen: Boolean? = null,
    autoConfirm: Boolean? = null,
    packingTime: Int? = null
  ) {
    val shopId = _userProfile.value.shopId ?: return
    val current = _shops.value.firstOrNull { it.id == shopId } ?: return
    updateShopDetails(
      shopId,
      current.copy(
        isOpen = isOpen ?: current.isOpen,
        autoConfirm = autoConfirm ?: current.autoConfirm,
        packingTime = packingTime ?: current.packingTime
      )
    )
  }

  fun updateOrderStatus(orderId: String, newStatus: OrderStatus) {
    // In-flight guard: the button was tapped, then tapped again before the
    // first PATCH resolved (or Realtime pushed a stale event that
    // recomposition re-fired the button for).
    if (orderId in _updatingOrderIds.value) return
    val existing = _orders.value.firstOrNull { it.id == orderId }
    // Nothing to do if the local status already matches (the transition
    // already happened via Realtime or a prior tap).
    if (existing != null && existing.status == newStatus) return

    val previousStatus = existing?.status

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

    _updatingOrderIds.update { it + orderId }
    viewModelScope.launch {
      supabaseGroceryRepo.updateOrderStatus(orderId, newStatus, supabaseAuthService.currentAccessToken)
        .onFailure { err ->
          // Roll the optimistic status back so the vendor sees the real state
          // instead of a lie. Realtime would eventually correct this, but only
          // if the app stayed open and connected.
          if (previousStatus != null) {
            _orders.update { list ->
              list.map { order ->
                if (order.id == orderId && order.status == newStatus) {
                  order.copy(
                    status = previousStatus,
                    timeline = buildOrderTimeline(
                      currentStatus = previousStatus,
                      orderDate = order.orderDate,
                      nowLabel = "Today, $currentTime"
                    )
                  )
                } else order
              }
            }
          }
          _userNotice.value = if (err is java.io.IOException) {
            "Couldn't reach BreakQ. Check your connection and try again."
          } else {
            err.message ?: "Couldn't update the order."
          }
          android.util.Log.w("BreakQ", "updateOrderStatus failed for $orderId -> ${newStatus.label}", err)
          // previousStatus can itself be stale (e.g. Realtime dropped the
          // customer's Cancel event while vendor was backgrounded, then vendor
          // tapped Confirm on a locally-still-PLACED row). Pull server truth so
          // the vendor's dashboard reflects reality, not the stale revert.
          refreshOrders()
        }
      _updatingOrderIds.update { it - orderId }
    }
  }

  fun cancelOrder(orderId: String) {
    val order = _orders.value.find { it.id == orderId } ?: return
    if (order.status == OrderStatus.COMPLETED || order.status == OrderStatus.CANCELLED) return
    updateOrderStatus(orderId, OrderStatus.CANCELLED)
    // Customer is notified via the server-side notify-order-status Edge
    // Function on the CANCELLED status transition — never fire a local push
    // here, otherwise whichever device called this (vendor or customer) would
    // pop up "Order cancelled" for the wrong party.
  }

  /** The shop cancels an order; the reason is saved on the order and shown to the customer. */
  fun cancelOrderAsVendor(orderId: String, reason: String) {
    if (orderId in _updatingOrderIds.value) return
    val order = _orders.value.find { it.id == orderId } ?: return
    if (order.status == OrderStatus.COMPLETED || order.status == OrderStatus.CANCELLED) return
    _updatingOrderIds.update { it + orderId }
    viewModelScope.launch {
      val result = supabaseGroceryRepo.vendorCancelOrder(orderId, reason, supabaseAuthService.currentAccessToken)
      _updatingOrderIds.update { it - orderId }
      result
        .onSuccess {
          _orders.update { list ->
            list.map {
              if (it.id == orderId) it.copy(status = OrderStatus.CANCELLED, cancelledBy = "vendor", cancelReason = reason) else it
            }
          }
        }
        .onFailure { err ->
          // Server not updated yet: fall back to the plain status change so cancelling still works.
          if (err is MissingServerFunctionException) {
            updateOrderStatus(orderId, OrderStatus.CANCELLED)
            return@onFailure
          }
          _userNotice.value = if (err is java.io.IOException) {
            "Couldn't cancel the order. Check your connection and try again."
          } else {
            err.message ?: "Couldn't cancel the order."
          }
          refreshOrders()
        }
    }
  }

  private fun restoreProductAfterFailedSave(previous: Product, err: Throwable) {
    _products.update { list -> list.map { if (it.id == previous.id) previous else it } }
    _userNotice.value = if (err is java.io.IOException) {
      "Couldn't save the change. Check your connection and try again."
    } else {
      err.message ?: "Couldn't save the change."
    }
  }

  fun updateProductStock(productId: String, inStock: Boolean) {
    val previous = _products.value.firstOrNull { it.id == productId } ?: return
    _products.update { list ->
      list.map {
        if (it.id == productId) it.copy(inStock = inStock) else it
      }
    }

    viewModelScope.launch {
      supabaseGroceryRepo.updateProductStock(productId, inStock, supabaseAuthService.currentAccessToken)
        .onFailure { restoreProductAfterFailedSave(previous, it) }
    }
  }

  fun updateProductPrice(productId: String, newPrice: Int) {
    val previous = _products.value.firstOrNull { it.id == productId } ?: return
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
        .onFailure { restoreProductAfterFailedSave(previous, it) }
    }
  }

  // null clears the count back to "untracked" (Call to Confirm); 0 means sold out.
  fun updateProductQty(productId: String, newQty: Int?) {
    val previous = _products.value.firstOrNull { it.id == productId } ?: return
    // Same rule as the database: 0 makes it unavailable, restocking from 0 makes it available.
    val inStock = when {
      newQty == 0 -> false
      previous.stockQty == 0 && newQty != null && newQty > 0 -> true
      else -> previous.inStock
    }
    _products.update { list ->
      list.map { if (it.id == productId) it.copy(stockQty = newQty, inStock = inStock) else it }
    }
    viewModelScope.launch {
      supabaseGroceryRepo.updateProductStockQty(productId, newQty, supabaseAuthService.currentAccessToken)
        .onFailure { restoreProductAfterFailedSave(previous, it) }
    }
  }

  fun selectShop(shopId: String?) {
    _activeShopId.value = shopId
    val shop = _shops.value.find { it.id == shopId }
    if (shop != null) {
      _userProfile.update { it.copy(activeStore = shop.name, activeStoreAddress = shop.address) }
    } else if (shopId == null) {
      _userProfile.update { it.copy(activeStore = "", activeStoreAddress = "") }
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
    fallbackImageUrl: String = "",
    forceInsertDespiteSoftMatch: Boolean = false
  ) {
    if (!canAddMoreProducts()) {
      val cap = currentTier()?.itemCap ?: 500
      _tierCapMessage.value = "You've listed $cap products — the maximum on the Free plan. Subscribe for an unlimited catalog."
      return
    }
    val productId = "p_${System.currentTimeMillis()}"
    val shopId = _userProfile.value.shopId ?: "s_bharat_kirana"

    // Stable identity for DB-level dedup. Only barcodes are globally stable;
    // manual entries pass NULL and fall to the app-side soft check below.
    val cleanBarcode = barcode.trim()
    val catalogRef = if (cleanBarcode.isNotBlank()) "barcode:$cleanBarcode" else null

    // App-side soft check for manual entries. Barcode dupes are caught at
    // DB level so we don't check them here (avoids double-warning on race).
    if (catalogRef == null && !forceInsertDespiteSoftMatch) {
      val existing = findLocalIdentityMatch(shopId, name, "Store Item", unit)
      if (existing != null) {
        _duplicateAlert.value = DuplicateAlert(
          existing = existing,
          severity = DuplicateAlert.Severity.Soft,
          source = DuplicateAlert.Source.ManualIdentity
        )
        return
      }
    }

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
        barcode = cleanBarcode,
        catalogRef = catalogRef
      )

      // 3. Sync to Supabase & Local
      _products.update { listOf(newProd) + it }
      val insertResult = supabaseGroceryRepo.addProduct(newProd, supabaseAuthService.currentAccessToken)
      _isLoading.value = false

      // 4. Handle the outcome. DB dedup rejection is a first-class case: the
      // repo throws DuplicateProductException, we surface a hard dialog with
      // the existing row (looked up in the local cache by catalogRef).
      val dupErr = insertResult.exceptionOrNull() as? DuplicateProductException
      if (dupErr != null) {
        _products.update { list -> list.filterNot { it.id == productId } }
        val existing = dupErr.catalogRef?.let { ref ->
          _products.value.firstOrNull { it.shopId == dupErr.shopId && it.catalogRef == ref }
        }
        if (existing != null) {
          _duplicateAlert.value = DuplicateAlert(
            existing = existing,
            severity = DuplicateAlert.Severity.Hard,
            source = if (dupErr.catalogRef?.startsWith("barcode:") == true) DuplicateAlert.Source.Barcode
                    else DuplicateAlert.Source.CatalogSelect
          )
        } else {
          _productUploadMessage.value = "This product is already in your inventory."
        }
        return@launch
      }

      // 5. Surface upload outcome so AddProductScreen can toast the vendor.
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

      // 6. Fire the success signal only on a real DB save so the caller can
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

  /** Local identity matcher used for the soft "possible duplicate" flow. */
  private fun findLocalIdentityMatch(
    shopId: String,
    name: String,
    brand: String,
    unit: String
  ): Product? {
    val n = name.trim().lowercase()
    val b = brand.trim().lowercase()
    val u = unit.replace(Regex("\\s+"), "").lowercase()
    if (n.isBlank()) return null
    return _products.value.firstOrNull { p ->
      p.shopId == shopId &&
        p.name.trim().lowercase() == n &&
        p.brand.trim().lowercase() == b &&
        p.unit.replace(Regex("\\s+"), "").lowercase() == u
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
    val previous = _products.value.firstOrNull { it.id == productId } ?: return
    _products.update { list -> list.filter { it.id != productId } }
    viewModelScope.launch {
      supabaseGroceryRepo.deleteProduct(productId, supabaseAuthService.currentAccessToken)
        .onFailure { err ->
          _products.update { list -> if (list.any { it.id == productId }) list else listOf(previous) + list }
          _userNotice.value = if (err is java.io.IOException) {
            "Couldn't delete the product. Check your connection and try again."
          } else {
            err.message ?: "Couldn't delete the product."
          }
        }
    }
  }

  // Rebuilt from today's catalog: order lines are price snapshots with no real shop attached.
  fun reorder(order: Order) {
    val catalog = _products.value
    if (catalog.isEmpty()) {
      _userNotice.value = "Couldn't check what's in stock right now. Pull down on Home to refresh, then try again."
      return
    }
    val shop = _shops.value.firstOrNull { it.id == order.shopId }
    if (shop == null) {
      _userNotice.value = "${order.storeName.ifBlank { "This shop" }} isn't on BreakQ right now, so this order can't be repeated."
      return
    }
    if (!shop.isOpen) {
      _userNotice.value = "${shop.name} isn't taking orders right now."
      return
    }

    val rebuilt = mutableListOf<CartItem>()
    var leftOut = 0
    var reduced = false
    order.items.forEach { line ->
      val product = catalog.firstOrNull { it.id == line.product.id && it.shopId == shop.id }
      if (product == null || !product.inStock || product.stockQty == 0) { leftOut++; return@forEach }
      val weight = product.weightOptions.firstOrNull { it.label == line.selectedWeight.label }
        ?: if (product.weightOptions.isEmpty()) WeightOption(product.unit, product.currentPrice) else null
      if (weight == null) { leftOut++; return@forEach }
      val alreadyAdded = rebuilt.filter { it.product.id == product.id }.sumOf { it.quantity }
      val available = product.stockQty?.let { it - alreadyAdded } ?: line.quantity
      val qty = minOf(line.quantity, available)
      if (qty <= 0) { leftOut++; return@forEach }
      if (qty < line.quantity) reduced = true
      rebuilt += CartItem(product, weight, qty)
    }

    if (rebuilt.isEmpty()) {
      _userNotice.value = "None of the items from ${order.displayNumber} are available right now."
      return
    }
    _cartItems.value = rebuilt
    _activeShopId.value = shop.id
    persistCart()
    _userNotice.value = when {
      leftOut > 0 -> "$leftOut item${if (leftOut == 1) "" else "s"} from ${order.displayNumber} ${if (leftOut == 1) "isn't" else "aren't"} available and ${if (leftOut == 1) "was" else "were"} left out. Prices are today's prices."
      reduced -> "Some quantities were lowered to what the shop has in stock. Prices are today's prices."
      else -> "Items added at today's prices."
    }
    navigateTo(AppScreen.Cart)
  }

  /**
   * On-demand line-item fetch. OrderDetailsScreen fires this when it opens an
   * order whose items list is empty (e.g. the ORDER_ITEMS_MIGRATION.sql fallback
   * ran and stripped the embed, or the customer opened a historical order that
   * predates the local placeOrder path). Populates in place so the tracker
   * shows product images, quantities and per-line totals.
   */
  fun hydrateOrderItems(orderId: String) {
    val existing = _orders.value.firstOrNull { it.id == orderId } ?: return
    if (existing.items.isNotEmpty()) return
    viewModelScope.launch {
      supabaseGroceryRepo.fetchOrderItems(orderId, supabaseAuthService.currentAccessToken)
        .onSuccess { fetched ->
          if (fetched.isNotEmpty()) {
            _orders.update { list ->
              list.map { if (it.id == orderId) it.copy(items = fetched) else it }
            }
          }
        }
    }
  }

  /**
   * One-shot hydrate of every order in `_orders` whose items list is empty.
   * Called when the vendor opens their dashboard so their order cards show
   * accurate item counts + product breakdown instead of "0 items".
   */
  fun hydrateAllEmptyOrderItems() {
    val emptyIds = _orders.value.filter { it.items.isEmpty() }.map { it.id }
    if (emptyIds.isEmpty()) return
    viewModelScope.launch {
      emptyIds.forEach { orderId ->
        supabaseGroceryRepo.fetchOrderItems(orderId, supabaseAuthService.currentAccessToken)
          .onSuccess { fetched ->
            if (fetched.isNotEmpty()) {
              _orders.update { list ->
                list.map { if (it.id == orderId) it.copy(items = fetched) else it }
              }
            }
          }
      }
    }
  }

  fun rateShop(shopId: String, orderId: String, rating: Int, review: String, onDone: (Boolean) -> Unit = {}) {
    val customerId = supabaseAuthService.currentUserId ?: run { onDone(false); return }
    viewModelScope.launch {
      supabaseGroceryRepo.submitShopRating(
        shopId = shopId,
        orderId = orderId,
        customerId = customerId,
        rating = rating,
        review = review,
        accessToken = supabaseAuthService.currentAccessToken
      ).onFailure { err ->
        _userNotice.value = if (err is java.io.IOException) {
          "Couldn't send your rating. Check your connection and try again."
        } else {
          "Couldn't send your rating. Please try again."
        }
        onDone(false)
      }.onSuccess {
        onDone(true)
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
        // Task 5: also prepend to the ratings feed so if the vendor is on the
        // Reviews screen, the new review shows up without a manual refresh.
        // Feed only tracks a single shop at a time, so filter to that shop_id.
        _shopRatings.update { list ->
          listOf(
            ShopRating(
              id = "",
              orderId = orderId,
              rating = rating,
              review = review,
              createdAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
            )
          ) + list
        }
      }
    }
  }

  /**
   * Task 5: load the vendor-visible ratings feed for a shop. Called when the
   * vendor opens VendorReviewsScreen. Safe to call repeatedly — refreshes the
   * feed and clears the loading flag when done.
   */
  fun loadShopRatings(shopId: String) {
    if (shopId.isBlank()) return
    _shopRatingsLoading.value = true
    viewModelScope.launch {
      supabaseGroceryRepo.fetchShopRatings(shopId, supabaseAuthService.currentAccessToken)
        .onSuccess { list -> _shopRatings.value = list }
        .onFailure { err ->
          android.util.Log.w("BreakQ", "loadShopRatings failed for $shopId", err)
        }
      _shopRatingsLoading.value = false
    }
  }
}

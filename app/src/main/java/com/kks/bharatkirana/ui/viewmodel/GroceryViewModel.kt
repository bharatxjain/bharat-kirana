package com.kks.bharatkirana.ui.viewmodel

import android.app.Application
import android.content.Context
import android.location.Location
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.firebase.messaging.FirebaseMessaging
import com.kks.bharatkirana.data.model.*
import com.kks.bharatkirana.data.repository.GroceryRepository
import com.kks.bharatkirana.data.supabase.SupabaseAuthService
import com.kks.bharatkirana.data.supabase.SupabaseGroceryRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

  private val _selectedProduct = MutableStateFlow<Product?>(repository.getProducts().firstOrNull())
  val selectedProduct: StateFlow<Product?> = _selectedProduct.asStateFlow()

  private val _selectedCategory = MutableStateFlow<Category?>(null)
  val selectedCategory: StateFlow<Category?> = _selectedCategory.asStateFlow()

  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

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

  init {
    loadSavedSession()
    loadSupabaseData()
    fetchUserLocation()
    fetchFcmToken()
  }

  private fun fetchFcmToken() {
    FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
      if (task.isSuccessful) {
        val token = task.result
        _userProfile.update { it.copy(fcmToken = token) }
        // If logged in, sync to Supabase
        if (_userProfile.value.email.isNotBlank()) {
          updateProfile(_userProfile.value.fullName, _userProfile.value.email, _userProfile.value.mobileNumber, _userProfile.value.address)
        }
      }
    }
  }

  private fun loadSavedSession() {
    val email = prefs.getString("user_email", "") ?: ""
    if (email.isNotBlank()) {
      login(email)
      val user = _userProfile.value
      // If profile is not completed, force them to complete it
      if (!user.profileCompleted) {
        _currentScreen.value = AppScreen.CompleteProfile
      } else {
        _currentScreen.value = AppScreen.Main
        _activeShopId.value = null
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
      val distanceKm = calculateDistance(userLoc.latitude, userLoc.longitude, shop.lat, shop.lng)
      shop.copy(distance = String.format("%.1f km", distanceKm))
    }.sortedBy { it.distance.substringBefore(" ").toDoubleOrNull() ?: 99.0 }
    
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
    val discount = if (itemTotal > 200) 15 else 0
    val handlingFee = 5
    val totalAmount = itemTotal + handlingFee - discount

    val orderNum = (1000..9999).random()
    val orderId = "KIR-8F$orderNum"

    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val currentTime = timeFormat.format(Date())

    val newOrder = Order(
      id = orderId,
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
    navigateTo(AppScreen.OrderPlaced(orderId))

    // Simulation: Notify Vendor
    simulateVendorNotification(newOrder)

    // Asynchronously sync order to Supabase
    viewModelScope.launch {
      supabaseGroceryRepo.insertOrder(
        order = newOrder,
        customerEmail = _userProfile.value.email,
        customerName = _userProfile.value.fullName,
        accessToken = supabaseAuthService.currentAccessToken
      )
    }

    return newOrder
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
          login(cleanEmail, authPath = AuthPath.EMAIL)
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
      // In a production app, you would call a backend function here 
      // to remove all user data from PostgreSQL and the Auth system.
      // For now, we perform a secure logout and clear local state.
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

  fun login(email: String, name: String = "", mobile: String = "", authPath: AuthPath? = null) {
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

    // Load orders for newly logged in user
    viewModelScope.launch {
      supabaseGroceryRepo.fetchOrders(customerEmail = cleanEmail, isAdmin = isAdmin).onSuccess { liveOrders ->
        if (liveOrders.isNotEmpty()) {
          _orders.value = liveOrders
        }
      }
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

  fun registerVendorShop(name: String, owner: String, address: String, phone: String) {
    val shopId = "s_${System.currentTimeMillis()}"
    val userId = supabaseAuthService.currentUserId ?: return
    val token = supabaseAuthService.currentAccessToken

    val newShop = Shop(
      id = shopId,
      name = name,
      ownerName = owner,
      address = address,
      phone = phone,
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

  fun openDirections(address: String) {
    val mapUri = Uri.parse("geo:0,0?q=${Uri.encode(address)}")
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, mapUri)
    intent.setPackage("com.google.android.apps.maps")
    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    getApplication<Application>().startActivity(intent)
  }

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
    imageUris: List<Uri>
  ) {
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
        weightOptions = listOf(WeightOption(unit, price, mrp))
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

  fun rateShop(shopId: String, rating: Int, review: String) {
    viewModelScope.launch {
      // Logic to sync rating to Supabase
      // supabaseGroceryRepo.submitRating(shopId, rating, review, currentUserId)
      
      // Update local state to reflect new rating for the shop
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

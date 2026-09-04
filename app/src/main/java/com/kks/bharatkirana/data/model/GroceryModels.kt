package com.kks.bharatkirana.data.model

import androidx.annotation.DrawableRes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Signals to the vendor that the product they're trying to list is already in
 * their shop's inventory. Severity distinguishes the confidence:
 *   - Hard: definitive match (barcode / catalog identity). App refuses to insert.
 *   - Soft: heuristic name/brand/unit match. Vendor can override.
 */
data class DuplicateAlert(
  val existing: Product,
  val severity: Severity,
  val source: Source
) {
  enum class Severity { Hard, Soft }
  enum class Source { Barcode, CatalogSelect, ManualIdentity }
}

/**
 * Fires when addToCart is called with a product whose shopId differs from
 * whatever's already in the cart. The UI renders a confirmation dialog:
 * customers can either wipe the cart to start fresh or cancel the add.
 */
data class CartShopSwitchAlert(
  val currentShopId: String,
  val currentShopName: String,
  val newShopName: String,
  val pendingProduct: Product,
  val pendingWeight: WeightOption,
  val pendingQty: Int
)

data class Category(
  val id: String,
  val name: String,
  val iconName: String,
  val colorHex: Long = 0xFFEDE9FE,
  @DrawableRes val fallbackDrawableRes: Int? = null,
  val itemsCount: Int = 24
)

data class WeightOption(
  val label: String, // e.g. "5 kg", "1 kg", "10 kg"
  val price: Int,    // e.g. 280
  val originalPrice: Int = 0,
  val discountLabel: String = ""
)

data class ProductFeature(
  val iconType: String, // "wheat", "clean", "safety", "fresh"
  val title: String
)

enum class VendorStatus(val label: String) {
  PENDING("Pending Approval"),
  APPROVED("Approved"),
  REJECTED("Rejected"),
  SUSPENDED("Suspended")
}

data class Shop(
  val id: String,
  val name: String,
  val ownerName: String,
  val address: String,
  val distance: String = "---",
  val lat: Double = 0.0,
  val lng: Double = 0.0,
  // 0f with ratingCount 0 means "no ratings yet" — never fake a score for a shop
  // nobody has actually reviewed.
  val rating: Float = 0f,
  val ratingCount: Int = 0,
  // Vendor-declared tenure. 0 = not provided.
  val yearsInBusiness: Int = 0,
  val deliveryTime: String = "20-30 mins",
  val imageUrl: String = "",
  @DrawableRes val localImageRes: Int? = null,
  val isPartner: Boolean = true,
  val phone: String = "",
  val primaryCategory: String = "Grocery",
  val status: VendorStatus = VendorStatus.APPROVED,
  val isOpen: Boolean = true, // maps to shops.accepting_orders — manual on/off
  val autoConfirm: Boolean = true,
  val packingTime: Int = 15,
  val openTime: String = "08:00", // 24-hour "HH:mm"
  val closeTime: String = "21:00"
) {
  val hasRatings: Boolean get() = ratingCount > 0
}

/**
 * Returns true only if the shop is manually open (accepting_orders) AND the current
 * wall-clock time falls within [openTime, closeTime]. Uses "HH:mm" string compare,
 * which is safe because both sides are zero-padded 24-hour times.
 */
fun Shop.isCurrentlyOpen(): Boolean {
  if (!isOpen) return false
  val now = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
  return now >= openTime && now < closeTime
}

data class Product(
  val id: String,
  val name: String,
  val brand: String,
  val categoryId: String,
  val shopId: String = "default_shop",
  val currentPrice: Int,
  val originalPrice: Int,
  val discountPercent: Int = 0,
  val unit: String, 
  val subtitle: String = "",
  val weightOptions: List<WeightOption> = emptyList(),
  @DrawableRes val localImageRes: Int? = null,
  val imageUrl: String = "",
  val imageUrls: List<String> = emptyList(),
  val description: String = "",
  val features: List<ProductFeature> = emptyList(),
  val rating: Float = 4.8f,
  val reviewCount: Int = 128,
  // null = vendor never declared a count (untracked). 0 = genuinely sold out.
  // The distinction is what separates "Call to Confirm" from "Out of Stock".
  val stockQty: Int? = null,
  val inStock: Boolean = true,
  val isPopular: Boolean = false,
  val isDailyEssential: Boolean = false,
  val barcode: String = "",
  // Stable identity for duplicate protection: "barcode:<code>" when known,
  // NULL for manual/heuristic entries. Enforced unique per shop by the
  // idx_products_shop_catalog_ref partial index (see FIX_PRODUCT_DUPLICATES.sql).
  val catalogRef: String? = null
) {
  val stockStatus: String
    get() = when {
      !inStock -> "Out of Stock"
      stockQty == null -> "Call to Confirm"
      stockQty <= 0 -> "Out of Stock"
      stockQty <= 5 -> "Low Stock"
      else -> "In Stock"
    }
}

data class CartItem(
  val product: Product,
  val selectedWeight: WeightOption,
  val quantity: Int
) {
  val totalPrice: Int
    get() = selectedWeight.price * quantity
}

enum class UserRole(val label: String) {
  CUSTOMER("Customer"),
  VENDOR("Shop Owner"),
  ADMIN("Platform Admin"),
  SUPER_ADMIN("Super Admin")
}

enum class AuthPath {
  GOOGLE, EMAIL
}

enum class UpdateStatus { NONE, OPTIONAL, FORCED }

data class PromoCode(
  val code: String,
  val description: String = "",
  val discountPercent: Int = 0,
  val discountFlatRupees: Int = 0,
  val minOrderAmount: Int = 0,
  val maxDiscountRupees: Int? = null
) {
  fun computeDiscount(orderAmount: Int): Int {
    if (orderAmount < minOrderAmount) return 0
    val raw = if (discountPercent > 0) (orderAmount * discountPercent / 100) else discountFlatRupees
    val cap = maxDiscountRupees
    return if (cap != null && cap > 0) minOf(raw, cap) else raw
  }
}

data class UserProfile(
  val fullName: String = "",
  val email: String = "",
  val mobileNumber: String = "",
  val address: String = "",
  val isLoyaltyMember: Boolean = false,
  val loyaltyPoints: Int = 0,
  val walletBalance: Int = 0,
  val activeStore: String = "",
  val activeStoreAddress: String = "",
  val shopId: String? = null, // For vendors
  val profileCompleted: Boolean = false,
  val phoneVerified: Boolean = false,
  val authPath: AuthPath? = null,
  val fcmToken: String? = null,
  // Populated from Supabase `user_profiles.role` after login. When null, we fall back
  // to a `.env`-driven whitelist (BuildConfig.SUPER_ADMIN_EMAIL / ADMIN_EMAILS). Both
  // are strictly UI hints — real authorization is enforced by Supabase RLS.
  val serverRole: UserRole? = null
) {
  // Server role is now the sole source of truth. The .env whitelist fallback
  // was a bootstrap for the pre-RLS era and started firing false positives once
  // the DB row loaded slowly — promoting anyone whose email matched to admin
  // even after the DB said they were a customer. Promote via Supabase Table
  // Editor now: set profiles.role = 'admin' for that user.
  val isSuperAdmin: Boolean
    get() = serverRole == UserRole.SUPER_ADMIN

  val isAdmin: Boolean
    get() = isSuperAdmin || serverRole == UserRole.ADMIN

  val isVendor: Boolean
    // Trust serverRole exclusively. A stale local shopId (from an aborted
    // vendor registration or an older account state) previously flipped this
    // to true for real customers, routing them to VendorDashboard where the
    // shop lookup fails and they saw a "white loading screen" after login.
    get() = serverRole == UserRole.VENDOR

  val role: UserRole
    get() = when {
      isSuperAdmin -> UserRole.SUPER_ADMIN
      isAdmin -> UserRole.ADMIN
      isVendor -> UserRole.VENDOR
      else -> UserRole.CUSTOMER
    }
}

/**
 * A structured delivery address from `public.customer_addresses`. Replaces the
 * legacy single-string [UserProfile.address], which is left in place so the
 * existing profile-sync and vendor flows keep working.
 */
data class CustomerAddress(
  val id: String = "",
  val label: String = "Home",
  val houseNo: String = "",
  val building: String = "",
  val floor: String = "",
  val areaStreet: String = "",
  val landmark: String = "",
  val city: String = "",
  val state: String = "",
  val pincode: String = "",
  val lat: Double? = null,
  val lng: Double? = null,
  val isForSelf: Boolean = true,
  val recipientName: String = "",
  val recipientPhone: String = "",
  val isDefault: Boolean = false
) {
  val formatted: String
    get() = listOf(houseNo, building, floor, areaStreet, landmark, city, state, pincode)
      .filter { it.isNotBlank() }
      .joinToString(", ")

  val shortLine: String
    get() = listOf(areaStreet, city, pincode).filter { it.isNotBlank() }.joinToString(", ")

  val isComplete: Boolean
    get() = houseNo.isNotBlank() && areaStreet.isNotBlank() && city.isNotBlank()
}

enum class OrderStatus(val label: String, val stepIndex: Int) {
  PLACED("Order Placed", 0),
  CONFIRMED("Order Confirmed", 1),
  PREPARING("Preparing", 2),
  READY_FOR_PICKUP("Ready for Pickup", 3),
  COMPLETED("Completed", 4),
  CANCELLED("Cancelled", 5)
}

// Round 6.1: single format used for both the profile QR (no order ref) and the
// order-pickup QR (with 4-digit ref). Vendor's scanner sees the same schema.
fun buildCustomerQrPayload(userEmail: String, orderId: String? = null): String {
  val user = userEmail.trim().lowercase().ifBlank { "unknown" }
  val base = "BREAKQ:USER:$user"
  val ref = orderId?.takeLast(4)?.uppercase()
  return if (ref.isNullOrBlank()) base else "$base:REF:$ref"
}

data class OrderTimelineItem(
  val status: OrderStatus,
  val time: String,
  val isCompleted: Boolean,
  val isCurrent: Boolean = false
)

// Round 6: single source of truth for how the 5-step customer timeline looks
// at any given backend status. Called both when the customer inserts an order
// (initial = PLACED) and when the Realtime UPDATE arrives after a vendor action.
fun buildOrderTimeline(currentStatus: OrderStatus, orderDate: String, nowLabel: String = orderDate): List<OrderTimelineItem> {
  val progressSteps = listOf(
    OrderStatus.PLACED,
    OrderStatus.CONFIRMED,
    OrderStatus.PREPARING,
    OrderStatus.READY_FOR_PICKUP,
    OrderStatus.COMPLETED
  )
  // Terminal cancel state — collapse the timeline to Placed + a Cancelled marker
  // so the customer sees "you ordered, we stopped" without a ghost progress bar.
  if (currentStatus == OrderStatus.CANCELLED) {
    return listOf(
      OrderTimelineItem(OrderStatus.PLACED, orderDate, isCompleted = true, isCurrent = false),
      OrderTimelineItem(OrderStatus.CANCELLED, nowLabel, isCompleted = true, isCurrent = true)
    )
  }
  return progressSteps.map { step ->
    val defaultTimeText = when (step) {
      OrderStatus.PLACED -> orderDate
      OrderStatus.CONFIRMED -> "Waiting for shop"
      OrderStatus.PREPARING -> "Not started"
      OrderStatus.READY_FOR_PICKUP -> "Not ready yet"
      OrderStatus.COMPLETED -> "Pending pickup"
      else -> ""
    }
    when {
      step.stepIndex < currentStatus.stepIndex -> OrderTimelineItem(step, defaultTimeText, isCompleted = true)
      step == currentStatus -> OrderTimelineItem(step, nowLabel, isCompleted = true, isCurrent = true)
      else -> OrderTimelineItem(step, defaultTimeText, isCompleted = false)
    }
  }
}

data class Order(
  val id: String, // e.g. "KIR-7F42"
  val shopId: String = "default_shop",
  val items: List<CartItem>,
  val totalAmount: Int,
  val orderDate: String, // e.g. "Today, 2:30 PM"
  val status: OrderStatus = OrderStatus.READY_FOR_PICKUP,
  val expectedPickupTime: String = "Today by 5:00 PM",
  val storeName: String = "BreakQ Store",
  val storeAddress: String = "Banjara Hills Rd 12, Hyderabad",
  val timeline: List<OrderTimelineItem> = emptyList(),
  val qrCodePayload: String = "",
  val backupCode: String = "123456",
  val customerName: String = "",
  val customerMobile: String = "",
  // ISO 8601 timestamp string from orders.created_at; used to compute the
  // "10 mins ago" style relative label the vendor sees on order cards.
  val createdAt: String = "",
  // Short human-friendly per-shop counter (e.g. 4827). Assigned by the
  // Postgres trigger on insert. Falls back to `id` for display when the
  // orders row predates the pickup migration.
  val orderNumber: Int? = null,
  // Opaque secret embedded in the customer's pickup QR. Only the vendor of
  // the matching shop can complete an order with this token.
  val pickupToken: String? = null
) {
  /** Human-friendly label the customer + vendor talk about. */
  val displayNumber: String
    get() = orderNumber?.let { "#$it" } ?: "#$id"
}

sealed class AppScreen {
  // Shown only while a persisted Supabase session is being refreshed on cold
  // start, so a logged-in user never sees the onboarding pager again.
  data object Restoring : AppScreen()
  data object Onboarding : AppScreen()
  data object Auth : AppScreen()
  data class SignupSplash(val userEmail: String, val role: String = "Customer") : AppScreen()
  data object CompleteProfile : AppScreen()
  data object Main : AppScreen()
  data object RoleSelection : AppScreen()
  data object CustomerOnboarding : AppScreen()
  data class ProductDetail(val productId: String) : AppScreen()
  data class ShopDetail(val shopId: String) : AppScreen()
  data object Cart : AppScreen()
  data class OrderPlaced(val orderId: String) : AppScreen()
  data class OrderDetails(val orderId: String) : AppScreen()
  data class VendorOrderDetails(val orderId: String) : AppScreen()
  data object AdminDashboard : AppScreen()
  data object PrivacyPolicy : AppScreen()
  data object TermsOfService : AppScreen()
  data object StoreInfo : AppScreen()
  data object NearbyShops : AppScreen()
  data object VendorRegistration : AppScreen()
  data object VendorDashboard : AppScreen()
  data object VendorProfile : AppScreen()
  data object VendorReviews : AppScreen()
  data object AddProduct : AppScreen()
  data object BarcodeScanner : AppScreen()
  data object Subscription : AppScreen()
  data class ShopsForProduct(val productName: String) : AppScreen()
  data class ResetPassword(val accessToken: String) : AppScreen()
  data object Notifications : AppScreen()
  data object Wishlist : AppScreen()
  data object VendorPickup : AppScreen()
  data object OrderHistory : AppScreen()
  data object EditProfile : AppScreen()
  data object SavedAddresses : AppScreen()
  data object SelectLocation : AppScreen()
  data class AddEditAddress(val addressId: String? = null) : AppScreen()
  data object NotificationPreferences : AppScreen()
  data object KiranaWallet : AppScreen()
  data object HelpSupport : AppScreen()
  data object AboutUs : AppScreen()
  data object AccountActions : AppScreen()
}

/** Lightweight projection returned by the vendor "find order by number" RPC. */
data class VendorOrderLookup(
  val orderId: String,
  val orderNumber: Int?,
  val status: String,
  val customerName: String,
  val totalAmount: Int,
  val pickupToken: String?
)

/** UI state for the vendor pickup screen (scan QR / find by number). */
data class PickupState(
  val isBusy: Boolean = false,
  val errorMessage: String? = null,
  val lookup: VendorOrderLookup? = null,
  val completedOrderId: String? = null
)

/**
 * An in-app notification row, mirrored from the `notifications` table (also written by
 * the `notify-order-status` Edge Function when it sends a matching push — see SETUP_STEPS.md).
 */
data class AppNotification(
  val id: String,
  val title: String,
  val message: String,
  val isRead: Boolean = false,
  val orderId: String? = null,
  val createdAt: String = ""
)

/**
 * A single row in the search-suggestions dropdown. Two variants: a product
 * (tap → shows shops that sell it) and a shop (tap → opens the shop directly).
 */
sealed class SearchSuggestion {
  data class ProductSuggestion(
    val name: String,
    val brand: String,
    val categoryId: String,
    val imageUrl: String = ""
  ) : SearchSuggestion()

  data class ShopSuggestion(val shop: Shop) : SearchSuggestion()
}

// Monetization catalog. Mirrors subscription_tiers table row-for-row.
// Round 8: the paywall is feature-based (analytics / placement / branding), not
// catalog-size based — itemCap stays only as a spam backstop.
data class SubscriptionTier(
  val id: String,           // "free" | "founding" | "advance" | "pro"
  val displayName: String,
  val priceRupees: Int,
  val itemCap: Int,         // -1 = unlimited
  val priorityRank: Int,
  val canPromote: Boolean,
  val promoteDailyCapRupees: Int,
  val commissionPercent: Double,
  val features: List<String>,
  val tagline: String = "",
  val hasBasicAnalytics: Boolean = false,
  val hasFullAnalytics: Boolean = false,
  val hasPriorityPlacement: Boolean = false,
  val hasTopBoost: Boolean = false,
  val hideBreakqBranding: Boolean = false,
  val hasWhatsappAlerts: Boolean = false,
  val hasMultiStaff: Boolean = false,
  val hasCompetitorPricing: Boolean = false,
  val isLimitedTime: Boolean = false,
  val offerEndsAt: String? = null
)

// Vendor's currently-active subscription row (one per shop).
data class VendorSubscription(
  val id: String,
  val shopId: String,
  val tierId: String,
  val status: String,       // "active" | "expired" | "cancelled" | "pending_payment"
  val startedAt: String,
  val expiresAt: String?,
  val amountPaidRupees: Int,
  val commissionLockedAtPercent: Double
)

// Round 8: what the vendor sees on the Insights tab. Empty/zeroed for Free tier.
data class VendorAnalytics(
  val shopViews: Int = 0,
  val productViews: Int = 0,
  val ordersToday: Int = 0,
  val topSearchedProducts: List<Pair<String, Int>> = emptyList(),
  val repeatCustomerPercent: Int = 0
)

enum class MainTab(val title: String, val testTag: String) {
  HOME("Home", "tab_home"),
  CATEGORIES("Categories", "tab_categories"),
  SEARCH("Search", "tab_search"),
  PROFILE("Profile", "tab_profile")
}

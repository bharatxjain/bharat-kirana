package com.kks.bharatkirana.data.model

import androidx.annotation.DrawableRes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
  val rating: Float = 4.5f,
  val ratingCount: Int = 0,
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
)

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
  val stockQty: Int = 10,
  val inStock: Boolean = true,
  val isPopular: Boolean = false,
  val isDailyEssential: Boolean = false,
  val barcode: String = ""
) {
  val stockStatus: String
    get() = when {
      !inStock || stockQty <= 0 -> "Out of Stock"
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
  val activeStore: String = "Bharat Kirana Store, Hyderabad",
  val activeStoreAddress: String = "Bharat Kirana Store, Banjara Hills Rd 12, Hyderabad",
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
  val isSuperAdmin: Boolean
    get() = serverRole == UserRole.SUPER_ADMIN ||
      (serverRole == null && email.isNotBlank() &&
        com.kks.bharatkirana.BuildConfig.SUPER_ADMIN_EMAIL.isNotBlank() &&
        email.trim().equals(com.kks.bharatkirana.BuildConfig.SUPER_ADMIN_EMAIL, ignoreCase = true))

  val isAdmin: Boolean
    get() = isSuperAdmin ||
      serverRole == UserRole.ADMIN ||
      (serverRole == null && email.isNotBlank() &&
        com.kks.bharatkirana.BuildConfig.ADMIN_EMAILS
          .split(",")
          .map { it.trim().lowercase() }
          .any { it.isNotBlank() && it == email.trim().lowercase() })

  val isVendor: Boolean
    get() = shopId != null || serverRole == UserRole.VENDOR

  val role: UserRole
    get() = when {
      isSuperAdmin -> UserRole.SUPER_ADMIN
      isAdmin -> UserRole.ADMIN
      isVendor -> UserRole.VENDOR
      else -> UserRole.CUSTOMER
    }
}

enum class OrderStatus(val label: String, val stepIndex: Int) {
  PLACED("Order Placed", 0),
  PREPARING("Preparing", 1),
  READY_FOR_PICKUP("Ready for Pickup", 2),
  COMPLETED("Completed", 3),
  CANCELLED("Cancelled", 4)
}

data class OrderTimelineItem(
  val status: OrderStatus,
  val time: String,
  val isCompleted: Boolean,
  val isCurrent: Boolean = false
)

data class Order(
  val id: String, // e.g. "KIR-7F42"
  val shopId: String = "default_shop",
  val items: List<CartItem>,
  val totalAmount: Int,
  val orderDate: String, // e.g. "Today, 2:30 PM"
  val status: OrderStatus = OrderStatus.READY_FOR_PICKUP,
  val expectedPickupTime: String = "Today by 5:00 PM",
  val storeName: String = "Bharat Kirana Store",
  val storeAddress: String = "Banjara Hills Rd 12, Hyderabad",
  val timeline: List<OrderTimelineItem> = emptyList(),
  val qrCodePayload: String = "",
  val backupCode: String = "123456"
)

sealed class AppScreen {
  data object Onboarding : AppScreen()
  data object Auth : AppScreen()
  data class SignupSplash(val userEmail: String, val role: String = "Customer") : AppScreen()
  data object CompleteProfile : AppScreen()
  data object Main : AppScreen()
  data object RoleSelection : AppScreen()
  data object CustomerOnboarding : AppScreen()
  data class ProductDetail(val productId: String) : AppScreen()
  data object Cart : AppScreen()
  data class OrderPlaced(val orderId: String) : AppScreen()
  data class OrderDetails(val orderId: String) : AppScreen()
  data object AdminDashboard : AppScreen()
  data object PrivacyPolicy : AppScreen()
  data object TermsOfService : AppScreen()
  data object StoreInfo : AppScreen()
  data object NearbyShops : AppScreen()
  data object VendorRegistration : AppScreen()
  data object VendorDashboard : AppScreen()
  data object AddProduct : AppScreen()
  data object BarcodeScanner : AppScreen()
  data object Subscription : AppScreen()
  data class ShopsForProduct(val productName: String) : AppScreen()
  data class ResetPassword(val accessToken: String) : AppScreen()
}

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

// Round 5: monetization catalog. Mirrors subscription_tiers table row-for-row.
data class SubscriptionTier(
  val id: String,           // "free" | "starter" | "standard" | "pro"
  val displayName: String,
  val priceRupees: Int,
  val itemCap: Int,         // -1 = unlimited
  val priorityRank: Int,
  val canPromote: Boolean,
  val promoteDailyCapRupees: Int,
  val commissionPercent: Double,
  val features: List<String>
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

enum class MainTab(val title: String, val testTag: String) {
  HOME("Home", "tab_home"),
  CATEGORIES("Categories", "tab_categories"),
  SEARCH("Search", "tab_search"),
  ORDERS("Orders", "tab_orders"),
  PROFILE("Profile", "tab_profile")
}

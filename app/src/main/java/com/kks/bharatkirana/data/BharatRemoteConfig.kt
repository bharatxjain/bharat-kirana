package com.kks.bharatkirana.data

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings

/**
 * Thin wrapper around Firebase Remote Config with typed getters for our known keys.
 * Values are set from Firebase Console — see SETUP_STEPS.md §F.
 * Defaults below keep the app functional before the first fetch completes.
 */
object BharatRemoteConfig {

  object Keys {
    const val MIN_ORDER_FREE_HANDLING = "min_order_for_free_handling"
    const val HANDLING_FEE_RUPEES = "handling_fee_rupees"
    const val FREE_HANDLING_DISCOUNT = "free_handling_discount_rupees"
    const val DEFAULT_SHOP_RADIUS_KM = "default_shop_radius_km"
    const val MIN_SUPPORTED_VERSION_CODE = "min_supported_version_code"
    const val LATEST_VERSION_CODE = "latest_version_code"
    const val PROMO_BANNER_TEXT = "promo_banner_text"
    const val PROMO_BANNER_ENABLED = "promo_banner_enabled"
    const val MAINTENANCE_MODE = "maintenance_mode"
    const val SUPPORT_WHATSAPP_NUMBER = "support_whatsapp_number"
  }

  private val rc: FirebaseRemoteConfig by lazy {
    FirebaseRemoteConfig.getInstance().apply {
      val settings = FirebaseRemoteConfigSettings.Builder()
        .setMinimumFetchIntervalInSeconds(3600) // 1 hour cache in production
        .build()
      setConfigSettingsAsync(settings)
      setDefaultsAsync(
        mapOf(
          Keys.MIN_ORDER_FREE_HANDLING to 200L,
          Keys.HANDLING_FEE_RUPEES to 5L,
          Keys.FREE_HANDLING_DISCOUNT to 15L,
          Keys.DEFAULT_SHOP_RADIUS_KM to 5L,
          Keys.MIN_SUPPORTED_VERSION_CODE to 1L,
          Keys.LATEST_VERSION_CODE to 1L,
          Keys.PROMO_BANNER_TEXT to "Welcome to BreakQ! \uD83D\uDED2",
          Keys.PROMO_BANNER_ENABLED to true,
          Keys.MAINTENANCE_MODE to false,
          Keys.SUPPORT_WHATSAPP_NUMBER to ""
        )
      )
    }
  }

  fun refresh(onComplete: (Boolean) -> Unit = {}) {
    rc.fetchAndActivate()
      .addOnSuccessListener { activated -> onComplete(activated) }
      .addOnFailureListener { onComplete(false) }
  }

  fun minOrderForFreeHandling(): Int = rc.getLong(Keys.MIN_ORDER_FREE_HANDLING).toInt()
  fun handlingFeeRupees(): Int = rc.getLong(Keys.HANDLING_FEE_RUPEES).toInt()
  fun freeHandlingDiscount(): Int = rc.getLong(Keys.FREE_HANDLING_DISCOUNT).toInt()
  fun defaultShopRadiusKm(): Int = rc.getLong(Keys.DEFAULT_SHOP_RADIUS_KM).toInt()
  fun minSupportedVersionCode(): Int = rc.getLong(Keys.MIN_SUPPORTED_VERSION_CODE).toInt()
  fun latestVersionCode(): Int = rc.getLong(Keys.LATEST_VERSION_CODE).toInt()
  fun promoBannerText(): String = rc.getString(Keys.PROMO_BANNER_TEXT)
  fun promoBannerEnabled(): Boolean = rc.getBoolean(Keys.PROMO_BANNER_ENABLED)
  fun maintenanceMode(): Boolean = rc.getBoolean(Keys.MAINTENANCE_MODE)
  fun supportWhatsappNumber(): String = rc.getString(Keys.SUPPORT_WHATSAPP_NUMBER)
}

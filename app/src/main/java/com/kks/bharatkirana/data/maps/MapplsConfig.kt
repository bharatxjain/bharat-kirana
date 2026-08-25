package com.kks.bharatkirana.data.maps

import android.content.Context
import com.kks.bharatkirana.BuildConfig
import com.mappls.sdk.maps.Mappls
import com.mappls.sdk.services.account.MapplsAccountManager

/**
 * Values are injected from `.env` (or `.env.example` as fallback) by the Secrets
 * Gradle Plugin, same as SupabaseConfig. See .env.example for where to get free keys.
 */
object MapplsConfig {

  val isConfigured: Boolean
    get() = BuildConfig.MAPPLS_API_KEY.isNotBlank() && !BuildConfig.MAPPLS_API_KEY.startsWith("your_")

  private var initialized = false

  fun initialize(context: Context) {
    if (initialized || !isConfigured) return
    MapplsAccountManager.getInstance().apply {
      init(context.applicationContext)
      restAPIKey = BuildConfig.MAPPLS_REST_KEY
      mapSDKKey = BuildConfig.MAPPLS_API_KEY
      atlasClientId = BuildConfig.MAPPLS_ATLAS_CLIENT_ID
      atlasClientSecret = BuildConfig.MAPPLS_ATLAS_CLIENT_SECRET
    }
    Mappls.getInstance(context.applicationContext)
    initialized = true
  }
}

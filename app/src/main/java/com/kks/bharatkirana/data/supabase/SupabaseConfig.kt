package com.kks.bharatkirana.data.supabase

import com.kks.bharatkirana.BuildConfig

object SupabaseConfig {
  val PROJECT_URL: String
    get() = BuildConfig.SUPABASE_URL.ifBlank { "https://psanmbsimwxpperizsxe.supabase.co" }

  val API_KEY: String
    get() = BuildConfig.SUPABASE_ANON_KEY.ifBlank { "sb_publishable_lUWgwASvsR_nWKS2_xymjA_DeNYyhQf" }

  val authUrl: String get() = "$PROJECT_URL/auth/v1"
  val restUrl: String get() = "$PROJECT_URL/rest/v1"
  val storageUrl: String get() = "$PROJECT_URL/storage/v1"
}

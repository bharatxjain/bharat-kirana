package com.kks.bharatkirana.data.supabase

import com.kks.bharatkirana.BuildConfig

object SupabaseConfig {
  // Values are injected from `.env` (or `.env.example` as fallback) by the
  // Secrets Gradle Plugin. No hardcoded credentials in source — create your
  // own .env before building. See SETUP_STEPS.md.
  val PROJECT_URL: String
    get() = BuildConfig.SUPABASE_URL

  val API_KEY: String
    get() = BuildConfig.SUPABASE_ANON_KEY

  val authUrl: String get() = "$PROJECT_URL/auth/v1"
  val restUrl: String get() = "$PROJECT_URL/rest/v1"
  val storageUrl: String get() = "$PROJECT_URL/storage/v1"
}

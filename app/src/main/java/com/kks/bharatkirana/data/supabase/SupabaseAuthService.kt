package com.kks.bharatkirana.data.supabase

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SupabaseAuthService(
  private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .build()
) {
  private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

  var currentAccessToken: String? = null
    private set

  var currentUserEmail: String? = null
    private set

  var currentUserId: String? = null
    private set

  /**
   * Sign up a new user with email and password
   */
  suspend fun signUp(email: String, password: String, metadata: JSONObject = JSONObject()): Result<AuthSession> = withContext(Dispatchers.IO) {
    runCatching {
      val url = "${SupabaseConfig.authUrl}/signup"
      val json = JSONObject().apply {
        put("email", email.trim().lowercase())
        put("password", password)
        put("data", metadata)
      }

      val request = Request.Builder()
        .url(url)
        .addHeader("apikey", SupabaseConfig.API_KEY)
        .addHeader("Authorization", "Bearer ${SupabaseConfig.API_KEY}")
        .addHeader("Content-Type", "application/json")
        .post(json.toString().toRequestBody(jsonMediaType))
        .build()

      val response = client.newCall(request).execute()
      val responseBody = response.body?.string() ?: ""

      if (response.isSuccessful) {
        val obj = JSONObject(responseBody)
        val accessToken = obj.optString("access_token")
        val userObj = obj.optJSONObject("user")
        val userId = userObj?.optString("id") ?: ""
        val userEmail = userObj?.optString("email") ?: email.trim().lowercase()

        // Access token might be null if email confirmation is required
        if (accessToken.isNotBlank()) {
          currentAccessToken = accessToken
          currentUserEmail = userEmail
          currentUserId = userId
        }

        AuthSession(
          accessToken = accessToken,
          userId = userId,
          email = userEmail
        )
      } else {
        val errorMsg = try {
          JSONObject(responseBody).optString("msg", JSONObject(responseBody).optString("error_description", "Sign up failed"))
        } catch (_: Exception) {
          "Server responded with code ${response.code}"
        }
        throw Exception(errorMsg)
      }
    }
  }

  /**
   * Log in an existing user with email and password
   */
  suspend fun login(email: String, password: String): Result<AuthSession> = withContext(Dispatchers.IO) {
    runCatching {
      val url = "${SupabaseConfig.authUrl}/token?grant_type=password"
      val json = JSONObject().apply {
        put("email", email.trim().lowercase())
        put("password", password)
      }

      val request = Request.Builder()
        .url(url)
        .addHeader("apikey", SupabaseConfig.API_KEY)
        .addHeader("Authorization", "Bearer ${SupabaseConfig.API_KEY}")
        .addHeader("Content-Type", "application/json")
        .post(json.toString().toRequestBody(jsonMediaType))
        .build()

      val response = client.newCall(request).execute()
      val responseBody = response.body?.string() ?: ""

      if (response.isSuccessful) {
        val obj = JSONObject(responseBody)
        val accessToken = obj.optString("access_token")
        val userObj = obj.optJSONObject("user")
        val userId = userObj?.optString("id") ?: ""
        val userEmail = userObj?.optString("email") ?: email.trim().lowercase()

        currentAccessToken = accessToken
        currentUserEmail = userEmail
        currentUserId = userId

        AuthSession(
          accessToken = accessToken,
          userId = userId,
          email = userEmail
        )
      } else {
        val errorMsg = try {
          JSONObject(responseBody).optString("msg", JSONObject(responseBody).optString("error_description", "Login failed"))
        } catch (_: Exception) {
          "Server responded with code ${response.code}"
        }
        throw Exception(errorMsg)
      }
    }
  }

  /**
   * Send 6-digit OTP code or Magic Link to email using Supabase GoTrue Auth API
   */
  suspend fun sendEmailOtp(email: String): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
      val url = "${SupabaseConfig.authUrl}/otp"
      val json = JSONObject().apply {
        put("email", email.trim().lowercase())
        put("create_user", true)
      }

      val request = Request.Builder()
        .url(url)
        .addHeader("apikey", SupabaseConfig.API_KEY)
        .addHeader("Authorization", "Bearer ${SupabaseConfig.API_KEY}")
        .addHeader("Content-Type", "application/json")
        .post(json.toString().toRequestBody(jsonMediaType))
        .build()

      val response = client.newCall(request).execute()
      val responseBody = response.body?.string() ?: ""

      if (response.isSuccessful) {
        "OTP code sent successfully to $email"
      } else {
        val errorMsg = try {
          JSONObject(responseBody).optString("msg", JSONObject(responseBody).optString("error_description", "Failed to send OTP ($responseBody)"))
        } catch (_: Exception) {
          "Server responded with code ${response.code}"
        }
        throw Exception(errorMsg)
      }
    }
  }

  /**
   * Verify the 6-digit OTP token entered by the user
   * type can be "signup", "recovery", "magiclink", "email"
   */
  suspend fun verifyEmailOtp(email: String, token: String, type: String = "signup"): Result<AuthSession> = withContext(Dispatchers.IO) {
    runCatching {
      val url = "${SupabaseConfig.authUrl}/verify"
      val json = JSONObject().apply {
        put("type", type)
        put("email", email.trim().lowercase())
        put("token", token.trim())
      }

      val request = Request.Builder()
        .url(url)
        .addHeader("apikey", SupabaseConfig.API_KEY)
        .addHeader("Authorization", "Bearer ${SupabaseConfig.API_KEY}")
        .addHeader("Content-Type", "application/json")
        .post(json.toString().toRequestBody(jsonMediaType))
        .build()

      val response = client.newCall(request).execute()
      val responseBody = response.body?.string() ?: ""

      if (response.isSuccessful) {
        val obj = JSONObject(responseBody)
        val accessToken = obj.optString("access_token")
        val userObj = obj.optJSONObject("user")
        val userId = userObj?.optString("id") ?: ""
        val userEmail = userObj?.optString("email") ?: email.trim().lowercase()

        currentAccessToken = accessToken
        currentUserEmail = userEmail
        currentUserId = userId

        AuthSession(
          accessToken = accessToken,
          userId = userId,
          email = userEmail
        )
      } else {
        val errorMsg = try {
          JSONObject(responseBody).optString("msg", JSONObject(responseBody).optString("error_description", "Invalid verification code"))
        } catch (_: Exception) {
          "Verification failed with code ${response.code}"
        }
        throw Exception(errorMsg)
      }
    }
  }

  /**
   * Send a reset password email to the user
   */
  suspend fun sendResetPasswordEmail(email: String): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
      val url = "${SupabaseConfig.authUrl}/recover"
      val json = JSONObject().apply {
        put("email", email.trim().lowercase())
        put("redirectTo", "bharatkirana://auth-callback")
      }

      val request = Request.Builder()
        .url(url)
        .addHeader("apikey", SupabaseConfig.API_KEY)
        .addHeader("Authorization", "Bearer ${SupabaseConfig.API_KEY}")
        .addHeader("Content-Type", "application/json")
        .post(json.toString().toRequestBody(jsonMediaType))
        .build()

      val response = client.newCall(request).execute()
      val responseBody = response.body?.string() ?: ""

      if (response.isSuccessful) {
        "Password reset email sent successfully"
      } else {
        val errorMsg = try {
          JSONObject(responseBody).optString("msg", JSONObject(responseBody).optString("error_description", "Failed to send reset email"))
        } catch (_: Exception) {
          "Server responded with code ${response.code}"
        }
        throw Exception(errorMsg)
      }
    }
  }

  /**
   * Update the logged-in user's password
   */
  suspend fun updateUserPassword(accessToken: String, newPassword: String): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
      val url = "${SupabaseConfig.authUrl}/user"
      val json = JSONObject().apply {
        put("password", newPassword)
      }

      val request = Request.Builder()
        .url(url)
        .addHeader("apikey", SupabaseConfig.API_KEY)
        .addHeader("Authorization", "Bearer $accessToken")
        .addHeader("Content-Type", "application/json")
        .put(json.toString().toRequestBody(jsonMediaType))
        .build()

      val response = client.newCall(request).execute()
      if (response.isSuccessful) {
        "Password updated successfully"
      } else {
        throw Exception("Failed to update password. Please try again.")
      }
    }
  }

  /**
   * Sign out current user
   */
  suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
      val token = currentAccessToken
      if (token != null) {
        val url = "${SupabaseConfig.authUrl}/logout"
        val request = Request.Builder()
          .url(url)
          .addHeader("apikey", SupabaseConfig.API_KEY)
          .addHeader("Authorization", "Bearer $token")
          .post("{}".toRequestBody(jsonMediaType))
          .build()

        client.newCall(request).execute()
      }
      currentAccessToken = null
      currentUserEmail = null
      currentUserId = null
    }
  }
}

data class AuthSession(
  val accessToken: String,
  val userId: String,
  val email: String
)

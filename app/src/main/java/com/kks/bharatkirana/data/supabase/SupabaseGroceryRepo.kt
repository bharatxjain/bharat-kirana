package com.kks.bharatkirana.data.supabase

import com.kks.bharatkirana.data.model.CartItem
import com.kks.bharatkirana.data.model.Category
import com.kks.bharatkirana.data.model.Order
import com.kks.bharatkirana.data.model.OrderStatus
import com.kks.bharatkirana.data.model.OrderTimelineItem
import com.kks.bharatkirana.data.model.Product
import com.kks.bharatkirana.data.model.PromoCode
import com.kks.bharatkirana.data.model.Shop
import com.kks.bharatkirana.data.model.SubscriptionTier
import com.kks.bharatkirana.data.model.UserProfile
import com.kks.bharatkirana.data.model.UserRole
import com.kks.bharatkirana.data.model.VendorStatus
import com.kks.bharatkirana.data.model.VendorSubscription
import com.kks.bharatkirana.data.model.WeightOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SupabaseGroceryRepo(
  private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .build()
) {
  private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

  private fun baseRequestBuilder(url: String, accessToken: String? = null): Request.Builder {
    val builder = Request.Builder()
      .url(url)
      .addHeader("apikey", SupabaseConfig.API_KEY)
      .addHeader("Authorization", "Bearer ${accessToken ?: SupabaseConfig.API_KEY}")
      .addHeader("Content-Type", "application/json")
    return builder
  }

  /**
   * Fetch live Products from Supabase PostgreSQL
   */
  suspend fun fetchProducts(): Result<List<Product>> = withContext(Dispatchers.IO) {
    runCatching {
      val url = "${SupabaseConfig.restUrl}/products?select=*"
      val request = baseRequestBuilder(url).get().build()
      val response = client.newCall(request).execute()
      val body = response.body?.string() ?: ""

      if (response.isSuccessful) {
        val array = JSONArray(body)
        val productList = mutableListOf<Product>()

        for (i in 0 until array.length()) {
          val obj = array.getJSONObject(i)
          val id = obj.optString("id")
          val name = obj.optString("name")
          val brand = obj.optString("brand")
          val categoryId = obj.optString("category_id")
          val currentPrice = obj.optInt("current_price", 100)
          val originalPrice = obj.optInt("original_price", currentPrice)
          val discountPercent = obj.optInt("discount_percent", 0)
          val unit = obj.optString("unit", "1 unit")
          val description = obj.optString("description", "")
          val inStock = obj.optBoolean("in_stock", true)

          val imageUrls = mutableListOf<String>()
          val urlsArr = obj.optJSONArray("image_urls")
          if (urlsArr != null) {
            for (k in 0 until urlsArr.length()) {
              imageUrls.add(urlsArr.optString(k))
            }
          }

          val weightOptions = mutableListOf<WeightOption>()
          val weightArr = obj.optJSONArray("weight_options")
          if (weightArr != null) {
            for (j in 0 until weightArr.length()) {
              val wObj = weightArr.optJSONObject(j)
              if (wObj != null) {
                weightOptions.add(
                  WeightOption(
                    label = wObj.optString("label", "1 unit"),
                    price = wObj.optInt("price", currentPrice),
                    originalPrice = wObj.optInt("originalPrice", originalPrice),
                    discountLabel = wObj.optString("discount", "")
                  )
                )
              }
            }
          }
          if (weightOptions.isEmpty()) {
            weightOptions.add(WeightOption(unit, currentPrice, originalPrice))
          }

          productList.add(
            Product(
              id = id,
              name = name,
              brand = brand,
              categoryId = categoryId,
              currentPrice = currentPrice,
              originalPrice = originalPrice,
              discountPercent = discountPercent,
              unit = unit,
              subtitle = "$unit • $brand",
              description = description,
              inStock = inStock,
              imageUrl = imageUrls.firstOrNull() ?: obj.optString("image_url", ""),
              imageUrls = imageUrls,
              weightOptions = weightOptions,
              barcode = obj.optString("barcode", "")
            )
          )
        }
        productList
      } else {
        throw Exception("Failed to fetch products: HTTP ${response.code}")
      }
    }
  }

  /**
   * Update Product In-Stock Status in Supabase (Store Admin)
   */
  suspend fun updateProductStock(productId: String, inStock: Boolean, accessToken: String? = null): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        val url = "${SupabaseConfig.restUrl}/products?id=eq.$productId"
        val payload = JSONObject().apply {
          put("in_stock", inStock)
        }
        val request = baseRequestBuilder(url, accessToken)
          .addHeader("Prefer", "return=minimal")
          .patch(payload.toString().toRequestBody(jsonMediaType))
          .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
          throw Exception("Failed to update stock: HTTP ${response.code}")
        }
      }
    }

  /**
   * Update Product Price in Supabase (Store Admin)
   */
  suspend fun updateProductPrice(productId: String, newPrice: Int, accessToken: String? = null): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        val url = "${SupabaseConfig.restUrl}/products?id=eq.$productId"
        val payload = JSONObject().apply {
          put("current_price", newPrice)
        }
        val request = baseRequestBuilder(url, accessToken)
          .addHeader("Prefer", "return=minimal")
          .patch(payload.toString().toRequestBody(jsonMediaType))
          .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
          throw Exception("Failed to update price: HTTP ${response.code}")
        }
      }
    }

  // Round 4b: targeted PATCH of just profiles.fcm_token — cheaper and safer than
  // re-writing the whole profile row via syncProfile whenever the FCM token rotates.
  suspend fun updateFcmToken(userId: String, token: String, accessToken: String? = null): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        val url = "${SupabaseConfig.restUrl}/profiles?id=eq.$userId"
        val payload = JSONObject().apply { put("fcm_token", token) }
        val request = baseRequestBuilder(url, accessToken)
          .addHeader("Prefer", "return=minimal")
          .patch(payload.toString().toRequestBody(jsonMediaType))
          .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
          throw Exception("Failed to update FCM token: HTTP ${response.code}")
        }
      }
    }

  /**
   * Fetch this user's in-app notifications (written by the notify-order-status Edge
   * Function alongside every push it sends — see SETUP_STEPS.md Task 3).
   */
  suspend fun fetchNotifications(userId: String, accessToken: String? = null): Result<List<com.kks.bharatkirana.data.model.AppNotification>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val url = "${SupabaseConfig.restUrl}/notifications?user_id=eq.$userId&select=*&order=created_at.desc&limit=50"
        val request = baseRequestBuilder(url, accessToken).get().build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: ""
        if (!response.isSuccessful) throw Exception("Failed to fetch notifications: HTTP ${response.code}")

        val array = JSONArray(body)
        val list = mutableListOf<com.kks.bharatkirana.data.model.AppNotification>()
        for (i in 0 until array.length()) {
          val obj = array.getJSONObject(i)
          list.add(
            com.kks.bharatkirana.data.model.AppNotification(
              id = obj.optString("id"),
              title = obj.optString("title"),
              message = obj.optString("message"),
              isRead = obj.optBoolean("is_read", false),
              orderId = obj.optString("order_id").takeIf { it.isNotBlank() },
              createdAt = obj.optString("created_at")
            )
          )
        }
        list
      }
    }

  suspend fun markNotificationRead(notificationId: String, accessToken: String? = null): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        val url = "${SupabaseConfig.restUrl}/notifications?id=eq.$notificationId"
        val payload = JSONObject().apply { put("is_read", true) }
        val request = baseRequestBuilder(url, accessToken)
          .addHeader("Prefer", "return=minimal")
          .patch(payload.toString().toRequestBody(jsonMediaType))
          .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
          throw Exception("Failed to mark notification read: HTTP ${response.code}")
        }
      }
    }

  /**
   * Add Product in Supabase (Store Admin)
   */
  suspend fun addProduct(product: Product, accessToken: String? = null): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        val url = "${SupabaseConfig.restUrl}/products"
        val weightsJson = JSONArray()
        for (w in product.weightOptions) {
          weightsJson.put(JSONObject().apply {
            put("label", w.label)
            put("price", w.price)
            put("originalPrice", w.originalPrice)
            put("discount", w.discountLabel)
          })
        }

        val payload = JSONObject().apply {
          put("id", product.id)
          put("name", product.name)
          put("brand", product.brand)
          put("category_id", product.categoryId)
          put("current_price", product.currentPrice)
          put("original_price", product.originalPrice)
          put("discount_percent", product.discountPercent)
          put("unit", product.unit)
          put("description", product.description)
          put("in_stock", product.inStock)
          put("weight_options", weightsJson)
          put("image_urls", JSONArray(product.imageUrls))
          put("image_url", product.imageUrl)
          if (product.barcode.isNotBlank()) put("barcode", product.barcode)
        }

        val request = baseRequestBuilder(url, accessToken)
          .addHeader("Prefer", "return=minimal")
          .post(payload.toString().toRequestBody(jsonMediaType))
          .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
          throw Exception("Failed to add product: HTTP ${response.code}")
        }
      }
    }

  /**
   * Delete Product from Supabase (Store Admin)
   */
  suspend fun deleteProduct(productId: String, accessToken: String? = null): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        val url = "${SupabaseConfig.restUrl}/products?id=eq.$productId"
        val request = baseRequestBuilder(url, accessToken)
          .delete()
          .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
          throw Exception("Failed to delete product: HTTP ${response.code}")
        }
      }
    }

  /**
   * Permanently delete the signed-in user's profile row (name, address, phone, etc.)
   * from Supabase as part of account deletion. Order history is intentionally left
   * in place for store accounting/tax purposes — see Privacy Policy data retention note.
   */
  suspend fun deleteUserProfile(userId: String, accessToken: String? = null): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        val url = "${SupabaseConfig.restUrl}/profiles?id=eq.$userId"
        val request = baseRequestBuilder(url, accessToken)
          .delete()
          .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
          throw Exception("Failed to delete profile: HTTP ${response.code}")
        }
      }
    }

  /**
   * Update full product details in Supabase
   */
  suspend fun updateFullProduct(product: com.kks.bharatkirana.data.model.Product, accessToken: String? = null): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        val url = "${SupabaseConfig.restUrl}/products?id=eq.${product.id}"
        val weightsJson = JSONArray()
        for (w in product.weightOptions) {
          weightsJson.put(JSONObject().apply {
            put("label", w.label)
            put("price", w.price)
            put("originalPrice", w.originalPrice)
            put("discount", w.discountLabel)
          })
        }

        val payload = JSONObject().apply {
          put("name", product.name)
          put("brand", product.brand)
          put("category_id", product.categoryId)
          put("current_price", product.currentPrice)
          put("original_price", product.originalPrice)
          put("discount_percent", product.discountPercent)
          put("unit", product.unit)
          put("description", product.description)
          put("in_stock", product.inStock)
          put("weight_options", weightsJson)
          put("image_urls", JSONArray(product.imageUrls))
          put("image_url", product.imageUrl)
        }

        val request = baseRequestBuilder(url, accessToken)
          .addHeader("Prefer", "return=minimal")
          .patch(payload.toString().toRequestBody(jsonMediaType))
          .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
          throw Exception("Failed to update product: HTTP ${response.code}")
        }
      }
    }

  /**
   * Fetch Orders from Supabase
   */
  suspend fun fetchOrders(customerEmail: String? = null, isAdmin: Boolean = false, accessToken: String? = null): Result<List<Order>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val url = if (isAdmin || customerEmail == null) {
          "${SupabaseConfig.restUrl}/orders?select=*&order=created_at.desc"
        } else {
          "${SupabaseConfig.restUrl}/orders?customer_email=eq.${customerEmail.trim().lowercase()}&select=*&order=created_at.desc"
        }

        val request = baseRequestBuilder(url, accessToken).get().build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: ""

        if (response.isSuccessful) {
          val array = JSONArray(body)
          val orderList = mutableListOf<Order>()

          for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val id = obj.optString("id")
            val totalAmount = obj.optInt("total_amount", 0)
            val orderDate = obj.optString("order_date", "Today")
            val statusStr = obj.optString("status", "Order Placed")
            val qrCodePayload = obj.optString("qr_code_payload", "ORDER:$id")

            val status = when (statusStr) {
              OrderStatus.CONFIRMED.label -> OrderStatus.CONFIRMED
              OrderStatus.PREPARING.label -> OrderStatus.PREPARING
              OrderStatus.READY_FOR_PICKUP.label -> OrderStatus.READY_FOR_PICKUP
              OrderStatus.COMPLETED.label -> OrderStatus.COMPLETED
              OrderStatus.CANCELLED.label -> OrderStatus.CANCELLED
              else -> OrderStatus.PLACED
            }

            // Round 6: reuse the same helper the client uses at insert time so
            // the customer sees a consistent 5-step timeline everywhere.
            val timeline = com.kks.bharatkirana.data.model.buildOrderTimeline(
              currentStatus = status,
              orderDate = orderDate,
              nowLabel = orderDate
            )

            orderList.add(
              Order(
                id = id,
                items = emptyList(),
                totalAmount = totalAmount,
                orderDate = orderDate,
                status = status,
                expectedPickupTime = "Today by 5:30 PM",
                qrCodePayload = qrCodePayload,
                timeline = timeline
              )
            )
          }
          orderList
        } else {
          throw Exception("Failed to fetch orders: HTTP ${response.code}")
        }
      }
    }

  /**
   * Create New Order in Supabase
   */
  suspend fun insertOrder(
    order: Order,
    customerEmail: String,
    customerName: String,
    customerMobile: String,
    userId: String?,
    promoCode: String? = null,
    promoDiscount: Int = 0,
    accessToken: String? = null
  ): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        val url = "${SupabaseConfig.restUrl}/orders"
        val payload = JSONObject().apply {
          put("id", order.id)
          put("customer_name", customerName)
          put("customer_email", customerEmail.trim().lowercase())
          put("customer_mobile", if (customerMobile.isNotBlank()) customerMobile else "")
          put("total_amount", order.totalAmount)
          put("status", order.status.label)
          put("order_date", order.orderDate)
          put("qr_code_payload", order.qrCodePayload)
          if (!userId.isNullOrBlank()) put("user_id", userId)
          if (order.shopId.isNotBlank() && order.shopId != "default_shop") put("shop_id", order.shopId)
          if (!promoCode.isNullOrBlank()) {
            put("promo_code", promoCode)
            put("promo_discount", promoDiscount)
          }
        }

        val request = baseRequestBuilder(url, accessToken)
          .addHeader("Prefer", "return=minimal")
          .post(payload.toString().toRequestBody(jsonMediaType))
          .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
          throw Exception("Failed to create order: HTTP ${response.code}")
        }
      }
    }

  /**
   * Update Order Status in Supabase (Store Admin)
   */
  suspend fun updateOrderStatus(orderId: String, newStatus: OrderStatus, accessToken: String? = null): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        val url = "${SupabaseConfig.restUrl}/orders?id=eq.$orderId"
        val payload = JSONObject().apply {
          put("status", newStatus.label)
        }

        val request = baseRequestBuilder(url, accessToken)
          .addHeader("Prefer", "return=minimal")
          .patch(payload.toString().toRequestBody(jsonMediaType))
          .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
          throw Exception("Failed to update order status: HTTP ${response.code}")
        }
      }
    }

  /**
   * Register a new Shop and link it to the user
   */
  suspend fun registerShop(shop: com.kks.bharatkirana.data.model.Shop, ownerId: String, accessToken: String? = null): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        // 1. Create the Shop
        val shopUrl = "${SupabaseConfig.restUrl}/shops"
        val shopPayload = JSONObject().apply {
          put("id", shop.id)
          put("name", shop.name)
          put("owner_name", shop.ownerName)
          put("owner_id", ownerId)
          put("address", shop.address)
          put("phone", shop.phone)
          put("lat", shop.lat)
          put("lng", shop.lng)
          put("primary_category", shop.primaryCategory)
          put("is_partner", false)
          put("accepting_orders", shop.isOpen)
          put("open_time", shop.openTime)
          put("close_time", shop.closeTime)
        }

        val shopRequest = baseRequestBuilder(shopUrl, accessToken)
          .post(shopPayload.toString().toRequestBody(jsonMediaType))
          .build()

        val shopResponse = client.newCall(shopRequest).execute()
        if (!shopResponse.isSuccessful) {
          throw Exception("Shop registration failed: HTTP ${shopResponse.code}")
        }

        // 2. Link the shop to this user in their profile.
        // NOTE: role stays 'customer' here — a super admin promotes the user to
        // 'vendor' after reviewing the registration (via the Admin Dashboard).
        val profileUrl = "${SupabaseConfig.restUrl}/profiles?id=eq.$ownerId"
        val profilePayload = JSONObject().apply {
          put("shop_id", shop.id)
        }
        
        val profileRequest = baseRequestBuilder(profileUrl, accessToken)
          .patch(profilePayload.toString().toRequestBody(jsonMediaType))
          .build()

        client.newCall(profileRequest).execute()
        Unit
      }
    }

  /**
   * Update Shop Details in Supabase
   */
  suspend fun updateShop(shopId: String, shop: com.kks.bharatkirana.data.model.Shop, accessToken: String? = null): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        val url = "${SupabaseConfig.restUrl}/shops?id=eq.$shopId"
        val payload = JSONObject().apply {
          put("name", shop.name)
          put("owner_name", shop.ownerName)
          put("address", shop.address)
          put("phone", shop.phone)
          put("lat", shop.lat)
          put("lng", shop.lng)
          put("accepting_orders", shop.isOpen)
          put("open_time", shop.openTime)
          put("close_time", shop.closeTime)
          put("is_partner", shop.isPartner)
        }

        val request = baseRequestBuilder(url, accessToken)
          .addHeader("Prefer", "return=minimal")
          .patch(payload.toString().toRequestBody(jsonMediaType))
          .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
          throw Exception("Failed to update shop: HTTP ${response.code}")
        }
      }
    }

  /**
   * Upload Product Image to Supabase Storage
   * Returns the public URL of the uploaded image
   */
  suspend fun uploadProductImage(imageName: String, imageBytes: ByteArray, accessToken: String? = null): Result<String> =
    withContext(Dispatchers.IO) {
      runCatching {
        // 1. Upload to Storage (Bucket: 'product-images')
        val url = "${SupabaseConfig.storageUrl}/object/product-images/$imageName"
        
        val request = baseRequestBuilder(url, accessToken)
          .post(imageBytes.toRequestBody("image/jpeg".toMediaType()))
          .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful && response.code != 409) { // 409 = already exists, we might want to update
           throw Exception("Storage upload failed: ${response.code}")
        }

        // 2. Get Public URL
        "${SupabaseConfig.storageUrl}/public/product-images/$imageName"
      }
    }

  /**
   * Sync or Save User Profile to Supabase.
   *
   * Targeted PATCH by `id` (like every other profile write in this file) rather
   * than a POST upsert with no `id` in the payload — the previous POST left it to
   * Postgres's column default to decide which row got written, so it could land on
   * a different row than the one `fetchProfile(userId=...)` reads back on the next
   * login, making `profile_completed` appear to silently revert to false.
   *
   * Round 6: switched to a real UPSERT (POST + `Prefer: resolution=merge-duplicates`)
   * WITH `id` in the payload. This means:
   *   - Row exists → Supabase does ON CONFLICT (id) DO UPDATE (same effect as PATCH).
   *   - Row missing → Supabase INSERTs a fresh row keyed by the supplied id.
   * The previous PATCH silently affected 0 rows when the row didn't exist yet
   * (e.g. after email OTP signup before the auth trigger fires), which is why
   * mobile_number and profile_completed appeared "not to save".
   */
  suspend fun syncProfile(userId: String, userProfile: UserProfile, accessToken: String? = null): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        val url = "${SupabaseConfig.restUrl}/profiles"
        val payload = JSONObject().apply {
          put("id", userId)
          put("email", userProfile.email.trim().lowercase())
          put("full_name", userProfile.fullName)
          put("mobile_number", userProfile.mobileNumber)
          put("address", userProfile.address)
          // NOTE: `role` is intentionally NOT sent from the client. It is set only by
          // a super admin via the Supabase Table Editor (or a future admin-only Edge
          // Function). RLS on user_profiles must reject any client attempt to set role.
          put("wallet_balance", userProfile.walletBalance)
          put("loyalty_points", userProfile.loyaltyPoints)
          put("profile_completed", userProfile.profileCompleted)
          put("phone_verified", userProfile.phoneVerified)
          put("auth_provider", userProfile.authPath?.name?.lowercase() ?: "phone")
          put("fcm_token", userProfile.fcmToken)
        }

        val request = baseRequestBuilder(url, accessToken)
          .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
          .post(payload.toString().toRequestBody(jsonMediaType))
          .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
          throw Exception("Profile sync failed: HTTP ${response.code} — ${response.body?.string().orEmpty()}")
        }
      }
    }

  /**
   * Fetch the currently-logged-in user's profile (including server-side role).
   * The role from this row is the ONLY source of truth for admin/vendor authorization.
   */
  suspend fun fetchProfile(userId: String, accessToken: String? = null): Result<UserProfile> =
    withContext(Dispatchers.IO) {
      runCatching {
        val url = "${SupabaseConfig.restUrl}/profiles?id=eq.$userId&select=*"
        val request = baseRequestBuilder(url, accessToken).get().build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: ""
        if (!response.isSuccessful) throw Exception("Failed to fetch profile: HTTP ${response.code}")

        val array = JSONArray(body)
        if (array.length() == 0) throw Exception("Profile row not found")

        val obj = array.getJSONObject(0)
        val roleStr = obj.optString("role", "customer").lowercase()
        val serverRole = when (roleStr) {
          "super_admin" -> UserRole.SUPER_ADMIN
          "admin" -> UserRole.ADMIN
          "vendor" -> UserRole.VENDOR
          else -> UserRole.CUSTOMER
        }
        UserProfile(
          fullName = obj.optString("full_name", ""),
          email = obj.optString("email", ""),
          mobileNumber = obj.optString("mobile_number", ""),
          address = obj.optString("address", ""),
          loyaltyPoints = obj.optInt("loyalty_points", 0),
          walletBalance = obj.optInt("wallet_balance", 0),
          shopId = obj.optString("shop_id").takeIf { it.isNotBlank() },
          profileCompleted = obj.optBoolean("profile_completed", false),
          phoneVerified = obj.optBoolean("phone_verified", false),
          serverRole = serverRole
        )
      }
    }

  /**
   * Fetch all shops from Supabase and map to the Shop domain model.
   */
  suspend fun fetchShops(accessToken: String? = null): Result<List<Shop>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val url = "${SupabaseConfig.restUrl}/shops?select=*"
        val request = baseRequestBuilder(url, accessToken).get().build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: ""
        if (!response.isSuccessful) throw Exception("Failed to fetch shops: HTTP ${response.code}")

        val array = JSONArray(body)
        val shops = mutableListOf<Shop>()
        for (i in 0 until array.length()) {
          val obj = array.getJSONObject(i)
          val statusStr = obj.optString("status", "pending").uppercase()
          val vendorStatus = try { VendorStatus.valueOf(statusStr) } catch (_: Exception) { VendorStatus.PENDING }
          shops.add(
            Shop(
              id = obj.optString("id"),
              name = obj.optString("name"),
              ownerName = obj.optString("owner_name", ""),
              address = obj.optString("address", ""),
              phone = obj.optString("phone", ""),
              lat = obj.optDouble("lat", 0.0),
              lng = obj.optDouble("lng", 0.0),
              rating = obj.optDouble("avg_rating", 4.5).toFloat(),
              ratingCount = obj.optInt("rating_count", 0),
              deliveryTime = obj.optString("delivery_time", "20-30 mins"),
              imageUrl = obj.optString("image_url", ""),
              isPartner = obj.optBoolean("is_partner", false),
              primaryCategory = obj.optString("primary_category", "Grocery"),
              status = vendorStatus,
              isOpen = obj.optBoolean("accepting_orders", true),
              openTime = obj.optString("open_time", "08:00"),
              closeTime = obj.optString("close_time", "21:00")
            )
          )
        }
        shops
      }
    }

  /**
   * Submit a rating & review for a completed order.
   * Server-side trigger will refresh shops.avg_rating and rating_count.
   */
  suspend fun submitShopRating(
    shopId: String,
    orderId: String,
    customerId: String,
    rating: Int,
    review: String,
    accessToken: String? = null
  ): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        val url = "${SupabaseConfig.restUrl}/shop_ratings"
        val payload = JSONObject().apply {
          put("shop_id", shopId)
          put("order_id", orderId)
          put("customer_id", customerId)
          put("rating", rating)
          put("review", review)
        }
        val request = baseRequestBuilder(url, accessToken)
          .addHeader("Prefer", "return=minimal")
          .post(payload.toString().toRequestBody(jsonMediaType))
          .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
          throw Exception("Failed to submit rating: HTTP ${response.code}")
        }
      }
    }

  // Round 6.1: which order IDs has this customer already rated? Called on login
  // so OrderDetailsScreen can suppress the star form for orders they've reviewed.
  suspend fun fetchRatedOrderIds(customerId: String, accessToken: String? = null): Result<Set<String>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val url = "${SupabaseConfig.restUrl}/shop_ratings?customer_id=eq.$customerId&select=order_id"
        val request = baseRequestBuilder(url, accessToken).get().build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: ""
        if (!response.isSuccessful) throw Exception("Failed to fetch ratings: HTTP ${response.code}")
        val array = JSONArray(body)
        val ids = mutableSetOf<String>()
        for (i in 0 until array.length()) {
          val o = array.optJSONObject(i) ?: continue
          val id = o.optString("order_id")
          if (id.isNotBlank()) ids.add(id)
        }
        ids
      }
    }

  /**
   * Look up a promo code by exact match. RLS on `promo_codes` already restricts
   * this to `active = TRUE AND valid window` — so a returned row is guaranteed
   * to be currently redeemable.
   */
  suspend fun fetchPromoCode(code: String, accessToken: String? = null): Result<PromoCode> =
    withContext(Dispatchers.IO) {
      runCatching {
        val cleanCode = code.trim().uppercase()
        val url = "${SupabaseConfig.restUrl}/promo_codes?code=eq.$cleanCode&select=*"
        val request = baseRequestBuilder(url, accessToken).get().build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: ""
        if (!response.isSuccessful) throw Exception("Failed to fetch promo: HTTP ${response.code}")

        val array = JSONArray(body)
        if (array.length() == 0) throw Exception("Invalid or expired code")

        val obj = array.getJSONObject(0)
        PromoCode(
          code = obj.optString("code", cleanCode),
          description = obj.optString("description", ""),
          discountPercent = obj.optInt("discount_percent", 0),
          discountFlatRupees = obj.optInt("discount_flat_rupees", 0),
          minOrderAmount = obj.optInt("min_order_amount", 0),
          maxDiscountRupees = if (obj.isNull("max_discount_rupees")) null else obj.optInt("max_discount_rupees")
        )
      }
    }

  /**
   * Look up a product by its barcode (13-digit EAN etc). Returns the first match
   * across ALL shops so the shopkeeper can reuse product metadata (name, brand,
   * category, image) without retyping it. The scanning shop still creates its
   * own product row with their own price/stock — this only pre-fills the form.
   */
  suspend fun fetchProductByBarcode(barcode: String, accessToken: String? = null): Result<Product?> =
    withContext(Dispatchers.IO) {
      runCatching {
        val clean = barcode.trim()
        if (clean.isBlank()) return@runCatching null
        val url = "${SupabaseConfig.restUrl}/products?barcode=eq.$clean&select=*&limit=1"
        val request = baseRequestBuilder(url, accessToken).get().build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: ""
        if (!response.isSuccessful) throw Exception("Failed to look up barcode: HTTP ${response.code}")

        val array = JSONArray(body)
        if (array.length() == 0) {
          // Round 5: fallback to OpenFoodFacts (free, 3.4M SKUs, no key needed).
          return@runCatching lookupOnOpenFoodFacts(clean)
        }

        val obj = array.getJSONObject(0)
        val imageUrls = mutableListOf<String>()
        obj.optJSONArray("image_urls")?.let { arr ->
          for (k in 0 until arr.length()) imageUrls.add(arr.optString(k))
        }
        val weightOptions = mutableListOf<WeightOption>()
        obj.optJSONArray("weight_options")?.let { arr ->
          for (j in 0 until arr.length()) {
            arr.optJSONObject(j)?.let { w ->
              weightOptions.add(
                WeightOption(
                  label = w.optString("label", "1 unit"),
                  price = w.optInt("price", 0),
                  originalPrice = w.optInt("originalPrice", 0),
                  discountLabel = w.optString("discount", "")
                )
              )
            }
          }
        }
        Product(
          id = obj.optString("id"),
          name = obj.optString("name", ""),
          brand = obj.optString("brand", ""),
          categoryId = obj.optString("category_id", ""),
          currentPrice = obj.optInt("current_price", 0),
          originalPrice = obj.optInt("original_price", 0),
          discountPercent = obj.optInt("discount_percent", 0),
          unit = obj.optString("unit", "1 unit"),
          description = obj.optString("description", ""),
          inStock = obj.optBoolean("in_stock", true),
          imageUrl = imageUrls.firstOrNull() ?: obj.optString("image_url", ""),
          imageUrls = imageUrls,
          weightOptions = weightOptions,
          barcode = obj.optString("barcode", "")
        )
      }
    }

  // Round 5: free OpenFoodFacts API — no key, no signup, User-Agent required by ToS.
  // Returns a partial Product template (no shopId/price/stock — vendor fills those).
  private fun lookupOnOpenFoodFacts(barcode: String): Product? {
    val url = "https://world.openfoodfacts.org/api/v2/product/$barcode.json?fields=code,product_name,brands,image_url,image_front_url,quantity,categories_tags,generic_name"
    val request = Request.Builder()
      .url(url)
      .addHeader("User-Agent", "BreakQ-Android/1.1 (support@breakq.app)")
      .get()
      .build()
    val response = client.newCall(request).execute()
    if (!response.isSuccessful) return null
    val body = response.body?.string() ?: return null
    val root = JSONObject(body)
    if (root.optInt("status", 0) != 1) return null

    val p = root.optJSONObject("product") ?: return null
    val name = p.optString("product_name").ifBlank { p.optString("generic_name") }
    if (name.isBlank()) return null

    val image = p.optString("image_front_url").ifBlank { p.optString("image_url") }
    val brand = p.optString("brands").split(",").firstOrNull()?.trim().orEmpty()
    val unit = p.optString("quantity").ifBlank { "1 unit" }

    // Categories arrive as ["en:noodles", "en:instant-noodles"] — take the last
    // (most specific) for future auto-mapping to our category tree.
    val catsTag = p.optJSONArray("categories_tags")
      ?.let { arr -> if (arr.length() > 0) arr.optString(arr.length() - 1) else null }
      ?.substringAfter(":")
      ?.replace("-", " ")
      ?.replaceFirstChar { it.uppercase() }
      .orEmpty()

    return Product(
      id = "",
      name = name.trim(),
      brand = brand,
      categoryId = catsTag,
      currentPrice = 0,
      originalPrice = 0,
      unit = unit,
      description = "",
      imageUrl = image,
      imageUrls = if (image.isNotBlank()) listOf(image) else emptyList(),
      weightOptions = emptyList(),
      barcode = barcode
    )
  }

  // Round 5: subscription catalog. Public read, so no token needed.
  suspend fun fetchSubscriptionTiers(): Result<List<SubscriptionTier>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val url = "${SupabaseConfig.restUrl}/subscription_tiers?is_active=eq.true&select=*&order=price_rupees.asc"
        val request = baseRequestBuilder(url).get().build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: ""
        if (!response.isSuccessful) throw Exception("Failed to fetch tiers: HTTP ${response.code}")
        val array = JSONArray(body)
        val list = mutableListOf<SubscriptionTier>()
        for (i in 0 until array.length()) {
          val o = array.getJSONObject(i)
          val featuresArr = o.optJSONArray("features")
          val features = mutableListOf<String>()
          if (featuresArr != null) {
            for (j in 0 until featuresArr.length()) features.add(featuresArr.optString(j))
          }
          list.add(
            SubscriptionTier(
              id = o.optString("id"),
              displayName = o.optString("display_name"),
              priceRupees = o.optInt("price_rupees", 0),
              itemCap = o.optInt("item_cap", 10),
              priorityRank = o.optInt("priority_rank", 0),
              canPromote = o.optBoolean("can_promote", false),
              promoteDailyCapRupees = o.optInt("promote_daily_cap_rupees", 0),
              commissionPercent = o.optDouble("commission_percent", 0.0),
              features = features
            )
          )
        }
        list
      }
    }

  suspend fun fetchVendorSubscription(shopId: String, accessToken: String? = null): Result<VendorSubscription?> =
    withContext(Dispatchers.IO) {
      runCatching {
        val url = "${SupabaseConfig.restUrl}/vendor_subscriptions?shop_id=eq.$shopId&status=eq.active&select=*&limit=1"
        val request = baseRequestBuilder(url, accessToken).get().build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: ""
        if (!response.isSuccessful) throw Exception("Failed to fetch subscription: HTTP ${response.code}")
        val array = JSONArray(body)
        if (array.length() == 0) return@runCatching null
        val o = array.getJSONObject(0)
        VendorSubscription(
          id = o.optString("id"),
          shopId = o.optString("shop_id"),
          tierId = o.optString("tier_id"),
          status = o.optString("status"),
          startedAt = o.optString("started_at"),
          expiresAt = if (o.isNull("expires_at")) null else o.optString("expires_at"),
          amountPaidRupees = o.optInt("amount_paid_rupees", 0),
          commissionLockedAtPercent = o.optDouble("commission_locked_at_percent", 0.0)
        )
      }
    }
}

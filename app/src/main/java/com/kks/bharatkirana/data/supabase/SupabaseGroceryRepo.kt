package com.kks.bharatkirana.data.supabase

import com.kks.bharatkirana.data.model.CartItem
import com.kks.bharatkirana.data.model.Category
import com.kks.bharatkirana.data.model.Order
import com.kks.bharatkirana.data.model.OrderStatus
import com.kks.bharatkirana.data.model.OrderTimelineItem
import com.kks.bharatkirana.data.model.Product
import com.kks.bharatkirana.data.model.UserProfile
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
              weightOptions = weightOptions
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
              OrderStatus.PREPARING.label -> OrderStatus.PREPARING
              OrderStatus.READY_FOR_PICKUP.label -> OrderStatus.READY_FOR_PICKUP
              OrderStatus.COMPLETED.label -> OrderStatus.COMPLETED
              OrderStatus.CANCELLED.label -> OrderStatus.CANCELLED
              else -> OrderStatus.PLACED
            }

            // Timeline reconstruction
            val timeline = listOf(
              OrderTimelineItem(OrderStatus.PLACED, orderDate, isCompleted = true),
              OrderTimelineItem(OrderStatus.PREPARING, orderDate, isCompleted = status.stepIndex >= 1, isCurrent = status == OrderStatus.PREPARING),
              OrderTimelineItem(OrderStatus.READY_FOR_PICKUP, "Counter 1", isCompleted = status.stepIndex >= 2, isCurrent = status == OrderStatus.READY_FOR_PICKUP),
              OrderTimelineItem(OrderStatus.COMPLETED, "Collected", isCompleted = status == OrderStatus.COMPLETED, isCurrent = status == OrderStatus.COMPLETED)
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
  suspend fun insertOrder(order: Order, customerEmail: String, customerName: String, accessToken: String? = null): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        val url = "${SupabaseConfig.restUrl}/orders"
        val payload = JSONObject().apply {
          put("id", order.id)
          put("customer_name", customerName)
          put("customer_email", customerEmail.trim().lowercase())
          put("customer_mobile", "98765 43210")
          put("total_amount", order.totalAmount)
          put("status", order.status.label)
          put("order_date", order.orderDate)
          put("qr_code_payload", order.qrCodePayload)
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
          put("is_partner", false)
          put("is_open", shop.isOpen)
          put("auto_confirm", shop.autoConfirm)
          put("packing_time", shop.packingTime)
        }
        
        val shopRequest = baseRequestBuilder(shopUrl, accessToken)
          .post(shopPayload.toString().toRequestBody(jsonMediaType))
          .build()

        val shopResponse = client.newCall(shopRequest).execute()
        if (!shopResponse.isSuccessful) {
          throw Exception("Shop registration failed: HTTP ${shopResponse.code}")
        }

        // 2. Update User Profile
        val profileUrl = "${SupabaseConfig.restUrl}/profiles?id=eq.$ownerId"
        val profilePayload = JSONObject().apply {
          put("role", "Vendor")
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
          put("is_open", shop.isOpen)
          put("auto_confirm", shop.autoConfirm)
          put("packing_time", shop.packingTime)
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
   * Sync or Save User Profile to Supabase
   */
  suspend fun syncProfile(userProfile: UserProfile, accessToken: String? = null): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        val url = "${SupabaseConfig.restUrl}/profiles"
        val payload = JSONObject().apply {
          put("email", userProfile.email.trim().lowercase())
          put("full_name", userProfile.fullName)
          put("mobile_number", userProfile.mobileNumber)
          put("address", userProfile.address)
          put("role", userProfile.role.name.lowercase())
          put("wallet_balance", userProfile.walletBalance)
          put("loyalty_points", userProfile.loyaltyPoints)
          put("profile_completed", userProfile.profileCompleted)
          put("phone_verified", userProfile.phoneVerified)
          put("auth_provider", userProfile.authPath?.name?.lowercase() ?: "phone")
          put("fcm_token", userProfile.fcmToken)
        }

        val request = baseRequestBuilder(url, accessToken)
          .addHeader("Prefer", "resolution=merge-duplicates")
          .post(payload.toString().toRequestBody(jsonMediaType))
          .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
          throw Exception("Profile sync failed: HTTP ${response.code}")
        }
      }
    }
}

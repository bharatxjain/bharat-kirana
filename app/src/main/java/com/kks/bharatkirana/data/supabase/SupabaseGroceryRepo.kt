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
import com.kks.bharatkirana.data.model.VendorAnalytics
import com.kks.bharatkirana.data.model.VendorStatus
import com.kks.bharatkirana.data.model.VendorSubscription
import com.kks.bharatkirana.data.model.WeightOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// Streams bytes to the server in small chunks and reports 0..100 progress as it
// goes, so the vendor sees a real percentage instead of a fake staged bar.
private class ProgressRequestBody(
  private val bytes: ByteArray,
  private val contentType: MediaType?,
  private val onProgress: (Int) -> Unit
) : RequestBody() {
  override fun contentType(): MediaType? = contentType
  override fun contentLength(): Long = bytes.size.toLong()

  override fun writeTo(sink: BufferedSink) {
    val chunk = 8 * 1024
    var written = 0
    onProgress(0)
    while (written < bytes.size) {
      val count = minOf(chunk, bytes.size - written)
      sink.write(bytes, written, count)
      sink.flush()
      written += count
      onProgress(((written * 100L) / bytes.size).toInt().coerceIn(0, 100))
    }
  }
}

/** Thrown by addProduct when the DB's partial unique index rejects the insert. */
class DuplicateProductException(
  val shopId: String,
  val catalogRef: String?
) : Exception("This product is already listed in your shop.")

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
          // SQL NULL means the vendor never declared a count — keep it null so
          // the badge can say "Call to Confirm" rather than "Out of Stock".
          val stockQty = if (obj.isNull("stock_qty")) null else obj.optInt("stock_qty")

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
              shopId = obj.optString("shop_id", "default_shop"),
              currentPrice = currentPrice,
              originalPrice = originalPrice,
              discountPercent = discountPercent,
              unit = unit,
              subtitle = "$unit • $brand",
              description = description,
              inStock = inStock,
              stockQty = stockQty,
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

  // Round 10: null clears the count back to "untracked" (Call to Confirm);
  // 0 explicitly means sold out.
  suspend fun updateProductStockQty(productId: String, stockQty: Int?, accessToken: String? = null): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        val url = "${SupabaseConfig.restUrl}/products?id=eq.$productId"
        val payload = JSONObject().apply {
          if (stockQty == null) put("stock_qty", JSONObject.NULL)
          else put("stock_qty", stockQty.coerceAtLeast(0))
        }
        val request = baseRequestBuilder(url, accessToken)
          .addHeader("Prefer", "return=minimal")
          .patch(payload.toString().toRequestBody(jsonMediaType))
          .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
          throw Exception("Failed to update stock quantity: HTTP ${response.code}")
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

  suspend fun markAllNotificationsRead(userId: String, accessToken: String? = null): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        val url = "${SupabaseConfig.restUrl}/notifications?user_id=eq.$userId&is_read=eq.false"
        val payload = JSONObject().apply { put("is_read", true) }
        val request = baseRequestBuilder(url, accessToken)
          .addHeader("Prefer", "return=minimal")
          .patch(payload.toString().toRequestBody(jsonMediaType))
          .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
          throw Exception("Failed to mark all notifications read: HTTP ${response.code}")
        }
      }
    }

  suspend fun deleteAllNotifications(userId: String, accessToken: String? = null): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        val url = "${SupabaseConfig.restUrl}/notifications?user_id=eq.$userId"
        val request = baseRequestBuilder(url, accessToken)
          .addHeader("Prefer", "return=minimal")
          .delete()
          .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
          throw Exception("Failed to clear notifications: HTTP ${response.code}")
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
          put("shop_id", product.shopId)
          put("name", product.name)
          put("brand", product.brand)
          put("category_id", product.categoryId)
          put("current_price", product.currentPrice)
          put("original_price", product.originalPrice)
          put("discount_percent", product.discountPercent)
          put("unit", product.unit)
          put("description", product.description)
          put("in_stock", product.inStock)
          put("stock_qty", product.stockQty ?: JSONObject.NULL)
          put("weight_options", weightsJson)
          put("image_urls", JSONArray(product.imageUrls))
          put("image_url", product.imageUrl)
          if (product.barcode.isNotBlank()) put("barcode", product.barcode)
          // catalog_ref is the DB-enforced dedup key. NULL means "no stable
          // identity" and skips the unique index (see FIX_PRODUCT_DUPLICATES.sql).
          if (product.catalogRef != null) put("catalog_ref", product.catalogRef) else put("catalog_ref", JSONObject.NULL)
        }

        val request = baseRequestBuilder(url, accessToken)
          .addHeader("Prefer", "return=minimal")
          .post(payload.toString().toRequestBody(jsonMediaType))
          .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
          val body = response.body?.string()?.take(400) ?: ""
          // Postgres unique_violation on our partial index means the shop
          // already has this catalog_ref. Surface as a typed exception so the
          // ViewModel can show the "Already in Inventory" dialog.
          if (response.code == 409 && body.contains("23505") && body.contains("idx_products_shop_catalog_ref")) {
            throw DuplicateProductException(shopId = product.shopId, catalogRef = product.catalogRef)
          }
          throw Exception("Failed to add product: HTTP ${response.code}${if (body.isNotBlank()) " — $body" else ""}")
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
  suspend fun fetchOrders(customerEmail: String? = null, isAdmin: Boolean = false, vendorShopId: String? = null, accessToken: String? = null): Result<List<Order>> =
    withContext(Dispatchers.IO) {
      runCatching {
        // Try embedding order_items first for a single-round-trip fetch; if the
        // migration for order_items hasn't been applied on this Supabase project
        // the embed 400s, so fall through to a plain select=* fetch.
        fun buildUrl(select: String): String = when {
          isAdmin -> "${SupabaseConfig.restUrl}/orders?$select&order=created_at.desc"
          !vendorShopId.isNullOrBlank() -> "${SupabaseConfig.restUrl}/orders?shop_id=eq.$vendorShopId&$select&order=created_at.desc"
          customerEmail != null -> "${SupabaseConfig.restUrl}/orders?customer_email=eq.${customerEmail.trim().lowercase()}&$select&order=created_at.desc"
          else -> "${SupabaseConfig.restUrl}/orders?$select&order=created_at.desc"
        }

        val embedRequest = baseRequestBuilder(buildUrl("select=*,order_items(*)"), accessToken).get().build()
        var response = client.newCall(embedRequest).execute()
        var body = response.body?.string() ?: ""

        if (!response.isSuccessful) {
          // Retry without the embed so vendors and customers still see their
          // orders on Supabase projects that haven't applied ORDER_ITEMS_MIGRATION.sql.
          val plainRequest = baseRequestBuilder(buildUrl("select=*"), accessToken).get().build()
          response = client.newCall(plainRequest).execute()
          body = response.body?.string() ?: ""
          if (!response.isSuccessful) {
            throw Exception("Failed to fetch orders: HTTP ${response.code}")
          }
        }

        val array = JSONArray(body)
        val orderList = mutableListOf<Order>()

        for (i in 0 until array.length()) {
          val obj = array.getJSONObject(i)
          val id = obj.optString("id")
          val totalAmount = obj.optInt("total_amount", 0)
          val orderDate = obj.optString("order_date", "Today")
          val statusStr = obj.optString("status", "Order Placed")
          val qrCodePayload = obj.optString("qr_code_payload", "ORDER:$id")
          val shopIdField = obj.optString("shop_id").takeIf { it.isNotBlank() } ?: "default_shop"

          val status = when (statusStr) {
            OrderStatus.CONFIRMED.label -> OrderStatus.CONFIRMED
            OrderStatus.PREPARING.label -> OrderStatus.PREPARING
            OrderStatus.READY_FOR_PICKUP.label -> OrderStatus.READY_FOR_PICKUP
            OrderStatus.COMPLETED.label -> OrderStatus.COMPLETED
            OrderStatus.CANCELLED.label -> OrderStatus.CANCELLED
            else -> OrderStatus.PLACED
          }

          val timeline = com.kks.bharatkirana.data.model.buildOrderTimeline(
            currentStatus = status,
            orderDate = orderDate,
            nowLabel = orderDate
          )

          val itemsArray = obj.optJSONArray("order_items")
          val items = if (itemsArray != null) parseOrderItems(itemsArray) else emptyList()

          orderList.add(
            Order(
              id = id,
              shopId = shopIdField,
              items = items,
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
      }
    }

  /**
   * Convert embedded `order_items` JSON rows into UI-shaped [CartItem]s.
   * Uses the denormalised snapshot fields so historical orders stay readable
   * even if the underlying product row later changes.
   */
  private fun parseOrderItems(itemsArray: JSONArray): List<com.kks.bharatkirana.data.model.CartItem> {
    val list = mutableListOf<com.kks.bharatkirana.data.model.CartItem>()
    for (j in 0 until itemsArray.length()) {
      val row = itemsArray.getJSONObject(j)
      val productId = row.optString("product_id")
      val productName = row.optString("product_name")
      val brand = row.optString("brand", "")
      val imageUrl = row.optString("image_url", "")
      val weightLabel = row.optString("weight_label", "1x")
      val unitPrice = row.optInt("unit_price", 0)
      val quantity = row.optInt("quantity", 1)
      val synthetic = com.kks.bharatkirana.data.model.Product(
        id = productId,
        name = productName,
        brand = brand,
        categoryId = "",
        currentPrice = unitPrice,
        originalPrice = unitPrice,
        unit = weightLabel,
        imageUrl = imageUrl,
        imageUrls = if (imageUrl.isNotBlank()) listOf(imageUrl) else emptyList()
      )
      list.add(
        com.kks.bharatkirana.data.model.CartItem(
          product = synthetic,
          selectedWeight = com.kks.bharatkirana.data.model.WeightOption(label = weightLabel, price = unitPrice),
          quantity = quantity
        )
      )
    }
    return list
  }

  /**
   * Fetch the line items of a single order. Used by the vendor's Realtime
   * INSERT handler, where the WebSocket payload only carries the `orders`
   * row and not its embedded children.
   */
  suspend fun fetchOrderItems(orderId: String, accessToken: String? = null): Result<List<com.kks.bharatkirana.data.model.CartItem>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val url = "${SupabaseConfig.restUrl}/order_items?order_id=eq.$orderId&select=*&order=id"
        val request = baseRequestBuilder(url, accessToken).get().build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: ""
        if (!response.isSuccessful) {
          throw Exception("Failed to fetch order items: HTTP ${response.code}")
        }
        parseOrderItems(JSONArray(body))
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

        // Persist line items in a single batch POST so vendors see the full
        // product breakdown. Denormalised snapshot fields (name, brand, image,
        // weight, unit_price) keep old orders readable even if the underlying
        // product later changes or is deleted. See ORDER_ITEMS_MIGRATION.sql.
        if (order.items.isNotEmpty()) {
          val itemsUrl = "${SupabaseConfig.restUrl}/order_items"
          val itemsArray = JSONArray().apply {
            order.items.forEach { ci ->
              val imageUrl = ci.product.imageUrls.firstOrNull { it.isNotBlank() }
                ?: ci.product.imageUrl
              put(
                JSONObject().apply {
                  put("order_id", order.id)
                  put("product_id", ci.product.id)
                  put("product_name", ci.product.name)
                  put("brand", ci.product.brand)
                  put("image_url", imageUrl)
                  put("weight_label", ci.selectedWeight.label)
                  put("unit_price", ci.selectedWeight.price)
                  put("quantity", ci.quantity)
                  put("line_total", ci.selectedWeight.price * ci.quantity)
                }
              )
            }
          }
          val itemsRequest = baseRequestBuilder(itemsUrl, accessToken)
            .addHeader("Prefer", "return=minimal")
            .post(itemsArray.toString().toRequestBody(jsonMediaType))
            .build()
          val itemsResponse = client.newCall(itemsRequest).execute()
          if (!itemsResponse.isSuccessful) {
            throw Exception("Failed to create order items: HTTP ${itemsResponse.code}")
          }
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
  suspend fun registerShop(
    shop: com.kks.bharatkirana.data.model.Shop,
    ownerId: String,
    accessToken: String? = null,
    shopImageUrl: String? = null,
    businessProofUrl: String? = null
  ): Result<Unit> =
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
          put("years_in_business", shop.yearsInBusiness)
          put("is_partner", false)
          put("accepting_orders", shop.isOpen)
          put("open_time", shop.openTime)
          put("close_time", shop.closeTime)
          // New shops start pending. An admin (web panel) flips this to
          // 'approved' or 'rejected' after reviewing the submitted details.
          // The vendor sees the Under Review screen until then.
          put("status", "pending")
          if (!shopImageUrl.isNullOrBlank()) put("image_url", shopImageUrl)
          if (!businessProofUrl.isNullOrBlank()) put("business_proof_url", businessProofUrl)
        }

        val shopRequest = baseRequestBuilder(shopUrl, accessToken)
          .post(shopPayload.toString().toRequestBody(jsonMediaType))
          .build()

        val shopResponse = client.newCall(shopRequest).execute()
        if (!shopResponse.isSuccessful) {
          // Include the server's response body so the specific reason (missing
          // column, RLS block, enum mismatch, etc.) is visible in the UI banner.
          val body = shopResponse.body?.string().orEmpty().take(400)
          throw Exception("Shop registration failed: HTTP ${shopResponse.code} — $body")
        }

        // profiles.shop_id is written by the on_shop_insert_link_profile trigger
        // (SHOPS_LINK_TRIGGER.sql) — the column is revoked from authenticated so
        // the client cannot do this itself. If that trigger is not installed,
        // vendor registration succeeds but shopId never sticks and the next
        // login treats the vendor as a customer.
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
    uploadImage("product-images", imageName, imageBytes, accessToken)

  // Round 7.2: generic Storage upload used by both product photos and vendor
  // registration documents (shop photo / business proof). Uses upsert so a retry
  // overwrites cleanly instead of silently swallowing a 409.
  suspend fun uploadImage(
    bucket: String,
    imageName: String,
    imageBytes: ByteArray,
    accessToken: String? = null,
    onProgress: (Int) -> Unit = {}
  ): Result<String> =
    withContext(Dispatchers.IO) {
      runCatching {
        val url = "${SupabaseConfig.storageUrl}/object/$bucket/$imageName"
        val body = ProgressRequestBody(imageBytes, "image/jpeg".toMediaType(), onProgress)
        val request = baseRequestBuilder(url, accessToken)
          .addHeader("x-upsert", "true")
          .post(body)
          .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
          val errBody = response.body?.string().orEmpty()
          throw Exception("Storage upload failed (${response.code}): $errBody")
        }
        onProgress(100)
        "${SupabaseConfig.storageUrl}/public/$bucket/$imageName"
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
          // wallet_balance and loyalty_points are likewise server-owned: this upsert
          // sends the whole row, so including them would overwrite a server-side
          // credit with whatever stale value the client happens to hold.
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
        // status filter keeps pending/rejected/suspended shops off the customer
        // home screen. Vendors see their own shop through profile.shop_id, not
        // this call, so the filter does not hide it from them.
        val url = "${SupabaseConfig.restUrl}/shops?status=eq.approved&select=*"
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
              rating = obj.optDouble("avg_rating", 0.0).toFloat(),
              ratingCount = obj.optInt("rating_count", 0),
              yearsInBusiness = obj.optInt("years_in_business", 0),
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
   * Community catalog search - looks up any product any vendor has already
   * added, so the next shopkeeper searching for "aashirvaad" can tap a result
   * instead of retyping the name, image URL, category, etc. DISTINCT-on-name
   * happens in the app layer because PostgREST doesn't support DISTINCT.
   */
  suspend fun searchProductsByName(query: String, accessToken: String? = null): Result<List<Product>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val q = query.trim()
        if (q.isBlank()) return@runCatching emptyList<Product>()
        val encoded = java.net.URLEncoder.encode("%$q%", "UTF-8")
        val url = "${SupabaseConfig.restUrl}/products?name=ilike.$encoded&select=id,name,brand,category_id,unit,description,image_url,image_urls,original_price,barcode&limit=25&order=name.asc"
        val request = baseRequestBuilder(url, accessToken).get().build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: ""
        if (!response.isSuccessful) throw Exception("Catalog search failed: HTTP ${response.code}")

        val array = JSONArray(body)
        val seen = HashSet<String>()
        val out = mutableListOf<Product>()
        for (i in 0 until array.length()) {
          val obj = array.getJSONObject(i)
          val name = obj.optString("name", "")
          if (name.isBlank()) continue
          val key = name.lowercase()
          if (key in seen) continue
          seen.add(key)
          val urls = mutableListOf<String>()
          obj.optJSONArray("image_urls")?.let { arr ->
            for (k in 0 until arr.length()) urls.add(arr.optString(k))
          }
          out.add(
            Product(
              id = "",
              name = name,
              brand = obj.optString("brand", ""),
              categoryId = obj.optString("category_id", ""),
              currentPrice = 0,
              originalPrice = obj.optInt("original_price", 0),
              unit = obj.optString("unit", "1 unit"),
              description = obj.optString("description", ""),
              imageUrl = urls.firstOrNull() ?: obj.optString("image_url", ""),
              imageUrls = urls,
              weightOptions = emptyList(),
              barcode = obj.optString("barcode", "")
            )
          )
        }
        out
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
              itemCap = o.optInt("item_cap", 500),
              priorityRank = o.optInt("priority_rank", 0),
              canPromote = o.optBoolean("can_promote", false),
              promoteDailyCapRupees = o.optInt("promote_daily_cap_rupees", 0),
              commissionPercent = o.optDouble("commission_percent", 0.0),
              features = features,
              tagline = o.optString("tagline", ""),
              hasBasicAnalytics = o.optBoolean("has_basic_analytics", false),
              hasFullAnalytics = o.optBoolean("has_full_analytics", false),
              hasPriorityPlacement = o.optBoolean("has_priority_placement", false),
              hasTopBoost = o.optBoolean("has_top_boost", false),
              hideBreakqBranding = o.optBoolean("hide_breakq_branding", false),
              hasWhatsappAlerts = o.optBoolean("has_whatsapp_alerts", false),
              hasMultiStaff = o.optBoolean("has_multi_staff", false),
              hasCompetitorPricing = o.optBoolean("has_competitor_pricing", false),
              isLimitedTime = o.optBoolean("is_limited_time", false),
              offerEndsAt = if (o.isNull("offer_ends_at")) null else o.optString("offer_ends_at")
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

  // Round 8: Razorpay orders MUST be created server-side (the key secret can
  // never ship in the APK). This calls the `create-razorpay-order` Edge Function,
  // which returns the order_id the Checkout SDK needs.
  data class RazorpayOrder(val orderId: String, val amountPaise: Int, val currency: String, val keyId: String)

  suspend fun createRazorpayOrder(
    shopId: String,
    tierId: String,
    accessToken: String? = null
  ): Result<RazorpayOrder> =
    withContext(Dispatchers.IO) {
      runCatching {
        val url = "${SupabaseConfig.functionsUrl}/create-razorpay-order"
        val payload = JSONObject().apply {
          put("shop_id", shopId)
          put("tier_id", tierId)
        }
        val request = baseRequestBuilder(url, accessToken)
          .post(payload.toString().toRequestBody(jsonMediaType))
          .build()
        val response = client.newCall(request).execute()
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) throw Exception("Could not start payment (${response.code}): $body")
        val o = JSONObject(body)
        RazorpayOrder(
          orderId = o.optString("order_id"),
          amountPaise = o.optInt("amount", 0),
          currency = o.optString("currency", "INR"),
          keyId = o.optString("key_id")
        )
      }
    }

  // Called after Razorpay Checkout succeeds. The Edge Function re-verifies the
  // HMAC signature against the key secret before flipping the vendor's tier —
  // the client's word alone is never trusted.
  suspend fun verifyRazorpayPayment(
    razorpayOrderId: String,
    razorpayPaymentId: String,
    razorpaySignature: String,
    accessToken: String? = null
  ): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        val url = "${SupabaseConfig.functionsUrl}/verify-razorpay-payment"
        val payload = JSONObject().apply {
          put("razorpay_order_id", razorpayOrderId)
          put("razorpay_payment_id", razorpayPaymentId)
          put("razorpay_signature", razorpaySignature)
        }
        val request = baseRequestBuilder(url, accessToken)
          .post(payload.toString().toRequestBody(jsonMediaType))
          .build()
        val response = client.newCall(request).execute()
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) throw Exception("Payment verification failed (${response.code}): $body")
      }
    }

  // Round 8: aggregates the shop_view_events table into the numbers the Advance
  // and Pro tiers pay for. Free-tier callers simply never invoke this.
  suspend fun fetchVendorAnalytics(shopId: String, accessToken: String? = null): Result<VendorAnalytics> =
    withContext(Dispatchers.IO) {
      runCatching {
        val url = "${SupabaseConfig.restUrl}/shop_view_events" +
          "?shop_id=eq.$shopId&select=event_type,search_term,product_id&limit=5000"
        val request = baseRequestBuilder(url, accessToken).get().build()
        val response = client.newCall(request).execute()
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) throw Exception("Failed to fetch analytics: HTTP ${response.code}")

        val array = JSONArray(body)
        var shopViews = 0
        var productViews = 0
        val searchCounts = mutableMapOf<String, Int>()
        for (i in 0 until array.length()) {
          val o = array.optJSONObject(i) ?: continue
          when (o.optString("event_type")) {
            "shop_view" -> shopViews++
            "product_view" -> productViews++
            "search_hit" -> {
              val term = o.optString("search_term").trim().lowercase()
              if (term.isNotBlank()) searchCounts[term] = (searchCounts[term] ?: 0) + 1
            }
          }
        }
        VendorAnalytics(
          shopViews = shopViews,
          productViews = productViews,
          topSearchedProducts = searchCounts.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key.replaceFirstChar { c -> c.uppercase() } to it.value }
        )
      }
    }

  // Fire-and-forget analytics ping from the customer app.
  suspend fun logShopViewEvent(
    shopId: String,
    eventType: String,
    productId: String? = null,
    searchTerm: String? = null,
    accessToken: String? = null
  ): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        val url = "${SupabaseConfig.restUrl}/shop_view_events"
        val payload = JSONObject().apply {
          put("shop_id", shopId)
          put("event_type", eventType)
          if (!productId.isNullOrBlank()) put("product_id", productId)
          if (!searchTerm.isNullOrBlank()) put("search_term", searchTerm)
        }
        val request = baseRequestBuilder(url, accessToken)
          .addHeader("Prefer", "return=minimal")
          .post(payload.toString().toRequestBody(jsonMediaType))
          .build()
        client.newCall(request).execute().close()
      }
    }
}

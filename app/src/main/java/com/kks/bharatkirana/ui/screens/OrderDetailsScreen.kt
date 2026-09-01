package com.kks.bharatkirana.ui.screens

import android.graphics.Color as AndroidColor
import android.location.Location
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import coil.compose.AsyncImage
import com.kks.bharatkirana.data.maps.MapplsConfig
import com.kks.bharatkirana.data.model.Order
import com.kks.bharatkirana.data.model.OrderStatus
import com.kks.bharatkirana.data.model.Shop
import com.kks.bharatkirana.ui.components.CustomQrCodePattern
import com.kks.bharatkirana.ui.components.OrderTimelineView
import com.kks.bharatkirana.ui.theme.*
import com.mappls.sdk.maps.MapView
import com.mappls.sdk.maps.MapplsMap
import com.mappls.sdk.maps.OnMapReadyCallback
import com.mappls.sdk.maps.annotations.MarkerOptions
import com.mappls.sdk.maps.annotations.PolylineOptions
import com.mappls.sdk.maps.camera.CameraPosition
import com.mappls.sdk.maps.geometry.LatLng

@Composable
fun OrderDetailsScreen(
  order: Order,
  onBackClick: () -> Unit,
  onReorder: (Order) -> Unit,
  onRateShop: (shopId: String, orderId: String, rating: Int, review: String) -> Unit = { _, _, _, _ -> },
  onCancelOrder: (String) -> Unit = {},
  hasAlreadyRated: Boolean = false,
  shopDistanceLabel: String? = null,
  shop: Shop? = null,
  userLocation: Location? = null,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val subtotal = order.items.sumOf { it.totalPrice }
  val bookedTotal = order.totalAmount
  val hasBillDifference = subtotal > 0 && subtotal != bookedTotal
  val handlingFee = if (hasBillDifference) (bookedTotal - subtotal).coerceAtLeast(0) else 0

  var ratingValue by remember { mutableIntStateOf(0) }
  var reviewText by remember { mutableStateOf("") }
  val showRatingForm = order.status == OrderStatus.COMPLETED && !hasAlreadyRated
  var ratingSubmitted by remember { mutableStateOf(false) }
  var ratingDismissed by remember { mutableStateOf(false) }
  var showCancelDialog by remember { mutableStateOf(false) }
  var showQrDialog by remember { mutableStateOf(false) }

  val canCancel = order.status == OrderStatus.PLACED || order.status == OrderStatus.CONFIRMED
  val isTerminal = order.status == OrderStatus.COMPLETED || order.status == OrderStatus.CANCELLED

  if (showCancelDialog) {
    AlertDialog(
      onDismissRequest = { showCancelDialog = false },
      containerColor = Color.White,
      title = { Text("Cancel this order?", fontWeight = FontWeight.Bold, color = BharatTextPrimary) },
      text = { Text("Order #${order.id} will be cancelled. This can't be undone \u2014 the shop will be notified.", color = BharatTextSecondary) },
      confirmButton = {
        Button(
          onClick = {
            showCancelDialog = false
            onCancelOrder(order.id)
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
        ) { Text("Yes, cancel", color = Color.White, fontWeight = FontWeight.Bold) }
      },
      dismissButton = {
        TextButton(onClick = { showCancelDialog = false }) {
          Text("Keep order", color = BharatPurplePrimary)
        }
      }
    )
  }

  if (showQrDialog) {
    AlertDialog(
      onDismissRequest = { showQrDialog = false },
      containerColor = Color.White,
      title = { Text("Pickup Code", fontWeight = FontWeight.Bold, color = BharatTextPrimary) },
      text = {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
          Box(
            modifier = Modifier.size(220.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(12.dp),
            contentAlignment = Alignment.Center
          ) {
            CustomQrCodePattern(tint = BharatPurpleDark)
          }
          Spacer(modifier = Modifier.height(12.dp))
          Text("Order #${order.id}", fontWeight = FontWeight.Bold, color = BharatTextPrimary)
          Text("Show this at the shop counter", fontSize = 12.sp, color = BharatTextSecondary)
        }
      },
      confirmButton = {
        Button(
          onClick = { showQrDialog = false },
          colors = ButtonDefaults.buttonColors(containerColor = BharatPurplePrimary)
        ) { Text("Done", color = Color.White, fontWeight = FontWeight.Bold) }
      }
    )
  }

  Surface(
    modifier = modifier.fillMaxSize(),
    color = BharatBackground
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding()
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(
          onClick = onBackClick,
          modifier = Modifier.testTag("order_details_back_button")
        ) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = BharatTextPrimary)
        }
        Spacer(modifier = Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Track Order",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = BharatTextPrimary
          )
          Text(
            text = "Order #${order.id}",
            fontSize = 12.sp,
            color = BharatPurplePrimary,
            fontWeight = FontWeight.SemiBold
          )
        }
        IconButton(onClick = { showQrDialog = true }) {
          Icon(Icons.Default.QrCode, contentDescription = "Show QR", tint = BharatPurplePrimary)
        }
      }

      Column(
        modifier = Modifier
          .weight(1f)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        StatusHeroCard(order = order)
        TimelineCard(order = order)

        val shopLat = shop?.lat ?: 0.0
        val shopLng = shop?.lng ?: 0.0
        val shopHasCoords = shop != null && shopLat != 0.0 && shopLng != 0.0
        if (shopHasCoords && shop != null) {
          MapCard(
            shopName = shop.name.ifBlank { order.storeName.ifBlank { "The shop" } },
            shopLocation = LatLng(shopLat, shopLng),
            userLocation = userLocation,
            onOpenDirections = {
              openDirections(context, shopLat, shopLng, shop.name.ifBlank { order.storeName })
            }
          )
        }

        StoreDetailsCard(
          shop = shop,
          fallbackName = order.storeName,
          fallbackAddress = order.storeAddress,
          pickupWindow = order.expectedPickupTime,
          distanceLabel = shopDistanceLabel,
          onCall = { phone ->
            val intent = android.content.Intent(android.content.Intent.ACTION_DIAL, "tel:$phone".toUri())
            context.startActivity(intent)
          }
        )

        ItemsCard(order = order, subtotal = subtotal, handlingFee = handlingFee)

        if (showRatingForm && !ratingSubmitted && !ratingDismissed) {
          RatingCard(
            ratingValue = ratingValue,
            onRatingChange = { ratingValue = it },
            reviewText = reviewText,
            onReviewChange = { reviewText = it },
            onSubmit = {
              onRateShop(order.shopId, order.id, ratingValue, reviewText)
              ratingSubmitted = true
            },
            onDismiss = { ratingDismissed = true }
          )
        } else if (ratingSubmitted) {
          Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = BharatGreen.copy(alpha = 0.1f)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.CheckCircle, contentDescription = null, tint = BharatGreen)
              Spacer(modifier = Modifier.width(12.dp))
              Text("Thank you for your feedback!", fontWeight = FontWeight.Bold, color = BharatGreen)
            }
          }
        }

        if (canCancel) {
          OutlinedButton(
            onClick = { showCancelDialog = true },
            modifier = Modifier.fillMaxWidth().testTag("cancel_order_button"),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFFDC2626))
          ) {
            Icon(Icons.Default.Close, null, tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Cancel Order", color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
          }
        }

        Spacer(modifier = Modifier.height(4.dp))
      }

      if (isTerminal) {
        Surface(color = Color.White, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
          Box(modifier = Modifier.padding(16.dp)) {
            Button(
              onClick = { onReorder(order) },
              modifier = Modifier.fillMaxWidth().height(48.dp).testTag("order_details_reorder_button"),
              colors = ButtonDefaults.buttonColors(containerColor = BharatPurplePrimary),
              shape = RoundedCornerShape(12.dp)
            ) {
              Icon(Icons.Default.Refresh, null, tint = Color.White, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("Reorder These Items", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun StatusHeroCard(order: Order) {
  val (headline, subline, tint) = statusCopy(order.status)
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(18.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier.size(56.dp).clip(CircleShape).background(tint.copy(alpha = 0.12f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = when (order.status) {
              OrderStatus.PLACED -> Icons.Default.ShoppingBag
              OrderStatus.CONFIRMED -> Icons.Default.CheckCircle
              OrderStatus.PREPARING -> Icons.Default.Restaurant
              OrderStatus.READY_FOR_PICKUP -> Icons.Default.Storefront
              OrderStatus.COMPLETED -> Icons.Default.CheckCircle
              OrderStatus.CANCELLED -> Icons.Default.Close
            },
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(28.dp)
          )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(headline, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = BharatTextPrimary)
          Text(subline, fontSize = 13.sp, color = BharatTextSecondary)
        }
      }
      Spacer(modifier = Modifier.height(12.dp))
      LinearProgressIndicator(
        progress = { statusProgress(order.status) },
        color = tint,
        trackColor = Color(0xFFF1F5F9),
        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp))
      )
    }
  }
}

private fun statusCopy(status: OrderStatus): Triple<String, String, Color> = when (status) {
  OrderStatus.PLACED -> Triple(
    "Order placed",
    "Waiting for the shop to accept your order.",
    Color(0xFF6C00FF)
  )
  OrderStatus.CONFIRMED -> Triple(
    "Order accepted",
    "The shop is getting ready to pack your items.",
    Color(0xFF0284C7)
  )
  OrderStatus.PREPARING -> Triple(
    "Being prepared",
    "Your order is being packed right now.",
    Color(0xFFD97706)
  )
  OrderStatus.READY_FOR_PICKUP -> Triple(
    "Ready for pickup",
    "Head over to the shop with your QR code.",
    Color(0xFF16A34A)
  )
  OrderStatus.COMPLETED -> Triple(
    "Order completed",
    "Hope you enjoyed shopping with us.",
    Color(0xFF16A34A)
  )
  OrderStatus.CANCELLED -> Triple(
    "Order cancelled",
    "This order was cancelled. No amount was charged.",
    Color(0xFFDC2626)
  )
}

private fun statusProgress(status: OrderStatus): Float = when (status) {
  OrderStatus.PLACED -> 0.15f
  OrderStatus.CONFIRMED -> 0.4f
  OrderStatus.PREPARING -> 0.65f
  OrderStatus.READY_FOR_PICKUP -> 0.9f
  OrderStatus.COMPLETED -> 1f
  OrderStatus.CANCELLED -> 1f
}

@Composable
private fun TimelineCard(order: Order) {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(18.dp)) {
      Text("Order Progress", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = BharatTextPrimary)
      Spacer(modifier = Modifier.height(12.dp))
      OrderTimelineView(timeline = order.timeline)
    }
  }
}

@Composable
private fun MapCard(
  shopName: String,
  shopLocation: LatLng,
  userLocation: Location?,
  onOpenDirections: () -> Unit
) {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenDirections)
  ) {
    Column(modifier = Modifier.padding(bottom = 0.dp)) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(180.dp)
          .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
      ) {
        if (MapplsConfig.isConfigured) {
          TrackerMap(
            shopName = shopName,
            shopLocation = shopLocation,
            userLocation = userLocation,
            modifier = Modifier.fillMaxSize()
          )
        } else {
          MapPlaceholder(shopName = shopName)
        }
        // "Tap for directions" pill overlay so the customer knows the whole
        // card is actionable even before they tap.
        Surface(
          color = Color.White,
          shape = RoundedCornerShape(20.dp),
          shadowElevation = 4.dp,
          modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.Default.DirectionsCar, null, tint = BharatPurplePrimary, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Directions", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BharatPurplePrimary)
          }
        }
      }
      Row(
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(Icons.Default.LocationOn, null, tint = BharatPurplePrimary, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = "Tap to open directions in Maps",
          fontSize = 12.sp,
          color = BharatTextSecondary
        )
      }
    }
  }
}

@Composable
private fun MapPlaceholder(shopName: String) {
  Box(
    modifier = Modifier.fillMaxSize().background(BharatPurpleContainer),
    contentAlignment = Alignment.Center
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Icon(Icons.Default.LocationOn, null, tint = BharatPurplePrimary, modifier = Modifier.size(44.dp))
      Spacer(modifier = Modifier.height(8.dp))
      Text(shopName, fontWeight = FontWeight.Bold, color = BharatTextPrimary)
      Text("Tap to see the route", fontSize = 12.sp, color = BharatTextSecondary)
    }
  }
}

private fun openDirections(context: android.content.Context, lat: Double, lng: Double, label: String) {
  val encodedLabel = android.net.Uri.encode(label.ifBlank { "Shop" })
  // Prefer Google Maps navigation intent for turn-by-turn; fall back to a
  // generic geo: URI that any installed map app can handle.
  val navUri = "google.navigation:q=$lat,$lng&mode=d".toUri()
  val navIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, navUri).apply {
    setPackage("com.google.android.apps.maps")
  }
  runCatching { context.startActivity(navIntent) }.onFailure {
    val geoUri = "geo:$lat,$lng?q=$lat,$lng($encodedLabel)".toUri()
    val fallback = android.content.Intent(android.content.Intent.ACTION_VIEW, geoUri)
    runCatching { context.startActivity(fallback) }
  }
}

@Composable
private fun TrackerMap(
  shopName: String,
  shopLocation: LatLng,
  userLocation: Location?,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val mapView = remember { MapView(context) }

  DisposableEffect(lifecycleOwner, mapView) {
    val observer = object : DefaultLifecycleObserver {
      override fun onCreate(owner: LifecycleOwner) = mapView.onCreate(null)
      override fun onStart(owner: LifecycleOwner) = mapView.onStart()
      override fun onResume(owner: LifecycleOwner) = mapView.onResume()
      override fun onPause(owner: LifecycleOwner) = mapView.onPause()
      override fun onStop(owner: LifecycleOwner) = mapView.onStop()
      override fun onDestroy(owner: LifecycleOwner) = mapView.onDestroy()
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  AndroidView(
    modifier = modifier,
    factory = { mapView },
    update = { view ->
      view.getMapAsync(object : OnMapReadyCallback {
        override fun onMapReady(mapplsMap: MapplsMap) {
          mapplsMap.clear()
          mapplsMap.addMarker(MarkerOptions().position(shopLocation).title(shopName))

          var targetLat = shopLocation.latitude
          var targetLng = shopLocation.longitude
          var zoom = 15.0

          if (userLocation != null) {
            val userLatLng = LatLng(userLocation)
            mapplsMap.addMarker(MarkerOptions().position(userLatLng).title("You"))
            mapplsMap.addPolyline(
              PolylineOptions()
                .add(userLatLng)
                .add(shopLocation)
                .color(AndroidColor.parseColor("#6C00FF"))
                .width(4f)
            )
            targetLat = (shopLocation.latitude + userLatLng.latitude) / 2
            targetLng = (shopLocation.longitude + userLatLng.longitude) / 2
            zoom = 13.5
          }

          mapplsMap.cameraPosition = CameraPosition.Builder()
            .target(LatLng(targetLat, targetLng))
            .zoom(zoom)
            .build()
        }

        override fun onMapError(code: Int, message: String?) { }
      })
    }
  )
}

@Composable
private fun StoreDetailsCard(
  shop: Shop?,
  fallbackName: String,
  fallbackAddress: String,
  pickupWindow: String,
  distanceLabel: String?,
  onCall: (String) -> Unit
) {
  val name = shop?.name?.takeIf { it.isNotBlank() } ?: fallbackName.ifBlank { "The shop" }
  val address = shop?.address?.takeIf { it.isNotBlank() } ?: fallbackAddress
  val phone = shop?.phone?.takeIf { it.isNotBlank() }

  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(18.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier.size(44.dp).clip(CircleShape).background(BharatPurpleContainer),
          contentAlignment = Alignment.Center
        ) {
          Icon(Icons.Default.Storefront, null, tint = BharatPurplePrimary, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(name, fontWeight = FontWeight.Bold, color = BharatTextPrimary)
          if (address.isNotBlank()) {
            Text(address, fontSize = 12.sp, color = BharatTextSecondary, maxLines = 2)
          }
          if (!distanceLabel.isNullOrBlank() && distanceLabel != "---") {
            Text(
              text = "$distanceLabel away",
              fontSize = 11.sp,
              color = BharatPurplePrimary,
              fontWeight = FontWeight.SemiBold
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Card(
          shape = RoundedCornerShape(12.dp),
          colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
          modifier = Modifier.weight(1f)
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Text("Pickup window", fontSize = 10.sp, color = BharatTextMuted, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(pickupWindow, fontSize = 12.sp, color = BharatTextPrimary, fontWeight = FontWeight.SemiBold)
          }
        }
        if (phone != null) {
          OutlinedButton(
            onClick = { onCall(phone) },
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, BharatPurplePrimary),
            modifier = Modifier.height(64.dp)
          ) {
            Icon(Icons.Default.Call, null, tint = BharatPurplePrimary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Call shop", color = BharatPurplePrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
          }
        }
      }
    }
  }
}

@Composable
private fun ItemsCard(order: Order, subtotal: Int, handlingFee: Int) {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(18.dp)) {
      Text(
        text = if (order.items.isEmpty()) "Order summary" else "Items ordered (${order.items.size})",
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        color = BharatTextPrimary
      )
      Spacer(modifier = Modifier.height(12.dp))

      if (order.items.isEmpty()) {
        Text(
          text = "Item details will appear once the shop confirms the order.",
          fontSize = 12.sp,
          color = BharatTextSecondary
        )
      } else {
        order.items.forEach { item ->
          Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            val previewUrl = item.product.imageUrls.firstOrNull { it.isNotBlank() }
              ?: item.product.imageUrl.takeIf { it.isNotBlank() }
            Box(
              modifier = Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFF1F5F9)),
              contentAlignment = Alignment.Center
            ) {
              if (previewUrl != null) {
                AsyncImage(
                  model = previewUrl,
                  contentDescription = item.product.name,
                  modifier = Modifier.fillMaxSize(),
                  contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
              } else {
                Icon(Icons.Default.ShoppingCart, null, tint = BharatPurpleAccent, modifier = Modifier.size(20.dp))
              }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(item.product.name, fontWeight = FontWeight.Bold, color = BharatTextPrimary, fontSize = 13.sp, maxLines = 1)
              Text(
                text = "${item.selectedWeight.label} \u00b7 Qty ${item.quantity} \u00b7 \u20b9${item.selectedWeight.price} each",
                fontSize = 11.sp,
                color = BharatTextSecondary,
                maxLines = 1
              )
            }
            Text("\u20b9${item.totalPrice}", fontWeight = FontWeight.Bold, color = BharatTextPrimary, fontSize = 13.sp)
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))
      HorizontalDivider(color = Color(0xFFF1F5F9))
      Spacer(modifier = Modifier.height(12.dp))

      if (subtotal > 0) {
        BillRow("Subtotal", "\u20b9$subtotal")
        if (handlingFee > 0) {
          Spacer(modifier = Modifier.height(4.dp))
          BillRow("Handling & extras", "\u20b9$handlingFee")
        }
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = Color(0xFFF1F5F9))
        Spacer(modifier = Modifier.height(8.dp))
      }
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("Total", fontWeight = FontWeight.Bold, color = BharatTextPrimary)
        Text(
          "\u20b9${order.totalAmount}",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
          color = BharatTextPrimary
        )
      }
    }
  }
}

@Composable
private fun BillRow(label: String, value: String) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(label, color = BharatTextSecondary, fontSize = 13.sp)
    Text(value, color = BharatTextPrimary, fontSize = 13.sp)
  }
}

@Composable
private fun RatingCard(
  ratingValue: Int,
  onRatingChange: (Int) -> Unit,
  reviewText: String,
  onReviewChange: (String) -> Unit,
  onSubmit: () -> Unit,
  onDismiss: () -> Unit
) {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = BharatPurpleContainer.copy(alpha = 0.4f)),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Spacer(modifier = Modifier.width(24.dp))
        Text("Rate your experience", fontWeight = FontWeight.Bold, color = BharatPurpleDark)
        IconButton(
          onClick = onDismiss,
          modifier = Modifier.size(24.dp).testTag("dismiss_rating_button")
        ) {
          Icon(Icons.Default.Close, null, tint = BharatPurpleDark, modifier = Modifier.size(18.dp))
        }
      }
      Spacer(modifier = Modifier.height(12.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(5) { index ->
          val starIndex = index + 1
          Icon(
            imageVector = if (starIndex <= ratingValue) Icons.Default.Star else Icons.Default.StarBorder,
            contentDescription = null,
            tint = if (starIndex <= ratingValue) Color(0xFFFFB800) else BharatTextMuted,
            modifier = Modifier.size(32.dp).clickable { onRatingChange(starIndex) }
          )
        }
      }
      Spacer(modifier = Modifier.height(14.dp))
      OutlinedTextField(
        value = reviewText,
        onValueChange = onReviewChange,
        placeholder = { Text("Write a quick review\u2026", fontSize = 13.sp) },
        modifier = Modifier.fillMaxWidth().height(78.dp),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BharatPurplePrimary)
      )
      Spacer(modifier = Modifier.height(12.dp))
      Button(
        onClick = onSubmit,
        enabled = ratingValue > 0,
        colors = ButtonDefaults.buttonColors(containerColor = BharatPurplePrimary),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
      ) { Text("Submit Rating", fontWeight = FontWeight.Bold, color = Color.White) }
    }
  }
}

package com.kks.bharatkirana.ui.screens

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.location.Location
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.kks.bharatkirana.R
import com.kks.bharatkirana.data.maps.MapplsConfig
import com.kks.bharatkirana.data.model.Shop
import com.kks.bharatkirana.data.model.isCurrentlyOpen
import com.kks.bharatkirana.ui.theme.BharatBackground
import com.kks.bharatkirana.ui.theme.BharatGreen
import com.kks.bharatkirana.ui.theme.BharatPurpleContainer
import com.kks.bharatkirana.ui.theme.BharatPurpleDark
import com.kks.bharatkirana.ui.theme.BharatPurplePrimary
import com.kks.bharatkirana.ui.theme.BharatTextPrimary
import com.kks.bharatkirana.ui.theme.BharatTextSecondary
import com.mappls.sdk.maps.MapView
import com.mappls.sdk.maps.MapplsMap
import com.mappls.sdk.maps.OnMapReadyCallback
import com.mappls.sdk.maps.annotations.MarkerOptions
import com.mappls.sdk.maps.annotations.PolylineOptions
import com.mappls.sdk.maps.camera.CameraPosition
import com.mappls.sdk.maps.geometry.LatLng

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreInfoScreen(
  shop: Shop?,
  onBackClick: () -> Unit,
  onViewCatalog: () -> Unit,
  onOpenDirections: (address: String, lat: Double, lng: Double) -> Unit = { _, _, _ -> },
  userLocation: Location? = null,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val storeName = shop?.name?.takeIf { it.isNotBlank() } ?: "BreakQ Store"
  val storePhone = shop?.phone?.takeIf { it.isNotBlank() } ?: "+91 9876543210"
  val storeAddress = shop?.address?.takeIf { it.isNotBlank() } ?: "Banjara Hills Rd 12, Hyderabad, TS 500034"
  val storeHours = if (shop != null) "${shop.openTime} – ${shop.closeTime}" else "7:00 AM – 10:30 PM"
  val hasCoords = shop != null && (shop.lat != 0.0 || shop.lng != 0.0)

  // Helper to open Google Maps
  val openMaps = {
    onOpenDirections(storeAddress, shop?.lat ?: 0.0, shop?.lng ?: 0.0)
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = "Store Information",
            style = MaterialTheme.typography.titleLarge.copy(
              fontWeight = FontWeight.Bold,
              fontSize = 20.sp
            ),
            color = BharatTextPrimary
          )
        },
        navigationIcon = {
          IconButton(onClick = onBackClick) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back",
              tint = BharatTextPrimary
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
      )
    },
    modifier = modifier
      .fillMaxSize()
      .background(BharatBackground)
      .statusBarsPadding()
      .navigationBarsPadding()
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      item { Spacer(modifier = Modifier.height(8.dp)) }

      // Map Snippet (Clickable) — live embedded map with a route line from the
      // user's current location to the store once Mappls keys are configured
      // (see .env.example); otherwise falls back to the static "open in Maps" card.
      item {
        Card(
          onClick = openMaps,
          shape = RoundedCornerShape(24.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
          if (MapplsConfig.isConfigured && hasCoords) {
            Box(modifier = Modifier.fillMaxSize()) {
              StoreRouteMap(
                storeLocation = LatLng(shop!!.lat, shop.lng),
                storeName = storeName,
                userLocation = userLocation,
                modifier = Modifier.fillMaxSize()
              )
              // "Tap to open full directions" overlay — the embedded map is a
              // preview; tapping still hands off to Maps for turn-by-turn nav.
              Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(50.dp),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
              ) {
                Text(
                  text = "Tap for turn-by-turn directions",
                  color = Color.White,
                  fontSize = 11.sp,
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
              }
            }
          } else {
            Box(modifier = Modifier.fillMaxSize()) {
              Image(
                painter = painterResource(id = R.drawable.img_onboarding_delivery), // Placeholder map image
                contentDescription = "Store Location Map",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
              )
              Box(
                modifier = Modifier
                  .fillMaxSize()
                  .background(Color.Black.copy(alpha = 0.05f))
              )
              // Centered Marker
              Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = BharatPurplePrimary,
                modifier = Modifier
                  .size(48.dp)
                  .align(Alignment.Center)
              )

              // "Tap to view on Maps" overlay
              Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(50.dp),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
              ) {
                Text(
                  text = if (hasCoords) "Tap to view exact location on Maps" else "Tap to search this address on Maps",
                  color = Color.White,
                  fontSize = 11.sp,
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
              }
            }
          }
        }
      }

      // Address Card
      item {
        StoreDetailCard(
          icon = Icons.Default.Storefront,
          title = storeName,
          subtitle = storeAddress,
          badge = if (shop?.isCurrentlyOpen() != false) "Open Now" else "Closed"
        )
      }

      // Round 7: distance + rating pill row so the customer immediately knows how
      // far the shop is and how it's rated. Uses ViewModel-computed shop.distance
      // (Haversine) — falls back gracefully when we lack coordinates.
      if (shop != null) {
        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            if (shop.distance.isNotBlank() && shop.distance != "---") {
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = BharatPurpleContainer,
                modifier = Modifier.weight(1f)
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(Icons.Default.LocationOn, contentDescription = null, tint = BharatPurpleDark, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Column {
                    Text("Distance", fontSize = 10.sp, color = BharatTextSecondary, fontWeight = FontWeight.SemiBold)
                    Text("${shop.distance} away", fontSize = 13.sp, color = BharatPurpleDark, fontWeight = FontWeight.Bold)
                  }
                }
              }
            }
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = Color(0xFFFEF3C7),
              modifier = Modifier.weight(1f)
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                  Text("Rating", fontSize = 10.sp, color = BharatTextSecondary, fontWeight = FontWeight.SemiBold)
                  Text(
                    text = if (shop.ratingCount > 0)
                      "${"%.1f".format(shop.rating)} · ${shop.ratingCount} review${if (shop.ratingCount == 1) "" else "s"}"
                    else
                      "No ratings yet",
                    fontSize = 13.sp,
                    color = Color(0xFFD97706),
                    fontWeight = FontWeight.Bold
                  )
                }
              }
            }
          }
        }
      }

      // Hours Card
      item {
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White),
          border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.Default.Schedule, contentDescription = null, tint = BharatPurplePrimary)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(text = "Operating Hours", fontWeight = FontWeight.Bold, color = BharatTextPrimary)
              Text(text = storeHours, color = BharatTextSecondary, fontSize = 14.sp)
            }
          }
        }
      }

      // View Catalog CTA
      item {
        Button(
          onClick = onViewCatalog,
          modifier = Modifier.fillMaxWidth().height(56.dp),
          colors = ButtonDefaults.buttonColors(containerColor = BharatPurplePrimary),
          shape = RoundedCornerShape(16.dp)
        ) {
          Text(text = "View Product Catalog", fontWeight = FontWeight.Bold)
        }
      }

      // Action Buttons (Call & Directions)
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          // Call Store Button
          Button(
            onClick = {
              val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$storePhone"))
              context.startActivity(intent)
            },
            modifier = Modifier
              .weight(1f)
              .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BharatPurplePrimary),
            shape = RoundedCornerShape(16.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Call,
              contentDescription = null,
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Call Store",
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold
            )
          }

          // Directions Button
          OutlinedButton(
            onClick = openMaps,
            modifier = Modifier
              .weight(1f)
              .height(56.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = BharatPurplePrimary),
            border = BorderStroke(1.5.dp, BharatPurplePrimary),
            shape = RoundedCornerShape(16.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Directions,
              contentDescription = null,
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Directions",
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold
            )
          }
        }
      }

      item { Spacer(modifier = Modifier.height(24.dp)) }
    }
  }
}

/**
 * Live Mappls map showing the store and (when available) the user's current
 * location, connected by a route line — the in-app preview equivalent of what
 * Zepto/Blinkit show before handing off to full turn-by-turn navigation.
 */
@Composable
private fun StoreRouteMap(
  storeLocation: LatLng,
  storeName: String,
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
          mapplsMap.addMarker(MarkerOptions().position(storeLocation).title(storeName))

          var targetLat = storeLocation.latitude
          var targetLng = storeLocation.longitude
          var zoom = 15.0

          if (userLocation != null) {
            val userLatLng = LatLng(userLocation)
            mapplsMap.addMarker(MarkerOptions().position(userLatLng).title("Your location"))
            mapplsMap.addPolyline(
              PolylineOptions()
                .add(userLatLng)
                .add(storeLocation)
                .color(AndroidColor.parseColor("#6C00FF"))
                .width(4f)
            )
            targetLat = (storeLocation.latitude + userLatLng.latitude) / 2
            targetLng = (storeLocation.longitude + userLatLng.longitude) / 2
            zoom = 13.5
          }

          mapplsMap.cameraPosition = CameraPosition.Builder()
            .target(LatLng(targetLat, targetLng))
            .zoom(zoom)
            .build()
        }

        override fun onMapError(code: Int, message: String?) { /* falls back to the static card's own error state visually — nothing to draw */ }
      })
    }
  )
}

@Composable
fun StoreDetailCard(
  icon: ImageVector,
  title: String,
  subtitle: String,
  badge: String
) {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier.padding(20.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(52.dp)
          .clip(CircleShape)
          .background(BharatPurpleContainer),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = BharatPurplePrimary,
          modifier = Modifier.size(28.dp)
        )
      }
      Spacer(modifier = Modifier.width(16.dp))
      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = BharatTextPrimary
          )
          Spacer(modifier = Modifier.width(8.dp))
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(6.dp))
              .background(BharatGreen.copy(alpha = 0.1f))
              .padding(horizontal = 6.dp, vertical = 2.dp)
          ) {
            Text(
              text = badge,
              color = BharatGreen,
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold
            )
          }
        }
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = BharatTextSecondary,
          modifier = Modifier.padding(top = 2.dp)
        )
      }
    }
  }
}

@Composable
fun ServiceInfoRow(
  icon: ImageVector,
  title: String,
  description: String
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .background(Color.White)
      .padding(16.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .size(40.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(Color(0xFFF8FAFC)),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = BharatPurplePrimary,
        modifier = Modifier.size(22.dp)
      )
    }
    Spacer(modifier = Modifier.width(16.dp))
    Column {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
        color = BharatTextPrimary
      )
      Text(
        text = description,
        style = MaterialTheme.typography.bodySmall,
        color = BharatTextSecondary
      )
    }
  }
}

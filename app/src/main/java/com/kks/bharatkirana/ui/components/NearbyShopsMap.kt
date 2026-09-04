package com.kks.bharatkirana.ui.components

import android.location.Location
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.kks.bharatkirana.data.model.Shop
import com.kks.bharatkirana.data.model.isCurrentlyOpen
import com.mappls.sdk.maps.MapView
import com.mappls.sdk.maps.MapplsMap
import com.mappls.sdk.maps.OnMapReadyCallback
import com.mappls.sdk.maps.annotations.MarkerOptions
import com.mappls.sdk.maps.camera.CameraPosition
import com.mappls.sdk.maps.geometry.LatLng
import kotlin.math.max

/**
 * Real Mappls-backed map showing every eligible shop from Supabase as a clickable
 * pin. Marker tap identifies the exact shop via a lookup keyed on the pin's LatLng,
 * then hands off to the caller-supplied onShopMarkerClick.
 *
 * Shared by NearbyShopsScreen (full-screen map view) and HomeScreen (preview card).
 */
@Composable
fun NearbyShopsMap(
  shops: List<Shop>,
  userLocation: Location?,
  onShopMarkerClick: (Shop) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val mapView = remember { MapView(context) }

  // Skip shops whose vendor never dropped a pin — otherwise they'd cluster at (0,0).
  val plottable = remember(shops) { shops.filter { it.lat != 0.0 || it.lng != 0.0 } }

  DisposableEffect(lifecycleOwner, mapView) {
    var destroyed = false
    val observer = object : DefaultLifecycleObserver {
      override fun onCreate(owner: LifecycleOwner) = mapView.onCreate(null)
      override fun onStart(owner: LifecycleOwner) = mapView.onStart()
      override fun onResume(owner: LifecycleOwner) = mapView.onResume()
      override fun onPause(owner: LifecycleOwner) = mapView.onPause()
      override fun onStop(owner: LifecycleOwner) = mapView.onStop()
      override fun onDestroy(owner: LifecycleOwner) {
        destroyed = true
        mapView.onDestroy()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      // Home hosts this inside a LazyColumn, so the card is disposed every time it
      // scrolls out of view. Without an explicit destroy each pass leaks a MapView.
      if (!destroyed) {
        destroyed = true
        runCatching { mapView.onDestroy() }
      }
    }
  }

  AndroidView(
    modifier = modifier,
    factory = { mapView },
    update = { view ->
      view.getMapAsync(object : OnMapReadyCallback {
        override fun onMapReady(mapplsMap: MapplsMap) {
          mapplsMap.clear()

          // Key shops by "lat,lng" string so we can look them up when the user taps
          // a marker (Mappls returns the Marker object; we round-trip through position).
          val shopByPosition = mutableMapOf<String, Shop>()
          plottable.forEach { shop ->
            val key = "${shop.lat},${shop.lng}"
            shopByPosition[key] = shop
            val snippetBits = buildList {
              if (shop.rating > 0f) add("★ ${"%.1f".format(shop.rating)}")
              if (shop.distance.isNotBlank() && shop.distance != "---") add(shop.distance)
              if (shop.isCurrentlyOpen()) add("Open") else add("Closed")
            }
            mapplsMap.addMarker(
              MarkerOptions()
                .position(LatLng(shop.lat, shop.lng))
                .title(shop.name)
                .snippet(snippetBits.joinToString(" · "))
            )
          }

          if (userLocation != null) {
            mapplsMap.addMarker(
              MarkerOptions()
                .position(LatLng(userLocation.latitude, userLocation.longitude))
                .title("You are here")
            )
          }

          mapplsMap.setOnMarkerClickListener { marker ->
            val pos = marker.position ?: return@setOnMarkerClickListener false
            val shop = shopByPosition["${pos.latitude},${pos.longitude}"]
            if (shop != null) {
              onShopMarkerClick(shop)
              true
            } else {
              false
            }
          }

          // Camera: center on the average of every plotted point (customer + shops)
          // and pick a zoom that (roughly) fits them all. Mappls' newLatLngBounds
          // isn't consistent across SDK versions, so we compute manually.
          val allPoints = buildList {
            plottable.forEach { add(LatLng(it.lat, it.lng)) }
            if (userLocation != null) add(LatLng(userLocation.latitude, userLocation.longitude))
          }
          if (allPoints.isNotEmpty()) {
            val centerLat = allPoints.sumOf { it.latitude } / allPoints.size
            val centerLng = allPoints.sumOf { it.longitude } / allPoints.size
            val spanLat = (allPoints.maxOf { it.latitude } - allPoints.minOf { it.latitude })
            val spanLng = (allPoints.maxOf { it.longitude } - allPoints.minOf { it.longitude })
            val span = max(spanLat, spanLng)
            val zoom = when {
              span > 0.5 -> 9.0
              span > 0.2 -> 11.0
              span > 0.05 -> 13.0
              span > 0.01 -> 14.5
              else -> 15.5
            }
            mapplsMap.cameraPosition = CameraPosition.Builder()
              .target(LatLng(centerLat, centerLng))
              .zoom(zoom)
              .build()
          }
        }

        override fun onMapError(code: Int, message: String?) { /* fall back to fallback UI */ }
      })
    }
  )
}

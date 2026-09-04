package com.kks.bharatkirana.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.mappls.sdk.maps.MapView
import com.mappls.sdk.maps.MapplsMap
import com.mappls.sdk.maps.OnMapReadyCallback
import com.mappls.sdk.maps.annotations.MarkerOptions
import com.mappls.sdk.maps.camera.CameraPosition
import com.mappls.sdk.maps.geometry.LatLng

/**
 * Tap-to-drop pin picker used by the delivery-address flow. Built on the same
 * Mappls APIs already proven by NearbyShopsMap — no new SDK surface.
 */
@Composable
fun LocationPickerMap(
  lat: Double?,
  lng: Double?,
  onPick: (Double, Double) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val mapView = remember { MapView(context) }

  // The click listener is attached once per map instance, so it must read the
  // newest callback rather than the one captured at attach time.
  val currentOnPick by rememberUpdatedState(onPick)
  val listenerAttached = remember { booleanArrayOf(false) }

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

          val hasPin = lat != null && lng != null && (lat != 0.0 || lng != 0.0)
          if (hasPin) {
            mapplsMap.addMarker(
              MarkerOptions()
                .position(LatLng(lat!!, lng!!))
                .title("Delivery location")
            )
            mapplsMap.cameraPosition = CameraPosition.Builder()
              .target(LatLng(lat, lng!!))
              .zoom(16.5)
              .build()
          }

          if (!listenerAttached[0]) {
            listenerAttached[0] = true
            mapplsMap.addOnMapClickListener { point ->
              currentOnPick(point.latitude, point.longitude)
              true
            }
          }
        }

        override fun onMapError(code: Int, message: String?) { /* caller shows fallback */ }
      })
    }
  )
}

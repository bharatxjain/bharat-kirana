package com.kks.bharatkirana

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.kks.bharatkirana.service.MyFirebaseMessagingService
import com.kks.bharatkirana.ui.screens.MainScreen
import com.kks.bharatkirana.ui.theme.BharatKiranaTheme
import com.kks.bharatkirana.ui.viewmodel.GroceryViewModel

class MainActivity : ComponentActivity() {
  private val viewModel: GroceryViewModel by viewModels()

  private val notificationPermissionLauncher =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Round 4b: register the FCM channel up-front so the very first push renders
    // correctly on Android 8+ even if MyFirebaseMessagingService hasn't run yet.
    MyFirebaseMessagingService.ensureChannel(this)

    // Android 13+ requires this runtime permission — without it, notify() calls in
    // MyFirebaseMessagingService silently no-op and pushes never appear.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
      notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // Handle deep link from Supabase Magic Link
    intent?.data?.let { uri ->
      handleDeepLink(uri)
    }

    // Round 4b: user tapped an FCM push — route them to the right screen.
    handlePushIntent(intent)

    setContent {
      BharatKiranaTheme {
        MainScreen(
          viewModel = viewModel,
          modifier = Modifier.fillMaxSize()
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    // Handle deep link if app is already open
    intent.data?.let { uri ->
      handleDeepLink(uri)
    }
    handlePushIntent(intent)
  }

  private fun handleDeepLink(uri: android.net.Uri) {
    if (uri.scheme == "bharatkirana" && uri.host == "auth-callback") {
      // Supabase fragment contains tokens: #access_token=...&type=recovery
      val fragment = uri.fragment ?: ""
      if (fragment.contains("type=recovery")) {
        val accessToken = fragment.split("&")
          .find { it.startsWith("access_token=") }
          ?.substringAfter("access_token=")

        if (accessToken != null) {
          viewModel.navigateToResetPassword(accessToken)
        }
      }
    }
  }

  private fun handlePushIntent(intent: Intent?) {
    if (intent == null) return
    if (!intent.getBooleanExtra(MyFirebaseMessagingService.EXTRA_FROM_PUSH, false)) return
    val orderId = intent.getStringExtra(MyFirebaseMessagingService.EXTRA_ORDER_ID)
    viewModel.handleNotificationTap(orderId)
    // Consume the extra so orientation changes don't re-trigger it.
    intent.removeExtra(MyFirebaseMessagingService.EXTRA_FROM_PUSH)
    intent.removeExtra(MyFirebaseMessagingService.EXTRA_ORDER_ID)
  }
}


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
import androidx.lifecycle.lifecycleScope
import com.kks.bharatkirana.data.maps.MapplsConfig
import com.kks.bharatkirana.service.MyFirebaseMessagingService
import com.kks.bharatkirana.ui.screens.MainScreen
import com.kks.bharatkirana.ui.theme.BharatKiranaTheme
import com.kks.bharatkirana.ui.viewmodel.GroceryViewModel
import com.razorpay.Checkout
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainActivity : ComponentActivity(), PaymentResultWithDataListener {
  private val viewModel: GroceryViewModel by viewModels()

  // Remembered so onPaymentSuccess can tell the ViewModel which plan was bought.
  private var pendingCheckoutTierName: String = ""

  private val notificationPermissionLauncher =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Round 4b: register the FCM channel up-front so the very first push renders
    // correctly on Android 8+ even if MyFirebaseMessagingService hasn't run yet.
    MyFirebaseMessagingService.ensureChannel(this)

    // Must run before any screen tries to render a MapplsMap. No-ops until real
    // keys are added to .env — see MapplsConfig.
    MapplsConfig.initialize(this)

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

    // Round 8: Razorpay Checkout must be opened from an Activity, so the
    // ViewModel signals readiness and we present the sheet here.
    lifecycleScope.launch {
      viewModel.checkoutState.collectLatest { state ->
        if (state is GroceryViewModel.CheckoutState.ReadyToPay) {
          pendingCheckoutTierName = state.tierName
          openRazorpayCheckout(state)
        }
      }
    }

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

  private fun openRazorpayCheckout(state: GroceryViewModel.CheckoutState.ReadyToPay) {
    try {
      val checkout = Checkout()
      checkout.setKeyID(state.keyId)
      val profile = viewModel.userProfile.value
      val options = JSONObject().apply {
        put("name", "BreakQ")
        put("description", "${state.tierName} plan — monthly subscription")
        put("currency", state.currency)
        put("amount", state.amountPaise)
        put("order_id", state.orderId)
        put("prefill", JSONObject().apply {
          put("email", profile.email)
          put("contact", profile.mobileNumber)
        })
        put("theme", JSONObject().apply { put("color", "#6C00FF") })
        put("retry", JSONObject().apply { put("enabled", true); put("max_count", 3) })
      }
      checkout.open(this, options)
    } catch (e: Exception) {
      viewModel.onRazorpayFailure(e.message ?: "Could not open payment screen.")
    }
  }

  override fun onPaymentSuccess(razorpayPaymentId: String?, paymentData: PaymentData?) {
    val orderId = paymentData?.orderId
    val signature = paymentData?.signature
    if (razorpayPaymentId.isNullOrBlank() || orderId.isNullOrBlank() || signature.isNullOrBlank()) {
      viewModel.onRazorpayFailure("Payment completed but details were missing. Please contact support.")
      return
    }
    viewModel.onRazorpaySuccess(orderId, razorpayPaymentId, signature, pendingCheckoutTierName)
  }

  override fun onPaymentError(code: Int, description: String?, paymentData: PaymentData?) {
    viewModel.onRazorpayFailure(description ?: "Payment was cancelled or failed.")
  }
}


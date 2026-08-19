package com.kks.bharatkirana

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.kks.bharatkirana.ui.screens.MainScreen
import com.kks.bharatkirana.ui.theme.BharatKiranaTheme
import com.kks.bharatkirana.ui.viewmodel.GroceryViewModel

class MainActivity : ComponentActivity() {
  private val viewModel: GroceryViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Handle deep link from Supabase Magic Link
    intent?.data?.let { uri ->
      handleDeepLink(uri)
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
}

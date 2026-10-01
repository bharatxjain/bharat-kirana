package com.kks.bharatkirana.ui.screens

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.NotificationImportant
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kks.bharatkirana.service.MyFirebaseMessagingService
import com.kks.bharatkirana.ui.theme.BharatBackground
import com.kks.bharatkirana.ui.theme.BharatPurpleContainer
import com.kks.bharatkirana.ui.theme.BharatPurplePrimary
import com.kks.bharatkirana.ui.theme.BharatTextMuted
import com.kks.bharatkirana.ui.theme.BharatTextPrimary
import com.kks.bharatkirana.ui.theme.BharatTextSecondary

/**
 * Shows the real on/off state of the two notification channels the app posts to
 * (order updates, promotions) and opens Android's own settings to change them.
 * There is no server-side opt-out, so in-app switches could not actually stop a push.
 */
@Composable
fun NotificationPreferencesScreen(
  onBackClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  // Re-read the channel state when the user comes back from system settings.
  val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
  var resumeCount by remember { mutableIntStateOf(0) }
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) resumeCount++
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }
  val orderUpdatesOn = remember(resumeCount) { isChannelOn(context, MyFirebaseMessagingService.CHANNEL_ID) }
  val promotionsOn = remember(resumeCount) { isChannelOn(context, MyFirebaseMessagingService.PROMOTIONS_CHANNEL_ID) }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(BharatBackground)
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      Surface(color = Color.White, shadowElevation = 1.dp) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(onClick = onBackClick) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = BharatTextPrimary)
          }
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = "Notification Preferences",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = BharatTextPrimary
          )
        }
      }
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White),
          border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column {
            PrefRow(
              icon = Icons.Default.NotificationImportant,
              title = "Order updates",
              subtitle = "Alerts when your order status changes",
              isOn = orderUpdatesOn,
              onChange = { openNotificationSettings(context, MyFirebaseMessagingService.CHANNEL_ID) }
            )
            HorizontalDivider(color = Color(0xFFF1F5F9))
            PrefRow(
              icon = Icons.Default.LocalOffer,
              title = "Promotions & offers",
              subtitle = "Offers and announcements from BreakQ",
              isOn = promotionsOn,
              onChange = { openNotificationSettings(context, MyFirebaseMessagingService.PROMOTIONS_CHANNEL_ID) }
            )
          }
        }
        Text(
          text = "These are your phone's notification settings for BreakQ. Keep order updates on so you know when your pickup is ready. The in-app Notifications list always shows every update.",
          fontSize = 11.sp,
          color = BharatTextMuted
        )
      }
    }
  }
}

private fun isChannelOn(context: Context, channelId: String): Boolean {
  if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
  val channel = context.getSystemService(NotificationManager::class.java)
    ?.getNotificationChannel(channelId) ?: return true
  return channel.importance != NotificationManager.IMPORTANCE_NONE
}

private fun openNotificationSettings(context: Context, channelId: String) {
  val appNotificationsOn = NotificationManagerCompat.from(context).areNotificationsEnabled()
  val intent = when {
    Build.VERSION.SDK_INT < Build.VERSION_CODES.O ->
      Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    !appNotificationsOn ->
      Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    else ->
      Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
  }
  runCatching { context.startActivity(intent) }
}

@Composable
private fun PrefRow(
  icon: ImageVector,
  title: String,
  subtitle: String,
  isOn: Boolean,
  onChange: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .size(36.dp)
        .clip(CircleShape)
        .background(BharatPurpleContainer),
      contentAlignment = Alignment.Center
    ) {
      Icon(icon, contentDescription = null, tint = BharatPurplePrimary, modifier = Modifier.size(20.dp))
    }
    Spacer(modifier = Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(text = title, fontWeight = FontWeight.SemiBold, color = BharatTextPrimary, fontSize = 14.sp)
      Text(text = subtitle, color = BharatTextSecondary, fontSize = 11.sp)
      Text(
        text = if (isOn) "On" else "Off",
        color = if (isOn) BharatPurplePrimary else Color(0xFFDC2626),
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp
      )
    }
    TextButton(onClick = onChange) {
      Text("Change", color = BharatPurplePrimary, fontWeight = FontWeight.SemiBold)
    }
  }
}

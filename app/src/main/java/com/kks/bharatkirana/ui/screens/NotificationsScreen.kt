package com.kks.bharatkirana.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kks.bharatkirana.data.model.AppNotification
import com.kks.bharatkirana.ui.theme.BharatBackground
import com.kks.bharatkirana.ui.theme.BharatPurpleContainer
import com.kks.bharatkirana.ui.theme.BharatPurplePrimary
import com.kks.bharatkirana.ui.theme.BharatTextMuted
import com.kks.bharatkirana.ui.theme.BharatTextPrimary
import com.kks.bharatkirana.ui.theme.BharatTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
  notifications: List<AppNotification>,
  onBackClick: () -> Unit,
  onNotificationClick: (AppNotification) -> Unit,
  modifier: Modifier = Modifier
) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = "Notifications",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
            color = BharatTextPrimary
          )
        },
        navigationIcon = {
          IconButton(onClick = onBackClick, modifier = Modifier.testTag("notifications_back_button")) {
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = BharatTextPrimary)
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
      .testTag("notifications_screen")
  ) { paddingValues ->
    if (notifications.isEmpty()) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        Icon(
          imageVector = Icons.Default.NotificationsNone,
          contentDescription = null,
          tint = BharatTextMuted,
          modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
          text = "No notifications yet",
          style = MaterialTheme.typography.bodyMedium,
          color = BharatTextSecondary,
          fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "Order and account updates will show up here.",
          style = MaterialTheme.typography.bodySmall,
          color = BharatTextMuted,
          textAlign = TextAlign.Center
        )
      }
    } else {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        items(notifications) { notification ->
          Card(
            onClick = { onNotificationClick(notification) },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
              containerColor = if (notification.isRead) Color.White else BharatPurpleContainer.copy(alpha = 0.4f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onNotificationClick(notification) }
          ) {
            Row(modifier = Modifier.padding(14.dp)) {
              Box(
                modifier = Modifier
                  .size(36.dp)
                  .clip(CircleShape)
                  .background(if (notification.isRead) Color(0xFFF1F5F9) else BharatPurpleContainer),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.Notifications,
                  contentDescription = null,
                  tint = BharatPurplePrimary,
                  modifier = Modifier.size(18.dp)
                )
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = notification.title,
                  style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (notification.isRead) FontWeight.Medium else FontWeight.Bold
                  ),
                  color = BharatTextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                  text = notification.message,
                  style = MaterialTheme.typography.bodySmall,
                  color = BharatTextSecondary
                )
              }
              if (!notification.isRead) {
                Box(
                  modifier = Modifier
                    .padding(start = 8.dp, top = 4.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(BharatPurplePrimary)
                )
              }
            }
          }
        }
      }
    }
  }
}

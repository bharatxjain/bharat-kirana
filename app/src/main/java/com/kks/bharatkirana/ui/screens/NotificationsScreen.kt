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
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
  onMarkAllRead: () -> Unit = {},
  onClearAll: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val unreadCount = notifications.count { !it.isRead }
  val (unread, read) = notifications.partition { !it.isRead }
  var showClearConfirm by remember { mutableStateOf(false) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              text = "Notifications",
              style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
              color = BharatTextPrimary
            )
            if (unreadCount > 0) {
              Text(
                text = "$unreadCount unread",
                style = MaterialTheme.typography.bodySmall,
                color = BharatPurplePrimary,
                fontWeight = FontWeight.SemiBold
              )
            }
          }
        },
        navigationIcon = {
          IconButton(onClick = onBackClick, modifier = Modifier.testTag("notifications_back_button")) {
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = BharatTextPrimary)
          }
        },
        actions = {
          if (notifications.isNotEmpty()) {
            IconButton(onClick = { showClearConfirm = true }) {
              Icon(
                imageVector = Icons.Default.DeleteSweep,
                contentDescription = "Clear all",
                tint = BharatTextSecondary
              )
            }
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
        Box(
          modifier = Modifier
            .size(88.dp)
            .clip(CircleShape)
            .background(BharatPurpleContainer),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.NotificationsNone,
            contentDescription = null,
            tint = BharatPurplePrimary,
            modifier = Modifier.size(44.dp)
          )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
          text = "You're all caught up",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = BharatTextPrimary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = "Order updates and account activity\nwill appear here.",
          style = MaterialTheme.typography.bodySmall,
          color = BharatTextSecondary,
          textAlign = TextAlign.Center
        )
      }
    } else {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        if (unread.isNotEmpty()) {
          item {
            Row(
              modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "New",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp,
                color = BharatTextSecondary
              )
              TextButton(onClick = onMarkAllRead, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Icon(Icons.Default.DoneAll, null, tint = BharatPurplePrimary, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Mark all as read", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = BharatPurplePrimary)
              }
            }
          }
          items(unread, key = { it.id }) { notification ->
            NotificationRowCard(notification = notification, onClick = { onNotificationClick(notification) })
          }
        }
        if (read.isNotEmpty()) {
          item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Earlier",
              fontWeight = FontWeight.ExtraBold,
              fontSize = 12.sp,
              color = BharatTextSecondary,
              modifier = Modifier.padding(bottom = 4.dp)
            )
          }
          items(read, key = { it.id }) { notification ->
            NotificationRowCard(notification = notification, onClick = { onNotificationClick(notification) })
          }
        }
        item { Spacer(modifier = Modifier.height(20.dp)) }
      }
    }
  }

  if (showClearConfirm) {
    AlertDialog(
      onDismissRequest = { showClearConfirm = false },
      containerColor = Color.White,
      title = { Text("Clear all notifications?", fontWeight = FontWeight.Bold, color = BharatTextPrimary) },
      text = { Text("Every notification in this list will be removed. This can't be undone.", color = BharatTextSecondary) },
      confirmButton = {
        Button(
          onClick = { showClearConfirm = false; onClearAll() },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
        ) { Text("Clear all", color = Color.White, fontWeight = FontWeight.Bold) }
      },
      dismissButton = {
        TextButton(onClick = { showClearConfirm = false }) {
          Text("Keep", color = BharatPurplePrimary)
        }
      }
    )
  }
}

@Composable
private fun NotificationRowCard(
  notification: AppNotification,
  onClick: () -> Unit
) {
  val isUnread = !notification.isRead
  Card(
    onClick = onClick,
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(
      containerColor = if (isUnread) BharatPurpleContainer.copy(alpha = 0.35f) else Color.White
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    border = androidx.compose.foundation.BorderStroke(
      1.dp,
      if (isUnread) BharatPurplePrimary.copy(alpha = 0.35f) else Color(0xFFE2E8F0)
    ),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
      Box(
        modifier = Modifier
          .size(40.dp)
          .clip(CircleShape)
          .background(if (isUnread) BharatPurplePrimary else Color(0xFFF1F5F9)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.Notifications,
          contentDescription = null,
          tint = if (isUnread) Color.White else BharatTextSecondary,
          modifier = Modifier.size(20.dp)
        )
      }
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = notification.title,
          style = MaterialTheme.typography.bodyMedium.copy(
            fontWeight = if (isUnread) FontWeight.ExtraBold else FontWeight.SemiBold
          ),
          color = BharatTextPrimary,
          maxLines = 2
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
          text = notification.message,
          style = MaterialTheme.typography.bodySmall,
          color = BharatTextSecondary,
          maxLines = 3
        )
        if (notification.createdAt.isNotBlank()) {
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = notification.createdAt.take(19).replace("T", " "),
            fontSize = 10.sp,
            color = BharatTextMuted
          )
        }
      }
      if (isUnread) {
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

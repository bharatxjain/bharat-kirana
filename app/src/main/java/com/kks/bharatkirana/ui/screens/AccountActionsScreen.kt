package com.kks.bharatkirana.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kks.bharatkirana.ui.theme.BharatBackground
import com.kks.bharatkirana.ui.theme.BharatTextMuted
import com.kks.bharatkirana.ui.theme.BharatTextPrimary
import com.kks.bharatkirana.ui.theme.BharatTextSecondary

/**
 * Log out / delete account, split out of ProfileScreen so the profile itself
 * stays a clean list. Deletion keeps the original two-step confirmation.
 */
@Composable
fun AccountActionsScreen(
  userEmail: String,
  onBackClick: () -> Unit,
  onLogout: () -> Unit,
  onDeleteAccount: () -> Unit,
  modifier: Modifier = Modifier
) {
  var showLogoutConfirm by remember { mutableStateOf(false) }
  // 0 = closed, 1 = first prompt, 2 = final confirmation.
  var deleteStep by remember { mutableIntStateOf(0) }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(BharatBackground)
  ) {
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
          text = "Account Actions",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = BharatTextPrimary
        )
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Card(
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = Color.White),
      border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
    ) {
      Column {
        SettingsRow(
          icon = Icons.Default.Logout,
          iconTint = Color(0xFFDC2626),
          iconBackground = Color(0xFFFEE2E2),
          title = "Log Out",
          subtitle = "Sign out of this account",
          testTagName = "profile_logout_row",
          onClick = { showLogoutConfirm = true }
        )
        HorizontalDivider(color = Color(0xFFF1F5F9))
        SettingsRow(
          icon = Icons.Default.Delete,
          iconTint = Color(0xFFDC2626),
          iconBackground = Color(0xFFFEE2E2),
          title = "Delete Account",
          subtitle = "Permanently remove your account",
          titleColor = Color(0xFFDC2626),
          testTagName = "profile_delete_account_row",
          onClick = { deleteStep = 1 }
        )
      }
    }

    if (userEmail.isNotBlank()) {
      Spacer(modifier = Modifier.height(12.dp))
      Text(
        text = "Signed in as $userEmail",
        fontSize = 12.sp,
        color = BharatTextMuted,
        modifier = Modifier.padding(horizontal = 20.dp)
      )
    }
  }

  if (showLogoutConfirm) {
    AlertDialog(
      onDismissRequest = { showLogoutConfirm = false },
      title = { Text("Log out?", fontWeight = FontWeight.Bold) },
      text = {
        Text(
          "You'll need to sign in again to access your account.",
          color = BharatTextSecondary
        )
      },
      confirmButton = {
        Button(
          onClick = {
            showLogoutConfirm = false
            onLogout()
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
          modifier = Modifier.testTag("logout_confirm_button")
        ) {
          Text("Log Out", color = Color.White, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showLogoutConfirm = false }) {
          Text("Cancel", color = BharatTextSecondary)
        }
      }
    )
  }

  if (deleteStep == 1) {
    AlertDialog(
      onDismissRequest = { deleteStep = 0 },
      title = { Text("Delete your account?", fontWeight = FontWeight.Bold) },
      text = {
        Text(
          "This action may permanently remove your account data.",
          color = BharatTextSecondary
        )
      },
      confirmButton = {
        Button(
          onClick = { deleteStep = 2 },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
          modifier = Modifier.testTag("delete_account_step1_continue")
        ) {
          Text("Continue", color = Color.White, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { deleteStep = 0 }) {
          Text("Cancel", color = BharatTextSecondary)
        }
      }
    )
  }

  if (deleteStep == 2) {
    AlertDialog(
      onDismissRequest = { deleteStep = 0 },
      title = { Text("Are you absolutely sure?", fontWeight = FontWeight.Bold) },
      text = {
        Text(
          "This action cannot be undone. All your profile data will be permanently deleted.",
          color = BharatTextSecondary
        )
      },
      confirmButton = {
        Button(
          onClick = {
            deleteStep = 0
            onDeleteAccount()
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
          modifier = Modifier.testTag("delete_account_final_confirm")
        ) {
          Text("Delete Account", color = Color.White, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { deleteStep = 0 }) {
          Text("Cancel", color = BharatTextSecondary)
        }
      }
    )
  }
}

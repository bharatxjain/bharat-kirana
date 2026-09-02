package com.kks.bharatkirana.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kks.bharatkirana.data.model.UserProfile
import com.kks.bharatkirana.ui.theme.BharatBackground
import com.kks.bharatkirana.ui.theme.BharatPurpleContainer
import com.kks.bharatkirana.ui.theme.BharatPurplePrimary
import com.kks.bharatkirana.ui.theme.BharatTextMuted
import com.kks.bharatkirana.ui.theme.BharatTextPrimary
import com.kks.bharatkirana.ui.theme.BharatTextSecondary

/**
 * Read-only saved addresses list. The current data model stores a single
 * address on UserProfile — this screen presents it, and offers the Edit
 * Profile flow to change or add one. Full multi-address book is planned
 * for a later iteration.
 */
@Composable
fun SavedAddressesScreen(
  userProfile: UserProfile,
  onBackClick: () -> Unit,
  onEditProfileClick: () -> Unit,
  modifier: Modifier = Modifier
) {
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
            text = "Saved Addresses",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = BharatTextPrimary
          )
        }
      }
      if (userProfile.address.isBlank()) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
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
            Icon(Icons.Default.LocationOn, contentDescription = null, tint = BharatPurplePrimary, modifier = Modifier.size(44.dp))
          }
          Spacer(modifier = Modifier.height(16.dp))
          Text(
            text = "No addresses saved",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = BharatTextPrimary
          )
          Text(
            text = "Add a delivery address from your profile.",
            color = BharatTextSecondary,
            fontSize = 13.sp
          )
          Spacer(modifier = Modifier.height(20.dp))
          TextButton(onClick = onEditProfileClick) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.Add, contentDescription = null, tint = BharatPurplePrimary)
              Spacer(modifier = Modifier.width(6.dp))
              Text("Add address", color = BharatPurplePrimary, fontWeight = FontWeight.Bold)
            }
          }
        }
      } else {
        Column(modifier = Modifier.padding(16.dp)) {
          Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onEditProfileClick() }
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              verticalAlignment = Alignment.Top
            ) {
              Box(
                modifier = Modifier
                  .size(40.dp)
                  .clip(CircleShape)
                  .background(BharatPurpleContainer),
                contentAlignment = Alignment.Center
              ) {
                Icon(Icons.Default.Home, contentDescription = null, tint = BharatPurplePrimary)
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text("Home", fontWeight = FontWeight.Bold, color = BharatTextPrimary)
                  Spacer(modifier = Modifier.width(6.dp))
                  Icon(Icons.Default.CheckCircle, contentDescription = null, tint = BharatPurplePrimary, modifier = Modifier.size(14.dp))
                  Text(
                    text = " Default",
                    fontSize = 11.sp,
                    color = BharatPurplePrimary,
                    fontWeight = FontWeight.SemiBold
                  )
                }
                Text(text = userProfile.address, color = BharatTextSecondary, fontSize = 13.sp)
                if (userProfile.mobileNumber.isNotBlank()) {
                  Text(text = "+91 ${userProfile.mobileNumber}", color = BharatTextMuted, fontSize = 12.sp)
                }
              }
            }
          }
          Spacer(modifier = Modifier.height(12.dp))
          TextButton(onClick = onEditProfileClick) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.Add, contentDescription = null, tint = BharatPurplePrimary)
              Spacer(modifier = Modifier.width(6.dp))
              Text("Change address", color = BharatPurplePrimary, fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }
  }
}

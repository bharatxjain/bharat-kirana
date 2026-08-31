package com.kks.bharatkirana.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kks.bharatkirana.data.model.Shop
import com.kks.bharatkirana.data.model.UserProfile
import com.kks.bharatkirana.ui.theme.BharatBackground
import com.kks.bharatkirana.ui.theme.BharatGreen
import com.kks.bharatkirana.ui.theme.BharatPurpleContainer
import com.kks.bharatkirana.ui.theme.BharatPurplePrimary
import com.kks.bharatkirana.ui.theme.BharatTextMuted
import com.kks.bharatkirana.ui.theme.BharatTextPrimary
import com.kks.bharatkirana.ui.theme.BharatTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorProfileScreen(
  userProfile: UserProfile,
  shop: Shop,
  currentTierName: String?,
  onBackClick: () -> Unit,
  onSavePersonalInfo: (String, String, String, String) -> Unit,
  onUpdateShop: (String, Shop) -> Unit,
  onManagePlan: () -> Unit,
  onOpenReviews: () -> Unit,
  onSupportClick: () -> Unit,
  onLogout: () -> Unit,
  modifier: Modifier = Modifier
) {
  var showEditShopDialog by remember { mutableStateOf(false) }
  var showEditPersonalDialog by remember { mutableStateOf(false) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = "Account",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = BharatTextPrimary
          )
        },
        navigationIcon = {
          IconButton(onClick = onBackClick) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = BharatTextPrimary)
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
      )
    },
    containerColor = BharatBackground,
    modifier = modifier
  ) { pad ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(pad),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      // ── Header card: owner name, email, shop badge ────────────────────────
      item {
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White),
          border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(BharatPurpleContainer),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = shop.ownerName.firstOrNull()?.uppercase() ?: "V",
                color = BharatPurplePrimary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 22.sp
              )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = shop.ownerName.ifBlank { userProfile.fullName.ifBlank { "Shop Owner" } },
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = BharatTextPrimary
              )
              Text(
                text = userProfile.email,
                fontSize = 12.sp,
                color = BharatTextSecondary
              )
              Spacer(modifier = Modifier.height(6.dp))
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Default.Storefront,
                  contentDescription = null,
                  tint = BharatPurplePrimary,
                  modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = shop.name,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = BharatPurplePrimary
                )
              }
            }
          }
        }
      }

      // ── Action cards ──────────────────────────────────────────────────────
      item {
        ProfileActionCard(
          icon = Icons.Default.Edit,
          title = "Edit Personal Info",
          subtitle = "Your name, mobile number and address",
          onClick = { showEditPersonalDialog = true }
        )
      }
      item {
        ProfileActionCard(
          icon = Icons.Default.Storefront,
          title = "Edit Store Details",
          subtitle = "Shop name, phone, address",
          onClick = { showEditShopDialog = true }
        )
      }
      item {
        ProfileActionCard(
          icon = Icons.Default.Star,
          title = "Ratings & Reviews",
          subtitle = "See what customers are saying",
          onClick = onOpenReviews,
          accentColor = Color(0xFFF59E0B)
        )
      }
      item {
        ProfileActionCard(
          icon = Icons.Default.ChatBubble,
          title = "Contact Support",
          subtitle = "Chat with our team on WhatsApp",
          onClick = onSupportClick,
          accentColor = BharatGreen
        )
      }

      // ── Logout ────────────────────────────────────────────────────────────
      item {
        Spacer(modifier = Modifier.height(8.dp))
        Button(
          onClick = onLogout,
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEF2F2)),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
          Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = Color(0xFFDC2626))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Log Out", color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
        }
      }
    }
  }

  if (showEditShopDialog) {
    var name by remember { mutableStateOf(shop.name) }
    var owner by remember { mutableStateOf(shop.ownerName) }
    var addr by remember { mutableStateOf(shop.address) }
    var phone by remember { mutableStateOf(shop.phone) }
    // Uses the exact same forced-light styling as the dashboard's dialog so a
    // system dark theme cannot render dark text on a dark surface.
    AlertDialog(
      onDismissRequest = { showEditShopDialog = false },
      containerColor = Color.White,
      titleContentColor = BharatTextPrimary,
      textContentColor = BharatTextPrimary,
      title = { Text("Edit Store Details", fontWeight = FontWeight.Bold, color = BharatTextPrimary) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Shop Name") }, modifier = Modifier.fillMaxWidth(), colors = editStoreFieldColors())
          OutlinedTextField(value = owner, onValueChange = { owner = it }, label = { Text("Owner Name") }, modifier = Modifier.fillMaxWidth(), colors = editStoreFieldColors())
          OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth(), colors = editStoreFieldColors())
          OutlinedTextField(value = addr, onValueChange = { addr = it }, label = { Text("Address") }, minLines = 2, modifier = Modifier.fillMaxWidth(), colors = editStoreFieldColors())
        }
      },
      confirmButton = {
        Button(
          onClick = {
            onUpdateShop(shop.id, shop.copy(name = name, ownerName = owner, address = addr, phone = phone))
            showEditShopDialog = false
          },
          colors = ButtonDefaults.buttonColors(containerColor = BharatPurplePrimary)
        ) { Text("Save", color = Color.White) }
      },
      dismissButton = {
        TextButton(onClick = { showEditShopDialog = false }) { Text("Cancel", color = BharatPurplePrimary) }
      }
    )
  }

  if (showEditPersonalDialog) {
    var name by remember { mutableStateOf(userProfile.fullName) }
    var mobile by remember { mutableStateOf(userProfile.mobileNumber) }
    var address by remember { mutableStateOf(userProfile.address) }
    AlertDialog(
      onDismissRequest = { showEditPersonalDialog = false },
      containerColor = Color.White,
      titleContentColor = BharatTextPrimary,
      textContentColor = BharatTextPrimary,
      title = { Text("Edit Personal Info", fontWeight = FontWeight.Bold, color = BharatTextPrimary) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name") }, modifier = Modifier.fillMaxWidth(), colors = editStoreFieldColors())
          OutlinedTextField(value = mobile, onValueChange = { mobile = it.filter { c -> c.isDigit() }.take(10) }, label = { Text("Mobile Number") }, modifier = Modifier.fillMaxWidth(), colors = editStoreFieldColors())
          OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address") }, minLines = 2, modifier = Modifier.fillMaxWidth(), colors = editStoreFieldColors())
          Text(
            text = "Email cannot be changed here.",
            fontSize = 11.sp,
            color = BharatTextMuted
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            onSavePersonalInfo(name, userProfile.email, mobile, address)
            showEditPersonalDialog = false
          },
          enabled = name.isNotBlank() && mobile.length == 10,
          colors = ButtonDefaults.buttonColors(containerColor = BharatPurplePrimary)
        ) { Text("Save", color = Color.White) }
      },
      dismissButton = {
        TextButton(onClick = { showEditPersonalDialog = false }) { Text("Cancel", color = BharatPurplePrimary) }
      }
    )
  }
}

@Composable
private fun ProfileActionCard(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  subtitle: String,
  onClick: () -> Unit,
  accentColor: Color = BharatPurplePrimary
) {
  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(40.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(accentColor.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
      }
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BharatTextPrimary)
        Text(subtitle, fontSize = 12.sp, color = BharatTextSecondary)
      }
      Icon(Icons.Default.ChevronRight, contentDescription = null, tint = BharatTextMuted)
    }
  }
}

@Composable
private fun editStoreFieldColors() = OutlinedTextFieldDefaults.colors(
  focusedTextColor = BharatTextPrimary,
  unfocusedTextColor = BharatTextPrimary,
  focusedLabelColor = BharatPurplePrimary,
  unfocusedLabelColor = BharatTextSecondary,
  focusedContainerColor = Color.White,
  unfocusedContainerColor = Color.White,
  focusedBorderColor = BharatPurplePrimary
)

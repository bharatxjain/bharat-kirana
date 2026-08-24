package com.kks.bharatkirana.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Loyalty
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kks.bharatkirana.data.model.Order
import com.kks.bharatkirana.data.model.OrderStatus
import com.kks.bharatkirana.data.model.UserProfile
import com.kks.bharatkirana.ui.components.CustomQrCodePattern
import com.kks.bharatkirana.ui.theme.BharatBackground
import com.kks.bharatkirana.ui.theme.BharatGreen
import com.kks.bharatkirana.ui.theme.BharatGreenLight
import com.kks.bharatkirana.ui.theme.BharatPurpleAccent
import com.kks.bharatkirana.ui.theme.BharatPurpleContainer
import com.kks.bharatkirana.ui.theme.BharatPurpleDark
import com.kks.bharatkirana.ui.theme.BharatPurpleLight
import com.kks.bharatkirana.ui.theme.BharatPurplePrimary
import com.kks.bharatkirana.ui.theme.BharatTextMuted
import com.kks.bharatkirana.ui.theme.BharatTextPrimary
import com.kks.bharatkirana.ui.theme.BharatTextSecondary

@Composable
fun ProfileScreen(
  userProfile: UserProfile,
  orders: List<Order>,
  cartItemCount: Int,
  onOrderClick: (Order) -> Unit,
  onReorder: (Order) -> Unit,
  onCartClick: () -> Unit,
  onUpdateProfile: (String, String, String, String) -> Unit,
  onPrivacyPolicyClick: () -> Unit = {},
  onTermsClick: () -> Unit = {},
  onVendorRegisterClick: () -> Unit = {},
  onLogout: () -> Unit = {},
  onDeleteAccount: () -> Unit = {},
  hasSupport: Boolean = false,
  onSupportClick: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  var editingProfile by remember { mutableStateOf(false) }
  var showQrDialog by remember { mutableStateOf(false) }
  var showLogoutConfirmDialog by remember { mutableStateOf(false) }
  var showDeleteConfirmDialog by remember { mutableStateOf(false) }

  var editName by remember { mutableStateOf(userProfile.fullName) }
  var editEmail by remember { mutableStateOf(userProfile.email) }
  var editMobile by remember { mutableStateOf(userProfile.mobileNumber) }
  var editAddress by remember { mutableStateOf(userProfile.address) }

  val latestOrder = orders.firstOrNull()

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(BharatBackground)
  ) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .testTag("profile_screen_content"),
      contentPadding = PaddingValues(bottom = 32.dp)
    ) {
      // Header with Store Title, Cart & Logout Actions
      item {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Storefront,
              contentDescription = "Store",
              tint = BharatPurplePrimary,
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "BreakQ",
              style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp
              ),
              color = BharatPurplePrimary
            )
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            // Cart Button
            IconButton(
              onClick = onCartClick,
              modifier = Modifier.testTag("profile_cart_icon")
            ) {
              Box(contentAlignment = Alignment.TopEnd) {
                Icon(
                  imageVector = Icons.Default.ShoppingCart,
                  contentDescription = "Cart",
                  tint = BharatTextPrimary
                )
                if (cartItemCount > 0) {
                  Box(
                    modifier = Modifier
                      .size(16.dp)
                      .clip(CircleShape)
                      .background(BharatPurplePrimary),
                    contentAlignment = Alignment.Center
                  ) {
                    Text(
                      text = "$cartItemCount",
                      color = Color.White,
                      fontSize = 9.sp,
                      fontWeight = FontWeight.Bold
                    )
                  }
                }
              }
            }

            // Prominent Top Bar Logout Button
            Surface(
              onClick = { showLogoutConfirmDialog = true },
              shape = RoundedCornerShape(8.dp),
              color = Color(0xFFFEF2F2),
              border = BorderStroke(1.dp, Color(0xFFFECACA)),
              modifier = Modifier.testTag("profile_topbar_logout_button")
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = Icons.Default.Logout,
                  contentDescription = "Log Out",
                  tint = Color(0xFFDC2626),
                  modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = "Log Out",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFFDC2626)
                )
              }
            }
          }
        }
      }

      // User Profile Card
      item {
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
          Column(modifier = Modifier.padding(18.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .size(60.dp)
                  .clip(CircleShape)
                  .background(
                    Brush.linearGradient(
                      if (userProfile.isAdmin) {
                        listOf(BharatPurpleDark, BharatPurplePrimary)
                      } else {
                        listOf(BharatPurplePrimary, BharatPurpleAccent)
                      }
                    )
                  ),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = userProfile.fullName.firstOrNull()?.toString() ?: "U",
                  color = Color.White,
                  style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                )
              }

              Spacer(modifier = Modifier.width(14.dp))

              Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(
                    text = userProfile.fullName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = BharatTextPrimary
                  )
                  Spacer(modifier = Modifier.width(6.dp))

                  // Role Badge
                  Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (userProfile.isAdmin) BharatPurpleContainer else Color(0xFFF1F5F9)
                  ) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                      Icon(
                        imageVector = if (userProfile.isAdmin) Icons.Default.AdminPanelSettings else Icons.Default.Person,
                        contentDescription = "Role",
                        tint = if (userProfile.isAdmin) BharatPurplePrimary else BharatTextSecondary,
                        modifier = Modifier.size(11.dp)
                      )
                      Spacer(modifier = Modifier.width(3.dp))
                      Text(
                        text = if (userProfile.isAdmin) "Admin" else "Customer",
                        color = if (userProfile.isAdmin) BharatPurplePrimary else BharatTextSecondary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 10.sp
                      )
                    }
                  }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Email Display
                Text(
                  text = userProfile.email,
                  style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                  color = if (userProfile.isAdmin) BharatPurplePrimary else BharatTextSecondary
                )

                Text(
                  text = "+91 ${userProfile.mobileNumber}",
                  style = MaterialTheme.typography.bodySmall,
                  color = BharatTextMuted
                )
              }

              // Kept only for accessibility / muscle memory — the real prominent
              // Edit button lives right below the profile row.
              if (editingProfile) {
                IconButton(
                  onClick = {
                    editName = userProfile.fullName
                    editEmail = userProfile.email
                    editMobile = userProfile.mobileNumber
                    editAddress = userProfile.address
                    editingProfile = false
                  },
                  modifier = Modifier.testTag("edit_profile_button")
                ) {
                  Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel edit",
                    tint = BharatTextSecondary,
                    modifier = Modifier.size(20.dp)
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Round 4a v2: prominent, obviously-tappable Edit Profile button.
            // Replaces the tiny 20dp pencil icon nobody noticed.
            if (!editingProfile) {
              Button(
                onClick = {
                  editName = userProfile.fullName
                  editEmail = userProfile.email
                  editMobile = userProfile.mobileNumber
                  editAddress = userProfile.address
                  editingProfile = true
                },
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("edit_profile_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                  containerColor = BharatPurpleContainer,
                  contentColor = BharatPurplePrimary
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
              ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = "Edit Profile",
                  fontWeight = FontWeight.Bold,
                  fontSize = 14.sp
                )
              }
            }

            // Inline edit form (Round 4a): appears directly under the avatar/name row
            // when user taps the Edit icon. No modal dialog anymore.
            if (editingProfile) {
              Spacer(modifier = Modifier.height(14.dp))
              HorizontalDivider(color = Color(0xFFF1F5F9))
              Spacer(modifier = Modifier.height(14.dp))
              Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                  value = editName,
                  onValueChange = { editName = it },
                  label = { Text("Full Name") },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth(),
                  colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = BharatTextPrimary,
                    unfocusedTextColor = BharatTextPrimary,
                    focusedBorderColor = BharatPurplePrimary
                  )
                )
                OutlinedTextField(
                  value = editAddress,
                  onValueChange = { editAddress = it },
                  label = { Text("Delivery / Pickup Address") },
                  minLines = 2,
                  modifier = Modifier.fillMaxWidth(),
                  colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = BharatTextPrimary,
                    unfocusedTextColor = BharatTextPrimary,
                    focusedBorderColor = BharatPurplePrimary
                  )
                )
                Row(
                  modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = BharatTextMuted, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                      text = "Email and mobile are locked (login identity).",
                      fontSize = 10.sp,
                      color = BharatTextMuted
                    )
                  }
                }
                Row(
                  modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                  horizontalArrangement = Arrangement.End,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  TextButton(onClick = {
                    editName = userProfile.fullName
                    editAddress = userProfile.address
                    editingProfile = false
                  }) {
                    Text("Cancel", color = BharatTextSecondary, fontWeight = FontWeight.SemiBold)
                  }
                  Spacer(modifier = Modifier.width(8.dp))
                  Button(
                    onClick = {
                      onUpdateProfile(editName.trim(), userProfile.email, userProfile.mobileNumber, editAddress.trim())
                      editingProfile = false
                    },
                    enabled = editName.isNotBlank() && (editName.trim() != userProfile.fullName || editAddress.trim() != userProfile.address),
                    colors = ButtonDefaults.buttonColors(containerColor = BharatPurplePrimary),
                    shape = RoundedCornerShape(10.dp)
                  ) {
                    Text("Save Changes", color = Color.White, fontWeight = FontWeight.Bold)
                  }
                }
              }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Color(0xFFF1F5F9))
            Spacer(modifier = Modifier.height(14.dp))

            // Quick Stats Row (Wallet & Loyalty Points)
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                  .weight(1f)
                  .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp))
                  .padding(10.dp)
              ) {
                Box(
                  modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(BharatPurpleContainer),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    tint = BharatPurplePrimary,
                    modifier = Modifier.size(20.dp)
                  )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                  Text("Kirana Wallet", style = MaterialTheme.typography.labelSmall, color = BharatTextSecondary)
                  Text("₹${userProfile.walletBalance}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = BharatTextPrimary)
                }
              }

              Spacer(modifier = Modifier.width(12.dp))

              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                  .weight(1f)
                  .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp))
                  .padding(10.dp)
              ) {
                Box(
                  modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(BharatGreenLight),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Default.Loyalty,
                    contentDescription = null,
                    tint = BharatGreen,
                    modifier = Modifier.size(20.dp)
                  )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                  Text("Reward Points", style = MaterialTheme.typography.labelSmall, color = BharatTextSecondary)
                  Text("${userProfile.loyaltyPoints} pts", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = BharatTextPrimary)
                }
              }
            }
          }
        }
      }

      // Scan at Counter Pickup QR Card (Show only if there's a recent order)
      if (latestOrder != null) {
        item {
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp)
              .clickable { showQrDialog = true }
              .testTag("scan_at_counter_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = BharatPurplePrimary),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Surface(
                  shape = RoundedCornerShape(6.dp),
                  color = Color.White.copy(alpha = 0.2f)
                ) {
                  Text(
                    text = "Order #${latestOrder.id}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                  )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                  text = "Present this code for Order Pickup",
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                  color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = "Tap to enlarge QR for counter barcode scanner",
                  style = MaterialTheme.typography.bodySmall,
                  color = Color.White.copy(alpha = 0.85f)
                )
              }

              Box(
                modifier = Modifier
                  .size(68.dp)
                  .clip(RoundedCornerShape(12.dp))
                  .background(Color.White)
                  .padding(6.dp),
                contentAlignment = Alignment.Center
              ) {
                CustomQrCodePattern(tint = BharatPurplePrimary)
              }
            }
          }
          Spacer(modifier = Modifier.height(20.dp))
        }
      }

      // Vendor Onboarding Card
      if (!userProfile.isAdmin && !userProfile.isVendor) {
        item {
          Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F3FF)),
            border = BorderStroke(1.dp, Color(0xFFDDD6FE)),
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp)
              .clickable { onVendorRegisterClick() }
          ) {
            Row(
              modifier = Modifier.padding(16.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .size(44.dp)
                  .clip(CircleShape)
                  .background(Color.White),
                contentAlignment = Alignment.Center
              ) {
                Icon(Icons.Default.Store, contentDescription = null, tint = BharatPurplePrimary)
              }
              Spacer(modifier = Modifier.width(16.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Register Your Shop",
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                  color = BharatPurplePrimary
                )
                Text(
                  text = "Start selling your groceries online today",
                  style = MaterialTheme.typography.bodySmall,
                  color = BharatTextSecondary
                )
              }
              Icon(Icons.Default.ChevronRight, contentDescription = null, tint = BharatPurplePrimary)
            }
          }
          Spacer(modifier = Modifier.height(20.dp))
        }
      }

      // Legal & Store Policies Card
      item {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
          text = "Legal & Policies",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = BharatTextPrimary,
          modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))

        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White),
          border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
        ) {
          Column {
            // Privacy Policy Item
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable { onPrivacyPolicyClick() }
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .testTag("profile_privacy_policy_link"),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEDE9FE)),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = BharatPurplePrimary,
                    modifier = Modifier.size(20.dp)
                  )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                  Text(
                    text = "Privacy Policy",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = BharatTextPrimary
                  )
                  Text(
                    text = "Data usage, cloud security & account deletion",
                    fontSize = 11.sp,
                    color = BharatTextSecondary
                  )
                }
              }
              Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = BharatTextMuted
              )
            }

            HorizontalDivider(color = Color(0xFFF1F5F9))

            // Terms of Service Item
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable { onTermsClick() }
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .testTag("profile_terms_of_service_link"),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEDE9FE)),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = BharatPurplePrimary,
                    modifier = Modifier.size(20.dp)
                  )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                  Text(
                    text = "Terms of Service",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = BharatTextPrimary
                  )
                  Text(
                    text = "Store pickup agreement, retail pricing & terms",
                    fontSize = 11.sp,
                    color = BharatTextSecondary
                  )
                }
              }
              Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = BharatTextMuted
              )
            }
          }
        }
      }

      // Contact Support (WhatsApp) — rendered only when Remote Config has a number.
      if (hasSupport) {
        item {
          Spacer(modifier = Modifier.height(14.dp))
          Surface(
            onClick = onSupportClick,
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFFECFDF5),
            border = BorderStroke(1.dp, Color(0xFF10B981)),
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp)
              .testTag("support_whatsapp_button")
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
              horizontalArrangement = Arrangement.Center,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = Color(0xFF059669),
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "Contact Support on WhatsApp",
                color = Color(0xFF059669),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
              )
            }
          }
        }
      }

      // Customer Logout Action
      item {
        Spacer(modifier = Modifier.height(14.dp))
        Surface(
          onClick = { showLogoutConfirmDialog = true },
          shape = RoundedCornerShape(16.dp),
          color = Color(0xFFFEF2F2),
          border = BorderStroke(1.dp, Color(0xFFFECACA)),
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("customer_logout_button")
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Default.Logout,
              contentDescription = "Log Out",
              tint = Color(0xFFDC2626),
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Log Out of Account",
              color = Color(0xFFDC2626),
              fontWeight = FontWeight.Bold,
              fontSize = 14.sp
            )
          }
        }

        Spacer(modifier = Modifier.height(14.dp))
        
        TextButton(
          onClick = { showDeleteConfirmDialog = true },
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("delete_account_button")
        ) {
          Text(
            text = "Delete Account & Data",
            color = Color(0xFF94A3B8),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
          )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // App Version and Package identification
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(
            text = "BreakQ v1.0.0 • com.kks.bharatkirana",
            fontSize = 11.sp,
            color = BharatTextMuted,
            textAlign = TextAlign.Center
          )
          Text(
            text = "Operated by KKS PVT • Made with ❤️ for Indian Kiranas",
            fontSize = 10.sp,
            color = BharatTextMuted.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
          )
        }

        Spacer(modifier = Modifier.height(16.dp))
      }
    }
  }

  // Logout Confirmation Dialog
  if (showLogoutConfirmDialog) {
    AlertDialog(
      onDismissRequest = { showLogoutConfirmDialog = false },
      title = {
        Text("Confirm Log Out", fontWeight = FontWeight.Bold)
      },
      text = {
        Text(
          text = "Are you sure you want to log out of ${userProfile.email}? You can sign back in at any time.",
          style = MaterialTheme.typography.bodyMedium,
          color = BharatTextSecondary
        )
      },
      confirmButton = {
        Button(
          onClick = {
            showLogoutConfirmDialog = false
            onLogout()
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
        ) {
          Text("Log Out", color = Color.White, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showLogoutConfirmDialog = false }) {
          Text("Cancel", color = BharatTextSecondary)
        }
      }
    )
  }

  // Account Deletion Confirmation Dialog
  if (showDeleteConfirmDialog) {
    AlertDialog(
      onDismissRequest = { showDeleteConfirmDialog = false },
      title = {
        Text("Delete Account Permanently?", fontWeight = FontWeight.Bold)
      },
      text = {
        Text(
          text = "This action cannot be undone. All your orders, wallet balance, and profile data will be permanently deleted from our servers.",
          style = MaterialTheme.typography.bodyMedium,
          color = BharatTextSecondary
        )
      },
      confirmButton = {
        Button(
          onClick = {
            showDeleteConfirmDialog = false
            onDeleteAccount()
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
        ) {
          Text("Delete My Data", color = Color.White, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showDeleteConfirmDialog = false }) {
          Text("Go Back", color = BharatTextSecondary)
        }
      }
    )
  }

  // Edit Profile Dialog — REMOVED (Round 4a): replaced with inline editing under the profile row above.

  // Large QR Code Dialog for Counter Pickup
  if (showQrDialog) {
    AlertDialog(
      onDismissRequest = { showQrDialog = false },
      title = {
        Text("Store Pickup QR Code", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
      },
      text = {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(
            text = "Show this QR code at BreakQ checkout counter to verify and collect your order.",
            style = MaterialTheme.typography.bodySmall,
            color = BharatTextSecondary,
            textAlign = TextAlign.Center
          )
          Spacer(modifier = Modifier.height(16.dp))
          Box(
            modifier = Modifier
              .size(220.dp)
              .clip(RoundedCornerShape(16.dp))
              .background(Color.White)
              .padding(16.dp),
            contentAlignment = Alignment.Center
          ) {
            CustomQrCodePattern(tint = BharatPurplePrimary)
          }
          Spacer(modifier = Modifier.height(12.dp))
          Text(
            text = latestOrder?.id ?: "---",
            style = MaterialTheme.typography.headlineMedium.copy(
              fontWeight = FontWeight.ExtraBold,
              letterSpacing = 2.sp
            ),
            color = BharatPurplePrimary
          )
          Text(
            text = "Pickup Code",
            style = MaterialTheme.typography.labelSmall,
            color = BharatTextSecondary
          )
        }
      },
      confirmButton = {
        Button(
          onClick = { showQrDialog = false },
          colors = ButtonDefaults.buttonColors(containerColor = BharatPurplePrimary)
        ) {
          Text("Close")
        }
      }
    )
  }
}

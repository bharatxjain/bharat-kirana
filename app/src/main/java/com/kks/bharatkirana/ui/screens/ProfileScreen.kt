package com.kks.bharatkirana.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.ShoppingCart
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kks.bharatkirana.BuildConfig
import com.kks.bharatkirana.data.model.Order
import com.kks.bharatkirana.data.model.UserProfile
import com.kks.bharatkirana.data.model.UserRole
import com.kks.bharatkirana.ui.theme.BharatBackground
import com.kks.bharatkirana.ui.theme.BharatPurpleAccent
import com.kks.bharatkirana.ui.theme.BharatPurpleContainer
import com.kks.bharatkirana.ui.theme.BharatPurpleDark
import com.kks.bharatkirana.ui.theme.BharatPurplePrimary
import com.kks.bharatkirana.ui.theme.BharatTextMuted
import com.kks.bharatkirana.ui.theme.BharatTextPrimary
import com.kks.bharatkirana.ui.theme.BharatTextSecondary

@Composable
fun ProfileScreen(
  userProfile: UserProfile,
  orders: List<Order>,
  cartItemCount: Int,
  onCartClick: () -> Unit,
  onMyOrdersClick: () -> Unit,
  onEditProfileClick: () -> Unit,
  onSavedAddressesClick: () -> Unit,
  onNotificationPreferencesClick: () -> Unit,
  onKiranaWalletClick: () -> Unit,
  onHelpSupportClick: () -> Unit,
  onVendorRegisterClick: () -> Unit,
  onAboutUsClick: () -> Unit,
  onAccountActionsClick: () -> Unit,
  savedAddressCount: Int = 0,
  profileFetchComplete: Boolean = true,
  modifier: Modifier = Modifier
) {
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
      // ---- Store title header (BreakQ + cart, no logout up here) ----------
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
        }
      }

      // ---- Profile header card (basic info only) --------------------------
      item {
        Spacer(modifier = Modifier.height(12.dp))
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White),
          border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            val isRealVendor = userProfile.serverRole == UserRole.VENDOR && userProfile.shopId != null
            Box(
              modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(
                  Brush.linearGradient(
                    if (userProfile.isAdmin) listOf(BharatPurpleDark, BharatPurplePrimary)
                    else listOf(BharatPurplePrimary, BharatPurpleAccent)
                  )
                ),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = userProfile.fullName.firstOrNull()?.uppercase() ?: "U",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
              )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = userProfile.fullName.ifBlank { "Your Account" },
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                  color = BharatTextPrimary
                )
                Spacer(modifier = Modifier.width(6.dp))
                RoleBadge(
                  isAdmin = userProfile.isAdmin,
                  isRealVendor = isRealVendor
                )
              }
              if (userProfile.mobileNumber.isNotBlank()) {
                Text(
                  text = "+91 ${userProfile.mobileNumber}",
                  style = MaterialTheme.typography.bodySmall,
                  color = BharatTextMuted
                )
              }
              if (userProfile.email.isNotBlank()) {
                Text(
                  text = userProfile.email,
                  style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                  color = BharatTextSecondary
                )
              }
            }
          }
        }
      }

      // ---- Quick Access tiles (Orders / Wallet / Help) --------------------
      item {
        Spacer(modifier = Modifier.height(16.dp))
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          QuickAccessTile(
            icon = Icons.Default.ReceiptLong,
            label = "Your Orders",
            testTagName = "quick_your_orders",
            onClick = onMyOrdersClick,
            modifier = Modifier.weight(1f)
          )
          QuickAccessTile(
            icon = Icons.Default.AccountBalanceWallet,
            label = "Kirana Wallet",
            testTagName = "quick_kirana_wallet",
            onClick = onKiranaWalletClick,
            modifier = Modifier.weight(1f)
          )
          QuickAccessTile(
            icon = Icons.Default.SupportAgent,
            label = "Help & Support",
            testTagName = "quick_help_support",
            onClick = onHelpSupportClick,
            modifier = Modifier.weight(1f)
          )
        }
      }

      // ---- Account section -------------------------------------------------
      item {
        SectionHeader(text = "Account")
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
              icon = Icons.Default.Edit,
              iconTint = BharatPurplePrimary,
              iconBackground = BharatPurpleContainer,
              title = "Edit Profile",
              subtitle = "Update your name and phone",
              testTagName = "profile_edit_profile_row",
              onClick = onEditProfileClick
            )
            HorizontalDivider(color = Color(0xFFF1F5F9))
            SettingsRow(
              icon = Icons.Default.LocationOn,
              iconTint = BharatPurplePrimary,
              iconBackground = BharatPurpleContainer,
              title = "Saved Addresses",
              subtitle = when (savedAddressCount) {
                0 -> "Add a delivery address"
                1 -> "1 address saved"
                else -> "$savedAddressCount addresses saved"
              },
              testTagName = "profile_saved_addresses_row",
              onClick = onSavedAddressesClick
            )
            HorizontalDivider(color = Color(0xFFF1F5F9))
            SettingsRow(
              icon = Icons.Default.Notifications,
              iconTint = BharatPurplePrimary,
              iconBackground = BharatPurpleContainer,
              title = "Notification Preferences",
              subtitle = "Manage order and promotional alerts",
              testTagName = "profile_notif_prefs_row",
              onClick = onNotificationPreferencesClick
            )
          }
        }
      }

      // ---- Register Your Shop CTA (only for non-admin, non-vendor) --------
      // Placed after Account so accidental taps are unlikely, but before the
      // legal/version section so it's still discoverable.
      if (profileFetchComplete && !userProfile.isAdmin && !userProfile.isVendor) {
        item {
          Spacer(modifier = Modifier.height(16.dp))
          Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F3FF)),
            border = BorderStroke(1.dp, Color(0xFFDDD6FE)),
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp)
              .clickable { onVendorRegisterClick() }
              .testTag("profile_register_shop_row")
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
              Spacer(modifier = Modifier.width(14.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Register Your Shop",
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                  color = BharatPurplePrimary
                )
                Text(
                  text = "Start selling your groceries on BreakQ",
                  style = MaterialTheme.typography.bodySmall,
                  color = BharatTextSecondary
                )
              }
              Icon(Icons.Default.ChevronRight, contentDescription = null, tint = BharatPurplePrimary)
            }
          }
        }
      }

      // ---- More: About + Account Actions -----------------------------------
      item {
        SectionHeader(text = "More")
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
              icon = Icons.Default.Info,
              iconTint = BharatPurplePrimary,
              iconBackground = BharatPurpleContainer,
              title = "About BreakQ",
              subtitle = "About us, privacy policy, terms & app version",
              testTagName = "profile_about_us_row",
              onClick = onAboutUsClick
            )
            HorizontalDivider(color = Color(0xFFF1F5F9))
            SettingsRow(
              icon = Icons.Default.ManageAccounts,
              iconTint = BharatPurplePrimary,
              iconBackground = BharatPurpleContainer,
              title = "Account Actions",
              subtitle = "Log out or delete your account",
              testTagName = "profile_account_actions_row",
              onClick = onAccountActionsClick
            )
          }
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
          text = "Made in India with ❤\uFE0F",
          fontSize = 11.sp,
          color = BharatTextMuted,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth()
        )
      }
    }
  }

}

@Composable
private fun RoleBadge(isAdmin: Boolean, isRealVendor: Boolean) {
  val label = when {
    isAdmin -> "Admin"
    isRealVendor -> "Shop Owner"
    else -> "Customer"
  }
  val highlighted = isAdmin || isRealVendor
  Surface(
    shape = RoundedCornerShape(6.dp),
    color = if (highlighted) BharatPurpleContainer else Color(0xFFF1F5F9)
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
      Icon(
        imageVector = when {
          isAdmin -> Icons.Default.AdminPanelSettings
          isRealVendor -> Icons.Default.Storefront
          else -> Icons.Default.Person
        },
        contentDescription = null,
        tint = if (highlighted) BharatPurplePrimary else BharatTextSecondary,
        modifier = Modifier.size(11.dp)
      )
      Spacer(modifier = Modifier.width(3.dp))
      Text(
        text = label,
        color = if (highlighted) BharatPurplePrimary else BharatTextSecondary,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 10.sp
      )
    }
  }
}

@Composable
private fun SectionHeader(text: String) {
  Spacer(modifier = Modifier.height(20.dp))
  Text(
    text = text,
    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
    color = BharatTextPrimary,
    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
  )
}

@Composable
private fun QuickAccessTile(
  icon: ImageVector,
  label: String,
  testTagName: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
    modifier = modifier
      .clickable { onClick() }
      .testTag(testTagName)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 14.dp, horizontal = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Box(
        modifier = Modifier
          .size(40.dp)
          .clip(CircleShape)
          .background(BharatPurpleContainer),
        contentAlignment = Alignment.Center
      ) {
        Icon(icon, contentDescription = null, tint = BharatPurplePrimary, modifier = Modifier.size(22.dp))
      }
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = label,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = BharatTextPrimary,
        textAlign = TextAlign.Center,
        maxLines = 2
      )
    }
  }
}

@Composable
internal fun SettingsRow(
  icon: ImageVector,
  iconTint: Color,
  iconBackground: Color,
  title: String,
  subtitle: String,
  testTagName: String,
  onClick: () -> Unit,
  titleColor: Color = BharatTextPrimary
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onClick() }
      .padding(horizontal = 16.dp, vertical = 14.dp)
      .testTag(testTagName),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .size(36.dp)
        .clip(CircleShape)
        .background(iconBackground),
      contentAlignment = Alignment.Center
    ) {
      Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
    }
    Spacer(modifier = Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(text = title, fontWeight = FontWeight.SemiBold, color = titleColor, fontSize = 14.sp)
      Text(text = subtitle, color = BharatTextSecondary, fontSize = 11.sp)
    }
    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = BharatTextMuted)
  }
}

@Composable
private fun AppVersionRow() {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .size(36.dp)
        .clip(CircleShape)
        .background(Color(0xFFF1F5F9)),
      contentAlignment = Alignment.Center
    ) {
      Icon(Icons.Default.Info, contentDescription = null, tint = BharatTextSecondary, modifier = Modifier.size(20.dp))
    }
    Spacer(modifier = Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(text = "App Version", fontWeight = FontWeight.SemiBold, color = BharatTextPrimary, fontSize = 14.sp)
      Text(
        text = "Build ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        color = BharatTextSecondary,
        fontSize = 11.sp
      )
    }
  }
}

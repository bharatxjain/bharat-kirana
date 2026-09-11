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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
  onUpdateShopImage: (String, Uri) -> Unit = { _, _ -> },
  onManagePlan: () -> Unit,
  onOpenReviews: () -> Unit,
  onSupportClick: () -> Unit,
  onLogout: () -> Unit,
  // Shop settings — same state that lives on the vendor dashboard's Settings
  // tab. Rendered here so the vendor can flip open/closed, tweak packing time
  // or auto-confirm without leaving Account.
  isStoreOpen: Boolean = true,
  autoConfirmOrders: Boolean = true,
  packingTimeMinutes: Int = 12,
  onToggleStoreStatus: () -> Unit = {},
  onToggleAutoConfirm: () -> Unit = {},
  onUpdatePackingTime: (Int) -> Unit = {},
  onOpenHowItWorks: () -> Unit = {},
  onOpenCancellationPolicy: () -> Unit = {},
  onOpenPrivacyPolicy: () -> Unit = {},
  onOpenTerms: () -> Unit = {},
  onOpenAboutUs: () -> Unit = {},
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
      // ── Header: shop identity + live status pill ──────────────────────────
      item {
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White),
          border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(56.dp)
                  .clip(CircleShape)
                  .background(BharatPurpleContainer),
                contentAlignment = Alignment.Center
              ) {
                if (shop.imageUrl.isNotBlank() && shop.imageUrl != "null") {
                  AsyncImage(
                    model = shop.imageUrl,
                    contentDescription = "Shop photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                  )
                } else {
                  Text(
                    text = shop.name.firstOrNull()?.uppercase() ?: "S",
                    color = BharatPurplePrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp
                  )
                }
              }
              Spacer(modifier = Modifier.width(14.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = shop.name.ifBlank { "Shop" },
                  fontWeight = FontWeight.ExtraBold,
                  fontSize = 17.sp,
                  color = BharatTextPrimary,
                  maxLines = 1
                )
                Text(
                  text = shop.ownerName.ifBlank { userProfile.fullName.ifBlank { "Shop Owner" } },
                  fontSize = 12.sp,
                  color = BharatTextSecondary,
                  maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(12.dp))
                  Spacer(modifier = Modifier.width(3.dp))
                  Text(
                    text = if (shop.ratingCount > 0)
                      "%.1f · %d review%s".format(shop.rating, shop.ratingCount, if (shop.ratingCount == 1) "" else "s")
                    else
                      "No reviews yet",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = BharatTextSecondary
                  )
                }
              }
              // Live status pill
              val pillBg = if (isStoreOpen) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)
              val pillFg = if (isStoreOpen) Color(0xFF166534) else Color(0xFFDC2626)
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(10.dp))
                  .background(pillBg)
                  .padding(horizontal = 10.dp, vertical = 6.dp)
              ) {
                Text(
                  text = if (isStoreOpen) "OPEN" else "CLOSED",
                  color = pillFg,
                  fontWeight = FontWeight.ExtraBold,
                  fontSize = 10.sp
                )
              }
            }
          }
        }
      }

      // ── Shop operations card (open/closed + packing time + auto-confirm) ──
      item { SectionHeader("Shop operations") }
      item {
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White),
          border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            SettingSwitchRow(
              title = "Accepting orders",
              subtitle = if (isStoreOpen) "Customers can place orders" else "Shop is closed for new orders",
              checked = isStoreOpen,
              onCheckedChange = { onToggleStoreStatus() }
            )
            Spacer(modifier = Modifier.height(6.dp))
            SettingSwitchRow(
              title = "Auto-confirm orders",
              subtitle = if (autoConfirmOrders) "New orders skip manual accept" else "You'll accept each order manually",
              checked = autoConfirmOrders,
              onCheckedChange = { onToggleAutoConfirm() }
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
              "Packing time",
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp,
              color = BharatTextPrimary
            )
            Text(
              "How long you usually take to pack an order. Customers see this as their pickup ETA.",
              fontSize = 11.sp,
              color = BharatTextSecondary
            )
            Spacer(modifier = Modifier.height(10.dp))
            // Equal-weight pill strip — each option gets 1/5 of the row so
            // labels never wrap regardless of device width.
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              listOf(5, 10, 15, 20, 30).forEach { mins ->
                val selected = packingTimeMinutes == mins
                Box(
                  modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) BharatPurplePrimary else Color(0xFFF1F5F9))
                    .clickable { onUpdatePackingTime(mins) },
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = "$mins min",
                    color = if (selected) Color.White else BharatTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    maxLines = 1
                  )
                }
              }
            }
          }
        }
      }

      // ── Business ──────────────────────────────────────────────────────────
      item { SectionHeader("Business") }
      item {
        ProfileActionCard(
          icon = Icons.Default.Payment,
          title = "Subscription plan",
          subtitle = currentTierName?.let { "$it plan · Manage billing" } ?: "Choose or upgrade your plan",
          onClick = onManagePlan
        )
      }
      item {
        ProfileActionCard(
          icon = Icons.Default.Star,
          title = "Ratings & Reviews",
          subtitle = if (shop.ratingCount > 0)
            "★ %.1f · %d review%s".format(shop.rating, shop.ratingCount, if (shop.ratingCount == 1) "" else "s")
          else
            "See what customers are saying",
          onClick = onOpenReviews,
          accentColor = Color(0xFFF59E0B)
        )
      }

      // ── Shop account ──────────────────────────────────────────────────────
      item { SectionHeader("Shop account") }
      item {
        ProfileActionCard(
          icon = Icons.Default.Storefront,
          title = "Edit shop details",
          subtitle = "Shop name, phone, address",
          onClick = { showEditShopDialog = true }
        )
      }
      item {
        ProfileActionCard(
          icon = Icons.Default.Edit,
          title = "Edit owner info",
          subtitle = "Your name, mobile number and address",
          onClick = { showEditPersonalDialog = true }
        )
      }

      // ── Support ──────────────────────────────────────────────────────────
      item { SectionHeader("Support") }
      item {
        ProfileActionCard(
          icon = Icons.Default.ChatBubble,
          title = "Chat with BreakQ support",
          subtitle = "Message our team on WhatsApp",
          onClick = onSupportClick,
          accentColor = BharatGreen
        )
      }
      item {
        ProfileActionCard(
          icon = Icons.AutoMirrored.Filled.HelpOutline,
          title = "How BreakQ works",
          subtitle = "For shop owners",
          onClick = onOpenHowItWorks
        )
      }
      item {
        ProfileActionCard(
          icon = Icons.Default.Timer,
          title = "Cancellation policy",
          subtitle = "When and why an order can be cancelled",
          onClick = onOpenCancellationPolicy
        )
      }

      // ── Legal ────────────────────────────────────────────────────────────
      item { SectionHeader("Legal") }
      item {
        ProfileActionCard(
          icon = Icons.Default.PrivacyTip,
          title = "Privacy policy",
          subtitle = "How we handle your data",
          onClick = onOpenPrivacyPolicy
        )
      }
      item {
        ProfileActionCard(
          icon = Icons.Default.Gavel,
          title = "Terms of service",
          subtitle = "Your agreement with BreakQ",
          onClick = onOpenTerms
        )
      }
      item {
        ProfileActionCard(
          icon = Icons.Default.Info,
          title = "About BreakQ",
          subtitle = "Version, credits, contact",
          onClick = onOpenAboutUs
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
      item {
        Text(
          text = userProfile.email,
          fontSize = 11.sp,
          color = BharatTextMuted,
          modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )
      }
    }
  }

  if (showEditShopDialog) {
    var name by remember { mutableStateOf(shop.name) }
    var owner by remember { mutableStateOf(shop.ownerName) }
    var addr by remember { mutableStateOf(shop.address) }
    var phone by remember { mutableStateOf(shop.phone) }
    var newPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val photoPickerLauncher = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.PickVisualMedia(),
      onResult = { uri -> if (uri != null) newPhotoUri = uri }
    )
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
          // Shop hero image picker + preview. Tapping the tile opens the
          // system photo picker; the chosen URI is uploaded on Save.
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(140.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(Color(0xFFF1F5F9))
              .clickable {
                photoPickerLauncher.launch(
                  PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
              },
            contentAlignment = Alignment.Center
          ) {
            when {
              newPhotoUri != null -> AsyncImage(
                model = newPhotoUri,
                contentDescription = "New shop photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
              )
              shop.imageUrl.isNotBlank() && shop.imageUrl != "null" -> AsyncImage(
                model = shop.imageUrl,
                contentDescription = "Current shop photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
              )
              else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, tint = BharatPurplePrimary)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Tap to add shop photo", fontSize = 12.sp, color = BharatTextSecondary)
              }
            }
          }
          if (newPhotoUri != null) {
            Text(
              "New photo selected — will replace current shop image on Save.",
              fontSize = 11.sp,
              color = BharatPurplePrimary
            )
          }
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
            newPhotoUri?.let { onUpdateShopImage(shop.id, it) }
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
private fun SectionHeader(text: String) {
  Text(
    text = text.uppercase(),
    fontWeight = FontWeight.ExtraBold,
    fontSize = 11.sp,
    color = BharatTextSecondary,
    letterSpacing = 0.6.sp,
    modifier = Modifier.padding(start = 4.dp, top = 8.dp)
  )
}

@Composable
private fun SettingSwitchRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BharatTextPrimary)
      Text(subtitle, fontSize = 11.sp, color = BharatTextSecondary)
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      colors = SwitchDefaults.colors(
        checkedThumbColor = Color.White,
        checkedTrackColor = BharatPurplePrimary,
        uncheckedThumbColor = Color.White,
        uncheckedTrackColor = Color(0xFFCBD5E1)
      )
    )
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

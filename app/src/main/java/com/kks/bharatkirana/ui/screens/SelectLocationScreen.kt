package com.kks.bharatkirana.ui.screens

import android.location.Geocoder
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kks.bharatkirana.data.model.CustomerAddress
import com.kks.bharatkirana.ui.theme.BharatBackground
import com.kks.bharatkirana.ui.theme.BharatPurpleContainer
import com.kks.bharatkirana.ui.theme.BharatPurplePrimary
import com.kks.bharatkirana.ui.theme.BharatTextMuted
import com.kks.bharatkirana.ui.theme.BharatTextPrimary
import com.kks.bharatkirana.ui.theme.BharatTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One geocoded suggestion from an area search. */
data class AreaSuggestion(val title: String, val subtitle: String, val lat: Double, val lng: Double)

@Composable
fun SelectLocationScreen(
  addresses: List<CustomerAddress>,
  isLoading: Boolean,
  onBackClick: () -> Unit,
  onUseCurrentLocation: () -> Unit,
  onAddNewAddress: (lat: Double?, lng: Double?) -> Unit,
  onSelectAddress: (CustomerAddress) -> Unit,
  onEditAddress: (CustomerAddress) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  var query by remember { mutableStateOf("") }
  var suggestions by remember { mutableStateOf<List<AreaSuggestion>>(emptyList()) }
  var searching by remember { mutableStateOf(false) }

  LaunchedEffect(query) {
    if (query.trim().length < 3) {
      suggestions = emptyList()
      return@LaunchedEffect
    }
    searching = true
    suggestions = withContext(Dispatchers.IO) {
      runCatching {
        @Suppress("DEPRECATION")
        Geocoder(context, java.util.Locale.getDefault()).getFromLocationName(query.trim(), 6)
      }.getOrNull().orEmpty().mapNotNull { a ->
        val title = a.featureName ?: a.subLocality ?: a.locality ?: return@mapNotNull null
        AreaSuggestion(
          title = title,
          subtitle = listOfNotNull(a.subLocality, a.locality, a.adminArea, a.postalCode)
            .filter { it.isNotBlank() }.distinct().joinToString(", "),
          lat = a.latitude,
          lng = a.longitude
        )
      }
    }
    searching = false
  }

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
          text = "Select Your Location",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = BharatTextPrimary
        )
      }
    }

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      item {
        OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          placeholder = { Text("Search an area or address", color = BharatTextMuted) },
          trailingIcon = {
            if (searching) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            else Icon(Icons.Default.Search, contentDescription = null, tint = BharatTextSecondary)
          },
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth(),
          colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = BharatTextPrimary,
            unfocusedTextColor = BharatTextPrimary,
            focusedBorderColor = BharatPurplePrimary
          )
        )
      }

      if (suggestions.isNotEmpty()) {
        items(suggestions) { s ->
          Card(
            onClick = { onAddNewAddress(s.lat, s.lng) },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.Place, contentDescription = null, tint = BharatPurplePrimary)
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text(s.title, fontWeight = FontWeight.Bold, color = BharatTextPrimary, fontSize = 14.sp)
                if (s.subtitle.isNotBlank()) {
                  Text(s.subtitle, color = BharatTextSecondary, fontSize = 12.sp, maxLines = 2)
                }
              }
            }
          }
        }
      } else {
        item {
          Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionTile(
              icon = { Icon(Icons.Default.MyLocation, contentDescription = null, tint = BharatPurplePrimary) },
              title = "Use Current Location",
              onClick = onUseCurrentLocation,
              modifier = Modifier.weight(1f)
            )
            ActionTile(
              icon = { Icon(Icons.Default.Add, contentDescription = null, tint = BharatPurplePrimary) },
              title = "Add New Address",
              onClick = { onAddNewAddress(null, null) },
              modifier = Modifier.weight(1f)
            )
          }
        }

        item {
          Text(
            text = "SAVED ADDRESSES",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = BharatTextMuted,
            modifier = Modifier.padding(top = 4.dp)
          )
        }

        when {
          isLoading && addresses.isEmpty() -> item {
            Box(
              modifier = Modifier.fillMaxWidth().padding(32.dp),
              contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = BharatPurplePrimary, strokeWidth = 3.dp) }
          }

          addresses.isEmpty() -> item {
            Card(
              shape = RoundedCornerShape(16.dp),
              colors = CardDefaults.cardColors(containerColor = Color.White),
              border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                Icon(Icons.Default.Place, contentDescription = null, tint = BharatTextMuted, modifier = Modifier.size(40.dp))
                Spacer(modifier = Modifier.height(10.dp))
                Text("No saved addresses yet", fontWeight = FontWeight.Bold, color = BharatTextPrimary)
                Text(
                  "Add one so we can show shops near you.",
                  color = BharatTextSecondary,
                  fontSize = 13.sp
                )
              }
            }
          }

          else -> items(addresses, key = { it.id }) { address ->
            AddressRow(
              address = address,
              onClick = { onSelectAddress(address) },
              onEdit = { onEditAddress(address) }
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ActionTile(
  icon: @Composable () -> Unit,
  title: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    onClick = onClick,
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
    modifier = modifier
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      icon()
      Spacer(modifier = Modifier.height(10.dp))
      Text(title, fontWeight = FontWeight.SemiBold, color = BharatTextPrimary, fontSize = 14.sp)
    }
  }
}

@Composable
fun AddressRow(
  address: CustomerAddress,
  onClick: () -> Unit,
  onEdit: () -> Unit,
  onDelete: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(1.dp, if (address.isDefault) BharatPurplePrimary else Color(0xFFE2E8F0)),
    modifier = modifier.fillMaxWidth().clickable(onClick = onClick)
  ) {
    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
      Box(
        modifier = Modifier.size(38.dp).clip(CircleShape).background(BharatPurpleContainer),
        contentAlignment = Alignment.Center
      ) {
        Icon(Icons.Default.Place, contentDescription = null, tint = BharatPurplePrimary, modifier = Modifier.size(20.dp))
      }
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(address.label, fontWeight = FontWeight.Bold, color = BharatTextPrimary)
          if (address.isDefault) {
            Spacer(modifier = Modifier.width(6.dp))
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = BharatPurplePrimary, modifier = Modifier.size(13.dp))
            Text(" Delivering here", fontSize = 11.sp, color = BharatPurplePrimary, fontWeight = FontWeight.SemiBold)
          }
        }
        Text(address.formatted, color = BharatTextSecondary, fontSize = 12.sp)
        if (!address.isForSelf && address.recipientName.isNotBlank()) {
          Text(
            text = "${address.recipientName} · +91 ${address.recipientPhone}",
            color = BharatTextMuted,
            fontSize = 11.sp
          )
        }
        Row {
          TextButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
            Text("Edit", color = BharatPurplePrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
          }
          if (onDelete != null) {
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = onDelete, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
              Text("Delete", color = Color(0xFFDC2626), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }
  }
}

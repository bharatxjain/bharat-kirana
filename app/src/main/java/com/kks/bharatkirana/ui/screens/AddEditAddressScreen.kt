package com.kks.bharatkirana.ui.screens

import android.location.Geocoder
import android.location.Location
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kks.bharatkirana.data.maps.MapplsConfig
import com.kks.bharatkirana.data.model.CustomerAddress
import com.kks.bharatkirana.data.model.UserProfile
import com.kks.bharatkirana.ui.components.LocationPickerMap
import com.kks.bharatkirana.ui.theme.BharatBackground
import com.kks.bharatkirana.ui.theme.BharatPurpleContainer
import com.kks.bharatkirana.ui.theme.BharatPurplePrimary
import com.kks.bharatkirana.ui.theme.BharatTextMuted
import com.kks.bharatkirana.ui.theme.BharatTextPrimary
import com.kks.bharatkirana.ui.theme.BharatTextSecondary

@Composable
fun AddEditAddressScreen(
  existing: CustomerAddress?,
  userProfile: UserProfile,
  userLocation: Location?,
  isSaving: Boolean,
  errorMessage: String?,
  onRequestCurrentLocation: () -> Unit,
  onDismissError: () -> Unit,
  onBackClick: () -> Unit,
  onSave: (CustomerAddress) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current

  var houseNo by remember { mutableStateOf(existing?.houseNo ?: "") }
  var building by remember { mutableStateOf(existing?.building ?: "") }
  var floor by remember { mutableStateOf(existing?.floor ?: "") }
  var areaStreet by remember { mutableStateOf(existing?.areaStreet ?: "") }
  var landmark by remember { mutableStateOf(existing?.landmark ?: "") }
  var city by remember { mutableStateOf(existing?.city ?: "") }
  var stateName by remember { mutableStateOf(existing?.state ?: "") }
  var pincode by remember { mutableStateOf(existing?.pincode ?: "") }
  var label by remember { mutableStateOf(existing?.label ?: "Home") }
  var isForSelf by remember { mutableStateOf(existing?.isForSelf ?: true) }
  var recipientName by remember { mutableStateOf(existing?.recipientName ?: "") }
  var recipientPhone by remember { mutableStateOf(existing?.recipientPhone ?: "") }
  var lat by remember { mutableStateOf(existing?.lat) }
  var lng by remember { mutableStateOf(existing?.lng) }

  // Only auto-fill from a reverse geocode once, and never over typed text.
  var geocodedFor by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(lat, lng) {
    val la = lat
    val ln = lng
    val key = "$la,$ln"
    if (la == null || ln == null || geocodedFor == key) return@LaunchedEffect
    geocodedFor = key
    runCatching {
      @Suppress("DEPRECATION")
      Geocoder(context, java.util.Locale.getDefault()).getFromLocation(la, ln, 1)
    }.getOrNull()?.firstOrNull()?.let { a ->
      if (areaStreet.isBlank()) {
        areaStreet = listOfNotNull(a.subLocality, a.locality).filter { it.isNotBlank() }
          .joinToString(", ")
          .ifBlank { a.thoroughfare.orEmpty() }
      }
      if (city.isBlank()) city = a.locality.orEmpty().ifBlank { a.subAdminArea.orEmpty() }
      if (stateName.isBlank()) stateName = a.adminArea.orEmpty()
      if (pincode.isBlank()) pincode = a.postalCode.orEmpty()
    }
  }

  val canSave = houseNo.isNotBlank() && areaStreet.isNotBlank() && city.isNotBlank() &&
    (isForSelf || (recipientName.isNotBlank() && recipientPhone.length == 10)) && !isSaving

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
            text = if (existing == null) "Add address details" else "Edit address",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = BharatTextPrimary
          )
        }
      }

      Column(
        modifier = Modifier
          .weight(1f)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        if (errorMessage != null) {
          Surface(
            color = Color(0xFFFEE2E2),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = errorMessage,
                color = Color(0xFF991B1B),
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
              )
              TextButton(onClick = onDismissError) { Text("Dismiss", color = Color(0xFF991B1B)) }
            }
          }
        }

        // ---- Map + pin -------------------------------------------------------
        AddressCard {
          if (MapplsConfig.isConfigured) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
            ) {
              LocationPickerMap(
                lat = lat,
                lng = lng,
                onPick = { pickedLat, pickedLng -> lat = pickedLat; lng = pickedLng },
                modifier = Modifier.fillMaxSize()
              )
            }
          } else {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
                .background(BharatPurpleContainer),
              horizontalArrangement = Arrangement.Center,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(Icons.Default.Place, contentDescription = null, tint = BharatPurplePrimary)
              Spacer(modifier = Modifier.width(8.dp))
              Text("Map unavailable — you can still type the address", fontSize = 12.sp, color = BharatTextSecondary)
            }
          }
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Location pin", fontWeight = FontWeight.Bold, color = BharatTextPrimary, fontSize = 13.sp)
              Text(
                text = if (lat != null && lng != null)
                  "%.5f, %.5f".format(lat, lng)
                else
                  "Tap the map or use Locate",
                color = BharatTextSecondary,
                fontSize = 12.sp
              )
            }
            Surface(
              onClick = {
                onRequestCurrentLocation()
                userLocation?.let { lat = it.latitude; lng = it.longitude }
              },
              color = BharatPurpleContainer,
              shape = RoundedCornerShape(20.dp)
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(Icons.Default.MyLocation, contentDescription = null, tint = BharatPurplePrimary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Locate", color = BharatPurplePrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
              }
            }
          }
        }

        // ---- Structured fields ----------------------------------------------
        AddressCard {
          Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Text("Address details", fontWeight = FontWeight.Bold, color = BharatTextPrimary)
            AddressField(houseNo, { houseNo = it }, "House / Flat / Door no. *")
            AddressField(building, { building = it }, "Building / Apartment / Society (optional)")
            AddressField(floor, { floor = it }, "Floor (optional)")
            AddressField(areaStreet, { areaStreet = it }, "Area / Street *")
            AddressField(landmark, { landmark = it }, "Landmark (optional)")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              AddressField(city, { city = it }, "City *", modifier = Modifier.weight(1f))
              AddressField(stateName, { stateName = it }, "State (optional)", modifier = Modifier.weight(1f))
            }
            AddressField(
              value = pincode,
              onValueChange = { pincode = it.filter { c -> c.isDigit() }.take(6) },
              label = "Pincode (optional)",
              keyboardType = KeyboardType.Number
            )
          }
        }

        // ---- Label -----------------------------------------------------------
        AddressCard {
          Column(modifier = Modifier.padding(14.dp)) {
            Text("Save as", fontWeight = FontWeight.Bold, color = BharatTextPrimary)
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              listOf("Home", "Work", "Other").forEach { option ->
                val selected = label == option
                Surface(
                  onClick = { label = option },
                  shape = RoundedCornerShape(10.dp),
                  color = if (selected) BharatPurpleContainer else Color(0xFFF8FAFC),
                  border = BorderStroke(1.dp, if (selected) BharatPurplePrimary else Color(0xFFE2E8F0)),
                  modifier = Modifier.weight(1f)
                ) {
                  Text(
                    text = option,
                    color = if (selected) BharatPurplePrimary else BharatTextSecondary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 12.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                  )
                }
              }
            }
          }
        }

        // ---- Contact ---------------------------------------------------------
        AddressCard {
          Column(modifier = Modifier.padding(14.dp)) {
            Text("Contact details", fontWeight = FontWeight.Bold, color = BharatTextPrimary)
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
              Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
              ) {
                RadioButton(
                  selected = isForSelf,
                  onClick = { isForSelf = true },
                  colors = RadioButtonDefaults.colors(selectedColor = BharatPurplePrimary)
                )
                Text("Myself", fontWeight = FontWeight.SemiBold, color = BharatTextPrimary, fontSize = 14.sp)
              }
              Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
              ) {
                RadioButton(
                  selected = !isForSelf,
                  onClick = { isForSelf = false },
                  colors = RadioButtonDefaults.colors(selectedColor = BharatPurplePrimary)
                )
                Text("Someone else", color = BharatTextPrimary, fontSize = 14.sp)
              }
            }
            if (isForSelf) {
              Text(
                text = "Orders will use ${userProfile.fullName.ifBlank { "your profile" }}" +
                  if (userProfile.mobileNumber.isNotBlank()) " · +91 ${userProfile.mobileNumber}" else "",
                fontSize = 12.sp,
                color = BharatTextMuted
              )
            } else {
              Spacer(modifier = Modifier.height(8.dp))
              AddressField(recipientName, { recipientName = it }, "Receiver's name *")
              Spacer(modifier = Modifier.height(8.dp))
              AddressField(
                value = recipientPhone,
                onValueChange = { recipientPhone = it.filter { c -> c.isDigit() }.take(10) },
                label = "Receiver's phone number *",
                keyboardType = KeyboardType.Phone
              )
            }
          }
        }
      }

      // ---- Save ---------------------------------------------------------------
      Surface(color = Color.White, shadowElevation = 8.dp) {
        Button(
          onClick = {
            onSave(
              CustomerAddress(
                id = existing?.id.orEmpty(),
                label = label,
                houseNo = houseNo.trim(),
                building = building.trim(),
                floor = floor.trim(),
                areaStreet = areaStreet.trim(),
                landmark = landmark.trim(),
                city = city.trim(),
                state = stateName.trim(),
                pincode = pincode.trim(),
                lat = lat,
                lng = lng,
                isForSelf = isForSelf,
                recipientName = recipientName.trim(),
                recipientPhone = recipientPhone.trim(),
                isDefault = existing?.isDefault ?: false
              )
            )
          },
          enabled = canSave,
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(containerColor = BharatPurplePrimary),
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .navigationBarsPadding()
            .height(50.dp)
        ) {
          if (isSaving) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
          } else {
            Text("Save address", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
          }
        }
      }
    }
  }
}

@Composable
private fun AddressCard(content: @Composable ColumnScope.() -> Unit) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
    modifier = Modifier.fillMaxWidth()
  ) { content() }
}

@Composable
private fun AddressField(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
  keyboardType: KeyboardType = KeyboardType.Text,
  modifier: Modifier = Modifier
) {
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    label = { Text(label, fontSize = 13.sp) },
    singleLine = true,
    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    shape = RoundedCornerShape(10.dp),
    modifier = modifier.fillMaxWidth(),
    colors = OutlinedTextFieldDefaults.colors(
      focusedTextColor = BharatTextPrimary,
      unfocusedTextColor = BharatTextPrimary,
      focusedBorderColor = BharatPurplePrimary
    )
  )
}

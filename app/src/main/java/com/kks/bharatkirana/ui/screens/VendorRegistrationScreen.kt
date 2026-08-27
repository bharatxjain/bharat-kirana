package com.kks.bharatkirana.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import coil.compose.AsyncImage
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.kks.bharatkirana.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorRegistrationScreen(
  onRegisterClick: (name: String, owner: String, address: String, phone: String, category: String, lat: Double, lng: Double, yearsInBusiness: Int, shopPhoto: Uri?, businessProof: Uri?) -> Unit,
  onBackClick: () -> Unit,
  uploadState: String = "IDLE",
  uploadPercent: Int = 0,
  uploadError: String? = null,
  onDismissUploadError: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  var step by remember { mutableIntStateOf(1) }
  
  // Step 1: Basic Info
  var shopName by remember { mutableStateOf("") }
  var selectedCategory by remember { mutableStateOf("Grocery") }
  var yearsInBusiness by remember { mutableStateOf("") }
  
  // Step 2: Contact & Address
  var ownerName by remember { mutableStateOf("") }
  var shopAddress by remember { mutableStateOf("") }
  var phoneNumber by remember { mutableStateOf("") }
  var lat by remember { mutableDoubleStateOf(0.0) }
  var lng by remember { mutableDoubleStateOf(0.0) }
  var showMapPicker by remember { mutableStateOf(false) }

  // Step 3: Verification
  var gstNumber by remember { mutableStateOf("") }
  var shopPhotoUri by remember { mutableStateOf<Uri?>(null) }
  var businessProofUri by remember { mutableStateOf<Uri?>(null) }

  val photoPickerLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.PickVisualMedia(), onResult = { uri -> shopPhotoUri = uri })
  val proofPickerLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.PickVisualMedia(), onResult = { uri -> businessProofUri = uri })

  // Driven by GroceryViewModel.VendorUploadState — passed in as a plain String so
  // this screen stays free of a ViewModel dependency.
  val isUploading = uploadState != "IDLE"
  val uploadStatusText = when (uploadState) {
    "UPLOADING_PHOTO" -> "Uploading shop photo… $uploadPercent%"
    "UPLOADING_PROOF" -> "Uploading business proof… $uploadPercent%"
    "SAVING_SHOP" -> "Saving your shop details…"
    else -> ""
  }
  val uploadProgressFraction = (uploadPercent.coerceIn(0, 100)) / 100f

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = "BreakQ Store",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = BharatPurplePrimary
          )
        },
        navigationIcon = {
          IconButton(onClick = { if (step > 1) step-- else onBackClick() }) {
            Icon(
              imageVector = if (step > 1) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Close,
              contentDescription = "Back",
              tint = BharatPurplePrimary
            )
          }
        },
        actions = {
          Text(
            text = "Step $step of 3",
            color = BharatTextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(end = 16.dp)
          )
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
      )
    },
    modifier = modifier.fillMaxSize()
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .background(Color(0xFFF9FAFB))
        .padding(paddingValues)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Text(
          text = "Partner With Us",
          style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
          color = BharatTextPrimary
        )
        Text(
          text = "Join our network of premium local grocers and expand your reach.",
          style = MaterialTheme.typography.bodyMedium,
          color = BharatTextSecondary,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(top = 8.dp)
        )
      }

      // Progress Steps
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 40.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        RegistrationStepItem(number = 1, label = "Basic", isActive = step >= 1)
        RegistrationStepItem(number = 2, label = "Contact", isActive = step >= 2)
        RegistrationStepItem(number = 3, label = "Verify", isActive = step >= 3)
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Content Card
      Card(
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxSize(),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
      ) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
        ) {
          when (step) {
            1 -> {
              Text(text = "Shop Details", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = BharatPurplePrimary)
              Spacer(modifier = Modifier.height(24.dp))
              
              OutlinedTextField(
                value = shopName,
                onValueChange = { shopName = it },
                label = { Text("Shop Name") },
                placeholder = { Text("e.g., Mahavir Kirana Store", color = BharatTextMuted) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                  focusedTextColor = BharatTextPrimary,
                  unfocusedTextColor = BharatTextPrimary,
                  focusedBorderColor = BharatPurplePrimary,
                  unfocusedBorderColor = Color(0xFFE5E7EB)
                )
              )

              Spacer(modifier = Modifier.height(16.dp))

              // Surfaced to customers on the store page as a trust signal.
              OutlinedTextField(
                value = yearsInBusiness,
                onValueChange = { input -> yearsInBusiness = input.filter { it.isDigit() }.take(2) },
                label = { Text("Years in Business") },
                placeholder = { Text("e.g., 8", color = BharatTextMuted) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = {
                  Text(
                    text = "Shown to customers as \"${yearsInBusiness.ifBlank { "8" }} years in business\" — builds trust with first-time buyers",
                    fontSize = 11.sp,
                    color = BharatTextSecondary
                  )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                  focusedTextColor = BharatTextPrimary,
                  unfocusedTextColor = BharatTextPrimary,
                  focusedBorderColor = BharatPurplePrimary,
                  unfocusedBorderColor = Color(0xFFE5E7EB)
                )
              )

              Spacer(modifier = Modifier.height(24.dp))
              Text(text = "Primary Category", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BharatTextPrimary, modifier = Modifier.padding(bottom = 12.dp))

              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CategorySelectCard(title = "Grocery", icon = Icons.Default.ShoppingBasket, isSelected = selectedCategory == "Grocery", onClick = { selectedCategory = "Grocery" }, modifier = Modifier.weight(1f))
                CategorySelectCard(title = "Dairy", icon = Icons.Default.WaterDrop, isSelected = selectedCategory == "Dairy", onClick = { selectedCategory = "Dairy" }, modifier = Modifier.weight(1f))
              }
              Spacer(modifier = Modifier.height(12.dp))
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CategorySelectCard(title = "Medical", icon = Icons.Default.LocalPharmacy, isSelected = selectedCategory == "Medical", onClick = { selectedCategory = "Medical" }, modifier = Modifier.weight(1f))
                CategorySelectCard(title = "Beauty", icon = Icons.Default.Face, isSelected = selectedCategory == "Beauty", onClick = { selectedCategory = "Beauty" }, modifier = Modifier.weight(1f))
              }
              Spacer(modifier = Modifier.height(12.dp))
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CategorySelectCard(title = "Electronics", icon = Icons.Default.Devices, isSelected = selectedCategory == "Electronics", onClick = { selectedCategory = "Electronics" }, modifier = Modifier.weight(1f))
                CategorySelectCard(title = "Pashu Aahar", icon = Icons.Default.Agriculture, isSelected = selectedCategory == "Pashu Aahar", onClick = { selectedCategory = "Pashu Aahar" }, modifier = Modifier.weight(1f))
              }
              Spacer(modifier = Modifier.height(12.dp))
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CategorySelectCard(title = "Others", icon = Icons.Default.Storefront, isSelected = selectedCategory == "Others", onClick = { selectedCategory = "Others" }, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.weight(1f))
              }
            }

            2 -> {
              Text(text = "Contact Information", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = BharatPurplePrimary)
              Spacer(modifier = Modifier.height(24.dp))

              OutlinedTextField(
                value = ownerName,
                onValueChange = { ownerName = it },
                label = { Text("Owner Name") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = BharatTextPrimary, unfocusedTextColor = BharatTextPrimary, focusedBorderColor = BharatPurplePrimary, unfocusedBorderColor = Color(0xFFE5E7EB))
              )

              Spacer(modifier = Modifier.height(16.dp))

              OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                label = { Text("Mobile Number") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = BharatTextPrimary, unfocusedTextColor = BharatTextPrimary, focusedBorderColor = BharatPurplePrimary, unfocusedBorderColor = Color(0xFFE5E7EB))
              )

              Spacer(modifier = Modifier.height(16.dp))

              OutlinedTextField(
                value = shopAddress,
                onValueChange = { shopAddress = it },
                label = { Text("Business Address") },
                modifier = Modifier.fillMaxWidth().height(100.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = BharatTextPrimary, unfocusedTextColor = BharatTextPrimary, focusedBorderColor = BharatPurplePrimary, unfocusedBorderColor = Color(0xFFE5E7EB))
              )

              Spacer(modifier = Modifier.height(24.dp))
              Text(text = "Location Mapping", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BharatTextPrimary)
              Spacer(modifier = Modifier.height(8.dp))
              
              Button(
                onClick = { showMapPicker = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BharatPurplePrimary)
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Default.Map, contentDescription = null, tint = BharatPurplePrimary)
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = if (lat != 0.0 || lng != 0.0) "Location Selected: ${"%.4f".format(lat)}, ${"%.4f".format(lng)}" else "Select Store Location on Map",
                    color = BharatPurplePrimary,
                    fontWeight = FontWeight.Bold
                  )
                }
              }
            }

            3 -> {
              Text(text = "Verification & Media", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = BharatPurplePrimary)
              Spacer(modifier = Modifier.height(24.dp))
              
              // Shop Photo (required)
              Row {
                Text(text = "Shop Photo", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BharatTextPrimary)
                Text(text = " *", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFDC2626))
              }
              Spacer(modifier = Modifier.height(8.dp))
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(140.dp)
                  .clip(RoundedCornerShape(12.dp))
                  .background(Color(0xFFF8FAFC))
                  .border(
                    1.dp,
                    if (shopPhotoUri != null) BharatGreen else Color(0xFFE2E8F0),
                    RoundedCornerShape(12.dp)
                  )
                  .clickable(enabled = !isUploading) {
                    photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                  },
                contentAlignment = Alignment.Center
              ) {
                if (shopPhotoUri != null) {
                  AsyncImage(
                    model = shopPhotoUri,
                    contentDescription = "Shop photo preview",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                  )
                  // Green "selected" chip in the corner + a tap-to-replace hint.
                  Surface(
                    color = BharatGreen,
                    shape = RoundedCornerShape(50.dp),
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                  ) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                      Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                      Spacer(modifier = Modifier.width(4.dp))
                      Text("Selected", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                  }
                  Surface(
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                  ) {
                    Text(
                      "Tap to change photo",
                      color = Color.White,
                      fontSize = 11.sp,
                      textAlign = TextAlign.Center,
                      modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                  }
                } else {
                  Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = BharatPurplePrimary)
                    Text("Upload Shop Photo", fontSize = 12.sp, color = BharatTextSecondary)
                  }
                }
              }

              Spacer(modifier = Modifier.height(16.dp))

              OutlinedTextField(
                value = gstNumber,
                onValueChange = { gstNumber = it },
                label = { Text("GSTIN (Optional)") },
                placeholder = { Text("22AAAAA0000A1Z5") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = BharatTextPrimary, unfocusedTextColor = BharatTextPrimary, focusedBorderColor = BharatPurplePrimary, unfocusedBorderColor = Color(0xFFE5E7EB))
              )
              
              Spacer(modifier = Modifier.height(16.dp))

              // Business Proof (optional)
              Text(text = "Business Proof (Utility bill / License) (Optional)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BharatTextPrimary)
              Spacer(modifier = Modifier.height(8.dp))
              if (businessProofUri != null) {
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, BharatGreen, RoundedCornerShape(12.dp))
                    .clickable(enabled = !isUploading) {
                      proofPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                ) {
                  AsyncImage(
                    model = businessProofUri,
                    contentDescription = "Business proof preview",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                  )
                  Surface(
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                  ) {
                    Text(
                      "Proof selected · tap to change",
                      color = Color.White,
                      fontSize = 11.sp,
                      textAlign = TextAlign.Center,
                      modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                  }
                  IconButton(
                    onClick = { businessProofUri = null },
                    modifier = Modifier.align(Alignment.TopEnd)
                  ) {
                    Surface(color = Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(50.dp)) {
                      Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove proof",
                        tint = Color.White,
                        modifier = Modifier.padding(4.dp).size(14.dp)
                      )
                    }
                  }
                }
              } else {
                Button(
                  onClick = { proofPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                  enabled = !isUploading,
                  modifier = Modifier.fillMaxWidth(),
                  colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                  shape = RoundedCornerShape(12.dp),
                  border = BorderStroke(1.dp, Color(0xFFD1D5DB))
                ) {
                  Icon(Icons.Default.UploadFile, contentDescription = null, tint = BharatTextSecondary)
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(text = "Upload Proof", color = BharatTextPrimary)
                }
              }

              // Live upload progress while the wizard pushes files to Supabase Storage.
              if (isUploading) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                  color = BharatPurpleContainer,
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = BharatPurplePrimary,
                        strokeWidth = 2.dp
                      )
                      Spacer(modifier = Modifier.width(12.dp))
                      Text(
                        text = uploadStatusText,
                        color = BharatPurpleDark,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                      )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                      progress = { uploadProgressFraction },
                      modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                      color = BharatPurplePrimary,
                      trackColor = Color.White
                    )
                  }
                }
              }

              // Upload / registration failure — actionable, dismissible.
              if (!uploadError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                  color = Color(0xFFFFE4E6),
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Icon(
                      Icons.Default.Warning,
                      contentDescription = null,
                      tint = Color(0xFFDC2626),
                      modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                      text = uploadError,
                      color = Color(0xFF991B1B),
                      fontSize = 12.sp,
                      modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismissUploadError, modifier = Modifier.size(28.dp)) {
                      Icon(
                        Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = Color(0xFF991B1B),
                        modifier = Modifier.size(16.dp)
                      )
                    }
                  }
                }
              }

              Spacer(modifier = Modifier.height(24.dp))
              
              Surface(
                color = BharatPurpleContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(modifier = Modifier.padding(16.dp)) {
                   Text(text = "Submission Summary", fontWeight = FontWeight.Bold, color = BharatPurpleDark)
                   Spacer(modifier = Modifier.height(8.dp))
                   Text(text = "Shop: $shopName", fontSize = 13.sp)
                   Text(text = "Owner: $ownerName", fontSize = 13.sp)
                   Text(text = "Location: $lat, $lng", fontSize = 13.sp)
                   Spacer(modifier = Modifier.height(8.dp))
                   Row {
                     Text(text = "*", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                     Text(text = " Required. Business proof is optional and can be added later.", fontSize = 11.sp, color = BharatTextSecondary)
                   }
                }
              }
            }
          }

          Spacer(modifier = Modifier.weight(1f))
          Spacer(modifier = Modifier.height(32.dp))

          Button(
            onClick = {
              if (step < 3) step++
              else onRegisterClick(shopName, ownerName, shopAddress, phoneNumber, selectedCategory, lat, lng, yearsInBusiness.toIntOrNull() ?: 0, shopPhotoUri, businessProofUri)
            },
            enabled = !isUploading && when(step) {
              1 -> shopName.isNotBlank()
              2 -> ownerName.isNotBlank() && phoneNumber.isNotBlank() && shopAddress.isNotBlank()
              // Shop photo is encouraged but not required — vendors can add it
              // later from the dashboard, and it is not always convenient to
              // supply during first-time setup (e.g. testing on an emulator).
              else -> true
            },
            modifier = Modifier
              .align(Alignment.End)
              .fillMaxWidth()
              .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BharatPurplePrimary),
            shape = RoundedCornerShape(16.dp)
          ) {
            if (isUploading) {
              CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color.White,
                strokeWidth = 2.dp
              )
              Spacer(modifier = Modifier.width(10.dp))
              Text(text = "Submitting…", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            } else {
              Text(
                text = if (step == 3) "Submit Application" else "Continue",
                fontWeight = FontWeight.Bold, 
                fontSize = 16.sp
              )
              if (step < 3) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp))
              }
            }
          }
          
          Spacer(modifier = Modifier.height(40.dp))
        }
      }
    }
  }

  if (showMapPicker) {
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var pinOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var locationError by remember { mutableStateOf<String?>(null) }
    // Roughly meters-per-pixel converted to degrees, so dragging across the 260dp
    // pad box moves the pin a realistic few hundred meters instead of a fixed dummy point.
    val degreesPerPixel = 0.00003

    val requestLocationPermission = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
      if (granted) {
        fusedLocationClient.getCurrentLocation(
          com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
          null
        ).addOnSuccessListener { location ->
          if (location != null) {
            lat = location.latitude
            lng = location.longitude
            pinOffset = androidx.compose.ui.geometry.Offset.Zero
            locationError = null
          } else {
            locationError = "Couldn't get current location. Drag the pin or enter coordinates manually."
          }
        }
      } else {
        locationError = "Location permission denied. Drag the pin or enter coordinates manually."
      }
    }

    fun useCurrentLocation() {
      val hasPermission = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
      ) == PackageManager.PERMISSION_GRANTED
      if (hasPermission) {
        // High-accuracy fresh fix, not the cached lastLocation — a shop's pinned
        // location should reflect where the owner is standing right now, not
        // wherever the device last happened to get a GPS/network fix.
        fusedLocationClient.getCurrentLocation(
          com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
          null
        ).addOnSuccessListener { location ->
          if (location != null) {
            lat = location.latitude
            lng = location.longitude
            pinOffset = androidx.compose.ui.geometry.Offset.Zero
            locationError = null
          } else {
            locationError = "Couldn't get current location. Drag the pin or enter coordinates manually."
          }
        }
      } else {
        requestLocationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
      }
    }

    AlertDialog(
      onDismissRequest = { showMapPicker = false },
      title = { Text("Select Store Location", fontWeight = FontWeight.Bold) },
      text = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
            "Drag the pin to fine-tune your store's exact spot, or fetch it from your device's GPS.",
            fontSize = 12.sp,
            color = BharatTextSecondary,
            textAlign = TextAlign.Center
          )
          Spacer(modifier = Modifier.height(16.dp))
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(220.dp)
              .clip(RoundedCornerShape(16.dp))
              .background(Color(0xFFE2E8F0))
              .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(16.dp))
              .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                  change.consume()
                  pinOffset += dragAmount
                  lat -= dragAmount.y * degreesPerPixel
                  lng += dragAmount.x * degreesPerPixel
                }
              },
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.LocationOn,
              contentDescription = "Store Pin — drag to adjust",
              tint = BharatPurplePrimary,
              modifier = Modifier
                .size(48.dp)
                .offset { androidx.compose.ui.unit.IntOffset(pinOffset.x.toInt(), pinOffset.y.toInt() - 40) }
            )

            Surface(
              color = Color.White.copy(alpha = 0.9f),
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)
            ) {
              Text(
                text = if (lat != 0.0 || lng != 0.0) "${"%.4f".format(lat)}, ${"%.4f".format(lng)}" else "No location set yet",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
              )
            }
          }

          Spacer(modifier = Modifier.height(12.dp))

          OutlinedButton(
            onClick = { useCurrentLocation() },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
          ) {
            Icon(Icons.Default.MyLocation, contentDescription = null, tint = BharatPurplePrimary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Use My Current Location", color = BharatPurplePrimary, fontWeight = FontWeight.SemiBold)
          }

          if (locationError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(locationError!!, fontSize = 11.sp, color = Color(0xFFDC2626), textAlign = TextAlign.Center)
          }

          Spacer(modifier = Modifier.height(12.dp))

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
              value = if (lat != 0.0) lat.toString() else "",
              onValueChange = { lat = it.toDoubleOrNull() ?: lat },
              label = { Text("Latitude", fontSize = 11.sp) },
              singleLine = true,
              keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
              modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
              value = if (lng != 0.0) lng.toString() else "",
              onValueChange = { lng = it.toDoubleOrNull() ?: lng },
              label = { Text("Longitude", fontSize = 11.sp) },
              singleLine = true,
              keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
              modifier = Modifier.weight(1f)
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            // Reverse-geocode the picked coordinates so the vendor does not have
            // to type the same address twice. Best-effort: if Geocoder is not
            // available or the network reverse lookup fails, we silently keep
            // whatever they typed.
            if (lat != 0.0 || lng != 0.0) {
              try {
                @Suppress("DEPRECATION")
                val results = Geocoder(context, java.util.Locale.getDefault())
                  .getFromLocation(lat, lng, 1)
                val line = results?.firstOrNull()?.getAddressLine(0)
                if (!line.isNullOrBlank()) shopAddress = line
              } catch (_: Exception) { /* keep manual entry */ }
            }
            showMapPicker = false
          },
          enabled = lat != 0.0 || lng != 0.0,
          colors = ButtonDefaults.buttonColors(containerColor = BharatPurplePrimary)
        ) {
          Text("Confirm Location")
        }
      },
      dismissButton = {
        TextButton(onClick = { showMapPicker = false }) {
          Text("Cancel")
        }
      }
    )
  }
}

@Composable
fun RegistrationStepItem(number: Int, label: String, isActive: Boolean) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      text = number.toString(),
      fontWeight = FontWeight.Bold,
      fontSize = 14.sp,
      color = if (isActive) BharatTextPrimary else BharatTextMuted
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
      text = label,
      fontSize = 11.sp,
      fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
      color = if (isActive) BharatPurplePrimary else BharatTextMuted
    )
  }
}

@Composable
fun CategorySelectCard(
  title: String,
  icon: ImageVector,
  isSelected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    onClick = onClick,
    shape = RoundedCornerShape(16.dp),
    border = BorderStroke(1.dp, if (isSelected) BharatPurplePrimary else Color(0xFFE5E7EB)),
    colors = CardDefaults.cardColors(
      containerColor = if (isSelected) BharatPurplePrimary else Color.White
    ),
    modifier = modifier.height(90.dp)
  ) {
    Column(
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (isSelected) Color.White else BharatPurplePrimary,
        modifier = Modifier.size(28.dp)
      )
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = if (isSelected) Color.White else BharatTextPrimary
      )
    }
  }
}

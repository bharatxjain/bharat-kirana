package com.kks.bharatkirana.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kks.bharatkirana.data.model.Product
import com.kks.bharatkirana.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProductScreen(
  onBackClick: () -> Unit,
  onListProduct: (String, String, String, Int, Int, String, Boolean, Int?, List<Uri>, String, String) -> Unit,
  onScanBarcode: () -> Unit = {},
  scannedTemplate: Product? = null,
  scannedBarcode: String? = null,
  barcodeStatusMessage: String? = null,
  onScanConsumed: () -> Unit = {},
  isUploading: Boolean = false,
  uploadResultMessage: String? = null,
  onUploadResultConsumed: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  var productName by remember { mutableStateOf("") }
  var category by remember { mutableStateOf("Select Category") }
  var weightValue by remember { mutableStateOf("") }
  var weightUnit by remember { mutableStateOf("kg") }
  var sellingPrice by remember { mutableStateOf("") }
  var mrp by remember { mutableStateOf("") }
  var description by remember { mutableStateOf("") }
  var inStock by remember { mutableStateOf(true) }
  var stockQty by remember { mutableStateOf("") }
  var selectedImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
  var localBarcode by remember { mutableStateOf<String?>(null) }
  // OpenFoodFacts hands back a remote image URL, not a gallery Uri, so it can't
  // live in selectedImageUris. Kept separately and passed through on save.
  var scannedImageUrl by remember { mutableStateOf("") }

  // Pre-fill fields from a scanned catalog match; keep localBarcode set even after the
  // ViewModel state is cleared so it survives on Save.
  LaunchedEffect(scannedTemplate?.id, scannedBarcode) {
    if (scannedBarcode != null) {
      localBarcode = scannedBarcode
      if (scannedTemplate != null) {
        if (productName.isBlank()) productName = scannedTemplate.name
        val catId = scannedTemplate.categoryId
        if (category == "Select Category" && catId.isNotBlank()) {
          category = catId.split("_").joinToString(" ") { part ->
            part.replaceFirstChar { it.uppercase() }
          }
        }
        if (description.isBlank() && scannedTemplate.description.isNotBlank()) {
          description = scannedTemplate.description
        }
        val unitStr = scannedTemplate.unit.trim()
        val parts = unitStr.split(" ", limit = 2)
        if (weightValue.isBlank() && parts.size == 2) {
          weightValue = parts[0]
          weightUnit = parts[1]
        }
        if (mrp.isBlank() && scannedTemplate.originalPrice > 0) {
          mrp = scannedTemplate.originalPrice.toString()
        }
        if (scannedImageUrl.isBlank() && scannedTemplate.imageUrl.isNotBlank()) {
          scannedImageUrl = scannedTemplate.imageUrl
        }
      }
      onScanConsumed()
    }
  }

  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickMultipleVisualMedia(3),
    onResult = { uris -> selectedImageUris = uris }
  )

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = "Add New Product",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = BharatTextPrimary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
          )
        },
        navigationIcon = {
          IconButton(onClick = onBackClick) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
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
        .verticalScroll(rememberScrollState())
        .padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
      // Scan Barcode entry point (Round 4)
      OutlinedCard(
        onClick = onScanBarcode,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = Color(0xFFF5F3FF)),
        border = BorderStroke(1.dp, BharatPurplePrimary),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.padding(14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = BharatPurplePrimary, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text("Scan Barcode", fontWeight = FontWeight.Bold, color = BharatTextPrimary, fontSize = 14.sp)
              Text("Auto-fill from existing catalog", fontSize = 11.sp, color = BharatTextSecondary)
            }
          }
          Icon(Icons.Default.ChevronRight, contentDescription = null, tint = BharatPurplePrimary)
        }
      }

      localBarcode?.let { code ->
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(color = Color(0xFFECFDF5), shape = RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(Icons.Default.CheckCircle, contentDescription = null, tint = BharatGreen, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Barcode: $code", fontSize = 12.sp, color = BharatGreen, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
          Text(
            "Remove",
            color = Color(0xFFDC2626),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { localBarcode = null }.padding(4.dp)
          )
        }
      }

      barcodeStatusMessage?.takeIf { it.isNotBlank() }?.let { msg ->
        Text(
          text = msg,
          fontSize = 12.sp,
          color = if (msg.startsWith("Found")) BharatGreen else BharatTextSecondary
        )
      }

      // Product Images Upload
      Column {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(text = "Product Images (Up to 3)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BharatTextPrimary)
          Text(text = "${selectedImageUris.size}/3", fontSize = 12.sp, color = BharatTextSecondary)
        }
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          // Image pulled from the barcode lookup — vendor can drop it and shoot
          // their own instead.
          if (scannedImageUrl.isNotBlank()) {
            Box(
              modifier = Modifier
                .weight(1f)
                .height(100.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(BorderStroke(1.dp, BharatGreen), RoundedCornerShape(12.dp))
            ) {
              AsyncImage(
                model = scannedImageUrl,
                contentDescription = "Scanned product image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
              )
              Surface(
                color = BharatGreen,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
              ) {
                Text(
                  "From barcode",
                  color = Color.White,
                  fontSize = 9.sp,
                  fontWeight = FontWeight.Bold,
                  textAlign = TextAlign.Center,
                  modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                )
              }
              IconButton(
                onClick = { scannedImageUrl = "" },
                modifier = Modifier
                  .size(24.dp)
                  .align(Alignment.TopEnd)
                  .padding(4.dp)
                  .background(Color.Black.copy(alpha = 0.5f), CircleShape)
              ) {
                Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(14.dp))
              }
            }
          }

          selectedImageUris.forEachIndexed { index, uri ->
            Box(
              modifier = Modifier
                .weight(1f)
                .height(100.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(BorderStroke(1.dp, Color(0xFFE5E7EB)), RoundedCornerShape(12.dp))
            ) {
              AsyncImage(
                model = uri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
              )
              IconButton(
                onClick = { 
                  selectedImageUris = selectedImageUris.toMutableList().apply { removeAt(index) }
                },
                modifier = Modifier
                  .size(24.dp)
                  .align(Alignment.TopEnd)
                  .padding(4.dp)
                  .background(Color.Black.copy(alpha = 0.5f), CircleShape)
              ) {
                Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(14.dp))
              }
            }
          }
          
          if (selectedImageUris.size < 3) {
            Box(
              modifier = Modifier
                .weight(1f)
                .height(100.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .border(BorderStroke(1.dp, Color(0xFFE5E7EB)), RoundedCornerShape(12.dp))
                .clickable { 
                  photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                  )
                },
              contentAlignment = Alignment.Center
            ) {
              Icon(Icons.Default.AddAPhoto, contentDescription = "Add Photo", tint = BharatPurplePrimary)
            }
          }
          
          // Fill remaining slots if any
          repeat(3 - selectedImageUris.size - (if (selectedImageUris.size < 3) 1 else 0)) {
            Spacer(modifier = Modifier.weight(1f))
          }
        }
      }

      HorizontalDivider(color = Color(0xFFE5E7EB))

      // Product Details
      AuthTextFieldSimple(
        value = productName,
        onValueChange = { productName = it },
        label = "Product Name *",
        placeholder = "e.g., Aashirvaad Whole Wheat Atta"
      )

      Column {
        Text(text = "Category *", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BharatTextPrimary, modifier = Modifier.padding(bottom = 6.dp))
        OutlinedCard(
          onClick = { /* Open category picker */ },
          shape = RoundedCornerShape(12.dp),
          colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
          border = BorderStroke(1.dp, Color(0xFFD1D5DB)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(text = category, color = if (category == "Select Category") BharatTextMuted else BharatTextPrimary)
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = BharatTextSecondary)
          }
        }
      }

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(modifier = Modifier.weight(1f)) {
          AuthTextFieldSimple(
            value = weightValue,
            onValueChange = { weightValue = it },
            label = "Weight / Quantity *",
            placeholder = "e.g., 5"
          )
        }
        Box(modifier = Modifier.weight(0.4f).padding(top = 22.dp)) {
           OutlinedCard(
            onClick = { /* Open unit picker */ },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFD1D5DB)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(text = weightUnit, color = BharatTextPrimary)
              Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = BharatTextSecondary)
            }
          }
        }
      }

      // Pricing Card
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F3FF)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(text = "PRICING INFORMATION", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = BharatTextPrimary, letterSpacing = 0.5.sp)
          Spacer(modifier = Modifier.height(12.dp))
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.weight(1f)) {
              AuthTextFieldSimple(
                value = sellingPrice,
                onValueChange = { sellingPrice = it },
                label = "Selling Price (₹) *",
                placeholder = "₹ 0.00"
              )
            }
            Box(modifier = Modifier.weight(1f)) {
              AuthTextFieldSimple(
                value = mrp,
                onValueChange = { mrp = it },
                label = "MRP (₹) (Optional)",
                placeholder = "₹ 0.00"
              )
            }
          }
        }
      }

      // Description
      Column {
        Text(text = "Product Description", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BharatTextPrimary, modifier = Modifier.padding(bottom = 6.dp))
        OutlinedTextField(
          value = description,
          onValueChange = { description = it },
          placeholder = { Text("Describe the product, its benefits, origins, etc...", color = BharatTextMuted, fontSize = 14.sp) },
          modifier = Modifier.fillMaxWidth().height(120.dp),
          shape = RoundedCornerShape(12.dp),
          colors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = BharatTextPrimary,
        unfocusedTextColor = BharatTextPrimary
      )
        )
      }

      // In Stock Toggle
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Column {
          Text(text = "In Stock", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BharatTextPrimary)
          Text(text = "Product is currently available for purchase", fontSize = 12.sp, color = BharatTextSecondary)
        }
        Switch(
          checked = inStock,
          onCheckedChange = { inStock = it },
          colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = BharatPurplePrimary)
        )
      }

      // Stock quantity drives the customer-facing badge: a number shows
      // "In Stock"/"Low Stock", leaving it blank shows "Call to Confirm".
      if (inStock) {
        OutlinedTextField(
          value = stockQty,
          onValueChange = { input -> stockQty = input.filter { it.isDigit() }.take(5) },
          label = { Text("Stock Quantity (Optional)") },
          placeholder = { Text("e.g. 24") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          supportingText = {
            Text(
              text = when (val q = stockQty.toIntOrNull()) {
                null -> "⚠️ Blank = \"Call to Confirm\" — use only if you can't count right now"
                0 -> "Customers will see \"Out of Stock\""
                in 1..5 -> "Customers will see \"Low Stock\""
                else -> "🟢 Customers will see \"In Stock\""
              },
              fontSize = 11.sp,
              color = BharatTextSecondary
            )
          },
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(12.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = BharatTextPrimary,
            unfocusedTextColor = BharatTextPrimary,
            focusedBorderColor = BharatPurplePrimary
          )
        )
      }

      Spacer(modifier = Modifier.height(24.dp))

      Button(
        onClick = { 
          onListProduct(
            productName,
            category,
            weightValue + weightUnit,
            sellingPrice.toIntOrNull() ?: 0,
            mrp.toIntOrNull() ?: 0,
            description,
            inStock,
            stockQty.toIntOrNull(),
            selectedImageUris,
            localBarcode ?: "",
            scannedImageUrl
          )
        },
        enabled = !isUploading && productName.isNotBlank() && sellingPrice.isNotBlank(),
        modifier = Modifier
          .fillMaxWidth()
          .height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = BharatPurplePrimary),
        shape = RoundedCornerShape(12.dp)
      ) {
        if (isUploading) {
          CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = Color.White,
            strokeWidth = 2.dp
          )
          Spacer(modifier = Modifier.width(12.dp))
          Text(text = "Uploading…", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        } else {
          Icon(Icons.Default.CheckCircle, contentDescription = null)
          Spacer(modifier = Modifier.width(10.dp))
          Text(text = "List Product", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
      }

      // Round 7: surfaces per-image upload result from the ViewModel.
      if (!uploadResultMessage.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(12.dp))
        val isSuccess = !uploadResultMessage.contains("failed", ignoreCase = true)
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = if (isSuccess) Color(0xFFDCFCE7) else Color(0xFFFEF3C7),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
              contentDescription = null,
              tint = if (isSuccess) Color(0xFF10B981) else Color(0xFFD97706),
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
              text = uploadResultMessage,
              modifier = Modifier.weight(1f),
              color = BharatTextPrimary,
              fontSize = 13.sp
            )
            IconButton(onClick = onUploadResultConsumed) {
              Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = BharatTextSecondary)
            }
          }
        }
      }
      
      Spacer(modifier = Modifier.height(40.dp))
    }
  }
}

@Composable
fun AuthTextFieldSimple(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
  placeholder: String
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Text(
      text = label,
      fontWeight = FontWeight.Bold,
      fontSize = 12.sp,
      color = BharatTextPrimary,
      modifier = Modifier.padding(bottom = 6.dp)
    )
    OutlinedTextField(
      value = value,
      onValueChange = onValueChange,
      placeholder = { Text(placeholder, color = BharatTextMuted, fontSize = 14.sp) },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(12.dp),
      colors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = BharatTextPrimary,
        unfocusedTextColor = BharatTextPrimary
      )
    )
  }
}

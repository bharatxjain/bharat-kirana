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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kks.bharatkirana.data.model.Category
import com.kks.bharatkirana.data.model.DuplicateAlert
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
  catalogSearchResults: List<Product> = emptyList(),
  catalogSearchLoading: Boolean = false,
  onSearchCatalog: (String) -> Unit = {},
  onSelectCatalogProduct: (Product) -> Unit = {},
  categories: List<Category> = emptyList(),
  duplicateAlert: DuplicateAlert? = null,
  onDuplicateDismiss: () -> Unit = {},
  onDuplicateUpdateStock: (Product) -> Unit = {},
  onDuplicateViewProduct: (Product) -> Unit = {},
  onDuplicateForceInsert: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  // Category names come from the ViewModel (which mirrors the Supabase
  // `categories` table). Unit list stays hardcoded — grocery weight units
  // don't change per user or per shop.
  val categoryOptions = categories.map { it.name }
  val unitOptions = listOf("kg", "g", "L", "ml", "pcs", "dozen", "pack")

  var productName by remember { mutableStateOf("") }
  var category by remember { mutableStateOf("Select Category") }
  var categoryMenuOpen by remember { mutableStateOf(false) }
  var weightValue by remember { mutableStateOf("") }
  var weightUnit by remember { mutableStateOf("kg") }
  var unitMenuOpen by remember { mutableStateOf(false) }
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
  // Community catalog search UI state.
  var showSearchDialog by remember { mutableStateOf(false) }
  var searchQuery by remember { mutableStateOf("") }

  // Pre-fill fields from a scanned catalog match; keep localBarcode set even after the
  // ViewModel state is cleared so it survives on Save.
  LaunchedEffect(scannedTemplate?.id, scannedBarcode) {
    if (scannedBarcode != null) {
      localBarcode = scannedBarcode
      if (scannedTemplate != null) {
        if (productName.isBlank()) productName = scannedTemplate.name
        val catId = scannedTemplate.categoryId
        if (category == "Select Category" && catId.isNotBlank()) {
          // OpenFoodFacts hands back tags like "Biscuits" that don't line up
          // with our picker's names. Map the raw value to an actual picker
          // option; if nothing matches, leave it blank so the vendor picks.
          matchCategoryFromRaw(catId, categories)?.let { category = it.name }
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
    bottomBar = {
      // Sticky List Product button. Vendors were losing the button below the
      // fold and thinking the form was broken; pinning it here removes the
      // "where is Save?" moment.
      val missingFields = buildList {
        if (productName.isBlank()) add("product name")
        if (category == "Select Category") add("category")
        if (weightValue.isBlank()) add("weight/quantity")
        if (sellingPrice.isBlank()) add("selling price")
      }
      Surface(
        color = Color.White,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
          if (missingFields.isNotEmpty() && !isUploading) {
            Text(
              text = "Fill required: ${missingFields.joinToString(", ")}",
              fontSize = 12.sp,
              color = BharatTextSecondary,
              modifier = Modifier.padding(bottom = 8.dp)
            )
          }
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
            enabled = !isUploading &&
              productName.isNotBlank() &&
              category != "Select Category" &&
              weightValue.isNotBlank() &&
              sellingPrice.isNotBlank(),
            modifier = Modifier
              .fillMaxWidth()
              .height(56.dp),
            // Explicit disabled colors — Material3's default disabledContainerColor
            // is a near-transparent grey that renders invisible against the white
            // sticky bar. Muted purple + white text keeps the button legible.
            colors = ButtonDefaults.buttonColors(
              containerColor = BharatPurplePrimary,
              contentColor = Color.White,
              disabledContainerColor = BharatPurplePrimary.copy(alpha = 0.35f),
              disabledContentColor = Color.White.copy(alpha = 0.7f)
            ),
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
        }
      }
    },
    modifier = modifier.fillMaxSize().imePadding()
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .padding(paddingValues)
        .verticalScroll(rememberScrollState())
        .padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
      // Three-option starting point. Vendors overwhelmingly want to
      // fast-path via barcode or catalog search; keeping "Fill Manually" as a
      // visible option makes it clear those are shortcuts, not requirements.
      Text(
        text = "How would you like to add this product?",
        fontSize = 13.sp,
        color = BharatTextSecondary,
        fontWeight = FontWeight.Medium
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        AddModeOption(
          icon = Icons.Default.QrCodeScanner,
          title = "Scan\nBarcode",
          subtitle = "Auto-fill",
          modifier = Modifier.weight(1f),
          onClick = onScanBarcode
        )
        AddModeOption(
          icon = Icons.Default.Search,
          title = "Search\nCatalog",
          subtitle = "From vendors",
          modifier = Modifier.weight(1f),
          onClick = { showSearchDialog = true; searchQuery = "" }
        )
        AddModeOption(
          icon = Icons.Default.Edit,
          title = "Fill\nManually",
          subtitle = "Type it in",
          modifier = Modifier.weight(1f),
          onClick = { /* form is already visible below */ }
        )
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
        val isPositive = msg.startsWith("Found", ignoreCase = true)
        val isLoading = msg.endsWith("\u2026") || msg.contains("Looking up", ignoreCase = true)
        val bg = when {
          isPositive -> Color(0xFFECFDF5)   // green tint
          isLoading  -> Color(0xFFF5F3FF)   // purple tint
          else       -> Color(0xFFFFF7ED)   // amber tint - "not found"
        }
        val fg = when {
          isPositive -> BharatGreen
          isLoading  -> BharatPurplePrimary
          else       -> Color(0xFF9A3412)
        }
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(color = bg, shape = RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            imageVector = when {
              isPositive -> Icons.Default.CheckCircle
              isLoading  -> Icons.Default.Search
              else       -> Icons.Default.Info
            },
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = msg,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = fg
          )
        }
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

      HorizontalDivider(color = MaterialTheme.colorScheme.outline)

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
          onClick = { categoryMenuOpen = true },
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
            onValueChange = { input -> weightValue = input.filter { it.isDigit() || it == '.' }.take(7) },
            label = "Weight / Quantity *",
            placeholder = "e.g., 5",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
          )
        }
        Box(modifier = Modifier.weight(0.4f).padding(top = 22.dp)) {
          OutlinedCard(
            onClick = { unitMenuOpen = true },
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(text = "PRICING INFORMATION", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = BharatTextPrimary, letterSpacing = 0.5.sp)
          Spacer(modifier = Modifier.height(12.dp))
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.weight(1f)) {
              AuthTextFieldSimple(
                value = sellingPrice,
                onValueChange = { input -> sellingPrice = input.filter { it.isDigit() }.take(6) },
                label = "Selling Price (₹) *",
                placeholder = "₹ 0.00",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
              )
            }
            Box(modifier = Modifier.weight(1f)) {
              AuthTextFieldSimple(
                value = mrp,
                onValueChange = { input -> mrp = input.filter { it.isDigit() }.take(6) },
                label = "MRP (₹) (Optional)",
                placeholder = "₹ 0.00",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
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

      Spacer(modifier = Modifier.height(16.dp))
    }
  }

  // Category picker sheet - replaces the dark Material dropdown that vendors
  // said was "a black rectangle with white rows". A grid of tiles is way
  // easier to eyeball on a small screen.
  if (categoryMenuOpen) {
    ModalBottomSheet(
      onDismissRequest = { categoryMenuOpen = false },
      containerColor = MaterialTheme.colorScheme.surface,
      dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline) }
    ) {
      Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
        Text(
          text = "Choose a category",
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold,
          color = BharatTextPrimary,
          modifier = Modifier.padding(bottom = 14.dp)
        )
        val rows = categoryOptions.chunked(2)
        rows.forEach { pair ->
          Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            pair.forEach { option ->
              val selected = option == category
              val tint = categoryColorFor(option)
              OutlinedCard(
                onClick = { category = option; categoryMenuOpen = false },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.outlinedCardColors(
                  containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                  width = if (selected) 2.dp else 1.dp,
                  color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier.weight(1f)
              ) {
                Row(
                  modifier = Modifier.padding(12.dp).heightIn(min = 48.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Box(
                    modifier = Modifier
                      .size(32.dp)
                      .clip(CircleShape)
                      .background(tint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(
                      imageVector = categoryIconFor(option),
                      contentDescription = null,
                      tint = tint,
                      modifier = Modifier.size(18.dp)
                    )
                  }
                  Spacer(modifier = Modifier.width(10.dp))
                  Text(
                    text = option,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) BharatPurplePrimary else BharatTextPrimary
                  )
                }
              }
            }
            if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
          }
        }
      }
    }
  }

  // Unit picker sheet - same visual language as the category picker so the
  // form doesn't jump from friendly tiles to a dark Material dropdown.
  if (unitMenuOpen) {
    ModalBottomSheet(
      onDismissRequest = { unitMenuOpen = false },
      containerColor = MaterialTheme.colorScheme.surface,
      dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline) }
    ) {
      Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
        Text(
          text = "Choose a unit",
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold,
          color = BharatTextPrimary,
          modifier = Modifier.padding(bottom = 14.dp)
        )
        val rows = unitOptions.chunked(3)
        rows.forEach { triple ->
          Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            triple.forEach { option ->
              val selected = option == weightUnit
              val tint = unitColorFor(option)
              OutlinedCard(
                onClick = { weightUnit = option; unitMenuOpen = false },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.outlinedCardColors(
                  containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                  width = if (selected) 2.dp else 1.dp,
                  color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier.weight(1f)
              ) {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                  horizontalAlignment = Alignment.CenterHorizontally
                ) {
                  Box(
                    modifier = Modifier
                      .size(36.dp)
                      .clip(CircleShape)
                      .background(tint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(
                      imageVector = unitIconFor(option),
                      contentDescription = null,
                      tint = tint,
                      modifier = Modifier.size(20.dp)
                    )
                  }
                  Spacer(modifier = Modifier.height(6.dp))
                  Text(
                    text = option,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) BharatPurplePrimary else BharatTextPrimary
                  )
                }
              }
            }
            repeat(3 - triple.size) { Spacer(modifier = Modifier.weight(1f)) }
          }
        }
      }
    }
  }

  // Duplicate product alert. Fired by the ViewModel from either the DB
  // rejection path (hard) or the app-side identity check (soft). Hard blocks
  // insertion entirely; soft offers a "Yes, Different Product" override.
  duplicateAlert?.let { alert ->
    val existing = alert.existing
    val isHard = alert.severity == DuplicateAlert.Severity.Hard
    AlertDialog(
      onDismissRequest = onDuplicateDismiss,
      containerColor = Color.White,
      shape = RoundedCornerShape(16.dp),
      title = {
        Text(
          text = if (isHard) "Already in Inventory" else "Possible duplicate",
          fontWeight = FontWeight.Bold,
          color = BharatTextPrimary
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = if (isHard)
              "\"${existing.name}\" is already listed in your inventory."
            else
              "This looks similar to a product you already have. Continue anyway if it's a different item.",
            fontSize = 13.sp,
            color = BharatTextSecondary
          )
          OutlinedCard(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              if (existing.imageUrl.isNotBlank()) {
                AsyncImage(
                  model = existing.imageUrl,
                  contentDescription = null,
                  modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                  contentScale = ContentScale.Crop
                )
              } else {
                Box(
                  modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(Icons.Default.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                }
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(existing.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BharatTextPrimary, maxLines = 1)
                Text(
                  text = "\u20B9${existing.currentPrice} \u2022 ${existing.stockStatus}",
                  fontSize = 12.sp,
                  color = BharatTextSecondary
                )
              }
            }
          }
        }
      },
      confirmButton = {
        Row {
          TextButton(onClick = { onDuplicateUpdateStock(existing) }) {
            Text("Update Stock", color = BharatPurplePrimary, fontWeight = FontWeight.Bold)
          }
          Spacer(modifier = Modifier.width(4.dp))
          TextButton(onClick = { onDuplicateViewProduct(existing) }) {
            Text("View Product", color = BharatPurplePrimary, fontWeight = FontWeight.SemiBold)
          }
        }
      },
      dismissButton = {
        if (isHard) {
          TextButton(onClick = onDuplicateDismiss) {
            Text("Cancel", color = BharatTextSecondary)
          }
        } else {
          TextButton(onClick = onDuplicateForceInsert) {
            Text("Yes, Different", color = BharatTextSecondary, fontWeight = FontWeight.SemiBold)
          }
        }
      }
    )
  }

  // Community catalog search dialog. Query hits Supabase; user taps a result
  // to autofill the form (reuses the same scannedProductTemplate slot).
  if (showSearchDialog) {
    AlertDialog(
      onDismissRequest = { showSearchDialog = false },
      containerColor = Color.White,
      shape = RoundedCornerShape(16.dp),
      title = {
        Text("Search Catalog", fontWeight = FontWeight.Bold, color = BharatTextPrimary)
      },
      text = {
        Column {
          Text(
            "Find products other vendors already added. Tap a result to prefill the form.",
            fontSize = 12.sp,
            color = BharatTextSecondary,
            modifier = Modifier.padding(bottom = 10.dp)
          )
          OutlinedTextField(
            value = searchQuery,
            onValueChange = {
              searchQuery = it
              onSearchCatalog(it)
            },
            placeholder = { Text("e.g. Aashirvaad, Maggi, Amul", color = BharatTextMuted) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = BharatTextSecondary) },
            trailingIcon = {
              if (catalogSearchLoading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = BharatPurplePrimary, strokeWidth = 2.dp)
              }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
              focusedTextColor = BharatTextPrimary,
              unfocusedTextColor = BharatTextPrimary,
              focusedBorderColor = BharatPurplePrimary
            )
          )
          Spacer(modifier = Modifier.height(12.dp))
          when {
            searchQuery.length < 2 -> {
              Text("Type at least 2 characters to search…", fontSize = 12.sp, color = BharatTextMuted)
            }
            catalogSearchResults.isEmpty() && !catalogSearchLoading -> {
              Text("No matches yet. Try a shorter name or add manually.", fontSize = 12.sp, color = BharatTextMuted)
            }
            else -> {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .heightIn(max = 320.dp)
                  .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                catalogSearchResults.forEach { p ->
                  OutlinedCard(
                    onClick = {
                      onSelectCatalogProduct(p)
                      showSearchDialog = false
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                  ) {
                    Row(
                      modifier = Modifier.padding(10.dp),
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      if (p.imageUrl.isNotBlank()) {
                        AsyncImage(
                          model = p.imageUrl,
                          contentDescription = null,
                          modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp)),
                          contentScale = ContentScale.Crop
                        )
                      } else {
                        Box(
                          modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF3F4F6)),
                          contentAlignment = Alignment.Center
                        ) {
                          Icon(Icons.Default.Inventory2, contentDescription = null, tint = BharatTextMuted, modifier = Modifier.size(20.dp))
                        }
                      }
                      Spacer(modifier = Modifier.width(10.dp))
                      Column(modifier = Modifier.weight(1f)) {
                        Text(
                          text = p.name,
                          fontSize = 13.sp,
                          fontWeight = FontWeight.Bold,
                          color = BharatTextPrimary,
                          maxLines = 1
                        )
                        val meta = listOfNotNull(
                          p.brand.takeIf { it.isNotBlank() },
                          p.unit.takeIf { it.isNotBlank() }
                        ).joinToString(" · ")
                        if (meta.isNotBlank()) {
                          Text(meta, fontSize = 11.sp, color = BharatTextSecondary, maxLines = 1)
                        }
                      }
                      Icon(Icons.Default.ChevronRight, contentDescription = null, tint = BharatTextMuted)
                    }
                  }
                }
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showSearchDialog = false }) {
          Text("Close", color = BharatPurplePrimary, fontWeight = FontWeight.Bold)
        }
      }
    )
  }
}

// Big top-of-screen mode cards. Kept as a private helper so the Row above
// stays a one-line-per-option summary.
@Composable
private fun AddModeOption(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  subtitle: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  OutlinedCard(
    onClick = onClick,
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
    modifier = modifier
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 14.dp, horizontal = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = BharatPurplePrimary,
        modifier = Modifier.size(28.dp)
      )
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = BharatTextPrimary,
        textAlign = TextAlign.Center,
        lineHeight = 14.sp
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = subtitle,
        fontSize = 10.sp,
        color = BharatTextSecondary,
        textAlign = TextAlign.Center
      )
    }
  }
}

// Icon per category - keeps the picker readable at a glance. Using generic
// Material icons instead of pixel-perfect art so we don't need to ship assets.
private fun categoryIconFor(name: String): androidx.compose.ui.graphics.vector.ImageVector {
  val n = name.lowercase()
  return when {
    "dairy" in n || "bread" in n || "egg" in n -> Icons.Default.BreakfastDining
    "cold" in n || "juice" in n || "tea" in n || "coffee" in n || "beverage" in n -> Icons.Default.EmojiFoodBeverage
    "snack" in n || "biscuit" in n || "munch" in n -> Icons.Default.Cookie
    "atta" in n || "rice" in n || "dal" in n || "masala" in n -> Icons.Default.Grain
    "spice" in n -> Icons.Default.Grass
    "staple" in n -> Icons.Default.Inventory2
    "oil" in n || "ghee" in n -> Icons.Default.LocalDrink
    "personal" in n || "hygiene" in n || "baby" in n || "health" in n -> Icons.Default.HealthAndSafety
    "clean" in n || "household" in n -> Icons.Default.CleaningServices
    "sweet" in n -> Icons.Default.Cake
    "meat" in n || "fish" in n -> Icons.Default.SetMeal
    "packaged" in n -> Icons.Default.Inventory2
    "breakfast" in n || "sauce" in n -> Icons.Default.RestaurantMenu
    "paan" in n -> Icons.Default.LocalFlorist
    else -> Icons.Default.Category
  }
}

// Distinct accent color per category so the picker doesn't read as a wall of
// black-and-white icons. Colors are muted enough not to fight the purple brand.
private fun categoryColorFor(name: String): Color {
  val n = name.lowercase()
  return when {
    "dairy" in n || "bread" in n || "egg" in n -> Color(0xFFF59E0B) // amber
    "cold" in n || "juice" in n || "tea" in n || "coffee" in n || "beverage" in n -> Color(0xFF3B82F6) // blue
    "snack" in n || "biscuit" in n || "munch" in n -> Color(0xFFB45309) // tan
    "atta" in n || "rice" in n || "dal" in n || "masala" in n -> Color(0xFFEA580C) // burnt orange
    "spice" in n -> Color(0xFFDC2626) // red
    "staple" in n -> Color(0xFF16A34A) // green
    "oil" in n || "ghee" in n -> Color(0xFFD97706) // dark amber
    "personal" in n || "hygiene" in n || "baby" in n || "health" in n -> Color(0xFF0891B2) // cyan
    "clean" in n || "household" in n -> Color(0xFF0D9488) // teal
    "sweet" in n -> Color(0xFFEC4899) // pink
    "meat" in n || "fish" in n -> Color(0xFFBE123C) // crimson
    "packaged" in n -> Color(0xFF0D9488) // teal
    "breakfast" in n || "sauce" in n -> Color(0xFF16A34A) // green
    "paan" in n -> Color(0xFF9333EA) // magenta-purple
    else -> BharatPurplePrimary
  }
}

// Icon per unit for the unit picker sheet - mirrors the visual treatment of
// categories so the whole form feels consistent.
private fun unitIconFor(unit: String): androidx.compose.ui.graphics.vector.ImageVector {
  return when (unit.lowercase()) {
    "kg" -> Icons.Default.Scale
    "g" -> Icons.Default.Balance
    "l" -> Icons.Default.WaterDrop
    "ml" -> Icons.Default.Opacity
    "pcs" -> Icons.Default.Numbers
    "dozen" -> Icons.Default.Egg
    "pack" -> Icons.Default.Inventory2
    else -> Icons.Default.Straighten
  }
}

private fun unitColorFor(unit: String): Color {
  return when (unit.lowercase()) {
    "kg" -> Color(0xFFEA580C)
    "g" -> Color(0xFFF59E0B)
    "l" -> Color(0xFF3B82F6)
    "ml" -> Color(0xFF0891B2)
    "pcs" -> Color(0xFF9333EA)
    "dozen" -> Color(0xFFDC2626)
    "pack" -> Color(0xFF0D9488)
    else -> BharatPurplePrimary
  }
}

// Common product keywords mapped to a canonical Supabase category id. Used to
// coerce OpenFoodFacts tags ("en:biscuits") and generic strings ("Milk") into
// a category that actually exists in the picker.
private val categorySynonyms = mapOf(
  "milk" to "dairy", "butter" to "dairy", "cheese" to "dairy", "bread" to "dairy",
  "egg" to "dairy", "curd" to "dairy", "yogurt" to "dairy", "yoghurt" to "dairy",
  "paneer" to "dairy", "cream" to "dairy",
  "juice" to "beverages", "drink" to "beverages", "cola" to "beverages",
  "tea" to "beverages", "coffee" to "beverages", "water" to "beverages",
  "soda" to "beverages", "beverage" to "beverages",
  "biscuit" to "snacks", "cookie" to "snacks", "chocolate" to "snacks",
  "candy" to "snacks", "wafer" to "snacks", "chip" to "snacks",
  "snack" to "snacks", "namkeen" to "snacks", "chip" to "snacks",
  "oil" to "oils", "ghee" to "oils",
  "rice" to "staples", "atta" to "staples", "flour" to "staples",
  "dal" to "staples", "lentil" to "staples", "pulse" to "staples",
  "sugar" to "staples", "noodle" to "staples",
  "salt" to "spices", "spice" to "spices", "masala" to "masala",
  "detergent" to "cleaning", "cleaner" to "cleaning", "phenyl" to "cleaning",
  "soap" to "personal_care", "shampoo" to "personal_care",
  "toothpaste" to "personal_care", "brush" to "personal_care"
)

// Fuzzy-map any raw category string to an actual Category the vendor can pick.
// Falls back to null so the caller can leave the picker on "Select Category".
private fun matchCategoryFromRaw(raw: String, categories: List<com.kks.bharatkirana.data.model.Category>): com.kks.bharatkirana.data.model.Category? {
  val r = raw.lowercase().trim()
  if (r.isBlank() || categories.isEmpty()) return null

  // 1. exact id match (raw already IS a canonical id, e.g. from our own DB rows)
  categories.firstOrNull { it.id.equals(r, ignoreCase = true) }?.let { return it }

  // 2. word-by-word synonym match; handles plurals via substring compare both ways.
  val rawWords = r.split(Regex("[^a-z0-9]+")).filter { it.length >= 3 }
  for (word in rawWords) {
    for ((syn, catId) in categorySynonyms) {
      if (word.contains(syn) || syn.contains(word)) {
        categories.firstOrNull { it.id == catId }?.let { return it }
      }
    }
    // 3. any category id or name-word overlaps this word
    categories.firstOrNull { cat ->
      cat.id.contains(word) ||
        cat.name.lowercase().split(Regex("[^a-z]+"))
          .any { p -> p.length >= 3 && (p.contains(word) || word.contains(p)) }
    }?.let { return it }
  }

  return null
}

@Composable
fun AuthTextFieldSimple(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
  placeholder: String,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default
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
      keyboardOptions = keyboardOptions,
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(12.dp),
      colors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = BharatTextPrimary,
        unfocusedTextColor = BharatTextPrimary
      )
    )
  }
}

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
import com.kks.bharatkirana.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProductScreen(
  onBackClick: () -> Unit,
  onListProduct: (String, String, String, Int, Int, String, Boolean, List<Uri>) -> Unit,
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
  var selectedImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

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

      Spacer(modifier = Modifier.height(24.dp))

      Button(
        onClick = { 
          onListProduct(productName, category, weightValue + weightUnit, sellingPrice.toIntOrNull() ?: 0, mrp.toIntOrNull() ?: 0, description, inStock, selectedImageUris)
        },
        enabled = productName.isNotBlank() && sellingPrice.isNotBlank(),
        modifier = Modifier
          .fillMaxWidth()
          .height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = BharatPurplePrimary),
        shape = RoundedCornerShape(12.dp)
      ) {
        Icon(Icons.Default.CheckCircle, contentDescription = null)
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = "List Product", fontWeight = FontWeight.Bold, fontSize = 16.sp)
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

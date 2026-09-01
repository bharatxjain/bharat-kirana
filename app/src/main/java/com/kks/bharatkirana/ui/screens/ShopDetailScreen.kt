package com.kks.bharatkirana.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kks.bharatkirana.data.model.Category
import com.kks.bharatkirana.data.model.Product
import com.kks.bharatkirana.data.model.Shop
import com.kks.bharatkirana.ui.components.ProductGridCard
import com.kks.bharatkirana.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopDetailScreen(
  shop: Shop,
  products: List<Product>,
  categories: List<Category>,
  cartQuantityFor: (Product) -> Int,
  onBackClick: () -> Unit,
  onProductClick: (Product) -> Unit,
  onAddToCart: (Product) -> Unit,
  onIncreaseQty: (Product) -> Unit,
  onDecreaseQty: (Product) -> Unit,
  modifier: Modifier = Modifier
) {
  var query by remember { mutableStateOf("") }
  var selectedCategoryId by remember { mutableStateOf<String?>(null) }

  // Only category tiles that this shop actually stocks — no dead chips.
  val shopCategoryIds = remember(products) { products.map { it.categoryId }.toSet() }
  val availableCategories = remember(categories, shopCategoryIds) {
    categories.filter { it.id in shopCategoryIds }
  }

  val filtered = products.filter { p ->
    (selectedCategoryId == null || p.categoryId == selectedCategoryId) &&
      (query.isBlank() || p.name.contains(query, ignoreCase = true) || p.brand.contains(query, ignoreCase = true))
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(shop.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = BharatTextPrimary, maxLines = 1)
            Text(
              text = if (shop.isOpen) "\u2022 Open now" else "\u2022 Closed",
              fontSize = 12.sp,
              color = if (shop.isOpen) BharatGreen else Color(0xFFDC2626),
              fontWeight = FontWeight.SemiBold
            )
          }
        },
        navigationIcon = {
          IconButton(onClick = onBackClick) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = BharatPurplePrimary)
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
      )
    },
    modifier = modifier.fillMaxSize()
  ) { padding ->
    LazyColumn(
      state = rememberLazyListState(),
      modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .padding(padding),
      contentPadding = PaddingValues(bottom = 16.dp)
    ) {

      // Shop info hero card
      item {
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(52.dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
              ) {
                Icon(Icons.Default.ShoppingBag, contentDescription = null, tint = BharatPurplePrimary, modifier = Modifier.size(26.dp))
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(shop.name, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = BharatTextPrimary, maxLines = 1)
                Text(shop.address, fontSize = 12.sp, color = BharatTextSecondary, maxLines = 2)
              }
              Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(14.dp))
                  Spacer(modifier = Modifier.width(2.dp))
                  Text(String.format("%.1f", shop.rating), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BharatTextPrimary)
                }
                Text("${products.size} items", fontSize = 11.sp, color = BharatTextSecondary)
              }
            }
            if (shop.phone.isNotBlank()) {
              Spacer(modifier = Modifier.height(10.dp))
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Phone, contentDescription = null, tint = BharatTextSecondary, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(shop.phone, fontSize = 12.sp, color = BharatTextSecondary)
              }
            }
          }
        }
      }

      // Search within this shop
      item {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.surface,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = BharatTextSecondary)
            Spacer(modifier = Modifier.width(10.dp))
            BasicSearchField(value = query, onValueChange = { query = it }, placeholder = "Search in ${shop.name}")
          }
        }
      }

      // Category chip row for products in this shop
      if (availableCategories.isNotEmpty()) {
        item {
          Text(
            text = "BROWSE BY CATEGORY",
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = BharatTextSecondary,
            modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp)
          )
        }
        item {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 12.dp)
              .horizontalScroll(rememberScrollStateForChips()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            CategoryChip(
              label = "All",
              selected = selectedCategoryId == null,
              onClick = { selectedCategoryId = null }
            )
            availableCategories.forEach { cat ->
              CategoryChip(
                label = cat.name,
                selected = selectedCategoryId == cat.id,
                onClick = { selectedCategoryId = cat.id }
              )
            }
          }
        }
      }

      // Product grid
      item {
        Text(
          text = if (selectedCategoryId == null) "ALL PRODUCTS" else "PRODUCTS",
          fontSize = 11.sp,
          fontWeight = FontWeight.ExtraBold,
          color = BharatTextSecondary,
          modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp)
        )
      }

      if (filtered.isEmpty()) {
        item {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Box(
              modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
              contentAlignment = Alignment.Center
            ) {
              Icon(Icons.Default.ShoppingBag, contentDescription = null, tint = BharatPurplePrimary, modifier = Modifier.size(32.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
              text = if (query.isNotBlank()) "No matches for \"$query\"" else "No products in this category yet",
              fontWeight = FontWeight.Bold,
              color = BharatTextPrimary,
              fontSize = 14.sp,
              textAlign = TextAlign.Center,
              modifier = Modifier.padding(horizontal = 32.dp)
            )
          }
        }
      } else {
        item {
          LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 200.dp, max = 2000.dp)
          ) {
            items(filtered, key = { it.id }) { product ->
              ProductGridCard(
                product = product,
                quantityInCart = cartQuantityFor(product),
                onProductClick = { onProductClick(product) },
                onAddToCart = { onAddToCart(product) },
                onIncrease = { onIncreaseQty(product) },
                onDecrease = { onDecreaseQty(product) }
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun rememberScrollStateForChips() = androidx.compose.foundation.rememberScrollState()

@Composable
private fun BasicSearchField(
  value: String,
  onValueChange: (String) -> Unit,
  placeholder: String
) {
  androidx.compose.foundation.text.BasicTextField(
    value = value,
    onValueChange = onValueChange,
    singleLine = true,
    modifier = Modifier.fillMaxWidth(),
    textStyle = androidx.compose.ui.text.TextStyle(color = BharatTextPrimary, fontSize = 14.sp),
    cursorBrush = androidx.compose.ui.graphics.SolidColor(BharatPurplePrimary),
    decorationBox = { inner ->
      Box {
        if (value.isEmpty()) {
          Text(placeholder, color = BharatTextMuted, fontSize = 14.sp)
        }
        inner()
      }
    }
  )
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
  Surface(
    shape = RoundedCornerShape(999.dp),
    color = if (selected) BharatPurplePrimary else MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, if (selected) BharatPurplePrimary else MaterialTheme.colorScheme.outline),
    modifier = Modifier.clickable(onClick = onClick)
  ) {
    Text(
      text = label,
      color = if (selected) Color.White else BharatTextPrimary,
      fontWeight = FontWeight.SemiBold,
      fontSize = 13.sp,
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
    )
  }
}

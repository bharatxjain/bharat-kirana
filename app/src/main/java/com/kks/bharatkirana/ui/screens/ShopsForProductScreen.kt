package com.kks.bharatkirana.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kks.bharatkirana.data.model.Product
import com.kks.bharatkirana.data.model.Shop
import com.kks.bharatkirana.data.model.isCurrentlyOpen
import com.kks.bharatkirana.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopsForProductScreen(
  productName: String,
  products: List<Product>,
  shops: List<Shop>,
  onBackClick: () -> Unit,
  onPickShop: (shopId: String, productName: String) -> Unit,
  modifier: Modifier = Modifier
) {
  // For each shop, find its first matching product row (case-insensitive name match).
  // We only surface shops that actually stock the item and have it in-stock.
  val matches: List<Pair<Shop, Product>> = shops.mapNotNull { shop ->
    val prod = products.firstOrNull {
      it.shopId == shop.id &&
        it.inStock &&
        it.name.equals(productName, ignoreCase = true)
    }
    if (prod != null) shop to prod else null
  }.sortedBy { it.first.distance.substringBefore(" ").toDoubleOrNull() ?: 99.0 }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              text = "Shops with",
              fontSize = 11.sp,
              color = BharatTextSecondary
            )
            Text(
              text = "\"$productName\"",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = BharatTextPrimary
            )
          }
        },
        navigationIcon = {
          IconButton(onClick = onBackClick) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = BharatPurplePrimary)
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
      )
    },
    modifier = modifier.fillMaxSize()
  ) { paddingValues ->
    if (matches.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(BharatBackground)
          .padding(paddingValues),
        contentAlignment = Alignment.Center
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.padding(24.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Storefront,
            contentDescription = null,
            tint = Color(0xFFCBD5E1),
            modifier = Modifier.size(60.dp)
          )
          Spacer(modifier = Modifier.height(12.dp))
          Text(
            text = "No shops carry \"$productName\" right now",
            fontWeight = FontWeight.Bold,
            color = BharatTextPrimary,
            textAlign = TextAlign.Center
          )
          Text(
            text = "Try a different item or come back later.",
            fontSize = 12.sp,
            color = BharatTextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
          )
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .background(BharatBackground)
          .padding(paddingValues),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        item {
          Text(
            text = "${matches.size} shop${if (matches.size == 1) "" else "s"} nearby",
            fontSize = 12.sp,
            color = BharatTextSecondary
          )
        }
        items(matches, key = { it.first.id }) { (shop, product) ->
          ShopForProductCard(
            shop = shop,
            product = product,
            onClick = { onPickShop(shop.id, product.name) }
          )
        }
      }
    }
  }
}

@Composable
private fun ShopForProductCard(
  shop: Shop,
  product: Product,
  onClick: () -> Unit
) {
  val open = shop.isCurrentlyOpen()
  Card(
    onClick = onClick,
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(48.dp)
          .clip(CircleShape)
          .background(BharatPurpleContainer),
        contentAlignment = Alignment.Center
      ) {
        Icon(Icons.Default.Storefront, contentDescription = null, tint = BharatPurplePrimary, modifier = Modifier.size(24.dp))
      }
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = shop.name,
            fontWeight = FontWeight.Bold,
            color = BharatTextPrimary,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f, fill = false)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (open) BharatGreen else Color(0xFFDC2626)
          ) {
            Text(
              text = if (open) "Open" else "Closed",
              color = Color.White,
              fontSize = 9.sp,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
          }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
          Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFB800), modifier = Modifier.size(12.dp))
          Spacer(modifier = Modifier.width(2.dp))
          Text("${shop.rating}", fontSize = 11.sp, color = BharatTextSecondary)
          Spacer(modifier = Modifier.width(8.dp))
          Icon(Icons.Default.LocationOn, contentDescription = null, tint = BharatTextSecondary, modifier = Modifier.size(12.dp))
          Text(shop.distance, fontSize = 11.sp, color = BharatTextSecondary)
        }
      }
      Spacer(modifier = Modifier.width(10.dp))
      Column(horizontalAlignment = Alignment.End) {
        Text(
          text = "\u20b9${product.currentPrice}",
          fontWeight = FontWeight.ExtraBold,
          color = BharatTextPrimary,
          fontSize = 16.sp
        )
        Text(product.unit, fontSize = 10.sp, color = BharatTextMuted)
      }
    }
  }
}

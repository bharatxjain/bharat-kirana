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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.ShoppingCart
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
import coil.compose.AsyncImage
import com.kks.bharatkirana.data.model.Product
import com.kks.bharatkirana.ui.theme.BharatBackground
import com.kks.bharatkirana.ui.theme.BharatGreen
import com.kks.bharatkirana.ui.theme.BharatPurpleContainer
import com.kks.bharatkirana.ui.theme.BharatPurplePrimary
import com.kks.bharatkirana.ui.theme.BharatTextMuted
import com.kks.bharatkirana.ui.theme.BharatTextPrimary
import com.kks.bharatkirana.ui.theme.BharatTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistScreen(
  products: List<Product>,
  onBackClick: () -> Unit,
  onProductClick: (Product) -> Unit,
  onAddToCart: (Product) -> Unit,
  onRemove: (String) -> Unit,
  onExploreClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text("Your Wishlist", fontWeight = FontWeight.Bold, color = BharatTextPrimary, fontSize = 18.sp)
            if (products.isNotEmpty()) {
              Text("${products.size} saved items", fontSize = 12.sp, color = BharatTextSecondary)
            }
          }
        },
        navigationIcon = {
          IconButton(onClick = onBackClick) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = BharatTextPrimary)
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
      )
    },
    modifier = modifier.fillMaxSize()
  ) { paddingValues ->
    if (products.isEmpty()) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .background(BharatBackground)
          .padding(paddingValues)
          .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        Box(
          modifier = Modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(BharatPurpleContainer),
          contentAlignment = Alignment.Center
        ) {
          Icon(Icons.Default.FavoriteBorder, null, tint = BharatPurplePrimary, modifier = Modifier.size(44.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("Your wishlist is empty", fontWeight = FontWeight.Bold, color = BharatTextPrimary, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = "Tap the heart on any product to save it here for later.",
          fontSize = 13.sp,
          color = BharatTextSecondary,
          textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
          onClick = onExploreClick,
          colors = ButtonDefaults.buttonColors(containerColor = BharatPurplePrimary),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.height(46.dp)
        ) {
          Text("Explore Products", fontWeight = FontWeight.Bold, color = Color.White)
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .background(BharatBackground)
          .padding(paddingValues),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        items(products, key = { it.id }) { product ->
          WishlistRow(
            product = product,
            onClick = { onProductClick(product) },
            onAddToCart = { onAddToCart(product) },
            onRemove = { onRemove(product.id) }
          )
        }
        item { Spacer(modifier = Modifier.height(16.dp)) }
      }
    }
  }
}

@Composable
private fun WishlistRow(
  product: Product,
  onClick: () -> Unit,
  onAddToCart: () -> Unit,
  onRemove: () -> Unit
) {
  val previewUrl = product.imageUrls.firstOrNull { it.isNotBlank() }
    ?: product.imageUrl.takeIf { it.isNotBlank() }
  Card(
    onClick = onClick,
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
  ) {
    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
      Box(
        modifier = Modifier
          .size(64.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(Color(0xFFF1F5F9)),
        contentAlignment = Alignment.Center
      ) {
        if (previewUrl != null) {
          AsyncImage(
            model = previewUrl,
            contentDescription = product.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
          )
        } else {
          Icon(Icons.Default.ShoppingCart, null, tint = BharatPurplePrimary, modifier = Modifier.size(28.dp))
        }
      }
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(product.name, fontWeight = FontWeight.Bold, color = BharatTextPrimary, maxLines = 1)
        if (product.brand.isNotBlank()) {
          Text(product.brand, fontSize = 11.sp, color = BharatTextMuted, maxLines = 1)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("₹${product.currentPrice}", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = BharatTextPrimary)
          if (product.originalPrice > product.currentPrice) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "₹${product.originalPrice}",
              fontSize = 11.sp,
              color = BharatTextMuted,
              textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
            )
          }
          if (product.inStock) {
            Spacer(modifier = Modifier.width(8.dp))
            Text("In stock", fontSize = 10.sp, color = BharatGreen, fontWeight = FontWeight.SemiBold)
          }
        }
      }
      Column(horizontalAlignment = Alignment.End) {
        IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
          Icon(Icons.Default.Favorite, contentDescription = "Remove", tint = Color(0xFFDC2626), modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedButton(
          onClick = onAddToCart,
          shape = RoundedCornerShape(8.dp),
          border = BorderStroke(1.dp, BharatPurplePrimary),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        ) {
          Text("Add", color = BharatPurplePrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}

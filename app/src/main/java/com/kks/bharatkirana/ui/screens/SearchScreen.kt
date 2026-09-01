package com.kks.bharatkirana.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kks.bharatkirana.data.model.CartItem
import com.kks.bharatkirana.data.model.Product
import com.kks.bharatkirana.data.model.SearchSuggestion
import com.kks.bharatkirana.data.model.Shop
import com.kks.bharatkirana.data.model.isCurrentlyOpen
import com.kks.bharatkirana.ui.components.CartFloatingBanner
import com.kks.bharatkirana.ui.components.GrocerySearchBar
import com.kks.bharatkirana.ui.components.ProductGridCard
import com.kks.bharatkirana.ui.theme.BharatBackground
import com.kks.bharatkirana.ui.theme.BharatGreen
import com.kks.bharatkirana.ui.theme.BharatPurpleContainer
import com.kks.bharatkirana.ui.theme.BharatPurplePrimary
import com.kks.bharatkirana.ui.theme.BharatTextMuted
import com.kks.bharatkirana.ui.theme.BharatTextPrimary
import com.kks.bharatkirana.ui.theme.BharatTextSecondary

@Composable
fun SearchScreen(
  searchQuery: String,
  products: List<Product>,
  cartItems: List<CartItem>,
  onSearchQueryChange: (String) -> Unit,
  onProductClick: (Product) -> Unit,
  onAddToCart: (Product) -> Unit,
  onUpdateCartQty: (String, String, Int) -> Unit,
  onViewCartClick: () -> Unit,
  shops: List<Shop> = emptyList(),
  onShopClick: (Shop) -> Unit = {},
  suggestions: List<SearchSuggestion> = emptyList(),
  onSuggestionClick: (SearchSuggestion) -> Unit = {},
  modifier: Modifier = Modifier
) {
  val popularKeywords = listOf("Atta", "Basmati Rice", "Amul Milk", "Sunflower Oil", "Spinach", "Bread", "Tata Salt")

  // Round 6.1: last 3 committed searches, persisted in SharedPreferences so they
  // survive app kill. New entries push older ones out, most recent first.
  val context = LocalContext.current
  val prefs = remember { context.getSharedPreferences("bharat_kirana_prefs", android.content.Context.MODE_PRIVATE) }
  var recentSearches by remember {
    mutableStateOf(
      prefs.getString("recent_searches", "")
        ?.split("|")
        ?.filter { it.isNotBlank() }
        ?.take(3)
        ?: emptyList()
    )
  }
  // Debounced write: once the user stops typing for 1 s and the query is meaningful,
  // promote it to the top of the recent-searches list and persist.
  LaunchedEffect(searchQuery) {
    val q = searchQuery.trim()
    if (q.length < 2) return@LaunchedEffect
    kotlinx.coroutines.delay(1000)
    if (q != searchQuery.trim()) return@LaunchedEffect
    val updated = (listOf(q) + recentSearches.filter { !it.equals(q, ignoreCase = true) }).take(3)
    if (updated != recentSearches) {
      recentSearches = updated
      prefs.edit().putString("recent_searches", updated.joinToString("|")).apply()
    }
  }

  val q = searchQuery.trim()
  val shopIdToName = remember(shops) { shops.associate { it.id to it.name } }

  val filteredShops = if (q.isEmpty()) emptyList()
  else shops.filter {
    it.name.contains(q, ignoreCase = true) ||
      it.address.contains(q, ignoreCase = true) ||
      it.primaryCategory.contains(q, ignoreCase = true)
  }

  val filteredProducts = if (q.isEmpty()) {
    products
  } else {
    products.filter { p ->
      val shopName = shopIdToName[p.shopId].orEmpty()
      p.name.contains(q, ignoreCase = true) ||
        p.brand.contains(q, ignoreCase = true) ||
        p.description.contains(q, ignoreCase = true) ||
        shopName.contains(q, ignoreCase = true)
    }
  }
  // Dedupe by name+brand: tapping a card opens a "shops carrying this" list.
  val displayProducts = filteredProducts.distinctBy {
    "${it.name.trim().lowercase()}|${it.brand.trim().lowercase()}"
  }

  val cartItemCount = cartItems.sumOf { it.quantity }
  val cartTotal = cartItems.sumOf { it.totalPrice }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(BharatBackground)
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 16.dp)
    ) {
      Spacer(modifier = Modifier.height(12.dp))

      GrocerySearchBar(
        query = searchQuery,
        onQueryChange = onSearchQueryChange,
        placeholder = "Search across 1000+ items...",
        autoFocus = true
      )

      // Autosuggestions dropdown: mixes matching products and shops.
      // Appears only while the user is actively typing.
      if (searchQuery.trim().isNotEmpty() && suggestions.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        SearchSuggestionsCard(
          suggestions = suggestions,
          onSuggestionClick = onSuggestionClick
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      if (recentSearches.isNotEmpty() && searchQuery.trim().isEmpty()) {
        Text(
          text = "Recent Searches",
          style = MaterialTheme.typography.labelMedium,
          color = BharatTextSecondary
        )
        Spacer(modifier = Modifier.height(6.dp))
        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          items(recentSearches) { keyword ->
            Surface(
              onClick = { onSearchQueryChange(keyword) },
              shape = RoundedCornerShape(20.dp),
              color = BharatPurpleContainer,
              border = androidx.compose.foundation.BorderStroke(1.dp, BharatPurplePrimary.copy(alpha = 0.3f))
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
              ) {
                Icon(
                  Icons.Default.Storefront,
                  contentDescription = null,
                  tint = BharatPurplePrimary,
                  modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = keyword,
                  color = BharatPurplePrimary,
                  fontWeight = FontWeight.Medium,
                  fontSize = 12.sp
                )
              }
            }
          }
        }
        Spacer(modifier = Modifier.height(16.dp))
      }

      // Trending Search chips
      Text(
        text = "Trending Searches",
        style = MaterialTheme.typography.labelMedium,
        color = BharatTextSecondary
      )
      Spacer(modifier = Modifier.height(6.dp))

      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        items(popularKeywords) { keyword ->
          val isSelected = searchQuery.equals(keyword, ignoreCase = true)
          Surface(
            onClick = { onSearchQueryChange(keyword) },
            shape = RoundedCornerShape(20.dp),
            color = if (isSelected) BharatPurplePrimary else Color.White,
            border = androidx.compose.foundation.BorderStroke(
              1.dp,
              if (isSelected) BharatPurplePrimary else Color(0xFFE2E8F0)
            )
          ) {
            Text(
              text = keyword,
              color = if (isSelected) Color.White else BharatTextPrimary,
              fontWeight = FontWeight.Medium,
              fontSize = 12.sp,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Matching shops appear above products so the customer can jump straight
      // to a shop when they searched by shop name.
      if (q.isNotEmpty() && filteredShops.isNotEmpty()) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Shops",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = BharatTextPrimary
          )
          Text(
            text = "${filteredShops.size} results",
            style = MaterialTheme.typography.bodySmall,
            color = BharatTextMuted
          )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          filteredShops.take(5).forEach { shop ->
            SearchShopRow(shop = shop, onClick = { onShopClick(shop) })
          }
        }
        Spacer(modifier = Modifier.height(16.dp))
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = if (q.isEmpty()) "All Products" else "Products",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = BharatTextPrimary
        )
        Text(
          text = "${displayProducts.size} results",
          style = MaterialTheme.typography.bodySmall,
          color = BharatTextMuted
        )
      }

      Spacer(modifier = Modifier.height(8.dp))

      if (displayProducts.isEmpty()) {
        if (filteredShops.isEmpty()) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "No shops or products match \"$searchQuery\"",
              style = MaterialTheme.typography.bodyMedium,
              color = BharatTextSecondary
            )
          }
        } else {
          Text(
            text = "No products match \"$searchQuery\"",
            style = MaterialTheme.typography.bodyMedium,
            color = BharatTextSecondary,
            modifier = Modifier.padding(vertical = 12.dp)
          )
        }
      } else {
        LazyVerticalGrid(
          columns = GridCells.Fixed(2),
          contentPadding = PaddingValues(bottom = if (cartItemCount > 0) 90.dp else 24.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier.fillMaxSize()
        ) {
          items(displayProducts) { product ->
            val qtyInCart = cartItems
              .filter { it.product.id == product.id }
              .sumOf { it.quantity }

            ProductGridCard(
              product = product,
              quantityInCart = qtyInCart,
              onProductClick = { onProductClick(product) },
              onAddToCart = { onAddToCart(product) },
              onIncrease = {
                val weight = product.weightOptions.firstOrNull()?.label ?: product.unit
                onUpdateCartQty(product.id, weight, 1)
              },
              onDecrease = {
                val weight = product.weightOptions.firstOrNull()?.label ?: product.unit
                onUpdateCartQty(product.id, weight, -1)
              }
            )
          }
        }
      }
    }

    // Floating Cart Banner
    CartFloatingBanner(
      itemCount = cartItemCount,
      totalAmount = cartTotal,
      discountApplied = if (cartTotal > 200) 15 else 0,
      onViewCartClick = onViewCartClick,
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(bottom = 8.dp)
    )
  }
}

@Composable
private fun SearchSuggestionsCard(
  suggestions: List<SearchSuggestion>,
  onSuggestionClick: (SearchSuggestion) -> Unit
) {
  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
      suggestions.forEachIndexed { index, suggestion ->
        when (suggestion) {
          is SearchSuggestion.ProductSuggestion -> SuggestionRow(
            icon = Icons.Default.ShoppingBag,
            title = suggestion.name,
            subtitle = if (suggestion.brand.isNotBlank()) "${suggestion.brand} • Product" else "Product",
            iconTint = BharatPurplePrimary,
            iconBg = BharatPurpleContainer,
            onClick = { onSuggestionClick(suggestion) }
          )
          is SearchSuggestion.ShopSuggestion -> SuggestionRow(
            icon = Icons.Default.Storefront,
            title = suggestion.shop.name,
            subtitle = "${suggestion.shop.primaryCategory} • Shop • ${suggestion.shop.distance}",
            iconTint = Color(0xFF059669),
            iconBg = Color(0xFFDCFCE7),
            onClick = { onSuggestionClick(suggestion) }
          )
        }
        if (index < suggestions.size - 1) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(1.dp)
              .padding(horizontal = 16.dp)
              .background(Color(0xFFF1F5F9))
          )
        }
      }
    }
  }
}

@Composable
private fun SuggestionRow(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  subtitle: String,
  iconTint: Color,
  iconBg: Color,
  onClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = 14.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .size(34.dp)
        .clip(CircleShape)
        .background(iconBg),
      contentAlignment = Alignment.Center
    ) {
      Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
    }
    Spacer(modifier = Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(text = title, fontWeight = FontWeight.Bold, color = BharatTextPrimary, fontSize = 14.sp)
      Text(text = subtitle, fontSize = 11.sp, color = BharatTextMuted)
    }
  }
}

@Composable
private fun SearchShopRow(shop: Shop, onClick: () -> Unit) {
  val open = shop.isOpen
  Card(
    onClick = onClick,
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier.padding(10.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(44.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(BharatPurpleContainer),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.Storefront,
          contentDescription = null,
          tint = BharatPurplePrimary,
          modifier = Modifier.size(22.dp)
        )
      }
      Spacer(modifier = Modifier.width(10.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = shop.name,
          fontWeight = FontWeight.Bold,
          color = BharatTextPrimary,
          fontSize = 14.sp,
          maxLines = 1
        )
        Text(
          text = shop.address.ifBlank { "Nearby" },
          fontSize = 11.sp,
          color = BharatTextSecondary,
          maxLines = 1
        )
      }
      Surface(
        color = if (open) BharatGreen else Color(0xFFDC2626),
        shape = RoundedCornerShape(6.dp)
      ) {
        Text(
          text = if (open) "Open" else "Closed",
          color = Color.White,
          fontSize = 9.sp,
          fontWeight = FontWeight.ExtraBold,
          modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
      }
    }
  }
}

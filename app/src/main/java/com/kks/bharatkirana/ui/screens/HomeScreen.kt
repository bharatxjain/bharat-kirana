package com.kks.bharatkirana.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kks.bharatkirana.data.model.CartItem
import com.kks.bharatkirana.data.model.Category
import com.kks.bharatkirana.data.model.Product
import com.kks.bharatkirana.data.model.UserProfile
import com.kks.bharatkirana.ui.components.CartFloatingBanner
import com.kks.bharatkirana.ui.components.CategoryItemCard
import com.kks.bharatkirana.ui.components.DailyEssentialCard
import com.kks.bharatkirana.ui.components.GrocerySearchBar
import com.kks.bharatkirana.ui.components.ProductGridCard
import com.kks.bharatkirana.ui.components.ShimmerCategoriesGrid
import com.kks.bharatkirana.ui.components.ShimmerDailyEssentialCard
import com.kks.bharatkirana.ui.components.ShimmerProductRow
import com.kks.bharatkirana.ui.components.StoreLocationHeader
import com.kks.bharatkirana.ui.theme.BharatBackground
import com.kks.bharatkirana.ui.theme.BharatTextPrimary

@Composable
fun HomeScreen(
  userProfile: UserProfile,
  categories: List<Category>,
  products: List<Product>,
  cartItems: List<CartItem>,
  searchQuery: String,
  onSearchQueryChange: (String) -> Unit,
  onCategoryClick: (Category) -> Unit,
  onProductClick: (Product) -> Unit,
  onAddToCart: (Product) -> Unit,
  onUpdateCartQty: (String, String, Int) -> Unit,
  onProfileClick: () -> Unit,
  onStoreClick: () -> Unit,
  onChangeStoreClick: () -> Unit,
  onViewCartClick: () -> Unit,
  onAdminClick: () -> Unit = {},
  promoBanner: String? = null,
  isLoading: Boolean = false,
  activeShopId: String? = null,
  modifier: Modifier = Modifier
) {
  val cartItemCount = cartItems.sumOf { it.quantity }
  val cartTotal = cartItems.sumOf { it.totalPrice }

  val shopProducts = if (activeShopId != null) {
    products.filter { it.shopId == activeShopId }
  } else {
    products
  }

  val popularProducts = shopProducts.filter { it.isPopular }
  val dailyEssentials = shopProducts.filter { it.isDailyEssential }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(BharatBackground)
  ) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .testTag("home_screen_content"),
      contentPadding = PaddingValues(bottom = if (cartItemCount > 0) 90.dp else 24.dp)
    ) {
      // Store Location Header
      item {
        StoreLocationHeader(
          storeName = userProfile.activeStore,
          userInitial = userProfile.fullName.firstOrNull()?.toString() ?: "R",
          isAdmin = userProfile.isAdmin,
          onProfileClick = onProfileClick,
          onStoreClick = onStoreClick,
          onChangeStoreClick = onChangeStoreClick,
          onAdminClick = onAdminClick
        )
      }

      // Search Bar
      item {
        GrocerySearchBar(
          query = searchQuery,
          onQueryChange = onSearchQueryChange
        )
        Spacer(modifier = Modifier.height(10.dp))
      }

      // Promo banner from Firebase Remote Config (F.promo_banner_text/enabled).
      // Change text/toggle from the Firebase console — no app update needed.
      if (!promoBanner.isNullOrBlank()) {
        item {
          androidx.compose.material3.Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            color = Color(0xFFF3E8FF),
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 12.dp, vertical = 4.dp)
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              androidx.compose.material3.Icon(
                imageVector = androidx.compose.material.icons.Icons.Default.Campaign,
                contentDescription = null,
                tint = androidx.compose.ui.graphics.Color(0xFF7C3AED),
                modifier = Modifier.padding(end = 8.dp)
              )
              androidx.compose.material3.Text(
                text = promoBanner,
                color = androidx.compose.ui.graphics.Color(0xFF4C1D95),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
              )
            }
          }
          Spacer(modifier = Modifier.height(6.dp))
        }
      }

      // 4-Column Categories Grid (3 rows x 4 = 12 categories) or Shimmer
      item {
        if (isLoading && categories.isEmpty()) {
          ShimmerCategoriesGrid()
        } else {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 12.dp)
          ) {
            val chunked = categories.chunked(4)
            chunked.forEach { rowCategories ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
              ) {
                rowCategories.forEach { category ->
                  CategoryItemCard(
                    category = category,
                    onClick = { onCategoryClick(category) },
                    modifier = Modifier.weight(1f)
                  )
                }
              }
              Spacer(modifier = Modifier.height(6.dp))
            }
          }
        }
        Spacer(modifier = Modifier.height(16.dp))
      }

      // Section: Popular in Rice & Grains
      item {
        Column(modifier = Modifier.fillMaxWidth()) {
          Text(
            text = "Popular in Rice & Grains",
            style = MaterialTheme.typography.titleLarge.copy(
              fontWeight = FontWeight.Bold,
              fontSize = 18.sp
            ),
            color = BharatTextPrimary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
          )

          if (isLoading && products.isEmpty()) {
            ShimmerProductRow()
          } else {
            LazyRow(
              contentPadding = PaddingValues(horizontal = 16.dp),
              horizontalArrangement = Arrangement.spacedBy(14.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              items(popularProducts) { product ->
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
        Spacer(modifier = Modifier.height(24.dp))
      }

      // Section: Daily Essentials
      item {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
        ) {
          Text(
            text = "Daily Essentials",
            style = MaterialTheme.typography.titleLarge.copy(
              fontWeight = FontWeight.Bold,
              fontSize = 18.sp
            ),
            color = BharatTextPrimary,
            modifier = Modifier.padding(vertical = 6.dp)
          )

          if (isLoading && products.isEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
              repeat(3) {
                ShimmerDailyEssentialCard()
              }
            }
          } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
              dailyEssentials.forEach { product ->
                val qtyInCart = cartItems
                  .filter { it.product.id == product.id }
                  .sumOf { it.quantity }

                DailyEssentialCard(
                  product = product,
                  quantityInCart = qtyInCart,
                  onProductClick = { onProductClick(product) },
                  onAddToCart = { onAddToCart(product) }
                )
              }
            }
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

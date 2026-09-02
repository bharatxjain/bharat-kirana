package com.kks.bharatkirana.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ShoppingBag
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
import com.kks.bharatkirana.ui.theme.BharatGreen
import com.kks.bharatkirana.ui.theme.BharatPurplePrimary
import com.kks.bharatkirana.ui.theme.BharatTextPrimary
import com.kks.bharatkirana.ui.theme.BharatTextSecondary

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
  onNotificationsClick: () -> Unit = {},
  unreadNotificationCount: Int = 0,
  promoBanner: String? = null,
  isLoading: Boolean = false,
  activeShopId: String? = null,
  shops: List<com.kks.bharatkirana.data.model.Shop> = emptyList(),
  onShopClick: (com.kks.bharatkirana.data.model.Shop) -> Unit = {},
  onViewAllShopsClick: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val cartItemCount = cartItems.sumOf { it.quantity }
  val cartTotal = cartItems.sumOf { it.totalPrice }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
  ) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .testTag("home_screen_content"),
      contentPadding = PaddingValues(bottom = if (cartItemCount > 0) 130.dp else 48.dp)
    ) {
      // Store Location Header
      item {
        StoreLocationHeader(
          // Prefer the user's saved delivery address over the legacy
          // "activeStore" concept (which the new shop-scoped architecture no
          // longer uses). Blank falls through to the "Pick a delivery
          // location" placeholder inside the header component.
          storeName = userProfile.address.ifBlank { userProfile.activeStore },
          userInitial = userProfile.fullName.firstOrNull()?.toString() ?: "R",
          isAdmin = userProfile.isAdmin,
          unreadNotificationCount = unreadNotificationCount,
          onProfileClick = onProfileClick,
          onStoreClick = onStoreClick,
          onChangeStoreClick = onChangeStoreClick,
          onAdminClick = onAdminClick,
          onNotificationsClick = onNotificationsClick
        )
      }

      // Search Bar — tap navigates to the Search tab (which owns the real
      // TextField) so the keyboard stays open while the user types.
      item {
        GrocerySearchBar(
          query = "",
          onQueryChange = {},
          readOnly = true,
          onClick = { onSearchQueryChange("") }
        )
        Spacer(modifier = Modifier.height(10.dp))
      }

      // Promo banner from Firebase Remote Config (F.promo_banner_text/enabled).
      // Change text/toggle from the Firebase console — no app update needed.
      if (!promoBanner.isNullOrBlank()) {
        item {
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 12.dp, vertical = 4.dp)
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = Icons.Default.Campaign,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 8.dp)
              )
              Text(
                text = promoBanner,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
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

      // Section: All Shops — full-width row cards below categories. Tapping a
      // shop opens its dedicated ShopDetail page.
      item {
        Text(
          text = "Shops Near You",
          style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp),
          color = BharatTextPrimary,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
      }
      if (shops.isEmpty()) {
        item {
          Text(
            text = "No shops available yet. Check back soon.",
            style = MaterialTheme.typography.bodySmall,
            color = BharatTextSecondary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)
          )
        }
      } else {
        items(shops) { shop ->
          NearbyShopRowCard(
            shop = shop,
            onClick = { onShopClick(shop) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
          )
        }
        item { Spacer(modifier = Modifier.height(12.dp)) }
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
private fun NearbyShopRowCard(
  shop: com.kks.bharatkirana.data.model.Shop,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val open = shop.isOpen
  Card(
    onClick = onClick,
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    modifier = modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier.padding(12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(56.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.ShoppingBag,
          contentDescription = null,
          tint = BharatPurplePrimary,
          modifier = Modifier.size(28.dp)
        )
      }
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = shop.name,
            fontWeight = FontWeight.Bold,
            color = BharatTextPrimary,
            fontSize = 15.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f, fill = false)
          )
          Spacer(modifier = Modifier.width(8.dp))
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
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = shop.address.ifBlank { "Nearby" },
          fontSize = 12.sp,
          color = BharatTextSecondary,
          maxLines = 2
        )
        if (shop.distance.isNotBlank() && shop.distance != "---") {
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = shop.distance,
            fontSize = 11.sp,
            color = BharatTextSecondary,
            fontWeight = FontWeight.Medium
          )
        }
      }
    }
  }
}

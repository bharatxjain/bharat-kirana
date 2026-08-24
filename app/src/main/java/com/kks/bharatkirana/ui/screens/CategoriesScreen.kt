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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import com.kks.bharatkirana.ui.components.CartFloatingBanner
import com.kks.bharatkirana.ui.components.ProductGridCard
import com.kks.bharatkirana.ui.components.ShimmerProductCard
import com.kks.bharatkirana.ui.components.getCategoryIcon
import com.kks.bharatkirana.ui.theme.BharatBackground
import com.kks.bharatkirana.ui.theme.BharatPurpleContainer
import com.kks.bharatkirana.ui.theme.BharatPurplePrimary
import com.kks.bharatkirana.ui.theme.BharatTextMuted
import com.kks.bharatkirana.ui.theme.BharatTextPrimary
import com.kks.bharatkirana.ui.theme.BharatTextSecondary

@Composable
fun CategoriesScreen(
  categories: List<Category>,
  products: List<Product>,
  selectedCategory: Category?,
  cartItems: List<CartItem>,
  onSelectCategory: (Category) -> Unit,
  onProductClick: (Product) -> Unit,
  onAddToCart: (Product) -> Unit,
  onUpdateCartQty: (String, String, Int) -> Unit,
  onViewCartClick: () -> Unit,
  isLoading: Boolean = false,
  modifier: Modifier = Modifier
) {
  val currentCategory = selectedCategory ?: categories.firstOrNull()
  val filteredProducts = if (currentCategory != null) {
    products.filter { it.categoryId == currentCategory.id }
  } else {
    products
  }

  val cartItemCount = cartItems.sumOf { it.quantity }
  val cartTotal = cartItems.sumOf { it.totalPrice }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(BharatBackground)
  ) {
    Row(modifier = Modifier.fillMaxSize()) {
      // Left Sidebar - Category list
      LazyColumn(
        modifier = Modifier
          .width(100.dp)
          .fillMaxSize()
          .background(Color(0xFFF1F5F9))
      ) {
        items(categories) { category ->
          val isSelected = currentCategory?.id == category.id
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onSelectCategory(category) }
              .background(if (isSelected) Color.White else Color.Transparent)
              .padding(vertical = 14.dp, horizontal = 8.dp),
            contentAlignment = Alignment.Center
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Box(
                modifier = Modifier
                  .size(44.dp)
                  .background(
                    if (isSelected) BharatPurpleContainer else Color(category.colorHex),
                    RoundedCornerShape(12.dp)
                  ),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = getCategoryIcon(category.iconName),
                  contentDescription = category.name,
                  tint = if (isSelected) BharatPurplePrimary else BharatTextSecondary,
                  modifier = Modifier.size(24.dp)
                )
              }
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = category.name,
                style = MaterialTheme.typography.bodySmall.copy(
                  fontSize = 11.sp,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                ),
                color = if (isSelected) BharatPurplePrimary else BharatTextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 2
              )
            }
          }
          HorizontalDivider(color = Color(0xFFE2E8F0).copy(alpha = 0.5f))
        }
      }

      // Right Content - Products in selected category
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxSize()
          .background(Color.White)
          .padding(horizontal = 12.dp)
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            text = currentCategory?.name ?: "All Categories",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = BharatTextPrimary
          )
          Text(
            text = "${filteredProducts.size} items",
            style = MaterialTheme.typography.bodySmall,
            color = BharatTextMuted
          )
        }

        if (isLoading && filteredProducts.isEmpty()) {
          LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(bottom = if (cartItemCount > 0) 90.dp else 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
          ) {
            items(6) {
              ShimmerProductCard(modifier = Modifier.fillMaxWidth())
            }
          }
        } else if (filteredProducts.isEmpty()) {
          Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
          ) {
            Icon(
              imageVector = Icons.Default.Inventory2,
              contentDescription = null,
              tint = BharatTextMuted,
              modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
              text = "No products found in this category",
              style = MaterialTheme.typography.bodyMedium,
              color = BharatTextSecondary,
              fontWeight = FontWeight.Medium,
              textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "This store hasn't added items here yet. Check back soon!",
              style = MaterialTheme.typography.bodySmall,
              color = BharatTextMuted,
              textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
          }
        } else {
          LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(bottom = if (cartItemCount > 0) 90.dp else 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
          ) {
            items(filteredProducts) { product ->
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

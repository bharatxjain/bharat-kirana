package com.kks.bharatkirana.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.kks.bharatkirana.ui.theme.BharatGreen
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
  var sortMode by remember { mutableStateOf(ProductSort.RELEVANCE) }
  var minRating by remember { mutableStateOf(0f) }
  // Reset filters when the category changes so a previous filter doesn't hide
  // everything in a newly-picked category.
  androidx.compose.runtime.LaunchedEffect(currentCategory?.id) {
    sortMode = ProductSort.RELEVANCE
    minRating = 0f
  }
  val filteredProducts = remember(products, currentCategory?.id, sortMode, minRating) {
    val base = if (currentCategory != null) {
      products.filter { it.categoryId == currentCategory.id }
    } else {
      products
    }
    val rated = if (minRating > 0f) base.filter { it.rating >= minRating } else base
    when (sortMode) {
      ProductSort.RELEVANCE -> rated
      ProductSort.PRICE_LOW_HIGH -> rated.sortedBy { it.currentPrice }
      ProductSort.PRICE_HIGH_LOW -> rated.sortedByDescending { it.currentPrice }
      ProductSort.DISCOUNT_HIGH_LOW -> rated.sortedByDescending { it.discountPercent }
    }
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

        // Sort + rating filter chip row. Filters real-time; no bottom sheets or
        // backend calls — everything computes from the local product list.
        CategoryFilterBar(
          sortMode = sortMode,
          onSortChange = { sortMode = it },
          minRating = minRating,
          onRatingChange = { minRating = it }
        )

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

enum class ProductSort(val label: String) {
  RELEVANCE("Relevance (default)"),
  PRICE_LOW_HIGH("Price (low to high)"),
  PRICE_HIGH_LOW("Price (high to low)"),
  DISCOUNT_HIGH_LOW("Discount (high to low)")
}

private data class RatingOption(val value: Float, val label: String)

private val RatingOptions = listOf(
  RatingOption(0f,  "Any"),
  RatingOption(3f,  "3+ stars"),
  RatingOption(4f,  "4+ stars"),
  RatingOption(4.5f, "4.5+ stars")
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CategoryFilterBar(
  sortMode: ProductSort,
  onSortChange: (ProductSort) -> Unit,
  minRating: Float,
  onRatingChange: (Float) -> Unit
) {
  var showSortSheet by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
  var showRatingSheet by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 6.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    FilterDropdownPill(
      label = "Sort by",
      value = sortMode.label,
      onClick = { showSortSheet = true },
      modifier = Modifier.weight(1f)
    )
    val ratingCurrent = RatingOptions.firstOrNull { kotlin.math.abs(it.value - minRating) < 0.01f }
      ?: RatingOptions.first()
    FilterDropdownPill(
      label = "Rating",
      value = ratingCurrent.label,
      onClick = { showRatingSheet = true },
      modifier = Modifier.weight(1f)
    )
  }

  if (showSortSheet) {
    ModalBottomSheet(
      onDismissRequest = { showSortSheet = false },
      containerColor = Color.White,
      dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
      Text(
        text = "Sort by",
        fontWeight = FontWeight.ExtraBold,
        fontSize = 18.sp,
        color = BharatTextPrimary,
        modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 8.dp)
      )
      HorizontalDivider(color = Color(0xFFF1F5F9))
      ProductSort.entries.forEach { mode ->
        val selected = mode == sortMode
        RadioOptionRow(
          label = mode.label,
          selected = selected,
          onClick = {
            onSortChange(mode)
            showSortSheet = false
          }
        )
      }
      Spacer(modifier = Modifier.height(16.dp))
    }
  }

  if (showRatingSheet) {
    ModalBottomSheet(
      onDismissRequest = { showRatingSheet = false },
      containerColor = Color.White,
      dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
      Text(
        text = "Rating",
        fontWeight = FontWeight.ExtraBold,
        fontSize = 18.sp,
        color = BharatTextPrimary,
        modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 8.dp)
      )
      HorizontalDivider(color = Color(0xFFF1F5F9))
      RatingOptions.forEach { opt ->
        val selected = kotlin.math.abs(opt.value - minRating) < 0.01f
        RadioOptionRow(
          label = opt.label,
          selected = selected,
          onClick = {
            onRatingChange(opt.value)
            showRatingSheet = false
          }
        )
      }
      Spacer(modifier = Modifier.height(16.dp))
    }
  }
}

@Composable
private fun FilterDropdownPill(
  label: String,
  value: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(12.dp),
    color = Color.White,
    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
    modifier = modifier
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(text = label, fontSize = 10.sp, color = BharatTextSecondary)
        Text(
          text = value,
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          color = BharatTextPrimary,
          maxLines = 1
        )
      }
      Icon(
        imageVector = Icons.Default.ArrowDropDown,
        contentDescription = null,
        tint = BharatPurplePrimary
      )
    }
  }
}

@Composable
private fun RadioOptionRow(
  label: String,
  selected: Boolean,
  onClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = 20.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    androidx.compose.material3.RadioButton(
      selected = selected,
      onClick = onClick,
      colors = androidx.compose.material3.RadioButtonDefaults.colors(
        selectedColor = BharatGreen,
        unselectedColor = BharatGreen
      )
    )
    Spacer(modifier = Modifier.width(12.dp))
    Text(
      text = label,
      fontSize = 15.sp,
      fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
      color = if (selected) BharatTextPrimary else BharatTextSecondary
    )
  }
}

package com.kks.bharatkirana.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kks.bharatkirana.R
import com.kks.bharatkirana.data.model.CartItem
import com.kks.bharatkirana.data.model.Product
import com.kks.bharatkirana.data.model.Shop
import com.kks.bharatkirana.data.model.WeightOption
import com.kks.bharatkirana.ui.components.ProductGridCard
import com.kks.bharatkirana.ui.theme.*

@Composable
fun ProductDetailScreen(
  product: Product,
  vendor: Shop?,
  recommendations: List<Product>,
  cartItems: List<CartItem>,
  onBackClick: () -> Unit,
  onAddToCart: (Product, WeightOption, Int) -> Unit,
  onProductClick: (Product) -> Unit,
  onViewCartClick: () -> Unit,
  onStoreClick: (Shop) -> Unit = {},
  isFavorite: Boolean = false,
  onFavoriteToggle: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val defaultWeight = product.weightOptions.firstOrNull() ?: WeightOption(product.unit, product.currentPrice)
  var selectedWeight by remember { mutableStateOf(defaultWeight) }
  var quantity by remember { mutableIntStateOf(1) }

  val context = LocalContext.current

  val savings = if (selectedWeight.originalPrice > selectedWeight.price) {
    selectedWeight.originalPrice - selectedWeight.price
  } else {
    product.originalPrice - product.currentPrice
  }

  Surface(
    modifier = modifier.fillMaxSize(),
    color = Color.White
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding()
    ) {
      // Top App Bar
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(
          onClick = onBackClick,
          modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color(0xFFF3F4F6))
            .testTag("product_detail_back_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = BharatTextPrimary
          )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
            onClick = { 
              val sendIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, "Check out ${product.name} on BreakQ! Price: ₹${selectedWeight.price}")
                type = "text/plain"
              }
              val shareIntent = Intent.createChooser(sendIntent, null)
              context.startActivity(shareIntent)
            },
            modifier = Modifier
              .size(40.dp)
              .clip(CircleShape)
              .background(Color(0xFFF3F4F6))
          ) {
            Icon(
              imageVector = Icons.Outlined.Share,
              contentDescription = "Share",
              tint = BharatTextPrimary,
              modifier = Modifier.size(20.dp)
            )
          }
          Spacer(modifier = Modifier.width(8.dp))
          IconButton(
            onClick = { onFavoriteToggle() },
            modifier = Modifier
              .size(40.dp)
              .clip(CircleShape)
              .background(Color(0xFFF3F4F6))
              .testTag("favorite_button")
          ) {
            Icon(
              imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
              contentDescription = "Favorite",
              tint = if (isFavorite) Color.Red else BharatTextPrimary,
              modifier = Modifier.size(20.dp)
            )
          }
        }
      }

      Column(
        modifier = Modifier
          .weight(1f)
          .verticalScroll(rememberScrollState())
          .padding(bottom = 16.dp)
      ) {
        // Discount and Rating
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          if (product.discountPercent > 0) {
            Surface(
              color = BharatRedDiscount,
              shape = RoundedCornerShape(8.dp)
            ) {
              Text(
                text = "🏷 ${product.discountPercent}% OFF",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
              )
            }
          } else {
            Spacer(modifier = Modifier.width(1.dp))
          }

          Surface(
            color = Color(0xFFF3F4F6),
            shape = RoundedCornerShape(8.dp)
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFB800), modifier = Modifier.size(14.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text(text = product.rating.toString(), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BharatTextPrimary)
              Text(text = " (${product.reviewCount})", fontSize = 11.sp, color = BharatTextSecondary)
            }
          }
        }

        // Product Hero Image
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .padding(16.dp),
          contentAlignment = Alignment.Center
        ) {
          when {
            product.imageUrl.isNotBlank() -> {
              AsyncImage(
                model = product.imageUrl,
                contentDescription = product.name,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Fit
              )
            }
            product.localImageRes != null -> {
              Image(
                painter = painterResource(id = product.localImageRes),
                contentDescription = product.name,
                modifier = Modifier
                  .fillMaxSize()
                  .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Fit
              )
            }
            else -> {
              Icon(
                imageVector = Icons.Default.Grain,
                contentDescription = null,
                tint = BharatPurplePrimary,
                modifier = Modifier.size(90.dp)
              )
            }
          }
        }

        // Details Section
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
          Text(
            text = product.brand.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(
              fontWeight = FontWeight.Bold,
              letterSpacing = 1.sp
            ),
            color = BharatPurplePrimary
          )

          Spacer(modifier = Modifier.height(4.dp))

          Text(
            text = product.name,
            style = MaterialTheme.typography.headlineSmall.copy(
              fontWeight = FontWeight.Bold,
              fontSize = 22.sp
            ),
            color = BharatTextPrimary
          )

          Spacer(modifier = Modifier.height(4.dp))

          Text(
            text = product.subtitle.ifEmpty { "${product.unit} • High Quality Fresh" },
            style = MaterialTheme.typography.bodyMedium,
            color = BharatTextSecondary
          )

          Spacer(modifier = Modifier.height(12.dp))

          // Price Row
          Row(
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "₹${selectedWeight.price}",
              style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 26.sp
              ),
              color = BharatTextPrimary
            )

            if (selectedWeight.originalPrice > selectedWeight.price) {
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "₹${selectedWeight.originalPrice}",
                style = MaterialTheme.typography.titleMedium.copy(
                  textDecoration = TextDecoration.LineThrough
                ),
                color = BharatTextMuted
              )
            }

            if (savings > 0) {
              Spacer(modifier = Modifier.width(12.dp))
              Surface(
                shape = RoundedCornerShape(6.dp),
                color = BharatPurpleContainer
              ) {
                Text(
                  text = "You save ₹$savings",
                  color = BharatPurpleDark,
                  fontWeight = FontWeight.Bold,
                  fontSize = 12.sp,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(20.dp))
          HorizontalDivider(color = Color(0xFFF3F4F6))
          Spacer(modifier = Modifier.height(16.dp))

          // Vendor Info
          if (vendor != null) {
            Card(
              onClick = { onStoreClick(vendor) },
              shape = RoundedCornerShape(16.dp),
              colors = CardDefaults.cardColors(containerColor = BharatPurpleContainer.copy(alpha = 0.4f)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Box(
                  modifier = Modifier.size(44.dp).clip(CircleShape).background(BharatPurplePrimary),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(Icons.Default.Storefront, contentDescription = null, tint = Color.White)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(text = "Sold by", style = MaterialTheme.typography.labelSmall, color = BharatTextSecondary)
                  Text(text = vendor.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = BharatTextPrimary)
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = BharatPurplePrimary, modifier = Modifier.size(20.dp))
              }
            }
            Spacer(modifier = Modifier.height(20.dp))
          }

          // Select Weight
          if (product.weightOptions.isNotEmpty()) {
            Text(
              text = "Select Weight",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = BharatTextPrimary
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              product.weightOptions.forEach { weight ->
                val isSelected = selectedWeight.label == weight.label
                Surface(
                  onClick = { selectedWeight = weight },
                  shape = RoundedCornerShape(12.dp),
                  color = if (isSelected) Color(0xFFF3E8FF) else Color(0xFFF9FAFB),
                  border = if (isSelected) BorderStroke(1.5.dp, BharatPurplePrimary) else BorderStroke(1.dp, Color(0xFFE5E7EB)),
                  modifier = Modifier.testTag("weight_option_${weight.label}")
                ) {
                  Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                  ) {
                    Text(
                      text = weight.label,
                      fontWeight = FontWeight.Bold,
                      fontSize = 14.sp,
                      color = if (isSelected) BharatPurplePrimary else BharatTextPrimary
                    )
                    Text(
                      text = "₹${weight.price}",
                      fontWeight = FontWeight.SemiBold,
                      fontSize = 13.sp,
                      color = if (isSelected) BharatPurplePrimary else BharatTextSecondary
                    )
                  }
                }
              }
            }
            Spacer(modifier = Modifier.height(20.dp))
          }

          // About this product
          Text(
            text = "About this product",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = BharatTextPrimary
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = product.description.ifEmpty { "High quality kirana grocery sourced directly from verified farms and manufacturers for daily fresh cooking." },
            style = MaterialTheme.typography.bodyMedium.copy(
              lineHeight = 22.sp
            ),
            color = BharatTextSecondary
          )

          Spacer(modifier = Modifier.height(16.dp))

          // Feature Badges
          if (product.features.isNotEmpty()) {
            val chunked = product.features.chunked(2)
            chunked.forEach { rowFeatures ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                rowFeatures.forEach { feature ->
                  Row(
                    modifier = Modifier
                      .weight(1f)
                      .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Icon(
                      imageVector = when (feature.iconType) {
                        "wheat" -> Icons.Default.Grain
                        "clean" -> Icons.Default.Restaurant
                        "safety" -> Icons.Default.Security
                        else -> Icons.Default.CheckCircleOutline
                      },
                      contentDescription = null,
                      tint = BharatPurplePrimary,
                      modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      text = feature.title,
                      style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                      color = BharatTextPrimary
                    )
                  }
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(24.dp))

          // You might also like
          Text(
            text = "You might also like",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = BharatTextPrimary
          )
          Spacer(modifier = Modifier.height(12.dp))

          LazyRow(
            contentPadding = PaddingValues(end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            items(recommendations) { recProduct ->
              ProductGridCard(
                product = recProduct,
                quantityInCart = cartItems.filter { it.product.id == recProduct.id }.sumOf { it.quantity },
                onProductClick = { onProductClick(recProduct) },
                onAddToCart = {
                  onAddToCart(recProduct, recProduct.weightOptions.firstOrNull() ?: WeightOption(recProduct.unit, recProduct.currentPrice), 1)
                },
                onIncrease = {
                  val weight = recProduct.weightOptions.firstOrNull()?.label ?: recProduct.unit
                  onAddToCart(recProduct, recProduct.weightOptions.firstOrNull() ?: WeightOption(weight, recProduct.currentPrice), 1)
                },
                onDecrease = {}
              )
            }
          }
        }
      }

      // Bottom Sticky Bar
      Surface(
        color = Color.White,
        shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth()
      ) {
        val cartItem = cartItems.find { it.product.id == product.id && it.selectedWeight.label == selectedWeight.label }
        val isInCart = cartItem != null

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
              .background(Color(0xFFF3F4F6), RoundedCornerShape(12.dp))
              .padding(horizontal = 4.dp, vertical = 4.dp)
          ) {
            IconButton(
              onClick = { 
                if (isInCart) {
                  onAddToCart(product, selectedWeight, -1)
                } else if (quantity > 1) {
                  quantity--
                }
              },
              modifier = Modifier.size(36.dp)
            ) {
              Icon(imageVector = Icons.Default.Remove, contentDescription = null, tint = BharatTextPrimary)
            }
            Text(
              text = "${if (isInCart) cartItem.quantity else quantity}",
              fontWeight = FontWeight.Bold,
              fontSize = 16.sp,
              color = BharatTextPrimary,
              modifier = Modifier.padding(horizontal = 8.dp)
            )
            IconButton(
              onClick = { 
                if (isInCart) {
                  onAddToCart(product, selectedWeight, 1)
                } else {
                  quantity++
                }
              },
              modifier = Modifier.size(36.dp)
            ) {
              Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = BharatPurplePrimary)
            }
          }

          Button(
            onClick = {
              if (isInCart) onViewCartClick() else onAddToCart(product, selectedWeight, quantity)
            },
            modifier = Modifier.weight(1f).height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BharatPurplePrimary),
            shape = RoundedCornerShape(12.dp)
          ) {
            Text(text = if (isInCart) "View Cart" else "Add to Cart", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
          }
        }
      }
    }
  }
}

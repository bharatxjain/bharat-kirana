package com.kks.bharatkirana.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kks.bharatkirana.R
import com.kks.bharatkirana.data.model.CartItem
import com.kks.bharatkirana.data.model.Category
import com.kks.bharatkirana.data.model.MainTab
import com.kks.bharatkirana.data.model.OrderStatus
import com.kks.bharatkirana.data.model.OrderTimelineItem
import com.kks.bharatkirana.data.model.Product
import com.kks.bharatkirana.ui.theme.*

@Composable
fun StoreLocationHeader(
  storeName: String = "BreakQ Store",
  userInitial: String = "R",
  isAdmin: Boolean = false,
  unreadNotificationCount: Int = 0,
  onProfileClick: () -> Unit = {},
  onStoreClick: () -> Unit = {},
  onChangeStoreClick: () -> Unit = {},
  onAdminClick: () -> Unit = {},
  onNotificationsClick: () -> Unit = {}
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(Color.White)
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .weight(1f)
        .clickable(onClick = onStoreClick)
    ) {
      Box(
        modifier = Modifier
          .size(40.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.LocationOn,
          contentDescription = null,
          tint = BharatPurplePrimary,
          modifier = Modifier.size(20.dp)
        )
      }
      
      Spacer(modifier = Modifier.width(12.dp))
      
      Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = "Delivering to",
            style = MaterialTheme.typography.labelSmall,
            color = BharatTextSecondary
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = "• Change",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = BharatPurplePrimary,
            modifier = Modifier.clickable(onClick = onChangeStoreClick)
          )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = storeName,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = BharatTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
          )
          Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = BharatTextMuted,
            modifier = Modifier.size(16.dp)
          )
        }
      }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
      if (isAdmin) {
        IconButton(
          onClick = onAdminClick,
          modifier = Modifier.size(32.dp)
        ) {
          Icon(Icons.Default.AdminPanelSettings, contentDescription = "Admin", tint = BharatPurplePrimary)
        }
        Spacer(modifier = Modifier.width(8.dp))
      }

      Box {
        IconButton(
          onClick = onNotificationsClick,
          modifier = Modifier.size(36.dp).testTag("notifications_bell_icon")
        ) {
          Icon(
            imageVector = Icons.Default.Notifications,
            contentDescription = "Notifications",
            tint = BharatPurplePrimary
          )
        }
        if (unreadNotificationCount > 0) {
          Box(
            modifier = Modifier
              .align(Alignment.TopEnd)
              .padding(top = 4.dp, end = 4.dp)
              .size(16.dp)
              .clip(CircleShape)
              .background(Color(0xFFDC2626)),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = if (unreadNotificationCount > 9) "9+" else unreadNotificationCount.toString(),
              color = Color.White,
              fontSize = 9.sp,
              fontWeight = FontWeight.Bold
            )
          }
        }
      }
      Spacer(modifier = Modifier.width(4.dp))

      // Round 6: circular avatar with the user's first-name initial (Blinkit-style)
      // instead of the old generic hero image. Falls back to "U" when the profile
      // hasn't loaded yet or is unavailable.
      val initial = userInitial.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "U"
      Box(
        modifier = Modifier
          .size(36.dp)
          .clip(CircleShape)
          .background(
            Brush.linearGradient(listOf(BharatPurplePrimary, BharatPurpleAccent))
          )
          .clickable(onClick = onProfileClick),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = initial,
          color = Color.White,
          fontWeight = FontWeight.Bold,
          fontSize = 16.sp
        )
      }
    }
  }
}

@Composable
fun GrocerySearchBar(
  query: String,
  onQueryChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  placeholder: String = "Search groceries, rice, atta..."
) {
  OutlinedTextField(
    value = query,
    onValueChange = onQueryChange,
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 6.dp)
      .shadow(elevation = 2.dp, shape = RoundedCornerShape(14.dp), spotColor = Color(0x1A000000))
      .background(Color.White, RoundedCornerShape(14.dp))
      .testTag("grocery_search_bar"),
    placeholder = {
      Text(
        text = placeholder,
        color = BharatTextMuted,
        fontSize = 14.sp
      )
    },
    leadingIcon = {
      Icon(
        imageVector = Icons.Default.Search,
        contentDescription = "Search",
        tint = BharatTextMuted
      )
    },
    singleLine = true,
    shape = RoundedCornerShape(14.dp),
    colors = OutlinedTextFieldDefaults.colors(
      focusedTextColor = BharatTextPrimary,
      unfocusedTextColor = BharatTextPrimary,
      focusedBorderColor = BharatPurplePrimary,
      unfocusedBorderColor = MaterialTheme.colorScheme.outline
    )
  )
}

fun getCategoryIcon(iconName: String): ImageVector {
  return when (iconName) {
    "egg" -> Icons.Default.Egg
    "local_drink" -> Icons.Default.LocalDrink
    "coffee" -> Icons.Default.Coffee
    "grain" -> Icons.Default.Inventory2
    "fastfood" -> Icons.Default.Fastfood
    "icecream" -> Icons.Default.Restaurant
    "cookie" -> Icons.Default.BreakfastDining
    "restaurant" -> Icons.Default.Restaurant
    "inventory_2" -> Icons.Default.Inventory2
    "breakfast_dining" -> Icons.Default.BreakfastDining
    "spa" -> Icons.Default.Spa
    "medical_services" -> Icons.Default.MedicalServices
    else -> Icons.Default.ShoppingCart
  }
}

@Composable
fun CategoryItemCard(
  category: Category,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
      )
      .padding(horizontal = 4.dp, vertical = 6.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Box(
      modifier = Modifier
        .size(68.dp)
        .clip(RoundedCornerShape(16.dp))
        .background(Color(category.colorHex))
        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = getCategoryIcon(category.iconName),
        contentDescription = category.name,
        tint = BharatPurplePrimary,
        modifier = Modifier.size(32.dp)
      )
    }
    Spacer(modifier = Modifier.height(6.dp))
    Text(
      text = category.name,
      style = MaterialTheme.typography.bodySmall.copy(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 14.sp
      ),
      color = BharatTextPrimary,
      textAlign = TextAlign.Center,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.width(72.dp)
    )
  }
}

@Composable
fun ProductGridCard(
  product: Product,
  quantityInCart: Int,
  onProductClick: () -> Unit,
  onAddToCart: () -> Unit,
  onIncrease: () -> Unit,
  onDecrease: () -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier
      .width(172.dp)
      .clickable(onClick = onProductClick)
      .testTag("product_card_${product.id}"),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
  ) {
    Column(modifier = Modifier.padding(10.dp)) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(130.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
      ) {
        when {
          product.imageUrl.isNotBlank() -> {
            AsyncImage(
              model = product.imageUrl,
              contentDescription = product.name,
              modifier = Modifier.fillMaxSize(),
              contentScale = ContentScale.Crop
            )
          }
          product.localImageRes != null -> {
            Image(
              painter = painterResource(id = product.localImageRes),
              contentDescription = product.name,
              modifier = Modifier.fillMaxSize(),
              contentScale = ContentScale.Crop
            )
          }
          else -> {
            Icon(
              imageVector = Icons.Default.ShoppingCart,
              contentDescription = null,
              tint = BharatPurpleAccent,
              modifier = Modifier.size(48.dp)
            )
          }
        }

        if (product.discountPercent > 0) {
          Surface(
            color = BharatRedDiscount,
            shape = RoundedCornerShape(topStart = 12.dp, bottomEnd = 8.dp),
            modifier = Modifier.align(Alignment.TopStart)
          ) {
            Text(
              text = "${product.discountPercent}% OFF",
              color = Color.White,
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = product.name,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        color = BharatTextPrimary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.height(36.dp)
      )

      Text(
        text = product.unit,
        style = MaterialTheme.typography.bodySmall,
        color = BharatTextMuted
      )

      Spacer(modifier = Modifier.height(4.dp))

      // Stock Status Badge
      val statusColor = when(product.stockStatus) {
        "In Stock" -> BharatGreen
        "Low Stock" -> Color(0xFFF59E0B)
        "Call to Confirm" -> Color(0xFFD97706)
        else -> Color(0xFFDC2626)
      }
      val statusPrefix = when(product.stockStatus) {
        "In Stock" -> "🟢 "
        "Call to Confirm" -> "⚠️ "
        else -> ""
      }
      Surface(
        color = statusColor.copy(alpha = 0.1f),
        shape = RoundedCornerShape(4.dp)
      ) {
        Text(
          text = "$statusPrefix${product.stockStatus}",
          color = statusColor,
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
      }

      Spacer(modifier = Modifier.height(6.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Column {
          Text(
            text = "₹${product.currentPrice}",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = BharatTextPrimary
          )
          if (product.originalPrice > product.currentPrice) {
            Text(
              text = "₹${product.originalPrice}",
              style = MaterialTheme.typography.bodySmall.copy(
                textDecoration = TextDecoration.LineThrough
              ),
              color = BharatTextMuted
            )
          }
        }

        if (quantityInCart == 0) {
          Surface(
            onClick = onAddToCart,
            shape = RoundedCornerShape(8.dp),
            color = BharatPurplePrimary,
            modifier = Modifier.testTag("add_button_${product.id}")
          ) {
            Text(
              text = "ADD",
              color = Color.White,
              fontWeight = FontWeight.Bold,
              fontSize = 12.sp,
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
          }
        } else {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
              .background(BharatPurpleContainer, RoundedCornerShape(8.dp))
              .border(1.dp, BharatPurplePrimary, RoundedCornerShape(8.dp))
          ) {
            IconButton(
              onClick = onDecrease,
              modifier = Modifier.size(28.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = "Decrease",
                tint = BharatPurplePrimary,
                modifier = Modifier.size(14.dp)
              )
            }
            Text(
              text = "$quantityInCart",
              fontWeight = FontWeight.Bold,
              color = BharatPurplePrimary,
              fontSize = 13.sp,
              modifier = Modifier.padding(horizontal = 4.dp)
            )
            IconButton(
              onClick = onIncrease,
              modifier = Modifier.size(28.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Increase",
                tint = BharatPurplePrimary,
                modifier = Modifier.size(14.dp)
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun DailyEssentialCard(
  product: Product,
  quantityInCart: Int,
  onProductClick: () -> Unit,
  onAddToCart: () -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier
      .fillMaxWidth()
      .clickable(onClick = onProductClick),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(76.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(Color(0xFFF8FAFC)),
        contentAlignment = Alignment.Center
      ) {
        when {
          product.imageUrl.isNotBlank() -> {
            AsyncImage(
              model = product.imageUrl,
              contentDescription = product.name,
              modifier = Modifier.fillMaxSize(),
              contentScale = ContentScale.Crop
            )
          }
          product.localImageRes != null -> {
            Image(
              painter = painterResource(id = product.localImageRes),
              contentDescription = product.name,
              modifier = Modifier.fillMaxSize(),
              contentScale = ContentScale.Crop
            )
          }
          else -> {
            Icon(
              imageVector = Icons.Default.ShoppingCart,
              contentDescription = null,
              tint = BharatPurpleAccent,
              modifier = Modifier.size(36.dp)
            )
          }
        }
      }

      Spacer(modifier = Modifier.width(12.dp))

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = product.name,
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
          color = BharatTextPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = product.unit,
          style = MaterialTheme.typography.bodySmall,
          color = BharatTextMuted
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = "₹${product.currentPrice}",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = BharatTextPrimary
          )
          if (product.originalPrice > product.currentPrice) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "₹${product.originalPrice}",
              style = MaterialTheme.typography.bodySmall.copy(
                textDecoration = TextDecoration.LineThrough
              ),
              color = BharatTextMuted
            )
          }
        }
      }

      Surface(
        onClick = onAddToCart,
        shape = RoundedCornerShape(10.dp),
        color = BharatPurplePrimary,
        modifier = Modifier.size(36.dp)
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Add to Cart",
            tint = Color.White,
            modifier = Modifier.size(20.dp)
          )
        }
      }
    }
  }
}

@Composable
fun CartFloatingBanner(
  itemCount: Int,
  totalAmount: Int,
  discountApplied: Int = 15,
  onViewCartClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  AnimatedVisibility(
    visible = itemCount > 0,
    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
    modifier = modifier
  ) {
    Surface(
      onClick = onViewCartClick,
      color = BharatPurplePrimary,
      shape = RoundedCornerShape(16.dp),
      shadowElevation = 8.dp,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 6.dp)
        .testTag("floating_cart_banner")
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(36.dp)
              .clip(CircleShape)
              .background(Color.White.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.ShoppingCart,
              contentDescription = "Cart",
              tint = Color.White,
              modifier = Modifier.size(18.dp)
            )
          }
          Spacer(modifier = Modifier.width(12.dp))
          Column {
            Text(
              text = "$itemCount items | ₹$totalAmount",
              color = Color.White,
              fontWeight = FontWeight.Bold,
              fontSize = 15.sp
            )
            if (discountApplied > 0) {
              Text(
                text = "Extra ₹$discountApplied off applied",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 11.sp
              )
            }
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = "View Cart",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
          )
          Spacer(modifier = Modifier.width(4.dp))
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = "Go to cart",
            tint = Color.White,
            modifier = Modifier.size(16.dp)
          )
        }
      }
    }
  }
}

@Composable
fun StorePickupCard(
  storeName: String = "BreakQ Store",
  storeAddress: String = "Banjara Hills Rd 12, Hyderabad",
  timeSlot: String = "Ready in ~15 mins",
  modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = BharatPurpleContainer.copy(alpha = 0.6f))
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(44.dp)
          .clip(CircleShape)
          .background(BharatPurplePrimary),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.Storefront,
          contentDescription = "Store",
          tint = Color.White,
          modifier = Modifier.size(24.dp)
        )
      }
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Pickup from",
          style = MaterialTheme.typography.labelSmall,
          color = BharatTextSecondary
        )
        Text(
          text = "$storeName, $storeAddress",
          style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
          color = BharatTextPrimary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = timeSlot,
          style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
          color = BharatPurplePrimary
        )
      }
    }
  }
}

@Composable
fun CustomQrCodePattern(
  modifier: Modifier = Modifier,
  tint: Color = Color(0xFF1F2937)
) {
  Canvas(
    modifier = modifier
      .aspectRatio(1f)
      .fillMaxSize()
  ) {
    val sizePx = size.minDimension
    val unit = sizePx / 10f

    // Top-left finder pattern
    drawRoundRect(
      color = tint,
      topLeft = Offset(unit * 0.5f, unit * 0.5f),
      size = Size(unit * 2.8f, unit * 2.8f),
      cornerRadius = CornerRadius(unit * 0.4f, unit * 0.4f)
    )
    drawRoundRect(
      color = Color.White,
      topLeft = Offset(unit * 0.9f, unit * 0.9f),
      size = Size(unit * 2f, unit * 2f),
      cornerRadius = CornerRadius(unit * 0.3f, unit * 0.3f)
    )
    drawRoundRect(
      color = tint,
      topLeft = Offset(unit * 1.3f, unit * 1.3f),
      size = Size(unit * 1.2f, unit * 1.2f),
      cornerRadius = CornerRadius(unit * 0.2f, unit * 0.2f)
    )

    // Top-right finder pattern
    drawRoundRect(
      color = tint,
      topLeft = Offset(sizePx - unit * 3.3f, unit * 0.5f),
      size = Size(unit * 2.8f, unit * 2.8f),
      cornerRadius = CornerRadius(unit * 0.4f, unit * 0.4f)
    )
    drawRoundRect(
      color = Color.White,
      topLeft = Offset(sizePx - unit * 2.9f, unit * 0.9f),
      size = Size(unit * 2f, unit * 2f),
      cornerRadius = CornerRadius(unit * 0.3f, unit * 0.3f)
    )
    drawRoundRect(
      color = tint,
      topLeft = Offset(sizePx - unit * 2.5f, unit * 1.3f),
      size = Size(unit * 1.2f, unit * 1.2f),
      cornerRadius = CornerRadius(unit * 0.2f, unit * 0.2f)
    )

    // Bottom-left finder pattern
    drawRoundRect(
      color = tint,
      topLeft = Offset(unit * 0.5f, sizePx - unit * 3.3f),
      size = Size(unit * 2.8f, unit * 2.8f),
      cornerRadius = CornerRadius(unit * 0.4f, unit * 0.4f)
    )
    drawRoundRect(
      color = Color.White,
      topLeft = Offset(unit * 0.9f, sizePx - unit * 2.9f),
      size = Size(unit * 2f, unit * 2f),
      cornerRadius = CornerRadius(unit * 0.3f, unit * 0.3f)
    )
    drawRoundRect(
      color = tint,
      topLeft = Offset(unit * 1.3f, sizePx - unit * 2.5f),
      size = Size(unit * 1.2f, unit * 1.2f),
      cornerRadius = CornerRadius(unit * 0.2f, unit * 0.2f)
    )

    // Data blocks / geometric matrix
    val blocks = listOf(
      Offset(unit * 4.2f, unit * 0.8f) to Size(unit * 0.9f, unit * 0.9f),
      Offset(unit * 5.4f, unit * 0.8f) to Size(unit * 0.9f, unit * 0.9f),
      Offset(unit * 4.2f, unit * 2.0f) to Size(unit * 1.2f, unit * 1.0f),
      Offset(unit * 3.8f, unit * 3.6f) to Size(unit * 1.0f, unit * 1.0f),
      Offset(unit * 5.2f, unit * 3.6f) to Size(unit * 1.0f, unit * 1.0f),
      Offset(unit * 6.5f, unit * 3.6f) to Size(unit * 1.0f, unit * 1.0f),
      Offset(unit * 3.8f, unit * 5.0f) to Size(unit * 1.0f, unit * 1.0f),
      Offset(unit * 5.2f, unit * 5.0f) to Size(unit * 1.0f, unit * 1.0f),
      Offset(unit * 6.5f, unit * 4.8f) to Size(unit * 1.2f, unit * 1.2f),
      Offset(unit * 7.9f, unit * 4.8f) to Size(unit * 1.2f, unit * 1.2f),
      Offset(unit * 3.8f, unit * 6.5f) to Size(unit * 1.4f, unit * 1.4f),
      Offset(unit * 5.6f, unit * 6.5f) to Size(unit * 1.0f, unit * 1.0f),
      Offset(unit * 7.0f, unit * 6.5f) to Size(unit * 1.0f, unit * 1.0f),
      Offset(unit * 4.5f, unit * 8.0f) to Size(unit * 2.0f, unit * 0.6f),
      Offset(unit * 7.2f, unit * 7.8f) to Size(unit * 1.2f, unit * 1.2f)
    )

    blocks.forEach { (offset, blockSize) ->
      drawRoundRect(
        color = tint,
        topLeft = offset,
        size = blockSize,
        cornerRadius = CornerRadius(unit * 0.15f, unit * 0.15f)
      )
    }
  }
}

@Composable
fun OrderTimelineView(
  timeline: List<OrderTimelineItem>,
  modifier: Modifier = Modifier
) {
  Column(modifier = modifier.fillMaxWidth()) {
    timeline.forEachIndexed { index, item ->
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.width(36.dp)
        ) {
          Box(
            modifier = Modifier
              .size(26.dp)
              .clip(CircleShape)
              .background(
                when {
                  item.isCompleted -> BharatPurplePrimary
                  item.isCurrent -> BharatPurpleAccent
                  else -> Color(0xFFE2E8F0)
                }
              ),
            contentAlignment = Alignment.Center
          ) {
            if (item.isCompleted) {
              Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Completed",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
              )
            } else if (item.isCurrent) {
              Box(
                modifier = Modifier
                  .size(10.dp)
                  .clip(CircleShape)
                  .background(Color.White)
              )
            }
          }

          if (index < timeline.size - 1) {
            Box(
              modifier = Modifier
                .width(2.dp)
                .height(36.dp)
                .background(
                  if (item.isCompleted) BharatPurplePrimary else Color(0xFFE2E8F0)
                )
            )
          }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.padding(top = 2.dp)) {
          Text(
            text = item.status.label,
            style = MaterialTheme.typography.bodyMedium.copy(
              fontWeight = if (item.isCompleted || item.isCurrent) FontWeight.Bold else FontWeight.Normal
            ),
            color = if (item.isCompleted || item.isCurrent) BharatTextPrimary else BharatTextMuted
          )
          Text(
            text = item.time,
            style = MaterialTheme.typography.bodySmall,
            color = BharatTextSecondary
          )
        }
      }
    }
  }
}

@Composable
fun BharatBottomNavigationBar(
  currentTab: MainTab,
  onTabSelected: (MainTab) -> Unit,
  cartItemCount: Int = 0,
  modifier: Modifier = Modifier
) {
  NavigationBar(
    containerColor = Color.White,
    tonalElevation = 8.dp,
    modifier = modifier
      .navigationBarsPadding()
      .testTag("bottom_nav_bar")
  ) {
    MainTab.entries.forEach { tab ->
      val isSelected = currentTab == tab
      val (icon, selectedIcon) = when (tab) {
        MainTab.HOME -> Icons.Outlined.Home to Icons.Default.Home
        MainTab.CATEGORIES -> Icons.Outlined.GridView to Icons.Default.GridView
        MainTab.SEARCH -> Icons.Outlined.Search to Icons.Default.Search
        MainTab.ORDERS -> Icons.Outlined.ReceiptLong to Icons.Default.ReceiptLong
        MainTab.PROFILE -> Icons.Outlined.Person to Icons.Default.Person
      }

      NavigationBarItem(
        selected = isSelected,
        onClick = { onTabSelected(tab) },
        icon = {
          Icon(
            imageVector = if (isSelected) selectedIcon else icon,
            contentDescription = tab.title
          )
        },
        label = {
          Text(
            text = tab.title,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
          )
        },
        colors = NavigationBarItemDefaults.colors(
          selectedIconColor = Color.White,
          selectedTextColor = BharatPurplePrimary,
          indicatorColor = BharatPurplePrimary,
          unselectedIconColor = BharatTextSecondary,
          unselectedTextColor = BharatTextSecondary
        ),
        modifier = Modifier.testTag(tab.testTag)
      )
    }
  }
}

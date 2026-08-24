package com.kks.bharatkirana.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kks.bharatkirana.data.model.CartItem
import com.kks.bharatkirana.data.model.PromoCode
import com.kks.bharatkirana.data.model.UserProfile
import com.kks.bharatkirana.ui.theme.BharatBackground
import com.kks.bharatkirana.ui.theme.BharatGreen
import com.kks.bharatkirana.ui.theme.BharatPurpleAccent
import com.kks.bharatkirana.ui.theme.BharatPurpleContainer
import com.kks.bharatkirana.ui.theme.BharatPurplePrimary
import com.kks.bharatkirana.ui.theme.BharatTextMuted
import com.kks.bharatkirana.ui.theme.BharatTextPrimary
import com.kks.bharatkirana.ui.theme.BharatTextSecondary

@Composable
fun CartScreen(
  userProfile: UserProfile,
  cartItems: List<CartItem>,
  onBackClick: () -> Unit,
  onUpdateQuantity: (String, String, Int) -> Unit,
  onCheckout: () -> Unit,
  onProfileClick: () -> Unit,
  onExploreProducts: () -> Unit,
  handlingFeeRupees: Int = 5,
  minOrderForFreeHandling: Int = 200,
  freeHandlingDiscount: Int = 15,
  appliedPromo: PromoCode? = null,
  promoStatusMessage: String? = null,
  onApplyPromo: (String) -> Unit = {},
  onClearPromo: () -> Unit = {},
  isCheckingOut: Boolean = false,
  modifier: Modifier = Modifier
) {
  val itemCount = cartItems.sumOf { it.quantity }
  val itemTotal = cartItems.sumOf { it.totalPrice }
  val discount = if (itemTotal > minOrderForFreeHandling) freeHandlingDiscount else 0
  val handlingFee = if (itemCount > 0) handlingFeeRupees else 0
  val promoDiscount = appliedPromo?.computeDiscount(itemTotal) ?: 0
  val finalTotal = (itemTotal + handlingFee - discount - promoDiscount).coerceAtLeast(0)

  Surface(
    modifier = modifier.fillMaxSize(),
    color = BharatBackground
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding()
    ) {
      // Header
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(Color.White)
          .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f)
        ) {
          IconButton(
            onClick = onBackClick,
            modifier = Modifier
              .size(36.dp)
              .testTag("cart_back_button")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back",
              tint = BharatTextPrimary
            )
          }
          Spacer(modifier = Modifier.width(4.dp))
          Column {
            Text(
              text = "Your Cart",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = BharatTextPrimary
            )
            Text(
              text = "$itemCount items in cart",
              style = MaterialTheme.typography.bodySmall,
              color = BharatTextSecondary
            )
          }
        }

        Box(
          modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(BharatPurplePrimary)
            .clickable(onClick = onProfileClick),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = userProfile.fullName.firstOrNull()?.toString() ?: "R",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
          )
        }
      }

      if (cartItems.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .weight(1f)
            .padding(24.dp),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
              imageVector = Icons.Default.ShoppingCart,
              contentDescription = "Empty Cart",
              tint = BharatPurpleAccent.copy(alpha = 0.5f),
              modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
              text = "Your cart is empty",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = BharatTextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "Explore fresh grains, dairy, and daily essentials from BreakQ.",
              style = MaterialTheme.typography.bodySmall,
              color = BharatTextSecondary,
              textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
              onClick = onExploreProducts,
              colors = ButtonDefaults.buttonColors(containerColor = BharatPurplePrimary),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.testTag("cart_explore_button")
            ) {
              Text("Start Shopping", fontWeight = FontWeight.Bold)
            }
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier
            .weight(1f)
            .padding(horizontal = 16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          item {
            Spacer(modifier = Modifier.height(4.dp))
            // Pickup details Card
            Card(
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
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(BharatPurplePrimary),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Default.Storefront,
                    contentDescription = "Store",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
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
                    text = userProfile.activeStoreAddress,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = BharatTextPrimary
                  )
                  Text(
                    text = "Ready in ~15 mins",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = BharatPurplePrimary
                  )
                }
              }
            }
          }

          // Cart Items List
          items(cartItems) { cartItem ->
            Card(
              shape = RoundedCornerShape(14.dp),
              colors = CardDefaults.cardColors(containerColor = Color.White),
              elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
              modifier = Modifier.testTag("cart_item_${cartItem.product.id}")
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Box(
                  modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF8FAFC)),
                  contentAlignment = Alignment.Center
                ) {
                  if (cartItem.product.localImageRes != null) {
                    Image(
                      painter = painterResource(id = cartItem.product.localImageRes),
                      contentDescription = cartItem.product.name,
                      modifier = Modifier.fillMaxSize(),
                      contentScale = ContentScale.Crop
                    )
                  } else {
                    Icon(
                      imageVector = Icons.Default.ShoppingCart,
                      contentDescription = null,
                      tint = BharatPurpleAccent,
                      modifier = Modifier.size(28.dp)
                    )
                  }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = cartItem.product.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = BharatTextPrimary,
                    maxLines = 1
                  )
                  Text(
                    text = cartItem.selectedWeight.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = BharatTextMuted
                  )
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = "₹${cartItem.selectedWeight.price}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = BharatTextPrimary
                  )
                }

                // Stepper
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier
                    .background(BharatPurpleContainer, RoundedCornerShape(8.dp))
                ) {
                  IconButton(
                    onClick = {
                      onUpdateQuantity(cartItem.product.id, cartItem.selectedWeight.label, -1)
                    },
                    modifier = Modifier.size(32.dp)
                  ) {
                    Icon(
                      imageVector = if (cartItem.quantity == 1) Icons.Default.DeleteOutline else Icons.Default.Remove,
                      contentDescription = "Decrease",
                      tint = BharatPurplePrimary,
                      modifier = Modifier.size(16.dp)
                    )
                  }
                  Text(
                    text = "${cartItem.quantity}",
                    fontWeight = FontWeight.Bold,
                    color = BharatPurplePrimary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 6.dp)
                  )
                  IconButton(
                    onClick = {
                      onUpdateQuantity(cartItem.product.id, cartItem.selectedWeight.label, 1)
                    },
                    modifier = Modifier.size(32.dp)
                  ) {
                    Icon(
                      imageVector = Icons.Default.Add,
                      contentDescription = "Increase",
                      tint = BharatPurplePrimary,
                      modifier = Modifier.size(16.dp)
                    )
                  }
                }
              }
            }
          }

          // Bill Details
          item {
            PromoCodeCard(
              appliedPromo = appliedPromo,
              promoDiscount = promoDiscount,
              statusMessage = promoStatusMessage,
              onApply = onApplyPromo,
              onClear = onClearPromo
            )
          }

          item {
            Card(
              shape = RoundedCornerShape(16.dp),
              colors = CardDefaults.cardColors(containerColor = Color.White),
              elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
              Column(modifier = Modifier.padding(16.dp)) {
                Text(
                  text = "Bill Details",
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                  color = BharatTextPrimary
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Text(text = "Item Total", color = BharatTextSecondary, fontSize = 14.sp)
                  Text(text = "₹$itemTotal", color = BharatTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Text(text = "Handling Fee", color = BharatTextSecondary, fontSize = 14.sp)
                  Text(text = "₹$handlingFee", color = BharatTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }

                if (discount > 0) {
                  Spacer(modifier = Modifier.height(8.dp))
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Text(text = "Store Discount Applied", color = BharatGreen, fontSize = 14.sp)
                    Text(text = "-₹$discount", color = BharatGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                  }
                }

                if (promoDiscount > 0 && appliedPromo != null) {
                  Spacer(modifier = Modifier.height(8.dp))
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Text(text = "Promo (${appliedPromo.code})", color = BharatGreen, fontSize = 14.sp)
                    Text(text = "-₹$promoDiscount", color = BharatGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                  }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFFF1F5F9))
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = "To Pay",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = BharatTextPrimary
                  )
                  Text(
                    text = "₹$finalTotal",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = BharatTextPrimary
                  )
                }
              }
            }
          }

          item {
            Spacer(modifier = Modifier.height(16.dp))
          }
        }

        // Sticky Checkout Footer
        Surface(
          color = Color.White,
          shadowElevation = 12.dp,
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Column {
              Text(
                text = "Total Amount",
                style = MaterialTheme.typography.labelSmall,
                color = BharatTextSecondary
              )
              Text(
                text = "₹$finalTotal",
                style = MaterialTheme.typography.titleLarge.copy(
                  fontWeight = FontWeight.ExtraBold,
                  fontSize = 20.sp
                ),
                color = BharatTextPrimary
              )
            }

            Button(
              onClick = onCheckout,
              enabled = !isCheckingOut,
              modifier = Modifier
                .height(48.dp)
                .testTag("cart_checkout_button"),
              colors = ButtonDefaults.buttonColors(containerColor = BharatPurplePrimary),
              shape = RoundedCornerShape(12.dp)
            ) {
              if (isCheckingOut) {
                CircularProgressIndicator(
                  color = Color.White,
                  modifier = Modifier.size(20.dp),
                  strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = "Processing...",
                  fontSize = 14.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color.White
                )
              } else {
                Text(
                  text = "Continue to Checkout",
                  fontSize = 15.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color.White
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                  contentDescription = null,
                  tint = Color.White,
                  modifier = Modifier.size(16.dp)
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PromoCodeCard(
  appliedPromo: PromoCode?,
  promoDiscount: Int,
  statusMessage: String?,
  onApply: (String) -> Unit,
  onClear: () -> Unit
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.LocalOffer, contentDescription = null, tint = BharatPurplePrimary, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Have a promo code?",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = BharatTextPrimary
        )
      }
      Spacer(modifier = Modifier.height(10.dp))

      if (appliedPromo != null) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(color = BharatGreen.copy(alpha = 0.12f), shape = RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = appliedPromo.code,
              fontWeight = FontWeight.ExtraBold,
              color = BharatGreen,
              fontSize = 14.sp
            )
            Text(
              text = "Saving ₹$promoDiscount",
              fontSize = 12.sp,
              color = BharatGreen
            )
          }
          TextButton(onClick = onClear) {
            Icon(Icons.Default.Close, contentDescription = "Remove", tint = BharatGreen, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Remove", color = BharatGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
          }
        }
      } else {
        var input by remember { mutableStateOf("") }
        Row(verticalAlignment = Alignment.CenterVertically) {
          OutlinedTextField(
            value = input,
            onValueChange = { input = it.uppercase() },
            placeholder = { Text("e.g. DIWALI30", color = BharatTextMuted, fontSize = 13.sp) },
            singleLine = true,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
              focusedTextColor = BharatTextPrimary,
              unfocusedTextColor = BharatTextPrimary,
              focusedBorderColor = BharatPurplePrimary
            )
          )
          Spacer(modifier = Modifier.width(8.dp))
          Button(
            onClick = { if (input.isNotBlank()) onApply(input); input = "" },
            enabled = input.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = BharatPurplePrimary),
            shape = RoundedCornerShape(12.dp)
          ) { Text("Apply", fontWeight = FontWeight.Bold, color = Color.White) }
        }
      }

      if (statusMessage != null) {
        Spacer(modifier = Modifier.height(6.dp))
        val isSuccess = statusMessage.contains("applied", ignoreCase = true)
        Text(
          text = statusMessage,
          color = if (isSuccess) BharatGreen else Color(0xFFDC2626),
          fontSize = 12.sp,
          fontWeight = FontWeight.Medium
        )
      }
    }
  }
}

package com.kks.bharatkirana.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kks.bharatkirana.R
import com.kks.bharatkirana.data.model.Shop
import com.kks.bharatkirana.data.model.isCurrentlyOpen
import com.kks.bharatkirana.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyShopsScreen(
  shops: List<Shop>,
  onShopClick: (Shop) -> Unit,
  onProfileClick: () -> Unit,
  onBackClick: () -> Unit,
  userInitial: String = "U",
  unreadNotificationCount: Int = 0,
  onNotificationsClick: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  var searchQuery by remember { mutableStateOf("") }
  val filters = listOf("All Shops", "Nearest", "Top Rated", "Groceries", "Organic")
  var selectedFilter by remember { mutableStateOf("All Shops") }
  var isMapView by remember { mutableStateOf(false) }
  
  // Distance filter: customer picks max radius to search within.
  val distanceOptions = listOf(1, 3, 5, 10)
  var selectedDistanceKm by remember { mutableIntStateOf(5) }

  // Filter Logic
  val filteredShops = shops.filter { shop ->
    // Shops with unknown distance ("---" or shops whose vendor never captured
    // lat/lng) are shown regardless of distance filter — better to include a valid
    // approved shop with a manual pin than to hide it because we lack coordinates.
    val distanceValue = shop.distance.substringBefore(" ").toDoubleOrNull()
    val matchesDistance = distanceValue == null || distanceValue <= selectedDistanceKm
    val matchesSearch = shop.name.contains(searchQuery, ignoreCase = true)
    val matchesFilter = when(selectedFilter) {
      "All Shops" -> true
      "Nearest" -> true 
      "Top Rated" -> shop.rating >= 4.5f
      "Groceries" -> shop.primaryCategory.equals("Grocery", ignoreCase = true)
      "Organic" -> shop.primaryCategory.equals("Produce", ignoreCase = true) || shop.primaryCategory.equals("Organic", ignoreCase = true)
      else -> true
    }
    // Only show approved shops on the customer side — pending/rejected/suspended
    // must never reach the marketplace listing.
    val isVisibleStatus = shop.status == com.kks.bharatkirana.data.model.VendorStatus.APPROVED
    isVisibleStatus && matchesDistance && matchesSearch && matchesFilter
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.LocationOn,
              contentDescription = null,
              tint = BharatPurplePrimary,
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
              Text(
                text = "Delivering to",
                style = MaterialTheme.typography.labelSmall,
                color = BharatTextSecondary
              )
              Text(
                text = "Your Neighborhood",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = BharatTextPrimary
              )
            }
          }
        },
        navigationIcon = {
          IconButton(onClick = onBackClick) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back",
              tint = BharatPurplePrimary
            )
          }
        },
        actions = {
          IconButton(onClick = { isMapView = !isMapView }) {
            Icon(
              imageVector = if (isMapView) Icons.Default.List else Icons.Default.Map,
              contentDescription = "Toggle View",
              tint = BharatPurplePrimary
            )
          }
          // Notification bell with unread badge (same behavior as HomeScreen).
          Box {
            IconButton(
              onClick = onNotificationsClick,
              modifier = Modifier.size(36.dp)
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
          // Round 6.1: circular gradient avatar with the user's initial (matches Home).
          val initial = userInitial.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "U"
          Box(
            modifier = Modifier
              .padding(end = 8.dp)
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
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
      )
    },
    modifier = modifier.fillMaxSize()
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .background(Color(0xFFF9FAFB))
        .padding(paddingValues)
    ) {
      // Search Bar
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        placeholder = { Text("Search nearby stores, groceries...", color = BharatTextMuted, fontSize = 14.sp) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = BharatTextSecondary) },
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedTextColor = BharatTextPrimary,
          unfocusedTextColor = BharatTextPrimary
        )
      )

      // Distance Filter Row
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Radius:",
          style = MaterialTheme.typography.labelMedium,
          color = BharatTextSecondary,
          modifier = Modifier.padding(end = 12.dp)
        )
        distanceOptions.forEach { km ->
          val isSelected = selectedDistanceKm == km
          Surface(
            onClick = { selectedDistanceKm = km },
            shape = RoundedCornerShape(8.dp),
            color = if (isSelected) BharatPurplePrimary else Color.White,
            border = BorderStroke(1.dp, if (isSelected) BharatPurplePrimary else Color(0xFFE5E7EB)),
            modifier = Modifier.padding(end = 8.dp)
          ) {
            Text(
              text = "${km}km",
              color = if (isSelected) Color.White else BharatTextPrimary,
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
          }
        }
      }

      // Other Filters
      LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        items(filters) { filter ->
          FilterChip(
            selected = selectedFilter == filter,
            onClick = { selectedFilter = filter },
            label = { Text(filter) },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = BharatPurplePrimary,
              selectedLabelColor = Color.White,
              containerColor = Color.White,
              labelColor = BharatTextSecondary
            ),
            border = FilterChipDefaults.filterChipBorder(
              enabled = true,
              selected = selectedFilter == filter,
              borderColor = Color(0xFFE5E7EB),
              selectedBorderColor = BharatPurplePrimary,
              borderWidth = 1.dp
            ),
            shape = RoundedCornerShape(12.dp)
          )
        }
      }

      // Shop List or Empty Message or Map View
      if (isMapView) {
        Box(
          modifier = Modifier.weight(1f).fillMaxWidth(),
          contentAlignment = Alignment.Center
        ) {
          Image(
            painter = painterResource(id = R.drawable.img_onboarding_delivery), // Placeholder map
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
          )
          // Simulation Markers
          filteredShops.forEach { shop ->
            Icon(
              imageVector = Icons.Default.LocationOn,
              contentDescription = null,
              tint = BharatPurplePrimary,
              modifier = Modifier
                .offset(x = ((-50..50).random()).dp, y = ((-50..50).random()).dp)
                .clickable { onShopClick(shop) }
            )
          }
        }
      } else if (filteredShops.isEmpty()) {
        Box(
          modifier = Modifier.weight(1f).fillMaxWidth(),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
              imageVector = Icons.Default.Storefront,
              contentDescription = null,
              tint = Color(0xFFCBD5E1),
              modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
              text = "No local shops under ${selectedDistanceKm}km",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = BharatTextPrimary
            )
            Text(
              text = "Try increasing the radius or checking another area.",
              style = MaterialTheme.typography.bodySmall,
              color = BharatTextSecondary,
              textAlign = TextAlign.Center,
              modifier = Modifier.padding(start = 40.dp, end = 40.dp, top = 4.dp)
            )
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
          verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
          items(filteredShops) { shop ->
            NearbyShopCard(shop = shop, onClick = { onShopClick(shop) })
          }
        }
      }
    }
  }
}

@Composable
fun NearbyShopCard(shop: Shop, onClick: () -> Unit) {
  Card(
    onClick = onClick,
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    modifier = Modifier.fillMaxWidth(),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
  ) {
    Column {
      Box(modifier = Modifier.fillMaxWidth().height(180.dp)) {
        if (shop.localImageRes != null) {
          Image(
            painter = painterResource(id = shop.localImageRes),
            contentDescription = shop.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
          )
        } else {
          Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F3FF)))
        }

        // Open/Closed badge (top-start): reads shop.isCurrentlyOpen() which combines
        // the manual accepting-orders toggle with the current time vs open/close hours.
        val open = shop.isCurrentlyOpen()
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = if (open) BharatGreen else Color(0xFFDC2626),
          modifier = Modifier.padding(12.dp).align(Alignment.TopStart)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color.White))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = if (open) "Open" else "Closed",
              color = Color.White,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold
            )
          }
        }

        // Rating Badge
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = Color.White,
          modifier = Modifier.padding(12.dp).align(Alignment.TopEnd)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFB800), modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = shop.rating.toString(), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            if (shop.ratingCount > 0) {
              Text(text = " (${shop.ratingCount})", fontSize = 11.sp, color = BharatTextSecondary)
            }
          }
        }
      }

      Column(modifier = Modifier.padding(16.dp)) {
        Text(
          text = shop.name,
          style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
          color = BharatTextPrimary
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = shop.primaryCategory,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = BharatPurplePrimary
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "• Fresh Produce & Daily Needs",
            style = MaterialTheme.typography.bodyMedium,
            color = BharatTextSecondary
          )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Schedule, contentDescription = null, tint = BharatPurplePrimary, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(text = shop.deliveryTime, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = BharatTextPrimary)
          
          Spacer(modifier = Modifier.width(16.dp))
          
          Icon(Icons.Default.LocationOn, contentDescription = null, tint = BharatTextSecondary, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(text = shop.distance, style = MaterialTheme.typography.bodySmall, color = BharatTextSecondary)
        }
      }
    }
  }
}

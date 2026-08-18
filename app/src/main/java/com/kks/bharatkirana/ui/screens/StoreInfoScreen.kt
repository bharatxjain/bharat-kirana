package com.kks.bharatkirana.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kks.bharatkirana.R
import com.kks.bharatkirana.ui.theme.BharatBackground
import com.kks.bharatkirana.ui.theme.BharatGreen
import com.kks.bharatkirana.ui.theme.BharatPurpleContainer
import com.kks.bharatkirana.ui.theme.BharatPurpleDark
import com.kks.bharatkirana.ui.theme.BharatPurplePrimary
import com.kks.bharatkirana.ui.theme.BharatTextPrimary
import com.kks.bharatkirana.ui.theme.BharatTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreInfoScreen(
  onBackClick: () -> Unit,
  onViewCatalog: () -> Unit,
  onOpenDirections: (String) -> Unit = { },
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val storePhone = "+91 9876543210"
  val storeAddress = "Banjara Hills Rd 12, Hyderabad, TS 500034"
  val storeHours = "7:00 AM – 10:30 PM"
  
  // Helper to open Google Maps
  val openMaps = {
    onOpenDirections(storeAddress)
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = "Store Information",
            style = MaterialTheme.typography.titleLarge.copy(
              fontWeight = FontWeight.Bold,
              fontSize = 20.sp
            ),
            color = BharatTextPrimary
          )
        },
        navigationIcon = {
          IconButton(onClick = onBackClick) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back",
              tint = BharatTextPrimary
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
      )
    },
    modifier = modifier
      .fillMaxSize()
      .background(BharatBackground)
      .statusBarsPadding()
      .navigationBarsPadding()
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      item { Spacer(modifier = Modifier.height(8.dp)) }

      // Map Snippet (Clickable)
      item {
        Card(
          onClick = openMaps,
          shape = RoundedCornerShape(24.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
          Box(modifier = Modifier.fillMaxSize()) {
            Image(
              painter = painterResource(id = R.drawable.img_onboarding_delivery), // Placeholder map image
              contentDescription = "Store Location Map",
              modifier = Modifier.fillMaxSize(),
              contentScale = ContentScale.Crop
            )
            Box(
              modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.05f))
            )
            // Centered Marker
            Icon(
              imageVector = Icons.Default.LocationOn,
              contentDescription = null,
              tint = BharatPurplePrimary,
              modifier = Modifier
                .size(48.dp)
                .align(Alignment.Center)
            )
            
            // "Tap to view on Maps" overlay
            Surface(
              color = Color.Black.copy(alpha = 0.6f),
              shape = RoundedCornerShape(50.dp),
              modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
            ) {
              Text(
                text = "Tap to view on Google Maps",
                color = Color.White,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
              )
            }
          }
        }
      }

      // Address Card
      item {
        StoreDetailCard(
          icon = Icons.Default.Storefront,
          title = "Bharat Kirana Store",
          subtitle = storeAddress,
          badge = "Open Now"
        )
      }

      // Hours Card
      item {
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White),
          border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.Default.Schedule, contentDescription = null, tint = BharatPurplePrimary)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(text = "Operating Hours", fontWeight = FontWeight.Bold, color = BharatTextPrimary)
              Text(text = storeHours, color = BharatTextSecondary, fontSize = 14.sp)
            }
          }
        }
      }

      // View Catalog CTA
      item {
        Button(
          onClick = onViewCatalog,
          modifier = Modifier.fillMaxWidth().height(56.dp),
          colors = ButtonDefaults.buttonColors(containerColor = BharatPurplePrimary),
          shape = RoundedCornerShape(16.dp)
        ) {
          Text(text = "View Product Catalog", fontWeight = FontWeight.Bold)
        }
      }

      // Action Buttons (Call & Directions)
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          // Call Store Button
          Button(
            onClick = {
              val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$storePhone"))
              context.startActivity(intent)
            },
            modifier = Modifier
              .weight(1f)
              .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BharatPurplePrimary),
            shape = RoundedCornerShape(16.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Call,
              contentDescription = null,
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Call Store",
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold
            )
          }

          // Directions Button
          OutlinedButton(
            onClick = openMaps,
            modifier = Modifier
              .weight(1f)
              .height(56.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = BharatPurplePrimary),
            border = BorderStroke(1.5.dp, BharatPurplePrimary),
            shape = RoundedCornerShape(16.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Directions,
              contentDescription = null,
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Directions",
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold
            )
          }
        }
      }

      item { Spacer(modifier = Modifier.height(24.dp)) }
    }
  }
}

@Composable
fun StoreDetailCard(
  icon: ImageVector,
  title: String,
  subtitle: String,
  badge: String
) {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier.padding(20.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(52.dp)
          .clip(CircleShape)
          .background(BharatPurpleContainer),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = BharatPurplePrimary,
          modifier = Modifier.size(28.dp)
        )
      }
      Spacer(modifier = Modifier.width(16.dp))
      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = BharatTextPrimary
          )
          Spacer(modifier = Modifier.width(8.dp))
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(6.dp))
              .background(BharatGreen.copy(alpha = 0.1f))
              .padding(horizontal = 6.dp, vertical = 2.dp)
          ) {
            Text(
              text = badge,
              color = BharatGreen,
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold
            )
          }
        }
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = BharatTextSecondary,
          modifier = Modifier.padding(top = 2.dp)
        )
      }
    }
  }
}

@Composable
fun ServiceInfoRow(
  icon: ImageVector,
  title: String,
  description: String
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .background(Color.White)
      .padding(16.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .size(40.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(Color(0xFFF8FAFC)),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = BharatPurplePrimary,
        modifier = Modifier.size(22.dp)
      )
    }
    Spacer(modifier = Modifier.width(16.dp))
    Column {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
        color = BharatTextPrimary
      )
      Text(
        text = description,
        style = MaterialTheme.typography.bodySmall,
        color = BharatTextSecondary
      )
    }
  }
}

package com.kks.bharatkirana.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kks.bharatkirana.ui.theme.*

@Composable
fun CustomerOnboardingScreen(
  onComplete: () -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    modifier = modifier.fillMaxSize(),
    color = Color.White
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Text(
        text = "Setting Up Your Profile",
        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        color = BharatTextPrimary
      )
      
      Spacer(modifier = Modifier.height(32.dp))
      
      // Profile Photo
      Box(
        modifier = Modifier
          .size(120.dp)
          .clip(CircleShape)
          .background(Color(0xFFF3F4F6))
          .clickable { /* Photo Picker */ },
        contentAlignment = Alignment.Center
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = BharatPurplePrimary)
          Text("Photo", fontSize = 11.sp, color = BharatTextSecondary)
        }
      }
      
      Spacer(modifier = Modifier.height(12.dp))
      Text(
        text = "Add a profile photo (Optional)",
        style = MaterialTheme.typography.bodySmall,
        color = BharatTextSecondary
      )

      Spacer(modifier = Modifier.height(48.dp))

      // Location Permission Simulation
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BharatPurpleContainer),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.padding(16.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(Icons.Default.LocationOn, contentDescription = null, tint = BharatPurplePrimary)
          Spacer(modifier = Modifier.width(12.dp))
          Column {
            Text("Enable Location Access", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("To find shops in your neighborhood.", fontSize = 12.sp, color = BharatTextSecondary)
          }
        }
      }

      Spacer(modifier = Modifier.height(48.dp))

      Button(
        onClick = onComplete,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = BharatPurplePrimary),
        shape = RoundedCornerShape(16.dp)
      ) {
        Text("Start Shopping", fontWeight = FontWeight.Bold, fontSize = 16.sp)
      }
    }
  }
}

package com.kks.bharatkirana.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.kks.bharatkirana.R
import com.kks.bharatkirana.data.model.AuthPath
import com.kks.bharatkirana.data.model.UserProfile
import com.kks.bharatkirana.ui.theme.*

@Composable
fun CompleteProfileScreen(
  userProfile: UserProfile,
  onProfileCompleted: (String, String, String, String) -> Unit,
  modifier: Modifier = Modifier
) {
  var fullName by remember { mutableStateOf(userProfile.fullName) }
  var email by remember { mutableStateOf(userProfile.email) }
  var mobileNumber by remember { mutableStateOf(userProfile.mobileNumber) }
  var address by remember { mutableStateOf(userProfile.address) }
  
  var statusMessage by remember { mutableStateOf<String?>(null) }

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
      // Top Header
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = Icons.Default.Storefront,
          contentDescription = null,
          tint = BharatPurplePrimary,
          modifier = Modifier.size(26.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
          text = "BreakQ",
          style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
          color = BharatPurplePrimary
        )
      }

      Column(
        modifier = Modifier
          .weight(1f)
          .verticalScroll(rememberScrollState())
      ) {
        // Hero Image
        Box(modifier = Modifier.fillMaxWidth().height(160.dp)) {
          Image(
            painter = painterResource(id = R.drawable.img_welcome_hero),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
          )
          Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)))
          Text(
            text = "Welcome",
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 28.sp),
            color = Color.White,
            modifier = Modifier.align(Alignment.BottomStart).padding(20.dp)
          )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
          Text(
            text = "Complete your profile",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp),
            color = BharatTextPrimary
          )

          Spacer(modifier = Modifier.height(6.dp))

          Text(
            text = "Please provide your details to continue to the marketplace.",
            style = MaterialTheme.typography.bodyMedium,
            color = BharatTextSecondary
          )

          Spacer(modifier = Modifier.height(20.dp))
          
          if (statusMessage != null) {
            Text(text = statusMessage!!, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp))
          }

          AuthTextFieldSimple(
            value = fullName,
            onValueChange = { fullName = it },
            label = "Full Name",
            placeholder = "John Doe",
            icon = Icons.Default.Person
          )
          
          Spacer(modifier = Modifier.height(16.dp))
          
          AuthTextFieldSimple(
            value = email,
            onValueChange = { email = it },
            label = "Email Address",
            placeholder = "john@example.com",
            icon = Icons.Default.Email
          )
          
          Spacer(modifier = Modifier.height(16.dp))

          Text(text = "Mobile Number", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = BharatTextPrimary)
          Spacer(modifier = Modifier.height(6.dp))
          Row(modifier = Modifier.fillMaxWidth()) {
            Box(
              modifier = Modifier
                .height(56.dp)
                .width(64.dp)
                .border(1.dp, Color(0xFFD1D5DB), RoundedCornerShape(12.dp))
                .background(Color(0xFFF9FAFB), RoundedCornerShape(12.dp)),
              contentAlignment = Alignment.Center
            ) {
              Text(text = "+91", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = BharatTextPrimary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(
              value = mobileNumber,
              onValueChange = { mobileNumber = it },
              placeholder = { Text("98765 43210", color = BharatTextMuted) },
              singleLine = true,
              modifier = Modifier.weight(1f),
              shape = RoundedCornerShape(12.dp),
              colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = BharatTextPrimary,
                unfocusedTextColor = BharatTextPrimary,
                focusedBorderColor = BharatPurplePrimary
              )
            )
          }

          Spacer(modifier = Modifier.height(16.dp))
          
          Text(text = "Delivery Address", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = BharatTextPrimary)
          Spacer(modifier = Modifier.height(6.dp))
          OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            placeholder = { Text("Flat No, Street...", color = BharatTextMuted) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
              focusedTextColor = BharatTextPrimary,
              unfocusedTextColor = BharatTextPrimary,
              focusedBorderColor = BharatPurplePrimary
            )
          )

          Spacer(modifier = Modifier.height(24.dp))
        }
      }

      // Bottom Sticky Button
      Surface(
        color = Color.White,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
          Button(
            onClick = { 
              if (fullName.isBlank() || email.isBlank() || mobileNumber.isBlank() || address.isBlank()) {
                statusMessage = "All fields are required"
              } else {
                onProfileCompleted(fullName, email, mobileNumber, address)
              }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BharatPurplePrimary),
            shape = RoundedCornerShape(14.dp)
          ) {
            Text("Complete Setup", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
          }
        }
      }
    }
  }
}

@Composable
fun AuthTextFieldSimple(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
  placeholder: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Text(text = label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = BharatTextPrimary)
    Spacer(modifier = Modifier.height(6.dp))
    OutlinedTextField(
      value = value,
      onValueChange = onValueChange,
      placeholder = { Text(placeholder, color = BharatTextMuted) },
      leadingIcon = { Icon(imageVector = icon, contentDescription = null, tint = BharatTextSecondary, modifier = Modifier.size(18.dp)) },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(12.dp),
      colors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = BharatTextPrimary,
        unfocusedTextColor = BharatTextPrimary,
        focusedBorderColor = BharatPurplePrimary
      )
    )
  }
}

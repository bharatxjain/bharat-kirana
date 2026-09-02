package com.kks.bharatkirana.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kks.bharatkirana.data.model.UserProfile
import com.kks.bharatkirana.ui.theme.BharatBackground
import com.kks.bharatkirana.ui.theme.BharatPurplePrimary
import com.kks.bharatkirana.ui.theme.BharatTextMuted
import com.kks.bharatkirana.ui.theme.BharatTextPrimary
import com.kks.bharatkirana.ui.theme.BharatTextSecondary

@Composable
fun EditProfileScreen(
  userProfile: UserProfile,
  syncPending: Boolean,
  onBackClick: () -> Unit,
  onSave: (name: String, email: String, mobile: String, address: String) -> Unit,
  modifier: Modifier = Modifier
) {
  var name by remember { mutableStateOf(userProfile.fullName) }
  var mobile by remember { mutableStateOf(userProfile.mobileNumber) }
  var address by remember { mutableStateOf(userProfile.address) }

  val nameChanged = name.trim() != userProfile.fullName
  val mobileChanged = mobile.trim() != userProfile.mobileNumber
  val addressChanged = address.trim() != userProfile.address
  val hasChanges = nameChanged || mobileChanged || addressChanged
  val canSave = name.isNotBlank() && mobile.length == 10 && (hasChanges || syncPending)

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(BharatBackground)
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
    ) {
      Surface(color = Color.White, shadowElevation = 1.dp) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(onClick = onBackClick) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = BharatTextPrimary)
          }
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = "Edit Profile",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = BharatTextPrimary
          )
        }
      }
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Full name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
          colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = BharatTextPrimary,
            unfocusedTextColor = BharatTextPrimary,
            focusedBorderColor = BharatPurplePrimary
          )
        )
        OutlinedTextField(
          value = mobile,
          onValueChange = { input -> mobile = input.filter { it.isDigit() }.take(10) },
          label = { Text("Mobile number") },
          leadingIcon = { Text("+91 ", color = BharatTextSecondary, fontWeight = FontWeight.Bold) },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
          modifier = Modifier.fillMaxWidth(),
          colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = BharatTextPrimary,
            unfocusedTextColor = BharatTextPrimary,
            focusedBorderColor = BharatPurplePrimary
          )
        )
        // The login identity can't be changed from here — surfaced disabled
        // so the user knows why it isn't editable.
        OutlinedTextField(
          value = userProfile.email,
          onValueChange = {},
          label = { Text("Email (locked)") },
          singleLine = true,
          enabled = false,
          trailingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = BharatTextMuted) },
          modifier = Modifier.fillMaxWidth(),
          colors = OutlinedTextFieldDefaults.colors(
            disabledTextColor = BharatTextSecondary,
            disabledBorderColor = Color(0xFFE5E7EB),
            disabledLabelColor = BharatTextMuted
          )
        )
        OutlinedTextField(
          value = address,
          onValueChange = { address = it },
          label = { Text("Address") },
          minLines = 2,
          modifier = Modifier.fillMaxWidth(),
          colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = BharatTextPrimary,
            unfocusedTextColor = BharatTextPrimary,
            focusedBorderColor = BharatPurplePrimary
          )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End
        ) {
          TextButton(onClick = onBackClick) {
            Text("Cancel", color = BharatTextSecondary, fontWeight = FontWeight.SemiBold)
          }
          Spacer(modifier = Modifier.width(8.dp))
          Button(
            onClick = {
              onSave(name.trim(), userProfile.email, mobile.trim(), address.trim())
              onBackClick()
            },
            enabled = canSave,
            colors = ButtonDefaults.buttonColors(containerColor = BharatPurplePrimary),
            shape = RoundedCornerShape(10.dp)
          ) {
            Text("Save Changes", color = Color.White, fontWeight = FontWeight.Bold)
          }
        }
        Text(
          text = "Your email is your login identity and can't be changed here.",
          fontSize = 11.sp,
          color = BharatTextMuted
        )
      }
      Spacer(modifier = Modifier.navigationBarsPadding())
    }
  }
}

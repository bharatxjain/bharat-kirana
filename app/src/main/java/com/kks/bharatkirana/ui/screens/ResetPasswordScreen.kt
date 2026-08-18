package com.kks.bharatkirana.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kks.bharatkirana.ui.theme.BharatPurplePrimary
import com.kks.bharatkirana.ui.theme.BharatTextPrimary
import com.kks.bharatkirana.ui.theme.BharatTextSecondary

@Composable
fun ResetPasswordScreen(
  accessToken: String,
  onResetPassword: (String, String) -> Unit,
  modifier: Modifier = Modifier,
  isLoading: Boolean = false,
  statusMessage: String? = null
) {
  var newPassword by remember { mutableStateOf("") }
  var confirmPassword by remember { mutableStateOf("") }
  var passwordVisible by remember { mutableStateOf(false) }
  var errorMsg by remember { mutableStateOf<String?>(null) }

  Surface(
    modifier = modifier.fillMaxSize(),
    color = Color(0xFFF9F6FE)
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(16.dp),
      contentAlignment = Alignment.Center
    ) {
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(
            text = "Create New Password",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = BharatTextPrimary
          )
          
          Spacer(modifier = Modifier.height(8.dp))
          
          Text(
            text = "Please enter your new secure password below.",
            style = MaterialTheme.typography.bodySmall,
            color = BharatTextSecondary,
            textAlign = TextAlign.Center
          )

          Spacer(modifier = Modifier.height(24.dp))

          OutlinedTextField(
            value = newPassword,
            onValueChange = { newPassword = it },
            label = { Text("New Password") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = BharatTextSecondary) },
            trailingIcon = {
              IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(
                  imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                  contentDescription = null,
                  tint = BharatTextSecondary
                )
              }
            },
            visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
              focusedTextColor = BharatTextPrimary,
              unfocusedTextColor = BharatTextPrimary,
              focusedBorderColor = BharatPurplePrimary,
              unfocusedBorderColor = Color(0xFFE5E7EB)
            )
          )

          Spacer(modifier = Modifier.height(12.dp))

          OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("Confirm Password") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = BharatTextSecondary) },
            visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
              focusedTextColor = BharatTextPrimary,
              unfocusedTextColor = BharatTextPrimary,
              focusedBorderColor = BharatPurplePrimary,
              unfocusedBorderColor = Color(0xFFE5E7EB)
            )
          )

          if (statusMessage != null || errorMsg != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
              text = statusMessage ?: errorMsg ?: "",
              color = if (statusMessage?.contains("success", true) == true) Color(0xFF059669) else Color.Red,
              fontSize = 12.sp,
              textAlign = TextAlign.Center
            )
          }

          Spacer(modifier = Modifier.height(24.dp))

          Button(
            onClick = {
              if (newPassword != confirmPassword) {
                errorMsg = "Passwords do not match"
                return@Button
              }
              onResetPassword(accessToken, newPassword)
            },
            enabled = !isLoading && newPassword.length >= 6,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BharatPurplePrimary),
            shape = RoundedCornerShape(14.dp)
          ) {
            if (isLoading) {
              CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
              Text("Reset Password", fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }
  }
}

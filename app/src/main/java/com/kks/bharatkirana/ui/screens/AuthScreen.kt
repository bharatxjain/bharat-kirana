package com.kks.bharatkirana.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kks.bharatkirana.data.model.AuthPath
import com.kks.bharatkirana.data.model.UserRole
import com.kks.bharatkirana.ui.theme.*

@Composable
fun AuthScreen(
  onAuthSuccess: (String, UserRole, AuthPath) -> Unit,
  initialEmail: String = "",
  onLogin: (String, String, (Boolean, String) -> Unit) -> Unit,
  onSignup: (String, String, String, String, String, (Boolean, String) -> Unit) -> Unit,
  onSendOtp: (String, (Boolean, String) -> Unit) -> Unit,
  onVerifyOtp: (String, String, (Boolean, String) -> Unit) -> Unit,
  onForgotPassword: (String, (Boolean, String) -> Unit) -> Unit = { _, _ -> },
  isLoading: Boolean = false,
  statusMessage: String? = null,
  onPrivacyPolicyClick: () -> Unit = {},
  onTermsClick: () -> Unit = {},
  onGoogleSignIn: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  var selectedTab by remember { mutableIntStateOf(0) } // 0: Login, 1: Signup, 2: OTP Verification, 3: Forgot Password
  var authRole by remember { mutableStateOf(UserRole.CUSTOMER) }
  var signupRoleChosen by remember { mutableStateOf(false) }
  var authPath by remember { mutableStateOf(AuthPath.EMAIL) }
  
  // Input States
  var emailInput by remember { mutableStateOf(initialEmail) }
  var passwordInput by remember { mutableStateOf("") }
  var confirmPasswordInput by remember { mutableStateOf("") }
  var nameInput by remember { mutableStateOf("") }
  var mobileInput by remember { mutableStateOf("") }
  var otpInput by remember { mutableStateOf("") }

  var passwordVisible by remember { mutableStateOf(false) }
  var localStatusMessage by remember { mutableStateOf<String?>(null) }
  var isLocalLoading by remember { mutableStateOf(false) }

  val effectiveLoading = isLoading || isLocalLoading
  val effectiveStatus = statusMessage ?: localStatusMessage

  Surface(
    modifier = modifier.fillMaxSize(),
    color = Color(0xFFF9F6FE)
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding()
        .padding(16.dp),
      contentAlignment = Alignment.Center
    ) {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 12.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Box(
            modifier = Modifier
              .size(60.dp)
              .clip(CircleShape)
              .background(BharatPurplePrimary),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.Storefront,
              contentDescription = "BreakQ",
              tint = Color.White,
              modifier = Modifier.size(32.dp)
            )
          }

          Spacer(modifier = Modifier.height(14.dp))

          Text(
            text = when (selectedTab) {
              2 -> "Verify Your Identity"
              3 -> "Reset Your Password"
              else -> "Login / Signup"
            },
            style = MaterialTheme.typography.headlineSmall.copy(
              fontWeight = FontWeight.Bold,
              fontSize = 20.sp
            ),
            color = BharatTextPrimary,
            textAlign = TextAlign.Center
          )

          Spacer(modifier = Modifier.height(4.dp))

          Text(
            text = when (selectedTab) {
              2 -> "Enter 6-digit verification code"
              3 -> "Enter your email to receive a reset link"
              else -> "Access your neighborhood marketplace"
            },
            style = MaterialTheme.typography.bodySmall,
            color = BharatTextSecondary,
            textAlign = TextAlign.Center
          )

          Spacer(modifier = Modifier.height(20.dp))

          // Segmented Tabs (only if not in OTP or Forgot Password mode)
          if (selectedTab < 2) {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = Color(0xFFF5F3FF),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(4.dp)
              ) {
                listOf("Login", "Signup").forEachIndexed { index, title ->
                  Surface(
                    onClick = { 
                      selectedTab = index 
                      localStatusMessage = null
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = if (selectedTab == index) Color.White else Color.Transparent,
                    shadowElevation = if (selectedTab == index) 2.dp else 0.dp,
                    modifier = Modifier.weight(1f)
                  ) {
                    Box(
                      modifier = Modifier.padding(vertical = 10.dp),
                      contentAlignment = Alignment.Center
                    ) {
                      Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (selectedTab == index) BharatPurplePrimary else BharatTextSecondary
                      )
                    }
                  }
                }
              }
            }
            Spacer(modifier = Modifier.height(20.dp))
          }

          // Status message display
          // Hidden while a request is in flight — the button already shows a spinner,
          // so surfacing "Creating account..." as a red banner is confusing.
          // Also hidden for any message ending with "..." in case the loading
          // flag races the message assignment on state clear.
          AnimatedVisibility(visible = effectiveStatus != null && !effectiveLoading && effectiveStatus?.endsWith("…") != true && effectiveStatus?.endsWith("...") != true) {
            effectiveStatus?.let { msg ->
              // A message is treated as positive if it announces an outcome the
              // user wants to see. Hard-coded phrase list rather than string
              // matching on "success" alone because messages like "Welcome back!"
              // and "Profile saved." were rendering red.
              val isPositive = listOf(
                "success", "check your", "welcome", "account created",
                "profile saved", "reset link sent", "otp sent",
                "password updated", "logged out", "deleted successfully"
              ).any { msg.contains(it, ignoreCase = true) }
              Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isPositive) Color(0xFFECFDF5) else Color(0xFFFEF2F2),
                border = BorderStroke(1.dp, if (isPositive) BharatGreen else Color(0xFFFCA5A5)),
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(bottom = 16.dp)
              ) {
                Row(
                  modifier = Modifier.padding(10.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(
                    imageVector = if (isPositive) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isPositive) BharatGreen else Color(0xFFDC2626),
                    modifier = Modifier.size(16.dp)
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = msg,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isPositive) Color(0xFF065F46) else Color(0xFF991B1B)
                  )
                }
              }
            }
          }

          // Form Content
          Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            if (selectedTab == 0) {
              // LOGIN FORM
              AuthTextField(
                value = emailInput,
                onValueChange = { emailInput = it },
                label = "Email Address",
                placeholder = "user@example.com",
                icon = Icons.Default.Email,
                keyboardType = KeyboardType.Email
              )
              
              AuthTextField(
                value = passwordInput,
                onValueChange = { passwordInput = it },
                label = "Password",
                placeholder = "Enter your password",
                icon = Icons.Default.Lock,
                isPassword = true,
                passwordVisible = passwordVisible,
                onToggleVisibility = { passwordVisible = !passwordVisible }
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
              ) {
                Text(
                  text = "Forgot Password?",
                  fontSize = 12.sp,
                  color = BharatPurplePrimary,
                  fontWeight = FontWeight.Medium,
                  modifier = Modifier
                    .clickable { 
                      selectedTab = 3 
                      localStatusMessage = null
                    }
                    .padding(vertical = 4.dp, horizontal = 8.dp)
                )
              }
              
              Spacer(modifier = Modifier.height(4.dp))
              
              Button(
                onClick = {
                  isLocalLoading = true
                  onLogin(emailInput.trim(), passwordInput) { success, msg ->
                    isLocalLoading = false
                    localStatusMessage = msg
                    if (success) onAuthSuccess(emailInput.trim(), authRole, AuthPath.EMAIL)
                  }
                },
                enabled = !effectiveLoading && emailInput.isNotBlank() && passwordInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BharatPurplePrimary),
                shape = RoundedCornerShape(14.dp)
              ) {
                if (effectiveLoading) {
                  CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                  Text(
                    text = "Login to Account",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                  )
                }
              }
            } else if (selectedTab == 1 && !signupRoleChosen) {
              // SIGNUP — role picker (asked FIRST, before the form)
              SignupRolePicker(
                onChoose = { role ->
                  authRole = role
                  signupRoleChosen = true
                  localStatusMessage = null
                }
              )
            } else if (selectedTab == 1) {
              // SIGNUP FORM
              SignupRolePill(
                role = authRole,
                onChange = { signupRoleChosen = false }
              )
              AuthTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                label = "Full Name",
                placeholder = "John Doe",
                icon = Icons.Default.Person
              )
              
              AuthTextField(
                value = mobileInput,
                onValueChange = { if (it.length <= 10 && it.all { c -> c.isDigit() }) mobileInput = it },
                label = "Mobile Number",
                placeholder = "10-digit number",
                icon = Icons.Default.Phone,
                keyboardType = KeyboardType.Phone
              )
              
              AuthTextField(
                value = emailInput,
                onValueChange = { emailInput = it },
                label = "Email Address",
                placeholder = "user@example.com",
                icon = Icons.Default.Email,
                keyboardType = KeyboardType.Email
              )

              AuthTextField(
                value = passwordInput,
                onValueChange = { passwordInput = it },
                label = "Create Password",
                placeholder = "Min 6 characters",
                icon = Icons.Default.Lock,
                isPassword = true,
                passwordVisible = passwordVisible,
                onToggleVisibility = { passwordVisible = !passwordVisible }
              )
              
              AuthTextField(
                value = confirmPasswordInput,
                onValueChange = { confirmPasswordInput = it },
                label = "Confirm Password",
                placeholder = "Repeat password",
                icon = Icons.Default.CheckCircle,
                isPassword = true,
                passwordVisible = passwordVisible,
                onToggleVisibility = { passwordVisible = !passwordVisible }
              )
              
              Spacer(modifier = Modifier.height(4.dp))
              
              Button(
                onClick = {
                  if (passwordInput != confirmPasswordInput) {
                    localStatusMessage = "Passwords do not match"
                    return@Button
                  }
                  if (mobileInput.length != 10) {
                    localStatusMessage = "Please enter a valid 10-digit mobile number"
                    return@Button
                  }
                  isLocalLoading = true
                  onSignup(nameInput, emailInput.trim(), mobileInput, "", passwordInput) { success, msg ->
                    isLocalLoading = false
                    localStatusMessage = msg
                    if (success) {
                      selectedTab = 2 
                    }
                  }
                },
                enabled = !effectiveLoading && emailInput.isNotBlank() && passwordInput.length >= 6 && nameInput.isNotBlank() && mobileInput.length == 10,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BharatPurplePrimary),
                shape = RoundedCornerShape(14.dp)
              ) {
                if (effectiveLoading) {
                  CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                  Text(
                    text = "Create Account",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                  )
                }
              }
            } else if (selectedTab == 2) {
              // OTP VERIFICATION (Email)
              AuthTextField(
                value = otpInput,
                onValueChange = { if (it.length <= 6) otpInput = it },
                label = "6-Digit OTP Code",
                placeholder = "Enter code",
                icon = Icons.Default.Key,
                keyboardType = KeyboardType.Number
              )
              
              Button(
                onClick = {
                  isLocalLoading = true
                  onVerifyOtp(emailInput.trim(), otpInput.trim()) { success, msg ->
                    isLocalLoading = false
                    localStatusMessage = msg
                    if (success) onAuthSuccess(emailInput.trim(), authRole, AuthPath.EMAIL)
                  }
                },
                enabled = !effectiveLoading && otpInput.length == 6,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BharatGreen),
                shape = RoundedCornerShape(14.dp)
              ) {
                if (effectiveLoading) {
                  CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                  Text("Verify & Continue", fontWeight = FontWeight.Bold, color = Color.White)
                }
              }
              
              Text(
                text = "Resend OTP",
                fontSize = 12.sp,
                color = BharatPurplePrimary,
                modifier = Modifier.clickable {
                   onSendOtp(emailInput.trim()) { _, msg -> localStatusMessage = msg }
                }.padding(8.dp)
              )
              
              TextButton(onClick = { selectedTab = 1 }) {
                Text("Go back to Signup", fontSize = 12.sp, color = BharatTextSecondary)
              }
            } else if (selectedTab == 3) {
              // FORGOT PASSWORD
              AuthTextField(
                value = emailInput,
                onValueChange = { emailInput = it },
                label = "Email Address",
                placeholder = "user@example.com",
                icon = Icons.Default.Email,
                keyboardType = KeyboardType.Email
              )

              Spacer(modifier = Modifier.height(8.dp))

              Button(
                onClick = {
                  isLocalLoading = true
                  onForgotPassword(emailInput.trim()) { success, msg ->
                    isLocalLoading = false
                    localStatusMessage = msg
                  }
                },
                enabled = !effectiveLoading && emailInput.isNotBlank(),
                modifier = Modifier
                  .fillMaxWidth()
                  .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BharatPurplePrimary),
                shape = RoundedCornerShape(14.dp)
              ) {
                if (effectiveLoading) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                  )
                } else {
                  Text("Send Reset Link", fontWeight = FontWeight.Bold, color = Color.White)
                }
              }

              TextButton(onClick = { selectedTab = 0 }) {
                Text("Back to Login", fontSize = 12.sp, color = BharatTextSecondary)
              }
            }
          }

          // Round 9: Google lives here now instead of on a separate gate screen.
          // Hidden during OTP entry / password reset where it makes no sense.
          if (selectedTab == 0 || selectedTab == 1) {
            Spacer(modifier = Modifier.height(20.dp))
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.fillMaxWidth()
            ) {
              HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE5E7EB))
              Text(
                text = "OR",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = BharatTextMuted,
                modifier = Modifier.padding(horizontal = 12.dp)
              )
              HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE5E7EB))
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
              onClick = {
                authPath = AuthPath.GOOGLE
                onGoogleSignIn()
              },
              enabled = !effectiveLoading,
              modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("google_sign_in_button"),
              shape = RoundedCornerShape(14.dp),
              border = BorderStroke(1.dp, Color(0xFFD1D5DB)),
              colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White)
            ) {
              Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = null,
                tint = Color(0xFF4285F4),
                modifier = Modifier.size(20.dp)
              )
              Spacer(modifier = Modifier.width(10.dp))
              Text(
                text = "Continue with Google",
                color = BharatTextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
              )
            }
          }

          Spacer(modifier = Modifier.height(24.dp))

          // Footer Links
          Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Text(
              text = "By continuing, you agree to our",
              style = MaterialTheme.typography.bodySmall,
              color = BharatTextMuted
            )
            Row {
              Text(
                text = "Terms",
                color = BharatPurplePrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                modifier = Modifier.clickable { onTermsClick() }.padding(4.dp)
              )
              Text("and", color = BharatTextMuted, fontSize = 11.sp, modifier = Modifier.padding(vertical = 4.dp))
              Text(
                text = "Privacy Policy",
                color = BharatPurplePrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                modifier = Modifier.clickable { onPrivacyPolicyClick() }.padding(4.dp)
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun AuthTextField(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
  placeholder: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  isPassword: Boolean = false,
  passwordVisible: Boolean = false,
  onToggleVisibility: () -> Unit = {},
  keyboardType: KeyboardType = KeyboardType.Text
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Text(
      text = label,
      fontWeight = FontWeight.SemiBold,
      fontSize = 12.sp,
      color = BharatTextPrimary,
      modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
    )
    OutlinedTextField(
      value = value,
      onValueChange = onValueChange,
      placeholder = { Text(placeholder, color = BharatTextMuted, fontSize = 13.sp) },
      leadingIcon = { Icon(imageVector = icon, contentDescription = null, tint = BharatTextSecondary, modifier = Modifier.size(18.dp)) },
      trailingIcon = if (isPassword) {
        {
          IconButton(onClick = onToggleVisibility) {
            Icon(
              imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
              contentDescription = null,
              tint = BharatTextSecondary
            )
          }
        }
      } else null,
      visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
      singleLine = true,
      keyboardOptions = KeyboardOptions(
        keyboardType = keyboardType,
        imeAction = ImeAction.Next
      ),
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(12.dp),
      colors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = BharatTextPrimary,
        unfocusedTextColor = BharatTextPrimary,
        focusedBorderColor = BharatPurplePrimary,
        unfocusedBorderColor = Color(0xFFE5E7EB)
      )
    )
  }
}

@Composable
fun TextButton(onClick: () -> Unit, content: @Composable () -> Unit) {
  Box(modifier = Modifier.clickable { onClick() }.padding(8.dp)) {
    content()
  }
}

@Composable
private fun SignupRolePicker(onChoose: (UserRole) -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(
      text = "How will you use BreakQ?",
      style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
      color = BharatTextPrimary
    )
    Text(
      text = "Pick one to continue. You can't change this later without a new account.",
      style = MaterialTheme.typography.bodySmall,
      color = BharatTextSecondary
    )
    SignupRoleOptionCard(
      icon = Icons.Default.ShoppingBag,
      title = "I'm a Customer",
      subtitle = "Order groceries from nearby kirana shops for pickup.",
      onClick = { onChoose(UserRole.CUSTOMER) }
    )
    SignupRoleOptionCard(
      icon = Icons.Default.Storefront,
      title = "I'm a Shop Owner",
      subtitle = "List your shop and receive orders from customers.",
      onClick = { onChoose(UserRole.VENDOR) }
    )
  }
}

@Composable
private fun SignupRoleOptionCard(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  subtitle: String,
  onClick: () -> Unit
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(14.dp),
    color = Color.White,
    border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(44.dp)
          .clip(CircleShape)
          .background(Color(0xFFF5F3FF)),
        contentAlignment = Alignment.Center
      ) {
        Icon(icon, contentDescription = null, tint = BharatPurplePrimary, modifier = Modifier.size(22.dp))
      }
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(title, fontWeight = FontWeight.Bold, color = BharatTextPrimary, fontSize = 14.sp)
        Text(subtitle, fontSize = 12.sp, color = BharatTextSecondary)
      }
      Icon(
        imageVector = Icons.Default.ChevronRight,
        contentDescription = null,
        tint = BharatPurplePrimary
      )
    }
  }
}

@Composable
private fun SignupRolePill(role: UserRole, onChange: () -> Unit) {
  Surface(
    shape = RoundedCornerShape(10.dp),
    color = Color(0xFFF5F3FF),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        imageVector = if (role == UserRole.VENDOR) Icons.Default.Storefront else Icons.Default.ShoppingBag,
        contentDescription = null,
        tint = BharatPurplePrimary,
        modifier = Modifier.size(16.dp)
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = "Signing up as ${role.label}",
        fontSize = 12.sp,
        color = BharatTextPrimary,
        modifier = Modifier.weight(1f)
      )
      Text(
        text = "Change",
        color = BharatPurplePrimary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.clickable(onClick = onChange).padding(4.dp)
      )
    }
  }
}

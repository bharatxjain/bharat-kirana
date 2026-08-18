package com.kks.bharatkirana.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kks.bharatkirana.data.model.UserRole
import com.kks.bharatkirana.ui.theme.*

@Composable
fun RoleSelectionScreen(
  onRoleSelected: (UserRole) -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    modifier = modifier.fillMaxSize(),
    color = Color(0xFFF9F6FE)
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Text(
        text = "How will you use Bharat Kirana?",
        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        color = BharatTextPrimary,
        textAlign = TextAlign.Center
      )
      Text(
        text = "Choose your role to get started",
        style = MaterialTheme.typography.bodyMedium,
        color = BharatTextSecondary,
        modifier = Modifier.padding(top = 8.dp)
      )

      Spacer(modifier = Modifier.height(48.dp))

      RoleCard(
        title = "I'm a Customer",
        description = "Find and order from local shops near you.",
        icon = Icons.Default.Person,
        color = BharatPurplePrimary,
        onClick = { onRoleSelected(UserRole.CUSTOMER) }
      )

      Spacer(modifier = Modifier.height(20.dp))

      RoleCard(
        title = "I'm a Shop Owner",
        description = "Manage your inventory and grow your business.",
        icon = Icons.Default.Storefront,
        color = BharatGreen,
        onClick = { onRoleSelected(UserRole.VENDOR) }
      )
    }
  }
}

@Composable
fun RoleCard(
  title: String,
  description: String,
  icon: ImageVector,
  color: Color,
  onClick: () -> Unit
) {
  Card(
    onClick = onClick,
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier.padding(20.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(56.dp)
          .clip(CircleShape)
          .background(color.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = color,
          modifier = Modifier.size(28.dp)
        )
      }
      Spacer(modifier = Modifier.width(20.dp))
      Column {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
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
}

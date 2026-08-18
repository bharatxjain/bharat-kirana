package com.kks.bharatkirana.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kks.bharatkirana.ui.theme.BharatBackground
import com.kks.bharatkirana.ui.theme.BharatGreen
import com.kks.bharatkirana.ui.theme.BharatPurpleContainer
import com.kks.bharatkirana.ui.theme.BharatPurpleDark
import com.kks.bharatkirana.ui.theme.BharatPurplePrimary
import com.kks.bharatkirana.ui.theme.BharatTextMuted
import com.kks.bharatkirana.ui.theme.BharatTextPrimary
import com.kks.bharatkirana.ui.theme.BharatTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(
  onBackClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = "Privacy Policy",
            style = MaterialTheme.typography.titleLarge.copy(
              fontWeight = FontWeight.Bold,
              fontSize = 20.sp
            ),
            color = BharatTextPrimary
          )
        },
        navigationIcon = {
          IconButton(
            onClick = onBackClick,
            modifier = Modifier.testTag("privacy_policy_back_button")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back",
              tint = BharatTextPrimary
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = Color.White
        )
      )
    },
    modifier = modifier
      .fillMaxSize()
      .background(BharatBackground)
      .statusBarsPadding()
      .navigationBarsPadding()
      .testTag("privacy_policy_screen")
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .background(BharatBackground)
        .padding(paddingValues),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      // Header Banner
      item {
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = BharatPurpleContainer),
          border = BorderStroke(1.dp, Color(0xFFDDD6FE)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(BharatPurplePrimary),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(26.dp)
              )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
              Text(
                text = "Bharat Kirana Data Commitment",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = BharatPurpleDark
              )
              Text(
                text = "Last Updated: August 2026 • Effective Worldwide",
                style = MaterialTheme.typography.bodySmall,
                color = BharatPurplePrimary
              )
            }
          }
        }
      }

      // App & Publisher Info
      item {
        PolicySectionCard(
          icon = Icons.Default.Storefront,
          title = "1. Introduction & Overview",
          content = "Bharat Kirana (application ID: com.kks.bharatkirana, operated by KKS PVT) is committed to safeguarding user privacy. This Privacy Policy explains how our local kirana ordering platform collects, handles, stores, and protects personal information when you browse grocery inventories, create accounts, place pickup orders, and interact with our store services."
        )
      }

      // Information We Collect
      item {
        PolicySectionCard(
          icon = Icons.Default.Person,
          title = "2. Information We Collect",
          content = "We collect necessary information to process store pickup orders and provide an authentic grocery shopping experience:\n\n" +
              "• Personal Details: Full name, verified email address, mobile contact number, and default delivery/pickup notes.\n" +
              "• Order & Transaction Data: Items placed in cart, quantity choices, order timestamps, pickup tokens (QR codes), order fulfillment history, and payment method indicators.\n" +
              "• Store Preference & Proximity: Active kirana branch location selection (e.g. Banjara Hills, Hyderabad) to display accurate stock inventories."
        )
      }

      // How We Use Information
      item {
        PolicySectionCard(
          icon = Icons.Default.VerifiedUser,
          title = "3. How We Use Your Data",
          content = "Your data is used strictly for legitimate retail operations:\n\n" +
              "• To create and manage your customer account and secure role authentication (Customer vs. Store Administrator).\n" +
              "• To transmit and confirm orders with store staff for fast 15-minute counter packing.\n" +
              "• To generate secure QR pickup passes for counter validation.\n" +
              "• To send order status updates (Received, Packing, Ready for Pickup, Picked Up).\n" +
              "• We NEVER sell, rent, or trade your personal information to third-party advertisers."
        )
      }

      // Cloud Architecture & Security
      item {
        PolicySectionCard(
          icon = Icons.Default.Lock,
          title = "4. Data Security & Cloud Storage",
          content = "We implement industry-standard administrative, physical, and technical safeguards:\n\n" +
              "• Secure Cloud Infrastructure: Database and authentication operations run on managed Supabase servers secured with Row Level Security (RLS) policies and HTTPS/TLS 1.3 encryption in transit.\n" +
              "• Protected Credentials: Passwords, OTP codes, and authentication tokens are securely hashed and never stored in plain text.\n" +
              "• Counter Verification: Pickup QR codes are cryptographically formatted to prevent tampering."
        )
      }

      // Data Retention & User Rights
      item {
        PolicySectionCard(
          icon = Icons.Default.Gavel,
          title = "5. Your Rights & Data Deletion",
          content = "In accordance with global privacy frameworks and Google Play Developer Policies:\n\n" +
              "• Access & Correction: You may view and edit your profile details at any time in the Profile tab.\n" +
              "• Account & Data Deletion: You have the right to request full deletion of your user profile, saved addresses, and active orders. To request deletion, contact our privacy team at officialbharatjain2004@gmail.com.\n" +
              "• Data Retention: Order history is retained solely for taxation, store accounting, and pickup verification purposes."
        )
      }

      // Third Party Services
      item {
        PolicySectionCard(
          icon = Icons.Default.Sync,
          title = "6. Third-Party Integrations",
          content = "Our application interfaces with trusted infrastructure providers:\n\n" +
              "• Supabase (PostgreSQL & Auth): For persistent database records, product inventory synchronization, and user session management.\n" +
              "• Google Play Services: For app distribution, security verification, and update delivery."
        )
      }

      // Children's Privacy
      item {
        PolicySectionCard(
          icon = Icons.Default.ChildCare,
          title = "7. Children's Privacy",
          content = "Bharat Kirana is a retail shopping application intended for general audiences. We do not knowingly solicit or collect personal identifiable information from children under 13 years of age."
        )
      }

      // Contact & Grievance Officer
      item {
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White),
          border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(36.dp)
                  .clip(CircleShape)
                  .background(Color(0xFFF1F5F9)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.Email,
                  contentDescription = null,
                  tint = BharatPurplePrimary,
                  modifier = Modifier.size(20.dp)
                )
              }
              Spacer(modifier = Modifier.width(10.dp))
              Text(
                text = "8. Contact & Privacy Inquiries",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = BharatTextPrimary
              )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
              text = "If you have any questions, concerns, or requests regarding this Privacy Policy or your data, please contact:",
              style = MaterialTheme.typography.bodyMedium,
              color = BharatTextSecondary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
              shape = RoundedCornerShape(10.dp),
              color = Color(0xFFF8FAFC),
              border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(modifier = Modifier.padding(12.dp)) {
                Text(
                  text = "Grievance Officer: Bharat Jain",
                  fontWeight = FontWeight.Bold,
                  fontSize = 13.sp,
                  color = BharatTextPrimary
                )
                Text(
                  text = "Organization: KKS PVT / Bharat Kirana Operations",
                  fontSize = 12.sp,
                  color = BharatTextSecondary
                )
                Text(
                  text = "Email: officialbharatjain2004@gmail.com",
                  fontSize = 12.sp,
                  color = BharatPurplePrimary,
                  fontWeight = FontWeight.SemiBold
                )
              }
            }
          }
        }
      }

      item {
        Spacer(modifier = Modifier.height(16.dp))
      }
    }
  }
}

@Composable
private fun PolicySectionCard(
  icon: ImageVector,
  title: String,
  content: String
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color(0xFFF1F5F9)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = BharatPurplePrimary,
            modifier = Modifier.size(20.dp)
          )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
          ),
          color = BharatTextPrimary
        )
      }

      Spacer(modifier = Modifier.height(10.dp))

      Text(
        text = content,
        style = MaterialTheme.typography.bodyMedium.copy(
          fontSize = 13.sp,
          lineHeight = 19.sp
        ),
        color = BharatTextSecondary
      )
    }
  }
}

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
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.kks.bharatkirana.ui.theme.BharatPurpleContainer
import com.kks.bharatkirana.ui.theme.BharatPurpleDark
import com.kks.bharatkirana.ui.theme.BharatPurplePrimary
import com.kks.bharatkirana.ui.theme.BharatTextPrimary
import com.kks.bharatkirana.ui.theme.BharatTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsOfServiceScreen(
  onBackClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = "Terms of Service",
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
            modifier = Modifier.testTag("terms_of_service_back_button")
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
      .testTag("terms_of_service_screen")
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
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(26.dp)
              )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
              Text(
                text = "Bharat Kirana User Agreement",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = BharatPurpleDark
              )
              Text(
                text = "Last Updated: August 2026 • Legal Terms & Conditions",
                style = MaterialTheme.typography.bodySmall,
                color = BharatPurplePrimary
              )
            }
          }
        }
      }

      // Section 1: Agreement
      item {
        TermsSectionCard(
          icon = Icons.Default.AssignmentTurnedIn,
          title = "1. Acceptance of Terms",
          content = "By downloading, installing, accessing, or using the Bharat Kirana mobile application (Package: com.kkspvt.bharatkirana.xrvk, provided by KKS PVT), you acknowledge that you have read, understood, and agree to be legally bound by these Terms of Service and our associated Privacy Policy. If you do not agree to all terms, please refrain from using the application."
        )
      }

      // Section 2: Account Registration & Security
      item {
        TermsSectionCard(
          icon = Icons.Default.Security,
          title = "2. User Accounts & Verification",
          content = "• Account Creation: Users must provide accurate, current, and complete registration information (name, valid email, mobile phone number).\n" +
              "• Credentials & Safety: You are responsible for safeguarding your login credentials and for all activities that occur under your account.\n" +
              "• Admin Privileges: Access to administrative inventory panels is strictly restricted to authorized store operators."
        )
      }

      // Section 3: Ordering & Store Pickup
      item {
        TermsSectionCard(
          icon = Icons.Default.Storefront,
          title = "3. Orders & Express Pickup Policy",
          content = "• Pickup Model: Bharat Kirana operates an express store-pickup retail model. Orders placed via the app are prepared at the selected partner store branch.\n" +
              "• Digital Pickup Tokens: When an order is marked ready, a unique QR pass is generated on your screen. You must present this token at the store counter to collect items.\n" +
              "• Collection Window: Prepared grocery orders are held for pickup within the specified operating store hours. Items not picked up may be returned to store shelves."
        )
      }

      // Section 4: Pricing & Billing
      item {
        TermsSectionCard(
          icon = Icons.Default.CurrencyRupee,
          title = "4. Pricing, Taxes & Payment",
          content = "• Authentic Kirana Pricing: All grocery prices are displayed in Indian National Rupees (₹) and include applicable GST/retail taxes.\n" +
              "• Price Fluctuations: Daily market prices for fresh dairy, staples, and fresh vegetables may vary by store location and batch dates.\n" +
              "• In-Store & Digital Payments: Payments can be settled via UPI, cash on counter pickup, or supported digital wallets at checkout."
        )
      }

      // Section 5: Inventory & Availability
      item {
        TermsSectionCard(
          icon = Icons.Default.Inventory,
          title = "5. Product Availability & Replacements",
          content = "While stock counts sync in real time via our Supabase cloud infrastructure, high-demand local items (such as milk pouches or fresh flour) may occasionally run out at the counter before fulfillment. In such cases, store staff will notify the customer or adjust the bill accordingly."
        )
      }

      // Section 6: Returns, Refunds & Cancellations
      item {
        TermsSectionCard(
          icon = Icons.Default.WarningAmber,
          title = "6. Returns, Refunds & Cancellations",
          content = "• Cancellation: Orders can be cancelled at no penalty before store packing commences.\n" +
              "• Inspection on Pickup: Customers are encouraged to inspect staple quality, packaging seals, and expiry dates at the counter during QR handover.\n" +
              "• Defective Goods: In the rare event of damaged goods or incorrect items, immediate exchange or refund will be provided at the store counter."
        )
      }

      // Section 7: Prohibited Conduct
      item {
        TermsSectionCard(
          icon = Icons.Default.Block,
          title = "7. Prohibited Uses",
          content = "You agree not to:\n\n" +
              "• Abuse, manipulate, or forge digital order QR codes.\n" +
              "• Interfere with network APIs or attempt unauthorized access to store administrative databases.\n" +
              "• Submit fraudulent orders or abuse local store staff.\n" +
              "• Reverse engineer, decompile, or copy the application source code."
        )
      }

      // Section 8: Governing Law & Jurisdiction
      item {
        TermsSectionCard(
          icon = Icons.Default.Gavel,
          title = "8. Governing Law & Dispute Resolution",
          content = "These Terms are governed by and construed in accordance with the laws of the Republic of India. Any disputes arising in connection with the Bharat Kirana application or store services shall be subject to the exclusive jurisdiction of the competent courts in Hyderabad, Telangana, India."
        )
      }

      // Section 9: Legal Contact
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
                text = "9. Legal Inquiries & Contact",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = BharatTextPrimary
              )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
              text = "For legal questions, licensing inquiries, or terms clarifications, please reach out to our legal desk:",
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
                  text = "Legal Entity: KKS PVT (Bharat Kirana)",
                  fontWeight = FontWeight.Bold,
                  fontSize = 13.sp,
                  color = BharatTextPrimary
                )
                Text(
                  text = "Developer Contact: Bharat Jain",
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
private fun TermsSectionCard(
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

package com.kks.bharatkirana.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kks.bharatkirana.data.model.ShopRating
import com.kks.bharatkirana.ui.theme.BharatBackground
import com.kks.bharatkirana.ui.theme.BharatPurplePrimary
import com.kks.bharatkirana.ui.theme.BharatTextPrimary
import com.kks.bharatkirana.ui.theme.BharatTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorReviewsScreen(
  ratings: List<ShopRating>,
  averageRating: Float,
  ratingCount: Int,
  isLoading: Boolean,
  onBackClick: () -> Unit,
  onRefresh: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Ratings & Reviews", fontWeight = FontWeight.Bold) },
        navigationIcon = {
          IconButton(onClick = onBackClick) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
      )
    },
    modifier = modifier.fillMaxSize()
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .background(BharatBackground)
        .padding(padding)
    ) {
      AggregateHeader(averageRating = averageRating, ratingCount = ratingCount)
      when {
        isLoading && ratings.isEmpty() -> {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = BharatPurplePrimary, strokeWidth = 3.dp)
          }
        }
        ratings.isEmpty() -> EmptyReviewsState()
        else -> {
          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            items(ratings, key = { it.id.ifBlank { it.orderId } }) { r ->
              ReviewCard(r)
            }
          }
        }
      }
    }
  }
  // Fire on first composition and whenever the caller updates onRefresh.
  androidx.compose.runtime.LaunchedEffect(Unit) { onRefresh() }
}

@Composable
private fun AggregateHeader(averageRating: Float, ratingCount: Int) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    modifier = Modifier.fillMaxWidth().padding(16.dp)
  ) {
    Row(
      modifier = Modifier.padding(16.dp).fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = if (ratingCount > 0) "%.1f".format(averageRating) else "–",
          fontSize = 30.sp,
          fontWeight = FontWeight.ExtraBold,
          color = BharatTextPrimary
        )
        StarRow(rating = averageRating)
        Text(
          text = if (ratingCount == 0) "No reviews yet"
                 else "$ratingCount review${if (ratingCount == 1) "" else "s"}",
          fontSize = 12.sp,
          color = BharatTextSecondary
        )
      }
      HorizontalDivider(
        modifier = Modifier.height(56.dp).width(1.dp),
        color = Color(0xFFE2E8F0)
      )
      Column {
        Text(
          text = if (ratingCount == 0)
            "Your first review will appear here once a customer picks up an order and rates you."
          else
            "Aggregate is updated by the server every time a customer submits a rating.",
          fontSize = 12.sp,
          color = BharatTextSecondary
        )
      }
    }
  }
}

@Composable
private fun ReviewCard(review: ShopRating) {
  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        StarRow(rating = review.rating.toFloat())
        Spacer(modifier = Modifier.weight(1f))
        Text(
          text = formatRelativeTime(review.createdAt),
          fontSize = 11.sp,
          color = BharatTextSecondary
        )
      }
      if (review.review.isNotBlank()) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = review.review,
          fontSize = 14.sp,
          color = BharatTextPrimary
        )
      }
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = "Verified pickup · Order #${review.orderId.takeLast(4).uppercase()}",
        fontSize = 11.sp,
        color = BharatTextSecondary
      )
    }
  }
}

@Composable
private fun StarRow(rating: Float) {
  Row {
    for (i in 1..5) {
      Icon(
        imageVector = if (i <= rating) Icons.Default.Star else Icons.Default.StarBorder,
        contentDescription = null,
        tint = Color(0xFFF59E0B),
        modifier = Modifier.size(16.dp)
      )
    }
  }
}

@Composable
private fun EmptyReviewsState() {
  Column(
    modifier = Modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Box(
      modifier = Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
      contentAlignment = Alignment.Center
    ) {
      Icon(Icons.Default.Star, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
    }
    Spacer(modifier = Modifier.height(20.dp))
    Text("No reviews yet", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = BharatTextPrimary)
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = "Customer reviews will appear here once they receive their orders and rate you.",
      fontSize = 14.sp,
      color = BharatTextSecondary,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(horizontal = 32.dp)
    )
  }
}

// Best-effort ISO-8601 → "3 hours ago" formatter. Falls back to the raw string
// if the timestamp is missing or unparseable.
private fun formatRelativeTime(iso: String): String {
  if (iso.isBlank()) return ""
  return try {
    val parsers = listOf(
      "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
      "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
      "yyyy-MM-dd'T'HH:mm:ssXXX",
      "yyyy-MM-dd'T'HH:mm:ss'Z'"
    )
    val date = parsers.firstNotNullOfOrNull { pattern ->
      try {
        java.text.SimpleDateFormat(pattern, java.util.Locale.US).apply {
          timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.parse(iso)
      } catch (_: Exception) { null }
    } ?: return iso
    val diffMs = System.currentTimeMillis() - date.time
    val diffMins = diffMs / 60_000L
    when {
      diffMins < 1 -> "just now"
      diffMins < 60 -> "$diffMins min ago"
      diffMins < 24 * 60 -> "${diffMins / 60} hr ago"
      diffMins < 30 * 24 * 60 -> "${diffMins / (24 * 60)} d ago"
      else -> java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault()).format(date)
    }
  } catch (_: Exception) { iso }
}

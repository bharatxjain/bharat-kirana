package com.kks.bharatkirana.ui.screens

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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kks.bharatkirana.R
import com.kks.bharatkirana.ui.theme.BharatPurplePrimary
import com.kks.bharatkirana.ui.theme.BharatTextMuted
import com.kks.bharatkirana.ui.theme.BharatTextPrimary
import com.kks.bharatkirana.ui.theme.BharatTextSecondary
import kotlinx.coroutines.launch

data class OnboardingPage(
  val imageRes: Int,
  val title: String,
  val description: String
)

@Composable
fun OnboardingScreen(
  onComplete: () -> Unit,
  modifier: Modifier = Modifier
) {
  val pages = listOf(
    OnboardingPage(
      imageRes = R.drawable.img_onboarding_delivery,
      title = "Your Neighborhood Digital Marketplace",
      description = "Discover and browse fresh groceries from multiple verified Kirana stores right in your locality."
    ),
    OnboardingPage(
      imageRes = R.drawable.img_welcome_hero,
      title = "Skip the Queue with Counter Pickup",
      description = "Order your daily essentials online and pick them up instantly from the counter at your convenience."
    )
  )

  val pagerState = rememberPagerState(pageCount = { pages.size })
  val scope = rememberCoroutineScope()

  Surface(
    modifier = modifier.fillMaxSize(),
    color = Color(0xFFFBF9FE)
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding()
        .padding(horizontal = 24.dp, vertical = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
      ) {
        Text(
          text = "Skip",
          color = BharatTextSecondary,
          fontSize = 15.sp,
          fontWeight = FontWeight.Medium,
          modifier = Modifier
            .clickable(onClick = onComplete)
            .padding(8.dp)
            .testTag("onboarding_skip_button")
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      HorizontalPager(
        state = pagerState,
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
      ) { pageIndex ->
        val item = pages[pageIndex]
        Column(
          modifier = Modifier.fillMaxSize(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          Box(
            modifier = Modifier
              .size(280.dp)
              .clip(CircleShape)
              .background(Color(0xFFF3E8FF)),
            contentAlignment = Alignment.Center
          ) {
            Image(
              painter = painterResource(id = item.imageRes),
              contentDescription = item.title,
              modifier = Modifier
                .size(250.dp)
                .clip(CircleShape),
              contentScale = ContentScale.Crop
            )
          }

          Spacer(modifier = Modifier.height(36.dp))

          Text(
            text = item.title,
            style = MaterialTheme.typography.headlineSmall.copy(
              fontWeight = FontWeight.Bold,
              fontSize = 24.sp,
              lineHeight = 32.sp
            ),
            color = BharatTextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
          )

          Spacer(modifier = Modifier.height(12.dp))

          Text(
            text = item.description,
            style = MaterialTheme.typography.bodyMedium.copy(
              fontSize = 14.sp,
              lineHeight = 22.sp
            ),
            color = BharatTextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp)
          )
        }
      }

      Row(
        modifier = Modifier.padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
      ) {
        repeat(pages.size) { index ->
          val isSelected = pagerState.currentPage == index
          Box(
            modifier = Modifier
              .padding(horizontal = 4.dp)
              .height(6.dp)
              .width(if (isSelected) 24.dp else 6.dp)
              .clip(CircleShape)
              .background(if (isSelected) BharatPurplePrimary else BharatTextMuted.copy(alpha = 0.5f))
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      Button(
        onClick = {
          if (pagerState.currentPage < pages.size - 1) {
            scope.launch {
              pagerState.animateScrollToPage(pagerState.currentPage + 1)
            }
          } else {
            onComplete()
          }
        },
        modifier = Modifier
          .fillMaxWidth()
          .height(54.dp)
          .testTag("onboarding_get_started_button"),
        colors = ButtonDefaults.buttonColors(containerColor = BharatPurplePrimary),
        shape = RoundedCornerShape(28.dp)
      ) {
        Text(
          text = if (pagerState.currentPage == pages.size - 1) "Get Started" else "Continue",
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold,
          color = Color.White
        )
      }
    }
  }
}

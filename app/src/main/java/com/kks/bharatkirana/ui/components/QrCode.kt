package com.kks.bharatkirana.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Encodes [content] as an actual scannable QR image (M-level EC, 512×512
 * bitmap). Replaces the decorative `CustomQrCodePattern` for real pickup
 * flows where a vendor's ML Kit scanner has to be able to read it.
 */
@Composable
fun QrCode(
  content: String,
  modifier: Modifier = Modifier,
  size: Dp = 220.dp,
  darkColor: Color = Color.Black,
  lightColor: Color = Color.White
) {
  val bitmap = remember(content, darkColor, lightColor) {
    generateQrBitmap(content, sizePx = 512, darkArgb = darkColor.toArgb(), lightArgb = lightColor.toArgb())
  }
  Box(modifier = modifier.size(size).background(lightColor), contentAlignment = Alignment.Center) {
    if (bitmap != null) {
      Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "Pickup QR code",
        modifier = Modifier.fillMaxSize()
      )
    }
  }
}

private fun generateQrBitmap(content: String, sizePx: Int, darkArgb: Int, lightArgb: Int): Bitmap? {
  if (content.isBlank()) return null
  return runCatching {
    val hints = mapOf(
      EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
      EncodeHintType.MARGIN to 1
    )
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    for (x in 0 until sizePx) {
      for (y in 0 until sizePx) {
        bmp.setPixel(x, y, if (matrix[x, y]) darkArgb else lightArgb)
      }
    }
    bmp
  }.getOrNull()
}

private fun Color.toArgb(): Int {
  val a = (alpha * 255).toInt().coerceIn(0, 255)
  val r = (red * 255).toInt().coerceIn(0, 255)
  val g = (green * 255).toInt().coerceIn(0, 255)
  val b = (blue * 255).toInt().coerceIn(0, 255)
  return (a shl 24) or (r shl 16) or (g shl 8) or b
}

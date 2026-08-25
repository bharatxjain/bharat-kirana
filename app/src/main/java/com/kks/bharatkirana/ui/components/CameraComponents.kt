package com.kks.bharatkirana.ui.components

import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.kks.bharatkirana.ui.theme.BharatPurplePrimary
import java.util.concurrent.Executors

@Composable
fun CameraPreview(
  modifier: Modifier = Modifier,
  scaleType: PreviewView.ScaleType = PreviewView.ScaleType.FILL_CENTER,
  cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

  AndroidView(
    modifier = modifier,
    factory = { ctx ->
      val previewView = PreviewView(ctx).apply {
        this.scaleType = scaleType
      }
      val preview = Preview.Builder().build()
      
      cameraProviderFuture.addListener({
        val cameraProvider = cameraProviderFuture.get()
        preview.setSurfaceProvider(previewView.surfaceProvider)
        
        try {
          cameraProvider.unbindAll()
          cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            preview
          )
        } catch (e: Exception) {
          e.printStackTrace()
        }
      }, ContextCompat.getMainExecutor(context))
      
      previewView
    }
  )
}

@Composable
fun QrScannerView(
  onCodeScanned: (String) -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier
) {
  val lifecycleOwner = LocalLifecycleOwner.current
  val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
  
  var isFlashOn by remember { mutableStateOf(false) }
  var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
  var isScanned by remember { mutableStateOf(false) }

  // Flash control
  LaunchedEffect(isFlashOn) {
    cameraControl?.enableTorch(isFlashOn)
  }

  Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
    AndroidView(
      factory = { ctx ->
        val previewView = PreviewView(ctx).apply {
          implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
        cameraProviderFuture.addListener({
          try {
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().apply {
              setSurfaceProvider(previewView.surfaceProvider)
            }
            
            val analysis = ImageAnalysis.Builder()
              .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
              .build()

            val options = BarcodeScannerOptions.Builder()
              .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
              .build()
            
            val scanner = BarcodeScanning.getClient(options)

            analysis.setAnalyzer(cameraExecutor) { proxy ->
              @OptIn(ExperimentalGetImage::class)
              val mediaImage = proxy.image
              if (mediaImage == null || isScanned) {
                proxy.close()
                return@setAnalyzer
              }
              val inputImage = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
              scanner.process(inputImage)
                .addOnSuccessListener { barcodes ->
                  if (!isScanned) {
                    barcodes.firstOrNull()?.rawValue?.takeIf { it.isNotBlank() }?.let { code ->
                      isScanned = true
                      onCodeScanned(code)
                    }
                  }
                }
                .addOnCompleteListener { proxy.close() }
            }

            cameraProvider.unbindAll()
            val camera = cameraProvider.bindToLifecycle(
              lifecycleOwner,
              CameraSelector.DEFAULT_BACK_CAMERA,
              preview,
              analysis
            )
            cameraControl = camera.cameraControl

          } catch (e: Exception) {
            android.util.Log.e("QrScannerView", "Camera init failed", e)
          }
        }, ContextCompat.getMainExecutor(ctx))
        previewView
      },
      modifier = Modifier.fillMaxSize()
    )

    // Scanner Overlay
    ScannerOverlay(
      onCancel = onClose,
      isFlashOn = isFlashOn,
      onToggleFlash = { isFlashOn = !isFlashOn }
    )

    if (isScanned) {
      Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center
      ) {
        CircularProgressIndicator(color = BharatPurplePrimary)
      }
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      cameraExecutor.shutdown()
    }
  }
}

@Composable
private fun ScannerOverlay(
  onCancel: () -> Unit,
  isFlashOn: Boolean,
  onToggleFlash: () -> Unit
) {
  val infiniteTransition = rememberInfiniteTransition(label = "scan_line")
  val scanLineY by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(2000, easing = LinearEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "line_pos"
  )

  Box(modifier = Modifier.fillMaxSize()) {
    // Darken outer area
    Canvas(modifier = Modifier.fillMaxSize()) {
      val scanAreaWidth = size.width * 0.7f
      val scanAreaHeight = scanAreaWidth * 1.0f // Square for QR
      val left = (size.width - scanAreaWidth) / 2
      val top = (size.height - scanAreaHeight) / 2

      drawRect(color = Color.Black.copy(alpha = 0.6f))
      
      // Clear the scan area
      drawRect(
        color = Color.Transparent,
        topLeft = Offset(left, top),
        size = androidx.compose.ui.geometry.Size(scanAreaWidth, scanAreaHeight),
        blendMode = BlendMode.Clear
      )
      
      // Draw border corners
      val lineLength = 40.dp.toPx()
      val strokeWidth = 4.dp.toPx()
      val cornerColor = BharatPurplePrimary

      // Top Left
      drawLine(cornerColor, Offset(left, top), Offset(left + lineLength, top), strokeWidth)
      drawLine(cornerColor, Offset(left, top), Offset(left, top + lineLength), strokeWidth)

      // Top Right
      drawLine(cornerColor, Offset(left + scanAreaWidth, top), Offset(left + scanAreaWidth - lineLength, top), strokeWidth)
      drawLine(cornerColor, Offset(left + scanAreaWidth, top), Offset(left + scanAreaWidth, top + lineLength), strokeWidth)

      // Bottom Left
      drawLine(cornerColor, Offset(left, top + scanAreaHeight), Offset(left + lineLength, top + scanAreaHeight), strokeWidth)
      drawLine(cornerColor, Offset(left, top + scanAreaHeight), Offset(left, top + scanAreaHeight - lineLength), strokeWidth)

      // Bottom Right
      drawLine(cornerColor, Offset(left + scanAreaWidth, top + scanAreaHeight), Offset(left + scanAreaWidth - lineLength, top + scanAreaHeight), strokeWidth)
      drawLine(cornerColor, Offset(left + scanAreaWidth, top + scanAreaHeight), Offset(left + scanAreaWidth, top + scanAreaHeight - lineLength), strokeWidth)
      
      // Scan Line
      val lineY = top + (scanAreaHeight * scanLineY)
      drawLine(
        color = BharatPurplePrimary.copy(alpha = 0.5f),
        start = Offset(left + 10.dp.toPx(), lineY),
        end = Offset(left + scanAreaWidth - 10.dp.toPx(), lineY),
        strokeWidth = 2.dp.toPx()
      )
    }

    // Controls
    Column(
      modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(
          onClick = onCancel,
          modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
          Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }
        
        Text(
          text = "Scan QR / Barcode",
          color = Color.White,
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp
        )

        IconButton(
          onClick = onToggleFlash,
          modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
          Icon(
            imageVector = if (isFlashOn) Icons.Default.FlashOff else Icons.Default.FlashOn,
            contentDescription = "Flash",
            tint = if (isFlashOn) Color.Yellow else Color.White
          )
        }
      }

      Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Surface(
          color = Color.Black.copy(alpha = 0.7f),
          shape = RoundedCornerShape(12.dp)
        ) {
          Text(
            text = "Align code inside the frame",
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            fontSize = 14.sp
          )
        }
      }
    }
  }
}

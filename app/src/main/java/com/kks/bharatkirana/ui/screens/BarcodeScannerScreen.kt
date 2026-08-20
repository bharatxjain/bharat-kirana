package com.kks.bharatkirana.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@Composable
fun BarcodeScannerScreen(
  onBarcodeScanned: (String) -> Unit,
  onCancel: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  var hasPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    )
  }
  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { granted -> hasPermission = granted }

  LaunchedEffect(Unit) {
    if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color.Black)
  ) {
    if (hasPermission) {
      CameraPreviewWithBarcode(onBarcodeScanned = onBarcodeScanned)
    } else {
      Column(
        modifier = Modifier.align(Alignment.Center).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Text(
          text = "Camera permission needed to scan barcodes.",
          color = Color.White,
          textAlign = TextAlign.Center
        )
      }
    }

    // Overlay
    Column(
      modifier = Modifier.fillMaxSize().statusBarsPadding(),
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      // Top bar
      Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(onClick = onCancel) {
          Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }
        Text(
          text = "Scan Product Barcode",
          color = Color.White,
          fontWeight = FontWeight.Bold,
          fontSize = 16.sp,
          modifier = Modifier.padding(start = 4.dp)
        )
      }

      // Middle scan window (visual only; ML Kit reads the entire frame)
      Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp),
        contentAlignment = Alignment.Center
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.6f)
            .border(3.dp, Color.White, RoundedCornerShape(16.dp))
        )
      }

      // Instructions
      Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Icon(
          imageVector = Icons.Default.QrCodeScanner,
          contentDescription = null,
          tint = Color.White,
          modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = "Point at the barcode on the product packet",
          color = Color.White,
          fontSize = 14.sp,
          textAlign = TextAlign.Center
        )
        Text(
          text = "Hold steady \u2014 works with EAN, UPC, QR codes",
          color = Color.White.copy(alpha = 0.7f),
          fontSize = 12.sp,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(top = 4.dp)
        )
      }
    }
  }
}

@Composable
private fun CameraPreviewWithBarcode(onBarcodeScanned: (String) -> Unit) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  var handled by remember { mutableStateOf(false) }
  var status by remember { mutableStateOf<String?>(null) }

  AndroidView(
    factory = { ctx ->
      val previewView = PreviewView(ctx).apply {
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
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
            .setBarcodeFormats(
              Barcode.FORMAT_EAN_13,
              Barcode.FORMAT_EAN_8,
              Barcode.FORMAT_UPC_A,
              Barcode.FORMAT_UPC_E,
              Barcode.FORMAT_CODE_128,
              Barcode.FORMAT_QR_CODE
            )
            .build()
          val scanner = BarcodeScanning.getClient(options)
          val executor = Executors.newSingleThreadExecutor()

          analysis.setAnalyzer(executor) { proxy ->
            @OptIn(ExperimentalGetImage::class)
            val mediaImage = proxy.image
            if (mediaImage == null || handled) {
              proxy.close()
              return@setAnalyzer
            }
            val inputImage = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
            scanner.process(inputImage)
              .addOnSuccessListener { barcodes ->
                if (!handled) {
                  barcodes.firstOrNull()?.rawValue?.takeIf { it.isNotBlank() }?.let { code ->
                    handled = true
                    onBarcodeScanned(code)
                  }
                }
              }
              .addOnCompleteListener { proxy.close() }
          }

          cameraProvider.unbindAll()
          cameraProvider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            analysis
          )
        } catch (e: Exception) {
          status = "Camera error: ${e.message}"
        }
      }, ContextCompat.getMainExecutor(ctx))
      previewView
    },
    modifier = Modifier.fillMaxSize()
  )

  if (status != null) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text(text = status!!, color = Color.White)
    }
  }
  if (handled) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
      CircularProgressIndicator(color = Color.White)
    }
  }
}

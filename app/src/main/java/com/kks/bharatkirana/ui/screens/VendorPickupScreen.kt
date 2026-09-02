package com.kks.bharatkirana.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kks.bharatkirana.data.model.PickupState
import com.kks.bharatkirana.data.model.VendorOrderLookup
import com.kks.bharatkirana.ui.theme.BharatBackground
import com.kks.bharatkirana.ui.theme.BharatPurplePrimary
import com.kks.bharatkirana.ui.theme.BharatTextPrimary
import com.kks.bharatkirana.ui.theme.BharatTextSecondary

@Composable
fun VendorPickupScreen(
  pickupState: PickupState,
  onBackClick: () -> Unit,
  onScanToken: (String) -> Unit,
  onFindByNumber: (Int) -> Unit,
  onConfirmLookup: (String) -> Unit,
  onOpenOrder: (String) -> Unit,
  onReset: () -> Unit,
  modifier: Modifier = Modifier
) {
  var mode by remember { mutableStateOf(PickupMode.Scan) }
  var manualInput by remember { mutableStateOf("") }
  val snackbarHostState = remember { SnackbarHostState() }

  // Surface RPC errors in a snackbar and clear them once shown so the user
  // can retry without a stale banner sticking around.
  LaunchedEffect(pickupState.errorMessage) {
    val msg = pickupState.errorMessage ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(msg)
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = BharatBackground,
    contentWindowInsets = WindowInsets(0),
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    topBar = {
      Surface(color = Color.White, shadowElevation = 1.dp) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(onClick = onBackClick) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = BharatTextPrimary)
          }
          Spacer(modifier = Modifier.width(4.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Verify Pickup",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = BharatTextPrimary
            )
            Text(
              text = "Scan customer QR or look up by order number",
              fontSize = 12.sp,
              color = BharatTextSecondary
            )
          }
        }
      }
    }
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .navigationBarsPadding()
    ) {
      TabRow(
        selectedTabIndex = mode.ordinal,
        containerColor = Color.White,
        contentColor = BharatPurplePrimary
      ) {
        androidx.compose.material3.Tab(
          selected = mode == PickupMode.Scan,
          onClick = { mode = PickupMode.Scan; onReset(); manualInput = "" },
          text = { Text("Scan QR", fontWeight = if (mode == PickupMode.Scan) FontWeight.Bold else FontWeight.Medium) },
          icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
          selectedContentColor = BharatPurplePrimary,
          unselectedContentColor = BharatTextSecondary
        )
        androidx.compose.material3.Tab(
          selected = mode == PickupMode.Number,
          onClick = { mode = PickupMode.Number; onReset() },
          text = { Text("Enter Number", fontWeight = if (mode == PickupMode.Number) FontWeight.Bold else FontWeight.Medium) },
          icon = { Icon(Icons.Default.Numbers, contentDescription = null) },
          selectedContentColor = BharatPurplePrimary,
          unselectedContentColor = BharatTextSecondary
        )
      }

      Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        when (mode) {
          PickupMode.Scan -> ScanPane(
            pickupState = pickupState,
            onScanToken = onScanToken,
            onOpenOrder = onOpenOrder,
            onScanAnother = onReset
          )
          PickupMode.Number -> NumberPane(
            pickupState = pickupState,
            input = manualInput,
            onInputChange = { manualInput = it.filter { c -> c.isDigit() }.take(9) },
            onFindByNumber = onFindByNumber,
            onConfirmLookup = onConfirmLookup,
            onOpenOrder = onOpenOrder,
            onReset = { manualInput = ""; onReset() }
          )
        }
      }
    }
  }
}

private enum class PickupMode { Scan, Number }

@Composable
private fun ScanPane(
  pickupState: PickupState,
  onScanToken: (String) -> Unit,
  onOpenOrder: (String) -> Unit,
  onScanAnother: () -> Unit
) {
  // While waiting on the RPC or after a success, stop feeding new frames to
  // the barcode scanner so we don't double-fire completes.
  when {
    pickupState.completedOrderId != null -> PickupSuccessCard(
      orderId = pickupState.completedOrderId,
      onOpenOrder = onOpenOrder,
      onDone = onScanAnother
    )
    pickupState.isBusy -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      CircularProgressIndicator(color = BharatPurplePrimary)
    }
    else -> BarcodeScannerScreen(
      onBarcodeScanned = { raw ->
        // Filter obvious junk: pickup tokens are URL-safe base64, no spaces.
        val trimmed = raw.trim()
        if (trimmed.isNotEmpty()) onScanToken(trimmed)
      },
      onCancel = onScanAnother
    )
  }
}

@Composable
private fun NumberPane(
  pickupState: PickupState,
  input: String,
  onInputChange: (String) -> Unit,
  onFindByNumber: (Int) -> Unit,
  onConfirmLookup: (String) -> Unit,
  onOpenOrder: (String) -> Unit,
  onReset: () -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    if (pickupState.completedOrderId != null) {
      PickupSuccessCard(
        orderId = pickupState.completedOrderId,
        onOpenOrder = onOpenOrder,
        onDone = onReset
      )
      return@Column
    }

    Card(
      colors = CardDefaults.cardColors(containerColor = Color.White),
      shape = RoundedCornerShape(14.dp),
      elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
      Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
          text = "Enter customer's order number",
          fontWeight = FontWeight.Bold,
          color = BharatTextPrimary
        )
        Text(
          text = "Shown on the customer's Order screen as #1042 etc.",
          fontSize = 12.sp,
          color = BharatTextSecondary
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
          value = input,
          onValueChange = onInputChange,
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier.fillMaxWidth(),
          placeholder = { Text("e.g. 1042") },
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = BharatPurplePrimary,
            focusedLabelColor = BharatPurplePrimary,
            cursorColor = BharatPurplePrimary
          )
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
          onClick = { input.toIntOrNull()?.let(onFindByNumber) },
          enabled = !pickupState.isBusy && input.toIntOrNull() != null,
          modifier = Modifier.fillMaxWidth(),
          colors = ButtonDefaults.buttonColors(containerColor = BharatPurplePrimary)
        ) {
          if (pickupState.isBusy && pickupState.lookup == null) {
            CircularProgressIndicator(
              color = Color.White,
              strokeWidth = 2.dp,
              modifier = Modifier.size(18.dp)
            )
          } else {
            Text("Find order", color = Color.White, fontWeight = FontWeight.Bold)
          }
        }
      }
    }

    pickupState.lookup?.let { lookup ->
      LookupCard(
        lookup = lookup,
        isBusy = pickupState.isBusy,
        onConfirm = { lookup.pickupToken?.let(onConfirmLookup) },
        onReset = onReset
      )
    }
  }
}

@Composable
private fun LookupCard(
  lookup: VendorOrderLookup,
  isBusy: Boolean,
  onConfirm: () -> Unit,
  onReset: () -> Unit
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = Color.White),
    shape = RoundedCornerShape(14.dp),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
      Text(
        text = lookup.orderNumber?.let { "#$it" } ?: "#${lookup.orderId.takeLast(6)}",
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        color = BharatTextPrimary
      )
      Text(text = lookup.customerName.ifBlank { "Customer" }, color = BharatTextSecondary)
      Spacer(modifier = Modifier.height(8.dp))
      HorizontalDivider(color = BharatBackground)
      Spacer(modifier = Modifier.height(8.dp))
      InfoRow(label = "Status", value = lookup.status)
      InfoRow(label = "Total", value = "\u20B9${lookup.totalAmount}")
      Spacer(modifier = Modifier.height(12.dp))
      val canConfirm = lookup.status.equals("Ready for pickup", ignoreCase = true) &&
        !lookup.pickupToken.isNullOrBlank()
      Button(
        onClick = onConfirm,
        enabled = canConfirm && !isBusy,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = BharatPurplePrimary)
      ) {
        Text(
          text = when {
            !canConfirm -> "Not ready to complete"
            isBusy -> "Completing…"
            else -> "Confirm pickup"
          },
          color = Color.White,
          fontWeight = FontWeight.Bold
        )
      }
      Spacer(modifier = Modifier.height(8.dp))
      Button(
        onClick = onReset,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = BharatBackground, contentColor = BharatTextPrimary)
      ) {
        Text("Clear", fontWeight = FontWeight.Medium)
      }
    }
  }
}

@Composable
private fun PickupSuccessCard(
  orderId: String,
  onOpenOrder: (String) -> Unit,
  onDone: () -> Unit
) {
  Column(
    modifier = Modifier.fillMaxSize().padding(24.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Box(
      modifier = Modifier.size(88.dp).background(BharatPurplePrimary, RoundedCornerShape(50)),
      contentAlignment = Alignment.Center
    ) {
      Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp))
    }
    Spacer(modifier = Modifier.height(16.dp))
    Text(
      text = "Pickup complete",
      style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
      color = BharatTextPrimary
    )
    Text("Order marked as Completed.", color = BharatTextSecondary)
    Spacer(modifier = Modifier.height(24.dp))
    Button(
      onClick = { onOpenOrder(orderId) },
      modifier = Modifier.fillMaxWidth(),
      colors = ButtonDefaults.buttonColors(containerColor = BharatPurplePrimary)
    ) { Text("View order", color = Color.White, fontWeight = FontWeight.Bold) }
    Spacer(modifier = Modifier.height(8.dp))
    Button(
      onClick = onDone,
      modifier = Modifier.fillMaxWidth(),
      colors = ButtonDefaults.buttonColors(containerColor = BharatBackground, contentColor = BharatTextPrimary)
    ) { Text("Verify another", fontWeight = FontWeight.Medium) }
  }
}

@Composable
private fun InfoRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Text(label, color = BharatTextSecondary)
    Text(value, color = BharatTextPrimary, fontWeight = FontWeight.SemiBold)
  }
}

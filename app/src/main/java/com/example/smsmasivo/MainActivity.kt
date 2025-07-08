package com.example.smsmasivo

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.telephony.SmsManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private lateinit var smsSentReceiver: BroadcastReceiver
    private lateinit var smsDeliveredReceiver: BroadcastReceiver
    
    companion object {
        const val SMS_SENT = "SMS_SENT"
        const val SMS_DELIVERED = "SMS_DELIVERED"
        var smsRecordsGlobal = mutableListOf<SMSRecord>()
        var updateCallback: ((List<SMSRecord>) -> Unit)? = null
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setupSMSReceivers()
        
        setContent {
            MaterialTheme {
                SMSMasivoApp()
            }
        }
    }
    
    private fun setupSMSReceivers() {
        smsSentReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val index = intent?.getIntExtra("sms_index", -1) ?: -1
                if (index >= 0 && index < smsRecordsGlobal.size) {
                    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                    val currentTime = dateFormat.format(Date())
                    
                    when (resultCode) {
                        Activity.RESULT_OK -> {
                            smsRecordsGlobal[index] = smsRecordsGlobal[index].copy(
                                status = "Enviado", 
                                sentDateTime = currentTime
                            )
                        }
                        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> {
                            smsRecordsGlobal[index] = smsRecordsGlobal[index].copy(
                                status = "Error: Fallo genérico", 
                                sentDateTime = currentTime
                            )
                        }
                        SmsManager.RESULT_ERROR_NO_SERVICE -> {
                            smsRecordsGlobal[index] = smsRecordsGlobal[index].copy(
                                status = "Error: Sin servicio", 
                                sentDateTime = currentTime
                            )
                        }
                        SmsManager.RESULT_ERROR_NULL_PDU -> {
                            smsRecordsGlobal[index] = smsRecordsGlobal[index].copy(
                                status = "Error: PDU nulo", 
                                sentDateTime = currentTime
                            )
                        }
                        SmsManager.RESULT_ERROR_RADIO_OFF -> {
                            smsRecordsGlobal[index] = smsRecordsGlobal[index].copy(
                                status = "Error: Radio apagado", 
                                sentDateTime = currentTime
                            )
                        }
                        else -> {
                            smsRecordsGlobal[index] = smsRecordsGlobal[index].copy(
                                status = "Error: Desconocido", 
                                sentDateTime = currentTime
                            )
                        }
                    }
                    updateCallback?.invoke(smsRecordsGlobal.toList())
                }
            }
        }
        
        smsDeliveredReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val index = intent?.getIntExtra("sms_index", -1) ?: -1
                if (index >= 0 && index < smsRecordsGlobal.size) {
                    when (resultCode) {
                        Activity.RESULT_OK -> {
                            if (smsRecordsGlobal[index].status == "Enviado") {
                                smsRecordsGlobal[index] = smsRecordsGlobal[index].copy(status = "Entregado")
                            }
                        }
                        Activity.RESULT_CANCELED -> {
                            if (smsRecordsGlobal[index].status == "Enviado") {
                                smsRecordsGlobal[index] = smsRecordsGlobal[index].copy(status = "No entregado")
                            }
                        }
                    }
                    updateCallback?.invoke(smsRecordsGlobal.toList())
                }
            }
        }
        
        registerReceiver(smsSentReceiver, IntentFilter(SMS_SENT))
        registerReceiver(smsDeliveredReceiver, IntentFilter(SMS_DELIVERED))
    }
    
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(smsSentReceiver)
        unregisterReceiver(smsDeliveredReceiver)
    }
}

data class SMSRecord(
    val phoneNumber: String,
    val message: String,
    var status: String = "Pendiente",
    var sentDateTime: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SMSMasivoApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var smsRecords by remember { mutableStateOf(listOf<SMSRecord>()) }
    var isProcessing by remember { mutableStateOf(false) }
    var currentBatch by remember { mutableStateOf(0) }
    var totalBatches by remember { mutableStateOf(0) }
    var showBatchDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showFirstTimeHelp by remember { mutableStateOf(smsRecords.isEmpty()) }
    var batchSize by remember { mutableStateOf(10) }
    var delayBetweenMessages by remember { mutableStateOf(2000L) }
    var hasSMSPermission by remember { 
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    // Launcher para seleccionar archivo CSV
    val csvLauncher: ActivityResultLauncher<Intent> = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val fileName = getFileName(context, uri) ?: ""
                if (fileName.endsWith(".csv", ignoreCase = true)) {
                    loadCSVFile(context, uri) { records ->
                        smsRecords = records
                    }
                } else {
                    Toast.makeText(context, "Solo archivos CSV permitidos", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    var showSettingsDialog by remember { mutableStateOf(false) }
    
    // Launcher para permisos SMS
    val permissionLauncher: ActivityResultLauncher<String> = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasSMSPermission = isGranted
        if (!isGranted) {
            Toast.makeText(context, "Permiso SMS requerido", Toast.LENGTH_SHORT).show()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header con título e iconos
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SMS Masivo",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            
            Row {
                IconButton(onClick = { showHelpDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Ayuda"
                    )
                }
                
                IconButton(onClick = { showSettingsDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Configuración"
                    )
                }
                
                IconButton(onClick = { showAboutDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Acerca de"
                    )
                }
            }
        }
        
        // Ayuda inicial para nuevos usuarios
        if (showFirstTimeHelp && smsRecords.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "💡 ¿Cómo empezar?",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "1. Crea un archivo CSV con formato:\n   +5511999999999,Tu mensaje aquí\n\n2. Carga el archivo\n3. Envía los SMS",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        IconButton(onClick = { showFirstTimeHelp = false }) {
                            Text("✕", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Botones principales
        Button(
            onClick = {
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                csvLauncher.launch(intent)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cargar Archivo CSV")
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Button(
            onClick = {
                if (!hasSMSPermission) {
                    permissionLauncher.launch(Manifest.permission.SEND_SMS)
                } else {
                    scope.launch {
                        MainActivity.smsRecordsGlobal = smsRecords.toMutableList()
                        MainActivity.updateCallback = { updatedRecords ->
                            smsRecords = updatedRecords
                        }
                        
                        totalBatches = (smsRecords.size + batchSize - 1) / batchSize
                        currentBatch = 1
                        isProcessing = true
                        
                        scope.launch {
                            sendSMSInBatches(context, smsRecords, batchSize, delayBetweenMessages) { batch, completed ->
                                currentBatch = batch
                                if (completed) {
                                    isProcessing = false
                                    if (batch < totalBatches) {
                                        showBatchDialog = true
                                    }
                                }
                            }
                        }
                    }
                }
            },
            enabled = smsRecords.isNotEmpty() && !isProcessing,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(if (hasSMSPermission) "Enviar SMS" else "Solicitar Permisos")
        }
        
        // Botón para descargar reporte (siempre visible si hay registros procesados)
        if (smsRecords.isNotEmpty() && smsRecords.any { it.status != "Pendiente" && it.status != "Enviando..." }) {
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    generateAndShareReport(context, smsRecords)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("Descargar Reporte CSV")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Información compacta en fila
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Progreso (más compacto)
            if (isProcessing || totalBatches > 0) {
                Card(
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "Progreso",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text("$currentBatch/$totalBatches", style = MaterialTheme.typography.bodySmall)
                        Text("Lote: $batchSize | ${delayBetweenMessages/1000}s", style = MaterialTheme.typography.bodySmall)
                        if (isProcessing) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
            
            // Reporte (más compacto)
            if (smsRecords.isNotEmpty()) {
                val enviados = smsRecords.count { it.status == "Enviado" || it.status == "Entregado" }
                val fallidos = smsRecords.count { it.status.startsWith("Error") || it.status == "No entregado" }
                val pendientes = smsRecords.count { it.status == "Pendiente" || it.status == "Enviando..." }
                
                Card(
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "Estado",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text("Total: ${smsRecords.size}", style = MaterialTheme.typography.bodySmall)
                        Row {
                            Text("✓$enviados ", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                            Text("✗$fallidos ", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            Text("◷$pendientes", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Diálogo de Ayuda
        if (showHelpDialog) {
            AlertDialog(
                onDismissRequest = { showHelpDialog = false },
                title = { Text("📋 Guía de Uso") },
                text = { 
                    LazyColumn {
                        item {
                            Text(
                                text = "📁 Formato del archivo CSV:",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "• Separado por comas\n• Sin encabezados\n• Codificación: UTF-8\n• Extensión: .csv",
                                style = MaterialTheme.typography.bodySmall
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Text(
                                text = "📝 Ejemplo de contenido:",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Text(
                                    text = "+5511999999999,Hola Juan!\n+5521888888888,\"Mensaje con, comas\"\n+5531777777777,Recordatorio cita",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Text(
                                text = "⚡ Configuración recomendada:",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "• Lotes de 5-10 SMS\n• Delay de 2-3 segundos\n• Evita números duplicados",
                                style = MaterialTheme.typography.bodySmall
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Text(
                                text = "⚠️ Importante:",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "• Usa números con código de país\n• Respeta políticas anti-spam\n• Verifica permisos de contactos",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showHelpDialog = false }) {
                        Text("Entendido")
                    }
                }
            )
        }
        
        // Diálogo de Configuración
        if (showSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = { Text("Configuración") },
                text = { 
                    Column {
                        Text(
                            text = "Tamaño de lote:",
                            fontWeight = FontWeight.Bold
                        )
                        Slider(
                            value = batchSize.toFloat(),
                            onValueChange = { batchSize = it.toInt() },
                            valueRange = 1f..20f,
                            steps = 18
                        )
                        Text("$batchSize mensajes por lote")
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "Delay entre mensajes:",
                            fontWeight = FontWeight.Bold
                        )
                        Slider(
                            value = delayBetweenMessages.toFloat() / 1000f,
                            onValueChange = { delayBetweenMessages = (it * 1000).toLong() },
                            valueRange = 1f..10f,
                            steps = 17
                        )
                        Text("${delayBetweenMessages/1000} segundos")
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "Recomendaciones:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "• Lotes pequeños (5-10): Más estable\n• Lotes grandes (15-20): Más rápido\n• Delay alto: Evita bloqueos de operadora",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSettingsDialog = false }) {
                        Text("Guardar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { 
                        batchSize = 10
                        delayBetweenMessages = 2000L
                        showSettingsDialog = false 
                    }) {
                        Text("Restaurar")
                    }
                }
            )
        }
        
        // Diálogo de continuación de lote
        if (showBatchDialog) {
            AlertDialog(
                onDismissRequest = { showBatchDialog = false },
                title = { Text("Lote Completado") },
                text = { 
                    Text("Lote $currentBatch de $totalBatches completado.\n¿Desea continuar con el siguiente lote?") 
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showBatchDialog = false
                            scope.launch {
                                isProcessing = true
                                sendSMSInBatches(context, smsRecords, batchSize, delayBetweenMessages, currentBatch) { batch, completed ->
                                    currentBatch = batch
                                    if (completed) {
                                        isProcessing = false
                                        if (batch < totalBatches) {
                                            showBatchDialog = true
                                        }
                                    }
                                }
                            }
                        }
                    ) {
                        Text("Continuar")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showBatchDialog = false }
                    ) {
                        Text("Detener")
                    }
                }
            )
        }
        
        // Diálogo Acerca de
        if (showAboutDialog) {
            AlertDialog(
                onDismissRequest = { showAboutDialog = false },
                title = { Text("Acerca de SMS Masivo") },
                text = { 
                    Column {
                        Text(
                            text = "SMS Masivo v1.0",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Aplicación para envío masivo de mensajes SMS desde archivos CSV.")
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Desarrollador:",
                            fontWeight = FontWeight.Bold
                        )
                        Text("Raúl Risco Castillo")
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Licencia:",
                            fontWeight = FontWeight.Bold
                        )
                        Text("Apache License 2.0")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Licensed under the Apache License, Version 2.0 (the \"License\"); you may not use this file except in compliance with the License. You may obtain a copy of the License at:\n\nhttp://www.apache.org/licenses/LICENSE-2.0\n\nUnless required by applicable law or agreed to in writing, software distributed under the License is distributed on an \"AS IS\" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAboutDialog = false }) {
                        Text("Cerrar")
                    }
                }
            )
        }
        
        // Estado vacío mejorado
        if (smsRecords.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "📱",
                    style = MaterialTheme.typography.displayLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "¡Envía SMS masivos fácilmente!",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Carga un archivo CSV para comenzar",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { showHelpDialog = true }) {
                    Text("🔍 Ver formato de archivo")
                }
            }
        } else {
            // Lista de registros (con scroll automático)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(smsRecords) { record ->
                    SMSRecordItem(record = record)
                }
            }
        }
    }
}

@Composable
fun SMSRecordItem(record: SMSRecord) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = record.phoneNumber,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = record.message,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2
            )
            if (record.sentDateTime.isNotEmpty()) {
                Text(
                    text = record.sentDateTime,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                val statusColor = when {
                    record.status == "Enviado" || record.status == "Entregado" -> MaterialTheme.colorScheme.primary
                    record.status.startsWith("Error") || record.status == "No entregado" -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Text(
                    text = record.status,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor
                )
            }
        }
    }
}

fun getFileName(context: android.content.Context, uri: Uri): String? {
    return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        cursor.moveToFirst()
        cursor.getString(nameIndex)
    }
}

fun loadCSVFile(context: android.content.Context, uri: Uri, onResult: (List<SMSRecord>) -> Unit) {
    try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val reader = BufferedReader(InputStreamReader(inputStream))
        val records = mutableListOf<SMSRecord>()
        
        reader.useLines { lines ->
            lines.forEach { line ->
                val parts = line.split(",")
                if (parts.size >= 2) {
                    val phoneNumber = parts[0].trim().replace("\"", "")
                    val message = parts[1].trim().replace("\"", "")
                    
                    if (phoneNumber.isNotEmpty() && message.isNotEmpty()) {
                        records.add(SMSRecord(phoneNumber, message))
                    }
                }
            }
        }
        
        onResult(records)
        Toast.makeText(context, "CSV cargado: ${records.size} registros", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Error al cargar CSV: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

suspend fun sendSMSInBatches(
    context: android.content.Context,
    records: List<SMSRecord>,
    batchSize: Int,
    delayBetweenMessages: Long,
    startBatch: Int = 1,
    onBatchComplete: (currentBatch: Int, isCompleted: Boolean) -> Unit
) {
    val totalBatches = (records.size + batchSize - 1) / batchSize
    
    for (batch in startBatch..totalBatches) {
        val startIndex = (batch - 1) * batchSize
        val endIndex = minOf(startIndex + batchSize, records.size)
        val batchRecords = records.subList(startIndex, endIndex)
        
        sendSMSBatch(context, batchRecords, startIndex, delayBetweenMessages) { }
        
        var allCompleted = false
        var attempts = 0
        val maxAttempts = 30
        
        while (!allCompleted && attempts < maxAttempts) {
            delay(1000)
            attempts++
            
            allCompleted = (startIndex until endIndex).all { index ->
                val status = MainActivity.smsRecordsGlobal[index].status
                status != "Pendiente" && status != "Enviando..."
            }
        }
        
        onBatchComplete(batch, true)
        
        if (batch < totalBatches) {
            break
        }
    }
}

suspend fun sendSMSBatch(
    context: android.content.Context,
    records: List<SMSRecord>,
    startIndex: Int = 0,
    delayBetweenMessages: Long = 2000L,
    onUpdate: (List<SMSRecord>) -> Unit
) {
    val smsManager = SmsManager.getDefault()
    
    for (i in records.indices) {
        try {
            val record = records[i]
            val globalIndex = startIndex + i
            
            val sentIntent = PendingIntent.getBroadcast(
                context, 
                globalIndex, 
                Intent(MainActivity.SMS_SENT).putExtra("sms_index", globalIndex), 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            val deliveredIntent = PendingIntent.getBroadcast(
                context, 
                globalIndex, 
                Intent(MainActivity.SMS_DELIVERED).putExtra("sms_index", globalIndex), 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            val parts = smsManager.divideMessage(record.message)
            
            if (parts.size == 1) {
                smsManager.sendTextMessage(
                    record.phoneNumber, 
                    null, 
                    record.message, 
                    sentIntent, 
                    deliveredIntent
                )
            } else {
                val sentIntents = arrayListOf<PendingIntent>()
                val deliveredIntents = arrayListOf<PendingIntent>()
                repeat(parts.size) { 
                    sentIntents.add(sentIntent)
                    deliveredIntents.add(deliveredIntent)
                }
                
                smsManager.sendMultipartTextMessage(
                    record.phoneNumber, 
                    null, 
                    parts, 
                    sentIntents, 
                    deliveredIntents
                )
            }
            
            MainActivity.smsRecordsGlobal[globalIndex] = record.copy(status = "Enviando...")
            MainActivity.updateCallback?.invoke(MainActivity.smsRecordsGlobal.toList())
            
        } catch (e: Exception) {
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            val currentTime = dateFormat.format(Date())
            val globalIndex = startIndex + i
            MainActivity.smsRecordsGlobal[globalIndex] = MainActivity.smsRecordsGlobal[globalIndex].copy(
                status = "Error: ${e.message}", 
                sentDateTime = currentTime
            )
            MainActivity.updateCallback?.invoke(MainActivity.smsRecordsGlobal.toList())
        }
        
        delay(delayBetweenMessages)
    }
}

fun generateAndShareReport(context: android.content.Context, records: List<SMSRecord>) {
    try {
        val dateFormat = SimpleDateFormat("yyMMdd_HHmm", Locale.getDefault())
        val currentTime = dateFormat.format(Date())
        val fileName = "SMS${currentTime}.csv"
        
        val csvContent = buildString {
            appendLine("Numero,Mensaje,Estado,FechaHora")
            
            records.forEach { record ->
                val escapedMessage = "\"${record.message.replace("\"", "\"\"")}\""
                appendLine("${record.phoneNumber},$escapedMessage,${record.status},${record.sentDateTime}")
            }
        }
        
        val file = File(context.filesDir, fileName)
        file.writeText(csvContent)
        
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Reporte SMS - $fileName")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        context.startActivity(Intent.createChooser(shareIntent, "Compartir reporte CSV"))
        
        Toast.makeText(context, "Reporte generado: $fileName", Toast.LENGTH_SHORT).show()
        
    } catch (e: Exception) {
        Toast.makeText(context, "Error al generar reporte: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

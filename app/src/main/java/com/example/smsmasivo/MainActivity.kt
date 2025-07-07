package com.example.smsmasivo

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.telephony.SmsManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                SMSMasivoApp()
            }
        }
    }
}

data class SMSRecord(
    val phoneNumber: String,
    val message: String,
    var status: String = "Pendiente"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SMSMasivoApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var smsRecords by remember { mutableStateOf(listOf<SMSRecord>()) }
    var isProcessing by remember { mutableStateOf(false) }
    var hasSMSPermission by remember { 
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    // Launcher para seleccionar archivo CSV
    val csvLauncher = rememberLauncherForActivityResult(
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
    
    // Launcher para permisos SMS
    val permissionLauncher = rememberLauncherForActivityResult(
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
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "SMS Masivo",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Botón para cargar CSV
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
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Botón para enviar SMS
        Button(
            onClick = {
                if (!hasSMSPermission) {
                    permissionLauncher.launch(Manifest.permission.SEND_SMS)
                } else {
                    scope.launch {
                        sendSMSBatch(smsRecords) { updatedRecords ->
                            smsRecords = updatedRecords
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
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Reporte de estado
        if (smsRecords.isNotEmpty()) {
            val enviados = smsRecords.count { it.status == "Enviado" }
            val fallidos = smsRecords.count { it.status == "Error" }
            val pendientes = smsRecords.count { it.status == "Pendiente" }
            
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Reporte de Envío",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Total: ${smsRecords.size}")
                    Text("Enviados: $enviados", color = MaterialTheme.colorScheme.primary)
                    Text("Fallidos: $fallidos", color = MaterialTheme.colorScheme.error)
                    Text("Pendientes: $pendientes")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Lista de registros
        LazyColumn {
            items(smsRecords) { record ->
                SMSRecordItem(record = record)
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                val statusColor = when (record.status) {
                    "Enviado" -> MaterialTheme.colorScheme.primary
                    "Error" -> MaterialTheme.colorScheme.error
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
                    
                    // Validar número telefónico
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

suspend fun sendSMSBatch(
    records: List<SMSRecord>,
    onUpdate: (List<SMSRecord>) -> Unit
) {
    val smsManager = SmsManager.getDefault()
    val updatedRecords = records.toMutableList()
    
    for (i in updatedRecords.indices) {
        try {
            val record = updatedRecords[i]
            
            // Dividir mensaje si es muy largo (160 caracteres máximo)
            val parts = smsManager.divideMessage(record.message)
            
            if (parts.size == 1) {
                smsManager.sendTextMessage(record.phoneNumber, null, record.message, null, null)
            } else {
                smsManager.sendMultipartTextMessage(record.phoneNumber, null, parts, null, null)
            }
            
            updatedRecords[i] = record.copy(status = "Enviado")
            
        } catch (e: Exception) {
            updatedRecords[i] = updatedRecords[i].copy(status = "Error")
        }
        
        // Actualizar UI
        onUpdate(updatedRecords.toList())
        
        // Delay para evitar restricciones de operadoras (1 segundo entre mensajes)
        delay(1000)
    }
}

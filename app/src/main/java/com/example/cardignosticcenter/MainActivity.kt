package com.example.cardignosticcenter
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.cardignosticcenter.data.AppDatabase
import com.example.cardignosticcenter.data.ScanHistory
import com.example.cardignosticcenter.data.Vehicle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import java.util.UUID

// --- Constants and Enums ---
private const val TAG = "CarDiagnosticApp"
private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

// --- Bluetooth Service Logic ---
class InternalBluetoothService(
    private val bluetoothAdapter: BluetoothAdapter?,
    private val coroutineScope: CoroutineScope
) {

    private val _connectionState =
        MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _pairedDevices =
        MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val pairedDevices: StateFlow<List<BluetoothDevice>> = _pairedDevices

    private val _receivedData = MutableSharedFlow<String>()
    val receivedData: SharedFlow<String> = _receivedData

    private var connectJob: Job? = null
    private var listenJob: Job? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    // ---------------- FETCH PAIRED DEVICES ----------------
    @SuppressLint("MissingPermission")
    fun fetchPairedDevices() {
        if (bluetoothAdapter?.isEnabled == true) {
            _pairedDevices.value =
                bluetoothAdapter.bondedDevices.toList()
        } else {
            _pairedDevices.value = emptyList()
        }
    }



    // ---------------- CONNECT ----------------
    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {

        if (_connectionState.value == ConnectionState.CONNECTING ||
            _connectionState.value == ConnectionState.CONNECTED
        ) return

        _connectionState.value = ConnectionState.CONNECTING

        connectJob?.cancel()

        connectJob = coroutineScope.launch(Dispatchers.IO) {
            try {
                bluetoothAdapter?.cancelDiscovery()

                bluetoothSocket =
                    device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)

                bluetoothSocket?.connect()

                inputStream = bluetoothSocket?.inputStream
                outputStream = bluetoothSocket?.outputStream

                _connectionState.value = ConnectionState.CONNECTED

                startListening()

            } catch (e: Exception) {
                Log.e(TAG, "Connection Failed: ${e.message}")
                _connectionState.value = ConnectionState.ERROR
                closeConnection()
            }
        }
    }

    // ---------------- LISTEN ----------------
    private fun startListening() {
        listenJob?.cancel()

        listenJob = coroutineScope.launch(Dispatchers.IO) {
            val buffer = ByteArray(1024)

            while (isActive &&
                bluetoothSocket?.isConnected == true &&
                inputStream != null
            ) {
                try {
                    val bytes = inputStream!!.read(buffer)
                    val rawData = String(buffer, 0, bytes)
                    val clean = rawData.replace(">", "").trim()
                    Log.d("ELM_RAW", clean)
                    if (clean.isNotBlank()) {
                        _receivedData.emit(clean)
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Read Error: ${e.message}")
                    _connectionState.value = ConnectionState.ERROR
                    break
                }
            }
        }
    }

    // ---------------- SEND COMMAND ----------------
    fun sendCommandToVehicle(command: String) {
        coroutineScope.launch {
            try {
                outputStream?.write((command + "\r").toByteArray())
                outputStream?.flush()
            } catch (e: Exception) {
                Log.e(TAG, "Send Error: ${e.message}")
                _connectionState.value = ConnectionState.ERROR
            }
        }
    }

    // ---------------- DISCONNECT ----------------
    fun disconnect() {
        closeConnection()
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    // ---------------- CLOSE SOCKET ----------------
    private fun closeConnection() {
        try {
            listenJob?.cancel()
            connectJob?.cancel()

            inputStream?.close()
            outputStream?.close()
            bluetoothSocket?.close()

        } catch (_: Exception) {
        }

        bluetoothSocket = null
    }

    fun cleanup() {
        disconnect()
    }
}

data class FaultCode(
            val code: String,
            val status: String,
            val description: String,
            val severity: String
        )
data class EcuModule(
    val name: String,
    val header: String
)
//------Smart DTC Model------
data class SmartDtcInfo(
    val description: String,
    val causes: List<String>,
    val solutions: List<String>,
    val severity: String
)
//-------Pattern Analysis Model----------
data class PatternDiagnosis(
    val codes: Set<String>,
    val message: String
)



//----------------------    VIEW MODEL   ----------------------
        class DiagnosticViewModel(application: Application) :
            AndroidViewModel(application) {
    fun initializeElm() {
        viewModelScope.launch {

            btService.sendCommandToVehicle("ATZ")
            delay(2000)

            btService.sendCommandToVehicle("ATE0")
            delay(1000)

            btService.sendCommandToVehicle("ATL0")
            delay(1000)

            btService.sendCommandToVehicle("ATS0")
            delay(1000)

            btService.sendCommandToVehicle("ATH0")
            delay(1000)

            btService.sendCommandToVehicle("ATSP0")
            delay(2000)

            // 🔥 After full setup start live monitoring
            startLiveMonitoring()
        }
    }

    private var monitoringJob: Job? = null

    fun startLiveMonitoring() {

        monitoringJob?.cancel()

        monitoringJob = viewModelScope.launch {

            while (connectionState.value == ConnectionState.CONNECTED) {

                btService.sendCommandToVehicle("010C")
                delay(500)

                btService.sendCommandToVehicle("010D")
                delay(500)

                btService.sendCommandToVehicle("0105")
                delay(500)

                btService.sendCommandToVehicle("0142")
                delay(1000)
            }
        }
    }

    fun disconnect() {
        monitoringJob?.cancel()
        btService.disconnect()
        resetLiveData()
    }



    private val _activeVehicle = MutableStateFlow<Vehicle?>(null)
    val activeVehicle: StateFlow<Vehicle?> = _activeVehicle

    fun setActiveVehicle(vehicle: Vehicle) {
        _activeVehicle.value = vehicle
    }

            //--------------VIN--------------
    private val _vin = MutableStateFlow("UNKNOWN")
    val vin: StateFlow<String> = _vin

            //---------------Database Connect-------------------
    private val database = AppDatabase.getDatabase(application)
    private val scanDao = database.scanHistoryDao()
    private val vehicleDao = database.vehicleDao()
    val scanHistory = scanDao.getAllScans()
    private val dao =
        AppDatabase.getDatabase(application).scanHistoryDao()

            //--------------Smart DTC MODEL-------------------

            private val modules = listOf(
                EcuModule("Engine", "7E0"),
                EcuModule("Transmission", "7E1"),
                EcuModule("ABS", "7E2"),
                EcuModule("Airbag", "7E3")
            )
            private val _engineFaults = MutableStateFlow<List<FaultCode>>(emptyList())
            val engineFaults: StateFlow<List<FaultCode>> = _engineFaults

            private val _transmissionFaults = MutableStateFlow<List<FaultCode>>(emptyList())
            val transmissionFaults: StateFlow<List<FaultCode>> = _transmissionFaults

            private val _absFaults = MutableStateFlow<List<FaultCode>>(emptyList())
            val absFaults: StateFlow<List<FaultCode>> = _absFaults

            private val _airbagFaults = MutableStateFlow<List<FaultCode>>(emptyList())
            val airbagFaults: StateFlow<List<FaultCode>> = _airbagFaults
    private val smartDatabase = mapOf(

        "P0301" to SmartDtcInfo(
            description = "Cylinder 1 Misfire Detected",
            causes = listOf(
                "Worn spark plug",
                "Faulty ignition coil",
                "Fuel injector problem",
                "Low compression"
            ),
            solutions = listOf(
                "Replace spark plug",
                "Test ignition coil",
                "Clean or replace injector",
                "Perform compression test"
            ),
            severity = "HIGH"
        ),

        "P0700" to SmartDtcInfo(
            description = "Transmission Control System Malfunction",
            causes = listOf(
                "Transmission fluid low",
                "TCM communication issue",
                "Internal transmission fault"
            ),
            solutions = listOf(
                "Check transmission fluid level",
                "Scan transmission module",
                "Inspect wiring to TCM"
            ),
            severity = "HIGH"
        ),

        "P0171" to SmartDtcInfo(
            description = "System Too Lean (Bank 1)",
            causes = listOf(
                "Vacuum leak",
                "Dirty MAF sensor",
                "Weak fuel pump"
            ),
            solutions = listOf(
                "Check intake hoses",
                "Clean MAF sensor",
                "Check fuel pressure"
            ),
            severity = "MEDIUM"
        )
    )

    //----------Pattern Database----------
    private val patternDatabase = listOf(

        PatternDiagnosis(
            codes = setOf("P0301", "P0171"),
            message = "Possible vacuum leak affecting cylinder combustion. Inspect intake hoses and fuel system."
        ),

        PatternDiagnosis(
            codes = setOf("P0700", "P0730"),
            message = "Transmission slipping or gear ratio issue. Check transmission fluid and internal clutch packs."
        )
    )

            // -------------------- BLUETOOTH --------------------
            private val bluetoothManager =
                application.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

            private val bluetoothAdapter: BluetoothAdapter? =
                bluetoothManager.adapter

            val btService =
                InternalBluetoothService(bluetoothAdapter, viewModelScope)

            val pairedDevices = btService.pairedDevices
            val connectionState = btService.connectionState
    val vehicles = mutableStateListOf<Vehicle>()


            // -------------------- LIVE DATA --------------------
            private val _rpm = MutableStateFlow<Int?>(null)
            val rpm: StateFlow<Int?> = _rpm

            private val _vehicleSpeed = MutableStateFlow<Int?>(null)
            val vehicleSpeed: StateFlow<Int?> = _vehicleSpeed
            private val _coolantTemp = MutableStateFlow<Int?>(null)
            val coolantTemp: StateFlow<Int?> = _coolantTemp

            private val _fuelLevel = MutableStateFlow<Int?>(null)
            val fuelLevel: StateFlow<Int?> = _fuelLevel

            private val _intakeTemp = MutableStateFlow<Int?>(null)
            val intakeTemp: StateFlow<Int?> = _intakeTemp

            private val _obdVoltage = MutableStateFlow<Float?>(null)
            val obdVoltage: StateFlow<Float?> = _obdVoltage


            // -------------------- SMART FEATURES --------------------
            private val _recommendedService =
                MutableStateFlow<String?>(null)
            val recommendedService = _recommendedService

            private val _faultSeverity =
                MutableStateFlow<String?>(null)
            val faultSeverity = _faultSeverity

            private val _healthScore =
                MutableStateFlow(100)
            val healthScore = _healthScore

            private val _faultCodes =
                MutableStateFlow<List<FaultCode>>(emptyList())
            val faultCodes: StateFlow<List<FaultCode>> = _faultCodes
            init {
                viewModelScope.launch {
                    btService.receivedData.collect { data ->
                        parseObdResponse(data)
                            viewModelScope.launch {
                                vehicles.clear()
                                vehicles.addAll(vehicleDao.getAllVehicles())
                            }
                    }
                }
                btService.fetchPairedDevices()
            }
            @SuppressLint("MissingPermission")
            fun refreshPairedDevices() {
                btService.fetchPairedDevices()
            }
    //-----------------------------------------------------------------
    fun addVehicle(
        vin: String,
        owner: String,
        model: String,
        year: String
    ) {
        viewModelScope.launch {

            val vehicle = Vehicle(
                vin = vin,
                ownerName = owner,
                model = model,
                year = year
            )

            vehicleDao.insertVehicle(vehicle)

            vehicles.clear()
            vehicles.addAll(vehicleDao.getAllVehicles())
        }
    }

    fun deleteVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            vehicleDao.deleteVehicle(vehicle)
            vehicles.clear()
            vehicles.addAll(vehicleDao.getAllVehicles())
        }
    }
    //-----VIN REQUEST FUNCTION--------------
    fun requestVin() {
        if (connectionState.value == ConnectionState.CONNECTED) {
            btService.sendCommandToVehicle("0902")
        }
    }

    //---------------Smart Diagnostic---------------------
    fun getSmartDiagnosis(code: String): SmartDtcInfo? {
        return smartDatabase[code]
    }
//-----------------------------------------------------------
    fun saveCurrentScan(vin: String) {

        viewModelScope.launch {

            val summary = _faultCodes.value.joinToString { it.code }

            val scan = ScanHistory(
                vin = vin,
                date = java.text.SimpleDateFormat(
                    "dd/MM/yyyy HH:mm",
                    java.util.Locale.getDefault()
                ).format(java.util.Date()),
                healthScore = _healthScore.value,
                severity = _faultSeverity.value ?: "NONE",
                faultSummary = summary
            )

            dao.insertScan(scan)
        }
    }
                //----------------Pattern Check Function-----------
    fun getPatternDiagnosis(detectedCodes: List<String>): String? {

        val detectedSet = detectedCodes.toSet()

        patternDatabase.forEach { pattern ->
            if (detectedSet.containsAll(pattern.codes)) {
                return pattern.message
            }
        }

        return null
    }
            // -------------------- CONNECTION --------------------

            fun connect(device: BluetoothDevice) {
                btService.connectToDevice(device)
            }



            private fun getFaultDescription(code: String): String {
                return when (code) {
                    "P0301" -> "Cylinder 1 - Misfire Detected"
                    "P0123" -> "Throttle Position Sensor High Input"
                    "P0420" -> "Catalyst System Efficiency Below Threshold"
                    "P0171" -> "System Too Lean (Bank 1)"
                    "P0100" -> "Mass Air Flow Circuit Malfunction"
                    "P0113" -> "Intake Air Temperature Sensor High Input"
                    "P0500" -> "Vehicle Speed Sensor Malfunction"
                    "P0700" -> "Transmission Control System Malfunction"
                    "P0440" -> "Evaporative Emission Control System Malfunction"
                    else -> "General OBD-II Fault"
                }
            }
            private fun resetLiveData() {
                _rpm.value = null
                _vehicleSpeed.value = null
                _recommendedService.value = null
                _faultSeverity.value = null
                _healthScore.value = 100
            }

            //-----------scan all modules--------

            fun scanAllModules() {
                if (connectionState.value == ConnectionState.CONNECTED) {

                    modules.forEach { module ->

                        // Set header
                        btService.sendCommandToVehicle("AT SH ${module.header}")

                        // Request DTC
                        btService.sendCommandToVehicle("03")
                    }
                }
            }
            // -------------------- OBD COMMANDS --------------------

            fun requestEngineRpm() {
                if (connectionState.value == ConnectionState.CONNECTED)
                    btService.sendCommandToVehicle("010C")
            }

            fun requestVehicleSpeed() {
                if (connectionState.value == ConnectionState.CONNECTED)
                    btService.sendCommandToVehicle("010D")
            }

            fun requestFaultCodes() {
                if (connectionState.value == ConnectionState.CONNECTED)
                    btService.sendCommandToVehicle("03")
            }

            fun requestClearFault() {
                if (connectionState.value == ConnectionState.CONNECTED) {
                    btService.sendCommandToVehicle("04")

                    _recommendedService.value = "Fault Cleared ✅"
                    _faultSeverity.value = "NONE"
                    _healthScore.value = 100
                    _faultCodes.value = emptyList()
                }
            }

            // -------------------- PARSER --------------------

    private fun parseObdResponse(response: String) {

        val clean = response
            .replace("SEARCHING...", "")
            .replace("\r", "")
            .replace("\n", "")
            .replace(">", "")
            .replace(" ", "")
            .uppercase(Locale.ROOT)

        try {

            Log.d("ELM_CLEAN", clean)

            // ================= RPM =================
            if (clean.contains("410C")) {

                val index = clean.indexOf("410C")

                if (index != -1 && clean.length >= index + 8) {

                    val a = clean.substring(index + 4, index + 6).toInt(16)
                    val b = clean.substring(index + 6, index + 8).toInt(16)

                    _rpm.value = ((a * 256) + b) / 4
                }
            }

            // ================= SPEED =================
            if (clean.contains("410D")) {

                val index = clean.indexOf("410D")

                if (index != -1 && clean.length >= index + 6) {

                    _vehicleSpeed.value =
                        clean.substring(index + 4, index + 6).toInt(16)
                }
            }

            // ================= COOLANT =================
            if (clean.contains("4105")) {

                val index = clean.indexOf("4105")

                if (index != -1 && clean.length >= index + 6) {

                    _coolantTemp.value =
                        clean.substring(index + 4, index + 6).toInt(16) - 40
                }
            }

            // ================= FUEL =================
            if (clean.contains("412F")) {

                val index = clean.indexOf("412F")

                if (index != -1 && clean.length >= index + 6) {

                    val level =
                        clean.substring(index + 4, index + 6).toInt(16)

                    _fuelLevel.value = (level * 100) / 255
                }
            }

            // ================= INTAKE TEMP =================
            if (clean.contains("410F")) {

                val index = clean.indexOf("410F")

                if (index != -1 && clean.length >= index + 6) {

                    _intakeTemp.value =
                        clean.substring(index + 4, index + 6).toInt(16) - 40
                }
            }

            // ================= VOLTAGE =================
            if (clean.contains("4142")) {

                val index = clean.indexOf("4142")

                if (index != -1 && clean.length >= index + 8) {

                    val a =
                        clean.substring(index + 4, index + 6).toInt(16)

                    val b =
                        clean.substring(index + 6, index + 8).toInt(16)

                    _obdVoltage.value =
                        ((a * 256) + b) / 1000f
                }
            }

            // ================= VIN =================
            if (clean.contains("4902")) {

                val index = clean.indexOf("4902")

                if (index != -1 && clean.length > index + 6) {

                    try {
                        val vinHex = clean.substring(index + 6)

                        val vinString = vinHex.chunked(2)
                            .mapNotNull {
                                try {
                                    it.toInt(16).toChar()
                                } catch (e: Exception) {
                                    null
                                }
                            }
                            .joinToString("")

                        _vin.value = vinString
                    } catch (e: Exception) {
                        _vin.value = "VIN Read Error"
                    }
                }
            }

            // ================= FAULT CODES =================
            if (clean.contains("43")) {

                val index = clean.indexOf("43")

                if (index == -1 || clean.length <= index + 2) return

                val dtcData = clean.substring(index + 2)

                val newFaultList = mutableListOf<FaultCode>()

                for (i in dtcData.indices step 4) {

                    if (i + 4 <= dtcData.length) {

                        val chunk = dtcData.substring(i, i + 4)

                        val code = decodeDTC(chunk)

                        if (code != "P0000") {

                            val description =
                                getFaultDescription(code)

                            val severity =
                                calculateSeverity(code)

                            newFaultList.add(
                                FaultCode(
                                    code = code,
                                    status = "Confirmed",
                                    description = description,
                                    severity = severity
                                )
                            )
                        }
                    }
                }

                _engineFaults.value = newFaultList
                _faultCodes.value = newFaultList

                val highestSeverity =
                    newFaultList.maxByOrNull {
                        when (it.severity) {
                            "HIGH" -> 3
                            "MEDIUM" -> 2
                            else -> 1
                        }
                    }?.severity

                _faultSeverity.value = highestSeverity
                updateHealthScore(highestSeverity ?: "LOW")

                _recommendedService.value =
                    if (newFaultList.isEmpty())
                        "No Fault Codes Found ✅"
                    else
                        "Detected ${newFaultList.size} Fault Codes"
            }

        } catch (e: Exception) {

            Log.e("OBD_PARSE_ERROR", e.message ?: "Parse Error")
        }
    }
    private fun decodeDTC(hex: String): String {

        if (hex.length < 4) return "Unknown"

        val firstByte = hex.substring(0, 2).toInt(16)
        val secondByte = hex.substring(2, 4)

        val type = when ((firstByte and 0xC0) shr 6) {
            0 -> "P"
            1 -> "C"
            2 -> "B"
            else -> "U"
        }

        return String.format(
            Locale.ROOT,
            "%s%01d%02X",
            type,
            (firstByte and 0x30) shr 4,
            secondByte.toInt(16)
        )
    }
            // ---------------- SETTINGS ----------------

            private val _useMetric = MutableStateFlow(true)
            val useMetric: StateFlow<Boolean> = _useMetric

            fun toggleUnits(metric: Boolean) {
                _useMetric.value = metric
            }

            private val _isDarkMode = MutableStateFlow(false)
            val isDarkMode: StateFlow<Boolean> = _isDarkMode

            fun toggleDarkMode(enabled: Boolean) {
                _isDarkMode.value = enabled
            }

            private val _liveMonitoring = MutableStateFlow(false)
            val liveMonitoring: StateFlow<Boolean> = _liveMonitoring

            fun toggleLiveMonitoring(enabled: Boolean) {
                _liveMonitoring.value = enabled
            }

    // -------------------- PDF GENERATION --------------------

    fun generatePdfReport(context: Context, scan: ScanHistory) {

        val pdfDocument = android.graphics.pdf.PdfDocument()

        val pageInfo = android.graphics.pdf.PdfDocument.PageInfo
            .Builder(595, 842, 1)
            .create()

        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val paint = android.graphics.Paint()

        var y = 50

        paint.textSize = 20f
        paint.isFakeBoldText = true
        canvas.drawText("Car Diagnostic Report", 150f, y.toFloat(), paint)

        y += 40
        paint.textSize = 14f
        paint.isFakeBoldText = false

        canvas.drawText("VIN: ${scan.vin}", 40f, y.toFloat(), paint)
        y += 25

        canvas.drawText("Date: ${scan.date}", 40f, y.toFloat(), paint)
        y += 25

        canvas.drawText("Health Score: ${scan.healthScore}%", 40f, y.toFloat(), paint)
        y += 25

        canvas.drawText("Severity: ${scan.severity}", 40f, y.toFloat(), paint)
        y += 25

        canvas.drawText("Fault Summary:", 40f, y.toFloat(), paint)
        y += 20

        canvas.drawText(scan.faultSummary, 60f, y.toFloat(), paint)

        pdfDocument.finishPage(page)

        val file = java.io.File(
            context.getExternalFilesDir(null),
            "Diagnostic_Report_${scan.id}.pdf"
        )

        pdfDocument.writeTo(java.io.FileOutputStream(file))
        pdfDocument.close()

        Toast.makeText(
            context,
            "PDF Saved: ${file.absolutePath}",
            Toast.LENGTH_LONG
        ).show()
    }
    // ---------------- SEVERITY CALCULATOR ----------------
    private fun calculateSeverity(code: String): String {
        return when {
            code.startsWith("P0") -> "HIGH"
            code.startsWith("P1") -> "MEDIUM"
            else -> "LOW"
        }
    }

    // ---------------- HEALTH SCORE UPDATE ----------------
    private fun updateHealthScore(severity: String) {
        _healthScore.value = when (severity) {
            "HIGH" -> 40
            "MEDIUM" -> 70
            else -> 90
        }
    }
    override fun onCleared() {
        super.onCleared()
        btService.cleanup()
    }
    fun requestAllSensors() {
        if (connectionState.value == ConnectionState.CONNECTED) {

            btService.sendCommandToVehicle("010C") // RPM
            btService.sendCommandToVehicle("010D") // Speed
            btService.sendCommandToVehicle("0105") // Coolant
            btService.sendCommandToVehicle("012F") // Fuel
            btService.sendCommandToVehicle("010F") // Intake
            btService.sendCommandToVehicle("0142") // Voltage
        }
    }
}

// -------------------- MAIN ACTIVITY --------------------

class MainActivity : ComponentActivity() {

    private val viewModel: DiagnosticViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            requestPermissions(
                arrayOf(
                    android.Manifest.permission.BLUETOOTH_CONNECT,
                    android.Manifest.permission.BLUETOOTH_SCAN
                ),
                1
            )
        }

        setContent {

            // 🔥 Observe dark mode state
            val isDark by viewModel.isDarkMode.collectAsState()

            MaterialTheme(
                colorScheme = if (isDark)
                    darkColorScheme()
                else
                    lightColorScheme()
            ) {

                val navController = rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = "vehicle_select"
                ) {
                    composable("vehicle_select") {
                        VehicleSelectionScreen(viewModel, navController)
                    }

                    composable("add_vehicle") {
                        AddVehicleScreen(viewModel, navController)
                    }
                    composable("settings_units") {
                        UnitsScreen(navController, viewModel)
                    }

                    composable("settings_interface") {
                        InterfaceScreen(navController, viewModel)
                    }

                    composable("settings_adapter") {
                        AdapterScreen(navController, viewModel)
                    }

                    composable("settings_dashboard") {
                        DashboardSettingsScreen(navController, viewModel)
                    }

                    composable("settings") {
                        SettingsScreen(navController)
                    }

                    composable("scanResult") {
                        ScanResultScreen(viewModel, navController)
                    }

                    composable("scan") {
                        DeviceScanScreen(viewModel, navController)
                    }

                    composable("diagnostic") {
                        DiagnosticScreen(viewModel, navController)
                    }

                    composable("history") {
                        HistoryScreen(viewModel)
                    }
                    composable("service_booking") {
                        ServiceBookingScreen(navController)
                    }
                    composable("workshops") {
                        WorkshopListScreen()
                    }
                }
            }
        }
    }
}

    @Composable
    fun DiagnosticScreen(
        viewModel: DiagnosticViewModel,
        navController: NavController
    ) {
        val context = LocalContext.current

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.vehicle),
                contentDescription = "Car Dashboard",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(70.dp),
                contentScale = ContentScale.Crop

            )

            Spacer(modifier = Modifier.height(16.dp))



            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Vehicle Dashboard",
                    style = MaterialTheme.typography.headlineMedium
                )

                Button(
                    onClick = {
                        navController.navigate("settings")
                    }
                ) {
                    Text("⚙")
                }
            }

            Spacer(Modifier.height(32.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    Toast.makeText(context, "Scanning modules...", Toast.LENGTH_SHORT).show()
                    viewModel.scanAllModules()
                    viewModel.saveCurrentScan(viewModel.vin.value)
                    navController.navigate("scanResult")
                }

            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Image(
                        painter = painterResource(id = R.drawable.ic_scan),
                        contentDescription = "Scan",
                        modifier = Modifier.size(22.dp)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Text("Scan All Modules")
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    viewModel.requestClearFault()
                    Toast.makeText(
                        context,
                        "Fault Codes Cleared ✅",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Image(
                        painter = painterResource(id = R.drawable.ic_warning),
                        contentDescription = "Warning",
                        modifier = Modifier.size(22.dp),
                        colorFilter = ColorFilter.tint(Color.White)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Text("Clear Fault Codes")
                }
            }
            Spacer(Modifier.height(16.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    Toast.makeText(context, "Opening history...", Toast.LENGTH_SHORT).show()
                    navController.navigate("history")
                }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Image(
                        painter = painterResource(id = R.drawable.ic_history),
                        contentDescription = "Scan",
                        modifier = Modifier.size(22.dp)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Text("View History")
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    Toast.makeText(context, "Disconnected", Toast.LENGTH_SHORT).show()
                    viewModel.disconnect()

                    navController.navigate("scan")
                }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Image(
                        painter = painterResource(id = R.drawable.ic_disconnect),
                        contentDescription = "Scan",
                        modifier = Modifier.size(22.dp)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Text("Disconnect")
                }
            }
            Spacer(Modifier.height(16.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    Toast.makeText(context, "Opening service booking...", Toast.LENGTH_SHORT).show()
                    navController.navigate("service_booking")
                }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Image(
                        painter = painterResource(id = R.drawable.ic_service),
                        contentDescription = "Scan",
                        modifier = Modifier.size(22.dp)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Text("Book Service")
                }
            }
            }
        }
// ----------------Scan Result Screen--------
@Composable
fun ScanResultScreen(
    viewModel: DiagnosticViewModel,
    navController: NavController
) {

    val rpm by viewModel.rpm.collectAsState()
    val speed by viewModel.vehicleSpeed.collectAsState()
    val coolant by viewModel.coolantTemp.collectAsState()
    val fuel by viewModel.fuelLevel.collectAsState()
    val intake by viewModel.intakeTemp.collectAsState()
    val volts by viewModel.obdVoltage.collectAsState()

    val healthScore by viewModel.healthScore.collectAsState()
    val severity by viewModel.faultSeverity.collectAsState()

    val engine by viewModel.engineFaults.collectAsState()
    val detectedCodes = engine.map { it.code }
    val patternMessage = viewModel.getPatternDiagnosis(detectedCodes)
    val transmission by viewModel.transmissionFaults.collectAsState()
    val abs by viewModel.absFaults.collectAsState()
    val airbag by viewModel.airbagFaults.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {

        Text(
            "Scan Results",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(Modifier.height(20.dp))

        // ================= MODULE SUMMARY =================

        Text("Module Scan Summary", style = MaterialTheme.typography.titleLarge)

        Spacer(Modifier.height(12.dp))

        ModuleCard("Engine", engine.size)
        ModuleCard("Transmission", transmission.size)
        ModuleCard("ABS", abs.size)
        ModuleCard("Airbag", airbag.size)

        Spacer(Modifier.height(20.dp))

        // ================= SENSOR DATA =================

        Text("Live Sensor Data", style = MaterialTheme.typography.titleLarge)

        Spacer(Modifier.height(12.dp))

        SensorCard("RPM", rpm?.toString() ?: "N/A")
        Text("Live RPM Graph")
        Spacer(Modifier.height(8.dp))
        LiveRpmGraph(rpm)
        Spacer(Modifier.height(16.dp))
        SensorCard("Speed", speed?.toString() ?: "N/A")
        SensorCard("Coolant °C", coolant?.toString() ?: "N/A")
        SensorCard("Fuel %", fuel?.toString() ?: "N/A")
        SensorCard("Intake °C", intake?.toString() ?: "N/A")
        SensorCard("Voltage V", volts?.toString() ?: "N/A")

        Spacer(Modifier.height(20.dp))

        Text("Health Score: $healthScore%")
        Text("Severity: ${severity ?: "N/A"}")

        Spacer(Modifier.height(20.dp))
        if (patternMessage != null) {

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFF3E0)
                ),
                elevation = CardDefaults.cardElevation(6.dp)
            ) {

                Column(
                    modifier = Modifier.padding(16.dp)
                ) {

                    Text(
                        text = "Combined Intelligent Analysis",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(Modifier.height(6.dp))

                    Text(patternMessage)
                }
            }

            Spacer(Modifier.height(12.dp))
        }

        // ================= ENGINE SMART DIAGNOSIS =================

        Text("Engine Smart Diagnosis", style = MaterialTheme.typography.titleLarge)

        Spacer(Modifier.height(12.dp))

        if (engine.isEmpty()) {
            Text("No Engine Fault Codes ✅")
        } else {

            engine.forEach { fault ->

                val smartInfo = viewModel.getSmartDiagnosis(fault.code)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    elevation = CardDefaults.cardElevation(6.dp)
                ) {

                    Column(Modifier.padding(16.dp)) {

                        Text(
                            text = fault.code,
                            style = MaterialTheme.typography.titleLarge
                        )

                        Spacer(Modifier.height(6.dp))

                        if (smartInfo != null) {

                            Text(
                                text = smartInfo.description,
                                style = MaterialTheme.typography.titleMedium
                            )

                            Spacer(Modifier.height(8.dp))

                            Text("Possible Causes:", color = Color.Red)

                            smartInfo.causes.forEach {
                                Text("• $it")
                            }

                            Spacer(Modifier.height(8.dp))

                            Text("Recommended Solutions:", color = Color.Green)

                            smartInfo.solutions.forEach {
                                Text("• $it")
                            }

                        } else {
                            Text(fault.description)
                        }

                        Spacer(Modifier.height(8.dp))

                        Text(
                            "Severity: ${fault.severity}",
                            color = when (fault.severity) {
                                "HIGH" -> Color.Red
                                "MEDIUM" -> Color(0xFFFFA500)
                                else -> Color.Green
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(30.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { navController.popBackStack() }
        ) {
            Text("Back")
        }
    }
}
// --------Sensor Card---------
    @Composable
    fun SensorCard(title: String, value: String) {

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            shape = RoundedCornerShape(18.dp),
            elevation = CardDefaults.cardElevation(6.dp)
        ) {
            Column(Modifier.padding(16.dp)) {

                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    }
// ----------------Module Card----------------
@Composable
fun ModuleCard(moduleName: String, faultCount: Int) {

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        elevation = CardDefaults.cardElevation(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            Text(
                text = moduleName,
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = if (faultCount == 0)
                    "No Fault"
                else
                    "$faultCount Fault(s)",
                color = if (faultCount == 0)
                    Color.Green
                else
                    Color.Red
            )
        }
    }
}
    // -------------------- DEVICE SCAN --------------------
    @SuppressLint("MissingPermission")
    @Composable
    fun DeviceScanScreen(
        viewModel: DiagnosticViewModel,
        navController: NavController
    ) {

        val context = LocalContext.current
        val devices by viewModel.pairedDevices.collectAsState()
        val state by viewModel.connectionState.collectAsState()

        // 🔥 Demo Mode: If emulator has no Bluetooth devices
        val demoMode = devices.isEmpty()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = "Select OBD Device",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    viewModel.refreshPairedDevices()
                    Toast.makeText(
                        context,
                        "Device list refreshed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            ) {
                Text("Refresh Devices")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 🔄 Connecting State
            if (state == ConnectionState.CONNECTING) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(10.dp))
                Text("Connecting...")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 🔥 DEMO DEVICE (for Emulator Testing)
            if (demoMode) {

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(6.dp),
                    onClick = {
                        Toast.makeText(
                            context,
                            "Demo Mode Activated",
                            Toast.LENGTH_SHORT
                        ).show()
                        navController.navigate("diagnostic")
                    }
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(text = "Demo OBD Device")
                        Text(text = "00:11:22:33:44:55")
                    }
                }

            } else {

                // ✅ Real Bluetooth Devices
                devices.forEach { device ->

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(6.dp),
                        onClick = {
                            viewModel.connect(device)
                        }
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(text = device.name ?: "Unknown Device")
                            Text(text = device.address)
                        }
                    }
                }
            }

            // ❌ Error State
            if (state == ConnectionState.ERROR) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Connection Failed",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        LaunchedEffect(state) {
            if (state == ConnectionState.CONNECTED) {

                viewModel.initializeElm()   // 🔥 IMPORTANT

                navController.navigate("diagnostic")
            }
        }
    }

    // -------------------- HISTORY SCREEN --------------------
    @Composable
    fun HistoryScreen(viewModel: DiagnosticViewModel) {

        val context = LocalContext.current
        val history by viewModel.scanHistory.collectAsState(initial = emptyList())

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // 🔥 Header
            item {
                Text(
                    text = "Scan History",
                    style = MaterialTheme.typography.headlineMedium
                )
            }

            if (history.isEmpty()) {

                item {
                    Text("No Previous Scan Records Found")
                }

            } else {

                items(history) { scan ->

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(6.dp)
                    ) {

                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {

                            Text("VIN: ${scan.vin}")
                            Spacer(Modifier.height(4.dp))

                            Text("Date: ${scan.date}")
                            Spacer(Modifier.height(4.dp))

                            Text("Health Score: ${scan.healthScore}%")
                            Spacer(Modifier.height(4.dp))

                            Text("Severity: ${scan.severity}")
                            Spacer(Modifier.height(4.dp))

                            Text("Faults: ${scan.faultSummary}")
                            Spacer(Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    viewModel.generatePdfReport(context, scan)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Generate PDF Report")
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(24.dp))
            }
        }
    }
//---------------- SETTING SCREEN -----------------
@Composable
fun SettingsScreen(navController: NavController) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {

        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(Modifier.height(20.dp))

        SettingItem("Units") {
            navController.navigate("settings_units")
        }

        SettingItem("Interface") {
            navController.navigate("settings_interface")
        }

        SettingItem("Adapter OBDII ELM327") {
            navController.navigate("settings_adapter")
        }

        SettingItem("Dashboard") {
            navController.navigate("settings_dashboard")
        }
        Spacer(Modifier.height(30.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { navController.popBackStack() }
        ) {
            Text("Back")
        }
    }
}
@Composable
fun SettingItem(
    title: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(4.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title)
            Text(">")
        }
    }
}
@Composable
fun UnitsScreen(
    navController: NavController,
    viewModel: DiagnosticViewModel
) {
    val metric by viewModel.useMetric.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Text("Units", style = MaterialTheme.typography.headlineMedium)

        Spacer(Modifier.height(20.dp))

        SettingSwitch(
            title = "Use Metric (KM/H, °C)",
            checked = metric
        ) {
            viewModel.toggleUnits(it)
        }

        Spacer(Modifier.height(30.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { navController.popBackStack() }
        ) {
            Text("Back")
        }
    }
}
@Composable
fun InterfaceScreen(
    navController: NavController,
    viewModel: DiagnosticViewModel
) {

    val dark by viewModel.isDarkMode.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Text("Interface", style = MaterialTheme.typography.headlineMedium)

        Spacer(Modifier.height(20.dp))

        SettingSwitch("Dark Mode", dark) {
            viewModel.toggleDarkMode(it)
        }

        Spacer(Modifier.height(30.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { navController.popBackStack() }
        ) {
            Text("Back")
        }
    }
}
@Composable
fun AdapterScreen(
    navController: NavController,
    viewModel: DiagnosticViewModel
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Text("Adapter Settings", style = MaterialTheme.typography.headlineMedium)

        Spacer(Modifier.height(20.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                viewModel.requestClearFault()
            }
        ) {
            Text("Reset Adapter")
        }

        Spacer(Modifier.height(30.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { navController.popBackStack() }
        ) {
            Text("Back")
        }
    }
}
@Composable
fun DashboardSettingsScreen(
    navController: NavController,
    viewModel: DiagnosticViewModel
) {

    val live by viewModel.liveMonitoring.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Text("Dashboard Settings", style = MaterialTheme.typography.headlineMedium)

        Spacer(Modifier.height(20.dp))

        SettingSwitch("Live Monitoring Mode", live) {
            viewModel.toggleLiveMonitoring(it)
        }

        Spacer(Modifier.height(30.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { navController.popBackStack() }
        ) {
            Text("Back")
        }
    }
}
@Composable
fun SettingSwitch(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title)
            Switch(
                checked = checked,
                onCheckedChange = onChange
            )
        }
    }
}
@Composable
fun VehicleSelectionScreen(
    viewModel: DiagnosticViewModel,
    navController: NavController
) {

    val vehicles = viewModel.vehicles

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Text(
            text = "Select Vehicle",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(Modifier.height(20.dp))

        if (vehicles.isEmpty()) {

            Text("No Vehicles Added")

        } else {

            vehicles.forEach { vehicle ->

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    onClick = {
                        viewModel.setActiveVehicle(vehicle)
                        navController.navigate("scan")
                    }
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("${vehicle.ownerName} - ${vehicle.model}")
                        Text("VIN: ${vehicle.vin}")
                        Text("Year: ${vehicle.year}")
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                navController.navigate("add_vehicle")
            }
        ) {
            Text("Add New Vehicle")
        }
    }
}
@Composable
fun AddVehicleScreen(
    viewModel: DiagnosticViewModel,
    navController: NavController
) {

    var vin by remember { mutableStateOf("") }
    var owner by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Text("Add Vehicle", style = MaterialTheme.typography.headlineMedium)

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(vin, { vin = it }, label = { Text("VIN") })
        OutlinedTextField(owner, { owner = it }, label = { Text("Owner Name") })
        OutlinedTextField(model, { model = it }, label = { Text("Model") })
        OutlinedTextField(year, { year = it }, label = { Text("Year") })

        Spacer(Modifier.height(20.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                viewModel.addVehicle(vin, owner, model, year)
                navController.popBackStack()
            }
        ) {
            Text("Save Vehicle")
        }
    }
}
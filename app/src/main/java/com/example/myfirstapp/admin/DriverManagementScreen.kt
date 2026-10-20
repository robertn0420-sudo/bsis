package com.example.myfirstapp.admin

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.myfirstapp.DatabaseManager
import com.example.myfirstapp.DriverRecord
import com.example.myfirstapp.user
import com.google.firebase.auth.FirebaseAuth
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * High-precision Philippine LTO & International Driver's License Parser for Google ML Kit OCR.
 * Accurately extracts Driver's Name (line below Last Name, First Name, Middle Name header),
 * 11-character License Number (e.g. N01-18-123456), and Expiration Date.
 */
fun parseLicenseVisionText(
    fullText: String,
    onScanned: (name: String?, licenseNo: String?, expiryDate: String?, fullRawText: String) -> Unit
) {
    var extractedName: String? = null
    var extractedLicenseNo: String? = null
    var extractedExpiryDate: String? = null

    // Exact Philippine LTO 11-character License Number regex (e.g. N01-18-123456 or N0118123456)
    val ltoStrictRegex = Regex("""\b([A-Z]\d{2}[- ]?\d{2}[- ]?\d{6})\b""", RegexOption.IGNORE_CASE)
    val general11CharRegex = Regex("""\b([A-Z][0-9]{10}|[A-Z0-9]{3}[- ]?[0-9]{2}[- ]?[0-9]{6})\b""", RegexOption.IGNORE_CASE)
    
    val datePatternRegex = Regex(
        """\b(\d{4}[-/. \s]+\d{1,2}[-/. \s]+\d{1,2}|\d{1,2}[-/. \s]+\d{1,2}[-/. \s]+\d{4}|\d{1,2}[-/. \s]+[A-Za-z]{3,9}[-/. \s]+\d{4}|[A-Za-z]{3,9}[-/. \s]+\d{1,2}[-/. \s,]+\d{4}|\d{8})\b""",
        RegexOption.IGNORE_CASE
    )

    val lines = fullText.lines().map { it.trim() }.filter { it.isNotBlank() }

    // --- 1. EXTRACT DRIVER NAME & LICENSE NUMBER ---
    for (i in lines.indices) {
        val line = lines[i]
        val upper = line.uppercase()

        // Name Extraction
        if (extractedName == null) {
            if (upper.contains("LAST NAME") || upper.contains("FIRST NAME") || 
                upper.contains("MIDDLE NAME") || upper.contains("SURNAME") || 
                upper.contains("GIVEN NAME") || upper.contains("1. SURNAME") ||
                upper.contains("1. NAME") || upper.contains("FULL NAME")) {
                
                if (i + 1 < lines.size) {
                    val nextLine = lines[i + 1].trim()
                    val nextUpper = nextLine.uppercase()
                    if (!nextUpper.contains("REPUBLIC") && !nextUpper.contains("LICENSE") &&
                        !nextUpper.contains("EXPIRATION") && !datePatternRegex.containsMatchIn(nextLine) &&
                        nextLine.length >= 3) {
                        
                        val cleanedName = nextLine.replace(Regex("""^[0-9.:\-\s]+"""), "").trim()
                        if (cleanedName.length >= 3) {
                            extractedName = formatDriverName(cleanedName)
                        }
                    }
                }
            }
        }

        // License Number Extraction
        if (extractedLicenseNo == null) {
            val strictMatch = ltoStrictRegex.find(line)
            if (strictMatch != null) {
                val raw = strictMatch.groupValues[1].replace(" ", "").uppercase()
                extractedLicenseNo = if (raw.length == 11 && !raw.contains("-")) {
                    "${raw.substring(0, 3)}-${raw.substring(3, 5)}-${raw.substring(5)}"
                } else {
                    raw
                }
            } else {
                val genMatch = general11CharRegex.find(line)
                if (genMatch != null) {
                    val raw = genMatch.groupValues[1].replace(" ", "").uppercase()
                    extractedLicenseNo = if (raw.length == 11 && !raw.contains("-")) {
                        "${raw.substring(0, 3)}-${raw.substring(3, 5)}-${raw.substring(5)}"
                    } else {
                        raw
                    }
                }
            }
        }
    }

    // --- 2. EXTRACT EXPIRATION DATE WITH KEYWORD PRIORITIZATION & YEAR FILTER ---
    val expiryKeywordRegex = Regex("""(EXPIRATION|EXPIRY|EXP\b|EXPIRES|VALID UNTIL|EXP DATE|EXP\. DATE)""", RegexOption.IGNORE_CASE)

    // Strategy A: Find line with Expiration Keyword
    for (i in lines.indices) {
        val line = lines[i]
        if (expiryKeywordRegex.containsMatchIn(line)) {
            // Check current line for date
            val matchInLine = datePatternRegex.find(line)
            if (matchInLine != null) {
                val std = standardizeDateToYYYYMMDD(matchInLine.value)
                if (std != null) {
                    val year = std.split("-").firstOrNull()?.toIntOrNull() ?: 0
                    if (year >= 2020) {
                        extractedExpiryDate = std
                        break
                    }
                }
            }

            // Check next 2 lines for date
            for (offset in 1..2) {
                if (i + offset < lines.size) {
                    val nextLine = lines[i + offset]
                    val matchInNext = datePatternRegex.find(nextLine)
                    if (matchInNext != null) {
                        val std = standardizeDateToYYYYMMDD(matchInNext.value)
                        if (std != null) {
                            val year = std.split("-").firstOrNull()?.toIntOrNull() ?: 0
                            if (year >= 2020) {
                                extractedExpiryDate = std
                                break
                            }
                        }
                    }
                }
            }
            if (extractedExpiryDate != null) break
        }
    }

    // Strategy B: Fallback - Scan all dates in document and filter for year >= 2020 (ignores birth dates)
    if (extractedExpiryDate == null) {
        val allMatches = datePatternRegex.findAll(fullText).map { it.value.trim() }.toList()
        for (rawDate in allMatches) {
            val stdDate = standardizeDateToYYYYMMDD(rawDate)
            if (stdDate != null) {
                val year = stdDate.split("-").firstOrNull()?.toIntOrNull() ?: 0
                if (year >= 2020) {
                    extractedExpiryDate = stdDate
                    break
                }
            }
        }
    }

    onScanned(extractedName, extractedLicenseNo, extractedExpiryDate, fullText)
}

fun formatDriverName(raw: String): String {
    val clean = raw.lowercase().replace(Regex("""[^a-z\s,-]"""), " ").trim()
    return clean.split(" ")
        .filter { it.isNotBlank() }
        .joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
}

fun standardizeDateToYYYYMMDD(raw: String): String? {
    val clean = raw.trim()
        .replace(Regex("""\s*[-/. \s]\s*"""), "-")
        .replace(Regex("""-+"""), "-")
    
    val formats = listOf(
        "yyyy-MM-dd", "yyyy-M-d",
        "dd-MM-yyyy", "d-M-yyyy",
        "dd-MMM-yyyy", "d-MMM-yyyy",
        "MMM-dd-yyyy", "MMM-d-yyyy",
        "dd-MMMM-yyyy", "MMMM-dd-yyyy"
    )
    for (fmt in formats) {
        try {
            val sdf = SimpleDateFormat(fmt, Locale.US)
            sdf.isLenient = false
            val parsedDate = sdf.parse(clean)
            if (parsedDate != null) {
                val outSdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                return outSdf.format(parsedDate)
            }
        } catch (_: Exception) {}
    }

    // Check for 8-digit YYYYMMDD string (e.g. 20281231)
    val digits = raw.replace(Regex("""\D"""), "")
    if (digits.length == 8) {
        val yr = digits.substring(0, 4).toIntOrNull() ?: 0
        val mo = digits.substring(4, 6).toIntOrNull() ?: 0
        val dy = digits.substring(6, 8).toIntOrNull() ?: 0
        if (yr in 2020..2040 && mo in 1..12 && dy in 1..31) {
            return String.format(Locale.US, "%04d-%02d-%02d", yr, mo, dy)
        }
    }

    return null
}

/**
 * Validates whether the driver's license expiration date is valid or expired against today's date.
 */
fun validateLicenseExpiry(expiryDateStr: String): Pair<Boolean, String> {
    if (expiryDateStr.isBlank()) return Pair(false, "License expiration date required")
    val stdDateStr = standardizeDateToYYYYMMDD(expiryDateStr) ?: expiryDateStr.trim()
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    return try {
        val expiryDate = sdf.parse(stdDateStr)
        val today = Date()
        if (expiryDate != null) {
            if (expiryDate.after(today)) {
                Pair(true, "License Valid until $stdDateStr")
            } else {
                Pair(false, "EXPIRED LICENSE ($stdDateStr)")
            }
        } else {
            Pair(false, "Invalid Date Format")
        }
    } catch (_: Exception) {
        Pair(true, "Expiry: $expiryDateStr")
    }
}

/**
 * ML Kit OCR Scanner Launcher for Bitmap from Live Camera
 */
fun scanLicenseBitmapWithMLKit(
    bitmap: Bitmap,
    onScanned: (name: String?, licenseNo: String?, expiryDate: String?, fullRawText: String) -> Unit,
    onError: (String) -> Unit
) {
    try {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = InputImage.fromBitmap(bitmap, 0)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                parseLicenseVisionText(visionText.text, onScanned)
            }
            .addOnFailureListener { e ->
                onError(e.localizedMessage ?: "OCR Text Recognition failed")
            }
    } catch (e: Exception) {
        onError(e.localizedMessage ?: "ML Kit initialization error")
    }
}

/**
 * ML Kit OCR Scanner Launcher for System Gallery Uri
 */
fun scanLicenseWithMLKit(
    context: Context,
    imageUri: Uri,
    onScanned: (name: String?, licenseNo: String?, expiryDate: String?, fullRawText: String) -> Unit,
    onError: (String) -> Unit
) {
    try {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = InputImage.fromFilePath(context, imageUri)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                parseLicenseVisionText(visionText.text, onScanned)
            }
            .addOnFailureListener { e ->
                onError(e.localizedMessage ?: "OCR Text Recognition failed")
            }
    } catch (e: Exception) {
        onError(e.localizedMessage ?: "ML Kit initialization error")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverManagementScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var editingDriver by remember { mutableStateOf<DriverRecord?>(null) }
    var driverToDelete by remember { mutableStateOf<DriverRecord?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    val driverList = remember { mutableStateListOf<DriverRecord>() }

    // Registered Buses for Dropdown Assignment
    val registeredBuses = remember {
        mutableStateListOf("Unassigned", "BUS-101 (Central ➔ Airport)", "BUS-102 (Cubao ➔ PITX)", "BUS-103 (Palico ➔ Lemery)")
    }

    val busObjectsList = remember { mutableStateListOf<com.example.myfirstapp.Bus>() }

    // Live Sync with Firestore Buses for assignment options
    LaunchedEffect(Unit) {
        DatabaseManager.listenToBuses(
            onUpdate = { fetchedBuses ->
                busObjectsList.clear()
                busObjectsList.addAll(fetchedBuses)

                registeredBuses.clear()
                registeredBuses.add("Unassigned")
                fetchedBuses.forEach { b ->
                    val bName = if (b.busNumber.isNotBlank()) b.busNumber else "BUS-${b.busId}"
                    val label = "$bName (${if (b.plateNumber.isNotBlank()) b.plateNumber else b.busId})"
                    registeredBuses.add(label)
                }
            }
        )
    }

    // Real-Time Live Sync with Cloud Firestore
    LaunchedEffect(Unit) {
        DatabaseManager.listenToDrivers(
            onUpdate = { fetchedDrivers ->
                driverList.clear()
                driverList.addAll(fetchedDrivers)
            }
        )
    }

    val filteredDrivers = driverList.filter {
        it.driverName.contains(searchQuery, ignoreCase = true) ||
        it.licenseNumber.contains(searchQuery, ignoreCase = true) ||
        it.driverId.contains(searchQuery, ignoreCase = true) ||
        it.assignedBus.contains(searchQuery, ignoreCase = true) ||
        it.email.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = AdminGreen,
                contentColor = Color.White
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Driver")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(AdminBg)
                .padding(16.dp)
        ) {
            // Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = AdminGreen
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Driver Management", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    Text("${driverList.size} Active Drivers Registered", fontSize = 12.sp, color = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Search TextField
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search driver name, license, email, or ID...", color = Color.Gray) },
                leadingIcon = { Icon(imageVector = Icons.Default.Person, contentDescription = "Search", tint = AdminGreen) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = AdminGreen,
                    unfocusedBorderColor = Color.LightGray
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (filteredDrivers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Default.Person, contentDescription = "Empty", tint = Color.Gray, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No Drivers Found", fontWeight = FontWeight.Bold, color = Color.Black)
                        Text("Tap + button below to add a driver record", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(filteredDrivers) { driver ->
                        val (isValidExpiry, _) = validateLicenseExpiry(driver.licenseExpiry)

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Surface(
                                            color = Color(0xFFE8F5E9),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = AdminGreen)
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(driver.driverName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
                                            Text("ID: ${driver.driverId}", fontSize = 11.sp, color = Color.Gray)
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            color = if (driver.employmentStatus == "On Duty") Color(0xFFE8F5E9) else Color(0xFFF5F5F5),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = driver.employmentStatus,
                                                fontSize = 11.sp,
                                                color = if (driver.employmentStatus == "On Duty") AdminGreen else Color.DarkGray,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(4.dp))

                                        // RED TRASH DELETE DRIVER ICON BUTTON
                                        IconButton(
                                            onClick = { driverToDelete = driver },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Driver", tint = Color(0xFFC62828), modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))

                                if (driver.email.isNotBlank()) {
                                    Text("🔑 Login Email: ${driver.email}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AdminGreen)
                                    Spacer(modifier = Modifier.height(2.dp))
                                }

                                Text("Contact Number: ${driver.contactNumber}", fontSize = 13.sp, color = Color.DarkGray)
                                
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("License No: ${driver.licenseNumber}", fontSize = 13.sp, color = Color.DarkGray)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    if (isValidExpiry) {
                                        Surface(color = Color(0xFFE8F5E9), shape = RoundedCornerShape(4.dp)) {
                                            Text("Valid Exp: ${driver.licenseExpiry}", fontSize = 10.sp, color = AdminGreen, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    } else {
                                        Surface(color = Color(0xFFFFEBEE), shape = RoundedCornerShape(4.dp)) {
                                            Text("EXPIRED: ${driver.licenseExpiry}", fontSize = 10.sp, color = Color(0xFFC62828), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Assigned Bus: ${driver.assignedBus}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AdminGreen
                                    )

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { editingDriver = driver },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Driver", tint = AdminGreen, modifier = Modifier.size(18.dp))
                                        }

                                        Spacer(modifier = Modifier.width(4.dp))

                                        IconButton(
                                            onClick = { driverToDelete = driver },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Driver", tint = Color(0xFFC62828), modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // DELETE DRIVER CONFIRMATION DIALOG WITH PROMINENT DELETE ICON
    if (driverToDelete != null) {
        val target = driverToDelete!!
        AlertDialog(
            onDismissRequest = { driverToDelete = null },
            containerColor = Color.White,
            titleContentColor = Color.Black,
            textContentColor = Color.Black,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = Color(0xFFC62828), modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Driver Record", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFFC62828))
                }
            },
            text = {
                Text("Are you sure you want to delete driver ${target.driverName} (${target.driverId})? This action will permanently remove the driver record from Cloud Firestore.", fontSize = 14.sp, color = Color.DarkGray)
            },
            confirmButton = {
                Button(
                    onClick = {
                        DatabaseManager.deleteDriver(
                            driverId = target.driverId,
                            onSuccess = {
                                Toast.makeText(context, "Driver ${target.driverName} deleted!", Toast.LENGTH_SHORT).show()
                            },
                            onFailure = { e ->
                                Toast.makeText(context, "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        )
                        driverList.remove(target)
                        driverToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete Driver", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { driverToDelete = null }) {
                    Text("Cancel", color = Color.DarkGray, fontWeight = FontWeight.Medium)
                }
            }
        )
    }

    // ADD OR EDIT DRIVER MODAL DIALOG WITH ML KIT OCR SCANNER & CAMERA CAPTURE
    if (showAddDialog || editingDriver != null) {
        val isEditing = editingDriver != null
        val targetDriver = editingDriver

        val autoDriverId = remember { targetDriver?.driverId ?: ("DRV-" + (104..999).random()) }
        var driverName by remember { mutableStateOf(targetDriver?.driverName ?: "") }
        var contactNumber by remember { mutableStateOf(targetDriver?.contactNumber ?: "") }
        var licenseNumber by remember { mutableStateOf(targetDriver?.licenseNumber ?: "") }
        var licenseExpiry by remember { mutableStateOf(targetDriver?.licenseExpiry ?: "2028-12-31") }
        
        var email by remember { mutableStateOf(targetDriver?.email ?: "") }
        var password by remember { mutableStateOf(targetDriver?.password ?: "") }
        var passwordVisible by remember { mutableStateOf(false) }

        var assignedBus by remember { mutableStateOf(targetDriver?.assignedBus ?: "Unassigned") }
        var busDropdownExpanded by remember { mutableStateOf(false) }
        var selectedStatus by remember { mutableStateOf(targetDriver?.employmentStatus ?: "On Duty") }

        var licenseImageUri by remember { mutableStateOf<Uri?>(null) }
        var licenseBitmap by remember { mutableStateOf<Bitmap?>(null) }

        var isScanningOCR by remember { mutableStateOf(false) }
        var ocrSuccessMsg by remember { mutableStateOf("") }
        var dialogError by remember { mutableStateOf("") }

        // License Expiration Date Live Validation
        val (isExpiryValid, expiryValidationMsg) = remember(licenseExpiry) { validateLicenseExpiry(licenseExpiry) }

        // Helper to process scanned result
        fun handleOcrResult(scannedName: String?, scannedLicenseNo: String?, scannedExpiryDate: String?, fullText: String) {
            isScanningOCR = false
            var autoFillCount = 0

            if (!scannedName.isNullOrBlank()) {
                driverName = scannedName
                autoFillCount++
            }
            if (!scannedLicenseNo.isNullOrBlank()) {
                licenseNumber = scannedLicenseNo
                autoFillCount++
            }
            if (!scannedExpiryDate.isNullOrBlank()) {
                licenseExpiry = scannedExpiryDate
                autoFillCount++
            }

            if (autoFillCount > 0) {
                ocrSuccessMsg = "✓ Google ML Kit OCR scanned & autofilled $autoFillCount fields!"
            } else {
                ocrSuccessMsg = "ML Kit OCR scanned text: " + fullText.take(50) + "..."
            }
        }

        // Camera Live Photo Capture Launcher
        val cameraLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.TakePicturePreview()
        ) { bitmap: Bitmap? ->
            if (bitmap != null) {
                licenseBitmap = bitmap
                licenseImageUri = null
                isScanningOCR = true
                ocrSuccessMsg = ""
                dialogError = ""

                scanLicenseBitmapWithMLKit(
                    bitmap = bitmap,
                    onScanned = { sName, sLicenseNo, sExpiryDate, rawText ->
                        handleOcrResult(sName, sLicenseNo, sExpiryDate, rawText)
                    },
                    onError = { err ->
                        isScanningOCR = false
                        dialogError = "Camera OCR Error: $err"
                    }
                )
            }
        }

        // System Gallery Image Picker Launcher
        val licenseImagePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            if (uri != null) {
                licenseImageUri = uri
                licenseBitmap = null
                isScanningOCR = true
                ocrSuccessMsg = ""
                dialogError = ""

                scanLicenseWithMLKit(
                    context = context,
                    imageUri = uri,
                    onScanned = { sName, sLicenseNo, sExpiryDate, rawText ->
                        handleOcrResult(sName, sLicenseNo, sExpiryDate, rawText)
                    },
                    onError = { err ->
                        isScanningOCR = false
                        dialogError = "Gallery OCR Error: $err"
                    }
                )
            }
        }

        val textFieldColors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.Black,
            unfocusedTextColor = Color.Black,
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedLabelColor = AdminGreen,
            unfocusedLabelColor = Color.DarkGray,
            focusedBorderColor = AdminGreen,
            unfocusedBorderColor = Color.LightGray
        )

        val statusOptions = listOf("On Duty", "Off Duty", "On Leave")

        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                editingDriver = null
            },
            containerColor = Color.White,
            titleContentColor = Color.Black,
            textContentColor = Color.Black,
            title = {
                Text(if (isEditing) "Edit Driver Details" else "Add New Driver", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color.Black)
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = Color(0xFFE8F5E9),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Driver ID: $autoDriverId",
                            modifier = Modifier.padding(10.dp),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = AdminGreen
                        )
                    }

                    // GOOGLE ML KIT OCR LICENSE SCANNER & CAMERA COMPONENT
                    Text("Scan License with Google ML Kit OCR", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = AdminGreen)
                    
                    Surface(
                        color = Color(0xFFF5F8F6),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, AdminGreen),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (licenseBitmap != null) {
                                    androidx.compose.foundation.Image(
                                        bitmap = licenseBitmap!!.asImageBitmap(),
                                        contentDescription = "Camera Photo",
                                        modifier = Modifier
                                            .size(50.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                } else if (licenseImageUri != null) {
                                    AsyncImage(
                                        model = licenseImageUri,
                                        contentDescription = "Gallery License Image",
                                        modifier = Modifier
                                            .size(50.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Surface(
                                        color = Color(0xFFE8F5E9),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.size(50.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(imageVector = Icons.Default.DocumentScanner, contentDescription = "Scan", tint = AdminGreen, modifier = Modifier.size(26.dp))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (licenseBitmap != null || licenseImageUri != null) "License Image Loaded" else "Scan Driver License",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                    Text(
                                        text = if (isScanningOCR) "Scanning text with ML Kit OCR..." else "Auto-extracts Name, 11-char License No & Expiry",
                                        fontSize = 11.sp,
                                        color = if (isScanningOCR) AdminGreen else Color.Gray,
                                        fontWeight = if (isScanningOCR) FontWeight.Bold else FontWeight.Normal
                                    )
                                }

                                if (isScanningOCR) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = AdminGreen, strokeWidth = 2.dp)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Dual Action Buttons: Take Photo with Camera OR Pick from Gallery
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { cameraLauncher.launch(null) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, AdminGreen),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AdminGreen)
                                ) {
                                    Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Take Photo", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = { licenseImagePickerLauncher.launch("image/*") },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, AdminGreen),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AdminGreen)
                                ) {
                                    Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("From Gallery", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    if (ocrSuccessMsg.isNotEmpty()) {
                        Surface(color = Color(0xFFE8F5E9), shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(ocrSuccessMsg, fontSize = 11.sp, color = AdminGreen, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))
                        }
                    }

                    // Driver Login Account Credentials Header
                    Text("Driver Account Login Credentials", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = AdminGreen)

                    // Driver Login Email
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Driver Login Email Address", color = Color.DarkGray) },
                        placeholder = { Text("e.g. driver.juan@transitgo.com", color = Color.Gray) },
                        singleLine = true,
                        colors = textFieldColors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Driver Login Password
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Driver Login Password", color = Color.DarkGray) },
                        placeholder = { Text("Minimum 8 characters", color = Color.Gray) },
                        singleLine = true,
                        colors = textFieldColors,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            TextButton(onClick = { passwordVisible = !passwordVisible }) {
                                Text(if (passwordVisible) "Hide" else "Show", color = AdminGreen, fontSize = 11.sp)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color(0xFFEEEEEE))

                    // Driver Name
                    OutlinedTextField(
                        value = driverName,
                        onValueChange = { driverName = it },
                        label = { Text("Driver Name", color = Color.DarkGray) },
                        placeholder = { Text("e.g. Carlos Santos", color = Color.Gray) },
                        singleLine = true,
                        colors = textFieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Contact Number
                    OutlinedTextField(
                        value = contactNumber,
                        onValueChange = { contactNumber = it },
                        label = { Text("Contact Number", color = Color.DarkGray) },
                        placeholder = { Text("e.g. 0917-123-4567", color = Color.Gray) },
                        singleLine = true,
                        colors = textFieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // License Number
                    OutlinedTextField(
                        value = licenseNumber,
                        onValueChange = { licenseNumber = it },
                        label = { Text("License Number (11 characters)", color = Color.DarkGray) },
                        placeholder = { Text("e.g. N01-18-123456", color = Color.Gray) },
                        singleLine = true,
                        colors = textFieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // License Expiry with Live Expiration Validation Badge
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = licenseExpiry,
                            onValueChange = { licenseExpiry = it },
                            label = { Text("License Expiry Date (YYYY-MM-DD)", color = Color.DarkGray) },
                            placeholder = { Text("e.g. 2028-12-31", color = Color.Gray) },
                            singleLine = true,
                            colors = textFieldColors,
                            trailingIcon = {
                                Icon(
                                    imageVector = if (isExpiryValid) Icons.Default.Verified else Icons.Default.Error,
                                    contentDescription = "Validation",
                                    tint = if (isExpiryValid) AdminGreen else Color(0xFFC62828)
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Real-Time Expiration Date Badge
                        Surface(
                            color = if (isExpiryValid) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isExpiryValid) Icons.Default.Verified else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = if (isExpiryValid) AdminGreen else Color(0xFFC62828),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = expiryValidationMsg,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isExpiryValid) AdminGreen else Color(0xFFC62828)
                                )
                            }
                        }
                    }

                    // Assigned Bus Selection
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = assignedBus,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Assigned Bus", color = Color.DarkGray) },
                            trailingIcon = {
                                IconButton(onClick = { busDropdownExpanded = !busDropdownExpanded }) {
                                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "Select Bus")
                                }
                            },
                            colors = textFieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { busDropdownExpanded = true }
                        )

                        DropdownMenu(
                            expanded = busDropdownExpanded,
                            onDismissRequest = { busDropdownExpanded = false },
                            modifier = Modifier.background(Color.White)
                        ) {
                            registeredBuses.forEach { bus ->
                                DropdownMenuItem(
                                    text = { Text(bus, color = Color.Black) },
                                    onClick = {
                                        assignedBus = bus
                                        busDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Employment Status
                    Text("Employment Status", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.Black)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        statusOptions.forEach { status ->
                            FilterChip(
                                selected = selectedStatus == status,
                                onClick = { selectedStatus = status },
                                label = { Text(status, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AdminGreen,
                                    selectedLabelColor = Color.White,
                                    containerColor = Color(0xFFF0F4F1),
                                    labelColor = Color.Black
                                )
                            )
                        }
                    }

                    if (dialogError.isNotEmpty()) {
                        Text(dialogError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isEditing && targetDriver != null) {
                        Button(
                            onClick = {
                                driverToDelete = targetDriver
                                showAddDialog = false
                                editingDriver = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFEBEE)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFC62828), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete", color = Color(0xFFC62828), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            showAddDialog = false
                            editingDriver = null
                        }) {
                            Text("Cancel", color = Color.DarkGray, fontWeight = FontWeight.Medium)
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Button(
                            onClick = {
                                if (driverName.isBlank()) {
                                    dialogError = "Please enter driver name"
                                    return@Button
                                }

                                if (email.isNotBlank() && password.length < 8) {
                                    dialogError = "Driver login password must be at least 8 characters"
                                    return@Button
                                }

                                // Validate License Expiration before saving
                                val (isValid, expiryMsg) = validateLicenseExpiry(licenseExpiry)
                                if (!isValid) {
                                    dialogError = expiryMsg
                                    return@Button
                                }

                                val newDriver = DriverRecord(
                                    driverId = autoDriverId,
                                    driverName = driverName.trim(),
                                    contactNumber = contactNumber.trim(),
                                    licenseNumber = licenseNumber.trim(),
                                    licenseExpiry = licenseExpiry.trim(),
                                    assignedBus = assignedBus,
                                    employmentStatus = selectedStatus,
                                    email = email.trim(),
                                    password = password
                                )

                                // Save or update driver directly in Cloud Firestore
                                val existingIndex = driverList.indexOfFirst { it.driverId == autoDriverId }
                                if (existingIndex != -1) {
                                    driverList[existingIndex] = newDriver
                                } else {
                                    driverList.add(0, newDriver)
                                }

                                DatabaseManager.saveDriverDetails(
                                    driverId = autoDriverId,
                                    driverName = driverName.trim(),
                                    contactNumber = contactNumber.trim(),
                                    licenseNumber = licenseNumber.trim(),
                                    licenseExpiry = licenseExpiry.trim(),
                                    assignedBus = assignedBus,
                                    employmentStatus = selectedStatus,
                                    email = email.trim(),
                                    password = password
                                )

                                // Register Driver Account in Firebase Auth & Firestore Users Collection
                                if (email.isNotBlank() && password.isNotBlank()) {
                                    try {
                                        FirebaseAuth.getInstance()
                                            .createUserWithEmailAndPassword(email.trim(), password)
                                            .addOnCompleteListener { task ->
                                                val uid = task.result?.user?.uid ?: autoDriverId
                                                val driverUser = user(
                                                    userId = uid,
                                                    fullname = driverName.trim(),
                                                    email = email.trim(),
                                                    password = password,
                                                    role = "Driver"
                                                )
                                                DatabaseManager.saveUser(driverUser)
                                            }
                                    } catch (_: Exception) {
                                        val driverUser = user(
                                            userId = autoDriverId,
                                            fullname = driverName.trim(),
                                            email = email.trim(),
                                            password = password,
                                            role = "Driver"
                                        )
                                        DatabaseManager.saveUser(driverUser)
                                    }
                                }

                                // Update assigned driver in Bus Management if a bus was assigned
                                if (assignedBus != "Unassigned") {
                                    val targetBusObj = busObjectsList.find { b ->
                                        val bName = if (b.busNumber.isNotBlank()) b.busNumber else "BUS-${b.busId}"
                                        val label = "$bName (${if (b.plateNumber.isNotBlank()) b.plateNumber else b.busId})"
                                        label == assignedBus || b.busNumber.equals(assignedBus, ignoreCase = true)
                                    }
                                    if (targetBusObj != null) {
                                        targetBusObj.assignedDriver = driverName.trim()
                                        DatabaseManager.saveBus(targetBusObj)
                                    }
                                }

                                Toast.makeText(context, "Driver $driverName saved to Cloud Database!", Toast.LENGTH_SHORT).show()
                                showAddDialog = false
                                editingDriver = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AdminGreen)
                        ) {
                            Text("Save Driver", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        )
    }
}

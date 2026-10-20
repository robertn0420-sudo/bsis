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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.myfirstapp.DatabaseManager
import com.example.myfirstapp.DriverRecord
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
    
    // Flexible date regex for YYYY-MM-DD, YYYY/MM/DD, DD/MM/YYYY, 31 DEC 2028, etc.
    val dateRegex = Regex(
        """\b(\d{4}[-/.]\d{1,2}[-/.]\d{1,2}|\d{1,2}[-/.]\d{1,2}[-/.]\d{4}|\d{1,2}\s+[A-Za-z]{3,9}\s+\d{4}|[A-Za-z]{3,9}\s+\d{1,2},?\s+\d{4}|\d{1,2}[-/.][A-Za-z]{3,9}[-/.]\d{4})\b""",
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
                        !nextUpper.contains("EXPIRATION") && !dateRegex.containsMatchIn(nextLine) &&
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
                var rawMatch = strictMatch.value.uppercase().replace(" ", "-")
                if (rawMatch.length == 11 && !rawMatch.contains("-")) {
                    rawMatch = "${rawMatch.substring(0, 3)}-${rawMatch.substring(3, 5)}-${rawMatch.substring(5)}"
                }
                extractedLicenseNo = rawMatch
            } else {
                val genMatch = general11CharRegex.find(line)
                if (genMatch != null) {
                    var rawMatch = genMatch.value.uppercase().replace(" ", "-")
                    if (rawMatch.length == 11 && !rawMatch.contains("-")) {
                        rawMatch = "${rawMatch.substring(0, 3)}-${rawMatch.substring(3, 5)}-${rawMatch.substring(5)}"
                    }
                    extractedLicenseNo = rawMatch
                }
            }
        }
    }

    // --- 2. HIGH-PRECISION EXPIRATION DATE EXTRACTION ---
    // PASS 1: Explicit Expiration Label check (check same line OR next line)
    for (i in lines.indices) {
        val line = lines[i]
        val upper = line.uppercase()

        // Expiration label keywords (e.g. "4b. EXPIRATION DATE", "EXP DATE", "VALID UNTIL", "EXPIRES")
        val isExpiryLabel = (upper.contains("EXPIRATION") || upper.contains("EXP") ||
                upper.contains("4B") || upper.contains("VALID UNTIL") || upper.contains("VALID THRU") ||
                upper.contains("EXPIRES")) &&
                !upper.contains("BIRTH") && !upper.contains("DOB") && !upper.contains("ISSUE") && !upper.contains("4A")

        if (isExpiryLabel) {
            // Check same line first
            val sameLineMatch = dateRegex.find(line)
            if (sameLineMatch != null) {
                extractedExpiryDate = normalizeDateFormat(sameLineMatch.value)
                break
            }

            // Check next line if same line has no date
            if (i + 1 < lines.size) {
                val nextLine = lines[i + 1].trim()
                val nextUpper = nextLine.uppercase()
                if (!nextUpper.contains("BIRTH") && !nextUpper.contains("DOB") && !nextUpper.contains("ISSUE")) {
                    val nextLineMatch = dateRegex.find(nextLine)
                    if (nextLineMatch != null) {
                        extractedExpiryDate = normalizeDateFormat(nextLineMatch.value)
                        break
                    }
                }
            }
        }
    }

    // PASS 2: Fallback if explicit label was missed/split - find future/latest date excluding Birth/Issue lines
    if (extractedExpiryDate == null) {
        val candidateDates = mutableListOf<String>()
        for (line in lines) {
            val upper = line.uppercase()
            // Strictly exclude lines that are birth dates, issue dates, or LTO codes 2a/2b/3/4a
            if (upper.contains("BIRTH") || upper.contains("DOB") || upper.contains("DATE OF BIRTH") ||
                upper.contains("ISSUE") || upper.contains("ISSUED") || upper.contains("4A")) {
                continue
            }

            val dateMatch = dateRegex.find(line)
            if (dateMatch != null) {
                val normalized = normalizeDateFormat(dateMatch.value)
                candidateDates.add(normalized)
            }
        }

        if (candidateDates.isNotEmpty()) {
            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            val futureOrCurrentDates = candidateDates.filter { dateStr ->
                val yearPart = dateStr.take(4).toIntOrNull() ?: 0
                yearPart >= (currentYear - 2) // allow recent/upcoming/future dates
            }

            extractedExpiryDate = if (futureOrCurrentDates.isNotEmpty()) {
                futureOrCurrentDates.maxOrNull()
            } else {
                candidateDates.maxOrNull()
            }
        }
    }

    // Fallbacks for Driver Name if header line was not found
    if (extractedName == null) {
        for (line in lines) {
            val upper = line.uppercase()
            if (line.contains(",") && !upper.contains("REPUBLIC") && !upper.contains("PHILIPPINES") &&
                !upper.contains("TRANSPORTATION") && !upper.contains("ADDRESS") && !upper.contains("OFFICE")) {
                val clean = line.filter { it.isLetter() || it.isWhitespace() || it == ',' }.trim()
                if (clean.length >= 5) {
                    extractedName = formatDriverName(clean)
                    break
                }
            }
        }
    }

    onScanned(extractedName, extractedLicenseNo, extractedExpiryDate, fullText)
}

/**
 * Formats "DELA CRUZ, JUAN PEDRO MARCOS" into "JUAN PEDRO MARCOS DELA CRUZ".
 */
fun formatDriverName(rawName: String): String {
    val clean = rawName.trim().replace(Regex("""\s+"""), " ")
    if (clean.contains(",")) {
        val parts = clean.split(",")
        if (parts.size >= 2) {
            val lastName = parts[0].trim()
            val firstMiddle = parts[1].trim()
            return "$firstMiddle $lastName".trim()
        }
    }
    return clean
}

/**
 * Scans License Image from Uri (Gallery) using Google ML Kit
 */
fun scanLicenseWithMLKit(
    context: Context,
    imageUri: Uri,
    onScanned: (name: String?, licenseNo: String?, expiryDate: String?, fullRawText: String) -> Unit,
    onError: (String) -> Unit
) {
    try {
        val inputImage = InputImage.fromFilePath(context, imageUri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                parseLicenseVisionText(visionText.text, onScanned)
            }
            .addOnFailureListener { e ->
                onError(e.localizedMessage ?: "Failed to scan license image with ML Kit")
            }
    } catch (e: Exception) {
        onError(e.localizedMessage ?: "Error opening image for OCR")
    }
}

/**
 * Scans License Bitmap from Live Camera Capture using Google ML Kit
 */
fun scanLicenseBitmapWithMLKit(
    bitmap: Bitmap,
    onScanned: (name: String?, licenseNo: String?, expiryDate: String?, fullRawText: String) -> Unit,
    onError: (String) -> Unit
) {
    try {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                parseLicenseVisionText(visionText.text, onScanned)
            }
            .addOnFailureListener { e ->
                onError(e.localizedMessage ?: "Failed to scan camera image with ML Kit")
            }
    } catch (e: Exception) {
        onError(e.localizedMessage ?: "Error processing camera bitmap for OCR")
    }
}

/**
 * Normalizes different date formats to standard YYYY-MM-DD
 */
fun normalizeDateFormat(rawDate: String): String {
    val clean = rawDate.replace(".", "-").replace("/", "-").replace(",", "").trim()

    val inputFormats = listOf(
        SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH),
        SimpleDateFormat("yyyy-M-d", Locale.ENGLISH),
        SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH),
        SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH),
        SimpleDateFormat("MMM dd yyyy", Locale.ENGLISH),
        SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH),
        SimpleDateFormat("d-M-yyyy", Locale.ENGLISH),
        SimpleDateFormat("MM-dd-yyyy", Locale.ENGLISH),
        SimpleDateFormat("M-d-yyyy", Locale.ENGLISH)
    )

    for (fmt in inputFormats) {
        try {
            fmt.isLenient = false
            val parsed = fmt.parse(clean)
            if (parsed != null) {
                return SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(parsed)
            }
        } catch (_: Exception) {}
    }

    val parts = clean.split("-", " ").filter { it.isNotBlank() }
    if (parts.size == 3) {
        val p0 = parts[0]
        val p1 = parts[1]
        val p2 = parts[2]

        if (p0.length == 4 && p0.all { it.isDigit() }) {
            val year = p0
            val month = p1.padStart(2, '0')
            val day = p2.padStart(2, '0')
            return "$year-$month-$day"
        } else if (p2.length == 4 && p2.all { it.isDigit() }) {
            val year = p2
            val v1 = p0.toIntOrNull() ?: 1
            val v2 = p1.toIntOrNull() ?: 1
            val (month, day) = if (v1 > 12) {
                Pair(v2.toString().padStart(2, '0'), v1.toString().padStart(2, '0'))
            } else if (v2 > 12) {
                Pair(v1.toString().padStart(2, '0'), v2.toString().padStart(2, '0'))
            } else {
                Pair(v1.toString().padStart(2, '0'), v2.toString().padStart(2, '0'))
            }
            return "$year-$month-$day"
        }
    }

    return clean
}

/**
 * Validates License Expiration Date against current system date.
 */
fun validateLicenseExpiry(expiryDateStr: String): Pair<Boolean, String> {
    if (expiryDateStr.isBlank()) {
        return Pair(false, "License expiration date is required.")
    }

    val normalized = normalizeDateFormat(expiryDateStr)
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
    sdf.isLenient = false

    val parsedDate = try {
        sdf.parse(normalized)
    } catch (_: Exception) {
        null
    }

    if (parsedDate == null) {
        return Pair(false, "Invalid date format! Please use YYYY-MM-DD (e.g. 2028-12-31).")
    }

    val calendarToday = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }

    if (parsedDate.before(calendarToday.time)) {
        val expiredFormatted = sdf.format(parsedDate)
        return Pair(false, "LICENSE EXPIRED! This license expired on $expiredFormatted and cannot be registered.")
    }

    return Pair(true, "License is Valid and Active")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverManagementScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var editingDriver by remember { mutableStateOf<DriverRecord?>(null) }
    var driverToDelete by remember { mutableStateOf<DriverRecord?>(null) }

    val registeredBuses = remember { mutableStateListOf("Unassigned") }
    val busObjectsList = remember { mutableStateListOf<com.example.myfirstapp.Bus>() }
    val driverList = remember { mutableStateListOf<DriverRecord>() }

    LaunchedEffect(Unit) {
        DatabaseManager.listenToDrivers(
            onUpdate = { fetchedDrivers ->
                driverList.clear()
                driverList.addAll(fetchedDrivers)
            }
        )

        DatabaseManager.listenToBuses(
            onUpdate = { buses ->
                busObjectsList.clear()
                busObjectsList.addAll(buses)
                registeredBuses.clear()
                registeredBuses.add("Unassigned")
                buses.forEach { b ->
                    val bName = if (b.busNumber.isNotBlank()) b.busNumber else "BUS-${b.busId}"
                    registeredBuses.add("$bName (${if (b.plateNumber.isNotBlank()) b.plateNumber else b.busId})")
                }
            }
        )
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
                Text("Driver Management", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (driverList.isEmpty()) {
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
                        Text("Tap + button below to add a driver", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(driverList) { driver ->
                        val (isValidExpiry, _) = remember(driver.licenseExpiry) { validateLicenseExpiry(driver.licenseExpiry) }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { editingDriver = driver },
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
                                    Text(driver.driverName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
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
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Driver ID: ${driver.driverId}", fontSize = 13.sp, color = Color.DarkGray)
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

    // DELETE DRIVER CONFIRMATION DIALOG
    if (driverToDelete != null) {
        val targetToDelete = driverToDelete!!
        AlertDialog(
            onDismissRequest = { driverToDelete = null },
            containerColor = Color.White,
            titleContentColor = Color.Black,
            textContentColor = Color.Black,
            title = {
                Text("Delete Driver", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFFC62828))
            },
            text = {
                Text("Are you sure you want to delete ${targetToDelete.driverName} (${targetToDelete.driverId})? This action will remove the record permanently from Cloud Firestore.", fontSize = 14.sp, color = Color.DarkGray)
            },
            confirmButton = {
                Button(
                    onClick = {
                        DatabaseManager.deleteDriver(
                            driverId = targetToDelete.driverId,
                            onSuccess = {
                                Toast.makeText(context, "Driver ${targetToDelete.driverName} deleted!", Toast.LENGTH_SHORT).show()
                            },
                            onFailure = { e ->
                                Toast.makeText(context, "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        )
                        driverList.remove(targetToDelete)
                        driverToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
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
                Button(
                    onClick = {
                        if (driverName.isBlank()) {
                            dialogError = "Please enter driver name"
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
                            employmentStatus = selectedStatus
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
                            employmentStatus = selectedStatus
                        )

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
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddDialog = false
                    editingDriver = null
                }) {
                    Text("Cancel", color = Color.DarkGray, fontWeight = FontWeight.Medium)
                }
            }
        )
    }
}

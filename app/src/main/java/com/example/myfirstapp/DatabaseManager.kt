package com.example.myfirstapp

import com.google.firebase.firestore.FirebaseFirestore

data class DriverRecord(
    val driverId: String,
    var driverName: String,
    var contactNumber: String,
    var licenseNumber: String,
    var licenseExpiry: String,
    var assignedBus: String,
    var employmentStatus: String,
    var email: String = "",
    var password: String = ""
)

object DatabaseManager {
    private val db: FirebaseFirestore by lazy {
        val instance = FirebaseFirestore.getInstance()
        try {
            val settings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(com.google.firebase.firestore.PersistentCacheSettings.newBuilder().build())
                .build()
            instance.firestoreSettings = settings
        } catch (_: Exception) { }
        instance
    }

    // Save or update User in Firestore
    fun saveUser(
        user: user,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        val userData = hashMapOf(
            "userId" to user.userId,
            "fullname" to user.fullname,
            "email" to user.email,
            "role" to user.role
        )
        db.collection("users").document(user.userId)
            .set(userData)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e) }
    }

    // Fetch User Profile and Role from Firestore by userId
    fun getUser(
        userId: String,
        onSuccess: (user?) -> Unit,
        onFailure: (Exception) -> Unit = {}
    ) {
        if (userId.isBlank()) {
            onSuccess(null)
            return
        }
        db.collection("users").document(userId)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val uId = doc.getString("userId") ?: doc.id
                    val fullname = doc.getString("fullname") ?: "Transit User"
                    val email = doc.getString("email") ?: ""
                    val role = doc.getString("role") ?: "Passenger"
                    onSuccess(user(uId, fullname, email, "", role))
                } else {
                    onSuccess(null)
                }
            }
            .addOnFailureListener { e -> onFailure(e) }
    }

    // Save or update Bus in Firestore
    fun saveBus(
        bus: Bus,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        val busData = hashMapOf(
            "busId" to bus.busId,
            "busNumber" to bus.busNumber,
            "plateNumber" to bus.plateNumber,
            "busType" to bus.busType,
            "capacity" to bus.capacity,
            "assignedDriver" to bus.assignedDriver,
            "operator" to bus.operator,
            "route" to bus.route,
            "busStop" to bus.busStop,
            "departureTime" to bus.departureTime,
            "status" to bus.status,
            "imageUrl" to bus.imageUrl
        )
        db.collection("buses").document(bus.busId)
            .set(busData)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e) }
    }

    // Real-time live listener for Buses from Cloud Firestore
    fun listenToBuses(
        onUpdate: (List<Bus>) -> Unit,
        onError: (Exception) -> Unit = {}
    ) {
        db.collection("buses").addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val buses = snapshot.documents.mapNotNull { doc ->
                    val busId = doc.getString("busId") ?: doc.id
                    val busNumber = doc.getString("busNumber") ?: ""
                    val capacity = (doc.getLong("capacity") ?: 50L).toInt()
                    val status = doc.getString("status") ?: "Active"
                    val plateNumber = doc.getString("plateNumber") ?: ""
                    val busType = doc.getString("busType") ?: "Air-conditioned"
                    val assignedDriver = doc.getString("assignedDriver") ?: "Unassigned"
                    val operator = doc.getString("operator") ?: ""
                    val route = doc.getString("route") ?: ""
                    val busStop = doc.getString("busStop") ?: ""
                    val departureTime = doc.getString("departureTime") ?: "08:00"
                    val imageUrl = doc.getString("imageUrl") ?: ""

                    Bus(
                        busId = busId,
                        busNumber = busNumber,
                        capacity = capacity,
                        status = status,
                        plateNumber = plateNumber,
                        busType = busType,
                        assignedDriver = assignedDriver,
                        operator = operator,
                        route = route,
                        busStop = busStop,
                        departureTime = departureTime,
                        imageUrl = imageUrl
                    )
                }
                onUpdate(buses)
            }
        }
    }

    // Delete Bus from Firestore
    fun deleteBus(
        busId: String,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        db.collection("buses").document(busId)
            .delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e) }
    }

    // Save or update Driver details in Firestore
    fun saveDriverDetails(
        driverId: String,
        driverName: String,
        contactNumber: String,
        licenseNumber: String,
        licenseExpiry: String,
        assignedBus: String,
        employmentStatus: String,
        email: String = "",
        password: String = "",
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        val driverData = hashMapOf(
            "driverId" to driverId,
            "driverName" to driverName,
            "contactNumber" to contactNumber,
            "licenseNumber" to licenseNumber,
            "licenseExpiry" to licenseExpiry,
            "assignedBus" to assignedBus,
            "employmentStatus" to employmentStatus,
            "email" to email,
            "password" to password
        )
        db.collection("drivers").document(driverId)
            .set(driverData)
            .addOnSuccessListener {
                if (email.isNotBlank() && password.isNotBlank()) {
                    val driverUser = user(
                        userId = driverId,
                        fullname = driverName,
                        email = email,
                        password = password,
                        role = "Driver"
                    )
                    saveUser(driverUser)
                }
                onSuccess()
            }
            .addOnFailureListener { e -> onFailure(e) }
    }

    // Real-time live listener for Drivers from Cloud Firestore
    fun listenToDrivers(
        onUpdate: (List<DriverRecord>) -> Unit,
        onError: (Exception) -> Unit = {}
    ) {
        db.collection("drivers").addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val drivers = snapshot.documents.mapNotNull { doc ->
                    val driverId = doc.getString("driverId") ?: doc.id
                    val driverName = doc.getString("driverName") ?: ""
                    val contactNumber = doc.getString("contactNumber") ?: ""
                    val licenseNumber = doc.getString("licenseNumber") ?: ""
                    val licenseExpiry = doc.getString("licenseExpiry") ?: ""
                    val assignedBus = doc.getString("assignedBus") ?: "Unassigned"
                    val employmentStatus = doc.getString("employmentStatus") ?: "On Duty"
                    val email = doc.getString("email") ?: ""
                    val password = doc.getString("password") ?: ""

                    DriverRecord(
                        driverId = driverId,
                        driverName = driverName,
                        contactNumber = contactNumber,
                        licenseNumber = licenseNumber,
                        licenseExpiry = licenseExpiry,
                        assignedBus = assignedBus,
                        employmentStatus = employmentStatus,
                        email = email,
                        password = password
                    )
                }
                onUpdate(drivers)
            }
        }
    }

    // Delete Driver from Firestore
    fun deleteDriver(
        driverId: String,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        db.collection("drivers").document(driverId)
            .delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e) }
    }

    // Save or update BusStop / Terminal Hub in Firestore
    fun saveBusStop(
        stop: BusStop,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        val stopData = hashMapOf(
            "stopId" to stop.stopId,
            "stopName" to stop.stopName,
            "location" to stop.location,
            "latitude" to stop.latitude,
            "longitude" to stop.longitude,
            "baysCount" to stop.baysCount,
            "activeBays" to stop.activeBays,
            "operatingHours" to stop.operatingHours,
            "status" to stop.status,
            "imageUrl" to stop.imageUrl
        )
        db.collection("bus_stops").document(stop.stopId)
            .set(stopData)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e) }
    }

    // Real-time live listener for BusStops / Terminal Hubs from Cloud Firestore
    fun listenToBusStops(
        onUpdate: (List<BusStop>) -> Unit,
        onError: (Exception) -> Unit = {}
    ) {
        db.collection("bus_stops").addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val stops = snapshot.documents.mapNotNull { doc ->
                    val stopId = doc.getString("stopId") ?: doc.id
                    val stopName = doc.getString("stopName") ?: ""
                    val location = doc.getString("location") ?: ""
                    val latitude = doc.getDouble("latitude") ?: 14.5995
                    val longitude = doc.getDouble("longitude") ?: 120.9842
                    val baysCount = (doc.getLong("baysCount") ?: 8L).toInt()
                    val activeBays = (doc.getLong("activeBays") ?: 5L).toInt()
                    val operatingHours = doc.getString("operatingHours") ?: "04:00 AM - 11:00 PM"
                    val status = doc.getString("status") ?: "Active"
                    val imageUrl = doc.getString("imageUrl") ?: ""

                    BusStop(
                        stopId = stopId,
                        stopName = stopName,
                        location = location,
                        latitude = latitude,
                        longitude = longitude,
                        baysCount = baysCount,
                        activeBays = activeBays,
                        operatingHours = operatingHours,
                        status = status,
                        imageUrl = imageUrl
                    )
                }
                onUpdate(stops)
            }
        }
    }

    // Delete BusStop / Terminal Hub from Firestore
    fun deleteBusStop(
        stopId: String,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        db.collection("bus_stops").document(stopId)
            .delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e) }
    }

    // Save or update BusRoute in Firestore
    fun saveBusRoute(
        route: BusRoute,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        val routeData = hashMapOf(
            "routeId" to route.routeId,
            "routeName" to route.routeName,
            "startPoint" to route.startPoint,
            "destination" to route.destination,
            "status" to route.status,
            "startLat" to route.startLat,
            "startLng" to route.startLng,
            "destLat" to route.destLat,
            "destLng" to route.destLng
        )
        db.collection("bus_routes").document(route.routeId)
            .set(routeData)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e) }
    }

    // Real-time live listener for Routes from Cloud Firestore
    fun listenToRoutes(
        onUpdate: (List<BusRoute>) -> Unit,
        onError: (Exception) -> Unit = {}
    ) {
        db.collection("bus_routes").addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val routes = snapshot.documents.mapNotNull { doc ->
                    val routeId = doc.getString("routeId") ?: doc.id
                    val routeName = doc.getString("routeName") ?: ""
                    val startPoint = doc.getString("startPoint") ?: ""
                    val destination = doc.getString("destination") ?: ""
                    val status = doc.getString("status") ?: "Active"
                    val startLat = doc.getDouble("startLat") ?: 0.0
                    val startLng = doc.getDouble("startLng") ?: 0.0
                    val destLat = doc.getDouble("destLat") ?: 0.0
                    val destLng = doc.getDouble("destLng") ?: 0.0

                    BusRoute(
                        routeId = routeId,
                        routeName = routeName,
                        startPoint = startPoint,
                        destination = destination,
                        status = status,
                        startLat = startLat,
                        startLng = startLng,
                        destLat = destLat,
                        destLng = destLng
                    )
                }
                onUpdate(routes)
            }
        }
    }

    // Delete Bus Route from Firestore
    fun deleteBusRoute(
        routeId: String,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        db.collection("bus_routes").document(routeId)
            .delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e) }
    }

    // Save or update BusSchedule in Firestore
    fun saveBusSchedule(
        schedule: BusSchedule,
        driverName: String = "",
        scheduleDate: String = "",
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        val scheduleData = hashMapOf(
            "scheduleId" to schedule.scheduleId,
            "busNumber" to schedule.bus.busNumber,
            "routeName" to schedule.route.routeName,
            "driverName" to if (driverName.isNotBlank()) driverName else schedule.bus.assignedDriver,
            "departureTime" to schedule.departureTime,
            "arrivalTime" to schedule.arrivalTime,
            "status" to schedule.status,
            "scheduleDate" to scheduleDate
        )
        db.collection("bus_schedules").document(schedule.scheduleId)
            .set(scheduleData)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e) }
    }

    // Real-time live listener for Schedules from Cloud Firestore
    fun listenToSchedules(
        onUpdate: (List<BusSchedule>) -> Unit,
        onError: (Exception) -> Unit = {}
    ) {
        db.collection("bus_schedules").addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val schedules = snapshot.documents.mapNotNull { doc ->
                    val scheduleId = doc.getString("scheduleId") ?: doc.id
                    val busNumber = doc.getString("busNumber") ?: "BUS-001"
                    val routeName = doc.getString("routeName") ?: "City Terminal ➔ Airport Hub"
                    val driverName = doc.getString("driverName") ?: "Unassigned"
                    val departureTime = doc.getString("departureTime") ?: "10:00 AM"
                    val arrivalTime = doc.getString("arrivalTime") ?: "01:30 PM"
                    val status = doc.getString("status") ?: "Scheduled"
                    val scheduleDate = doc.getString("scheduleDate") ?: ""

                    val startP = if (routeName.contains(" ➔ ")) {
                        routeName.split(" ➔ ")[0].replace(Regex("""^Route\s+[A-Za-z0-9-]+:\s*""", RegexOption.IGNORE_CASE), "").trim()
                    } else "Central Terminal"

                    val destP = if (routeName.contains(" ➔ ")) {
                        routeName.split(" ➔ ")[1].trim()
                    } else "Airport Hub"

                    val busObj = Bus(scheduleId, busNumber, 50, status)
                    busObj.assignedDriver = driverName
                    busObj.operator = scheduleDate

                    val routeObj = BusRoute("R01", routeName, startP, destP)
                    val scheduleObj = BusSchedule(scheduleId, busObj, routeObj, departureTime, arrivalTime)
                    scheduleObj.status = status
                    scheduleObj
                }
                onUpdate(schedules)
            }
        }
    }

    // Delete Bus Schedule from Firestore
    fun deleteBusSchedule(
        scheduleId: String,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        db.collection("bus_schedules").document(scheduleId)
            .delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e) }
    }

    // Update schedule status (Accepted, Rejected, In Transit, Completed) in Cloud Firestore
    fun updateScheduleStatus(
        scheduleId: String,
        newStatus: String,
        driverName: String = "",
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        val updates = hashMapOf<String, Any>(
            "status" to newStatus
        )
        if (driverName.isNotBlank()) {
            updates["driverName"] = driverName
        }
        db.collection("bus_schedules").document(scheduleId)
            .update(updates)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e) }
    }

    // Save all sample OOP objects at once
    fun saveAllSampleData(
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        val sampleUser = user("U002", "Juan Dela Cruz", "juan@example.com", "pass123", "Passenger")
        val sampleStop = BusStop("S001", "Central Terminal", "Manila", 14.5995, 120.9842)
        val sampleRoute = BusRoute("R001", "Manila Route", "Central Manila", "City Center")
        sampleRoute.addBusStop(sampleStop)

        saveUser(sampleUser)
        saveBusStop(sampleStop)
        saveBusRoute(sampleRoute, onSuccess = onSuccess, onFailure = onFailure)
    }
}

package com.example.myfirstapp

class Bus(
    val busId: String,
    val busNumber: String,
    var capacity: Int,
    var status: String,
    var plateNumber: String = "",
    var busType: String = "Air-conditioned",
    var assignedDriver: String = "Unassigned",
    var operator: String = "",
    var route: String = "",
    var busStop: String = "",
    var departureTime: String = "08:00",
    var imageUrl: String = ""
) {
    fun updateStatus(newStatus: String) {
        status = newStatus
    }

    fun displayInfo() {
        println("Bus ID: $busId")
        println("Bus Number: $busNumber")
        println("Plate Number: $plateNumber")
        println("Bus Type: $busType")
        println("Capacity: $capacity")
        println("Assigned Driver: $assignedDriver")
        println("Operator: $operator")
        println("Route: $route")
        println("Bus Stop: $busStop")
        println("Departure Time: $departureTime")
        println("Status: $status")
        println("Image URL: $imageUrl")
    }
}

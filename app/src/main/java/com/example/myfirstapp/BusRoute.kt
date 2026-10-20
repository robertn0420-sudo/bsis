package com.example.myfirstapp

class BusRoute(
    val routeId: String,
    var routeName: String,
    var startPoint: String,
    var destination: String,
    var status: String = "Active",
    var startLat: Double = 0.0,
    var startLng: Double = 0.0,
    var destLat: Double = 0.0,
    var destLng: Double = 0.0
) {
    private val busStop = mutableListOf<BusStop>()

    fun addBusStop(stop: BusStop) {
        busStop.add(stop)
    }

    fun displayInfo() {
        println("Route ID: $routeId")
        println("Route Name: $routeName")
        println("Start: $startPoint ($startLat, $startLng)")
        println("Destination: $destination ($destLat, $destLng)")
        println("Status: $status")

        println("Bus Stop:")
        busStop.forEach {
            println("- ${it.stopName}")
        }
    }
}

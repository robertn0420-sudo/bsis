package com.example.myfirstapp

class BusStop(
    val stopId: String,
    var stopName: String,
    var location: String,
    var latitude: Double,
    var longitude: Double,
    var baysCount: Int = 8,
    var activeBays: Int = 5,
    var operatingHours: String = "04:00 AM - 11:00 PM",
    var status: String = "Active"
) {
    fun displayInfo(){
        println("Stop ID: $stopId")
        println("Stop Name: $stopName")
        println("Location: $location")
        println("Latitude: $latitude")
        println("Longitude: $longitude")
    }
}
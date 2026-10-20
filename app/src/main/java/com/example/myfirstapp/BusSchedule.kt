package com.example.myfirstapp

class BusSchedule(
    val scheduleId: String,
    var bus: Bus,
    var route: BusRoute,
    var departureTime: String,
    var arrivalTime: String
) {
    var status: String = "Scheduled"

    fun updateTime(
        newDeparture: String,
        newArrival: String
    ){
        departureTime = newDeparture
        arrivalTime = newArrival

    }

    fun displaySchedule(){
        println("Schedule ID: $scheduleId")
        println("Bus: ${bus.busNumber}")
        println("Route: ${route.routeName}")
        println("Departure: $departureTime")
        println("Arrival: $arrivalTime")
        println("Status: $status")
    }
}
package com.example.myfirstapp

class user(
    val userId: String,
    var fullname: String,
    var email: String,
    private var password: String,
    var role: String
) {
    fun verifyPassword(input: String): Boolean {
        return password == input
    }

    fun changePassword(
        oldPassword: String,
        newPassword: String
    ): Boolean {
        if (password == oldPassword &&
            newPassword.isNotBlank()){
            password = newPassword
            return true
        }
        return false
    }

    fun displayInfo() {
        println("User ID: $userId")
        println("Name: $fullname")
        println("Email: $email")
        println("Role: $role")
    }
}
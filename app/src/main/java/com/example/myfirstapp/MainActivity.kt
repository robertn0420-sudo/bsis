package com.example.myfirstapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myfirstapp.admin.AdminDashboardScreen
import com.example.myfirstapp.admin.LeafletMapViewScreen
import com.example.myfirstapp.ui.theme.MyFirstAppTheme
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyFirstAppTheme {
                MainApp()
            }
        }
    }
}

enum class ScreenState {
    LOGIN,
    SIGN_UP,
    HOME
}

@Composable
fun MainApp() {
    var currentScreen by remember { mutableStateOf(ScreenState.LOGIN) }
    var loggedInUser by remember { mutableStateOf<user?>(null) }

    // Auto-check if a user session is active (Auto-Login / Remember Me feature)
    LaunchedEffect(Unit) {
        val firebaseUser = FirebaseAuth.getInstance().currentUser
        if (firebaseUser != null) {
            DatabaseManager.getUser(
                userId = firebaseUser.uid,
                onSuccess = { fetchedUser ->
                    if (fetchedUser != null) {
                        loggedInUser = fetchedUser
                    } else {
                        loggedInUser = user(
                            userId = firebaseUser.uid,
                            fullname = firebaseUser.displayName ?: "Transit User",
                            email = firebaseUser.email ?: "",
                            password = "",
                            role = "Passenger"
                        )
                    }
                    currentScreen = ScreenState.HOME
                },
                onFailure = {
                    loggedInUser = user(
                        userId = firebaseUser.uid,
                        fullname = firebaseUser.displayName ?: "Transit User",
                        email = firebaseUser.email ?: "",
                        password = "",
                        role = "Passenger"
                    )
                    currentScreen = ScreenState.HOME
                }
            )
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when (currentScreen) {
            ScreenState.LOGIN -> {
                LoginScreen(
                    onLoginSuccess = { user ->
                        loggedInUser = user
                        DatabaseManager.saveUser(user)
                        currentScreen = ScreenState.HOME
                    },
                    onNavigateToSignUp = {
                        currentScreen = ScreenState.SIGN_UP
                    }
                )
            }
            ScreenState.SIGN_UP -> {
                SignUpScreen(
                    onSignUpSuccess = { user ->
                        loggedInUser = user
                        DatabaseManager.saveUser(user)
                        currentScreen = ScreenState.HOME
                    },
                    onNavigateToLogin = {
                        currentScreen = ScreenState.LOGIN
                    }
                )
            }
            ScreenState.HOME -> {
                val user = loggedInUser
                if (user != null) {
                    if (user.role == "Staff" || user.role == "Admin") {
                        AdminDashboardScreen(
                            user = user,
                            onLogout = {
                                FirebaseAuth.getInstance().signOut()
                                loggedInUser = null
                                currentScreen = ScreenState.LOGIN
                            }
                        )
                    } else {
                        HomeScreen(
                            user = user,
                            onLogout = {
                                FirebaseAuth.getInstance().signOut()
                                loggedInUser = null
                                currentScreen = ScreenState.LOGIN
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    user: user,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var dbStatus by remember { mutableStateOf("Connecting to Cloud Firestore...") }
    var isSearchingDestination by remember { mutableStateOf(false) }
    var trackedRouteName by remember { mutableStateOf<String?>(null) }

    // Automatically sync user and sample data to Firebase Firestore
    LaunchedEffect(Unit) {
        DatabaseManager.saveAllSampleData(
            onSuccess = {
                dbStatus = "Cloud Database Connected & Synced"
            },
            onFailure = { e ->
                if (e.localizedMessage?.contains("PERMISSION_DENIED") == true) {
                    dbStatus = "Firestore Security Rules: Set rules to allow read, write in Firebase Console"
                } else {
                    dbStatus = "Cloud Sync Notice: ${e.localizedMessage}"
                }
            }
        )
    }

    if (trackedRouteName != null) {
        LeafletMapViewScreen(
            leafletId = "L001",
            routeName = trackedRouteName ?: "Manila Transit Route",
            onBack = { trackedRouteName = null }
        )
        return
    }

    if (isSearchingDestination) {
        PassengerSearchScreen(
            onBack = { isSearchingDestination = false },
            onDestinationSelected = { origin, destination ->
                isSearchingDestination = false
                trackedRouteName = "$origin ➔ $destination"
            }
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF5F8F6))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // TransitGo Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = TransitGreen)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Welcome back, ${user.fullname}!",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Role: ${user.role}  •  Email: ${user.email}",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Role-Specific Dashboard Content based on UI designs
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (user.role != "Driver") {
                        isSearchingDestination = true
                    }
                },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (user.role == "Driver") Icons.Default.DirectionsBus else Icons.Default.Search,
                        contentDescription = "Header Icon",
                        tint = TransitGreen,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (user.role) {
                            "Driver" -> "Driver Dashboard"
                            else -> "Passenger Booking & Tracking"
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TransitGreen
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                when (user.role) {
                    "Driver" -> {
                        Text("Today's Assigned Route:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.Black)
                        Text("City Terminal ➔ Airport Hub (BUS_101)", fontSize = 13.sp, color = Color.DarkGray)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Scheduled Departure: 09:30 AM", fontSize = 13.sp, color = Color.DarkGray)
                        Text("Status: On Duty", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TransitGreen)
                    }
                    else -> {
                        Text("Where to go?", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Origin: Central Terminal (Manila)", fontSize = 13.sp, color = Color.DarkGray)
                        Text("Destination: Choose destination...", fontSize = 13.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { isSearchingDestination = true },
                            colors = ButtonDefaults.buttonColors(containerColor = TransitGreen),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Search Destination & Buses", color = Color.White)
                        }
                    }
                }
            }
        }

        if (dbStatus.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = dbStatus,
                color = TransitGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Logout", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TransitGreen)
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Preview(showBackground = true)
@Composable
fun MainAppPreview() {
    MyFirstAppTheme {
        MainApp()
    }
}

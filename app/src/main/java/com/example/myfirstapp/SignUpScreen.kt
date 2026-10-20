package com.example.myfirstapp

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myfirstapp.ui.theme.MyFirstAppTheme
import com.google.firebase.auth.FirebaseAuth

val TransitGreen = Color(0xFF116530)

@Composable
fun SignUpScreen(
    onSignUpSuccess: (user) -> Unit = {},
    onNavigateToLogin: () -> Unit = {}
) {
    val context = LocalContext.current
    var selectedRole by remember { mutableStateOf("Passenger") }

    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var agreedToTerms by remember { mutableStateOf(false) }

    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val roles = listOf("Passenger", "Staff")

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.Black,
        unfocusedTextColor = Color.Black,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        focusedLabelColor = TransitGreen,
        unfocusedLabelColor = Color.Gray,
        focusedBorderColor = TransitGreen,
        unfocusedBorderColor = Color.LightGray
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Create Account",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                Text(
                    text = "Sign up to start booking and tracking",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                // Segmented Role Selector Tab
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    roles.forEachIndexed { index, role ->
                        SegmentedButton(
                            selected = selectedRole == role,
                            onClick = { selectedRole = role },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = roles.size),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = TransitGreen,
                                activeContentColor = Color.White,
                                inactiveContainerColor = Color(0xFFF0F4F1),
                                inactiveContentColor = Color.DarkGray
                            )
                        ) {
                            Text(text = role, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Full Name
                OutlinedTextField(
                    value = fullName,
                    onValueChange = {
                        fullName = it
                        errorMessage = ""
                    },
                    label = { Text("Full Name") },
                    singleLine = true,
                    colors = textFieldColors,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Email Address
                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        errorMessage = ""
                    },
                    label = { Text("Email Address") },
                    singleLine = true,
                    colors = textFieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Phone Number
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = {
                        phoneNumber = it
                        errorMessage = ""
                    },
                    label = { Text("Phone Number") },
                    singleLine = true,
                    colors = textFieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Password
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = ""
                    },
                    label = { Text("Password") },
                    singleLine = true,
                    colors = textFieldColors,
                    supportingText = { Text("Must be at least 8 characters", fontSize = 11.sp) },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        TextButton(onClick = { passwordVisible = !passwordVisible }) {
                            Text(if (passwordVisible) "Hide" else "Show", color = TransitGreen)
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Terms Checkbox
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = agreedToTerms,
                        onCheckedChange = { agreedToTerms = it },
                        colors = CheckboxDefaults.colors(checkedColor = TransitGreen)
                    )
                    Text(
                        text = "I agree to the Terms of Service & Privacy Policy",
                        fontSize = 11.sp,
                        color = Color.DarkGray
                    )
                }

                if (errorMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Create Account Button
                Button(
                    onClick = {
                        when {
                            fullName.isBlank() -> errorMessage = "Please enter your full name"
                            email.isBlank() -> errorMessage = "Please enter your email address"
                            password.length < 8 -> errorMessage = "Password must be at least 8 characters"
                            !agreedToTerms -> errorMessage = "Please agree to the Terms of Service"
                            else -> {
                                isLoading = true
                                errorMessage = ""
                                FirebaseAuth.getInstance()
                                    .createUserWithEmailAndPassword(email.trim(), password)
                                    .addOnSuccessListener { authResult ->
                                        isLoading = false
                                        val uid = authResult.user?.uid ?: "U_${System.currentTimeMillis()}"
                                        val newUser = user(
                                            userId = uid,
                                            fullname = fullName.trim(),
                                            email = email.trim(),
                                            password = password,
                                            role = selectedRole
                                        )
                                        DatabaseManager.saveUser(newUser)
                                        Toast.makeText(context, "Account created successfully as $selectedRole!", Toast.LENGTH_SHORT).show()
                                        onSignUpSuccess(newUser)
                                    }
                                    .addOnFailureListener { e ->
                                        isLoading = false
                                        errorMessage = e.localizedMessage ?: "Failed to create account in Firebase"
                                        Toast.makeText(context, "Sign Up Notice: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                    }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TransitGreen),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Text("Create Account", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Already have an account? ", fontSize = 13.sp, color = Color.Gray)
                    TextButton(onClick = onNavigateToLogin, contentPadding = PaddingValues(0.dp)) {
                        Text("Log in", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TransitGreen)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SignUpScreenPreview() {
    MyFirstAppTheme {
        SignUpScreen()
    }
}

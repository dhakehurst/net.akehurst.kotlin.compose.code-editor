package net.akehurst.kotlin.compose.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue


@Composable
fun TextInputDialog(
    title: String,
    onDismissRequest: () -> Unit,
    onConfirmation: (String) -> Unit
) {
    // State to hold the text inside the TextField
    var inputText by remember { mutableStateOf("") }

    AlertDialog(
        title = { Text(text = title) },
        text = {
            // The text input field
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                label = { Text("Identity") },
                singleLine = true,
            )
        },
        onDismissRequest = { onDismissRequest() },
        confirmButton = { Button(onClick = { onConfirmation(inputText) }) { Text("Confirm") } },
        dismissButton = { Button(onClick = { onDismissRequest() }) { Text("Dismiss") } }
    )
}
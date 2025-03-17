@file:Suppress("UNUSED", "INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package test

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.window.singleWindowApplication
import kotlin.test.Test

class test_refresh {

    @Test
    fun test() {

        var color = Color.Green

        singleWindowApplication(
            title = "Test",
        ) {
            val regex = Regex("error")
            val textState by remember { mutableStateOf(TextFieldState("this text contains error and other things.")) }
            val outputTransformation = OutputTransformation({
                val txt = this.originalText
                if(txt.isNotEmpty()) {
                    val annotated = buildAnnotatedString {
                        regex.findAll(txt).forEach {
                            addStyle(SpanStyle(color), it.range.start, it.range.endInclusive+1)
                        }
                    }
                    setComposition(0, txt.length, annotated.annotations)
                    changeTracker.trackChange(0, txt.length, txt.length)
                }
            })

            Surface {
                Column {
                    Row {
                        Button(onClick = { color = Color.Red }) { Text("Red") }
                        Button(onClick = { color = Color.Blue }) { Text("Blue") }
                    }
                    BasicTextField(
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                        textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface),
                        state = textState,
                        modifier = Modifier
                            .fillMaxSize(),
                        outputTransformation = outputTransformation
                    )
                }
            }
        }


    }

}
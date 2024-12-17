/*
package net.akehurst.kotlin.compose.editor


import androidx.compose.ui.text.AnnotatedString
import org.jetbrains.skiko.SkikoPlatformKeyboardEvent

actual fun SkikoPlatformKeyboardEvent.myConsume() {
    this.stopPropagation()
}

actual object MyClipboardManager  {


    actual  fun setText(annotatedString: AnnotatedString) {
        kotlinx.browser.window.navigator.clipboard.writeText(annotatedString.text)
    }

    actual suspend fun getText():AnnotatedString? {
        TODO()
        //return kotlinx.browser.window.navigator.clipboard.readText().await()
    }

    actual  fun hasText(): Boolean {
        TODO()
    }
}

 */
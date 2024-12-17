/*
package net.akehurst.kotlin.compose.editor


import androidx.compose.ui.text.AnnotatedString
import kotlinx.browser.window
import kotlinx.coroutines.*
import org.jetbrains.skiko.SkikoPlatformKeyboardEvent

actual fun SkikoPlatformKeyboardEvent.myConsume() {
    this.stopPropagation()
}

actual object MyClipboardManager {

    actual fun setText(annotatedString: AnnotatedString) {
        kotlinx.browser.window.navigator.clipboard.writeText(annotatedString.text)
    }

    actual suspend fun getText(): AnnotatedString? {
        val txt = kotlinx.browser.window.navigator.clipboard.readText().await()
        return AnnotatedString(text = txt)
    }

    actual fun hasText(): Boolean {
        TODO()
    }

}

 */
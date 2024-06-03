package net.akehurst.kotlin.compose.editor

import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
//import org.jetbrains.skiko.SkikoKeyboardEvent
//import org.jetbrains.skiko.SkikoPlatformKeyboardEvent

//fun KeyEvent.myConsume() {
//    (this.nativeKeyEvent as SkikoKeyboardEvent).platform?.myConsume()
//}

//expect fun SkikoPlatformKeyboardEvent.myConsume()

// getText Currently not supported by kotlin-compose on JS
expect object MyClipboardManager {
    fun setText(annotatedString: AnnotatedString)
    suspend fun getText(): AnnotatedString?
    fun hasText(): Boolean
}
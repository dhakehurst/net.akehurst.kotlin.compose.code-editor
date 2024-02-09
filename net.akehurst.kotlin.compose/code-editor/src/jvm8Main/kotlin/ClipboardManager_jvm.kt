package net.akehurst.kotlin.compose.editor

import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.skiko.SkikoPlatformKeyboardEvent
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

actual fun SkikoPlatformKeyboardEvent.myConsume() {
    this.consume()
}

actual object MyClipboardManager {

    actual fun setText(annotatedString: AnnotatedString) {
        Toolkit.getDefaultToolkit().systemClipboard?.let { cb ->
            cb.setContents(StringSelection(annotatedString.text), null)
        }
    }

    actual suspend fun getText(): AnnotatedString? {
        return Toolkit.getDefaultToolkit().systemClipboard?.let { cb ->
            val text = cb.getContents(null).getTransferData(DataFlavor.stringFlavor) as String
            AnnotatedString(text = text)
        }
    }

    actual fun hasText(): Boolean {
        TODO()
    }

}
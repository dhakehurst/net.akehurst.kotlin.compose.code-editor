@file:Suppress("UNUSED", "INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "EQUALS_MISSING", "CANNOT_OVERRIDE_INVISIBLE_MEMBER")
@file:OptIn(ExperimentalFoundationApi::class)
package net.akehurst.kotlin.compose.editor.text

import androidx.compose.foundation.ExperimentalFoundationApi

class UndoState3 internal constructor(private val state: TextFieldState3) {

    /**
     * Whether it is possible to execute a meaningful undo action right now. If this value is false,
     * calling `undo` would be a no-op.
     */
    @Suppress("GetterSetterNames")
    @get:Suppress("GetterSetterNames")
    val canUndo: Boolean
        get() = state.textUndoManager.canUndo

    /**
     * Whether it is possible to execute a meaningful redo action right now. If this value is false,
     * calling `redo` would be a no-op.
     */
    @Suppress("GetterSetterNames")
    @get:Suppress("GetterSetterNames")
    val canRedo: Boolean
        get() = state.textUndoManager.canRedo

    /**
     * Reverts the latest edit action or a group of actions that are merged together. Calling it
     * repeatedly can continue undoing the previous actions.
     */
    fun undo() {
        state.textUndoManager.undo(state)
    }

    /** Re-applies a change that was previously reverted via [undo]. */
    fun redo() {
        state.textUndoManager.redo(state)
    }

    /** Clears all undo and redo history up to this point. */
    fun clearHistory() {
        state.textUndoManager.clearHistory()
    }
}

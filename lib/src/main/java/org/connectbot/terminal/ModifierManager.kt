/*
 * ConnectBot Terminal
 * Copyright 2025 Kenny Root
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.connectbot.terminal

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages sticky modifier key state for terminal keyboard input.
 *
 * Provides a 3-state toggle system for Ctrl, Alt, and Shift modifiers:
 * - OFF: Modifier not active
 * - TRANSIENT: One-shot modifier (clears after next key press)
 * - LOCKED: Sticky modifier (stays active until explicitly toggled off)
 *
 * This enables on-screen keyboard buttons that can be tapped to activate
 * modifiers temporarily or locked on for multiple key presses.
 *
 * Example usage:
 * ```kotlin
 * val modifierManager = ModifierManager().apply {
 *     setStickyModifiers(ctrl = true, alt = true, shift = true)
 * }
 * Terminal(…, modifierManager = modifierManager)
 *
 * // User taps Ctrl button in UI
 * modifierManager.metaPress(ModifierManager.CTRL_ON, forceSticky = true)
 *
 * // User presses 'C' key which internally calls
 * keyboardHandler.onKeyEvent(event)  // Sends Ctrl+C, clears transient Ctrl
 * ```
 */
interface ModifierManager {
    /**
     * Check if Ctrl modifier is active (transient or locked).
     */
    fun isCtrlActive(): Boolean

    /**
     * Check if Alt modifier is active (transient or locked).
     */
    fun isAltActive(): Boolean

    /**
     * Check if Shift modifier is active (transient or locked).
     */
    fun isShiftActive(): Boolean

    /**
     * Clear transient modifiers after a key press.
     *
     * This should be called by KeyboardHandler after each key is dispatched
     * to the terminal. Transient modifiers are one-shot and clear automatically,
     * while locked modifiers persist.
     */
    fun clearTransients()
}

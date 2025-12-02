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
class ModifierManager {
    companion object {
        // Modifier bit flags (compatible with ConnectBot's TerminalKeyListener)
        const val CTRL_ON = 0x01      // Transient Ctrl
        const val CTRL_LOCK = 0x02    // Locked Ctrl
        const val ALT_ON = 0x04       // Transient Alt
        const val ALT_LOCK = 0x08     // Locked Alt
        const val SHIFT_ON = 0x10     // Transient Shift
        const val SHIFT_LOCK = 0x20   // Locked Shift

        // Convenience masks
        private const val CTRL_MASK = CTRL_ON or CTRL_LOCK
        private const val ALT_MASK = ALT_ON or ALT_LOCK
        private const val SHIFT_MASK = SHIFT_ON or SHIFT_LOCK
        private const val TRANSIENT_MASK = CTRL_ON or ALT_ON or SHIFT_ON
    }

    /**
     * Current modifier state as a bitmask.
     * Use the modifier constants to check individual modifier states.
     */
    private var state: Int = 0

    /**
     * Configuration: which modifiers can be sticky.
     * Set via setStickyModifiers().
     */
    private var stickyModifiers: Int = 0

    private val _modifierState = MutableStateFlow(getState())
    val modifierState: StateFlow<ModifierState> = _modifierState.asStateFlow()

    /**
     * Toggle modifier state (for UI buttons).
     *
     * Implements a 3-state cycle:
     * 1. OFF → TRANSIENT (or LOCKED if forceSticky=true)
     * 2. TRANSIENT → LOCKED
     * 3. LOCKED → OFF
     *
     * @param code Modifier code (CTRL_ON, ALT_ON, or SHIFT_ON)
     * @param forceSticky If true, first press enables sticky mode directly (skips transient)
     */
    fun metaPress(code: Int, forceSticky: Boolean = false) {
        when {
            // Currently locked → turn off completely
            (state and (code shl 1)) != 0 -> {
                state = state and (code shl 1).inv()
            }
            // Currently transient → make locked
            (state and code) != 0 -> {
                state = state and code.inv()
                state = state or (code shl 1)
            }
            // Off → make transient (or locked if forceSticky/stickyModifiers allows)
            forceSticky || (stickyModifiers and code) != 0 -> {
                state = state or code
            }
            // Sticky disabled for this modifier - do nothing
            else -> return
        }
        _modifierState.value = getState()
    }

    /**
     * Get VTerm modifier mask for current sticky state.
     *
     * Returns a bitmask where:
     * - Bit 0 (0x01): Shift
     * - Bit 1 (0x02): Alt
     * - Bit 2 (0x04): Ctrl
     *
     * This matches the format expected by Terminal.dispatchKey() and dispatchCharacter().
     */
    fun getModifierMask(): Int {
        var mask = 0
        if ((state and SHIFT_MASK) != 0) mask = mask or 1  // Bit 0: Shift
        if ((state and ALT_MASK) != 0) mask = mask or 2    // Bit 1: Alt
        if ((state and CTRL_MASK) != 0) mask = mask or 4   // Bit 2: Ctrl
        return mask
    }

    /**
     * Combine sticky modifiers with hardware keyboard modifiers.
     *
     * This enables sticky modifiers from UI buttons to work alongside
     * physical keyboard modifiers. For example, sticky Ctrl + hardware Alt
     * will produce Ctrl+Alt.
     *
     * @param hardwareCtrl True if Ctrl is pressed on hardware keyboard
     * @param hardwareAlt True if Alt is pressed on hardware keyboard
     * @param hardwareShift True if Shift is pressed on hardware keyboard
     * @return Combined modifier mask (Shift=1, Alt=2, Ctrl=4)
     */
    fun combineWithHardware(hardwareCtrl: Boolean, hardwareAlt: Boolean, hardwareShift: Boolean): Int {
        var mask = getModifierMask()
        // OR with hardware modifiers (sticky + hardware = both active)
        if (hardwareShift) mask = mask or 1
        if (hardwareAlt) mask = mask or 2
        if (hardwareCtrl) mask = mask or 4
        return mask
    }

    /**
     * Clear transient modifiers after a key press.
     *
     * This should be called by KeyboardHandler after each key is dispatched
     * to the terminal. Transient modifiers are one-shot and clear automatically,
     * while locked modifiers persist.
     */
    fun clearTransients() {
        val oldState = state
        state = state and TRANSIENT_MASK.inv()
        if (state != oldState) {
            _modifierState.value = getState()
        }
    }

    /**
     * Check if Ctrl modifier is active (transient or locked).
     */
    fun isCtrlActive(): Boolean = (state and CTRL_MASK) != 0

    /**
     * Check if Alt modifier is active (transient or locked).
     */
    fun isAltActive(): Boolean = (state and ALT_MASK) != 0

    /**
     * Check if Shift modifier is active (transient or locked).
     */
    fun isShiftActive(): Boolean = (state and SHIFT_MASK) != 0

    /**
     * Check if Ctrl modifier is in transient (one-shot) state.
     */
    fun isCtrlTransient(): Boolean = (state and CTRL_ON) != 0

    /**
     * Check if Ctrl modifier is in locked (sticky) state.
     */
    fun isCtrlLocked(): Boolean = (state and CTRL_LOCK) != 0

    /**
     * Check if Alt modifier is in transient (one-shot) state.
     */
    fun isAltTransient(): Boolean = (state and ALT_ON) != 0

    /**
     * Check if Alt modifier is in locked (sticky) state.
     */
    fun isAltLocked(): Boolean = (state and ALT_LOCK) != 0

    /**
     * Check if Shift modifier is in transient (one-shot) state.
     */
    fun isShiftTransient(): Boolean = (state and SHIFT_ON) != 0

    /**
     * Check if Shift modifier is in locked (sticky) state.
     */
    fun isShiftLocked(): Boolean = (state and SHIFT_LOCK) != 0

    /**
     * Get detailed modifier state for UI display.
     *
     * Returns a ModifierState object with the current level (OFF, TRANSIENT, LOCKED)
     * for each modifier. Useful for highlighting UI buttons.
     */
    fun getState(): ModifierState {
        return ModifierState(
            ctrlState = when {
                (state and CTRL_LOCK) != 0 -> ModifierLevel.LOCKED
                (state and CTRL_ON) != 0 -> ModifierLevel.TRANSIENT
                else -> ModifierLevel.OFF
            },
            altState = when {
                (state and ALT_LOCK) != 0 -> ModifierLevel.LOCKED
                (state and ALT_ON) != 0 -> ModifierLevel.TRANSIENT
                else -> ModifierLevel.OFF
            },
            shiftState = when {
                (state and SHIFT_LOCK) != 0 -> ModifierLevel.LOCKED
                (state and SHIFT_ON) != 0 -> ModifierLevel.TRANSIENT
                else -> ModifierLevel.OFF
            }
        )
    }

    /**
     * Configure which modifiers can be sticky.
     *
     * By default, all modifiers are disabled. Call this method to enable
     * sticky behavior for specific modifiers based on user preferences.
     *
     * @param ctrl True to allow Ctrl to be sticky
     * @param alt True to allow Alt to be sticky
     * @param shift True to allow Shift to be sticky
     */
    fun setStickyModifiers(ctrl: Boolean = true, alt: Boolean = true, shift: Boolean = true) {
        stickyModifiers = 0
        if (ctrl) stickyModifiers = stickyModifiers or CTRL_ON
        if (alt) stickyModifiers = stickyModifiers or ALT_ON
        if (shift) stickyModifiers = stickyModifiers or SHIFT_ON
    }

    /**
     * Reset all modifiers to OFF state.
     *
     * This clears both transient and locked modifiers.
     */
    fun reset() {
        if (state != 0) {
            state = 0
            _modifierState.value = getState()
        }
    }
}

/**
 * Represents the detailed state of all modifiers.
 *
 * @property ctrlState Current state of Ctrl modifier
 * @property altState Current state of Alt modifier
 * @property shiftState Current state of Shift modifier
 */
data class ModifierState(
    val ctrlState: ModifierLevel,
    val altState: ModifierLevel,
    val shiftState: ModifierLevel
)

/**
 * Level of a modifier key.
 */
enum class ModifierLevel {
    OFF,        // Modifier not active
    TRANSIENT,  // One-shot modifier (clears after next key)
    LOCKED      // Sticky modifier (stays until toggled off)
}

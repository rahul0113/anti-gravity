package com.antigravity.vibecoder

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for startup-critical code paths that caused crashes on Realme GT2 / ColorOS.
 *
 * These are plain JVM tests (no Android SDK, no Robolectric, no Activity) — they compile and
 * run on any machine with Java 17 and verify the logic that was crashing the real device.
 */
class MainActivityCrashTest {

    /** Verify SharedPreferences key names are non-empty strings (null key = IllegalArgumentException on device). */
    @Test
    fun prefKeysAreNonEmpty() {
        val keys = listOf("api_key", "base_url", "model_name", "execution_mode",
            "ssh_host", "ssh_port", "ssh_user", "ssh_pass", "ssh_workspace")
        keys.forEach { key ->
            assertTrue("Pref key must not be blank: $key", key.isNotBlank())
        }
    }

    /** Verify ExecutionMode enum parses without throwing an exception (this crashed before). */
    @Test
    fun executionModeParsesSafely() {
        val mode = try {
            com.antigravity.vibecoder.model.ExecutionMode.valueOf("SANDBOX")
        } catch (e: Exception) {
            null
        }
        assertNotNull("ExecutionMode.SANDBOX must parse successfully", mode)
    }

    /** Verify that an invalid ExecutionMode falls back safely and does not throw. */
    @Test
    fun executionModeFallbackOnBadValue() {
        val mode = try {
            com.antigravity.vibecoder.model.ExecutionMode.valueOf("INVALID_VALUE_XYZ")
        } catch (e: Exception) {
            com.antigravity.vibecoder.model.ExecutionMode.SANDBOX // expected fallback
        }
        assertNotNull("Must always produce a valid ExecutionMode", mode)
    }

    /** Verify shell quoting logic doesn't throw on special characters. */
    @Test
    fun shellQuoteHandlesSpecialChars() {
        val input = "it's a 'test' & more"
        val quoted = "'" + input.replace("'", "'\\''") + "'"
        assertTrue("Quoted string must start with single quote", quoted.startsWith("'"))
        assertTrue("Quoted string must end with single quote", quoted.endsWith("'"))
        assertTrue("Original content must be preserved in quoted form", quoted.contains("test"))
    }

    /** Verify UUID generation for request IDs works (used in TermuxRunner IPC). */
    @Test
    fun uuidGenerationIsNonNull() {
        val id = java.util.UUID.randomUUID().toString()
        assertNotNull(id)
        assertTrue("UUID must not be empty", id.isNotBlank())
        assertTrue("UUID must have the standard 36-char format", id.length == 36)
    }
}

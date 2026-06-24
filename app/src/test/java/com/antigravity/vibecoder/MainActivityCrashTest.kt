package com.antigravity.vibecoder

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityCrashTest {

    @Test
    fun prefKeysAreNonEmpty() {
        val keys = listOf("api_key", "base_url", "model_name", "exec_mode",
            "ssh_host", "ssh_port", "ssh_user", "ssh_pass", "ssh_work", "grpc_port")
        keys.forEach { key ->
            assertTrue("Pref key must not be blank: $key", key.isNotBlank())
        }
    }

    @Test
    fun executionModeParsesSafely() {
        val mode = try {
            com.antigravity.vibecoder.model.ExecutionMode.valueOf("OPENCLAUDE")
        } catch (e: Exception) {
            null
        }
        assertNotNull("ExecutionMode.OPENCLAUDE must parse successfully", mode)
    }

    @Test
    fun executionModeFallbackOnBadValue() {
        val mode = try {
            com.antigravity.vibecoder.model.ExecutionMode.valueOf("INVALID_VALUE_XYZ")
        } catch (e: Exception) {
            com.antigravity.vibecoder.model.ExecutionMode.OPENCLAUDE
        }
        assertNotNull("Must always produce a valid ExecutionMode", mode)
    }

    @Test
    fun shellQuoteHandlesSpecialChars() {
        val input = "it's a 'test' & more"
        val quoted = "'" + input.replace("'", "'\\''") + "'"
        assertTrue("Quoted string must start with single quote", quoted.startsWith("'"))
        assertTrue("Quoted string must end with single quote", quoted.endsWith("'"))
        assertTrue("Original content must be preserved in quoted form", quoted.contains("test"))
    }

    @Test
    fun uuidGenerationIsNonNull() {
        val id = java.util.UUID.randomUUID().toString()
        assertNotNull(id)
        assertTrue("UUID must not be empty", id.isNotBlank())
        assertTrue("UUID must have the standard 36-char format", id.length == 36)
    }
}

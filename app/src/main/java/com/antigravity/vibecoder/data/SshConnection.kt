package com.antigravity.vibecoder.data

import com.antigravity.vibecoder.model.ConnectionConfig
import com.antigravity.vibecoder.model.WorkspaceFile
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Properties
import java.util.Vector

// O-11 FIX: Removed unused InputStream import

object SshConnection {

    // O-5 FIX: Session pool — reuse authenticated sessions instead of creating one per call
    private val sessionLock = Any()
    private var cachedSession: Session? = null
    private var cachedConfig: ConnectionConfig? = null

    private fun isLocalAddress(host: String): Boolean =
        host == "127.0.0.1" || host == "localhost" || host == "::1" ||
            host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172.")

    private fun createSession(config: ConnectionConfig): Session {
        val jsch = JSch()
        val session = jsch.getSession(config.user, config.host, config.port)
        if (config.authType == ConnectionConfig.AuthType.PASSWORD) {
            session.setPassword(config.passwordKey)
        } else {
            jsch.addIdentity("key", config.passwordKey.toByteArray(), null, null)
        }
        val prop = Properties()
        prop["StrictHostKeyChecking"] = if (isLocalAddress(config.host)) "no" else "yes"
        session.setConfig(prop)
        session.connect(15000)
        return session
    }

    private fun getSession(config: ConnectionConfig): Session = synchronized(sessionLock) {
        val existing = cachedSession
        // Reuse if same config and still connected
        if (existing != null && existing.isConnected && cachedConfig == config) {
            return existing
        }
        existing?.disconnect()
        val fresh = createSession(config)
        cachedSession = fresh
        cachedConfig = config
        return fresh
    }

    fun invalidateSession() = synchronized(sessionLock) {
        cachedSession?.disconnect()
        cachedSession = null
        cachedConfig = null
    }

    // C-3 FIX: replaced Thread.sleep() with delay() + added withTimeout to prevent thread exhaustion
    suspend fun executeCommand(config: ConnectionConfig, command: String): String = withContext(Dispatchers.IO) {
        var channel: ChannelExec? = null
        try {
            withTimeout(30_000L) {
                val session = getSession(config)
                channel = session.openChannel("exec") as ChannelExec
                channel!!.setCommand(command)

                val outputStream = ByteArrayOutputStream()
                val errorStream = ByteArrayOutputStream()
                channel!!.setOutputStream(outputStream)
                channel!!.setErrStream(errorStream)
                channel!!.connect()

                // C-3 FIX: Use delay() — yields the coroutine instead of blocking the IO thread
                while (!channel!!.isClosed) {
                    delay(100)
                }

                val outStr = outputStream.toString("UTF-8")
                val errStr = errorStream.toString("UTF-8")
                if (errStr.isNotEmpty()) "$outStr\nError: $errStr" else outStr
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            invalidateSession()
            "SSH Command timed out after 30 seconds."
        } catch (e: Exception) {
            // On error, invalidate the cached session so next call creates a fresh one
            invalidateSession()
            "SSH Command failed: ${e.message}"
        } finally {
            channel?.disconnect()
        }
    }

    suspend fun readFile(config: ConnectionConfig, path: String): String = withContext(Dispatchers.IO) {
        var channel: ChannelSftp? = null
        try {
            val session = getSession(config)
            channel = session.openChannel("sftp") as ChannelSftp
            channel.connect()
            channel.get(path).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            invalidateSession()
            "SSH Read failed: ${e.message}"
        } finally {
            channel?.disconnect()
        }
    }

    suspend fun writeFile(config: ConnectionConfig, path: String, content: String): Boolean = withContext(Dispatchers.IO) {
        var channel: ChannelSftp? = null
        try {
            val session = getSession(config)
            channel = session.openChannel("sftp") as ChannelSftp
            channel.connect()
            channel.put(ByteArrayInputStream(content.toByteArray(Charsets.UTF_8)), path)
            true
        } catch (e: Exception) {
            invalidateSession()
            false
        } finally {
            channel?.disconnect()
        }
    }

    suspend fun listDirectory(config: ConnectionConfig, path: String): List<WorkspaceFile> = withContext(Dispatchers.IO) {
        var channel: ChannelSftp? = null
        try {
            val session = getSession(config)
            channel = session.openChannel("sftp") as ChannelSftp
            channel.connect()
            val rawList = channel.ls(path) as? Vector<*> ?: Vector<Any>()
            rawList.filterIsInstance<ChannelSftp.LsEntry>()
                .filter { it.filename != "." && it.filename != ".." }
                .map { entry ->
                    val fullPath = if (path.endsWith("/")) "$path${entry.filename}" else "$path/${entry.filename}"
                    WorkspaceFile(entry.filename, fullPath, entry.attrs.isDir, entry.attrs.size)
                }
                .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        } catch (e: Exception) {
            invalidateSession()
            emptyList()
        } finally {
            channel?.disconnect()
        }
    }
}

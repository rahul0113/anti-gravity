package com.antigravity.vibecoder.data

import com.antigravity.vibecoder.model.ConnectionConfig
import com.antigravity.vibecoder.model.WorkspaceFile
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Properties
import java.util.Vector

object SshConnection {

    private fun isLocalAddress(host: String): Boolean {
        return host == "127.0.0.1" || host == "localhost" || host == "::1" ||
            host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172.")
    }

    private fun createSession(config: ConnectionConfig): Session {
        val jsch = JSch()
        val session = jsch.getSession(config.user, config.host, config.port)

        if (config.authType == ConnectionConfig.AuthType.PASSWORD) {
            session.setPassword(config.passwordKey)
        } else {
            jsch.addIdentity("key", config.passwordKey.toByteArray(), null, null)
        }

        val prop = Properties()
        // SEC-3 FIX: Only skip host key checking for loopback/LAN addresses.
        // For remote hosts, enforce strict checking to prevent MITM attacks.
        prop["StrictHostKeyChecking"] = if (isLocalAddress(config.host)) "no" else "yes"
        session.setConfig(prop)
        session.connect(15000)
        return session
    }

    suspend fun executeCommand(config: ConnectionConfig, command: String): String = withContext(Dispatchers.IO) {
        var session: Session? = null
        var channel: ChannelExec? = null
        try {
            session = createSession(config)
            channel = session.openChannel("exec") as ChannelExec
            channel.setCommand(command)

            val outputStream = ByteArrayOutputStream()
            val errorStream = ByteArrayOutputStream()
            channel.setOutputStream(outputStream)
            channel.setErrStream(errorStream)

            channel.connect()

            while (!channel.isClosed) {
                withContext(Dispatchers.IO) {
                    Thread.sleep(100)
                }
            }

            val outStr = outputStream.toString("UTF-8")
            val errStr = errorStream.toString("UTF-8")
            
            if (errStr.isNotEmpty()) {
                "$outStr\nError: $errStr"
            } else {
                outStr
            }
        } catch (e: Exception) {
            "SSH Command failed: ${e.message}"
        } finally {
            channel?.disconnect()
            session?.disconnect()
        }
    }

    suspend fun readFile(config: ConnectionConfig, path: String): String = withContext(Dispatchers.IO) {
        var session: Session? = null
        var channel: ChannelSftp? = null
        try {
            session = createSession(config)
            channel = session.openChannel("sftp") as ChannelSftp
            channel.connect()

            val inputStream = channel.get(path)
            val result = inputStream.bufferedReader().use { it.readText() }
            result
        } catch (e: Exception) {
            "SSH Read failed: ${e.message}"
        } finally {
            channel?.disconnect()
            session?.disconnect()
        }
    }

    suspend fun writeFile(config: ConnectionConfig, path: String, content: String): Boolean = withContext(Dispatchers.IO) {
        var session: Session? = null
        var channel: ChannelSftp? = null
        try {
            session = createSession(config)
            channel = session.openChannel("sftp") as ChannelSftp
            channel.connect()

            val inputStream = ByteArrayInputStream(content.toByteArray(Charsets.UTF_8))
            channel.put(inputStream, path)
            true
        } catch (e: Exception) {
            false
        } finally {
            channel?.disconnect()
            session?.disconnect()
        }
    }

    suspend fun listDirectory(config: ConnectionConfig, path: String): List<WorkspaceFile> = withContext(Dispatchers.IO) {
        var session: Session? = null
        var channel: ChannelSftp? = null
        val filesList = mutableListOf<WorkspaceFile>()
        try {
            session = createSession(config)
            channel = session.openChannel("sftp") as ChannelSftp
            channel.connect()

            val rawList = channel.ls(path) as Vector<ChannelSftp.LsEntry>
            for (entry in rawList) {
                val name = entry.filename
                if (name == "." || name == "..") continue

                val isDir = entry.attrs.isDir
                val size = entry.attrs.size
                val fullPath = if (path.endsWith("/")) "$path$name" else "$path/$name"
                filesList.add(WorkspaceFile(name, fullPath, isDir, size))
            }
            filesList.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        } catch (e: Exception) {
            emptyList()
        } finally {
            channel?.disconnect()
            session?.disconnect()
        }
    }
}

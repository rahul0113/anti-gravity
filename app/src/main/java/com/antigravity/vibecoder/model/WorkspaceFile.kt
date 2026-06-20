package com.antigravity.vibecoder.model

data class WorkspaceFile(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0,
    val children: List<WorkspaceFile> = emptyList()
)

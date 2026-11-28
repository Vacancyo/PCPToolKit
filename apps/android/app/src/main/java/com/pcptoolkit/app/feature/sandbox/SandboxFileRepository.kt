/**
 * PCP ToolKit
 * Copyright (C) 2026 VacancyoTP
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * @file SandboxFileRepository.kt
 * @brief 沙箱应用私有文件浏览与安全替换。
 */
package com.pcptoolkit.app.feature.sandbox

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.system.Os
import android.system.OsConstants
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.core.env.BEnvironment
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class SandboxFileArea(val titleKey: String) {
    APK("sandbox.files.area_apk"),
    DATA("sandbox.files.area_data"),
    EXTERNAL("sandbox.files.area_external")
}

data class SandboxFileEntry(
    val name: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long
) {
    val isEditableText: Boolean
        get() = !isDirectory && size <= MAX_EDITABLE_BYTES && isTextFileName(name)

    private companion object {
        const val MAX_EDITABLE_BYTES = 2L * 1024L * 1024L

        fun isTextFileName(name: String): Boolean {
            val lower = name.lowercase()
            val extension = lower.substringAfterLast('.', "")
            return extension in setOf(
                "txt", "json", "xml", "properties", "cfg", "ini",
                "conf", "log", "csv", "lua", "yaml", "yml"
            )
        }
    }
}

data class SandboxFileContent(
    val text: String,
    val truncated: Boolean
)

enum class SandboxImportConflictAction {
    REPLACE,
    SKIP,
    KEEP_BOTH
}

data class SandboxImportCandidate(
    val displayName: String,
    val targetExists: Boolean
)

data class SandboxFileProperties(
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val permissions: String,
    val owner: String,
    val group: String
)

object SandboxFileRepository {
    private const val USER_ID = 0
    private const val MAX_TEXT_BYTES = 2L * 1024L * 1024L
    private const val BUFFER_SIZE = 1024 * 1024
    // 0666: owner, group, and other can read and write.
    private const val IMPORTED_FILE_MODE = 0x1B6
    // 0777: owner, group, and other can access and traverse the directory.
    private const val IMPORTED_DIRECTORY_MODE = 0x1FF

    fun list(
        packageName: String,
        area: SandboxFileArea,
        relativePath: String
    ): List<SandboxFileEntry> {
        val directory = resolve(packageName, area, relativePath)
        if (relativePath.isBlank()) directory.mkdirs()
        if (!directory.isDirectory) return emptyList()
        return directory.listFiles()
            .orEmpty()
            .sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
            .map { file ->
                val childPath = joinRelative(relativePath, file.name)
                SandboxFileEntry(
                    name = file.name,
                    relativePath = childPath,
                    isDirectory = file.isDirectory,
                    size = if (file.isFile) file.length() else 0L,
                    lastModified = file.lastModified()
                )
            }
    }

    fun readText(
        packageName: String,
        area: SandboxFileArea,
        relativePath: String
    ): SandboxFileContent {
        val file = resolve(packageName, area, relativePath)
        require(file.isFile) { "not_a_file" }
        require(file.length() <= MAX_TEXT_BYTES) { "file_too_large" }
        return SandboxFileContent(file.readText(Charsets.UTF_8), truncated = false)
    }

    fun writeText(
        packageName: String,
        area: SandboxFileArea,
        relativePath: String,
        text: String
    ) {
        val target = resolve(packageName, area, relativePath)
        require(target.isFile) { "not_a_file" }
        stopPackageIfNeeded(packageName, area)
        val temp = File(target.parentFile, ".${target.name}.pcp-write")
        try {
            temp.writeText(text, Charsets.UTF_8)
            replaceWithFile(target, temp)
        } finally {
            temp.delete()
        }
    }

    fun inspectImport(
        context: Context,
        packageName: String,
        area: SandboxFileArea,
        directoryPath: String,
        source: Uri
    ): SandboxImportCandidate {
        val directory = resolve(packageName, area, directoryPath)
        require(directory.isDirectory) { "not_a_directory" }
        val displayName = queryDisplayName(context, source) ?: "imported_file"
        val safeName = displayName.substringAfterLast('/').substringAfterLast('\\')
        require(safeName.isNotBlank() && safeName != "." && safeName != "..") { "invalid_file_name" }
        val target = File(directory, safeName)
        ensureInside(resolveRoot(packageName, area), target)
        return SandboxImportCandidate(
            displayName = safeName,
            targetExists = target.exists()
        )
    }

    fun importFile(
        context: Context,
        packageName: String,
        area: SandboxFileArea,
        directoryPath: String,
        source: Uri,
        displayName: String,
        conflictAction: SandboxImportConflictAction
    ): SandboxFileEntry? {
        val directory = resolve(packageName, area, directoryPath)
        require(directory.isDirectory) { "not_a_directory" }
        val safeName = displayName.substringAfterLast('/').substringAfterLast('\\')
        require(safeName.isNotBlank() && safeName != "." && safeName != "..") { "invalid_file_name" }
        var target = File(directory, safeName)
        ensureInside(resolveRoot(packageName, area), target)
        if (target.exists()) {
            when (conflictAction) {
                SandboxImportConflictAction.SKIP -> return null
                SandboxImportConflictAction.REPLACE -> require(target.isFile) { "file_exists" }
                SandboxImportConflictAction.KEEP_BOTH -> target = availableTarget(directory, safeName)
            }
        }
        stopPackageIfNeeded(packageName, area)
        copyUriToTarget(context, source, target)
        return SandboxFileEntry(
            name = target.name,
            relativePath = joinRelative(directoryPath, target.name),
            isDirectory = false,
            size = target.length(),
            lastModified = target.lastModified()
        )
    }

    private fun availableTarget(directory: File, name: String): File {
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val extension = if (dot > 0) name.substring(dot) else ""
        var index = 1
        var candidate = File(directory, "$base ($index)$extension")
        while (candidate.exists()) {
            index++
            candidate = File(directory, "$base ($index)$extension")
        }
        return candidate
    }

    fun createEntry(
        packageName: String,
        area: SandboxFileArea,
        directoryPath: String,
        name: String,
        isDirectory: Boolean
    ): SandboxFileEntry {
        val directory = resolve(packageName, area, directoryPath)
        require(directory.isDirectory) { "not_a_directory" }
        val safeName = name.trim()
        require(
            safeName.isNotBlank() &&
                safeName != "." &&
                safeName != ".." &&
                '/' !in safeName &&
                '\\' !in safeName
        ) { "invalid_file_name" }
        val target = File(directory, safeName)
        ensureInside(resolveRoot(packageName, area), target)
        require(!target.exists()) { "file_exists" }

        stopPackageIfNeeded(packageName, area)
        val created = if (isDirectory) target.mkdir() else target.createNewFile()
        if (!created) {
            throw IOException("Unable to create ${target.name}")
        }
        if (isDirectory) {
            applyImportedDirectoryPermissions(target)
        } else {
            applyImportedFilePermissions(target)
        }
        return SandboxFileEntry(
            name = target.name,
            relativePath = joinRelative(directoryPath, target.name),
            isDirectory = isDirectory,
            size = if (isDirectory) 0L else target.length(),
            lastModified = target.lastModified()
        )
    }

    fun rename(
        packageName: String,
        area: SandboxFileArea,
        entry: SandboxFileEntry,
        newName: String
    ): SandboxFileEntry {
        val source = resolve(packageName, area, entry.relativePath)
        require(source.exists()) { "not_found" }
        val safeName = newName.trim()
        require(
            safeName.isNotBlank() &&
                safeName != "." &&
                safeName != ".." &&
                '/' !in safeName &&
                '\\' !in safeName
        ) { "invalid_file_name" }
        val parent = source.parentFile ?: throw IOException("Missing parent directory")
        val target = File(parent, safeName)
        ensureInside(resolveRoot(packageName, area), target)
        require(
            target.canonicalPath == source.canonicalPath || !target.exists()
        ) { "file_exists" }

        if (target.canonicalPath != source.canonicalPath) {
            stopPackageIfNeeded(packageName, area)
            if (!source.renameTo(target)) {
                throw IOException("Unable to rename ${source.name}")
            }
        }
        val parentPath = entry.relativePath.substringBeforeLast('/', "")
        return SandboxFileEntry(
            name = target.name,
            relativePath = joinRelative(parentPath, target.name),
            isDirectory = target.isDirectory,
            size = if (target.isDirectory) 0L else target.length(),
            lastModified = target.lastModified()
        )
    }

    fun copy(
        packageName: String,
        sourceArea: SandboxFileArea,
        sourceEntry: SandboxFileEntry,
        targetArea: SandboxFileArea,
        targetDirectoryPath: String,
        conflictAction: SandboxImportConflictAction? = null
    ): SandboxFileEntry? {
        val source = resolve(packageName, sourceArea, sourceEntry.relativePath)
        val targetDirectory = resolve(packageName, targetArea, targetDirectoryPath)
        require(source.exists()) { "not_found" }
        require(targetDirectory.isDirectory) { "not_a_directory" }
        ensureNotInsideSource(source, targetDirectory)
        var target = File(targetDirectory, source.name)
        if (target.exists()) {
            when (conflictAction) {
                null -> require(false) { "file_exists" }
                SandboxImportConflictAction.SKIP -> return null
                SandboxImportConflictAction.KEEP_BOTH ->
                    target = availableTarget(targetDirectory, source.name)
                SandboxImportConflictAction.REPLACE -> deletePath(target)
            }
        }

        stopPackageIfNeeded(packageName, sourceArea)
        if (sourceArea != targetArea) stopPackageIfNeeded(packageName, targetArea)
        copyEntry(source, target)
        return target.toEntry(targetDirectoryPath)
    }

    fun move(
        packageName: String,
        sourceArea: SandboxFileArea,
        sourceEntry: SandboxFileEntry,
        targetArea: SandboxFileArea,
        targetDirectoryPath: String,
        conflictAction: SandboxImportConflictAction? = null
    ): SandboxFileEntry? {
        val source = resolve(packageName, sourceArea, sourceEntry.relativePath)
        val targetDirectory = resolve(packageName, targetArea, targetDirectoryPath)
        require(source.exists()) { "not_found" }
        require(targetDirectory.isDirectory) { "not_a_directory" }
        ensureNotInsideSource(source, targetDirectory)
        var target = File(targetDirectory, source.name)
        if (target.exists()) {
            when (conflictAction) {
                null -> require(false) { "file_exists" }
                SandboxImportConflictAction.SKIP -> return null
                SandboxImportConflictAction.KEEP_BOTH ->
                    target = availableTarget(targetDirectory, source.name)
                SandboxImportConflictAction.REPLACE -> deletePath(target)
            }
        }

        stopPackageIfNeeded(packageName, sourceArea)
        if (sourceArea != targetArea) stopPackageIfNeeded(packageName, targetArea)
        if (!source.renameTo(target)) {
            copyEntry(source, target)
            delete(packageName, sourceArea, sourceEntry)
        }
        return target.toEntry(targetDirectoryPath)
    }

    fun exportToUri(
        context: Context,
        packageName: String,
        area: SandboxFileArea,
        file: SandboxFileEntry,
        target: Uri
    ) {
        val sourceFile = resolve(packageName, area, file.relativePath)
        require(sourceFile.exists()) { "not_found" }
        val output = context.contentResolver.openOutputStream(target, "w")
            ?: throw IOException("Cannot open export target")
        output.use { destination ->
            if (sourceFile.isDirectory) {
                ZipOutputStream(destination).use { zip ->
                    sourceFile.walkTopDown().forEach { child ->
                        if (child == sourceFile) return@forEach
                        val relative = child.relativeTo(sourceFile).invariantSeparatorsPath
                        val entryName = if (child.isDirectory) "$relative/" else relative
                        zip.putNextEntry(ZipEntry(entryName))
                        if (child.isFile) {
                            child.inputStream().use { source ->
                                source.copyTo(zip, BUFFER_SIZE)
                            }
                        }
                        zip.closeEntry()
                    }
                }
            } else {
                sourceFile.inputStream().use { source ->
                    source.copyTo(destination, BUFFER_SIZE)
                }
            }
        }
    }

    fun exportEntriesToUri(
        context: Context,
        packageName: String,
        area: SandboxFileArea,
        entries: List<SandboxFileEntry>,
        target: Uri
    ) {
        require(entries.isNotEmpty()) { "empty_selection" }
        if (entries.size == 1) {
            exportToUri(context, packageName, area, entries.first(), target)
            return
        }
        val output = context.contentResolver.openOutputStream(target, "w")
            ?: throw IOException("Cannot open export target")
        output.use { destination ->
            ZipOutputStream(destination).use { zip ->
                entries.forEach { entry ->
                    val source = resolve(packageName, area, entry.relativePath)
                    require(source.exists()) { "not_found" }
                    source.walkTopDown().forEach { child ->
                        val relative = child.relativeTo(source).invariantSeparatorsPath
                        val entryName = if (relative.isEmpty()) {
                            source.name
                        } else {
                            "${source.name}/$relative"
                        }
                        zip.putNextEntry(
                            ZipEntry(if (child.isDirectory) "$entryName/" else entryName)
                        )
                        if (child.isFile) {
                            child.inputStream().use { input ->
                                input.copyTo(zip, BUFFER_SIZE)
                            }
                        }
                        zip.closeEntry()
                    }
                }
            }
        }
    }

    fun getProperties(
        packageName: String,
        area: SandboxFileArea,
        entry: SandboxFileEntry
    ): SandboxFileProperties {
        val file = resolve(packageName, area, entry.relativePath)
        val stat = runCatching { Os.stat(file.absolutePath) }.getOrNull()
        return SandboxFileProperties(
            name = file.name,
            isDirectory = file.isDirectory,
            size = if (file.isDirectory) 0L else (stat?.st_size ?: file.length()),
            lastModified = stat?.st_mtime?.times(1000L) ?: file.lastModified(),
            permissions = stat?.st_mode?.let(::formatPermissions) ?: "---------",
            owner = stat?.st_uid?.toString() ?: "?",
            group = stat?.st_gid?.toString() ?: "?"
        )
    }

    fun prepareShareFile(
        context: Context,
        packageName: String,
        area: SandboxFileArea,
        entry: SandboxFileEntry
    ): File = prepareShareFiles(context, packageName, area, listOf(entry)).first()

    fun prepareShareFiles(
        context: Context,
        packageName: String,
        area: SandboxFileArea,
        entries: List<SandboxFileEntry>
    ): List<File> {
        require(entries.isNotEmpty()) { "empty_selection" }
        require(entries.all { !it.isDirectory }) { "not_a_file" }
        val shareDirectory = File(context.cacheDir, "sandbox-share").apply { mkdirs() }
        shareDirectory.listFiles()?.forEach { it.delete() }
        return entries.map { entry ->
            val source = resolve(packageName, area, entry.relativePath)
            require(source.isFile) { "not_a_file" }
            val target = File(shareDirectory, entry.name)
            source.inputStream().use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output, BUFFER_SIZE)
                }
            }
            applyImportedFilePermissions(target)
            target
        }
    }

    fun delete(packageName: String, area: SandboxFileArea, file: SandboxFileEntry) {
        val target = resolve(packageName, area, file.relativePath)
        stopPackageIfNeeded(packageName, area)
        deletePath(target)
    }

    private fun deletePath(target: File) {
        val deleted = if (target.isDirectory) {
            target.walkBottomUp().forEach { child ->
                child.setWritable(true)
                child.delete()
            }
            !target.exists()
        } else {
            target.setWritable(true)
            target.delete()
        }
        if (!deleted) {
            throw IOException("Unable to delete ${target.name}")
        }
    }

    fun rootDisplayPath(packageName: String, area: SandboxFileArea): String =
        resolveRoot(packageName, area).absolutePath

    private fun resolve(packageName: String, area: SandboxFileArea, relativePath: String): File {
        val root = resolveRoot(packageName, area).canonicalFile
        val target = File(root, relativePath).canonicalFile
        ensureInside(root, target)
        return target
    }

    private fun resolveRoot(packageName: String, area: SandboxFileArea): File =
        when (area) {
            SandboxFileArea.APK -> BEnvironment.getAppDir(packageName)
            SandboxFileArea.DATA -> BEnvironment.getDataDir(packageName, USER_ID)
            SandboxFileArea.EXTERNAL -> BEnvironment.getExternalDataDir(packageName, USER_ID)
        }

    private fun ensureInside(root: File, target: File) {
        val rootPath = root.canonicalPath
        val targetPath = target.canonicalPath
        require(targetPath == rootPath || targetPath.startsWith(rootPath + File.separator)) {
            "path_outside_sandbox"
        }
    }

    private fun ensureNotInsideSource(source: File, targetDirectory: File) {
        if (!source.isDirectory) return
        val sourcePath = source.canonicalPath
        require(
            targetDirectory.canonicalPath != sourcePath &&
                !targetDirectory.canonicalPath.startsWith(sourcePath + File.separator)
        ) { "target_inside_source" }
    }

    private fun copyEntry(source: File, target: File) {
        if (source.isDirectory) {
            require(target.mkdirs()) { "Unable to create ${target.name}" }
            source.listFiles().orEmpty().forEach { child ->
                copyEntry(child, File(target, child.name))
            }
            applyImportedDirectoryPermissions(target)
        } else {
            source.inputStream().use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output, BUFFER_SIZE)
                }
            }
            target.setLastModified(source.lastModified())
            applyImportedFilePermissions(target)
        }
    }

    private fun File.toEntry(parentPath: String): SandboxFileEntry =
        SandboxFileEntry(
            name = name,
            relativePath = joinRelative(parentPath, name),
            isDirectory = isDirectory,
            size = if (isDirectory) 0L else length(),
            lastModified = lastModified()
        )

    private fun joinRelative(parent: String, child: String): String =
        if (parent.isBlank()) child else "$parent/$child"

    private fun stopPackageIfNeeded(packageName: String, area: SandboxFileArea) {
        if (area != SandboxFileArea.APK) return
        runCatching {
            BlackBoxCore.get().stopPackage(packageName, USER_ID)
        }
    }

    private fun copyUriToTarget(context: Context, source: Uri, target: File) {
        val parent = target.parentFile ?: throw IOException("Missing parent directory")
        parent.mkdirs()
        val temp = File(parent, ".${target.name}.pcp-import")
        try {
            val input = context.contentResolver.openInputStream(source)
                ?: throw IOException("Cannot open source file")
            input.use { sourceStream ->
                temp.outputStream().use { destination ->
                    sourceStream.copyTo(destination, BUFFER_SIZE)
                }
            }
            replaceWithFile(target, temp)
        } finally {
            temp.delete()
        }
    }

    private fun replaceWithFile(target: File, replacement: File) {
        val parent = target.parentFile ?: throw IOException("Missing target directory")
        parent.mkdirs()
        val backup = File(parent, ".${target.name}.pcp-backup")
        backup.delete()

        if (target.exists()) {
            target.setWritable(true)
            if (!target.renameTo(backup) && !target.delete()) {
                throw IOException("Unable to replace ${target.name}")
            }
        }

        if (!replacement.renameTo(target)) {
            if (backup.exists()) {
                backup.renameTo(target)
            }
            throw IOException("Unable to move replacement into place")
        }
        applyImportedFilePermissions(target)
        backup.delete()
    }

    private fun applyImportedFilePermissions(file: File) {
        runCatching {
            Os.chmod(file.absolutePath, IMPORTED_FILE_MODE)
        }.onFailure {
            file.setReadable(true, false)
            file.setWritable(true, false)
            file.setExecutable(false, false)
        }
    }

    private fun applyImportedDirectoryPermissions(directory: File) {
        runCatching {
            Os.chmod(directory.absolutePath, IMPORTED_DIRECTORY_MODE)
        }.onFailure {
            directory.setReadable(true, false)
            directory.setWritable(true, false)
            directory.setExecutable(true, false)
        }
    }

    private fun formatPermissions(mode: Int): String = buildString(9) {
        append(if (mode and OsConstants.S_IRUSR != 0) 'r' else '-')
        append(if (mode and OsConstants.S_IWUSR != 0) 'w' else '-')
        append(if (mode and OsConstants.S_IXUSR != 0) 'x' else '-')
        append(if (mode and OsConstants.S_IRGRP != 0) 'r' else '-')
        append(if (mode and OsConstants.S_IWGRP != 0) 'w' else '-')
        append(if (mode and OsConstants.S_IXGRP != 0) 'x' else '-')
        append(if (mode and OsConstants.S_IROTH != 0) 'r' else '-')
        append(if (mode and OsConstants.S_IWOTH != 0) 'w' else '-')
        append(if (mode and OsConstants.S_IXOTH != 0) 'x' else '-')
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        val cursor = context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        ) ?: return null
        return cursor.use {
            if (!it.moveToFirst()) return@use null
            val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index < 0) null else it.getString(index)
        }
    }

}

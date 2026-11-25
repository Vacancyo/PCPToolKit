package com.pcptoolkit.app.feature.script

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

data class ScriptEntry(val name: String, val uri: Uri)

object ScriptStore {
    const val PREFS_NAME = "settings"
    const val TREE_KEY = "scripts.tree"
    const val EXTENSION = ".pcs"

    fun treeUri(context: Context): Uri? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(TREE_KEY, null)
            ?.let(Uri::parse)

    fun folderName(context: Context, tree: Uri): String? = runCatching {
        context.contentResolver.query(
            rootDocumentUri(tree),
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()

    fun list(context: Context, tree: Uri): List<ScriptEntry>? = runCatching {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            tree,
            DocumentsContract.getTreeDocumentId(tree)
        )
        context.contentResolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            ),
            null,
            null,
            null
        )?.use { cursor ->
            val result = mutableListOf<ScriptEntry>()
            while (cursor.moveToNext()) {
                if (cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) continue
                val id = cursor.getString(0) ?: continue
                val name = cursor.getString(1) ?: continue
                if (!name.endsWith(EXTENSION, ignoreCase = true)) continue
                result += ScriptEntry(name, DocumentsContract.buildDocumentUriUsingTree(tree, id))
            }
            result.sortedBy { it.name }
        }
    }.getOrNull()

    fun read(context: Context, entry: ScriptEntry): String? = runCatching {
        context.contentResolver.openInputStream(entry.uri)?.bufferedReader(Charsets.UTF_8)?.use {
            it.readText().removePrefix("\uFEFF")
        }
    }.getOrNull()

    fun rootDocumentUri(tree: Uri): Uri =
        DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
}

package com.livetv.vn.data.store

import android.content.Context
import androidx.core.util.AtomicFile
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

class DiskJsonStore(
    context: Context,
    private val json: Json,
    directoryName: String = "livetv_data",
) {
    private val root = File(context.filesDir, directoryName).apply { mkdirs() }

    suspend fun <T> read(name: String, serializer: KSerializer<T>): T? = withContext(Dispatchers.IO) {
        val file = AtomicFile(File(root, name))
        try {
            if (!file.baseFile.exists()) return@withContext null
            val text = file.readFully().decodeToString()
            json.decodeFromString(serializer, text)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun <T> write(name: String, serializer: KSerializer<T>, value: T) {
        withContext(Dispatchers.IO) {
            val file = AtomicFile(File(root, name))
            val stream = file.startWrite()
            try {
                stream.write(json.encodeToString(serializer, value).encodeToByteArray())
                file.finishWrite(stream)
            } catch (error: Exception) {
                file.failWrite(stream)
                throw error
            }
        }
    }

    suspend fun delete(name: String) = withContext(Dispatchers.IO) {
        runCatching { AtomicFile(File(root, name)).delete() }
        Unit
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        root.listFiles()?.forEach { it.delete() }
        Unit
    }

    fun fileName(prefix: String, id: String): String {
        val hash = java.util.UUID.nameUUIDFromBytes(id.toByteArray()).toString()
        return "${prefix}_$hash.json"
    }
}

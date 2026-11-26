package io.github.mstajanc.gros.data.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** JSON s poľom `schemaVersion`, potom gzip. Všetko v pamäti, nič sa nezapisuje na disk. */
object BackupCodec {

    private val json = Json {
        encodeDefaults = true
        // Neznáme polia znamenajú novší alebo cudzí formát; radšej odmietnuť než potichu zahodiť dáta.
        ignoreUnknownKeys = false
    }

    fun encode(data: BackupData): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(json.encodeToString(BackupData.serializer(), data).encodeToByteArray()) }
        return out.toByteArray()
    }

    /**
     * Rozbalí a načíta zálohu. Novšia `schemaVersion` sa odmietne; staršia by sa tu zmigrovala
     * (zatiaľ existuje len verzia 1).
     */
    fun decode(bytes: ByteArray, currentSchemaVersion: Int): BackupData {
        val text = try {
            GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }.decodeToString()
        } catch (e: IOException) {
            throw BackupException(BackupError.INVALID_CONTENT, e)
        }
        try {
            val version = json.parseToJsonElement(text).jsonObject["schemaVersion"]?.jsonPrimitive?.int
                ?: throw BackupException(BackupError.INVALID_CONTENT)
            if (version > currentSchemaVersion) throw BackupException(BackupError.NEWER_SCHEMA)
            if (version < 1) throw BackupException(BackupError.INVALID_CONTENT)
            return json.decodeFromString(BackupData.serializer(), text)
        } catch (e: SerializationException) {
            throw BackupException(BackupError.INVALID_CONTENT, e)
        } catch (e: IllegalArgumentException) {
            throw BackupException(BackupError.INVALID_CONTENT, e)
        }
    }
}

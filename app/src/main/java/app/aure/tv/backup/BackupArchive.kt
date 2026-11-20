package app.aure.tv.backup

import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal object BackupHashing {
    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}

internal object BackupArchive {

    fun write(
        manifest: BackupManifest,
        components: Map<String, JSONObject>,
        password: CharArray?,
    ): ByteArray {
        if (password == null) {
            val entries = LinkedHashMap<String, ByteArray>()
            entries[BackupContract.MANIFEST_ENTRY] =
                BackupJson.encodeManifest(manifest).toString().toByteArray(Charsets.UTF_8)
            components.forEach { (path, json) ->
                entries[path] = json.toString().toByteArray(Charsets.UTF_8)
            }
            return zip(entries)
        }

        val inner = JSONObject()
            .put("manifest", BackupJson.encodeManifest(manifest))
            .put(
                "components",
                JSONObject().apply {
                    components.forEach { (path, json) -> put(path, json) }
                },
            )
        val encrypted = BackupEncryption.encrypt(inner.toString().toByteArray(Charsets.UTF_8), password)
        val outer = manifest.copy(
            components = emptyList(),
            encryption = encrypted.params,
            payload = PayloadRef(
                path = BackupContract.PAYLOAD_ENTRY,
                bytes = encrypted.ciphertext.size.toLong(),
                sha256 = BackupHashing.sha256(encrypted.ciphertext),
            ),
        )
        return zip(
            linkedMapOf(
                BackupContract.MANIFEST_ENTRY to
                    BackupJson.encodeManifest(outer).toString().toByteArray(Charsets.UTF_8),
                BackupContract.PAYLOAD_ENTRY to encrypted.ciphertext,
            ),
        )
    }

    fun read(input: InputStream, password: CharArray?): ReadBackup {
        val archive = input.readBounded(BackupContract.MAX_ARCHIVE_BYTES, "archive")
        val entries = unzip(archive)
        val manifestRaw = entries[BackupContract.MANIFEST_ENTRY]
            ?: throw BackupException(BackupStage.FORMAT, "manifest is missing")
        val manifest = BackupJson.decodeManifest(manifestRaw.toString(Charsets.UTF_8))
        if (manifest.encryption != null) {
            if (password == null || password.isEmpty()) {
                throw BackupException(BackupStage.CRYPTO, "password required")
            }
            val payloadRef = manifest.payload
                ?: throw BackupException(BackupStage.FORMAT, "encrypted payload is missing")
            if (payloadRef.path != BackupContract.PAYLOAD_ENTRY) {
                throw BackupException(BackupStage.FORMAT, "unexpected payload path")
            }
            val ciphertext = entries[payloadRef.path]
                ?: throw BackupException(BackupStage.FORMAT, "encrypted payload is missing")
            if (payloadRef.bytes != ciphertext.size.toLong() ||
                payloadRef.sha256 != BackupHashing.sha256(ciphertext)
            ) {
                throw BackupException(BackupStage.INTEGRITY, "encrypted payload checksum mismatch")
            }
            val plain = BackupEncryption.decrypt(ciphertext, manifest.encryption, password)
            val payload = try {
                JSONObject(plain.toString(Charsets.UTF_8))
            } catch (error: Exception) {
                throw BackupException(BackupStage.CRYPTO, "encrypted payload is not JSON", error)
            }
            val innerManifestRaw = payload.optJSONObject("manifest")
                ?: throw BackupException(BackupStage.FORMAT, "inner manifest is missing")
            val innerManifest = BackupJson.decodeManifest(innerManifestRaw.toString())
            val componentJson = payload.optJSONObject("components")
                ?: throw BackupException(BackupStage.FORMAT, "inner components are missing")
            val components = LinkedHashMap<String, String>()
            componentJson.keys().forEach { key ->
                val value = componentJson.optJSONObject(key)
                    ?: throw BackupException(BackupStage.FORMAT, "component is not an object")
                components[key] = value.toString()
            }
            return ReadBackup(innerManifest, components, encrypted = true)
        }

        if (manifest.payload != null) {
            throw BackupException(BackupStage.FORMAT, "payload present without encryption")
        }
        val components = LinkedHashMap<String, String>()
        manifest.components.forEach { component ->
            val bytes = entries[component.path]
                ?: throw BackupException(BackupStage.FORMAT, "component is missing: ${component.path}")
            if (component.bytes != bytes.size.toLong() ||
                component.sha256 != BackupHashing.sha256(bytes)
            ) {
                throw BackupException(
                    BackupStage.INTEGRITY,
                    "component checksum mismatch: ${component.path}",
                )
            }
            components[component.path] = bytes.toString(Charsets.UTF_8)
        }
        return ReadBackup(manifest, components, encrypted = false)
    }

    private fun zip(entries: Map<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.setLevel(java.util.zip.Deflater.BEST_SPEED)
            entries.forEach { (name, bytes) ->
                val entry = ZipEntry(name)
                entry.time = 0L
                zip.putNextEntry(entry)
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun unzip(archive: ByteArray): Map<String, ByteArray> {
        val result = LinkedHashMap<String, ByteArray>()
        var total = 0L
        ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
            var count = 0
            while (true) {
                val entry = zip.nextEntry ?: break
                count++
                if (count > BackupContract.MAX_ENTRIES) {
                    throw BackupException(BackupStage.FORMAT, "too many archive entries")
                }
                if (entry.isDirectory) {
                    throw BackupException(BackupStage.FORMAT, "directories are not allowed")
                }
                val name = safeName(entry.name)
                if (result.containsKey(name)) {
                    throw BackupException(BackupStage.FORMAT, "duplicate archive entry")
                }
                if (entry.size > BackupContract.MAX_ENTRY_BYTES) {
                    throw BackupException(BackupStage.FORMAT, "archive entry is too large")
                }
                if (entry.compressedSize > 0 && entry.size > 0 &&
                    entry.size / entry.compressedSize > BackupContract.MAX_COMPRESSION_RATIO
                ) {
                    throw BackupException(BackupStage.FORMAT, "archive entry compression ratio is unsafe")
                }
                val bytes = zip.readBounded(BackupContract.MAX_ENTRY_BYTES, "archive entry")
                total += bytes.size
                if (total > BackupContract.MAX_UNCOMPRESSED_BYTES) {
                    throw BackupException(BackupStage.FORMAT, "archive is too large when unpacked")
                }
                result[name] = bytes
                zip.closeEntry()
            }
        }
        return result
    }

    private fun safeName(raw: String): String {
        if (raw.isBlank() || raw.startsWith("/") || raw.startsWith("\\") ||
            raw.contains('\\') || raw.contains('\u0000') || raw.contains(':')
        ) {
            throw BackupException(BackupStage.FORMAT, "unsafe archive path")
        }
        val segments = raw.split('/')
        if (segments.any { it.isEmpty() || it == "." || it == ".." }) {
            throw BackupException(BackupStage.FORMAT, "unsafe archive path")
        }
        if (raw != BackupContract.MANIFEST_ENTRY &&
            raw != BackupContract.PAYLOAD_ENTRY &&
            !raw.startsWith(BackupContract.COMPONENT_DIR)
        ) {
            throw BackupException(BackupStage.FORMAT, "unexpected archive entry")
        }
        return raw
    }

    private fun InputStream.readBounded(limit: Long, what: String): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) {
                throw BackupException(BackupStage.FORMAT, "$what exceeds the size limit")
            }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}

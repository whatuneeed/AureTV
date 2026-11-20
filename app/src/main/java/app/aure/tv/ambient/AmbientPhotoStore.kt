package app.aure.tv.ambient

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import app.aure.tv.data.LauncherPrefs
import app.aure.tv.data.SafePreferences
import org.json.JSONArray

/**
 * The photo source is either one folder chosen with the system document picker
 * or a bounded set of individual photos chosen with the system photo picker.
 * Both are provider grants, never a broad media permission. Neither the tree
 * nor the selected URIs is part of a backup: a restore cannot recreate another
 * device's grant.
 */
object AmbientPhotoStore {
    const val KEY_TREE_URI = "ambient_photo_tree_uri"
    const val KEY_PHOTO_URIS = "ambient_photo_selected_uris"
    const val MAX_SELECTED = 50
    internal const val PICK_IMAGES = "android.provider.action.PICK_IMAGES"
    internal const val PICK_IMAGES_MAX = "android.provider.extra.PICK_IMAGES_MAX"
    // Android TV ROMs ship a DocumentsUI stub that only shows "no app can do
    // this". It resolves, so resolving alone is not enough.
    private const val DOCUMENTS_STUB = "com.android.tv.frameworkpackagestubs"

    fun treeUri(context: Context): Uri? =
        SafePreferences.string(prefs(context), KEY_TREE_URI, null)
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?.takeIf { it.scheme == ContentResolver.SCHEME_CONTENT }

    /**
     * Many Android TV images ship without DocumentsUI. Check before launching
     * so the screen shows its own explanation instead of the system's generic
     * "no app can do this" toast.
     */
    fun canPickFolder(context: Context): Boolean {
        val resolved = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            .addCategory(Intent.CATEGORY_DEFAULT)
            .resolveActivity(context.packageManager)
            ?: return false
        return resolved.packageName != DOCUMENTS_STUB
    }

    /**
     * Android 13's system photo picker is present on Google TV images even when
     * DocumentsUI is not. It grants individual photos rather than a folder.
     */
    fun canPickPhotos(context: Context): Boolean =
        Intent(PICK_IMAGES)
            .setType("image/*")
            .resolveActivity(context.packageManager) != null

    /** True only while the provider still grants persisted read access. */
    fun hasFolder(context: Context): Boolean {
        val uri = treeUri(context) ?: return false
        return hasReadGrant(context, uri)
    }

    fun hasSource(context: Context): Boolean =
        hasFolder(context) || selectedPhotos(context).isNotEmpty()

    /**
     * Persists the tree grant and the URI. Returns false when the provider
     * does not offer persistable access, so a folder that would disappear after
     * a reboot is never presented as chosen.
     */
    fun setTree(context: Context, uri: Uri): Boolean {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return false
        return try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            releaseSelected(context)
            prefs(context).edit()
                .putString(KEY_TREE_URI, uri.toString())
                .remove(KEY_PHOTO_URIS)
                .apply()
            true
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    /** Persists individual photo grants; false when none can survive a reboot. */
    fun setPhotos(context: Context, uris: List<Uri>): Boolean {
        val accepted = LinkedHashSet<Uri>()
        for (uri in uris.take(MAX_SELECTED)) {
            if (uri.scheme != ContentResolver.SCHEME_CONTENT) continue
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
                accepted += uri
            } catch (_: SecurityException) {
                // A picker that grants only for this session cannot be a
                // durable screen saver source; skip that URI.
            } catch (_: IllegalArgumentException) {
                // Same: an unusable grant is not a photo.
            }
        }
        if (accepted.isEmpty()) return false
        releaseTree(context)
        releaseSelected(context, except = accepted)
        val array = JSONArray()
        accepted.forEach { array.put(it.toString()) }
        prefs(context).edit()
            .putString(KEY_PHOTO_URIS, array.toString())
            .remove(KEY_TREE_URI)
            .apply()
        return true
    }

    fun selectedPhotos(context: Context): List<Uri> {
        val raw = SafePreferences.string(prefs(context), KEY_PHOTO_URIS, null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val granted = context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .mapTo(HashSet()) { it.uri }
        val photos = ArrayList<Uri>(array.length())
        for (index in 0 until array.length()) {
            val uri = array.optString(index).takeIf { it.isNotBlank() }
                ?.let { runCatching { Uri.parse(it) }.getOrNull() }
                ?.takeIf { it.scheme == ContentResolver.SCHEME_CONTENT }
                ?: continue
            if (uri in granted && uri !in photos) photos += uri
        }
        return photos
    }

    /** Drops the source and its grants. It never touches the photos themselves. */
    fun clear(context: Context) {
        releaseTree(context)
        releaseSelected(context)
        prefs(context).edit()
            .remove(KEY_TREE_URI)
            .remove(KEY_PHOTO_URIS)
            .apply()
    }

    /** The last segment of the tree id, e.g. "Pictures" for primary:Pictures. */
    fun folderLabel(context: Context): String? {
        if (!hasFolder(context)) return null
        val uri = treeUri(context) ?: return null
        val id = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
        return id.substringAfter(':').ifBlank { id }
            .substringAfterLast('/')
            .ifBlank { null }
    }

    private fun releaseTree(context: Context) {
        treeUri(context)?.let { uri ->
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
    }

    private fun releaseSelected(context: Context, except: Set<Uri> = emptySet()) {
        selectedPhotos(context)
            .filter { it !in except }
            .forEach { uri ->
                runCatching {
                    context.contentResolver.releasePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            }
    }

    private fun hasReadGrant(context: Context, uri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }

    private fun prefs(context: Context) =
        context.getSharedPreferences(LauncherPrefs.NAME, Context.MODE_PRIVATE)
}

package app.aure.tv.ambient

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Images in the folder the viewer chose. The query is one level deep, matching
 * what the picker chose: a folder, not a library scan. The list is capped so a
 * very large folder cannot turn a screen saver into an indexing job.
 */
object AmbientPhotoLibrary {
    const val MAX_PHOTOS = 300
    private const val TAG = "AmbientPhotoLibrary"

    suspend fun load(context: Context): List<Uri> = withContext(Dispatchers.IO) {
        query(context)
    }

    private fun query(context: Context): List<Uri> {
        val tree = AmbientPhotoStore.treeUri(context)
        if (tree == null || !AmbientPhotoStore.hasFolder(context)) {
            return AmbientPhotoStore.selectedPhotos(context).take(MAX_PHOTOS)
        }
        val resolver = context.contentResolver
        val parent = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull()
            ?: return emptyList()
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        )
        return try {
            val found = ArrayList<Uri>()
            resolver.query(children, projection, null, null, null)?.use { cursor ->
                val idColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val mimeColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val nameColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                if (idColumn < 0 || mimeColumn < 0) return emptyList()
                val entries = ArrayList<Pair<String, String>>()
                while (cursor.moveToNext() && entries.size < MAX_PHOTOS) {
                    val mime = cursor.getString(mimeColumn) ?: continue
                    if (!mime.startsWith("image/")) continue
                    val id = cursor.getString(idColumn) ?: continue
                    val name = if (nameColumn >= 0) cursor.getString(nameColumn).orEmpty() else ""
                    entries += id to name
                }
                entries.sortWith { left, right ->
                    String.CASE_INSENSITIVE_ORDER.compare(left.second, right.second)
                }
                entries.forEach { (id, _) ->
                    found += DocumentsContract.buildDocumentUriUsingTree(tree, id)
                }
            }
            found
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "photo folder query failed", error)
            emptyList()
        }
    }
}

package app.aure.tv.ambient

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

/**
 * The system photo picker on Android 13+. [androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia]
 * falls back to `ACTION_OPEN_DOCUMENT` when it does not recognise the TV's
 * picker, which fails on images without DocumentsUI. This contract asks for the
 * photo picker directly; the caller checks [AmbientPhotoStore.canPickPhotos]
 * first and shows its own explanation when no picker exists.
 */
internal class PhotoPickerContract(
    private val maxItems: Int,
) : ActivityResultContract<Unit, List<Uri>>() {
    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(AmbientPhotoStore.PICK_IMAGES)
            .setType("image/*")
            .putExtra(AmbientPhotoStore.PICK_IMAGES_MAX, maxItems)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
        if (resultCode != Activity.RESULT_OK || intent == null) return emptyList()
        val clip = intent.clipData
        if (clip != null) {
            return (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
        }
        return listOfNotNull(intent.data)
    }
}
